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
import kotlinx.coroutines.launch

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
    private var pendingPreviewBind: PendingPreviewBind? = null

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
                    isRecovery = pending.isRecovery
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
            is SessionEffect.ApplyZoomRatio -> cameraAdapter.dispatch(
                DeviceCommand.UpdateZoomRatio(effect.zoomRatio)
            )
            is SessionEffect.BindPreview -> bindPreview(
                modeId = effect.modeId,
                deviceGraph = effect.deviceGraph,
                reason = effect.reason,
                isRecovery = effect.isRecovery
            )
            is SessionEffect.UnbindPreview -> unbindPreview(
                reason = effect.reason,
                clearHost = effect.clearHost
            )
            is SessionEffect.ApplyPreviewMetering -> cameraAdapter.dispatch(
                DeviceCommand.ApplyPreviewMetering(effect.request)
            )
            is SessionEffect.UpdateOutputRotation -> cameraAdapter.dispatch(
                DeviceCommand.UpdateOutputRotation(effect.rotation)
            )
            is SessionEffect.ApplyPreviewBrightness -> cameraAdapter.dispatch(
                DeviceCommand.ApplyPreviewBrightness(effect.request)
            )
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
        isRecovery: Boolean
    ) {
        if (attachedMode == modeId && cameraAdapter.boundGraph() == deviceGraph && !isRecovery) {
            return
        }
        val owner = lifecycleOwner
        val preview = previewView
        if (owner == null || preview == null) {
            pendingPreviewBind = PendingPreviewBind(modeId, deviceGraph, reason, isRecovery)
            return
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
            cameraAdapter.bindUseCases(owner, preview, deviceGraph)
        }.onSuccess {
            attachedMode = modeId
            sceneBrightnessSource?.onPreviewStarted()
        }.onFailure { throwable ->
            attachedMode = null
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
        lifecycleOwner = null
        previewView = null
    }

    private data class PendingPreviewBind(
        val modeId: ModeId,
        val deviceGraph: DeviceGraphSpec,
        val reason: String,
        val isRecovery: Boolean
    )
}
