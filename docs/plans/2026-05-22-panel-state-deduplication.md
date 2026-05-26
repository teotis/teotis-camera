# Panel State Deduplication Plan

> **For agentic workers:** This is a self-contained implementation handoff for a non-multimodal agent. Use render-model tests instead of screenshots. Run shell commands through `rtk`.

**Goal:** Remove aggregate setting-state strings from panel headers/footers and make each child setting item display its own state exactly once. This fixes duplicated and triplicated state text in `设置`, `拍照`, `录像`, and secondary panels.

**Architecture:** `SessionUiRenderModel` formats panel display data. `MainActivity` renders that data. `SessionSettingsManager` remains the only settings mutation owner. Do not introduce a new settings state owner.

**Tech Stack:** Kotlin render models, Android XML Views, `AppTextResolver`, unit tests.

---

## Evidence

Screenshots show:

- Settings root header has an aggregate string such as `网格Off | 快门声音开 | 自拍镜像关 ...`.
- The `通用` section repeats another aggregate.
- Each child card repeats its own value/support state, creating triple display.
- Similar duplication appears in `拍照/录像`, especially when filter, watermark, Live, manual, and video defaults are summarized in multiple places.

Current code facts:

- `SessionSettingsPageRenderModel` contains `heroSummary`, per-section `summary`, and `catalogFooter`.
- `MainActivity.renderSettingsPage()` renders all of them:
  - `settingsHeroSummary`
  - `settingsCommonSummary`
  - `settingsPhotoSummary`
  - `settingsVideoSummary`
  - `settingsCatalogFooter`
- `sessionSettingsRenderModel()` and `sessionSettingsPageRenderModel()` build aggregate strings using `settingsJoiner*` helpers.

## Required Behavior

- Panel header should describe purpose, not enumerate state.
- Section headers should not include aggregate setting values.
- Each child control row/card displays:
  - short label,
  - current value,
  - support/degraded/unavailable hint if needed.
- No normal user-facing panel should display the same setting state more than once in the same viewport.
- Debug/dev panels may still show raw diagnostic summaries.

## Files

Modify:

- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/TestAppTextResolver.kt`

## Implementation Tasks

- [ ] **Step 1: Redefine settings page summary fields**

In `SessionUiRenderModel.kt`, change settings page construction so:

```kotlin
heroSummary = ""
commonSection.summary = ""
photoSection.summary = ""
videoSection.summary = ""
catalogFooter = ""
```

Or remove these fields if the XML can be simplified safely. A low-churn approach is to keep fields but set them empty and hide empty views in `MainActivity`.

- [ ] **Step 2: Hide empty summary views**

In `MainActivity.renderSettingsPage()`:

```kotlin
settingsHeroSummary.text = model.heroSummary
settingsHeroSummary.isVisible = model.heroSummary.isNotBlank()
settingsCommonSummary.text = model.commonSection.summary
settingsCommonSummary.isVisible = model.commonSection.summary.isNotBlank()
settingsPhotoSummary.text = model.photoSection.summary
settingsPhotoSummary.isVisible = model.photoSection.summary.isNotBlank()
settingsVideoSummary.text = model.videoSection.summary
settingsVideoSummary.isVisible = model.videoSection.summary.isNotBlank()
settingsCatalogFooter.text = model.catalogFooter
settingsCatalogFooter.isVisible = model.catalogFooter.isNotBlank()
```

Keep `settingsSupportingText` as a short purpose sentence, for example:

```text
调整相机默认项。高频拍摄控制保留在快捷面板。
```

- [ ] **Step 3: Ensure child controls carry state**

For every `SettingsControlRenderModel`, keep:

- `label`
- `value`
- `supportLabel`
- `availability`

Make `renderSettingsControl()` place these in child controls only. If current button text is multiline such as:

```text
构图网格
Off
Supported • 切换 3 种布局
```

it is acceptable for this pass as long as the same value is not also repeated in headers. A later visual pass can split label/value/support into separate `TextView`s.

- [ ] **Step 4: Remove aggregate joiner usage from normal panels**

Search:

```bash
rtk rg -n "settingsJoiner|heroSummary|catalogFooter|commonSummary|photoSummary|videoSummary" app/src/main/java/com/opencamera/app -S
```

Keep aggregate builders only if used by dev/debug output. Normal settings panels should not use `settingsJoinerGrid`, `settingsJoinerFilter`, and similar helpers.

- [ ] **Step 5: Clean filter/watermark/portrait secondary headers**

Apply the same rule to secondary panels:

- `filterLabHeroSummary` should not list selected profile internals when the selected card already shows it.
- `watermarkSelectorHeroSummary` should not duplicate current template if the selected template row shows it.
- `watermarkDetailHeroSummary` should not duplicate placement/scale/opacity if the child controls show them.
- `portraitLabHeroSummary` should not duplicate profile/beauty/bokeh values if controls show them.

Use empty hero summary plus hidden view where this is the smallest safe change.

- [ ] **Step 6: Add render-model tests for no aggregate duplication**

Add tests in `SessionUiRenderModelTest`:

```kotlin
@Test
fun `settings page does not aggregate child states in header or footer`() {
    val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())
    assertTrue(model.heroSummary.isBlank())
    assertTrue(model.commonSection.summary.isBlank())
    assertTrue(model.photoSection.summary.isBlank())
    assertTrue(model.videoSection.summary.isBlank())
    assertTrue(model.catalogFooter.isBlank())
    assertEquals("构图网格", model.commonSection.gridMode.label)
}
```

If tests use English `TestAppTextResolver`, assert structure instead of exact Chinese:

```kotlin
assertFalse(model.supportingText.contains("|"))
assertFalse(model.heroSummary.contains("Grid"))
```

Add secondary panel tests ensuring action labels and hero summaries do not contain internal state lists.

- [ ] **Step 7: Verify**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Manual Smoke

- Open `设置`.
- Header should show only title/purpose.
- `通用 / 拍照 / 录像` section headers should not show `网格Off | ...` lists.
- Each item still shows its own current state.
- Open style/filter, watermark, and portrait subpages; headers should not repeat the selected item state already shown below.

## Non-Goals

- Do not remove settings functionality.
- Do not create a new settings repository.
- Do not redesign card visuals unless required to hide empty summary text safely.
- Do not require screenshot judgment in this pass.

