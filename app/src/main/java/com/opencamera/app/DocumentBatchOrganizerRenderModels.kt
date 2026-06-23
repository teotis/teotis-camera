package com.opencamera.app

import com.opencamera.app.i18n.AppTextResolver
import com.opencamera.core.media.outputPathOrNull
import com.opencamera.core.media.renderUriOrNull
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.DocumentBatchStatus
import com.opencamera.core.session.SessionState

internal data class DocumentBatchOrganizerRenderModel(
    val visible: Boolean,
    val title: String,
    val countText: String,
    val items: List<DocumentBatchOrganizerItemRenderModel>,
    val isBatchOverviewMode: Boolean = false,
    val showContinueShooting: Boolean = false,
    val showExport: Boolean = false,
    val emptyHint: String = ""
)

internal data class DocumentBatchOrganizerItemRenderModel(
    val itemId: String,
    val pageNumber: Int,
    val renderUri: String?,
    val cropStatusLabel: String?,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val cropEditLabel: String? = null
)

internal fun documentBatchOrganizerRenderModel(
    state: SessionState,
    text: AppTextResolver,
    activeRoute: CockpitPanelRoute = CockpitPanelRoute.None
): DocumentBatchOrganizerRenderModel {
    val batch = state.presentation.documentBatch
    val isDocumentMode = state.activeMode == ModeId.DOCUMENT
    val isActive = batch.status == DocumentBatchStatus.ACTIVE
    val isBatchOverview = activeRoute is CockpitPanelRoute.BatchOverview

    if (!isDocumentMode || !isActive) {
        return DocumentBatchOrganizerRenderModel(
            visible = false,
            title = text.get(R.string.document_batch_organizer_title),
            countText = "",
            items = emptyList()
        )
    }

    if (!isBatchOverview) {
        return DocumentBatchOrganizerRenderModel(
            visible = false,
            title = text.get(R.string.document_batch_organizer_title),
            countText = text.documentBatchPageCount(batch.items.size),
            items = emptyList()
        )
    }

    val sortedItems = batch.items.sortedBy { it.orderIndex }
    val items = sortedItems.mapIndexed { index, item ->
        DocumentBatchOrganizerItemRenderModel(
            itemId = item.itemId,
            pageNumber = index + 1,
            renderUri = item.renderUri ?: item.thumbnailSource.renderUriOrNull()
                ?: item.thumbnailSource.outputPathOrNull() ?: item.outputPath,
            cropStatusLabel = null,
            canMoveUp = index > 0,
            canMoveDown = index < sortedItems.lastIndex,
            cropEditLabel = null
        )
    }

    return DocumentBatchOrganizerRenderModel(
        visible = true,
        title = text.get(R.string.document_batch_organizer_title),
        countText = text.documentBatchPageCount(items.size),
        items = items,
        isBatchOverviewMode = true,
        showContinueShooting = true,
        showExport = items.isNotEmpty(),
        emptyHint = text.get(R.string.document_batch_empty_hint)
    )
}
