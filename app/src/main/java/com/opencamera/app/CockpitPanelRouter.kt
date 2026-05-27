package com.opencamera.app

internal data class CockpitPanelUiState(
    val route: CockpitPanelRoute = CockpitPanelRoute.None,
    val selectedSettingsTab: SettingsTab = SettingsTab.COMMON,
    val selectedWatermarkDetailTemplateId: String? = null,
    val selectedFilterLabFamilyOverride: FilterLabFamily? = null,
    val isFilterAdjustmentVisible: Boolean = false,
    val filterAdjustmentMode: FilterAdjustmentMode = FilterAdjustmentMode.LIGHT
)

internal sealed class CockpitPanelCommand {
    data object DismissAll : CockpitPanelCommand()
    data object ToggleColorLab : CockpitPanelCommand()
    data object ToggleStyleLab : CockpitPanelCommand()
    data object ToggleSettingsRoot : CockpitPanelCommand()
    data object CloseSettings : CockpitPanelCommand()
    data class SelectSettingsTab(val tab: SettingsTab) : CockpitPanelCommand()
    data object SettingsBack : CockpitPanelCommand()
    data object ToggleDevConsole : CockpitPanelCommand()
    data object ToggleQuickBubble : CockpitPanelCommand()
    data object CloseFilterLab : CockpitPanelCommand()
    data object CloseDevConsole : CockpitPanelCommand()
    data object OpenPortraitLab : CockpitPanelCommand()
    data object OpenWatermarkSelector : CockpitPanelCommand()
    data class OpenWatermarkDetail(val templateId: String) : CockpitPanelCommand()
    data class SelectFilterFamily(val family: FilterLabFamily) : CockpitPanelCommand()
    data object ToggleFilterAdjustmentMode : CockpitPanelCommand()
    data object ToggleDocumentBatchOrganizer : CockpitPanelCommand()
    data object CloseDocumentBatchOrganizer : CockpitPanelCommand()
    data object AndroidBack : CockpitPanelCommand()
}

internal class CockpitPanelRouter(
    initialState: CockpitPanelUiState = CockpitPanelUiState()
) {
    var state: CockpitPanelUiState = initialState
        private set

    fun reduce(command: CockpitPanelCommand): CockpitPanelUiState {
        state = nextState(state, command)
        return state
    }
}

internal fun nextState(
    current: CockpitPanelUiState,
    command: CockpitPanelCommand
): CockpitPanelUiState {
    val defaultState = CockpitPanelUiState()
    return when (command) {
        is CockpitPanelCommand.DismissAll -> defaultState

        is CockpitPanelCommand.ToggleColorLab -> {
            if (current.route is CockpitPanelRoute.ColorLab) {
                defaultState
            } else {
                current.copy(
                    route = CockpitPanelRoute.ColorLab,
                    isFilterAdjustmentVisible = true,
                    selectedFilterLabFamilyOverride = null,
                    filterAdjustmentMode = FilterAdjustmentMode.LIGHT
                )
            }
        }

        is CockpitPanelCommand.ToggleStyleLab -> {
            if (current.route is CockpitPanelRoute.StyleLab) {
                defaultState
            } else {
                current.copy(
                    route = CockpitPanelRoute.StyleLab,
                    isFilterAdjustmentVisible = true,
                    selectedFilterLabFamilyOverride = null,
                    filterAdjustmentMode = FilterAdjustmentMode.LIGHT
                )
            }
        }

        is CockpitPanelCommand.ToggleSettingsRoot -> {
            if (current.route.isSettingsOpen) {
                defaultState
            } else {
                current.copy(
                    route = CockpitPanelRoute.Settings(),
                    selectedWatermarkDetailTemplateId = null
                )
            }
        }

        is CockpitPanelCommand.CloseSettings -> {
            current.copy(
                route = CockpitPanelRoute.None,
                selectedSettingsTab = SettingsTab.COMMON,
                selectedWatermarkDetailTemplateId = null
            )
        }

        is CockpitPanelCommand.SelectSettingsTab -> {
            current.copy(selectedSettingsTab = command.tab)
        }

        is CockpitPanelCommand.SettingsBack -> {
            when (current.route) {
                is CockpitPanelRoute.Settings -> {
                    when (current.route.subpage) {
                        SettingsSubpage.WATERMARK_DETAIL -> {
                            current.copy(
                                route = CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_SELECTOR),
                                selectedWatermarkDetailTemplateId = null
                            )
                        }
                        SettingsSubpage.PORTRAIT_LAB,
                        SettingsSubpage.WATERMARK_SELECTOR -> {
                            current.copy(route = CockpitPanelRoute.Settings())
                        }
                        SettingsSubpage.ROOT -> {
                            current
                        }
                    }
                }
                else -> current
            }
        }

        is CockpitPanelCommand.ToggleDevConsole -> {
            if (current.route is CockpitPanelRoute.DevConsole) {
                current.copy(route = CockpitPanelRoute.None)
            } else {
                current.copy(route = CockpitPanelRoute.DevConsole)
            }
        }

        is CockpitPanelCommand.ToggleQuickBubble -> {
            if (current.route is CockpitPanelRoute.QuickBubble) {
                current.copy(route = CockpitPanelRoute.None)
            } else {
                current.copy(route = CockpitPanelRoute.QuickBubble)
            }
        }

        is CockpitPanelCommand.CloseFilterLab -> {
            current.copy(
                route = CockpitPanelRoute.None,
                selectedFilterLabFamilyOverride = null,
                isFilterAdjustmentVisible = false,
                filterAdjustmentMode = FilterAdjustmentMode.LIGHT
            )
        }

        is CockpitPanelCommand.CloseDevConsole -> {
            current.copy(route = CockpitPanelRoute.None)
        }

        is CockpitPanelCommand.OpenPortraitLab -> {
            current.copy(route = CockpitPanelRoute.Settings(SettingsSubpage.PORTRAIT_LAB))
        }

        is CockpitPanelCommand.OpenWatermarkSelector -> {
            current.copy(
                route = CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_SELECTOR),
                selectedWatermarkDetailTemplateId = null
            )
        }

        is CockpitPanelCommand.OpenWatermarkDetail -> {
            current.copy(
                route = CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_DETAIL),
                selectedWatermarkDetailTemplateId = command.templateId
            )
        }

        is CockpitPanelCommand.SelectFilterFamily -> {
            current.copy(
                selectedFilterLabFamilyOverride = command.family,
                isFilterAdjustmentVisible = true
            )
        }

        is CockpitPanelCommand.ToggleFilterAdjustmentMode -> {
            current.copy(
                filterAdjustmentMode = when (current.filterAdjustmentMode) {
                    FilterAdjustmentMode.LIGHT -> FilterAdjustmentMode.ADVANCED
                    FilterAdjustmentMode.ADVANCED -> FilterAdjustmentMode.LIGHT
                }
            )
        }

        is CockpitPanelCommand.ToggleDocumentBatchOrganizer -> {
            if (current.route is CockpitPanelRoute.DocumentBatchOrganizer) {
                current.copy(route = CockpitPanelRoute.None)
            } else {
                current.copy(route = CockpitPanelRoute.DocumentBatchOrganizer)
            }
        }

        is CockpitPanelCommand.CloseDocumentBatchOrganizer -> {
            if (current.route is CockpitPanelRoute.DocumentBatchOrganizer) {
                current.copy(route = CockpitPanelRoute.None)
            } else {
                current
            }
        }

        is CockpitPanelCommand.AndroidBack -> {
            when (current.route) {
                is CockpitPanelRoute.None -> current
                is CockpitPanelRoute.Settings -> {
                    when (current.route.subpage) {
                        SettingsSubpage.WATERMARK_DETAIL -> {
                            current.copy(
                                route = CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_SELECTOR),
                                selectedWatermarkDetailTemplateId = null
                            )
                        }
                        SettingsSubpage.PORTRAIT_LAB,
                        SettingsSubpage.WATERMARK_SELECTOR -> {
                            current.copy(route = CockpitPanelRoute.Settings())
                        }
                        SettingsSubpage.ROOT -> {
                            current.copy(
                                route = CockpitPanelRoute.None,
                                selectedSettingsTab = SettingsTab.COMMON
                            )
                        }
                    }
                }
                is CockpitPanelRoute.StyleLab,
                is CockpitPanelRoute.ColorLab -> {
                    current.copy(
                        route = CockpitPanelRoute.None,
                        selectedFilterLabFamilyOverride = null,
                        isFilterAdjustmentVisible = false,
                        filterAdjustmentMode = FilterAdjustmentMode.LIGHT
                    )
                }
                is CockpitPanelRoute.DevConsole,
                is CockpitPanelRoute.QuickBubble,
                is CockpitPanelRoute.DocumentBatchOrganizer -> {
                    current.copy(route = CockpitPanelRoute.None)
                }
            }
        }
    }
}
