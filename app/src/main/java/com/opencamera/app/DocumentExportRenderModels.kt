package com.opencamera.app

import com.opencamera.app.i18n.AppTextResolver

internal sealed class ExportState {
    data class InProgress(val currentPage: Int, val totalPages: Int) : ExportState()
    data class Success(val totalPages: Int) : ExportState()
    data class Failed(val errorMessage: String) : ExportState()
}

internal data class DocumentExportRenderModel(
    val visible: Boolean = false,
    val titleText: String = "",
    val progressText: String = "",
    val isError: Boolean = false,
    val showReturnButton: Boolean = false,
    val returnLabel: String = "",
    val showRetryButton: Boolean = false,
    val retryLabel: String = ""
)

internal fun exportRenderModel(
    panelState: CockpitPanelUiState,
    text: AppTextResolver
): DocumentExportRenderModel {
    val isExportRoute = panelState.route is CockpitPanelRoute.Export
    if (!isExportRoute) {
        return DocumentExportRenderModel()
    }

    return when (val exportState = panelState.exportState) {
        null -> DocumentExportRenderModel(
            visible = true,
            titleText = text.get(R.string.button_document_batch_export),
            progressText = text.documentExportProgress(0, 0),
            returnLabel = text.get(R.string.button_document_export_return),
            retryLabel = text.get(R.string.button_document_export_retry)
        )
        is ExportState.InProgress -> DocumentExportRenderModel(
            visible = true,
            titleText = text.get(R.string.button_document_batch_export),
            progressText = text.documentExportProgress(exportState.currentPage, exportState.totalPages),
            returnLabel = text.get(R.string.button_document_export_return),
            retryLabel = text.get(R.string.button_document_export_retry)
        )
        is ExportState.Success -> DocumentExportRenderModel(
            visible = true,
            titleText = text.get(R.string.document_export_success).format(exportState.totalPages),
            progressText = "",
            showReturnButton = true,
            returnLabel = text.get(R.string.button_document_export_return)
        )
        is ExportState.Failed -> DocumentExportRenderModel(
            visible = true,
            titleText = text.get(R.string.document_export_failed),
            progressText = exportState.errorMessage,
            isError = true,
            showReturnButton = true,
            returnLabel = text.get(R.string.button_document_export_return),
            showRetryButton = true,
            retryLabel = text.get(R.string.button_document_export_retry)
        )
    }
}
