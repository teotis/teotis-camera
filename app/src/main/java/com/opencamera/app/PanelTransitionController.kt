package com.opencamera.app

import android.view.View
import android.view.ViewPropertyAnimator

/**
 * Lightweight, interruptible transition helper for panel show/hide and scrim fade.
 *
 * Always sets final visibility/alpha/translation deterministically so that
 * cancelled or disabled animations never leave views in an inconsistent state.
 */
internal class PanelTransitionController(
    private val panelViews: () -> List<View>,
    private val scrimView: () -> View
) {
    private var activeAnimator: ViewPropertyAnimator? = null

    /**
     * Run a transition from the current visual state to the target state.
     * Cancels any in-flight animation first, then applies final state
     * and optionally animates from an opening/closing offset.
     *
     * @param opening true if the panel is becoming visible, false if hiding
     * @param durationMs animation duration in ms; 0 means apply instantly
     */
    fun transition(opening: Boolean, durationMs: Long = PANEL_TRANSITION_DURATION_MS) {
        cancel()

        if (durationMs <= 0L) {
            applyFinalState(opening)
            return
        }

        if (opening) {
            animateOpen(durationMs)
        } else {
            animateClose(durationMs)
        }
    }

    /**
     * Cancel any running animation and snap to the final state of the
     * most recently requested direction. If no transition was ever run,
     * this is a no-op.
     */
    fun cancel() {
        activeAnimator?.let {
            it.cancel()
            activeAnimator = null
        }
    }

    /**
     * Immediately apply the final visual state for the given direction
     * without any animation. This is the deterministic fallback used when
     * animations are disabled or cancelled.
     */
    fun applyFinalState(opening: Boolean) {
        for (panel in panelViews()) {
            if (opening) {
                panel.alpha = 1f
                panel.translationY = 0f
                panel.visibility = View.VISIBLE
            } else {
                panel.alpha = 0f
                panel.translationY = PANEL_SLIDE_OFFSET_PX
                panel.visibility = View.GONE
            }
        }
        val scrim = scrimView()
        if (opening) {
            scrim.alpha = 1f
            scrim.visibility = View.VISIBLE
        } else {
            scrim.alpha = 0f
            scrim.visibility = View.GONE
        }
    }

    private fun animateOpen(durationMs: Long) {
        for (panel in panelViews()) {
            panel.visibility = View.VISIBLE
            panel.alpha = 0f
            panel.translationY = PANEL_SLIDE_OFFSET_PX
        }
        val scrim = scrimView()
        scrim.visibility = View.VISIBLE
        scrim.alpha = 0f

        // Animate scrim fade-in
        scrim.animate()
            .alpha(1f)
            .setDuration(durationMs)
            .start()

        // Animate first panel (only one should be visible at a time)
        val primaryPanel = panelViews().firstOrNull { it.visibility == View.VISIBLE }
        primaryPanel?.let { panel ->
            activeAnimator = panel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(durationMs)
                .withEndAction {
                    activeAnimator = null
                }
        }
    }

    private fun animateClose(durationMs: Long) {
        val scrim = scrimView()
        scrim.animate()
            .alpha(0f)
            .setDuration(durationMs)
            .withEndAction { scrim.visibility = View.GONE }
            .start()

        val primaryPanel = panelViews().firstOrNull { it.visibility == View.VISIBLE }
        primaryPanel?.let { panel ->
            activeAnimator = panel.animate()
                .alpha(0f)
                .translationY(PANEL_SLIDE_OFFSET_PX)
                .setDuration(durationMs)
                .withEndAction {
                    panel.visibility = View.GONE
                    panel.translationY = 0f
                    activeAnimator = null
                }
        }
    }

    companion object {
        /** Short duration that keeps transitions snappy. */
        const val PANEL_TRANSITION_DURATION_MS = 150L
        /** Vertical slide offset in pixels for enter/exit. */
        const val PANEL_SLIDE_OFFSET_PX = 48f
    }
}
