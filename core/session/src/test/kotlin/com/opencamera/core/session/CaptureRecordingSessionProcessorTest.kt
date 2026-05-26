package com.opencamera.core.session

import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.MediaType
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
class CaptureRecordingSessionProcessorTest {

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
        override val id: ModeId = ModeId.PHOTO,
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

    private class Harness(initialState: SessionState = runningState()) {
        val testDispatcher = StandardTestDispatcher()
        val scope = kotlinx.coroutines.CoroutineScope(testDispatcher)
        val state = MutableStateFlow(initialState)
        val effects = RecordingEffects()
        val trace = InMemorySessionTrace()
        val modeController = FakeModeController()
        val dispatchedIntents = mutableListOf<SessionIntent>()
        val shotExecutor = com.opencamera.core.media.ShotExecutor()

        val processor = CaptureRecordingSessionProcessor(
            scope = scope,
            state = state,
            effects = effects,
            trace = trace,
            shotExecutor = shotExecutor,
            currentController = { modeController },
            resolvedActiveDeviceGraph = { initialState.activeDeviceGraph },
            updateState = SessionStateUpdater { transform ->
                state.value = transform(state.value)
            },
            dispatch = { intent -> dispatchedIntents.add(intent) }
        )

        suspend fun process(intent: SessionIntent) = processor.process(intent)
        fun allEffects(): List<SessionEffect> = effects.recorded.toList()
        fun lastEffect(): SessionEffect? = effects.recorded.lastOrNull()
    }

    companion object {
        private val defaultModeSnapshot = ModeSnapshot(
            id = ModeId.PHOTO,
            uiSpec = ModeUiSpec(title = "Photo", shutterLabel = "Shutter"),
            state = ModeState(headline = "Ready", detail = "")
        )

        fun runningState() = SessionState(
            lifecycle = SessionLifecycle.RUNNING,
            permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
            previewHostAvailable = true,
            previewStatus = PreviewStatus.ACTIVE,
            previewStatusDetail = null,
            activeMode = ModeId.PHOTO,
            availableModes = listOf(ModeId.PHOTO),
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = defaultModeSnapshot,
            activeDeviceCapabilities = com.opencamera.core.device.DeviceCapabilities.DEFAULT,
            activeDeviceGraph = DeviceGraphSpec.stillCapture(),
            previewMetrics = PreviewMetrics()
        )

        private fun testShotRequest(
            shotId: String = "shot-1",
            mediaType: MediaType = MediaType.PHOTO
        ) = ShotRequest(
            shotId = shotId,
            shotKind = if (mediaType == MediaType.PHOTO) ShotKind.STILL_CAPTURE
            else ShotKind.VIDEO_RECORDING,
            mediaType = mediaType,
            saveRequest = if (mediaType == MediaType.PHOTO) SaveRequest.photoLibrary()
            else SaveRequest.videoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.KEEP_PREVIEW_FRAME,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )

        private fun testShotResult(
            shotId: String = "shot-1",
            mediaType: MediaType = MediaType.PHOTO,
            outputPath: String = "/sdcard/photo.jpg"
        ) = ShotResult(
            shotId = shotId,
            mediaType = mediaType,
            outputPath = outputPath,
            saveRequest = if (mediaType == MediaType.PHOTO) SaveRequest.photoLibrary()
            else SaveRequest.videoLibrary(),
            thumbnailSource = ThumbnailSource.SavedMedia(outputPath),
            metadata = com.opencamera.core.media.MediaMetadata(),
            timing = ShotTiming()
        )
    }

    // ── Countdown Tests ─────────────────────────────────────────────────

    @Test
    fun `startCaptureCountdown sets countdown state`() = runTest {
        val harness = Harness()
        harness.processor.startCaptureCountdown(CaptureStrategy.SingleFrame(), countdownSeconds = 3)

        assertEquals(CaptureStatus.REQUESTED, harness.state.value.captureStatus)
        assertEquals(3, harness.state.value.presentation.countdownRemainingSeconds)
        assertTrue(harness.processor.countdownInProgress())
        assertTrue(harness.trace.snapshot().any { it.name == "capture.countdown.started" })
    }

    @Test
    fun `cancelPendingCountdown clears countdown state`() = runTest {
        val harness = Harness()
        harness.processor.startCaptureCountdown(CaptureStrategy.SingleFrame(), countdownSeconds = 5)
        harness.processor.cancelPendingCountdown("user cancelled")

        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertNull(harness.state.value.presentation.countdownRemainingSeconds)
        assertFalse(harness.processor.countdownInProgress())
        assertTrue(harness.trace.snapshot().any { it.name == "capture.countdown.cancelled" })
    }

    @Test
    fun `handleCountdownTick updates remaining seconds`() = runTest {
        val harness = Harness()
        harness.processor.startCaptureCountdown(CaptureStrategy.SingleFrame(), countdownSeconds = 3)
        harness.process(SessionIntent.CountdownTick(2))

        assertEquals(2, harness.state.value.presentation.countdownRemainingSeconds)
        assertEquals("Photo capture starts in 2s", harness.state.value.presentation.lastAction)
    }

    @Test
    fun `handleCountdownTick ignored when no countdown active`() = runTest {
        val harness = Harness()
        val previousLastAction = harness.state.value.presentation.lastAction
        harness.process(SessionIntent.CountdownTick(1))
        assertEquals(previousLastAction, harness.state.value.presentation.lastAction)
    }

    @Test
    fun `handleCountdownCompleted submits strategy and clears countdown`() = runTest {
        val harness = Harness()
        harness.processor.startCaptureCountdown(CaptureStrategy.SingleFrame(), countdownSeconds = 2)
        harness.process(SessionIntent.CountdownCompleted)

        assertFalse(harness.processor.countdownInProgress())
        assertNull(harness.state.value.presentation.countdownRemainingSeconds)
        assertEquals(CaptureStatus.REQUESTED, harness.state.value.captureStatus)
    }

    @Test
    fun `handleCountdownCompleted ignored when no pending strategy`() = runTest {
        val harness = Harness()
        harness.process(SessionIntent.CountdownCompleted)
        assertFalse(harness.processor.countdownInProgress())
    }

    // ── Shot Lifecycle Tests ────────────────────────────────────────────

    @Test
    fun `handleShotStarted updates state for photo capture`() = runTest {
        val harness = Harness()
        val shot = testShotRequest("shot-1", MediaType.PHOTO)
        harness.process(SessionIntent.ShotStarted(shot))

        assertEquals(CaptureStatus.SAVING, harness.state.value.captureStatus)
        assertEquals(RecordingStatus.IDLE, harness.state.value.recordingStatus)
        assertEquals(shot, harness.state.value.activeShot)
        assertEquals("Saving captured photo", harness.state.value.presentation.lastAction)
        assertTrue(harness.modeController.sessionEvents.any { it is ModeSessionEvent.ShotStarted })
    }

    @Test
    fun `handleShotStarted updates state for video recording`() = runTest {
        val harness = Harness()
        val shot = testShotRequest("shot-v", MediaType.VIDEO)
        harness.process(SessionIntent.ShotStarted(shot))

        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertEquals(RecordingStatus.RECORDING, harness.state.value.recordingStatus)
        assertEquals(shot, harness.state.value.activeShot)
        assertEquals("Video recording started", harness.state.value.presentation.lastAction)
    }

    @Test
    fun `handleShotCompleted updates state for photo`() = runTest {
        val harness = Harness(runningState().copy(
            activeShot = testShotRequest("shot-1", MediaType.PHOTO)
        ))
        harness.process(SessionIntent.ShotCompleted(testShotResult("shot-1", MediaType.PHOTO)))

        assertEquals(CaptureStatus.COMPLETED, harness.state.value.captureStatus)
        assertEquals(RecordingStatus.IDLE, harness.state.value.recordingStatus)
        assertNull(harness.state.value.activeShot)
        assertEquals("Photo saved", harness.state.value.presentation.lastAction)
        assertEquals("/sdcard/photo.jpg", harness.state.value.presentation.latestCapturePath)
        assertTrue(harness.modeController.sessionEvents.any { it is ModeSessionEvent.ShotCompleted })
    }

    @Test
    fun `handleShotCompleted updates thumbnail source for saved media`() = runTest {
        val harness = Harness(runningState().copy(
            activeShot = testShotRequest("shot-1", MediaType.PHOTO)
        ))
        harness.process(SessionIntent.ShotCompleted(testShotResult("shot-1", MediaType.PHOTO)))
        assertTrue(harness.state.value.presentation.latestThumbnailSource is ThumbnailSource.SavedMedia)
    }

    @Test
    fun `handleShotCompleted retains previous thumbnail when result is None`() = runTest {
        val previousSource = ThumbnailSource.SavedMedia("/prev.jpg")
        val harness = Harness(runningState().copy(
            activeShot = testShotRequest("shot-1", MediaType.PHOTO),
            presentation = SessionPresentationState(latestThumbnailSource = previousSource)
        ))
        harness.process(SessionIntent.ShotCompleted(
            testShotResult("shot-1", MediaType.PHOTO).copy(thumbnailSource = ThumbnailSource.None)
        ))
        assertEquals(previousSource, harness.state.value.presentation.latestThumbnailSource)
    }

    @Test
    fun `handleShotFailed updates state for matching active shot`() = runTest {
        val shot = testShotRequest("shot-1", MediaType.PHOTO)
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.process(SessionIntent.ShotFailed("shot-1", MediaType.PHOTO, "camera error"))

        assertEquals(CaptureStatus.FAILED, harness.state.value.captureStatus)
        assertEquals(RecordingStatus.IDLE, harness.state.value.recordingStatus)
        assertNull(harness.state.value.activeShot)
        assertEquals("Photo capture failed", harness.state.value.presentation.lastAction)
        assertEquals("camera error", harness.state.value.presentation.lastError)
    }

    @Test
    fun `handleShotFailed ignored when no active shot`() = runTest {
        val harness = Harness(runningState().copy(activeShot = null))
        harness.process(SessionIntent.ShotFailed("shot-x", MediaType.PHOTO, "error"))
        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
    }

    @Test
    fun `handleShotFailed ignored when shotId mismatches active shot`() = runTest {
        val shot = testShotRequest("shot-1", MediaType.PHOTO)
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.process(SessionIntent.ShotFailed("shot-2", MediaType.PHOTO, "error"))

        assertNotNull(harness.state.value.activeShot)
        assertEquals("shot-1", harness.state.value.activeShot?.shotId)
    }

    @Test
    fun `handleDataReceived transitions capture status for photo`() = runTest {
        val shot = testShotRequest("shot-1", MediaType.PHOTO)
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.process(SessionIntent.DataReceived("shot-1", MediaType.PHOTO))

        assertEquals(CaptureStatus.DATA_RECEIVED, harness.state.value.captureStatus)
        assertEquals(shot, harness.state.value.activeShot)
    }

    @Test
    fun `SAVING to DATA_RECEIVED to COMPLETED lifecycle`() = runTest {
        val harness = Harness()
        val shot = testShotRequest("shot-1", MediaType.PHOTO)

        harness.process(SessionIntent.ShotStarted(shot))
        assertEquals(CaptureStatus.SAVING, harness.state.value.captureStatus)

        harness.process(SessionIntent.DataReceived("shot-1", MediaType.PHOTO))
        assertEquals(CaptureStatus.DATA_RECEIVED, harness.state.value.captureStatus)
        assertNotNull(harness.state.value.activeShot)

        harness.process(SessionIntent.ShotCompleted(testShotResult("shot-1", MediaType.PHOTO)))
        assertEquals(CaptureStatus.COMPLETED, harness.state.value.captureStatus)
        assertNull(harness.state.value.activeShot)
    }

    @Test
    fun `handleShotCompleted for video sets recording status to IDLE`() = runTest {
        val harness = Harness(runningState().copy(
            activeShot = testShotRequest("shot-v", MediaType.VIDEO),
            recordingStatus = RecordingStatus.RECORDING
        ))
        harness.process(SessionIntent.ShotCompleted(
            testShotResult("shot-v", MediaType.VIDEO, "/sdcard/video.mp4"))
        )

        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertEquals(RecordingStatus.IDLE, harness.state.value.recordingStatus)
        assertEquals("Video saved", harness.state.value.presentation.lastAction)
    }

    // ── Capture Strategy Tests ──────────────────────────────────────────

    @Test
    fun `submitCaptureStrategy emits ExecuteShot effect and updates state`() = runTest {
        val harness = Harness()
        harness.processor.submitCaptureStrategy(CaptureStrategy.SingleFrame())

        val effect = harness.lastEffect()
        assertTrue(effect is SessionEffect.ExecuteShot)
        assertEquals(CaptureStatus.REQUESTED, harness.state.value.captureStatus)
    }

    @Test
    fun `submitCaptureStrategy for video starts with REQUESTING status`() = runTest {
        val harness = Harness()
        harness.processor.submitCaptureStrategy(CaptureStrategy.VideoRecording())

        assertEquals(RecordingStatus.REQUESTING, harness.state.value.recordingStatus)
        assertNotNull(harness.state.value.activeShot)
    }

    @Test
    fun `submitCaptureStrategy handles plan failure due to active shot`() = runTest {
        val existingShot = testShotRequest("existing", MediaType.PHOTO)
        val harness = Harness(runningState().copy(activeShot = existingShot))
        harness.processor.submitCaptureStrategy(CaptureStrategy.SingleFrame())

        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertNotNull(harness.state.value.presentation.lastError)
        assertTrue(harness.trace.snapshot().any { it.name == "shot.plan.failed" })
    }

    // ── Recording Watchdog Tests ────────────────────────────────────────
    // NOTE: The watchdog currently launches on Dispatchers.Default (not the
    // injected scope), so timeout-based tests with StandardTestDispatcher
    // cannot control its timing. Full timeout tests should be added after
    // P6 fix moves watchdog to the injected scope.

    @Test
    fun `startRecordingWatchdog starts without error`() = runTest {
        val harness = Harness(runningState().copy(
            recordingStatus = RecordingStatus.REQUESTING
        ))
        harness.processor.startRecordingWatchdog(RecordingStatus.REQUESTING, timeoutMillis = 5_000L)
        // Watchdog started successfully — state is unchanged until timeout
        assertEquals(RecordingStatus.REQUESTING, harness.state.value.recordingStatus)
    }

    @Test
    fun `starting new watchdog cancels previous without error`() = runTest {
        val harness = Harness(runningState().copy(
            recordingStatus = RecordingStatus.REQUESTING
        ))
        harness.processor.startRecordingWatchdog(RecordingStatus.REQUESTING, timeoutMillis = 5_000L)
        // Start a second watchdog — should cancel the first without error
        harness.processor.startRecordingWatchdog(RecordingStatus.STOPPING, timeoutMillis = 10_000L)
        // No crash = success
    }

    // ── Interrupted Shot Tests ──────────────────────────────────────────

    @Test
    fun `handleInterruptedShotFailure notifies controller and traces`() = runTest {
        val harness = Harness()
        val shot = testShotRequest("shot-1", MediaType.PHOTO)
        harness.processor.handleInterruptedShotFailure(shot, "permission lost")

        assertTrue(harness.modeController.sessionEvents.any { event ->
            event is ModeSessionEvent.ShotFailed &&
                event.shotId == "shot-1" &&
                event.reason == "permission lost"
        })
        assertTrue(harness.trace.snapshot().any { it.name == "capture.failed" })
    }

    // ── Error handling test ─────────────────────────────────────────────

    @Test
    fun `process throws on unexpected intent type`() = runTest {
        val harness = Harness()
        var threw = false
        try {
            harness.process(SessionIntent.Boot)
        } catch (_: IllegalStateException) {
            threw = true
        }
        assertTrue(threw)
    }

    // ── Document Crop Status Parsing ──────────────────────────────────

    @Test
    fun `documentCropStatusFrom returns APPLIED for applied note`() {
        val notes = listOf("document:auto-crop:applied", "document:auto-crop:bounds=10,20,300,400")
        assertEquals(DocumentBatchCropStatus.APPLIED, documentCropStatusFrom(notes))
    }

    @Test
    fun `documentCropStatusFrom returns SKIPPED for skipped note`() {
        val notes = listOf("document:auto-crop:skipped:unsupported-mime")
        assertEquals(DocumentBatchCropStatus.SKIPPED, documentCropStatusFrom(notes))
    }

    @Test
    fun `documentCropStatusFrom returns FAILED for failed note`() {
        val notes = listOf("document:auto-crop:failed:decode-failed")
        assertEquals(DocumentBatchCropStatus.FAILED, documentCropStatusFrom(notes))
    }

    @Test
    fun `documentCropStatusFrom returns NOT_REQUESTED for empty notes`() {
        assertEquals(DocumentBatchCropStatus.NOT_REQUESTED, documentCropStatusFrom(emptyList()))
    }

    @Test
    fun `documentCropStatusFrom returns NOT_REQUESTED when no crop notes present`() {
        val notes = listOf("some-other-note", "another-note")
        assertEquals(DocumentBatchCropStatus.NOT_REQUESTED, documentCropStatusFrom(notes))
    }

    @Test
    fun `documentCropStatusFrom prefers APPLIED over later notes`() {
        val notes = listOf("document:auto-crop:applied", "document:auto-crop:skipped:reason")
        assertEquals(DocumentBatchCropStatus.APPLIED, documentCropStatusFrom(notes))
    }

    // ── Document Batch Capture Integration ────────────────────────────

    @Test
    fun `document photo ShotCompleted appends batch item with metadata`() = runTest {
        val documentState = runningState().copy(
            activeMode = ModeId.DOCUMENT,
            presentation = SessionPresentationState(
                documentBatch = DocumentBatchState(
                    batchId = "batch-1",
                    status = DocumentBatchStatus.ACTIVE
                )
            )
        )
        val harness = Harness(documentState)
        val result = testShotResult("doc-shot-1", MediaType.PHOTO).copy(
            metadata = com.opencamera.core.media.MediaMetadata(
                customTags = mapOf(
                    "mode" to "document",
                    "profile" to "receipt",
                    "scanMode" to "enhanced"
                )
            ),
            pipelineNotes = listOf("document:auto-crop:applied")
        )
        harness.process(SessionIntent.ShotCompleted(result))

        val batch = harness.state.value.presentation.documentBatch
        assertEquals(1, batch.items.size)
        assertEquals("doc-shot-1", batch.items[0].shotId)
        assertEquals("receipt", batch.items[0].profileId)
        assertEquals("enhanced", batch.items[0].scanMode)
        assertEquals(DocumentBatchCropStatus.APPLIED, batch.items[0].cropStatus)
        assertEquals("doc-shot-1", batch.latestItemId)
        assertTrue(batch.lastMessage?.contains("auto-cropped") == true)
    }

    @Test
    fun `video ShotCompleted does not append to document batch`() = runTest {
        val documentState = runningState().copy(
            activeMode = ModeId.DOCUMENT,
            recordingStatus = RecordingStatus.RECORDING,
            presentation = SessionPresentationState(
                documentBatch = DocumentBatchState(
                    batchId = "batch-1",
                    status = DocumentBatchStatus.ACTIVE
                )
            )
        )
        val harness = Harness(documentState)
        val result = testShotResult("video-1", MediaType.VIDEO, "/sdcard/video.mp4")
        harness.process(SessionIntent.ShotCompleted(result))

        val batch = harness.state.value.presentation.documentBatch
        assertEquals(0, batch.items.size)
    }
}
