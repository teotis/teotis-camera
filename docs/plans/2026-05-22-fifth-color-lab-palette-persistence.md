# Fifth Feedback: Color Lab Palette Persistence And Simplification

> **For text-only agents:** This is a functional and render-model task. It does not require visual judgment, but must preserve architecture boundaries. Use `rtk` for every command.

## Goal

Fix Color Lab so the palette actually persists and affects the selected Color Lab state, then simplify the panel by removing `进阶`.

## Observed Problem

In the real-device recording, tapping/dragging the Color Lab palette returns the reticle to the origin. The adjustment appears not to persist.

Likely code cause:

- `FilterPaletteView` emits axes correctly.
- `MainActivity.handleFilterPaletteTouch()` currently reads `latestFilterLabRenderModel?.adjustmentPanel`, requires `selectedProfileId`, and writes through `sessionSettingsManager.updateCustomFilterRenderSpec(...)`.
- Color Lab render state is modeled by `ColorLabSpec` in persisted settings and exposed through `colorLabPanelRenderModel(...)`.
- Therefore the Color Lab route can snap back because the reticle is driven by persisted `ColorLabSpec`, while touches update a custom filter render spec instead.

## Required Behavior

- In `CockpitPanelRoute.ColorLab`, palette touch updates persisted `ColorLabSpec(colorAxis, toneAxis, strength)` through `PersistedSettingsAction.UpdateColorLabSpec`.
- The reticle remains at the touched location after render.
- Color Lab does not require a selected filter profile.
- Color Lab does not show or route to advanced controls.
- Remove the `进阶` / mode-toggle button from Color Lab.
- Keep Style/Lens/StyleLab behavior separate: style palette adjustment may still update a custom filter render spec if that is the intended Style path.

## Suggested Implementation

1. Add tests first:
   - `filterLabPageRenderModel(... panelRole = COLOR_LAB)` has no advanced mode toggle.
   - Color Lab route emits/uses `ColorLabSpec` values for palette summary and reticle.
   - A `MainActivity`-adjacent pure helper or settings-manager test proves `UpdateColorLabSpec` is the action for Color Lab palette touch.

2. Split touch handling:
   - If `activePanelRoute is CockpitPanelRoute.ColorLab`, call:
     `container.sessionSettingsManager.apply(PersistedSettingsAction.UpdateColorLabSpec(ColorLabSpec(colorAxis = ..., toneAxis = ..., strength = existingStrength)))`
   - Else keep existing style/filter custom-spec path.

3. Render simplification:
   - For `COLOR_LAB`, hide or remove `buttonFilterModeToggle`.
   - Do not show `filter_section_advanced`.
   - Do not show `filterAdvancedControls`.
   - Keep reset color as a separate clear button only if it is visually part of Color Lab; otherwise place it in a compact footer.

4. State feedback:
   - Summary should be human-readable, e.g. `色彩 偏暖 +0.42 / 影调 加深 -0.18`, not raw internal spec dump.
   - English fallback can remain concise.

## Files To Inspect Or Modify

- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SessionSettingsManager.kt`
- `app/src/main/java/com/opencamera/app/FilterPaletteView.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionSettingsManagerTest.kt`

## Acceptance Criteria

- Dragging Color Lab palette changes persisted `ColorLabSpec`.
- Reticle does not snap back to center after the next render.
- Color Lab contains no `进阶` button and no advanced parameter grid.
- Style/镜头 panel still works independently.
- Tests cover the Color Lab persistence path.

## Verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionSettingsManagerTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Manual/multimodal follow-up: drag palette on vivo X300 and confirm reticle stays where released.
