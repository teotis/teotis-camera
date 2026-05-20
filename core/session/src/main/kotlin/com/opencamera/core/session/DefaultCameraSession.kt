package com.opencamera.core.session

import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.displayReason
import com.opencamera.core.device.nextZoomRatio
import com.opencamera.core.device.normalizedZoomRatioValue
import com.opencamera.core.device.resolvedZoomRatioSelection
import com.opencamera.core.device.recoveryReason
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotExecutor
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.media.outputPathOrNull
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeIntent
import com.opencamera.core.mode.ModeRuntimeState
import com.opencamera.core.mode.ModeRegistry
import com.opencamera.core.mode.ModeSessionEvent
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.settings.SessionSettingsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DefaultCameraSession(
    private val registry: ModeRegistry,
    private val trace: SessionTrace,
    private val baseDeviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val defaultMode: ModeId = ModeId.PHOTO,
    private val settingsSnapshot: SessionSettingsSnapshot = SessionSettingsSnapshot(),
    private val shotExecutor: ShotExecutor = ShotExecutor()
) : CameraSession {
    private val intentChannel = Channel<SessionIntent>(Channel.UNLIMITED)
    private val supportedModes = registry.supportedModes(baseDeviceCapabilities)
    private val initialMode = supportedModes.firstOrNull { it == defaultMode }
        ?: supportedModes.firstOrNull()
        ?: error("No supported camera modes for $baseDeviceCapabilities")
    private val initialLensFacing = defaultLensFacing(baseDeviceCapabilities.availableLensFacings)
    private val initialStillCaptureQuality = StillCaptureQualityPreference.LATENCY
    private val initialStillCaptureResolutionPreset = clampStillCaptureResolutionPreset(
        StillCaptureResolutionPreset.LARGE_12MP,
        baseDeviceCapabilities.availableStillCaptureResolutionPresets
    )
    private val initialStillCaptureOutputSize = resolvedStillCaptureOutputSizeSelection(
        current = null,
        available = baseDeviceCapabilities.availableStillCaptureOutputSizes,
        fallbackPreset = initialStillCaptureResolutionPreset
    )
    private var sessionDeviceCapabilities = baseDeviceCapabilities
    private var sessionLensFacing = initialLensFacing
    private var sessionStillCaptureQuality = initialStillCaptureQuality
    private var sessionStillCaptureResolutionPreset = initialStillCaptureResolutionPreset
    private var sessionSettingsSnapshot = settingsSnapshot
    private var pendingCountdownJob: Job? = null
    private var pendingCountdownStrategy: CaptureStrategy? = null
    private var pendingPreviewHostRecoveryReason: String? = null
    private var currentController: ModeController = createController(
        modeId = initialMode,
        deviceCapabilities = baseDeviceCapabilities,
        lensFacing = initialLensFacing,
        stillCaptureQuality = initialStillCaptureQuality,
        stillCaptureResolutionPreset = initialStillCaptureResolutionPreset
    )
    private val _effects = MutableSharedFlow<SessionEffect>(extraBufferCapacity = 8)

    private val _state = MutableStateFlow(
        SessionState(
            lifecycle = SessionLifecycle.CREATED,
            permissionState = PermissionState(),
            previewHostAvailable = false,
            previewStatus = PreviewStatus.IDLE,
            previewStatusDetail = null,
            activeMode = currentController.id,
            availableModes = supportedModes,
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = baseDeviceCapabilities,
            activeDeviceGraph = resolveActiveDeviceGraph(
                baseGraph = currentController.deviceGraph(),
                deviceCapabilities = baseDeviceCapabilities,
                requestedOutputSize = initialStillCaptureOutputSize,
                requestedZoomRatio = currentController.deviceGraph().preview.zoomRatio
            ),
            previewMetrics = PreviewMetrics(),
            settings = sessionSettingsSnapshot,
            presentation = SessionPresentationState(
                lastAction = "Session created"
            )
        )
    )

    override val state = _state.asStateFlow()
    override val effects = _effects.asSharedFlow()

    init {
        trace.record("session.created", "defaultMode=$defaultMode,initialMode=$initialMode")
        scope.launch {
            for (intent in intentChannel) {
                process(intent)
            }
        }
    }

    override suspend fun dispatch(intent: SessionIntent) {
        intentChannel.send(intent)
    }

    private suspend fun process(intent: SessionIntent) {
        trace.record("intent.received", intent.toString())
        when (intent) {
            SessionIntent.Boot -> handleBoot()
            SessionIntent.Shutdown -> handleShutdown()
            is SessionIntent.SettingsUpdated -> handleSettingsUpdated(intent.snapshot)
            is SessionIntent.SwitchMode -> handleSwitchMode(intent.modeId)
            SessionIntent.ShutterPressed -> handleModeIntent(ModeIntent.ShutterPressed)
            SessionIntent.SecondaryActionPressed -> handleModeIntent(ModeIntent.SecondaryActionPressed)
            SessionIntent.TertiaryActionPressed -> handleModeIntent(ModeIntent.TertiaryActionPressed)
            SessionIntent.ProActionPressed -> handleModeIntent(ModeIntent.ProActionPressed)
            is SessionIntent.CountdownTick -> handleCountdownTick(intent.remainingSeconds)
            SessionIntent.CountdownCompleted -> handleCountdownCompleted()
            SessionIntent.LensFacingToggled -> handleLensFacingToggled()
            SessionIntent.ZoomRatioToggled -> handleZoomRatioToggled()
            SessionIntent.StillCaptureQualityToggled -> handleStillCaptureQualityToggled()
            SessionIntent.StillCaptureResolutionToggled -> handleStillCaptureResolutionToggled()
            is SessionIntent.DeviceCapabilitiesUpdated -> handleDeviceCapabilitiesUpdated(
                intent.capabilities
            )
            is SessionIntent.PermissionsUpdated -> handlePermissionsUpdated(
                cameraGranted = intent.cameraGranted,
                microphoneGranted = intent.microphoneGranted
            )
            SessionIntent.PreviewHostAttached -> handlePreviewHostAttached()
            is SessionIntent.PreviewHostDetached -> handlePreviewHostDetached(intent.reason)
            is SessionIntent.PreviewBindingStarted -> handlePreviewBindingStarted(
                reason = intent.reason,
                isRecovery = intent.isRecovery
            )
            is SessionIntent.PreviewFirstFrameAvailable -> handlePreviewFirstFrameAvailable(
                intent.firstFrameLatencyMillis
            )
            is SessionIntent.PreviewSnapshotUpdated -> handlePreviewSnapshotUpdated(intent.source)
            is SessionIntent.PreviewSurfaceLost -> handlePreviewSurfaceLost(intent.reason)
            is SessionIntent.PreviewError -> handlePreviewError(intent.reason)
            is SessionIntent.PreviewRuntimeIssue -> handlePreviewRuntimeIssue(intent.issue)
            is SessionIntent.PreviewStopped -> handlePreviewStopped(intent.reason)
            is SessionIntent.ShotStarted -> handleShotStarted(intent.shot)
            is SessionIntent.ShotCompleted -> handleShotCompleted(intent.result)
            is SessionIntent.ShotFailed -> handleShotFailed(
                shotId = intent.shotId,
                mediaType = intent.mediaType,
                reason = intent.reason
            )
        }
    }

    private suspend fun handleBoot() {
        if (_state.value.lifecycle == SessionLifecycle.RUNNING) {
            updateState(lastAction = "Session already running")
            trace.record("session.boot.skipped", "already running")
            return
        }

        currentController.onEnter()
        val hasCameraPermission = _state.value.permissionState.cameraGranted
        updateState(
            lifecycle = SessionLifecycle.RUNNING,
            previewStatus = if (hasCameraPermission) PreviewStatus.IDLE else PreviewStatus.BLOCKED,
            previewStatusDetail = if (hasCameraPermission) null else "Camera permission missing",
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolvedActiveDeviceGraph(),
            lastAction = if (hasCameraPermission) {
                "Session booted"
            } else {
                "Session booted, waiting for camera permission"
            },
            lastError = if (hasCameraPermission) null else "Camera permission missing"
        )
        trace.record("session.booted", "mode=${currentController.id}")
        requestPreviewBinding(reason = "session boot", isRecovery = false)
    }

    private suspend fun handleShutdown() {
        if (_state.value.lifecycle == SessionLifecycle.STOPPED) {
            updateState(lastAction = "Session already stopped")
            trace.record("session.shutdown.skipped", "already stopped")
            return
        }

        cancelPendingCountdown("Countdown cancelled because session stopped")
        pendingPreviewHostRecoveryReason = null
        currentController.onExit()
        updateState(
            lifecycle = SessionLifecycle.STOPPED,
            previewStatus = PreviewStatus.IDLE,
            previewStatusDetail = null,
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolvedActiveDeviceGraph(),
            lastAction = "Session stopped"
        )
        trace.record("session.stopped", "mode=${currentController.id}")
        requestPreviewUnbind(reason = "Session stopped", clearHost = false)
    }

    private suspend fun handleSwitchMode(modeId: ModeId) {
        if (countdownInProgress()) {
            updateState(lastAction = "Wait for countdown to finish before switching modes")
            trace.record("mode.switch.blocked", "countdown=${_state.value.countdownRemainingSeconds}")
            return
        }
        val activeShot = _state.value.activeShot
        if (activeShot != null) {
            val reason = if (activeShot.mediaType == MediaType.VIDEO) {
                "Stop recording before switching modes"
            } else {
                "Wait for current capture to finish before switching modes"
            }
            updateState(lastAction = reason)
            trace.record(
                "mode.switch.blocked",
                "shot=${activeShot.shotId},mediaType=${activeShot.mediaType}"
            )
            return
        }

        if (modeId !in _state.value.availableModes) {
            updateState(lastAction = "Mode unavailable on this device: $modeId")
            trace.record("mode.switch.unsupported", modeId.name)
            return
        }

        if (modeId == _state.value.activeMode) {
            updateState(lastAction = "Mode already active: $modeId")
            trace.record("mode.switch.skipped", "already in $modeId")
            return
        }

        currentController.onExit()
        currentController = createController(
            modeId = modeId,
            deviceCapabilities = sessionDeviceCapabilities,
            lensFacing = sessionLensFacing,
            stillCaptureQuality = sessionStillCaptureQuality,
            stillCaptureResolutionPreset = sessionStillCaptureResolutionPreset
        )
        currentController.onEnter()

        updateState(
            activeMode = currentController.id,
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolvedActiveDeviceGraph(),
            lastAction = "Switched to ${currentController.snapshot.value.uiSpec.title}",
            lastError = null
        )
        trace.record("mode.switched", modeId.name)
        requestPreviewBinding(reason = "mode switched to ${modeId.name.lowercase()}")
    }

    private suspend fun handleSettingsUpdated(
        snapshot: SessionSettingsSnapshot
    ) {
        if (snapshot == sessionSettingsSnapshot) {
            updateState(lastAction = "Session settings already applied")
            trace.record("settings.update.skipped", "no-op")
            return
        }

        if (countdownInProgress()) {
            updateState(lastAction = "Wait for countdown to finish before updating settings")
            trace.record(
                "settings.update.blocked",
                "countdown=${_state.value.countdownRemainingSeconds}"
            )
            return
        }

        val activeShot = _state.value.activeShot
        if (activeShot != null) {
            updateState(lastAction = "Wait for current capture to finish before updating settings")
            trace.record(
                "settings.update.blocked",
                "shot=${activeShot.shotId},mediaType=${activeShot.mediaType}"
            )
            return
        }

        val wasRunning = _state.value.lifecycle == SessionLifecycle.RUNNING
        sessionSettingsSnapshot = snapshot
        if (wasRunning) {
            currentController.onEnter()
        }

        updateState(
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolvedActiveDeviceGraph(),
            settings = sessionSettingsSnapshot,
            lastAction = "Session settings updated",
            lastError = null
        )
        trace.record(
            "settings.updated",
            buildString {
                append("photoFilter=")
                append(snapshot.persisted.photo.defaultFilterProfileId)
                append(",videoSpec=")
                append(snapshot.persisted.video.defaultVideoSpec.summaryLabel)
            }
        )
        requestPreviewBinding(reason = "session settings updated")
    }

    private suspend fun handleLensFacingToggled() {
        if (countdownInProgress()) {
            updateState(lastAction = "Wait for countdown to finish before switching lenses")
            trace.record("lens.switch.blocked", "countdown=${_state.value.countdownRemainingSeconds}")
            return
        }
        val activeShot = _state.value.activeShot
        if (activeShot != null) {
            val reason = if (activeShot.mediaType == MediaType.VIDEO) {
                "Stop recording before switching lenses"
            } else {
                "Wait for current capture to finish before switching lenses"
            }
            updateState(lastAction = reason)
            trace.record(
                "lens.switch.blocked",
                "shot=${activeShot.shotId},mediaType=${activeShot.mediaType}"
            )
            return
        }

        val availableLensFacings = _state.value.activeDeviceCapabilities.availableLensFacings
        if (availableLensFacings.size < 2) {
            updateState(lastAction = "No alternate lens available on this device")
            trace.record("lens.switch.unavailable", availableLensFacings.joinToString())
            return
        }

        val nextLensFacing = nextLensFacing(
            current = sessionLensFacing,
            available = availableLensFacings
        )
        if (nextLensFacing == sessionLensFacing) {
            updateState(lastAction = "Lens already active: ${nextLensFacing.label}")
            trace.record("lens.switch.skipped", nextLensFacing.name)
            return
        }

        sessionLensFacing = nextLensFacing
        currentController.onLensFacingChanged(nextLensFacing)
        updateState(
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolvedActiveDeviceGraph(),
            lastAction = "Switched to ${nextLensFacing.label} lens",
            lastError = null
        )
        trace.record("lens.switched", nextLensFacing.name)
        requestPreviewBinding(reason = "lens switched to ${nextLensFacing.name.lowercase()}")
    }

    private suspend fun handleZoomRatioToggled() {
        if (countdownInProgress()) {
            updateState(lastAction = "Wait for countdown to finish before switching zoom")
            trace.record("zoom.switch.blocked", "countdown=${_state.value.countdownRemainingSeconds}")
            return
        }

        val activeShot = _state.value.activeShot
        if (activeShot != null && activeShot.mediaType == MediaType.PHOTO) {
            updateState(lastAction = "Wait for current capture to finish before switching zoom")
            trace.record(
                "zoom.switch.blocked",
                "shot=${activeShot.shotId},mediaType=${activeShot.mediaType}"
            )
            return
        }
        if (activeShot != null && _state.value.recordingStatus != RecordingStatus.RECORDING) {
            updateState(lastAction = "Wait for recording to start before switching zoom")
            trace.record(
                "zoom.switch.blocked",
                "shot=${activeShot.shotId},recording=pending"
            )
            return
        }

        val zoomCapability = _state.value.activeDeviceCapabilities.zoomRatioCapability
        if (!zoomCapability.isSwitchingSupported) {
            updateState(lastAction = "Zoom switching is unavailable on this device")
            trace.record(
                "zoom.switch.unavailable",
                zoomCapability.support.tagValue
            )
            return
        }

        val currentZoomRatio = _state.value.activeDeviceGraph.preview.zoomRatio
        val nextRatio = nextZoomRatio(
            current = currentZoomRatio,
            capability = zoomCapability
        )
        if (nextRatio == normalizedZoomRatioValue(currentZoomRatio)) {
            updateState(lastAction = "Zoom already active at ${zoomLabel(nextRatio)}")
            trace.record("zoom.switch.skipped", zoomLabel(nextRatio))
            return
        }

        updateState(
            activeDeviceGraph = resolveActiveDeviceGraph(
                baseGraph = currentController.deviceGraph(),
                deviceCapabilities = _state.value.activeDeviceCapabilities,
                requestedOutputSize = _state.value.activeDeviceGraph.stillCapture.outputSize,
                requestedZoomRatio = nextRatio
            ),
            lastAction = "Zoom set to ${zoomLabel(nextRatio)}",
            lastError = null
        )
        trace.record("zoom.updated", zoomLabel(nextRatio))
        requestZoomApply(nextRatio)
    }

    private suspend fun handleStillCaptureQualityToggled() {
        if (countdownInProgress()) {
            updateState(lastAction = "Wait for countdown to finish before changing still quality")
            trace.record(
                "still-quality.blocked",
                "countdown=${_state.value.countdownRemainingSeconds}"
            )
            return
        }
        val activeShot = _state.value.activeShot
        if (activeShot != null) {
            val reason = if (activeShot.mediaType == MediaType.VIDEO) {
                "Stop recording before changing still quality"
            } else {
                "Wait for current capture to finish before changing still quality"
            }
            updateState(lastAction = reason)
            trace.record(
                "still-quality.blocked",
                "shot=${activeShot.shotId},mediaType=${activeShot.mediaType}"
            )
            return
        }

        if (_state.value.activeDeviceGraph.template != CaptureTemplate.STILL_CAPTURE) {
            updateState(lastAction = "Still quality is only available in photo modes")
            trace.record("still-quality.unavailable", _state.value.activeMode.name)
            return
        }

        val nextQuality = nextStillCaptureQuality(
            sessionStillCaptureQuality
        )
        sessionStillCaptureQuality = nextQuality
        currentController.onStillCaptureQualityChanged(nextQuality)
        updateState(
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolvedActiveDeviceGraph(),
            lastAction = "Still quality set to ${nextQuality.label}",
            lastError = null
        )
        trace.record("still-quality.updated", nextQuality.tagValue)
        requestPreviewBinding(reason = "still quality updated to ${nextQuality.tagValue}")
    }

    private suspend fun handleStillCaptureResolutionToggled() {
        if (countdownInProgress()) {
            updateState(lastAction = "Wait for countdown to finish before changing still resolution")
            trace.record(
                "still-resolution.blocked",
                "countdown=${_state.value.countdownRemainingSeconds}"
            )
            return
        }
        val activeShot = _state.value.activeShot
        if (activeShot != null) {
            val reason = if (activeShot.mediaType == MediaType.VIDEO) {
                "Stop recording before changing still resolution"
            } else {
                "Wait for current capture to finish before changing still resolution"
            }
            updateState(lastAction = reason)
            trace.record(
                "still-resolution.blocked",
                "shot=${activeShot.shotId},mediaType=${activeShot.mediaType}"
            )
            return
        }

        if (_state.value.activeDeviceGraph.template != CaptureTemplate.STILL_CAPTURE) {
            updateState(lastAction = "Still resolution is only available in photo modes")
            trace.record("still-resolution.unavailable", _state.value.activeMode.name)
            return
        }

        val availableOutputSizes = _state.value.activeDeviceCapabilities.availableStillCaptureOutputSizes
        val currentOutputSize = resolvedStillCaptureOutputSizeSelection(
            current = _state.value.activeDeviceGraph.stillCapture.outputSize,
            available = availableOutputSizes,
            fallbackPreset = _state.value.activeDeviceGraph.stillCapture.resolutionPreset
        )
        val nextOutputSize = if (availableOutputSizes.size >= 2) {
            nextStillCaptureOutputSize(
                current = currentOutputSize,
                available = availableOutputSizes
            )
        } else {
            null
        }
        val nextPreset = if (nextOutputSize != null) {
            resolutionPresetForOutputSize(nextOutputSize)
        } else {
            val availablePresets = _state.value.activeDeviceCapabilities
                .availableStillCaptureResolutionPresets
            if (availablePresets.size < 2) {
                updateState(lastAction = "No alternate still resolution available on this lens")
                trace.record(
                    "still-resolution.single",
                    availablePresets.joinToString { it.tagValue }
                )
                return
            }
            nextStillCaptureResolutionPreset(
                current = _state.value.activeDeviceGraph.stillCapture.resolutionPreset,
                available = availablePresets
            )
        }
        sessionStillCaptureResolutionPreset = nextPreset
        currentController.onStillCaptureResolutionChanged(nextPreset)
        updateState(
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolveActiveDeviceGraph(
                baseGraph = currentController.deviceGraph(),
                deviceCapabilities = _state.value.activeDeviceCapabilities,
                requestedOutputSize = nextOutputSize
            ),
            lastAction = if (nextOutputSize != null) {
                "Still resolution set to ${nextOutputSize.width}x${nextOutputSize.height}"
            } else {
                "Still resolution set to ${nextPreset.label}"
            },
            lastError = null
        )
        trace.record(
            "still-resolution.updated",
            nextOutputSize?.let { "${it.width}x${it.height}:${nextPreset.tagValue}" }
                ?: nextPreset.tagValue
        )
        requestPreviewBinding(reason = "still resolution updated to ${nextPreset.tagValue}")
    }

    private suspend fun handleModeIntent(intent: ModeIntent) {
        if (_state.value.lifecycle != SessionLifecycle.RUNNING) {
            updateState(lastAction = "Ignored $intent because session is not running")
            trace.record("intent.ignored", "lifecycle=${_state.value.lifecycle}")
            return
        }

        if (countdownInProgress()) {
            updateState(lastAction = "Wait for countdown to finish before sending another command")
            trace.record("mode.intent.blocked", "countdown=${_state.value.countdownRemainingSeconds},intent=$intent")
            return
        }

        val activeShot = _state.value.activeShot
        if (activeShot != null) {
            when {
                activeShot.mediaType == MediaType.PHOTO -> {
                    updateState(lastAction = "Wait for current capture to finish before sending another command")
                    trace.record("mode.intent.blocked", "shot=${activeShot.shotId},intent=$intent")
                    return
                }

                _state.value.recordingStatus != RecordingStatus.RECORDING -> {
                    updateState(lastAction = "Recording request already in progress")
                    trace.record(
                        "mode.intent.blocked",
                        "shot=${activeShot.shotId},recording=pending,intent=$intent"
                    )
                    return
                }

                intent != ModeIntent.ShutterPressed -> {
                    updateState(
                        lastAction = when (intent) {
                            ModeIntent.SecondaryActionPressed ->
                                "Stop recording before changing torch"

                            ModeIntent.TertiaryActionPressed ->
                                "Stop recording before changing video quality"

                            ModeIntent.ProActionPressed ->
                                "Stop recording before changing Pro variant"

                            ModeIntent.ShutterPressed ->
                                _state.value.lastAction
                        }
                    )
                    trace.record(
                        "mode.intent.blocked",
                        "shot=${activeShot.shotId},recording=active,intent=$intent"
                    )
                    return
                }
            }
        }

        val signal = currentController.handle(intent)
        updateState(
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolvedActiveDeviceGraph()
        )

        when (signal) {
            ModeSignal.None -> {
                trace.record("mode.signal", "none")
            }

            is ModeSignal.SubmitCapture -> {
                if (signal.countdownSeconds > 0) {
                    startCaptureCountdown(signal.strategy, signal.countdownSeconds)
                } else {
                    submitCaptureStrategy(signal.strategy)
                }
            }

            ModeSignal.StopActiveCapture -> {
                val stoppableShot = runCatching {
                    shotExecutor.requireStoppableShot(_state.value.activeShot)
                }.getOrElse { throwable ->
                    updateState(
                        lastAction = throwable.message ?: "No active recording to stop",
                        lastError = throwable.message
                    )
                    trace.record("recording.stop.blocked", throwable.message ?: "unknown")
                    return
                }
                updateState(lastAction = "Stopping video recording")
                _effects.emit(SessionEffect.StopActiveShot(stoppableShot.shotId))
                trace.record("recording.stop.requested", "mode=${currentController.id}")
            }

            is ModeSignal.ShowHint -> {
                updateState(lastAction = signal.message)
                trace.record("mode.hint", signal.message)
            }
        }

        updateState(
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolvedActiveDeviceGraph()
        )
    }

    private suspend fun handleCountdownTick(remainingSeconds: Int) {
        if (!countdownInProgress()) {
            return
        }
        updateState(
            captureStatus = CaptureStatus.REQUESTED,
            countdownRemainingSeconds = remainingSeconds,
            lastAction = "Photo capture starts in ${remainingSeconds}s",
            lastError = null
        )
        trace.record("capture.countdown.tick", "${remainingSeconds}s")
    }

    private suspend fun handleCountdownCompleted() {
        val strategy = pendingCountdownStrategy ?: return
        pendingCountdownStrategy = null
        pendingCountdownJob = null
        updateState(
            countdownRemainingSeconds = null,
            lastAction = "Countdown finished",
            lastError = null
        )
        submitCaptureStrategy(strategy)
    }

    private suspend fun handleDeviceCapabilitiesUpdated(
        deviceCapabilities: DeviceCapabilities
    ) {
        if (deviceCapabilities == _state.value.activeDeviceCapabilities) {
            return
        }

        sessionDeviceCapabilities = deviceCapabilities
        currentController.onDeviceCapabilitiesChanged(deviceCapabilities)
        val clampedResolutionPreset = clampStillCaptureResolutionPreset(
            current = sessionStillCaptureResolutionPreset,
            available = deviceCapabilities.availableStillCaptureResolutionPresets
        )
        if (clampedResolutionPreset != sessionStillCaptureResolutionPreset) {
            sessionStillCaptureResolutionPreset = clampedResolutionPreset
            currentController.onStillCaptureResolutionChanged(clampedResolutionPreset)
        }
        updateState(
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = deviceCapabilities,
            activeDeviceGraph = resolveActiveDeviceGraph(
                baseGraph = currentController.deviceGraph(),
                deviceCapabilities = deviceCapabilities
            ),
            lastAction = if (
                clampedResolutionPreset != _state.value.activeDeviceGraph.stillCapture.resolutionPreset
            ) {
                "Still resolution adjusted to ${clampedResolutionPreset.label} for current lens"
            } else {
                "Device capabilities refreshed"
            },
            lastError = null
        )
        trace.record(
            "device.capabilities.updated",
            buildString {
                append("flash=")
                append(deviceCapabilities.supportsFlashControl)
                append(",audio=")
                append(deviceCapabilities.supportsAudioRecording)
                append(",previewSnapshots=")
                append(deviceCapabilities.supportsPreviewSnapshots)
            }
        )
        requestPreviewBinding(reason = "device capabilities refreshed")
    }

    private suspend fun handlePermissionsUpdated(
        cameraGranted: Boolean,
        microphoneGranted: Boolean
    ) {
        val previousState = _state.value
        val previous = _state.value.permissionState
        val newPermissionState = PermissionState(
            cameraGranted = cameraGranted,
            microphoneGranted = microphoneGranted
        )
        val previewStatus = when {
            !cameraGranted && _state.value.lifecycle == SessionLifecycle.RUNNING -> PreviewStatus.BLOCKED
            cameraGranted && _state.value.previewStatus == PreviewStatus.BLOCKED -> PreviewStatus.IDLE
            else -> _state.value.previewStatus
        }
        val captureStatus = if (cameraGranted) {
            _state.value.captureStatus
        } else {
            CaptureStatus.IDLE
        }
        val recordingStatus = if (cameraGranted) {
            _state.value.recordingStatus
        } else {
            RecordingStatus.IDLE
        }
        val lastAction = when {
            !cameraGranted -> "Camera permission required for preview"
            previous.cameraGranted != cameraGranted || previous.microphoneGranted != microphoneGranted ->
                "Permissions updated"
            else -> _state.value.lastAction
        }
        val lastError = when {
            !cameraGranted -> "Camera permission missing"
            previous.cameraGranted != cameraGranted -> null
            else -> _state.value.lastError
        }

        if (!cameraGranted && countdownInProgress()) {
            cancelPendingCountdown("Countdown cancelled because camera permission is missing")
        }

        if (!cameraGranted && previousState.activeShot != null) {
            val activeShot = previousState.activeShot
            handleInterruptedShotFailure(
                shot = activeShot,
                reason = "Camera permission missing"
            )
        }

        updateState(
            permissionState = newPermissionState,
            previewHostAvailable = _state.value.previewHostAvailable,
            previewStatus = previewStatus,
            previewStatusDetail = if (!cameraGranted) "Camera permission missing" else _state.value.previewStatusDetail,
            captureStatus = captureStatus,
            recordingStatus = recordingStatus,
            activeShot = if (cameraGranted) _state.value.activeShot else null,
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolvedActiveDeviceGraph(),
            lastAction = lastAction,
            latestPipelineNotes = if (cameraGranted) _state.value.latestPipelineNotes else emptyList(),
            lastError = lastError
        )
        trace.record(
            "permissions.updated",
            "camera=$cameraGranted,mic=$microphoneGranted"
        )
        when {
            !cameraGranted && previous.cameraGranted ->
                requestPreviewUnbind(reason = "Camera permission missing", clearHost = false)

            cameraGranted && !previous.cameraGranted -> {
                if (!requestPendingPreviewHostRecovery()) {
                    requestPreviewBinding(reason = "camera permission granted", isRecovery = false)
                }
            }
        }
    }

    private suspend fun handlePreviewHostAttached() {
        if (_state.value.previewHostAvailable) {
            trace.record("preview.host.attach.skipped", "already attached")
            requestPreviewBinding(reason = "preview host reattached", isRecovery = false)
            return
        }
        updateState(
            previewHostAvailable = true,
            lastAction = if (pendingPreviewHostRecoveryReason != null) {
                "Preview host reattached"
            } else {
                "Preview host attached"
            },
            lastError = null
        )
        trace.record("preview.host.attached", "lifecycle=${_state.value.lifecycle}")
        if (!requestPendingPreviewHostRecovery()) {
            requestPreviewBinding(reason = "preview host attached", isRecovery = false)
        }
    }

    private suspend fun handlePreviewHostDetached(reason: String) {
        if (countdownInProgress()) {
            cancelPendingCountdown("Countdown cancelled because preview host detached")
        }
        if (_state.value.lifecycle == SessionLifecycle.RUNNING) {
            pendingPreviewHostRecoveryReason = reason
        }
        updateState(
            previewHostAvailable = false,
            previewStatus = if (_state.value.permissionState.cameraGranted) {
                PreviewStatus.IDLE
            } else {
                PreviewStatus.BLOCKED
            },
            previewStatusDetail = reason,
            lastAction = "Preview host detached",
            lastError = if (_state.value.permissionState.cameraGranted) null else "Camera permission missing"
        )
        trace.record("preview.host.detached", reason)
        requestPreviewUnbind(reason = reason, clearHost = true)
    }

    private suspend fun requestPendingPreviewHostRecovery(): Boolean {
        val hostRecoveryReason = pendingPreviewHostRecoveryReason ?: return false
        val snapshot = _state.value
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

    private fun handlePreviewBindingStarted(
        reason: String,
        isRecovery: Boolean
    ) {
        if (!_state.value.permissionState.cameraGranted) {
            updateState(
                previewStatus = PreviewStatus.BLOCKED,
                previewStatusDetail = reason,
                lastAction = "Preview blocked until camera permission is granted",
                lastError = "Camera permission missing"
            )
            trace.record("preview.blocked", reason)
            return
        }

        val metrics = _state.value.previewMetrics
        updateState(
            previewStatus = if (isRecovery) PreviewStatus.RECOVERING else PreviewStatus.STARTING,
            previewStatusDetail = reason,
            previewMetrics = metrics.copy(
                bindCount = metrics.bindCount + 1,
                recoveryCount = metrics.recoveryCount + if (isRecovery) 1 else 0,
                lastStartReason = reason
            ),
            lastAction = if (isRecovery) {
                "Recovering preview"
            } else {
                "Starting preview"
            },
            lastError = null
        )
        trace.record(
            if (isRecovery) "preview.recovery.started" else "preview.binding.started",
            reason
        )
    }

    private fun handlePreviewFirstFrameAvailable(firstFrameLatencyMillis: Long) {
        val metrics = _state.value.previewMetrics
        val bestLatency = listOfNotNull(
            metrics.bestFirstFrameLatencyMillis,
            firstFrameLatencyMillis
        ).minOrNull()
        val worstLatency = listOfNotNull(
            metrics.worstFirstFrameLatencyMillis,
            firstFrameLatencyMillis
        ).maxOrNull()
        updateState(
            previewStatus = PreviewStatus.ACTIVE,
            previewMetrics = metrics.copy(
                lastFirstFrameLatencyMillis = firstFrameLatencyMillis,
                bestFirstFrameLatencyMillis = bestLatency,
                worstFirstFrameLatencyMillis = worstLatency
            ),
            lastAction = "Preview active (${firstFrameLatencyMillis} ms first frame)",
            lastError = null
        )
        trace.record("preview.first.frame", "${firstFrameLatencyMillis}ms")
    }

    private fun handlePreviewSnapshotUpdated(source: ThumbnailSource) {
        updateState(
            previewThumbnailPath = source.outputPathOrNull(),
            latestThumbnailSource = source
        )
        trace.record("preview.snapshot.updated", source.outputPathOrNull().orEmpty())
    }

    private suspend fun handlePreviewSurfaceLost(reason: String) {
        if (countdownInProgress()) {
            cancelPendingCountdown("Countdown cancelled because preview surface was lost")
        }
        if (_state.value.recordingStatus == RecordingStatus.RECORDING) {
            handlePreviewError("Preview surface lost during recording: $reason")
            return
        }
        updateState(
            previewStatus = PreviewStatus.SURFACE_LOST,
            previewStatusDetail = reason,
            lastAction = "Preview surface lost",
            lastError = reason
        )
        trace.record("preview.surface.lost", reason)
        requestPreviewBinding(reason = "recover after $reason", isRecovery = true)
    }

    private suspend fun handlePreviewError(reason: String) {
        if (countdownInProgress()) {
            cancelPendingCountdown("Countdown cancelled because preview failed")
        }
        val shouldAttemptRecovery = shouldAttemptPreviewErrorRecovery()
        updateState(
            previewStatus = PreviewStatus.ERROR,
            previewStatusDetail = reason,
            lastAction = if (shouldAttemptRecovery) {
                "Preview error, attempting recovery"
            } else {
                "Preview error"
            },
            lastError = reason
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
        val recoveryWasActive = _state.value.previewStatus == PreviewStatus.RECOVERING
        val shouldAttemptRecovery = issue.isRecoverable &&
            !recoveryWasActive &&
            shouldAttemptPreviewErrorRecovery()
        updateState(
            previewStatus = PreviewStatus.ERROR,
            previewStatusDetail = renderedReason,
            lastAction = when {
                recoveryWasActive && issue.isRecoverable -> "Preview recovery failed"
                recoveryWasActive -> "Preview recovery failed, manual intervention required"
                shouldAttemptRecovery -> "Preview runtime issue, attempting recovery"
                issue.isRecoverable -> "Preview runtime issue"
                else -> "Preview runtime issue, manual intervention required"
            },
            lastError = renderedReason
        )
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
        val hasCameraPermission = _state.value.permissionState.cameraGranted
        updateState(
            previewStatus = if (hasCameraPermission) PreviewStatus.IDLE else PreviewStatus.BLOCKED,
            previewStatusDetail = reason,
            lastAction = "Preview stopped",
            lastError = if (hasCameraPermission) null else "Camera permission missing"
        )
        trace.record("preview.stopped", reason)
    }

    private suspend fun handleShotStarted(shot: ShotRequest) {
        currentController.onSessionEvent(ModeSessionEvent.ShotStarted(shot))
        updateState(
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
            countdownRemainingSeconds = null,
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolvedActiveDeviceGraph(),
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
        trace.record(
            if (shot.mediaType == MediaType.PHOTO) "capture.saving" else "recording.started",
            "shot=${shot.shotId},mode=${currentController.id}"
        )
    }

    private suspend fun handleShotCompleted(result: ShotResult) {
        currentController.onSessionEvent(ModeSessionEvent.ShotCompleted(result))
        updateState(
            captureStatus = if (result.mediaType == MediaType.PHOTO) {
                CaptureStatus.COMPLETED
            } else {
                CaptureStatus.IDLE
            },
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            countdownRemainingSeconds = null,
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolvedActiveDeviceGraph(),
            previewThumbnailPath = result.thumbnailSource.outputPathOrNull()
                ?: _state.value.previewThumbnailPath,
            latestThumbnailSource = when (result.thumbnailSource) {
                ThumbnailSource.None -> _state.value.latestThumbnailSource
                else -> result.thumbnailSource
            },
            lastAction = if (result.mediaType == MediaType.PHOTO) {
                if (result.livePhotoBundle != null) {
                    "Live photo saved"
                } else {
                    "Photo saved"
                }
            } else {
                "Video saved"
            },
            latestCapturePath = if (result.mediaType == MediaType.PHOTO) {
                result.outputPath
            } else {
                _state.value.latestCapturePath
            },
            latestVideoPath = if (result.mediaType == MediaType.VIDEO) {
                result.outputPath
            } else {
                _state.value.latestVideoPath
            },
            latestLivePhotoBundle = latestLivePhotoBundleFor(result),
            latestSavedMediaType = if (result.mediaType == MediaType.PHOTO) {
                SavedMediaType.PHOTO
            } else {
                SavedMediaType.VIDEO
            },
            latestPipelineNotes = result.pipelineNotes,
            lastError = null
        )
        trace.record(
            if (result.mediaType == MediaType.PHOTO) "capture.saved" else "recording.saved",
            result.outputPath
        )
    }

    private fun latestLivePhotoBundleFor(result: ShotResult): LivePhotoBundle? {
        return if (result.mediaType == MediaType.PHOTO) {
            result.livePhotoBundle
        } else {
            _state.value.latestLivePhotoBundle
        }
    }

    private suspend fun handleShotFailed(
        shotId: String,
        mediaType: MediaType,
        reason: String
    ) {
        currentController.onSessionEvent(
            ModeSessionEvent.ShotFailed(
                shotId = shotId,
                mediaType = mediaType,
                reason = reason
            )
        )
        updateState(
            captureStatus = if (mediaType == MediaType.PHOTO) {
                CaptureStatus.FAILED
            } else {
                CaptureStatus.IDLE
            },
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            countdownRemainingSeconds = null,
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolvedActiveDeviceGraph(),
            lastAction = if (mediaType == MediaType.PHOTO) {
                "Photo capture failed"
            } else {
                "Video recording failed"
            },
            latestPipelineNotes = emptyList(),
            lastError = reason
        )
        trace.record(
            if (mediaType == MediaType.PHOTO) "capture.failed" else "recording.failed",
            "$shotId:$reason"
        )
    }

    private suspend fun handleInterruptedShotFailure(
        shot: ShotRequest,
        reason: String
    ) {
        currentController.onSessionEvent(
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

    private fun countdownInProgress(): Boolean = pendingCountdownStrategy != null

    private fun cancelPendingCountdown(reason: String) {
        pendingCountdownJob?.cancel()
        pendingCountdownJob = null
        pendingCountdownStrategy = null
        updateState(
            captureStatus = CaptureStatus.IDLE,
            countdownRemainingSeconds = null,
            lastAction = reason,
            lastError = null
        )
        trace.record("capture.countdown.cancelled", reason)
    }

    private fun startCaptureCountdown(
        strategy: CaptureStrategy,
        countdownSeconds: Int
    ) {
        pendingCountdownJob?.cancel()
        pendingCountdownStrategy = strategy
        updateState(
            captureStatus = CaptureStatus.REQUESTED,
            countdownRemainingSeconds = countdownSeconds,
            lastAction = "Photo capture starts in ${countdownSeconds}s",
            lastError = null
        )
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

    private suspend fun submitCaptureStrategy(strategy: CaptureStrategy) {
        val plan = runCatching {
            shotExecutor.plan(
                strategy = strategy,
                activeShot = _state.value.activeShot
            )
        }.map { createdPlan ->
            enrichPlanWithStillOutputSize(createdPlan)
        }.getOrElse { throwable ->
            updateState(
                captureStatus = CaptureStatus.IDLE,
                countdownRemainingSeconds = null,
                lastAction = throwable.message ?: "Failed to create shot plan",
                lastError = throwable.message
            )
            trace.record("shot.plan.failed", throwable.message ?: "unknown")
            return
        }
        updateState(
            captureStatus = if (plan.request.mediaType == MediaType.PHOTO) {
                CaptureStatus.REQUESTED
            } else {
                CaptureStatus.IDLE
            },
            recordingStatus = RecordingStatus.IDLE,
            activeShot = plan.request,
            countdownRemainingSeconds = null,
            lastAction = if (plan.request.mediaType == MediaType.PHOTO) {
                "Photo capture requested"
            } else {
                "Video recording requested"
            },
            lastError = null
        )
        _effects.emit(SessionEffect.ExecuteShot(plan))
        trace.record(
            if (plan.request.mediaType == MediaType.PHOTO) {
                "capture.photo"
            } else {
                "recording.requested"
            },
            "mode=${currentController.id},shot=${plan.request.shotId}"
        )
    }

    private fun updateState(
        lifecycle: SessionLifecycle = _state.value.lifecycle,
        permissionState: PermissionState = _state.value.permissionState,
        previewHostAvailable: Boolean = _state.value.previewHostAvailable,
        previewStatus: PreviewStatus = _state.value.previewStatus,
        previewStatusDetail: String? = _state.value.previewStatusDetail,
        activeMode: ModeId = _state.value.activeMode,
        captureStatus: CaptureStatus = _state.value.captureStatus,
        recordingStatus: RecordingStatus = _state.value.recordingStatus,
        activeShot: ShotRequest? = _state.value.activeShot,
        modeSnapshot: com.opencamera.core.mode.ModeSnapshot = _state.value.modeSnapshot,
        activeDeviceCapabilities: DeviceCapabilities = _state.value.activeDeviceCapabilities,
        activeDeviceGraph: DeviceGraphSpec = _state.value.activeDeviceGraph,
        previewMetrics: PreviewMetrics = _state.value.previewMetrics,
        settings: SessionSettingsSnapshot = _state.value.settings,
        countdownRemainingSeconds: Int? = _state.value.presentation.countdownRemainingSeconds,
        previewThumbnailPath: String? = _state.value.presentation.previewThumbnailPath,
        latestThumbnailSource: ThumbnailSource? = _state.value.presentation.latestThumbnailSource,
        lastAction: String = _state.value.presentation.lastAction,
        latestCapturePath: String? = _state.value.presentation.latestCapturePath,
        latestVideoPath: String? = _state.value.presentation.latestVideoPath,
        latestLivePhotoBundle: LivePhotoBundle? = _state.value.presentation.latestLivePhotoBundle,
        latestSavedMediaType: SavedMediaType? = _state.value.presentation.latestSavedMediaType,
        latestPipelineNotes: List<String> = _state.value.presentation.latestPipelineNotes,
        lastError: String? = _state.value.presentation.lastError
    ) {
        _state.value = _state.value.copy(
            lifecycle = lifecycle,
            permissionState = permissionState,
            previewHostAvailable = previewHostAvailable,
            previewStatus = previewStatus,
            previewStatusDetail = previewStatusDetail,
            activeMode = activeMode,
            captureStatus = captureStatus,
            recordingStatus = recordingStatus,
            activeShot = activeShot,
            modeSnapshot = modeSnapshot,
            activeDeviceCapabilities = activeDeviceCapabilities,
            activeDeviceGraph = activeDeviceGraph,
            previewMetrics = previewMetrics,
            settings = settings,
            presentation = _state.value.presentation.copy(
                countdownRemainingSeconds = countdownRemainingSeconds,
                previewThumbnailPath = previewThumbnailPath,
                latestThumbnailSource = latestThumbnailSource,
                lastAction = lastAction,
                latestCapturePath = latestCapturePath,
                latestVideoPath = latestVideoPath,
                latestLivePhotoBundle = latestLivePhotoBundle,
                latestSavedMediaType = latestSavedMediaType,
                latestPipelineNotes = latestPipelineNotes,
                lastError = lastError
            )
        )
    }

    private suspend fun requestPreviewBinding(
        reason: String,
        isRecovery: Boolean = false
    ) {
        val snapshot = _state.value
        if (
            snapshot.lifecycle != SessionLifecycle.RUNNING ||
            !snapshot.permissionState.cameraGranted ||
            !snapshot.previewHostAvailable ||
            snapshot.recordingStatus == RecordingStatus.RECORDING
        ) {
            return
        }
        _effects.emit(
            SessionEffect.BindPreview(
                modeId = snapshot.activeMode,
                deviceGraph = snapshot.activeDeviceGraph,
                reason = reason,
                isRecovery = isRecovery
            )
        )
    }

    private suspend fun requestZoomApply(zoomRatio: Float) {
        _effects.emit(
            SessionEffect.ApplyZoomRatio(
                zoomRatio = normalizedZoomRatioValue(zoomRatio)
            )
        )
    }

    private fun shouldAttemptPreviewErrorRecovery(): Boolean {
        val snapshot = _state.value
        return snapshot.lifecycle == SessionLifecycle.RUNNING &&
            snapshot.permissionState.cameraGranted &&
            snapshot.previewHostAvailable &&
            snapshot.recordingStatus != RecordingStatus.RECORDING &&
            snapshot.activeShot == null
    }

    private suspend fun requestPreviewUnbind(
        reason: String,
        clearHost: Boolean
    ) {
        _effects.emit(
            SessionEffect.UnbindPreview(
                reason = reason,
                clearHost = clearHost
            )
        )
    }

    private fun resolvedActiveDeviceGraph(
        baseGraph: DeviceGraphSpec = currentController.deviceGraph(),
        deviceCapabilities: DeviceCapabilities = _state.value.activeDeviceCapabilities
    ): DeviceGraphSpec {
        return resolveActiveDeviceGraph(
            baseGraph = baseGraph,
            deviceCapabilities = deviceCapabilities,
            requestedOutputSize = _state.value.activeDeviceGraph.stillCapture.outputSize,
            requestedZoomRatio = _state.value.activeDeviceGraph.preview.zoomRatio
        )
    }

    private fun resolveActiveDeviceGraph(
        baseGraph: DeviceGraphSpec = currentController.deviceGraph(),
        deviceCapabilities: DeviceCapabilities = _state.value.activeDeviceCapabilities,
        requestedOutputSize: StillCaptureOutputSize? = baseGraph.stillCapture.outputSize,
        requestedZoomRatio: Float? = baseGraph.preview.zoomRatio
    ): DeviceGraphSpec {
        val resolvedOutputSize = resolvedStillCaptureOutputSizeSelection(
            current = requestedOutputSize,
            available = deviceCapabilities.availableStillCaptureOutputSizes,
            fallbackPreset = baseGraph.stillCapture.resolutionPreset
        )
        val resolvedZoomRatio = resolvedZoomRatioSelection(
            current = requestedZoomRatio,
            capability = deviceCapabilities.zoomRatioCapability
        )
        val resolvedPreset = resolvedOutputSize
            ?.let(::resolutionPresetForOutputSize)
            ?: clampStillCaptureResolutionPreset(
                current = baseGraph.stillCapture.resolutionPreset,
                available = deviceCapabilities.availableStillCaptureResolutionPresets
            )
        return baseGraph.copy(
            preview = baseGraph.preview.copy(
                zoomRatio = resolvedZoomRatio
            ),
            stillCapture = baseGraph.stillCapture.copy(
                resolutionPreset = resolvedPreset,
                outputSize = resolvedOutputSize
            )
        )
    }

    private fun zoomLabel(zoomRatio: Float): String = "${normalizedZoomRatioValue(zoomRatio)}x"

    private fun enrichPlanWithStillOutputSize(plan: com.opencamera.core.media.ShotPlan): com.opencamera.core.media.ShotPlan {
        if (plan.request.mediaType != MediaType.PHOTO) {
            return plan
        }

        val outputSize = _state.value.activeDeviceGraph.stillCapture.outputSize ?: return plan
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

    private fun createController(
        modeId: ModeId,
        deviceCapabilities: DeviceCapabilities,
        lensFacing: LensFacing,
        stillCaptureQuality: StillCaptureQualityPreference,
        stillCaptureResolutionPreset: StillCaptureResolutionPreset
    ): ModeController {
        val clampedStillCaptureResolutionPreset = clampStillCaptureResolutionPreset(
            current = stillCaptureResolutionPreset,
            available = deviceCapabilities.availableStillCaptureResolutionPresets
        )
        sessionDeviceCapabilities = deviceCapabilities
        sessionLensFacing = lensFacing
        sessionStillCaptureQuality = stillCaptureQuality
        sessionStillCaptureResolutionPreset = clampedStillCaptureResolutionPreset
        return registry.createController(
            modeId = modeId,
            context = ModeContext(
                deviceCapabilities = deviceCapabilities,
                initialLensFacing = lensFacing,
                initialStillCaptureQuality = stillCaptureQuality,
                initialStillCaptureResolutionPreset = clampedStillCaptureResolutionPreset,
                runtimeState = {
                    ModeRuntimeState(
                        deviceCapabilities = sessionDeviceCapabilities,
                        lensFacing = sessionLensFacing,
                        stillCaptureQuality = sessionStillCaptureQuality,
                        stillCaptureResolutionPreset = sessionStillCaptureResolutionPreset
                    )
                },
                eventSink = { detail ->
                    trace.record("mode.event", detail)
                },
                settingsSnapshotProvider = { sessionSettingsSnapshot }
            )
        )
    }

    private fun defaultLensFacing(availableLensFacings: Set<LensFacing>): LensFacing {
        return when {
            LensFacing.BACK in availableLensFacings -> LensFacing.BACK
            LensFacing.FRONT in availableLensFacings -> LensFacing.FRONT
            else -> LensFacing.BACK
        }
    }

    private fun nextLensFacing(
        current: LensFacing,
        available: Set<LensFacing>
    ): LensFacing {
        val ordered = available
            .sortedBy { it.ordinal }
            .ifEmpty { listOf(current) }
        val currentIndex = ordered.indexOf(current)
        if (currentIndex == -1) {
            return ordered.first()
        }
        return ordered[(currentIndex + 1) % ordered.size]
    }

    private fun nextStillCaptureQuality(
        current: StillCaptureQualityPreference
    ): StillCaptureQualityPreference {
        return when (current) {
            StillCaptureQualityPreference.LATENCY -> StillCaptureQualityPreference.QUALITY
            StillCaptureQualityPreference.QUALITY -> StillCaptureQualityPreference.LATENCY
        }
    }

    private fun clampStillCaptureResolutionPreset(
        current: StillCaptureResolutionPreset,
        available: Set<StillCaptureResolutionPreset>
    ): StillCaptureResolutionPreset {
        val ordered = listOf(
            StillCaptureResolutionPreset.LARGE_12MP,
            StillCaptureResolutionPreset.MEDIUM_8MP,
            StillCaptureResolutionPreset.SMALL_2MP
        )
        if (current in available) {
            return current
        }
        val currentIndex = ordered.indexOf(current)
        if (currentIndex != -1) {
            for (index in currentIndex + 1..ordered.lastIndex) {
                val candidate = ordered[index]
                if (candidate in available) {
                    return candidate
                }
            }
        }
        return ordered.firstOrNull { it in available } ?: ordered.last()
    }

    private fun resolvedStillCaptureOutputSizeSelection(
        current: StillCaptureOutputSize?,
        available: List<StillCaptureOutputSize>,
        fallbackPreset: StillCaptureResolutionPreset
    ): StillCaptureOutputSize? {
        if (available.isEmpty()) {
            return null
        }
        if (current != null && current in available) {
            return current
        }
        return resolveOutputSizeForPreset(fallbackPreset, available)
    }

    private fun nextStillCaptureOutputSize(
        current: StillCaptureOutputSize?,
        available: List<StillCaptureOutputSize>
    ): StillCaptureOutputSize {
        val ordered = available
            .sortedByDescending { it.pixelCount }
            .ifEmpty { error("No still capture output sizes available") }
        val currentIndex = current?.let(ordered::indexOf) ?: -1
        if (currentIndex == -1) {
            return ordered.first()
        }
        return ordered[(currentIndex + 1) % ordered.size]
    }

    private fun resolveOutputSizeForPreset(
        preset: StillCaptureResolutionPreset,
        available: List<StillCaptureOutputSize>
    ): StillCaptureOutputSize {
        val desiredPixels = preset.targetWidth.toLong() * preset.targetHeight.toLong()
        val sortedByPixels = available.sortedBy { it.pixelCount }
        return when (preset) {
            StillCaptureResolutionPreset.LARGE_12MP -> sortedByPixels
                .firstOrNull { it.pixelCount >= desiredPixels }
                ?: sortedByPixels.last()

            StillCaptureResolutionPreset.MEDIUM_8MP,
            StillCaptureResolutionPreset.SMALL_2MP -> sortedByPixels
                .lastOrNull { it.pixelCount <= desiredPixels }
                ?: sortedByPixels.first()
        }
    }

    private fun resolutionPresetForOutputSize(
        outputSize: StillCaptureOutputSize
    ): StillCaptureResolutionPreset {
        val outputPixels = outputSize.pixelCount
        return StillCaptureResolutionPreset.entries.minByOrNull { preset ->
            kotlin.math.abs(outputPixels - preset.targetWidth.toLong() * preset.targetHeight.toLong())
        } ?: StillCaptureResolutionPreset.LARGE_12MP
    }

    private fun nextStillCaptureResolutionPreset(
        current: StillCaptureResolutionPreset,
        available: Set<StillCaptureResolutionPreset>
    ): StillCaptureResolutionPreset {
        val ordered = listOf(
            StillCaptureResolutionPreset.LARGE_12MP,
            StillCaptureResolutionPreset.MEDIUM_8MP,
            StillCaptureResolutionPreset.SMALL_2MP
        ).filter { it in available }
            .ifEmpty {
                listOf(
                    clampStillCaptureResolutionPreset(
                        current = current,
                        available = available
                    )
                )
            }
        val currentIndex = ordered.indexOf(clampStillCaptureResolutionPreset(current, available))
        if (currentIndex == -1) {
            return ordered.first()
        }
        return ordered[(currentIndex + 1) % ordered.size]
    }

    private val LensFacing.label: String
        get() = when (this) {
            LensFacing.BACK -> "back"
            LensFacing.FRONT -> "front"
        }

}
