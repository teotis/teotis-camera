package com.opencamera.app.gesture

import com.opencamera.app.CockpitPanelRoute
import com.opencamera.app.isSettingsOpen

data class GestureGuardState(
    val activePanel: CockpitPanelRoute = CockpitPanelRoute.None,
    val isFilterAdjustmentActive: Boolean = false
)

class GestureGuard {

    fun isGestureAllowed(zone: GestureZone, state: GestureGuardState): Boolean {
        val panel = state.activePanel
        if (panel.isSettingsOpen) return false
        if (panel is CockpitPanelRoute.StyleLab) {
            return zone == GestureZone.SECONDARY_PANEL
        }
        if (panel is CockpitPanelRoute.DevConsole) return false
        if (panel is CockpitPanelRoute.QuickBubble) return false
        return true
    }

    fun isHorizontalScrollAllowed(state: GestureGuardState): Boolean {
        val panel = state.activePanel
        return !panel.isSettingsOpen &&
               panel !is CockpitPanelRoute.StyleLab &&
               !state.isFilterAdjustmentActive
    }
}
