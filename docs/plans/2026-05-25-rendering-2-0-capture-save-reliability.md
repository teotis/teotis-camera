# Rendering 2.0 Capture Save Reliability

## Goal

Make rendering/postprocess optional from the perspective of save reliability: when the camera has already captured a JPEG, Color Lab/filter/watermark/frame/selfie postprocess failures must preserve the original saved media and produce explicit degraded diagnostics rather than silent animation-only no-image behavior.

## Context

- User request: approved Rendering 2.0 upgrade item `拍照保存可靠性`.
- Verified facts:
  - `PhotoAlgorithmPostProcessor` already catches editor exceptions in several paths and records `algorithm-render:failed:render-exception`.
  - `AndroidPhotoAlgorithmEditor.apply()` now wraps decode/copy in a try block and returns `ProcessorEditorResult.Failed("render-exception")` for ordinary failures.
  - `CameraXCaptureAdapter.emitShotCompleted(...)` still calls `mediaPostProcessor.process(rawResult)` directly before emitting `DeviceEvent.ShotCompleted`.
  - `CompositeMediaPostProcessor` still has no generic fail-soft wrapper around each optional processor.
  - `PreviewRecoverySessionProcessor` suppresses raw preview feedback for final-output postprocess shots, so a missing `ShotCompleted` can appear to the user as shutter animation without a final image.
- Relevant files:
  - `core/media/src/main/kotlin/com/opencamera/core/media/MediaPostProcessors.kt`
  - `core/media/src/main/kotlin/com/opencamera/core/media/MediaPostProcessorContracts.kt`
  - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessor.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoSelfieMirrorPostProcessor.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/PreviewRecoverySessionProcessor.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`
  - `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- Related plans:
  - `docs/plans/2026-05-25-color-lab-capture-save-regression.md`
  - `docs/plans/2026-05-22-render-recipe-preview-engine-handoff.md`
- Non-goals:
  - Do not fake success for camera capture failure before a JPEG exists.
  - Do not remove raw preview feedback suppression for final-output postprocess shots.
  - Do not hide postprocess failure; report degraded output through pipeline notes and UI/status where the existing render model supports it.
  - Do not implement Color Lab strength changes in this package.

## Implementation Scope

- Add a generic fail-soft boundary for optional media postprocessors.
- Ensure `CameraXCaptureAdapter.emitShotCompleted(...)` preserves and emits `ShotCompleted` with the original `rawResult` if optional postprocess throws.
- Add pipeline notes that identify the failed processor and preserve enough evidence for diagnostics.
- Add tests proving a postprocess exception keeps `outputHandle`, `thumbnailSource`, `outputPath`, and saved-media state intact.

## Steps

1. Inspect current processor order in `CameraXCaptureAdapter` and `AppContainer` so fail-soft behavior does not accidentally skip required metadata processors.
2. Update `CompositeMediaPostProcessor.process(...)` to catch `Throwable` from each processor and continue with the current `ShotResult`, appending a note such as `postprocess:failed:<processor-name>`.
3. Add a second outer guard in `CameraXCaptureAdapter.emitShotCompleted(...)` around `mediaPostProcessor.process(rawResult)` so a composite-level exception still emits a degraded `ShotCompleted` with a note such as `postprocess:failed:composite`.
4. Keep capture-critical failures in existing `emitShotFailure(...)` paths. Only failures after a valid `rawResult` has been built should be treated as optional postprocess degradation.
5. Add or update tests:
   - a `CompositeMediaPostProcessor` unit test with one throwing processor between two successful processors;
   - a `PhotoAlgorithmPostProcessorTest` case for throwing editor remains degraded, not fatal;
   - a session-level test that a filter/Color Lab shot receiving degraded `ShotCompleted` updates `latestThumbnailSource` to `SavedMedia` and clears `activeShot`.
6. Ensure UI/status text uses existing degraded save semantics if present. Do not add new UI wording unless required by tests.

## Acceptance Criteria

- A postprocess exception after image capture no longer prevents `ShotCompleted`.
- The original output handle/path/thumbnail source are preserved when postprocess degrades.
- `latestPipelineNotes` includes a deterministic failure note, not only a logcat line.
- Color Lab/filter shots still suppress raw preview feedback until saved media or explicit failure is available.
- Normal no-effect photo, watermark, frame ratio, selfie mirror, Live still fallback, and video paths do not regress.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Real-Device Smoke

Codex/user should run this after implementation:

1. Install the debug APK on the current target device.
2. Open Photo mode, activate Color Lab, drag to a non-center point.
3. Tap shutter once.
4. Confirm one saved image appears in thumbnail/gallery.
5. Confirm diagnostics or pipeline notes include either `algorithm-render:applied:*` or explicit degraded notes.

## Risks And Notes

- Full-resolution bitmap processing can still be slow or memory-heavy. This package only prevents silent loss; performance tuning belongs to later rendering budget work.
- Catching `Throwable` is acceptable only at optional postprocess boundaries after a saved/captured result exists. Do not use it to mask device capture failures.
- If processor names are unstable in tests, introduce a small `diagnosticName` contract rather than asserting Kotlin reflection output.

