package com.opencamera.core.session

import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.DeviceRuntimeIssueKind
import com.opencamera.core.device.PreviewMeteringPoint
import com.opencamera.core.device.PreviewMeteringResult
import com.opencamera.core.device.PreviewMeteringResultStatus
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewRecoverySessionProcessorTest {

    /** SharedFlow wrapper that records every emitted value. */
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

        override fun resetReplayCache() {
            delegate.resetReplayCache()
        }

        override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<SessionEffect>): Nothing {
            delegate.collect(collector)
        }
    }

    private class Harness(
        initialState: SessionState = runningState()
    ) {
        val state = MutableStateFlow(initialState)
        val effects = RecordingEffects()
        val trace = InMemorySessionTrace()
        val mutations = RecordingMutations(state)
        var countdownActive = false
        val countdownCancellations = mutableListOf<String>()
        val recordingElapsedTimerCancellations = mutableListOf<String>()
        val testScope = TestScope()

        lateinit var processor: PreviewRecoverySessionProcessor

        init {
            processor = PreviewRecoverySessionProcessor(
                state = state,
                effects = effects,
                trace = trace,
                linkRecorder = InMemoryPerformanceLinkRecorder(),
                mutations = mutations,
                countdownInProgress = { countdownActive },
                cancelPendingCountdown = { reason -> countdownCancellations.add(reason) },
                cancelRecordingElapsedTimer = { recordingElapsedTimerCancellations.add("cancelled") },
                scope = testScope,
                dispatch = { intent -> processor.process(intent) }
            )
        }

        suspend fun dispatch(intent: SessionIntent) = processor.process(intent)

        fun lastEffect(): SessionEffect? = effects.recorded.lastOrNull()

        fun allEffects(): List<SessionEffect> = effects.recorded.toList()
    }

    private class RecordingMutations(
        private val flow: MutableStateFlow<SessionState>
    ) : PreviewSessionMutations {
        val calls = mutableListOf<String>()

        override fun updatePreviewBlocked(reason: String) {
            calls.add("blocked:$reason")
            flow.value = flow.value.copy(
                previewStatus = PreviewStatus.BLOCKED,
                previewStatusDetail = reason,
                presentation = flow.value.presentation.copy(
                    lastAction = "Preview blocked",
                    lastError = reason
                )
            )
        }

        override fun updatePreviewStarting(reason: String, isRecovery: Boolean) {
            calls.add("starting:$reason,recovery=$isRecovery")
            flow.value = flow.value.copy(
                previewStatus = if (isRecovery) PreviewStatus.RECOVERING else PreviewStatus.STARTING,
                previewStatusDetail = reason,
                presentation = flow.value.presentation.copy(
                    lastAction = if (isRecovery) "Preview recovering" else "Preview starting",
                    lastError = null
                )
            )
        }

        override fun updatePreviewActive(firstFrameLatencyMillis: Long) {
            calls.add("active:${firstFrameLatencyMillis}")
            flow.value = flow.value.copy(
                previewStatus = PreviewStatus.ACTIVE,
                previewStatusDetail = null,
                presentation = flow.value.presentation.copy(
                    lastAction = "Preview active",
                    lastError = null
                )
            )
        }

        override fun updatePreviewError(reason: String, action: String) {
            calls.add("error:$reason,action=$action")
            flow.value = flow.value.copy(
                previewStatus = PreviewStatus.ERROR,
                previewStatusDetail = reason,
                presentation = flow.value.presentation.copy(
                    lastAction = action,
                    lastError = reason
                )
            )
        }

        override fun updatePreviewStopped(reason: String) {
            calls.add("stopped:$reason")
            val hasCameraPermission = flow.value.permissionState.cameraGranted
            flow.value = flow.value.copy(
                previewStatus = if (hasCameraPermission) PreviewStatus.IDLE else PreviewStatus.BLOCKED,
                previewStatusDetail = reason,
                presentation = flow.value.presentation.copy(
                    lastAction = "Preview stopped",
                    lastError = if (hasCameraPermission) null else "Camera permission missing"
                )
            )
        }

        override fun updatePreviewThumbnail(source: ThumbnailSource, generation: Int) {
            calls.add("thumbnail:gen=$generation")
        }

        override fun updateCaptureFeedback(shotId: String, outputPath: String) {
            calls.add("feedback:$shotId,$outputPath")
        }

        override fun updateDocumentBatchPreviewItem(shot: ShotRequest, outputPath: String) {
            calls.add("documentPreview:${shot.shotId},$outputPath")
        }

        override fun updatePreviewMeteringRequested(requestId: String, point: PreviewMeteringPoint) {
            calls.add("meteringRequested:$requestId")
        }

        override fun updatePreviewMeteringCompleted(result: PreviewMeteringResult) {
            calls.add("meteringCompleted:${result.requestId}")
        }

        override fun clearPreviewMeteringFeedback(requestId: String) {
            calls.add("clearMetering:$requestId")
            flow.value = flow.value.copy(
                presentation = flow.value.presentation.copy(
                    previewMeteringFeedback = null
                )
            )
        }

        override fun updatePreviewHostAttached(lastAction: String) {
            calls.add("hostAttached:$lastAction")
            flow.value = flow.value.copy(
                previewHostAvailable = true,
                presentation = flow.value.presentation.copy(
                    lastAction = lastAction,
                    lastError = null
                )
            )
        }

        override fun updatePreviewHostDetached(reason: String, hasPermission: Boolean) {
            calls.add("hostDetached:$reason,hasPermission=$hasPermission")
            flow.value = flow.value.copy(
                previewHostAvailable = false,
                previewStatus = if (hasPermission) PreviewStatus.IDLE else PreviewStatus.BLOCKED,
                previewStatusDetail = reason,
                presentation = flow.value.presentation.copy(
                    lastAction = "Preview host detached",
                    lastError = if (hasPermission) null else "Camera permission missing"
                )
            )
        }

        override fun updatePreviewSurfaceLost(reason: String) {
            calls.add("surfaceLost:$reason")
            flow.value = flow.value.copy(
                previewStatus = PreviewStatus.SURFACE_LOST,
                previewStatusDetail = reason,
                presentation = flow.value.presentation.copy(
                    lastAction = "Preview surface lost",
                    lastError = reason
                )
            )
        }

        override fun updatePreviewRuntimeError(detail: String, action: String) {
            calls.add("runtimeError:$detail,action=$action")
            flow.value = flow.value.copy(
                previewStatus = PreviewStatus.ERROR,
                previewStatusDetail = detail,
                presentation = flow.value.presentation.copy(
                    lastAction = action,
                    lastError = detail
                )
            )
        }

        override fun updatePreviewMetrics(metrics: PreviewMetrics) {
            calls.add("metrics:bind=${metrics.bindCount},recovery=${metrics.recoveryCount}")
            flow.value = flow.value.copy(previewMetrics = metrics)
        }
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

        private fun testShotRequest(shotId: String = "shot-1") = ShotRequest(
            shotId = shotId,
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.KEEP_PREVIEW_FRAME,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
    }

    @Test
    fun `PreviewHostAttached when already available skips state update and requests binding`() = runTest {
        val harness = Harness(runningState().copy(previewHostAvailable = true))
        harness.dispatch(SessionIntent.PreviewHostAttached)

        assertTrue(harness.state.value.previewHostAvailable)
        val effect = harness.lastEffect()
        assertTrue(effect is SessionEffect.BindPreview)
        assertEquals("preview host reattached", effect.reason)
        assertFalse(effect.isRecovery)
    }

    @Test
    fun `PreviewHostAttached when not available sets host available and requests binding`() = runTest {
        val harness = Harness(runningState().copy(previewHostAvailable = false))
        harness.dispatch(SessionIntent.PreviewHostAttached)

        assertTrue(harness.state.value.previewHostAvailable)
        assertEquals("Preview host attached", harness.state.value.presentation.lastAction)
        val effect = harness.lastEffect()
        assertTrue(effect is SessionEffect.BindPreview)
        assertEquals("preview host attached", effect.reason)
    }

    @Test
    fun `PreviewHostAttached with pending recovery uses recovery action text`() = runTest {
        val harness = Harness(runningState().copy(
            previewHostAvailable = false,
            lifecycle = SessionLifecycle.RUNNING
        ))
        harness.dispatch(SessionIntent.PreviewHostDetached("test-detach"))
        harness.dispatch(SessionIntent.PreviewHostAttached)

        assertTrue(harness.state.value.previewHostAvailable)
        assertEquals("Preview host reattached", harness.state.value.presentation.lastAction)
    }

    @Test
    fun `PreviewHostDetached sets host unavailable and requests unbind`() = runTest {
        val harness = Harness()
        harness.dispatch(SessionIntent.PreviewHostDetached("test-reason"))

        assertFalse(harness.state.value.previewHostAvailable)
        assertEquals("Preview host detached", harness.state.value.presentation.lastAction)
        val effect = harness.lastEffect()
        assertTrue(effect is SessionEffect.UnbindPreview)
        assertEquals("test-reason", effect.reason)
        assertTrue(effect.clearHost)
    }

    @Test
    fun `PreviewHostDetached cancels countdown when active`() = runTest {
        val harness = Harness()
        harness.countdownActive = true
        harness.dispatch(SessionIntent.PreviewHostDetached("rotation"))

        assertEquals(1, harness.countdownCancellations.size)
        assertTrue(harness.countdownCancellations[0].contains("preview host detached"))
    }

    @Test
    fun `PreviewHostDetached sets BLOCKED when camera permission missing`() = runTest {
        val harness = Harness(runningState().copy(
            permissionState = PermissionState(cameraGranted = false)
        ))
        harness.dispatch(SessionIntent.PreviewHostDetached("test"))

        assertEquals(PreviewStatus.BLOCKED, harness.state.value.previewStatus)
        assertEquals("Camera permission missing", harness.state.value.presentation.lastError)
    }

    @Test
    fun `PreviewHostDetached sets IDLE when camera permission granted`() = runTest {
        val harness = Harness(runningState().copy(
            permissionState = PermissionState(cameraGranted = true)
        ))
        harness.dispatch(SessionIntent.PreviewHostDetached("test"))

        assertEquals(PreviewStatus.IDLE, harness.state.value.previewStatus)
        assertNull(harness.state.value.presentation.lastError)
    }

    @Test
    fun `PreviewHostDetached stores recovery reason when RUNNING`() = runTest {
        val harness = Harness(runningState().copy(
            lifecycle = SessionLifecycle.RUNNING,
            previewHostAvailable = true
        ))
        harness.dispatch(SessionIntent.PreviewHostDetached("rotation"))

        harness.dispatch(SessionIntent.PreviewHostAttached)
        val effects = harness.allEffects()
        val bindEffect = effects.filterIsInstance<SessionEffect.BindPreview>().lastOrNull()
        assertNotNull(bindEffect)
        assertTrue(bindEffect.isRecovery)
        assertTrue(bindEffect.reason.contains("recover after preview host detached"))
    }

    @Test
    fun `recovery not attempted when not RUNNING`() = runTest {
        val harness = Harness(runningState().copy(
            lifecycle = SessionLifecycle.STOPPED,
            previewHostAvailable = false
        ))
        harness.dispatch(SessionIntent.PreviewHostDetached("test"))
        harness.state.value = harness.state.value.copy(lifecycle = SessionLifecycle.STOPPED)
        harness.dispatch(SessionIntent.PreviewHostAttached)

        val effects = harness.allEffects()
        val bindEffects = effects.filterIsInstance<SessionEffect.BindPreview>()
        assertTrue(bindEffects.isEmpty())
    }

    @Test
    fun `recovery not attempted when recording`() = runTest {
        val harness = Harness(runningState().copy(
            previewHostAvailable = true,
            recordingStatus = RecordingStatus.RECORDING
        ))
        harness.dispatch(SessionIntent.PreviewHostDetached("test"))
        harness.state.value = harness.state.value.copy(
            previewHostAvailable = true,
            recordingStatus = RecordingStatus.RECORDING
        )
        harness.dispatch(SessionIntent.PreviewHostAttached)

        val effects = harness.allEffects()
        val bindEffects = effects.filterIsInstance<SessionEffect.BindPreview>()
        assertTrue(bindEffects.isEmpty())
    }

    @Test
    fun `PreviewBindingStarted updates preview status and metrics`() = runTest {
        val harness = Harness(runningState().copy(
            previewMetrics = PreviewMetrics(bindCount = 2, recoveryCount = 1)
        ))
        harness.dispatch(SessionIntent.PreviewBindingStarted("test-bind", isRecovery = false))

        assertTrue(harness.mutations.calls.contains("starting:test-bind,recovery=false"))
        assertEquals(3, harness.state.value.previewMetrics.bindCount)
        assertEquals(1, harness.state.value.previewMetrics.recoveryCount)
    }

    @Test
    fun `PreviewBindingStarted with recovery increments recovery count`() = runTest {
        val harness = Harness(runningState().copy(
            previewMetrics = PreviewMetrics(bindCount = 2, recoveryCount = 1, consecutiveRecoveryCount = 1)
        ))
        harness.dispatch(SessionIntent.PreviewBindingStarted("recover", isRecovery = true))

        assertEquals(3, harness.state.value.previewMetrics.bindCount)
        assertEquals(2, harness.state.value.previewMetrics.recoveryCount)
        assertEquals(2, harness.state.value.previewMetrics.consecutiveRecoveryCount)
    }

    @Test
    fun `PreviewBindingStarted blocks when no camera permission`() = runTest {
        val harness = Harness(runningState().copy(
            permissionState = PermissionState(cameraGranted = false)
        ))
        harness.dispatch(SessionIntent.PreviewBindingStarted("test", false))

        assertTrue(harness.mutations.calls.contains("blocked:test"))
    }

    @Test
    fun `PreviewBindingStarted records trace when blocked`() = runTest {
        val harness = Harness(runningState().copy(
            permissionState = PermissionState(cameraGranted = false)
        ))
        harness.dispatch(SessionIntent.PreviewBindingStarted("test", false))

        assertTrue(harness.trace.snapshot().any { it.name == "preview.blocked" })
    }

    @Test
    fun `PreviewFirstFrameAvailable updates metrics and sets ACTIVE`() = runTest {
        val harness = Harness(runningState().copy(
            previewMetrics = PreviewMetrics()
        ))
        harness.dispatch(SessionIntent.PreviewFirstFrameAvailable(150L))

        assertTrue(harness.mutations.calls.contains("active:150"))
        assertEquals(150L, harness.state.value.previewMetrics.lastFirstFrameLatencyMillis)
        assertEquals(150L, harness.state.value.previewMetrics.bestFirstFrameLatencyMillis)
        assertEquals(150L, harness.state.value.previewMetrics.worstFirstFrameLatencyMillis)
    }

    @Test
    fun `PreviewFirstFrameAvailable tracks best and worst latency`() = runTest {
        val harness = Harness(runningState().copy(
            previewMetrics = PreviewMetrics(
                bestFirstFrameLatencyMillis = 100L,
                worstFirstFrameLatencyMillis = 200L
            )
        ))
        harness.dispatch(SessionIntent.PreviewFirstFrameAvailable(150L))

        assertEquals(100L, harness.state.value.previewMetrics.bestFirstFrameLatencyMillis)
        assertEquals(200L, harness.state.value.previewMetrics.worstFirstFrameLatencyMillis)
    }

    @Test
    fun `PreviewFirstFrameAvailable updates best when lower`() = runTest {
        val harness = Harness(runningState().copy(
            previewMetrics = PreviewMetrics(bestFirstFrameLatencyMillis = 200L)
        ))
        harness.dispatch(SessionIntent.PreviewFirstFrameAvailable(100L))

        assertEquals(100L, harness.state.value.previewMetrics.bestFirstFrameLatencyMillis)
    }

    @Test
    fun `PreviewSnapshotUpdated updates thumbnail`() = runTest {
        val harness = Harness()
        val source = ThumbnailSource.PreviewSnapshot("/tmp/preview.jpg")
        harness.dispatch(SessionIntent.PreviewSnapshotUpdated(source, generation = 1))

        assertTrue(harness.mutations.calls.contains("thumbnail:gen=1"))
    }

    @Test
    fun `PreviewSnapshotUpdated ignores stale generation`() = runTest {
        val harness = Harness(runningState().copy(
            presentation = SessionPresentationState(previewSnapshotGeneration = 5)
        ))
        harness.dispatch(SessionIntent.PreviewSnapshotUpdated(
            ThumbnailSource.PreviewSnapshot("/tmp/a.jpg"),
            generation = 3
        ))

        assertFalse(harness.mutations.calls.any { it.startsWith("thumbnail:") })
    }

    @Test
    fun `PreviewSnapshotUpdated ignores when SavedMedia thumbnail exists`() = runTest {
        val harness = Harness(runningState().copy(
            presentation = SessionPresentationState(
                latestThumbnailSource = ThumbnailSource.SavedMedia("/saved.jpg")
            )
        ))
        harness.dispatch(SessionIntent.PreviewSnapshotUpdated(
            ThumbnailSource.PreviewSnapshot("/tmp/a.jpg"),
            generation = 1
        ))

        assertFalse(harness.mutations.calls.any { it.startsWith("thumbnail:") })
    }

    @Test
    fun `CaptureFeedbackSnapshotUpdated updates feedback for active shot`() = runTest {
        val shot = testShotRequest("shot-1")
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.dispatch(SessionIntent.CaptureFeedbackSnapshotUpdated("shot-1", "/tmp/feedback.jpg"))

        assertTrue(harness.mutations.calls.contains("feedback:shot-1,/tmp/feedback.jpg"))
    }

    @Test
    fun `CaptureFeedbackSnapshotUpdated updates document batch preview item in document mode`() = runTest {
        val shot = testShotRequest("doc-shot-1")
        val harness = Harness(runningState().copy(
            activeMode = ModeId.DOCUMENT,
            availableModes = listOf(ModeId.DOCUMENT),
            activeShot = shot,
            presentation = SessionPresentationState(
                documentBatch = DocumentBatchState.active("batch-1")
            )
        ))

        harness.dispatch(SessionIntent.CaptureFeedbackSnapshotUpdated("doc-shot-1", "/tmp/doc-preview.jpg"))

        assertTrue(harness.mutations.calls.contains("documentPreview:doc-shot-1,/tmp/doc-preview.jpg"))
    }

    @Test
    fun `CaptureFeedbackSnapshotUpdated skips when no active shot`() = runTest {
        val harness = Harness(runningState().copy(activeShot = null))
        harness.dispatch(SessionIntent.CaptureFeedbackSnapshotUpdated("shot-1", "/tmp/f.jpg"))

        assertFalse(harness.mutations.calls.any { it.startsWith("feedback:") })
    }

    @Test
    fun `CaptureFeedbackSnapshotUpdated skips when shotId mismatch`() = runTest {
        val shot = testShotRequest("shot-1")
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.dispatch(SessionIntent.CaptureFeedbackSnapshotUpdated("shot-2", "/tmp/f.jpg"))

        assertFalse(harness.mutations.calls.any { it.startsWith("feedback:") })
    }

    @Test
    fun `PreviewSurfaceLost sets SURFACE_LOST and requests recovery binding`() = runTest {
        val harness = Harness()
        harness.dispatch(SessionIntent.PreviewSurfaceLost("surface destroyed"))

        assertEquals(PreviewStatus.SURFACE_LOST, harness.state.value.previewStatus)
        assertEquals("surface destroyed", harness.state.value.previewStatusDetail)
        assertEquals("Preview surface lost", harness.state.value.presentation.lastAction)
        assertEquals("surface destroyed", harness.state.value.presentation.lastError)

        val effect = harness.lastEffect()
        assertTrue(effect is SessionEffect.BindPreview)
        assertTrue(effect.isRecovery)
        assertTrue(effect.reason.contains("recover after surface destroyed"))
    }

    @Test
    fun `PreviewSurfaceLost during recording delegates to handlePreviewError`() = runTest {
        val harness = Harness(runningState().copy(
            recordingStatus = RecordingStatus.RECORDING
        ))
        harness.dispatch(SessionIntent.PreviewSurfaceLost("surface gone"))

        // Delegates to handlePreviewError which uses mutations.updatePreviewError
        assertTrue(harness.mutations.calls.any { it.startsWith("error:") })
    }

    @Test
    fun `PreviewSurfaceLost cancels countdown when active`() = runTest {
        val harness = Harness()
        harness.countdownActive = true
        harness.dispatch(SessionIntent.PreviewSurfaceLost("test"))

        assertEquals(1, harness.countdownCancellations.size)
    }

    @Test
    fun `PreviewError sets ERROR status and attempts recovery when conditions met`() = runTest {
        val harness = Harness()
        harness.dispatch(SessionIntent.PreviewError("camera error"))

        // updatePreviewError is called via mutations (stub records call)
        assertTrue(harness.mutations.calls.contains("error:camera error,action=Preview error, attempting recovery"))

        val effect = harness.lastEffect()
        assertTrue(effect is SessionEffect.BindPreview)
        assertTrue(effect.isRecovery)
    }

    @Test
    fun `PreviewError skips recovery when recording`() = runTest {
        val harness = Harness(runningState().copy(
            recordingStatus = RecordingStatus.RECORDING
        ))
        harness.dispatch(SessionIntent.PreviewError("error"))

        assertTrue(harness.mutations.calls.contains("error:error,action=Preview error"))
        val effect = harness.lastEffect()
        assertFalse(effect is SessionEffect.BindPreview)
    }

    @Test
    fun `PreviewError cancels countdown when active`() = runTest {
        val harness = Harness()
        harness.countdownActive = true
        harness.dispatch(SessionIntent.PreviewError("err"))

        assertEquals(1, harness.countdownCancellations.size)
    }

    @Test
    fun `PreviewRuntimeIssue sets ERROR and attempts recovery for recoverable issue`() = runTest {
        val harness = Harness()
        val issue = DeviceRuntimeIssue(
            kind = DeviceRuntimeIssueKind.BIND_FAILURE,
            reason = "camera disconnected",
            isRecoverable = true
        )
        harness.dispatch(SessionIntent.PreviewRuntimeIssue(issue))

        assertEquals(PreviewStatus.ERROR, harness.state.value.previewStatus)
        val effect = harness.lastEffect()
        assertTrue(effect is SessionEffect.BindPreview)
        assertTrue(effect.isRecovery)
    }

    @Test
    fun `PreviewRuntimeIssue does not attempt recovery for non-recoverable issue`() = runTest {
        val harness = Harness()
        val issue = DeviceRuntimeIssue(
            kind = DeviceRuntimeIssueKind.CAMERA_FATAL,
            reason = "fatal",
            isRecoverable = false
        )
        harness.dispatch(SessionIntent.PreviewRuntimeIssue(issue))

        assertEquals(PreviewStatus.ERROR, harness.state.value.previewStatus)
        assertFalse(harness.allEffects().any { it is SessionEffect.BindPreview })
    }

    @Test
    fun `PreviewRuntimeIssue does not re-recover when already recovering`() = runTest {
        val harness = Harness(runningState().copy(
            previewStatus = PreviewStatus.RECOVERING
        ))
        val issue = DeviceRuntimeIssue(
            kind = DeviceRuntimeIssueKind.BIND_FAILURE,
            reason = "again",
            isRecoverable = true
        )
        harness.dispatch(SessionIntent.PreviewRuntimeIssue(issue))

        assertEquals(PreviewStatus.ERROR, harness.state.value.previewStatus)
        assertFalse(harness.allEffects().any { it is SessionEffect.BindPreview })
    }

    @Test
    fun `PreviewRuntimeIssue cancels countdown when active`() = runTest {
        val harness = Harness()
        harness.countdownActive = true
        val issue = DeviceRuntimeIssue(
            kind = DeviceRuntimeIssueKind.BIND_FAILURE,
            reason = "test",
            isRecoverable = false
        )
        harness.dispatch(SessionIntent.PreviewRuntimeIssue(issue))

        assertEquals(1, harness.countdownCancellations.size)
    }

    @Test
    fun `PreviewStopped sets IDLE when camera permission granted`() = runTest {
        val harness = Harness(runningState().copy(
            permissionState = PermissionState(cameraGranted = true)
        ))
        harness.dispatch(SessionIntent.PreviewStopped("mode switch"))

        assertEquals(PreviewStatus.IDLE, harness.state.value.previewStatus)
        assertEquals("mode switch", harness.state.value.previewStatusDetail)
        assertEquals("Preview stopped", harness.state.value.presentation.lastAction)
        assertNull(harness.state.value.presentation.lastError)
    }

    @Test
    fun `PreviewStopped sets BLOCKED when camera permission missing`() = runTest {
        val harness = Harness(runningState().copy(
            permissionState = PermissionState(cameraGranted = false)
        ))
        harness.dispatch(SessionIntent.PreviewStopped("perm revoked"))

        assertEquals(PreviewStatus.BLOCKED, harness.state.value.previewStatus)
        assertEquals("Camera permission missing", harness.state.value.presentation.lastError)
    }

    @Test
    fun `PreviewStopped cancels countdown when active`() = runTest {
        val harness = Harness()
        harness.countdownActive = true
        harness.dispatch(SessionIntent.PreviewStopped("test"))

        assertEquals(1, harness.countdownCancellations.size)
    }

    @Test
    fun `PreviewTapToFocus emits ApplyPreviewMetering when preview active`() = runTest {
        val harness = Harness(runningState().copy(
            previewStatus = PreviewStatus.ACTIVE
        ))
        harness.dispatch(SessionIntent.PreviewTapToFocus(0.5f, 0.4f))

        val effect = harness.lastEffect()
        assertTrue(effect is SessionEffect.ApplyPreviewMetering)
        assertEquals(0.5f, effect.request.point.normalizedX, 0.01f)
        assertEquals(0.4f, effect.request.point.normalizedY, 0.01f)
        assertTrue(harness.mutations.calls.any { it.startsWith("meteringRequested:") })
    }

    @Test
    fun `PreviewTapToFocus ignored when preview not active`() = runTest {
        val harness = Harness(runningState().copy(
            previewStatus = PreviewStatus.IDLE
        ))
        harness.dispatch(SessionIntent.PreviewTapToFocus(0.5f, 0.5f))

        assertFalse(harness.allEffects().any { it is SessionEffect.ApplyPreviewMetering })
    }

    @Test
    fun `PreviewTapToFocus ignored when no camera permission`() = runTest {
        val harness = Harness(runningState().copy(
            previewStatus = PreviewStatus.ACTIVE,
            permissionState = PermissionState(cameraGranted = false)
        ))
        harness.dispatch(SessionIntent.PreviewTapToFocus(0.5f, 0.5f))

        assertFalse(harness.allEffects().any { it is SessionEffect.ApplyPreviewMetering })
    }

    @Test
    fun `PreviewTapToFocus ignored when host not available`() = runTest {
        val harness = Harness(runningState().copy(
            previewStatus = PreviewStatus.ACTIVE,
            previewHostAvailable = false
        ))
        harness.dispatch(SessionIntent.PreviewTapToFocus(0.5f, 0.5f))

        assertFalse(harness.allEffects().any { it is SessionEffect.ApplyPreviewMetering })
    }

    @Test
    fun `PreviewTapToFocus increments metering counter`() = runTest {
        val harness = Harness(runningState().copy(
            previewStatus = PreviewStatus.ACTIVE
        ))
        harness.dispatch(SessionIntent.PreviewTapToFocus(0.5f, 0.5f))
        harness.dispatch(SessionIntent.PreviewTapToFocus(0.6f, 0.6f))

        val effects = harness.allEffects().filterIsInstance<SessionEffect.ApplyPreviewMetering>()
        assertEquals(2, effects.size)
        assertEquals("meter-1", effects[0].request.requestId)
        assertEquals("meter-2", effects[1].request.requestId)
    }

    @Test
    fun `PreviewMeteringCompleted updates feedback status`() = runTest {
        val harness = Harness(runningState().copy(
            presentation = SessionPresentationState(
                previewMeteringFeedback = PreviewMeteringFeedback(
                    requestId = "meter-1",
                    normalizedX = 0.5f,
                    normalizedY = 0.4f,
                    status = PreviewMeteringFeedbackStatus.REQUESTED
                )
            )
        ))
        harness.dispatch(SessionIntent.PreviewMeteringCompleted(
            PreviewMeteringResult(
                requestId = "meter-1",
                point = PreviewMeteringPoint(0.5f, 0.4f),
                status = PreviewMeteringResultStatus.SUCCEEDED
            )
        ))

        assertTrue(harness.mutations.calls.contains("meteringCompleted:meter-1"))
    }

    @Test
    fun `PreviewMeteringCompleted ignores stale result`() = runTest {
        val harness = Harness(runningState().copy(
            presentation = SessionPresentationState(
                previewMeteringFeedback = PreviewMeteringFeedback(
                    requestId = "meter-2",
                    normalizedX = 0.5f,
                    normalizedY = 0.4f,
                    status = PreviewMeteringFeedbackStatus.REQUESTED
                )
            )
        ))
        harness.dispatch(SessionIntent.PreviewMeteringCompleted(
            PreviewMeteringResult(
                requestId = "meter-1",
                point = PreviewMeteringPoint(0.5f, 0.4f),
                status = PreviewMeteringResultStatus.SUCCEEDED
            )
        ))

        assertFalse(harness.mutations.calls.any { it.startsWith("meteringCompleted:") })
    }

    @Test
    fun `PreviewMeteringCompleted ignores when no feedback pending`() = runTest {
        val harness = Harness(runningState().copy(
            presentation = SessionPresentationState(previewMeteringFeedback = null)
        ))
        harness.dispatch(SessionIntent.PreviewMeteringCompleted(
            PreviewMeteringResult(
                requestId = "meter-1",
                point = PreviewMeteringPoint(0.5f, 0.4f),
                status = PreviewMeteringResultStatus.SUCCEEDED
            )
        ))

        assertFalse(harness.mutations.calls.any { it.startsWith("meteringCompleted:") })
    }

    @Test
    fun `requestPreviewBinding does not emit when not RUNNING`() = runTest {
        val harness = Harness(runningState().copy(
            lifecycle = SessionLifecycle.STOPPED,
            previewHostAvailable = false
        ))
        harness.dispatch(SessionIntent.PreviewHostAttached)

        assertFalse(harness.allEffects().any { it is SessionEffect.BindPreview })
    }

    @Test
    fun `requestPreviewBinding does not emit when no camera permission`() = runTest {
        val harness = Harness(runningState().copy(
            permissionState = PermissionState(cameraGranted = false),
            previewHostAvailable = false
        ))
        harness.dispatch(SessionIntent.PreviewHostAttached)

        assertFalse(harness.allEffects().any { it is SessionEffect.BindPreview })
    }

    @Test
    fun `requestPreviewBinding does not emit when recording`() = runTest {
        val harness = Harness(runningState().copy(
            recordingStatus = RecordingStatus.RECORDING,
            previewHostAvailable = false
        ))
        harness.dispatch(SessionIntent.PreviewHostAttached)

        assertFalse(harness.allEffects().any { it is SessionEffect.BindPreview })
    }

    @Test
    fun `recovery skipped when activeShot present`() = runTest {
        val shot = testShotRequest("shot-1")
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.dispatch(SessionIntent.PreviewError("error"))

        assertFalse(harness.allEffects().any { it is SessionEffect.BindPreview })
    }

    @Test
    fun `process throws on unexpected intent`() = runTest {
        val harness = Harness()
        var threw = false
        try {
            harness.dispatch(SessionIntent.Boot)
        } catch (_: IllegalStateException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `LatestGalleryMediaLoaded sets thumbnail source and media type`() = runTest {
        val harness = Harness()
        val source = ThumbnailSource.SavedMedia(
            outputPath = "Movies/video.mp4",
            renderUri = "content://media/external/video/media/99"
        )
        harness.dispatch(SessionIntent.LatestGalleryMediaLoaded(source, SavedMediaType.VIDEO))

        assertTrue(harness.mutations.calls.any { it.startsWith("thumbnail:") })
        assertEquals(SavedMediaType.VIDEO, harness.state.value.presentation.latestSavedMediaType)
    }

    @Test
    fun `LatestGalleryMediaLoaded ignored when saved media already exists`() = runTest {
        val harness = Harness(runningState().copy(
            presentation = SessionPresentationState(
                latestThumbnailSource = ThumbnailSource.SavedMedia("/saved.jpg")
            )
        ))
        harness.dispatch(SessionIntent.LatestGalleryMediaLoaded(
            ThumbnailSource.SavedMedia("Movies/video.mp4", "content://media/external/video/media/99"),
            SavedMediaType.VIDEO
        ))

        assertFalse(harness.mutations.calls.any { it.startsWith("thumbnail:") })
        assertNull(harness.state.value.presentation.latestSavedMediaType)
    }

    @Test
    fun `LatestGalleryImageLoaded does not overwrite existing saved media`() = runTest {
        val harness = Harness(runningState().copy(
            presentation = SessionPresentationState(
                latestThumbnailSource = ThumbnailSource.SavedMedia(
                    outputPath = "Pictures/existing.jpg",
                    renderUri = "content://media/external/images/media/10"
                )
            )
        ))
        harness.dispatch(SessionIntent.LatestGalleryImageLoaded(
            ThumbnailSource.SavedMedia("Pictures/new.jpg", "content://media/external/images/media/20")
        ))

        assertFalse(harness.mutations.calls.any { it.startsWith("thumbnail:") })
    }

    @Test
    fun `recovery stops after MAX_CONSECUTIVE_RECOVERIES exhaustion`() = runTest {
        val harness = Harness(runningState().copy(
            previewMetrics = PreviewMetrics(consecutiveRecoveryCount = PreviewRecoverySessionProcessor.MAX_CONSECUTIVE_RECOVERIES)
        ))
        harness.dispatch(SessionIntent.PreviewError("persistent failure"))

        // Should not attempt recovery
        assertFalse(harness.allEffects().any { it is SessionEffect.BindPreview })
        assertEquals("Preview error", harness.state.value.presentation.lastAction)
    }

    @Test
    fun `recovery attempted when count is below limit`() = runTest {
        val harness = Harness(runningState().copy(
            previewMetrics = PreviewMetrics(consecutiveRecoveryCount = 1)
        ))
        harness.dispatch(SessionIntent.PreviewError("transient error"))

        assertTrue(harness.allEffects().any { it is SessionEffect.BindPreview })
    }

    @Test
    fun `PreviewSurfaceLost during recording emits StopActiveShot`() = runTest {
        val shot = testShotRequest("recording-shot-1")
        val harness = Harness(runningState().copy(
            recordingStatus = RecordingStatus.RECORDING,
            activeShot = shot
        ))
        harness.dispatch(SessionIntent.PreviewSurfaceLost("surface gone"))

        val stopEffects = harness.allEffects().filterIsInstance<SessionEffect.StopActiveShot>()
        assertEquals(1, stopEffects.size)
        assertEquals("recording-shot-1", stopEffects[0].shotId)
    }

    @Test
    fun `PreviewSurfaceLost during recording does not emit StopActiveShot when no active shot`() = runTest {
        val harness = Harness(runningState().copy(
            recordingStatus = RecordingStatus.RECORDING,
            activeShot = null
        ))
        harness.dispatch(SessionIntent.PreviewSurfaceLost("surface gone"))

        val stopEffects = harness.allEffects().filterIsInstance<SessionEffect.StopActiveShot>()
        assertTrue(stopEffects.isEmpty())
    }

    @Test
    fun `PreviewSurfaceLost during recording cancels elapsed timer`() = runTest {
        val harness = Harness(runningState().copy(
            recordingStatus = RecordingStatus.RECORDING
        ))
        harness.dispatch(SessionIntent.PreviewSurfaceLost("surface gone"))

        assertTrue(harness.recordingElapsedTimerCancellations.isNotEmpty())
    }

    @Test
    fun `PreviewRuntimeIssue cancels recording elapsed timer when recording`() = runTest {
        val harness = Harness(runningState().copy(
            recordingStatus = RecordingStatus.RECORDING
        ))
        val issue = DeviceRuntimeIssue(
            kind = DeviceRuntimeIssueKind.BIND_FAILURE,
            reason = "camera lost",
            isRecoverable = true
        )
        harness.dispatch(SessionIntent.PreviewRuntimeIssue(issue))

        assertEquals(1, harness.recordingElapsedTimerCancellations.size)
    }

    @Test
    fun `PreviewRuntimeIssue does not cancel timer when not recording`() = runTest {
        val harness = Harness(runningState().copy(
            recordingStatus = RecordingStatus.IDLE
        ))
        val issue = DeviceRuntimeIssue(
            kind = DeviceRuntimeIssueKind.BIND_FAILURE,
            reason = "camera lost",
            isRecoverable = true
        )
        harness.dispatch(SessionIntent.PreviewRuntimeIssue(issue))

        assertTrue(harness.recordingElapsedTimerCancellations.isEmpty())
    }

    @Test
    fun `consecutiveRecoveryCount increments on recovery binding`() = runTest {
        val harness = Harness(runningState().copy(
            previewMetrics = PreviewMetrics(bindCount = 1, recoveryCount = 0, consecutiveRecoveryCount = 0)
        ))
        harness.dispatch(SessionIntent.PreviewBindingStarted("recover", isRecovery = true))

        assertEquals(1, harness.state.value.previewMetrics.consecutiveRecoveryCount)
        assertEquals(1, harness.state.value.previewMetrics.recoveryCount)
    }

    @Test
    fun `consecutiveRecoveryCount does not increment on non-recovery binding`() = runTest {
        val harness = Harness(runningState().copy(
            previewMetrics = PreviewMetrics(bindCount = 1, consecutiveRecoveryCount = 2)
        ))
        harness.dispatch(SessionIntent.PreviewBindingStarted("initial", isRecovery = false))

        assertEquals(2, harness.state.value.previewMetrics.consecutiveRecoveryCount)
    }

    @Test
    fun `consecutiveRecoveryCount resets to zero on first frame available`() = runTest {
        val harness = Harness(runningState().copy(
            previewMetrics = PreviewMetrics(consecutiveRecoveryCount = 2)
        ))
        harness.dispatch(SessionIntent.PreviewFirstFrameAvailable(120L))

        assertEquals(0, harness.state.value.previewMetrics.consecutiveRecoveryCount)
    }

    @Test
    fun `recovery exhaustion records trace and action text`() = runTest {
        val harness = Harness(runningState().copy(
            previewMetrics = PreviewMetrics(consecutiveRecoveryCount = 3)
        ))
        harness.dispatch(SessionIntent.PreviewError("something broke"))

        assertEquals("Preview error", harness.state.value.presentation.lastAction)
        val traceEntries = harness.trace.snapshot()
        assertTrue(traceEntries.any { it.name == "preview.error" })
    }
}
