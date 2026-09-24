package com.opencamera.app.reliability

import com.opencamera.core.device.DeviceRuntimeIssueKind
import com.opencamera.core.media.MediaType
import com.opencamera.core.mode.ModeId
import kotlin.random.Random

/**
 * Deterministic scenario DSL for the Capture Reliability Closure.
 *
 * A [ScenarioStep] list plus a [Long] seed fully determines a scenario run:
 * the same seed always produces the same device script and step sequence, so
 * any failure can be replayed with a single seed.
 */
sealed interface ScenarioStep {
    data object Boot : ScenarioStep
    data object Shutdown : ScenarioStep
    data object AttachHost : ScenarioStep
    data class DetachHost(val reason: String) : ScenarioStep
    data class Permissions(val camera: Boolean, val microphone: Boolean) : ScenarioStep
    data object ShutterPhoto : ScenarioStep
    data object ShutterVideo : ScenarioStep
    data object StopActiveShot : ScenarioStep
    data class CountdownShutter(val seconds: Int) : ScenarioStep
    data class SwitchMode(val modeId: ModeId) : ScenarioStep
    data object ToggleLens : ScenarioStep
    data object ToggleZoom : ScenarioStep
    data class DeviceShotScript(val script: ShotScript) : ScenarioStep
    data class DeviceRuntimeIssue(
        val kind: DeviceRuntimeIssueKind,
        val recoverable: Boolean
    ) : ScenarioStep

    data object DeviceSurfaceLost : ScenarioStep
    data object DevicePreviewError : ScenarioStep
    data class Wait(val millis: Long) : ScenarioStep

    /** Advances past every session watchdog window (liveness 8s+2s, recording 10s/15s). */
    data object WaitWatchdogs : ScenarioStep
}

/**
 * Deterministic per-shot device behavior. [ShotScript] is consumed by the
 * [ProgrammableCameraDeviceAdapter] in ExecuteShot order.
 */
sealed interface ShotScript {
    /** Started → Committed → DataReceived → Completed. */
    data object Normal : ShotScript

    /** Started → Failed before any output claim (camera never wrote the file). */
    data class FailImmediately(val reason: String) : ShotScript

    /** Started → Committed → DataReceived → Failed, output claimed then rolled back. */
    data class FailAfterData(val reason: String) : ShotScript

    /** Started only; no terminal event (recording REQUESTING / liveness window). */
    data object HangAfterStarted : ShotScript

    /** Started → Committed → DataReceived; no terminal (postprocess hang). */
    data object HangAfterData : ShotScript

    /** Started emitted after [delayMillis] of virtual time, then [inner]. */
    data class LateStarted(val delayMillis: Long, val inner: ShotScript) : ShotScript

    /** Started emitted twice for the same shot. */
    data object DuplicateStarted : ShotScript

    /** Completed emitted twice for the same shot. */
    data object DuplicateCompleted : ShotScript

    /** Completed carrying a postprocess failure summary (degraded save, e.g. OOM/decode). */
    data class CompletedWithFailureSummary(val reason: String) : ShotScript

    /** Started → Failed with an OutOfMemoryError-shaped reason. */
    data object Oom : ShotScript

    /** Started → Failed with a storage-full shaped reason. */
    data object StorageFull : ShotScript

    // Video finalize scripts, consumed by DeviceCommand.StopActiveShot:

    /** Finalize succeeds (ShotCompleted for the video). */
    data object FinalizeSuccess : ShotScript

    /** Finalize fails (ShotFailed for the video). */
    data class FinalizeError(val reason: String) : ShotScript

    /** Stop requested but finalize never arrives (STOPPING watchdog path). */
    data object FinalizeHang : ShotScript
}

/** Deterministic script generation for the stress suite. */
object ReliabilityScriptGenerator {

    fun generate(
        seed: Long,
        stepCount: Int = 16
    ): List<ScenarioStep> {
        val rng = Random(seed)
        val steps = mutableListOf<ScenarioStep>()
        var recordingInFlight = false
        var photoInFlight = false
        var deviceScriptQueue = mutableListOf<ShotScript>()
        val photoScripts = listOf(
            ShotScript.Normal,
            ShotScript.FailImmediately("fixture:io-error"),
            ShotScript.FailAfterData("fixture:postprocess-failed"),
            ShotScript.HangAfterData,
            ShotScript.DuplicateStarted,
            ShotScript.DuplicateCompleted,
            ShotScript.CompletedWithFailureSummary("postprocess:failed:out-of-memory"),
            ShotScript.Oom,
            ShotScript.StorageFull
        )
        val videoScripts = listOf(
            ShotScript.Normal,
            ShotScript.FailImmediately("fixture:start-failed"),
            ShotScript.HangAfterStarted,
            ShotScript.LateStarted(9_500, ShotScript.Normal),
            ShotScript.FinalizeError("fixture:finalize-error"),
            ShotScript.FinalizeHang
        )

        repeat(stepCount) { index ->
            val next = rng.nextInt(100)
            when {
                deviceScriptQueue.isNotEmpty() && rng.nextInt(100) < 30 -> {
                    steps += ScenarioStep.DeviceShotScript(deviceScriptQueue.removeFirst())
                }

                recordingInFlight && next < 55 -> {
                    steps += ScenarioStep.StopActiveShot
                    recordingInFlight = false
                }

                next < 38 -> {
                    val script = photoScripts[rng.nextInt(photoScripts.size)]
                    steps += ScenarioStep.DeviceShotScript(script)
                    steps += ScenarioStep.ShutterPhoto
                    photoInFlight = true
                }

                next < 58 -> {
                    val script = videoScripts[rng.nextInt(videoScripts.size)]
                    steps += ScenarioStep.DeviceShotScript(script)
                    steps += ScenarioStep.ShutterVideo
                    recordingInFlight = true
                }

                next < 66 -> steps += ScenarioStep.Wait(rng.nextLong(1_000, 6_000))

                next < 72 -> steps += ScenarioStep.Wait(20_000)

                next < 80 -> steps += ScenarioStep.DetachHost("fixture:activity-paused")
                next < 88 -> steps += ScenarioStep.AttachHost
                next < 92 -> steps += ScenarioStep.SwitchMode(
                    if (rng.nextBoolean()) ModeId.VIDEO else ModeId.PHOTO
                )
                next < 95 -> steps += ScenarioStep.Permissions(rng.nextBoolean(), true)
                else -> steps += ScenarioStep.DeviceRuntimeIssue(
                    kind = if (rng.nextBoolean()) {
                        DeviceRuntimeIssueKind.CAMERA_FATAL
                    } else {
                        DeviceRuntimeIssueKind.CAMERA_RECOVERABLE
                    },
                    recoverable = rng.nextBoolean()
                )
            }
        }

        // Every scenario must end at a convergent point. A late video Started
        // (LateStarted up to 9.5s) can re-enter RECORDING after the tracked stop,
        // so: let the late-start window pass, stop whatever is recording, give the
        // STOPPING/REQUESTING watchdogs their windows, and stop once more as a
        // safety net (a stop with nothing recording re-submits a fresh request,
        // which the final watchdog window then resolves).
        if (recordingInFlight) {
            steps += ScenarioStep.StopActiveShot
        }
        steps += ScenarioStep.Wait(10_000)
        steps += ScenarioStep.StopActiveShot
        steps += ScenarioStep.Wait(15_100)
        steps += ScenarioStep.StopActiveShot
        steps += ScenarioStep.WaitWatchdogs
        return steps
    }

    fun videoFinalizeFor(script: ShotScript): ShotScript = script

    /** Synthetic failure reasons used by the generator (fixture scope). */
    object FixtureReasons {
        const val IO_ERROR = "fixture:io-error"
        const val POSTPROCESS_FAILED = "fixture:postprocess-failed"
        const val START_FAILED = "fixture:start-failed"
        const val FINALIZE_ERROR = "fixture:finalize-error"
        const val STORAGE_FULL = "Storage is full"
        const val OOM = "OutOfMemoryError: fixture heap exhaustion"
    }
}
