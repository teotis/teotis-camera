# Multi Watermark Settings And Contracts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Use `rtk` for every shell command.

**Goal:** Add durable settings/catalog contracts for pure text and blurred four-border watermarks.

**Architecture:** Keep template choice and user-tunable style in `core:settings`. Mode plugins keep reading selected settings through `ModeContext.settingsSnapshot`. UI render models derive available controls from template capabilities instead of hard-coded assumptions.

**Tech Stack:** Kotlin, Gradle Kotlin DSL, existing JVM/unit tests, existing `SessionUiRenderModel` settings render-model pattern.

---

## File Structure

- Modify `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsEnums.kt`
  - Add `WatermarkTemplateKind`.
- Modify `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDataModels.kt`
  - Extend `WatermarkTemplate` with template kind and allowed controls.
  - Add `pureTextWatermarkStyle` and `blurFourBorderWatermarkStyle` to `PhotoSettings`.
- Modify `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDefaults.kt`
  - Add `pure-text` and `blur-four-border` templates.
- Modify `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsActions.kt`
  - Update `watermarkStyleFor()` and `updateWatermarkStyle()` for the two new template IDs.
- Modify `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt`
  - Persist and restore the two new per-template styles.
- Modify `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
  - Filter placement/background cycles using template capability lists.
  - Hide frame-background control for pure text.
  - Show only blur-family backgrounds for blurred four-border.
- Modify `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
  - Add user-facing labels/summaries if current generic strings are not enough.
- Modify tests:
  - `core/settings/src/test/kotlin/com/opencamera/core/settings/PersistedSettingsSerializerTest.kt`
  - `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

## Contract Shape

Add a template kind enum:

```kotlin
enum class WatermarkTemplateKind {
    TEXT_OVERLAY,
    INFO_OVERLAY,
    EXPANDED_FRAME
}
```

Extend `WatermarkTemplate` conservatively:

```kotlin
data class WatermarkTemplate(
    val id: String,
    val label: String,
    val tokenKeys: Set<String> = emptySet(),
    val supportsFrameBorder: Boolean = false,
    val kind: WatermarkTemplateKind = WatermarkTemplateKind.INFO_OVERLAY,
    val allowedPlacements: Set<WatermarkTextPlacement> = WatermarkTextPlacement.entries.toSet(),
    val allowedFrameBackgrounds: Set<WatermarkFrameBackground> = WatermarkFrameBackground.entries.toSet()
)
```

Use these template IDs exactly:

```text
pure-text
blur-four-border
```

Recommended defaults:

```kotlin
pureTextWatermarkStyle = WatermarkStyleSettings(
    textPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
    textScale = WatermarkTextScale.NORMAL,
    textOpacity = WatermarkTextOpacity.SOFT,
    frameBackground = WatermarkFrameBackground.DARK
)

blurFourBorderWatermarkStyle = WatermarkStyleSettings(
    textPlacement = WatermarkTextPlacement.BOTTOM_CENTER,
    textScale = WatermarkTextScale.NORMAL,
    textOpacity = WatermarkTextOpacity.SOLID,
    frameBackground = WatermarkFrameBackground.SOURCE_LIGHT_BLUR
)
```

For `blur-four-border`, set:

```kotlin
allowedPlacements = setOf(
    WatermarkTextPlacement.BOTTOM_LEFT,
    WatermarkTextPlacement.BOTTOM_CENTER,
    WatermarkTextPlacement.BOTTOM_RIGHT
)
allowedFrameBackgrounds = setOf(
    WatermarkFrameBackground.SOURCE_BLUR,
    WatermarkFrameBackground.SOURCE_LIGHT_BLUR,
    WatermarkFrameBackground.SOURCE_VIVID_BLUR
)
```

For `pure-text`, set `supportsFrameBorder = false`; the UI must not show a frame background row even though a fallback value remains in persisted style for serializer compatibility.

## Implementation Tasks

### Task 1: Add settings contracts

- [ ] **Step 1: Write failing catalog tests**

Add tests in `PersistedSettingsSerializerTest.kt` or a new focused settings test:

```kotlin
@Test
fun `default watermark catalog includes pure text and blur four border`() {
    val catalog = FeatureCatalog()
    val pureText = catalog.watermarkTemplates.first { it.id == "pure-text" }
    val blurBorder = catalog.watermarkTemplates.first { it.id == "blur-four-border" }

    assertEquals(WatermarkTemplateKind.TEXT_OVERLAY, pureText.kind)
    assertFalse(pureText.supportsFrameBorder)
    assertEquals(WatermarkTemplateKind.EXPANDED_FRAME, blurBorder.kind)
    assertTrue(blurBorder.supportsFrameBorder)
    assertEquals(
        setOf(
            WatermarkFrameBackground.SOURCE_BLUR,
            WatermarkFrameBackground.SOURCE_LIGHT_BLUR,
            WatermarkFrameBackground.SOURCE_VIVID_BLUR
        ),
        blurBorder.allowedFrameBackgrounds
    )
}
```

- [ ] **Step 2: Implement enum/data/defaults**

Add `WatermarkTemplateKind`, extend `WatermarkTemplate`, and register:

```kotlin
WatermarkTemplate(
    id = "pure-text",
    label = "Pure Text",
    tokenKeys = setOf("model", "datetime", "camera-params"),
    supportsFrameBorder = false,
    kind = WatermarkTemplateKind.TEXT_OVERLAY
)

WatermarkTemplate(
    id = "blur-four-border",
    label = "Blur Four Border",
    tokenKeys = setOf("model", "datetime", "location", "camera-params"),
    supportsFrameBorder = true,
    kind = WatermarkTemplateKind.EXPANDED_FRAME,
    allowedPlacements = setOf(
        WatermarkTextPlacement.BOTTOM_LEFT,
        WatermarkTextPlacement.BOTTOM_CENTER,
        WatermarkTextPlacement.BOTTOM_RIGHT
    ),
    allowedFrameBackgrounds = setOf(
        WatermarkFrameBackground.SOURCE_BLUR,
        WatermarkFrameBackground.SOURCE_LIGHT_BLUR,
        WatermarkFrameBackground.SOURCE_VIVID_BLUR
    )
)
```

- [ ] **Step 3: Run settings focused test**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
```

Expected result: test compiles and fails until serializer/style fields are added.

### Task 2: Persist new template styles

- [ ] **Step 1: Add round-trip tests**

Extend the existing serializer round-trip test with:

```kotlin
pureTextWatermarkStyle = WatermarkStyleSettings(
    textPlacement = WatermarkTextPlacement.TOP_RIGHT,
    textScale = WatermarkTextScale.LARGE,
    textOpacity = WatermarkTextOpacity.SUBTLE,
    frameBackground = WatermarkFrameBackground.DARK
),
blurFourBorderWatermarkStyle = WatermarkStyleSettings(
    textPlacement = WatermarkTextPlacement.BOTTOM_CENTER,
    textScale = WatermarkTextScale.COMPACT,
    textOpacity = WatermarkTextOpacity.SOLID,
    frameBackground = WatermarkFrameBackground.SOURCE_VIVID_BLUR
)
```

Assert serialized keys:

```kotlin
assertEquals("top-right", serialized["photo.watermark.pureText.position"])
assertEquals("source-vivid-blur", serialized["photo.watermark.blurFourBorder.background"])
```

- [ ] **Step 2: Add serializer keys**

Use these key names:

```text
photo.watermark.pureText.position
photo.watermark.pureText.scale
photo.watermark.pureText.opacity
photo.watermark.pureText.background
photo.watermark.blurFourBorder.position
photo.watermark.blurFourBorder.scale
photo.watermark.blurFourBorder.opacity
photo.watermark.blurFourBorder.background
```

Parse invalid values with existing fallback behavior.

- [ ] **Step 3: Update reducers**

Extend `PhotoSettings.watermarkStyleFor()` and private `updateWatermarkStyle()`:

```kotlin
"pure-text" -> pureTextWatermarkStyle
"blur-four-border" -> blurFourBorderWatermarkStyle
```

- [ ] **Step 4: Run settings tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
```

Expected result: serializer tests pass.

### Task 3: Filter UI controls by template capability

- [ ] **Step 1: Add render-model tests**

In `SessionUiRenderModelTest.kt`, add tests:

```kotlin
@Test
fun `pure text watermark detail hides frame background`() {
    val model = watermarkLabDetailRenderModel(
        state = stateWithWatermarkTemplate("pure-text"),
        templateId = "pure-text",
        text = TestAppTextResolver()
    )

    assertNull(model.frameBackgroundControl)
    assertNotNull(model.placementControl.nextAction)
}

@Test
fun `blur four border cycles only blur backgrounds`() {
    val model = watermarkLabDetailRenderModel(
        state = stateWithWatermarkTemplate("blur-four-border"),
        templateId = "blur-four-border",
        text = TestAppTextResolver()
    )

    val action = model.frameBackgroundControl?.nextAction
    assertTrue(action is PersistedSettingsAction.UpdateWatermarkFrameBackground)
    assertTrue(action.background in setOf(
        WatermarkFrameBackground.SOURCE_BLUR,
        WatermarkFrameBackground.SOURCE_LIGHT_BLUR,
        WatermarkFrameBackground.SOURCE_VIVID_BLUR
    ))
}
```

Use existing test helpers in `SessionUiRenderModelTest.kt`; if no helper exists, build a minimal `SessionState` the same way nearby watermark tests do.

- [ ] **Step 2: Implement filtered cycling**

In `watermarkLabDetailRenderModel()`, compute:

```kotlin
val allowedPlacements = template.allowedPlacements
    .takeIf { it.isNotEmpty() }
    ?: WatermarkTextPlacement.entries.toSet()
val allowedBackgrounds = template.allowedFrameBackgrounds
    .takeIf { it.isNotEmpty() }
    ?: WatermarkFrameBackground.entries.toSet()
```

Use `allowedPlacements.toList()` for `UpdateWatermarkTextPlacement`.

Use `allowedBackgrounds.toList()` for `UpdateWatermarkFrameBackground`.

Set `frameBackgroundControl = null` when `template.supportsFrameBorder == false`.

- [ ] **Step 3: Run app render-model tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

Expected result: new and existing settings/watermark render-model tests pass.

## Acceptance

- The two new template IDs exist in the feature catalog.
- Every template has explicit kind and control capability semantics.
- Settings persistence survives app restart for both new templates.
- UI does not expose irrelevant frame background controls for pure text.
- UI cycles only blur backgrounds for blurred four-border.
- Existing templates keep their current defaults and round-trip behavior.

## Verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```
