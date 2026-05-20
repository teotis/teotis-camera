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

    fun map(event: GestureEvent, @Suppress("UNUSED_PARAMETER") activeMode: ModeId): GestureAction {
        return when (event) {
            is GestureEvent.Tap -> GestureAction.FocusAt(event.x, event.y)
            is GestureEvent.DoubleTap -> GestureAction.DispatchSession(SessionIntent.LensFacingToggled)
            is GestureEvent.PinchZoom -> GestureAction.DispatchSession(SessionIntent.ZoomRatioToggled)
            is GestureEvent.VerticalScroll -> GestureAction.ShowExposureHint(event.deltaY)
            is GestureEvent.HorizontalScroll -> GestureAction.AssistModeSwitch(event.deltaX)
            is GestureEvent.LongPress -> GestureAction.Ignore
            is GestureEvent.DragCancel -> GestureAction.Ignore
        }
    }
}
