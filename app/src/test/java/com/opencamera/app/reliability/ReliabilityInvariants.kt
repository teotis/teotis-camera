package com.opencamera.app.reliability

import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.SessionTrace
import com.opencamera.core.device.DeviceEvent

/**
 * Capture Reliability invariant checker (see docs/plans/capture-reliability-closure-orchestration/INVARIANTS.md).
 *
 * Five invariant families, all evaluated over the recorded state sequence, the
 * device event trace, the fake MediaStore model and the session trace:
 *  - INV-1 session uniqueness
 *  - INV-2 output atomicity
 *  - INV-3 state truthfulness
 *  - INV-4 resource cleanup
 *  - INV-5 recovery idempotency
 */
class ReliabilityInvariants(
    private val states: List<SessionState>,
    private val deviceEvents: List<DeviceEvent>,
    private val mediaStore: FakeMediaStoreModel,
    private val trace: SessionTrace,
    private val finalState: SessionState
) {
    class Violation(
        val invariantId: String,
        val detail: String
    ) {
        override fun toString(): String = "[$invariantId] $detail"
    }

    private val violations = mutableListOf<Violation>()

    fun checkAll(): List<Violation> {
        checkSessionUniqueness()
        checkOutputAtomicity()
        checkStateTruthfulness()
        checkResourceCleanup()
        checkRecoveryIdempotency()
        return violations
    }

    // ── INV-1 session uniqueness ─────────────────────────────────────

    private fun checkSessionUniqueness() {
        val startedEvents = deviceEvents.filterIsInstance<DeviceEvent.ShotStarted>()
        val completedEvents = deviceEvents.filterIsInstance<DeviceEvent.ShotCompleted>()
        val failedEvents = deviceEvents.filterIsInstance<DeviceEvent.ShotFailed>()

        // INV-1b is evaluated on the session acceptance plane: a shot is activated
        // at most once (capture.saving / recording.started), even if the device
        // emitted duplicate Started events.
        val traceEvents = trace.snapshot()
        val acceptedStarts = (traceEvents.filter { it.name == "capture.saving" } +
            traceEvents.filter { it.name == "recording.started" })
            .mapNotNull { event ->
                Regex("shot=([^,]+)").find(event.detail)?.groupValues?.get(1)
            }
        acceptedStarts.groupBy { it }.forEach { (shotId, starts) ->
            if (starts.size > 1) {
                violations += Violation(
                    "INV-1b",
                    "session accepted ShotStarted for $shotId ${starts.size} times"
                )
            }
        }

        // INV-1d is evaluated on the session acceptance plane: each shot id is
        // terminal-applied at most once (capture.saved/recording.saved and
        // capture.failed/recording.failed traces).
        val appliedTerminals = traceEvents.filter {
            it.name == "capture.saved" || it.name == "recording.saved" ||
                it.name == "capture.failed" || it.name == "recording.failed"
        }.mapNotNull { event ->
            val shotId = Regex("^([^:]+)").find(event.detail)?.groupValues?.get(1)
                ?: Regex("shotId=([^,]+)").find(event.detail)?.groupValues?.get(1)
                ?: Regex("([^:]+):.*").find(event.detail)?.groupValues?.get(1)
            shotId
        }
        appliedTerminals.groupBy { it }.forEach { (shotId, terminals) ->
            if (terminals.size > 1) {
                violations += Violation(
                    "INV-1d",
                    "session applied ${terminals.size} terminal events for shot $shotId"
                )
            }
        }

        // 1c: between two consecutive shot submissions (capture.photo /
        // recording.requested) the previous shot must have been released by a
        // terminal outcome, a session-side interruption (failed), a liveness
        // force-release or a watchdog timeout. This guards concurrent shooting
        // on the session acceptance plane (session-side interruptions do not
        // emit device terminals).
        // A re-armed photo (DataReceived/readiness set) releases the shutter
        // even though its postprocess continues in the background; submitting a
        // new shot then is legitimate.
        val releaseEvents = setOf(
            "capture.saved", "recording.saved",
            "capture.failed", "recording.failed",
            "liveness.session.release",
            "liveness.document.force-release",
            "recording.watchdog.timeout",
            "capture.data.received",
            "capture.readiness.set"
        )
        val openEvents = setOf("capture.photo", "recording.requested")
        val submissionEvents = traceEvents.filter { it.name in openEvents || it.name in releaseEvents }
        var lastSubmissionIndex = -1
        submissionEvents.forEachIndexed { index, event ->
            if (event.name in openEvents) {
                if (lastSubmissionIndex >= 0) {
                    val releasedBetween = submissionEvents
                        .subList(lastSubmissionIndex + 1, index)
                        .any { it.name in releaseEvents }
                    if (!releasedBetween) {
                        violations += Violation(
                            "INV-1c",
                            "shot submitted without releasing the previous one " +
                                "(trace ${submissionEvents[lastSubmissionIndex].name} at " +
                                "seq=${submissionEvents[lastSubmissionIndex].sequence} -> " +
                                "${event.name} at seq=${event.sequence})"
                        )
                    }
                }
                lastSubmissionIndex = index
            }
        }

        // 1e: a stale/duplicate ShotStarted must never replace the active shot
        // directly: between two adjacent recorded states the activeShot may only
        // change through an intermediate null (terminal/rearm/watchdog clearing).
        states.zipWithNext().forEach { (previous, current) ->
            val previousId = previous.activeShot?.shotId
            val currentId = current.activeShot?.shotId
            if (previousId != null && currentId != null && previousId != currentId) {
                violations += Violation(
                    "INV-1e",
                    "activeShot jumped $previousId -> $currentId between adjacent states"
                )
            }
        }
    }

    // ── INV-2 output atomicity ───────────────────────────────────────

    private fun checkOutputAtomicity() {
        val terminalByShot = mutableMapOf<String, String>()
        deviceEvents.forEach { event ->
            when (event) {
                is DeviceEvent.ShotCompleted -> terminalByShot[event.result.shotId] = "completed"
                is DeviceEvent.ShotFailed -> terminalByShot[event.shotId] = "failed"
                else -> Unit
            }
        }

        val claims = mutableListOf<FakeMediaStoreModel.OutputClaim>()
        deviceEvents.forEach { event ->
            when (event) {
                is DeviceEvent.ShotCompleted -> {
                    val path = event.result.outputPath
                    val kind = if (event.result.mediaType == com.opencamera.core.media.MediaType.PHOTO) {
                        "photo"
                    } else {
                        "video"
                    }
                    val claim = mediaStore.claim(event.result.shotId, kind, path)
                    mediaStore.commit(claim)
                    claims += claim
                }

                is DeviceEvent.ShotFailed -> Unit
                else -> Unit
            }
        }
        // INV-2b: at most one distinct output claim per shot (deduplicated by path).
        claims.groupBy { it.shotId }.forEach { (shotId, shotClaims) ->
            val distinctPaths = shotClaims.distinctBy { it.path }
            if (distinctPaths.size > 1) {
                violations += Violation(
                    "INV-2b",
                    "shot $shotId produced ${distinctPaths.size} distinct output paths"
                )
            }
        }

        // 2c: ShotCompleted requires a prior ShotStarted of the same shot.
        val startedIds = deviceEvents.filterIsInstance<DeviceEvent.ShotStarted>()
            .map { it.shot.shotId }.toSet()
        deviceEvents.filterIsInstance<DeviceEvent.ShotCompleted>().forEach { event ->
            if (event.result.shotId !in startedIds) {
                violations += Violation(
                    "INV-2c",
                    "phantom ShotCompleted for ${event.result.shotId} without ShotStarted"
                )
            }
        }

        // 2d: a failed shot's output must be rolled back (no visible output).
        deviceEvents.filterIsInstance<DeviceEvent.ShotFailed>().forEach { event ->
            mediaStore.claimsFor(event.shotId).forEach { claim ->
                if (claim.committed && !claim.rolledBack) {
                    violations += Violation(
                        "INV-2d",
                        "failed shot ${event.shotId} left visible output ${claim.path}"
                    )
                }
            }
        }

        // 2e: no pending MediaStore rows may remain at scenario end.
        if (mediaStore.pendingRowCount() > 0) {
            violations += Violation(
                "INV-2e",
                "MediaStore pending rows did not converge: ${mediaStore.pendingRowCount()} pending"
            )
        }
    }

    // ── INV-3 state truthfulness ─────────────────────────────────────

    private fun checkStateTruthfulness() {
        states.forEach { state ->
            val active = state.activeShot
            when (state.captureStatus) {
                CaptureStatus.SAVING -> {
                    val rearmedWindow = active == null &&
                        state.presentation.captureReadiness != null &&
                        state.presentation.pendingPostprocess != null
                    if (active == null && !rearmedWindow) {
                        violations += Violation(
                            "INV-3a",
                            "captureStatus=SAVING without activeShot and without committed-rearm window"
                        )
                    } else if (active != null && active.mediaType != com.opencamera.core.media.MediaType.PHOTO) {
                        violations += Violation(
                            "INV-3a",
                            "captureStatus=SAVING with video shot ${active.shotId}"
                        )
                    }
                }

                CaptureStatus.DATA_RECEIVED -> {
                    if (active != null) {
                        violations += Violation(
                            "INV-3a",
                            "captureStatus=DATA_RECEIVED with activeShot still set"
                        )
                    }
                    val pending = state.presentation.pendingPostprocess
                    if (pending == null) {
                        violations += Violation(
                            "INV-3a",
                            "captureStatus=DATA_RECEIVED without pendingPostprocess"
                        )
                    } else if (pending.shotId.isEmpty()) {
                        violations += Violation("INV-3a", "pendingPostprocess without shotId")
                    }
                }

                else -> Unit
            }

            when (state.recordingStatus) {
                RecordingStatus.REQUESTING,
                RecordingStatus.RECORDING,
                RecordingStatus.STOPPING -> {
                    if (active == null) {
                        violations += Violation(
                            "INV-3b",
                            "recordingStatus=${state.recordingStatus} without activeShot"
                        )
                    } else if (active.mediaType != com.opencamera.core.media.MediaType.VIDEO) {
                        violations += Violation(
                            "INV-3b",
                            "recordingStatus=${state.recordingStatus} with photo shot ${active.shotId}"
                        )
                    }
                }

                else -> Unit
            }

            if (state.captureStatus == CaptureStatus.FAILED && state.lastError == null) {
                violations += Violation("INV-3c", "captureStatus=FAILED without lastError")
            }

            if (state.previewStatus == PreviewStatus.RECOVERING &&
                state.previewMetrics.consecutiveRecoveryCount > 3
            ) {
                violations += Violation(
                    "INV-3e",
                    "consecutiveRecoveryCount=${state.previewMetrics.consecutiveRecoveryCount} exceeds max"
                )
            }

            val countdown = state.presentation.countdownRemainingSeconds
            if (countdown != null && countdown < 0) {
                violations += Violation("INV-3f", "negative countdown $countdown")
            }
        }
    }

    // ── INV-4 resource cleanup ───────────────────────────────────────

    private fun checkResourceCleanup() {
        val state = finalState
        val pendingPostprocess = state.presentation.pendingPostprocess
        if (pendingPostprocess != null) {
            violations += Violation(
                "INV-4a",
                "final state still has pendingPostprocess for ${pendingPostprocess.shotId}"
            )
        }
        if (state.presentation.captureReadiness != null) {
            violations += Violation("INV-4a", "final state still has captureReadiness set")
        }
        if (state.presentation.pendingCaptureFeedback != null) {
            violations += Violation("INV-4a", "final state still has pendingCaptureFeedback")
        }
        if (state.captureStatus != CaptureStatus.IDLE && state.captureStatus != CaptureStatus.FAILED) {
            violations += Violation(
                "INV-4a",
                "final captureStatus=${state.captureStatus} (expected IDLE or FAILED)"
            )
        }
        if (state.captureStatus == CaptureStatus.FAILED && state.lastError == null) {
            violations += Violation(
                "INV-4a",
                "final captureStatus=FAILED without lastError"
            )
        }
        if (state.recordingStatus != RecordingStatus.IDLE) {
            violations += Violation(
                "INV-4a",
                "final recordingStatus=${state.recordingStatus} (expected IDLE)"
            )
        }
        val activeShot = state.activeShot
        if (activeShot != null) {
            violations += Violation(
                "INV-4a",
                "final state still has activeShot ${activeShot.shotId}"
            )
        }

        if (mediaStore.unresolvedOutputCount() > 0) {
            violations += Violation(
                "INV-4c",
                "unresolved output claims (neither committed nor rolled back): " +
                    mediaStore.unresolvedOutputCount()
            )
        }
        if (mediaStore.pendingRowCount() > 0) {
            violations += Violation("INV-4d", "MediaStore pending rows did not converge")
        }

        // 4b: if the scenario ended with Shutdown, the session must be STOPPED.
        if (states.last().lifecycle == com.opencamera.core.session.SessionLifecycle.STOPPED) {
            if (state.previewStatus != PreviewStatus.IDLE) {
                violations += Violation(
                    "INV-4b",
                    "stopped session left previewStatus=${state.previewStatus}"
                )
            }
        }
    }

    // ── INV-5 recovery idempotency ───────────────────────────────────

    private fun checkRecoveryIdempotency() {
        val traceEvents = trace.snapshot()
        val firstFrames = traceEvents.filter { it.name == "preview.first.frame" }.size
        val bindingStarted = traceEvents.filter { it.name == "preview.binding.started" }.size
        val recoveryStarted = traceEvents.filter { it.name == "preview.recovery.started" }.size

        // 5a: recovery metrics must not grow beyond the events that actually happened.
        val bindCount = finalState.previewMetrics.bindCount
        if (bindCount > bindingStarted + recoveryStarted) {
            violations += Violation(
                "INV-5b",
                "bindCount=$bindCount exceeds binding events (${bindingStarted + recoveryStarted})"
            )
        }
        if (finalState.previewStatus == PreviewStatus.ACTIVE &&
            finalState.previewMetrics.consecutiveRecoveryCount != 0
        ) {
            violations += Violation(
                "INV-5b",
                "consecutiveRecoveryCount=${finalState.previewMetrics.consecutiveRecoveryCount} " +
                    "not reset after first frame"
            )
        }

        // 5d: repeated lifecycle intents must be no-ops (already enforced by state checks).
    }
}
