# 2026-05-22 Fourth Real-Device Feedback Index

> **For agentic workers:** This is a handoff index, not an implementation patch. Pick one linked plan, keep edits scoped, and run the listed verification. Use `rtk` for every shell command.

## Context

The latest APK was tested again on a real device. The user reported:

1. Landscape interaction is still poor. The preferred direction is to keep the overall UI topology stable, while rotating button glyphs/text so controls still read as upright in landscape.
2. The real imaging area appears lower than the preview area.
3. Mode-track text should be more prominent and clearer.
4. The top-right `色彩实验室` entry still has not been restored. Instead, the function became a right-side rail entry. The right-side rail is now confusing and needs cleanup.
5. The quick sub-panel layout is weak and text is ellipsized.
6. Left/secondary panels are too large, obscure other controls, and some content exceeds the visible layout.
7. The mode track is not sensitive or accurate enough; false taps happen often.
8. There may be additional unnoticed defects.

This fourth round overlaps with earlier plans but narrows the priority to what was still visible on the newest APK: orientation behavior, preview alignment, rail IA, panel bounds, quick-panel density, and mode-track interaction.

## Current Code Facts

- `activity_main.xml` keeps `PreviewView` and `PreviewOverlayView` full-screen with `app:scaleType="fillCenter"`.
- `PreviewOverlayView` exists separately from `PreviewView`; any frame/grid math must stay aligned with the actual `PreviewView` content transform.
- `floatingUtilityGroup` currently contains `buttonFilterEntry`, `buttonQuickLauncher`, `buttonLensLabEntry`, and hidden dev entry.
- `CameraCockpitRenderModel` still models right-rail entries for `FilterLab`, `LensLab`, `QuickBubble`, `Settings`, and hidden `DevConsole`, while `activity_main.xml` only has concrete rail buttons for filter, quick, lens, and dev.
- `buttonSettingsEntry` remains in the top panel XML but `MainActivity.renderPanelVisibility()` forces it `GONE`.
- `quickBubblePanel` is a vertical fixed-width group. Buttons are mostly `64dp x 44dp`; dynamic labels such as `网格 ${value}`, `实况 ${value}`, and `定时 ${value}` can truncate.
- `filterPanel` is a bottom-constrained `NestedScrollView` with `layout_height="wrap_content"` and no explicit top/bottom bounded height, so large content can cover too much of the cockpit.
- `modeTrackScroll` contains `Button` chips using `wrap_content`. `bindModeTrack()` consumes touch manually with `20dp` slop and returns mixed `true/false` during move, which can make scroll/tap arbitration feel unreliable.

## Work Packages

1. [Landscape Preview Alignment And Rotation](./2026-05-22-landscape-preview-alignment-and-rotation.md)
   - Add orientation-aware control rotation without rearranging the cockpit topology.
   - Align overlay/frame/grid math with the actual `PreviewView` content rect.
   - Provide non-multimodal geometry tests for the reported "imaging area lower than preview" class of bug.

2. [Rail And Color Lab Entry Consolidation](./2026-05-22-rail-and-color-lab-entry-consolidation.md)
   - Restore `色彩实验室`/`镜头实验室` as a top-right entry as requested.
   - Reduce right-side rail to a small, predictable set of utilities.
   - Keep `风格` and color/palette tuning conceptually separate.

3. [Quick And Secondary Panel Bounds](./2026-05-22-quick-and-secondary-panel-bounds.md)
   - Fix quick-panel truncation by changing fixed narrow buttons into compact state rows/chips.
   - Bound left/secondary panels to the safe visible area.
   - Prevent panels from covering critical controls or exceeding screen bounds.

4. [Mode Track Legibility And Hit Targets](./2026-05-22-mode-track-legibility-and-hit-targets.md)
   - Make mode labels clearer and visually stronger.
   - Improve tap accuracy and scroll/tap arbitration.
   - Add deterministic tests around route dispatch and hit-target dimensions.

5. [Multimodal Visual QA For Fourth Feedback](./2026-05-22-fourth-feedback-multimodal-visual-qa.md)
   - Execute after text-only implementation.
   - Requires screenshots/recordings to judge final visual alignment, panel occlusion, and landscape feel.

## Recommended Dependency Order

1. Rail IA first, because it decides which controls live in top vs right rail.
2. Quick/panel bounds next, because it reduces the worst occlusion and truncation.
3. Mode track hit targets next, because it is a focused interaction fix.
4. Landscape/preview geometry next if it can be owned by one integrator; it touches layout, overlay geometry, and orientation transforms.
5. Multimodal QA last, on a real-device APK with all relevant changes.

If multiple agents work in parallel, do not let more than one agent own `MainActivity.kt` at the same time without an integrator. Prefer splitting by XML/resources/render-model tests first, then one integration pass.

## Global Verification

Run the focused commands in each plan. After a meaningful closed loop, run:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

This package changes product/UI plans only. It does not authorize a new project stage and does not change the Stage 7 architecture boundary.
