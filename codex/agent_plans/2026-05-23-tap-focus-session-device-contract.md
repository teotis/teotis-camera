# Tap Focus Session And Device Contract Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this task. Use `rtk` for every command. This task defines contracts only; CameraX execution and UI reticle are separate packages.

**Goal:** Add a session-owned, device-command-driven contract for preview tap focus and automatic exposure metering.

**Architecture:** UI sends a session intent with a normalized preview point. Session validates preview state, records ephemeral feedback, and emits a device effect. Coordinator forwards the effect as a `DeviceCommand`. The device adapter reports success, degraded AE-only behavior, failure, or unsupported behavior through `DeviceEvent`.

**Tech Stack:** Kotlin contracts in `core:session` and `core:device`, coroutine flows, session/coordinator unit tests.

---

## Required Semantics

- A tap focus request is session-ephemeral. It is not a persisted setting.
- The request point is normalized in preview-view coordinates:
  - `normalizedX` in `0.0..1.0`
  - `normalizedY` in `0.0..1.0`
- Default mode is focus plus automatic exposure metering.
- If focus is unsupported but AE metering is supported, the result is degraded, not failed.
- If neither AF nor AE metering is supported, the result is unsupported.
- Session should ignore requests when preview is not active or is recovering.
- Session must not emit a device command if the camera permission or preview host is missing.
- Recording support should be conservative: allow metering during active preview and active recording only if the device adapter can execute it; never stop recording to apply metering.

## Files

Modify:

- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `app/src/test/java/com/opencamera/app/camera/CameraSessionCoordinatorTest.kt`

## Contract Shape

Add device-level value types:

```kotlin
data class PreviewMeteringPoint(
    val normalizedX: Float,
    val normalizedY: Float
) {
    fun clamped(): PreviewMeteringPoint = PreviewMeteringPoint(
        normalizedX = normalizedX.coerceIn(0f, 1f),
        normalizedY = normalizedY.coerceIn(0f, 1f)
    )
}

enum class PreviewMeteringMode {
    FOCUS_AND_AUTO_EXPOSURE,
    AUTO_EXPOSURE_ONLY
}

data class PreviewMeteringRequest(
    val requestId: String,
    val point: PreviewMeteringPoint,
    val mode: PreviewMeteringMode = PreviewMeteringMode.FOCUS_AND_AUTO_EXPOSURE,
    val autoCancelMillis: Long = 3_000L
)

enum class PreviewMeteringResultStatus {
    SUCCEEDED,
    DEGRADED_AUTO_EXPOSURE_ONLY,
    FAILED,
    UNSUPPORTED
}

data class PreviewMeteringResult(
    val requestId: String,
    val point: PreviewMeteringPoint,
    val status: PreviewMeteringResultStatus,
    val reason: String? = null
)
```

Add to `DeviceCommand`:

```kotlin
data class ApplyPreviewMetering(
    val request: PreviewMeteringRequest
) : DeviceCommand
```

Add to `DeviceEvent`:

```kotlin
data class PreviewMeteringCompleted(
    val result: PreviewMeteringResult
) : DeviceEvent
```

Add session presentation feedback:

```kotlin
enum class PreviewMeteringFeedbackStatus {
    REQUESTED,
    SUCCEEDED,
    DEGRADED_AUTO_EXPOSURE_ONLY,
    FAILED,
    UNSUPPORTED
}

data class PreviewMeteringFeedback(
    val requestId: String,
    val normalizedX: Float,
    val normalizedY: Float,
    val status: PreviewMeteringFeedbackStatus,
    val reason: String? = null
)
```

Add to `SessionPresentationState`:

```kotlin
val previewMeteringFeedback: PreviewMeteringFeedback? = null
```

Add to `SessionIntent`:

```kotlin
data class PreviewTapToFocus(
    val normalizedX: Float,
    val normalizedY: Float
) : SessionIntent

data class PreviewMeteringCompleted(
    val result: com.opencamera.core.device.PreviewMeteringResult
) : SessionIntent
```

Add to `SessionEffect`:

```kotlin
data class ApplyPreviewMetering(
    val request: com.opencamera.core.device.PreviewMeteringRequest
) : SessionEffect
```

## Session Rules

In `DefaultCameraSession.handlePreviewTapToFocus`:

1. Clamp the normalized point.
2. If `previewStatus != PreviewStatus.ACTIVE`, record trace:

```text
preview.metering.ignored -> reason=preview-not-active,status=<status>
```

3. If `permissionState.cameraGranted == false` or `previewHostAvailable == false`, record trace and do not emit effect.
4. Generate request id using a local counter:

```text
meter-1
meter-2
```

5. Update presentation:

```kotlin
previewMeteringFeedback = PreviewMeteringFeedback(
    requestId = requestId,
    normalizedX = point.normalizedX,
    normalizedY = point.normalizedY,
    status = PreviewMeteringFeedbackStatus.REQUESTED
)
```

6. Emit `SessionEffect.ApplyPreviewMetering(request)`.
7. Record trace:

```text
preview.metering.requested -> requestId=meter-1,x=0.50,y=0.40,mode=focus+ae
```

In `handlePreviewMeteringCompleted`:

1. Ignore stale result ids that do not match current `previewMeteringFeedback?.requestId`.
2. Map device result status to presentation status.
3. Record one of:

```text
preview.metering.succeeded
preview.metering.degraded
preview.metering.failed
preview.metering.unsupported
```

## Coordinator Rules

In `CameraSessionCoordinator.handleEffect`:

```kotlin
is SessionEffect.ApplyPreviewMetering -> cameraAdapter.dispatch(
    DeviceCommand.ApplyPreviewMetering(effect.request)
)
```

In `handleDeviceEvent`:

```kotlin
is DeviceEvent.PreviewMeteringCompleted -> session.dispatch(
    SessionIntent.PreviewMeteringCompleted(event.result)
)
```

## Tests

Add `DefaultCameraSessionTest` cases:

- Active preview tap emits `SessionEffect.ApplyPreviewMetering`.
- Request point is clamped to `0.0..1.0`.
- Preview inactive tap is ignored and emits no device effect.
- Missing permission tap is ignored and emits no device effect.
- Completion updates `previewMeteringFeedback` to `SUCCEEDED`.
- Stale completion result does not overwrite a newer request.
- Unsupported result updates feedback to `UNSUPPORTED`.

Add `CameraSessionCoordinatorTest` cases:

- `SessionEffect.ApplyPreviewMetering` forwards `DeviceCommand.ApplyPreviewMetering`.
- `DeviceEvent.PreviewMeteringCompleted` forwards `SessionIntent.PreviewMeteringCompleted`.

Expected command examples:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
```

## Non-Goals

- Do not implement CameraX `FocusMeteringAction` in this package.
- Do not draw UI reticles in this package.
- Do not persist tap focus/EV into settings.
- Do not introduce a second camera state owner in `MainActivity`.

