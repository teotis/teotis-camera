# 并行任务 D：输入输出链路通畅性审查

> 非多模态任务。验证从输入意图到媒体输出的链路完整、可追踪、无静默失败。保存图视觉质量交给多模态主控，但文件、metadata、测试和日志可由本任务审查。

## 1. 目标

证明 2.0 的四条金路径通畅：

1. Photo capture E2E
2. Video record E2E
3. Mode switch + capture E2E
4. Recovery 后再次 capture E2E

每条路径都要覆盖：

```text
Input -> Session Kernel -> Device Adapter -> Media Pipeline -> Feedback
```

## 2. 输入

必读：

- `codex/documentation.md`
- `docs/plans/2026-05-22-media-output-filter-thumbnail-stability.md`
- `docs/plans/2026-05-22-watermarked-thumbnail-first-feedback.md`
- `docs/plans/2026-05-22-zero-latency-thumbnail-feedback.md`
- `codex/capability_kernel_v2/03_capture_graph_and_algorithm_pipeline.md`
- `codex/capability_kernel_v2/04_live_photo_and_temporal_media.md`

重点代码：

- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceShotRequestTranslator.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/ShotExecutor.kt`（若存在）
- `core/media/src/main/kotlin/com/opencamera/core/media/MediaSaveTransaction.kt`（若存在）
- `app/src/main/java/com/opencamera/app/ThumbnailRenderCommand.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
- `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
- `app/src/main/java/com/opencamera/app/camera/VideoWatermarkSubtitlePostProcessor.kt`
- `app/src/main/java/com/opencamera/app/camera/AlgorithmProcessorBridges.kt`

相关测试：

- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `core/device/src/test/kotlin/com/opencamera/core/device/DefaultDeviceShotRequestTranslatorTest.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/ShotExecutorTest.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/MediaSaveTransactionTest.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/PipelineNotesOrderTest.kt`
- `app/src/test/java/com/opencamera/app/ThumbnailRenderCommandTest.kt`
- `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapter*Test.kt`
- `app/src/test/java/com/opencamera/app/camera/Photo*PostProcessorTest.kt`
- `app/src/test/java/com/opencamera/app/camera/VideoWatermarkSubtitlePostProcessorTest.kt`

## 3. 必测链路

### 3.1 Photo capture E2E

文本时序模板：

```text
Shutter tap
-> SessionIntent.Capture / equivalent
-> session state: active shot / saving
-> device shot request translated
-> CameraX image capture or fake test adapter
-> media pipeline postprocessors
-> saved media result
-> ThumbnailRenderCommand chooses official result
-> UI feedback
```

断链风险：

- `pendingCaptureFeedback` 早于水印/滤镜后处理且最终缩略图跳变。
- filter/watermark metadata 已写入，但 postprocessor 未执行。
- Live sidecar 或 video sidecar 写入 scoped storage 不合规。
- 保存失败只进日志，不回到 session/UI feedback。

### 3.2 Video record E2E

检查：

- start/stop intent 和 recording state 是否闭合。
- quality/fps/audio profile 是否走 device selection 或 explicit degraded。
- video output 和 sidecar/subtitle 写入路径是否合法。
- thumbnail/record saved feedback 是否不被 preview snapshot 覆盖。

### 3.3 Mode switch + capture E2E

检查：

- mode tap 后 `ModeId`、capture policy、metadata、postprocess contributors 是否同步。
- Pro/Night/Portrait/Document/Humanistic 的关键差异是否进入 shot plan 或 explicit unsupported/degraded。

### 3.4 Recovery 后再次 capture E2E

检查：

- `PreviewHostDetached -> Attached` 或 permission restored 后，recovery bind 之后是否能重新 capture。
- 首帧 stall 或 recovery failure 是否阻止用户在不可用状态下拍摄。
- diagnostics 是否能解释失败。

## 4. 输出格式

生成 `IO-Chain-Audit.md`：

```markdown
# IO Chain Audit

## Summary

- Result: Pass / Risk / Fail
- Blocking IO risks:

## Golden Path Matrix

| Path | Input | Session | Device | Media | Feedback | Result |
| --- | --- | --- | --- | --- | --- | --- |

## Chain Diagrams

### Photo Capture
```text
Shutter tap
-> session capture intent
-> active shot state/effect
-> device shot request
-> media postprocess/save
-> thumbnail/result feedback
```

## Breakpoints

| Priority | Path | File/Test | Evidence | Failure mode | Fix direction | Retest |
| --- | --- | --- | --- | --- | --- | --- |

## Needs Multimodal Review

| Item | Why text-only is insufficient | Required artifact |
| --- | --- | --- |
```

## 5. 验证命令

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.ThumbnailRenderCommandTest --tests 'com.opencamera.app.camera.*'
```

如果 `rtk`/Gradle 对通配测试过滤不支持，改为列出与发现相关的最小测试集。

## 6. P0/P1 判定规则

- P0：拍照/录像无法产出可打开媒体，或保存失败无可恢复反馈。
- P0：权限恢复后主路径不可用且无明确错误状态。
- P1：媒体可保存但缩略图/后处理/metadata 明显不一致，导致用户误判结果。
- P1：模式切换后拍摄结果仍使用旧模式策略且用户无提示。
- P2：链路可用但测试不足、日志不足、性能/反馈不够顺滑。

## 7. 边界

- 不判断成片是否“好看”；只判断文件、metadata、链路、反馈是否一致。
- 不修改保存路径或 postprocessor。
- 不把本地 fake test 成功等同真机通过；真机项标 `需真机验证`。
