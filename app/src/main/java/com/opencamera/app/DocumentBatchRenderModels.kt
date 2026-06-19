package com.opencamera.app

import com.opencamera.app.i18n.AppTextResolver
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.DocumentBatchCropStatus
import com.opencamera.core.session.DocumentBatchStatus
import com.opencamera.core.session.SessionState

internal data class DocumentBatchOrganizerRenderModel(
    val visible: Boolean,
    val title: String,
    val countText: String,
    val items: List<DocumentBatchOrganizerItemRenderModel>
)

internal data class DocumentBatchOrganizerItemRenderModel(
    val itemId: String,
    val pageNumber: Int,
    val renderUri: String?,
    val cropStatusLabel: String?,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean
)

internal fun documentBatchOrganizerRenderModel(
    state: SessionState,
    text: AppTextResolver
): DocumentBatchOrganizerRenderModel {
    val batch = state.presentation.documentBatch
    val isDocumentMode = state.activeMode == ModeId.DOCUMENT
    val isActive = batch.status == DocumentBatchStatus.ACTIVE

    if (!isDocumentMode || !isActive) {
        return DocumentBatchOrganizerRenderModel(
            visible = false,
            title = text.get(R.string.document_batch_organizer_title),
            countText = "",
            items = emptyList()
        )
    }

    val items = batch.items.mapIndexed { index, item ->
        DocumentBatchOrganizerItemRenderModel(
            itemId = item.itemId,
            pageNumber = index + 1,
            renderUri = item.renderUri ?: item.outputPath,
            cropStatusLabel = item.cropStatus.toLabel(text),
            canMoveUp = index > 0,
            canMoveDown = index < batch.items.lastIndex
        )
    }

    return DocumentBatchOrganizerRenderModel(
        visible = true,
        title = text.get(R.string.document_batch_organizer_title),
        countText = text.documentBatchPageCount(items.size),
        items = items
    )
}

private fun DocumentBatchCropStatus.toLabel(text: AppTextResolver): String? {
    return when (this) {
        DocumentBatchCropStatus.NOT_REQUESTED -> null
        DocumentBatchCropStatus.APPLIED -> text.get(R.string.document_batch_crop_applied)
        DocumentBatchCropStatus.SKIPPED -> text.get(R.string.document_batch_crop_skipped)
        DocumentBatchCropStatus.FAILED -> text.get(R.string.document_batch_crop_failed)
    }
}

internal data class DocumentBatchRailRenderModel(
    val visible: Boolean,
    val countText: String,
    val items: List<DocumentBatchRailItemRenderModel>,
    val latestItemId: String?,
    val organizeEnabled: Boolean
)

internal data class DocumentBatchRailItemRenderModel(
    val itemId: String,
    val pageNumber: Int,
    val renderUri: String?,
    val statusLabel: String?,
    val isLatest: Boolean,
    val removeContentDescription: String
)

internal fun documentBatchRailRenderModel(
    state: SessionState,
    text: AppTextResolver
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
            renderUri = item.renderUri ?: item.outputPath,
            statusLabel = item.cropStatus.toLabel(text),
            isLatest = item.itemId == batch.latestItemId,
            removeContentDescription = text.get(R.string.document_batch_remove)
        )
    }

    return DocumentBatchRailRenderModel(
        visible = items.isNotEmpty(),
        countText = text.documentBatchPageCount(items.size),
        items = items,
        latestItemId = batch.latestItemId,
        organizeEnabled = items.isNotEmpty()
    )
}
