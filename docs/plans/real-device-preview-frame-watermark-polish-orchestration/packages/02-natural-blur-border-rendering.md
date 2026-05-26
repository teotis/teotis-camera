# Package 02 - Natural Blur Border Rendering

## Package ID

`02-natural-blur-border-rendering`

## Goal

Make `blur-four-border` look like the photo naturally extends into a blurred frame. The border background should be content-aware, derived from neighboring source pixels, blurred and softly transitioned, not a globally stretched bitmap with a pale fixed overlay or white frame impression.

## Problem Statement

Real-device feedback says the current result looks like "a blurred image plus a whitish frame" rather than a natural extension of the photo. Current rendering routes source-blur backgrounds through `drawFrameBackground(...)`, which scales the whole source bitmap into the full frame and overlays fixed tint colors. For `blur-four-border`, that can create a washed-out border unrelated to adjacent edge content.

## Design Direction

- Build the expanded background from adjacent source content:
  - top border from the top strip of the source,
  - bottom border from the bottom strip,
  - left/right borders from the matching side strips,
  - corners from the nearest corner regions.
- Blur after extension so seams soften naturally.
- Keep any tone overlay subtle and template-specific; it must not dominate the source-derived border.
- Keep text readability through text paint/shadow/contrast, not by painting a large white backing over the whole border.
- Preserve the existing template id, metadata tags, resolver semantics, and OCWM reversible archive behavior.

## File Ownership

Allowed paths:
- `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
- `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkPostProcessorTest.kt`
- `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkTemplateResolverTest.kt` only if resolver warnings/notes need strengthening
- narrowly scoped test fixtures under `app/src/test/**` if needed
- `docs/plans/real-device-preview-frame-watermark-polish-orchestration/status/02-natural-blur-border-rendering.md`

Forbidden paths:
- `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/main/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessor.kt`
- `core/settings/**` unless a focused failing test proves settings are the source
- `core/effect/**`
- other packages' status files or orchestration indexes

## Dependencies

None, but coordinate with existing active Watermark 2.0 agents, especially:
- `docs/plans/watermark-2-validation-fix-orchestration/packages/02-blur-border-settings-guard.md`

## Parallel Safety

Safe with package `01-preview-frame-contract` because file ownership is disjoint.

## Implementation Scope

1. Extract or add a small testable helper for content-aware blur-frame background creation inside the watermark postprocessor boundary.
2. Replace the `blur-four-border` background path so the expanded frame is generated from edge/corner content before blur/tone treatment.
3. Keep `SOURCE_BLUR`, `SOURCE_LIGHT_BLUR`, and `SOURCE_VIVID_BLUR` visually distinct without relying on a heavy white/cream overlay.
4. Add deterministic bitmap tests with synthetic edge colors proving each border area is derived from the adjacent source edge, not from a global flat color.
5. Add a guard test that the light blur path does not wash the frame into near-white when the adjacent photo edge is dark or colorful.
6. Preserve existing renderer notes and archive behavior.

## Acceptance Criteria

- [ ] `blur-four-border` border pixels are derived from adjacent edge/corner source content in deterministic tests.
- [ ] A synthetic dark-edge input does not produce a mostly white/pale border in `SOURCE_LIGHT_BLUR`.
- [ ] A synthetic colorful-edge input keeps distinguishable top/bottom/left/right border influence after blur/tone processing.
- [ ] Existing `classic-overlay`, `travel-polaroid`, `retro-frame`, `pure-text`, and `professional-bottom-bar` behavior is not changed except through shared helper changes proven harmless by tests.
- [ ] `PhotoWatermarkPostProcessorTest` covers the new natural blur behavior.
- [ ] The package status includes at least one before/after visual note or generated artifact path if the agent creates local visual samples.

## Verification Commands

External agents in a worktree MUST use isolated Gradle:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

If the full Watermark V2 gate is run, use an isolated root:

```bash
rtk env OPENCAMERA_BUILD_ROOT=/private/tmp/opencamera-natural-blur-border ./scripts/verify_stage_6b3_watermark_v2.sh
```

## Expected Evidence Pack

- Rendering algorithm summary.
- Pixel-test evidence showing source-edge-derived borders.
- Any visual sample paths, if generated.
- Verification command summaries.
- Confirmation that OCWM/reversible archive behavior was not changed.
- Self-certification that only allowed paths were touched.

## Risks And Notes

- This is a bitmap rendering improvement, not a settings/catalog migration.
- Avoid adding heavyweight image-processing dependencies. Use existing Android `Bitmap`, `Canvas`, `Paint`, downsample/scale, and small helper functions.
- Final product acceptance is visual and should include real photos, not only synthetic tests.
