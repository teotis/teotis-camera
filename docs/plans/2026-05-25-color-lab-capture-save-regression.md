# Color Lab Capture Save Regression Plan

日期：2026-05-25

## Goal

修复真机反馈：“选择了色彩实验室以后，点击拍照虽然有动画，但没有图片拍摄。”目标是让 Color Lab 活跃时的拍照链路可观测、可恢复、可保存：点击快门必须最终进入 `ShotCompleted` 并更新 saved media，或进入明确的 `ShotFailed`/degraded 状态，不能只播放 shutter 动画后沉默。

## Context

- User request: Color Lab 选中后点击拍照有动画，但没有图片拍摄。
- Verified facts:
  - `MainActivityActionBinder.bindCaptureActions()` 的 shutter click 直接 dispatch `SessionIntent.ShutterPressed`；如果按钮有动画，大概率 View 收到了 click。
  - `PhotoModePlugin.handle(ShutterPressed)` 会构造 `EffectSpec`，其中 `FilterEffect` 来自 `renderStyleColorSpec(...)` 和当前 `photo.colorLabSpec`。
  - `EffectBridge.toMetadataTags(effectSpec)` 会把 `filterProfile` 和 `filterSpec.*` 写入 capture metadata。
  - `PreviewRecoverySessionProcessor.handleCaptureFeedbackSnapshotUpdated()` 会对 Color Lab/filter 等需最终后处理的 shot 抑制 raw preview feedback，等待 saved media。
  - `CameraXCaptureAdapter.captureStillImage()` 先发 `ShotStarted` 和 raw feedback snapshot，再执行 `capture.takePicture(...)`，成功后调用 `emitShotCompleted(...)`。
  - `emitShotCompleted(...)` 会同步执行 `mediaPostProcessor.process(rawResult)`；如果该阶段抛出异常，外层会清理 still capture artifacts 并发 `ShotFailed`。
  - `PhotoAlgorithmPostProcessor` 对带 `filterProfile` 或 `filterSpec.*` 的 JPEG 执行 saved-photo render。当前 `AndroidPhotoAlgorithmEditor.apply()` 在 decode/copy 大图时有潜在真实设备风险：部分 decode/copy 工作发生在主 try 之前，`OutOfMemoryError` 或 bitmap copy 异常可能绕过 `ProcessorEditorResult.Failed` 的降级路径。
- Relevant files:
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/PreviewRecoverySessionProcessor.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
  - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `app/src/main/java/com/opencamera/app/AppContainer.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`
  - `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- Non-goals:
  - 不改变 Session Kernel 的 shot state owner。
  - 不取消 Color Lab/filter shot 的 raw feedback suppression；该策略是为了避免缩略图先显示未后处理图片。
  - 不把真实保存失败改成“假成功”。若只能保留原始 JPEG，必须标记 degraded/failed notes。

## Implementation Scope

- 给 Color Lab active capture 增加端到端回归：`ShutterPressed -> ShotStarted -> filter metadata -> postprocess -> ShotCompleted -> SavedMedia thumbnail`。
- 硬化 `PhotoAlgorithmPostProcessor` 和 `AndroidPhotoAlgorithmEditor`，让滤镜/Color Lab 后处理失败时返回原始保存图并记录 `algorithm-render:failed:*`，而不是让异常逃逸导致图片被清理。
- 在 `CameraXCaptureAdapter.emitShotCompleted(...)` 或 media postprocessor 边界补充保护：后处理异常应转为 degraded saved result，除非是明确的 capture-critical failure。
- 增加可诊断 trace/pipeline notes，区分：
  - shutter dispatch 未发生；
  - `ShotStarted` 未发生；
  - CameraX `takePicture` 失败；
  - postprocess failed but original JPEG saved;
  - `ShotCompleted` 已发但 UI/gallery 未更新。
- 保持最终 output handle 指向可打开的 MediaStore URI 或 file path。

## Steps

1. Reproduce from code-level tests first:
   - Add a fake media postprocessor test around `CameraXCaptureAdapter` if existing seams allow it.
   - Add `PhotoAlgorithmPostProcessorTest` cases where editor throws during decode/copy equivalent, and assert result is degraded rather than fatal.
   - Add or extend `DefaultCameraSessionTest` to prove a Color Lab/filter shot that reports `ShotCompleted` updates `latestThumbnailSource` to `SavedMedia` and clears `activeShot`.

2. Harden postprocess exception boundaries:
   - Wrap decode/copy/read/write work in `AndroidPhotoAlgorithmEditor.apply()` so all ordinary failures return `ProcessorEditorResult.Failed(reason)`.
   - In `PhotoAlgorithmPostProcessor.process()`, catch editor exceptions and add `algorithm-render:failed:<reason>` to the existing `ShotResult`.
   - If broader processors can throw, consider a small fail-soft wrapper in `CompositeMediaPostProcessor` or at `CameraXCaptureAdapter.emitShotCompleted(...)` that preserves the original captured JPEG and appends `postprocess:failed:<processor>`.
   - Do not delete the already captured image for optional render failures.

3. Verify handle and MediaStore behavior:
   - Confirm `createPhotoOutputRequest()` still pre-creates or otherwise retains an editable `contentUri` on Android Q+.
   - Confirm `MediaOutputHandle.renderUriOrNull()` is available for `ThumbnailSource.SavedMedia`.
   - Add regression around CameraX not returning `savedUri` if a suitable test seam already exists.

4. Improve diagnostics:
   - Ensure Color Lab capture metadata includes `filterProfile`, `filterSpec.version`, and enough `filterSpec.*` fields to prove Color Lab was active.
   - Ensure `latestPipelineNotes` contains either `algorithm-render:applied:<profile>` or `algorithm-render:failed:<reason>`.
   - Ensure UI status uses existing degraded save semantics: `Photo saved (degraded)` when postprocess failed but original JPEG remains.

5. Real-device smoke:
   - Install debug APK.
   - Open Photo mode, Color Lab, drag to a visible non-center point.
   - Tap shutter once.
   - Confirm one new image appears in system gallery/thumbnail and trace shows `capture.saved`.
   - Repeat at a Color Lab corner and immediately after reset.

## Acceptance Criteria

- With Color Lab panel active and non-neutral, tapping shutter produces one saved image or a visible `ShotFailed` state with reason.
- A postprocess failure no longer causes silent loss of the already captured JPEG for Color Lab/filter shots.
- Session state after completion has `activeShot == null`, `captureStatus == COMPLETED`, `latestThumbnailSource is SavedMedia`, and `latestCapturePath` set.
- `latestPipelineNotes` include Color Lab/filter render result notes.
- Raw feedback remains suppressed for Color Lab/filter shots until saved media is available.
- No regression to normal photo capture, watermark, frame ratio, Live Photo still fallback, or video recording.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

Optional real-device diagnostics:

```bash
rtk adb logcat -c
rtk adb install -r -d <HOME>/.codex-build/OpenCamera/app/outputs/apk/debug/app-debug.apk
rtk adb logcat -d | rg "OpenCamera|capture|Shot|algorithm-render|postprocess|ImageCapture"
```

## Risks And Notes

- Full-resolution bitmap postprocessing can be expensive on real devices. If memory pressure is confirmed, prefer fail-soft original JPEG preservation first, then optimize rendering memory usage separately.
- This plan intentionally treats Color Lab render as optional postprocess for save reliability. Product quality still requires the perceptual strength plan, but save reliability comes first.
- Codex/user should do final real-device acceptance because only the target device can prove the symptom is gone.

