package com.opencamera.app

import com.opencamera.app.i18n.AppTextResolver
import com.opencamera.core.media.outputPathOrNull
import com.opencamera.core.media.renderUriOrNull
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.DocumentBatchStatus
import com.opencamera.core.session.SessionState

internal data class DocumentBatchRailRenderModel(
    val visible: Boolean,
    val countText: String,
    val items: List<DocumentBatchRailItemRenderModel>,
    val latestItemId: String?,
    val organizeEnabled: Boolean,
    val latestThumbnailUri: String? = null,
    val isSlimShooting: Boolean = false,
    val overviewLabel: String = "",
    val moveUpLabel: String = "",
    val moveDownLabel: String = ""
)

internal data class DocumentBatchRailItemRenderModel(
    val itemId: String,
    val pageNumber: Int,
    val renderUri: String?,
    val statusLabel: String?,
    val isLatest: Boolean,
    val removeContentDescription: String,
    val canMoveUp: Boolean = false,
    val canMoveDown: Boolean = false
)

internal fun documentBatchRailRenderModel(
    state: SessionState,
    text: AppTextResolver,
    cockpitRoute: CockpitPanelRoute = CockpitPanelRoute.None
): DocumentBatchRailRenderModel {
    val batch = state.presentation.documentBatch
    val isDocumentMode = state.activeMode == ModeId.DOCUMENT
    val isActive = batch.status == DocumentBatchStatus.ACTIVE

    if (!isDocumentMode || !isActive) {
        return DocumentBatchRailRenderModel(
            visible = false,
            countText = "",
            items = emptyList(),
            latestItemId = null,
            organizeEnabled = false
        )
    }

    val sortedItems = batch.items.sortedBy { it.orderIndex }
    val items = sortedItems.mapIndexed { index, item ->
        DocumentBatchRailItemRenderModel(
            itemId = item.itemId,
            pageNumber = index + 1,
            renderUri = item.renderUri ?: item.thumbnailSource.renderUriOrNull()
                ?: item.thumbnailSource.outputPathOrNull() ?: item.outputPath,
            statusLabel = null,
            isLatest = item.itemId == batch.latestItemId,
            removeContentDescription = text.get(R.string.document_batch_remove),
            canMoveUp = index > 0,
            canMoveDown = index < sortedItems.lastIndex
        )
    }

    val isSlimShooting = cockpitRoute is CockpitPanelRoute.None
    val latestItem = items.lastOrNull { it.isLatest }

    return DocumentBatchRailRenderModel(
        visible = items.isNotEmpty() && isSlimShooting,
        countText = text.documentBatchPageCount(items.size),
        items = items,
        latestItemId = batch.latestItemId,
        organizeEnabled = items.isNotEmpty(),
        latestThumbnailUri = latestItem?.renderUri,
        isSlimShooting = isSlimShooting,
        overviewLabel = text.get(R.string.button_document_batch_export),
        moveUpLabel = text.get(R.string.document_batch_move_up),
        moveDownLabel = text.get(R.string.document_batch_move_down)
    )
}
