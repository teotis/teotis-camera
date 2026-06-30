package com.opencamera.app

import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.DocumentBatchStatus
import com.opencamera.core.session.DocumentWorkflowPhase

internal data class CockpitPanelUiState(
    val route: CockpitPanelRoute = CockpitPanelRoute.None,
    val selectedSettingsTab: SettingsTab = SettingsTab.COMMON,
    val selectedWatermarkDetailTemplateId: String? = null,
    val selectedFilterLabFamilyOverride: FilterLabFamily? = null,
    val isFilterAdjustmentVisible: Boolean = false,
    val filterAdjustmentMode: FilterAdjustmentMode = FilterAdjustmentMode.LIGHT,
    val isDocumentBatchOrganizerDismissed: Boolean = false,
    /** ID of the batch item currently being crop-edited. */
    val selectedCropEditItemId: String? = null,
    /** Current export state, non-null when export route is active. */
    val exportState: ExportState? = null
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
    data object ToggleStyleStrip : CockpitPanelCommand()
    data object ToggleCheckInStylePanel : CockpitPanelCommand()
    data class SelectCheckInScenario(val scenarioId: String) : CockpitPanelCommand()
    data class SelectCheckInStyle(val styleId: String) : CockpitPanelCommand()
    data object ToggleDocumentBatchOrganizer : CockpitPanelCommand()
    data object CloseDocumentBatchOrganizer : CockpitPanelCommand()
    data object NavigateToCropEdit : CockpitPanelCommand()
    data object NavigateToExport : CockpitPanelCommand()
    data object NavigateToBatchOverview : CockpitPanelCommand()
    data object CloseBatchOverview : CockpitPanelCommand()
    data object CloseCropEdit : CockpitPanelCommand()
    data class SelectCropEditItem(val itemId: String) : CockpitPanelCommand()
    data object StartExport : CockpitPanelCommand()
    data class UpdateExportProgress(val currentPage: Int, val totalPages: Int) : CockpitPanelCommand()
    data object CompleteExport : CockpitPanelCommand()
    data object FailExport : CockpitPanelCommand()
    data object CloseExport : CockpitPanelCommand()
    data object ReturnToShooting : CockpitPanelCommand()
    data object DocumentBatchCaptureTriggered : CockpitPanelCommand()
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

internal fun workflowPhaseToRoute(phase: DocumentWorkflowPhase): CockpitPanelRoute = when (phase) {
    DocumentWorkflowPhase.Shooting -> CockpitPanelRoute.None
    DocumentWorkflowPhase.BatchOverview -> CockpitPanelRoute.BatchOverview
    DocumentWorkflowPhase.CropEdit -> CockpitPanelRoute.CropEdit
    DocumentWorkflowPhase.Export -> CockpitPanelRoute.Export
}

internal fun documentBatchStartExportCommands(totalPages: Int): List<CockpitPanelCommand> {
    return listOf(
        CockpitPanelCommand.StartExport,
        CockpitPanelCommand.UpdateExportProgress(
            currentPage = 0,
            totalPages = totalPages.coerceAtLeast(0)
        )
    )
}

internal fun shouldCloseDocumentWorkflowRoute(
    route: CockpitPanelRoute,
    activeMode: ModeId,
    batchStatus: DocumentBatchStatus
): Boolean {
    if (!route.isDocumentWorkflowRoute) return false
    if (activeMode != ModeId.DOCUMENT) return true
    return when (route) {
        is CockpitPanelRoute.DocumentBatchOrganizer,
        is CockpitPanelRoute.BatchOverview,
        is CockpitPanelRoute.CropEdit -> batchStatus != DocumentBatchStatus.ACTIVE
        is CockpitPanelRoute.Export -> batchStatus == DocumentBatchStatus.INACTIVE
        else -> false
    }
}

internal fun nextState(
    current: CockpitPanelUiState,
    command: CockpitPanelCommand
): CockpitPanelUiState {
    val defaultState = CockpitPanelUiState()
    return when (command) {
        is CockpitPanelCommand.DismissAll -> defaultState.copy(
            isDocumentBatchOrganizerDismissed = current.route is CockpitPanelRoute.DocumentBatchOrganizer ||
                current.route is CockpitPanelRoute.BatchOverview ||
                current.isDocumentBatchOrganizerDismissed
        )

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

        is CockpitPanelCommand.ToggleStyleStrip -> {
            if (current.route is CockpitPanelRoute.StyleStrip) {
                current.copy(route = CockpitPanelRoute.None)
            } else {
                current.copy(route = CockpitPanelRoute.StyleStrip)
            }
        }

        is CockpitPanelCommand.ToggleCheckInStylePanel -> {
            if (current.route is CockpitPanelRoute.CheckInStylePanel) {
                defaultState
            } else {
                current.copy(
                    route = CockpitPanelRoute.CheckInStylePanel,
                    selectedFilterLabFamilyOverride = null
                )
            }
        }

        is CockpitPanelCommand.SelectCheckInScenario -> {
            current
        }

        is CockpitPanelCommand.SelectCheckInStyle -> {
            current
        }

        is CockpitPanelCommand.ToggleDocumentBatchOrganizer -> {
            if (current.route is CockpitPanelRoute.DocumentBatchOrganizer ||
                current.route is CockpitPanelRoute.BatchOverview) {
                current.copy(
                    route = CockpitPanelRoute.None,
                    isDocumentBatchOrganizerDismissed = true
                )
            } else if (current.isDocumentBatchOrganizerDismissed) {
                current
            } else {
                current.copy(route = CockpitPanelRoute.BatchOverview)
            }
        }

        is CockpitPanelCommand.CloseDocumentBatchOrganizer -> {
            if (current.route is CockpitPanelRoute.DocumentBatchOrganizer ||
                current.route is CockpitPanelRoute.BatchOverview) {
                current.copy(
                    route = CockpitPanelRoute.None,
                    isDocumentBatchOrganizerDismissed = true
                )
            } else {
                current
            }
        }

        is CockpitPanelCommand.NavigateToCropEdit -> {
            current.copy(route = CockpitPanelRoute.CropEdit)
        }

        is CockpitPanelCommand.NavigateToExport -> {
            current.copy(route = CockpitPanelRoute.Export)
        }

        is CockpitPanelCommand.NavigateToBatchOverview -> {
            current.copy(
                route = CockpitPanelRoute.BatchOverview,
                isDocumentBatchOrganizerDismissed = false,
                selectedCropEditItemId = null
            )
        }

        is CockpitPanelCommand.CloseBatchOverview -> {
            current.copy(
                route = CockpitPanelRoute.None,
                isDocumentBatchOrganizerDismissed = false,
                selectedCropEditItemId = null
            )
        }

        is CockpitPanelCommand.CloseCropEdit -> {
            current.copy(
                route = CockpitPanelRoute.BatchOverview,
                selectedCropEditItemId = null
            )
        }

        is CockpitPanelCommand.SelectCropEditItem -> {
            current.copy(selectedCropEditItemId = command.itemId)
        }

        is CockpitPanelCommand.StartExport -> {
            current.copy(
                route = CockpitPanelRoute.Export,
                exportState = ExportState.InProgress(currentPage = 0, totalPages = 0)
            )
        }

        is CockpitPanelCommand.UpdateExportProgress -> {
            current.copy(
                exportState = ExportState.InProgress(
                    currentPage = command.currentPage,
                    totalPages = command.totalPages
                )
            )
        }

        is CockpitPanelCommand.CompleteExport -> {
            val totalPages = (current.exportState as? ExportState.InProgress)?.totalPages ?: 0
            current.copy(exportState = ExportState.Success(totalPages))
        }

        is CockpitPanelCommand.FailExport -> {
            current.copy(exportState = ExportState.Failed(errorMessage = "导出失败"))
        }

        is CockpitPanelCommand.CloseExport -> {
            current.copy(
                route = CockpitPanelRoute.BatchOverview,
                exportState = null,
                selectedCropEditItemId = null
            )
        }

        is CockpitPanelCommand.ReturnToShooting -> {
            current.copy(
                route = CockpitPanelRoute.None,
                exportState = null,
                isDocumentBatchOrganizerDismissed = true
            )
        }

        is CockpitPanelCommand.DocumentBatchCaptureTriggered -> {
            current.copy(isDocumentBatchOrganizerDismissed = false)
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
                is CockpitPanelRoute.CheckInStylePanel,
                is CockpitPanelRoute.StyleStrip,
                is CockpitPanelRoute.DevConsole,
                is CockpitPanelRoute.QuickBubble,
                is CockpitPanelRoute.DocumentBatchOrganizer -> {
                    current.copy(
                        route = CockpitPanelRoute.None,
                        isDocumentBatchOrganizerDismissed = current.route is CockpitPanelRoute.DocumentBatchOrganizer ||
                            current.isDocumentBatchOrganizerDismissed
                    )
                }
                is CockpitPanelRoute.BatchOverview -> {
                    current.copy(route = CockpitPanelRoute.None)
                }
                is CockpitPanelRoute.CropEdit -> {
                    current.copy(
                        route = CockpitPanelRoute.BatchOverview,
                        selectedCropEditItemId = null
                    )
                }
                is CockpitPanelRoute.Export -> {
                    current.copy(
                        route = CockpitPanelRoute.BatchOverview,
                        exportState = null
                    )
                }
            }
        }
    }
}
