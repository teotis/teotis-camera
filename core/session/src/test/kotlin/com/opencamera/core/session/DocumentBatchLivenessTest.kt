package com.opencamera.core.session

import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessLivenessDeadline
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ShotTiming
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeIntent
import com.opencamera.core.mode.ModeSessionEvent
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentBatchLivenessTest {

    private class RecordingEffects : MutableSharedFlow<SessionEffect> {
        private val delegate = MutableSharedFlow<SessionEffect>(extraBufferCapacity = 64)
        val recorded = mutableListOf<SessionEffect>()
        override val replayCache get() = delegate.replayCache
        override val subscriptionCount get() = delegate.subscriptionCount
        override suspend fun emit(value: SessionEffect) {
            recorded.add(value)
            delegate.emit(value)
        }
        override fun tryEmit(value: SessionEffect): Boolean {
            recorded.add(value)
            return delegate.tryEmit(value)
        }
        override fun resetReplayCache() { delegate.resetReplayCache() }
        override suspend fun collect(
            collector: kotlinx.coroutines.flow.FlowCollector<SessionEffect>
        ): Nothing = delegate.collect(collector)
    }

    private class FakeModeController(
        override val id: ModeId = ModeId.DOCUMENT,
        private val snapshotFlow: MutableStateFlow<ModeSnapshot> = MutableStateFlow(defaultModeSnapshot)
    ) : ModeController {
        val sessionEvents = mutableListOf<ModeSessionEvent>()
        override val snapshot: StateFlow<ModeSnapshot> = snapshotFlow
        override fun deviceGraph(): DeviceGraphSpec = DeviceGraphSpec.stillCapture()
        override suspend fun onEnter() {}
        override suspend fun onExit() {}
        override suspend fun handle(intent: ModeIntent): ModeSignal = ModeSignal.None
        override suspend fun onSessionEvent(event: ModeSessionEvent) { sessionEvents.add(event) }
    }

    private class Harness(
        initialState: SessionState = documentRunningState(),
        elapsedRealtimeMillis: () -> Long = { 0L }
    ) {
        val testDispatcher = StandardTestDispatcher()
        val scope = kotlinx.coroutines.CoroutineScope(testDispatcher)
        val state = MutableStateFlow(initialState)
        val effects = RecordingEffects()
        val trace = InMemorySessionTrace()
        val modeController = FakeModeController()
        val dispatchedIntents = mutableListOf<SessionIntent>()
        val shotExecutor = com.opencamera.core.media.ShotExecutor()
        val linkRecorder = InMemoryPerformanceLinkRecorder()

        val processor = CaptureRecordingSessionProcessor(
            scope = scope,
            state = state,
            effects = effects,
            trace = trace,
            linkRecorder = linkRecorder,
            shotExecutor = shotExecutor,
            currentController = { modeController },
            resolvedActiveDeviceGraph = { initialState.activeDeviceGraph },
            updateState = SessionStateUpdater { transform ->
                state.value = transform(state.value)
            },
            dispatch = { intent -> dispatchedIntents.add(intent) },
            elapsedRealtimeMillis = elapsedRealtimeMillis
        )

        suspend fun process(intent: SessionIntent) = processor.process(intent)
        fun allEffects(): List<SessionEffect> = effects.recorded.toList()
    }

    companion object {
        private val defaultModeSnapshot = ModeSnapshot(
            id = ModeId.DOCUMENT,
            uiSpec = ModeUiSpec(title = "Document", shutterLabel = "Shutter"),
            state = ModeState(headline = "Ready", detail = "")
        )

        fun documentRunningState() = SessionState(
            lifecycle = SessionLifecycle.RUNNING,
            permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
            previewHostAvailable = true,
            previewStatus = PreviewStatus.ACTIVE,
            previewStatusDetail = null,
            activeMode = ModeId.DOCUMENT,
            availableModes = listOf(ModeId.DOCUMENT),
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = defaultModeSnapshot,
            activeDeviceCapabilities = com.opencamera.core.device.DeviceCapabilities.DEFAULT,
            activeDeviceGraph = DeviceGraphSpec.stillCapture(),
            previewMetrics = PreviewMetrics(),
            presentation = SessionPresentationState(
                documentBatch = DocumentBatchState(
                    batchId = "batch-doc",
                    status = DocumentBatchStatus.ACTIVE
                )
            )
        )

        private fun documentShotRequest(
            shotId: String = "doc-shot-1"
        ) = ShotRequest(
            shotId = shotId,
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )

        private fun documentShotResult(
            shotId: String = "doc-shot-1",
            outputPath: String = "/sdcard/doc.jpg"
        ) = ShotResult(
            shotId = shotId,
            mediaType = MediaType.PHOTO,
            outputPath = outputPath,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.SavedMedia(outputPath),
            metadata = com.opencamera.core.media.MediaMetadata(
                customTags = mapOf("mode" to "document")
            ),
            timing = ShotTiming()
        )
    }

    @Test
    fun `watchdog force-releases activeShot after deadline elapses`() = runTest {
        val harness = Harness()
        val shot = documentShotRequest("doc-shot-1")
        harness.process(SessionIntent.ShotStarted(shot))

        assertNotNull(harness.state.value.activeShot)
        assertNotNull(harness.processor.documentBatchLivenessForTest())

        harness.testDispatcher.scheduler.advanceTimeBy(
            PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS
        )
        harness.testDispatcher.scheduler.runCurrent()

        assertNull(harness.state.value.activeShot)
        assertNull(harness.state.value.presentation.pendingPostprocess)
        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        val batch = harness.state.value.presentation.documentBatch
        assertEquals(1, batch.items.size)
        assertEquals("doc-shot-1", batch.items[0].shotId)
        assertEquals(DocumentBatchCropStatus.FAILED, batch.items[0].cropStatus)
        assertTrue(batch.items[0].pipelineNotes.contains("document:liveness:force-released"))
        assertTrue(
            harness.trace.snapshot().any { it.name == "liveness.document.force-release" }
        )
        assertTrue(harness.processor.forceReleasedShotIdsForTest().contains("doc-shot-1"))
    }

    @Test
    fun `watchdog does not fire when shot completes before deadline`() = runTest {
        val harness = Harness()
        val shot = documentShotRequest("doc-shot-1")
        harness.process(SessionIntent.ShotStarted(shot))
        harness.process(SessionIntent.ShotCompleted(documentShotResult("doc-shot-1")))

        assertNull(harness.state.value.activeShot)
        val batch = harness.state.value.presentation.documentBatch
        assertEquals(1, batch.items.size)
        assertEquals(DocumentBatchCropStatus.NOT_REQUESTED, batch.items[0].cropStatus)
        assertFalse(harness.trace.snapshot().any { it.name == "liveness.document.force-release" })

        harness.testDispatcher.scheduler.advanceTimeBy(
            PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS + 1L
        )
        harness.testDispatcher.scheduler.runCurrent()

        assertFalse(harness.trace.snapshot().any { it.name == "liveness.document.force-release" })
    }

    @Test
    fun `late ShotCompleted after force-release is rejected as stale`() = runTest {
        val harness = Harness()
        val shot = documentShotRequest("doc-shot-1")
        harness.process(SessionIntent.ShotStarted(shot))

        harness.testDispatcher.scheduler.advanceTimeBy(
            PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS
        )
        harness.testDispatcher.scheduler.runCurrent()

        val batchBeforeLate = harness.state.value.presentation.documentBatch
        assertEquals(1, batchBeforeLate.items.size)
        assertEquals(DocumentBatchCropStatus.FAILED, batchBeforeLate.items[0].cropStatus)

        harness.process(SessionIntent.ShotCompleted(documentShotResult("doc-shot-1")))

        val batch = harness.state.value.presentation.documentBatch
        assertEquals(1, batch.items.size)
        assertEquals(DocumentBatchCropStatus.FAILED, batch.items[0].cropStatus)
        assertTrue(
            harness.trace.snapshot().any { it.name == "shot.completed.force-released.stale" }
        )
    }

    @Test
    fun `next RequestCapture is admitted after force-release`() = runTest {
        val harness = Harness()
        val shot = documentShotRequest("doc-shot-1")
        harness.process(SessionIntent.ShotStarted(shot))

        harness.testDispatcher.scheduler.advanceTimeBy(
            PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS
        )
        harness.testDispatcher.scheduler.runCurrent()

        assertNull(harness.state.value.activeShot)
        val snapshot = SessionCommandAdmissionSnapshot(
            countdownInProgress = false,
            countdownRemainingSeconds = null,
            activeShot = harness.state.value.activeShot,
            recordingStatus = harness.state.value.recordingStatus
        )
        val admission = SessionCommandAdmission.evaluate(
            SessionCommandKind.MODE_SWITCH,
            snapshot
        )
        assertEquals(SessionCommandAdmissionResult.Allowed, admission)

        harness.processor.submitCaptureStrategy(CaptureStrategy.SingleFrame())
        val effect = harness.allEffects().lastOrNull()
        assertTrue(effect is SessionEffect.ExecuteShot)
    }

    @Test
    fun `expired deadline in handleDataReceived triggers force-release`() = runTest {
        var clock = 0L
        val harness = Harness(elapsedRealtimeMillis = { clock })
        val shot = documentShotRequest("doc-shot-1")
        harness.process(SessionIntent.ShotStarted(shot))

        clock += PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS + 1L

        harness.process(SessionIntent.DataReceived("doc-shot-1", MediaType.PHOTO))

        assertNull(harness.state.value.activeShot)
        assertNull(harness.state.value.presentation.pendingPostprocess)
        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        val batch = harness.state.value.presentation.documentBatch
        assertEquals(1, batch.items.size)
        assertEquals(DocumentBatchCropStatus.FAILED, batch.items[0].cropStatus)
        assertTrue(
            harness.trace.snapshot().any { it.name == "liveness.document.force-release" }
        )
    }

    @Test
    fun `expired deadline in handleShotCompleted triggers force-release instead of normal completion`() = runTest {
        var clock = 0L
        val harness = Harness(elapsedRealtimeMillis = { clock })
        val shot = documentShotRequest("doc-shot-1")
        harness.process(SessionIntent.ShotStarted(shot))

        clock += PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS + 1L

        harness.process(SessionIntent.ShotCompleted(documentShotResult("doc-shot-1")))

        val batch = harness.state.value.presentation.documentBatch
        assertEquals(1, batch.items.size)
        assertEquals(DocumentBatchCropStatus.FAILED, batch.items[0].cropStatus)
        assertNull(batch.items[0].outputPath)
        assertTrue(
            harness.trace.snapshot().any { it.name == "liveness.document.force-release" }
        )
    }

    @Test
    fun `expired deadline in handleShotFailed triggers force-release`() = runTest {
        var clock = 0L
        val harness = Harness(elapsedRealtimeMillis = { clock })
        val shot = documentShotRequest("doc-shot-1")
        harness.process(SessionIntent.ShotStarted(shot))

        clock += PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS + 1L

        harness.process(SessionIntent.ShotFailed("doc-shot-1", MediaType.PHOTO, "postprocess stuck"))

        assertNull(harness.state.value.activeShot)
        val batch = harness.state.value.presentation.documentBatch
        assertEquals(1, batch.items.size)
        assertEquals(DocumentBatchCropStatus.FAILED, batch.items[0].cropStatus)
        assertTrue(
            harness.trace.snapshot().any { it.name == "liveness.document.force-release" }
        )
    }

    @Test
    fun `normal document photo completion path is preserved`() = runTest {
        val harness = Harness()
        val shot = documentShotRequest("doc-shot-1")
        harness.process(SessionIntent.ShotStarted(shot))
        harness.process(SessionIntent.ShotCompleted(
            documentShotResult("doc-shot-1").copy(
                pipelineNotes = listOf("document:auto-crop:applied")
            )
        ))

        val batch = harness.state.value.presentation.documentBatch
        assertEquals(1, batch.items.size)
        assertEquals(DocumentBatchCropStatus.APPLIED, batch.items[0].cropStatus)
        assertEquals("/sdcard/doc.jpg", batch.items[0].outputPath)
        assertFalse(harness.trace.snapshot().any { it.name == "liveness.document.force-release" })
    }

    @Test
    fun `watchdog not started for non-document mode`() = runTest {
        val harness = Harness(documentRunningState().copy(activeMode = ModeId.PHOTO))
        val shot = documentShotRequest("photo-shot-1")
        harness.process(SessionIntent.ShotStarted(shot))

        assertNull(harness.processor.documentBatchLivenessForTest())

        harness.testDispatcher.scheduler.advanceTimeBy(
            PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS + 1L
        )
        harness.testDispatcher.scheduler.runCurrent()

        assertFalse(harness.trace.snapshot().any { it.name == "liveness.document.force-release" })
    }

    @Test
    fun `watchdog not started for video shot in document mode`() = runTest {
        val harness = Harness()
        val shot = ShotRequest(
            shotId = "doc-video-1",
            shotKind = ShotKind.VIDEO_RECORDING,
            mediaType = MediaType.VIDEO,
            saveRequest = SaveRequest.videoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.KEEP_PREVIEW_FRAME,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
        harness.process(SessionIntent.ShotStarted(shot))

        assertNull(harness.processor.documentBatchLivenessForTest())
    }

    @Test
    fun `force-release preserves existing batch items and appends failed item`() = runTest {
        val existingItem = DocumentBatchItem(
            itemId = "doc-shot-0",
            shotId = "doc-shot-0",
            orderIndex = 0,
            outputPath = "/sdcard/doc0.jpg",
            renderUri = null,
            thumbnailSource = ThumbnailSource.SavedMedia("/sdcard/doc0.jpg"),
            profileId = null,
            scanMode = null,
            cropStatus = DocumentBatchCropStatus.APPLIED,
            pipelineNotes = listOf("document:auto-crop:applied")
        )
        val initialState = documentRunningState().copy(
            presentation = documentRunningState().presentation.copy(
                documentBatch = DocumentBatchState(
                    batchId = "batch-doc",
                    status = DocumentBatchStatus.ACTIVE,
                    items = listOf(existingItem),
                    latestItemId = "doc-shot-0"
                )
            )
        )
        val harness = Harness(initialState)
        val shot = documentShotRequest("doc-shot-1")
        harness.process(SessionIntent.ShotStarted(shot))

        harness.testDispatcher.scheduler.advanceTimeBy(
            PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS
        )
        harness.testDispatcher.scheduler.runCurrent()

        val batch = harness.state.value.presentation.documentBatch
        assertEquals(2, batch.items.size)
        assertEquals("doc-shot-0", batch.items[0].shotId)
        assertEquals(DocumentBatchCropStatus.APPLIED, batch.items[0].cropStatus)
        assertEquals("doc-shot-1", batch.items[1].shotId)
        assertEquals(DocumentBatchCropStatus.FAILED, batch.items[1].cropStatus)
        assertEquals(1, batch.items[1].orderIndex)
    }

    @Test
    fun `liveness event diagnostic string is recorded with itemId`() = runTest {
        val harness = Harness()
        val shot = documentShotRequest("doc-shot-1")
        harness.process(SessionIntent.ShotStarted(shot))

        harness.testDispatcher.scheduler.advanceTimeBy(
            PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS
        )
        harness.testDispatcher.scheduler.runCurrent()

        val traceEvent = harness.trace.snapshot().first { it.name == "liveness.document.force-release" }
        assertTrue(traceEvent.detail.contains("force-released-from-document-batch"))
        assertTrue(traceEvent.detail.contains("shotId=doc-shot-1"))
        assertTrue(traceEvent.detail.contains("itemId=doc-shot-1"))
        assertTrue(traceEvent.detail.contains("stage=DOCUMENT_BATCH"))
    }
}
