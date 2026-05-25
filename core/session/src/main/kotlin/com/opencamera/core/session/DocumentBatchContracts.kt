package com.opencamera.core.session

import com.opencamera.core.media.ThumbnailSource
import java.util.UUID

data class DocumentBatchState(
    val batchId: String,
    val status: DocumentBatchStatus,
    val items: List<DocumentBatchItem> = emptyList(),
    val latestItemId: String? = null,
    val lastMessage: String? = null
) {
    val pageCount: Int get() = items.size

    companion object {
        fun inactive(): DocumentBatchState = DocumentBatchState(
            batchId = "",
            status = DocumentBatchStatus.INACTIVE
        )

        fun active(batchId: String = UUID.randomUUID().toString()): DocumentBatchState = DocumentBatchState(
            batchId = batchId,
            status = DocumentBatchStatus.ACTIVE
        )
    }
}

enum class DocumentBatchStatus {
    INACTIVE,
    ACTIVE,
    FINISHED
}

data class DocumentBatchItem(
    val itemId: String,
    val shotId: String,
    val orderIndex: Int,
    val outputPath: String?,
    val renderUri: String?,
    val thumbnailSource: ThumbnailSource,
    val profileId: String?,
    val scanMode: String?,
    val cropStatus: DocumentBatchCropStatus,
    val pipelineNotes: List<String>
)

enum class DocumentBatchCropStatus {
    NOT_REQUESTED,
    APPLIED,
    SKIPPED,
    FAILED
}

fun DocumentBatchState.removeItem(itemId: String): DocumentBatchState {
    val newItems = items
        .filter { it.itemId != itemId }
        .mapIndexed { index, item -> item.copy(orderIndex = index) }
    return copy(
        items = newItems,
        latestItemId = newItems.lastOrNull()?.itemId,
        lastMessage = "Item removed"
    )
}

fun DocumentBatchState.moveItem(itemId: String, direction: DocumentBatchMoveDirection): DocumentBatchState {
    val currentIndex = items.indexOfFirst { it.itemId == itemId }
    if (currentIndex == -1) return this

    val targetIndex = when (direction) {
        DocumentBatchMoveDirection.UP -> (currentIndex - 1).coerceAtLeast(0)
        DocumentBatchMoveDirection.DOWN -> (currentIndex + 1).coerceAtMost(items.lastIndex)
    }
    if (currentIndex == targetIndex) return this

    val mutableItems = items.toMutableList()
    val item = mutableItems.removeAt(currentIndex)
    mutableItems.add(targetIndex, item)

    return copy(
        items = mutableItems.mapIndexed { index, batchItem -> batchItem.copy(orderIndex = index) },
        lastMessage = "Item moved"
    )
}

fun DocumentBatchState.reorder(orderedItemIds: List<String>): DocumentBatchState {
    val itemMap = items.associateBy { it.itemId }
    val reordered = orderedItemIds.mapNotNull { id -> itemMap[id] }
    if (reordered.size != items.size) return this

    return copy(
        items = reordered.mapIndexed { index, item -> item.copy(orderIndex = index) },
        lastMessage = "Items reordered"
    )
}

enum class DocumentBatchMoveDirection {
    UP,
    DOWN
}
