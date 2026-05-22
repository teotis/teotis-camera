package com.opencamera.app

enum class SettingsSubpage {
    ROOT,
    PORTRAIT_LAB,
    WATERMARK_SELECTOR,
    WATERMARK_DETAIL
}

sealed class CockpitPanelRoute {
    data object None : CockpitPanelRoute()
    data object QuickBubble : CockpitPanelRoute()
    data object DevConsole : CockpitPanelRoute()
    data class Settings(val subpage: SettingsSubpage = SettingsSubpage.ROOT) : CockpitPanelRoute()
    data object StyleLab : CockpitPanelRoute()
    data object ColorLab : CockpitPanelRoute()
}

internal val CockpitPanelRoute.isSettingsOpen: Boolean
    get() = this is CockpitPanelRoute.Settings

internal val CockpitPanelRoute.isAnyPanelOpen: Boolean
    get() = this !is CockpitPanelRoute.None
