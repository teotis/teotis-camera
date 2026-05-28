package com.opencamera.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CockpitPanelRouteTest {

    @Test
    fun `None is default and not any panel`() {
        val route = CockpitPanelRoute.None
        assertFalse(route.isAnyPanelOpen)
        assertFalse(route.isSettingsOpen)
    }

    @Test
    fun `Settings route carries subpage and is recognized`() {
        val route = CockpitPanelRoute.Settings(SettingsSubpage.PORTRAIT_LAB)
        assertTrue(route.isAnyPanelOpen)
        assertTrue(route.isSettingsOpen)
        assertEquals(SettingsSubpage.PORTRAIT_LAB, route.subpage)
    }

    @Test
    fun `Settings default subpage is ROOT`() {
        val route = CockpitPanelRoute.Settings()
        assertEquals(SettingsSubpage.ROOT, route.subpage)
    }

    @Test
    fun `StyleLab route is a panel but not settings`() {
        val route = CockpitPanelRoute.StyleLab
        assertTrue(route.isAnyPanelOpen)
        assertFalse(route.isSettingsOpen)
    }

    @Test
    fun `QuickBubble route is a panel but not settings`() {
        val route = CockpitPanelRoute.QuickBubble
        assertTrue(route.isAnyPanelOpen)
        assertFalse(route.isSettingsOpen)
    }

    @Test
    fun `DevConsole route is a panel but not settings`() {
        val route = CockpitPanelRoute.DevConsole
        assertTrue(route.isAnyPanelOpen)
        assertFalse(route.isSettingsOpen)
    }

    @Test
    fun `Settings route equality depends on subpage`() {
        val a = CockpitPanelRoute.Settings(SettingsSubpage.ROOT)
        val b = CockpitPanelRoute.Settings(SettingsSubpage.ROOT)
        val c = CockpitPanelRoute.Settings(SettingsSubpage.PORTRAIT_LAB)
        assertEquals(a, b)
        assertFalse(a == c)
    }

    @Test
    fun `StyleLab and QuickBubble are distinct routes`() {
        val a: CockpitPanelRoute = CockpitPanelRoute.StyleLab
        val b: CockpitPanelRoute = CockpitPanelRoute.QuickBubble
        assertFalse(a == b)
    }

    @Test
    fun `ColorLab route is a panel but not settings`() {
        val route = CockpitPanelRoute.ColorLab
        assertTrue(route.isAnyPanelOpen)
        assertFalse(route.isSettingsOpen)
    }

    @Test
    fun `isAnyPanelOpen is true for all panel routes`() {
        val panels = listOf(
            CockpitPanelRoute.QuickBubble,
            CockpitPanelRoute.Settings(),
            CockpitPanelRoute.StyleLab,
            CockpitPanelRoute.ColorLab,
            CockpitPanelRoute.DevConsole
        )
        for (panel in panels) {
            assertTrue(panel.isAnyPanelOpen, "$panel should be isAnyPanelOpen")
        }
    }

    @Test
    fun `isAnyPanelOpen is false only for None`() {
        assertFalse(CockpitPanelRoute.None.isAnyPanelOpen)
    }
}
