package com.opencamera.app.gesture

sealed interface GestureEvent {
    data class Tap(val x: Float, val y: Float) : GestureEvent
    data class DoubleTap(val x: Float, val y: Float) : GestureEvent
    data class LongPress(val x: Float, val y: Float) : GestureEvent
    data class VerticalScroll(val deltaY: Float, val x: Float, val y: Float) : GestureEvent
    data class HorizontalScroll(val deltaX: Float, val x: Float, val y: Float) : GestureEvent
    data class PinchZoom(val scaleFactor: Float, val focusX: Float, val focusY: Float) : GestureEvent
    data object ScaleEnd : GestureEvent
    data class DragStart(val x: Float, val y: Float) : GestureEvent
    data class DragMove(val x: Float, val y: Float) : GestureEvent
    data object DragEnd : GestureEvent
    data object DragCancel : GestureEvent
}

enum class GestureZone {
    PREVIEW,
    MODE_TRACK,
    SHUTTER,
    SECONDARY_PANEL
}
