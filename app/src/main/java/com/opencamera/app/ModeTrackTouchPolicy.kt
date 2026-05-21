package com.opencamera.app

import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView

internal data class ModeTrackTouchDecision(
    val shouldDispatchClick: Boolean,
    val shouldTreatAsScroll: Boolean
)

/**
 * Pure function: given pointer-down/up coordinates and a movement threshold,
 * decides whether the gesture is a tap (dispatch click) or a scroll (suppress).
 */
internal fun modeTrackTouchDecision(
    downX: Float,
    downY: Float,
    upX: Float,
    upY: Float,
    touchSlop: Float
): ModeTrackTouchDecision {
    val dx = kotlin.math.abs(upX - downX)
    val dy = kotlin.math.abs(upY - downY)
    val isScroll = dx > touchSlop || dy > touchSlop
    return ModeTrackTouchDecision(
        shouldDispatchClick = !isScroll,
        shouldTreatAsScroll = isScroll
    )
}

/**
 * Attaches to a [HorizontalScrollView] to track whether the user is actively
 * scrolling. Mode button clicks are suppressed while scrolling is active.
 *
 * This replaces the fragile per-button OnTouchListener approach: each button
 * uses a normal `setOnClickListener`, and the guard prevents accidental mode
 * switches during horizontal flings.
 */
internal class ModeTrackScrollGuard(
    private val scrollSlopPx: Float
) {
    var isScrolling: Boolean = false
        private set

    private var downX = 0f
    private var didMove = false

    fun attach(scrollView: HorizontalScrollView) {
        scrollView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    didMove = false
                    isScrolling = false
                    false // let the scroll view handle it
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.x - downX) > scrollSlopPx) {
                        didMove = true
                        isScrolling = true
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Delay clearing the flag so the click event (which fires
                    // after ACTION_UP) can still see isScrolling == true.
                    if (didMove) {
                        scrollView.postDelayed({
                            isScrolling = false
                        }, 150L)
                    } else {
                        isScrolling = false
                    }
                    false
                }
                else -> false
            }
        }
    }
}
