# Watermark 2.0 Minimalist Text Polish

## Goal

Productize the existing `pure-text` watermark into the quiet default-style option: readable typography only, no frame, no card, no decorative container, and no settings controls that imply a frame background.

## Context

- User request: include minimalist text watermark in Watermark 2.0.
- Verified facts:
  - `pure-text` is already registered in `SettingsDefaults.kt`.
  - `PhotoWatermarkPostProcessor.kt` already has `TEMPLATE_PURE_TEXT`, resolver support, and a `drawPureTextOverlay(...)` render path.
  - `PhotoWatermarkTemplateResolverTest.kt` already has a pure-text resolver test.
  - `PhotoWatermarkPostProcessorTest.kt` already verifies `pure-text` pipeline notes.
- Relevant files:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDefaults.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDataModels.kt`
  - `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt`
  - `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-en/strings.xml`
  - `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
- Non-goals:
  - Do not make pure text into a new frame template.
  - Do not add font picker or freeform text editor in this package.

## Implementation Scope

- Confirm `pure-text` exposes placement, text scale, and opacity only.
- Remove or hide frame-background controls for `pure-text` if any render model still exposes them.
- Tighten copy so selector/detail pages describe it as clean text overlay, not a framed watermark.
- Tune renderer only if tests/visual review show poor legibility in bright/dark scenes; prefer bounded shadow/opacity changes over broad style changes.

## Steps

1. Inspect `watermarkLabDetailRenderModel(...)` and its tests around `pure-text`.
2. Ensure `WatermarkTemplate.allowedFrameBackgrounds` or render-model capability filtering makes background control absent/disabled for `pure-text`.
3. Update text resolver strings and tests to describe pure text accurately.
4. Add or strengthen tests that `pure-text` detail model has no frame background control and keeps placement/scale/opacity controls.
5. Add renderer smoke coverage only if current tests do not assert the branch remains active.
6. Run focused verification.

## Acceptance Criteria

- Watermark Lab lists `pure-text` as a clean text overlay.
- `pure-text` detail page does not expose frame-background controls.
- Saved JPEG still renders a typography-only watermark through `PhotoWatermarkPostProcessor`.
- No regressions to the other templates.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest
rtk ./scripts/verify_stage_6b3_watermark_v2.sh
```

## Risks And Notes

- This package may already be satisfied by current code. If inspection proves all acceptance criteria pass, update the package index to `implemented` or `validated` instead of making cosmetic churn.
- Visual QA should compare light and dark saved JPEGs; text should be legible without looking like a card.
