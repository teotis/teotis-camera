package com.opencamera.app

import android.view.View
import androidx.core.view.isVisible

internal class MainActivityRenderer(
    private val views: MainActivityViews,
    private val cockpit: CockpitSurfaceRenderer,
    private val settings: SettingsPanelRenderer,
    private val filterLab: FilterLabPanelRenderer,
    private val devConsole: DevConsoleRenderer
) {
    private var lastRenderedPanelRoute: CockpitPanelRoute = CockpitPanelRoute.None

    private val transitionController = PanelTransitionController(
        panelViews = {
            listOfNotNull(activePanelView(lastRenderedPanelRoute))
        },
        scrimView = { views.panelDismissScrim }
    )

    fun renderPanelVisibility(activePanelRoute: CockpitPanelRoute) {
        val route = activePanelRoute
        val routeChanged = route != lastRenderedPanelRoute

        if (routeChanged) {
            transitionController.cancel()
        }

        lastRenderedPanelRoute = route

        // Set final visibility deterministically for all panels.
        views.settingsPanel.panel.isVisible = route.isSettingsOpen
        views.filterLab.panel.isVisible = route is CockpitPanelRoute.StyleLab ||
            route is CockpitPanelRoute.ColorLab ||
            route is CockpitPanelRoute.CheckInStylePanel
        if (route is CockpitPanelRoute.ColorLab) {
            views.filterLab.panel.isNestedScrollingEnabled = false
            views.filterLab.panel.overScrollMode = android.view.View.OVER_SCROLL_NEVER
        } else {
            views.filterLab.panel.isNestedScrollingEnabled = true
            views.filterLab.panel.overScrollMode = android.view.View.OVER_SCROLL_ALWAYS
        }
        views.documentBatchOrganizer.panel.isVisible = route is CockpitPanelRoute.DocumentBatchOrganizer
        views.filterStrip.scroll.isVisible = route is CockpitPanelRoute.StyleStrip
        views.quickPanel.panel.isVisible = route is CockpitPanelRoute.QuickBubble
        views.panelDismissScrim.isVisible = route.isAnyPanelOpen && route !is CockpitPanelRoute.StyleStrip

        // Reset panel transforms to final state so cancelled animations don't leak.
        if (route.isAnyPanelOpen) {
            for (panel in listOf(
                views.settingsPanel.panel,
                views.filterLab.panel,
                views.documentBatchOrganizer.panel,
                views.quickPanel.panel
            )) {
                if (panel.isVisible) {
                    panel.alpha = 1f
                    panel.translationY = 0f
                }
            }
            views.panelDismissScrim.alpha = 1f
        }

        if (routeChanged) {
            // Run transition for the newly visible panel.
            if (route.isAnyPanelOpen) {
                transitionController.transition(opening = true)
            }

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
        views.topBar.filterEntry.alpha = if (route is CockpitPanelRoute.StyleLab || route is CockpitPanelRoute.CheckInStylePanel) 1f else 0.92f
        views.quickPanel.launcher.alpha = if (route is CockpitPanelRoute.QuickBubble) 1f else 0.86f
    }

    fun renderDevEntryVisibility(isDebug: Boolean) {
        views.devConsole.entry.isVisible = isDebug
    }

    private fun activePanelView(route: CockpitPanelRoute): View? = when (route) {
        is CockpitPanelRoute.Settings -> views.settingsPanel.panel
        is CockpitPanelRoute.StyleLab,
        is CockpitPanelRoute.ColorLab,
        is CockpitPanelRoute.CheckInStylePanel -> views.filterLab.panel
        is CockpitPanelRoute.DocumentBatchOrganizer -> views.documentBatchOrganizer.panel
        is CockpitPanelRoute.QuickBubble -> views.quickPanel.panel
        is CockpitPanelRoute.DevConsole -> views.devConsole.panel
        is CockpitPanelRoute.StyleStrip,
        is CockpitPanelRoute.None -> null
    }
}
