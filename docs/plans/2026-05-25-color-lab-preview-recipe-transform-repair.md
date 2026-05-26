# Color Lab Preview Recipe Transform Repair

## Goal

Make Color Lab preview consume the same `PerceptualColorRecipe` semantics used for saved-photo rendering, so maximum Color Lab positions are visible in preview as an honest approximation instead of a pale tint-only overlay.

## Context

- User request: Color Lab maximum effect remains too pale on real device.
- Verified facts:
  - `FilterEffect.recipe` exists after the external-agent implementation.
  - `PreviewEffectAdapter.adapt(...)` currently derives preview color behavior mainly from overlay and scene mask state, not from `FilterEffect.recipe`.
  - `PreviewOverlayView` still renders a coarse full-screen tint overlay path.
  - Related broader plan: [`Rendering 2.0 Color Lab Perceptual Rendering`](2026-05-25-rendering-2-0-color-lab-perceptual-rendering.md).
- Relevant files:
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewColorTransform.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectModel.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectSpec.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/PerceptualColorRecipe.kt`
  - `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
  - `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
  - `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt`
- Non-goals:
  - Do not implement a full GL/OES renderer here.
  - Do not claim pixel-perfect parity between preview and saved JPEG.
  - Do not tint cockpit controls, panels, or letterbox bands.

## Implementation Scope

- Convert non-neutral `PerceptualColorRecipe` into a deterministic preview transform or explicit degraded fallback.
- Ensure `PreviewEffectAdapter` gives recipe transform priority over scene-mask-only tint decisions where appropriate.
- Keep preview fidelity truthful through `PreviewColorFidelity.APPROXIMATE`, `DEGRADED`, or existing unsupported semantics.
- Add tests that fail if recipe is ignored.

## Steps

1. Inspect `PreviewColorTransform` and existing adapter tests to determine whether a 20-value color matrix is already supported.
2. Add a pure builder for preview approximation:
   - use recipe tone/chroma/warmth/tint values to build a bounded color matrix when supported;
   - otherwise generate the strongest truthful fallback already supported by `PreviewEffectModel`.
3. Update `PreviewEffectAdapter.adapt(...)`:
   - read `FilterEffect.recipe`;
   - keep neutral recipe behavior unchanged;
   - for non-neutral recipe, set a non-`NONE` color transform or explicit degraded fallback;
   - do not let absent scene mask erase recipe-driven color transform.
4. If app rendering cannot apply matrix yet, surface this honestly in the render model and notes rather than claiming exact preview support.
5. Add tests:
   - strong warm/deep recipe produces a non-neutral transform;
   - strong cool/airy recipe produces a directionally different transform;
   - neutral recipe remains neutral;
   - missing/unsupported mask does not remove recipe transform;
   - fallback path reports degraded fidelity.

## Acceptance Criteria

- `PreviewEffectAdapterTest` proves Color Lab recipe affects preview model without relying only on overlay alpha.
- Preview and saved-photo recipe directions match for warm/cool and airy/deep semantics.
- Devices or renderer paths without matrix support report degraded approximation.
- UI controls and panels are not tinted by the preview approximation.
- No new Color Lab controls or advanced UI are added.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest --tests com.opencamera.core.effect.EffectBridgeTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Real-Device Acceptance

Codex/user should retain final visual judgment:

1. Install the APK after the recipe metadata bridge and preview transform both land.
2. Capture neutral, warm/deep, cool/deep, warm/airy, and cool/airy samples.
3. Compare screen recording preview direction against saved JPEGs.
4. Accept only if maximum positions are visibly different while still natural.

## Risks And Notes

- A matrix approximation will be imperfect compared with saved-photo per-pixel processing. This package should make the product truthful and visible, not exact.
- If app UI currently cannot apply `PreviewColorTransform`, split the final renderer wiring into a small follow-up but keep tests proving the adapter no longer ignores recipe.
