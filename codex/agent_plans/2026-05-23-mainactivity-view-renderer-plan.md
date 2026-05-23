# MainActivity View Binder And Renderer Surface Extraction Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this task. Use `rtk` for every command. This package is text-only and does not require screenshots.

**Goal:** Move bulk view references, manual view lookup, and view-application rendering code out of `MainActivity.kt` without changing behavior.

**Architecture:** `MainActivityViews` is a grouped passive view container. Renderer classes receive `MainActivityViews`, Android resources/context, and callbacks where needed. Render-model construction remains in app-layer pure functions; renderers only apply already-built models to Android views.

**Tech Stack:** Android Views/XML, Kotlin app module, existing render models and JVM tests, `:app:assembleDebug`.

---

## Current Code Facts

- `MainActivity.onCreate()` performs a long sequence of `findViewById` assignments.
- `MainActivity.render(state)` builds multiple render models and immediately applies them to views.
- There are already pure render-model tests in `SessionUiRenderModelTest`, `CameraCockpitRenderModelTest`, `DevLogRenderModelTest`, and related test classes.
- The app Gradle file does not currently enable Android ViewBinding. A manual binder avoids Gradle/config churn.

## Required Behavior

- Keep `activity_main.xml` and all view IDs unchanged.
- Preserve all current visible behavior and route visibility.
- Keep `MainActivity` as the owner of Android lifecycle and state collection.
- Renderers may set view text, alpha, visibility, background resources, enabled state, list contents, scroll position, image URI, and overlay state.
- Renderers must not dispatch `SessionIntent` directly unless a callback is explicitly injected for dynamic controls such as zoom chips.
- Renderers must not read `container.cameraSession` or own session runtime state.

## Files

Modify:

- `app/src/main/java/com/opencamera/app/MainActivity.kt`

Create:

- `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
- `app/src/main/java/com/opencamera/app/MainActivityRenderer.kt`
- `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt`
- `app/src/main/java/com/opencamera/app/FilterLabPanelRenderer.kt`
- `app/src/main/java/com/opencamera/app/DevConsoleRenderer.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`

Tests:

- Prefer existing render-model tests for data correctness.
- Add small JVM tests only for extracted pure mappers, not Android view mutation.
- Use `:app:assembleDebug` as the primary verification for Android view wiring.

## Proposed View Groups

Create grouped data classes in `MainActivityViews.kt`:

```kotlin
internal data class PreviewViews(
    val previewView: PreviewView,
    val overlayView: PreviewOverlayView,
    val thumbnail: ImageView,
    val captureOutput: TextView
)
```

```kotlin
internal data class TopBarViews(
    val titleText: TextView,
    val permissionStatus: TextView,
    val colorLabEntry: Button,
    val settingsEntry: Button
)
```

```kotlin
internal data class QuickPanelViews(
    val panel: androidx.core.widget.NestedScrollView,
    val grid: Button,
    val quality: Button,
    val frame43: Button,
    val frame169: Button,
    val frame11: Button,
    val livePhoto: Button,
    val timer: Button,
    val launcher: Button
)

internal data class SettingsPanelViews(
    val panel: androidx.core.widget.NestedScrollView,
    val close: Button,
    val back: Button,
    val rootContent: LinearLayout,
    val portraitLabContent: LinearLayout,
    val watermarkSelectorContent: LinearLayout,
    val watermarkDetailContent: LinearLayout,
    val headline: TextView,
    val supportingText: TextView,
    val heroSummary: TextView,
    val commonSummary: TextView,
    val photoSummary: TextView,
    val videoSummary: TextView,
    val catalogFooter: TextView,
    val editingHint: TextView,
    val tabCommon: Button,
    val tabPhoto: Button,
    val tabVideo: Button,
    val commonSection: LinearLayout,
    val photoSection: LinearLayout,
    val videoSection: LinearLayout,
    val gridMode: Button,
    val shutterSound: Button,
    val selfieMirror: Button,
    val photoFilter: Button,
    val photoPortraitLab: Button,
    val photoWatermark: Button,
    val photoLive: Button,
    val photoTimer: Button,
    val videoResolution: Button,
    val videoFrameRate: Button,
    val videoDynamicFps: Button,
    val videoAudio: Button,
    val videoFilter: Button,
    val portraitHeadline: TextView,
    val portraitSupportingText: TextView,
    val portraitHeroSummary: TextView,
    val portraitEditingHint: TextView,
    val portraitProfile: Button,
    val portraitBeautyPreset: Button,
    val portraitBeautyStrength: Button,
    val portraitBokehEffect: Button,
    val portraitFooter: TextView,
    val watermarkSelectorHeadline: TextView,
    val watermarkSelectorSupportingText: TextView,
    val watermarkSelectorHeroSummary: TextView,
    val watermarkSelectorList: LinearLayout,
    val watermarkSelectorEditingHint: TextView,
    val watermarkSelectorFooter: TextView,
    val watermarkDetailHeadline: TextView,
    val watermarkDetailSupportingText: TextView,
    val watermarkDetailHeroSummary: TextView,
    val watermarkDetailEditingHint: TextView,
    val watermarkPlacement: Button,
    val watermarkTextScale: Button,
    val watermarkTextOpacity: Button,
    val watermarkFrameBackground: Button,
    val watermarkDetailFooter: TextView
)

internal data class FilterLabViews(
    val panel: androidx.core.widget.NestedScrollView,
    val close: Button,
    val headline: TextView,
    val supportingText: TextView,
    val heroSummary: TextView,
    val currentSummary: TextView,
    val sectionFiltersTitle: TextView,
    val selectionCard: LinearLayout,
    val selectionList: LinearLayout,
    val editingHint: TextView,
    val footer: TextView,
    val photoTab: Button,
    val humanisticTab: Button,
    val portraitTab: Button,
    val videoTab: Button,
    val saveCustom: Button,
    val sectionPaletteTitle: TextView,
    val adjustmentPanel: LinearLayout,
    val modeToggle: Button,
    val paletteSummary: TextView,
    val paletteHint: TextView,
    val paletteSurface: FilterPaletteView,
    val advancedTitle: TextView,
    val advancedControls: LinearLayout,
    val advancedExposure: Button,
    val advancedSoftGlow: Button,
    val advancedHalo: Button,
    val advancedGrain: Button,
    val advancedSharpness: Button,
    val advancedVignette: Button,
    val advancedHighlights: Button,
    val advancedShadows: Button,
    val advancedWarmBoost: Button,
    val advancedCoolBoost: Button,
    val advancedTemperatureShift: Button,
    val advancedTintShift: Button
)

internal data class DevConsoleViews(
    val entry: Button,
    val panel: com.google.android.material.card.MaterialCardView,
    val tabKey: Button,
    val tabCore: Button,
    val tabError: Button,
    val tabAll: Button,
    val title: TextView,
    val summary: TextView,
    val content: TextView,
    val export: Button,
    val close: Button
)

internal data class ModeTrackViews(
    val scroll: android.widget.HorizontalScrollView,
    val photo: Button,
    val night: Button,
    val portrait: Button,
    val pro: Button,
    val video: Button,
    val document: Button,
    val humanistic: Button
)

internal data class BottomCockpitViews(
    val shutter: Button,
    val lensFacing: Button,
    val zoomScroll: android.widget.HorizontalScrollView,
    val zoomRow: LinearLayout
)
```

Then add a `MainActivityViews.bind(activity)` factory. The factory should move the exact current `findViewById` assignments out of `MainActivity.onCreate()` and into the matching groups. Example for the preview group:

```kotlin
internal data class MainActivityViews(
    val preview: PreviewViews,
    val topBar: TopBarViews,
    val quickPanel: QuickPanelViews,
    val settingsPanel: SettingsPanelViews,
    val filterLab: FilterLabViews,
    val devConsole: DevConsoleViews,
    val modeTrack: ModeTrackViews,
    val bottomCockpit: BottomCockpitViews,
    val panelDismissScrim: View
) {
    companion object {
        fun bind(activity: AppCompatActivity): MainActivityViews {
            val preview = PreviewViews(
                previewView = activity.findViewById(R.id.cameraPreview),
                overlayView = activity.findViewById(R.id.previewOverlay),
                thumbnail = activity.findViewById(R.id.previewThumbnail),
                captureOutput = activity.findViewById(R.id.captureOutput)
            )
            return MainActivityViews(
                preview = preview,
                topBar = bindTopBar(activity),
                quickPanel = bindQuickPanel(activity),
                settingsPanel = bindSettingsPanel(activity),
                filterLab = bindFilterLab(activity),
                devConsole = bindDevConsole(activity),
                modeTrack = bindModeTrack(activity),
                bottomCockpit = bindBottomCockpit(activity),
                panelDismissScrim = activity.findViewById(R.id.panelDismissScrim)
            )
        }
    }
}
```

Use exact current view IDs from `MainActivity.kt`. Do not rename IDs.

## Renderer Breakdown

### CockpitSurfaceRenderer

Owns applying:

- top title/app label
- shutter button label/content description/background/enabled state
- lens facing button label/enabled state
- zoom capsule row rendering
- mode track rendering and auto-scroll
- quick bubble row state
- preview mirror `scaleX`
- capture output text

It may accept callbacks:

```kotlin
internal data class CockpitCallbacks(
    val onZoomRatioSelected: (Float) -> Unit
)
```

### SettingsPanelRenderer

Owns applying:

- `SessionSettingsPageRenderModel`
- `PortraitLabPageRenderModel`
- `WatermarkLabSelectorRenderModel`
- `WatermarkLabDetailRenderModel`
- settings tabs visibility/alpha/enabled state
- dynamic watermark template cards

It may accept callbacks:

```kotlin
internal data class SettingsPanelCallbacks(
    val onSettingsAction: (PersistedSettingsAction) -> Unit,
    val onOpenWatermarkDetail: (String) -> Unit
)
```

### FilterLabPanelRenderer

Owns applying:

- `FilterLabPageRenderModel`
- family tabs
- save custom control
- filter selection cards
- adjustment panel and advanced controls
- palette summary/hint

It may accept callbacks:

```kotlin
internal data class FilterLabCallbacks(
    val onSelectFamily: (FilterLabFamily) -> Unit,
    val onPrepareAdjustment: (FilterLabAdjustRenderModel) -> Unit,
    val onSelectFilter: (SettingsControlRenderModel) -> Unit
)
```

Keep the existing `FilterPaletteView.setOnPaletteTouchListener` in the action binder package, not in this renderer, unless package 3 has already landed.

### DevConsoleRenderer

Owns applying:

- dev panel visibility
- dev log title, summary, content
- dev tab enabled/alpha state

It must not export files. Export remains an action binder/platform shell responsibility.

### MainActivityRenderer

Coordinates the renderer calls:

```kotlin
internal class MainActivityRenderer(
    private val views: MainActivityViews,
    private val cockpit: CockpitSurfaceRenderer,
    private val settings: SettingsPanelRenderer,
    private val filterLab: FilterLabPanelRenderer,
    private val devConsole: DevConsoleRenderer
) {
    fun render(snapshot: MainActivityRenderSnapshot) {
        cockpit.render(snapshot)
        settings.render(snapshot)
        filterLab.render(snapshot)
        devConsole.render(snapshot)
    }
}
```

Use a render snapshot to avoid each renderer reading from `MainActivity` fields:

```kotlin
internal data class MainActivityRenderSnapshot(
    val state: SessionState,
    val panelState: CockpitPanelUiState,
    val controls: SessionControlsRenderModel,
    val settingsPage: SessionSettingsPageRenderModel,
    val portraitLabPage: PortraitLabPageRenderModel,
    val watermarkSelectorPage: WatermarkLabSelectorRenderModel,
    val watermarkDetailPage: WatermarkLabDetailRenderModel,
    val filterLabPage: FilterLabPageRenderModel,
    val modeTrack: ModeTrackRenderModel,
    val devLog: DevLogRenderModel,
    val overlay: PreviewOverlayRenderModel,
    val focusReticle: FocusReticleRenderModel?,
    val thumbnailRenderCommand: ThumbnailRenderCommand
)
```

If this snapshot is too large for one first diff, introduce it after moving `MainActivityViews`.

## Implementation Tasks

### Task 1: Create `MainActivityViews`

- Move all view fields into grouped data classes.
- In `MainActivity`, replace individual `lateinit var` fields with:

```kotlin
private lateinit var views: MainActivityViews
```

- Replace `previewView` reads with `views.preview.previewView`, then optionally add local compatibility accessors for smaller diffs:

```kotlin
private val previewView: PreviewView
    get() = views.preview.previewView
```

### Task 2: Move panel visibility rendering

Move `renderPanelVisibility()` into `MainActivityRenderer` or a small `PanelVisibilityRenderer`.

Inputs:

- current `CockpitPanelUiState`
- previous route for scroll reset

The previous-route cache belongs in the renderer, not in `MainActivity`.

### Task 3: Move dev console rendering

Move `renderDevConsoleVisibility()` and `renderDevConsole()` into `DevConsoleRenderer`.

Keep `refreshDevLogModel()` in `MainActivity` until package 3 because it reads trace and selected tab.

### Task 4: Move settings panel rendering

Move:

- `renderSettingsPage`
- `renderSettingsTabs`
- `renderPortraitLabPage`
- `renderWatermarkLabSelectorPage`
- `renderWatermarkLabDetailPage`
- `renderSettingsControl`

to `SettingsPanelRenderer`.

Keep callbacks injected; do not let the renderer call `container.sessionSettingsManager`.

### Task 5: Move filter lab rendering

Move:

- `renderFilterLabPage`
- `renderFilterLabTab`
- `renderSaveCustomControl`
- `renderFilterSelectionList`
- `renderAdjustmentPanel`
- `buttonLabel` helper if it only supports filter UI

to `FilterLabPanelRenderer`.

### Task 6: Move cockpit rendering

Move:

- `renderQuickBubble`
- `renderZoomCapsules`
- `renderModeTrack`
- shutter/lens/capture output application from `render(state)`

to `CockpitSurfaceRenderer`.

Keep `maybePlayShutterSound(state)` in Activity because it owns `MediaActionSound`.

### Task 7: Keep `MainActivity.render(state)` as orchestration only

After extraction, `render(state)` should:

1. Store `latestSessionState`.
2. Build text resolver and render models.
3. Update render-model caches if still needed by action binder.
4. Build or pass a snapshot to renderer.
5. Handle platform side effects that cannot live in passive renderers:
   - locale application
   - shutter sound
   - thumbnail image command if no `ThumbnailRenderer` has been created yet

## Focused Verification

Run after each large move:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.DevLogRenderModelTest --tests com.opencamera.app.ThumbnailRenderCommandTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

After all tasks:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Non-Goals

- Do not migrate to Compose.
- Do not enable ViewBinding in Gradle in this package.
- Do not change UI copy, layout hierarchy, colors, or dimensions.
- Do not change `SessionUiRenderModel` behavior.
- Do not change gesture or click binding yet unless compilation requires a narrow callback.
