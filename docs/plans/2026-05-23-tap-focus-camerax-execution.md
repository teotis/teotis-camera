# CameraX Tap Focus And Auto EV Execution Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this task. Use `rtk` for every command. This package depends on the contract names from `2026-05-23-tap-focus-session-device-contract.md`.

**Goal:** Execute preview tap focus and automatic exposure metering through CameraX using `FocusMeteringAction`.

**Architecture:** `CameraXCaptureAdapter` owns CameraX runtime calls. It receives `DeviceCommand.ApplyPreviewMetering`, converts the normalized preview point into a `PreviewView.meteringPointFactory` point, applies AF + AE metering, and emits `DeviceEvent.PreviewMeteringCompleted`.

**Tech Stack:** CameraX `FocusMeteringAction`, `MeteringPointFactory`, `CameraControl.startFocusAndMetering`, Kotlin coroutines, adapter/coordinator tests.

---

## Required Behavior

- A tap request should apply both focus and auto exposure metering at the tapped area.
- If AF + AE is unsupported but AE-only is supported, apply AE-only and report degraded result.
- If both are unsupported, report unsupported result.
- If CameraX throws or no camera is bound, report failed result.
- Do not rebind preview to focus.
- Do not stop recording to focus.
- Do not alter persisted EV/manual settings. The "auto EV" behavior is CameraX AE metering region selection, not a saved EV compensation value.

## Files

Modify:

- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/test/java/com/opencamera/app/camera/CameraSessionCoordinatorTest.kt`

Create:

- `app/src/test/java/com/opencamera/app/camera/PreviewMeteringActionPlannerTest.kt`

Optional helper in adapter file or separate file:

- `app/src/main/java/com/opencamera/app/camera/PreviewMeteringActionPlanner.kt`

## Implementation Tasks

### Task 1: Add pure planning helpers

Create helper functions that can be tested without CameraX objects:

```kotlin
internal data class PreviewMeteringPixelPoint(
    val x: Float,
    val y: Float
)

internal fun previewMeteringPixelPoint(
    normalizedX: Float,
    normalizedY: Float,
    viewWidth: Int,
    viewHeight: Int
): PreviewMeteringPixelPoint {
    val width = viewWidth.coerceAtLeast(1)
    val height = viewHeight.coerceAtLeast(1)
    return PreviewMeteringPixelPoint(
        x = normalizedX.coerceIn(0f, 1f) * width.toFloat(),
        y = normalizedY.coerceIn(0f, 1f) * height.toFloat()
    )
}
```

Test cases:

- `(0.5, 0.25)` in `1000 x 800` becomes `(500, 200)`.
- Values below `0` clamp to `0`.
- Values above `1` clamp to the view edge.
- Zero width/height do not crash.

### Task 2: Handle DeviceCommand in CameraXCaptureAdapter

Add a branch to `dispatch(command)`:

```kotlin
is DeviceCommand.ApplyPreviewMetering -> runCatching {
    applyPreviewMetering(command.request)
}.onFailure { throwable ->
    _events.emit(
        DeviceEvent.PreviewMeteringCompleted(
            PreviewMeteringResult(
                requestId = command.request.requestId,
                point = command.request.point.clamped(),
                status = PreviewMeteringResultStatus.FAILED,
                reason = throwable.message ?: "Preview metering failed"
            )
        )
    )
}
```

### Task 3: Implement CameraX metering

Expected shape:

```kotlin
private suspend fun applyPreviewMetering(request: PreviewMeteringRequest) {
    withContext(Dispatchers.Main.immediate) {
        val camera = boundCamera
        val preview = boundPreviewView
        if (camera == null || preview == null) {
            _events.emit(
                DeviceEvent.PreviewMeteringCompleted(
                    PreviewMeteringResult(
                        requestId = request.requestId,
                        point = request.point.clamped(),
                        status = PreviewMeteringResultStatus.FAILED,
                        reason = "Preview is not bound"
                    )
                )
            )
            return@withContext
        }

        val point = request.point.clamped()
        val pixel = previewMeteringPixelPoint(
            normalizedX = point.normalizedX,
            normalizedY = point.normalizedY,
            viewWidth = preview.width,
            viewHeight = preview.height
        )
        val meteringPoint = preview.meteringPointFactory.createPoint(pixel.x, pixel.y, 0.15f)

        val focusAndAeAction = FocusMeteringAction.Builder(
            meteringPoint,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        )
            .setAutoCancelDuration(request.autoCancelMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        val aeOnlyAction = FocusMeteringAction.Builder(
            meteringPoint,
            FocusMeteringAction.FLAG_AE
        )
            .setAutoCancelDuration(request.autoCancelMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        val actionAndStatus = when {
            camera.cameraInfo.isFocusMeteringSupported(focusAndAeAction) ->
                focusAndAeAction to PreviewMeteringResultStatus.SUCCEEDED

            camera.cameraInfo.isFocusMeteringSupported(aeOnlyAction) ->
                aeOnlyAction to PreviewMeteringResultStatus.DEGRADED_AUTO_EXPOSURE_ONLY

            else -> null
        }

        if (actionAndStatus == null) {
            _events.emit(
                DeviceEvent.PreviewMeteringCompleted(
                    PreviewMeteringResult(
                        requestId = request.requestId,
                        point = point,
                        status = PreviewMeteringResultStatus.UNSUPPORTED,
                        reason = "Focus and exposure metering are unsupported"
                    )
                )
            )
            return@withContext
        }

        val result = camera.cameraControl.startFocusAndMetering(actionAndStatus.first).await()
        _events.emit(
            DeviceEvent.PreviewMeteringCompleted(
                PreviewMeteringResult(
                    requestId = request.requestId,
                    point = point,
                    status = actionAndStatus.second,
                    reason = if (result.isFocusSuccessful || actionAndStatus.second != PreviewMeteringResultStatus.SUCCEEDED) {
                        null
                    } else {
                        "Focus did not lock"
                    }
                )
            )
        )
    }
}
```

If `emit` cannot be called directly inside `withContext(Dispatchers.Main.immediate)`, compute the result on main and emit after the main block.

### Task 4: Keep runtime issue handling separate

Do not classify tap focus failure as preview bind failure. Tap focus failure should be represented by `PreviewMeteringResultStatus.FAILED` or `UNSUPPORTED`, not by `DeviceRuntimeIssueKind.BIND_FAILURE`.

Only emit `DeviceRuntimeIssue` if applying metering reveals a serious camera runtime problem that also breaks preview.

### Task 5: Add tests

Add pure helper tests in `PreviewMeteringActionPlannerTest`.

Update `CameraSessionCoordinatorTest` fake adapter to record `DeviceCommand.ApplyPreviewMetering` and emit `DeviceEvent.PreviewMeteringCompleted`.

Because real CameraX focus metering is not easy to unit-test without instrumentation, keep CameraX-specific verification to compile/assemble plus real-device smoke in the integration plan.

## Focused Verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewMeteringActionPlannerTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Non-Goals

- Do not build a custom brightness/EV algorithm in this task.
- Do not change pro/manual exposure persistence.
- Do not rebind CameraX use cases to focus.
- Do not draw the reticle in this task.

