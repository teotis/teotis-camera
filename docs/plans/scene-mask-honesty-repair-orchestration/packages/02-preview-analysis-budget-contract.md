# Package 02 - Preview Analysis Budget Contract

## Package ID

`02-preview-analysis-budget-contract`

## Goal

Make the preview Scene Mask analysis contract honest and bounded: `PreviewAnalysisFanout` remains the single `ImageProxy.close()` owner, `MlKitSelfiePreviewSceneMaskSource` respects preview configuration enough that target size and `maxFps` are real cost controls, and tests prevent lifecycle regressions.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/camera/MlKitSelfiePreviewSceneMaskSource.kt`
- `app/src/main/java/com/opencamera/app/camera/PreviewSceneMaskSource.kt`
- `app/src/main/java/com/opencamera/app/camera/PreviewAnalysisFanout.kt`
- `app/src/test/java/com/opencamera/app/camera/PreviewSceneMaskSourceTest.kt`
- `app/src/test/java/com/opencamera/app/camera/PreviewAnalysisFanoutTest.kt`
- `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterLivePhotoTest.kt` only if needed to preserve analysis wiring evidence
- Your coordinator status file: `docs/plans/scene-mask-honesty-repair-orchestration/status/02-preview-analysis-budget-contract.md`
- `docs/plans/scene-mask-honesty-repair-orchestration/status/state.tsv`

## Forbidden Paths

- PhotoAlgorithm saved-output files owned by package 01.
- Verification script/docs owned by package 03.
- `INDEX.md` and other package status files.
- Session kernel or mode plugin files.

## Dependencies

None. Can run in wave 1 with package 01.

## Branch / Worktree Policy

- Branch: `agent/scene-mask-honesty-repair/02-preview-analysis-budget-contract`
- Worktree: `/private/tmp/open_camera-orchestration/scene-mask-honesty-repair/02-preview-analysis-budget-contract`
- Base: `main`

## Implementation Requirements

1. Reconfirm current ownership:
   - `PreviewAnalysisFanout` closes `ImageProxy` exactly once.
   - `NoOpPreviewSceneMaskSource` does not close.
   - `MlKitSelfiePreviewSceneMaskSource` does not close the proxy directly when called through fanout.
2. Preserve this ownership unless tests prove current production wiring is unsafe. Do not blindly add `image.close()` inside `MlKitSelfiePreviewSceneMaskSource`.
3. Make `PreviewSceneMaskConfig.targetWidth` and `targetHeight` meaningful:
   - downscale/copy to a bounded bitmap before ML Kit processing, or otherwise configure analysis input so mask work is bounded;
   - preserve transform/source/mask metadata so downstream knows the resolution relationship.
4. Make `PreviewSceneMaskConfig.maxFps` meaningful:
   - drop frames before expensive conversion when not enough time has elapsed;
   - keep `inferenceInFlight` frame dropping;
   - record diagnostics for received/processed/dropped/fps-throttled.
5. Add tests for:
   - fanout closes exactly once with scene mask + live consumers;
   - source does not own close when used through fanout;
   - maxFps throttle avoids processing a too-soon frame;
   - target size affects processed mask or recorded descriptor/diagnostics.
6. Keep preview mask as approximate only. Do not route preview masks into saved-photo rendering.

## Acceptance Criteria

- Preview lifecycle tests prove exactly one `ImageProxy.close()` owner.
- `MlKitSelfiePreviewSceneMaskSource` config is no longer just log text.
- Frame throttling happens before heavy conversion when possible.
- Existing Live preview fanout tests still pass.
- No package changes outside allowed paths.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewSceneMaskSourceTest --tests com.opencamera.app.camera.PreviewAnalysisFanoutTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest --tests com.opencamera.app.camera.live.LivePreviewFrameSourceTest
```

## Expected Evidence Pack

- Worktree path, branch, base commit, commit hash.
- Changed files.
- Diff summary.
- Test commands and pass/fail summary.
- Explicit statement of `ImageProxy` lifecycle ownership.
- Remaining real-device performance risks.

