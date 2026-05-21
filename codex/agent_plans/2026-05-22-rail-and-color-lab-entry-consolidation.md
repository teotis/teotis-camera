# Rail And Color Lab Entry Consolidation Plan

> **For agentic workers:** This is a text-only IA and routing handoff. It does not require judging screenshots. Use `rtk` for shell commands.

**Goal:** Restore the requested top-right `色彩实验室`/`镜头实验室` entry and reduce the current right-side rail confusion.

## Problem Statement

The user expected the top-right lab entry to return, but the latest APK still exposes lab-like functions as a right-side rail. The rail now mixes concepts:

- style/filter,
- quick controls,
- lens/color lab,
- settings/dev remnants.

This makes the cockpit hard to scan and conflicts with the requested split:

- `风格`: style/filter/bokeh-like look selection.
- `色彩实验室` or `镜头实验室`: palette/color tuning that affects preview and saved output.
- `快捷`: short one-hop camera controls.
- `设置`: broader persistent settings.

## Current Code Facts

- `CameraCockpitRenderModel` includes right-rail entries for `FilterLab`, `LensLab`, `QuickBubble`, `Settings`, and hidden `DevConsole`.
- `activity_main.xml` has concrete right-rail buttons: `buttonFilterEntry`, `buttonQuickLauncher`, `buttonLensLabEntry`, and hidden `buttonDevEntry`.
- `buttonSettingsEntry` exists in the top panel but `MainActivity.renderPanelVisibility()` forces it `View.GONE`.
- `strings.xml` currently includes `button_style_entry`, `button_lens_lab_entry`, `lens_lab_panel_title`, `button_palette_entry`, and `button_lens_rail`. Some labels are inconsistent; `button_lens_rail` currently maps to `设置`.

## Required Behavior

- Top-right entry should be restored as the lab entry:
  - Preferred visible label: `色彩实验室` if the product meaning is color tuning.
  - Alternative label: `镜头实验室` if the existing product direction keeps lens experiments broader.
  - Choose one label and use it consistently across XML, render model, and panel title.
- Right rail should only contain high-frequency one-tap utilities:
  - `风格`
  - `快捷`
  - optionally dev entry when debug-visible
- Do not put both `色彩实验室` and `镜头实验室` in the right rail.
- `设置` should be a settings entry, not mislabeled as lens/lab.
- `风格` and lab panels must not open identical content.

## Recommended IA

Use this structure for the next APK:

- Top panel:
  - left/center: app title or current mode status
  - right: `色彩实验室` entry
- Right rail:
  - `风格`
  - `快捷`
  - hidden `Dev` only when applicable
- Bottom/cockpit:
  - lens-facing button remains true camera lens switch
  - shutter and mode track unchanged except for their own plans
- Settings:
  - broad persistent settings only

## Implementation Plan

### Step 1: Normalize route names

Low-churn option:

- Keep `CockpitPanelRoute.FilterLab` internally for style until a larger refactor.
- Keep `CockpitPanelRoute.LensLab` internally for color/lab.
- Ensure visible labels are:
  - `FilterLab` -> `风格`
  - `LensLab` -> `色彩实验室` or `镜头实验室`

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

### Step 2: Restore top-right lab button

Use the existing `buttonSettingsEntry` slot or create a clearly named replacement:

- Rename XML id only if the integration owner can update all references.
- Otherwise reuse `buttonSettingsEntry` as a top-right lab button temporarily but update comments/tests so it no longer behaves like settings.

Preferred:

```xml
<Button
    android:id="@+id/buttonColorLabEntry"
    style="@style/Widget.OpenCamera.PillButton"
    android:layout_width="wrap_content"
    android:layout_height="32dp"
    android:text="@string/button_color_lab_entry" />
```

In `MainActivity.renderPanelVisibility()`, remove the unconditional `GONE` behavior and set active alpha/selection based on the lab route.

### Step 3: Reduce right rail buttons

In XML and binding:

- Keep `buttonFilterEntry` as `风格`.
- Keep `buttonQuickLauncher` as `快捷`.
- Remove or hide `buttonLensLabEntry` from the right rail after the top lab entry works.
- Do not use `button_lens_rail` to mean `设置`.

In `CameraCockpitRenderModel`, make `rightRail.entries` match real XML:

- `StyleLab/FilterLab`
- `QuickBubble`
- `DevConsole` hidden

Do not include `Settings` in right rail unless there is a real visible settings button.

### Step 4: Split panel role at render time

When lab route is active:

- Header title must be `色彩实验室`/`镜头实验室`.
- Primary content should be palette/color tuning, not the same list as `风格`.
- If existing implementation must share `filterPanel`, hide style-only rows for lab route and hide palette-only rows for style route.

Acceptance:

- Tapping `风格` opens style/filter choice.
- Tapping top-right lab opens palette/color adjustment.
- Tapping `快捷` opens quick controls.
- No visible rail item says `设置` unless it opens settings.

### Step 5: Tests

Add or update render-model tests:

- top status/lab entry label is `色彩实验室` or `镜头实验室`.
- right rail entries do not contain `Settings`.
- right rail entries contain `风格` and `快捷`.
- `FilterLab/StyleLab` and `LensLab/ColorLab` expose different panel role flags.

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

- Do not implement new color science in this IA pass.
- Do not move CameraX/device behavior.
- Do not make right rail a catch-all settings drawer.

## Verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```
