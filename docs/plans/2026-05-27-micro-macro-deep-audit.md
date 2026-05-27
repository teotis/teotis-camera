# OpenCamera 微观到宏观全面深度审计报告

**审计日期**: 2026-05-27
**审计方法**: 6 个并行专项 agent + 宏观手动检查
**审计范围**: 并发安全、资源生命周期、状态机正确性、数据流逻辑、测试质量、项目基础设施
**项目规模**: ~70,597 行 Kotlin 源码, 157 源文件, 123 测试文件, 1,619 测试方法

---

## 一、宏观健康问题 (项目基础设施)

### 1.1 Git 仓库膨胀 — Critical

| 指标 | 数值 | 问题 |
|------|------|------|
| 松散对象 | 6.74 GB | 远超正常 Android 项目 |
| 跟踪文件 | 1,121 个 | 含大量不应跟踪的文件 |
| Worktree | 50 个 | 绝大多数已废弃 |
| 文档文件 | 861 个 (66,616 行) | 文档比代码还多 |

**根因**:
- `public/teotis-camera/` 目录 (2.9GB) 被整体跟踪 — 这是项目的一个完整副本
- `public/app-debug.apk` (31MB) 被跟踪 — APK 不应进 git
- `.idea/` 目录 (11 个文件) 被跟踪 — IDE 配置不应进 git
- `.tmp/` 目录 (21 个文件) 被跟踪 — 临时文件不应进 git
- `public/readme-assets-source/` 含大量高分辨率 JPG (每张 2-5MB)

**建议**: 使用 `git filter-repo` 清除大文件，将 `teotis-camera` 移到独立仓库或 submodule，将 `.idea/` 和 `.tmp/` 加入 `.gitignore`。

### 1.2 Worktree 污染 — High

50 个 git worktree 中:
- 43 个在 `.claude/worktrees/` 或 `.worktrees/` 下
- 多个指向已合并的 commit (如 `36abefa` 即 main HEAD)
- 部分路径指向旧工作区 `/Volumes/Extreme_SSD/project/codex_camera/` 已不存在
- 占用磁盘空间但无实际价值

**建议**: 批量清理已合并/过期的 worktree: `git worktree list --porcelain | ... | xargs git worktree remove`

### 1.3 依赖版本过时 — Medium

| 依赖 | 当前版本 | 最新版本 | 差距 |
|------|---------|---------|------|
| AGP | 8.5.2 | 8.7+ | ~1 年 |
| Kotlin | 1.9.24 | 2.0+ | ~1 年 |
| CameraX | 1.3.4 | 1.4.x | ~6 月 |
| Material | 1.12.0 | 1.12.0 | 最新 |
| Robolectric | 4.13 | 4.14 | ~3 月 |
| mockito-inline | 5.2.0 | 5.3+ | 已弃用，应用 mockito-core |

### 1.4 构建配置问题 — Medium

- `isMinifyEnabled = false` — Release 构建未启用混淆/压缩
- `abiFilters += "arm64-v8a"` — 仅支持 64 位，排除了 32 位设备
- `versionCode = 1, versionName = "0.1.0"` — 尚未正式版本化

### 1.5 提交质量 — Low

最近 50 个 commit 中:
- `chore: 更新 X 个文件` 占比过高 (~40%) — 无法从 commit message 理解改动意图
- 大量 orchestration 文档 commit 而非代码 commit
- 缺少 conventional commit 的 scope 标记

---

## 二、并发安全问题 (Critical/High)

### 2.1 [Critical] 孤儿 CoroutineScope — CaptureRecordingSessionProcessor

**文件**: `core/session/.../CaptureRecordingSessionProcessor.kt:92,258`

`startRecordingWatchdog` 和 `handleShotStarted` 中使用 `CoroutineScope(Dispatchers.Default + SupervisorJob())` 创建独立于 session scope 的协程。Session 关闭后这些协程继续运行，可能导致:
- Watchdog 在新 session 上误触发
- 录制计时器更新已废弃的状态
- 内存泄漏

**修复**: 替换为构造函数传入的 `scope.launch`。

### 2.2 [Critical] FrameRingBuffer 无线程安全保护

**文件**: `core/media/.../FrameRingBuffer.kt:20-88`

`frames` (mutableListOf) 在 ImageAnalysis 线程上调用 `append()`，在快门线程上调用 `select()` 和 `snapshot()`。无任何同步机制。

**后果**: `ConcurrentModificationException` 崩溃，或 Live Photo 丢失帧。

### 2.3 [High] CameraXLivePreviewFrameSource buffer 竞态

**文件**: `app/.../CameraXLivePreviewFrameSource.kt:20,74-98`

`buffer` 字段在 `synchronized` 块外部被读写。`stop()` 设置 `buffer = null` 和 `appendCapturedFrame()` 读取 `buffer` 可能并发执行。

### 2.4 [High] CameraXCaptureAdapter 多字段无锁访问

**文件**: `app/.../CameraXCaptureAdapter.kt:1248-1274`

约 20 个可变字段通过 `withContext(Dispatchers.Main.immediate)` 访问，但 `observeForever` 的 LiveData Observer 在任意线程回调，直接修改 `cameraProvider`、`boundCamera` 等字段。

### 2.5 [High] DefaultCameraSession StateFlow 非原子更新

**文件**: `core/session/.../DefaultCameraSession.kt:1285-1363`

`updateState()` 读-改-写序列不是原子的。`PreviewRecoverySessionProcessor` 中多处直接写 `state.value` 绕过 Channel 串行化保护。

### 2.6 [Medium] emit/tryEmit 混用导致事件丢失风险

**文件**: `app/.../CameraXCaptureAdapter.kt:1274,1577,2449`

`_events` 是 `SharedFlow(extraBufferCapacity=16)`。`release()` 和 `handleCameraState` 中使用 `tryEmit()`，缓冲区满时 `ShotFailed`、`RuntimeIssue` 等关键事件可能被静默丢弃。

---

## 三、资源生命周期问题 (Critical/High)

### 3.1 [Critical] ImageProxy 在异常路径未关闭

**文件**: `app/.../CameraXLivePreviewFrameSource.kt:74-98`

`onAnalyzeFrame` 中，`_isActive` 为 false 或 `buffer` 为 null 时直接 return，不关闭 ImageProxy。异常路径同样未关闭。CameraX ImageReader 缓冲池 (4-6 个) 耗尽后预览冻结。

### 3.2 [High] Bitmap 在 ML Kit 同步异常时未 recycle

**文件**: `app/.../MlKitSelfiePreviewSceneMaskSource.kt:125-169`

`segmenter.process()` 如果同步抛出异常 (而非返回失败 Task)，`scaledBitmap` 永远不会被 recycle。

### 3.3 [High] PreviewAnalysisFanout 只捕获 Exception 不捕获 Error

**文件**: `app/.../PreviewAnalysisFanout.kt:14-31`

OOM 是 Error 不是 Exception。如果 `livePreviewConsumer` 触发 OOM，`imageProxy.close()` 不会执行。

### 3.4 [High] adapterScope 从未 cancel

**文件**: `app/.../CameraXCaptureAdapter.kt:1247`

`release()` 中从未调用 `adapterScope.cancel()`。postprocess 协程在 adapter 释放后继续运行，可能访问已释放的资源。

### 3.5 [Medium] FrameRingBuffer.maxBytes 字段从未被检查

**文件**: `core/media/.../FrameRingBuffer.kt:75-87`

`FrameBufferPolicy.maxBytes` (128MB) 定义了但 `evict()` 只检查 `maxFrames` 和 `retentionWindowMillis`，不检查字节数。

---

## 四、状态机正确性问题 (High/Medium)

### 4.1 [High] 比较-after-变异导致 UI 消息丢失

**文件**: `core/session/.../DefaultCameraSession.kt:959-978`

`handleDeviceCapabilitiesUpdated` 中，第 959 行将 `sessionStillCaptureResolutionPreset` 更新为 `clampedResolutionPreset`，第 970 行再比较两者是否相等 — 永远为 false。用户永远看不到分辨率调整提示。

**这是一个确定性的逻辑 bug。**

### 4.2 [Medium] handleSettingsUpdated 重复调用 onEnter()

**文件**: `core/session/.../DefaultCameraSession.kt:525-527`

RUNNING 状态下设置更新时重复调用 `currentController.onEnter()`，违反控制器生命周期契约。

### 4.3 [Medium] Preview Surface Lost 恢复可能静默失败

**文件**: `core/session/.../PreviewRecoverySessionProcessor.kt:226-238`

`handlePreviewSurfaceLost` 调用 `requestPreviewBinding`，但后者有多个 guard 条件。任何不满足都会导致 previewStatus 永久卡在 `SURFACE_LOST`，取景器黑屏。

### 4.4 [Medium] submitCaptureStrategy 未校验 session 生命周期

**文件**: `core/session/.../CaptureRecordingSessionProcessor.kt:520-578`

Session 已 STOPPED 时仍可能执行拍摄指令。

### 4.5 [Medium] 权限撤销时未取消录制看门狗

**文件**: `core/session/.../DefaultCameraSession.kt:1034-1039`

Camera 权限被撤销时，录制看门狗协程未被取消，持续运行直到 session shutdown。

---

## 五、数据流与逻辑缺陷 (High/Medium)

### 5.1 [High] Float 精确相等比较在 zoom 操作中脆弱

**文件**: `core/device/.../DeviceContracts.kt:386-388`

`normalizedZoomRatioValue` 通过 `String.format("%.1f").toFloat()` 归一化，后续用 `==` 精确比较 Float。在边界值 (如 10.0) 附近可能因 IEEE 754 精度差异导致比较失败。

### 5.2 [Medium] OCWM payload 长度 Long->Int 未校验溢出

**文件**: `core/media/.../OcwmJpegContainer.kt:151`

`totalPayloadLength.toLong().toInt()` 在超过 Int.MAX_VALUE 时溢出。

### 5.3 [Medium] SceneMaskContracts.parseTransform 的 toInt() 未防御超大数字

**文件**: `core/media/.../SceneMaskContracts.kt:93-97`

正则 `\d+` 可匹配任意长度数字串，`toInt()` 对超大数字抛 NumberFormatException。

### 5.4 [Medium] clampStillCaptureResolutionPreset 空 available 时 fallback 不在集合中

**文件**: `core/session/.../DefaultCameraSession.kt:1516`

`available` 为空集时 fallback 到 `ordered.last()` (SMALL_2MP)，但 SMALL_2MP 不在 available 中。

---

## 六、测试质量问题

### 6.1 [High] Thread.sleep(3100) 导致测试不稳定

**文件**: `core/session/.../DefaultCameraSessionTest.kt:1036`

CI 环境中极易 flaky。应使用 `advanceTimeBy` 或注入虚拟时钟。

### 6.2 [Medium] sun.misc.Unsafe 实例化 PreviewView

**文件**: `app/.../CameraSessionCoordinatorTest.kt:1054-1065`

依赖 JVM 内部 API，15 个测试方法重复使用。JDK 版本兼容性风险。

### 6.3 [Medium] 8 处 defaultSessionState 重复定义

8 个测试文件中分别定义了高度相似的 `defaultSessionState()` 工厂方法。应提取到共享测试工具。

### 6.4 [Medium] 415+ 条断言缺少失败消息

测试失败时只能看到 "expected true but was false"，无法快速定位原因。

### 6.5 [Low] FakeCameraSession 重复定义

两个文件中有几乎相同的 Fake 实现。

### 6.6 [Low] assertTrue(x is T) 应改为 assertIs

多处使用 `assertTrue(x is Type)` 而非 `assertIs<Type>(x)`，后者失败时打印实际类型。

---

## 七、Android 生命周期问题

### 7.1 [High] 无 ViewModel — 进程死亡丢失全部状态

项目不使用 ViewModel。所有状态保存在 Activity 字段和 AppContainer 单例中。进程死亡后:
- `panelRouter` (面板路由) 丢失
- `selectedDevLogTab` 丢失
- `latestSessionState` 丢失
- 用户回到 app 看到的是默认状态而非之前的面板/模式

### 7.2 [High] 无 onSaveInstanceState/onRestoreInstanceState

Activity 没有实现状态保存/恢复。配合 `configChanges="orientation|screenSize|keyboardHidden"` 和 `screenOrientation="portrait"`，配置变更被手动处理，但进程死亡恢复完全缺失。

### 7.3 [High] render() 中调用 applyLocale() 可触发 Activity 重建

`MainActivity.render()` 第 254 行调用 `applyLocale()`，其中 `AppCompatDelegate.setApplicationLocales()` 会触发 Activity 重建。但重建时无状态保存，用户当前面板/设置页面丢失。

### 7.4 [Medium] SessionIntent.Shutdown 从未被 dispatch

`onStop()` dispatch `PreviewHostDetached` 但不 dispatch `Shutdown`。`handleShutdown()` 方法存在但从未被 Activity 生命周期调用。

### 7.5 [Medium] requestCameraPermissionIfNeeded() 首次安装逻辑缺陷

`shouldShowRequestPermissionRationale` 在首次安装时返回 false + 权限未授予 → 进入"永久拒绝"分支，而非请求权限。

### 7.6 [Medium] onBackPressed() 已弃用

`onBackPressed()` 在 API 33 已弃用，应使用 `OnBackPressedCallback`。

### 7.7 [Medium] VideoFrameExtractor.extract() 在主线程同步执行

`render()` 中调用 `VideoFrameExtractor.extract()`，该方法执行文件 I/O 和 Bitmap 操作，在主线程同步运行。

### 7.8 [Medium] configChanges 缺少 uiMode

Manifest 的 `configChanges` 不包含 `uiMode`，暗色模式切换时行为未定义。

### 7.9 [Low] 无多窗口模式处理

未声明 `resizeableActivity`，无 `onMultiWindowModeChanged` 覆写。分屏模式下相机预览行为未定义。

---

## 八、代码健康问题

### 7.1 被禁用的功能 (TODO)

| 文件 | 行号 | 内容 | 重要性 |
|------|------|------|--------|
| DefaultCameraSession.kt | 769 | `TODO: re-enable when StillCaptureQualityPreference is merged` | 高 |
| DefaultCameraSession.kt | 803 | `TODO: re-enable when StillCaptureResolutionPreset is merged` | 高 |
| MainActivityActionBinder.kt | 418 | `TODO: mode track assist switch via horizontal scroll` | 中 |

### 7.2 God Class 问题 (已有报告确认)

| 文件 | 行数 | 职责 |
|------|------|------|
| CameraXCaptureAdapter.kt | 3,134 | CameraX 适配、拍照/录像、设备能力、provider 管理 |
| SessionUiRenderModel.kt | 2,289 | 所有 UI 渲染模型构建 |
| DefaultCameraSession.kt | 1,604 | 37 类 SessionIntent 处理 |
| MainActivity.kt | ~1,200 | ViewBinder + StateRenderer + PanelRouter + ActionBinder + GestureBridge |

### 7.3 测试中的重复模式

| 模式 | 出现次数 | 建议 |
|------|---------|------|
| `defaultSessionState()` 工厂 | 8 个文件 | 提取到 TestSessionStateFactory |
| `FakeCameraSession` 定义 | 2 个文件 | 提取到共享测试工具 |
| `256L * 1024 * 1024` 常量 | 6 个文件 | 定义 TEST_MEMORY_256MB |
| `allocateInstance(PreviewView)` | 13 处 | 提取到 @Before |

---

## 八、优先级排序汇总

### P0 — 确定性 Bug / Critical 风险 (立即修复)

1. **比较-after-变异** — DefaultCameraSession.kt:959 — UI 消息永远不显示分辨率调整
2. **孤儿 CoroutineScope** — CaptureRecordingSessionProcessor.kt:92,258 — Session 关闭后协程泄漏
3. **FrameRingBuffer 无锁** — FrameRingBuffer.kt — 并发访问崩溃
4. **ImageProxy 未关闭** — CameraXLivePreviewFrameSource.kt:74 — 预览冻结
5. **无进程死亡恢复** — MainActivity — 无 ViewModel、无 onSaveInstanceState，进程死亡丢失全部状态

### P1 — High 风险 (本周修复)

5. **无 ViewModel / 无状态保存** — MainActivity — 进程死亡丢失全部 UI 状态
6. **render() 中 applyLocale 触发重建** — MainActivity:254 — Activity 重建无状态保存
7. **Float 精确比较** — DeviceContracts.kt:387 — Zoom 操作脆弱
8. **buffer 竞态** — CameraXLivePreviewFrameSource.kt — NPE 风险
9. **adapter 多字段无锁** — CameraXCaptureAdapter.kt — 状态不一致
10. **StateFlow 非原子更新** — DefaultCameraSession.kt — 状态覆盖
11. **Bitmap 未 recycle** — MlKitSelfiePreviewSceneMaskSource.kt — OOM 风险
12. **PreviewAnalysisFanout 不捕获 Error** — PreviewAnalysisFanout.kt — ImageProxy 泄漏
13. **adapterScope 未 cancel** — CameraXCaptureAdapter.kt — 资源泄漏
14. **Thread.sleep(3100)** — DefaultCameraSessionTest.kt — CI 不稳定

### P2 — Medium 风险 (本月修复)

15. **Preview Surface Lost 卡死** — PreviewRecoverySessionProcessor.kt
16. **onEnter() 重复调用** — DefaultCameraSession.kt
17. **submitCaptureStrategy 无 lifecycle 检查** — CaptureRecordingSessionProcessor.kt
18. **权限撤销未取消看门狗** — DefaultCameraSession.kt
19. **emit/tryEmit 混用** — CameraXCaptureAdapter.kt
20. **OCWM 溢出** — OcwmJpegContainer.kt
21. **请求权限首次安装逻辑缺陷** — MainActivity
22. **onBackPressed() 弃用** — MainActivity
23. **VideoFrameExtractor 主线程同步** — MainActivity
24. **Shutdown 从未 dispatch** — MainActivity
25. **configChanges 缺少 uiMode** — AndroidManifest
26. **依赖版本升级** — AGP, Kotlin, CameraX
27. **Git 仓库清理** — 2.9GB teotis-camera, 31MB APK

### P3 — Low 风险 / 改进项 (排期)

28. Worktree 批量清理
29. 测试断言消息补充
30. 重复测试代码提取
31. 提交信息规范化
32. .idea/.tmp 从 git 移除
33. onBackPressed 弃用迁移
34. MediaActionSound 按需创建

---

## 九、与已有审计报告的差异

本次审计与 2026-05-27 的 `deep-audit-optimization-orchestration` 互补:

| 维度 | 已有审计 | 本次审计 |
|------|---------|---------|
| God Class / 行数统计 | 详细 | 未重复 |
| 模块依赖图 | 详细 | 未重复 |
| 性能瓶颈估算 | 有 (百分比) | 未做估算 |
| **并发安全** | 未覆盖 | **2 Critical + 3 High** |
| **资源泄漏** | 未覆盖 | **1 Critical + 3 High** |
| **逻辑 bug** | 未覆盖 | **1 确定性 bug + 1 High** |
| **状态机正确性** | 未覆盖 | **1 High + 4 Medium** |
| **Android 生命周期** | 未覆盖 | **3 High + 5 Medium** |
| **测试质量细节** | 覆盖率百分比 | **具体问题清单** |
| **Git 仓库健康** | 未覆盖 | **6.74GB 膨胀** |
| **依赖版本** | 提及可升级 | **具体版本对比** |

---

*本报告为纯分析，未修改任何源代码。*
*审计方法: 6 个并行专项 agent (并发安全、资源生命周期、状态机正确性、数据流逻辑、测试质量) + 宏观手动检查。*
