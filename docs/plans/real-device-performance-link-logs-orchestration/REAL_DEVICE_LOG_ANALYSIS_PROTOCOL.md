# Real-Device Log Analysis Protocol

本协议用于指导真机测试人员（或设备持有 Codex session）收集和分析性能链路日志。
协议区分**实现验证**（本 package 已完成）与**真机产品信心**（需外部协助）。

---

## 一、APK 构建与安装

### 构建 APK

在 worktree 内执行：

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
```

### APK 输出路径

```
<HOME>/.codex-build/OpenCamera-b8ca60d4/app/outputs/apk/debug/app-debug.apk
```

构建后复制到 `public/` 目录：
```
/Volumes/Extreme_SSD/project/open_camera/.worktrees/performance-link-logs/04-real-device-log-analysis-protocol/public/app-debug.apk
```

### 安装到设备

```bash
adb install -r <HOME>/.codex-build/OpenCamera-b8ca60d4/app/outputs/apk/debug/app-debug.apk
```

若已有旧版本签名冲突：
```bash
adb uninstall com.opencamera.app
adb install <HOME>/.codex-build/OpenCamera-b8ca60d4/app/outputs/apk/debug/app-debug.apk
```

---

## 二、APP 内导出日志操作步骤

### 2.1 打开 Dev 控制台

启动 App → 点击取景器右侧竖排按钮栏中的 **DEV** 按钮 → 弹出 Dev 控制台面板。

### 2.2 导出 LINK（耗时）日志

1. 在 Dev 控制台顶部 Tab 栏点击 **"耗时"** 按钮（`buttonDevTabLink`）
2. 确认面板显示机器可读的 link 格式行（格式见第三节）
3. 点击 **"Export Log"** 按钮
4. 导出文件保存至 `debug-logs/opencamera-debug-<timestamp>.log`，文件头 `# type: LINK`

### 2.3 导出 ERROR（错误）日志

1. 点击 **"错误"** Tab（`buttonDevTabError`）
2. 确认面板显示错误事件列表
3. 点击 **"Export Log"** → 文件头 `# type: ERROR`

### 2.4 导出 ALL（全部）日志

1. 点击 **"全部"** Tab（`buttonDevTabAll`）
2. 点击 **"Export Log"** → 文件头 `# type: ALL`，包含所有区段

### 2.5 导出文件位置

```
/sdcard/Android/data/com.opencamera.app/files/debug-logs/opencamera-debug-<timestamp>.log
```

获取导出文件：
```bash
adb pull /sdcard/Android/data/com.opencamera.app/files/debug-logs/ ./device-logs/
```

---

## 三、导出日志格式与 Schema

### 3.1 导出文件结构

```
# type: ALL
=== KEY EVENTS ===
0. app.start -> detail=...
1. session.created -> detail=...

=== CORE EVENTS ===
...

=== ERROR EVENTS ===
...

=== LINK FLOW EVENTS ===
link flow=preview stage=first_frame status=completed id=corr-1 startElapsed=100 endElapsed=188 duration=88ms detail=cold_start source=PreviewRecoverySessionProcessor
link flow=capture stage=shot status=completed id=corr-2 startElapsed=500 endElapsed=763 duration=263ms detail=device_245ms,postprocess_18ms source=CaptureRecordingSessionProcessor

=== ALL EVENTS ===
...

=== RESOURCE DIAGNOSTICS ===
...

=== CORE SUMMARY ===
...
```

### 3.2 LINK FLOW EVENTS 行格式

每行空格分隔的键值对，agent/grep 友好：

```
link flow=<flow> stage=<stage> status=<status> id=<correlation_id> startElapsed=<ms> [endElapsed=<ms>] [duration=<ms>ms] [detail=<detail>] source=<source>
```

| 字段 | 说明 | 示例 |
|---|---|---|
| `flow` | 流程名 | `preview`, `capture`, `recording`, `mode_switch`, `recovery` |
| `stage` | 阶段名 | `first_frame`, `shot`, `start`, `stop`, `switch` |
| `status` | 状态枚举 | `started`, `completed`, `degraded`, `failed`, `cancelled`, `unavailable` |
| `id` | 关联 ID | `corr-<n>` |
| `startElapsed` | 单调起始时刻(ms) | `100` |
| `endElapsed` | 单调结束时刻(ms) | `188` |
| `duration` | 耗时(ms) | `88ms` |
| `detail` | 补充信息 | `cold_start`, `device_245ms,postprocess_18ms` |
| `source` | 记录来源 | `PreviewRecoverySessionProcessor`, `CaptureRecordingSessionProcessor` |

> 注意：时间使用单调时钟（`System.nanoTime()/1_000_000`），不受系统时间调整影响。

### 3.3 LinkEventStatus 枚举

| 状态 | label | 含义 |
|---|---|---|
| `STARTED` | `started` | 已开始 |
| `COMPLETED` | `completed` | 已完成 |
| `DEGRADED` | `degraded` | 降级完成 |
| `FAILED` | `failed` | 失败 |
| `CANCELLED` | `cancelled` | 已取消 |
| `UNAVAILABLE` | `unavailable` | 能力不可用 |

---

## 四、设备元数据记录模板

每次真机测试必须记录以下信息（填入表格）：

```
设备型号:  [例如: 厂商 14 / Pixel 8 Pro]
Android 版本: [例如: Android 14, API 34]
App 构建 commit:  [例如: dee6bf7]
App 构建时间戳:  [例如: 2026-05-29T10:00:00Z]
测试日期:  [例如: 2026-05-29]
测试人员/会话:  [例如: user@device]
环境备注:  [例如: 室温 25°C, 电量 80%, WiFi 连接]
```

---

## 五、需要训练的流程

测试时按以下顺序执行，每个流程完成后导出一份 LINK 日志：

| # | 流程 | 操作步骤 | 预期事件 | 关键指标 |
|---|---|---|---|---|
| 1 | 冷启动预览 | 杀死 App → 重新启动 → 等待第一帧预览出现 | `preview/first_frame` | duration < 500ms |
| 2 | 前台恢复 | 按 Home → 等待 3 秒 → 切回 App | `preview/first_frame` (resume) | duration 应小于冷启动 |
| 3 | 模式切换 | 照片 → 视频 → 专业 → 人像 → 照片 | `mode_switch/switch` | 每次切换 < 1000ms |
| 4 | 变焦/镜头切换 | 在照片模式下逐步 zoom 从 1x 到 5x 再到 1x | `lens/switch` 或 `zoom/switch` | 镜头切换 < 800ms |
| 5 | 触摸对焦 | 点击取景器不同位置 3 次 | `focus/tap` 或 `focus/auto` | duration < 500ms |
| 6 | 亮度调节 | 滑动亮度滑块 ±2 EV | `brightness/adjust` 或 `exposure/adjust` | duration < 300ms |
| 7 | 拍照 | 按下快门 → 等待拍摄完成 → 确认预览恢复 | `capture/shot` | total < 2000ms, device 部分 < 1500ms |
| 8 | 录像 | 开始录制 → 录制 5 秒 → 停止 | `recording/start`, `recording/stop` | start 延迟 < 1000ms |
| 9 | 可恢复/降级路径 | 覆盖镜头 → 等待恢复提示 | `recovery/recover` 或 `preview/degraded` | 恢复 < 3000ms |
| 10 | 快速连拍 | 连续按快门 5 次 | 多个 `capture/shot` | 每次间隔合理，无 failed |

---

## 六、性能分析表模板

对每条导出的 link 行，填入以下分析表：

| 关联 ID | 流程 | 最慢阶段 | 耗时(ms) | 阈值/预算 | 疑似归属 | 证据行 | 下一步动作 |
|---|---|---|---|---|---|---|---|
| corr-1 | preview | first_frame | 88 | 500ms | 设备 CameraX 初始化 | `link flow=preview stage=first_frame status=completed id=corr-1 startElapsed=100 endElapsed=188 duration=88ms` | 通过 |
| corr-2 | capture | shot | 263 | 2000ms | 设备采集 245ms, 后处理 18ms | `link flow=capture stage=shot status=completed id=corr-2 startElapsed=500 endElapsed=763 duration=263ms detail=device_245ms,postprocess_18ms` | 通过 |
| ... | ... | ... | ... | ... | ... | ... | ... |

---

## 七、性能证据分类规则

每条 link 事件的 `flow` + `stage` + `source` 组合归属以下类别之一：

| 类别 | 匹配规则 | 说明 |
|---|---|---|
| **预览 (preview)** | `flow=preview` 或 source 含 `Preview` | 预览启动、恢复、帧就绪 |
| **设备采集 (device)** | `flow=capture` 且 detail 含 `device_` | CameraX/HAL 层采集耗时 |
| **后处理 (postprocess)** | `flow=capture` 且 detail 含 `postprocess_` | CPU/GPU 图像处理 |
| **媒体保存 (save)** | `flow=capture\|recording` 且 detail 含 `save_\|media_` | 文件 I/O、MediaStore 写入 |
| **UI 反馈 (ui)** | `flow` 含 `ui_` 或 stage 含 `render` | UI 线程、渲染耗时 |
| **恢复 (recovery)** | `flow=recovery` 或 status 含 `degraded\|failed` | 错误恢复路径 |

**归属判定规则：**

1. 若 duration > 阈值，标记为 **需关注**
2. 若 `status=failed` 或 `degraded`，标记为 **阻塞/降级**
3. 若 `device_` 时间占比 > 80%，归属为 **设备端瓶颈**
4. 若 `postprocess_` 时间异常高（> 500ms），归属为 **后处理瓶颈**
5. 若同一 flow 在同一测试轮次出现 3 次以上 failed，标记为 **系统性问题**

---

## 八、测试停止条件

出现以下任一情况时应立即停止测试并记录原因：

| 停止条件 | 检测方式 | 处理建议 |
|---|---|---|
| 设备过热 | 系统弹出过热警告 / Dev 面板 "thermal" 字段显示 CRITICAL | 冷却 5 分钟后重试 |
| 相机不可用 | `preview/first_frame` status=failed 或 `preview/unavailable` | 重启 App；仍失败则重启设备 |
| App 崩溃 | 导出日志中无正常完成记录 / logcat 显示 FATAL | 记录崩溃堆栈，重启测试 |
| 连续 provider 失败 | 同一 flow 内 3 次连续 failed 或 unavailable | 停止该流程，标记为设备不兼容 |
| 存储空间不足 | Dev 面板显示 usedBytes 接近 20MB 上限 | 点击清理按钮或手动清除 debug-logs 目录 |
| 预览冻结超过 10 秒 | 人工观察无画面更新 + 导出日志无新 link 事件 | 强制停止 App 并重启 |

---

## 九、实现验证 vs 真机产品信心

### 已完成的实现验证（本 package）

本 package 已通过本地构建和单元测试完成实现验证：

```
DevLogRenderModelTest: 21 passed (含 6 个 LINK tab 测试)
DevLogExporterTest: 16 passed (含 LINK type header 测试)
:app:assembleDebug: BUILD SUCCESSFUL
APK 路径: <HOME>/.codex-build/OpenCamera-b8ca60d4/app/outputs/apk/debug/app-debug.apk
```

验证命令：
```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DevLogRenderModelTest --tests com.opencamera.app.DevLogExporterTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh status
```

### 仍需外部协助的真机证据

**以下证据只能由物理设备持有者提供，本地无法模拟：**

- 真机各流程的 LINK 日志导出文件
- 真机 ERROR 日志（至少一次已知失败/降级路径）
- 设备型号、Android 版本等元数据
- 流程耗时实际数值与阈值的对比
- 分析表填入和性能分类结论

**声明：真机 QA 尚未通过。** 当前状态为"实现验证完成，等待真机证据"。除非提供了上述真机证据，不得声称真机性能验收通过。

---

## 十、快速参考：真机测试 checklist

- [ ] 安装 APK 到测试设备
- [ ] 记录设备元数据（第四节模板）
- [ ] 执行冷启动预览 → 导出 LINK 日志
- [ ] 执行前台恢复 → 导出 LINK 日志
- [ ] 执行模式切换 → 导出 LINK 日志
- [ ] 执行变焦/镜头切换 → 导出 LINK 日志
- [ ] 执行触摸对焦 → 导出 LINK 日志
- [ ] 执行亮度调节 → 导出 LINK 日志
- [ ] 执行拍照 → 导出 LINK 日志
- [ ] 执行录像 → 导出 LINK 日志
- [ ] 执行可恢复/降级路径 → 导出 ERROR + LINK 日志
- [ ] 执行快速连拍 → 导出 LINK 日志
- [ ] 拉取所有导出日志到本地：`adb pull /sdcard/Android/data/com.opencamera.app/files/debug-logs/ ./device-logs/`
- [ ] 对照分析表模板（第六节）逐行填入
- [ ] 按分类规则（第七节）标记归属
- [ ] 检查是否有停止条件触发（第八节）
- [ ] 将分析结论注回此文档或 coordinator status

---

## 十一、从 logcat 补充抓取

真机测试时可同时抓取 logcat 作为补充：

```bash
# 过滤 tag 包含 OpenCamera
adb logcat -s "OpenCamera:*" > opencamera-logcat.log

# 或全量抓取（文件较大）
adb logcat -d > full-logcat.log
```

---

*协议版本: 1.0 | 生成时间: 2026-05-29 | 关联 package: 04-real-device-log-analysis-protocol*
