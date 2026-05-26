# Watermark 2.0 Blurred Four-Border Polish

## Goal

Productize the existing `blur-four-border` template so it clearly renders source-image blur on all four borders, keeps the center photo inspectable, and exposes only blur-family background controls.

## Context

- User request: include blurred four-border watermark in Watermark 2.0.
- Verified facts:
  - `blur-four-border` is already registered in `SettingsDefaults.kt`.
  - `PhotoWatermarkPostProcessor.kt` already has `TEMPLATE_BLUR_FOUR_BORDER`, resolver support, `SUPPORTED_BLUR_BACKGROUNDS`, and a `drawBlurFourBorderFrame(...)` render path.
  - `PhotoWatermarkTemplateResolverTest.kt` already verifies unsupported solid backgrounds clamp to `SOURCE_LIGHT_BLUR`.
  - `PhotoWatermarkPostProcessorTest.kt` already verifies `blur-four-border` pipeline notes.
- Relevant files:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDefaults.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDataModels.kt`
  - `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
  - `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkTemplateResolverTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkPostProcessorTest.kt`
- Non-goals:
  - Do not add solid dark/white backgrounds to this template.
  - Do not implement a generic border editor.
  - Do not burn this into video frames or Live motion segments.

## Implementation Scope

- Confirm allowed placements are bottom-left, bottom-center, and bottom-right only.
- Confirm allowed backgrounds are `SOURCE_BLUR`, `SOURCE_LIGHT_BLUR`, and `SOURCE_VIVID_BLUR` only.
- Strengthen UI/model tests for control filtering and copy.
- Strengthen renderer tests only where deterministic checks are feasible; leave beauty/proportion judgment to visual QA.

## Steps

1. Inspect `DEFAULT_WATERMARK_TEMPLATES` and render-model filtering for `blur-four-border`.
2. Verify the detail page shows only valid placement/background controls.
3. Add tests if missing for allowed placement/background lists and footer/summary copy.
4. Inspect `drawBlurFourBorderFrame(...)` for accidental center blur, excessive band width, or text over center image.
5. Add renderer smoke tests or existing fake-editor tests proving the branch remains selected.
6. Run focused verification.

## Acceptance Criteria

- `blur-four-border` cannot select solid dark/white background through UI or persisted action paths.
- Resolver clamps unsupported backgrounds to a blur background.
- Saved JPEG path uses `drawBlurFourBorderFrame(...)`.
- Center image remains the source photo area; blur is confined to the border treatment.
- Existing reversible archive behavior still embeds the pre-watermark JPEG when this template renders.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest --tests com.opencamera.app.camera.PhotoWatermarkArchiveEditorTest
rtk ./scripts/verify_stage_6b3_watermark_v2.sh
rtk ./scripts/verify_reversible_watermark_archive.sh
```

## Risks And Notes

- Do not expand this into broad renderer refactoring unless an existing test failure proves it is needed.
- Visual QA should inspect at least one high-detail photo; the border should look derived from the image, not like a flat colored frame.
