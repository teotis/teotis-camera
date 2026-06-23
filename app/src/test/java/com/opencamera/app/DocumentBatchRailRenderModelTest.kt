package com.opencamera.app

import androidx.annotation.StringRes
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentBatchRailRenderModelTest {

    private val text = object : TestAppTextResolver() {
        internal override fun get(@StringRes resId: Int): String = when (resId) {
            R.string.document_batch_remove -> "Remove"
            R.string.document_batch_crop_applied -> "Cropped"
            R.string.document_batch_crop_skipped -> "Not cropped"
            R.string.document_batch_crop_failed -> "Crop failed"
            R.string.document_batch_rail_overview_description -> "查看批次"
            else -> super.get(resId)
        }

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
    fun `hidden outside document mode`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(batchItem("item-1", 0))
        )
        val state = sessionState(ModeId.PHOTO, batchState)

        val model = documentBatchRailRenderModel(state, text)

        assertFalse(model.visible)
        assertTrue(model.items.isEmpty())
        assertEquals("", model.countText)
    }

    @Test
    fun `hidden for inactive batch`() {
        val state = sessionState(ModeId.DOCUMENT, DocumentBatchState.inactive())

        val model = documentBatchRailRenderModel(state, text)

        assertFalse(model.visible)
        assertTrue(model.items.isEmpty())
    }

    @Test
    fun `hidden for empty active batch`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = emptyList()
        )
        val state = sessionState(ModeId.DOCUMENT, batchState)

        val model = documentBatchRailRenderModel(state, text)

        assertFalse(model.visible, "Rail should be hidden when batch is empty")
        assertTrue(model.items.isEmpty())
    }

    @Test
    fun `visible for active document batch with items`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(batchItem("item-1", 0))
        )
        val state = sessionState(ModeId.DOCUMENT, batchState)

        val model = documentBatchRailRenderModel(state, text)

        assertTrue(model.visible)
        assertEquals(1, model.items.size)
        assertTrue(model.organizeEnabled)
    }

    @Test
    fun `count text equals item count`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                batchItem("item-1", 0),
                batchItem("item-2", 1),
                batchItem("item-3", 2)
            )
        )
        val state = sessionState(ModeId.DOCUMENT, batchState)

        val model = documentBatchRailRenderModel(state, text)

        assertEquals("3 pages", model.countText)
    }

    @Test
    fun `item order follows documentBatch items`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                batchItem("item-3", 2),
                batchItem("item-1", 0),
                batchItem("item-2", 1)
            )
        )
        val state = sessionState(ModeId.DOCUMENT, batchState)

        val model = documentBatchRailRenderModel(state, text)

        assertEquals(3, model.items.size)
        assertEquals("item-1", model.items[0].itemId)
        assertEquals(1, model.items[0].pageNumber)
        assertEquals("item-2", model.items[1].itemId)
        assertEquals(2, model.items[1].pageNumber)
        assertEquals("item-3", model.items[2].itemId)
        assertEquals(3, model.items[2].pageNumber)
    }

    @Test
    fun `latest item is marked`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                batchItem("item-1", 0),
                batchItem("item-2", 1)
            ),
            latestItemId = "item-2"
        )
        val state = sessionState(ModeId.DOCUMENT, batchState)

        val model = documentBatchRailRenderModel(state, text)

        assertFalse(model.items[0].isLatest)
        assertTrue(model.items[1].isLatest)
        assertEquals("item-2", model.latestItemId)
    }

    @Test
    fun `rail items expose vertical reorder affordances`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                batchItem("item-1", 0),
                batchItem("item-2", 1),
                batchItem("item-3", 2)
            ),
            latestItemId = "item-2"
        )
        val state = sessionState(ModeId.DOCUMENT, batchState)

        val model = documentBatchRailRenderModel(state, text, CockpitPanelRoute.None)

        assertFalse(model.items[0].canMoveUp)
        assertTrue(model.items[0].canMoveDown)
        assertTrue(model.items[1].canMoveUp)
        assertTrue(model.items[1].canMoveDown)
        assertTrue(model.items[2].canMoveUp)
        assertFalse(model.items[2].canMoveDown)
        assertEquals("Move up", model.moveUpLabel)
        assertEquals("Move down", model.moveDownLabel)
    }

    @Test
    fun `crop status labels match text resolver`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                batchItem("item-1", 0, cropStatus = DocumentBatchCropStatus.NOT_REQUESTED),
                batchItem("item-2", 1, cropStatus = DocumentBatchCropStatus.APPLIED),
                batchItem("item-3", 2, cropStatus = DocumentBatchCropStatus.SKIPPED),
                batchItem("item-4", 3, cropStatus = DocumentBatchCropStatus.FAILED)
            )
        )
        val state = sessionState(ModeId.DOCUMENT, batchState)

        val model = documentBatchRailRenderModel(state, text)

        assertNull(model.items[0].statusLabel)
        assertEquals("Cropped", model.items[1].statusLabel)
        assertEquals("Not cropped", model.items[2].statusLabel)
        assertEquals("Crop failed", model.items[3].statusLabel)
    }

    @Test
    fun `item without render URI gets placeholder-safe model`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                batchItem(
                    "item-1", 0,
                    outputPath = null,
                    renderUri = null,
                    thumbnailSource = ThumbnailSource.None
                )
            )
        )
        val state = sessionState(ModeId.DOCUMENT, batchState)

        val model = documentBatchRailRenderModel(state, text)

        assertEquals(1, model.items.size)
        assertNull(model.items[0].renderUri)
    }

    @Test
    fun `remove content description uses text resolver`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(batchItem("item-1", 0))
        )
        val state = sessionState(ModeId.DOCUMENT, batchState)

        val model = documentBatchRailRenderModel(state, text)

        assertEquals("Remove", model.items[0].removeContentDescription)
    }

    @Test
    fun `finished batch is hidden`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.FINISHED,
            items = listOf(batchItem("item-1", 0))
        )
        val state = sessionState(ModeId.DOCUMENT, batchState)

        val model = documentBatchRailRenderModel(state, text)

        assertFalse(model.visible, "Finished batch should be hidden")
    }

    @Test
    fun `shooting mode shows slim rail with count and latest thumbnail`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                batchItem("item-1", 0, renderUri = "/images/item-1.jpg"),
                batchItem("item-2", 1, renderUri = "/images/item-2.jpg")
            ),
            latestItemId = "item-2"
        )
        val state = sessionState(ModeId.DOCUMENT, batchState)

        val model = documentBatchRailRenderModel(state, text, CockpitPanelRoute.None)

        assertTrue(model.visible)
        assertTrue(model.isSlimShooting)
        assertEquals("2 pages", model.countText)
        assertEquals("/images/item-2.jpg", model.latestThumbnailUri)
        assertEquals("查看批次", model.overviewLabel)
    }

    @Test
    fun `non-shooting mode hides rail`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(batchItem("item-1", 0))
        )
        val state = sessionState(ModeId.DOCUMENT, batchState)

        val model = documentBatchRailRenderModel(state, text, CockpitPanelRoute.BatchOverview)

        assertFalse(model.visible, "Rail should be hidden when a panel is open")
        assertFalse(model.isSlimShooting)
    }

    @Test
    fun `shooting mode with no latest item still visible`() {
        val batchState = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(batchItem("item-1", 0, renderUri = null)),
            latestItemId = null
        )
        val state = sessionState(ModeId.DOCUMENT, batchState)

        val model = documentBatchRailRenderModel(state, text, CockpitPanelRoute.None)

        assertTrue(model.visible)
        assertTrue(model.isSlimShooting)
        assertNull(model.latestThumbnailUri)
    }

    private fun sessionState(mode: ModeId, batchState: DocumentBatchState): SessionState {
        return SessionState(
            lifecycle = SessionLifecycle.RUNNING,
            permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
            previewHostAvailable = true,
            previewStatus = PreviewStatus.ACTIVE,
            previewStatusDetail = null,
            activeMode = mode,
            availableModes = listOf(ModeId.PHOTO, ModeId.DOCUMENT),
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = ModeSnapshot(
                id = mode,
                uiSpec = ModeUiSpec(title = mode.name, shutterLabel = "Capture"),
                state = ModeState(headline = "${mode.name} mode", detail = "Ready")
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
