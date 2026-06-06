package com.opencamera.app

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.HorizontalScrollView

/**
 * Attaches to a [HorizontalScrollView] to track whether the user is actively
 * scrolling. Mode button clicks are suppressed while scrolling is active.
 *
 * Uses velocity-aware settle detection: high-velocity flings wait for the
 * scroll to actually settle before clearing [isScrolling], while low-velocity
 * drags clear immediately.
 */
internal class ModeTrackScrollGuard(
    private val scrollSlopPx: Float
) {
    var isScrolling: Boolean = false
        private set

    private var downX = 0f
    private var didMove = false

    private var velocityTracker: VelocityTracker? = null
    private var lastScrollX = 0
    private var settledCount = 0
    private var scrollViewRef: HorizontalScrollView? = null
    private var pendingClear: Runnable? = null

    private val scrollSettleListener = View.OnScrollChangeListener { _, scrollX, _, _, _ ->
        if (scrollX == lastScrollX) {
            settledCount++
        } else {
            settledCount = 0
            lastScrollX = scrollX
        }
        if (settledCount >= 2) {
            cancelPendingClear()
            isScrolling = false
            settledCount = 0
        }
    }

    fun attach(scrollView: HorizontalScrollView) {
        cancelPendingClear()
        scrollViewRef?.let { prev ->
            prev.setOnScrollChangeListener(null as View.OnScrollChangeListener?)
        }

        scrollViewRef = scrollView
        lastScrollX = scrollView.scrollX
        settledCount = 0
        scrollView.setOnScrollChangeListener(scrollSettleListener)

        scrollView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                    downX = event.x
                    didMove = false
                    isScrolling = false
                    false // let the scroll view handle it
                }
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    if (kotlin.math.abs(event.x - downX) > scrollSlopPx) {
                        didMove = true
                        isScrolling = true
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (didMove) {
                        velocityTracker?.computeCurrentVelocity(1000)
                        val velocityX = velocityTracker?.xVelocity ?: 0f
                        val absVelocity = kotlin.math.abs(velocityX)

                        if (absVelocity < 500f) {
                            // Low velocity: scroll has effectively stopped
                            isScrolling = false
                        } else {
                            // High velocity fling: wait for settle callback
                            settledCount = 0
                            lastScrollX = scrollView.scrollX
                            val clearRunnable = Runnable {
                                isScrolling = false
                                pendingClear = null
                            }
                            pendingClear = clearRunnable
                            scrollView.postDelayed(clearRunnable, 500L)
                        }
                    } else {
                        isScrolling = false
                    }

                    velocityTracker?.recycle()
                    velocityTracker = null
                    false
                }
                else -> false
            }
        }
    }

    private fun cancelPendingClear() {
        pendingClear?.let { runnable ->
            scrollViewRef?.removeCallbacks(runnable)
        }
        pendingClear = null
    }
}
