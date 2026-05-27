# Package 01 - Watermark Template Preview Expectation

## Package ID

`01-watermark-template-preview-expectation`

## Goal

When the user selects or edits a watermark template, the live preview surface must show an approximate but truthful template preview at the correct location. The user should be able to predict the final saved watermark style without taking a photo first.

## User Symptoms Covered

- Latest real-device issue 1: choosing a watermark type does not make the preview effect arrive.
- Product rule: template data, a schematic border, an icon, or a simplified overlay is acceptable, but the visual must be specific enough for the selected template.
- Example rule: `professional-bottom-bar` must look like a bottom parameter bar in the preview, not like a generic text watermark.

## Branch And Worktree

- Branch: `agent/watermark-zoom-preview-fix/01-watermark-template-preview-expectation`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/watermark-zoom-preview-fix/01-watermark-template-preview-expectation`
- Base: latest `main` unless `99-finalize` or the user says otherwise.

## Allowed Paths

- `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectModel.kt`
- `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
- `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt`
- `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt` only for plumbing selected watermark detail/template state into preview render state
- `app/src/test/java/com/opencamera/app/SessionPreviewRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt`
- `app/src/test/java/com/opencamera/app/PreviewColorTransformOverlayTest.kt` only if overlay rendering tests need a nearby fixture
- Coordinator status file: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/status/01-watermark-template-preview-expectation.md`

## Forbidden Paths

- Saved-photo watermark pixel renderer, except for a tiny shared mapping constant if absolutely needed and covered by tests.
- Shutter/session runtime files.
- Zoom/lens switching files.
- Other package status files.
- `INDEX.md`.
- UI-local state as the source of capture metadata truth.

## Required Investigation

1. Read this plan, `docs/plans/real-device-shutter-feedback-watermark-preview-orchestration/packages/04-watermark-template-preview.md`, and current code before editing.
2. Trace watermark data from persisted photo settings and active mode `WatermarkEffect` into `PreviewEffectAdapter`, `SessionPreviewRenderModel`, and `PreviewOverlayView`.
3. Confirm whether changing the selector/detail template updates the active preview while the settings page is open, not only after a later session rebuild.
4. Decide the smallest model change that distinguishes built-in templates:
   - `pure-text`: text-only hint.
   - `classic-overlay`: backed text at placement.
   - `travel-polaroid` / `retro-frame`: expanded-frame hint with border/background family.
   - `blur-four-border`: four-border hint.
   - `professional-bottom-bar`: dedicated bottom-bar/parameter-strip hint.
5. Keep the preview honest: it may be approximate, but it must not imply exact blur pixels, exact saved typography, or exact EXIF values when those are unavailable.

## Implementation Guidance

- Reuse `WatermarkHintSpec` and `WatermarkPreviewShape` rather than inventing a second preview model.
- Add a specific `WatermarkPreviewShape` or equivalent fields if `professional-bottom-bar` cannot be expressed clearly by `EXPANDED_FRAME`.
- If needed, use template/sample text such as model, date, focal length, aperture, shutter, ISO, or small icons. These are preview hints only and must not alter saved metadata.
- Use the active preview/capture frame geometry, not the whole screen, for placement when a frame rect exists.
- If selector/detail pages stage a template before it is the persisted default, explicitly choose whether the live preview follows the staged choice or only the selected default; the latest feedback favors immediate staged preview.

## Acceptance Criteria

- [ ] Selecting a watermark template changes the live preview hint immediately while the user is in the watermark selector/detail flow.
- [ ] `professional-bottom-bar` has a distinct bottom-parameter-bar preview affordance.
- [ ] `pure-text`, `classic-overlay`, `travel-polaroid`/`retro-frame`, `blur-four-border`, and `professional-bottom-bar` are visually distinguishable in the preview model or overlay drawing.
- [ ] Placement, opacity, text scale, and background/frame family are reflected when practical; any approximation is recorded in tests/status.
- [ ] The saved-photo watermark renderer remains driven by settings/media pipeline and is not replaced by UI preview state.
- [ ] Focused app/core tests pass.

## Verification Commands

Run from the package worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionPreviewRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
```

## Expected Evidence

- Worktree path, branch, base commit, final commit hash.
- Changed files and `git diff --stat`.
- Mapping table for each built-in template id to preview shape/placement/approximation.
- Test output summaries.
- Residual real-device visual QA risks.

## Unlock Condition

Mark completed only after focused preview tests and assemble pass, or blocked with exact failing command and reason.
