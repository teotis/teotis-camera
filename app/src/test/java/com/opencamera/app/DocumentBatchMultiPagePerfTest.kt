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
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

class DocumentBatchMultiPagePerfTest {

    private val text = object : TestAppTextResolver() {
        override fun documentBatchPageCount(count: Int): String = "$count pages"
        override fun documentExportProgress(current: Int, total: Int): String = "Exporting $current/$total pages"
    }

    private fun batchItem(index: Int) = DocumentBatchItem(
        itemId = "item-$index",
        shotId = "shot-$index",
        orderIndex = index,
        outputPath = "/images/page-$index.jpg",
        renderUri = "file:///images/page-$index.jpg",
        thumbnailSource = ThumbnailSource.PreviewSnapshot("/images/page-$index.jpg"),
        profileId = "receipt",
        scanMode = "enhanced",
        cropStatus = DocumentBatchCropStatus.NOT_REQUESTED,
        pipelineNotes = emptyList()
    )

    private fun sessionState(pageCount: Int): SessionState {
        val batch = DocumentBatchState(
            batchId = "batch-perf",
            status = DocumentBatchStatus.ACTIVE,
            items = (0 until pageCount).map { batchItem(it) },
            latestItemId = "item-${pageCount - 1}"
        )
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
            presentation = SessionPresentationState(documentBatch = batch)
        )
    }

    @Test
    fun `state migration - batch overview route completes in under 200ms`() {
        val router = CockpitPanelRouter()
        val elapsed = measureTimeMillis {
            repeat(100) {
                router.reduce(CockpitPanelCommand.NavigateToBatchOverview)
                router.reduce(CockpitPanelCommand.CloseBatchOverview)
            }
        }
        assertTrue(
            elapsed < 200,
            "100 batch overview route migrations took ${elapsed}ms, expected < 200ms"
        )
    }

    @Test
    fun `state migration - crop edit route completes in under 200ms`() {
        val router = CockpitPanelRouter()
        val elapsed = measureTimeMillis {
            repeat(100) {
                router.reduce(CockpitPanelCommand.NavigateToBatchOverview)
                router.reduce(CockpitPanelCommand.SelectCropEditItem("item-0"))
                router.reduce(CockpitPanelCommand.NavigateToCropEdit)
                router.reduce(CockpitPanelCommand.CloseCropEdit)
            }
        }
        assertTrue(
            elapsed < 200,
            "100 crop edit route migrations took ${elapsed}ms, expected < 200ms"
        )
    }

    @Test
    fun `state migration - export route completes in under 200ms`() {
        val router = CockpitPanelRouter()
        val elapsed = measureTimeMillis {
            repeat(100) {
                router.reduce(CockpitPanelCommand.NavigateToBatchOverview)
                router.reduce(CockpitPanelCommand.StartExport)
                router.reduce(CockpitPanelCommand.UpdateExportProgress(5, 10))
                router.reduce(CockpitPanelCommand.CompleteExport)
                router.reduce(CockpitPanelCommand.ReturnToShooting)
            }
        }
        assertTrue(
            elapsed < 200,
            "100 export route migrations took ${elapsed}ms, expected < 200ms"
        )
    }

    @Test
    fun `batch organizer render model - 10 pages completes in under 200ms`() {
        val state = sessionState(10)
        val elapsed = measureTimeMillis {
            repeat(100) {
                documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)
            }
        }
        assertTrue(
            elapsed < 200,
            "100 render model computations for 10 pages took ${elapsed}ms, expected < 200ms"
        )
    }

    @Test
    fun `batch organizer render model - 50 pages completes in under 500ms`() {
        val state = sessionState(50)
        val elapsed = measureTimeMillis {
            repeat(100) {
                documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)
            }
        }
        assertTrue(
            elapsed < 500,
            "100 render model computations for 50 pages took ${elapsed}ms, expected < 500ms"
        )
    }

    @Test
    fun `batch rail render model - 10 pages completes in under 200ms`() {
        val state = sessionState(10)
        val elapsed = measureTimeMillis {
            repeat(100) {
                documentBatchRailRenderModel(state, text)
            }
        }
        assertTrue(
            elapsed < 200,
            "100 rail render model computations for 10 pages took ${elapsed}ms, expected < 200ms"
        )
    }

    @Test
    fun `batch rail render model - 50 pages completes in under 500ms`() {
        val state = sessionState(50)
        val elapsed = measureTimeMillis {
            repeat(100) {
                documentBatchRailRenderModel(state, text)
            }
        }
        assertTrue(
            elapsed < 500,
            "100 rail render model computations for 50 pages took ${elapsed}ms, expected < 500ms"
        )
    }

    @Test
    fun `memory pressure - 10 pages render model uses under 2MB`() {
        val state = sessionState(10)
        val runtime = Runtime.getRuntime()
        runtime.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        repeat(1000) {
            documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)
        }
        runtime.gc()
        val after = runtime.totalMemory() - runtime.freeMemory()
        val deltaKB = (after - before) / 1024
        assertTrue(
            deltaKB < 2048,
            "1000 render model computations for 10 pages used ${deltaKB}KB, expected < 2048KB"
        )
    }

    @Test
    fun `memory pressure - 50 pages render model uses under 10MB`() {
        val state = sessionState(50)
        val runtime = Runtime.getRuntime()
        runtime.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        repeat(500) {
            documentBatchOrganizerRenderModel(state, text, CockpitPanelRoute.BatchOverview)
        }
        runtime.gc()
        val after = runtime.totalMemory() - runtime.freeMemory()
        val deltaKB = (after - before) / 1024
        assertTrue(
            deltaKB < 10240,
            "500 render model computations for 50 pages used ${deltaKB}KB, expected < 10240KB"
        )
    }
}
