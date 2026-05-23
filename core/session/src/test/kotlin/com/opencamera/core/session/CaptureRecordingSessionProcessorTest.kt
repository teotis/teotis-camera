package com.opencamera.core.session

import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotPlan
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ShotTiming
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSessionEvent
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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

        override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<SessionEffect>): Nothing {
            delegate.collect(collector)
        }
    }

    private class FakeShotExecutor : com.opencamera.core.media.ShotExecutor() {
        var planResult: Result<ShotPlan> = Result.failure(NotImplementedError("no plan configured"))
        val plannedStrategies = mutableListOf<CaptureStrategy>()

        override fun plan(strategy: CaptureStrategy, activeShot: ShotRequest?): ShotPlan {
            plannedStrategies.add(strategy)
            return planResult.getOrThrow()
        }
    }

    private open class FakeModeController(
        val id: ModeId = ModeId.PHOTO,
        val snapshotFlow: MutableStateFlow<ModeSnapshot> = MutableStateFlow(defaultModeSnapshot)
    ) : ModeController {
        val sessionEvents = mutableListOf<ModeSessionEvent>()

        override val snapshot: kotlinx.coroutines.flow.StateFlow<ModeSnapshot> = snapshotFlow

        override fun onEnter() {}
        override fun onExit() {}
        override fun deviceGraph(): DeviceGraphSpec = DeviceGraphSpec.stillCapture()
        override fun handle(intent: com.opencamera.core.mode.ModeIntent) = com.opencamera.core.mode.ModeSignal.None
        override fun onLensFacingChanged(facing: com.opencamera.core.device.LensFacing) {}
        override fun onStillCaptureQualityChanged(quality: com.opencamera.core.media.StillCaptureQualityPreference) {}
        override fun onStillCaptureResolutionChanged(preset: com.opencamera.core.media.StillCaptureResolutionPreset) {}
        override fun onDeviceCapabilitiesChanged(capabilities: com.opencamera.core.device.DeviceCapabilities) {}
        override fun onSessionEvent(event: ModeSessionEvent) { sessionEvents.add(event) }
    }

    private class Harness(
        initialState: SessionState = runningState()
    ) {
        val testDispatcher = StandardTestDispatcher()
        val scope = kotlinx.coroutines.CoroutineScope(testDispatcher)
        val state = MutableStateFlow(initialState)
        val effects = RecordingEffects()
        val trace = InMemorySessionTrace()
        val shotExecutor = FakeShotExecutor()
        val modeController = FakeModeController()
        val dispatchedIntents = mutableListOf<SessionIntent>()

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
            shotKind = if (mediaType == MediaType.PHOTO) ShotKind.STILL_CAPTURE else ShotKind.VIDEO_RECORDING,
            mediaType = mediaType,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.KEEP_PREVIEW_FRAME,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )

        private fun testShotPlan(shotId: String = "shot-1", mediaType: MediaType = MediaType.PHOTO) = ShotPlan(
            request = testShotRequest(shotId, mediaType),
            saveTask = com.opencamera.core.media.MediaSaveTask(
                saveRequest = SaveRequest.photoLibrary()
            ),
            graph = com.opencamera.core.media.ShotGraph(
                shotId = shotId,
                captureNodes = emptyList(),
                algorithmNodes = emptyList(),
                outputNodes = emptyList()
            )
        )

        private fun testShotResult(
            shotId: String = "shot-1",
            mediaType: MediaType = MediaType.PHOTO,
            outputPath: String = "/sdcard/photo.jpg"
        ) = ShotResult(
            shotId = shotId,
            mediaType = mediaType,
            outputPath = outputPath,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.SavedMedia(outputPath),
            metadata = com.opencamera.core.media.MediaMetadata(),
            timing = ShotTiming()
        )
    }

    // ── Countdown Tests ─────────────────────────────────────────────────

    @Test
    fun `startCaptureCountdown sets countdown state and dispatches ticks`() = runTest {
        val harness = Harness()
        val strategy = CaptureStrategy.SingleFrame()

        harness.processor.startCaptureCountdown(strategy, countdownSeconds = 3)

        assertEquals(CaptureStatus.REQUESTED, harness.state.value.captureStatus)
        assertEquals(3, harness.state.value.presentation.countdownRemainingSeconds)
        assertTrue(harness.processor.countdownInProgress())
        assertTrue(harness.trace.snapshot().any { it.name == "capture.countdown.started" })
    }

    @Test
    fun `cancelPendingCountdown clears countdown state`() = runTest {
        val harness = Harness()
        val strategy = CaptureStrategy.SingleFrame()

        harness.processor.startCaptureCountdown(strategy, countdownSeconds = 5)
        harness.processor.cancelPendingCountdown("user cancelled")

        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertNull(harness.state.value.presentation.countdownRemainingSeconds)
        assertFalse(harness.processor.countdownInProgress())
        assertTrue(harness.trace.snapshot().any { it.name == "capture.countdown.cancelled" })
    }

    @Test
    fun `handleCountdownTick updates remaining seconds`() = runTest {
        val harness = Harness()
        val strategy = CaptureStrategy.SingleFrame()
        harness.processor.startCaptureCountdown(strategy, countdownSeconds = 3)

        harness.process(SessionIntent.CountdownTick(2))

        assertEquals(2, harness.state.value.presentation.countdownRemainingSeconds)
        assertEquals("Photo capture starts in 2s", harness.state.value.presentation.lastAction)
    }

    @Test
    fun `handleCountdownTick ignored when no countdown active`() = runTest {
        val harness = Harness()
        val previousState = harness.state.value

        harness.process(SessionIntent.CountdownTick(1))

        assertEquals(previousState, harness.state.value)
    }

    @Test
    fun `handleCountdownCompleted submits strategy and clears countdown`() = runTest {
        val harness = Harness()
        val strategy = CaptureStrategy.SingleFrame()
        val plan = testShotPlan("shot-cd")
        harness.shotExecutor.planResult = Result.success(plan)

        harness.processor.startCaptureCountdown(strategy, countdownSeconds = 2)
        harness.process(SessionIntent.CountdownCompleted)

        assertFalse(harness.processor.countdownInProgress())
        assertNull(harness.state.value.presentation.countdownRemainingSeconds)
        assertEquals(1, harness.shotExecutor.plannedStrategies.size)
    }

    @Test
    fun `handleCountdownCompleted ignored when no pending strategy`() = runTest {
        val harness = Harness()

        harness.process(SessionIntent.CountdownCompleted)

        assertFalse(harness.processor.countdownInProgress())
        assertEquals(0, harness.shotExecutor.plannedStrategies.size)
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
        val result = testShotResult("shot-1", MediaType.PHOTO)

        harness.process(SessionIntent.ShotCompleted(result))

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
        val result = testShotResult("shot-1", MediaType.PHOTO)

        harness.process(SessionIntent.ShotCompleted(result))

        assertTrue(harness.state.value.presentation.latestThumbnailSource is ThumbnailSource.SavedMedia)
    }

    @Test
    fun `handleShotCompleted retains previous thumbnail when result is None`() = runTest {
        val previousSource = ThumbnailSource.SavedMedia("/prev.jpg")
        val harness = Harness(runningState().copy(
            activeShot = testShotRequest("shot-1", MediaType.PHOTO),
            presentation = SessionPresentationState(latestThumbnailSource = previousSource)
        ))
        val result = testShotResult("shot-1", MediaType.PHOTO).copy(
            thumbnailSource = ThumbnailSource.None
        )

        harness.process(SessionIntent.ShotCompleted(result))

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
        val previousState = harness.state.value

        harness.process(SessionIntent.ShotFailed("shot-x", MediaType.PHOTO, "error"))

        assertEquals(previousState.captureStatus, harness.state.value.captureStatus)
    }

    @Test
    fun `handleShotFailed ignored when shotId mismatches active shot`() = runTest {
        val shot = testShotRequest("shot-1", MediaType.PHOTO)
        val harness = Harness(runningState().copy(activeShot = shot))

        harness.process(SessionIntent.ShotFailed("shot-2", MediaType.PHOTO, "error"))

        assertNotNull(harness.state.value.activeShot)
        assertEquals("shot-1", harness.state.value.activeShot?.shotId)
    }

    // ── Capture Strategy Tests ──────────────────────────────────────────

    @Test
    fun `submitCaptureStrategy emits ExecuteShot effect`() = runTest {
        val harness = Harness()
        val strategy = CaptureStrategy.SingleFrame()
        val plan = testShotPlan("shot-1", MediaType.PHOTO)
        harness.shotExecutor.planResult = Result.success(plan)

        harness.processor.submitCaptureStrategy(strategy)

        val effect = harness.lastEffect()
        assertTrue(effect is SessionEffect.ExecuteShot)
        assertEquals(CaptureStatus.REQUESTED, harness.state.value.captureStatus)
    }

    @Test
    fun `submitCaptureStrategy for video starts recording watchdog`() = runTest {
        val harness = Harness()
        val strategy = CaptureStrategy.VideoRecording()
        val plan = testShotPlan("shot-v", MediaType.VIDEO)
        harness.shotExecutor.planResult = Result.success(plan)

        harness.processor.submitCaptureStrategy(strategy)

        assertEquals(RecordingStatus.REQUESTING, harness.state.value.recordingStatus)
        assertNotNull(harness.state.value.activeShot)
    }

    @Test
    fun `submitCaptureStrategy handles plan failure`() = runTest {
        val harness = Harness()
        val strategy = CaptureStrategy.SingleFrame()
        harness.shotExecutor.planResult = Result.failure(RuntimeException("no graph"))

        harness.processor.submitCaptureStrategy(strategy)

        assertEquals(CaptureStatus.IDLE, harness.state.value.captureStatus)
        assertEquals("no graph", harness.state.value.presentation.lastError)
        assertTrue(harness.trace.snapshot().any { it.name == "shot.plan.failed" })
    }

    // ── Recording Watchdog Tests ────────────────────────────────────────

    @Test
    fun `recording watchdog times out when status still matches`() = runTest {
        val harness = Harness(runningState().copy(
            recordingStatus = RecordingStatus.REQUESTING
        ))

        harness.processor.startRecordingWatchdog(RecordingStatus.REQUESTING, timeoutMillis = 100L)
        harness.testDispatcher.scheduler.advanceTimeBy(150L)
        harness.testDispatcher.scheduler.runCurrent()

        assertEquals(RecordingStatus.IDLE, harness.state.value.recordingStatus)
        assertNull(harness.state.value.activeShot)
        assertTrue(harness.trace.snapshot().any { it.name == "recording.watchdog.timeout" })
    }

    @Test
    fun `recording watchdog does not fire when status changes before timeout`() = runTest {
        val harness = Harness(runningState().copy(
            recordingStatus = RecordingStatus.REQUESTING
        ))

        harness.processor.startRecordingWatchdog(RecordingStatus.REQUESTING, timeoutMillis = 200L)
        // Status changes before timeout
        harness.state.value = harness.state.value.copy(recordingStatus = RecordingStatus.RECORDING)
        harness.testDispatcher.scheduler.advanceTimeBy(250L)
        harness.testDispatcher.scheduler.runCurrent()

        assertEquals(RecordingStatus.RECORDING, harness.state.value.recordingStatus)
        assertFalse(harness.trace.snapshot().any { it.name == "recording.watchdog.timeout" })
    }

    @Test
    fun `starting new watchdog cancels previous one`() = runTest {
        val harness = Harness(runningState().copy(
            recordingStatus = RecordingStatus.REQUESTING
        ))

        harness.processor.startRecordingWatchdog(RecordingStatus.REQUESTING, timeoutMillis = 200L)
        // Start a second watchdog before first fires
        harness.state.value = harness.state.value.copy(recordingStatus = RecordingStatus.STOPPING)
        harness.processor.startRecordingWatchdog(RecordingStatus.STOPPING, timeoutMillis = 100L)
        harness.testDispatcher.scheduler.advanceTimeBy(150L)
        harness.testDispatcher.scheduler.runCurrent()

        // The first watchdog should have been cancelled; only second fires
        assertEquals(RecordingStatus.IDLE, harness.state.value.recordingStatus)
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

    // ── Error handling tests ────────────────────────────────────────────

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
}
