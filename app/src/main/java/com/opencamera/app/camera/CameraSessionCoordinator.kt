package com.opencamera.app.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.opencamera.app.camera.device.CameraDeviceAdapter
import com.opencamera.core.device.DeviceCommand
import com.opencamera.core.device.DeviceEvent
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.CameraSession
import com.opencamera.core.session.SessionEffect
import com.opencamera.core.session.SessionIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * Mode switch state machine: STABLE → SWITCHING → STABLE.
 *
 * During SWITCHING, [isGeometryLocked] is true so the overlay preserves its
 * last geometry snapshot and the preview view transform stays consistent
 * until the rebind completes.
 */
enum class ModeSwitchState { STABLE, SWITCHING }

class CameraSessionCoordinator(
    private val session: CameraSession,
    private val cameraAdapter: CameraDeviceAdapter,
    private val scope: CoroutineScope,
    private val runtimeIssueMonitor: RuntimeIssueMonitor = NoOpRuntimeIssueMonitor,
    private val sceneBrightnessSource: SceneBrightnessSignalSource? = null
) {
    private var lifecycleOwner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var attachedMode: ModeId? = null
    private var attachedManualCaptureParams: com.opencamera.core.settings.ManualCaptureParams? = null
    private var pendingPreviewBind: PendingPreviewBind? = null
    private var previewMeteringJob: Job? = null

    private var modeSwitchState: ModeSwitchState = ModeSwitchState.STABLE

    /** True when a mode switch rebind is in-flight and overlay geometry must not recompute. */
    val isGeometryLocked: Boolean get() = modeSwitchState == ModeSwitchState.SWITCHING

    /** Current mode switch state, exposed for testing. */
    fun currentModeSwitchState(): ModeSwitchState = modeSwitchState

    init {
        scope.launch {
            session.effects.collect(::handleEffect)
        }
        scope.launch {
            cameraAdapter.events.collect(::handleDeviceEvent)
        }
        scope.launch {
            runtimeIssueMonitor.runtimeIssues.collect { issue ->
                runtimeIssueMonitor.onPreviewStopped(issue.reason)
                session.dispatch(SessionIntent.PreviewRuntimeIssue(issue))
            }
        }
        sceneBrightnessSource?.let { source ->
            scope.launch {
                source.signals.collect { signal ->
                    session.dispatch(SessionIntent.PhotoSceneSignalUpdated(signal))
                }
            }
        }
    }

    fun attachPreviewHost(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        this.lifecycleOwner = lifecycleOwner
        this.previewView = previewView
        runtimeIssueMonitor.onPreviewHostAttached()
        pendingPreviewBind?.let { pending ->
            pendingPreviewBind = null
            scope.launch {
                bindPreview(
                    modeId = pending.modeId,
                    deviceGraph = pending.deviceGraph,
                    reason = pending.reason,
                    isRecovery = pending.isRecovery,
                    manualCaptureParams = pending.manualCaptureParams
                )
            }
        }
    }

    fun hasAttachedPreviewHost(): Boolean {
        return lifecycleOwner != null && previewView != null
    }

    private suspend fun handleEffect(effect: SessionEffect) {
        when (effect) {
            is SessionEffect.ExecuteShot -> cameraAdapter.dispatch(DeviceCommand.ExecuteShot(effect.plan))
            is SessionEffect.StopActiveShot -> cameraAdapter.dispatch(
                DeviceCommand.StopActiveShot(effect.shotId)
            )
            is SessionEffect.ApplyZoomRatio -> {
                cancelPreviewMeteringJob("zoom changed")
                cameraAdapter.dispatch(
                    DeviceCommand.UpdateZoomRatio(effect.zoomRatio, effect.previewZoomRatio)
                )
            }
            is SessionEffect.SwitchLensNode -> {
                cancelPreviewMeteringJob("lens node changed")
                cameraAdapter.dispatch(DeviceCommand.SwitchLensNode(effect.lensNode, effect.reason))
            }
            is SessionEffect.BindPreview -> {
                cancelPreviewMeteringJob("preview rebound")
                bindPreview(
                    modeId = effect.modeId,
                    deviceGraph = effect.deviceGraph,
                    reason = effect.reason,
                    isRecovery = effect.isRecovery,
                    manualCaptureParams = effect.manualCaptureParams
                )
            }
            is SessionEffect.UnbindPreview -> {
                cancelPreviewMeteringJob("preview unbound")
                unbindPreview(reason = effect.reason, clearHost = effect.clearHost)
            }
            is SessionEffect.ApplyPreviewMetering -> {
                cancelPreviewMeteringJob("metering replaced")
                previewMeteringJob = scope.launch {
                    cameraAdapter.dispatch(DeviceCommand.ApplyPreviewMetering(effect.request))
                }
            }
            is SessionEffect.CancelPreviewMetering -> {
                cancelPreviewMeteringJob()
                cameraAdapter.dispatch(DeviceCommand.CancelPreviewMetering(effect.reason))
            }
            is SessionEffect.UpdateOutputRotation -> cameraAdapter.dispatch(
                DeviceCommand.UpdateOutputRotation(effect.rotation)
            )
            is SessionEffect.ApplyPreviewBrightness -> {
                cancelPreviewMeteringJob("preview brightness changed")
                cameraAdapter.dispatch(DeviceCommand.ApplyPreviewBrightness(effect.request))
            }
        }
    }

    private suspend fun cancelPreviewMeteringJob(cancelDeviceReason: String? = null) {
        val hadMeteringJob = previewMeteringJob != null
        previewMeteringJob?.cancelAndJoin()
        previewMeteringJob = null
        if (hadMeteringJob && cancelDeviceReason != null) {
            cameraAdapter.dispatch(DeviceCommand.CancelPreviewMetering(cancelDeviceReason))
        }
    }

    private suspend fun handleDeviceEvent(event: DeviceEvent) {
        when (event) {
            is DeviceEvent.PreviewFirstFrameAvailable -> {
                runtimeIssueMonitor.onPreviewFirstFrameAvailable(event.firstFrameLatencyMillis)
                session.dispatch(
                    SessionIntent.PreviewFirstFrameAvailable(event.firstFrameLatencyMillis)
                )
            }
            is DeviceEvent.PreviewSnapshotAvailable -> session.dispatch(
                SessionIntent.PreviewSnapshotUpdated(event.source, event.generation)
            )
            is DeviceEvent.CaptureFeedbackSnapshotAvailable -> session.dispatch(
                SessionIntent.CaptureFeedbackSnapshotUpdated(
                    shotId = event.shotId,
                    outputPath = event.outputPath
                )
            )
            is DeviceEvent.PreviewSurfaceLost -> {
                runtimeIssueMonitor.onPreviewStopped(event.reason)
                session.dispatch(SessionIntent.PreviewSurfaceLost(event.reason))
            }
            is DeviceEvent.PreviewError -> {
                runtimeIssueMonitor.onPreviewStopped(event.reason)
                session.dispatch(SessionIntent.PreviewError(event.reason))
            }
            is DeviceEvent.RuntimeIssue -> {
                runtimeIssueMonitor.onPreviewStopped(event.issue.reason)
                session.dispatch(SessionIntent.PreviewRuntimeIssue(event.issue))
            }
            is DeviceEvent.ShotStarted -> session.dispatch(
                SessionIntent.ShotStarted(event.shot)
            )
            is DeviceEvent.CaptureCommitted -> session.dispatch(
                SessionIntent.CaptureCommitted(
                    shotId = event.shotId,
                    mediaType = event.mediaType,
                    source = event.source,
                    elapsedTimestampMs = event.elapsedTimestampMs
                )
            )
            is DeviceEvent.DataReceived -> session.dispatch(
                SessionIntent.DataReceived(event.shotId, event.mediaType)
            )
            is DeviceEvent.ShotCompleted -> session.dispatch(
                SessionIntent.ShotCompleted(event.result)
            )
            is DeviceEvent.ShotFailed -> session.dispatch(
                SessionIntent.ShotFailed(
                    shotId = event.shotId,
                    mediaType = event.mediaType,
                    reason = event.reason
                )
            )
            is DeviceEvent.PreviewMeteringCompleted -> session.dispatch(
                SessionIntent.PreviewMeteringCompleted(event.result)
            )
            is DeviceEvent.PreviewBrightnessApplied -> session.dispatch(
                SessionIntent.PreviewBrightnessApplied(event.result)
            )
        }
    }

    private suspend fun bindPreview(
        modeId: ModeId,
        deviceGraph: DeviceGraphSpec,
        reason: String,
        isRecovery: Boolean,
        manualCaptureParams: com.opencamera.core.settings.ManualCaptureParams? = null
    ) {
        if (
            attachedMode == modeId &&
            cameraAdapter.boundGraph() == deviceGraph &&
            attachedManualCaptureParams == manualCaptureParams &&
            !isRecovery
        ) {
            return
        }
        val owner = lifecycleOwner
        val preview = previewView
        if (owner == null || preview == null) {
            pendingPreviewBind = PendingPreviewBind(
                modeId,
                deviceGraph,
                reason,
                isRecovery,
                manualCaptureParams
            )
            return
        }
        val isModeSwitch = attachedMode != null && attachedMode != modeId
        if (isModeSwitch) {
            modeSwitchState = ModeSwitchState.SWITCHING
        }
        syncActiveDeviceCapabilities(deviceGraph)
        session.dispatch(
            SessionIntent.PreviewBindingStarted(
                reason = reason,
                isRecovery = isRecovery
            )
        )
        runtimeIssueMonitor.onPreviewBindingStarted(
            reason = reason,
            isRecovery = isRecovery
        )
        runCatching {
            cameraAdapter.bindUseCases(owner, preview, deviceGraph, manualCaptureParams)
        }.onSuccess {
            attachedMode = modeId
            attachedManualCaptureParams = manualCaptureParams
            sceneBrightnessSource?.onPreviewStarted()
            if (isModeSwitch) {
                modeSwitchState = ModeSwitchState.STABLE
            }
        }.onFailure { throwable ->
            attachedMode = null
            attachedManualCaptureParams = null
            modeSwitchState = ModeSwitchState.STABLE
            runtimeIssueMonitor.onPreviewStopped(throwable.message ?: "bind failure")
            session.dispatch(
                SessionIntent.PreviewRuntimeIssue(
                    classifyPreviewBindingFailure(throwable)
                )
            )
        }
    }

    private suspend fun unbindPreview(
        reason: String,
        clearHost: Boolean
    ) {
        cameraAdapter.release()
        attachedMode = null
        attachedManualCaptureParams = null
        sceneBrightnessSource?.onPreviewStopped()
        runtimeIssueMonitor.onPreviewStopped(reason)
        if (clearHost) {
            pendingPreviewBind = null
            clearPreviewAttachment()
        }
        session.dispatch(SessionIntent.PreviewStopped(reason))
    }

    private suspend fun syncActiveDeviceCapabilities(deviceGraph: DeviceGraphSpec) {
        val resolvedCapabilities = cameraAdapter.capabilitiesFor(deviceGraph)
        if (resolvedCapabilities == session.state.value.activeDeviceCapabilities) {
            return
        }
        session.dispatch(SessionIntent.DeviceCapabilitiesUpdated(resolvedCapabilities))
    }

    private fun clearPreviewAttachment() {
        sceneBrightnessSource?.onPreviewHostDetached()
        runtimeIssueMonitor.onPreviewHostDetached()
        attachedMode = null
        attachedManualCaptureParams = null
        lifecycleOwner = null
        previewView = null
    }

    private data class PendingPreviewBind(
        val modeId: ModeId,
        val deviceGraph: DeviceGraphSpec,
        val reason: String,
        val isRecovery: Boolean,
        val manualCaptureParams: com.opencamera.core.settings.ManualCaptureParams?
    )
}
