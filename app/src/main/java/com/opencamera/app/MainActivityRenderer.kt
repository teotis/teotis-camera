package com.opencamera.app

import androidx.core.view.isVisible

internal class MainActivityRenderer(
    private val views: MainActivityViews,
    private val cockpit: CockpitSurfaceRenderer,
    private val settings: SettingsPanelRenderer,
    private val filterLab: FilterLabPanelRenderer,
    private val devConsole: DevConsoleRenderer
) {
    private var lastRenderedPanelRoute: CockpitPanelRoute = CockpitPanelRoute.None

    fun renderPanelVisibility(activePanelRoute: CockpitPanelRoute) {
        val route = activePanelRoute
        val routeChanged = route != lastRenderedPanelRoute
        lastRenderedPanelRoute = route
        views.settingsPanel.panel.isVisible = route.isSettingsOpen
        views.filterLab.panel.isVisible = route is CockpitPanelRoute.StyleLab || route is CockpitPanelRoute.ColorLab
        views.documentBatchOrganizer.panel.isVisible = route is CockpitPanelRoute.DocumentBatchOrganizer
        views.panelDismissScrim.isVisible = route.isAnyPanelOpen
        if (routeChanged) {
            if (views.settingsPanel.panel.isVisible) views.settingsPanel.panel.scrollTo(0, 0)
            if (views.filterLab.panel.isVisible) views.filterLab.panel.scrollTo(0, 0)
            if (views.documentBatchOrganizer.panel.isVisible) views.documentBatchOrganizer.panel.scrollTo(0, 0)
        }

        val subpage = (route as? CockpitPanelRoute.Settings)?.subpage
        views.settingsPanel.rootContent.isVisible = route.isSettingsOpen && (subpage == null || subpage == SettingsSubpage.ROOT)
        views.settingsPanel.portraitLabContent.isVisible = subpage == SettingsSubpage.PORTRAIT_LAB
        views.settingsPanel.watermarkSelectorContent.isVisible = subpage == SettingsSubpage.WATERMARK_SELECTOR
        views.settingsPanel.watermarkDetailContent.isVisible = subpage == SettingsSubpage.WATERMARK_DETAIL
        views.settingsPanel.back.isVisible = route.isSettingsOpen && subpage != null && subpage != SettingsSubpage.ROOT

        views.topBar.colorLabEntry.alpha = if (route is CockpitPanelRoute.ColorLab) 1f else 0.92f
        views.topBar.settingsEntry.alpha = if (route.isSettingsOpen) 1f else 0.92f
        views.topBar.filterEntry.alpha = if (route is CockpitPanelRoute.StyleLab) 1f else 0.92f
        views.quickPanel.panel.isVisible = route is CockpitPanelRoute.QuickBubble
        views.quickPanel.launcher.alpha = if (route is CockpitPanelRoute.QuickBubble) 1f else 0.86f
    }

    fun renderDevEntryVisibility(isDebug: Boolean) {
        views.devConsole.entry.isVisible = isDebug
    }
}
