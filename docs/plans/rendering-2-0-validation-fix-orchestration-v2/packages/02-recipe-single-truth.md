# Package 02 - Render Recipe Single Truth

## Package ID

`02-recipe-single-truth`

## Problem

The failed audit found that `PhotoAlgorithmPostProcessor.decidePhotoAlgorithmWork(...)` still uses a local `parseRecipeFromTags(...)` wrapper and still returns `ProcessorWork.None` for recipe-only metadata when no `FilterRenderSpec` or algorithm profile is present. That violates the Render Recipe V2 goal that preview, saved output, thumbnail policy, and diagnostics share one recipe truth.

## Goal

Make `RenderRecipe` and the shared metadata codec the single truth used by saved-photo postprocess work decisions.

## File Ownership

Allowed paths:
- `core/effect/src/main/**/RenderRecipe*`
- `core/effect/src/main/**/EffectBridge*`
- `core/effect/src/main/**/SettingsMetadataCodecs*`
- `core/effect/src/test/**`
- `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
- `app/src/test/java/com/opencamera/app/camera/**PhotoAlgorithmPostProcessor*`

Forbidden paths:
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `docs/plans/**` except `docs/plans/rendering-2-0-validation-fix-orchestration-v2/status/02-recipe-single-truth.md`

## Dependencies

None. Coordinate with package 01 if both need to edit the same camera test helper.

## Required Behavior

- Centralize recipe parsing/serialization through the shared `RenderRecipe` or metadata codec API.
- Remove or reduce local wrapper parsing in `PhotoAlgorithmPostProcessor` unless it delegates directly to the shared codec and exists only as a tiny compatibility shim.
- Recipe-only metadata with a non-neutral render recipe must schedule saved-output postprocess work when a raw media result is available.
- Preserve backward compatibility for existing filter/profile metadata tags.
- Keep runtime state and persisted settings separate.

## Acceptance Criteria

- `RenderRecipe.from(shot)` and saved-photo postprocess work decisions agree for recipe metadata.
- Non-neutral recipe-only metadata no longer returns `ProcessorWork.None` solely because filter/profile metadata is absent.
- Neutral/no-op recipes do not trigger unnecessary saved-output work.
- Legacy filter/profile paths still pass existing tests.
- Focused tests cover recipe-only, neutral recipe, and legacy metadata cases.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

If a worktree is used, run Gradle through:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:effect:test
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
```

## Expected Evidence Pack

Write to `status/02-recipe-single-truth.md`:
- worktree path and branch
- changed files and `git diff --stat`
- exact verification commands and pass/fail summaries
- the shared API that now owns recipe parsing
- commit hash or PR link
- self-certification that only allowed paths were touched
