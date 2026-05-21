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
    data object FilterLab : CockpitPanelRoute()
}
