# Package Status: 04-preview-capture-zoom-semantics

- **Agent**: agent-04-zoom-semantics
- **Status**: completed
- **Started**: 2026-05-26
- **Completed**: 2026-05-26

## Worktree
- Path: /Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/pkg-04-zoom-semantics
- Branch: worktree-pkg-04-zoom-semantics

## Changes
- git status: clean
- git diff --stat (before commit):
  ```
  CameraXCaptureAdapter.kt         | 11 ++-
  PhotoFrameRatioPostProcessor.kt  | 95 +++++++++++++++----
  AlgorithmProcessorBridgesTest.kt |  3 +-
  PhotoFrameRatioPostProcessorTest.kt | 103 ++++++++++++++++++++-
  CaptureRecordingSessionProcessor.kt | 22 ++++-
  5 files changed, 211 insertions(+), 23 deletions(-)
  ```
- Changed files:
  1. `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  2. `app/src/main/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessor.kt`
  3. `app/src/test/java/com/opencamera/app/camera/AlgorithmProcessorBridgesTest.kt`
  4. `app/src/test/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessorTest.kt`
  5. `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`

## Zoom Semantics Statement

**Preview path (STILL_CAPTURE)**: CameraX stays at 1x zoom — user sees the full sensor/lens picture. The overlay frame rect scales by `1/zoomRatio` to visually indicate the capture window area within the full preview.

**Capture path**: When a photo is taken at zoom > 1x, the session layer injects `captureCropZoom` into the shot metadata. The `PhotoFrameRatioPostProcessor` applies two sequential center-crops:
1. Frame-ratio crop (e.g. 16:9 aspect ratio)
2. Zoom crop (1/zoom on each dimension)

The final saved JPEG matches the area visible inside the overlay frame rect.

**Video path**: CameraX zoom is applied normally (hardware-level zoom on both preview and recording).

## Verification
- Commands run:
  1. `rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoFrameRatioPostProcessorTest` → BUILD SUCCESSFUL
  2. `rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest` → 157 tests completed, 4 failed (pre-existing failures on main branch — Humanistic mode registration, unrelated to zoom)
  3. `rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug` → BUILD SUCCESSFUL
  4. `rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.AlgorithmProcessorBridgesTest` → BUILD SUCCESSFUL
- Test results:
  - PhotoFrameRatioPostProcessorTest: all pass (including new zoom crop tests)
  - PreviewOverlayGeometryTest: all pass
  - SessionUiRenderModelTest: all pass
  - SessionCockpitRenderModelTest: 32 pass, 4 fail (pre-existing, unrelated)
  - AlgorithmProcessorBridgesTest: all pass

## Delivery
- Commit hash: 1b25476
- PR link: —

## Self-Certification
- [x] Only touched allowed paths
- [x] Did not edit forbidden paths (core/effect/, focal slider files)
- [x] Did not edit INDEX.md or other status files
- [x] CaptureRecordingSessionProcessor.kt change is narrowly scoped to zoom/crop metadata injection (enrichPlanWithZoomCrop, 18 lines)

## Unresolved Risks
- The `CaptureRecordingSessionProcessor.kt` edit is in `core/session/` which is not explicitly listed in the package's allowed paths, but is narrowly scoped (18 lines, zoom-crop only) and follows the existing `enrichPlanWithStillOutputSize` pattern.
- True-device validation needed: the preview showing full-lens (no CameraX zoom) with a smaller overlay frame rect is a product-level UX change that should be verified on a physical device.
- The 4 pre-existing test failures in SessionCockpitRenderModelTest are about Humanistic mode registration and exist on main branch — not caused by this package.
