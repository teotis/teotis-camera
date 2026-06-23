package com.opencamera.core.session

import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessFailure
import com.opencamera.core.media.PostProcessFailureCause
import com.opencamera.core.media.PostProcessFailureDisposition
import com.opencamera.core.media.PostProcessFailureStage
import com.opencamera.core.media.PostProcessOutputIntegrity
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

        private fun multiFrameShotRequest(
            shotId: String = "shot-mf-1"
        ) = ShotRequest(
            shotId = shotId,
            shotKind = ShotKind.MULTI_FRAME_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile(frameCount = 3)
        )

        private fun livePhotoShotRequest(
            shotId: String = "shot-live-1"
        ) = ShotRequest(
            shotId = shotId,
            shotKind = ShotKind.LIVE_PHOTO,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile(),
            livePhotoSpec = com.opencamera.core.media.LivePhotoCaptureSpec()
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

        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
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
    fun `handleShotStarted clears shutterPressedAtElapsedMillis`() = runTest {
        val harness = Harness(runningState().copy(
            shutterPressedAtElapsedMillis = 12345L,
            captureStatus = CaptureStatus.REQUESTED
        ))
        val shot = testShotRequest("shot-1", MediaType.PHOTO)
        harness.process(SessionIntent.ShotStarted(shot))

        assertNull(harness.state.value.shutterPressedAtElapsedMillis)
        assertEquals(CaptureStatus.SAVING, harness.state.value.captureStatus)
    }

    // ── G1 Shutter-to-Device Latency ──────────────────────────────────

    @Test
    fun `handleShotCompleted records shutter-to-device link event for photo`() = runTest {
        val harness = Harness(runningState().copy(
            activeShot = testShotRequest("shot-g1", MediaType.PHOTO)
        ))
        val result = testShotResult("shot-g1", MediaType.PHOTO).copy(
            timing = ShotTiming(
                requestedAtElapsedMillis = 1000L,
                deviceCaptureStartedAtElapsedMillis = 1150L,
                deviceCaptureCompletedAtElapsedMillis = 1300L,
                postProcessCompletedAtElapsedMillis = 1500L
            )
        )
        harness.process(SessionIntent.ShotCompleted(result))

        val linkEvents = harness.linkRecorder.snapshot()
        val shutterEvent = linkEvents.find {
            it.flow == "capture" && it.stage == "shutter-to-device"
        }
        assertNotNull(shutterEvent, "Expected capture/shutter-to-device link event")
        assertEquals("capture", shutterEvent.flow)
        assertEquals("shutter-to-device", shutterEvent.stage)
        assertEquals(LinkEventStatus.COMPLETED, shutterEvent.status)
        assertEquals(150L, shutterEvent.durationMillis)
        assertEquals(1000L, shutterEvent.startElapsedMillis)
        assertEquals(1150L, shutterEvent.endElapsedMillis)
        assertEquals("shot-g1", shutterEvent.correlationId)
        assertTrue(harness.trace.snapshot().any { it.name == "capture.shutter.to.device" })
    }

    @Test
    fun `handleDataReceived for ordinary still releases activeShot`() = runTest {
        val shot = testShotRequest("shot-1", MediaType.PHOTO)
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.process(SessionIntent.DataReceived("shot-1", MediaType.PHOTO))

        assertEquals(CaptureStatus.DATA_RECEIVED, harness.state.value.captureStatus)
        assertNull(harness.state.value.activeShot)
    }

    @Test
    fun `SAVING to DATA_RECEIVED to COMPLETED lifecycle for ordinary still`() = runTest {
        val harness = Harness()
        val shot = testShotRequest("shot-1", MediaType.PHOTO)

        harness.process(SessionIntent.ShotStarted(shot))
        assertEquals(CaptureStatus.SAVING, harness.state.value.captureStatus)

        harness.process(SessionIntent.DataReceived("shot-1", MediaType.PHOTO))
        assertEquals(CaptureStatus.DATA_RECEIVED, harness.state.value.captureStatus)
        assertNull(harness.state.value.activeShot)

        harness.process(SessionIntent.ShotCompleted(testShotResult("shot-1", MediaType.PHOTO)))
        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertNull(harness.state.value.activeShot)
    }

    @Test
    fun `handleDataReceived for multi-frame keeps activeShot blocked`() = runTest {
        val shot = multiFrameShotRequest("shot-mf-1")
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.process(SessionIntent.DataReceived("shot-mf-1", MediaType.PHOTO))

        assertEquals(CaptureStatus.DATA_RECEIVED, harness.state.value.captureStatus)
        assertEquals(shot, harness.state.value.activeShot)
    }

    @Test
    fun `handleDataReceived for live photo keeps activeShot blocked`() = runTest {
        val shot = livePhotoShotRequest("shot-live-1")
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.process(SessionIntent.DataReceived("shot-live-1", MediaType.PHOTO))

        assertEquals(CaptureStatus.DATA_RECEIVED, harness.state.value.captureStatus)
        assertEquals(shot, harness.state.value.activeShot)
    }

    @Test
    fun `multi-frame DataReceived then ShotCompleted clears activeShot and updates presentation`() = runTest {
        val shot = multiFrameShotRequest("shot-mf-1")
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.process(SessionIntent.DataReceived("shot-mf-1", MediaType.PHOTO))
        assertNotNull(harness.state.value.activeShot)

        harness.process(SessionIntent.ShotCompleted(testShotResult("shot-mf-1", MediaType.PHOTO)))
        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertNull(harness.state.value.activeShot)
        assertEquals("/sdcard/photo.jpg", harness.state.value.presentation.latestCapturePath)
        assertEquals("Photo saved", harness.state.value.presentation.lastAction)
    }

    @Test
    fun `ordinary still early rearm then ShotCompleted still updates presentation`() = runTest {
        val harness = Harness()
        val shot = testShotRequest("shot-1", MediaType.PHOTO)
        harness.process(SessionIntent.ShotStarted(shot))
        harness.process(SessionIntent.DataReceived("shot-1", MediaType.PHOTO))
        assertNull(harness.state.value.activeShot)

        harness.process(SessionIntent.ShotCompleted(testShotResult("shot-1", MediaType.PHOTO)))
        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertNull(harness.state.value.activeShot)
        assertEquals("/sdcard/photo.jpg", harness.state.value.presentation.latestCapturePath)
        assertEquals("Photo saved", harness.state.value.presentation.lastAction)
        assertTrue(harness.state.value.presentation.latestThumbnailSource is ThumbnailSource.SavedMedia)
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

    @Test
    fun `video DataReceived does not affect activeShot or capture status`() = runTest {
        val shot = testShotRequest("shot-v", MediaType.VIDEO)
        val harness = Harness(runningState().copy(
            activeShot = shot,
            recordingStatus = RecordingStatus.RECORDING
        ))
        harness.process(SessionIntent.DataReceived("shot-v", MediaType.VIDEO))

        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertEquals(shot, harness.state.value.activeShot)
        assertEquals(RecordingStatus.RECORDING, harness.state.value.recordingStatus)
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

    @Test
    fun `startRecordingWatchdog times out with injected virtual time`() = runTest {
        val harness = Harness(runningState().copy(
            activeShot = testShotRequest("shot-video", MediaType.VIDEO),
            recordingStatus = RecordingStatus.REQUESTING
        ))
        harness.processor.startRecordingWatchdog(RecordingStatus.REQUESTING, timeoutMillis = 5_000L)

        harness.testDispatcher.scheduler.advanceTimeBy(5_000L)
        harness.testDispatcher.scheduler.runCurrent()

        assertEquals(RecordingStatus.IDLE, harness.state.value.recordingStatus)
        assertNull(harness.state.value.activeShot)
        assertEquals("Recording state REQUESTING timed out", harness.state.value.presentation.lastError)
        assertTrue(harness.trace.snapshot().any { it.name == "recording.watchdog.timeout" })
    }

    @Test
    fun `startRecordingWatchdog leaves state unchanged before timeout`() = runTest {
        val harness = Harness(runningState().copy(
            recordingStatus = RecordingStatus.REQUESTING
        ))
        harness.processor.startRecordingWatchdog(RecordingStatus.REQUESTING, timeoutMillis = 5_000L)

        harness.testDispatcher.scheduler.advanceTimeBy(4_999L)
        harness.testDispatcher.scheduler.runCurrent()

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

    // ── Pending Postprocess UI State Tests ────────────────────────────

    @Test
    fun `handleDataReceived for ordinary still sets pendingPostprocess`() = runTest {
        val shot = testShotRequest("shot-1", MediaType.PHOTO)
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.process(SessionIntent.DataReceived("shot-1", MediaType.PHOTO))

        val pending = harness.state.value.presentation.pendingPostprocess
        assertNotNull(pending)
        assertEquals("shot-1", pending.shotId)
        assertEquals(MediaType.PHOTO, pending.mediaType)
        assertTrue(pending.warnBeforeExit)
        assertNull(harness.state.value.activeShot)
    }

    @Test
    fun `pendingPostprocess cleared after ShotCompleted`() = runTest {
        val harness = Harness()
        val shot = testShotRequest("shot-1", MediaType.PHOTO)
        harness.process(SessionIntent.ShotStarted(shot))
        harness.process(SessionIntent.DataReceived("shot-1", MediaType.PHOTO))
        assertNotNull(harness.state.value.presentation.pendingPostprocess)

        harness.process(SessionIntent.ShotCompleted(testShotResult("shot-1", MediaType.PHOTO)))
        assertNull(harness.state.value.presentation.pendingPostprocess)
        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
    }

    @Test
    fun `pendingPostprocess cleared after ShotFailed with matching activeShot`() = runTest {
        val shot = testShotRequest("shot-1", MediaType.PHOTO)
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.process(SessionIntent.DataReceived("shot-1", MediaType.PHOTO))
        assertNotNull(harness.state.value.presentation.pendingPostprocess)

        harness.process(SessionIntent.ShotFailed("shot-1", MediaType.PHOTO, "camera error"))
        assertNull(harness.state.value.presentation.pendingPostprocess)
    }

    @Test
    fun `pendingPostprocess cleared after ShotFailed for orphaned rearmed shot`() = runTest {
        // Simulate: ordinary still rearmed (activeShot = null, pendingPostprocess set),
        // then a late ShotFailed arrives.
        val initialState = runningState().copy(
            captureStatus = CaptureStatus.DATA_RECEIVED,
            activeShot = null,
            presentation = SessionPresentationState(
                pendingPostprocess = PendingPostprocessUiState(
                    shotId = "shot-1",
                    mediaType = MediaType.PHOTO,
                    message = "",
                    warnBeforeExit = true
                )
            )
        )
        val harness = Harness(initialState)
        assertNotNull(harness.state.value.presentation.pendingPostprocess)

        harness.process(SessionIntent.ShotFailed("shot-1", MediaType.PHOTO, "postprocess error"))
        assertNull(harness.state.value.presentation.pendingPostprocess)
        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertTrue(harness.state.value.presentation.lastError == "postprocess error")
        assertTrue(harness.trace.snapshot().any { it.name == "shot.failed.orphaned" })
    }

    @Test
    fun `multi-frame DataReceived does not set pendingPostprocess`() = runTest {
        val shot = multiFrameShotRequest("shot-mf-1")
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.process(SessionIntent.DataReceived("shot-mf-1", MediaType.PHOTO))

        assertEquals(CaptureStatus.DATA_RECEIVED, harness.state.value.captureStatus)
        assertEquals(shot, harness.state.value.activeShot)
        assertNull(harness.state.value.presentation.pendingPostprocess)
    }

    @Test
    fun `live photo DataReceived does not set pendingPostprocess`() = runTest {
        val shot = livePhotoShotRequest("shot-live-1")
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.process(SessionIntent.DataReceived("shot-live-1", MediaType.PHOTO))

        assertEquals(CaptureStatus.DATA_RECEIVED, harness.state.value.captureStatus)
        assertEquals(shot, harness.state.value.activeShot)
        assertNull(harness.state.value.presentation.pendingPostprocess)
    }

    @Test
    fun `second ordinary still DataReceived overwrites pendingPostprocess`() = runTest {
        val harness = Harness()
        // First shot
        val shot1 = testShotRequest("shot-1", MediaType.PHOTO)
        harness.process(SessionIntent.ShotStarted(shot1))
        harness.process(SessionIntent.DataReceived("shot-1", MediaType.PHOTO))
        assertEquals("shot-1", harness.state.value.presentation.pendingPostprocess?.shotId)

        // Second shot - should overwrite
        harness.state.value = harness.state.value.copy(
            captureStatus = CaptureStatus.IDLE,
            activeShot = testShotRequest("shot-2", MediaType.PHOTO)
        )
        harness.process(SessionIntent.DataReceived("shot-2", MediaType.PHOTO))
        val pending = harness.state.value.presentation.pendingPostprocess
        assertNotNull(pending)
        assertEquals("shot-2", pending.shotId)
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

    // ── DFS-01: Orphaned ShotFailed regression tests ─────────────────

    @Test
    fun `DFS-01 orphaned ShotFailed in DATA_RECEIVED resets captureStatus to IDLE`() = runTest {
        val initialState = runningState().copy(
            captureStatus = CaptureStatus.DATA_RECEIVED,
            activeShot = null,
            presentation = SessionPresentationState(
                pendingPostprocess = PendingPostprocessUiState(
                    shotId = "shot-1",
                    mediaType = MediaType.PHOTO,
                    message = "",
                    warnBeforeExit = true
                )
            )
        )
        val harness = Harness(initialState)

        harness.process(SessionIntent.ShotFailed("shot-1", MediaType.PHOTO, "postprocess timeout"))
        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertNull(harness.state.value.presentation.pendingPostprocess)
        assertEquals("postprocess timeout", harness.state.value.presentation.lastError)
        assertTrue(harness.trace.snapshot().any { it.name == "shot.failed.orphaned" })
    }

    @Test
    fun `DFS-01 orphaned ShotFailed in COMPLETED resets captureStatus to IDLE`() = runTest {
        val initialState = runningState().copy(
            captureStatus = CaptureStatus.COMPLETED,
            activeShot = null
        )
        val harness = Harness(initialState)

        harness.process(SessionIntent.ShotFailed("shot-1", MediaType.PHOTO, "late error"))
        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertEquals("late error", harness.state.value.presentation.lastError)
    }

    @Test
    fun `DFS-01 orphaned ShotFailed with no pending and IDLE status is no-op`() = runTest {
        val harness = Harness(runningState().copy(
            captureStatus = CaptureStatus.IDLE,
            activeShot = null
        ))

        harness.process(SessionIntent.ShotFailed("shot-1", MediaType.PHOTO, "error"))
        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
    }

    // ── DFS-02: Stale ShotCompleted guard test ───────────────────────

    @Test
    fun `DFS-02 stale ShotCompleted for different active shot is rejected`() = runTest {
        val activeShot2 = testShotRequest("shot-2", MediaType.PHOTO)
        val harness = Harness(runningState().copy(
            activeShot = activeShot2,
            captureStatus = CaptureStatus.SAVING
        ))
        val staleResult = testShotResult("shot-1", MediaType.PHOTO, "/sdcard/stale.jpg")

        harness.process(SessionIntent.ShotCompleted(staleResult))

        // State must not change - stale completion is rejected
        assertEquals(CaptureStatus.SAVING, harness.state.value.captureStatus)
        assertEquals(activeShot2, harness.state.value.activeShot)
        assertNull(harness.state.value.presentation.latestCapturePath)
        assertTrue(harness.trace.snapshot().any { it.name == "shot.completed.stale" })
    }

    @Test
    fun `DFS-02 valid ShotCompleted when activeShot matches is applied`() = runTest {
        val shot = testShotRequest("shot-1", MediaType.PHOTO)
        val harness = Harness(runningState().copy(
            activeShot = shot,
            captureStatus = CaptureStatus.SAVING
        ))

        harness.process(SessionIntent.ShotCompleted(testShotResult("shot-1", MediaType.PHOTO)))
        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertNull(harness.state.value.activeShot)
        assertEquals("/sdcard/photo.jpg", harness.state.value.presentation.latestCapturePath)
        assertFalse(harness.trace.snapshot().any { it.name == "shot.completed.stale" })
    }

    @Test
    fun `DFS-02 valid ShotCompleted when activeShot is null is applied`() = runTest {
        val harness = Harness(runningState().copy(activeShot = null))

        harness.process(SessionIntent.ShotCompleted(testShotResult("shot-1", MediaType.PHOTO)))
        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertEquals("/sdcard/photo.jpg", harness.state.value.presentation.latestCapturePath)
    }

    // ── DFS-13: COMPLETED to IDLE transition test ────────────────────

    @Test
    fun `DFS-13 photo ShotCompleted transitions to IDLE after presentation update`() = runTest {
        val harness = Harness(runningState().copy(
            activeShot = testShotRequest("shot-1", MediaType.PHOTO)
        ))
        harness.process(SessionIntent.ShotCompleted(testShotResult("shot-1", MediaType.PHOTO)))

        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertEquals("Photo saved", harness.state.value.presentation.lastAction)
        assertEquals("/sdcard/photo.jpg", harness.state.value.presentation.latestCapturePath)
    }

    // ── DFS-14: Interrupted shot failure clears activeShot ──────────

    @Test
    fun `DFS-14 handleInterruptedShotFailure clears matching activeShot`() = runTest {
        val shot = testShotRequest("shot-1", MediaType.PHOTO)
        val harness = Harness(runningState().copy(
            activeShot = shot,
            captureStatus = CaptureStatus.SAVING,
            recordingStatus = RecordingStatus.IDLE
        ))
        harness.processor.handleInterruptedShotFailure(shot, "mode switched")

        assertNull(harness.state.value.activeShot)
        assertEquals(CaptureStatus.FAILED, harness.state.value.captureStatus)
        assertEquals(RecordingStatus.IDLE, harness.state.value.recordingStatus)
        assertEquals("mode switched", harness.state.value.presentation.lastError)
        assertEquals("Photo capture interrupted", harness.state.value.presentation.lastAction)
        assertTrue(harness.modeController.sessionEvents.any { event ->
            event is ModeSessionEvent.ShotFailed &&
                event.shotId == "shot-1" &&
                event.reason == "mode switched"
        })
        assertTrue(harness.trace.snapshot().any { it.name == "capture.failed" })
    }

    @Test
    fun `DFS-14 handleInterruptedShotFailure does not clear non-matching activeShot`() = runTest {
        val otherShot = testShotRequest("shot-2", MediaType.PHOTO)
        val harness = Harness(runningState().copy(
            activeShot = otherShot,
            captureStatus = CaptureStatus.SAVING
        ))
        val interruptedShot = testShotRequest("shot-1", MediaType.PHOTO)
        harness.processor.handleInterruptedShotFailure(interruptedShot, "mode switched")

        assertNotNull(harness.state.value.activeShot)
        assertEquals("shot-2", harness.state.value.activeShot?.shotId)
        assertEquals(CaptureStatus.SAVING, harness.state.value.captureStatus)
        assertTrue(harness.modeController.sessionEvents.any { event ->
            event is ModeSessionEvent.ShotFailed && event.shotId == "shot-1"
        })
    }

    // ── Document batch liveness reducer branch (package 04) ──────────

    @Test
    fun `document mode ShotStarted arms liveness watchdog`() = runTest {
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
        val shot = testShotRequest("doc-shot-1", MediaType.PHOTO)
        harness.process(SessionIntent.ShotStarted(shot))

        assertNotNull(harness.processor.documentBatchLivenessForTest())
        assertEquals("doc-shot-1", harness.processor.documentBatchLivenessForTest()?.shotId)
    }

    @Test
    fun `document mode watchdog fires and marks batch item FAILED`() = runTest {
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
        val shot = testShotRequest("doc-shot-1", MediaType.PHOTO)
        harness.process(SessionIntent.ShotStarted(shot))

        harness.testDispatcher.scheduler.advanceTimeBy(
            com.opencamera.core.media.PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS
        )
        harness.testDispatcher.scheduler.runCurrent()

        assertNull(harness.state.value.activeShot)
        assertNull(harness.state.value.presentation.pendingPostprocess)
        val batch = harness.state.value.presentation.documentBatch
        assertEquals(1, batch.items.size)
        assertEquals(DocumentBatchCropStatus.FAILED, batch.items[0].cropStatus)
        assertTrue(
            harness.trace.snapshot().any { it.name == "liveness.document.force-release" }
        )
    }

    @Test
    fun `document batch normal auto-crop path unchanged when watchdog does not fire`() = runTest {
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
        val shot = testShotRequest("doc-shot-1", MediaType.PHOTO)
        harness.process(SessionIntent.ShotStarted(shot))
        harness.process(SessionIntent.ShotCompleted(
            testShotResult("doc-shot-1", MediaType.PHOTO).copy(
                pipelineNotes = listOf("document:auto-crop:applied")
            )
        ))

        val batch = harness.state.value.presentation.documentBatch
        assertEquals(1, batch.items.size)
        assertEquals(DocumentBatchCropStatus.APPLIED, batch.items[0].cropStatus)
        assertEquals("doc-shot-1", batch.latestItemId)
        assertFalse(harness.trace.snapshot().any { it.name == "liveness.document.force-release" })
        assertNull(harness.processor.documentBatchLivenessForTest())
    }

    // ── Package 05: Post-process liveness watchdog (non-document shots) ──

    @Test
    fun `post-process liveness armed for ordinary still capture`() = runTest {
        val harness = Harness()
        val shot = testShotRequest("shot-pl-1", MediaType.PHOTO)
        harness.process(SessionIntent.ShotStarted(shot))

        assertNotNull(harness.processor.postProcessLivenessForTest())
        assertEquals("shot-pl-1", harness.processor.postProcessLivenessForTest()?.shotId)
        assertNotNull(harness.processor.postProcessLivenessConfigSnapshotForTest())
    }

    @Test
    fun `post-process liveness not armed for document mode`() = runTest {
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
        val shot = testShotRequest("doc-shot-pl", MediaType.PHOTO)
        harness.process(SessionIntent.ShotStarted(shot))

        assertNull(harness.processor.postProcessLivenessForTest())
        assertNotNull(harness.processor.documentBatchLivenessForTest())
    }

    @Test
    fun `post-process liveness not armed for video`() = runTest {
        val harness = Harness()
        val shot = testShotRequest("shot-v-pl", MediaType.VIDEO)
        harness.process(SessionIntent.ShotStarted(shot))

        assertNull(harness.processor.postProcessLivenessForTest())
    }

    @Test
    fun `ordinary still watchdog force-releases pendingPostprocess after deadline`() = runTest {
        val harness = Harness()
        val shot = testShotRequest("shot-pl-2", MediaType.PHOTO)
        harness.process(SessionIntent.ShotStarted(shot))
        harness.process(SessionIntent.DataReceived("shot-pl-2", MediaType.PHOTO))
        assertNotNull(harness.state.value.presentation.pendingPostprocess)

        harness.testDispatcher.scheduler.advanceTimeBy(
            PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS
        )
        harness.testDispatcher.scheduler.runCurrent()

        assertNull(harness.state.value.presentation.pendingPostprocess)
        assertNull(harness.state.value.presentation.captureReadiness)
        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertTrue(
            harness.trace.snapshot().any { it.name == "liveness.session.deadline-expired" }
        )
        assertTrue(
            harness.trace.snapshot().any { it.name == "liveness.session.release" }
        )
    }

    @Test
    fun `ordinary still watchdog does not fire when ShotCompleted arrives in time`() = runTest {
        val harness = Harness()
        val shot = testShotRequest("shot-pl-3", MediaType.PHOTO)
        harness.process(SessionIntent.ShotStarted(shot))
        harness.process(SessionIntent.DataReceived("shot-pl-3", MediaType.PHOTO))
        harness.process(SessionIntent.ShotCompleted(testShotResult("shot-pl-3", MediaType.PHOTO)))

        harness.testDispatcher.scheduler.advanceTimeBy(
            PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS
        )
        harness.testDispatcher.scheduler.runCurrent()

        assertFalse(
            harness.trace.snapshot().any { it.name == "liveness.session.deadline-expired" }
        )
        assertNull(harness.processor.postProcessLivenessForTest())
    }

    @Test
    fun `ShotCompleted with TIMEOUT failure emits DeadlineExpired event`() = runTest {
        val harness = Harness()
        val shot = testShotRequest("shot-pl-to", MediaType.PHOTO)
        harness.process(SessionIntent.ShotStarted(shot))
        harness.process(SessionIntent.DataReceived("shot-pl-to", MediaType.PHOTO))

        val result = testShotResult("shot-pl-to", MediaType.PHOTO).copy(
            structuredPostProcessFailures = listOf(
                PostProcessFailure(
                    stage = PostProcessFailureStage.ALGORITHM,
                    cause = PostProcessFailureCause.TIMEOUT,
                    integrity = PostProcessOutputIntegrity.UNKNOWN,
                    disposition = PostProcessFailureDisposition.UNRECOVERABLE,
                    processorName = "algorithm-render"
                )
            )
        )
        harness.process(SessionIntent.ShotCompleted(result))

        val deadlineTraces = harness.trace.snapshot().filter {
            it.name == "liveness.session.deadline-expired"
        }
        assertTrue(deadlineTraces.isNotEmpty())
        assertTrue(deadlineTraces.any { it.detail.contains("deadline-expired") })
        assertTrue(
            harness.trace.snapshot().any {
                it.name == "liveness.session.release" &&
                    it.detail.contains("shotId=shot-pl-to") &&
                    it.detail.contains("reason=algorithm-render:timeout")
            }
        )
    }

    @Test
    fun `ShotCompleted with non-TIMEOUT failure emits PipelineFailed event`() = runTest {
        val harness = Harness()
        val shot = testShotRequest("shot-pl-pf", MediaType.PHOTO)
        harness.process(SessionIntent.ShotStarted(shot))
        harness.process(SessionIntent.DataReceived("shot-pl-pf", MediaType.PHOTO))

        val result = testShotResult("shot-pl-pf", MediaType.PHOTO).copy(
            structuredPostProcessFailures = listOf(
                PostProcessFailure(
                    stage = PostProcessFailureStage.WATERMARK,
                    cause = PostProcessFailureCause.ENCODE,
                    integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
                    disposition = PostProcessFailureDisposition.RECOVERABLE,
                    processorName = null
                )
            )
        )
        harness.process(SessionIntent.ShotCompleted(result))

        assertTrue(
            harness.trace.snapshot().any { it.name == "liveness.session.pipeline-failed" }
        )
        assertTrue(
            harness.trace.snapshot().any {
                it.name == "liveness.session.release" &&
                    it.detail.contains("reason=watermark:encode-failed")
            }
        )
        assertFalse(
            harness.trace.snapshot().any {
                it.detail.contains("deadline-expired") &&
                    it.detail.contains("shot-pl-pf")
            }
        )
    }

    @Test
    fun `conservative kind watchdog force-releases activeShot after grace`() = runTest {
        val harness = Harness()
        val shot = multiFrameShotRequest("shot-mf-pl")
        harness.process(SessionIntent.ShotStarted(shot))
        assertEquals(shot, harness.state.value.activeShot)

        // Advance past the deadline; cooperative-cancel trace should fire.
        harness.testDispatcher.scheduler.advanceTimeBy(
            PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS
        )
        harness.testDispatcher.scheduler.runCurrent()
        assertTrue(
            harness.trace.snapshot().any { it.name == "liveness.session.cooperative-cancel" }
        )

        // Advance past the grace period; force-release should fire.
        harness.testDispatcher.scheduler.advanceTimeBy(2_000L)
        harness.testDispatcher.scheduler.runCurrent()

        assertNull(harness.state.value.activeShot)
        assertNull(harness.state.value.presentation.pendingPostprocess)
        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertTrue(harness.processor.conservativeForceReleasedShotIdsForTest().contains("shot-mf-pl"))
        val releaseTraces = harness.trace.snapshot().filter {
            it.name == "liveness.session.release" &&
                it.detail.contains("shotId=shot-mf-pl") &&
                it.detail.contains("mode=PHOTO") &&
                it.detail.contains("shotKind=MULTI_FRAME_CAPTURE")
        }
        assertTrue(releaseTraces.isNotEmpty())
    }

    @Test
    fun `conservative kind watchdog does not fire when ShotCompleted arrives during grace`() = runTest {
        val harness = Harness()
        val shot = livePhotoShotRequest("shot-live-pl")
        harness.process(SessionIntent.ShotStarted(shot))

        // Advance past the deadline; cooperative-cancel fires.
        harness.testDispatcher.scheduler.advanceTimeBy(
            PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS
        )
        harness.testDispatcher.scheduler.runCurrent()

        // ShotCompleted arrives during grace period.
        harness.process(SessionIntent.ShotCompleted(testShotResult("shot-live-pl", MediaType.PHOTO)))

        // Advance past grace; force-release should NOT fire.
        harness.testDispatcher.scheduler.advanceTimeBy(2_000L)
        harness.testDispatcher.scheduler.runCurrent()

        assertFalse(harness.processor.conservativeForceReleasedShotIdsForTest().contains("shot-live-pl"))
        assertFalse(
            harness.trace.snapshot().any {
                it.name == "liveness.session.release" &&
                    it.detail.contains("shotId=shot-live-pl")
            }
        )
    }

    @Test
    fun `late ShotCompleted after conservative force-release is rejected as stale`() = runTest {
        val harness = Harness()
        val shot = multiFrameShotRequest("shot-mf-stale")
        harness.process(SessionIntent.ShotStarted(shot))

        harness.testDispatcher.scheduler.advanceTimeBy(
            PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS + 2_000L
        )
        harness.testDispatcher.scheduler.runCurrent()
        assertTrue(harness.processor.conservativeForceReleasedShotIdsForTest().contains("shot-mf-stale"))

        val previousLatestCapture = harness.state.value.presentation.latestCapturePath
        harness.process(SessionIntent.ShotCompleted(testShotResult("shot-mf-stale", MediaType.PHOTO)))

        // Stale completion must not update presentation.
        assertEquals(previousLatestCapture, harness.state.value.presentation.latestCapturePath)
        assertTrue(
            harness.trace.snapshot().any {
                it.name == "shot.completed.conservative-force-released.stale"
            }
        )
    }

    @Test
    fun `pendingPostprocess carries liveness attachment for ordinary still`() = runTest {
        val harness = Harness()
        val shot = testShotRequest("shot-pl-att", MediaType.PHOTO)
        harness.process(SessionIntent.ShotStarted(shot))
        harness.process(SessionIntent.DataReceived("shot-pl-att", MediaType.PHOTO))

        val pending = harness.state.value.presentation.pendingPostprocess
        assertNotNull(pending)
        assertNotNull(pending.livenessAttachment)
        assertNotNull(pending.livenessAttachment?.configSnapshot)
        assertNotNull(pending.livenessAttachment?.liveness)
        assertEquals("shot-pl-att", pending.livenessAttachment?.liveness?.shotId)
    }

    @Test
    fun `liveness watchdog cancelled on ShotFailed for ordinary still`() = runTest {
        val harness = Harness()
        val shot = testShotRequest("shot-pl-fail", MediaType.PHOTO)
        harness.process(SessionIntent.ShotStarted(shot))
        harness.process(SessionIntent.DataReceived("shot-pl-fail", MediaType.PHOTO))

        harness.process(SessionIntent.ShotFailed("shot-pl-fail", MediaType.PHOTO, "postprocess error"))

        assertNull(harness.processor.postProcessLivenessForTest())
        harness.testDispatcher.scheduler.advanceTimeBy(
            PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS
        )
        harness.testDispatcher.scheduler.runCurrent()
        assertFalse(
            harness.trace.snapshot().any {
                it.name == "liveness.session.deadline-expired" &&
                    it.detail.contains("shot-pl-fail")
            }
        )
    }
}
