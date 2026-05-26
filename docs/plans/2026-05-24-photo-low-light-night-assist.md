# Photo Low-Light / Night Assist Strategy Plan

> For text-only agents: this task is a session-owned adaptive photo strategy. Do not auto-switch modes and do not let UI decide capture behavior. Use `rtk` for every command.

## Goal

Add a photo-mode low-light/night assist strategy that is enabled by default, can be toggled by the user, shows a floating night prompt for 3 seconds when low-light conditions are detected, and changes photo capture strategy only through mode/session contracts.

## Required Semantics

- The user setting is persisted under photo settings and defaults to enabled.
- Scene detection is runtime state, not persisted settings.
- Prompt visibility is session presentation state and expires after 3 seconds.
- Low-light detection should use hysteresis:
  - Enter low light after two consecutive samples with brightness score at or below `0.18`.
  - Exit low light after two consecutive samples with brightness score at or above `0.24`.
  - If the signal source is unknown, the state is `UNKNOWN`, not `false`.
- The prompt appears when active mode is `PHOTO`, preview is active, and light state enters `LOW_LIGHT` or stays low-light after the user toggles the setting.
- The prompt should not reappear every sampling tick. It may reappear after low-light exits and re-enters, or after the user toggles the setting.
- If the setting is enabled and low light is active, photo capture uses low-light strategy.
- If `DeviceCapabilities.supportsNightMultiFrame` is true, use multi-frame strategy.
- If still capture is supported but night multi-frame is false, use degraded single-frame brightening.
- If still capture is unsupported, the strategy is unsupported and the icon should not claim it can capture.
- UI can render and tap the icon, but it must only dispatch a settings action or session intent.

## Files To Inspect Or Modify

- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDataModels.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsActions.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt`
- `core/settings/src/test/kotlin/com/opencamera/core/settings/PersistedSettingsSerializerTest.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionIntentOwnership.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
- `feature/mode-photo/src/test/kotlin/com/opencamera/feature/photo/PhotoModePluginTest.kt` new. If the module currently lacks test wiring, add the focused capture-strategy assertions to `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt` instead and keep the test helper local.
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- `app/src/main/java/com/opencamera/app/camera/PreviewSceneBrightnessMonitor.kt` new, if implementing the low-rate sampler.
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/camera/CameraSessionCoordinatorTest.kt`

## Contract Shape

Add device-level scene signal and support semantics in `DeviceContracts.kt`:

```kotlin
enum class PhotoLowLightStrategySupport {
    UNSUPPORTED,
    DEGRADED_SINGLE_FRAME,
    SUPPORTED_MULTI_FRAME
}

enum class SceneLightState {
    UNKNOWN,
    NORMAL,
    LOW_LIGHT
}

data class PhotoSceneSignal(
    val lightState: SceneLightState = SceneLightState.UNKNOWN,
    val brightnessScore: Float? = null,
    val source: String = "unknown"
)

fun DeviceCapabilities.photoLowLightStrategySupport(): PhotoLowLightStrategySupport = when {
    !supportsStillCapture -> PhotoLowLightStrategySupport.UNSUPPORTED
    supportsNightMultiFrame -> PhotoLowLightStrategySupport.SUPPORTED_MULTI_FRAME
    else -> PhotoLowLightStrategySupport.DEGRADED_SINGLE_FRAME
}
```

Add this field to the existing `PhotoSettings` constructor:

```kotlin
val lowLightNightAssistEnabled: Boolean = true
```

Add this action to the existing `PersistedSettingsAction` sealed interface:

```kotlin
data class UpdatePhotoLowLightNightAssistEnabled(val enabled: Boolean) : PersistedSettingsAction
```

Add serializer key:

```kotlin
private const val KEY_PHOTO_LOW_LIGHT_NIGHT_ASSIST =
    "photo.lowLightNightAssistEnabled"
```

Add session presentation:

```kotlin
enum class PhotoLowLightPromptStatus {
    HIDDEN,
    AVAILABLE_ENABLED,
    AVAILABLE_DISABLED,
    DEGRADED_ENABLED,
    DEGRADED_DISABLED,
    UNSUPPORTED
}

data class PhotoLowLightPrompt(
    val status: PhotoLowLightPromptStatus,
    val visibleUntilElapsedMillis: Long?,
    val brightnessScore: Float?,
    val message: String
)
```

Add to `SessionPresentationState`:

```kotlin
val photoSceneSignal: PhotoSceneSignal = PhotoSceneSignal()
val photoLowLightPrompt: PhotoLowLightPrompt? = null
```

Add session intents:

```kotlin
data class PhotoSceneSignalUpdated(val signal: PhotoSceneSignal) : SessionIntent
data object PhotoLowLightPromptExpired : SessionIntent
```

Ownership:

- Map `PhotoSceneSignalUpdated` and `PhotoLowLightPromptExpired` to `PREVIEW_RECOVERY` for this pass, because that owner already handles preview-derived low-frequency presentation events.
- Do not add a toggle session intent. Wire the prompt tap to `PersistedSettingsAction.UpdatePhotoLowLightNightAssistEnabled`.

## Scene Signal Source

First implementation should use a low-rate, replaceable app monitor:

- Create `PreviewSceneBrightnessMonitor` in `app/src/main/java/com/opencamera/app/camera`.
- It should sample at most once every 700-1000 ms while preview is active.
- It may use `PreviewView.bitmap` at a small scaled size, or a fake provider in tests.
- It emits `PhotoSceneSignal` with `source = "preview-bitmap-luma"`.
- It must stop when preview host detaches, preview stops, or lifecycle shuts down.
- It must not dispatch capture commands or change settings.

The coordinator should forward signals:

```kotlin
session.dispatch(SessionIntent.PhotoSceneSignalUpdated(signal))
```

If the monitor cannot sample, emit `SceneLightState.UNKNOWN` only when the previous state needs clearing; do not spam unknown signals.

## Session Rules

When `PhotoSceneSignalUpdated` arrives:

1. Store the signal in presentation state.
2. Ignore prompt logic unless `activeMode == ModeId.PHOTO`.
3. Ignore prompt logic unless `previewStatus == PreviewStatus.ACTIVE`.
4. Resolve support with `activeDeviceCapabilities.photoLowLightStrategySupport()`.
5. If `signal.lightState == LOW_LIGHT`, set `photoLowLightPrompt` visible for 3 seconds.
6. If the setting is enabled:
   - support multi-frame -> `AVAILABLE_ENABLED`
   - degraded single-frame -> `DEGRADED_ENABLED`
7. If the setting is disabled:
   - support multi-frame -> `AVAILABLE_DISABLED`
   - degraded single-frame -> `DEGRADED_DISABLED`
8. If unsupported, use `UNSUPPORTED` only for diagnostics; do not show a user-facing icon as available.
9. Record trace:

```text
photo.low-light.detected -> state=LOW_LIGHT,score=0.16,support=SUPPORTED_MULTI_FRAME,setting=enabled
photo.low-light.prompt.visible -> untilElapsedMillis=<currentElapsedMillis+3000>
```

When `PhotoLowLightPromptExpired` arrives:

- Hide the prompt only if its `visibleUntilElapsedMillis` has passed.
- Do not clear the stored scene signal.
- Record trace:

```text
photo.low-light.prompt.hidden -> expired
```

When settings update disables assist while low light is still active:

- Keep `photoSceneSignal`.
- Show `AVAILABLE_DISABLED` or `DEGRADED_DISABLED` for 3 seconds so the user receives feedback.

## Photo Mode Capture Rules

Extend `ModeRuntimeState` or `ModeContext` with a low-light photo runtime snapshot. Keep this read-only from the mode plugin.

Suggested shape:

```kotlin
data class PhotoLowLightRuntimeState(
    val settingEnabled: Boolean,
    val sceneSignal: PhotoSceneSignal,
    val support: PhotoLowLightStrategySupport
) {
    val shouldUseNightAssist: Boolean
        get() = settingEnabled &&
            sceneSignal.lightState == SceneLightState.LOW_LIGHT &&
            support != PhotoLowLightStrategySupport.UNSUPPORTED
}
```

In `PhotoModePlugin.handle(ShutterPressed)`:

- If `shouldUseNightAssist == false`, preserve current behavior.
- If support is `SUPPORTED_MULTI_FRAME`, submit `CaptureStrategy.MultiFrame` with conservative profile:

```kotlin
CaptureProfile(
    frameCount = 6,
    longExposureMillis = 450L,
    requiresTripod = false,
    flashMode = FlashMode.OFF,
    stillCaptureQuality = runtimeState().stillCaptureQuality,
    stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
)
```

- If support is `DEGRADED_SINGLE_FRAME`, submit `CaptureStrategy.SingleFrame` with `PostProcessSpec.algorithmProfile = "photo-low-light-single-frame"`.
- In both paths add metadata:

```kotlin
put("photoLowLightNightAssist", "on")
put("photoLowLightStrategy", "multi-frame" or "single-frame-degraded")
put("photoLowLightBrightnessScore", signal.brightnessScore?.toString() ?: "unknown")
put("photoLowLightSignalSource", signal.source)
```

Do not reuse `NightModeController` directly. Share only constants/helpers if a small pure helper is useful.

## UI Rules

The floating prompt can be a small `Button` or `TextView` anchored near the existing right rail. It should:

- Be visible only when `photoLowLightPrompt` is visible and not expired.
- Show a concise icon-like label, such as `夜` in Chinese and `Night` or `N` in English if no drawable asset is added.
- Use content description from strings.
- On tap, apply `PersistedSettingsAction.UpdatePhotoLowLightNightAssistEnabled(!currentValue)`.
- Not directly call CameraX or mutate session state.

No raster asset is required for this pass.

## Tests

Add settings tests:

- Default `PhotoSettings().lowLightNightAssistEnabled` is true.
- Reducer toggles only this field.
- Serializer round-trips the field.
- Missing serializer key falls back to true.

Add session tests:

- Low-light signal in photo mode with active preview shows prompt for 3 seconds.
- Low-light signal outside photo mode does not show prompt.
- Normal-light signal clears or leaves hidden prompt.
- Disabled setting shows disabled prompt state on low-light detection.
- Multi-frame support maps to available state.
- No multi-frame maps to degraded state.
- Unsupported still capture does not present as available.

Add photo mode tests:

- Normal light preserves current single-frame behavior.
- Enabled low light with multi-frame support submits `CaptureStrategy.MultiFrame`.
- Enabled low light without multi-frame submits degraded single-frame strategy with metadata.
- Disabled low-light assist preserves normal photo behavior even when signal says low light.

Focused verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Non-Goals

- Do not tune final low-light thresholds for a real device in this pass.
- Do not claim real ISP/vendor night mode parity.
- Do not auto-enter `ModeId.NIGHT`.
- Do not build a high-frequency image analysis pipeline.
- Do not add a new hidden session kernel in coordinator, monitor, UI, or mode plugin.
