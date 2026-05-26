package com.opencamera.app.gesture

import com.opencamera.app.CockpitPanelRoute
import com.opencamera.app.SettingsSubpage
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
        val state = GestureGuardState(activePanel = CockpitPanelRoute.Settings())
        assertFalse(guard.isGestureAllowed(GestureZone.PREVIEW, state))
    }

    @Test
    fun previewGesture_blockedWhenStyleLabOpen() {
        val state = GestureGuardState(activePanel = CockpitPanelRoute.StyleLab)
        assertFalse(guard.isGestureAllowed(GestureZone.PREVIEW, state))
    }

    @Test
    fun previewGesture_blockedWhenDevConsoleOpen() {
        val state = GestureGuardState(activePanel = CockpitPanelRoute.DevConsole)
        assertFalse(guard.isGestureAllowed(GestureZone.PREVIEW, state))
    }

    @Test
    fun previewGesture_blockedWhenQuickBubbleOpen() {
        val state = GestureGuardState(activePanel = CockpitPanelRoute.QuickBubble)
        assertFalse(guard.isGestureAllowed(GestureZone.PREVIEW, state))
    }

    @Test
    fun secondaryPanelGesture_allowedWhenStyleLabOpen() {
        val state = GestureGuardState(activePanel = CockpitPanelRoute.StyleLab)
        assertTrue(guard.isGestureAllowed(GestureZone.SECONDARY_PANEL, state))
    }

    @Test
    fun settingsSubpages_blockGestures() {
        val state = GestureGuardState(activePanel = CockpitPanelRoute.Settings(SettingsSubpage.PORTRAIT_LAB))
        assertFalse(guard.isGestureAllowed(GestureZone.PREVIEW, state))
    }

    @Test
    fun horizontalScroll_allowedByDefault() {
        assertTrue(guard.isHorizontalScrollAllowed(GestureGuardState()))
    }

    @Test
    fun horizontalScroll_blockedWhenSettingsOpen() {
        assertFalse(guard.isHorizontalScrollAllowed(
            GestureGuardState(activePanel = CockpitPanelRoute.Settings())
        ))
    }

    @Test
    fun horizontalScroll_blockedWhenStyleLabOpen() {
        assertFalse(guard.isHorizontalScrollAllowed(
            GestureGuardState(activePanel = CockpitPanelRoute.StyleLab)
        ))
    }

    @Test
    fun horizontalScroll_blockedWhenFilterAdjustmentActive() {
        assertFalse(guard.isHorizontalScrollAllowed(
            GestureGuardState(isFilterAdjustmentActive = true)
        ))
    }

    @Test
    fun previewGesture_blockedWhenColorLabOpen() {
        val state = GestureGuardState(activePanel = CockpitPanelRoute.ColorLab)
        assertFalse(guard.isGestureAllowed(GestureZone.PREVIEW, state))
    }

    @Test
    fun secondaryPanelGesture_allowedWhenColorLabOpen() {
        val state = GestureGuardState(activePanel = CockpitPanelRoute.ColorLab)
        assertTrue(guard.isGestureAllowed(GestureZone.SECONDARY_PANEL, state))
    }

    @Test
    fun horizontalScroll_blockedWhenColorLabOpen() {
        assertFalse(guard.isHorizontalScrollAllowed(
            GestureGuardState(activePanel = CockpitPanelRoute.ColorLab)
        ))
    }
}
