# Watermark 2.0 Professional Parameter Bottom Bar

## Goal

Add a still-photo watermark template that renders a calm professional bottom bar with device/model, mode/profile, and camera parameters. It should feel like a flagship camera parameter strip, not an OEM clone, and must preserve the project's media-pipeline ownership boundaries.

## Context

- User request: upgrade Watermark 2.0 with a professional parameter bottom-bar watermark.
- Verified facts:
  - `DEFAULT_WATERMARK_TEMPLATES` currently has `classic-overlay`, `travel-polaroid`, `retro-frame`, `pure-text`, and `blur-four-border`.
  - `PhotoWatermarkPostProcessor.kt` already resolves `watermarkModel`, `watermarkDatetime`, `watermarkLocation`, `watermarkCameraParams`, `watermarkModeName`, and `watermarkProfileName` from metadata/EXIF.
  - `PhotoModePlugin.kt` builds `WatermarkEffect` from persisted settings and selected catalog template.
  - `EffectBridge.kt` carries watermark template and style values into metadata tags.
- Relevant files:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDataModels.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDefaults.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsActions.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt`
  - `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
  - `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkTemplateResolverTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkPostProcessorTest.kt`
- Non-goals:
  - Do not copy Hasselblad, OPPO, vivo, or Apple branding.
  - Do not add video frame watermark burn-in.
  - Do not add freeform layout editing.

## Implementation Scope

- Add a new template id, recommended `professional-bottom-bar`.
- Register it as an expanded-frame or bottom-bar template with token keys for `model`, `datetime`, `camera-params`, and mode/profile.
- Add per-template persisted `WatermarkStyleSettings` following the current explicit-field pattern unless the codebase has already migrated to a map.
- Render a bottom band below the photo; show the title line as `model · mode profile` when structured tags exist, and show camera params/date as supporting text.
- Keep controls template-specific: placement should likely be bottom-only; background should be limited to dark/white/source blur variants that the renderer truly supports.

## Steps

1. Inspect current `WatermarkTemplate`, `PhotoSettings.watermarkStyleFor`, `SettingsActions.updateWatermarkStyle`, serializer keys, and existing `pure-text`/`blur-four-border` patterns.
2. Add the `professional-bottom-bar` template to `DEFAULT_WATERMARK_TEMPLATES` with explicit allowed placements/backgrounds.
3. Add persisted style defaults and serializer round-trip support.
4. Update `resolvePhotoWatermarkTemplate(...)` so the new template resolves title, supporting lines, default placement, default background, and fallback behavior.
5. Add a renderer branch in `renderPhotoWatermarkBitmap(...)` using existing `drawExpandedFrame(...)` if enough, or a small dedicated helper if the information layout needs two/three columns.
6. Update UI render-model tests so Watermark Lab lists the new template, filters invalid controls, and uses truthful copy.
7. Add resolver and postprocessor tests that prove the new template passes through pipeline notes and renders from EXIF/custom tags.
8. Run focused verification.

## Acceptance Criteria

- `FeatureCatalog.DEFAULT_WATERMARK_TEMPLATES` exposes `professional-bottom-bar`.
- Selecting the template persists and restores across `PersistedSettingsSerializer`.
- Saved JPEG watermark rendering visibly changes through `PhotoWatermarkPostProcessor`, not only metadata.
- EXIF/custom tags can produce model, mode/profile, datetime, and camera params in the resolved template.
- Unknown/missing parameters degrade gracefully; the output still renders with available fields.
- UI does not offer impossible placement/background controls for this template.
- Existing `classic-overlay`, `travel-polaroid`, `retro-frame`, `pure-text`, and `blur-four-border` tests still pass.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest
rtk ./scripts/verify_stage_6b3_watermark_v2.sh
```

## Risks And Notes

- `PhotoWatermarkPostProcessor.kt` is already large; add only the smallest helper needed for bottom-bar layout.
- Do not change postprocessor order in `AppContainer`.
- Final visual judgment, especially text density and bottom-band proportions, stays with Codex/user visual QA after implementation.
