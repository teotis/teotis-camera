package com.opencamera.app.gesture

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class GestureRouter(
    context: Context,
    private val onGesture: (GestureEvent) -> Unit
) {

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onGesture(GestureEvent.Tap(e.x, e.y))
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onGesture(GestureEvent.DoubleTap(e.x, e.y))
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            onGesture(GestureEvent.LongPress(e.x, e.y))
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                onGesture(GestureEvent.HorizontalScroll(dx, e2.x, e2.y))
            } else {
                onGesture(GestureEvent.VerticalScroll(dy, e2.x, e2.y))
            }
            return true
        }

        override fun onDown(e: MotionEvent): Boolean = true
    })

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            onGesture(GestureEvent.PinchZoom(detector.scaleFactor, detector.focusX, detector.focusY))
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            onGesture(GestureEvent.ScaleEnd)
        }
    })

    private var isDragging = false
    private var isEnabled = true

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun onTouchEvent(@Suppress("UNUSED_PARAMETER") view: View, event: MotionEvent): Boolean {
        if (!isEnabled) return false

        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                isDragging = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    onGesture(GestureEvent.DragCancel)
                }
                isDragging = false
            }
        }
        return true
    }
}
