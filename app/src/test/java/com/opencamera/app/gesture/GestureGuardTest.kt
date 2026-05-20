package com.opencamera.app.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureGuardTest {

    private val guard = GestureGuard()

    @Test
    fun previewGesture_allowedWhenNoPanelsOpen() {
        val state = GestureGuardState()
        assertTrue(guard.isGestureAllowed(GestureZone.PREVIEW, state))
    }

    @Test
    fun previewGesture_blockedWhenSettingsOpen() {
        val state = GestureGuardState(isSettingsPanelOpen = true)
        assertFalse(guard.isGestureAllowed(GestureZone.PREVIEW, state))
    }

    @Test
    fun previewGesture_blockedWhenFilterOpen() {
        val state = GestureGuardState(isFilterPanelOpen = true)
        assertFalse(guard.isGestureAllowed(GestureZone.PREVIEW, state))
    }

    @Test
    fun previewGesture_blockedWhenMoreControlsOpen() {
        val state = GestureGuardState(isMoreControlsOpen = true)
        assertFalse(guard.isGestureAllowed(GestureZone.PREVIEW, state))
    }

    @Test
    fun secondaryPanelGesture_allowedWhenFilterOpen() {
        val state = GestureGuardState(isFilterPanelOpen = true)
        assertTrue(guard.isGestureAllowed(GestureZone.SECONDARY_PANEL, state))
    }

    @Test
    fun horizontalScroll_allowedByDefault() {
        assertTrue(guard.isHorizontalScrollAllowed(GestureGuardState()))
    }

    @Test
    fun horizontalScroll_blockedWhenSettingsOpen() {
        assertFalse(guard.isHorizontalScrollAllowed(GestureGuardState(isSettingsPanelOpen = true)))
    }

    @Test
    fun horizontalScroll_blockedWhenFilterOpen() {
        assertFalse(guard.isHorizontalScrollAllowed(GestureGuardState(isFilterPanelOpen = true)))
    }

    @Test
    fun horizontalScroll_blockedWhenFilterAdjustmentActive() {
        assertFalse(guard.isHorizontalScrollAllowed(GestureGuardState(isFilterAdjustmentActive = true)))
    }
}
