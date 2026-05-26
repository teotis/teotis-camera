# Style And Color Lab IA Plan

> **For agentic workers:** This is a self-contained implementation handoff for a non-multimodal agent. It covers naming, routing, render models, and text-only behavior. Visual product quality is deferred to multimodal QA. Run shell commands through `rtk`.

**Goal:** Split the current overloaded `色调/滤镜` concept into two user-facing surfaces: `风格` for style/filter/bokeh-like capture looks, and `镜头实验室` for color palette/tuning controls that affect preview and saved output.

**Architecture:** Keep existing `FilterProfile`/`FilterRenderSpec` settings and media postprocessor path. This plan changes product IA and routing first; it does not create a second effect engine. Preview effect remains adapted from `activeEffectSpec`; saved output remains handled by media postprocessors.

**Tech Stack:** Kotlin render models, Android XML Views, `AppTextResolver`, `SessionSettingsManager`, filter/effect tests.

---

## Product Interpretation

The user distinguished:

- **风格**: a higher-level shooting look. It can combine filter choice, ISO/exposure tendencies, vignette, bokeh/light-spot behavior, portrait profile, or other mode/product choices.
- **色彩/调色**: post-capture/render tuning. Palette changes should affect preview and saved output but should not be the same thing as choosing a mode style.

The current UI calls the first-screen entry `色调`, but the panel mixes filters, palette, advanced parameters, default state, and selected-profile internals. This makes the surface hard to understand.

## Required Behavior

- First-screen side/left rail entry becomes `风格`.
- `风格` panel shows mode-relevant styles/looks:
  - filter roster,
  - style presets,
  - portrait bokeh/light-spot entry if available,
  - concise selected state.
- `镜头实验室` is restored as the color/palette tuning surface:
  - palette/tone/color controls,
  - advanced render parameters,
  - custom save,
  - preview and saved-output effect requirements.
- Selected filter/style rows should not show verbose internal numeric text such as `B 0 | C 1.01 | S 1.01 | W 0 | Mono 0.00 | Vig 0.00` as the primary selected label.
- Chinese labels must be product-facing:
  - `风格`
  - `镜头实验室`
  - `滤镜`
  - `调色板`
  - `进阶`
  - `使用此风格`
  - `保存为自定义`

## Current Code Facts

- `CameraCockpitRenderModel` first right-rail entry uses `text.tone()` and `CockpitPanelRoute.FilterLab`.
- `activity_main.xml` contains `buttonFilterEntry`, `filterPanel`, `filterPaletteSurface`, and filter sections.
- `SessionUiRenderModel.kt` has `FilterLabRenderModel`, `FilterAdjustmentPanelRenderModel`, and helpers around `FilterRenderSpec`.
- `PhotoAlgorithmPostProcessor` applies `FilterRenderSpec` metadata to saved photos.
- Preview rendering is represented by `PreviewEffectAdapter` and `PreviewEffectRenderModel`.

## Files

Modify:

- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionSettingsManagerTest.kt`
- `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`

Do not modify low-level CameraX adapter code in this IA pass.

## Implementation Tasks

- [ ] **Step 1: Add explicit text resolver methods**

In `AppTextResolver` add methods instead of overloading `tone()`:

```kotlin
open fun styleEntry(): String = str(R.string.button_style_entry, "Style")
open fun lensLabEntry(): String = str(R.string.button_lens_lab_entry, "Lens Lab")
open fun stylePanelTitle(): String = str(R.string.style_panel_title, "Style")
open fun lensLabPanelTitle(): String = str(R.string.lens_lab_panel_title, "Lens Lab")
open fun paletteSectionTitle(): String = str(R.string.filter_section_palette, "Palette")
```

Add Chinese strings:

```xml
<string name="button_style_entry">风格</string>
<string name="button_lens_lab_entry">镜头实验室</string>
<string name="style_panel_title">风格</string>
<string name="lens_lab_panel_title">镜头实验室</string>
<string name="button_use_this_style">使用此风格</string>
```

English:

```xml
<string name="button_style_entry">Style</string>
<string name="button_lens_lab_entry">Lens Lab</string>
<string name="style_panel_title">Style</string>
<string name="lens_lab_panel_title">Lens Lab</string>
<string name="button_use_this_style">Use Style</string>
```

- [ ] **Step 2: Rename the first rail route to Style without breaking internals**

Low-churn path:

- Keep `CockpitPanelRoute.FilterLab` internally.
- Change visible label from `text.tone()` to `text.styleEntry()`.
- Rename local variables only where it improves clarity; avoid broad churn.

Test:

```kotlin
val model = cameraCockpitRenderModel(state, text, strings)
assertEquals("风格", model.rightRail.entries.first().label)
```

If `TestAppTextResolver` is English, assert `Style`.

- [ ] **Step 3: Restore a separate `镜头实验室` entry**

Add a visible route entry for `镜头实验室` only if it can open a distinct panel. Do not reuse the settings panel.

Preferred route model:

```kotlin
sealed interface CockpitPanelRoute {
    data object StyleLab : CockpitPanelRoute
    data object LensLab : CockpitPanelRoute
    data object QuickBubble : CockpitPanelRoute
    data class Settings(...) : CockpitPanelRoute
    data object DevConsole : CockpitPanelRoute
}
```

Low-churn alternative:

- Keep `FilterLab` as the style panel.
- Add `LensLab` as a new route that shows only palette/advanced tuning portions from the existing filter panel.

Acceptance:

- `风格` and `镜头实验室` do not open identical content.
- `设置` stays separate from both.

- [ ] **Step 4: Split render models by role**

In `SessionUiRenderModel.kt`, separate current filter lab fields into two render models:

```kotlin
internal data class StyleLabRenderModel(
    val headline: String,
    val currentStyleLabel: String,
    val filterFamilies: List<FilterFamilyTabRenderModel>,
    val styleItems: List<FilterProfileItemRenderModel>,
    val editingEnabled: Boolean
)

internal data class LensLabRenderModel(
    val headline: String,
    val currentColorLabel: String,
    val palette: FilterAdjustmentPanelRenderModel,
    val advancedControls: List<FilterAdjustmentControlRenderModel>,
    val editingEnabled: Boolean
)
```

If this is too large, create wrapper functions over the existing model:

```kotlin
internal fun styleLabRenderModel(...): FilterLabRenderModel
internal fun lensLabRenderModel(...): FilterLabRenderModel
```

But tests must prove the two panels expose different primary sections.

- [ ] **Step 5: Simplify selected style text**

Selected style cards should show:

- title: localized look name,
- subtitle: one short description or family,
- state badge: `当前` or `已选`,
- action: `使用此风格`.

Do not put raw parameter strings in the selected card title or action label. Move raw render spec values to debug/dev or an optional advanced detail area.

Add tests:

```kotlin
assertFalse(model.items.any { it.title.contains("B ") && it.title.contains("C ") })
assertFalse(model.items.any { it.actionLabel.contains("\n") })
```

- [ ] **Step 6: Make palette changes explicitly affect preview and saved output**

Keep existing paths:

- Preview: `PreviewEffectAdapter.adapt(state.activeEffectSpec)`
- Saved output: `PhotoAlgorithmPostProcessor` reads `FilterRenderSpec.fromMetadataTags(...)`

Add or strengthen tests:

```kotlin
@Test
fun `lens lab palette render spec is present in capture metadata`() { ... }

@Test
fun `custom palette metadata is rendered by photo algorithm postprocessor`() { ... }
```

If capture metadata already has this, assert it with an existing mode/session test rather than changing implementation.

- [ ] **Step 7: Verify**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionSettingsManagerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Manual Smoke

- First-screen entry shows `风格`.
- `风格` opens style/filter choices.
- `镜头实验室` opens palette/color tuning, not general settings.
- Dragging palette still creates/updates a custom render spec through existing settings manager behavior.
- Capture with custom palette still reaches `algorithm-render:applied:<profile-id>`.

## Non-Goals

- Do not build a full computational style engine in this pass.
- Do not implement real ISO/exposure auto-policy as part of `风格`; only IA and existing metadata wiring.
- Do not move media rendering out of the media pipeline.
- Do not judge visual color science quality in this non-multimodal plan.

