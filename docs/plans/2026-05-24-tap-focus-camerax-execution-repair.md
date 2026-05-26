# Tap Focus CameraX Execution Repair Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Use `rtk` for every shell command. This plan repairs an incomplete prior tap-focus landing; do not redesign the session or UI layers.

**Goal:** Replace the current `CameraXCaptureAdapter` tap-focus stub with real CameraX AF + AE metering execution.

**Architecture:** The existing UI, session, and coordinator chain is already present: `GestureAction.FocusAt -> SessionIntent.PreviewTapToFocus -> SessionEffect.ApplyPreviewMetering -> DeviceCommand.ApplyPreviewMetering`. This plan only changes the CameraX adapter execution endpoint so the device layer owns `FocusMeteringAction` and reports `DeviceEvent.PreviewMeteringCompleted`.

**Tech Stack:** Kotlin, CameraX `1.3.4`, `FocusMeteringAction`, `PreviewView.meteringPointFactory`, `CameraControl.startFocusAndMetering`, `kotlinx-coroutines-guava.await`.

---

## Current Evidence

- Current broken branch: `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`.
- The `DeviceCommand.ApplyPreviewMetering` branch always emits `PreviewMeteringResultStatus.UNSUPPORTED` with reason `CameraX FocusMeteringAction not yet implemented`.
- `app/src/main/java/com/opencamera/app/camera/PreviewMeteringActionPlanner.kt` and `PreviewMeteringActionPlannerTest.kt` already exist and cover normalized-to-pixel coordinate planning.
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt` already forwards the session effect to `DeviceCommand.ApplyPreviewMetering` and forwards `DeviceEvent.PreviewMeteringCompleted` back into session.

## Difficulty

Low to medium.

The code path is localized to `CameraXCaptureAdapter.kt`. The only reason this is not "low" is that the real CameraX execution path needs compile/assemble and real-device smoke validation; JVM unit tests cannot fully exercise `Camera.cameraControl.startFocusAndMetering`.

## Non-Goals

- Do not change `SessionIntent`, `SessionEffect`, `DeviceCommand`, or `DeviceEvent` names.
- Do not call CameraX from UI, `MainActivity`, or `CameraSessionCoordinator`.
- Do not rebind preview to focus.
- Do not stop recording to focus.
- Do not persist tap focus or automatic exposure metering as a setting.
- Do not classify ordinary tap-focus failure as `DeviceRuntimeIssueKind.BIND_FAILURE`.

## Task 1: Add CameraX Metering Imports

**Files:**

- Modify: `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`

- [ ] **Step 1: Add imports near the existing CameraX/core imports**

```kotlin
import androidx.camera.core.FocusMeteringAction
import com.opencamera.core.device.PreviewMeteringMode
import com.opencamera.core.device.PreviewMeteringRequest
import com.opencamera.core.device.PreviewMeteringResult
import com.opencamera.core.device.PreviewMeteringResultStatus
import java.util.concurrent.TimeUnit
```

- [ ] **Step 2: Run compile to catch import drift**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:compileDebugKotlin
```

Expected if the current unrelated `SavedMediaType` import blocker is still present: fail in `SessionCockpitRenderModel.kt`. If that blocker is already fixed, this task should either pass or expose only metering-related compile issues.

## Task 2: Replace The Stub Dispatch Branch

**Files:**

- Modify: `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`

- [ ] **Step 1: Replace only the `DeviceCommand.ApplyPreviewMetering` branch**

Replace the current branch:

```kotlin
is DeviceCommand.ApplyPreviewMetering -> {
    _events.emit(
        DeviceEvent.PreviewMeteringCompleted(
            com.opencamera.core.device.PreviewMeteringResult(
                requestId = command.request.requestId,
                point = command.request.point,
                status = com.opencamera.core.device.PreviewMeteringResultStatus.UNSUPPORTED,
                reason = "CameraX FocusMeteringAction not yet implemented"
            )
        )
    )
}
```

with:

```kotlin
is DeviceCommand.ApplyPreviewMetering -> runCatching {
    applyPreviewMetering(command.request)
}.onFailure { throwable ->
    _events.emit(
        DeviceEvent.PreviewMeteringCompleted(
            previewMeteringResult(
                request = command.request,
                status = PreviewMeteringResultStatus.FAILED,
                reason = throwable.message ?: "Preview metering failed"
            )
        )
    )
}
```

- [ ] **Step 2: Verify no stub string remains in the adapter**

Run:

```bash
rtk rg -n "FocusMeteringAction not yet implemented|PreviewMeteringResultStatus\\.UNSUPPORTED" app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt
```

Expected: the old `FocusMeteringAction not yet implemented` string is gone. A remaining `PreviewMeteringResultStatus.UNSUPPORTED` is acceptable only inside real unsupported capability handling.

## Task 3: Add Result Helper

**Files:**

- Modify: `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`

- [ ] **Step 1: Add this helper near `updateZoomRatio` or the new metering function**

```kotlin
private fun previewMeteringResult(
    request: PreviewMeteringRequest,
    status: PreviewMeteringResultStatus,
    reason: String? = null
): PreviewMeteringResult {
    return PreviewMeteringResult(
        requestId = request.requestId,
        point = request.point.clamped(),
        status = status,
        reason = reason
    )
}
```

- [ ] **Step 2: Compile the app module**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:compileDebugKotlin
```

Expected: no new errors from `previewMeteringResult`. If the pre-existing `SavedMediaType` import error remains, handle it through the companion verification/preflight plan before judging this repair.

## Task 4: Implement `applyPreviewMetering`

**Files:**

- Modify: `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`

- [ ] **Step 1: Add constants near existing top-level constants**

```kotlin
private const val PREVIEW_METERING_POINT_SIZE = 0.15f
private const val MIN_PREVIEW_METERING_AUTO_CANCEL_MILLIS = 1L
```

- [ ] **Step 2: Add the CameraX execution method near `updateZoomRatio`**

```kotlin
private suspend fun applyPreviewMetering(request: PreviewMeteringRequest) {
    val result = withContext(Dispatchers.Main.immediate) {
        val camera = boundCamera
        val previewView = boundPreviewView
        if (camera == null || previewView == null) {
            return@withContext previewMeteringResult(
                request = request,
                status = PreviewMeteringResultStatus.FAILED,
                reason = "Preview is not bound"
            )
        }

        val point = request.point.clamped()
        val pixel = previewMeteringPixelPoint(
            normalizedX = point.normalizedX,
            normalizedY = point.normalizedY,
            viewWidth = previewView.width,
            viewHeight = previewView.height
        )
        val meteringPoint = previewView.meteringPointFactory.createPoint(
            pixel.x,
            pixel.y,
            PREVIEW_METERING_POINT_SIZE
        )
        val autoCancelMillis = request.autoCancelMillis
            .coerceAtLeast(MIN_PREVIEW_METERING_AUTO_CANCEL_MILLIS)

        val focusAndAeAction = FocusMeteringAction.Builder(
            meteringPoint,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        )
            .setAutoCancelDuration(autoCancelMillis, TimeUnit.MILLISECONDS)
            .build()

        val aeOnlyAction = FocusMeteringAction.Builder(
            meteringPoint,
            FocusMeteringAction.FLAG_AE
        )
            .setAutoCancelDuration(autoCancelMillis, TimeUnit.MILLISECONDS)
            .build()

        val selected = when (request.mode) {
            PreviewMeteringMode.FOCUS_AND_AUTO_EXPOSURE -> when {
                camera.cameraInfo.isFocusMeteringSupported(focusAndAeAction) ->
                    focusAndAeAction to PreviewMeteringResultStatus.SUCCEEDED

                camera.cameraInfo.isFocusMeteringSupported(aeOnlyAction) ->
                    aeOnlyAction to PreviewMeteringResultStatus.DEGRADED_AUTO_EXPOSURE_ONLY

                else -> null
            }

            PreviewMeteringMode.AUTO_EXPOSURE_ONLY -> when {
                camera.cameraInfo.isFocusMeteringSupported(aeOnlyAction) ->
                    aeOnlyAction to PreviewMeteringResultStatus.DEGRADED_AUTO_EXPOSURE_ONLY

                else -> null
            }
        }

        if (selected == null) {
            return@withContext PreviewMeteringResult(
                requestId = request.requestId,
                point = point,
                status = PreviewMeteringResultStatus.UNSUPPORTED,
                reason = "Focus and exposure metering are unsupported"
            )
        }

        val cameraXResult = camera.cameraControl.startFocusAndMetering(selected.first).await()
        val status = selected.second
        val reason = if (
            status == PreviewMeteringResultStatus.SUCCEEDED &&
            !cameraXResult.isFocusSuccessful
        ) {
            "Focus did not lock"
        } else {
            null
        }
        PreviewMeteringResult(
            requestId = request.requestId,
            point = point,
            status = status,
            reason = reason
        )
    }

    _events.emit(DeviceEvent.PreviewMeteringCompleted(result))
}
```

- [ ] **Step 3: Compile**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:compileDebugKotlin
```

Expected: compile succeeds once unrelated blockers are cleared. If CameraX API signatures differ, keep the same behavior and adapt only the failing call signature.

## Task 5: Preserve Runtime-Issue Boundaries

**Files:**

- Modify: `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`

- [ ] **Step 1: Check failure handling**

Confirm the `ApplyPreviewMetering` branch emits `DeviceEvent.PreviewMeteringCompleted` with `FAILED` on exceptions and does not call:

```kotlin
classifyPreviewBindingFailure(throwable)
invalidateCachedProviderState(issue)
DeviceEvent.RuntimeIssue(issue)
```

- [ ] **Step 2: Run source check**

Run:

```bash
rtk rg -n "ApplyPreviewMetering|classifyPreviewBindingFailure|RuntimeIssue|PreviewMeteringCompleted" app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt
```

Expected: `classifyPreviewBindingFailure` remains used for bind/zoom/runtime paths, not as the ordinary tap-focus failure path.

## Task 6: Focused Verification

**Files:**

- Test existing: `app/src/test/java/com/opencamera/app/camera/PreviewMeteringActionPlannerTest.kt`
- Test existing: `app/src/test/java/com/opencamera/app/camera/CameraSessionCoordinatorTest.kt`

- [ ] **Step 1: Run app camera focused tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewMeteringActionPlannerTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
```

Expected: pass.

- [ ] **Step 2: Run assemble**

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
```

Expected: pass.

- [ ] **Step 3: Run Stage 7 gate if the focused checks pass**

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

Expected: pass, or only fail for a clearly unrelated pre-existing blocker that is documented with exact file and compiler/test output.

## Task 7: Real-Device Smoke Checklist

This cannot be fully validated by JVM tests. After a passing debug APK is available, run a short manual smoke on a device:

- [ ] Launch app and wait for preview to become active.
- [ ] Tap near preview center. Expected: reticle appears and device attempts focus/AE.
- [ ] Tap a bright region and a dark region. Expected: exposure metering reacts where the CameraX device supports AE regions.
- [ ] Start video recording, tap preview, and confirm recording does not stop.
- [ ] If the device reports unsupported, confirm the app shows unsupported feedback without crashing.

## Acceptance

- `CameraXCaptureAdapter` no longer contains the old `CameraX FocusMeteringAction not yet implemented` stub.
- Active preview taps reach `CameraControl.startFocusAndMetering`.
- AF + AE support returns `PreviewMeteringResultStatus.SUCCEEDED`.
- AE-only fallback returns `PreviewMeteringResultStatus.DEGRADED_AUTO_EXPOSURE_ONLY`.
- No support returns `PreviewMeteringResultStatus.UNSUPPORTED`.
- Runtime exceptions return `PreviewMeteringResultStatus.FAILED`.
- Stage 7 verification remains green after unrelated compile blockers are cleared.
