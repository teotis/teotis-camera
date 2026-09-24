package com.opencamera.app.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.opencamera.app.camera.fixture.FixtureCameraDeviceAdapter
import com.opencamera.app.camera.fixture.FixtureCameraScenario
import com.opencamera.app.camera.fixture.FixtureCaptureOutput
import com.opencamera.app.camera.fixture.FixtureImageExpectation
import com.opencamera.app.camera.fixture.FixtureImageProbe
import com.opencamera.app.camera.fixture.FixturePreviewFrame
import com.opencamera.core.device.DeviceCommand
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.ShotExecutor
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.mode.ModeRegistry
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.DefaultCameraSession
import com.opencamera.core.session.InMemorySessionTrace
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.SavedMediaType
import com.opencamera.core.session.SessionIntent
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.feature.checkin.CheckInModePlugin
import com.opencamera.feature.document.DocumentModePlugin
import com.opencamera.feature.humanistic.HumanisticModePlugin
import com.opencamera.feature.photo.PhotoModePlugin
import com.opencamera.feature.video.VideoModePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FakeCameraFixturePipelineTest {
    @Test
    fun `fixture camera drives preview and still capture through session coordinator`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val adapter = FixtureCameraDeviceAdapter(
            FixtureCameraScenario(
                previewFrame = FixturePreviewFrame(
                    firstFrameLatencyMillis = 37L,
                    snapshot = ThumbnailSource.PreviewSnapshot("/fixture/preview-frame.jpg")
                ),
                captureOutputs = listOf(
                    FixtureCaptureOutput(
                        outputPath = "/fixture/OpenCamera_daylight_001.jpg",
                        metadata = MediaMetadata(
                            customTags = mapOf(
                                "fixture" to "stable-daylight",
                                "qualityGate" to "fake-camera"
                            )
                        ),
                        pipelineNotes = listOf(
                            "fixture:scene=stable-daylight",
                            "fixture:camera-pipeline=completed"
                        )
                    )
                )
            )
        )
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = TestScope(StandardTestDispatcher(testScheduler))
        )
        advanceUntilIdle()

        coordinator.attachPreviewHost(
            lifecycleOwner = StartedLifecycleOwner(),
            previewView = PreviewView(RuntimeEnvironment.getApplication())
        )
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertEquals(PreviewStatus.ACTIVE, session.state.value.previewStatus)
        assertEquals(37L, session.state.value.previewMetrics.lastFirstFrameLatencyMillis)
        assertEquals("/fixture/preview-frame.jpg", session.state.value.previewThumbnailPath)
        assertEquals(1, adapter.bindRequests.size)

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        assertTrue(adapter.recordedCommands.any { it is DeviceCommand.ExecuteShot })
        assertEquals(CaptureStatus.IDLE, session.state.value.captureStatus)
        assertEquals("/fixture/OpenCamera_daylight_001.jpg", session.state.value.latestCapturePath)
        assertEquals(SavedMediaType.PHOTO, session.state.value.latestSavedMediaType)
        assertEquals(
            listOf(
                "fixture:scene=stable-daylight",
                "fixture:camera-pipeline=completed"
            ),
            session.state.value.latestPipelineNotes
        )
        assertIs<ThumbnailSource.SavedMedia>(session.state.value.latestThumbnailSource)
        assertTrue(trace.snapshot().any { it.name == "capture.saved" })
    }

    @Test
    fun `fixture camera bind failure becomes provider preview diagnostic`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val adapter = FixtureCameraDeviceAdapter(
            FixtureCameraScenario(
                bindFailureReason = "fixture provider unavailable"
            )
        )
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = TestScope(StandardTestDispatcher(testScheduler))
        )
        advanceUntilIdle()

        coordinator.attachPreviewHost(
            lifecycleOwner = StartedLifecycleOwner(),
            previewView = PreviewView(RuntimeEnvironment.getApplication())
        )
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertEquals(PreviewStatus.ERROR, session.state.value.previewStatus)
        assertEquals("Provider failure: fixture provider unavailable", session.state.value.lastError)
        assertNotNull(
            trace.snapshot().firstOrNull { event ->
                event.name == "preview.runtime.issue" &&
                    event.detail.contains("kind=PROVIDER_FAILURE")
            }
        )
    }

    @Test
    fun `fixture camera records image signature receipt for golden effect confirmation`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val imageProbe = FixtureImageProbe.fromArgbPixels(
            width = 2,
            height = 2,
            pixels = listOf(
                0xFF000000.toInt(),
                0xFFFFFFFF.toInt(),
                0xFFFF0000.toInt(),
                0xFF00FF00.toInt()
            )
        )
        val adapter = FixtureCameraDeviceAdapter(
            FixtureCameraScenario(
                captureOutputs = listOf(
                    FixtureCaptureOutput(
                        outputPath = "/fixture/OpenCamera_signature_001.jpg",
                        imageProbe = imageProbe,
                        imageExpectation = FixtureImageExpectation(
                            expectedSignature = imageProbe.signature
                        )
                    )
                )
            )
        )
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = TestScope(StandardTestDispatcher(testScheduler))
        )
        advanceUntilIdle()

        coordinator.attachPreviewHost(
            lifecycleOwner = StartedLifecycleOwner(),
            previewView = PreviewView(RuntimeEnvironment.getApplication())
        )
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        assertEquals("/fixture/OpenCamera_signature_001.jpg", session.state.value.latestCapturePath)
        assertTrue(
            session.state.value.latestPipelineNotes.contains(
                "fixture:image-signature=${imageProbe.signature.receipt}"
            )
        )
        assertTrue(session.state.value.latestPipelineNotes.contains("fixture:golden=passed"))
    }

    @Test
    fun `fixture camera fails shot when image signature differs from golden expectation`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val imageProbe = FixtureImageProbe.fromArgbPixels(
            width = 1,
            height = 1,
            pixels = listOf(0xFFFFFFFF.toInt())
        )
        val mismatchedProbe = FixtureImageProbe.fromArgbPixels(
            width = 1,
            height = 1,
            pixels = listOf(0xFF000000.toInt())
        )
        val adapter = FixtureCameraDeviceAdapter(
            FixtureCameraScenario(
                captureOutputs = listOf(
                    FixtureCaptureOutput(
                        imageProbe = imageProbe,
                        imageExpectation = FixtureImageExpectation(
                            expectedSignature = mismatchedProbe.signature
                        )
                    )
                )
            )
        )
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = TestScope(StandardTestDispatcher(testScheduler))
        )
        advanceUntilIdle()

        coordinator.attachPreviewHost(
            lifecycleOwner = StartedLifecycleOwner(),
            previewView = PreviewView(RuntimeEnvironment.getApplication())
        )
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        assertEquals(CaptureStatus.FAILED, session.state.value.captureStatus)
        assertEquals(
            "fixture golden mismatch: expected ${mismatchedProbe.signature.receipt}, " +
                "actual ${imageProbe.signature.receipt}",
            session.state.value.lastError
        )
    }

    private fun createSession(
        trace: InMemorySessionTrace,
        testScope: TestScope
    ): DefaultCameraSession {
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
            trace = trace,
            scope = TestScope(StandardTestDispatcher(testScope.testScheduler)),
            settingsSnapshot = SessionSettingsSnapshot(),
            shotExecutor = ShotExecutor(idGenerator = { "fixture-shot-${++shotIndex}" }),
            recordingTimerDispatcher = Dispatchers.Default
        )
    }

    private class StartedLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle
            get() = registry
    }
}
