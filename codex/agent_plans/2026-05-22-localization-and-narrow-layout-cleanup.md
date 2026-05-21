# Localization And Narrow Layout Cleanup Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if executing this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clean remaining English copy and prevent Chinese labels from wrapping into unreadable second lines or vertical text in right rail, quick panel, and secondary panels.

**Architecture:** User-facing strings should come from `AppTextResolver` and resources. Render models should produce compact localized labels. XML/View layout should provide stable dimensions and avoid packing long multi-line text into narrow buttons.

**Tech Stack:** Android XML Views, MaterialComponents styles, Kotlin render models, app unit tests.

---

## Evidence

User issues covered:

- `2`: “镜头实验室”“开发”依然中文显示不全或竖向排列；“快捷”中项目中文显示不当，出现第二行现象。
- `3`: 次级面板中依然英文问题没有处理干净。

Current screenshot evidence:

- Right rail `开发` appears split vertically in a 44dp square button.
- `镜头实验室` in the top pill is close to clipping.
- Secondary watermark panel still contains English content:
  - `Back`
  - `Default Travel Polaroid • 3 templates staged`
  - `Classic Overlay`
  - `Tokens Camera Params, Date/Time, Location, Mode`
  - `Placement Bottom Right | Scale Normal | Opacity Soft`
  - `Open Style Page`
- Quick panel and right rail can force Chinese labels into two lines because controls use narrow square button containers with text.

Current code evidence:

- `SessionUiRenderModel.kt` still hard-codes English in settings summaries, watermark selector/detail, portrait lab footer, and support labels.
- `MainActivity.renderFilterSelectionList()` dynamically creates buttons but some labels still come from model copy that may contain multi-line text.
- `activity_main.xml` right rail buttons use `@dimen/touch_target_min` width and height, so two-character labels fit, but four-character labels such as `镜头实验室` and debug labels do not.
- `strings.xml` has many Chinese strings, but user-facing model bodies still mix English.

## Required Behavior

- Chinese default locale must not show English in regular user-facing panels.
- English is allowed only in:
  - debug trace event names,
  - exported logs,
  - raw metadata keys if explicitly presented as developer data.
- Right rail labels must be one-line horizontal labels.
- If a label cannot fit in a 44dp square, do one of:
  - shorten the label,
  - make the rail button wider,
  - use an icon plus content description,
  - hide debug entry behind a compact icon.
- Secondary panel buttons should not contain three-line labels. Split title/value/support into separate `TextView`s and use a short action button.

## Files

Modify:

- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/TestAppTextResolver.kt`

## Implementation Tasks

- [ ] **Step 1: Inventory remaining English copy**

Run:

```bash
rtk rg -n "\"[A-Za-z][^\"]*(Lab|Default|Current|Ready|Unavailable|Open|Style|Page|Placement|Scale|Opacity|Background|Token|Template|Saved|Supported|Unsupported|Degraded|Quick|Still|Manual|Live|Filter|Watermark|Portrait|Profile|Beauty|Bokeh|Frame|Location|Date|Time|Mode|Camera)\" app/src/main/java/com/opencamera/app app/src/main/res/values -S
```

Classify results:

- user-facing normal UI: must localize,
- debug/dev-only: may remain English,
- test-only: update only if assertions depend on user copy.

- [ ] **Step 2: Localize settings summaries and support labels**

Replace English-building code in `sessionSettingsRenderModel()` and `sessionSettingsPageRenderModel()` with `AppTextResolver` methods and Chinese resources.

Examples:

- `Grid` -> `网格`
- `Shutter sound` -> `快门声音`
- `Selfie mirror` -> `自拍镜像`
- `Filter` -> `滤镜`
- `Watermark` -> `水印`
- `Timer` -> `定时`
- `Low-light auto 24fps` -> `弱光自动 24fps`
- `Locked fps` -> `锁定帧率`
- `Saved as ... active graph uses ...` -> `已保存为...，当前图使用...`

Add tests asserting `SessionSettingsPageRenderModel` does not contain these English words in Chinese/default resolver:

```kotlin
val allText = listOf(model.headline, model.supportingText, model.heroSummary, model.catalogFooter).joinToString("\n")
assertFalse(allText.contains("Quick defaults"))
assertFalse(allText.contains("Still watermark"))
assertFalse(allText.contains("Manual drafts"))
```

- [ ] **Step 3: Localize watermark selector/detail**

In `watermarkLabSelectorRenderModel()`:

- `Default` -> `默认`
- `templates staged` -> `个模板`
- `Classic overlay` -> `经典叠加`
- `Expanded frame` -> `扩展边框`
- `Tokens` -> `标记`
- `Placement` -> `位置`
- `Scale` -> `大小`
- `Opacity` -> `透明度`
- `Background` -> `背景`
- `Current default` -> `当前默认`

Do not use template English labels as the only visible title. Either add localized display labels for built-ins or map them at render time:

- `Classic Overlay` -> `经典叠加`
- `Travel Polaroid` -> `旅行拍立得`
- `Retro Frame` -> `复古边框`

In `watermarkLabDetailRenderModel()`:

- `Placement ... • Scale ... • Opacity ...` -> `位置 ... • 大小 ... • 透明度 ...`
- Support labels such as `5 placements` -> `5 个位置`.

- [ ] **Step 4: Remove multi-line action buttons in watermark cards**

Current selector card uses an action label built with newlines:

```kotlin
打开样式页
Classic Overlay
Placement, scale, opacity
```

Replace with:

- card title: localized template name,
- supporting text: localized details,
- primary button: `使用此模板`,
- secondary button: `样式`,
- no button text should contain `\n`.

Update `WatermarkLabTemplateItemRenderModel` if needed:

```kotlin
val styleButtonLabel: String? = text.openStylePageShort()
```

Where Chinese short label is `样式`, English is `Style`.

- [ ] **Step 5: Fix right rail text layout**

Preferred small pass:

- Set right rail user-facing labels to two characters where possible:
  - `色调`
  - `快捷`
  - `设置`
  - `开发` or debug icon `⋯`
- Ensure `buttonDevEntry` uses one-line text:
  - `android:maxLines="1"`
  - `android:singleLine="true"`
  - `android:ellipsize="end"`
  - `android:includeFontPadding="false"`
- If `开发` still wraps, change debug label to `DEV` in debug builds or use `⋯` with `contentDescription="开发"`.

Do not show `镜头实验室` in a 44dp right rail button.

- [ ] **Step 6: Fix quick panel labels**

Review `quickBubblePanel` buttons:

- Keep labels short:
  - `网格`
  - `画质`
  - `比例`
  - `实况`
  - `定时`
  - `更多`
- Buttons should use `maxLines=1`, `ellipsize=end`, and adequate `minWidth`.
- If value must be visible, use separate small value text below or beside the label, not a forced second line in the button.

- [ ] **Step 7: Add layout-oriented smoke tests where possible**

Unit tests cannot fully verify visual wrapping, but they can assert copy constraints:

- right rail labels length <= 2 Chinese chars or explicit debug exception,
- quick labels contain no newline,
- watermark action labels contain no newline,
- normal panel render models contain no banned English phrases under Chinese resolver.

Example:

```kotlin
assertTrue(model.rightRail.entries.filter { it.isVisible }.all { it.label.length <= 2 })
assertFalse(watermark.items.any { it.editButtonLabel?.contains('\n') == true })
```

- [ ] **Step 8: Verify**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Manual smoke:

- Open settings, tone, watermark selector, watermark detail, portrait page, quick panel, and dev panel.
- Default Chinese UI should not show `Back`, `Default`, `Open Style Page`, `Placement`, `Scale`, `Opacity`, `Current default`, or `templates staged`.
- Right rail and quick panel labels stay horizontal and single-line.

## Non-Goals

- Do not translate raw debug trace event keys inside the dev log content.
- Do not redesign the whole panel visual system in this pass.
- Do not use screenshot-based judgement in this non-multimodal implementation pass; put visual judgement in the multimodal follow-up doc.
