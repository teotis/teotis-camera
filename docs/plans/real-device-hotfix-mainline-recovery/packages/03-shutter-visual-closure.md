# Package 03 — Shutter Visual Closure

## Package ID
`03-shutter-visual-closure`

## Goal
Close the remaining Package 01 gap: the active shutter UI must visibly stop loading at `DATA_RECEIVED`, while remaining disabled until `ShotCompleted`.

## Context
- Previous audit found `DATA_RECEIVED` on main, but `CockpitSurfaceRenderer.renderShutter()` still uses XML selector backgrounds and does not wire `ShutterVisualDrawable`.
- Previous Package 01 documented a CameraX limitation: with `takePicture(OutputFileOptions, OnImageSavedCallback)`, `DataReceived` can honestly mean saved-file-available-before-postprocess, not raw `ImageProxy` receipt.
- This package should not redesign media capture. It should make active UI behavior match the honest state contract.

## Implementation Scope
- Make the active shutter rendering path observe capture status: `SAVING` shows loading, `DATA_RECEIVED` stops loading, `COMPLETED/IDLE` ready.
- Keep shutter disabled while a photo shot is active or `DATA_RECEIVED`.
- Either wire `ShutterVisualDrawable` into `CockpitSurfaceRenderer.renderShutter()` or implement an equivalent tested active visual state path.
- Add focused tests for render model/visual mapping.
- Update only `status/03-shutter-visual-closure.md` with fresh evidence.

## Acceptance Criteria
- [ ] Active UI path maps `CaptureStatus.SAVING` to a loading visual.
- [ ] Active UI path maps `CaptureStatus.DATA_RECEIVED` to a non-loading visual.
- [ ] Shutter remains disabled until `ShotCompleted` clears the active photo shot.
- [ ] `CaptureStatus.SAVING -> DATA_RECEIVED -> COMPLETED` remains covered by session tests.
- [ ] Video shutter behavior is unchanged.
- [ ] The package clearly states whether it uses `ShutterVisualDrawable` or an equivalent renderer path.

## Allowed Paths
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/ShutterVisualDrawable.kt`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessorTest.kt`
- `docs/plans/real-device-hotfix-mainline-recovery/status/03-shutter-visual-closure.md`

## Forbidden Paths
- `feature/mode-*/**`
- `core/effect/**`
- `core/settings/**`
- `app/src/main/java/com/opencamera/app/gesture/**`
- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `docs/plans/real-device-hotfix-mainline-recovery/INDEX.md`
- Other packages' status files.

## Dependencies
- Depends on: none

## Parallel Safety
- caution
- Reason: isolated from 01/02, but app render tests may expose pre-existing failures that must be classified carefully.

## Verification Commands
```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests '*Shutter*'
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence Pack
- [ ] Worktree path and branch.
- [ ] Shutter visual strategy chosen.
- [ ] Git diff stat and changed files.
- [ ] Verification command results and any pre-existing failure classification.
- [ ] Commit hash / PR link.
- [ ] Unresolved risks.
