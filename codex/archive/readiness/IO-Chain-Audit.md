# IO Chain Audit

> 审查日期: 2026-05-22
> 审查范围: 2.0 四条金路径输入输出链路通畅性
> 审查方法: 测试套件验证 + 代码逐链路追踪

## Summary

- **Result**: Risk
- **Blocking IO risks**: 1 (P0 — 已有修复方案)
- **Non-blocking risks**: 5 (3 P1, 2 P2)
- **测试基线**: Session 98/100 通过 (2 个 humanistic mode 样式循环期望值未同步), Device/Media/App 全部通过

四条金路径的主链路均完整连通，状态机转换闭合，无静默断链。风险集中在**后处理失败的静默吞没**和**缩略图反馈优先级边界条件**。

## Golden Path Matrix

| Path | Input | Session | Device | Media | Feedback | Result |
| --- | --- | --- | --- | --- | --- | --- |
| Photo capture | ShutterPressed → ModeIntent.ShutterPressed | submitCaptureStrategy: IDLE→REQUESTED→SAVING→COMPLETED | DeviceShotRequestTranslator.translate → CameraX ImageCapture | CompositeMediaPostProcessor (MultiFrameMerge + PipelineMetadata) → emitShotCompleted | pendingCaptureFeedback → latestThumbnailSource → ThumbnailRenderCommand.Load | **Pass** (链路完整) |
| Video record | ShutterPressed → SubmitCapture(VideoRecording) → 再次 ShutterPressed → StopActiveCapture | REQUESTING→RECORDING→STOPPING→IDLE, 看门狗 10s/15s | translate(VIDEO_RECORDING) → VideoCapture+Recorder | sidecar/subtitle write → emitShotCompleted | latestThumbnailSource (USE_SAVED_MEDIA) → Load | **Pass** (链路完整) |
| Mode switch + capture | SwitchMode → ShutterPressed | 阻塞检查: activeShot!=null 或 countdownInProgress → 拒绝切换; 否则 currentController 切换 | 新 ModeController 的 CaptureStrategy 翻译为 DeviceShotRequest | 后处理链根据 shot metadata 选择处理器 | 同 Photo capture 反馈路径 | **Pass** (链路完整) |
| Recovery + capture | PreviewHostDetached → Attached → ShutterPressed | pendingPreviewHostRecoveryReason → requestPreviewBinding(isRecovery=true) → RECOVERING → ACTIVE | 恢复绑定后正常翻译 | 正常后处理 | 正常反馈 | **Pass** (链路完整) |

## Chain Diagrams

### Photo Capture

```text
Shutter tap
  → SessionIntent.ShutterPressed
  → DefaultCameraSession.process() → handleModeIntent(ModeIntent.ShutterPressed)
  → ModeController.handle() → ModeSignal.SubmitCapture(strategy)
  → submitCaptureStrategy()
    → ShotExecutor.plan(strategy) → ShotPlan(ShotRequest + MediaSaveTask)
    → updateState(captureStatus=REQUESTED, activeShot=plan.request)
    → _effects.emit(SessionEffect.ExecuteShot(plan))
  → DeviceShotRequestTranslator.translate(plan) → DeviceShotRequest
  → CameraXCaptureAdapter.executeShot() → captureStillImage()
    → CameraX ImageCapture.takePicture()
    → PhotoCaptureOutcome.Success
  → emitShotCompleted()
    → shotExecutor.resultFor() → ShotResult
    → mediaPostProcessor.process(rawResult) → processedResult
    → _events.emit(DeviceEvent.ShotCompleted(result))
  → CameraSessionCoordinator → SessionIntent.ShotCompleted(result)
  → handleShotCompleted()
    → updateState(captureStatus=COMPLETED, latestThumbnailSource, pendingCaptureFeedback=null)
  → UI: nextThumbnailRenderCommand() → Load(uri) → previewThumbnail.setImageURI()
```

### Video Record

```text
Shutter tap (start)
  → SessionIntent.ShutterPressed → ModeIntent.ShutterPressed
  → ModeController → ModeSignal.SubmitCapture(VideoRecording strategy)
  → submitCaptureStrategy()
    → updateState(recordingStatus=REQUESTING, activeShot)
    → startRecordingWatchdog(REQUESTING, 10s)
    → SessionEffect.ExecuteShot(plan)
  → CameraXCaptureAdapter → startVideoRecording()
    → VideoCapture.prepareRecording() → activeRecording.start()
  → DeviceEvent.ShotStarted → SessionIntent.ShotStarted
  → handleShotStarted() → updateState(recordingStatus=RECORDING)
    → startRecordingWatchdog(RECORDING, 15s)

Shutter tap (stop)
  → SessionIntent.ShutterPressed → ModeIntent.ShutterPressed
  → ModeController → ModeSignal.StopActiveCapture
  → ShotExecutor.requireStoppableShot() → validate
  → updateState(recordingStatus=STOPPING)
  → SessionEffect.StopActiveShot(shotId)
  → CameraXCaptureAdapter → activeRecording.stop()
  → DeviceEvent.ShotCompleted → SessionIntent.ShotCompleted
  → handleShotCompleted() → updateState(recordingStatus=IDLE)
```

### Mode Switch + Capture

```text
Mode tap
  → SessionIntent.SwitchMode(modeId)
  → handleSwitchMode()
    → 检查: countdownInProgress() → 阻塞
    → 检查: activeShot != null → 阻塞
    → currentController.onExit()
    → currentController = registry.controllerFor(modeId)
    → currentController.onEnter()
    → updateState(activeMode=modeId, modeSnapshot)

Shutter tap
  → 新 ModeController.handle(ModeIntent.ShutterPressed)
  → ModeSignal.SubmitCapture(新模式的 CaptureStrategy)
  → 后续同 Photo Capture 链路
```

### Recovery + Capture

```text
Preview host detach
  → SessionIntent.PreviewHostDetached
  → handlePreviewHostDetached()
    → pendingPreviewHostRecoveryReason = reason
    → requestPreviewUnbind(clearHost=true)

Preview host reattach
  → SessionIntent.PreviewHostAttached
  → handlePreviewHostAttached()
    → requestPendingPreviewHostRecovery()
      → 检查: lifecycle=RUNNING, hostAvailable, cameraGranted, !RECORDING
      → requestPreviewBinding(isRecovery=true)
  → PreviewBindingStarted → PreviewStatus.RECOVERING
  → PreviewFirstFrameAvailable
    → handlePreviewFirstFrameAvailable() → PreviewStatus.ACTIVE, lastError=null

Shutter tap
  → previewStatus=ACTIVE → capture proceeds normally
```

## Breakpoints

| Priority | Path | File/Test | Evidence | Failure mode | Fix direction | Retest |
| --- | --- | --- | --- | --- |---| --- |
| P0 | Photo/Video capture | `CameraXCaptureAdapter.kt` :1468-1518 | `emitShotCompleted()` 中 `mediaPostProcessor.process()` 的失败仅记录到 `pipelineNotes`，不产生 `ShotFailed` 事件。Live sidecar 在 scoped storage 下失败曾阻断 `ShotCompleted` 到达。 | Live sidecar 创建失败 → 照片无法保存 → 用户无反馈 | 已在 `2026-05-22-media-output-filter-thumbnail-stability.md` 中修复: sidecar 非致命化 + app-private 存储 | 需真机验证 |
| P1 | Photo capture | `CameraXCaptureAdapter.kt` :1005-1009 | 默认后处理链仅含 `MultiFrameMergePlaceholderPostProcessor` + `PipelineMetadataPostProcessor`。应用层 6 个后处理器通过 `AlgorithmProcessorBridges` 桥接，失败以 `pipelineNotes` 静默记录 | 水印/滤镜/人像渲染失败 → 用户看到"Photo saved"但图片未处理 | 后处理失败应升级为可感知的用户提示或 `ShotFailed` 降级 | 需真机验证 |
| P1 | Photo capture | `MediaPipelineContracts.kt` :1072-1108 | `ShotResult.toTransactionResult()` 的 status 硬编码为 `SUCCESS`，未检测 `pipelineNotes` 中的失败标记 | 事务报告永远成功，即使后处理链部分失败 | `toTransactionResult()` 应检查 `pipelineNotes` 中的 `failed:` 前缀 | 单元测试 |
| P1 | Photo/Video capture | `DefaultCameraSession.kt` :1347-1350 | `handleShotFailed()` 中 `activeShot == null` 或 ID 不匹配时直接 return，仅记录 `shot.failed.duplicate` | 权限撤销等并发场景下 `activeShot` 已被清除，真正失败原因被静默丢弃 | 区分 "duplicate" 和 "orphaned failure"，orphaned 失败仍应记录到 `lastError` | 单元测试 |
| P2 | Photo capture | `DefaultCameraSession.kt` :1274-1276 | `ThumbnailPolicy.KEEP_PREVIEW_FRAME` 时 `thumbnailSource=None`，`latestThumbnailSource` 保留旧值 | 首次拍摄 + KEEP_PREVIEW_FRAME → 缩略图区域空白，用户无视觉反馈 | 首次拍摄时应 fallback 到 preview snapshot 或显示占位符 | 单元测试 |
| P2 | Photo capture | 各 PostProcessor 的 EXIF 恢复逻辑 | EXIF 恢复失败仅返回 warning 字符串，不中断流程 | 照片丢失方向/GPS/时间元数据，用户无提示 | EXIF 恢复失败应记录到 `pipelineNotes` 供诊断 | 单元测试 |
| P2 | Session | `DefaultCameraSessionTest.kt` :1944, :2003 | 2 个 humanistic mode 测试失败: 期望 `portrait` 实际 `vivid`，期望 `chasing-light` 实际 `original` | 测试期望值与代码不同步，不影响链路但影响回归检测 | 更新测试期望值匹配当前样式循环顺序 | 重新运行测试 |

## Needs Multimodal Review

| Item | Why text-only is insufficient | Required artifact |
| --- | --- | --- |
| 水印渲染后缩略图视觉一致性 | 需要对比预览帧缩略图与水印渲染后缩略图的视觉差异，确认无跳变 | 真机截图: 开启水印前后拍摄的缩略图对比 |
| Live Photo sidecar 写入 | scoped storage 下 `MediaStore.Files` 拒绝写入需要真机环境验证 fallback 路径 | 真机日志: Live Photo 拍摄的完整 trace |
| Recovery 后首帧质量 | 文本无法判断恢复后预览是否正常显示 | 真机截图: recovery 前后的预览画面 |
| 视频 sidecar/subtitle 写入路径 | 需要确认 scoped storage 下视频附属文件的写入合法性 | 真机文件系统检查: DCIM/Camera 下的 sidecar 文件 |
| 后处理失败的实际可观测性 | 需要确认用户在 UI 上是否能感知到后处理失败 | 真机操作: 人为触发后处理失败后的 UI 状态截图 |
