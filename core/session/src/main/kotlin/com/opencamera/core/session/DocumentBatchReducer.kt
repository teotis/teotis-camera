package com.opencamera.core.session

/** Pure document-batch mutations and their user-visible status messages. */
internal object DocumentBatchReducer {
    fun clear(batch: DocumentBatchState): DocumentBatchTransition = when (batch.status) {
        DocumentBatchStatus.INACTIVE -> transition(batch, "No active document batch to clear")
        else -> transition(
            batch.copy(items = emptyList(), latestItemId = null, lastMessage = "Batch cleared"),
            "Document batch cleared"
        )
    }

    fun remove(batch: DocumentBatchState, itemId: String): DocumentBatchTransition {
        if (batch.status != DocumentBatchStatus.ACTIVE) return transition(batch, "Cannot remove item: batch is not active")
        if (batch.items.none { it.itemId == itemId }) return transition(batch, "Cannot remove item: $itemId not in batch")
        return transition(batch.removeItem(itemId), "Removed document page from batch")
    }

    fun move(
        batch: DocumentBatchState,
        itemId: String,
        direction: DocumentBatchMoveDirection
    ): DocumentBatchTransition {
        if (batch.status != DocumentBatchStatus.ACTIVE) return transition(batch, "Cannot move item: batch is not active")
        val currentIndex = batch.items.indexOfFirst { it.itemId == itemId }
        if (currentIndex == -1) return transition(batch, "Cannot move: $itemId not in batch")
        if (batch.items.size < 2) return transition(batch, "Cannot reorder: batch has fewer than 2 items")
        val targetIndex = when (direction) {
            DocumentBatchMoveDirection.UP -> (currentIndex - 1).coerceAtLeast(0)
            DocumentBatchMoveDirection.DOWN -> (currentIndex + 1).coerceAtMost(batch.items.lastIndex)
        }
        if (targetIndex == currentIndex) return transition(batch, "Item already at target position")
        return transition(batch.moveItem(itemId, direction), "Reordered document pages")
    }

    fun reorder(batch: DocumentBatchState, orderedItemIds: List<String>): DocumentBatchTransition {
        if (batch.status != DocumentBatchStatus.ACTIVE) return transition(batch, "Cannot reorder: batch is not active")
        return transition(batch.reorder(orderedItemIds), "Document pages reordered")
    }

    fun updateCrop(
        batch: DocumentBatchState,
        itemId: String,
        cropStatus: DocumentBatchCropStatus,
        cropRect: CropRect?
    ): DocumentBatchTransition {
        if (batch.status != DocumentBatchStatus.ACTIVE) return transition(batch, "Cannot update crop: batch is not active")
        return transition(batch.updateItemCropStatus(itemId, cropStatus, cropRect), "Crop status updated for $itemId")
    }

    fun finish(batch: DocumentBatchState): DocumentBatchTransition {
        if (batch.status != DocumentBatchStatus.ACTIVE) return transition(batch, "Cannot finish: batch is not active")
        return transition(
            batch.copy(status = DocumentBatchStatus.FINISHED, lastMessage = "Batch finished"),
            "Document batch finished"
        )
    }

    private fun transition(batch: DocumentBatchState, lastAction: String) =
        DocumentBatchTransition(batch, lastAction)
}

internal data class DocumentBatchTransition(
    val batch: DocumentBatchState,
    val lastAction: String
)
