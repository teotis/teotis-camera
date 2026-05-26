package com.opencamera.app

import android.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FocusReticleVisualStateTest {

    // --- REQUESTED state ---

    @Test
    fun `requested at t=0 has expanded scale`() {
        val v = focusReticleVisualState(FocusReticleStatus.REQUESTED, 0L)
        assertEquals(1.3f, v.scale, 0.01f)
        assertEquals(1f, v.alpha, 0.01f)
        assertFalse(v.ticksVisible)
        assertFalse(v.expired)
    }

    @Test
    fun `requested at t=100 settles to scale 1`() {
        val v = focusReticleVisualState(FocusReticleStatus.REQUESTED, 100L)
        assertEquals(1.0f, v.scale, 0.01f)
    }

    @Test
    fun `requested at t=400 still fully visible`() {
        val v = focusReticleVisualState(FocusReticleStatus.REQUESTED, 400L)
        assertEquals(1.0f, v.alpha, 0.01f)
        assertFalse(v.expired)
    }

    @Test
    fun `requested at t=500 fading`() {
        val v = focusReticleVisualState(FocusReticleStatus.REQUESTED, 500L)
        assertTrue(v.alpha < 1f)
        assertTrue(v.alpha > 0f)
        assertFalse(v.expired)
    }

    @Test
    fun `requested at t=601 expired`() {
        val v = focusReticleVisualState(FocusReticleStatus.REQUESTED, 601L)
        assertTrue(v.expired)
    }

    @Test
    fun `requested uses amber color`() {
        val v = focusReticleVisualState(FocusReticleStatus.REQUESTED, 50L)
        assertEquals(Color.rgb(255, 191, 0), v.ringColor)
    }

    // --- SUCCEEDED state ---

    @Test
    fun `succeeded at t=0 fully visible white`() {
        val v = focusReticleVisualState(FocusReticleStatus.SUCCEEDED, 0L)
        assertEquals(1.0f, v.scale, 0.01f)
        assertEquals(1.0f, v.alpha, 0.01f)
        assertEquals(Color.WHITE, v.ringColor)
        assertFalse(v.ticksVisible)
        assertFalse(v.expired)
    }

    @Test
    fun `succeeded at t=250 still fully visible`() {
        val v = focusReticleVisualState(FocusReticleStatus.SUCCEEDED, 250L)
        assertEquals(1.0f, v.alpha, 0.01f)
        assertFalse(v.expired)
    }

    @Test
    fun `succeeded at t=400 fading`() {
        val v = focusReticleVisualState(FocusReticleStatus.SUCCEEDED, 400L)
        assertTrue(v.alpha < 1f)
        assertTrue(v.alpha > 0f)
    }

    @Test
    fun `succeeded at t=501 expired`() {
        val v = focusReticleVisualState(FocusReticleStatus.SUCCEEDED, 501L)
        assertTrue(v.expired)
    }

    // --- DEGRADED state ---

    @Test
    fun `degraded has ticks visible`() {
        val v = focusReticleVisualState(FocusReticleStatus.DEGRADED, 50L)
        assertTrue(v.ticksVisible)
        assertEquals(Color.rgb(255, 191, 0), v.ringColor)
    }

    @Test
    fun `degraded at t=350 still fully visible`() {
        val v = focusReticleVisualState(FocusReticleStatus.DEGRADED, 350L)
        assertEquals(1.0f, v.alpha, 0.01f)
        assertFalse(v.expired)
    }

    @Test
    fun `degraded at t=601 expired`() {
        val v = focusReticleVisualState(FocusReticleStatus.DEGRADED, 601L)
        assertTrue(v.expired)
    }

    // --- FAILED/UNSUPPORTED state ---

    @Test
    fun `failed at t=0 has muted alpha`() {
        val v = focusReticleVisualState(FocusReticleStatus.FAILED, 0L)
        assertEquals(0.5f, v.alpha, 0.01f)
        assertFalse(v.ticksVisible)
    }

    @Test
    fun `failed contracts over time`() {
        val v0 = focusReticleVisualState(FocusReticleStatus.FAILED, 0L)
        val v200 = focusReticleVisualState(FocusReticleStatus.FAILED, 200L)
        assertTrue(v200.scale < v0.scale)
    }

    @Test
    fun `failed uses gray color`() {
        val v = focusReticleVisualState(FocusReticleStatus.FAILED, 50L)
        assertEquals(Color.rgb(128, 128, 128), v.ringColor)
    }

    @Test
    fun `failed at t=401 expired`() {
        val v = focusReticleVisualState(FocusReticleStatus.FAILED, 401L)
        assertTrue(v.expired)
    }

    @Test
    fun `unsupported behaves same as failed`() {
        val failed = focusReticleVisualState(FocusReticleStatus.FAILED, 100L)
        val unsupported = focusReticleVisualState(FocusReticleStatus.UNSUPPORTED, 100L)
        assertEquals(failed.scale, unsupported.scale, 0.001f)
        assertEquals(failed.alpha, unsupported.alpha, 0.001f)
        assertEquals(failed.ringColor, unsupported.ringColor)
        assertEquals(failed.ticksVisible, unsupported.ticksVisible)
        assertEquals(failed.expired, unsupported.expired)
    }

    // --- Scale clamping ---

    @Test
    fun `scale never goes below 0_5`() {
        // FAILED at very large elapsed time would try to shrink further
        val v = focusReticleVisualState(FocusReticleStatus.FAILED, 10000L)
        assertTrue(v.scale >= 0.5f)
    }

    // --- Rapid replacement: new status resets animation ---

    @Test
    fun `new requested after expired succeeded is not expired`() {
        // Simulates: succeeded expires, then new tap fires REQUESTED at t=0
        val expired = focusReticleVisualState(FocusReticleStatus.SUCCEEDED, 600L)
        assertTrue(expired.expired)

        val fresh = focusReticleVisualState(FocusReticleStatus.REQUESTED, 0L)
        assertFalse(fresh.expired)
        assertEquals(1.3f, fresh.scale, 0.01f)
    }
}
