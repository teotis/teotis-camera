package com.opencamera.app

import com.opencamera.app.i18n.AppTextResolver
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.CropRect
import com.opencamera.core.session.DocumentBatchCropStatus
import com.opencamera.core.session.DocumentBatchStatus
import com.opencamera.core.session.SessionState

/**
 * Render model for the crop-edit overlay.
 *
 * @property visible Whether the overlay should be shown.
 * @property itemId ID of the batch item being edited.
 * @property pageNumber 1-based page number within the batch.
 * @property pageRenderUri URI of the page image to display.
 * @property cropEdges Current crop edges in normalized 0..1 coordinates.
 * @property controlPoints The 8 draggable control points derived from [cropEdges].
 * @property titleText Title text (e.g. "第 2 页").
 * @property confirmLabel Confirm button label.
 * @property cancelLabel Cancel button label.
 */
internal data class DocumentCropEditRenderModel(
    val visible: Boolean,
    val itemId: String,
    val pageNumber: Int,
    val pageRenderUri: String?,
    val cropEdges: CropEdges,
    val controlPoints: List<CropControlPoint>,
    val titleText: String,
    val confirmLabel: String,
    val cancelLabel: String
)

/**
 * Crop edges in normalized 0..1 coordinates relative to the image.
 */
internal data class CropEdges(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    fun toCropRect(): CropRect = CropRect(left, top, right, bottom)

    companion object {
        fun fromCropRect(rect: CropRect): CropEdges = CropEdges(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom
        )

        fun default(): CropEdges = CropEdges(0f, 0f, 1f, 1f)
    }
}

/**
 * A draggable control point on the crop boundary.
 */
internal data class CropControlPoint(
    val id: Int,
    val x: Float,
    val y: Float
)

internal fun cropEditControlPoints(edges: CropEdges): List<CropControlPoint> = listOf(
    CropControlPoint(0, edges.left, edges.top),          // top-left corner
    CropControlPoint(1, (edges.left + edges.right) / 2f, edges.top), // top center
    CropControlPoint(2, edges.right, edges.top),          // top-right corner
    CropControlPoint(3, edges.right, (edges.top + edges.bottom) / 2f), // right center
    CropControlPoint(4, edges.right, edges.bottom),       // bottom-right corner
    CropControlPoint(5, (edges.left + edges.right) / 2f, edges.bottom), // bottom center
    CropControlPoint(6, edges.left, edges.bottom),        // bottom-left corner
    CropControlPoint(7, edges.left, (edges.top + edges.bottom) / 2f)  // left center
)

internal fun cropEditRenderModel(
    state: SessionState,
    text: AppTextResolver,
    selectedItemId: String?
): DocumentCropEditRenderModel {
    val batch = state.presentation.documentBatch
    val isDocumentMode = state.activeMode == ModeId.DOCUMENT
    val isCropEdit = selectedItemId != null && batch.status == DocumentBatchStatus.ACTIVE

    if (!isDocumentMode || !isCropEdit || selectedItemId == null) {
        return DocumentCropEditRenderModel(
            visible = false,
            itemId = "",
            pageNumber = 0,
            pageRenderUri = null,
            cropEdges = CropEdges.default(),
            controlPoints = cropEditControlPoints(CropEdges.default()),
            titleText = "",
            confirmLabel = text.get(R.string.document_crop_edit_confirm),
            cancelLabel = text.get(R.string.document_crop_edit_cancel)
        )
    }

    val itemIndex = batch.items.indexOfFirst { it.itemId == selectedItemId }
    if (itemIndex == -1) {
        return DocumentCropEditRenderModel(
            visible = false,
            itemId = selectedItemId,
            pageNumber = 0,
            pageRenderUri = null,
            cropEdges = CropEdges.default(),
            controlPoints = cropEditControlPoints(CropEdges.default()),
            titleText = "",
            confirmLabel = text.get(R.string.document_crop_edit_confirm),
            cancelLabel = text.get(R.string.document_crop_edit_cancel)
        )
    }

    val item = batch.items[itemIndex]
    val edges = item.manualCropRect?.let { CropEdges.fromCropRect(it) } ?: CropEdges.default()

    return DocumentCropEditRenderModel(
        visible = true,
        itemId = item.itemId,
        pageNumber = itemIndex + 1,
        pageRenderUri = item.renderUri ?: item.outputPath,
        cropEdges = edges,
        controlPoints = cropEditControlPoints(edges),
        titleText = String.format(text.get(R.string.document_crop_edit_page_format), itemIndex + 1),
        confirmLabel = text.get(R.string.document_crop_edit_confirm),
        cancelLabel = text.get(R.string.document_crop_edit_cancel)
    )
}
