package com.opencamera.app.gesture

import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.SessionIntent

sealed interface GestureAction {
    data class DispatchSession(val intent: SessionIntent) : GestureAction
    data class FocusAt(val x: Float, val y: Float) : GestureAction
    data class ShowExposureHint(val deltaY: Float) : GestureAction
    data class AssistModeSwitch(val deltaX: Float) : GestureAction
    data object Ignore : GestureAction
}

class GesturePolicy {
    private var localZoomRatio = 1.0f
    private var lastPinchTimestamp = 0L

    fun resetZoomAccumulation() {
        localZoomRatio = 1.0f
        lastPinchTimestamp = 0L
    }

    fun syncZoomRatio(ratio: Float) {
        localZoomRatio = ratio
    }

    fun map(event: GestureEvent, @Suppress("UNUSED_PARAMETER") activeMode: ModeId, currentZoomRatio: Float = 1.0f): GestureAction {
        return when (event) {
            is GestureEvent.Tap -> GestureAction.FocusAt(event.x, event.y)
            is GestureEvent.DoubleTap -> GestureAction.DispatchSession(SessionIntent.LensFacingToggled)
            is GestureEvent.PinchZoom -> {
                localZoomRatio = (localZoomRatio * event.scaleFactor).coerceIn(0.5f, 10.0f)
                val now = System.currentTimeMillis()
                if (now - lastPinchTimestamp > 16) {
                    lastPinchTimestamp = now
                    GestureAction.DispatchSession(SessionIntent.ApplyZoomRatio(localZoomRatio))
                } else {
                    GestureAction.Ignore
                }
            }
            is GestureEvent.ScaleEnd -> {
                resetZoomAccumulation()
                GestureAction.Ignore
            }
            is GestureEvent.VerticalScroll -> GestureAction.ShowExposureHint(event.deltaY)
            is GestureEvent.HorizontalScroll -> GestureAction.AssistModeSwitch(event.deltaX)
            is GestureEvent.LongPress -> GestureAction.Ignore
            is GestureEvent.DragCancel -> GestureAction.Ignore
        }
    }
}
