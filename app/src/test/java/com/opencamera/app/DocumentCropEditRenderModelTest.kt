package com.opencamera.app

import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.CropRect
import com.opencamera.core.session.DocumentBatchCropStatus
import com.opencamera.core.session.DocumentBatchItem
import com.opencamera.core.session.DocumentBatchState
import com.opencamera.core.session.DocumentBatchStatus
import com.opencamera.core.session.PermissionState
import com.opencamera.core.session.PreviewMetrics
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SessionLifecycle
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionState
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.SessionSettingsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DocumentCropEditRenderModelTest {

    private val text = object : TestAppTextResolver() {
        override fun documentBatchPageCount(count: Int): String = "$count pages"
    }

    private fun batchItem(
        itemId: String,
        orderIndex: Int,
        outputPath: String? = "/images/$itemId.jpg",
        renderUri: String? = outputPath,
        cropStatus: DocumentBatchCropStatus = DocumentBatchCropStatus.NOT_REQUESTED,
        manualCropRect: CropRect? = null
    ) = DocumentBatchItem(
        itemId = itemId,
        shotId = "shot-$itemId",
        orderIndex = orderIndex,
        outputPath = outputPath,
        renderUri = renderUri,
        thumbnailSource = ThumbnailSource.PreviewSnapshot(outputPath ?: "/images/$itemId.jpg"),
        profileId = "receipt",
        scanMode = "enhanced",
        cropStatus = cropStatus,
        pipelineNotes = emptyList(),
        manualCropRect = manualCropRect
    )

    private fun documentBatchSessionState(batchState: DocumentBatchState): SessionState {
        return SessionState(
            lifecycle = SessionLifecycle.RUNNING,
            permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
            previewHostAvailable = true,
            previewStatus = PreviewStatus.ACTIVE,
            previewStatusDetail = null,
            activeMode = ModeId.DOCUMENT,
            availableModes = listOf(ModeId.PHOTO, ModeId.DOCUMENT),
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = ModeSnapshot(
                id = ModeId.DOCUMENT,
                uiSpec = ModeUiSpec(title = "Document", shutterLabel = "Scan Document"),
                state = ModeState(headline = "Document scan active", detail = "Ready")
            ),
            activeDeviceCapabilities = com.opencamera.core.device.DeviceCapabilities.DEFAULT,
            activeDeviceGraph = com.opencamera.core.device.DeviceGraphSpec.stillCapture(
                preferredLensFacing = com.opencamera.core.device.LensFacing.BACK,
                enablePreviewSnapshots = true
            ),
            previewMetrics = PreviewMetrics(),
            settings = SessionSettingsSnapshot(persisted = PersistedSettings()),
            presentation = SessionPresentationState(documentBatch = batchState)
        )
    }

    @Test
    fun `overlay hidden when no item selected`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(batchItem("item-1", 0))
        )
        val state = documentBatchSessionState(batchState)

        val model = cropEditRenderModel(state, text, null)

        assertFalse(model.visible)
        assertEquals("", model.itemId)
        assertEquals(0, model.pageNumber)
    }

    @Test
    fun `overlay visible when item selected and batch active`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                batchItem("item-1", 0),
                batchItem("item-2", 1)
            )
        )
        val state = documentBatchSessionState(batchState)

        val model = cropEditRenderModel(state, text, "item-2")

        assertTrue(model.visible)
        assertEquals("item-2", model.itemId)
        assertEquals(2, model.pageNumber)
        assertNotNull(model.pageRenderUri)
    }

    @Test
    fun `default edges cover full image when no manual crop`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(batchItem("item-1", 0))
        )
        val state = documentBatchSessionState(batchState)

        val model = cropEditRenderModel(state, text, "item-1")

        assertEquals(0f, model.cropEdges.left)
        assertEquals(0f, model.cropEdges.top)
        assertEquals(1f, model.cropEdges.right)
        assertEquals(1f, model.cropEdges.bottom)
    }

    @Test
    fun `existing manual crop rect is used for edges`() {
        val cropRect = CropRect(left = 0.1f, top = 0.15f, right = 0.9f, bottom = 0.85f)
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(batchItem("item-1", 0, manualCropRect = cropRect))
        )
        val state = documentBatchSessionState(batchState)

        val model = cropEditRenderModel(state, text, "item-1")

        assertEquals(0.1f, model.cropEdges.left)
        assertEquals(0.15f, model.cropEdges.top)
        assertEquals(0.9f, model.cropEdges.right)
        assertEquals(0.85f, model.cropEdges.bottom)
    }

    @Test
    fun `eight control points generated from edges`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(batchItem("item-1", 0))
        )
        val state = documentBatchSessionState(batchState)

        val model = cropEditRenderModel(state, text, "item-1")

        assertEquals(8, model.controlPoints.size)
    }

    @Test
    fun `confirm and cancel labels are present`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(batchItem("item-1", 0))
        )
        val state = documentBatchSessionState(batchState)

        val model = cropEditRenderModel(state, text, "item-1")

        assertTrue(model.confirmLabel.isNotEmpty())
        assertTrue(model.cancelLabel.isNotEmpty())
    }

    @Test
    fun `overlay hidden for non-existent item ID`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(batchItem("item-1", 0))
        )
        val state = documentBatchSessionState(batchState)

        val model = cropEditRenderModel(state, text, "item-999")

        assertFalse(model.visible)
    }

    @Test
    fun `overlay hidden for inactive batch`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.FINISHED,
            items = listOf(batchItem("item-1", 0))
        )
        val state = documentBatchSessionState(batchState)

        val model = cropEditRenderModel(state, text, "item-1")

        assertFalse(model.visible, "Should be hidden for non-active batch")
    }

    @Test
    fun `overlay hidden outside document mode`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(batchItem("item-1", 0))
        )
        val state = SessionState(
            lifecycle = SessionLifecycle.RUNNING,
            permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
            previewHostAvailable = true,
            previewStatus = PreviewStatus.ACTIVE,
            previewStatusDetail = null,
            activeMode = ModeId.PHOTO,
            availableModes = listOf(ModeId.PHOTO, ModeId.DOCUMENT),
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = ModeSnapshot(
                id = ModeId.PHOTO,
                uiSpec = ModeUiSpec(title = "PHOTO", shutterLabel = "Capture"),
                state = ModeState(headline = "PHOTO mode", detail = "Ready")
            ),
            activeDeviceCapabilities = com.opencamera.core.device.DeviceCapabilities.DEFAULT,
            activeDeviceGraph = com.opencamera.core.device.DeviceGraphSpec.stillCapture(
                preferredLensFacing = com.opencamera.core.device.LensFacing.BACK,
                enablePreviewSnapshots = true
            ),
            previewMetrics = PreviewMetrics(),
            settings = SessionSettingsSnapshot(persisted = PersistedSettings()),
            presentation = SessionPresentationState(documentBatch = batchState)
        )

        val model = cropEditRenderModel(state, text, "item-1")

        assertFalse(model.visible, "Should be hidden outside document mode")
    }

    @Test
    fun `page number matches item position in batch`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                batchItem("item-1", 0),
                batchItem("item-2", 1),
                batchItem("item-3", 2)
            )
        )
        val state = documentBatchSessionState(batchState)

        val model = cropEditRenderModel(state, text, "item-3")

        assertEquals(3, model.pageNumber)
    }

    @Test
    fun `cropEdges default companion creates full-image edges`() {
        val edges = CropEdges.default()
        assertEquals(0f, edges.left)
        assertEquals(0f, edges.top)
        assertEquals(1f, edges.right)
        assertEquals(1f, edges.bottom)
    }

    @Test
    fun `CropEdges toCropRect roundtrips correctly`() {
        val edges = CropEdges(left = 0.1f, top = 0.2f, right = 0.8f, bottom = 0.9f)
        val rect = edges.toCropRect()
        val restored = CropEdges.fromCropRect(rect)

        assertEquals(edges.left, restored.left)
        assertEquals(edges.top, restored.top)
        assertEquals(edges.right, restored.right)
        assertEquals(edges.bottom, restored.bottom)
    }
}
