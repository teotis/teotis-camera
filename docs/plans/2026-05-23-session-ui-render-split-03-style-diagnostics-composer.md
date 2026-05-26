# Session UI Render Split 03: Style, Diagnostics, And Composition

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this task. Use `rtk` for every command. This package is text-only and does not require screenshots.

**Goal:** Move style/color lab and diagnostics render-model builders out of `SessionUiRenderModel.kt`, split the remaining tests, and reduce `MainActivity.render(state)` to domain-level composition calls.

**Architecture:** Style/color render models remain pure app-layer formatting of settings/catalog state. Diagnostics render models remain presentation-only consumers of `SessionState`, `SessionTraceEvent`, and diagnostics snapshots. A small composition facade may group models for `MainActivity`, but it must not own mutable state or session runtime decisions.

**Tech Stack:** Kotlin app module, existing `core:effect`, `core:settings`, `core:session`, JVM unit tests, `:app:assembleDebug`.

---

## Dependency

Implement this after packages 1 and 2 land. This package expects cockpit/preview/settings declarations to have moved out of `SessionUiRenderModel.kt`.

## Current Code Facts

- Style/color declarations currently include:
  - `FilterLabFamily`
  - `FilterLabTabRenderModel`
  - `FilterAdjustmentMode`
  - `FilterAdvancedControl`
  - `FilterLabFilterItemRenderModel`
  - `FilterLabAdjustRenderModel`
  - `FilterLightPaletteRenderModel`
  - `FilterAdvancedControlRenderModel`
  - `FilterAdjustmentPanelRenderModel`
  - `StyleAndColorLabRole`
  - `ColorLabPanelRenderModel`
  - `FilterLabPageRenderModel`
  - `FilterLabSaveCustomRenderModel`
- Style/color builders include `colorLabPanelRenderModel`, `filterLabPageRenderModel`, `defaultFilterLabFamily`, `FilterRenderSpec.applyLightPalette`, and `FilterRenderSpec.nextAdvancedControl`.
- Diagnostics builders include `sessionDiagnosticsText` and `devLogRenderModel`.
- `MainActivity.render(state)` still directly constructs every domain render model.

## Files

Create:

- `app/src/main/java/com/opencamera/app/SessionStyleColorRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SessionDiagnosticsRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SessionScreenRenderModels.kt`
- `app/src/test/java/com/opencamera/app/SessionStyleColorRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionDiagnosticsRenderModelTest.kt`

Modify:

- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

Do not modify:

- `app/src/main/java/com/opencamera/app/FilterLabPanelRenderer.kt`
- `app/src/main/java/com/opencamera/app/DevConsoleRenderer.kt`
- `core/session/**`
- `core/settings/**`
- `core/effect/**`

## Step 1: Baseline

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

Expected: `BUILD SUCCESSFUL`.

## Step 2: Move Style And Color Lab Code

Create `SessionStyleColorRenderModel.kt` and move all style/color declarations and helpers:

- declarations listed in Current Code Facts
- `colorLabPanelRenderModel`
- `filterLabPageRenderModel`
- `defaultFilterLabFamily`
- filter family state helpers
- filter tab builder
- feature catalog filter profile helpers
- filter render spec summaries
- light palette and advanced-control helpers
- adjustment level enums and level mapping helpers

Keep `FilterRenderSpec.applyLightPalette` and `FilterRenderSpec.nextAdvancedControl` `internal` because `MainActivity` currently calls them during palette and advanced-control actions.

Do not change any label text, control ordering, selected family behavior, or `showFilterItems/showAdjustmentPanel/showAdvancedControls/showModeToggle` behavior.

## Step 3: Move Diagnostics Code

Create `SessionDiagnosticsRenderModel.kt` and move:

- `sessionDiagnosticsText`
- `devLogRenderModel`
- event-name grouping constants
- `isErrorEvent`
- diagnostics string helpers used only by those functions

Keep `DevLogRenderModel.kt` as-is if it already contains independent model types. Do not merge it into the new file.

## Step 4: Add A Small MainActivity Composition Facade

Create `SessionScreenRenderModels.kt` with immutable grouping types that reduce `MainActivity.render(state)` fan-out without owning state:

```kotlin
package com.opencamera.app

import com.opencamera.app.i18n.AppTextResolver
import com.opencamera.core.effect.PreviewEffectAdapter
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.SessionTraceEvent

internal data class SettingsSurfaceRenderModels(
    val settingsPage: SessionSettingsPageRenderModel,
    val portraitLabPage: PortraitLabPageRenderModel,
    val watermarkSelectorPage: WatermarkLabSelectorRenderModel,
    val watermarkDetailPage: WatermarkLabDetailRenderModel
)

internal data class StyleSurfaceRenderModels(
    val filterLabPage: FilterLabPageRenderModel,
    val colorLabPanel: ColorLabPanelRenderModel?
)

internal data class CockpitSurfaceRenderModels(
    val controls: SessionControlsRenderModel,
    val modeTrack: ModeTrackRenderModel,
    val quickPanelSheet: QuickPanelSheetRenderModel,
    val previewOverlay: PreviewOverlayRenderModel,
    val captureOutputText: String
)

internal data class DiagnosticsSurfaceRenderModels(
    val devLog: DevLogRenderModel?
)
```

Add builder functions:

```kotlin
internal fun settingsSurfaceRenderModels(
    state: SessionState,
    selectedWatermarkDetailTemplateId: String?,
    text: AppTextResolver
): SettingsSurfaceRenderModels {
    return SettingsSurfaceRenderModels(
        settingsPage = sessionSettingsPageRenderModel(state, text),
        portraitLabPage = portraitLabPageRenderModel(state, text),
        watermarkSelectorPage = watermarkLabSelectorRenderModel(state, text),
        watermarkDetailPage = watermarkLabDetailRenderModel(
            state = state,
            templateId = selectedWatermarkDetailTemplateId
                ?: state.settings.persisted.photo.defaultWatermarkTemplateId,
            text = text
        )
    )
}
```

```kotlin
internal fun styleSurfaceRenderModels(
    state: SessionState,
    text: AppTextResolver,
    activePanelRoute: CockpitPanelRoute,
    selectedFamily: FilterLabFamily,
    showAdjustmentPanel: Boolean,
    adjustmentMode: FilterAdjustmentMode
): StyleSurfaceRenderModels {
    val role = if (activePanelRoute is CockpitPanelRoute.ColorLab) {
        StyleAndColorLabRole.COLOR_LAB
    } else {
        StyleAndColorLabRole.STYLE
    }
    return StyleSurfaceRenderModels(
        filterLabPage = filterLabPageRenderModel(
            state = state,
            text = text,
            selectedFamily = selectedFamily,
            panelRole = role,
            showAdjustmentPanel = showAdjustmentPanel,
            adjustmentMode = adjustmentMode
        ),
        colorLabPanel = if (activePanelRoute is CockpitPanelRoute.ColorLab) {
            colorLabPanelRenderModel(state, text)
        } else {
            null
        }
    )
}
```

```kotlin
internal fun cockpitSurfaceRenderModels(
    state: SessionState,
    text: AppTextResolver,
    strings: SessionUiStrings,
    previewEffectAdapter: PreviewEffectAdapter
): CockpitSurfaceRenderModels {
    return CockpitSurfaceRenderModels(
        controls = sessionControlsRenderModel(state, strings),
        modeTrack = modeTrackRenderModel(state, text),
        quickPanelSheet = quickPanelSheetRenderModel(state, text, strings),
        previewOverlay = previewOverlayRenderModel(state, previewEffectAdapter),
        captureOutputText = sessionCaptureOutputText(state, strings)
    )
}
```

```kotlin
internal fun diagnosticsSurfaceRenderModels(
    state: SessionState,
    traceEvents: List<SessionTraceEvent>,
    isDebugBuild: Boolean,
    selectedTab: DevLogTab,
    text: AppTextResolver
): DiagnosticsSurfaceRenderModels {
    return DiagnosticsSurfaceRenderModels(
        devLog = devLogRenderModel(
            state = state,
            traceEvents = traceEvents,
            isDebugBuild = isDebugBuild,
            selectedTab = selectedTab,
            text = text
        )
    )
}
```

These functions should not store mutable values, read Android views, launch intents, or dispatch session intents.

## Step 5: Update `MainActivity.render(state)`

Replace direct construction of every model with the domain facade calls. Preserve current cache assignments and render order.

The method should still:

- set `latestSessionState`
- create `AppTextResolver`
- call `applyLocale`
- update `latestSettingsPageRenderModel`, `latestPortraitLabRenderModel`, `latestWatermarkLabSelectorRenderModel`, `latestWatermarkLabDetailRenderModel`, `latestFilterLabRenderModel`, and `latestDevLogRenderModel`
- update `lightPaletteBaseSpec` under the same condition
- update color-lab reticle only when color lab is active
- call existing renderers in the same order
- keep thumbnail rendering logic unchanged

This is a fan-out cleanup only, not a behavior change.

## Step 6: Add Focused Tests

Create `SessionStyleColorRenderModelTest.kt` by moving style/color tests:

- `filter lab render model follows active portrait mode and cycles portrait defaults`
- `filter lab render model switches to humanistic family and keeps import export deferred`
- `filter lab render model exposes light adjustment panel for selected custom filter`
- `filter lab render model exposes advanced adjustment controls for selected portrait filter`
- `advanced control cycling covers halo temperature and tint shifts`
- `photo filter family exposes humanistic styles as photo style subitems`
- `filter lab page uses text resolver for all key labels`
- `filter lab adjustment panel is visible by default`
- `built-in filter shows auto-prepare hint on palette`
- `custom filter does not need auto-prepare`
- `advanced mode shows 12 controls`
- `style lab shows filter items and family tabs but not adjustment panel`
- `color lab shows adjustment panel but not filter items and family tabs`
- `filter items do not show raw parameter strings in title`
- `user-facing panels do not leak raw render spec internals`
- `selected filter item shows family label in supporting text`
- `color lab panel render model title is color lab`
- `color lab panel render model reflects persisted spec`
- `color lab panel render model exposes reset action`
- `color lab filter page does not show advanced controls`
- `color lab filter page does not show mode toggle button`
- `color lab filter page omits style only content`
- `color lab adjustment panel summarizes persisted color lab spec`
- `style filter page shows mode toggle button`
- `style filter family tabs use short family labels`
- `color lab panel summary uses human readable format`
- `style filter page hides advanced controls`
- `style filter page carries style strength from persisted settings`

Create `SessionDiagnosticsRenderModelTest.kt` by moving diagnostics tests:

- `session diagnostics text renders debug dump perf snapshot and recovery trace`
- `session diagnostics text includes resource diagnostics when present`

Add one small composition test in an existing or new test file:

```kotlin
@Test
fun `screen render model composition keeps color lab panel conditional`() {
    val state = defaultSessionState()
    val text = TestAppTextResolver()

    val styleModels = styleSurfaceRenderModels(
        state = state,
        text = text,
        activePanelRoute = CockpitPanelRoute.ColorLab,
        selectedFamily = FilterLabFamily.PHOTO,
        showAdjustmentPanel = true,
        adjustmentMode = FilterAdjustmentMode.LIGHT
    )

    assertNotNull(styleModels.colorLabPanel)
    assertEquals(StyleAndColorLabRole.COLOR_LAB, styleModels.filterLabPage.panelRole)
}
```

Use the project’s existing fixture helpers after package 2 moves them.

## Step 7: Remove Or Shrink The Old Monolithic Test

After focused tests pass, remove tests from `SessionUiRenderModelTest.kt` that now live in domain test files. If no meaningful compatibility smoke tests remain, delete `SessionUiRenderModelTest.kt`.

Do not delete a test until its equivalent exists in a domain test file.

## Step 8: Verify

Run focused tests:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionStyleColorRenderModelTest --tests com.opencamera.app.SessionDiagnosticsRenderModelTest
```

Run all render-model tests:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionPreviewRenderModelTest --tests com.opencamera.app.SessionSettingsRenderModelTest --tests com.opencamera.app.SessionStyleColorRenderModelTest --tests com.opencamera.app.SessionDiagnosticsRenderModelTest
```

Run app compile:

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
```

Run Stage 7 gate:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

Expected: all commands end with `BUILD SUCCESSFUL`.

## Acceptance

- `SessionUiRenderModel.kt` is deleted or reduced to a small compatibility facade.
- `MainActivity.render(state)` delegates model construction through domain facades instead of constructing every panel model inline.
- Style/color and diagnostics tests are independent from settings/cockpit/preview tests.
- No behavior changes are made to panel IA, render text, diagnostics formatting, filter adjustment math, or color-lab state.
