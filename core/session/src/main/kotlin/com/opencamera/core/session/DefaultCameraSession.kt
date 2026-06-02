package com.opencamera.core.session

import com.opencamera.core.device.CameraOutputRotation
import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.LensNode
import com.opencamera.core.device.LensNodeAvailability
import com.opencamera.core.device.PreviewBrightnessRequest
import com.opencamera.core.device.PreviewBrightnessResult
import com.opencamera.core.device.PreviewBrightnessResultStatus
import com.opencamera.core.device.PreviewMeteringPoint
import com.opencamera.core.device.PreviewMeteringResult
import com.opencamera.core.device.PreviewMeteringResultStatus
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.nextZoomRatio
import com.opencamera.core.device.normalizedZoomRatioValue
import com.opencamera.core.device.resolvedZoomRatioSelection
import com.opencamera.core.media.CaptureFeedbackPreview
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotExecutor
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.media.outputPathOrNull
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeIntent
import com.opencamera.core.mode.ModeRuntimeState
import com.opencamera.core.mode.ModeRegistry
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.settings.SessionSettingsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import com.opencamera.core.media.StillCaptureQualityPreference
import kotlinx.coroutines.launch

class DefaultCameraSession(
    private val registry: ModeRegistry,
    private val trace: SessionTrace,
    linkRecorder: PerformanceLinkRecorder? = null,
    private val baseDeviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val defaultMode: ModeId = ModeId.PHOTO,
    private val settingsSnapshot: SessionSettingsSnapshot = SessionSettingsSnapshot(),
    private val shotExecutor: ShotExecutor = ShotExecutor(),
    private val effectCapabilityResolver: com.opencamera.core.effect.EffectCapabilityResolver? = null,
    private val capabilityGraphResolver: com.opencamera.core.capability.CapabilityGraphResolver? = null,
    private val capabilityRequirements: () -> List<com.opencamera.core.capability.CapabilityRequirement> = { emptyList() }
) : CameraSession {
    private val linkRecorder: PerformanceLinkRecorder = linkRecorder ?: InMemoryPerformanceLinkRecorder()
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
    private var sessionStillCaptureOutputSize = initialStillCaptureOutputSize
    private var sessionPreviewRatio: PreviewRatio = PreviewRatio.FULL
    private var sessionSettingsSnapshot = settingsSnapshot
    private var pendingSwitchTraceHandle: TraceHandle? = null
    private var pendingSwitchSpan: PerformanceSpanSnapshot? = null
    private var currentController: ModeController = createController(
        modeId = initialMode,
        deviceCapabilities = baseDeviceCapabilities,
        lensFacing = initialLensFacing,
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

    private val captureRecordingProcessor = CaptureRecordingSessionProcessor(
        scope = scope,
        state = _state,
        effects = _effects,
        trace = trace,
        linkRecorder = this.linkRecorder,
        shotExecutor = shotExecutor,
        currentController = { currentController },
        resolvedActiveDeviceGraph = { resolvedActiveDeviceGraph() },
        updateState = SessionStateUpdater { transform ->
            _state.value = transform(_state.value)
        },
        dispatch = { intent -> dispatch(intent) }
    )

    private val previewSessionMutations = object : PreviewSessionMutations {
        override fun updatePreviewBlocked(reason: String) {
            updateState(
                previewStatus = PreviewStatus.BLOCKED,
                previewStatusDetail = reason,
                lastAction = "Preview blocked until camera permission is granted",
                lastError = "Camera permission missing"
            )
        }

        override fun updatePreviewStarting(reason: String, isRecovery: Boolean) {
            updateState(
                previewStatus = if (isRecovery) PreviewStatus.RECOVERING else PreviewStatus.STARTING,
                previewStatusDetail = reason,
                lastAction = if (isRecovery) "Recovering preview" else "Starting preview",
                lastError = null
            )
        }

        override fun updatePreviewActive(firstFrameLatencyMillis: Long) {
            updateState(
                previewStatus = PreviewStatus.ACTIVE,
                lastAction = "Preview active (${firstFrameLatencyMillis} ms first frame)",
                lastError = null
            )
        }

        override fun updatePreviewError(reason: String, action: String) {
            updateState(
                previewStatus = PreviewStatus.ERROR,
                previewStatusDetail = reason,
                lastAction = action,
                lastError = reason
            )
        }

        override fun updatePreviewStopped(reason: String) {
            val hasCameraPermission = _state.value.permissionState.cameraGranted
            updateState(
                previewStatus = if (hasCameraPermission) PreviewStatus.IDLE else PreviewStatus.BLOCKED,
                previewStatusDetail = reason,
                lastAction = "Preview stopped",
                lastError = if (hasCameraPermission) null else "Camera permission missing"
            )
        }

        override fun updatePreviewThumbnail(source: ThumbnailSource, generation: Int) {
            updateState(
                previewThumbnailPath = source.outputPathOrNull(),
                latestThumbnailSource = source,
                previewSnapshotGeneration = generation
            )
        }

        override fun updateCaptureFeedback(shotId: String, outputPath: String) {
            updateState(
                pendingCaptureFeedback = CaptureFeedbackPreview(
                    shotId = shotId,
                    outputPath = outputPath
                ),
                lastAction = "Capture feedback ready"
            )
        }

        override fun updatePreviewMeteringRequested(requestId: String, point: PreviewMeteringPoint) {
            updateState(
                previewMeteringFeedback = PreviewMeteringFeedback(
                    requestId = requestId,
                    normalizedX = point.normalizedX,
                    normalizedY = point.normalizedY,
                    status = PreviewMeteringFeedbackStatus.REQUESTED
                )
            )
        }

        override fun updatePreviewMeteringCompleted(result: PreviewMeteringResult) {
            val currentFeedback = _state.value.presentation.previewMeteringFeedback ?: return
            val feedbackStatus = when (result.status) {
                PreviewMeteringResultStatus.SUCCEEDED -> PreviewMeteringFeedbackStatus.SUCCEEDED
                PreviewMeteringResultStatus.DEGRADED_AUTO_EXPOSURE_ONLY -> PreviewMeteringFeedbackStatus.DEGRADED_AUTO_EXPOSURE_ONLY
                PreviewMeteringResultStatus.FAILED -> PreviewMeteringFeedbackStatus.FAILED
                PreviewMeteringResultStatus.UNSUPPORTED -> PreviewMeteringFeedbackStatus.UNSUPPORTED
            }
            updateState(
                previewMeteringFeedback = currentFeedback.copy(
                    status = feedbackStatus,
                    reason = result.reason
                )
            )
        }

        override fun clearPreviewMeteringFeedback(requestId: String) {
            val currentFeedback = _state.value.presentation.previewMeteringFeedback ?: return
            if (currentFeedback.requestId != requestId) return
            updateState(previewMeteringFeedback = null)
        }

        override fun updatePreviewHostAttached(lastAction: String) {
            updateState(
                previewHostAvailable = true,
                lastAction = lastAction,
                lastError = null
            )
        }

        override fun updatePreviewHostDetached(reason: String, hasPermission: Boolean) {
            updateState(
                previewHostAvailable = false,
                previewStatus = if (hasPermission) PreviewStatus.IDLE else PreviewStatus.BLOCKED,
                previewStatusDetail = reason,
                lastAction = "Preview host detached",
                lastError = if (hasPermission) null else "Camera permission missing"
            )
        }

        override fun updatePreviewSurfaceLost(reason: String) {
            updateState(
                previewStatus = PreviewStatus.SURFACE_LOST,
                previewStatusDetail = reason,
                lastAction = "Preview surface lost",
                lastError = reason
            )
        }

        override fun updatePreviewRuntimeError(detail: String, action: String) {
            updateState(
                previewStatus = PreviewStatus.ERROR,
                previewStatusDetail = detail,
                lastAction = action,
                lastError = detail
            )
        }

        override fun updatePreviewMetrics(metrics: PreviewMetrics) {
            updateState(previewMetrics = metrics)
        }
    }

    private val previewRecoveryProcessor = PreviewRecoverySessionProcessor(
        state = _state,
        effects = _effects,
        trace = trace,
        linkRecorder = this.linkRecorder,
        mutations = previewSessionMutations,
        countdownInProgress = { captureRecordingProcessor.countdownInProgress() },
        cancelPendingCountdown = { reason -> captureRecordingProcessor.cancelPendingCountdown(reason) },
        cancelRecordingElapsedTimer = { captureRecordingProcessor.cancelRecordingElapsedTimer() },
        scope = scope,
        dispatch = { intent -> dispatch(intent) }
    )

    fun linkEventSnapshot(): List<PerformanceLinkEvent> = linkRecorder.snapshot()

    private var activeBrightnessSpan: PerformanceSpanSnapshot? = null

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
        when (intent.owner()) {
            SessionIntentOwner.LIFECYCLE -> processLifecycleIntent(intent)
            SessionIntentOwner.MODE_CONTROL -> processModeControlIntent(intent)
            SessionIntentOwner.PREVIEW_RECOVERY -> processPreviewRecoveryIntent(intent)
            SessionIntentOwner.CAPTURE_RECORDING -> processCaptureRecordingIntent(intent)
            SessionIntentOwner.DIAGNOSTICS -> processDiagnosticsIntent(intent)
        }
    }

    private suspend fun processLifecycleIntent(intent: SessionIntent) {
        when (intent) {
            SessionIntent.Boot -> handleBoot()
            SessionIntent.Shutdown -> handleShutdown()
            is SessionIntent.PermissionsUpdated -> handlePermissionsUpdated(
                cameraGranted = intent.cameraGranted,
                microphoneGranted = intent.microphoneGranted
            )
            is SessionIntent.DeviceCapabilitiesUpdated -> handleDeviceCapabilitiesUpdated(intent.capabilities)
            else -> error("Unexpected lifecycle intent: $intent")
        }
    }

    private suspend fun processModeControlIntent(intent: SessionIntent) {
        when (intent) {
            is SessionIntent.SettingsUpdated -> handleSettingsUpdated(intent.snapshot)
            is SessionIntent.SwitchMode -> handleSwitchMode(intent.modeId)
            SessionIntent.ShutterPressed -> {
                val elapsedNanos = System.nanoTime()
                _state.value = _state.value.copy(
                    shutterPressedAtElapsedMillis = elapsedNanos / 1_000_000L
                )
                trace.record("capture.shutter.pressed", "elapsed=${elapsedNanos / 1_000_000L}ms")
                handleModeIntent(ModeIntent.ShutterPressed)
            }
            SessionIntent.SecondaryActionPressed -> handleModeIntent(ModeIntent.SecondaryActionPressed)
            SessionIntent.TertiaryActionPressed -> handleModeIntent(ModeIntent.TertiaryActionPressed)
            SessionIntent.ProActionPressed -> handleModeIntent(ModeIntent.ProActionPressed)
            SessionIntent.LensFacingToggled -> handleLensFacingToggled()
            SessionIntent.ZoomRatioToggled -> handleZoomRatioToggled()
            is SessionIntent.ApplyZoomRatio -> handleApplyZoomRatio(intent.ratio)
            SessionIntent.StillCaptureQualityToggled -> handleStillCaptureQualityToggled()
            SessionIntent.StillCaptureResolutionToggled -> handleStillCaptureResolutionToggled()
            SessionIntent.PreviewRatioToggled -> handlePreviewRatioToggled()
            is SessionIntent.FrameRatioSelected -> handleModeIntent(ModeIntent.FrameRatioSelected(intent.ratio))
            is SessionIntent.OutputRotationChanged -> handleOutputRotationChanged(intent.rotation)
            is SessionIntent.ApplyPreviewBrightness -> handleApplyPreviewBrightness(intent.exposureCompensationSteps)
            SessionIntent.IncreasePreviewBrightness -> handleStepPreviewBrightness(1)
            SessionIntent.DecreasePreviewBrightness -> handleStepPreviewBrightness(-1)
            SessionIntent.ResetPreviewBrightness -> handleApplyPreviewBrightness(0)
            is SessionIntent.PreviewBrightnessApplied -> handlePreviewBrightnessApplied(intent.result)
            SessionIntent.DocumentBatchClear -> handleDocumentBatchClear()
            is SessionIntent.DocumentBatchRemoveItem -> handleDocumentBatchRemoveItem(intent.itemId)
            is SessionIntent.DocumentBatchMoveItem -> handleDocumentBatchMoveItem(intent.itemId, intent.direction)
            is SessionIntent.DocumentBatchReorder -> handleDocumentBatchReorder(intent.orderedItemIds)
            SessionIntent.DocumentBatchFinish -> handleDocumentBatchFinish()
            else -> error("Unexpected mode control intent: $intent")
        }
    }

    private suspend fun processPreviewRecoveryIntent(intent: SessionIntent) {
        if (intent is SessionIntent.PreviewFirstFrameAvailable) {
            pendingSwitchTraceHandle?.let { handle ->
                trace.end(handle, "mode=${_state.value.activeMode}")
                pendingSwitchTraceHandle = null
            }
            pendingSwitchSpan?.let { span ->
                linkRecorder.completeSpan(
                    span,
                    status = LinkEventStatus.COMPLETED,
                    detail = "firstFrameLatency=${intent.firstFrameLatencyMillis}ms"
                )
                val frameTrace = if (span.flow == "mode-switch") "mode.switch.first.frame" else "lens.switch.first.frame"
                trace.record(frameTrace, "${intent.firstFrameLatencyMillis}ms")
                pendingSwitchSpan = null
            }
        }
        if (intent is SessionIntent.PreviewError ||
            intent is SessionIntent.PreviewSurfaceLost ||
            intent is SessionIntent.PreviewRuntimeIssue
        ) {
            pendingSwitchTraceHandle?.let { handle ->
                trace.end(handle, "failed")
                pendingSwitchTraceHandle = null
            }
            pendingSwitchSpan?.let { span ->
                linkRecorder.completeSpan(span, status = LinkEventStatus.FAILED, detail = "preview error")
                pendingSwitchSpan = null
            }
        }
        previewRecoveryProcessor.process(intent)
    }

    private suspend fun processCaptureRecordingIntent(intent: SessionIntent) {
        captureRecordingProcessor.process(intent)
    }

    private fun processDiagnosticsIntent(intent: SessionIntent) {
        when (intent) {
            is SessionIntent.ThermalStateChanged ->
                trace.record("intent.thermal", intent.thermalState.toString())
            is SessionIntent.PerformanceClassChanged ->
                trace.record("intent.performance", intent.performanceClass.toString())
            else -> error("Unexpected diagnostics intent: $intent")
        }
    }

    private suspend fun handleBoot() {
        if (_state.value.lifecycle == SessionLifecycle.RUNNING) {
            updateState(lastAction = "Session already running")
            trace.record("session.boot.skipped", "already running")
            return
        }

        currentController.onEnter()
        resetPreviewBrightness()
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
        previewRecoveryProcessor.requestPreviewBinding(reason = "session boot", isRecovery = false)
    }

    private suspend fun handleShutdown() {
        if (_state.value.lifecycle == SessionLifecycle.STOPPED) {
            updateState(lastAction = "Session already stopped")
            trace.record("session.shutdown.skipped", "already stopped")
            return
        }

        captureRecordingProcessor.cancelPendingCountdown("Countdown cancelled because session stopped")
        captureRecordingProcessor.cancelRecordingWatchdog()
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
        previewRecoveryProcessor.requestPreviewUnbind(reason = "Session stopped", clearHost = false)
    }

    private suspend fun handleSwitchMode(modeId: ModeId) {
        if (captureRecordingProcessor.countdownInProgress()) {
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

        val previousModeId = currentController.id
        pendingSwitchTraceHandle = trace.begin("mode.switch")
        val modeCorrelationId = "mode-${modeId.name}-${System.nanoTime()}"
        pendingSwitchSpan = linkRecorder.startSpan(
            flow = "mode-switch",
            stage = "total",
            correlationId = modeCorrelationId,
            detail = "from=${previousModeId.name},to=${modeId.name}",
            source = "DefaultCameraSession"
        )
        trace.record("mode.switch.unbind", previousModeId.name)
        currentController.onExit()
        currentController = createController(
            modeId = modeId,
            deviceCapabilities = sessionDeviceCapabilities,
            lensFacing = sessionLensFacing,
            stillCaptureResolutionPreset = sessionStillCaptureResolutionPreset
        )
        currentController.onEnter()
        trace.record("mode.switch.bind", modeId.name)
        resetPreviewBrightness()

        val newDocumentBatch = if (currentController.id == ModeId.DOCUMENT &&
            _state.value.presentation.documentBatch.status != DocumentBatchStatus.ACTIVE
        ) {
            DocumentBatchState(
                batchId = UUID.randomUUID().toString(),
                status = DocumentBatchStatus.ACTIVE
            )
        } else {
            _state.value.presentation.documentBatch
        }
        updateState(
            activeMode = currentController.id,
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolvedActiveDeviceGraph(),
            documentBatch = newDocumentBatch,
            lastAction = "Switched to ${currentController.snapshot.value.uiSpec.title}",
            lastError = null
        )
        trace.record("mode.switched", modeId.name)
        previewRecoveryProcessor.requestPreviewBinding(reason = "mode switched to ${modeId.name.lowercase()}")
    }

    private suspend fun handleSettingsUpdated(
        snapshot: SessionSettingsSnapshot
    ) {
        if (snapshot == sessionSettingsSnapshot) {
            updateState(lastAction = "Session settings already applied")
            trace.record("settings.update.skipped", "no-op")
            return
        }

        if (captureRecordingProcessor.countdownInProgress()) {
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
        previewRecoveryProcessor.requestPreviewBinding(reason = "session settings updated")
    }

    private suspend fun handleLensFacingToggled() {
        if (captureRecordingProcessor.countdownInProgress()) {
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

        val previousLensFacing = sessionLensFacing
        sessionLensFacing = nextLensFacing
        pendingSwitchTraceHandle = trace.begin("lens.switch")
        val lensCorrelationId = "lens-${nextLensFacing.name}-${System.nanoTime()}"
        pendingSwitchSpan = linkRecorder.startSpan(
            flow = "lens-switch",
            stage = "total",
            correlationId = lensCorrelationId,
            detail = "from=${previousLensFacing.label},to=${nextLensFacing.label}",
            source = "DefaultCameraSession"
        )
        trace.record("lens.switch.unbind", previousLensFacing.name)
        currentController.onLensFacingChanged(nextLensFacing)
        trace.record("lens.switch.bind", nextLensFacing.name)
        resetPreviewBrightness()
        updateState(
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolvedActiveDeviceGraph(),
            lastAction = "Switched to ${nextLensFacing.label} lens",
            lastError = null
        )
        trace.record("lens.switched", nextLensFacing.name)
        previewRecoveryProcessor.requestPreviewBinding(reason = "lens switched to ${nextLensFacing.name.lowercase()}")
    }

    private suspend fun handleZoomRatioToggled() {
        if (captureRecordingProcessor.countdownInProgress()) {
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

        val recordingStatus = _state.value.recordingStatus
        if (recordingStatus == RecordingStatus.REQUESTING || recordingStatus == RecordingStatus.STOPPING) {
            updateState(lastAction = "Wait for recording state to settle before adjusting zoom")
            trace.record("zoom.switch.blocked", "recording=$recordingStatus")
            return
        }
        if (activeShot != null && recordingStatus != RecordingStatus.RECORDING) {
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

        if (recordingStatus == RecordingStatus.RECORDING && zoomCapability.support == ZoomControlSupport.DISCRETE_PRESET) {
            updateState(lastAction = "Zoom preset stepping is blocked during recording")
            trace.record("zoom.switch.blocked.recording", "discrete-preset")
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

    private suspend fun handleApplyZoomRatio(targetRatio: Float) {
        if (captureRecordingProcessor.countdownInProgress()) {
            updateState(lastAction = "Wait for countdown to finish before switching zoom")
            trace.record("zoom.apply.blocked", "countdown=${_state.value.countdownRemainingSeconds}")
            return
        }

        val activeShot = _state.value.activeShot
        if (activeShot != null && activeShot.mediaType == MediaType.PHOTO) {
            updateState(lastAction = "Wait for current capture to finish before switching zoom")
            trace.record("zoom.apply.blocked", "shot=${activeShot.shotId}")
            return
        }

        val recordingStatus = _state.value.recordingStatus
        if (recordingStatus == RecordingStatus.REQUESTING || recordingStatus == RecordingStatus.STOPPING) {
            updateState(lastAction = "Wait for recording state to settle before adjusting zoom")
            trace.record("zoom.apply.blocked", "recording=$recordingStatus")
            return
        }

        val zoomCapability = _state.value.activeDeviceCapabilities.zoomRatioCapability
        if (!zoomCapability.isSwitchingSupported) {
            updateState(lastAction = "Zoom switching is unavailable on this device")
            trace.record("zoom.apply.unavailable", zoomCapability.support.tagValue)
            return
        }

        if (recordingStatus == RecordingStatus.RECORDING && zoomCapability.support == ZoomControlSupport.DISCRETE_PRESET) {
            updateState(lastAction = "Zoom preset stepping is blocked during recording")
            trace.record("zoom.apply.blocked.recording", "discrete-preset")
            return
        }

        val normalizedTarget = normalizedZoomRatioValue(targetRatio)
        val clampedRatio = if (zoomCapability.support == ZoomControlSupport.DISCRETE_PRESET) {
            zoomCapability.normalizedSupportedRatios.minByOrNull { kotlin.math.abs(it - normalizedTarget) }
                ?: normalizedTarget
        } else {
            normalizedTarget.coerceIn(
                zoomCapability.normalizedSupportedRatios.first(),
                zoomCapability.normalizedSupportedRatios.last()
            )
        }
        val currentZoomRatio = _state.value.activeDeviceGraph.preview.zoomRatio
        if (clampedRatio == normalizedZoomRatioValue(currentZoomRatio)) {
            updateState(lastAction = "Zoom already active at ${zoomLabel(clampedRatio)}")
            trace.record("zoom.apply.skipped", zoomLabel(clampedRatio))
            return
        }

        // Lens node hysteresis: determine target node from zoom ratio thresholds
        val lensNodeMap = zoomCapability.lensNodeMap
        val currentLensNode = _state.value.activeDeviceGraph.preview.requestedLensNode
        val previewZoomRatio = computePreviewZoomRatio(clampedRatio, lensNodeMap)
        if (lensNodeMap.isNotEmpty()) {
            val targetLensNode = evaluateLensNode(clampedRatio, currentLensNode, lensNodeMap)
            if (targetLensNode != currentLensNode) {
                val switchReason = "Zoom ${zoomLabel(clampedRatio)} crosses threshold for ${targetLensNode.label}"
                val lensCorrelationId = "lens-${System.nanoTime()}"
                val lensSpan = linkRecorder.startSpan(
                    flow = "lens",
                    stage = "switch_node",
                    correlationId = lensCorrelationId,
                    detail = switchReason,
                    source = "DefaultCameraSession"
                )
                _effects.emit(
                    SessionEffect.SwitchLensNode(
                        lensNode = targetLensNode,
                        reason = switchReason
                    )
                )
                linkRecorder.completeSpan(lensSpan, status = LinkEventStatus.COMPLETED)
            }
            updateState(
                activeDeviceGraph = resolveActiveDeviceGraph(
                    baseGraph = currentController.deviceGraph(),
                    deviceCapabilities = _state.value.activeDeviceCapabilities,
                    requestedOutputSize = _state.value.activeDeviceGraph.stillCapture.outputSize,
                    requestedZoomRatio = clampedRatio
                ).let { graph ->
                    graph.copy(preview = graph.preview.copy(
                        requestedLensNode = targetLensNode,
                        previewZoomRatio = previewZoomRatio
                    ))
                },
                lastAction = "Zoom set to ${zoomLabel(clampedRatio)}, lens=${targetLensNode.tagValue}",
                lastError = null
            )
        } else {
            updateState(
                activeDeviceGraph = resolveActiveDeviceGraph(
                    baseGraph = currentController.deviceGraph(),
                    deviceCapabilities = _state.value.activeDeviceCapabilities,
                    requestedOutputSize = _state.value.activeDeviceGraph.stillCapture.outputSize,
                    requestedZoomRatio = clampedRatio
                ).let { graph ->
                    graph.copy(preview = graph.preview.copy(previewZoomRatio = previewZoomRatio))
                },
                lastAction = "Zoom set to ${zoomLabel(clampedRatio)}",
                lastError = null
            )
        }
        trace.record("zoom.updated", zoomLabel(clampedRatio))
        requestZoomApply(clampedRatio)
    }

    /**
     * Determines the target [LensNode] for the given [ratio] using hysteresis around
     * node thresholds. When [currentNode] is non-null, switching requires crossing
     * the threshold by [LENS_NODE_HYSTERESIS_DELTA] to prevent jitter.
     *
     * Returns [LensNode.WIDE] as the fallback when no multi-camera is available.
     */
    internal fun evaluateLensNode(
        ratio: Float,
        currentNode: LensNode?,
        lensNodeMap: Map<LensNode, LensNodeAvailability>
    ): LensNode {
        if (lensNodeMap.isEmpty()) return LensNode.WIDE
        val sorted = lensNodeMap.values
            .filter { it.available }
            .sortedByDescending { it.thresholdRatio }
        if (sorted.isEmpty()) return LensNode.WIDE

        // Find which node range the ratio falls into (from highest threshold down)
        var target = LensNode.WIDE
        for (avail in sorted) {
            if (ratio >= avail.thresholdRatio) {
                target = avail.node
                break
            }
        }

        // Apply hysteresis when switching between nodes
        if (currentNode != null && currentNode != target) {
            val currentThreshold = lensNodeMap[currentNode]?.thresholdRatio ?: return target
            val targetThreshold = lensNodeMap[target]?.thresholdRatio ?: return target
            return if (currentThreshold > targetThreshold) {
                // Switching to lower-zoom node: only if at or below current threshold minus hysteresis
                if (ratio <= currentThreshold - LENS_NODE_HYSTERESIS_DELTA) target else currentNode
            } else {
                // Switching to higher-zoom node: only if above or at target threshold plus hysteresis
                if (ratio >= targetThreshold + LENS_NODE_HYSTERESIS_DELTA) target else currentNode
            }
        }
        return target
    }

    /**
     * Computes the discrete preview zoom ratio for a given capture zoom ratio.
     * Returns the largest available thresholdRatio ≤ [captureZoom], or the smallest
     * threshold if captureZoom is below all thresholds. The result is always ≤ captureZoom.
     */
    internal fun computePreviewZoomRatio(
        captureZoom: Float,
        lensNodeMap: Map<LensNode, LensNodeAvailability>
    ): Float {
        if (lensNodeMap.isEmpty()) return captureZoom.coerceAtLeast(1f)
        val availableThresholds = lensNodeMap.values
            .filter { it.available }
            .map { it.thresholdRatio }
            .sorted()
        if (availableThresholds.isEmpty()) return captureZoom.coerceAtLeast(1f)
        val maxThreshold = availableThresholds.last()
        if (captureZoom >= maxThreshold) return maxThreshold
        val match = availableThresholds.lastOrNull { it <= captureZoom }
        return match ?: availableThresholds.first()
    }

    companion object {
        /** Hysteresis delta for lens node switching (zoom ratio units). */
        internal const val LENS_NODE_HYSTERESIS_DELTA = 0.1f
    }

    private suspend fun handleStillCaptureQualityToggled() {
        if (captureRecordingProcessor.countdownInProgress()) {
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

        val nextQuality = when (sessionStillCaptureQuality) {
            StillCaptureQualityPreference.LATENCY -> StillCaptureQualityPreference.QUALITY
            StillCaptureQualityPreference.QUALITY -> StillCaptureQualityPreference.LATENCY
        }
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
    }

    private suspend fun handleStillCaptureResolutionToggled() {
        if (captureRecordingProcessor.countdownInProgress()) {
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

        val nextOutputSize = sessionDeviceCapabilities.availableStillCaptureOutputSizes
            .takeIf { it.isNotEmpty() }
            ?.let { sizes ->
                nextStillCaptureOutputSize(
                    current = sessionStillCaptureOutputSize,
                    available = sizes
                )
            }
        val nextPreset = nextOutputSize?.let(::resolutionPresetForOutputSize)
            ?: nextStillCaptureResolutionPreset(
                current = sessionStillCaptureResolutionPreset,
                available = sessionDeviceCapabilities.availableStillCaptureResolutionPresets
            )
        sessionStillCaptureResolutionPreset = nextPreset
        sessionStillCaptureOutputSize = nextOutputSize
        currentController.onStillCaptureResolutionChanged(nextPreset)
        val activeGraph = resolveActiveDeviceGraph(
            baseGraph = currentController.deviceGraph(),
            deviceCapabilities = _state.value.activeDeviceCapabilities,
            requestedOutputSize = nextOutputSize,
            requestedZoomRatio = _state.value.activeDeviceGraph.preview.zoomRatio
        )
        updateState(
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = activeGraph,
            lastAction = if (nextOutputSize != null) {
                "Still resolution set to ${nextOutputSize.width}x${nextOutputSize.height}"
            } else {
                "Still resolution set to ${nextPreset.label}"
            },
            lastError = null
        )
        trace.record("still-resolution.updated", nextPreset.tagValue)
    }

    private suspend fun handlePreviewRatioToggled() {
        if (captureRecordingProcessor.countdownInProgress()) {
            updateState(lastAction = "Wait for countdown to finish before switching preview ratio")
            trace.record("preview-ratio.blocked", "countdown=${_state.value.countdownRemainingSeconds}")
            return
        }
        val activeShot = _state.value.activeShot
        if (activeShot != null) {
            val reason = if (activeShot.mediaType == MediaType.VIDEO) {
                "Stop recording before switching preview ratio"
            } else {
                "Wait for current capture to finish before switching preview ratio"
            }
            updateState(lastAction = reason)
            trace.record("preview-ratio.blocked", "shot=${activeShot.shotId},mediaType=${activeShot.mediaType}")
            return
        }
        val nextRatio = nextPreviewRatio(sessionPreviewRatio)
        sessionPreviewRatio = nextRatio
        updateState(
            previewRatio = nextRatio,
            lastAction = "Preview ratio set to ${nextRatio.label}",
            lastError = null
        )
        trace.record("preview-ratio.updated", nextRatio.tagValue)
    }

    private suspend fun handleModeIntent(intent: ModeIntent) {
        if (_state.value.lifecycle != SessionLifecycle.RUNNING) {
            updateState(lastAction = "Ignored $intent because session is not running")
            trace.record("intent.ignored", "lifecycle=${_state.value.lifecycle}")
            return
        }

        if (captureRecordingProcessor.countdownInProgress()) {
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

                            is ModeIntent.FrameRatioSelected ->
                                "Stop recording before changing frame ratio"

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
                    captureRecordingProcessor.startCaptureCountdown(signal.strategy, signal.countdownSeconds)
                } else {
                    captureRecordingProcessor.submitCaptureStrategy(signal.strategy)
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
                updateState(
                    recordingStatus = RecordingStatus.STOPPING,
                    lastAction = "Stopping video recording"
                )
                captureRecordingProcessor.startRecordingWatchdog(RecordingStatus.STOPPING, 15_000L)
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
        val resolutionAdjusted = clampedResolutionPreset != sessionStillCaptureResolutionPreset
        if (resolutionAdjusted) {
            sessionStillCaptureResolutionPreset = clampedResolutionPreset
            sessionStillCaptureOutputSize = resolvedStillCaptureOutputSizeSelection(
                current = null,
                available = deviceCapabilities.availableStillCaptureOutputSizes,
                fallbackPreset = clampedResolutionPreset
            )
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
                resolutionAdjusted
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
        previewRecoveryProcessor.requestPreviewBinding(reason = "device capabilities refreshed")
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

        if (!cameraGranted && captureRecordingProcessor.countdownInProgress()) {
            captureRecordingProcessor.cancelPendingCountdown("Countdown cancelled because camera permission is missing")
        }

        if (!cameraGranted && previousState.activeShot != null) {
            val activeShot = previousState.activeShot
            captureRecordingProcessor.handleInterruptedShotFailure(
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
                previewRecoveryProcessor.requestPreviewUnbind(reason = "Camera permission missing", clearHost = false)

            cameraGranted && !previous.cameraGranted -> {
                if (!previewRecoveryProcessor.requestPendingPreviewHostRecovery()) {
                    previewRecoveryProcessor.requestPreviewBinding(reason = "camera permission granted", isRecovery = false)
                }
            }
        }
    }

    private suspend fun handleOutputRotationChanged(rotation: CameraOutputRotation) {
        if (rotation == _state.value.outputRotation) {
            trace.record("orientation.output.skipped", "already=$rotation")
            return
        }
        updateState(
            outputRotation = rotation,
            lastAction = "Output rotation set to $rotation",
            lastError = null
        )
        trace.record("orientation.output.changed", rotation.name)
        _effects.emit(SessionEffect.UpdateOutputRotation(rotation))
    }

    private suspend fun handleStepPreviewBrightness(delta: Int) {
        val current = _state.value.presentation.previewBrightnessSteps
        val range = _state.value.activeDeviceCapabilities.previewBrightnessRange
        val newValue = range.clamp(current + delta)
        handleApplyPreviewBrightness(newValue)
    }

    private suspend fun handleApplyPreviewBrightness(targetSteps: Int) {
        if (_state.value.activeMode != ModeId.PHOTO) {
            updateState(lastAction = "Brightness is only available in photo mode")
            return
        }
        if (_state.value.previewStatus != PreviewStatus.ACTIVE) {
            return
        }
        if (!_state.value.permissionState.cameraGranted || _state.value.previewHostAvailable.not()) {
            return
        }
        if (captureRecordingProcessor.countdownInProgress()) {
            updateState(lastAction = "Wait for countdown to finish before adjusting brightness")
            return
        }
        if (_state.value.activeShot != null) {
            updateState(lastAction = "Wait for current capture to finish before adjusting brightness")
            return
        }

        val range = _state.value.activeDeviceCapabilities.previewBrightnessRange
        val clamped = range.clamp(targetSteps)
        val requestId = "brightness-${System.nanoTime()}"
        updateState(
            previewBrightnessFeedback = PreviewBrightnessFeedback(
                requestId = requestId,
                requestedSteps = clamped,
                appliedSteps = null,
                status = PreviewBrightnessFeedbackStatus.REQUESTED
            ),
            lastAction = "Brightness set to ${brightnessLabel(clamped)}",
            lastError = null
        )
        trace.record("preview.brightness.requested", "requestId=$requestId,steps=$clamped")
        // Start brightness link span
        activeBrightnessSpan = linkRecorder.startSpan(
            flow = "brightness",
            stage = "requested",
            correlationId = requestId,
            detail = "steps=$clamped",
            source = "DefaultCameraSession"
        )
        _effects.emit(
            SessionEffect.ApplyPreviewBrightness(
                PreviewBrightnessRequest(
                    requestId = requestId,
                    exposureCompensationSteps = clamped
                )
            )
        )
    }

    private fun handlePreviewBrightnessApplied(result: PreviewBrightnessResult) {
        val currentFeedback = _state.value.presentation.previewBrightnessFeedback ?: return
        if (currentFeedback.requestId != result.requestId) {
            trace.record("preview.brightness.stale", "expected=${currentFeedback.requestId},got=${result.requestId}")
            return
        }
        // Complete brightness link span
        activeBrightnessSpan?.let { span ->
            if (span.correlationId == result.requestId) {
                val linkStatus = when (result.status) {
                    PreviewBrightnessResultStatus.APPLIED -> LinkEventStatus.COMPLETED
                    PreviewBrightnessResultStatus.DEGRADED_SAVED_ONLY -> LinkEventStatus.DEGRADED
                    PreviewBrightnessResultStatus.FAILED -> LinkEventStatus.FAILED
                    PreviewBrightnessResultStatus.UNSUPPORTED -> LinkEventStatus.UNAVAILABLE
                }
                linkRecorder.completeSpan(span, status = linkStatus, detail = result.reason)
                activeBrightnessSpan = null
            }
        }
        when (result.status) {
            PreviewBrightnessResultStatus.APPLIED -> {
                updateState(
                    previewBrightnessSteps = result.exposureCompensationSteps,
                    previewBrightnessFeedback = currentFeedback.copy(
                        appliedSteps = result.exposureCompensationSteps,
                        status = PreviewBrightnessFeedbackStatus.APPLIED
                    )
                )
                trace.record("preview.brightness.applied", "steps=${result.exposureCompensationSteps}")
            }
            PreviewBrightnessResultStatus.DEGRADED_SAVED_ONLY -> {
                updateState(
                    previewBrightnessFeedback = currentFeedback.copy(
                        status = PreviewBrightnessFeedbackStatus.DEGRADED_SAVED_ONLY,
                        reason = result.reason
                    )
                )
                trace.record("preview.brightness.degraded", result.reason ?: "saved-only")
            }
            PreviewBrightnessResultStatus.FAILED -> {
                updateState(
                    previewBrightnessFeedback = currentFeedback.copy(
                        status = PreviewBrightnessFeedbackStatus.FAILED,
                        reason = result.reason
                    )
                )
                trace.record("preview.brightness.failed", result.reason ?: "unknown")
            }
            PreviewBrightnessResultStatus.UNSUPPORTED -> {
                updateState(
                    previewBrightnessFeedback = currentFeedback.copy(
                        status = PreviewBrightnessFeedbackStatus.UNSUPPORTED,
                        reason = result.reason
                    )
                )
                trace.record("preview.brightness.unsupported", result.reason ?: "unsupported")
            }
        }
    }

    private fun resetPreviewBrightness() {
        val current = _state.value.presentation.previewBrightnessSteps
        if (current != 0) {
            updateState(previewBrightnessSteps = 0, previewBrightnessFeedback = null)
        }
    }

    private fun handleDocumentBatchClear() {
        val currentBatch = _state.value.presentation.documentBatch
        if (currentBatch.status == DocumentBatchStatus.INACTIVE) {
            updateState(lastAction = "No active document batch to clear")
            return
        }
        updateState(
            documentBatch = currentBatch.copy(
                items = emptyList(),
                latestItemId = null,
                lastMessage = "Batch cleared"
            ),
            lastAction = "Document batch cleared"
        )
    }

    private fun handleDocumentBatchRemoveItem(itemId: String) {
        val currentBatch = _state.value.presentation.documentBatch
        if (currentBatch.status != DocumentBatchStatus.ACTIVE) {
            updateState(lastAction = "Cannot remove item: batch is not active")
            return
        }
        val item = currentBatch.items.find { it.itemId == itemId }
        if (item == null) {
            updateState(lastAction = "Cannot remove item: $itemId not in batch")
            return
        }
        updateState(
            documentBatch = currentBatch.removeItem(itemId),
            lastAction = "Removed document page from batch"
        )
    }

    private fun handleDocumentBatchMoveItem(itemId: String, direction: DocumentBatchMoveDirection) {
        val currentBatch = _state.value.presentation.documentBatch
        if (currentBatch.status != DocumentBatchStatus.ACTIVE) {
            updateState(lastAction = "Cannot move item: batch is not active")
            return
        }
        val currentIndex = currentBatch.items.indexOfFirst { it.itemId == itemId }
        if (currentIndex == -1) {
            updateState(lastAction = "Cannot move: $itemId not in batch")
            return
        }
        if (currentBatch.items.size < 2) {
            updateState(lastAction = "Cannot reorder: batch has fewer than 2 items")
            return
        }
        val targetIndex = when (direction) {
            DocumentBatchMoveDirection.UP -> (currentIndex - 1).coerceAtLeast(0)
            DocumentBatchMoveDirection.DOWN -> (currentIndex + 1).coerceAtMost(currentBatch.items.lastIndex)
        }
        if (targetIndex == currentIndex) {
            updateState(lastAction = "Item already at target position")
            return
        }
        updateState(
            documentBatch = currentBatch.moveItem(itemId, direction),
            lastAction = "Reordered document pages"
        )
    }

    private fun handleDocumentBatchReorder(orderedItemIds: List<String>) {
        val currentBatch = _state.value.presentation.documentBatch
        if (currentBatch.status != DocumentBatchStatus.ACTIVE) {
            updateState(lastAction = "Cannot reorder: batch is not active")
            return
        }
        updateState(
            documentBatch = currentBatch.reorder(orderedItemIds),
            lastAction = "Document pages reordered"
        )
    }

    private fun handleDocumentBatchFinish() {
        val currentBatch = _state.value.presentation.documentBatch
        if (currentBatch.status != DocumentBatchStatus.ACTIVE) {
            updateState(lastAction = "Cannot finish: batch is not active")
            return
        }
        updateState(
            documentBatch = currentBatch.copy(
                status = DocumentBatchStatus.FINISHED,
                lastMessage = "Batch finished"
            ),
            lastAction = "Document batch finished"
        )
    }

    private fun brightnessLabel(steps: Int): String {
        return if (steps >= 0) "+$steps" else "$steps"
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
        activeEffectSpec: com.opencamera.core.effect.EffectSpec = _state.value.activeEffectSpec,
        activeCapabilityReport: com.opencamera.core.capability.CapabilityGraphReport? = _state.value.activeCapabilityReport,
        previewRatio: PreviewRatio = _state.value.previewRatio,
        outputRotation: CameraOutputRotation = _state.value.outputRotation,
        countdownRemainingSeconds: Int? = _state.value.presentation.countdownRemainingSeconds,
        previewThumbnailPath: String? = _state.value.presentation.previewThumbnailPath,
        latestThumbnailSource: ThumbnailSource? = _state.value.presentation.latestThumbnailSource,
        previewSnapshotGeneration: Int = _state.value.presentation.previewSnapshotGeneration,
        lastAction: String = _state.value.presentation.lastAction,
        latestCapturePath: String? = _state.value.presentation.latestCapturePath,
        latestVideoPath: String? = _state.value.presentation.latestVideoPath,
        latestLivePhotoBundle: LivePhotoBundle? = _state.value.presentation.latestLivePhotoBundle,
        latestSavedMediaType: SavedMediaType? = _state.value.presentation.latestSavedMediaType,
        latestPipelineNotes: List<String> = _state.value.presentation.latestPipelineNotes,
        pendingCaptureFeedback: CaptureFeedbackPreview? = _state.value.presentation.pendingCaptureFeedback,
        lastError: String? = _state.value.presentation.lastError,
        previewMeteringFeedback: PreviewMeteringFeedback? = _state.value.presentation.previewMeteringFeedback,
        previewBrightnessSteps: Int = _state.value.presentation.previewBrightnessSteps,
        previewBrightnessFeedback: PreviewBrightnessFeedback? = _state.value.presentation.previewBrightnessFeedback,
        photoSceneSignal: com.opencamera.core.device.PhotoSceneSignal = _state.value.presentation.photoSceneSignal,
        photoLowLightPrompt: PhotoLowLightPrompt? = _state.value.presentation.photoLowLightPrompt,
        documentBatch: DocumentBatchState = _state.value.presentation.documentBatch
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
            activeEffectSpec = activeEffectSpec,
            activeCapabilityReport = activeCapabilityReport,
            previewRatio = previewRatio,
            outputRotation = outputRotation,
            presentation = _state.value.presentation.copy(
                countdownRemainingSeconds = countdownRemainingSeconds,
                previewThumbnailPath = previewThumbnailPath,
                latestThumbnailSource = latestThumbnailSource,
                previewSnapshotGeneration = previewSnapshotGeneration,
                pendingCaptureFeedback = pendingCaptureFeedback,
                lastAction = lastAction,
                latestCapturePath = latestCapturePath,
                latestVideoPath = latestVideoPath,
                latestLivePhotoBundle = latestLivePhotoBundle,
                latestSavedMediaType = latestSavedMediaType,
                latestPipelineNotes = latestPipelineNotes,
                lastError = lastError,
                previewMeteringFeedback = previewMeteringFeedback,
                previewBrightnessSteps = previewBrightnessSteps,
                previewBrightnessFeedback = previewBrightnessFeedback,
                photoSceneSignal = photoSceneSignal,
                photoLowLightPrompt = photoLowLightPrompt,
                documentBatch = documentBatch
            )
        )
    }

    private suspend fun requestZoomApply(zoomRatio: Float) {
        val correlationId = "zoom-${System.nanoTime()}"
        val span = linkRecorder.startSpan(
            flow = "lens",
            stage = "zoom_request",
            correlationId = correlationId,
            detail = "ratio=${normalizedZoomRatioValue(zoomRatio)}x",
            source = "DefaultCameraSession"
        )
        _effects.emit(
            SessionEffect.ApplyZoomRatio(
                zoomRatio = normalizedZoomRatioValue(zoomRatio)
            )
        )
        linkRecorder.completeSpan(span, status = LinkEventStatus.COMPLETED)
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
            fallbackPreset = sessionStillCaptureResolutionPreset
        )
        val resolvedZoomRatio = resolvedZoomRatioSelection(
            current = requestedZoomRatio,
            capability = deviceCapabilities.zoomRatioCapability
        )
        val resolvedPreviewZoomRatio = computePreviewZoomRatio(
            resolvedZoomRatio,
            deviceCapabilities.zoomRatioCapability.lensNodeMap
        )
        return baseGraph.copy(
            preview = baseGraph.preview.copy(
                zoomRatio = resolvedZoomRatio,
                previewZoomRatio = resolvedPreviewZoomRatio
            ),
            stillCapture = baseGraph.stillCapture.copy(
                outputSize = resolvedOutputSize
            )
        )
    }

    private fun zoomLabel(zoomRatio: Float): String = "${normalizedZoomRatioValue(zoomRatio)}x"

    private fun createController(
        modeId: ModeId,
        deviceCapabilities: DeviceCapabilities,
        lensFacing: LensFacing,
        stillCaptureResolutionPreset: StillCaptureResolutionPreset
    ): ModeController {
        val clampedStillCaptureResolutionPreset = clampStillCaptureResolutionPreset(
            current = stillCaptureResolutionPreset,
            available = deviceCapabilities.availableStillCaptureResolutionPresets
        )
        sessionDeviceCapabilities = deviceCapabilities
        sessionLensFacing = lensFacing
        sessionStillCaptureResolutionPreset = clampedStillCaptureResolutionPreset
        return registry.createController(
            modeId = modeId,
            context = ModeContext(
                deviceCapabilities = deviceCapabilities,
                initialLensFacing = lensFacing,
                initialStillCaptureResolutionPreset = clampedStillCaptureResolutionPreset,
                runtimeState = {
                    ModeRuntimeState(
                        deviceCapabilities = sessionDeviceCapabilities,
                        lensFacing = sessionLensFacing,
                        stillCaptureResolutionPreset = sessionStillCaptureResolutionPreset,
                        stillCaptureQuality = sessionStillCaptureQuality,
                        stillCaptureOutputSize = sessionStillCaptureOutputSize
                    )
                },
                eventSink = { detail ->
                    trace.record("mode.event", detail)
                },
                onEffectSpecChanged = { spec ->
                    val resolver = effectCapabilityResolver
                    if (resolver != null) {
                        val report = resolver.resolve(spec)
                        updateState(activeEffectSpec = report.effectiveSpec)
                    } else {
                        updateState(activeEffectSpec = spec)
                    }
                    val graphResolver = capabilityGraphResolver
                    if (graphResolver != null) {
                        val requirements = capabilityRequirements()
                        if (requirements.isNotEmpty()) {
                            val graphReport = graphResolver.resolve(
                                featureId = currentController.id.name.lowercase(),
                                requirements = requirements,
                                effectSpec = spec
                            )
                            updateState(activeCapabilityReport = graphReport)
                        }
                    }
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

    private fun nextPreviewRatio(current: PreviewRatio): PreviewRatio {
        val ordered = PreviewRatio.entries
        val currentIndex = ordered.indexOf(current)
        return ordered[(currentIndex + 1) % ordered.size]
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
