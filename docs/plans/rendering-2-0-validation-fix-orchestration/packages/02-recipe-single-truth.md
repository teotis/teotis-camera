# Package 02 — Recipe Single Truth Repair

## Package ID

`02-recipe-single-truth`

## Goal

Close the remaining gap between the stated `RenderRecipe V2 single truth` goal and the current code: saved JPEG postprocess should not have independent recipe decision logic, and recipe-only metadata must not become a no-op.

## Context

- Verified facts:
  - `RenderRecipe.from(shot)` parses recipe tags and drives capture feedback suppression.
  - `PhotoAlgorithmPostProcessor.decidePhotoAlgorithmWork(...)` still calls a local `parseRecipeFromTags(...)` wrapper and only executes when `FilterRenderSpec` or `algorithmProfile` exists.
  - If a future shot contains only non-neutral recipe tags, capture feedback can be suppressed while saved JPEG postprocess does nothing.
  - `RenderRecipe` currently no longer carries color-science provenance even though the plan requested provenance fields where useful.
- Relevant files:
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/RenderRecipe.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt`
  - `core/effect/src/test/kotlin/com/opencamera/core/effect/RenderRecipeTest.kt`
  - `core/effect/src/test/kotlin/com/opencamera/core/effect/EffectBridgeTest.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsMetadataCodecs.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`
- Non-goals:
  - Do not add Color Lab advanced tools.
  - Do not add sharing/import/export.
  - Do not rewrite the whole media pipeline.

## File Ownership

- Allowed paths:
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/RenderRecipe.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt`
  - `core/effect/src/test/kotlin/com/opencamera/core/effect/RenderRecipeTest.kt`
  - `core/effect/src/test/kotlin/com/opencamera/core/effect/EffectBridgeTest.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsMetadataCodecs.kt`
  - `core/settings/src/test/kotlin/com/opencamera/core/settings/PerceptualColorRecipeTest.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`
- Forbidden paths:
  - `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
  - orchestration `INDEX.md`

## Implementation Scope

- Make `PhotoAlgorithmPostProcessor` consume the shared recipe/metadata codec without local parser ownership.
- Ensure non-neutral recipe tags alone trigger a `PhotoAlgorithmSpec`, even when no `filterSpec.*` or `algorithmProfile` is present.
- Preserve backward compatibility for old `recipeToneLift` keys.
- Decide whether color-science provenance belongs in `RenderRecipe`; if not, document the reason in tests or package status.

## Acceptance Criteria

- `RenderRecipe.from(shot)` and `PhotoAlgorithmPostProcessor.decidePhotoAlgorithmWork(...)` agree that non-neutral recipe metadata requires saved output postprocess.
- Recipe-only metadata produces executable postprocess work with a deterministic profile name such as `perceptual-color`.
- No local ad hoc parser remains in `PhotoAlgorithmPostProcessor`; it either uses `RenderRecipe` or a shared codec directly.
- Existing bridge metadata and round-trip tests pass.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PerceptualColorRecipeTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.RenderRecipeTest --tests com.opencamera.core.effect.EffectBridgeTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence Pack

- Tests showing recipe-only postprocess work executes.
- Diff proving parser ownership is shared, not copied.
- Confirmation that backward-compatible legacy tags still parse.

## Risks And Notes

- Avoid introducing a dependency cycle. If app cannot depend on `RenderRecipe` cleanly, use the shared settings codec but remove the local wrapper and add agreement tests.

