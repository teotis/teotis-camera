# Package 01 — Shutter Data Boundary

## Package ID
`01-shutter-data-boundary`

## Goal
Make the photo shutter loading animation stop when bottom-layer photo data is actually available, not when CameraX reports the file has already been saved and post-save work has completed.

## Context
- User issue: the shutter loading animation lasts too long; it should only last until the camera receives complete underlying data.
- Current validation finding: `DATA_RECEIVED` exists on `main`, but `DeviceEvent.DataReceived` is emitted after `PhotoCaptureOutcome.Success`, whose still-photo path is driven by `ImageCapture.OnImageSavedCallback.onImageSaved()`. That is a saved-file boundary, not a raw data boundary.
- Current validation finding: `ShutterVisualDrawable` exists but is not clearly connected to the active shutter rendering path; `CockpitSurfaceRenderer.renderShutter()` still selects XML backgrounds.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
  - `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
  - `app/src/main/java/com/opencamera/app/ShutterVisualDrawable.kt`
- Non-goals:
  - Do not redesign the entire media pipeline.
  - Do not change video recording behavior except to preserve it.
  - Do not alter watermark or zoom behavior.

## Implementation Scope
- Move or add the `DataReceived` event to a true bottom-layer still-data boundary.
- If the current `ImageCapture.takePicture(OutputFileOptions, OnImageSavedCallback)` API cannot expose that boundary, use a scope-contained CameraX path or callback that does. If this is not possible without redesign, document the blocker and implement a correctly named fallback, not a misleading `DATA_RECEIVED`.
- Ensure shutter visual rendering actually observes `SAVING -> DATA_RECEIVED` and stops the loading animation while keeping capture disabled until completion.
- Add tests for `DataReceived` state transitions and render-model/visual-state mapping.

## Acceptance Criteria
- [ ] `DeviceEvent.DataReceived` is emitted before saved-file completion for normal still capture, or the implementation explicitly documents why CameraX cannot provide that boundary and uses honest naming/semantics.
- [ ] `CaptureStatus.SAVING -> DATA_RECEIVED -> COMPLETED` is covered by unit tests.
- [ ] The shutter loading visual maps `SAVING` to loading and `DATA_RECEIVED` to non-loading while the shutter remains disabled until `ShotCompleted`.
- [ ] Failed captures do not leave the shutter in `DATA_RECEIVED` or loading state.
- [ ] Video recording status is unchanged.
- [ ] Existing still capture saves photos normally.

## Allowed Paths
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/ShutterVisualDrawable.kt`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
- Focused tests under `app/src/test/**` and `core/session/src/test/**`

## Forbidden Paths
- `feature/mode-*/**`
- `app/src/main/java/com/opencamera/app/gesture/**`
- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
- `core/settings/**`

## Dependencies
- Depends on: none

## Parallel Safety
- caution
- Reason: mostly isolated, but `CockpitSurfaceRenderer.kt` can conflict with package 03 if both edit nearby rendering code.

## Verification Commands
```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests '*Shutter*'
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence Pack
- [ ] Worktree path and branch.
- [ ] Exact data boundary chosen and why.
- [ ] Git diff stat and changed files.
- [ ] Tests proving `DATA_RECEIVED` is before completion and stops loading visual.
- [ ] Verification command results.
- [ ] Commit hash / PR link.
- [ ] Unresolved risks.
