package com.opencamera.core.session

import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.LiveBundleStatus
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotExecutor
import com.opencamera.core.media.ShotPlan
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.media.hasPostProcessFailures
import com.opencamera.core.media.isTemporalMedia
import com.opencamera.core.media.outputPathOrNull
import com.opencamera.core.media.renderUriOrNull
import com.opencamera.core.media.postProcessFailureSummary
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Functional interface for applying state transformations to [SessionState].
 *
 * Wraps a [MutableStateFlow] so processors can update state without
 * depending on the full [DefaultCameraSession.updateState] signature.
 */
internal fun interface SessionStateUpdater {
    fun update(transform: (SessionState) -> SessionState)
}

/**
 * Handles capture and recording lifecycle intents extracted from [DefaultCameraSession].
 *
 * Responsibilities:
 * - Countdown management (start, tick, complete, cancel)
 * - Shot lifecycle (started, completed, failed, interrupted)
 * - Recording watchdog timeout
 * - Shot plan submission and capture strategy execution
 */
internal class CaptureRecordingSessionProcessor(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SessionState>,
    private val effects: MutableSharedFlow<SessionEffect>,
    private val trace: SessionTrace,
    private val shotExecutor: ShotExecutor,
    private val currentController: () -> ModeController,
    private val resolvedActiveDeviceGraph: () -> DeviceGraphSpec,
    private val updateState: SessionStateUpdater,
    private val dispatch: suspend (SessionIntent) -> Unit
) {
    private var pendingCountdownJob: Job? = null
    private var pendingCountdownStrategy: CaptureStrategy? = null
    private var recordingWatchdogJob: Job? = null
    private var recordingElapsedJob: Job? = null

    // ── Public queries ──────────────────────────────────────────────

    fun countdownInProgress(): Boolean = pendingCountdownStrategy != null

    // ── Intent dispatch ─────────────────────────────────────────────

    suspend fun process(intent: SessionIntent) {
        when (intent) {
            is SessionIntent.CountdownTick -> handleCountdownTick(intent.remainingSeconds)
            SessionIntent.CountdownCompleted -> handleCountdownCompleted()
            is SessionIntent.ShotStarted -> handleShotStarted(intent.shot)
            is SessionIntent.ShotCompleted -> handleShotCompleted(intent.result)
            is SessionIntent.ShotFailed -> handleShotFailed(
                shotId = intent.shotId,
                mediaType = intent.mediaType,
                reason = intent.reason
            )
            else -> error("Unexpected capture/recording intent: $intent")
        }
    }

    // ── Recording watchdog ──────────────────────────────────────────

    fun startRecordingWatchdog(expectedStatus: RecordingStatus, timeoutMillis: Long) {
        recordingWatchdogJob?.cancel()
        recordingWatchdogJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            delay(timeoutMillis)
            if (state.value.recordingStatus == expectedStatus) {
                trace.record("recording.watchdog.timeout", "status=$expectedStatus, timeout=${timeoutMillis}ms")
                recordingElapsedJob?.cancel()
                recordingElapsedJob = null
                updateState.update { s ->
                    s.copy(
                        recordingStatus = RecordingStatus.IDLE,
                        activeShot = null,
                        presentation = s.presentation.copy(
                            recordingStartedAtElapsedMillis = null,
                            recordingElapsedMillis = null,
                            lastAction = "Recording timed out after ${timeoutMillis}ms",
                            lastError = "Recording state $expectedStatus timed out"
                        )
                    )
                }
            }
        }
    }

    fun cancelRecordingWatchdog() {
        recordingWatchdogJob?.cancel()
        recordingWatchdogJob = null
    }

    private fun cancelRecordingElapsedTimer() {
        recordingElapsedJob?.cancel()
        recordingElapsedJob = null
        updateState.update { s ->
            s.copy(
                presentation = s.presentation.copy(
                    recordingStartedAtElapsedMillis = null,
                    recordingElapsedMillis = null
                )
            )
        }
    }

    // ── Countdown management ────────────────────────────────────────

    private suspend fun handleCountdownTick(remainingSeconds: Int) {
        if (!countdownInProgress()) {
            return
        }
        updateState.update { s ->
            s.copy(
                captureStatus = CaptureStatus.REQUESTED,
                presentation = s.presentation.copy(
                    countdownRemainingSeconds = remainingSeconds,
                    lastAction = "Photo capture starts in ${remainingSeconds}s",
                    lastError = null
                )
            )
        }
        trace.record("capture.countdown.tick", "${remainingSeconds}s")
    }

    private suspend fun handleCountdownCompleted() {
        val strategy = pendingCountdownStrategy ?: return
        pendingCountdownStrategy = null
        pendingCountdownJob = null
        updateState.update { s ->
            s.copy(
                presentation = s.presentation.copy(
                    countdownRemainingSeconds = null,
                    lastAction = "Countdown finished",
                    lastError = null
                )
            )
        }
        submitCaptureStrategy(strategy)
    }

    fun cancelPendingCountdown(reason: String) {
        pendingCountdownJob?.cancel()
        pendingCountdownJob = null
        pendingCountdownStrategy = null
        updateState.update { s ->
            s.copy(
                captureStatus = CaptureStatus.IDLE,
                presentation = s.presentation.copy(
                    countdownRemainingSeconds = null,
                    lastAction = reason,
                    lastError = null
                )
            )
        }
        trace.record("capture.countdown.cancelled", reason)
    }

    fun startCaptureCountdown(
        strategy: CaptureStrategy,
        countdownSeconds: Int
    ) {
        pendingCountdownJob?.cancel()
        pendingCountdownStrategy = strategy
        updateState.update { s ->
            s.copy(
                captureStatus = CaptureStatus.REQUESTED,
                presentation = s.presentation.copy(
                    countdownRemainingSeconds = countdownSeconds,
                    lastAction = "Photo capture starts in ${countdownSeconds}s",
                    lastError = null
                )
            )
        }
        trace.record("capture.countdown.started", "${countdownSeconds}s")
        pendingCountdownJob = scope.launch {
            for (remainingSeconds in countdownSeconds - 1 downTo 1) {
                delay(1_000)
                dispatch(SessionIntent.CountdownTick(remainingSeconds))
            }
            delay(1_000)
            dispatch(SessionIntent.CountdownCompleted)
        }
    }

    // ── Shot lifecycle ──────────────────────────────────────────────

    private suspend fun handleShotStarted(shot: ShotRequest) {
        currentController().onSessionEvent(ModeSessionEvent.ShotStarted(shot))
        recordingElapsedJob?.cancel()
        recordingElapsedJob = null
        val startedAt = if (shot.mediaType == MediaType.VIDEO) {
            System.nanoTime() / 1_000_000L
        } else {
            null
        }
        updateState.update { s ->
            s.copy(
                captureStatus = if (shot.mediaType == MediaType.PHOTO) {
                    CaptureStatus.SAVING
                } else {
                    CaptureStatus.IDLE
                },
                recordingStatus = if (shot.mediaType == MediaType.VIDEO) {
                    RecordingStatus.RECORDING
                } else {
                    RecordingStatus.IDLE
                },
                activeShot = shot,
                modeSnapshot = currentController().snapshot.value,
                activeDeviceCapabilities = s.activeDeviceCapabilities,
                activeDeviceGraph = resolvedActiveDeviceGraph(),
                presentation = s.presentation.copy(
                    countdownRemainingSeconds = null,
                    pendingCaptureFeedback = null,
                    recordingStartedAtElapsedMillis = startedAt,
                    recordingElapsedMillis = if (shot.mediaType == MediaType.VIDEO) 0L else null,
                    lastAction = if (shot.mediaType == MediaType.PHOTO) {
                        if (shot.livePhotoSpec != null) {
                            "Saving Live photo bundle"
                        } else {
                            "Saving captured photo"
                        }
                    } else {
                        "Video recording started"
                    },
                    lastError = null
                )
            )
        }
        if (shot.mediaType == MediaType.VIDEO) {
            val shotId = shot.shotId
            recordingElapsedJob = scope.launch {
                while (state.value.activeShot?.shotId == shotId &&
                    state.value.recordingStatus == RecordingStatus.RECORDING
                ) {
                    delay(1_000)
                    val current = state.value
                    if (current.activeShot?.shotId != shotId ||
                        current.recordingStatus != RecordingStatus.RECORDING
                    ) break
                    val elapsed = startedAt?.let { System.nanoTime() / 1_000_000L - it } ?: 0L
                    updateState.update { s ->
                        s.copy(
                            presentation = s.presentation.copy(
                                recordingElapsedMillis = elapsed
                            )
                        )
                    }
                }
            }
        }
        if (shot.mediaType == MediaType.PHOTO) {
            trace.record("capture.feedback.snapshot.requested", "shotId=${shot.shotId}")
        }
        trace.record(
            if (shot.mediaType == MediaType.PHOTO) "capture.saving" else "recording.started",
            "shot=${shot.shotId},mode=${currentController().id}"
        )
    }

    private suspend fun handleShotCompleted(result: ShotResult) {
        currentController().onSessionEvent(ModeSessionEvent.ShotCompleted(result))
        recordingElapsedJob?.cancel()
        recordingElapsedJob = null
        updateState.update { s ->
            s.copy(
                captureStatus = if (result.mediaType == MediaType.PHOTO) {
                    CaptureStatus.COMPLETED
                } else {
                    CaptureStatus.IDLE
                },
                recordingStatus = RecordingStatus.IDLE,
                activeShot = null,
                modeSnapshot = currentController().snapshot.value,
                activeDeviceCapabilities = s.activeDeviceCapabilities,
                activeDeviceGraph = resolvedActiveDeviceGraph(),
                presentation = s.presentation.copy(
                    countdownRemainingSeconds = null,
                    recordingStartedAtElapsedMillis = null,
                    recordingElapsedMillis = null,
                    previewThumbnailPath = result.thumbnailSource.outputPathOrNull()
                        ?: s.presentation.previewThumbnailPath,
                    latestThumbnailSource = when (result.thumbnailSource) {
                        ThumbnailSource.None -> {
                            val previous = s.presentation.latestThumbnailSource
                            if (previous == null || previous is ThumbnailSource.Pending) {
                                ThumbnailSource.Pending
                            } else {
                                previous
                            }
                        }
                        else -> result.thumbnailSource
                    },
                    lastAction = if (result.mediaType == MediaType.PHOTO) {
                        val hasFailures = result.hasPostProcessFailures()
                        when {
                            result.livePhotoBundle?.bundleStatus == LiveBundleStatus.STILL_ONLY_FALLBACK ->
                                "Live photo saved (still only)"
                            result.livePhotoBundle?.isTemporalMedia() == true ->
                                if (hasFailures) "Live photo saved (degraded)" else "Live photo saved"
                            hasFailures -> "Photo saved (degraded)"
                            else -> "Photo saved"
                        }
                    } else {
                        "Video saved"
                    },
                    latestCapturePath = if (result.mediaType == MediaType.PHOTO) {
                        result.outputPath
                    } else {
                        s.presentation.latestCapturePath
                    },
                    latestVideoPath = if (result.mediaType == MediaType.VIDEO) {
                        result.outputPath
                    } else {
                        s.presentation.latestVideoPath
                    },
                    latestLivePhotoBundle = latestLivePhotoBundleFor(result),
                    latestSavedMediaType = if (result.mediaType == MediaType.PHOTO) {
                        SavedMediaType.PHOTO
                    } else {
                        SavedMediaType.VIDEO
                    },
                    latestPipelineNotes = result.pipelineNotes,
                    pendingCaptureFeedback = null,
                    lastError = result.postProcessFailureSummary(),
                    documentBatch = if (s.activeMode == ModeId.DOCUMENT &&
                        s.presentation.documentBatch.status == DocumentBatchStatus.ACTIVE &&
                        result.mediaType == MediaType.PHOTO
                    ) {
                        val currentBatch = s.presentation.documentBatch
                        val cropStatus = documentCropStatusFrom(result.pipelineNotes)
                        val cropSuffix = when (cropStatus) {
                            DocumentBatchCropStatus.APPLIED -> " • auto-cropped"
                            DocumentBatchCropStatus.SKIPPED -> " • original kept"
                            DocumentBatchCropStatus.FAILED -> " • processing degraded"
                            DocumentBatchCropStatus.NOT_REQUESTED -> ""
                        }
                        val newItem = DocumentBatchItem(
                            itemId = result.shotId,
                            shotId = result.shotId,
                            orderIndex = currentBatch.items.size,
                            outputPath = result.outputPath,
                            renderUri = result.thumbnailSource.renderUriOrNull(),
                            thumbnailSource = result.thumbnailSource,
                            profileId = result.metadata.customTags["profile"],
                            scanMode = result.metadata.customTags["scanMode"],
                            cropStatus = cropStatus,
                            pipelineNotes = result.pipelineNotes
                        )
                        currentBatch.copy(
                            items = currentBatch.items + newItem,
                            latestItemId = newItem.itemId,
                            lastMessage = "Page added$cropSuffix"
                        )
                    } else {
                        s.presentation.documentBatch
                    }
                )
            )
        }
        trace.record(
            if (result.mediaType == MediaType.PHOTO) "capture.saved" else "recording.saved",
            result.outputPath
        )
        val t = result.timing
        val requested = t.requestedAtElapsedMillis
        val postCompleted = t.postProcessCompletedAtElapsedMillis
        if (requested != null && postCompleted != null) {
            val deviceStarted = t.deviceCaptureStartedAtElapsedMillis
            val deviceCompleted = t.deviceCaptureCompletedAtElapsedMillis
            val deviceMs = if (deviceStarted != null && deviceCompleted != null) {
                "${deviceCompleted - deviceStarted}"
            } else {
                "--"
            }
            val postprocessMs = if (deviceCompleted != null) {
                "${postCompleted - deviceCompleted}"
            } else {
                "--"
            }
            trace.record(
                if (result.mediaType == MediaType.PHOTO) "capture.timing" else "recording.timing",
                "shot=${result.shotId},device=${deviceMs}ms,postprocess=${postprocessMs}ms,total=${postCompleted - requested}ms"
            )
        }
    }

    private fun latestLivePhotoBundleFor(result: ShotResult): LivePhotoBundle? {
        return if (result.mediaType == MediaType.PHOTO) {
            result.livePhotoBundle?.takeIf { it.isTemporalMedia() }
        } else {
            state.value.presentation.latestLivePhotoBundle
        }
    }

    private suspend fun handleShotFailed(
        shotId: String,
        mediaType: MediaType,
        reason: String
    ) {
        val currentActiveShot = state.value.activeShot
        if (currentActiveShot == null) {
            trace.record("shot.failed.orphaned", "shotId=$shotId,reason=$reason")
            return
        }
        if (currentActiveShot.shotId != shotId) {
            trace.record("shot.failed.duplicate", "$shotId:$reason")
            return
        }
        currentController().onSessionEvent(
            ModeSessionEvent.ShotFailed(
                shotId = shotId,
                mediaType = mediaType,
                reason = reason
            )
        )
        recordingElapsedJob?.cancel()
        recordingElapsedJob = null
        updateState.update { s ->
            s.copy(
                captureStatus = if (mediaType == MediaType.PHOTO) {
                    CaptureStatus.FAILED
                } else {
                    CaptureStatus.IDLE
                },
                recordingStatus = RecordingStatus.IDLE,
                activeShot = null,
                modeSnapshot = currentController().snapshot.value,
                activeDeviceCapabilities = s.activeDeviceCapabilities,
                activeDeviceGraph = resolvedActiveDeviceGraph(),
                presentation = s.presentation.copy(
                    countdownRemainingSeconds = null,
                    recordingStartedAtElapsedMillis = null,
                    recordingElapsedMillis = null,
                    lastAction = if (mediaType == MediaType.PHOTO) {
                        "Photo capture failed"
                    } else {
                        "Video recording failed"
                    },
                    latestPipelineNotes = emptyList(),
                    pendingCaptureFeedback = null,
                    lastError = reason
                )
            )
        }
        trace.record(
            if (mediaType == MediaType.PHOTO) "capture.failed" else "recording.failed",
            "$shotId:$reason"
        )
    }

    suspend fun handleInterruptedShotFailure(
        shot: ShotRequest,
        reason: String
    ) {
        cancelRecordingElapsedTimer()
        currentController().onSessionEvent(
            ModeSessionEvent.ShotFailed(
                shotId = shot.shotId,
                mediaType = shot.mediaType,
                reason = reason
            )
        )
        trace.record(
            if (shot.mediaType == MediaType.PHOTO) "capture.failed" else "recording.failed",
            "${shot.shotId}:$reason"
        )
    }

    // ── Capture strategy ────────────────────────────────────────────

    suspend fun submitCaptureStrategy(strategy: CaptureStrategy) {
        val plan = runCatching {
            shotExecutor.plan(
                strategy = strategy,
                activeShot = state.value.activeShot
            )
        }.map { createdPlan ->
            enrichPlanWithStillOutputSize(createdPlan)
        }.getOrElse { throwable ->
            updateState.update { s ->
                s.copy(
                    captureStatus = CaptureStatus.IDLE,
                    presentation = s.presentation.copy(
                        countdownRemainingSeconds = null,
                        lastAction = throwable.message ?: "Failed to create shot plan",
                        lastError = throwable.message
                    )
                )
            }
            trace.record("shot.plan.failed", throwable.message ?: "unknown")
            return
        }
        updateState.update { s ->
            s.copy(
                captureStatus = if (plan.request.mediaType == MediaType.PHOTO) {
                    CaptureStatus.REQUESTED
                } else {
                    CaptureStatus.IDLE
                },
                recordingStatus = if (plan.request.mediaType == MediaType.VIDEO) {
                    RecordingStatus.REQUESTING
                } else {
                    RecordingStatus.IDLE
                },
                activeShot = plan.request,
                presentation = s.presentation.copy(
                    countdownRemainingSeconds = null,
                    lastAction = if (plan.request.mediaType == MediaType.PHOTO) {
                        "Photo capture requested"
                    } else {
                        "Video recording requested"
                    },
                    lastError = null
                )
            )
        }
        if (plan.request.mediaType == MediaType.VIDEO) {
            startRecordingWatchdog(RecordingStatus.REQUESTING, 10_000L)
        }
        effects.emit(SessionEffect.ExecuteShot(plan))
        trace.record(
            if (plan.request.mediaType == MediaType.PHOTO) {
                "capture.photo"
            } else {
                "recording.requested"
            },
            "mode=${currentController().id},shot=${plan.request.shotId}"
        )
    }

    // ── Plan enrichment ─────────────────────────────────────────────

    private fun enrichPlanWithStillOutputSize(plan: ShotPlan): ShotPlan {
        if (plan.request.mediaType != MediaType.PHOTO) {
            return plan
        }

        val outputSize = state.value.activeDeviceGraph.stillCapture.outputSize ?: return plan
        val outputSizeLabel = "${outputSize.width}x${outputSize.height}"
        val updatedSaveRequest = plan.request.saveRequest.copy(
            metadata = plan.request.saveRequest.metadata.copy(
                customTags = plan.request.saveRequest.metadata.customTags + mapOf(
                    "stillOutputSize" to outputSizeLabel
                )
            )
        )
        val updatedRequest = plan.request.copy(
            saveRequest = updatedSaveRequest
        )
        val updatedSaveTask = plan.saveTask.copy(
            saveRequest = updatedSaveRequest
        )
        return plan.copy(
            request = updatedRequest,
            saveTask = updatedSaveTask
        )
    }
}

internal fun documentCropStatusFrom(notes: List<String>): DocumentBatchCropStatus {
    for (note in notes) {
        if (note == "document:auto-crop:applied") return DocumentBatchCropStatus.APPLIED
        if (note.startsWith("document:auto-crop:skipped:")) return DocumentBatchCropStatus.SKIPPED
        if (note.startsWith("document:auto-crop:failed:")) return DocumentBatchCropStatus.FAILED
    }
    return DocumentBatchCropStatus.NOT_REQUESTED
}
