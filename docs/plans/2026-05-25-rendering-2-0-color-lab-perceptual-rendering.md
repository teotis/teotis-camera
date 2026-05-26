# Rendering 2.0 Color Lab Perceptual Rendering

## Goal

Make Color Lab V2 visibly effective, natural, and preview/saved-output consistent using the approved two-axis palette model. This package excludes advanced tools and sharing; it focuses only on the default Color Lab palette experience.

## Context

- User request: approved Rendering 2.0 upgrade item `Color Lab V2 感知式调色`, explicitly without advanced tools or sharing.
- Verified facts:
  - `ColorLabSpec` and `StyleColorPipeline` already provide a style-aware color pipeline.
  - `PerceptualColorRecipe` already exists and supports tone lift/depth, chroma boost, warmth/tint bias, highlight/shadow tint, neutral protection, skin protection, and preview fidelity.
  - `PhotoAlgorithmPostProcessor` already contains recipe-driven per-pixel adjustments and a mask-aware variant.
  - `PreviewEffectAdapter` still mainly creates tint overlay and does not build a recipe-backed color matrix.
  - Existing related plans asked for Color Lab strength and consistency, but this package narrows scope to no advanced tools and no sharing.
- Product references:
  - Apple Photographic Styles are useful as a product model for tone/color/intensity and localized color adjustment, not as a claim of private Apple ISP capability.
  - vivo-style two-axis palette semantics are useful for warm/cool and tone direction, not as a claim of vivo BlueImage hardware capability.
- Relevant files:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/ColorLabSpec.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/PerceptualColorRecipe.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/StyleColorPipeline.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewColorTransform.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectModel.kt`
  - `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
  - `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
  - `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
  - `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
- Depends on:
  - [Rendering 2.0 Capture Save Reliability](./2026-05-25-rendering-2-0-capture-save-reliability.md)
  - [Rendering 2.0 Render Recipe Single Truth](./2026-05-25-rendering-2-0-render-recipe-single-truth.md)
- Non-goals:
  - No advanced controls such as exposure, shadow, highlight, grain, sharpness, soft glow, temperature, or tint sliders.
  - No Color Lab recipe sharing, import, export, QR code, cloud filter, or marketplace.
  - No vendor SDK, private ISP, or hardware-specific color science claim.
  - No full GL/OES preview renderer in this package.

## Implementation Scope

- Tune `ColorLabSpec.toRecipe(...)` and `StyleColorPipeline` so palette edges/corners have a guaranteed perceptual minimum while staying natural.
- Feed the same recipe into saved JPEG processing and preview approximation through the Render Recipe V2 contract.
- Upgrade preview approximation from tint-only to recipe-backed `PreviewColorTransform`.
- Keep UI unchanged except for existing Color Lab behavior becoming more visible.
- Add deterministic tests for edge/corner strength, neutral/skin protection, and preview transform availability.

## Product Semantics

Keep the current two-axis palette:

- Horizontal axis: cool to warm / color direction.
- Vertical axis: airy/bright to deep/contrast tone.

Corners should read as:

- Right top: warm airy, sunlight and transparency, protected highlights.
- Right bottom: warm deep, film/夕阳/deeper contrast, protected skin.
- Left top: cool airy, clean air and freshness, protected neutral gray.
- Left bottom: cool deep, city/night density, protected shadows.

## Steps

1. Confirm Render Recipe V2 landed and Color Lab metadata contains shared recipe tags.
2. Strengthen recipe mapping conservatively:
   - add a small dead zone near center;
   - use accelerated edge response;
   - ensure `NATURAL`, `TEXTURE`, and `VIVID` have visible minimum values at full-strength edges;
   - keep `MONOCHROME` color axis muted but tone axis effective.
3. Update preview model:
   - build a 20-value color matrix or a deterministic fallback transform from `PerceptualColorRecipe`;
   - set `PreviewColorFidelity.APPROXIMATE` when a matrix is available;
   - set `PreviewColorFidelity.DEGRADED` when only tint overlay fallback is possible;
   - apply preview transform only to the preview content area, not cockpit controls or letterbox bands.
4. Update saved JPEG render path only where needed:
   - keep existing luma/chroma/neutral/skin-protection structure;
   - ensure saved render uses the same recipe values that preview receives;
   - do not add new controls or profile sharing.
5. Add tests:
   - all four Color Lab corners produce non-neutral recipe values with minimum magnitudes;
   - center and zero strength remain neutral;
   - gray patch stays close to gray under warm/cool extremes;
   - skin-like patch does not swing strongly orange/blue/green;
   - preview transform is stronger than old low-alpha tint-only behavior at palette edges;
   - unsupported preview path reports degraded fidelity.
6. Update existing Color Lab plan references only if they would mislead future agents about advanced tools or sharing being in scope.

## Acceptance Criteria

- Color Lab full-edge and corner positions are visibly different in saved JPEG output under unit/pixel tests and real-device samples.
- Preview and saved JPEG move in the same direction for warm/cool, airy/deep, chroma, highlight, and shadow behavior.
- Center/reset returns close to neutral.
- Effect remains natural: no heavy uniform color wash, posterization, neon saturation, broken skin, or gray objects becoming visibly colored.
- No new advanced Color Lab controls or sharing surfaces are added.
- Devices unable to support stronger preview transform expose degraded approximation rather than claiming exact preview parity.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.ColorLabSpecTest --tests com.opencamera.core.settings.PerceptualColorRecipeTest --tests com.opencamera.core.settings.StyleColorPipelineTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Real-Device Acceptance

Codex/user should retain this final check:

1. Open Photo mode with default style and Color Lab centered.
2. Capture one neutral JPEG.
3. Drag Color Lab to each edge and corner; capture one JPEG per position and record the screen.
4. Verify the preview and saved JPEG direction match qualitatively.
5. Repeat one non-default style such as vivid or texture.
6. Confirm thumbnail opens the saved postprocessed media rather than raw preview feedback.

## Risks And Notes

- Unit tests can prove direction, bounds, and protection behavior, but cannot judge final taste. Keep visual acceptance with Codex/user.
- A matrix preview approximation will not perfectly match per-pixel saved rendering. The goal is product-truthful similarity, not pixel identity.
- Stronger output before capture-save reliability lands could make the no-image symptom more visible. Keep package order.

