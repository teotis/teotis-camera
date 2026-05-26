# Tone Palette Discoverability Plan

> **For agentic workers:** Use this as a self-contained implementation handoff. Run shell commands through `rtk`. This plan is implementation-only and does not require image understanding.

**Goal:** Make the existing color palette discoverable from the first screen and reduce the steps needed to reach it.

**Recommended approach:** Keep the current Filter/Tone Lab implementation, but expose the palette as a first-class `色调 -> 调色板` surface instead of hiding it behind selected filter rows and an "Adjust Selected" action.

---

## Current Code Facts

- Right rail has `buttonFilterEntry` with user-facing label `色调`.
- The panel headline still uses `button_filter_entry`, currently `色调实验室`.
- `FilterPaletteView` exists and supports two-axis touch:
  - horizontal axis: color
  - vertical axis: tone
- `filterAdjustmentPanel` contains `FilterPaletteView`, but it is hidden until `isFilterAdjustmentVisible = true`.
- To show the palette today, a user must:
  1. Tap `色调`.
  2. Find the selected filter card.
  3. Tap the selected card's adjust button.
  4. Then the palette appears.

This is too buried for a flagship-style camera surface, which explains the user report: "I did not see where the palette is."

## UX Contract

- The first-screen right rail entry is `色调`.
- Opening `色调` should show a visible `调色板` area without requiring the user to discover a hidden adjust button.
- The user should see three concepts inside the panel:
  - `滤镜`: choose a look.
  - `调色板`: drag to tune color/tone.
  - `进阶`: exposure, glow, grain, sharpness, etc.
- Dragging the palette on a built-in filter should create or prepare an editable custom copy using the existing settings manager path.
- User-facing copy should use Chinese product labels where strings exist; avoid English-only diagnostics in visible controls.

## Implementation Scope

Modify:

- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/FilterPaletteView.kt` only if visual affordance needs a selected-point API
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`

Do not modify:

- Photo postprocessors.
- Filter profile serialization.
- Import/export behavior unless it is already implemented elsewhere.

## Design

### 1. Rename The Surface

User-facing:

- Right rail: `色调`.
- Panel title: `色调`.
- Section labels: `滤镜`, `调色板`, `进阶`.

Internal names such as `FilterLabPageRenderModel` can stay for now to reduce churn.

### 2. Make Palette Visible On Panel Open

When `buttonFilterEntry` opens the panel:

- Default `isFilterAdjustmentVisible = true`.
- Default `filterAdjustmentMode = FilterAdjustmentMode.LIGHT`.
- Show `FilterPaletteView` near the top after current filter summary.

If the selected filter is built-in:

- Show a clear button/state: `拖动调色板将保存为自定义`.
- On first drag, call the same path currently used by `openSelectedFilterAdjustment()`: `SessionSettingsManager.prepareFilterForAdjustment(...)`.
- After the editable profile id is returned, apply `updateCustomFilterRenderSpec(...)`.

### 3. Keep Filter Selection Simple

The selected filter card can remain, but its adjust button should no longer be the only way to open the palette.

Recommended panel order:

1. Header: `色调`.
2. Current summary.
3. Visible palette section.
4. Mode toggle: `进阶`.
5. Filter family tabs and filter list.
6. Save custom control.

If XML movement is too large, keep existing order but make `filterAdjustmentPanel` visible by default and rename the mode toggle/summary clearly.

### 4. Use The Existing Two-Axis Model

Keep:

- `FilterRenderSpec.applyLightPalette(colorAxis, toneAxis)`
- `FilterPaletteView.setOnPaletteTouchListener`
- `SessionSettingsManager.updateCustomFilterRenderSpec`

Add a small helper in `MainActivity`:

```kotlin
private suspend fun ensureEditableFilterForPalette(panel: FilterAdjustmentPanelRenderModel): String?
```

It should:

- Return current selected profile id if already custom.
- Call `prepareFilterForAdjustment()` if current profile is built-in.
- Return null if editing is disabled or no source profile exists.

### 5. Tests

Add tests:

- Opening Tone Lab defaults to visible light palette in render model.
- Built-in selected filter exposes a "will create custom copy" state.
- Advanced mode still shows 12 controls.
- Right rail render model first visible entry is `色调`.
- User-facing strings do not show `Filter Lab` in Chinese locale.

## Verification

Focused:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionSettingsManagerTest
```

Stage:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

Manual real-device smoke:

- Launch app.
- Tap right rail `色调`.
- The palette is immediately visible.
- Drag palette; selected look changes and persists as custom when required.
- Tap `进阶`; advanced controls show.
- Close and reopen; palette remains discoverable.

## Non-Goals

- Do not redesign the whole filter engine.
- Do not implement import/export in this loop.
- Do not require screenshot/image analysis to complete the implementation.
- Do not bury palette under settings only; it must remain a first-screen camera tool.
