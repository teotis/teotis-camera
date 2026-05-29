# Fifth Feedback: Top Bar And Rail IA Polish

> **For text-only agents:** This package does not require judging screenshots. It implements user-confirmed IA and layout constraints. Use `rtk` for all shell commands.

## Goal

Make the top bar and right rail feel like a camera cockpit instead of a row of equal bordered debug buttons.

## Problems To Fix

- Top-bar Chinese text is partially obstructed on 参考设备.
- `设置` and `色彩实验室` have the same visual length/weight, flattening hierarchy.
- Top bar should move slightly upward while respecting status/cutout safe area.
- Top buttons use outlined borders that conflict with the rest of the style.
- Right rail label `色调` should become `镜头`.
- The screen currently overuses bordered text pills.

## Required Behavior

### Top bar

- App name remains on the left.
- `色彩实验室` remains a top action, but should not visually fight with `设置`.
- `设置` should be visually lighter/shorter than `色彩实验室` where possible.
- Move the top row upward by a small amount, but do not collide with the status bar or recording indicator.
- Replace heavy outlined pill border with a quieter translucent chip style.
- Text must not clip, wrap, or be obscured on `540x1176` portrait.

### Right rail

- Rename visible right rail `色调` to `镜头`.
- Keep route semantics explicit. If existing route is `StyleLab`, decide whether the visible label is now `镜头` while still opening style/lens-look content, or whether a separate `Lens` product route is needed. Prefer the low-churn option for this package: label changes first, route stays stable.
- Right rail should remain short: `镜头 / 快捷 / Dev`.

## Suggested Implementation

1. Inspect:
   - `app/src/main/res/layout/activity_main.xml`
   - `app/src/main/res/values/dimens.xml`
   - `app/src/main/res/values/themes.xml`
   - `app/src/main/res/values/strings.xml`
   - `app/src/main/res/values-en/strings.xml`
   - `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
   - `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
   - related tests.

2. Add or adjust a top-action style:
   - no strong outline,
   - semi-transparent dark fill,
   - selected state can use a subtle accent underline/fill,
   - fixed/min width only where needed to prevent clipping,
   - no equal forced width for `设置` and `色彩实验室`.

3. Tune top row:
   - keep it within safe area,
   - reduce unnecessary horizontal padding,
   - use `maxLines=1`, `includeFontPadding=false` where appropriate,
   - set touch target height at least 32-36dp; avoid taller pills.

4. Rename rail string:
   - `button_palette_entry` or the rail-specific label should become `镜头` / `Lens`.
   - Do not change Color Lab label.
   - Add tests for cockpit rail labels.

## Acceptance Criteria

- On a `540x1176` portrait screen, `OpenCamera`, `色彩实验室`, and `设置` all fit and are readable.
- `设置` is not the same visual weight/width as `色彩实验室`.
- Top actions no longer use the old heavy outlined pill look.
- Right rail shows `镜头 / 快捷 / Dev`.
- No user-facing `色调` remains on the right rail.

## Verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Manual/multimodal follow-up: 参考设备 screenshot to verify no clipping under the recording/status bar.
