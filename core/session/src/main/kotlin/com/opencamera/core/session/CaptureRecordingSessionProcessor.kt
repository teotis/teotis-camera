package com.opencamera.core.session

import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.EffectiveStillCaptureRecipe
import com.opencamera.core.effect.RenderRecipe
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.LiveBundleStatus
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessFailureCause.TIMEOUT
import com.opencamera.core.media.PostProcessFailureStage
import com.opencamera.core.media.PostProcessFailureCause
import com.opencamera.core.media.PostProcessLivenessDeadline
import com.opencamera.core.media.ShotConfigSnapshot
import com.opencamera.core.media.ShotExecutor
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotPlan
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.media.hasPostProcessFailures
import com.opencamera.core.media.isTemporalMedia
import com.opencamera.core.media.legacyNotePrefix
import com.opencamera.core.media.legacyNoteSuffix
import com.opencamera.core.media.outputPathOrNull
import com.opencamera.core.media.renderUriOrNull
import com.opencamera.core.media.postProcessFailureSummary
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSessionEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private val linkRecorder: PerformanceLinkRecorder,
    private val shotExecutor: ShotExecutor,
    private val currentController: () -> ModeController,
    private val resolvedActiveDeviceGraph: () -> DeviceGraphSpec,
    private val updateState: SessionStateUpdater,
    private val dispatch: suspend (SessionIntent) -> Unit,
    private val recordingTimerDispatcher: CoroutineDispatcher? = null,
    private val elapsedRealtimeMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private var pendingCountdownJob: Job? = null
    private var pendingCountdownStrategy: CaptureStrategy? = null
    private var recordingWatchdogJob: Job? = null
    private var recordingElapsedJob: Job? = null
    private val activeShotSpans = mutableMapOf<String, PerformanceSpanSnapshot>()
    private var documentBatchWatchdogJob: Job? = null
    private var documentBatchLiveness: PostProcessLivenessDeadline? = null
    private val forceReleasedShotIds = mutableSetOf<String>()
    private var postProcessLiveness: PostProcessLivenessDeadline? = null
    private var postProcessLivenessJob: Job? = null
    private var postProcessLivenessShot: ShotRequest? = null
    private var postProcessLivenessConfigSnapshot: ShotConfigSnapshot? = null
    private val conservativeForceReleasedShotIds = mutableSetOf<String>()

    /**
     * Extra grace window granted to conservative kinds (live photo / multi-frame) after the
     * post-process liveness deadline elapses, before the reducer force-releases the shot.
     *
     * The deadline itself ([PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS]) already aligns
     * with the longest [com.opencamera.core.media.AlgorithmJobSpec.timeoutMillis]; conservative
     * kinds legitimately need additional time because their pipelines run multiple frame passes
     * plus a composite stage. The grace below is the cooperative-cancel window: the pipeline
     * has this long to finish on its own after the deadline before [forceReleaseConservativeShot]
     * tears down [SessionState.activeShot].
     */
    private val conservativeLivenessGraceMs: Long = 2_000L

    // ── Public queries ──────────────────────────────────────────────

    fun countdownInProgress(): Boolean = pendingCountdownStrategy != null

    // ── Intent dispatch ─────────────────────────────────────────────

    suspend fun process(intent: SessionIntent) {
        when (intent) {
            is SessionIntent.CountdownTick -> handleCountdownTick(intent.remainingSeconds)
            SessionIntent.CountdownCompleted -> handleCountdownCompleted()
            is SessionIntent.ShotStarted -> handleShotStarted(intent.shot)
            is SessionIntent.CaptureCommitted -> handleCaptureCommitted(
                shotId = intent.shotId,
                mediaType = intent.mediaType,
                source = intent.source,
                elapsedTimestampMs = intent.elapsedTimestampMs
            )
            is SessionIntent.DataReceived -> handleDataReceived(intent.shotId, intent.mediaType)
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
        recordingWatchdogJob = launchRecordingTimer {
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

    fun cancelRecordingElapsedTimer() {
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
            elapsedRealtimeMillis()
        } else {
            null
        }
        if (shot.mediaType == MediaType.VIDEO && startedAt != null) {
            val shutterPressedAt = state.value.shutterPressedAtElapsedMillis
            if (shutterPressedAt != null && shutterPressedAt > 0) {
                val startupLatencyMs = startedAt - shutterPressedAt
                if (startupLatencyMs >= 0) {
                    trace.record("recording.startup.latency", "${startupLatencyMs}ms")
                }
            }
        }
        val displayedStartedAt = if (shot.mediaType == MediaType.VIDEO) 0L else null
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
                shutterPressedAtElapsedMillis = null,
                modeSnapshot = currentController().snapshot.value,
                activeDeviceCapabilities = s.activeDeviceCapabilities,
                activeDeviceGraph = resolvedActiveDeviceGraph(),
                presentation = s.presentation.copy(
                    countdownRemainingSeconds = null,
                    pendingCaptureFeedback = null,
                    captureReadiness = null,
                    recordingStartedAtElapsedMillis = displayedStartedAt,
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
            recordingElapsedJob = launchRecordingTimer {
                while (state.value.activeShot?.shotId == shotId &&
                    state.value.recordingStatus == RecordingStatus.RECORDING
                ) {
                    delay(1_000)
                    val current = state.value
                    if (current.activeShot?.shotId != shotId ||
                        current.recordingStatus != RecordingStatus.RECORDING
                    ) break
                    val elapsed = startedAt?.let { elapsedRealtimeMillis() - it } ?: 0L
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
        maybeStartDocumentBatchWatchdog(shot)
        maybeArmPostProcessLiveness(shot)
        // Record link event for device capture started
        activeShotSpans[shot.shotId]?.let { _ ->
            linkRecorder.recordEvent(
                PerformanceLinkEvent(
                    flow = if (shot.mediaType == MediaType.PHOTO) "capture" else "recording",
                    stage = "device_started",
                    status = LinkEventStatus.STARTED,
                    correlationId = shot.shotId,
                    startElapsedMillis = System.nanoTime() / 1_000_000L,
                    endElapsedMillis = null,
                    durationMillis = null,
                    detail = "mode=${currentController().id}",
                    source = "CaptureRecordingSessionProcessor"
                )
            )
        }
    }

    private fun canRearmOnDataReceived(shot: ShotRequest): Boolean {
        if (shot.mediaType != MediaType.PHOTO) return false
        return shot.shotKind == ShotKind.STILL_CAPTURE ||
            shot.shotKind == ShotKind.LIVE_PHOTO
    }

    private suspend fun handleDataReceived(shotId: String, mediaType: MediaType) {
        if (shouldForceReleaseDocumentBatchShot(shotId)) {
            forceReleaseDocumentBatchShot(
                shotId = shotId,
                mediaType = mediaType,
                reason = "deadline-expired:data-received"
            )
            return
        }
        updateState.update { s ->
            val activeShot = s.activeShot ?: return@update s
            if (activeShot.shotId != shotId || mediaType != MediaType.PHOTO) return@update s
            if (canRearmOnDataReceived(activeShot)) {
                s.copy(
                    captureStatus = CaptureStatus.DATA_RECEIVED,
                    activeShot = null,
                    presentation = s.presentation.copy(
                        captureReadiness = s.presentation.captureReadiness
                            ?: com.opencamera.core.device.CaptureReadiness(
                                shotId = shotId,
                                mediaType = mediaType,
                                source = "DeviceEvent.DataReceived",
                                elapsedTimestampMs = null
                            ),
                        pendingPostprocess = PendingPostprocessUiState(
                            shotId = shotId,
                            mediaType = mediaType,
                            message = "",
                            warnBeforeExit = true,
                            livenessAttachment = currentLivenessAttachment()
                        )
                    )
                )
            } else {
                s.copy(captureStatus = CaptureStatus.DATA_RECEIVED)
            }
        }
        trace.record("capture.data.received", "shotId=$shotId")
        // Record link event for data received
        activeShotSpans[shotId]?.let { _ ->
            linkRecorder.recordEvent(
                PerformanceLinkEvent(
                    flow = "capture",
                    stage = "data_received",
                    status = LinkEventStatus.COMPLETED,
                    correlationId = shotId,
                    startElapsedMillis = System.nanoTime() / 1_000_000L,
                    endElapsedMillis = null,
                    durationMillis = null,
                    detail = null,
                    source = "CaptureRecordingSessionProcessor"
                )
            )
        }
    }

    private fun canSignalReadinessOnCaptureCommitted(shot: ShotRequest): Boolean {
        if (shot.mediaType != MediaType.PHOTO) return false
        if (shot.livePhotoSpec != null) return false
        return shot.shotKind == ShotKind.STILL_CAPTURE
    }

    private fun handleCaptureCommitted(
        shotId: String,
        mediaType: MediaType,
        source: String,
        elapsedTimestampMs: Long?
    ) {
        updateState.update { s ->
            val activeShot = s.activeShot ?: return@update s
            if (activeShot.shotId != shotId || mediaType != MediaType.PHOTO) return@update s
            if (canSignalReadinessOnCaptureCommitted(activeShot)) {
                s.copy(
                    activeShot = null,
                    presentation = s.presentation.copy(
                        captureReadiness = com.opencamera.core.device.CaptureReadiness(
                            shotId = shotId,
                            mediaType = mediaType,
                            source = source,
                            elapsedTimestampMs = elapsedTimestampMs
                        ),
                        pendingPostprocess = PendingPostprocessUiState(
                            shotId = shotId,
                            mediaType = mediaType,
                            message = "",
                            warnBeforeExit = true,
                            livenessAttachment = currentLivenessAttachment()
                        )
                    )
                )
            } else {
                s
            }
        }
        trace.record("capture.committed", "shotId=$shotId,source=$source")
    }

    private suspend fun handleShotCompleted(result: ShotResult) {
        if (result.shotId in forceReleasedShotIds) {
            trace.record(
                "shot.completed.force-released.hydrated",
                "result=${result.shotId}"
            )
            hydrateForceReleasedDocumentBatchItem(result)
            return
        }
        if (result.shotId in conservativeForceReleasedShotIds) {
            trace.record(
                "shot.completed.conservative-force-released.stale",
                "result=${result.shotId}"
            )
            cancelPostProcessLiveness(result.shotId)
            return
        }
        if (shouldForceReleaseDocumentBatchShot(result.shotId)) {
            forceReleaseDocumentBatchShot(
                shotId = result.shotId,
                mediaType = result.mediaType,
                reason = "deadline-expired:shot-completed"
            )
            return
        }
        val currentActiveShot = state.value.activeShot
        if (currentActiveShot != null && currentActiveShot.shotId != result.shotId) {
            trace.record(
                "shot.completed.stale",
                "result=${result.shotId},active=${currentActiveShot.shotId}"
            )
            return
        }
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
                    pendingPostprocess = null,
                    captureReadiness = null,
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
                            DocumentBatchCropStatus.APPLIED -> ""
                            DocumentBatchCropStatus.APPLIED_MANUAL -> ""
                            DocumentBatchCropStatus.SKIPPED -> ""
                            DocumentBatchCropStatus.FAILED -> ""
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
                        val existingIndex = currentBatch.items.indexOfFirst { it.shotId == result.shotId }
                        val updatedItems = if (existingIndex >= 0) {
                            currentBatch.items.mapIndexed { index, item ->
                                if (index == existingIndex) newItem.copy(orderIndex = item.orderIndex) else item
                            }
                        } else {
                            currentBatch.items + newItem
                        }
                        currentBatch.copy(
                            items = updatedItems,
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
        // Complete capture/recording link span
        activeShotSpans.remove(result.shotId)?.let { span ->
            val hasFailures = result.hasPostProcessFailures()
            val linkStatus = when {
                hasFailures -> LinkEventStatus.DEGRADED
                else -> LinkEventStatus.COMPLETED
            }
            linkRecorder.completeSpan(
                span,
                status = linkStatus,
                detail = result.postProcessFailureSummary()
            )
        }
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
            if (deviceStarted != null && deviceStarted > 0) {
                val intentDelayMs = deviceStarted - requested
                if (intentDelayMs > 0) {
                    trace.record(
                        "capture.shutter.to.device",
                        "shot=${result.shotId},${intentDelayMs}ms"
                    )
                }
            }
        }

        // G1: Shutter-to-Capture latency
        if (result.mediaType == MediaType.PHOTO) {
            val deviceStarted = result.timing.deviceCaptureStartedAtElapsedMillis
            if (requested != null && deviceStarted != null) {
                val shutterToDeviceMs = deviceStarted - requested
                trace.record("capture.shutter.to.device", "${shutterToDeviceMs}ms")
                linkRecorder.recordEvent(
                    PerformanceLinkEvent(
                        flow = "capture",
                        stage = "shutter-to-device",
                        status = LinkEventStatus.COMPLETED,
                        correlationId = result.shotId,
                        startElapsedMillis = requested,
                        endElapsedMillis = deviceStarted,
                        durationMillis = shutterToDeviceMs,
                        detail = null,
                        source = "CaptureRecordingSessionProcessor"
                    )
                )
            }
        }

        // DFS-13: Transition COMPLETED back to IDLE so shutter re-arms after presentation update.
        if (result.mediaType == MediaType.PHOTO && state.value.captureStatus == CaptureStatus.COMPLETED) {
            updateState.update { s ->
                s.copy(captureStatus = CaptureStatus.IDLE)
            }
        }
        emitPostProcessLivenessEventsForCompleted(result)
        cancelDocumentBatchWatchdog(result.shotId)
        cancelPostProcessLiveness(result.shotId)
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
        if (shotId in forceReleasedShotIds) {
            trace.record(
                "shot.failed.force-released.stale",
                "shotId=$shotId,reason=$reason"
            )
            return
        }
        if (shotId in conservativeForceReleasedShotIds) {
            trace.record(
                "shot.failed.conservative-force-released.stale",
                "shotId=$shotId,reason=$reason"
            )
            cancelPostProcessLiveness(shotId)
            return
        }
        if (shouldForceReleaseDocumentBatchShot(shotId)) {
            forceReleaseDocumentBatchShot(
                shotId = shotId,
                mediaType = mediaType,
                reason = "deadline-expired:shot-failed"
            )
            return
        }
        val currentActiveShot = state.value.activeShot
        if (currentActiveShot == null) {
            clearPendingPostprocessIfMatches(shotId)
            resetCaptureStatusFromTerminalOrDataReceived(shotId, reason)
            trace.record("shot.failed.orphaned", "shotId=$shotId,reason=$reason")
            cancelPostProcessLiveness(shotId)
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
                    pendingPostprocess = null,
                    captureReadiness = null,
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
        // Complete capture/recording link span as failed
        activeShotSpans.remove(shotId)?.let { span ->
            linkRecorder.completeSpan(span, status = LinkEventStatus.FAILED, detail = reason)
        }
        cancelDocumentBatchWatchdog(shotId)
        cancelPostProcessLiveness(shotId)
    }

    private fun clearPendingPostprocessIfMatches(shotId: String) {
        val pending = state.value.presentation.pendingPostprocess
        if (pending != null && pending.shotId == shotId) {
            updateState.update { s ->
                s.copy(
                    presentation = s.presentation.copy(pendingPostprocess = null)
                )
            }
        }
    }

    private suspend fun resetCaptureStatusFromTerminalOrDataReceived(shotId: String, reason: String) {
        val currentStatus = state.value.captureStatus
        if (currentStatus != CaptureStatus.DATA_RECEIVED && currentStatus != CaptureStatus.COMPLETED) {
            return
        }
        updateState.update { s ->
            s.copy(
                captureStatus = CaptureStatus.IDLE,
                presentation = s.presentation.copy(
                    lastAction = if (currentStatus == CaptureStatus.DATA_RECEIVED) {
                        "Orphaned postprocess cleared for $shotId"
                    } else {
                        "Shot failed after completion for $shotId"
                    },
                    lastError = reason,
                    captureReadiness = null
                )
            )
        }
    }

    suspend fun handleInterruptedShotFailure(
        shot: ShotRequest,
        reason: String
    ) {
        cancelRecordingElapsedTimer()
        // DFS-14: Clear activeShot if it still matches the interrupted shot.
        val matchedActiveShot = state.value.activeShot
        if (matchedActiveShot != null && matchedActiveShot.shotId == shot.shotId) {
            recordingElapsedJob?.cancel()
            recordingElapsedJob = null
            updateState.update { s ->
                s.copy(
                    captureStatus = CaptureStatus.FAILED,
                    recordingStatus = RecordingStatus.IDLE,
                    activeShot = null,
                    presentation = s.presentation.copy(
                        countdownRemainingSeconds = null,
                        recordingStartedAtElapsedMillis = null,
                        recordingElapsedMillis = null,
                        pendingPostprocess = null,
                        captureReadiness = null,
                        lastAction = if (shot.mediaType == MediaType.PHOTO) {
                            "Photo capture interrupted"
                        } else {
                            "Video recording interrupted"
                        },
                        pendingCaptureFeedback = null,
                        lastError = reason
                    )
                )
            }
        }
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
        // Complete capture/recording link span as failed
        activeShotSpans.remove(shot.shotId)?.let { span ->
            linkRecorder.completeSpan(span, status = LinkEventStatus.FAILED, detail = reason)
        }
        cancelDocumentBatchWatchdog(shot.shotId)
        cancelPostProcessLiveness(shot.shotId)
    }

    // ── Capture strategy ────────────────────────────────────────────

    suspend fun submitCaptureStrategy(strategy: CaptureStrategy) {
        val plan = runCatching {
            shotExecutor.plan(
                strategy = strategy,
                activeShot = state.value.activeShot
            )
        }.map { createdPlan ->
            enrichPlanWithZoomCrop(enrichPlanWithStillOutputSize(createdPlan))
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
        val flow = if (plan.request.mediaType == MediaType.PHOTO) "capture" else "recording"
        trace.record(
            if (plan.request.mediaType == MediaType.PHOTO) {
                "capture.photo"
            } else {
                "recording.requested"
            },
            "mode=${currentController().id},shot=${plan.request.shotId}"
        )
        // Start capture/recording link span
        activeShotSpans[plan.request.shotId] = linkRecorder.startSpan(
            flow = flow,
            stage = "requested",
            correlationId = plan.request.shotId,
            detail = "mode=${currentController().id}",
            source = "CaptureRecordingSessionProcessor"
        )
    }

    // ── Plan enrichment ─────────────────────────────────────────────

    private fun enrichPlanWithStillOutputSize(plan: ShotPlan): ShotPlan {
        if (plan.request.mediaType != MediaType.PHOTO) {
            return plan
        }

        val recipe = EffectiveStillCaptureRecipe.build(
            state.value.activeDeviceGraph,
            state.value.activeDeviceCapabilities
        )
        val recipeTags = recipe.metadataCustomTags
        if (recipeTags.isEmpty()) return plan

        val updatedSaveRequest = plan.request.saveRequest.copy(
            metadata = plan.request.saveRequest.metadata.copy(
                customTags = plan.request.saveRequest.metadata.customTags + recipeTags
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

    private fun enrichPlanWithZoomCrop(plan: ShotPlan): ShotPlan {
        if (plan.request.mediaType != MediaType.PHOTO) {
            return plan
        }
        val zoomRatio = state.value.activeDeviceGraph.preview.zoomRatio
        if (zoomRatio <= 1f) {
            return plan
        }
        val updatedSaveRequest = plan.request.saveRequest.copy(
            metadata = plan.request.saveRequest.metadata.copy(
                customTags = plan.request.saveRequest.metadata.customTags + mapOf(
                    "captureCropZoom" to zoomRatio.toString()
                )
            )
        )
        val updatedRequest = plan.request.copy(saveRequest = updatedSaveRequest)
        val updatedSaveTask = plan.saveTask.copy(saveRequest = updatedSaveRequest)
        return plan.copy(request = updatedRequest, saveTask = updatedSaveTask)
    }

    private fun launchRecordingTimer(block: suspend CoroutineScope.() -> Unit): Job {
        return if (recordingTimerDispatcher != null) {
            scope.launch(recordingTimerDispatcher, block = block)
        } else {
            scope.launch(block = block)
        }
    }

    // ── Document batch liveness watchdog ───────────────────────────

    private fun maybeStartDocumentBatchWatchdog(shot: ShotRequest) {
        if (shot.mediaType != MediaType.PHOTO) return
        if (state.value.activeMode != ModeId.DOCUMENT) return
        val start = elapsedRealtimeMillis()
        val deadline = PostProcessLivenessDeadline.forShot(
            shotId = shot.shotId,
            start = start,
            budgetMs = PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS
        )
        documentBatchLiveness = deadline
        documentBatchWatchdogJob?.cancel()
        documentBatchWatchdogJob = launchRecordingTimer {
            delay(deadline.budgetMillis)
            val stillActive = state.value.activeShot?.shotId == shot.shotId ||
                state.value.presentation.pendingPostprocess?.shotId == shot.shotId
            if (!stillActive) return@launchRecordingTimer
            forceReleaseDocumentBatchShot(
                shotId = shot.shotId,
                mediaType = MediaType.PHOTO,
                reason = "document-batch-liveness-deadline"
            )
        }
    }

    private fun cancelDocumentBatchWatchdog(shotId: String?) {
        if (shotId != null && documentBatchLiveness?.shotId != shotId) return
        documentBatchWatchdogJob?.cancel()
        documentBatchWatchdogJob = null
        documentBatchLiveness = null
    }

    private fun shouldForceReleaseDocumentBatchShot(shotId: String): Boolean {
        if (shotId in forceReleasedShotIds) return false
        val liveness = documentBatchLiveness ?: return false
        if (liveness.shotId != shotId) return false
        return liveness.isExpired(elapsedRealtimeMillis())
    }

    private suspend fun forceReleaseDocumentBatchShot(
        shotId: String,
        mediaType: MediaType,
        reason: String
    ) {
        if (shotId in forceReleasedShotIds) return
        forceReleasedShotIds.add(shotId)
        val liveness = documentBatchLiveness
        val nowMs = elapsedRealtimeMillis()
        val elapsedSinceShutterMs = liveness?.let { nowMs - it.startedAtElapsedMillis } ?: 0L
        val mode = state.value.activeMode
        val event = PostProcessLivenessEvent.ForceReleasedFromDocumentBatch(
            shotId = shotId,
            mediaType = mediaType,
            mode = mode,
            stage = PostProcessLivenessStage.DOCUMENT_BATCH,
            reason = reason,
            elapsedSinceShutterMs = elapsedSinceShutterMs.coerceAtLeast(0L),
            elapsedSincePostprocessStartMs = elapsedSinceShutterMs.coerceAtLeast(0L),
            itemId = shotId
        )
        trace.record("liveness.document.force-release", event.toDiagnosticString())
        updateState.update { s ->
            val currentBatch = s.presentation.documentBatch
            val existingItem = currentBatch.items.firstOrNull { it.shotId == shotId }
            val updatedItems = if (existingItem != null) {
                currentBatch.items.map { item ->
                    if (item.shotId == shotId) item.copy(
                        cropStatus = DocumentBatchCropStatus.NOT_REQUESTED,
                        pipelineNotes = item.pipelineNotes + "document:liveness:force-released"
                    ) else item
                }
            } else {
                val newItem = DocumentBatchItem(
                    itemId = shotId,
                    shotId = shotId,
                    orderIndex = currentBatch.items.size,
                    outputPath = null,
                    renderUri = null,
                    thumbnailSource = ThumbnailSource.Pending,
                    profileId = null,
                    scanMode = null,
                    cropStatus = DocumentBatchCropStatus.NOT_REQUESTED,
                    pipelineNotes = listOf("document:liveness:force-released")
                )
                currentBatch.items + newItem
            }
            s.copy(
                captureStatus = CaptureStatus.IDLE,
                recordingStatus = RecordingStatus.IDLE,
                activeShot = null,
                presentation = s.presentation.copy(
                    countdownRemainingSeconds = null,
                    pendingPostprocess = null,
                    captureReadiness = null,
                    documentBatch = currentBatch.copy(
                        items = updatedItems,
                        latestItemId = updatedItems.lastOrNull()?.itemId ?: currentBatch.latestItemId,
                        lastMessage = "Page processing timed out"
                    ),
                    lastAction = "Document page processing timed out",
                    lastError = reason
                )
            )
        }
        cancelDocumentBatchWatchdog(shotId)
    }

    private fun hydrateForceReleasedDocumentBatchItem(result: ShotResult) {
        if (result.mediaType != MediaType.PHOTO) return
        val cropStatus = documentCropStatusFrom(result.pipelineNotes)
        updateState.update { s ->
            val currentBatch = s.presentation.documentBatch
            if (currentBatch.status != DocumentBatchStatus.ACTIVE) return@update s
            val existingIndex = currentBatch.items.indexOfFirst { it.shotId == result.shotId }
            val hydratedItem = DocumentBatchItem(
                itemId = result.shotId,
                shotId = result.shotId,
                orderIndex = if (existingIndex >= 0) {
                    currentBatch.items[existingIndex].orderIndex
                } else {
                    currentBatch.items.size
                },
                outputPath = result.outputPath,
                renderUri = result.thumbnailSource.renderUriOrNull(),
                thumbnailSource = result.thumbnailSource,
                profileId = result.metadata.customTags["profile"],
                scanMode = result.metadata.customTags["scanMode"],
                cropStatus = cropStatus,
                pipelineNotes = result.pipelineNotes + "document:liveness:late-result-hydrated"
            )
            val updatedItems = if (existingIndex >= 0) {
                currentBatch.items.mapIndexed { index, item ->
                    if (index == existingIndex) hydratedItem else item
                }
            } else {
                currentBatch.items + hydratedItem
            }
            s.copy(
                presentation = s.presentation.copy(
                    previewThumbnailPath = result.thumbnailSource.outputPathOrNull()
                        ?: s.presentation.previewThumbnailPath,
                    latestThumbnailSource = result.thumbnailSource,
                    latestCapturePath = result.outputPath,
                    latestSavedMediaType = SavedMediaType.PHOTO,
                    latestPipelineNotes = result.pipelineNotes,
                    documentBatch = currentBatch.copy(
                        items = updatedItems,
                        latestItemId = hydratedItem.itemId,
                        lastMessage = "Page image ready"
                    ),
                    lastError = result.postProcessFailureSummary()
                )
            )
        }
    }

    internal fun documentBatchLivenessForTest(): PostProcessLivenessDeadline? = documentBatchLiveness
    internal fun forceReleasedShotIdsForTest(): Set<String> = forceReleasedShotIds.toSet()

    // ── Post-process liveness (non-document shots) ─────────────────

    /**
     * Builds the shot-time [ShotConfigSnapshot] from the current [ShotRequest] so the
     * post-process pipeline can read frozen UI configuration instead of live settings.
     *
     * The snapshot is stored alongside the [PostProcessLivenessDeadline] in
     * [PendingPostprocessUiState.livenessAttachment] for ordinary still captures.
     * Conservative kinds keep it in [postProcessLivenessConfigSnapshot] because their
     * [PendingPostprocessUiState] is not populated until [SessionIntent.ShotCompleted].
     */
    private fun buildShotConfigSnapshot(shot: ShotRequest): ShotConfigSnapshot {
        val recipe = RenderRecipe.from(shot)
        val isDocumentMode = state.value.activeMode == ModeId.DOCUMENT
        return ShotConfigSnapshot(
            watermarkTemplateId = recipe.watermarkTemplateId,
            frameRatio = recipe.frameRatio ?: FrameRatio.RATIO_4_3,
            colorRecipeId = recipe.filterProfileId,
            isDocumentMode = isDocumentMode
        )
    }

    private fun currentLivenessAttachment(): PendingPostprocessLivenessAttachment? {
        val snapshot = postProcessLivenessConfigSnapshot ?: return null
        return PendingPostprocessLivenessAttachment(
            configSnapshot = snapshot,
            liveness = postProcessLiveness
        )
    }

    /**
     * Arms the per-shot post-process liveness watchdog for non-document photo shots.
     * Document mode is owned by [maybeStartDocumentBatchWatchdog]; video is owned by
     * [startRecordingWatchdog]. This watchdog covers the post-process window for:
     *
     * - Re-armable still captures (ordinary still / live photo): protects the
     *   DATA_RECEIVED → ShotCompleted gap when [activeShot] has already been cleared
     *   by rearm but [PendingPostprocessUiState] is still set. On deadline, clears
     *   pendingPostprocess + captureReadiness and emits [PostProcessLivenessEvent.DeadlineExpired].
     * - Conservative kinds (multi-frame): protects the ShotStarted →
     *   ShotCompleted gap during which [activeShot] stays set. On deadline, emits a
     *   cooperative-cancel trace, waits [conservativeLivenessGraceMs], and if the shot
     *   is still active force-releases [activeShot] + pendingPostprocess and emits
     *   [PostProcessLivenessEvent.DeadlineExpired] with mode/shotKind fields.
     */
    private fun maybeArmPostProcessLiveness(shot: ShotRequest) {
        if (shot.mediaType != MediaType.PHOTO) return
        if (state.value.activeMode == ModeId.DOCUMENT) return
        val start = elapsedRealtimeMillis()
        val deadline = PostProcessLivenessDeadline.forShot(
            shotId = shot.shotId,
            start = start,
            budgetMs = PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS
        )
        postProcessLiveness = deadline
        postProcessLivenessShot = shot
        postProcessLivenessConfigSnapshot = buildShotConfigSnapshot(shot)
        postProcessLivenessJob?.cancel()
        val isConservative = !canRearmOnDataReceived(shot)
        val shotForWatchdog = shot
        postProcessLivenessJob = launchRecordingTimer {
            delay(deadline.budgetMillis)
            if (isConservative) {
                trace.record(
                    "liveness.session.cooperative-cancel",
                    "shotId=${shotForWatchdog.shotId},mode=${state.value.activeMode},shotKind=${shotForWatchdog.shotKind}"
                )
                delay(conservativeLivenessGraceMs)
                val stillActive = state.value.activeShot?.shotId == shotForWatchdog.shotId ||
                    state.value.presentation.pendingPostprocess?.shotId == shotForWatchdog.shotId
                if (!stillActive) return@launchRecordingTimer
                forceReleaseConservativeShot(shotForWatchdog, reason = "conservative-liveness-deadline")
            } else {
                val stillPending = state.value.presentation.pendingPostprocess?.shotId == shotForWatchdog.shotId
                if (!stillPending) return@launchRecordingTimer
                forceReleasePostProcessLiveness(shotForWatchdog, reason = "postprocess-liveness-deadline")
            }
        }
    }

    private fun cancelPostProcessLiveness(shotId: String?) {
        if (shotId != null && postProcessLiveness?.shotId != shotId) return
        postProcessLivenessJob?.cancel()
        postProcessLivenessJob = null
        postProcessLiveness = null
        postProcessLivenessShot = null
        postProcessLivenessConfigSnapshot = null
    }

    private suspend fun forceReleasePostProcessLiveness(
        shot: ShotRequest,
        reason: String
    ) {
        val liveness = postProcessLiveness
        val nowMs = elapsedRealtimeMillis()
        val elapsedSinceShutterMs = liveness?.let { nowMs - it.startedAtElapsedMillis } ?: 0L
        val mode = state.value.activeMode
        val event = PostProcessLivenessEvent.DeadlineExpired(
            shotId = shot.shotId,
            mediaType = shot.mediaType,
            mode = mode,
            stage = PostProcessLivenessStage.MEDIA_POST_PROCESS,
            reason = reason,
            elapsedSinceShutterMs = elapsedSinceShutterMs.coerceAtLeast(0L),
            elapsedSincePostprocessStartMs = elapsedSinceShutterMs.coerceAtLeast(0L),
            budgetMillis = liveness?.budgetMillis ?: PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS
        )
        trace.record("liveness.session.deadline-expired", event.toDiagnosticString())
        trace.record(
            "liveness.session.release",
            "shotId=${shot.shotId} stage=${event.stage.name} reason=$reason"
        )
        updateState.update { s ->
            s.copy(
                captureStatus = CaptureStatus.IDLE,
                presentation = s.presentation.copy(
                    pendingPostprocess = null,
                    captureReadiness = null,
                    lastAction = "Previous photo processing timed out",
                    lastError = reason
                )
            )
        }
        cancelPostProcessLiveness(shot.shotId)
    }

    private suspend fun forceReleaseConservativeShot(
        shot: ShotRequest,
        reason: String
    ) {
        if (shot.shotId in conservativeForceReleasedShotIds) return
        conservativeForceReleasedShotIds.add(shot.shotId)
        val liveness = postProcessLiveness
        val nowMs = elapsedRealtimeMillis()
        val elapsedSinceShutterMs = liveness?.let { nowMs - it.startedAtElapsedMillis } ?: 0L
        val mode = state.value.activeMode
        val budgetWithGrace = (liveness?.budgetMillis ?: PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS) +
            conservativeLivenessGraceMs
        val event = PostProcessLivenessEvent.DeadlineExpired(
            shotId = shot.shotId,
            mediaType = shot.mediaType,
            mode = mode,
            stage = PostProcessLivenessStage.MEDIA_POST_PROCESS,
            reason = reason,
            elapsedSinceShutterMs = elapsedSinceShutterMs.coerceAtLeast(0L),
            elapsedSincePostprocessStartMs = elapsedSinceShutterMs.coerceAtLeast(0L),
            budgetMillis = budgetWithGrace
        )
        trace.record("liveness.session.deadline-expired", event.toDiagnosticString())
        trace.record(
            "liveness.session.release",
            "shotId=${shot.shotId} stage=${event.stage.name} reason=$reason mode=${mode.name} shotKind=${shot.shotKind.name}"
        )
        recordingElapsedJob?.cancel()
        recordingElapsedJob = null
        updateState.update { s ->
            s.copy(
                captureStatus = CaptureStatus.IDLE,
                recordingStatus = RecordingStatus.IDLE,
                activeShot = null,
                presentation = s.presentation.copy(
                    countdownRemainingSeconds = null,
                    recordingStartedAtElapsedMillis = null,
                    recordingElapsedMillis = null,
                    pendingPostprocess = null,
                    captureReadiness = null,
                    lastAction = "Previous capture processing timed out",
                    lastError = reason
                )
            )
        }
        activeShotSpans.remove(shot.shotId)?.let { span ->
            linkRecorder.completeSpan(span, status = LinkEventStatus.FAILED, detail = reason)
        }
        cancelPostProcessLiveness(shot.shotId)
    }

    /**
     * Inspects [ShotResult.structuredPostProcessFailures] and emits the matching
     * [PostProcessLivenessEvent] variant per typed failure:
     *
     * - [PostProcessFailureCause.TIMEOUT] → [PostProcessLivenessEvent.DeadlineExpired]
     * - any other cause → [PostProcessLivenessEvent.PipelineFailed]
     *
     * Emitted only when [postProcessLiveness] is armed (i.e. the shot went through
     * [maybeArmPostProcessLiveness]). Document mode is skipped because its failures
     * are surfaced via [PostProcessLivenessEvent.ForceReleasedFromDocumentBatch].
     */
    private fun emitPostProcessLivenessEventsForCompleted(result: ShotResult) {
        val failures = result.structuredPostProcessFailures
        if (failures.isEmpty()) return
        val liveness = postProcessLiveness ?: return
        val nowMs = elapsedRealtimeMillis()
        val elapsedSinceShutterMs = (nowMs - liveness.startedAtElapsedMillis).coerceAtLeast(0L)
        val mode = state.value.activeMode
        val stage = PostProcessLivenessStage.MEDIA_POST_PROCESS
        for (failure in failures) {
            val reason = "${failure.stage.legacyNotePrefix}:${failure.cause.legacyNoteSuffix}"
            val event: PostProcessLivenessEvent = if (failure.cause == TIMEOUT) {
                PostProcessLivenessEvent.DeadlineExpired(
                    shotId = result.shotId,
                    mediaType = result.mediaType,
                    mode = mode,
                    stage = stage,
                    reason = reason,
                    elapsedSinceShutterMs = elapsedSinceShutterMs,
                    elapsedSincePostprocessStartMs = elapsedSinceShutterMs,
                    budgetMillis = liveness.budgetMillis
                )
            } else {
                PostProcessLivenessEvent.PipelineFailed(
                    shotId = result.shotId,
                    mediaType = result.mediaType,
                    mode = mode,
                    stage = stage,
                    reason = reason,
                    elapsedSinceShutterMs = elapsedSinceShutterMs,
                    elapsedSincePostprocessStartMs = elapsedSinceShutterMs
                )
            }
            trace.record(
                if (failure.cause == TIMEOUT) "liveness.session.deadline-expired"
                else "liveness.session.pipeline-failed",
                event.toDiagnosticString()
            )
            trace.record(
                "liveness.session.release",
                "shotId=${result.shotId} stage=${stage.name} reason=$reason"
            )
        }
    }

    internal fun postProcessLivenessForTest(): PostProcessLivenessDeadline? = postProcessLiveness
    internal fun conservativeForceReleasedShotIdsForTest(): Set<String> =
        conservativeForceReleasedShotIds.toSet()
    internal fun postProcessLivenessConfigSnapshotForTest(): ShotConfigSnapshot? =
        postProcessLivenessConfigSnapshot
}

@Suppress("UNUSED_PARAMETER")
internal fun documentCropStatusFrom(notes: List<String>): DocumentBatchCropStatus {
    return DocumentBatchCropStatus.NOT_REQUESTED
}
