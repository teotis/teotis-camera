# Real Device Timing Protocol — Shutter Readiness & Sound

## Purpose

本协议用于在真实设备上验证快门就绪与声音时机优化后的行为。协议不执行实测——它提供 APK 路径、安装命令、证据收集方法、时序记录表和判定标准，设备持有者按本协议操作即可产出可比对的时间证据。

**重要**: 本协议不宣称真实设备 PASS。真实设备 QA 属于 `external-assist`，需要人工听觉/视觉判断。

---

## 1. 背景：当前实现中的关键时序点

在开始实测前，先理解当前代码的快门时序链：

```
用户按下快门按钮 (t0)
  │
  ├─ SessionIntent.ShutterPressed
  │   └─ activeShot 设置, captureStatus = REQUESTED
  │
  ├─ CameraXCaptureAdapter.captureStillImage()
  │   ├─ DeviceEvent.ShotStarted 发射 (t1)
  │   └─ ImageCapture.takePicture() 调用
  │
  ├─ [Camera2 曝光 → 传感器读出 → JPEG 编码 → 文件写入]
  │
  ├─ Camera2 onCaptureCompleted (STILL_CAPTURE)
  │   └─ DeviceEvent.CaptureCommitted 发射 (t_capture_done)
  │       └─ SessionKernel: captureReadiness 信号设置 ← 🎯 快门声音在此播放
  │       └─ SessionKernel: 快门按钮从此可重新启用
  │
  ├─ OnImageSavedCallback.onImageSaved()
  │   └─ DeviceEvent.DataReceived 发射 (t2)
  │       └─ 普通 STILL_CAPTURE: activeShot 清除，按钮可再次按下
  │
  └─ emitShotCompleted() (后台协程，非关键路径)
      └─ DeviceEvent.ShotCompleted 发射 (t3)
```

**核心改动（相较于原始行为）**:
- **快门声音**: 已从 `activeShot` 首次出现（请求反馈）移至 `CaptureCommitted`（Camera2 报告帧采集完成），更接近"用户可以放下手机"的时刻。
- **按钮重新启用**: 对于普通 STILL_CAPTURE 照片，`CaptureCommitted` 到达后即允许重新按下；对于多帧/动态照片等保守模式，保持原有阻塞逻辑。

---

## 2. Debug APK

### 2.1 构建命令

```bash
# 主仓库构建
rtk ./gradlew --no-daemon :app:assembleDebug

# 隔离 worktree 构建
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

### 2.2 APK 路径

| 构建环境 | APK 路径 |
|----------|---------|
| 主仓库 (默认) | `~/.codex-build/OpenCamera/app/outputs/apk/debug/app-debug.apk` |
| 隔离 worktree | `~/.codex-build/OpenCamera-<git-root-hash>/app/outputs/apk/debug/app-debug.apk` |

可通过以下命令在构建后定位 APK：
```bash
find ~/.codex-build/ -name "app-debug.apk" -type f 2>/dev/null
```

### 2.3 安装

```bash
# 首次安装
adb install ~/.codex-build/OpenCamera/app/outputs/apk/debug/app-debug.apk

# 覆盖安装（保留数据）
adb install -r ~/.codex-build/OpenCamera/app/outputs/apk/debug/app-debug.apk

# 验证安装
adb shell pm list packages | grep opencamera
# 期望输出: package:com.opencamera.app
```

---

## 3. 证据收集方法

### 3.1 方法 A：App 内 Dev Log → LINK 标签页（首选）

应用内置 Dev Console，其 LINK 标签页显示结构化性能事件，格式为：

```
link flow=capture stage=device_started status=started id=<shotId> startElapsed=<ms> ...
link flow=capture stage=capture_committed status=completed id=<shotId> startElapsed=<ms> duration=<N>ms ...
link flow=capture stage=data_received status=completed id=<shotId> startElapsed=<ms> duration=<N>ms ...
link flow=capture stage=postprocess status=completed id=<shotId> startElapsed=<ms> duration=<N>ms ...
```

**操作步骤**:
1. 打开 OpenCamera App。
2. 进入 Dev Console（从设置或调试入口）。
3. 切换到 **LINK** 标签页。
4. 拍摄一张照片。
5. 观察 LINK 标签页中 `flow=capture` 的事件时间线。
6. 导出日志：
   - Dev Console 提供导出功能，日志文件保存到 `Android/data/com.opencamera.app/files/debug-logs/opencamera-debug-<timestamp>.log`
   - 通过 adb 拉取：`adb pull /sdcard/Android/data/com.opencamera.app/files/debug-logs/ ./device-logs/`

### 3.2 方法 B：logcat 实时监控

CameraX/Camera2 内部日志可通过 logcat 过滤：

```bash
# 监控 CameraX 内部日志
adb logcat -v threadtime | grep -E "CameraX|Camera2|ImageCapture|CaptureSession"

# 监控 Camera2 帧回调（底层时序）
adb logcat -v threadtime -s Camera2CaptureSessionImpl:D CameraCaptureSession:D

# 全量 CameraX 日志（包含 JPEG 编码时间）
adb logcat -v threadtime | grep -E "CameraX|Camera2|ImageCapture|JpegEncoder|ImageSaver"
```

**关键 logcat 搜索词**:
| 关键词 | 含义 |
|--------|------|
| `takePicture` | 拍照请求发出 |
| `onCaptureCompleted` | Camera2 帧捕获完成 |
| `onImageSaved` | 图片文件写入完成 |
| `ImageSaver` | JPEG 保存队列 |
| `CaptureSession` | Camera2 会话状态 |

### 3.3 方法 C：慢动作视频/音频录制（推荐用于声音时序判断）

如果设备支持 240fps+ 慢动作录制：

1. 用另一台手机以 240fps 或更高帧率录制 OpenCamera App 屏幕。
2. 确保录制画面同时包含：
   - 快门按钮区域（可见按钮状态变化）
   - 设备扬声器区域（或使用外接麦克风）
3. 帧步进分析：
   - 手指接触屏幕的帧 → `t_touch`
   - 按钮视觉状态变化的帧 → `t_loading`
   - 音频波形首次出现快门声的帧 → `t_sound`
   - 按钮恢复可点击外观的帧 → `t_ready`

**240fps 下的时间分辨率**: 每帧 ≈ 4.17ms。

### 3.4 方法 D：系统相机对照

在同一场景（光照、距离、主体相同）下，使用系统相机 App 拍摄照片，记录：
- 按下快门到听到声音的感知延迟
- 按下快门到可以立即拍第二张的感知延迟

不需要仪器级别精度，只需要主观对比："比系统相机慢/快/差不多"。

---

## 4. 时序记录表

以下表格供设备持有者在实测时填写。每个场景至少拍摄 5 张照片，取中位数。

### 4.1 单张测试（普通 STILL_CAPTURE，非多帧/非动态照片）

| 指标 | 第1次 | 第2次 | 第3次 | 第4次 | 第5次 | 中位数 |
|------|-------|-------|-------|-------|-------|--------|
| press-to-loading (按钮进入加载态, ms) | | | | | | |
| press-to-sound (听到快门声, ms) | | | | | | |
| press-to-clickable (按钮恢复可点击, ms) | | | | | | |
| press-to-second-shot-accepted (第二次按下有效, ms) | | | | | | |
| press-to-saved-media (媒体出现在相册, ms) | | | | | | |
| Dev Log: request→capture_committed (ms) | | | | | | |
| Dev Log: capture_committed→data_received (ms) | | | | | | |
| Dev Log: data_received→postprocess (ms) | | | | | | |

### 4.2 连拍测试（快速连续拍摄 5 张）

| 指标 | 值 |
|------|-----|
| 第1张到第2张的最小间隔 (ms) | |
| 第2张到第3张的最小间隔 (ms) | |
| 连拍中是否有某张被忽略/吞掉 | Y/N |
| 声音是否每张都播放 | Y/N |
| 按钮是否在连拍间隙闪烁/闪烁加载态 | Y/N |

### 4.3 系统相机对照

| 指标 | OpenCamera | 系统相机 |
|------|-----------|----------|
| press-to-sound 感知延迟 | | |
| press-to-ready 感知延迟 | | |
| 连拍最小间隔 | | |
| 声音与实际采集时机的一致性（主观） | | |

---

## 5. 关键判定规则

### 5.1 PASS 指示器

- [ ] 快门声音在 `CaptureCommitted` 后播放（而非在 `ShutterPressed` 时），且与系统相机在感知上接近。
- [ ] 普通 STILL_CAPTURE 模式下，按钮在 `CaptureCommitted` 后重新启用，允许立即拍摄下一张。
- [ ] Dev Log LINK 标签页中可观察到完整的 `requested → device_started → capture_committed → data_received → postprocess` 事件链。
- [ ] 第二次按下快门确实触发了新的拍摄（而非被忽略）。
- [ ] 保守拍摄模式（多帧、动态照片等）的按钮在数据完全接收前保持阻塞。

### 5.2 FAIL 指示器

- [ ] 快门声音在按下瞬间播放（回退到了请求反馈而非就绪反馈）。
- [ ] 普通照片拍摄后按钮长时间（>2秒）不可用。
- [ ] 声音完全不播放（但设置中已开启）。
- [ ] 第二次按下被悄悄吞掉，无任何反馈。
- [ ] 保守拍摄模式的安全阻塞被绕过，允许在数据就绪前拍摄第二张。

### 5.3 边界情况

- [ ] 快速切换拍照/录像模式后拍摄 → 声音/按钮行为正常。
- [ ] 低存储空间下拍摄 → 有合理降级提示，不崩溃。
- [ ] 预览画面被遮挡/切换 App 回来后拍摄 → 行为正常。
- [ ] 倒计时拍摄（如有）→ 倒计时结束后的声音/按钮时序正确。

---

## 6. 证据回传格式

请将以下内容打包回传：

```
evidence/
├── README.md                  # 设备型号、Android 版本、测试日期、环境光照条件
├── timing-table.md            # 填写完成的第4节时序记录表
├── dev-logs/
│   └── opencamera-debug-*.log # 从 Dev Log LINK 标签页导出的日志
├── logcat/
│   └── logcat-capture.txt     # adb logcat 抓取的 CameraX/Camera2 日志
├── video/
│   └── slowmo-sample.mp4      # (可选) 慢动作视频片段
├── screenshots/
│   └── link-tab.png           # Dev Console LINK 标签页截图
└── comparison-notes.md        # 与系统相机的主观对比笔记
```

### 6.1 最小可接受证据集

如果资源有限，至少提供：
- 填写的 `timing-table.md`（至少完成 5 张单张测试）
- 一份从 Dev Log LINK 标签页导出的日志
- 设备型号和 Android 版本
- 一句主观对比："快门声音比系统相机 [快/慢/差不多]"

---

## 7. 设备要求

| 要求 | 最低 | 推荐 |
|------|------|------|
| Android 版本 | 8.0 (API 26) | 12+ (API 31+) |
| 架构 | arm64-v8a | arm64-v8a |
| CameraX 支持 | LEGACY 级别即可 | LEVEL_3 或更高 |
| 辅助设备 | 无 | 第二台手机用于慢动作录制 |

---

## 8. 故障排查

### APK 安装失败 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

```bash
adb uninstall com.opencamera.app
adb install ~/.codex-build/OpenCamera/app/outputs/apk/debug/app-debug.apk
```

### Dev Console 不显示 LINK 标签页

确认 App 为 debug 构建（`assembleDebug`），不是 release 构建。LINK 标签页仅在 debug 构建中可见。

### 找不到 Dev Console 入口

在 App 主界面查找调试入口（通常在设置菜单或长按某个 UI 元素）。如果找不到，检查 `MainActivity` 中 Dev Console 的触发方式。

### 声音延迟明显长于系统相机

这是已知的预期差距——OpenCamera 的 `CaptureCommitted` 信号来自 Camera2 会话回调，发生在 JPEG 编码开始后；而系统相机可能使用更底层的 HAL 信号。记录实际差距值比消除差距更重要。

---

## 9. 变更记录

| 日期 | 变更 |
|------|------|
| 2026-05-29 | 初始版本，基于 packages 04/05 的实现状态创建 |

---

## 10. 相关文档

- 包 01 研究输出: `outputs/01-camerax-camera2-signal-feasibility.md`
- 包 02 测试基线: `outputs/02-current-shutter-timing-tests.md`
- 实现代码:
  - `CameraXCaptureAdapter.kt` — DeviceEvent 发射和 Camera2 互操作
  - `CaptureRecordingSessionProcessor.kt` — captureReadiness 信号和 activeShot 管理
  - `MainActivity.kt` — maybePlayShutterSound() 声音触发逻辑
  - `SessionCockpitRenderModel.kt` — shutterVisualState/shutterDisabledReason 按钮状态
  - `PerformanceLinkEvent.kt` — LINK 标签页结构化事件格式
