package com.opencamera.app

import android.animation.ObjectAnimator
import android.view.View

internal class OrientationContentRotator(
    private val animationDurationMs: Long = 160
) {
    private val registeredViews = mutableListOf<View>()
    private var currentDegrees: Float = 0f

    fun register(vararg views: View) {
        registeredViews.addAll(views)
    }

    fun unregister(vararg views: View) {
        registeredViews.removeAll(views.toSet())
    }

    fun applyRotation(degrees: Float) {
        if (degrees == currentDegrees) return
        currentDegrees = degrees
        registeredViews.forEach { animateRotation(it, degrees) }
    }

    fun applyRotationToView(view: View, degrees: Float) {
        animateRotation(view, degrees)
    }

    private fun animateRotation(view: View, targetDegrees: Float) {
        val current = view.rotation
        val diff = targetDegrees - current
        if (diff == 0f) return
        ObjectAnimator.ofFloat(view, View.ROTATION, current, targetDegrees).apply {
            duration = animationDurationMs
            start()
        }
    }
}
