# Fifth Feedback: Quick Panel Frame Ratio Sheet

> **For text-only agents:** Implement a deterministic panelized quick surface. Do not judge visual polish beyond the textual constraints here. Use `rtk` for all commands.

## Goal

Replace the current floating quick-button stack with a compact panel/sheet that preserves frame-ratio choices and is easier to scan.

## Problems To Fix

- Current quick panel is a vertical floating stack over the preview.
- The frame-ratio sub-options can disappear or become hard to see in real use.
- The quick panel competes with the right rail instead of feeling like a secondary panel.
- Labels and selected states are not stable enough.

## Required Behavior

- Tapping `快捷` opens one compact quick panel, not a scattered stack of floating buttons.
- Quick panel includes rows for:
  - grid,
  - image quality,
  - frame ratio,
  - live,
  - timer.
- Frame ratio row must always show available options, e.g. `4:3 / 16:9 / 1:1`.
- Current selected ratio must be visibly selected.
- Disabled options must remain visible with a reason or disabled state; do not make them vanish.
- Panel should be bounded and should not cover shutter or mode/zoom controls.

## Suggested Implementation

1. Change `quickBubblePanel` content from stacked buttons to a small sheet layout:
   - row title,
   - current value,
   - segmented chips where needed.

2. For frame ratio:
   - Keep `buttonFrameRatio43`, `buttonFrameRatio169`, `buttonFrameRatio11` or replace with generated chips.
   - Ensure their labels are static string resources or stable literals.
   - Active chip gets selected background.
   - Unsupported modes disable the row but keep labels visible.

3. Constrain panel:
   - width around `220dp-280dp` or match a sensible max width,
   - height wrap content with max height,
   - anchored near right rail but not overlaying shutter.

4. Update tests:
   - render model exposes all quick rows.
   - frame ratio options remain present even when one is selected.
   - disabled frame ratio state has a reason.

## Files To Inspect Or Modify

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/drawable/*quick*`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

## Acceptance Criteria

- Quick panel appears as one coherent panel/sheet.
- Frame ratio options do not disappear.
- Selected ratio is visually distinct.
- Panel does not cover shutter or bottom mode/zoom cockpit.
- All labels fit in Chinese on narrow portrait screens.

## Verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Manual/multimodal follow-up: open `快捷` on 参考设备 and verify `4:3 / 16:9 / 1:1` remain visible.
