package com.opencamera.app.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.opencamera.app.camera.device.CameraDeviceAdapter
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceCommand
import com.opencamera.core.device.DeviceEvent
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.DeviceRuntimeIssueKind
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.RecordingQualityPreset
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.CameraSession
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PermissionState
import com.opencamera.core.session.PreviewMetrics
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SessionEffect
import com.opencamera.core.session.SessionIntent
import com.opencamera.core.session.SessionLifecycle
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.SavedMediaType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CameraSessionCoordinatorTest {
    @Test
    fun `device shot events are forwarded to session intents in order`() = runTest {
        val session = FakeCameraSession()
        val adapter = FakeCameraDeviceAdapter()
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope
        )
        advanceUntilIdle()

        val shot = ShotRequest(
            shotId = "shot-night-1",
            shotKind = com.opencamera.core.media.ShotKind.MULTI_FRAME_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(relativePath = "Pictures/OpenCamera/Night"),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = com.opencamera.core.media.PostProcessSpec(
                algorithmProfile = "night-multiframe-tripod"
            ),
            captureProfile = com.opencamera.core.media.CaptureProfile(frameCount = 12)
        )

        adapter.emit(
            DeviceEvent.ShotStarted(shot)
        )
        adapter.emit(
            DeviceEvent.ShotFailed(
                shotId = shot.shotId,
                mediaType = MediaType.PHOTO,
                reason = "Camera permission missing"
            )
        )
        advanceUntilIdle()

        assertEquals(
            listOf(
                SessionIntent.ShotStarted(shot),
                SessionIntent.ShotFailed(
                    shotId = shot.shotId,
                    mediaType = MediaType.PHOTO,
                    reason = "Camera permission missing"
                )
            ),
            session.recordedIntents.takeLast(2)
        )
    }

    @Test
    fun `preview surface loss during recording reports error without rebinding`() = runTest {
        val session = FakeCameraSession(
            initialState = defaultSessionState(
                recordingStatus = RecordingStatus.RECORDING
            )
        )
        val adapter = FakeCameraDeviceAdapter()
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope
        )
        advanceUntilIdle()

        adapter.emit(
            DeviceEvent.PreviewSurfaceLost("surface lost while recording")
        )
        advanceUntilIdle()

        assertTrue(
            session.recordedIntents.contains(
                SessionIntent.PreviewSurfaceLost("surface lost while recording")
            )
        )
        assertEquals(0, adapter.bindRequests)
    }

    @Test
    fun `binding failure clears attached mode so later mode changes can retry`() = runTest {
        val photoGraph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.BACK,
            enablePreviewSnapshots = true
        )
        val nightGraph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.FRONT,
            enablePreviewSnapshots = true
        )
        val session = FakeCameraSession(
            initialState = defaultSessionState(
                activeMode = ModeId.PHOTO,
                modeId = ModeId.PHOTO,
                deviceGraph = photoGraph
            )
        )
        val adapter = FakeCameraDeviceAdapter(
            bindResults = ArrayDeque(
                listOf(
                    BindResult.Success,
                    BindResult.Failure("Night bind failed"),
                    BindResult.Success
                )
            )
        )
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope
        )
        val lifecycleOwner = TestLifecycleOwner()
        val previewView = allocateInstance(PreviewView::class.java)
        advanceUntilIdle()

        coordinator.attachPreviewHost(lifecycleOwner, previewView)
        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.PHOTO,
                deviceGraph = photoGraph,
                reason = "initial attach",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.NIGHT,
                deviceGraph = nightGraph,
                reason = "mode switched to night",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.PHOTO,
                deviceGraph = photoGraph,
                reason = "mode switched to photo",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        assertEquals(3, adapter.bindRequests)
        assertTrue(
            session.recordedIntents.contains(
                SessionIntent.PreviewRuntimeIssue(
                    DeviceRuntimeIssue(
                        kind = DeviceRuntimeIssueKind.BIND_FAILURE,
                        reason = "Night bind failed",
                        isRecoverable = true
                    )
                )
            )
        )
    }

    @Test
    fun `initial bind effect reports failure when first bind does not succeed`() = runTest {
        val session = FakeCameraSession()
        val adapter = FakeCameraDeviceAdapter(
            bindResults = ArrayDeque(
                listOf(BindResult.Failure("Initial bind failed"))
            )
        )
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope
        )
        val lifecycleOwner = TestLifecycleOwner()
        val previewView = allocateInstance(PreviewView::class.java)
        advanceUntilIdle()

        coordinator.attachPreviewHost(lifecycleOwner, previewView)
        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.PHOTO,
                deviceGraph = session.state.value.activeDeviceGraph,
                reason = "initial attach",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        assertEquals(1, adapter.bindRequests)
        assertTrue(
            session.recordedIntents.contains(
                SessionIntent.PreviewRuntimeIssue(
                    DeviceRuntimeIssue(
                        kind = DeviceRuntimeIssueKind.BIND_FAILURE,
                        reason = "Initial bind failed",
                        isRecoverable = true
                    )
                )
            )
        )
    }

    @Test
    fun `runtime issue device events are forwarded to session intents`() = runTest {
        val session = FakeCameraSession()
        val adapter = FakeCameraDeviceAdapter()
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope
        )
        advanceUntilIdle()

        val issue = DeviceRuntimeIssue(
            kind = DeviceRuntimeIssueKind.CAMERA_FATAL,
            reason = "camera service died",
            isRecoverable = false
        )
        adapter.emit(DeviceEvent.RuntimeIssue(issue))
        advanceUntilIdle()

        assertTrue(
            session.recordedIntents.contains(
                SessionIntent.PreviewRuntimeIssue(issue)
            )
        )
    }

    @Test
    fun `preview startup watchdog issue is forwarded after bind start`() = runTest {
        val session = FakeCameraSession()
        val adapter = FakeCameraDeviceAdapter()
        val runtimeIssueMonitor = CompositeRuntimeIssueMonitor(
            PreviewStartupRuntimeIssueMonitor(TestScope(StandardTestDispatcher(testScheduler)))
        )
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope,
            runtimeIssueMonitor = runtimeIssueMonitor
        )
        val lifecycleOwner = TestLifecycleOwner()
        val previewView = allocateInstance(PreviewView::class.java)
        advanceUntilIdle()

        coordinator.attachPreviewHost(lifecycleOwner, previewView)
        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.PHOTO,
                deviceGraph = session.state.value.activeDeviceGraph,
                reason = "session boot",
                isRecovery = false
            )
        )
        advanceTimeBy(1200)
        advanceUntilIdle()

        assertTrue(
            session.recordedIntents.contains(
                SessionIntent.PreviewRuntimeIssue(
                    DeviceRuntimeIssue(
                        kind = DeviceRuntimeIssueKind.PREVIEW_STALL,
                        reason = "first frame timed out after 1200 ms (Cold start): session boot",
                        isRecoverable = true
                    )
                )
            )
        )
    }

    @Test
    fun `thermal runtime issue monitor forwards issues after preview host attach`() = runTest {
        val session = FakeCameraSession()
        val adapter = FakeCameraDeviceAdapter()
        val runtimeIssueMonitor = FakeRuntimeIssueMonitor()
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope,
            runtimeIssueMonitor = runtimeIssueMonitor
        )
        val lifecycleOwner = TestLifecycleOwner()
        val previewView = allocateInstance(PreviewView::class.java)
        advanceUntilIdle()

        coordinator.attachPreviewHost(lifecycleOwner, previewView)
        runtimeIssueMonitor.emit(
            DeviceRuntimeIssue(
                kind = DeviceRuntimeIssueKind.THERMAL_CRITICAL,
                reason = "thermal status severe",
                isRecoverable = false
            )
        )
        advanceUntilIdle()

        assertTrue(runtimeIssueMonitor.attached)
        assertTrue(
            session.recordedIntents.contains(
                SessionIntent.PreviewRuntimeIssue(
                    DeviceRuntimeIssue(
                        kind = DeviceRuntimeIssueKind.THERMAL_CRITICAL,
                        reason = "thermal status severe",
                        isRecoverable = false
                    )
                )
            )
        )
    }

    @Test
    fun `clearing preview host detaches thermal runtime issue monitor`() = runTest {
        val session = FakeCameraSession()
        val adapter = FakeCameraDeviceAdapter()
        val runtimeIssueMonitor = FakeRuntimeIssueMonitor()
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope,
            runtimeIssueMonitor = runtimeIssueMonitor
        )
        val lifecycleOwner = TestLifecycleOwner()
        val previewView = allocateInstance(PreviewView::class.java)
        advanceUntilIdle()

        coordinator.attachPreviewHost(lifecycleOwner, previewView)
        session.emitEffect(
            SessionEffect.UnbindPreview(
                reason = "Activity moved to background",
                clearHost = true
            )
        )
        advanceUntilIdle()

        assertFalse(runtimeIssueMonitor.attached)
    }

    @Test
    fun `zoom effect dispatches zoom command without rebinding preview`() = runTest {
        val session = FakeCameraSession()
        val adapter = FakeCameraDeviceAdapter()
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope
        )
        advanceUntilIdle()

        session.emitEffect(
            SessionEffect.ApplyZoomRatio(2f)
        )
        advanceUntilIdle()

        assertTrue(adapter.recordedCommands.contains(DeviceCommand.UpdateZoomRatio(2f)))
        assertEquals(0, adapter.bindRequests)
    }

    @Test
    fun `failed bind keeps preview host so a later bind effect can retry`() = runTest {
        val photoGraph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.BACK,
            enablePreviewSnapshots = true
        )
        val nightGraph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.FRONT,
            enablePreviewSnapshots = true
        )
        val session = FakeCameraSession(
            initialState = defaultSessionState(
                activeMode = ModeId.PHOTO,
                modeId = ModeId.PHOTO,
                deviceGraph = photoGraph
            )
        )
        val adapter = FakeCameraDeviceAdapter(
            bindResults = ArrayDeque(
                listOf(
                    BindResult.Failure("Initial bind failed"),
                    BindResult.Success
                )
            )
        )
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope
        )
        val lifecycleOwner = TestLifecycleOwner()
        val previewView = allocateInstance(PreviewView::class.java)
        advanceUntilIdle()

        coordinator.attachPreviewHost(lifecycleOwner, previewView)
        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.PHOTO,
                deviceGraph = photoGraph,
                reason = "initial attach",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.NIGHT,
                deviceGraph = nightGraph,
                reason = "retry after failure",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        assertTrue(coordinator.hasAttachedPreviewHost())
        assertEquals(2, adapter.bindRequests)
        assertEquals(nightGraph, adapter.boundGraph())
    }

    @Test
    fun `video bind effect rebinds while staying in same mode`() = runTest {
        val initialGraph = DeviceGraphSpec.videoRecording(
            preferredLensFacing = LensFacing.BACK,
            enablePreviewSnapshots = true,
            audioEnabledWhenPermitted = true,
            qualityPreset = RecordingQualityPreset.FHD
        )
        val updatedGraph = DeviceGraphSpec.videoRecording(
            preferredLensFacing = LensFacing.BACK,
            enablePreviewSnapshots = true,
            audioEnabledWhenPermitted = true,
            qualityPreset = RecordingQualityPreset.HD
        )
        val session = FakeCameraSession(
            initialState = defaultSessionState(
                activeMode = ModeId.VIDEO,
                modeId = ModeId.VIDEO,
                deviceGraph = initialGraph
            )
        )
        val adapter = FakeCameraDeviceAdapter()
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope
        )
        val lifecycleOwner = TestLifecycleOwner()
        val previewView = allocateInstance(PreviewView::class.java)
        advanceUntilIdle()

        coordinator.attachPreviewHost(lifecycleOwner, previewView)
        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.VIDEO,
                deviceGraph = initialGraph,
                reason = "initial attach",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.VIDEO,
                deviceGraph = updatedGraph,
                reason = "video quality updated",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        assertEquals(2, adapter.bindRequests)
        assertEquals(updatedGraph, adapter.boundGraph())
    }

    @Test
    fun `still quality bind effect rebinds while staying in same mode`() = runTest {
        val initialGraph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.BACK,
            enablePreviewSnapshots = true,
            qualityPreference = StillCaptureQualityPreference.LATENCY
        )
        val updatedGraph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.BACK,
            enablePreviewSnapshots = true,
            qualityPreference = StillCaptureQualityPreference.QUALITY
        )
        val session = FakeCameraSession(
            initialState = defaultSessionState(
                activeMode = ModeId.PHOTO,
                modeId = ModeId.PHOTO,
                deviceGraph = initialGraph
            )
        )
        val adapter = FakeCameraDeviceAdapter()
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope
        )
        val lifecycleOwner = TestLifecycleOwner()
        val previewView = allocateInstance(PreviewView::class.java)
        advanceUntilIdle()

        coordinator.attachPreviewHost(lifecycleOwner, previewView)
        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.PHOTO,
                deviceGraph = initialGraph,
                reason = "initial attach",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.PHOTO,
                deviceGraph = updatedGraph,
                reason = "still quality updated",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        assertEquals(2, adapter.bindRequests)
        assertEquals(updatedGraph, adapter.boundGraph())
    }

    @Test
    fun `still resolution bind effect rebinds while staying in same mode`() = runTest {
        val initialGraph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.BACK,
            enablePreviewSnapshots = true,
            resolutionPreset = StillCaptureResolutionPreset.LARGE_12MP
        )
        val updatedGraph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.BACK,
            enablePreviewSnapshots = true,
            resolutionPreset = StillCaptureResolutionPreset.MEDIUM_8MP
        )
        val session = FakeCameraSession(
            initialState = defaultSessionState(
                activeMode = ModeId.PHOTO,
                modeId = ModeId.PHOTO,
                deviceGraph = initialGraph
            )
        )
        val adapter = FakeCameraDeviceAdapter()
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope
        )
        val lifecycleOwner = TestLifecycleOwner()
        val previewView = allocateInstance(PreviewView::class.java)
        advanceUntilIdle()

        coordinator.attachPreviewHost(lifecycleOwner, previewView)
        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.PHOTO,
                deviceGraph = initialGraph,
                reason = "initial attach",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.PHOTO,
                deviceGraph = updatedGraph,
                reason = "still resolution updated",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        assertEquals(2, adapter.bindRequests)
        assertEquals(updatedGraph, adapter.boundGraph())
    }

    @Test
    fun `bind effect refreshes lens-specific capabilities when graph lens changes`() = runTest {
        val backGraph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.BACK,
            enablePreviewSnapshots = true
        )
        val frontGraph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.FRONT,
            enablePreviewSnapshots = true
        )
        val backCapabilities = DeviceCapabilities.DEFAULT.copy(supportsFlashControl = true)
        val frontCapabilities = DeviceCapabilities.DEFAULT.copy(supportsFlashControl = false)
        val session = FakeCameraSession(
            initialState = defaultSessionState(
                activeMode = ModeId.PHOTO,
                modeId = ModeId.PHOTO,
                deviceGraph = backGraph,
                activeDeviceCapabilities = backCapabilities
            )
        )
        val adapter = FakeCameraDeviceAdapter(
            capabilitiesByLensFacing = mapOf(
                LensFacing.BACK to backCapabilities,
                LensFacing.FRONT to frontCapabilities
            )
        )
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope
        )
        val lifecycleOwner = TestLifecycleOwner()
        val previewView = allocateInstance(PreviewView::class.java)
        advanceUntilIdle()

        coordinator.attachPreviewHost(lifecycleOwner, previewView)
        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.PHOTO,
                deviceGraph = backGraph,
                reason = "initial attach",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.NIGHT,
                deviceGraph = frontGraph,
                reason = "lens switched to front",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        assertTrue(
            session.recordedIntents.contains(
                SessionIntent.DeviceCapabilitiesUpdated(frontCapabilities)
            )
        )
        assertEquals(2, adapter.bindRequests)
        assertEquals(frontGraph, adapter.boundGraph())
    }

    @Test
    fun `pending preview bind queues when host is missing and replays on attach`() = runTest {
        val photoGraph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.BACK,
            enablePreviewSnapshots = true
        )
        val session = FakeCameraSession()
        val adapter = FakeCameraDeviceAdapter()
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope
        )
        advanceUntilIdle()

        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.PHOTO,
                deviceGraph = photoGraph,
                reason = "camera permission granted",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        assertEquals(0, adapter.bindRequests)

        val lifecycleOwner = TestLifecycleOwner()
        val previewView = allocateInstance(PreviewView::class.java)
        coordinator.attachPreviewHost(lifecycleOwner, previewView)
        advanceUntilIdle()

        assertEquals(1, adapter.bindRequests)
        assertEquals(photoGraph, adapter.boundGraph())
        assertTrue(
            session.recordedIntents.contains(
                SessionIntent.PreviewBindingStarted(
                    reason = "camera permission granted",
                    isRecovery = false
                )
            )
        )
    }

    @Test
    fun `multiple pending preview binds keep only the latest`() = runTest {
        val photoGraph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.BACK,
            enablePreviewSnapshots = true
        )
        val nightGraph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.FRONT,
            enablePreviewSnapshots = true
        )
        val session = FakeCameraSession()
        val adapter = FakeCameraDeviceAdapter()
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope
        )
        advanceUntilIdle()

        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.PHOTO,
                deviceGraph = photoGraph,
                reason = "session boot",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        session.emitEffect(
            SessionEffect.BindPreview(
                modeId = ModeId.NIGHT,
                deviceGraph = nightGraph,
                reason = "mode switched to night",
                isRecovery = false
            )
        )
        advanceUntilIdle()

        assertEquals(0, adapter.bindRequests)

        val lifecycleOwner = TestLifecycleOwner()
        val previewView = allocateInstance(PreviewView::class.java)
        coordinator.attachPreviewHost(lifecycleOwner, previewView)
        advanceUntilIdle()

        assertEquals(1, adapter.bindRequests)
        assertEquals(nightGraph, adapter.boundGraph())
    }

    @Test
    fun `capture feedback snapshot event is forwarded to session intent`() = runTest {
        val session = FakeCameraSession()
        val adapter = FakeCameraDeviceAdapter()
        val coordinatorScope = TestScope(StandardTestDispatcher(testScheduler))
        CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope
        )
        advanceUntilIdle()

        adapter.emit(
            DeviceEvent.CaptureFeedbackSnapshotAvailable(
                shotId = "shot-1",
                outputPath = "/tmp/feedback.jpg"
            )
        )
        advanceUntilIdle()

        val expectedIntent = SessionIntent.CaptureFeedbackSnapshotUpdated(
            shotId = "shot-1",
            outputPath = "/tmp/feedback.jpg"
        )
        assertTrue(
            session.recordedIntents.contains(expectedIntent),
            "Expected CaptureFeedbackSnapshotUpdated intent but got: ${session.recordedIntents}"
        )
    }

    private class FakeCameraSession(
        initialState: SessionState = defaultSessionState()
    ) : CameraSession {
        private val mutableState = MutableStateFlow(initialState)
        private val mutableEffects = MutableSharedFlow<SessionEffect>(extraBufferCapacity = 4)

        val recordedIntents = mutableListOf<SessionIntent>()

        override val state: StateFlow<SessionState> = mutableState.asStateFlow()
        override val effects: Flow<SessionEffect> = mutableEffects.asSharedFlow()

        override suspend fun dispatch(intent: SessionIntent) {
            recordedIntents += intent
        }

        suspend fun emitEffect(effect: SessionEffect) {
            mutableEffects.emit(effect)
        }

        fun updateState(state: SessionState) {
            mutableState.value = state
        }
    }

    private class FakeCameraDeviceAdapter(
        private val bindResults: ArrayDeque<BindResult> = ArrayDeque(),
        private val capabilitiesByLensFacing: Map<LensFacing, DeviceCapabilities> = emptyMap()
    ) : CameraDeviceAdapter {
        private val mutableEvents = MutableSharedFlow<DeviceEvent>(
            replay = 8,
            extraBufferCapacity = 8
        )

        var bindRequests: Int = 0
            private set
        val recordedCommands = mutableListOf<DeviceCommand>()
        private var boundGraph: DeviceGraphSpec? = null

        override val capabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT
        override val events: Flow<DeviceEvent> = mutableEvents.asSharedFlow()

        override fun capabilitiesFor(deviceGraph: DeviceGraphSpec): DeviceCapabilities {
            return capabilitiesByLensFacing[deviceGraph.preferredLensFacing] ?: capabilities
        }

        suspend fun emit(event: DeviceEvent) {
            mutableEvents.emit(event)
        }

        override suspend fun bindUseCases(
            lifecycleOwner: androidx.lifecycle.LifecycleOwner,
            previewView: androidx.camera.view.PreviewView,
            deviceGraph: DeviceGraphSpec
        ) {
            bindRequests += 1
            when (val result = bindResults.removeFirstOrNull() ?: BindResult.Success) {
                BindResult.Success -> boundGraph = deviceGraph
                is BindResult.Failure -> throw IllegalStateException(result.reason)
            }
        }

        override suspend fun dispatch(command: DeviceCommand) {
            recordedCommands += command
        }

        override suspend fun release() = Unit

        override fun boundGraph(): DeviceGraphSpec? = boundGraph
    }

    private class FakeRuntimeIssueMonitor : RuntimeIssueMonitor {
        private val mutableRuntimeIssues = MutableSharedFlow<DeviceRuntimeIssue>(
            replay = 8,
            extraBufferCapacity = 8
        )

        var attached = false
            private set

        override val runtimeIssues: Flow<DeviceRuntimeIssue> = mutableRuntimeIssues.asSharedFlow()

        override fun onPreviewHostAttached() {
            attached = true
        }

        override fun onPreviewHostDetached() {
            attached = false
        }

        suspend fun emit(issue: DeviceRuntimeIssue) {
            mutableRuntimeIssues.emit(issue)
        }
    }

    companion object {
        private val unsafe: Any by lazy {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val field = unsafeClass.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null)
        }

        private fun <T> allocateInstance(type: Class<T>): T {
            val allocateInstance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            @Suppress("UNCHECKED_CAST")
            return allocateInstance.invoke(unsafe, type) as T
        }

        private fun defaultSessionState(
            activeMode: ModeId = ModeId.NIGHT,
            modeId: ModeId = ModeId.NIGHT,
            deviceGraph: DeviceGraphSpec = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true
            ),
            recordingStatus: RecordingStatus = RecordingStatus.IDLE,
            activeDeviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT
        ): SessionState {
            return SessionState(
                lifecycle = SessionLifecycle.RUNNING,
                permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
                previewHostAvailable = true,
                previewStatus = PreviewStatus.ACTIVE,
                previewStatusDetail = null,
                activeMode = activeMode,
                availableModes = listOf(ModeId.PHOTO, ModeId.NIGHT, ModeId.VIDEO),
                captureStatus = CaptureStatus.IDLE,
                recordingStatus = recordingStatus,
                activeShot = null,
                activeDeviceCapabilities = activeDeviceCapabilities,
                activeDeviceGraph = deviceGraph,
                modeSnapshot = com.opencamera.core.mode.ModeSnapshot(
                    id = modeId,
                    uiSpec = com.opencamera.core.mode.ModeUiSpec(
                        title = modeId.name,
                        shutterLabel = "Capture ${modeId.name}"
                    ),
                    state = com.opencamera.core.mode.ModeState(
                        headline = "${modeId.name} mode active",
                        detail = "Ready"
                    )
                ),
                previewMetrics = PreviewMetrics(),
                presentation = SessionPresentationState(
                    lastAction = "Ready",
                    latestSavedMediaType = SavedMediaType.PHOTO
                )
            )
        }
    }

    private sealed interface BindResult {
        data object Success : BindResult

        data class Failure(val reason: String) : BindResult
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val delegateLifecycle = object : Lifecycle() {
            override fun addObserver(observer: LifecycleObserver) = Unit

            override fun removeObserver(observer: LifecycleObserver) = Unit

            override val currentState: State
                get() = State.RESUMED
        }

        override val lifecycle: Lifecycle
            get() = delegateLifecycle
    }
}
