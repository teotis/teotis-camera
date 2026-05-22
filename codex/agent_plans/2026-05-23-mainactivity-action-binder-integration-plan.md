# MainActivity Action Binder And Thin Activity Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this task. Use `rtk` for every command. This package is text-only and does not require screenshots.

**Goal:** Move click/touch binding and UI event translation out of `MainActivity.kt`, leaving the Activity as lifecycle/composition shell.

**Architecture:** `MainActivityActionBinder` binds Android views to a narrow callback interface. The binder may read current UI/session snapshots through provider lambdas, but it must not own session runtime state and must not call `container.cameraSession` directly. `MainActivity` implements or supplies callbacks that dispatch session intents, apply settings actions, launch permissions, launch gallery, export logs, and ask the panel router to transition.

**Tech Stack:** Android Views, existing gesture classes, pure resolver tests, `:app:assembleDebug`.

---

## Current Code Facts

- `MainActivity.bindActions()` contains most click listeners and mixes route changes, settings actions, dev log export, gallery launch, permission checks, and session dispatch.
- `MainActivity.bindGestureRouter()` bridges preview touch events to `GesturePolicy`, `GestureGuard`, `PreviewTapFocusGeometry`, and `SessionIntent.PreviewTapToFocus`.
- Existing pure helpers already separate some decisions:
  - `GalleryOpenTarget.kt`
  - `PreviewTapFocusGeometry.kt`
  - `ModeTrackTouchPolicy.kt`
  - `GesturePolicy.kt`
  - `GestureGuard.kt`
- Package 1 should have extracted `CockpitPanelRouter`.
- Package 2 should have extracted `MainActivityViews`.

## Required Behavior

- Preserve every current click behavior.
- Keep all session runtime changes going through `SessionIntent`.
- Keep settings changes going through `SessionSettingsManager.apply(...)` or existing manager methods.
- Keep Android-only operations, such as permission launch, `Toast`, `Intent.ACTION_VIEW`, FileProvider, and dev-log file export, outside renderers.
- Binder can request actions through callbacks; it cannot directly own app container dependencies.

## Files

Modify:

- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/MainActivityViews.kt`

Create:

- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionCallbacks.kt`
- `app/src/main/java/com/opencamera/app/GalleryLauncher.kt`
- `app/src/main/java/com/opencamera/app/PermissionUiController.kt`

Optional create:

- `app/src/main/java/com/opencamera/app/MainActivityUiSnapshot.kt`

Tests:

- Keep existing pure tests.
- Add JVM tests only for new pure mappers or callback decision helpers.
- Use assemble and Stage 7 gate for Android listener wiring.

## Proposed Callback Shape

Create `MainActivityActionCallbacks.kt`:

```kotlin
internal interface MainActivityActionCallbacks {
    fun dispatch(intent: SessionIntent)
    fun applySettingsAction(action: PersistedSettingsAction)
    fun applySettingsControl(control: SettingsControlRenderModel?)
    fun reducePanel(command: CockpitPanelCommand)
    fun renderAfterPanelChange()
    fun renderLatestSettingsSurfaces()
    fun renderLatestFilterLab()
    fun maybeAutoPrepareFilter()
    fun saveCurrentFilterAsCustom(control: FilterLabSaveCustomRenderModel?)
    fun openSelectedFilterAdjustment(control: FilterLabAdjustRenderModel?)
    fun applyAdvancedFilterControl(control: FilterAdvancedControl)
    fun handleFilterPaletteTouch(colorAxis: Float, toneAxis: Float)
    fun requestCameraPermissionIfNeeded()
    fun requestMicrophonePermission()
    fun showDisabledReason(reason: String)
    fun openLatestGalleryMedia()
    fun exportDevLog()
}
```

If this interface feels too broad during implementation, split it into:

- `SessionActionCallbacks`
- `PanelActionCallbacks`
- `SettingsActionCallbacks`
- `PlatformActionCallbacks`

Do not pass `AppContainer` into the binder.

## Proposed Snapshot Providers

The binder needs current state but should not store it. Supply provider lambdas:

```kotlin
internal data class MainActivityUiSnapshot(
    val sessionState: SessionState?,
    val panelState: CockpitPanelUiState,
    val settingsPage: SessionSettingsPageRenderModel?,
    val portraitLabPage: PortraitLabPageRenderModel?,
    val watermarkDetailPage: WatermarkLabDetailRenderModel?,
    val filterLabPage: FilterLabPageRenderModel?,
    val devLog: DevLogRenderModel?
)
```

Binder constructor:

```kotlin
internal class MainActivityActionBinder(
    private val views: MainActivityViews,
    private val snapshot: () -> MainActivityUiSnapshot,
    private val callbacks: MainActivityActionCallbacks
) {
    fun bind() { ... }
}
```

## Implementation Tasks

### Task 1: Extract gallery launch platform effect

Create `GalleryLauncher.kt`:

```kotlin
internal class GalleryLauncher(
    private val activity: AppCompatActivity
) {
    fun open(target: GalleryOpenTarget): Boolean {
        val uri = when (target.kind) {
            GalleryOpenUriKind.CONTENT_URI -> Uri.parse(target.uri)
            GalleryOpenUriKind.ABSOLUTE_FILE -> {
                val file = File(target.uri)
                if (!file.exists()) return false
                FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, target.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching { activity.startActivity(intent) }.isSuccess
    }
}
```

`MainActivity.openLatestGalleryMedia()` should:

1. Read `latestSessionState?.presentation`.
2. Resolve `galleryOpenTargetFor(...)`.
3. Call `GalleryLauncher.open(target)`.
4. Show `R.string.gallery_open_failed` when no target or launch failure.

### Task 2: Extract permission UI controller

Create `PermissionUiController.kt` for UI text and settings-Intent behavior currently in `requestCameraPermissionIfNeeded()`.

Keep the permission launcher itself in `MainActivity` because it is registered against Activity lifecycle.

The controller may expose:

```kotlin
internal class PermissionUiController(
    private val activity: AppCompatActivity,
    private val permissionStatus: TextView,
    private val text: () -> AppTextResolver
) {
    fun renderRequestPrompt(cameraGranted: Boolean, microphoneGranted: Boolean, launchRequest: () -> Unit) { ... }
}
```

### Task 3: Extract click binding

Move listener setup from `bindActions()` into `MainActivityActionBinder.bind()`.

Examples:

```kotlin
views.panelDismissScrim.setOnClickListener {
    callbacks.reducePanel(CockpitPanelCommand.DismissAll)
    callbacks.renderAfterPanelChange()
}
```

```kotlin
views.bottomCockpit.shutterButton.setOnClickListener {
    val state = snapshot().sessionState
    if (state?.permissionState?.cameraGranted != true) {
        callbacks.requestCameraPermissionIfNeeded()
        return@setOnClickListener
    }
    callbacks.dispatch(SessionIntent.ShutterPressed)
}
```

Use current behavior as source of truth. Do not create new product behavior while extracting.

### Task 4: Extract mode track binding

Move `bindModeTrackTouch()` into the binder or a `ModeTrackActionBinder`.

Keep `ModeTrackScrollGuard` as a field in the binder:

```kotlin
private val modeTrackScrollGuard = ModeTrackScrollGuard(scrollSlopPx = 12f)
```

The binder should:

- attach the guard to `views.modeTrack.scroll`
- hide the humanistic mode button
- check capture disabled reason through a callback or pure helper
- request microphone permission for video mode if needed
- dispatch `SessionIntent.SwitchMode(modeId)`

### Task 5: Extract gesture binding

Move `bindGestureRouter()` into the binder or a `PreviewGestureActionBinder`.

Constructor dependencies:

```kotlin
private val gesturePolicy: GesturePolicy = GesturePolicy()
private val gestureGuard: GestureGuard = GestureGuard()
```

Gesture behavior must remain:

- Build `GestureGuardState` from `snapshot().panelState`.
- Block preview gestures when the guard rejects them.
- Map gestures using `GesturePolicy`.
- Normalize focus taps using `normalizedPreviewTapOrNull`.
- Dispatch `SessionIntent.PreviewTapToFocus`.
- Leave exposure and assisted mode switch TODOs unchanged unless a separate plan exists.

### Task 6: Collapse MainActivity to composition shell

After packages 1-3, `MainActivity` should keep only:

- `container`
- `views`
- `panelRouter`
- `renderer`
- `actionBinder`
- lifecycle-bound launchers/monitors/resources:
  - permission launcher
  - orientation monitor
  - shutter sound
  - dev log exporter or platform helper
- `latestSessionState` and render-model caches only if the binder still needs snapshot providers
- `onCreate`, `bindState`, `render`, `onBackPressed`, `onDestroy`
- small platform callback implementations

Expected direction:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    views = MainActivityViews.bind(this)
    renderer = MainActivityRenderer(...)
    actionBinder = MainActivityActionBinder(views, ::currentUiSnapshot, callbacks)
    actionBinder.bind()
    bindState()
    syncPermissionState()
    initOrientationMonitor()
}
```

Do not chase an exact 200-line target in this package. A stable 350-500-line shell with clear collaborators is a good first landing. Further reduction can follow once tests confirm behavior.

## Focused Verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.GalleryOpenTargetTest --tests com.opencamera.app.PreviewTapFocusGeometryTest --tests com.opencamera.app.gesture.GesturePolicyTest --tests com.opencamera.app.gesture.GestureGuardTest --tests com.opencamera.app.ModeTrackTouchPolicyTest --tests com.opencamera.app.CockpitPanelRouterTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

If Gradle reports transient Kotlin/build-directory errors under `~/.codex-build/OpenCamera`, rerun the smallest failing verification serially before treating it as a product regression.

## Non-Goals

- Do not introduce a broad ViewModel in this package.
- Do not move session kernel ownership.
- Do not change settings persistence semantics.
- Do not change route IA, visible labels, or gestures.
- Do not add new feature behavior while extracting action binding.

