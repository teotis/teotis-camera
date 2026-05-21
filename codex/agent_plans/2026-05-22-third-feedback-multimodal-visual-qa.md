# Third Feedback Multimodal Visual QA Plan

> **For multimodal-capable agents only.** Do not assign this document to a text-only implementation agent. This plan requires screenshots, screen recordings, or saved-image visual comparison.

**Goal:** Validate the real-device visual behavior and saved-photo output for the third 2026-05-22 feedback round after the text-only implementation plans land.

---

## Execute After

- `2026-05-22-watermarked-thumbnail-first-feedback.md`
- `2026-05-22-landscape-grid-frame-ratio-geometry.md`
- `2026-05-22-panel-state-deduplication.md`
- `2026-05-22-style-and-color-lab-ia.md`
- `2026-05-22-shutter-button-visual-refresh.md`

## Required Inputs

Collect:

- APK build timestamp/hash.
- Device model and Android version.
- Screen recording in portrait:
  - open app,
  - open `快捷`,
  - switch `4:3 / 16:9 / 1:1`,
  - enable grid modes,
  - capture with watermark enabled,
  - open `设置`, `风格`, and `镜头实验室`.
- Screen recording in landscape:
  - rotate device,
  - confirm preview frame,
  - confirm button/text readability,
  - switch frame ratios and grid.
- Saved JPEGs for:
  - watermark enabled,
  - `4:3`,
  - `16:9`,
  - `1:1`,
  - strong palette/color adjustment.
- Fresh debug log if available.

## Visual QA Checklist

### 1. Watermarked Thumbnail First Feedback

Pass criteria:

- With an expanded-frame or visible watermark enabled, the thumbnail does not show a raw no-watermark preview first.
- The first visible new thumbnail is either delayed until final saved media or already visibly watermarked.
- After save, the thumbnail remains on latest saved media.
- No later preview snapshot replaces the saved-media thumbnail.

Record:

- Time from shutter tap to first thumbnail update.
- Whether the first update is raw preview, watermarked feedback, or saved media.

### 2. Landscape Adaptation

Pass criteria:

- Main UI topology stays recognizable.
- Buttons/text are readable after rotation.
- Preview frame becomes landscape-aware.
- Controls do not incoherently overlap the active capture frame.
- No important labels are clipped by status/navigation bars.

### 3. Composition Grid

Pass criteria:

- Grid lines divide the active capture frame, not the full screen.
- `3x3` lines appear at visual thirds of the active frame.
- Golden-ratio grid, if enabled, is visibly distinct and still inside the active frame.
- Outside-dimmed areas do not receive grid lines.
- Grid color/alpha is visible but not distracting.

Reference direction:

- Prefer vivo X300 Ultra-like and iPhone-like grid restraint: thin, stable, aligned to the image area, no heavy full-screen border.

### 4. Frame Ratio And Sensor-Area Impression

Pass criteria:

- Portrait `16:9` uses a tall frame, not a wide letterbox that wastes vertical sensor area.
- Landscape `16:9` uses a wide frame.
- Saved JPEG ratio matches the selected visible frame within reasonable tolerance.
- Quick UI makes selected ratio obvious.

### 5. Panel State Deduplication

Pass criteria:

- `设置` header does not show aggregate state collections.
- `通用 / 拍照 / 录像` section headers do not repeat all child states.
- Each child item shows its own value/status.
- No triple-display of the same state remains in the same panel.

### 6. Style And Color Lab IA

Pass criteria:

- First-screen rail entry says `风格`.
- `风格` feels like choosing a look/style, not a raw color math page.
- `镜头实验室` is visible/reachable and contains palette/color tuning.
- Selected style row text is concise.
- Palette tuning visibly affects preview and saved output when a strong adjustment is used.

### 7. Shutter Button

Pass criteria:

- The shutter reads as a camera button, not a flat text disk.
- No clipped text appears inside the shutter.
- Pressed and disabled states are visible.
- Recording state is clearly distinct.
- Button remains easy to tap.

## Report Template

Save the report as:

```text
codex/v2_ui/visual_reviews/2026-05-22-third-feedback-qa-report.md
```

Use:

```markdown
# 2026-05-22 Third Feedback QA Report

## Device And Build

- Device:
- Android:
- APK:
- Time:

## Pass/Fail Summary

- Watermarked thumbnail:
- Landscape:
- Grid:
- Frame ratio:
- Panel dedup:
- Style/color IA:
- Shutter button:

## Evidence

- Screenshots:
- Screen recordings:
- Saved JPEGs:
- Debug log:

## Findings

1. Record each issue with screen/timestamp and expected behavior.

## Follow-Up Engineering Tasks

1. Record each task with owning plan/file area.
```

## Non-Goals

- Do not implement code changes in this QA pass unless explicitly requested.
- Do not require pixel-perfect matching to vivo/iPhone references.
- Do not judge color science beyond confirming that selected palette/style effects are visible and routed.

