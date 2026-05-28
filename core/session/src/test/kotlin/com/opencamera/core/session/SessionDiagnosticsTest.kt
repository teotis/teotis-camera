package com.opencamera.core.session

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.CameraPerformanceClass
import com.opencamera.core.media.CameraThermalState
import com.opencamera.core.media.ResourceDiagnosticsSnapshot
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.settings.SessionSettingsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionDiagnosticsTest {
    @Test
    fun `perf snapshot mirrors preview metrics`() {
        val state = defaultSessionState(
            previewMetrics = PreviewMetrics(
                bindCount = 3,
                recoveryCount = 1,
                lastFirstFrameLatencyMillis = 84,
                bestFirstFrameLatencyMillis = 72,
                worstFirstFrameLatencyMillis = 110,
                lastStartReason = "recover after surface lost"
            ),
            previewStatus = PreviewStatus.ACTIVE
        )

        val snapshot = state.toPerfSnapshot()

        assertEquals(PreviewStatus.ACTIVE, snapshot.previewStatus)
        assertEquals(3, snapshot.bindCount)
        assertEquals(1, snapshot.recoveryCount)
        assertEquals(84, snapshot.lastFirstFrameLatencyMillis)
        assertEquals(72, snapshot.bestFirstFrameLatencyMillis)
        assertEquals(110, snapshot.worstFirstFrameLatencyMillis)
        assertEquals("recover after surface lost", snapshot.lastStartReason)
        assertEquals(PreviewStartCategory.RECOVERY, snapshot.firstFrameBudget.startCategory)
        assertEquals(PerfBudgetStatus.WITHIN_BUDGET, snapshot.firstFrameBudget.status)
        assertEquals(180, snapshot.firstFrameBudget.warnThresholdMillis)
        assertEquals(320, snapshot.firstFrameBudget.failThresholdMillis)
    }

    @Test
    fun `perf snapshot classifies foreground resume latency against resume budget`() {
        val state = defaultSessionState(
            previewMetrics = PreviewMetrics(
                bindCount = 2,
                recoveryCount = 1,
                lastFirstFrameLatencyMillis = 210,
                bestFirstFrameLatencyMillis = 108,
                worstFirstFrameLatencyMillis = 210,
                lastStartReason = "recover after preview host detached: Activity moved to background"
            ),
            previewStatus = PreviewStatus.ACTIVE
        )

        val snapshot = state.toPerfSnapshot()

        assertEquals(PreviewStartCategory.FOREGROUND_RESUME, snapshot.firstFrameBudget.startCategory)
        assertEquals(PerfBudgetStatus.WARNING, snapshot.firstFrameBudget.status)
        assertEquals(150, snapshot.firstFrameBudget.warnThresholdMillis)
        assertEquals(260, snapshot.firstFrameBudget.failThresholdMillis)
    }

    @Test
    fun `recovery trace snapshot keeps last recovery cause and recovered frame`() {
        val traceEvents = listOf(
            SessionTraceEvent(1, "preview.binding.started", "session boot", 1L),
            SessionTraceEvent(2, "preview.first.frame", "140ms", 2L),
            SessionTraceEvent(3, "preview.surface.lost", "surface dropped", 3L),
            SessionTraceEvent(4, "preview.recovery.started", "recover after surface dropped", 4L),
            SessionTraceEvent(5, "preview.first.frame", "92ms", 5L)
        )
        val state = defaultSessionState(
            previewStatus = PreviewStatus.ACTIVE,
            previewMetrics = PreviewMetrics(
                bindCount = 2,
                recoveryCount = 1,
                lastFirstFrameLatencyMillis = 92,
                bestFirstFrameLatencyMillis = 92,
                worstFirstFrameLatencyMillis = 140,
                lastStartReason = "recover after surface dropped"
            )
        )

        val snapshot = buildRecoveryTraceSnapshot(state, traceEvents)

        assertFalse(snapshot.isRecoveryActive)
        assertEquals(1, snapshot.recoveryCount)
        assertEquals("recover after surface dropped", snapshot.lastRecoveryReason)
        assertEquals("92ms", traceEvents.last().detail)
        assertEquals(92L, snapshot.recoveredFirstFrameLatencyMillis)
        assertEquals(listOf(1, 2, 3, 4, 5), snapshot.events.map { it.sequence })
    }

    @Test
    fun `recovery trace snapshot falls back to state error when recovery has not completed`() {
        val traceEvents = listOf(
            SessionTraceEvent(
                6,
                "preview.runtime.issue",
                "kind=PROVIDER_FAILURE,recoverable=true,reason=provider restarted",
                6L
            ),
            SessionTraceEvent(7, "preview.surface.lost", "surface lost while resuming", 7L),
            SessionTraceEvent(8, "preview.recovery.started", "recover after surface lost while resuming", 8L),
            SessionTraceEvent(9, "preview.recovery.failed", "Bind failure: rebind failed", 9L)
        )
        val state = defaultSessionState(
            previewStatus = PreviewStatus.RECOVERING,
            previewMetrics = PreviewMetrics(bindCount = 2, recoveryCount = 1),
            presentation = SessionPresentationState(
                lastAction = "Recovering preview",
                lastError = "surface lost while resuming"
            )
        )

        val snapshot = buildRecoveryTraceSnapshot(state, traceEvents)

        assertTrue(snapshot.isRecoveryActive)
        assertEquals("recover after surface lost while resuming", snapshot.lastRecoveryReason)
        assertEquals("surface lost while resuming", snapshot.lastFailureReason)
        assertNull(snapshot.recoveredFirstFrameLatencyMillis)
        assertEquals(listOf(6, 7, 8, 9), snapshot.events.map { it.sequence })
    }

    @Test
    fun `session debug dump keeps recent events only`() {
        val traceEvents = (1..14).map { index ->
            SessionTraceEvent(
                sequence = index,
                name = if (index % 2 == 0) "preview.first.frame" else "intent.received",
                detail = "detail-$index",
                timestampMillis = index.toLong()
            )
        }
        val state = defaultSessionState(
            previewStatus = PreviewStatus.ACTIVE,
            previewMetrics = PreviewMetrics(
                bindCount = 4,
                recoveryCount = 2,
                lastFirstFrameLatencyMillis = 88,
                bestFirstFrameLatencyMillis = 80,
                worstFirstFrameLatencyMillis = 120,
                lastStartReason = "recover after provider restart"
            ),
            presentation = SessionPresentationState(
                lastAction = "Preview active (88 ms first frame)"
            )
        )

        val dump = buildSessionDebugDump(
            state = state,
            traceEvents = traceEvents,
            recentEventLimit = 5,
            recoveryEventLimit = 3
        )

        assertEquals(SessionLifecycle.RUNNING, dump.lifecycle)
        assertEquals(ModeId.PHOTO, dump.activeMode)
        assertEquals(listOf(10, 11, 12, 13, 14), dump.recentEvents.map { it.sequence })
        assertEquals(3, dump.recoveryTrace.events.size)
        assertEquals(2, dump.perfSnapshot.recoveryCount)
        assertEquals(PreviewStartCategory.RECOVERY, dump.perfSnapshot.firstFrameBudget.startCategory)
        assertEquals("Preview active (88 ms first frame)", dump.lastAction)
    }

    @Test
    fun `preview start timeout uses fail budget plus grace`() {
        assertEquals(400L, previewStartTimeoutMillis("recover after provider failure: provider restarted"))
        assertEquals(300L, previewStartTimeoutMillis("session boot"))
    }

    @Test
    fun `preview start watchdog uses independent conservative thresholds`() {
        assertEquals(1200L, previewStartWatchdogMillis("session boot"))
        assertEquals(1200L, previewStartWatchdogMillis("preview host attached"))
        assertEquals(1200L, previewStartWatchdogMillis("camera permission granted"))
        assertEquals(1200L, previewStartWatchdogMillis("recover after preview host detached: Activity moved to background"))
        assertEquals(1500L, previewStartWatchdogMillis("recover after provider failure: provider restarted"))
        assertEquals(1000L, previewStartWatchdogMillis("mode switched to video"))
        assertEquals(1000L, previewStartWatchdogMillis("lens switched to front"))
        assertEquals(1000L, previewStartWatchdogMillis("session settings updated"))
        assertEquals(1200L, previewStartWatchdogMillis("unknown reason"))
        assertEquals(1200L, previewStartWatchdogMillis(null))
    }

    @Test
    fun `session debug dump includes resource diagnostics when provided`() {
        val state = defaultSessionState()
        val resourceDiag = ResourceDiagnosticsSnapshot(
            thermalState = CameraThermalState.WARM,
            performanceClass = CameraPerformanceClass.MID,
            memoryBudgetBytes = 256L * 1024 * 1024,
            activeAlgorithmJobs = 1,
            maxConcurrentAlgorithmJobs = 2,
            featureDegradations = mapOf("live" to "degraded:max-frames"),
            pipelineNotes = listOf("resource:class=mid", "resource:thermal=warm")
        )
        val dump = buildSessionDebugDump(
            state = state,
            traceEvents = emptyList(),
            resourceDiagnostics = resourceDiag
        )
        assertEquals(resourceDiag, dump.resourceDiagnostics)
    }

    @Test
    fun `session debug dump has null resource diagnostics by default`() {
        val state = defaultSessionState()
        val dump = buildSessionDebugDump(state = state, traceEvents = emptyList())
        assertNull(dump.resourceDiagnostics)
    }

    @Test
    fun `resource diagnostics renderable in dev log without raw frame data`() {
        val resourceDiag = ResourceDiagnosticsSnapshot(
            thermalState = CameraThermalState.HOT,
            performanceClass = CameraPerformanceClass.LOW,
            memoryBudgetBytes = 128L * 1024 * 1024,
            activeAlgorithmJobs = 2,
            maxConcurrentAlgorithmJobs = 1,
            featureDegradations = mapOf("night" to "degraded:frame-count-3"),
            pipelineNotes = listOf(
                "resource:class=low",
                "resource:thermal=hot",
                "resource:analysis-fps=0",
                "resource:algorithm-queue=busy",
                "resource:night=degraded:frame-count-3"
            )
        )
        val notes = resourceDiag.pipelineNotes
        assertTrue(notes.all { it.startsWith("resource:") })
        assertTrue(notes.none { it.contains("frame-data") || it.contains("pixel") || it.contains("byte") })
    }

    private fun defaultSessionState(
        previewStatus: PreviewStatus = PreviewStatus.IDLE,
        previewMetrics: PreviewMetrics = PreviewMetrics(),
        presentation: SessionPresentationState = SessionPresentationState(
            lastAction = "Ready"
        )
    ): SessionState {
        return SessionState(
            lifecycle = SessionLifecycle.RUNNING,
            permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
            previewHostAvailable = true,
            previewStatus = previewStatus,
            previewStatusDetail = null,
            activeMode = ModeId.PHOTO,
            availableModes = listOf(ModeId.PHOTO, ModeId.VIDEO),
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = ModeSnapshot(
                id = ModeId.PHOTO,
                uiSpec = ModeUiSpec(
                    title = "Photo",
                    shutterLabel = "Shutter"
                ),
                state = ModeState(
                    headline = "Photo ready",
                    detail = "Waiting",
                    isShutterEnabled = true,
                    isSecondaryActionEnabled = false,
                    isTertiaryActionEnabled = false,
                    isProActionEnabled = false
                )
            ),
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT)
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true
            ),
            previewMetrics = previewMetrics,
            settings = SessionSettingsSnapshot(),
            presentation = presentation
        )
    }

    // ── Link event diagnostics tests ────────────────────────────────

    @Test
    fun `session debug dump includes link events when provided`() {
        val linkEvents = listOf(
            PerformanceLinkEvent(
                flow = "preview",
                stage = "binding",
                status = LinkEventStatus.COMPLETED,
                correlationId = "prev-1",
                startElapsedMillis = 100L,
                endElapsedMillis = 250L,
                durationMillis = 150L,
                detail = "firstFrameLatency=150ms",
                source = "PreviewRecoverySessionProcessor"
            ),
            PerformanceLinkEvent(
                flow = "capture",
                stage = "requested",
                status = LinkEventStatus.COMPLETED,
                correlationId = "shot-1",
                startElapsedMillis = 500L,
                endElapsedMillis = 800L,
                durationMillis = 300L,
                detail = null,
                source = "CaptureRecordingSessionProcessor"
            )
        )
        val state = defaultSessionState()
        val dump = buildSessionDebugDump(
            state = state,
            traceEvents = emptyList(),
            linkEvents = linkEvents
        )
        assertEquals(2, dump.recentLinkEvents.size)
        assertEquals("preview", dump.recentLinkEvents[0].flow)
        assertEquals("capture", dump.recentLinkEvents[1].flow)
    }

    @Test
    fun `session debug dump limits link events to recent limit`() {
        val linkEvents = (1..15).map { index ->
            PerformanceLinkEvent(
                flow = "preview",
                stage = "binding",
                status = LinkEventStatus.COMPLETED,
                correlationId = "prev-$index",
                startElapsedMillis = index * 100L,
                endElapsedMillis = index * 100L + 80,
                durationMillis = 80L,
                detail = null,
                source = "test"
            )
        }
        val state = defaultSessionState()
        val dump = buildSessionDebugDump(
            state = state,
            traceEvents = emptyList(),
            recentEventLimit = 5,
            linkEvents = linkEvents
        )
        assertEquals(5, dump.recentLinkEvents.size)
        assertEquals("prev-15", dump.recentLinkEvents.last().correlationId)
    }

    @Test
    fun `link events default to empty when not provided`() {
        val state = defaultSessionState()
        val dump = buildSessionDebugDump(state = state, traceEvents = emptyList())
        assertTrue(dump.recentLinkEvents.isEmpty())
    }
}
