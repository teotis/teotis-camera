# MainActivity Panel Router And UI State Extraction Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this task. Use `rtk` for every command. This package is text-only and does not require screenshots.

**Goal:** Move panel route transition policy out of `MainActivity.kt` into a pure, testable `CockpitPanelRouter`.

**Architecture:** `CockpitPanelRoute` remains the route model. `CockpitPanelRouter` owns UI-only route state and transition rules. `MainActivity` asks the router to transition, then asks the renderer to apply the resulting route state. The router must not depend on Android views, lifecycle, session dispatch, CameraX, or settings storage.

**Tech Stack:** Kotlin/JVM unit tests, existing `CockpitPanelRoute`, existing `CockpitPanelRouteTest`.

---

## Current Code Facts

- `CockpitPanelRoute.kt` defines route types but not transition behavior.
- `MainActivity.activePanelRoute` is mutated in click handlers, `toggleSettingsPanel()`, `openPortraitLab()`, `openWatermarkLabSelector()`, `openWatermarkLabDetail()`, `selectFilterLabFamily()`, `renderPanelVisibility()`, and `onBackPressed()`.
- `MainActivity` stores route-adjacent UI state:
  - `selectedSettingsTab`
  - `selectedWatermarkDetailTemplateId`
  - `selectedFilterLabFamilyOverride`
  - `isFilterAdjustmentVisible`
  - `filterAdjustmentMode`
  - `lightPaletteBaseSpec`
  - `lastRenderedPanelRoute`
- Settings back behavior appears in both the settings back button handler and `onBackPressed`.
- Closing style/color lab must reset filter family override, adjustment visibility, and palette base state.

## Required Behavior

- Preserve all current route semantics:
  - Scrim closes every panel and resets settings/filter local panel state.
  - Tapping Color Lab toggles `ColorLab`; opening it enables adjustment panel and clears it on close.
  - Tapping Style Lab toggles `StyleLab`; opening it enables adjustment panel and clears it on close.
  - Settings entry toggles root settings.
  - Settings close returns to `None` and resets selected settings tab.
  - Settings back from `WATERMARK_DETAIL` returns to `WATERMARK_SELECTOR` and clears selected template id.
  - Settings back from `PORTRAIT_LAB` or `WATERMARK_SELECTOR` returns to root settings.
  - Android back follows existing `onBackPressed()` behavior.
  - Quick bubble and dev console remain mutually exclusive route values.
- Router state is UI-only and never persisted.
- Router state does not include `latestSessionState` or any session runtime ownership.

## Files

Modify:

- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt`
- `app/src/test/java/com/opencamera/app/CockpitPanelRouteTest.kt`

Create:

- `app/src/main/java/com/opencamera/app/CockpitPanelRouter.kt`
- `app/src/test/java/com/opencamera/app/CockpitPanelRouterTest.kt`

## Proposed Model

Add a UI-state container:

```kotlin
package com.opencamera.app

internal data class CockpitPanelUiState(
    val route: CockpitPanelRoute = CockpitPanelRoute.None,
    val selectedSettingsTab: SettingsTab = SettingsTab.COMMON,
    val selectedWatermarkDetailTemplateId: String? = null,
    val selectedFilterLabFamilyOverride: FilterLabFamily? = null,
    val isFilterAdjustmentVisible: Boolean = false,
    val filterAdjustmentMode: FilterAdjustmentMode = FilterAdjustmentMode.LIGHT
)
```

If `SettingsTab`, `FilterLabFamily`, or `FilterAdjustmentMode` visibility blocks this file, move only the minimum enum visibility from private to internal. Do not rename enum values.

Add router commands:

```kotlin
internal sealed class CockpitPanelCommand {
    data object DismissAll : CockpitPanelCommand()
    data object ToggleColorLab : CockpitPanelCommand()
    data object ToggleStyleLab : CockpitPanelCommand()
    data object ToggleSettingsRoot : CockpitPanelCommand()
    data object CloseSettings : CockpitPanelCommand()
    data class SelectSettingsTab(val tab: SettingsTab) : CockpitPanelCommand()
    data object SettingsBack : CockpitPanelCommand()
    data object ToggleDevConsole : CockpitPanelCommand()
    data object ToggleQuickBubble : CockpitPanelCommand()
    data object CloseFilterLab : CockpitPanelCommand()
    data object CloseDevConsole : CockpitPanelCommand()
    data object OpenPortraitLab : CockpitPanelCommand()
    data object OpenWatermarkSelector : CockpitPanelCommand()
    data class OpenWatermarkDetail(val templateId: String) : CockpitPanelCommand()
    data class SelectFilterFamily(val family: FilterLabFamily) : CockpitPanelCommand()
    data object ToggleFilterAdjustmentMode : CockpitPanelCommand()
    data object AndroidBack : CockpitPanelCommand()
}
```

Add router:

```kotlin
internal class CockpitPanelRouter(
    initialState: CockpitPanelUiState = CockpitPanelUiState()
) {
    var state: CockpitPanelUiState = initialState
        private set

    fun reduce(command: CockpitPanelCommand): CockpitPanelUiState {
        state = nextState(state, command)
        return state
    }
}
```

`nextState()` should be an internal pure function so tests can call it directly.

## Implementation Tasks

### Task 1: Expose minimal enum visibility

Move `SettingsTab` out of `MainActivity.kt` or change it from private to internal in a small file if needed:

```kotlin
internal enum class SettingsTab { COMMON, PHOTO, VIDEO }
```

Do not change labels or rendering.

### Task 2: Add router state and reducer

Create `CockpitPanelRouter.kt` with `CockpitPanelUiState`, `CockpitPanelCommand`, `CockpitPanelRouter`, and `nextCockpitPanelUiState`.

Reducer rules:

```kotlin
DismissAll -> default state
ToggleColorLab -> ColorLab if not ColorLab else default state
ToggleStyleLab -> StyleLab if not StyleLab else default state
ToggleSettingsRoot -> Settings(ROOT) if settings is closed else default state
CloseSettings -> default route and SettingsTab.COMMON
SelectSettingsTab(tab) -> same state with selectedSettingsTab = tab
SettingsBack -> WATERMARK_DETAIL to WATERMARK_SELECTOR; PORTRAIT_LAB/WATERMARK_SELECTOR to ROOT; ROOT to ROOT
ToggleDevConsole -> DevConsole if not DevConsole else None
ToggleQuickBubble -> QuickBubble if not QuickBubble else None
CloseFilterLab -> default route/filter state
CloseDevConsole -> None preserving default non-route state
OpenPortraitLab -> Settings(PORTRAIT_LAB)
OpenWatermarkSelector -> Settings(WATERMARK_SELECTOR) and selectedWatermarkDetailTemplateId = null
OpenWatermarkDetail(id) -> Settings(WATERMARK_DETAIL) and selectedWatermarkDetailTemplateId = id
SelectFilterFamily(family) -> same route with selectedFilterLabFamilyOverride = family, isFilterAdjustmentVisible = true, light base reset handled by Activity until package 2
ToggleFilterAdjustmentMode -> LIGHT <-> ADVANCED
AndroidBack -> mirror current MainActivity.onBackPressed route behavior
```

If `lightPaletteBaseSpec` cannot move into the pure state because it uses `FilterRenderSpec`, keep it in `MainActivity` for this package and document it as a package 3 cleanup item.

### Task 3: Add tests before wiring Activity

Create `CockpitPanelRouterTest` with tests for:

- `ToggleColorLab` opens then closes and resets filter state.
- `ToggleStyleLab` opens then closes and resets filter state.
- `DismissAll` resets route, settings tab, selected watermark id, selected family, adjustment visibility, and adjustment mode.
- `SettingsBack` from watermark detail returns to selector and clears selected template id.
- `SettingsBack` from portrait lab returns to root.
- `AndroidBack` from `None` reports unchanged `None`; Activity still calls `super.onBackPressed()` when route is `None`.
- `AndroidBack` from style/color lab closes and resets filter state.
- `SelectSettingsTab` updates only the selected tab.

### Task 4: Replace MainActivity route fields with router state

In `MainActivity.kt`:

- Replace `activePanelRoute` with `private val panelRouter = CockpitPanelRouter()`.
- Add helper:

```kotlin
private val panelState: CockpitPanelUiState
    get() = panelRouter.state

private val activePanelRoute: CockpitPanelRoute
    get() = panelState.route
```

This compatibility helper keeps the first wiring diff small.

- Replace direct mutations such as `activePanelRoute = CockpitPanelRoute.None` with `panelRouter.reduce(CockpitPanelCommand.DismissAll)`.
- Replace `selectedSettingsTab`, `selectedWatermarkDetailTemplateId`, `selectedFilterLabFamilyOverride`, `isFilterAdjustmentVisible`, and `filterAdjustmentMode` reads with `panelState.*`.
- Keep `lightPaletteBaseSpec` in Activity for now if moving it would enlarge the diff.

### Task 5: Keep rendering behavior unchanged

`renderPanelVisibility()` should read:

```kotlin
val route = panelState.route
val selectedTab = panelState.selectedSettingsTab
```

The filter lab render-model call should receive:

```kotlin
showAdjustmentPanel = panelState.isFilterAdjustmentVisible
adjustmentMode = panelState.filterAdjustmentMode
```

`selectedFilterLabFamily(state)` should prefer:

```kotlin
panelState.selectedFilterLabFamilyOverride ?: defaultFilterLabFamily(state.activeMode)
```

## Focused Verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CockpitPanelRouteTest --tests com.opencamera.app.CockpitPanelRouterTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.gesture.GestureGuardTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Non-Goals

- Do not introduce a ViewModel.
- Do not move render functions yet.
- Do not change panel layout, labels, or XML.
- Do not change session state or settings storage.
