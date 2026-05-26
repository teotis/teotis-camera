# Color Lab Recipe Metadata Bridge Repair

## Goal

Ensure the non-neutral `PerceptualColorRecipe` produced by Color Lab reaches capture metadata and saved-photo rendering, so the saved JPEG is not rendered from `PerceptualColorRecipe.NEUTRAL` while the mode plugin believes a strong Color Lab recipe is active.

## Context

- User request: Color Lab maximum effect is still too pale after external-agent implementation.
- Verified facts:
  - `PhotoModePlugin` creates a `PerceptualColorRecipe` and stores it in `FilterEffect(recipe = recipe)`.
  - `EffectBridge.toMetadataTags(...)` currently writes `filterProfile` and `filterSpec.*`, but not recipe fields such as `recipeToneLift`.
  - `PhotoAlgorithmPostProcessor` attempts to read recipe-like metadata keys and falls back to neutral when they are absent.
  - Related broader plan: [`Rendering 2.0 Render Recipe Single Truth`](2026-05-25-rendering-2-0-render-recipe-single-truth.md).
- Relevant files:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/PerceptualColorRecipe.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsMetadataCodecs.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectSpec.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
  - `core/effect/src/test/kotlin/com/opencamera/core/effect/EffectBridgeTest.kt`
  - `core/settings/src/test/kotlin/com/opencamera/core/settings/PerceptualColorRecipeTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`
- Non-goals:
  - Do not add Color Lab advanced controls.
  - Do not tune Color Lab taste or strength in this package.
  - Do not duplicate recipe parsing in app code if a settings/effect codec can own it.

## Implementation Scope

- Add a shared metadata codec for `PerceptualColorRecipe`.
- Make `EffectBridge.toMetadataTags(...)` write recipe fields when the active effect has a non-neutral recipe.
- Update `PhotoAlgorithmPostProcessor` to use the shared parser or delegate to it.
- Preserve backward compatibility with existing key names already used by tests or postprocessor code.

## Steps

1. Inspect `PerceptualColorRecipe` fields and current `PhotoAlgorithmPostProcessor` local parsing keys.
2. Add codec functions in `core/settings`, preferably near `SettingsMetadataCodecs.kt`:
   - `PerceptualColorRecipe.toMetadataTags(prefix: String = "recipe")`;
   - `PerceptualColorRecipe.fromMetadataTags(tags: Map<String, String>, prefix: String = "recipe")`.
3. Support existing flat keys such as `recipeToneLift`, `recipeToneDepth`, `recipeChromaBoost`, `recipeWarmthBias`, and `recipeTintBias`.
4. Define one canonical future key form if needed, for example `recipe.toneLift`, while keeping old keys readable.
5. Update `EffectBridge.toMetadataTags(...)` to write recipe tags from `FilterEffect.recipe` when non-neutral.
6. Update `PhotoAlgorithmPostProcessor` to read the shared codec and remove or narrow local ad hoc parsing.
7. Add tests:
   - recipe metadata round trip in `PerceptualColorRecipeTest`;
   - `EffectBridgeTest` proves non-neutral Color Lab effect emits recipe tags;
   - `PhotoAlgorithmPostProcessorTest` proves non-neutral tags produce non-neutral render behavior or notes;
   - one mode/session-level test proves a strong Color Lab shot metadata includes recipe fields.

## Acceptance Criteria

- A strong Color Lab shot metadata includes recipe tags in addition to `filterSpec.*`.
- Saved-photo postprocess reads the same recipe values that `EffectBridge` writes.
- Neutral/no-filter shots do not emit noisy non-neutral recipe tags.
- Existing compatibility fields remain intact so older filter tests do not regress.
- No mode plugin writes recipe metadata by hand.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PerceptualColorRecipeTest --tests com.opencamera.core.settings.StyleColorPipelineTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.EffectBridgeTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.color lab filtered photo suppresses raw capture feedback"
```

## Risks And Notes

- This package is the narrow repair for the current validation failure. The broader `RenderRecipe` V2 upgrade may later fold this codec into a larger contract.
- If adding the codec requires a dependency direction change, keep ownership in `core:settings` or `core:effect`; do not make `core:settings` depend on app/device code.
