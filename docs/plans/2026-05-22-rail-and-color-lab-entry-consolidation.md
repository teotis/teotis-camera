# Rail And Color Lab Entry Consolidation Plan

> **For agentic workers:** This is a text-only IA and routing handoff. It does not require judging screenshots. Use `rtk` for shell commands.

**Goal:** Restore the top-bar `色彩实验室` and `设置` entries, remove lab/settings confusion from the side rail, and keep the title area clean.

## Problem Statement

The latest product direction is:

- the top-left title area shows only the app name,
- the top middle-right entry opens `色彩实验室`,
- the top far-right entry opens `设置`,
- the side rail contains only shooting-surface utilities.

The latest APK still exposes lab/settings-like functions through a confusing right-side rail. The rail currently mixes concepts:

- style/filter,
- quick controls,
- lens/color lab,
- settings/dev remnants.

This makes the cockpit hard to scan and conflicts with the requested split:

- `风格`: style/filter/bokeh-like look selection.
- `色彩实验室`: a compact post-render color module that affects preview and saved output.
- `快捷`: short one-hop camera controls.
- `设置`: broader persistent settings.
- `Dev`: always available as a first-class dev/diagnostics entry; do not hide it only because a build is "release-like" unless product policy later changes.

## Current Code Facts

- `CameraCockpitRenderModel` includes right-rail entries for `FilterLab`, `LensLab`, `QuickBubble`, `Settings`, and hidden `DevConsole`.
- `activity_main.xml` has concrete right-rail buttons: `buttonFilterEntry`, `buttonQuickLauncher`, `buttonLensLabEntry`, and hidden `buttonDevEntry`.
- `buttonSettingsEntry` exists in the top panel but `MainActivity.renderPanelVisibility()` forces it `View.GONE`.
- `strings.xml` currently includes `button_style_entry`, `button_lens_lab_entry`, `lens_lab_panel_title`, `button_palette_entry`, and `button_lens_rail`. Some labels are inconsistent; `button_lens_rail` currently maps to `设置`.

## Required Behavior

- Top bar:
  - left: app name only, such as `OpenCamera`.
  - remove the current `· 模式名` suffix from `titleText`.
  - middle-right: `色彩实验室`.
  - far-right: `设置`.
- Side rail should only contain shooting-surface utilities:
  - `风格`
  - `快捷`
  - `Dev`
- Do not put `色彩实验室` or `设置` in the side rail.
- Do not use `镜头实验室` for this round; the product label is `色彩实验室`.
- `风格` and `色彩实验室` panels must not open identical content.
- `Dev` remains visible according to product request; do not treat it as a debug-build-only item in this IA pass.

## Recommended IA

Use this structure for the next APK:

- Top panel:
  - left: app name only
  - middle-right: `色彩实验室`
  - far-right: `设置`
- Right rail:
  - `风格`
  - `快捷`
  - `Dev`
- Bottom/cockpit:
  - lens-facing button remains true camera lens switch
  - shutter and mode track unchanged except for their own plans
- Settings:
  - broad persistent settings only
- Style panel:
  - preset style list,
  - filter list,
  - each style/filter supports one strength control.
- Color Lab panel:
  - one two-dimensional palette, inspired by Apple photographic styles and vivo blueprint palette,
  - horizontal axis: color tendency,
  - vertical axis: tone/shadow-highlight relationship,
  - no exposed professional parameter list.

## Implementation Plan

### Step 1: Normalize route names

Low-churn option:

- Keep `CockpitPanelRoute.FilterLab` internally for style until a larger refactor.
- Keep `CockpitPanelRoute.LensLab` internally for color/lab.
- Ensure visible labels are:
  - `FilterLab` -> `风格`
  - `LensLab` -> `色彩实验室`

Cleaner option:

```kotlin
sealed class CockpitPanelRoute {
    data object None : CockpitPanelRoute()
    data object QuickBubble : CockpitPanelRoute()
    data object DevConsole : CockpitPanelRoute()
    data class Settings(val subpage: SettingsSubpage = SettingsSubpage.ROOT) : CockpitPanelRoute()
    data object StyleLab : CockpitPanelRoute()
    data object ColorLab : CockpitPanelRoute()
}
```

Only take the cleaner option if the owner can update tests and avoid broad churn.

### Step 2: Rebuild the top bar

Replace the current single hidden top-right settings button with two explicit top-bar actions:

- `buttonColorLabEntry` near the middle-right.
- `buttonSettingsEntry` as the far-right action.

Also change title rendering:

```kotlin
titleText.text = getString(R.string.app_name)
```

Do not append `· ${modeName}` after the app name.

Preferred:

```xml
<Button
    android:id="@+id/buttonColorLabEntry"
    style="@style/Widget.OpenCamera.PillButton"
    android:layout_width="wrap_content"
    android:layout_height="32dp"
    android:text="@string/button_color_lab_entry" />

<Button
    android:id="@+id/buttonSettingsEntry"
    style="@style/Widget.OpenCamera.PillButton"
    android:layout_width="wrap_content"
    android:layout_height="32dp"
    android:text="@string/button_settings_entry" />
```

In `MainActivity.renderPanelVisibility()`, remove the unconditional `buttonSettingsEntry.visibility = View.GONE`. Set active alpha/selection for both top actions based on `ColorLab/LensLab` and `Settings` routes.

### Step 3: Reduce right rail buttons

In XML and binding:

- Keep `buttonFilterEntry` as `风格`.
- Keep `buttonQuickLauncher` as `快捷`.
- Remove or hide `buttonLensLabEntry` from the right rail after the top lab entry works.
- Keep `buttonDevEntry` visible according to product policy.
- Do not use `button_lens_rail` to mean `设置`.
- Do not put `色彩实验室` in the side rail.

In `CameraCockpitRenderModel`, make `rightRail.entries` match real XML:

- `StyleLab/FilterLab`
- `QuickBubble`
- `DevConsole`

Do not include `Settings` or `ColorLab/LensLab` in right rail.

### Step 4: Split panel role at render time

When lab route is active:

- Header title must be `色彩实验室`.
- Primary content should be a compact two-dimensional palette, not the same list as `风格`.
- The palette is a post-render module:
  - horizontal axis: color tendency, roughly cool/warm, hue shift, and natural vibrance;
  - vertical axis: tone, roughly shadow/highlight contrast and midtone lift/depth;
  - output maps to one render spec consumed by preview and saved output.
- Do not expose a professional list of `white balance / color temperature / highlights / shadows / saturation / sharpness / vignette / grain` as separate buttons in this panel.
- If existing implementation must share `filterPanel`, hide style-only rows for lab route and replace advanced-control rows with the palette surface.

When style route is active:

- Show preset style list.
- Show filter list.
- Each preset/filter supports one strength control.
- Do not expose separate bokeh, light spot, vignette, soft glow, grain, or similar effect components as independent rows; those should be folded into style presets where needed.

Acceptance:

- Tapping `风格` opens style/filter choice.
- Tapping top middle-right `色彩实验室` opens the two-dimensional palette.
- Tapping top far-right `设置` opens settings.
- Tapping `快捷` opens quick controls.
- Side rail contains `风格`, `快捷`, and `Dev`, but not `设置` or `色彩实验室`.
- Title text does not contain `· 模式名`.

### Step 5: Tests

Add or update render-model tests:

- top status/title label is app name only.
- top actions include `色彩实验室` and `设置`.
- right rail entries do not contain `Settings` or `ColorLab/LensLab`.
- right rail entries contain `风格` and `快捷`.
- right rail entries contain visible `Dev`.
- `FilterLab/StyleLab` and `LensLab/ColorLab` expose different panel role flags.
- `ColorLab/LensLab` primary panel role is two-dimensional palette, not advanced parameter list.

## Files To Inspect Or Modify

- `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

## Non-Goals

- Do not implement full Apple-style semantic segmentation or a large 3D LUT matrix in this IA pass.
- Do not move CameraX/device behavior.
- Do not make right rail a catch-all settings drawer.

## Verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```
