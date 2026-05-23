# Tap Focus UI Routing And Reticle Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this task. Use `rtk` for every command. This package depends on the session contract from `2026-05-23-tap-focus-session-device-contract.md`.

**Goal:** Connect preview taps to the session tap-focus intent and show a small focus/AE reticle from session state.

**Architecture:** UI remains a renderer and intent dispatcher. `MainActivity` converts the gesture point to normalized preview coordinates and dispatches `SessionIntent.PreviewTapToFocus`. `PreviewOverlayView` draws session presentation feedback; it does not call CameraX.

**Tech Stack:** Android View touch routing, existing `GestureRouter` / `GesturePolicy`, `PreviewOverlayView`, pure geometry tests.

---

## Current Code Facts

- `GesturePolicy.map(GestureEvent.Tap(...))` already returns `GestureAction.FocusAt(x, y)`.
- `MainActivity.bindGestureRouter()` receives `GestureAction.FocusAt` and currently leaves a placeholder comment.
- `GestureGuard` already blocks preview gestures when settings, style lab, quick bubble, or dev console should own touch.
- `PreviewOverlayView` already draws preview overlays and has geometry helpers that align frame/grid with preview content.

## Required Behavior

- Single tap on active preview dispatches a normalized focus/AE metering intent.
- Tap coordinates are normalized against the `PreviewView` size.
- Values are clamped to `0.0..1.0`.
- If the tap falls outside the active capture frame while a frame ratio is active, ignore it. This prevents focusing on dimmed crop-mask areas.
- Reticle appears immediately when session enters `REQUESTED`.
- Reticle updates for `SUCCEEDED`, `DEGRADED_AUTO_EXPOSURE_ONLY`, `FAILED`, or `UNSUPPORTED`.
- Reticle is visual only. No visible instructional text is added to the shooting surface.
- Reticle disappears when no `previewMeteringFeedback` is present or when the next render clears it.

## Files

Modify:

- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt`
- `app/src/test/java/com/opencamera/app/gesture/GesturePolicyTest.kt`

Create:

- `app/src/main/java/com/opencamera/app/PreviewTapFocusGeometry.kt`
- `app/src/test/java/com/opencamera/app/PreviewTapFocusGeometryTest.kt`

## Implementation Tasks

### Task 1: Add pure tap geometry helper

Create a helper that normalizes and optionally filters taps by active frame rect.

Expected shape:

```kotlin
package com.opencamera.app

import android.graphics.RectF

internal data class NormalizedPreviewTap(
    val x: Float,
    val y: Float
)

internal fun normalizedPreviewTapOrNull(
    tapX: Float,
    tapY: Float,
    viewWidth: Int,
    viewHeight: Int,
    activeFrameRect: RectF? = null
): NormalizedPreviewTap? {
    if (viewWidth <= 0 || viewHeight <= 0) return null
    if (activeFrameRect != null && !activeFrameRect.contains(tapX, tapY)) return null
    return NormalizedPreviewTap(
        x = (tapX / viewWidth.toFloat()).coerceIn(0f, 1f),
        y = (tapY / viewHeight.toFloat()).coerceIn(0f, 1f)
    )
}
```

Tests:

- Center tap in `1000 x 800` becomes `0.5, 0.5`.
- Negative tap clamps to `0.0`.
- Over-edge tap clamps to `1.0`.
- Zero size returns null.
- Tap outside active frame returns null.
- Tap inside active frame returns normalized full-view coordinates.

### Task 2: Dispatch session intent from MainActivity

Replace the `GestureAction.FocusAt` placeholder with:

```kotlin
is GestureAction.FocusAt -> {
    val tap = normalizedPreviewTapOrNull(
        tapX = action.x,
        tapY = action.y,
        viewWidth = previewView.width,
        viewHeight = previewView.height,
        activeFrameRect = previewOverlayView.currentActiveFrameRectOrNull()
    ) ?: return@GestureRouter
    dispatch(
        SessionIntent.PreviewTapToFocus(
            normalizedX = tap.x,
            normalizedY = tap.y
        )
    )
}
```

If `PreviewOverlayView` does not expose the active rect, add a small read-only method:

```kotlin
internal fun currentActiveFrameRectOrNull(): RectF?
```

The method should return the same rect used for frame, grid, and dim-scrim drawing. It must not recompute from a separate geometry path.

### Task 3: Add reticle render state to PreviewOverlayView

Add a small UI model:

```kotlin
internal enum class FocusReticleStatus {
    REQUESTED,
    SUCCEEDED,
    DEGRADED,
    FAILED,
    UNSUPPORTED
}

internal data class FocusReticleRenderModel(
    val normalizedX: Float,
    val normalizedY: Float,
    val status: FocusReticleStatus
)
```

Add setter:

```kotlin
fun updateFocusReticle(model: FocusReticleRenderModel?) {
    focusReticle = model
    invalidate()
}
```

Draw a small ring at:

```kotlin
val cx = model.normalizedX.coerceIn(0f, 1f) * width
val cy = model.normalizedY.coerceIn(0f, 1f) * height
```

Recommended non-text visual states:

- Requested: amber ring
- Succeeded: white ring
- Degraded AE-only: amber ring with short tick marks
- Failed/unsupported: dim gray ring

Do not add on-screen text or explanations.

### Task 4: Render reticle from session state

In `MainActivity.render(state)`:

```kotlin
previewOverlayView.updateFocusReticle(
    state.presentation.previewMeteringFeedback?.toFocusReticleRenderModel()
)
```

Add a small mapper in `MainActivity.kt` or a helper file:

```kotlin
internal fun PreviewMeteringFeedback.toFocusReticleRenderModel(): FocusReticleRenderModel {
    return FocusReticleRenderModel(
        normalizedX = normalizedX,
        normalizedY = normalizedY,
        status = when (status) {
            PreviewMeteringFeedbackStatus.REQUESTED -> FocusReticleStatus.REQUESTED
            PreviewMeteringFeedbackStatus.SUCCEEDED -> FocusReticleStatus.SUCCEEDED
            PreviewMeteringFeedbackStatus.DEGRADED_AUTO_EXPOSURE_ONLY -> FocusReticleStatus.DEGRADED
            PreviewMeteringFeedbackStatus.FAILED -> FocusReticleStatus.FAILED
            PreviewMeteringFeedbackStatus.UNSUPPORTED -> FocusReticleStatus.UNSUPPORTED
        }
    )
}
```

### Task 5: Keep gesture policy tests unchanged

`GesturePolicyTest.tap_mapsToFocusAt` should continue to pass. Add tests only for the new geometry helper and reticle mapper.

## Focused Verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewTapFocusGeometryTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Non-Goals

- Do not call CameraX or adapter APIs from `MainActivity`.
- Do not add a focus mode settings page.
- Do not add visible explanatory text to the shooting surface.
- Do not make tap focus persist across app restarts.

