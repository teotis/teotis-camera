# Package Status: 02-preview-analysis-budget-contract

- **Agent**: agent-02-preview-analysis-budget-contract
- **Status**: completed
- **Started**: 2026-05-26T18:58:36Z
- **Completed**: 2026-05-27T04:00:00Z

## Worktree

- Path: /private/tmp/open_camera-orchestration/scene-mask-honesty-repair/02-preview-analysis-budget-contract
- Branch: agent/scene-mask-honesty-repair/02-preview-analysis-budget-contract
- Base commit: d10609a331d1eae249412e680db26539fb93580a
- Commit hash: c60dc1d30d53398a28c5b39b460dc63299fa2139

## Changes

- git status: 4 files changed
- git diff --stat: 197 insertions, 12 deletions
- Changed files:
  - app/src/main/java/com/opencamera/app/camera/MlKitSelfiePreviewSceneMaskSource.kt
  - app/src/main/java/com/opencamera/app/camera/PreviewSceneMaskSource.kt
  - app/src/test/java/com/opencamera/app/camera/PreviewAnalysisFanoutTest.kt
  - app/src/test/java/com/opencamera/app/camera/PreviewSceneMaskSourceTest.kt

## Verification

- Commands run:
  - `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewSceneMaskSourceTest --tests com.opencamera.app.camera.PreviewAnalysisFanoutTest` — PASS
  - `./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest --tests com.opencamera.app.camera.live.LivePreviewFrameSourceTest` — PASS
- Test results: All tests passed (BUILD SUCCESSFUL)

## Evidence

- Acceptance criteria:
  - [x] Preview lifecycle tests prove exactly one `ImageProxy.close()` owner — fanout test `fanout owns close with MlKit scene mask source` confirms fanout is sole close owner
  - [x] `MlKitSelfiePreviewSceneMaskSource` config is no longer just log text — `targetWidth`/`targetHeight` used for bitmap downscaling before ML Kit; `maxFps` enforced via time-based throttle before expensive conversion
  - [x] Frame throttling happens before heavy conversion when possible — fps throttle check placed before `imageProxyToBitmap()` call
  - [x] Existing Live preview fanout tests still pass — CameraXCaptureAdapterLivePhotoTest and LivePreviewFrameSourceTest both PASS
  - [x] No package changes outside allowed paths — only 4 files modified, all within allowed paths
- Notes:
  - `PreviewSceneMaskPayload` now carries `sourceWidth`/`sourceHeight` to record the resolution relationship between camera input and processed mask
  - `SceneMaskTransform` in descriptor correctly reflects source vs mask dimensions
  - `framesFpsThrottled` counter added to diagnostics output
  - `lastProcessedTimeMs` tracks timing for fps throttle

## Integration

- Integration branch: (pending merge by 99-finalize)
- Merge status: pending
- Cleanup status: pending

## Self-Certification

- [x] Only touched allowed paths
- [x] Did not edit forbidden paths
- [x] Did not edit INDEX.md or other status files
- [x] Updated `status/state.tsv` consistently

## Unresolved Risks

- Real-device performance: bitmap downscaling adds a `createScaledBitmap` call per processed frame; on low-end devices this may add latency. Mitigation: target size is 256x256 by default (small), and the throttle already limits frame rate.
- ML Kit `enableRawSizeMask()` returns mask at input resolution; the mask dimensions will match the scaled target size, not the original camera frame.
