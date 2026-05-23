package com.opencamera.core.session

import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.PreviewMeteringPoint
import com.opencamera.core.device.PreviewMeteringRequest
import com.opencamera.core.device.PreviewMeteringResult
import com.opencamera.core.device.PreviewMeteringResultStatus
import com.opencamera.core.device.displayReason
import com.opencamera.core.device.photoLowLightStrategySupport
import com.opencamera.core.device.recoveryReason
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.media.outputPathOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Processes all preview lifecycle and recovery intents.
 *
 * Extracted from [DefaultCameraSession] to isolate preview host
 * attach/detach, binding lifecycle, snapshot updates, metering,
 * and error-recovery logic in a single focused processor.
 */
internal class PreviewRecoverySessionProcessor(
    private val state: MutableStateFlow<SessionState>,
    private val effects: MutableSharedFlow<SessionEffect>,
    private val trace: SessionTrace,
    private val mutations: PreviewSessionMutations,
    private val countdownInProgress: () -> Boolean,
    private val cancelPendingCountdown: (String) -> Unit
) {
    private var pendingPreviewHostRecoveryReason: String? = null
    private var meteringCounter: Int = 0

    suspend fun process(intent: SessionIntent) {
        when (intent) {
            SessionIntent.PreviewHostAttached -> handlePreviewHostAttached()
            is SessionIntent.PreviewHostDetached -> handlePreviewHostDetached(intent.reason)
            is SessionIntent.PreviewBindingStarted -> handlePreviewBindingStarted(intent.reason, intent.isRecovery)
            is SessionIntent.PreviewFirstFrameAvailable -> handlePreviewFirstFrameAvailable(intent.firstFrameLatencyMillis)
            is SessionIntent.PreviewSnapshotUpdated -> handlePreviewSnapshotUpdated(intent.source, intent.generation)
            is SessionIntent.LatestGalleryImageLoaded -> handleLatestGalleryImageLoaded(intent.source)
            is SessionIntent.LatestGalleryMediaLoaded -> handleLatestGalleryMediaLoaded(intent.source, intent.mediaType)
            is SessionIntent.CaptureFeedbackSnapshotUpdated -> handleCaptureFeedbackSnapshotUpdated(intent.shotId, intent.outputPath)
            is SessionIntent.PreviewSurfaceLost -> handlePreviewSurfaceLost(intent.reason)
            is SessionIntent.PreviewError -> handlePreviewError(intent.reason)
            is SessionIntent.PreviewRuntimeIssue -> handlePreviewRuntimeIssue(intent.issue)
            is SessionIntent.PreviewStopped -> handlePreviewStopped(intent.reason)
            is SessionIntent.PreviewTapToFocus -> handlePreviewTapToFocus(intent.normalizedX, intent.normalizedY)
            is SessionIntent.PreviewMeteringCompleted -> handlePreviewMeteringCompleted(intent.result)
            is SessionIntent.PhotoSceneSignalUpdated -> handlePhotoSceneSignalUpdated(intent.signal)
            SessionIntent.PhotoLowLightPromptExpired -> handlePhotoLowLightPromptExpired()
            else -> error("Unexpected preview intent: $intent")
        }
    }

    // -- Preview host attach / detach --

    private suspend fun handlePreviewHostAttached() {
        if (state.value.previewHostAvailable) {
            trace.record("preview.host.attach.skipped", "already attached")
            requestPreviewBinding(reason = "preview host reattached", isRecovery = false)
            return
        }
        val lastAction = if (pendingPreviewHostRecoveryReason != null) {
            "Preview host reattached"
        } else {
            "Preview host attached"
        }
        mutations.updatePreviewHostAttached(lastAction)
        trace.record("preview.host.attached", "lifecycle=${state.value.lifecycle}")
        if (!requestPendingPreviewHostRecovery()) {
            requestPreviewBinding(reason = "preview host attached", isRecovery = false)
        }
    }

    private suspend fun handlePreviewHostDetached(reason: String) {
        if (countdownInProgress()) {
            cancelPendingCountdown("Countdown cancelled because preview host detached")
        }
        if (state.value.lifecycle == SessionLifecycle.RUNNING) {
            pendingPreviewHostRecoveryReason = reason
        }
        mutations.updatePreviewHostDetached(reason, state.value.permissionState.cameraGranted)
        trace.record("preview.host.detached", reason)
        requestPreviewUnbind(reason = reason, clearHost = true)
    }

    suspend fun requestPendingPreviewHostRecovery(): Boolean {
        val hostRecoveryReason = pendingPreviewHostRecoveryReason ?: return false
        val snapshot = state.value
        if (
            snapshot.lifecycle != SessionLifecycle.RUNNING ||
            !snapshot.previewHostAvailable ||
            !snapshot.permissionState.cameraGranted ||
            snapshot.recordingStatus == RecordingStatus.RECORDING
        ) {
            return false
        }
        val recoveryReason = "recover after preview host detached: $hostRecoveryReason"
        pendingPreviewHostRecoveryReason = null
        trace.record("preview.host.recovery.requested", recoveryReason)
        requestPreviewBinding(reason = recoveryReason, isRecovery = true)
        return true
    }

    // -- Preview binding lifecycle --

    private suspend fun handlePreviewBindingStarted(
        reason: String,
        isRecovery: Boolean
    ) {
        if (!state.value.permissionState.cameraGranted) {
            mutations.updatePreviewBlocked(reason)
            trace.record("preview.blocked", reason)
            return
        }

        val metrics = state.value.previewMetrics
        mutations.updatePreviewStarting(reason, isRecovery)
        mutations.updatePreviewMetrics(
            metrics.copy(
                bindCount = metrics.bindCount + 1,
                recoveryCount = metrics.recoveryCount + if (isRecovery) 1 else 0,
                lastStartReason = reason
            )
        )
        trace.record(
            if (isRecovery) "preview.recovery.started" else "preview.binding.started",
            reason
        )
    }

    private fun handlePreviewFirstFrameAvailable(firstFrameLatencyMillis: Long) {
        val metrics = state.value.previewMetrics
        val bestLatency = listOfNotNull(
            metrics.bestFirstFrameLatencyMillis,
            firstFrameLatencyMillis
        ).minOrNull()
        val worstLatency = listOfNotNull(
            metrics.worstFirstFrameLatencyMillis,
            firstFrameLatencyMillis
        ).maxOrNull()
        mutations.updatePreviewActive(firstFrameLatencyMillis)
        mutations.updatePreviewMetrics(
            metrics.copy(
                lastFirstFrameLatencyMillis = firstFrameLatencyMillis,
                bestFirstFrameLatencyMillis = bestLatency,
                worstFirstFrameLatencyMillis = worstLatency
            )
        )
        trace.record("preview.first.frame", "${firstFrameLatencyMillis}ms")
    }

    // -- Snapshot and capture feedback --

    private fun handlePreviewSnapshotUpdated(source: ThumbnailSource, generation: Int) {
        val currentGen = state.value.presentation.previewSnapshotGeneration
        if (generation < currentGen) {
            trace.record("preview.snapshot.stale", "gen=$generation,current=$currentGen")
            return
        }
        val hasSavedMediaThumbnail = state.value.presentation.latestThumbnailSource is ThumbnailSource.SavedMedia
        if (hasSavedMediaThumbnail) {
            trace.record("preview.snapshot.ignored", source.outputPathOrNull().orEmpty())
            return
        }
        mutations.updatePreviewThumbnail(source, generation)
        trace.record("preview.snapshot.updated", source.outputPathOrNull().orEmpty())
    }

    private fun handleLatestGalleryImageLoaded(source: ThumbnailSource.SavedMedia) {
        val currentSource = state.value.presentation.latestThumbnailSource
        if (currentSource is ThumbnailSource.SavedMedia) {
            trace.record("latest.gallery.ignored", "already has saved media")
            return
        }
        mutations.updatePreviewThumbnail(source, generation = 0)
        trace.record("latest.gallery.loaded", source.outputPathOrNull().orEmpty())
    }

    private fun handleLatestGalleryMediaLoaded(source: ThumbnailSource.SavedMedia, mediaType: SavedMediaType) {
        val currentSource = state.value.presentation.latestThumbnailSource
        if (currentSource is ThumbnailSource.SavedMedia) {
            trace.record("latest.gallery.media.ignored", "already has saved media")
            return
        }
        mutations.updatePreviewThumbnail(source, generation = 0)
        state.value = state.value.copy(
            presentation = state.value.presentation.copy(
                latestSavedMediaType = mediaType
            )
        )
        trace.record("latest.gallery.media.loaded", "path=${source.outputPathOrNull()},type=$mediaType")
    }

    private fun handleCaptureFeedbackSnapshotUpdated(shotId: String, outputPath: String) {
        val activeShot = state.value.activeShot
        if (activeShot == null || activeShot.shotId != shotId) {
            trace.record("capture.feedback.snapshot.skipped", "shotId=$shotId,active=${activeShot?.shotId}")
            return
        }
        if (captureFeedbackPolicyFor(activeShot) == CaptureFeedbackPolicy.SUPPRESS_UNTIL_SAVED_MEDIA) {
            trace.record("capture.feedback.snapshot.suppressed", "shotId=$shotId,reason=final-output-postprocess")
            return
        }
        mutations.updateCaptureFeedback(shotId, outputPath)
        trace.record("capture.feedback.snapshot.updated", "shotId=$shotId")
    }

    // -- Preview surface / error / runtime issue --

    private suspend fun handlePreviewSurfaceLost(reason: String) {
        if (countdownInProgress()) {
            cancelPendingCountdown("Countdown cancelled because preview surface was lost")
        }
        if (state.value.recordingStatus == RecordingStatus.RECORDING) {
            handlePreviewError("Preview surface lost during recording: $reason")
            return
        }
        mutations.updatePreviewSurfaceLost(reason)
        trace.record("preview.surface.lost", reason)
        requestPreviewBinding(reason = "recover after $reason", isRecovery = true)
    }

    private suspend fun handlePreviewError(reason: String) {
        if (countdownInProgress()) {
            cancelPendingCountdown("Countdown cancelled because preview failed")
        }
        val shouldAttemptRecovery = shouldAttemptPreviewErrorRecovery()
        mutations.updatePreviewError(
            reason = reason,
            action = if (shouldAttemptRecovery) {
                "Preview error, attempting recovery"
            } else {
                "Preview error"
            }
        )
        trace.record("preview.error", reason)
        if (shouldAttemptRecovery) {
            val recoveryReason = "recover after preview error: $reason"
            trace.record("preview.recovery.requested", recoveryReason)
            requestPreviewBinding(reason = recoveryReason, isRecovery = true)
        }
    }

    private suspend fun handlePreviewRuntimeIssue(issue: DeviceRuntimeIssue) {
        if (countdownInProgress()) {
            cancelPendingCountdown("Countdown cancelled because preview failed")
        }
        val renderedReason = issue.displayReason()
        val recoveryWasActive = state.value.previewStatus == PreviewStatus.RECOVERING
        val shouldAttemptRecovery = issue.isRecoverable &&
            !recoveryWasActive &&
            shouldAttemptPreviewErrorRecovery()
        val action = when {
            recoveryWasActive && issue.isRecoverable -> "Preview recovery failed"
            recoveryWasActive -> "Preview recovery failed, manual intervention required"
            shouldAttemptRecovery -> "Preview runtime issue, attempting recovery"
            issue.isRecoverable -> "Preview runtime issue"
            else -> "Preview runtime issue, manual intervention required"
        }
        mutations.updatePreviewRuntimeError(renderedReason, action)
        trace.record(
            "preview.runtime.issue",
            "kind=${issue.kind.name},recoverable=${issue.isRecoverable},reason=${issue.reason}"
        )
        if (recoveryWasActive) {
            trace.record("preview.recovery.failed", renderedReason)
        }
        trace.record("preview.error", renderedReason)
        if (shouldAttemptRecovery) {
            val recoveryReason = issue.recoveryReason()
            trace.record("preview.recovery.requested", recoveryReason)
            requestPreviewBinding(reason = recoveryReason, isRecovery = true)
        }
    }

    private fun handlePreviewStopped(reason: String) {
        if (countdownInProgress()) {
            cancelPendingCountdown("Countdown cancelled because preview stopped")
        }
        mutations.updatePreviewStopped(reason)
        trace.record("preview.stopped", reason)
    }

    // -- Tap-to-focus / metering --

    private suspend fun handlePreviewTapToFocus(normalizedX: Float, normalizedY: Float) {
        val point = PreviewMeteringPoint(normalizedX, normalizedY).clamped()
        val snapshot = state.value
        if (snapshot.previewStatus != PreviewStatus.ACTIVE) {
            trace.record(
                "preview.metering.ignored",
                "reason=preview-not-active,status=${snapshot.previewStatus}"
            )
            return
        }
        if (!snapshot.permissionState.cameraGranted || !snapshot.previewHostAvailable) {
            trace.record(
                "preview.metering.ignored",
                "reason=permission-or-host-missing,granted=${snapshot.permissionState.cameraGranted},host=${snapshot.previewHostAvailable}"
            )
            return
        }
        meteringCounter++
        val requestId = "meter-$meteringCounter"
        val request = PreviewMeteringRequest(
            requestId = requestId,
            point = point
        )
        mutations.updatePreviewMeteringRequested(requestId, point)
        effects.emit(SessionEffect.ApplyPreviewMetering(request))
        trace.record(
            "preview.metering.requested",
            "requestId=$requestId,x=${"%.2f".format(point.normalizedX)},y=${"%.2f".format(point.normalizedY)},mode=focus+ae"
        )
    }

    private fun handlePreviewMeteringCompleted(result: PreviewMeteringResult) {
        val currentFeedback = state.value.presentation.previewMeteringFeedback
        if (currentFeedback == null || currentFeedback.requestId != result.requestId) {
            trace.record("preview.metering.stale", "resultId=${result.requestId},currentId=${currentFeedback?.requestId}")
            return
        }
        mutations.updatePreviewMeteringCompleted(result)
        val traceLabel = when (result.status) {
            PreviewMeteringResultStatus.SUCCEEDED -> "preview.metering.succeeded"
            PreviewMeteringResultStatus.DEGRADED_AUTO_EXPOSURE_ONLY -> "preview.metering.degraded"
            PreviewMeteringResultStatus.FAILED -> "preview.metering.failed"
            PreviewMeteringResultStatus.UNSUPPORTED -> "preview.metering.unsupported"
        }
        trace.record(traceLabel, "requestId=${result.requestId}")
    }

    // -- Low-light scene signal --

    private fun handlePhotoSceneSignalUpdated(signal: com.opencamera.core.device.PhotoSceneSignal) {
        val snapshot = state.value
        state.value = snapshot.copy(
            presentation = snapshot.presentation.copy(
                photoSceneSignal = signal
            )
        )

        if (snapshot.activeMode != com.opencamera.core.mode.ModeId.PHOTO) {
            trace.record("photo.low-light.ignored", "mode=${snapshot.activeMode}")
            return
        }
        if (snapshot.previewStatus != PreviewStatus.ACTIVE) {
            trace.record("photo.low-light.ignored", "preview=${snapshot.previewStatus}")
            return
        }

        val support = snapshot.activeDeviceCapabilities.photoLowLightStrategySupport()
        val settingEnabled = snapshot.settings.persisted.photo.lowLightNightAssistEnabled

        if (signal.lightState == com.opencamera.core.device.SceneLightState.LOW_LIGHT) {
            trace.record(
                "photo.low-light.detected",
                "state=LOW_LIGHT,score=${signal.brightnessScore},support=$support,setting=${if (settingEnabled) "enabled" else "disabled"}"
            )
            val status = resolveLowLightPromptStatus(settingEnabled, support)
            val message = when (status) {
                PhotoLowLightPromptStatus.AVAILABLE_ENABLED -> "夜间辅助已开启"
                PhotoLowLightPromptStatus.AVAILABLE_DISABLED -> "夜间辅助已关闭"
                PhotoLowLightPromptStatus.DEGRADED_ENABLED -> "夜间辅助已开启（降级）"
                PhotoLowLightPromptStatus.DEGRADED_DISABLED -> "夜间辅助已关闭（降级）"
                PhotoLowLightPromptStatus.UNSUPPORTED -> "设备不支持夜间辅助"
                PhotoLowLightPromptStatus.HIDDEN -> ""
            }
            val visibleUntil = System.currentTimeMillis() + 3000L
            state.value = state.value.copy(
                presentation = state.value.presentation.copy(
                    photoLowLightPrompt = PhotoLowLightPrompt(
                        status = status,
                        visibleUntilElapsedMillis = visibleUntil,
                        brightnessScore = signal.brightnessScore,
                        message = message
                    )
                )
            )
            trace.record(
                "photo.low-light.prompt.visible",
                "untilElapsedMillis=$visibleUntil"
            )
        }
    }

    private fun handlePhotoLowLightPromptExpired() {
        val prompt = state.value.presentation.photoLowLightPrompt ?: return
        val now = System.currentTimeMillis()
        val visibleUntil = prompt.visibleUntilElapsedMillis
        if (visibleUntil != null && now >= visibleUntil) {
            state.value = state.value.copy(
                presentation = state.value.presentation.copy(
                    photoLowLightPrompt = prompt.copy(
                        status = PhotoLowLightPromptStatus.HIDDEN,
                        visibleUntilElapsedMillis = null
                    )
                )
            )
            trace.record("photo.low-light.prompt.hidden", "expired")
        }
    }

    private fun resolveLowLightPromptStatus(
        settingEnabled: Boolean,
        support: com.opencamera.core.device.PhotoLowLightStrategySupport
    ): PhotoLowLightPromptStatus = when (support) {
        com.opencamera.core.device.PhotoLowLightStrategySupport.SUPPORTED_MULTI_FRAME ->
            if (settingEnabled) PhotoLowLightPromptStatus.AVAILABLE_ENABLED
            else PhotoLowLightPromptStatus.AVAILABLE_DISABLED
        com.opencamera.core.device.PhotoLowLightStrategySupport.DEGRADED_SINGLE_FRAME ->
            if (settingEnabled) PhotoLowLightPromptStatus.DEGRADED_ENABLED
            else PhotoLowLightPromptStatus.DEGRADED_DISABLED
        com.opencamera.core.device.PhotoLowLightStrategySupport.UNSUPPORTED ->
            PhotoLowLightPromptStatus.UNSUPPORTED
    }

    // -- Helpers (also called from DefaultCameraSession lifecycle handlers) --

    suspend fun requestPreviewBinding(
        reason: String,
        isRecovery: Boolean = false
    ) {
        val snapshot = state.value
        if (
            snapshot.lifecycle != SessionLifecycle.RUNNING ||
            !snapshot.permissionState.cameraGranted ||
            !snapshot.previewHostAvailable ||
            snapshot.recordingStatus == RecordingStatus.RECORDING
        ) {
            return
        }
        effects.emit(
            SessionEffect.BindPreview(
                modeId = snapshot.activeMode,
                deviceGraph = snapshot.activeDeviceGraph,
                reason = reason,
                isRecovery = isRecovery
            )
        )
    }

    suspend fun requestPreviewUnbind(
        reason: String,
        clearHost: Boolean
    ) {
        effects.emit(
            SessionEffect.UnbindPreview(
                reason = reason,
                clearHost = clearHost
            )
        )
    }

    private fun shouldAttemptPreviewErrorRecovery(): Boolean {
        val snapshot = state.value
        return snapshot.lifecycle == SessionLifecycle.RUNNING &&
            snapshot.permissionState.cameraGranted &&
            snapshot.previewHostAvailable &&
            snapshot.recordingStatus != RecordingStatus.RECORDING &&
            snapshot.activeShot == null
    }
}
