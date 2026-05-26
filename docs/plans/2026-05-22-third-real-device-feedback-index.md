# 2026-05-22 Third Real-Device Feedback Index

> **For agentic workers:** This is a handoff index, not an implementation patch. Pick one linked plan, keep changes scoped to that plan, and run the listed focused verification before any broader Stage 7 verification. Use `rtk` for every shell command.

## Context

The latest APK was tested on a real device and exposed eight user-visible product issues:

1. After capture, the thumbnail first shows a no-watermark image and then jumps to a watermarked image. It should show the watermarked result from the beginning.
2. Landscape is not adapted. Preferred behavior: keep the main UI layout stable, rotate buttons/text, and make the preview frame landscape-aware.
3. The composition grid looks poor. It should divide the actual captured preview area, not the full screen or dimmed area.
4. Panel headers/footers show aggregated setting-state strings. Each child item should show its own state. The settings panel currently has triple duplication.
5. Frame ratio handling is wrong. Quick UI and actual frame result must respect sensor-area usage; in portrait use, the long edge should stay vertical.
6. The color secondary panel needs a full cleanup: translations, uneven top items, noisy text, and selected-filter copy.
7. Rename the left `色彩/色调` concept to `风格`, put style modes and bokeh-like effects there, and restore the top/right `镜头实验室` as a place for palette/color tuning that affects preview and saved output.
8. The shutter button graphic needs a better button-like visual treatment.

The screenshots attached to the user report are visual evidence, but most implementation work can be specified textually. Anything requiring screenshot judgment or saved-image visual comparison is isolated into the multimodal follow-up plan.

## Current Code Facts

- Transient capture feedback is generated in `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` by `captureCaptureFeedbackSnapshot()`, which saves a raw preview bitmap before media postprocessors run.
- `app/src/main/java/com/opencamera/app/MainActivity.kt` renders `pendingCaptureFeedback` before `latestThumbnailSource`, so the first visible thumbnail can be a pre-watermark preview snapshot.
- Saved-photo watermarking happens later in `PhotoWatermarkPostProcessor`, after frame ratio, portrait, algorithm/filter, and before selfie mirror in `AppContainer`.
- `PreviewOverlayView.drawGrid()` currently draws grid lines against the whole view. It does not use `computePreviewFrameRect()` or the active frame rect.
- Frame-ratio UI already has direct `4:3 / 16:9 / 1:1` buttons, but frame orientation and grid alignment still need tightening.
- `activity_main.xml` has no landscape-specific resource variant and `MainActivity` has no rotation/orientation render model.
- The panel IA cleanup from the previous round is partly present: right rail is now `色调 / 快捷 / 设置`, but panel summaries still include aggregate state strings in headers/footers.
- `CameraCockpitRenderModel` still models the left rail entry as `tone`; the requested product split is now `风格` versus `镜头实验室/色彩调色`.
- The shutter is a plain oval drawable (`bg_shutter_circle.xml`) applied to a text `Button`.

## Work Packages

1. [Watermarked Thumbnail First Feedback](./2026-05-22-watermarked-thumbnail-first-feedback.md)
   - Make the first visible post-capture thumbnail represent the final watermarked output when watermark is enabled.
   - Avoid the no-watermark to watermarked thumbnail jump.
   - Keep session thumbnail precedence owned by the Session Kernel.

2. [Landscape, Grid, And Frame Ratio Geometry](./2026-05-22-landscape-grid-frame-ratio-geometry.md)
   - Keep portrait UI topology while adding orientation-aware rotation for controls/text.
   - Make preview/frame ratio long-edge behavior explicit.
   - Draw grid lines within the actual active frame rect.

3. [Panel State Deduplication](./2026-05-22-panel-state-deduplication.md)
   - Remove aggregate setting-state collections from panel headers/footers.
   - Keep each child item responsible for its own state.
   - Fix `设置 / 拍照 / 录像` triple-display behavior.

4. [Style And Color Lab IA](./2026-05-22-style-and-color-lab-ia.md)
   - Rename the first-screen left/right-rail `色调` entry to `风格`.
   - Move filter/style/bokeh-facing choices into `风格`.
   - Restore `镜头实验室` as the color/palette tuning home, with preview and saved-output effect requirements.

5. [Shutter Button Visual Refresh](./2026-05-22-shutter-button-visual-refresh.md)
   - Replace the plain purple/text shutter with a camera-like button visual using drawable/state resources and a compact render contract.
   - Text-only agents can implement dimensions, state drawables, and tests; final taste validation is multimodal.

6. [Multimodal Visual QA For Third Feedback](./2026-05-22-third-feedback-multimodal-visual-qa.md)
   - Execute after the text-only plans.
   - Requires screenshots, screen recordings, or saved JPEG comparison.

## Recommended Dependency Order

1. Watermarked thumbnail feedback first. It is a direct correctness issue and can be verified by state and pipeline notes.
2. Panel state deduplication next. It reduces noisy UI text before larger IA movement.
3. Landscape/grid/frame geometry next. It touches overlay math and activity layout behavior.
4. Style/color lab IA next. It is broader and changes product naming/routes.
5. Shutter visual refresh last, because it is mostly visual polish.
6. Run multimodal QA after at least one APK contains the relevant changes.

Avoid editing `MainActivity.kt` in parallel across packages without one integrator. Several plans touch that file; keep ownership explicit if multiple agents run concurrently.

## Global Verification

Focused commands are listed in each plan. After a meaningful closed loop, run:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

If Gradle shows transient Kotlin/build-directory errors in `~/.codex-build/OpenCamera`, rerun the smallest failed command serially before declaring a product regression.

