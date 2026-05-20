package com.opencamera.core.session

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
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
                enablePreviewSnapshots = true,
                qualityPreference = StillCaptureQualityPreference.LATENCY,
                resolutionPreset = StillCaptureResolutionPreset.LARGE_12MP
            ),
            previewMetrics = previewMetrics,
            settings = SessionSettingsSnapshot(),
            presentation = presentation
        )
    }
}
