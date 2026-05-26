# Photo Quick Brightness Adjustment Plan

> For text-only agents: this task adds quick-panel brightness controls and session-owned preview EV. UI renders controls and dispatches intents only. Use `rtk` for every command.

## Goal

Add frame brightness adjustment in the `快捷` quick panel for photo mode. The adjustment should visibly affect preview exposure when supported, expose degraded/unsupported results, and be tracked in session state rather than hidden in UI.

## Required Semantics

- Brightness adjustment is a session runtime control, not a persisted photo default in this pass.
- The value is normalized as exposure compensation steps, default `0`.
- Range should come from device capability when available; otherwise use a conservative `-2..2` UI range and expect the adapter to return degraded or unsupported.
- UI must not call CameraX directly.
- Changing brightness during active still capture or countdown should be blocked.
- Changing brightness during recording is out of scope for this photo-mode pass.
- Brightness resets to `0` on session boot, lens switch, and mode switch. A later product pass may choose per-mode persistence, but this pass should keep the runtime value simple and tested.
- If the device applies preview EV, return `APPLIED`.
- If the device can store EV only for still capture metadata but not preview, return `DEGRADED_SAVED_ONLY`.
- If unsupported, return `UNSUPPORTED`.
- If CameraX fails, return `FAILED` and do not mark the control as applied.

## Current Gap

- `GesturePolicy` maps vertical preview scroll to `GestureAction.ShowExposureHint`, but `MainActivityActionBinder` leaves it as TODO.
- Quick panel has no brightness row.
- Device contracts have `ManualControlSupport.exposureCompensation` for manual capture, but no preview brightness command/result.
- `CameraXCaptureAdapter` applies exposure compensation to still ImageCapture via Camera2 interop when manual params request it. It does not expose preview EV through `CameraControl.exposureCompensationIndex`.

## Files To Inspect Or Modify

- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionIntentOwnership.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
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
- `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterCapabilityDetectionTest.kt`

## Contract Shape

Add preview brightness types in `DeviceContracts.kt`:

```kotlin
data class PreviewBrightnessRange(
    val minSteps: Int,
    val maxSteps: Int
) {
    fun clamp(value: Int): Int = value.coerceIn(minSteps, maxSteps)

    companion object {
        val CONSERVATIVE = PreviewBrightnessRange(-2, 2)
        val UNSUPPORTED = PreviewBrightnessRange(0, 0)
    }
}

enum class PreviewBrightnessResultStatus {
    APPLIED,
    DEGRADED_SAVED_ONLY,
    FAILED,
    UNSUPPORTED
}

data class PreviewBrightnessRequest(
    val requestId: String,
    val exposureCompensationSteps: Int
)

data class PreviewBrightnessResult(
    val requestId: String,
    val exposureCompensationSteps: Int,
    val status: PreviewBrightnessResultStatus,
    val reason: String? = null
)
```

Extend `DeviceCapabilities`:

```kotlin
val previewBrightnessRange: PreviewBrightnessRange = PreviewBrightnessRange.CONSERVATIVE
```

Populate this field during app/device capability detection from CameraX exposure state when available. If detection is not available for a fake adapter, use `PreviewBrightnessRange.CONSERVATIVE` in tests and return explicit unsupported results from the adapter where appropriate.

Extend `DeviceCommand`:

```kotlin
data class ApplyPreviewBrightness(val request: PreviewBrightnessRequest) : DeviceCommand
```

Extend `DeviceEvent`:

```kotlin
data class PreviewBrightnessApplied(val result: PreviewBrightnessResult) : DeviceEvent
```

Extend session contracts:

```kotlin
enum class PreviewBrightnessFeedbackStatus {
    REQUESTED,
    APPLIED,
    DEGRADED_SAVED_ONLY,
    FAILED,
    UNSUPPORTED
}

data class PreviewBrightnessFeedback(
    val requestId: String,
    val requestedSteps: Int,
    val appliedSteps: Int?,
    val status: PreviewBrightnessFeedbackStatus,
    val reason: String? = null
)
```

Add to `SessionPresentationState`:

```kotlin
val previewBrightnessSteps: Int = 0
val previewBrightnessFeedback: PreviewBrightnessFeedback? = null
```

Add to `SessionIntent`:

```kotlin
data class ApplyPreviewBrightness(val exposureCompensationSteps: Int) : SessionIntent
data object IncreasePreviewBrightness : SessionIntent
data object DecreasePreviewBrightness : SessionIntent
data object ResetPreviewBrightness : SessionIntent
data class PreviewBrightnessApplied(
    val result: com.opencamera.core.device.PreviewBrightnessResult
) : SessionIntent
```

Add to `SessionEffect`:

```kotlin
data class ApplyPreviewBrightness(
    val request: com.opencamera.core.device.PreviewBrightnessRequest
) : SessionEffect
```

Map brightness intents and results to `MODE_CONTROL` in `SessionIntentOwnership.kt` for this pass. Do not make UI own the state.

## Session Rules

For `IncreasePreviewBrightness` and `DecreasePreviewBrightness`:

1. Resolve current value from `state.presentation.previewBrightnessSteps`.
2. Resolve range from `state.activeDeviceCapabilities.previewBrightnessRange`.
3. Step by `1`.
4. Delegate to `ApplyPreviewBrightness(newValue)`.

For `ResetPreviewBrightness`:

- Delegate to `ApplyPreviewBrightness(0)`.

For `ApplyPreviewBrightness(value)`:

1. If active mode is not `PHOTO`, update `lastAction` and emit no effect.
2. If `previewStatus != ACTIVE`, emit no effect.
3. If camera permission or host is missing, emit no effect.
4. If countdown or active photo shot exists, block and emit no effect.
5. Clamp to range.
6. Generate request ids like `brightness-1`.
7. Set `previewBrightnessFeedback = REQUESTED`.
8. Emit `SessionEffect.ApplyPreviewBrightness`.
9. Trace:

```text
preview.brightness.requested -> requestId=brightness-1,steps=1
```

For `PreviewBrightnessApplied(result)`:

- Ignore stale request ids.
- If `APPLIED`, set `previewBrightnessSteps = result.exposureCompensationSteps`.
- If `DEGRADED_SAVED_ONLY`, keep `previewBrightnessSteps` unchanged and show degraded feedback. Do not silently imply that preview brightness changed.
- If `FAILED` or `UNSUPPORTED`, do not change `previewBrightnessSteps`.
- Trace one of:

```text
preview.brightness.applied
preview.brightness.degraded
preview.brightness.failed
preview.brightness.unsupported
```

## Adapter Rules

In `CameraSessionCoordinator.handleEffect`:

```kotlin
is SessionEffect.ApplyPreviewBrightness -> cameraAdapter.dispatch(
    DeviceCommand.ApplyPreviewBrightness(effect.request)
)
```

In `handleDeviceEvent`:

```kotlin
is DeviceEvent.PreviewBrightnessApplied -> session.dispatch(
    SessionIntent.PreviewBrightnessApplied(event.result)
)
```

In `CameraXCaptureAdapter.dispatch`:

- Handle `DeviceCommand.ApplyPreviewBrightness`.
- If there is no bound camera, emit `FAILED`.
- If CameraX exposure compensation range includes the requested value, call:

```kotlin
camera.cameraControl.setExposureCompensationIndex(steps).await()
```

- If CameraX reports unsupported range or throws unsupported, emit `UNSUPPORTED`.
- If `manualControlCapabilities?.exposureCompensation == ManualControlSupport.SAVED_ONLY` and CameraX preview EV is unavailable, emit `DEGRADED_SAVED_ONLY`.
- Do not classify brightness failure as preview bind failure.

Capability detection:

- Use `camera.cameraInfo.exposureState.exposureCompensationRange`.
- Convert CameraX range into `PreviewBrightnessRange`.
- If range is empty or min/max both zero, expose unsupported or conservative zero range.

## Quick Panel UI

Add a brightness row to the quick bubble:

- Label: Chinese `亮度`, English `Bright`.
- Controls: minus button, value button, and plus button. Tapping the value button resets brightness to `0`.
- Keep the row enabled only in photo mode, active preview, no countdown, no active photo shot.
- Display values as:
  - `0`
  - `+1`
  - `-1`
- If unsupported, show `N/A` and disable plus/minus.

Suggested render model:

```kotlin
internal data class QuickBrightnessRenderModel(
    val title: String,
    val value: String,
    val canDecrease: Boolean,
    val canIncrease: Boolean,
    val canReset: Boolean,
    val isVisible: Boolean,
    val disabledReason: String?
)
```

Add this to `QuickPanelSheetRenderModel`.

Update `activity_main.xml` by adding one row inside `quickBubblePanel`, preferably after quality and before frame ratio. Use existing quick bubble style and stable dimensions. Do not enlarge the panel beyond its existing bounded area without testing text fit.

Update `MainActivityViews.QuickPanelViews` with:

- `brightnessMinus: Button`
- `brightnessValue: Button` or `TextView`
- `brightnessPlus: Button`

Update `MainActivityActionBinder`:

- Minus dispatches `SessionIntent.DecreasePreviewBrightness`.
- Plus dispatches `SessionIntent.IncreasePreviewBrightness`.
- Value/reset dispatches `SessionIntent.ResetPreviewBrightness`.
- Leave vertical preview scroll unchanged in this pass. The requested surface is `快捷`; gesture brightness needs a separate interaction decision.

## Tests

Add session tests:

- Increase emits `SessionEffect.ApplyPreviewBrightness`.
- Decrease clamps at min.
- Increase clamps at max.
- Reset requests zero.
- Busy countdown blocks brightness.
- Active still shot blocks brightness.
- Result `APPLIED` updates `previewBrightnessSteps`.
- Result `UNSUPPORTED` does not update steps.
- Stale result is ignored.

Add coordinator tests:

- `SessionEffect.ApplyPreviewBrightness` maps to `DeviceCommand.ApplyPreviewBrightness`.
- `DeviceEvent.PreviewBrightnessApplied` maps to `SessionIntent.PreviewBrightnessApplied`.

Add adapter tests:

- Supported CameraX exposure range returns `APPLIED`.
- Unsupported range returns `UNSUPPORTED`.
- Missing bound camera returns `FAILED`.
- Saved-only manual exposure capability returns `DEGRADED_SAVED_ONLY` when CameraX preview EV is unavailable.

Add UI/render tests:

- Quick panel includes brightness row in photo mode.
- Brightness row is disabled during active shot/countdown.
- Value labels format `-1`, `0`, `+1`.
- Button labels remain short enough for quick panel width.

Focused verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Non-Goals

- Do not persist brightness as a default setting in this pass.
- Do not modify filter `brightnessShift`; that is Color Lab/postprocess brightness, not live exposure.
- Do not promise exact EV units in the UI.
- Do not add video brightness control unless a separate video-mode plan asks for it.
- Do not implement a custom image-processing preview brightness shader.
