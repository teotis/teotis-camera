package com.opencamera.app.reliability

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceRuntimeIssueKind
import com.opencamera.core.device.LensFacing
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SessionLifecycle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * PR quick set: the fixed seed scenarios of the Capture Reliability Closure.
 *
 * 12 categories × 1-2 deterministic variants. Every scenario asserts that all
 * five invariant families pass and that the final state converges. Each test
 * is fully replayable (fixed steps + fixed device scripts + virtual clock).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CaptureReliabilityQuickSuiteTest {

    private val dualLensCapabilities = DeviceCapabilities.DEFAULT.copy(
        availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT)
    )

    // ── S01 权限 ─────────────────────────────────────────────────────

    @Test
    fun `s01 revoking permission mid-capture fails the shot and converges`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 1001)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.LateStarted(3_000, ShotScript.Normal)),
                ScenarioStep.ShutterPhoto,
                ScenarioStep.Wait(500),
                ScenarioStep.Permissions(camera = false, microphone = true),
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(CaptureStatus.IDLE, result.finalState.captureStatus)
        assertEquals(PreviewStatus.BLOCKED, result.finalState.previewStatus)
        assertNull(result.finalState.activeShot)
    }

    @Test
    fun `s01 boot without permission blocks preview then grants and recovers`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 1002)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.Permissions(camera = false, microphone = true),
                ScenarioStep.Boot,
                ScenarioStep.Wait(100),
                ScenarioStep.Permissions(camera = true, microphone = true),
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(PreviewStatus.ACTIVE, result.finalState.previewStatus)
        assertTrue(result.finalState.previewMetrics.bindCount >= 2)
    }

    // ── S02 前后台 ───────────────────────────────────────────────────

    @Test
    fun `s02 host detach after data received then reattach converges`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 2001)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.HangAfterData),
                ScenarioStep.ShutterPhoto,
                ScenarioStep.Wait(100),
                ScenarioStep.DetachHost("fixture:activity-paused"),
                ScenarioStep.Wait(200),
                ScenarioStep.AttachHost,
                ScenarioStep.DeviceShotScript(ShotScript.Normal),
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(CaptureStatus.IDLE, result.finalState.captureStatus)
    }

    @Test
    fun `s02 countdown cancelled by host detach`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 2002)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.CountdownShutter(seconds = 3),
                ScenarioStep.Wait(500),
                ScenarioStep.DetachHost("fixture:activity-paused"),
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertNull(result.finalState.presentation.countdownRemainingSeconds)
        assertEquals(CaptureStatus.IDLE, result.finalState.captureStatus)
    }

    // ── S03 重绑定 ───────────────────────────────────────────────────

    @Test
    fun `s03 repeated recoverable issues stop after recovery limit`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 3001)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceRuntimeIssue(DeviceRuntimeIssueKind.CAMERA_RECOVERABLE, true),
                ScenarioStep.Wait(100),
                ScenarioStep.DeviceRuntimeIssue(DeviceRuntimeIssueKind.CAMERA_RECOVERABLE, true),
                ScenarioStep.Wait(100),
                ScenarioStep.DeviceRuntimeIssue(DeviceRuntimeIssueKind.CAMERA_RECOVERABLE, true),
                ScenarioStep.Wait(100),
                ScenarioStep.DeviceRuntimeIssue(DeviceRuntimeIssueKind.CAMERA_RECOVERABLE, true),
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertTrue(
            result.finalState.previewMetrics.consecutiveRecoveryCount <= 3,
            "recovery limit exceeded: ${result.finalState.previewMetrics.consecutiveRecoveryCount}"
        )
    }

    @Test
    fun `s03 host reattach triggers pending recovery`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 3002)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DetachHost("fixture:background"),
                ScenarioStep.Wait(100),
                ScenarioStep.AttachHost,
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(PreviewStatus.ACTIVE, result.finalState.previewStatus)
    }

    // ── S04 镜头/模式切换 ────────────────────────────────────────────

    @Test
    fun `s04 lens toggle during capture is rejected`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 4001, )
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.HangAfterStarted),
                ScenarioStep.ShutterPhoto,
                ScenarioStep.Wait(100),
                ScenarioStep.ToggleLens,
                ScenarioStep.WaitWatchdogs
            ),
            deviceCapabilities = dualLensCapabilities
        )
        assertTrue(result.passed, result.summary())
        assertTrue(
            result.traceEvents.any { it.name == "lens.switch.blocked" },
            "lens toggle during capture must be blocked"
        )
    }

    @Test
    fun `s04 mode switch during recording is rejected then allowed after stop`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 4002)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.Normal),
                ScenarioStep.ShutterVideo,
                ScenarioStep.Wait(100),
                ScenarioStep.SwitchMode(ModeId.PHOTO),
                ScenarioStep.Wait(100),
                ScenarioStep.DeviceShotScript(ShotScript.FinalizeSuccess),
                ScenarioStep.StopActiveShot,
                ScenarioStep.Wait(100),
                ScenarioStep.SwitchMode(ModeId.PHOTO),
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(ModeId.PHOTO, result.finalState.activeMode)
    }

    @Test
    fun `s04 idle lens toggle rebinds preview`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 4003)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.ToggleLens,
                ScenarioStep.WaitWatchdogs
            ),
            deviceCapabilities = dualLensCapabilities
        )
        assertTrue(result.passed, result.summary())
        assertEquals(
            LensFacing.FRONT,
            result.finalState.activeDeviceGraph.preferredLensFacing
        )
        assertTrue(result.finalState.previewMetrics.bindCount >= 2)
    }

    // ── S05 连拍/多帧故障 ────────────────────────────────────────────

    @Test
    fun `s05 postprocess failure after data received rolls back output`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 5001)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.FailAfterData("fixture:postprocess-failed")),
                ScenarioStep.ShutterPhoto,
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(CaptureStatus.IDLE, result.finalState.captureStatus)
        assertEquals("fixture:postprocess-failed", result.finalState.lastError)
    }

    @Test
    fun `s05 hung postprocess force-releases then late completion hydrates`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 5002)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.HangAfterData),
                ScenarioStep.ShutterPhoto,
                ScenarioStep.Wait(9_000),
                ScenarioStep.DeviceShotScript(ShotScript.Normal),
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(CaptureStatus.IDLE, result.finalState.captureStatus)
    }

    @Test
    fun `s05 completed with failure summary is a degraded but truthful save`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 5003)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(
                    ShotScript.CompletedWithFailureSummary("postprocess:failed:out-of-memory")
                ),
                ScenarioStep.ShutterPhoto,
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(CaptureStatus.IDLE, result.finalState.captureStatus)
        assertTrue(
            result.finalState.lastError?.contains("out-of-memory") == true,
            "degraded save must surface failure summary, got ${result.finalState.lastError}"
        )
    }

    // ── S06 录制停止 ─────────────────────────────────────────────────

    @Test
    fun `s06 recording stop finalize success saves video`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 6001)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.Normal),
                ScenarioStep.ShutterVideo,
                ScenarioStep.Wait(200),
                ScenarioStep.StopActiveShot,
                ScenarioStep.DeviceShotScript(ShotScript.FinalizeSuccess),
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(RecordingStatus.IDLE, result.finalState.recordingStatus)
        assertNull(result.finalState.activeShot)
    }

    @Test
    fun `s06 recording stop finalize error fails honestly`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 6002)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.Normal),
                ScenarioStep.ShutterVideo,
                ScenarioStep.Wait(200),
                ScenarioStep.DeviceShotScript(ShotScript.FinalizeError("fixture:finalize-error")),
                ScenarioStep.StopActiveShot,
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(RecordingStatus.IDLE, result.finalState.recordingStatus)
        assertEquals("fixture:finalize-error", result.finalState.lastError)
    }

    // ── S07 超时 ─────────────────────────────────────────────────────

    @Test
    fun `s07 video requesting timeout converges without device events`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 7001)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.HangAfterStarted),
                ScenarioStep.ShutterVideo,
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(RecordingStatus.IDLE, result.finalState.recordingStatus)
        assertNull(result.finalState.activeShot)
        assertTrue(result.finalState.lastError?.contains("timed out") == true)
    }

    @Test
    fun `s07 stopping watchdog converges when finalize never arrives`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 7002)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.Normal),
                ScenarioStep.ShutterVideo,
                ScenarioStep.Wait(200),
                ScenarioStep.DeviceShotScript(ShotScript.FinalizeHang),
                ScenarioStep.StopActiveShot,
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(RecordingStatus.IDLE, result.finalState.recordingStatus)
        assertNull(result.finalState.activeShot)
    }

    // ── S08 取消 ─────────────────────────────────────────────────────

    @Test
    fun `s08 cancelling a pending recording start fails cleanly`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 8001)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.HangAfterStarted),
                ScenarioStep.ShutterVideo,
                ScenarioStep.Wait(100),
                ScenarioStep.StopActiveShot,
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(RecordingStatus.IDLE, result.finalState.recordingStatus)
        assertNull(result.finalState.activeShot)
    }

    // ── S09 Camera disconnect ────────────────────────────────────────

    @Test
    fun `s09 camera fatal during capture force-releases via liveness`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 9001)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.HangAfterStarted),
                ScenarioStep.ShutterPhoto,
                ScenarioStep.Wait(100),
                ScenarioStep.DeviceRuntimeIssue(DeviceRuntimeIssueKind.CAMERA_FATAL, false),
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(CaptureStatus.IDLE, result.finalState.captureStatus)
        assertNull(result.finalState.activeShot)
    }

    @Test
    fun `s09 camera fatal during recording stops and converges via watchdog`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 9002)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.Normal),
                ScenarioStep.ShutterVideo,
                ScenarioStep.Wait(200),
                ScenarioStep.DeviceRuntimeIssue(DeviceRuntimeIssueKind.CAMERA_FATAL, false),
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(RecordingStatus.IDLE, result.finalState.recordingStatus)
        assertNull(result.finalState.activeShot)
    }

    @Test
    fun `s09 surface lost during recording stops the recording`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 9003)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.Normal),
                ScenarioStep.ShutterVideo,
                ScenarioStep.Wait(200),
                ScenarioStep.DeviceSurfaceLost,
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(RecordingStatus.IDLE, result.finalState.recordingStatus)
        assertNull(result.finalState.activeShot)
    }

    // ── S10 存储不足 ─────────────────────────────────────────────────

    @Test
    fun `s10 storage full photo capture fails with no visible output`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 10001)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.StorageFull),
                ScenarioStep.ShutterPhoto,
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(CaptureStatus.FAILED, result.finalState.captureStatus)
        assertNull(result.finalState.activeShot)
        assertTrue(result.finalState.lastError?.contains("Storage") == true)
    }

    @Test
    fun `s10 storage full video finalize fails honestly`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 10002)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.Normal),
                ScenarioStep.ShutterVideo,
                ScenarioStep.Wait(200),
                ScenarioStep.DeviceShotScript(ShotScript.FinalizeError("Storage is full")),
                ScenarioStep.StopActiveShot,
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(RecordingStatus.IDLE, result.finalState.recordingStatus)
        assertTrue(result.finalState.lastError?.contains("Storage") == true)
    }

    // ── S11 写入异常 ─────────────────────────────────────────────────

    @Test
    fun `s11 write exception after data received fails and rolls back`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 11001)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(
                    ShotScript.FailAfterData("Failed to open output stream: ENOENT")
                ),
                ScenarioStep.ShutterPhoto,
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(CaptureStatus.IDLE, result.finalState.captureStatus)
        assertTrue(result.finalState.lastError?.contains("output stream") == true)
    }

    @Test
    fun `s11 duplicate terminal events are applied once`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 11002)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.DuplicateCompleted),
                ScenarioStep.ShutterPhoto,
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(CaptureStatus.IDLE, result.finalState.captureStatus)
    }

    @Test
    fun `s11 duplicate started event is stale`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 11003)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.DuplicateStarted),
                ScenarioStep.ShutterPhoto,
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(CaptureStatus.IDLE, result.finalState.captureStatus)
        assertTrue(
            result.traceEvents.any { it.name == "shot.started.duplicate" },
            "duplicate Started must be traced as stale"
        )
    }

    // ── S12 OOM ──────────────────────────────────────────────────────

    @Test
    fun `s12 oom during capture fails with no visible output`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 12001)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.Oom),
                ScenarioStep.ShutterPhoto,
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(CaptureStatus.FAILED, result.finalState.captureStatus)
        assertNull(result.finalState.activeShot)
        assertTrue(result.finalState.lastError?.contains("OutOfMemory") == true)
    }

    @Test
    fun `s12 oom postprocess degrades save with truthful summary`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 12002)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(
                    ShotScript.CompletedWithFailureSummary("postprocess:failed:out-of-memory")
                ),
                ScenarioStep.ShutterPhoto,
                ScenarioStep.WaitWatchdogs
            )
        )
        assertTrue(result.passed, result.summary())
        assertEquals(CaptureStatus.IDLE, result.finalState.captureStatus)
        assertTrue(result.finalState.lastError?.contains("out-of-memory") == true)
    }

    // ── Shutdown convergence ─────────────────────────────────────────

    @Test
    fun `shutdown mid-postprocess converges`() = runTest {
        val runner = ReliabilityScenarioRunner(seed = 13001)
        val result = runner.run(
            testScope = this,
            steps = listOf(
                ScenarioStep.DeviceShotScript(ShotScript.HangAfterData),
                ScenarioStep.ShutterPhoto,
                ScenarioStep.Wait(200),
                ScenarioStep.Shutdown,
                ScenarioStep.WaitWatchdogs
            ),
            endWithShutdown = false
        )
        assertTrue(result.passed, result.summary())
        assertEquals(SessionLifecycle.STOPPED, result.finalState.lifecycle)
        assertNull(result.finalState.presentation.pendingPostprocess)
        assertNull(result.finalState.presentation.captureReadiness)
    }
}
