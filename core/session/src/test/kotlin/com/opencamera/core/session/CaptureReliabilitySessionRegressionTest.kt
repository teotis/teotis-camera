package com.opencamera.core.session

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.DeviceRuntimeIssueKind
import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotExecutor
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeRegistry
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.feature.checkin.CheckInModePlugin
import com.opencamera.feature.document.DocumentModePlugin
import com.opencamera.feature.humanistic.HumanisticModePlugin
import com.opencamera.feature.photo.PhotoModePlugin
import com.opencamera.feature.video.VideoModePlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Capture reliability regression tests for session-level fault handling.
 *
 * These tests encode the invariant checks of the Capture Reliability Closure
 * at the pure-JVM session layer (no Robolectric needed):
 * - INV-1e: stale/duplicate ShotStarted must not clobber the current shot.
 * - INV-4b: Shutdown must converge all in-flight postprocess UI state.
 * - INV-1d/INV-3c: terminal events are applied at most once; a failed shot
 *   never flips back to saved.
 * - INV-3b: a non-recoverable camera fault during recording must stop the
 *   recording instead of leaving RECORDING forever.
 * - INV-5d: Boot/Shutdown are idempotent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureReliabilitySessionRegressionTest {

    @Test
    fun `late shot started for video after requesting timeout does not clobber newer shot`() = runTest {
        val session = createSession(this)
        bootVideoSession(session)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val firstShot = assertNotNull(session.state.value.activeShot)
        assertEquals(RecordingStatus.REQUESTING, session.state.value.recordingStatus)

        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(RecordingStatus.IDLE, session.state.value.recordingStatus)
        assertNull(session.state.value.activeShot)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val secondShot = assertNotNull(session.state.value.activeShot)
        assertEquals(RecordingStatus.REQUESTING, session.state.value.recordingStatus)

        session.dispatch(SessionIntent.ShotStarted(firstShot))
        runCurrent()

        assertEquals(secondShot.shotId, session.state.value.activeShot?.shotId)
        assertEquals(RecordingStatus.REQUESTING, session.state.value.recordingStatus)
    }

    @Test
    fun `duplicate shot started for current photo shot is idempotent`() = runTest {
        val session = createSession(this)
        bootSession(session)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)

        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()
        assertEquals(CaptureStatus.SAVING, session.state.value.captureStatus)
        assertEquals(shot.shotId, session.state.value.activeShot?.shotId)

        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()

        assertEquals(CaptureStatus.SAVING, session.state.value.captureStatus)
        assertEquals(shot.shotId, session.state.value.activeShot?.shotId)
    }

    @Test
    fun `late photo shot started after data received is treated as stale`() = runTest {
        val session = createSession(this)
        bootSession(session)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)

        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()
        session.dispatch(SessionIntent.DataReceived(shot.shotId, MediaType.PHOTO))
        runCurrent()
        assertNull(session.state.value.activeShot)
        assertEquals(shot.shotId, session.state.value.presentation.pendingPostprocess?.shotId)

        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()

        assertNull(session.state.value.activeShot)
        assertEquals(shot.shotId, session.state.value.presentation.pendingPostprocess?.shotId)
    }

    @Test
    fun `shutdown clears pending postprocess and capture readiness`() = runTest {
        val session = createSession(this)
        bootSession(session)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()
        session.dispatch(SessionIntent.DataReceived(shot.shotId, MediaType.PHOTO))
        runCurrent()
        assertNotNull(session.state.value.presentation.pendingPostprocess)
        assertNotNull(session.state.value.presentation.captureReadiness)

        session.dispatch(SessionIntent.Shutdown)
        runCurrent()

        assertEquals(SessionLifecycle.STOPPED, session.state.value.lifecycle)
        assertNull(session.state.value.presentation.pendingPostprocess)
        assertNull(session.state.value.presentation.captureReadiness)
        assertNull(session.state.value.presentation.pendingCaptureFeedback)
        assertEquals(CaptureStatus.IDLE, session.state.value.captureStatus)
        assertEquals(RecordingStatus.IDLE, session.state.value.recordingStatus)
        assertNull(session.state.value.activeShot)
    }

    @Test
    fun `duplicate shot completed for same shot is applied only once`() = runTest {
        val session = createSession(this)
        bootSession(session)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()

        val result = completedResult(shot)
        session.dispatch(SessionIntent.ShotCompleted(result))
        runCurrent()
        assertEquals(result.outputPath, session.state.value.latestCapturePath)

        session.dispatch(SessionIntent.ShotCompleted(result))
        runCurrent()

        assertEquals(result.outputPath, session.state.value.latestCapturePath)
    }

    @Test
    fun `shot completed after failed does not flip to saved`() = runTest {
        val session = createSession(this)
        bootSession(session)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()

        session.dispatch(SessionIntent.ShotFailed(shot.shotId, MediaType.PHOTO, "storage full"))
        runCurrent()
        assertEquals(CaptureStatus.FAILED, session.state.value.captureStatus)
        assertNull(session.state.value.activeShot)
        assertNull(session.state.value.latestCapturePath)

        session.dispatch(SessionIntent.ShotCompleted(completedResult(shot)))
        runCurrent()

        assertEquals(CaptureStatus.FAILED, session.state.value.captureStatus)
        assertNull(session.state.value.latestCapturePath)
        assertEquals("storage full", session.state.value.lastError)
    }

    @Test
    fun `duplicate shot failed for same shot is applied only once`() = runTest {
        val session = createSession(this)
        bootSession(session)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()

        session.dispatch(SessionIntent.ShotFailed(shot.shotId, MediaType.PHOTO, "io error"))
        runCurrent()
        session.dispatch(SessionIntent.ShotFailed(shot.shotId, MediaType.PHOTO, "io error"))
        runCurrent()

        assertEquals(CaptureStatus.FAILED, session.state.value.captureStatus)
        assertEquals("io error", session.state.value.lastError)
    }

    @Test
    fun `non-recoverable camera fault during recording stops the recording`() = runTest {
        val session = createSession(this)
        bootVideoSession(session)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()
        assertEquals(RecordingStatus.RECORDING, session.state.value.recordingStatus)

        session.dispatch(
            SessionIntent.PreviewRuntimeIssue(
                DeviceRuntimeIssue(
                    kind = DeviceRuntimeIssueKind.CAMERA_FATAL,
                    reason = "camera hardware disconnected",
                    isRecoverable = false
                )
            )
        )
        runCurrent()

        assertEquals(RecordingStatus.STOPPING, session.state.value.recordingStatus)
    }

    @Test
    fun `fatal camera fault during recording converges via stopping watchdog`() = runTest {
        val session = createSession(this)
        bootVideoSession(session)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()

        session.dispatch(
            SessionIntent.PreviewRuntimeIssue(
                DeviceRuntimeIssue(
                    kind = DeviceRuntimeIssueKind.CAMERA_FATAL,
                    reason = "camera hardware disconnected",
                    isRecoverable = false
                )
            )
        )
        runCurrent()

        advanceTimeBy(15_001)
        runCurrent()

        assertEquals(RecordingStatus.IDLE, session.state.value.recordingStatus)
        assertNull(session.state.value.activeShot)
        assertNotNull(session.state.value.lastError)
    }

    @Test
    fun `recoverable camera issue during recording leaves recording active`() = runTest {
        val session = createSession(this)
        bootVideoSession(session)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()

        session.dispatch(
            SessionIntent.PreviewRuntimeIssue(
                DeviceRuntimeIssue(
                    kind = DeviceRuntimeIssueKind.THERMAL_CRITICAL,
                    reason = "thermal throttling",
                    isRecoverable = true
                )
            )
        )
        runCurrent()

        assertEquals(RecordingStatus.RECORDING, session.state.value.recordingStatus)
        assertEquals(shot.shotId, session.state.value.activeShot?.shotId)
    }

    @Test
    fun `duplicate boot and duplicate shutdown are no-ops`() = runTest {
        val session = createSession(this)
        bootSession(session)

        session.dispatch(SessionIntent.Boot)
        runCurrent()
        assertEquals(SessionLifecycle.RUNNING, session.state.value.lifecycle)

        session.dispatch(SessionIntent.Shutdown)
        runCurrent()
        assertEquals(SessionLifecycle.STOPPED, session.state.value.lifecycle)

        session.dispatch(SessionIntent.Shutdown)
        runCurrent()
        assertEquals(SessionLifecycle.STOPPED, session.state.value.lifecycle)
    }

    private fun completedResult(shot: com.opencamera.core.media.ShotRequest): ShotResult {
        return ShotResult(
            shotId = shot.shotId,
            mediaType = shot.mediaType,
            outputPath = "/saved/${shot.shotId}.jpg",
            outputHandle = com.opencamera.core.media.MediaOutputHandle(
                displayPath = "/saved/${shot.shotId}.jpg"
            ),
            saveRequest = shot.saveRequest,
            thumbnailSource = ThumbnailSource.SavedMedia("/saved/${shot.shotId}.jpg"),
            captureProfile = shot.captureProfile,
            metadata = shot.saveRequest.metadata
        )
    }

    private suspend fun TestScope.bootSession(session: DefaultCameraSession) {
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        runCurrent()
    }

    private suspend fun TestScope.bootVideoSession(session: DefaultCameraSession) {
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        runCurrent()
    }

    private fun createSession(testScope: TestScope): DefaultCameraSession {
        var shotIndex = 0
        return DefaultCameraSession(
            registry = ModeRegistry(
                listOf(
                    PhotoModePlugin(),
                    DocumentModePlugin(),
                    HumanisticModePlugin(),
                    CheckInModePlugin(),
                    VideoModePlugin()
                )
            ),
            trace = InMemorySessionTrace(),
            baseDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT)
            ),
            scope = TestScope(StandardTestDispatcher(testScope.testScheduler)),
            settingsSnapshot = SessionSettingsSnapshot(),
            shotExecutor = ShotExecutor(idGenerator = { "reliability-${++shotIndex}" }),
            recordingTimerDispatcher = null
        )
    }
}
