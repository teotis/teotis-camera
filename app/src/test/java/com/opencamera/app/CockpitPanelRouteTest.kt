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
    fun `FilterLab route is a panel but not settings`() {
        val route = CockpitPanelRoute.FilterLab
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
    fun `FilterLab and QuickBubble are distinct routes`() {
        val a: CockpitPanelRoute = CockpitPanelRoute.FilterLab
        val b: CockpitPanelRoute = CockpitPanelRoute.QuickBubble
        assertFalse(a == b)
    }
}
