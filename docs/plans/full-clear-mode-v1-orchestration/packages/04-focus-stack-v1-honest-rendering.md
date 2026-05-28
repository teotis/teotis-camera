# Package 04 - Focus Stack V1 Honest Rendering

## Goal

Add V1 saved-output processing for Full Clear focus bracket artifacts, with a conservative fusion attempt and honest fallback.

## Allowed Paths

- `core/media/src/main/kotlin/com/opencamera/core/media/**`
- `core/media/src/test/kotlin/com/opencamera/core/media/**`
- `app/src/main/java/com/opencamera/app/camera/**`
- `app/src/test/java/com/opencamera/app/camera/**`
- `feature/mode-fullclear/**`
- `docs/plans/full-clear-mode-v1-orchestration/v1-implementation-design.md`

## Forbidden Paths

- Unrelated style/filter/watermark behavior.
- Scene Mask backend expansion unless strictly optional and fail-soft.
- Coordinator files outside `status/04-focus-stack-v1-honest-rendering.md`.

## Required Work

1. Add a `FocusStackV1` or equivalent postprocessor for focus-bracket artifacts.
2. Implement a conservative V1 algorithm using local sharpness/contrast evidence and synthetic-testable behavior.
3. If alignment/fusion confidence is low, save the best frame and mark `fullClearFusionStatus=best-frame` or `degraded`.
4. Add pipeline notes for fused/best-frame/degraded/unsupported/failed.
5. Wire `FullClearModePlugin` to request the V1 path when contracts are available.
6. Do not overclaim segmentation, true depth, or vendor-quality fusion.

## Acceptance Criteria

- Synthetic bitmap tests prove near-region and far-region behavior for a simple bracket pair.
- Failure tests prove fallback notes survive into `ShotResult.pipelineNotes`.
- Full Clear output metadata includes mode, bracket status, fusion status, and fallback reason when applicable.
- Existing postprocessors remain fail-soft.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.FocusStackV1PostProcessorTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :feature:mode-fullclear:test
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:media:test
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
```

## Expected Evidence

- Algorithm/fallback summary.
- Changed files.
- Verification command results.
- Explicit statement of what real-device QA still must prove.

