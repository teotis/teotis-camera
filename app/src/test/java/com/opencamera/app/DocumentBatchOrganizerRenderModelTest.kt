package com.opencamera.app

import com.opencamera.app.i18n.AppTextResolver
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.session.CaptureStatus
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
import kotlin.test.assertTrue

class DocumentBatchOrganizerRenderModelTest {

    private val text = object : TestAppTextResolver() {
        override fun documentBatchPageCount(count: Int): String = "$count pages"
    }

    private fun batchItem(
        itemId: String,
        orderIndex: Int,
        outputPath: String? = "/images/$itemId.jpg",
        renderUri: String? = outputPath,
        thumbnailSource: ThumbnailSource = ThumbnailSource.PreviewSnapshot(outputPath ?: "/images/$itemId.jpg"),
        cropStatus: DocumentBatchCropStatus = DocumentBatchCropStatus.NOT_REQUESTED
    ) = DocumentBatchItem(
        itemId = itemId,
        shotId = "shot-$itemId",
        orderIndex = orderIndex,
        outputPath = outputPath,
        renderUri = renderUri,
        thumbnailSource = thumbnailSource,
        profileId = "receipt",
        scanMode = "enhanced",
        cropStatus = cropStatus,
        pipelineNotes = emptyList()
    )

    @Test
    fun `organizer item order follows session batch order`() {
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

        val model = documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)

        assertTrue(model.visible)
        assertEquals(3, model.items.size)
        assertEquals("item-1", model.items[0].itemId)
        assertEquals(1, model.items[0].pageNumber)
        assertEquals("item-2", model.items[1].itemId)
        assertEquals(2, model.items[1].pageNumber)
        assertEquals("item-3", model.items[2].itemId)
        assertEquals(3, model.items[2].pageNumber)
    }

    @Test
    fun `first item cannot move up`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                batchItem("item-1", 0),
                batchItem("item-2", 1)
            )
        )
        val state = documentBatchSessionState(batchState)

        val model = documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)

        assertFalse(model.items[0].canMoveUp, "First item should not be able to move up")
        assertTrue(model.items[1].canMoveUp, "Second item should be able to move up")
    }

    @Test
    fun `last item cannot move down`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                batchItem("item-1", 0),
                batchItem("item-2", 1)
            )
        )
        val state = documentBatchSessionState(batchState)

        val model = documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)

        assertTrue(model.items[0].canMoveDown, "First item should be able to move down")
        assertFalse(model.items[1].canMoveDown, "Last item should not be able to move down")
    }

    @Test
    fun `outside document mode organizer is hidden`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                batchItem("item-1", 0)
            )
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

        val model = documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)

        assertFalse(model.visible, "Organizer should be hidden outside document mode")
        assertTrue(model.items.isEmpty(), "Items should be empty when hidden")
    }

    @Test
    fun `empty batch shows zero count in document mode`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = emptyList()
        )
        val state = documentBatchSessionState(batchState)

        val model = documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)

        assertTrue(model.visible, "Organizer should be visible in document mode even with empty batch")
        assertTrue(model.items.isEmpty())
        assertEquals("0 pages", model.countText)
    }

    @Test
    fun `single item can neither move up nor down`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                batchItem("only", 0)
            )
        )
        val state = documentBatchSessionState(batchState)

        val model = documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)

        assertEquals(1, model.items.size)
        assertFalse(model.items[0].canMoveUp, "Single item should not move up")
        assertFalse(model.items[0].canMoveDown, "Single item should not move down")
    }

    @Test
    fun `item render model uses renderUri from batch item`() {
        val outputPath = "/images/doc_page.jpg"
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                batchItem("item-1", 0, outputPath = outputPath, renderUri = outputPath)
            )
        )
        val state = documentBatchSessionState(batchState)

        val model = documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)

        assertEquals(1, model.items.size)
        assertEquals(outputPath, model.items[0].renderUri)
    }

    @Test
    fun `item page numbers are sequential from 1 regardless of orderIndex`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                batchItem("a", 0),
                batchItem("b", 1),
                batchItem("c", 2),
                batchItem("d", 3),
                batchItem("e", 4)
            )
        )
        val state = documentBatchSessionState(batchState)

        val model = documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)

        assertEquals(5, model.items.size)
        assertEquals("5 pages", model.countText)
        for (i in 0 until 5) {
            assertEquals(i + 1, model.items[i].pageNumber, "Page number at index $i should be ${i + 1}")
        }
    }

    @Test
    fun `inactive batch is hidden even in document mode`() {
        val state = documentBatchSessionState(DocumentBatchState.inactive())

        val model = documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)

        assertFalse(model.visible, "Inactive batch should be hidden")
        assertTrue(model.items.isEmpty())
    }

    // --- BatchOverview-specific tests ---

    @Test
    fun `batch overview mode is active when route is BatchOverview`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(batchItem("item-1", 0))
        )
        val state = documentBatchSessionState(batchState)

        val model = documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)

        assertTrue(model.visible)
        assertTrue(model.isBatchOverviewMode)
    }

    @Test
    fun `organizer hidden when route is None even with active batch`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(batchItem("item-1", 0))
        )
        val state = documentBatchSessionState(batchState)

        val model = documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.None)

        assertFalse(model.visible, "Organizer should be hidden when route is None")
        assertFalse(model.isBatchOverviewMode)
    }

    @Test
    fun `continue shooting button visible in batch overview mode`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(batchItem("item-1", 0))
        )
        val state = documentBatchSessionState(batchState)

        val model = documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)

        assertTrue(model.showContinueShooting)
    }

    @Test
    fun `export button visible when batch has items`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(batchItem("item-1", 0))
        )
        val state = documentBatchSessionState(batchState)

        val model = documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)

        assertTrue(model.showExport)
    }

    @Test
    fun `export button hidden when batch is empty`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = emptyList()
        )
        val state = documentBatchSessionState(batchState)

        val model = documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)

        assertFalse(model.showExport, "Export button should be hidden for empty batch")
        assertTrue(model.showContinueShooting)
    }

    @Test
    fun `empty hint text is set in batch overview mode`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = emptyList()
        )
        val state = documentBatchSessionState(batchState)

        val model = documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)

        assertTrue(model.emptyHint.isNotEmpty())
    }

    @Test
    fun `items do not expose crop edit labels because document mode keeps originals`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                batchItem("item-1", 0),
                batchItem("item-2", 1)
            )
        )
        val state = documentBatchSessionState(batchState)

        val model = documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)

        assertTrue(model.items.all { it.cropEditLabel == null })
    }

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
}
