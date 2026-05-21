# Panel Entry Information Architecture Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if executing this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove duplicate and confusing panel entries by making the right-rail `镜头` entry become `设置`, extracting settings content out of `镜头实验室`, and keeping `快捷` as a small first-screen control surface without duplicate long-form settings.

**Architecture:** App shell owns cockpit panel routing. `SessionUiRenderModel` provides localized display models. Settings changes still flow through `SessionSettingsManager` and `PersistedSettingsAction`; panel routing must not become a second session kernel.

**Tech Stack:** Android Views/XML, Kotlin render models, `CockpitPanelRoute`, `AppTextResolver`, `SessionUiRenderModelTest`, `CameraCockpitRenderModelTest`.

---

## Evidence

User issues covered:

- `4`: 面板入口逻辑方案需要优化；“镜头”和“镜头实验室”重复；“镜头实验室”里包含了“设置”的内容；建议“镜头”改为“设置”并从“镜头实验室”中提取。
- `5`: “镜头”中的内容和“快捷”又有重复。

Current code evidence:

- `activity_main.xml` has both:
  - top `buttonSettingsEntry` text = `button_settings_entry` (`镜头实验室`)
  - right rail `buttonLensLabEntry` text = `button_lens_rail` (`镜头`)
- `MainActivity.bindActions()` wires both `buttonSettingsEntry` and `buttonLensLabEntry` to `toggleSettingsPanel()`.
- `CameraCockpitRenderModel` exposes both `topStatus.labEntryLabel = text.lensLab()` and a right-rail entry with `route = CockpitPanelRoute.Settings(), label = text.lensLab()`.
- `CockpitPanelRoute.Settings` currently contains `ROOT`, `PORTRAIT_LAB`, `WATERMARK_SELECTOR`, and `WATERMARK_DETAIL`, mixing general settings, product labs, and template detail pages.
- `sessionSettingsPageRenderModel()` headline is `text.lensLab()` and contains common/photo/video settings plus links to portrait/watermark labs.

## Proposed IA

Use these user-facing entries:

- Top pill: remove or demote. The first viewport should not have both `镜头实验室` and right-rail `设置` opening the same route.
- Right rail:
  - `色调`: filter/tone panel.
  - `快捷`: small, immediate controls only.
  - `设置`: general/default settings.
  - `开发`: debug-only.
- Settings root (`设置`):
  - `通用`: grid, shutter sound, selfie mirror, language if present.
  - `拍照`: default photo filter link, Live default, countdown, watermark link.
  - `录像`: video spec, audio profile, video tone/default filter.
- Labs:
  - `色调`: standalone first-screen tool for filters and palette.
  - `水印`: selector/detail under settings, but user-facing title should be `水印`, not `水印实验室` unless product copy explicitly wants "实验室".
  - `人像`: profile/beauty/bokeh defaults under settings.
- Lens switching:
  - Keep the bottom cockpit lens button for front/back camera switching.
  - Do not use `镜头` as the settings panel label.

## Required Behavior

- There is only one primary entry to settings, labeled `设置`.
- The `镜头` label is reserved for actual lens switching or lens-specific capability rows, not a settings panel entry.
- `快捷` contains only high-frequency one-tap controls and no duplicate long descriptions from settings.
- Settings root does not use `镜头实验室` as headline.
- Watermark/portrait subpages remain reachable but are clearly subpages, not mixed into the root summary as long prose.
- Internal names like `SettingsSubpage.WATERMARK_SELECTOR` can remain if changing them adds churn; user-facing labels must be fixed first.

## Files

Modify:

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt` only if route names need clarity.
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

## Implementation Tasks

- [ ] **Step 1: Rename user-facing settings entry**

Update strings:

```xml
<string name="button_settings_entry">设置</string>
<string name="button_lens_rail">设置</string>
<string name="button_settings_close">关闭设置</string>
<string name="settings_panel_content_description">相机设置</string>
```

In English:

```xml
<string name="button_settings_entry">Settings</string>
<string name="button_lens_rail">Settings</string>
<string name="button_settings_close">Close Settings</string>
<string name="settings_panel_content_description">Camera settings</string>
```

Consider adding dedicated methods in `AppTextResolver`:

```kotlin
open fun settingsEntry(): String = str(R.string.button_settings_entry, "Settings")
open fun closeSettings(): String = str(R.string.button_settings_close, "Close Settings")
```

Keep `lensLab()` only if a true future lens lab remains; do not use it for the settings root.

- [ ] **Step 2: Remove duplicate top settings pill or make it secondary**

Preferred product behavior:

- Hide `buttonSettingsEntry` in the top panel.
- Keep right rail `设置` as the single visible settings entry.

If removing the top pill requires too much XML churn:

- Keep it visible only as the same `设置` label.
- Ensure it is not simultaneously called `镜头实验室`.
- Acceptance still requires no user-facing duplication between `镜头实验室` and `镜头`.

- [ ] **Step 3: Update cockpit render model**

In `CameraCockpitRenderModel`:

- Replace `labEntryLabel` with `settingsEntryLabel` if the top pill remains.
- Right rail settings entry label should call `text.settingsEntry()` or equivalent, not `text.lensLab()`.
- Add a unit test:

```kotlin
val model = cameraCockpitRenderModel(state, text, strings)
assertEquals("设置", model.rightRail.entries.single { it.route is CockpitPanelRoute.Settings }.label)
assertFalse(model.rightRail.entries.any { it.label == "镜头实验室" })
```

- [ ] **Step 4: Reframe settings root copy**

In `sessionSettingsPageRenderModel()`:

- `headline = "设置"`
- Supporting text should be short and Chinese by default:
  - `调整相机默认项。高频拍摄控制保留在快捷面板。`
- Remove English phrases such as:
  - `Quick defaults with explicit supported...`
  - `Still watermark templates now flow...`
  - `Manual drafts and Live/video defaults remain staged...`

Use `AppTextResolver` methods or string resources instead of inline English.

- [ ] **Step 5: Define the Quick panel boundary**

Keep `快捷` limited to:

- grid toggle,
- flash/quality if currently implemented as quick control,
- preview ratio,
- Live default quick toggle if already present,
- countdown quick toggle,
- `更多` as an entry to settings if needed.

Do not duplicate:

- full default filter selection,
- watermark template selector/detail,
- portrait profile/beauty/bokeh,
- video resolution/frame-rate/audio full matrix.

If a quick item points to the same setting, its label should be short and its value compact. The full description belongs in settings.

- [ ] **Step 6: Keep lens switching separate**

The bottom cockpit lens button may keep labels like:

- `后置`
- `前置`
- `切前摄`
- `切后摄`

Do not route this button into settings. It should continue dispatching `SessionIntent.LensFacingToggled`.

- [ ] **Step 7: Verify**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Manual smoke:

- First screen right rail shows `色调 / 快捷 / 设置 / 开发` with no vertical wrapping.
- Opening `设置` shows a settings headline, not `镜头实验室`.
- Lens switch button still switches front/back lens.
- Quick panel remains small and does not repeat long settings content.

## Non-Goals

- Do not create a new settings state owner outside `SessionSettingsManager`.
- Do not migrate panel UI to Compose.
- Do not remove existing settings functionality; only reorganize labels and routes.
- Do not change capture/session behavior in this IA pass.
