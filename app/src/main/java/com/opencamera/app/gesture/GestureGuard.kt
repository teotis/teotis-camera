package com.opencamera.app.gesture

data class GestureGuardState(
    val isSettingsPanelOpen: Boolean = false,
    val isFilterPanelOpen: Boolean = false,
    val isMoreControlsOpen: Boolean = false,
    val isFilterAdjustmentActive: Boolean = false
)

class GestureGuard {

    fun isGestureAllowed(zone: GestureZone, state: GestureGuardState): Boolean {
        if (state.isSettingsPanelOpen) return false
        if (state.isFilterPanelOpen) {
            return zone == GestureZone.SECONDARY_PANEL
        }
        if (state.isMoreControlsOpen) return false
        return true
    }

    fun isHorizontalScrollAllowed(state: GestureGuardState): Boolean {
        return !state.isSettingsPanelOpen && !state.isFilterPanelOpen && !state.isFilterAdjustmentActive
    }
}
