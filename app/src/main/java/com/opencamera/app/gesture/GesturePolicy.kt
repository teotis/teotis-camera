package com.opencamera.app.gesture

import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.SessionIntent

sealed interface GestureAction {
    data class DispatchSession(val intent: SessionIntent) : GestureAction
    data class FocusAt(val x: Float, val y: Float) : GestureAction
    data class ShowExposureHint(val deltaY: Float) : GestureAction
    data class AssistModeSwitch(val deltaX: Float) : GestureAction
    data object Ignore : GestureAction
}

class GesturePolicy(
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private var localZoomRatio = 1.0f
    private var lastPinchTimestamp = 0L
    private var isDraggingZoom = false
    private var lastDragTimestamp = 0L

    fun resetZoomAccumulation() {
        localZoomRatio = 1.0f
        lastPinchTimestamp = 0L
    }

    fun syncZoomRatio(ratio: Float) {
        localZoomRatio = ratio
    }

    fun cancelDrag() {
        isDraggingZoom = false
        lastDragTimestamp = 0L
    }

    fun map(event: GestureEvent, @Suppress("UNUSED_PARAMETER") activeMode: ModeId, currentZoomRatio: Float = 1.0f): GestureAction {
        return when (event) {
            is GestureEvent.Tap -> GestureAction.FocusAt(event.x, event.y)
            is GestureEvent.DoubleTap -> GestureAction.DispatchSession(SessionIntent.LensFacingToggled)
            is GestureEvent.PinchZoom -> {
                localZoomRatio = (localZoomRatio * event.scaleFactor).coerceIn(0.5f, 10.0f)
                val now = nowMillis()
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
            is GestureEvent.DragStart -> {
                isDraggingZoom = true
                lastDragTimestamp = 0L
                GestureAction.Ignore
            }
            is GestureEvent.DragMove -> {
                if (!isDraggingZoom) return GestureAction.Ignore
                val now = System.currentTimeMillis()
                if (now - lastDragTimestamp > 50) {
                    lastDragTimestamp = now
                    GestureAction.DispatchSession(SessionIntent.ApplyZoomRatio(currentZoomRatio))
                } else {
                    GestureAction.Ignore
                }
            }
            is GestureEvent.DragEnd -> {
                isDraggingZoom = false
                lastDragTimestamp = 0L
                GestureAction.Ignore
            }
            is GestureEvent.VerticalScroll -> GestureAction.ShowExposureHint(event.deltaY)
            is GestureEvent.HorizontalScroll -> GestureAction.AssistModeSwitch(event.deltaX)
            is GestureEvent.LongPress -> GestureAction.Ignore
            is GestureEvent.DragCancel -> {
                cancelDrag()
                GestureAction.Ignore
            }
        }
    }

    fun mapDragToRatio(
        x: Float,
        mapper: ZoomScaleMapper,
        @Suppress("UNUSED_PARAMETER") capability: ZoomRatioCapability
    ): GestureAction {
        if (!isDraggingZoom) return GestureAction.Ignore
        val result = mapper.mapPositionToRatio(x)
        val now = System.currentTimeMillis()
        if (now - lastDragTimestamp > 50) {
            lastDragTimestamp = now
            return GestureAction.DispatchSession(SessionIntent.ApplyZoomRatio(result.targetRatio))
        }
        return GestureAction.Ignore
    }
}
