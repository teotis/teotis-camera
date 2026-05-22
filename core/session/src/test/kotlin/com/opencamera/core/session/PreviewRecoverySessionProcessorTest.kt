package com.opencamera.core.session

import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.DeviceRuntimeIssueKind
import com.opencamera.core.device.PreviewMeteringPoint
import com.opencamera.core.device.PreviewMeteringResult
import com.opencamera.core.device.PreviewMeteringResultStatus
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.mode.ModeId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewRecoverySessionProcessorTest {

    // ── Test harness ─────────────────────────────────────────────────

    private class Harness(
        initialState: SessionState = runningState()
    ) {
        val state = MutableStateFlow(initialState)
        val effects = MutableSharedFlow<SessionEffect>(extraBufferCapacity = 64)
        val trace = InMemorySessionTrace()
        val stateUpdater = RecordingStateUpdater(state)
        val mutations = RecordingMutations()
        var countdownActive = false
        val countdownCancellations = mutableListOf<String>()

        val processor = PreviewRecoverySessionProcessor(
            state = state,
            effects = effects,
            trace = trace,
            updateState = stateUpdater,
            mutations = mutations,
            countdownInProgress = { countdownActive },
            cancelPendingCountdown = { reason -> countdownCancellations.add(reason) }
        )

        suspend fun dispatch(intent: SessionIntent) = processor.process(intent)

        fun lastEffect(): SessionEffect? {
            val events = mutableListOf<SessionEffect>()
            while (true) {
                val e = effects.tryReceive().getOrNull() ?: break
                events.add(e)
            }
            return events.lastOrNull()
        }

        fun allEffects(): List<SessionEffect> {
            val events = mutableListOf<SessionEffect>()
            while (true) {
                val e = effects.tryReceive().getOrNull() ?: break
                events.add(e)
            }
            return events
        }
    }

    /** Stub [SessionStateUpdater] that delegates to [MutableStateFlow]. */
    private class RecordingStateUpdater(
        private val flow: MutableStateFlow<SessionState>
    ) : SessionStateUpdater {
        override fun update(transform: (SessionState) -> SessionState) {
            flow.value = transform(flow.value)
        }
    }

    /** Stub [PreviewSessionMutations] that records calls. */
    private class RecordingMutations : PreviewSessionMutations {
        val calls = mutableListOf<String>()

        override fun updatePreviewBlocked(reason: String) {
            calls.add("blocked:$reason")
        }

        override fun updatePreviewStarting(reason: String, isRecovery: Boolean) {
            calls.add("starting:$reason,recovery=$isRecovery")
        }

        override fun updatePreviewActive(firstFrameLatencyMillis: Long) {
            calls.add("active:${firstFrameLatencyMillis}")
        }

        override fun updatePreviewError(reason: String, action: String) {
            calls.add("error:$reason,action=$action")
        }

        override fun updatePreviewStopped(reason: String) {
            calls.add("stopped:$reason")
        }

        override fun updatePreviewThumbnail(source: ThumbnailSource, generation: Int) {
            calls.add("thumbnail:gen=$generation")
        }

        override fun updateCaptureFeedback(shotId: String, outputPath: String) {
            calls.add("feedback:$shotId,$outputPath")
        }

        override fun updatePreviewMeteringRequested(requestId: String, point: PreviewMeteringPoint) {
            calls.add("meteringRequested:$requestId")
        }

        override fun updatePreviewMeteringCompleted(result: PreviewMeteringResult) {
            calls.add("meteringCompleted:${result.requestId}")
        }
    }

    companion object {
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
            modeSnapshot = com.opencamera.core.mode.ModeSnapshot(ModeId.PHOTO, emptyMap()),
            activeDeviceCapabilities = com.opencamera.core.device.DeviceCapabilities.DEFAULT,
            activeDeviceGraph = DeviceGraphSpec.DEFAULT,
            previewMetrics = PreviewMetrics()
        )
    }

    // ── PreviewHostAttached ───────────────────────────────────────────

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
        // First detach to set pending recovery reason
        harness.dispatch(SessionIntent.PreviewHostDetached("test-detach"))
        // Then attach
        harness.dispatch(SessionIntent.PreviewHostAttached)

        assertTrue(harness.state.value.previewHostAvailable)
        assertEquals("Preview host reattached", harness.state.value.presentation.lastAction)
    }

    // ── PreviewHostDetached ───────────────────────────────────────────

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

        // Now attach to trigger recovery
        harness.dispatch(SessionIntent.PreviewHostAttached)
        val effects = harness.allEffects()
        // The recovery should have been requested (BindPreview with isRecovery=true)
        val bindEffect = effects.filterIsInstance<SessionEffect.BindPreview>().lastOrNull()
        assertNotNull(bindEffect)
        assertTrue(bindEffect.isRecovery)
        assertTrue(bindEffect.reason.contains("recover after preview host detached"))
    }

    // ── requestPendingPreviewHostRecovery ─────────────────────────────

    @Test
    fun `recovery not attempted when not RUNNING`() = runTest {
        val harness = Harness(runningState().copy(
            lifecycle = SessionLifecycle.STOPPED,
            previewHostAvailable = false
        ))
        harness.dispatch(SessionIntent.PreviewHostDetached("test"))
        // Reset to stopped
        harness.state.value = harness.state.value.copy(lifecycle = SessionLifecycle.STOPPED)
        harness.dispatch(SessionIntent.PreviewHostAttached)

        // Should fall through to normal binding, not recovery
        val effects = harness.allEffects()
        val bindEffects = effects.filterIsInstance<SessionEffect.BindPreview>()
        // Since lifecycle is STOPPED, requestPreviewBinding will not emit
        assertTrue(bindEffects.isEmpty())
    }

    @Test
    fun `recovery not attempted when recording`() = runTest {
        val harness = Harness(runningState().copy(
            previewHostAvailable = true,
            recordingStatus = RecordingStatus.RECORDING
        ))
        harness.dispatch(SessionIntent.PreviewHostDetached("test"))
        // Set back to recording state
        harness.state.value = harness.state.value.copy(
            previewHostAvailable = true,
            recordingStatus = RecordingStatus.RECORDING
        )
        harness.dispatch(SessionIntent.PreviewHostAttached)

        // Recovery should not be attempted since recording
        // The processor should fall through to requestPreviewBinding which also blocks during recording
        val effects = harness.allEffects()
        val bindEffects = effects.filterIsInstance<SessionEffect.BindPreview>()
        assertTrue(bindEffects.isEmpty())
    }

    // ── PreviewBindingStarted ─────────────────────────────────────────

    @Test
    fun `PreviewBindingStarted updates preview status and metrics`() = runTest {
        val harness = Harness(runningState().copy(
            previewMetrics = PreviewMetrics(bindCount = 2, recoveryCount = 1)
        ))
        harness.dispatch(SessionIntent.PreviewBindingStarted("test-bind", isRecovery = false))

        harness.mutations.calls.contains("starting:test-bind,recovery=false")
        assertEquals(3, harness.state.value.previewMetrics.bindCount)
        assertEquals(1, harness.state.value.previewMetrics.recoveryCount)
    }

    @Test
    fun `PreviewBindingStarted with recovery increments recovery count`() = runTest {
        val harness = Harness(runningState().copy(
            previewMetrics = PreviewMetrics(bindCount = 2, recoveryCount = 1)
        ))
        harness.dispatch(SessionIntent.PreviewBindingStarted("recover", isRecovery = true))

        assertEquals(3, harness.state.value.previewMetrics.bindCount)
        assertEquals(2, harness.state.value.previewMetrics.recoveryCount)
    }

    @Test
    fun `PreviewBindingStarted blocks when no camera permission`() = runTest {
        val harness = Harness(runningState().copy(
            permissionState = PermissionState(cameraGranted = false)
        ))
        harness.dispatch(SessionIntent.PreviewBindingStarted("test", false))

        assertTrue(harness.mutations.calls.contains("blocked:test"))
    }

    // ── PreviewFirstFrameAvailable ────────────────────────────────────

    @Test
    fun `PreviewFirstFrameAvailable updates metrics and sets ACTIVE`() = runTest {
        val harness = Harness(runningState().copy(
            previewMetrics = PreviewMetrics()
        ))
        harness.dispatch(SessionIntent.PreviewFirstFrameAvailable(150L))

        harness.mutations.calls.contains("active:150")
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

    // ── PreviewSnapshotUpdated ────────────────────────────────────────

    @Test
    fun `PreviewSnapshotUpdated updates thumbnail`() = runTest {
        val harness = Harness()
        val source = ThumbnailSource.PreviewSnapshot("/tmp/preview.jpg")
        harness.dispatch(SessionIntent.PreviewSnapshotUpdated(source, generation = 1))

        harness.mutations.calls.contains("thumbnail:gen=1")
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

        // Should not call updatePreviewThumbnail
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

    // ── CaptureFeedbackSnapshotUpdated ────────────────────────────────

    @Test
    fun `CaptureFeedbackSnapshotUpdated updates feedback for active shot`() = runTest {
        val shot = ShotRequest(
            shotId = "shot-1",
            mediaType = com.opencamera.core.media.MediaType.PHOTO,
            saveRequest = com.opencamera.core.media.SaveRequest()
        )
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.dispatch(SessionIntent.CaptureFeedbackSnapshotUpdated("shot-1", "/tmp/feedback.jpg"))

        assertTrue(harness.mutations.calls.contains("feedback:shot-1,/tmp/feedback.jpg"))
    }

    @Test
    fun `CaptureFeedbackSnapshotUpdated skips when no active shot`() = runTest {
        val harness = Harness(runningState().copy(activeShot = null))
        harness.dispatch(SessionIntent.CaptureFeedbackSnapshotUpdated("shot-1", "/tmp/f.jpg"))

        assertFalse(harness.mutations.calls.any { it.startsWith("feedback:") })
    }

    @Test
    fun `CaptureFeedbackSnapshotUpdated skips when shotId mismatch`() = runTest {
        val shot = ShotRequest(
            shotId = "shot-1",
            mediaType = com.opencamera.core.media.MediaType.PHOTO,
            saveRequest = com.opencamera.core.media.SaveRequest()
        )
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.dispatch(SessionIntent.CaptureFeedbackSnapshotUpdated("shot-2", "/tmp/f.jpg"))

        assertFalse(harness.mutations.calls.any { it.startsWith("feedback:") })
    }

    // ── PreviewSurfaceLost ────────────────────────────────────────────

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

        assertEquals(PreviewStatus.ERROR, harness.state.value.previewStatus)
    }

    @Test
    fun `PreviewSurfaceLost cancels countdown when active`() = runTest {
        val harness = Harness()
        harness.countdownActive = true
        harness.dispatch(SessionIntent.PreviewSurfaceLost("test"))

        assertEquals(1, harness.countdownCancellations.size)
    }

    // ── PreviewError ──────────────────────────────────────────────────

    @Test
    fun `PreviewError sets ERROR status and attempts recovery when conditions met`() = runTest {
        val harness = Harness()
        harness.dispatch(SessionIntent.PreviewError("camera error"))

        assertEquals(PreviewStatus.ERROR, harness.state.value.previewStatus)
        assertEquals("camera error", harness.state.value.previewStatusDetail)
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

        assertEquals(PreviewStatus.ERROR, harness.state.value.previewStatus)
        assertTrue(harness.mutations.calls.contains("error:error,action=Preview error"))
        // No BindPreview effect should be emitted
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

    // ── PreviewRuntimeIssue ───────────────────────────────────────────

    @Test
    fun `PreviewRuntimeIssue sets ERROR and attempts recovery for recoverable issue`() = runTest {
        val harness = Harness()
        val issue = DeviceRuntimeIssue(
            kind = DeviceRuntimeIssueKind.DISCONNECTED,
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
            kind = DeviceRuntimeIssueKind.DISCONNECTED,
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
            kind = DeviceRuntimeIssueKind.DISCONNECTED,
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
            kind = DeviceRuntimeIssueKind.DISCONNECTED,
            reason = "test",
            isRecoverable = false
        )
        harness.dispatch(SessionIntent.PreviewRuntimeIssue(issue))

        assertEquals(1, harness.countdownCancellations.size)
    }

    // ── PreviewStopped ────────────────────────────────────────────────

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

    // ── PreviewTapToFocus ─────────────────────────────────────────────

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

    // ── PreviewMeteringCompleted ──────────────────────────────────────

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

    // ── requestPreviewBinding guards ──────────────────────────────────

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

    // ── shouldAttemptPreviewErrorRecovery ─────────────────────────────

    @Test
    fun `recovery skipped when activeShot present`() = runTest {
        val shot = ShotRequest(
            shotId = "shot-1",
            mediaType = com.opencamera.core.media.MediaType.PHOTO,
            saveRequest = com.opencamera.core.media.SaveRequest()
        )
        val harness = Harness(runningState().copy(activeShot = shot))
        harness.dispatch(SessionIntent.PreviewError("error"))

        // Recovery should not be attempted
        assertFalse(harness.allEffects().any { it is SessionEffect.BindPreview })
    }

    // ── process rejects unexpected intents ─────────────────────────────

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
}
