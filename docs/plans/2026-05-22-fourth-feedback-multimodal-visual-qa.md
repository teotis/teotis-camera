# Multimodal Visual QA For Fourth Feedback

> **For multimodal agents only:** This plan requires looking at screenshots, screen recordings, or real-device photos. Do not assign it to a text-only implementation agent.

## Goal

Verify the fourth-round real-device fixes after text-only agents implement their plans. The key risks are visual/interaction issues that cannot be fully judged from source code.

## Inputs

Use the user's screenshots as baseline evidence:

- `<HOME>/Downloads/20260522-061623.jpg`
- `<HOME>/Downloads/20260522-061617.jpg`
- `<HOME>/Downloads/20260522-061620.jpg`

Also collect fresh screenshots/recordings from the new APK after implementation.

## Required Captures

Capture these states on a real device:

- portrait photo mode, no panel open,
- landscape left, no panel open,
- landscape right, no panel open,
- quick panel open in portrait,
- quick panel open in landscape,
- style panel open,
- color/lens lab panel open from top-right entry,
- mode track tap/scroll recording,
- frame ratio `4:3`,
- frame ratio `16:9`,
- frame ratio `1:1`,
- grid on/off with each frame ratio if available.

## Visual Checks

### Landscape interaction

Pass criteria:

- UI topology remains recognizable from portrait.
- Text-bearing controls are readable/upright in landscape.
- Rotated controls do not overlap or clip.
- Tap targets remain reachable.

Fail examples:

- labels sideways when they should face the user,
- top/right controls collide,
- rotated text is clipped inside buttons.

### Preview and imaging alignment

Pass criteria:

- grid/frame appears centered on the visible imaging area.
- saved image framing matches the preview mask expectation.
- no obvious downward bias between preview frame and final saved crop.

Fail examples:

- overlay frame is centered but saved composition is lower,
- grid lines divide dimmed/non-imaging area,
- frame mask shifts when rotating device without changing ratio.

### Top bar, side rail, and lab entry

Pass criteria:

- top-left title shows only the app name, without `· 模式名`.
- top middle-right `色彩实验室` is visible and clearly labeled.
- top far-right `设置` is visible and clearly labeled.
- side rail is short and understandable: `风格`, `快捷`, and `Dev`.
- `风格`, `快捷`, `Dev`, `色彩实验室`, and `设置` do not feel like duplicate entries.

Fail examples:

- `色彩实验室` still appears only as side rail,
- right rail contains settings/lab labels that compete,
- title still shows `OpenCamera · 拍照` or similar mode suffix,
- two different entries open visually identical panels.

### Quick panel and secondary panels

Pass criteria:

- no Chinese label is ellipsized.
- quick values are legible.
- panels remain inside visible bounds.
- panels do not hide shutter/mode track unless intentionally modal and dismissible.

Fail examples:

- `...` appears in quick controls,
- panel bottom is off-screen,
- close/back affordance is unreachable.

### Mode track

Pass criteria:

- active mode is obvious.
- inactive labels are readable.
- mode switching feels accurate.
- horizontal scrolling does not accidentally switch modes.

Fail examples:

- active label is too faint,
- chip hit areas feel narrow,
- scroll gestures switch modes.

## Output

Produce a Markdown QA note with:

- APK/build identifier,
- device model and Android version,
- exact capture list,
- pass/fail table,
- screenshot references,
- top 5 remaining defects ordered by severity.

If a defect needs code follow-up, reference the relevant text-only plan:

- `2026-05-22-landscape-preview-alignment-and-rotation.md`
- `2026-05-22-rail-and-color-lab-entry-consolidation.md`
- `2026-05-22-quick-and-secondary-panel-bounds.md`
- `2026-05-22-mode-track-legibility-and-hit-targets.md`

## Non-Goals

- Do not implement fixes in this QA pass.
- Do not infer source-code ownership from visuals without checking the relevant plan.
- Do not replace deterministic geometry tests with screenshot judgment.
