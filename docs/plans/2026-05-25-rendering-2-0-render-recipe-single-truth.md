# Rendering 2.0 Render Recipe Single Truth

## Goal

Upgrade `RenderRecipe` from a postprocess yes/no helper into the single rendering contract shared by mode plugins, capture metadata, preview approximation, saved JPEG postprocess, thumbnail policy, and diagnostics.

## Context

- User request: approved Rendering 2.0 upgrade item `Render Recipe V2 单一真相`.
- Verified facts:
  - `RenderRecipe` currently captures `filterProfileId`, `FilterRenderSpec`, `FrameRatio`, watermark identity/text, and selfie mirror.
  - `captureFeedbackPolicyFor(...)` already uses `RenderRecipe.from(shot).requiresFinalOutputPostprocess`.
  - `PerceptualColorRecipe` exists and `PhotoAlgorithmPostProcessor` can parse recipe-like tags.
  - `EffectBridge.toMetadataTags(...)` does not currently write perceptual recipe fields.
  - `StyleColorPipelineResult` currently returns `finalRenderSpec`, `baseLutId`, `stages`, and notes, but not a recipe object as the canonical output.
- Relevant files:
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/RenderRecipe.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectSpec.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/PerceptualColorRecipe.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/StyleColorPipeline.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsMetadataCodecs.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
  - `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
  - `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
  - `feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt`
- Related plans:
  - `docs/plans/2026-05-22-render-recipe-preview-engine-handoff.md`
  - `docs/plans/2026-05-25-color-lab-perceptual-strength-and-consistency.md`
- Non-goals:
  - Do not add Color Lab advanced tools.
  - Do not add recipe sharing/import/export.
  - Do not implement a full GL preview render engine.
  - Do not move rendering ownership into UI or Session Kernel.

## Implementation Scope

- Extend `RenderRecipe` with perceptual color recipe and provenance fields.
- Add metadata codecs for `PerceptualColorRecipe` with backward-compatible parsing of current `recipeToneLift` style tags.
- Make `StyleColorPipeline` produce the canonical recipe along with `finalRenderSpec`.
- Make `EffectBridge` write the recipe into capture metadata when the active filter/style has non-neutral Color Lab semantics.
- Update `PhotoAlgorithmPostProcessor` to read the centralized codec rather than local ad hoc parsing.
- Keep capture feedback policy based on `RenderRecipe.requiresFinalOutputPostprocess`.

## Suggested Model Shape

Use exact naming that fits the codebase, but preserve these semantics:

```kotlin
data class RenderRecipe(
    val filterProfileId: String?,
    val filterRenderSpec: FilterRenderSpec?,
    val perceptualColorRecipe: PerceptualColorRecipe,
    val colorScience: StyleColorScience?,
    val frameRatio: FrameRatio?,
    val watermarkTemplateId: String?,
    val watermarkText: String?,
    val selfieMirror: Boolean
)
```

`requiresFinalOutputPostprocess` should be true when the perceptual recipe is non-neutral even if a low-dimensional `FilterRenderSpec` is absent.

## Steps

1. Add `PerceptualColorRecipe.toMetadataTags(...)` and `PerceptualColorRecipe.fromMetadataTags(...)` in `SettingsMetadataCodecs.kt` or a nearby settings codec file.
2. Preserve current parser compatibility:
   - support existing keys such as `recipeToneLift`;
   - define one canonical new prefix for future tags, for example `recipe.toneLift`.
3. Extend `StyleColorPipelineResult` to include `perceptualColorRecipe`.
4. Update `renderStyleColorSpec(...)` or add a sibling function that returns both `finalRenderSpec` and recipe. Avoid a second hidden color pipeline.
5. Extend `FilterEffect` or add an effect-level carrier only if necessary. The mode plugin should still declare desired effects; UI should not store recipe internals.
6. Update `EffectBridge.toMetadataTags(...)` so capture metadata contains:
   - `filterProfile`;
   - `filterSpec.*`;
   - perceptual recipe tags when non-neutral;
   - optional color-science/profile provenance for diagnostics.
7. Update `RenderRecipe.from(effectSpec)` and `RenderRecipe.from(shot)` to parse the recipe consistently.
8. Update `PhotoAlgorithmPostProcessor` to call the shared recipe parser.
9. Add focused tests for codec round trip, bridge metadata, recipe extraction, and capture feedback policy.

## Acceptance Criteria

- `RenderRecipe.from(effectSpec)` and `RenderRecipe.from(shot)` agree on non-neutral Color Lab recipe semantics.
- Capture metadata for Color Lab shots contains both compatibility `filterSpec.*` and recipe tags.
- `PhotoAlgorithmPostProcessor` no longer owns a separate recipe parser.
- `captureFeedbackPolicyFor(...)` suppresses raw feedback for non-neutral recipe shots even if future modes do not use `filterProfile` as the signal.
- Existing filter, frame ratio, watermark, and selfie mirror recipe tests continue passing.
- No mode plugin writes ad hoc recipe tags by hand after this change.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PerceptualColorRecipeTest --tests com.opencamera.core.settings.StyleColorPipelineTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.RenderRecipeTest --tests com.opencamera.core.effect.EffectBridgeTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureFeedbackPolicyTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- This package touches shared contracts. Keep it mechanical and heavily tested before Color Lab visual tuning.
- Avoid adding another `ColorLabRenderSpec` if `PerceptualColorRecipe` can be made the canonical recipe.
- If `FilterEffect` is extended, update all mode plugins and tests in one pass to avoid mixed old/new metadata paths.

