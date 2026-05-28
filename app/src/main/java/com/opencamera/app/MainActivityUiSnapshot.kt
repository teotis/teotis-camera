package com.opencamera.app

import com.opencamera.core.session.SessionState

internal data class MainActivityUiSnapshot(
    val sessionState: SessionState?,
    val activePanelRoute: CockpitPanelRoute,
    val isFilterAdjustmentVisible: Boolean,
    val settingsPage: SessionSettingsPageRenderModel?,
    val quickPanelSheet: QuickPanelSheetRenderModel?,
    val portraitLabPage: PortraitLabPageRenderModel?,
    val watermarkDetailPage: WatermarkLabDetailRenderModel?,
    val filterLabPage: FilterLabPageRenderModel?,
    val devLog: DevLogRenderModel?
)
