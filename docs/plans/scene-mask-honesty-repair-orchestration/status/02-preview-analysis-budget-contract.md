# Package Status: 02-preview-analysis-budget-contract

- **Agent**: agent-02-preview-analysis-budget-contract
- **Status**: completed
- **Started**: 2026-05-27T04:30:00Z
- **Completed**: 2026-05-27T04:45:00Z

## Worktree

- Path: /private/tmp/open_camera-orchestration/scene-mask-honesty-repair/02-preview-analysis-budget-contract
- Branch: agent/scene-mask-honesty-repair/02-preview-analysis-budget-contract
- Base commit: 5aaee97
- Commit hash: d8e9b0e

## Changes

- git diff --stat: 151 insertions, 6 deletions
- Changed files:
  - app/src/main/java/com/opencamera/app/camera/MlKitSelfiePreviewSceneMaskSource.kt
  - app/src/test/java/com/opencamera/app/camera/PreviewSceneMaskSourceTest.kt
  - app/src/test/java/com/opencamera/app/camera/PreviewAnalysisFanoutTest.kt

## Verification

- Commands run:
  - `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewSceneMaskSourceTest --tests com.opencamera.app.camera.PreviewAnalysisFanoutTest` — PASS
  - `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest --tests com.opencamera.app.camera.live.LivePreviewFrameSourceTest` — PASS
- Test results: All tests passed (BUILD SUCCESSFUL)

## Evidence

- Acceptance criteria:
  - [x] Preview lifecycle tests prove exactly one `ImageProxy.close()` owner — fanout test `scene mask source does not own close when used through fanout` confirms fanout is sole close owner; `MlKit source does not close ImageProxy in onAnalyzeFrame` confirms source never closes
  - [x] `MlKitSelfiePreviewSceneMaskSource` config is no longer just log text — `targetWidth`/`targetHeight` used for bitmap downscaling before ML Kit; `maxFps` enforced via time-based throttle before expensive conversion
  - [x] Frame throttling happens before heavy conversion when possible — fps throttle check placed before `imageProxyToBitmap()` call
  - [x] Existing Live preview fanout tests still pass — CameraXCaptureAdapterLivePhotoTest and LivePreviewFrameSourceTest both PASS
  - [x] No package changes outside allowed paths — only 3 files modified, all within allowed paths

## ImageProxy Lifecycle Ownership

`PreviewAnalysisFanout` remains the single owner of `ImageProxy.close()`. `MlKitSelfiePreviewSceneMaskSource.onAnalyzeFrame()` does NOT call `close()` on the proxy. `NoOpPreviewSceneMaskSource` does not close. Tests verify this contract.

## Remaining Real-Device Performance Risks

- `downscaleToTarget` creates a new `Bitmap.createScaledBitmap` on the calling thread; on very slow devices this adds latency before ML Kit submission. The maxFps throttle mitigates repeated cost but a single frame still pays the full downscale.
- ML Kit `process()` is async; the `inferenceInFlight` guard prevents queue buildup but the actual inference time is device-dependent.

## Self-Certification

- [x] Only touched allowed paths
- [x] Did not edit forbidden paths
- [x] Did not edit INDEX.md or other status files
- [x] Updated `status/state.tsv` consistently
