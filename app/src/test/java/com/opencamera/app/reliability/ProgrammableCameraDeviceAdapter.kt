package com.opencamera.app.reliability

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.opencamera.app.camera.device.CameraDeviceAdapter
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceCommand
import com.opencamera.core.device.DeviceEvent
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.DeviceRuntimeIssueKind
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotPlan
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ShotTiming
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.media.postProcessFailureSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Deterministic device adapter for the Capture Reliability Closure.
 *
 * Implements the same [CameraDeviceAdapter] contract as
 * [com.opencamera.app.camera.CameraXCaptureAdapter], but every shot follows a
 * scripted event trace ([ShotScript]) and all outputs are tracked in a
 * [FakeMediaStoreModel]. Fault injection includes hung, duplicated, delayed and
 * failure-shaped event traces, camera disconnects and storage-full failures.
 */
class ProgrammableCameraDeviceAdapter(
    private val scope: CoroutineScope,
    private val mediaStore: FakeMediaStoreModel = FakeMediaStoreModel(),
    override val capabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT
) : CameraDeviceAdapter {
    private val mutableEvents = MutableSharedFlow<DeviceEvent>(
        replay = 32,
        extraBufferCapacity = 64
    )
    private var boundGraph: DeviceGraphSpec? = null
    private val shotScriptQueue = ArrayDeque<ShotScript>()
    private val finalizeScriptQueue = ArrayDeque<ShotScript>()
    private val pendingScriptedEmit = ArrayDeque<Pair<Long, ShotScript>>()

    /** Event trace in emission order (for invariant checks and replay). */
    val emittedEvents = mutableListOf<DeviceEvent>()

    val bindRequests = mutableListOf<DeviceGraphSpec>()
    val recordedCommands = mutableListOf<DeviceCommand>()
    var releaseCount: Int = 0
        private set

    /** Active video shot awaiting a finalize, keyed by shotId. */
    private var activeVideoShot: ShotPlan? = null

    override val events: Flow<DeviceEvent> = mutableEvents.asSharedFlow()

    /** Queues the script for the next ExecuteShot (finalize scripts go to the stop queue). */
    fun enqueueShotScript(script: ShotScript) {
        when (script) {
            ShotScript.FinalizeSuccess,
            is ShotScript.FinalizeError,
            ShotScript.FinalizeHang -> finalizeScriptQueue.addLast(script)
            else -> shotScriptQueue.addLast(script)
        }
    }

    fun outputClaimsFor(shotId: String): List<FakeMediaStoreModel.OutputClaim> =
        mediaStore.claimsFor(shotId)

    fun pendingRowCount(): Int = mediaStore.pendingRowCount()

    fun visibleOutputCount(): Int = mediaStore.visibleOutputCount()

    override suspend fun bindUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        deviceGraph: DeviceGraphSpec,
        manualCaptureParams: com.opencamera.core.settings.ManualCaptureParams?
    ) {
        bindRequests += deviceGraph
        boundGraph = deviceGraph
        if (bindEmitsFirstFrame) {
            emit(DeviceEvent.PreviewFirstFrameAvailable(48L))
        }
    }

    /** When false, binds never emit PreviewFirstFrameAvailable (recovery-limit scenarios). */
    var bindEmitsFirstFrame: Boolean = true

    override suspend fun dispatch(command: DeviceCommand) {
        recordedCommands += command
        when (command) {
            is DeviceCommand.ExecuteShot -> executeShot(command.plan)
            is DeviceCommand.StopActiveShot -> stopActiveShot(command.shotId)
            is DeviceCommand.UpdateZoomRatio -> Unit
            is DeviceCommand.SwitchLensNode -> Unit
            is DeviceCommand.UpdateOutputRotation -> Unit
            is DeviceCommand.ApplyPreviewMetering -> {
                emit(
                    DeviceEvent.PreviewMeteringCompleted(
                        com.opencamera.core.device.PreviewMeteringResult(
                            requestId = command.request.requestId,
                            point = command.request.point.clamped(),
                            status = com.opencamera.core.device.PreviewMeteringResultStatus.SUCCEEDED
                        )
                    )
                )
            }
            is DeviceCommand.CancelPreviewMetering -> Unit
            is DeviceCommand.ApplyPreviewBrightness -> {
                emit(
                    DeviceEvent.PreviewBrightnessApplied(
                        com.opencamera.core.device.PreviewBrightnessResult(
                            requestId = command.request.requestId,
                            exposureCompensationSteps = command.request.exposureCompensationSteps,
                            status = com.opencamera.core.device.PreviewBrightnessResultStatus.APPLIED
                        )
                    )
                )
            }
        }
    }

    override suspend fun release() {
        releaseCount += 1
        boundGraph = null
    }

    override fun boundGraph(): DeviceGraphSpec? = boundGraph

    // ── External fault injection (camera disconnect) ─────────────────

    fun emitRuntimeIssue(
        kind: DeviceRuntimeIssueKind,
        recoverable: Boolean,
        reason: String = "fixture camera $kind"
    ) {
        emit(
            DeviceEvent.RuntimeIssue(
                DeviceRuntimeIssue(
                    kind = kind,
                    reason = reason,
                    isRecoverable = recoverable
                )
            )
        )
    }

    fun emitSurfaceLost(reason: String = "fixture surface lost") {
        emit(DeviceEvent.PreviewSurfaceLost(reason))
    }

    fun emitPreviewError(reason: String = "fixture preview error") {
        emit(DeviceEvent.PreviewError(reason))
    }

    // ── Shot execution ───────────────────────────────────────────────

    private suspend fun executeShot(plan: ShotPlan) {
        val script = shotScriptQueue.removeFirstOrNull() ?: ShotScript.Normal
        if (plan.request.mediaType == MediaType.VIDEO) {
            // Video: Started marks the recording running; the finalize behavior is
            // decided by the stop path (finalizeScriptQueue), never by the start.
            when (script) {
                is ShotScript.LateStarted -> {
                    emitDelayedStarted(script.delayMillis, plan, script.inner)
                }

                is ShotScript.FailImmediately -> {
                    emitStarted(plan)
                    emit(
                        DeviceEvent.ShotFailed(
                            shotId = plan.request.shotId,
                            mediaType = plan.request.mediaType,
                            reason = script.reason
                        )
                    )
                }

                ShotScript.Normal -> emitStarted(plan)

                // Started never arrives: the REQUESTING watchdog must converge.
                ShotScript.HangAfterStarted -> Unit

                else -> emitStarted(plan)
            }
            return
        }
        when (script) {
            is ShotScript.LateStarted -> {
                pendingScriptedEmit.addLast(script.delayMillis to script.inner)
                emitDelayedStarted(script.delayMillis, plan, script.inner)
            }

            else -> playScript(script, plan)
        }
    }

    private suspend fun playScript(script: ShotScript, plan: ShotPlan) {
        when (script) {
            is ShotScript.Normal -> {
                emitStarted(plan)
                emitCommitted(plan)
                emitDataReceived(plan)
                emitCompleted(plan)
            }

            is ShotScript.FailImmediately -> {
                emitStarted(plan)
                emit(
                    DeviceEvent.ShotFailed(
                        shotId = plan.request.shotId,
                        mediaType = plan.request.mediaType,
                        reason = script.reason
                    )
                )
            }

            is ShotScript.FailAfterData -> {
                emitStarted(plan)
                emitCommitted(plan)
                emitDataReceived(plan)
                val claim = mediaStore.claim(
                    shotId = plan.request.shotId,
                    kind = outputKind(plan),
                    path = outputPath(plan)
                )
                // Simulate CameraXCaptureAdapter.cleanupStillCaptureArtifacts:
                // the failed shot's output is rolled back before the failure event.
                mediaStore.rollback(claim)
                emit(
                    DeviceEvent.ShotFailed(
                        shotId = plan.request.shotId,
                        mediaType = plan.request.mediaType,
                        reason = script.reason
                    )
                )
            }

            ShotScript.HangAfterStarted -> emitStarted(plan)

            ShotScript.HangAfterData -> {
                emitStarted(plan)
                emitCommitted(plan)
                emitDataReceived(plan)
            }

            ShotScript.DuplicateStarted -> {
                emitStarted(plan)
                emitStarted(plan)
                emitCommitted(plan)
                emitDataReceived(plan)
                emitCompleted(plan)
            }

            ShotScript.DuplicateCompleted -> {
                emitStarted(plan)
                emitCommitted(plan)
                emitDataReceived(plan)
                emitCompleted(plan)
                emitCompleted(plan)
            }

            is ShotScript.CompletedWithFailureSummary -> {
                emitStarted(plan)
                emitCommitted(plan)
                emitDataReceived(plan)
                val claim = mediaStore.claim(
                    shotId = plan.request.shotId,
                    kind = outputKind(plan),
                    path = outputPath(plan)
                )
                mediaStore.commit(claim)
                emitCompleted(plan, failureSummary = script.reason)
            }

            ShotScript.Oom -> {
                emitStarted(plan)
                emit(
                    DeviceEvent.ShotFailed(
                        shotId = plan.request.shotId,
                        mediaType = plan.request.mediaType,
                        reason = "OutOfMemoryError: fixture heap exhaustion"
                    )
                )
            }

            ShotScript.StorageFull -> {
                emitStarted(plan)
                emit(
                    DeviceEvent.ShotFailed(
                        shotId = plan.request.shotId,
                        mediaType = plan.request.mediaType,
                        reason = "Storage is full"
                    )
                )
            }

            is ShotScript.LateStarted -> Unit
            ShotScript.FinalizeSuccess,
            is ShotScript.FinalizeError,
            ShotScript.FinalizeHang -> Unit
        }
    }

    private suspend fun emitDelayedStarted(delayMillis: Long, plan: ShotPlan, inner: ShotScript) {
        scope.launch {
            delay(delayMillis)
            emitStarted(plan)
            if (plan.request.mediaType != MediaType.VIDEO) {
                when (inner) {
                    ShotScript.Normal -> {
                        emitCommitted(plan)
                        emitDataReceived(plan)
                        emitCompleted(plan)
                    }

                    else -> playScript(inner, plan)
                }
            }
        }
    }

    private suspend fun stopActiveShot(shotId: String) {
        val plan = activeVideoShot
        if (plan == null || plan.request.shotId != shotId) {
            emit(
                DeviceEvent.ShotFailed(
                    shotId = shotId,
                    mediaType = MediaType.VIDEO,
                    reason = "No active recording for $shotId"
                )
            )
            return
        }
        val script = if (finalizeScriptQueue.isNotEmpty()) {
            finalizeScriptQueue.removeFirst()
        } else {
            ShotScript.FinalizeSuccess
        }
        activeVideoShot = null
        when (script) {
            ShotScript.FinalizeSuccess -> {
                val claim = mediaStore.claim(
                    shotId = plan.request.shotId,
                    kind = "video",
                    path = outputPath(plan)
                )
                mediaStore.commit(claim)
                emitCompleted(plan)
            }

            is ShotScript.FinalizeError -> {
                val claim = mediaStore.claim(
                    shotId = plan.request.shotId,
                    kind = "video",
                    path = outputPath(plan)
                )
                mediaStore.rollback(claim)
                emit(
                    DeviceEvent.ShotFailed(
                        shotId = plan.request.shotId,
                        mediaType = plan.request.mediaType,
                        reason = script.reason
                    )
                )
            }

            ShotScript.FinalizeHang -> Unit
            else -> Unit
        }
    }

    private fun emitStarted(plan: ShotPlan) {
        if (plan.request.mediaType == MediaType.VIDEO) {
            activeVideoShot = plan
        }
        emit(DeviceEvent.ShotStarted(plan.request))
    }

    private fun emitCommitted(plan: ShotPlan) {
        emit(
            DeviceEvent.CaptureCommitted(
                shotId = plan.request.shotId,
                mediaType = plan.request.mediaType,
                source = "ProgrammableCameraDeviceAdapter",
                elapsedTimestampMs = 120L
            )
        )
    }

    private fun emitDataReceived(plan: ShotPlan) {
        emit(
            DeviceEvent.DataReceived(
                shotId = plan.request.shotId,
                mediaType = plan.request.mediaType
            )
        )
    }

    private fun emitCompleted(plan: ShotPlan, failureSummary: String? = null) {
        val path = outputPath(plan)
        // A duplicate completion must not double-claim the same physical output.
        if (mediaStore.claimsFor(plan.request.shotId).none { it.path == path }) {
            val claim = mediaStore.claim(
                shotId = plan.request.shotId,
                kind = outputKind(plan),
                path = path
            )
            mediaStore.commit(claim)
        }
        val notes = mutableListOf("fixture:camera-pipeline=completed")
        if (failureSummary != null) {
            notes += failureSummary
        }
        emit(
            DeviceEvent.ShotCompleted(
                ShotResult(
                    shotId = plan.request.shotId,
                    mediaType = plan.request.mediaType,
                    outputPath = path,
                    outputHandle = MediaOutputHandle(displayPath = path),
                    saveRequest = plan.request.saveRequest,
                    thumbnailSource = ThumbnailSource.SavedMedia(path),
                    captureProfile = plan.request.captureProfile,
                    metadata = MediaMetadata(customTags = mapOf("fixture" to "reliability")),
                    pipelineNotes = notes,
                    timing = ShotTiming(
                        requestedAtElapsedMillis = 0L,
                        deviceCaptureStartedAtElapsedMillis = 0L,
                        deviceCaptureCompletedAtElapsedMillis = 120L,
                        postProcessCompletedAtElapsedMillis = 120L
                    )
                )
            )
        )
    }

    private fun outputKind(plan: ShotPlan): String =
        if (plan.request.mediaType == MediaType.PHOTO) "photo" else "video"

    private fun outputPath(plan: ShotPlan): String =
        "/fixture/${plan.request.mediaType.name.lowercase()}-${plan.request.shotId}.jpg"

    private fun emit(event: DeviceEvent) {
        emittedEvents += event
        mutableEvents.tryEmit(event)
    }
}

/**
 * In-memory model of the shared photo/video library used by the adapter.
 *
 * Mirrors MediaStore semantics: outputs are either committed (visible) or
 * rolled back (deleted). Pending rows model the live-photo sidecar IS_PENDING
 * lifecycle and must converge to zero.
 */
class FakeMediaStoreModel {
    data class OutputClaim(
        val shotId: String,
        val kind: String,
        val path: String,
        var committed: Boolean = false,
        var rolledBack: Boolean = false
    )

    data class PendingRow(
        val uri: String,
        val displayName: String,
        var pending: Boolean = true,
        var deleted: Boolean = false
    )

    private val claims = mutableListOf<OutputClaim>()
    private val pendingRows = mutableListOf<PendingRow>()
    private var rowCounter = 0

    fun claim(shotId: String, kind: String, path: String): OutputClaim {
        val claim = OutputClaim(shotId = shotId, kind = kind, path = path)
        claims += claim
        return claim
    }

    fun commit(claim: OutputClaim) {
        claim.committed = true
    }

    fun rollback(claim: OutputClaim) {
        claim.rolledBack = true
    }

    fun insertPendingRow(displayName: String): PendingRow {
        val row = PendingRow(
            uri = "content://fixture/media/${++rowCounter}",
            displayName = displayName
        )
        pendingRows += row
        return row
    }

    fun commitPendingRow(row: PendingRow) {
        row.pending = false
    }

    fun deletePendingRow(row: PendingRow) {
        row.deleted = true
    }

    fun claimsFor(shotId: String): List<OutputClaim> = claims.filter { it.shotId == shotId }

    fun pendingRowCount(): Int = pendingRows.count { it.pending && !it.deleted }

    fun visibleOutputCount(): Int = claims.count { it.committed && !it.rolledBack }

    /** Outputs that are neither committed nor rolled back (partial writes). */
    fun unresolvedOutputCount(): Int = claims.count { !it.committed && !it.rolledBack }
}
