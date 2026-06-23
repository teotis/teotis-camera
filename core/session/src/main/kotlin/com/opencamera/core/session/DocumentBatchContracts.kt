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

/**
 * Four-phase workflow state for document mode.
 *
 * - [Shooting]: Camera active, user captures pages.
 * - [BatchOverview]: Overview of all captured pages with reorder/remove.
 * - [CropEdit]: Individual page crop editing.
 * - [Export]: Batch export in progress.
 */
enum class DocumentWorkflowPhase {
    Shooting,
    BatchOverview,
    CropEdit,
    Export
}

fun DocumentWorkflowPhase.toBatchStatus(): DocumentBatchStatus = when (this) {
    DocumentWorkflowPhase.Shooting -> DocumentBatchStatus.ACTIVE
    DocumentWorkflowPhase.BatchOverview -> DocumentBatchStatus.ACTIVE
    DocumentWorkflowPhase.CropEdit -> DocumentBatchStatus.ACTIVE
    DocumentWorkflowPhase.Export -> DocumentBatchStatus.FINISHED
}

fun DocumentBatchStatus.toWorkflowPhase(): DocumentWorkflowPhase = when (this) {
    DocumentBatchStatus.INACTIVE -> DocumentWorkflowPhase.Shooting
    DocumentBatchStatus.ACTIVE -> DocumentWorkflowPhase.Shooting
    DocumentBatchStatus.FINISHED -> DocumentWorkflowPhase.Export
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
    val pipelineNotes: List<String>,
    /** Manual crop rect in normalized 0..1 coords relative to the image (left, top, right, bottom). Null = no manual crop applied. */
    val manualCropRect: CropRect? = null,
    /** Whether the crop rect was manually adjusted by the user. */
    val isManuallyCropped: Boolean = false
)

enum class DocumentBatchCropStatus {
    NOT_REQUESTED,
    APPLIED,
    SKIPPED,
    FAILED,
    APPLIED_MANUAL
}

/**
 * Normalized crop rectangle in 0..1 coordinates relative to the image bounds.
 */
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
)

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

fun DocumentBatchState.upsertPreviewItem(previewItem: DocumentBatchItem): DocumentBatchState {
    if (status != DocumentBatchStatus.ACTIVE) return this
    val existingIndex = items.indexOfFirst { it.shotId == previewItem.shotId }
    val updatedItems = if (existingIndex >= 0) {
        items.mapIndexed { index, item ->
            if (index == existingIndex) previewItem.copy(orderIndex = item.orderIndex) else item
        }
    } else {
        items + previewItem.copy(orderIndex = items.size)
    }
    return copy(
        items = updatedItems,
        latestItemId = previewItem.itemId,
        lastMessage = "Page preview ready"
    )
}

fun DocumentBatchState.updateItemCropStatus(
    itemId: String,
    cropStatus: DocumentBatchCropStatus,
    cropRect: CropRect? = null
): DocumentBatchState {
    val newItems = items.map { item ->
        if (item.itemId == itemId) item.copy(
            cropStatus = cropStatus,
            manualCropRect = cropRect,
            isManuallyCropped = cropStatus == DocumentBatchCropStatus.APPLIED_MANUAL
        ) else item
    }
    return copy(
        items = newItems,
        lastMessage = "Crop status updated for $itemId"
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
