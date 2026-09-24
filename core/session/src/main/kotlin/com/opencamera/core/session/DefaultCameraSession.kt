package com.opencamera.core.session

import com.opencamera.core.device.CameraOutputRotation
import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.EffectiveStillCaptureRecipe
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.LensNode
import com.opencamera.core.device.LensNodeAvailability
import com.opencamera.core.device.PreviewStreamAspect
import com.opencamera.core.device.PreviewBrightnessRequest
import com.opencamera.core.device.PreviewBrightnessResult
import com.opencamera.core.device.PreviewBrightnessResultStatus
import com.opencamera.core.device.PreviewMeteringPoint
import com.opencamera.core.device.PreviewMeteringResult
import com.opencamera.core.device.PreviewMeteringResultStatus
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
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
import com.opencamera.core.media.renderUriOrNull
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeIntent
import com.opencamera.core.mode.ModeRuntimeState
import com.opencamera.core.mode.ModeRegistry
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.settings.reduce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
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
    private val capabilityRequirements: () -> List<com.opencamera.core.capability.CapabilityRequirement> = { emptyList() },
    private val recordingTimerDispatcher: CoroutineDispatcher? = Dispatchers.Default,
    private val elapsedRealtimeMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) : CameraSession {
    private val linkRecorder: PerformanceLinkRecorder = linkRecorder ?: InMemoryPerformanceLinkRecorder()
    private val intentChannel = Channel<SessionIntent>(Channel.UNLIMITED)
    private val supportedModes = registry.supportedModes(baseDeviceCapabilities)
    private val initialMode = supportedModes.firstOrNull { it == defaultMode }
        ?: supportedModes.firstOrNull()
        ?: error("No supported camera modes for $baseDeviceCapabilities")
    private val initialLensFacing = defaultLensFacing(baseDeviceCapabilities.availableLensFacings)
    private val initialStillCaptureQuality = StillCaptureQualityPreference.QUALITY
    private val initialStillCaptureResolutionPreset = clampStillCaptureResolutionPreset(
        StillCaptureResolutionPreset.LARGE_12MP,
        baseDeviceCapabilities.availableStillCaptureResolutionPresets
    )
    private val initialStillCaptureOutputSize = resolvedStillCaptureOutputSizeSelection(
        current = null,
        available = baseDeviceCapabilities.availableStillCaptureOutputSizes,
        fallbackPreset = initialStillCaptureResolutionPreset
    )
    private var runtimeConfiguration = SessionRuntimeConfiguration(
        deviceCapabilities = baseDeviceCapabilities,
        lensFacing = initialLensFacing,
        stillCaptureQuality = initialStillCaptureQuality,
        stillCaptureResolutionPreset = initialStillCaptureResolutionPreset,
        stillCaptureOutputSize = initialStillCaptureOutputSize,
        previewRatio = PreviewRatio.FULL,
        settings = settingsSnapshot
    )
    private var pendingSwitchTraceHandle: TraceHandle? = null
    private var pendingSwitchSpan: PerformanceSpanSnapshot? = null
    private var previewStyleStrengthOverride: Float? = null
    private var previewStyleOriginalComparisonActive = false
    private var previewStyleRefreshDeferred = false
    @Volatile private var pendingSwitchLensNode: LensNode? = null
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
            settings = runtimeConfiguration.settings,
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
        dispatch = { intent -> dispatch(intent) },
        recordingTimerDispatcher = recordingTimerDispatcher,
        elapsedRealtimeMillis = elapsedRealtimeMillis
    )

    /**
     * A capture failure (captureStatus=FAILED) must keep its error visible until
     * the next successful capture/recording action; preview lifecycle events
     * (start/active/stop/host re-attach) must not silently erase it (INV-3c).
     */
    private fun previewLastErrorOrCaptureFailure(): String? {
        return if (_state.value.captureStatus == CaptureStatus.FAILED) {
            _state.value.lastError
        } else {
            null
        }
    }

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
                lastError = previewLastErrorOrCaptureFailure()
            )
        }

        override fun updatePreviewActive(firstFrameLatencyMillis: Long) {
            updateState(
                previewStatus = PreviewStatus.ACTIVE,
                lastAction = "Preview active (${firstFrameLatencyMillis} ms first frame)",
                lastError = previewLastErrorOrCaptureFailure()
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
            val stopped = _state.value.lifecycle == SessionLifecycle.STOPPED
            updateState(
                // After Shutdown the preview must stay IDLE; a late PreviewStopped
                // must not resurrect BLOCKED on a stopped session (INV-4b).
                previewStatus = if (stopped || hasCameraPermission) {
                    PreviewStatus.IDLE
                } else {
                    PreviewStatus.BLOCKED
                },
                previewStatusDetail = reason,
                lastAction = "Preview stopped",
                lastError = if (hasCameraPermission) {
                    previewLastErrorOrCaptureFailure()
                } else {
                    "Camera permission missing"
                }
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

        override fun updateDocumentBatchPreviewItem(shot: ShotRequest, outputPath: String) {
            val source = ThumbnailSource.PreviewSnapshot(outputPath)
            val previewItem = DocumentBatchItem(
                itemId = shot.shotId,
                shotId = shot.shotId,
                orderIndex = _state.value.presentation.documentBatch.items.size,
                outputPath = null,
                renderUri = source.renderUriOrNull() ?: source.outputPathOrNull(),
                thumbnailSource = source,
                profileId = shot.saveRequest.metadata.customTags["profile"],
                scanMode = shot.saveRequest.metadata.customTags["scanMode"],
                cropStatus = DocumentBatchCropStatus.NOT_REQUESTED,
                pipelineNotes = listOf("document:preview-feedback")
            )
            updateState(
                documentBatch = _state.value.presentation.documentBatch.upsertPreviewItem(previewItem),
                lastAction = "Document page preview ready"
            )
        }

        override fun updatePreviewMeteringRequested(
            requestId: String,
            point: PreviewMeteringPoint,
            persistence: com.opencamera.core.device.PreviewMeteringPersistence
        ) {
            updateState(
                previewMeteringFeedback = PreviewMeteringFeedback(
                    requestId = requestId,
                    normalizedX = point.normalizedX,
                    normalizedY = point.normalizedY,
                    status = PreviewMeteringFeedbackStatus.REQUESTED,
                    persistence = persistence
                )
            )
        }

        override fun updatePreviewMeteringCompleted(result: PreviewMeteringResult) {
            val currentFeedback = _state.value.presentation.previewMeteringFeedback ?: return
            val feedbackStatus = when (result.status) {
                PreviewMeteringResultStatus.SUCCEEDED -> PreviewMeteringFeedbackStatus.SUCCEEDED
                PreviewMeteringResultStatus.LOCKED -> PreviewMeteringFeedbackStatus.LOCKED
                PreviewMeteringResultStatus.DEGRADED_FOCUS_LOCK_ONLY ->
                    PreviewMeteringFeedbackStatus.DEGRADED_FOCUS_LOCK_ONLY
                PreviewMeteringResultStatus.DEGRADED_EXPOSURE_LOCK_ONLY ->
                    PreviewMeteringFeedbackStatus.DEGRADED_EXPOSURE_LOCK_ONLY
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
                lastError = previewLastErrorOrCaptureFailure()
            )
        }

        override fun updatePreviewHostDetached(reason: String, hasPermission: Boolean) {
            updateState(
                previewHostAvailable = false,
                previewStatus = if (hasPermission) PreviewStatus.IDLE else PreviewStatus.BLOCKED,
                previewStatusDetail = reason,
                lastAction = "Preview host detached",
                lastError = if (hasPermission) {
                    previewLastErrorOrCaptureFailure()
                } else {
                    "Camera permission missing"
                }
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
            is SessionIntent.PreviewStyleStrengthChanged -> handlePreviewStyleStrengthChanged(intent.strength)
            is SessionIntent.PreviewStyleOriginalComparisonChanged ->
                handlePreviewStyleOriginalComparisonChanged(intent.active)
            is SessionIntent.SwitchMode -> handleSwitchMode(intent.modeId)
            SessionIntent.ShutterPressed -> {
                if (hasTransientStylePreviewOverride()) {
                    updateState(lastAction = "Release style preview before capture")
                    trace.record("capture.shutter.blocked", "transient-style-preview")
                    return
                }
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
            is SessionIntent.DocumentBatchUpdateCropStatus -> handleDocumentBatchUpdateCropStatus(
                intent.itemId, intent.cropStatus, intent.cropRect
            )
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
        // Guaranteed convergence: when a fatal preview fault or surface loss set the
        // recording into STOPPING, arm the STOPPING watchdog so the session recovers
        // even if the device never finalizes (INV-3b).
        if (state.value.recordingStatus == RecordingStatus.STOPPING) {
            captureRecordingProcessor.startRecordingWatchdog(RecordingStatus.STOPPING, 15_000L)
        }
    }

    private suspend fun processCaptureRecordingIntent(intent: SessionIntent) {
        captureRecordingProcessor.process(intent)
        if (previewStyleRefreshDeferred && !shouldDeferPreviewStyleRefresh()) {
            previewStyleRefreshDeferred = false
            refreshPreviewStyleRuntime("preview style override cleared after capture")
        }
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
        captureRecordingProcessor.cancelPostProcessLiveness(null)
        captureRecordingProcessor.cancelDocumentBatchWatchdog(null)
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
            lastAction = "Session stopped",
            pendingPostprocess = null,
            captureReadiness = null,
            pendingCaptureFeedback = null
        )
        trace.record("session.stopped", "mode=${currentController.id}")
        previewRecoveryProcessor.requestPreviewUnbind(reason = "Session stopped", clearHost = false)
    }

    private fun blockIfCommandNotAdmitted(command: SessionCommandKind, traceEventName: String? = null): Boolean {
        val admission = SessionCommandAdmission.evaluate(
            command = command,
            snapshot = SessionCommandAdmissionSnapshot(
                countdownInProgress = captureRecordingProcessor.countdownInProgress(),
                countdownRemainingSeconds = _state.value.countdownRemainingSeconds,
                activeShot = _state.value.activeShot,
                recordingStatus = _state.value.recordingStatus
            )
        )
        return when (admission) {
            SessionCommandAdmissionResult.Allowed -> false
            is SessionCommandAdmissionResult.Blocked -> {
                updateState(lastAction = admission.lastAction)
                traceEventName?.let { trace.record(it, admission.traceDetail) }
                true
            }
        }
    }

    private suspend fun handleSwitchMode(modeId: ModeId) {
        if (blockIfCommandNotAdmitted(SessionCommandKind.MODE_SWITCH, "mode.switch.blocked")) {
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
            deviceCapabilities = runtimeConfiguration.deviceCapabilities,
            lensFacing = runtimeConfiguration.lensFacing,
            stillCaptureResolutionPreset = runtimeConfiguration.stillCaptureResolutionPreset
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
        if (snapshot == runtimeConfiguration.settings) {
            updateState(lastAction = "Session settings already applied")
            trace.record("settings.update.skipped", "no-op")
            return
        }

        if (blockIfCommandNotAdmitted(SessionCommandKind.SETTINGS_UPDATE, "settings.update.blocked")) {
            return
        }

        val wasRunning = _state.value.lifecycle == SessionLifecycle.RUNNING
        val previousEvSteps = runtimeConfiguration.settings.catalog.manualCaptureDraft.exposureCompensationSteps
        runtimeConfiguration = runtimeConfiguration.copy(settings = snapshot)
        if (wasRunning) {
            currentController.onEnter()
        }

        updateState(
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolvedActiveDeviceGraph(),
            settings = runtimeConfiguration.settings,
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

        val currentEvSteps = snapshot.catalog.manualCaptureDraft.exposureCompensationSteps
        if (previousEvSteps != currentEvSteps && _state.value.activeMode == ModeId.HUMANISTIC) {
            handleApplyPreviewBrightness(currentEvSteps ?: 0)
        }
    }

    private suspend fun handlePreviewStyleStrengthChanged(strength: Float?) {
        val normalized = strength?.coerceIn(0f, 1f)
        if (previewStyleStrengthOverride == normalized) return
        if (normalized != null && blockIfCommandNotAdmitted(
                SessionCommandKind.SETTINGS_UPDATE,
                "preview.style-strength.blocked"
            )
        ) {
            return
        }
        previewStyleStrengthOverride = normalized
        if (shouldDeferPreviewStyleRefresh()) {
            previewStyleRefreshDeferred = true
            return
        }
        refreshPreviewStyleRuntime("preview style strength changed")
    }

    private suspend fun handlePreviewStyleOriginalComparisonChanged(active: Boolean) {
        if (previewStyleOriginalComparisonActive == active) return
        if (active && blockIfCommandNotAdmitted(
                SessionCommandKind.SETTINGS_UPDATE,
                "preview.style-original.blocked"
            )
        ) {
            return
        }
        previewStyleOriginalComparisonActive = active
        if (shouldDeferPreviewStyleRefresh()) {
            previewStyleRefreshDeferred = true
            return
        }
        refreshPreviewStyleRuntime(
            if (active) "preview style original comparison started" else "preview style original comparison ended"
        )
    }

    private suspend fun refreshPreviewStyleRuntime(lastAction: String) {
        currentController.refreshEffectSpec()
        updateState(
            modeSnapshot = currentController.snapshot.value,
            activeDeviceCapabilities = _state.value.activeDeviceCapabilities,
            activeDeviceGraph = resolvedActiveDeviceGraph(),
            settings = runtimeConfiguration.settings,
            lastAction = lastAction,
            lastError = null
        )
    }

    private fun hasTransientStylePreviewOverride(): Boolean =
        previewStyleOriginalComparisonActive || previewStyleStrengthOverride != null

    private fun shouldDeferPreviewStyleRefresh(): Boolean =
        captureRecordingProcessor.countdownInProgress() || _state.value.activeShot != null

    private fun effectiveStylePreviewSettings(): SessionSettingsSnapshot {
        val baseline = runtimeConfiguration.settings
        val photo = baseline.persisted.photo
        val strength = when {
            previewStyleOriginalComparisonActive -> 0f
            previewStyleStrengthOverride != null -> previewStyleStrengthOverride!!
            else -> photo.styleStrength
        }
        val colorLab = if (previewStyleOriginalComparisonActive) {
            photo.colorLabSpec.copy(strength = 0f)
        } else {
            photo.colorLabSpec
        }
        return baseline.copy(
            persisted = baseline.persisted.copy(
                photo = photo.copy(styleStrength = strength, colorLabSpec = colorLab)
            )
        )
    }

    private suspend fun handleLensFacingToggled() {
        if (blockIfCommandNotAdmitted(SessionCommandKind.LENS_SWITCH, "lens.switch.blocked")) {
            return
        }

        val availableLensFacings = _state.value.activeDeviceCapabilities.availableLensFacings
        if (availableLensFacings.size < 2) {
            updateState(lastAction = "No alternate lens available on this device")
            trace.record("lens.switch.unavailable", availableLensFacings.joinToString())
            return
        }

        val nextLensFacing = nextLensFacing(
            current = runtimeConfiguration.lensFacing,
            available = availableLensFacings
        )
        if (nextLensFacing == runtimeConfiguration.lensFacing) {
            updateState(lastAction = "Lens already active: ${nextLensFacing.label}")
            trace.record("lens.switch.skipped", nextLensFacing.name)
            return
        }

        val previousLensFacing = runtimeConfiguration.lensFacing
        runtimeConfiguration = runtimeConfiguration.copy(lensFacing = nextLensFacing)
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

        previewRecoveryProcessor.cancelFocusExposureLockIfActive("zoom changed")
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

        previewRecoveryProcessor.cancelFocusExposureLockIfActive("zoom changed")
        // Lens node hysteresis: determine target node from zoom ratio thresholds.
        // Logical-only camera ranges can still expose preview baselines; those must not
        // force a physical lens rebind when there is only one real lens node.
        val lensNodeMap = zoomCapability.lensNodeMap
        val hasMultipleLensNodes = lensNodeMap.values.count { it.available } > 1
        val currentLensNode = _state.value.activeDeviceGraph.preview.requestedLensNode
        val previewZoomRatio = computePreviewZoomRatio(clampedRatio, zoomCapability)
        if (hasMultipleLensNodes) {
            val targetLensNode = evaluateLensNode(clampedRatio, currentLensNode, lensNodeMap)
            if (targetLensNode != currentLensNode) {
                // Skip duplicate SwitchLensNode if transition to this node is already pending
                if (pendingSwitchLensNode != targetLensNode) {
                    pendingSwitchLensNode = targetLensNode
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
                pendingSwitchLensNode = null
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
     * the threshold by [LENS_NODE_HYSTERESIS_RATIO] * ratio to prevent jitter.
     *
     * Returns [LensNode.WIDE] as the fallback when no multi-camera is available.
     */
    internal fun evaluateLensNode(
        ratio: Float,
        currentNode: LensNode?,
        lensNodeMap: Map<LensNode, LensNodeAvailability>
    ): LensNode = SessionDevicePolicies.evaluateLensNode(ratio, currentNode, lensNodeMap)

    /**
     * Computes the discrete preview zoom ratio for a given capture zoom ratio.
     *
     * When hardware exposes logical zoom only, [ZoomRatioCapability.previewBaseRatios] gives
     * us the stable preview baselines. Physical lens thresholds remain the fallback for older
     * multi-camera probes. Each baseline switches after a short delay, so the frame overlay
     * shows the changing capture area before the preview stream jumps.
     */
    internal fun computePreviewZoomRatio(
        captureZoom: Float,
        capability: ZoomRatioCapability
    ): Float = SessionDevicePolicies.previewZoomRatio(captureZoom, capability)

    /**
     * Computes preview baselines from physical lens thresholds.
     */
    internal fun computePreviewZoomRatio(
        captureZoom: Float,
        lensNodeMap: Map<LensNode, LensNodeAvailability>
    ): Float = SessionDevicePolicies.previewZoomRatio(captureZoom, lensNodeMap)

    private suspend fun handleStillCaptureQualityToggled() {
        if (blockIfCommandNotAdmitted(SessionCommandKind.STILL_CAPTURE_QUALITY, "still-quality.blocked")) {
            return
        }

        if (_state.value.activeDeviceGraph.template != CaptureTemplate.STILL_CAPTURE) {
            updateState(lastAction = "Still quality is only available in photo modes")
            trace.record("still-quality.unavailable", _state.value.activeMode.name)
            return
        }

        val nextQuality = when (runtimeConfiguration.stillCaptureQuality) {
            StillCaptureQualityPreference.LATENCY -> StillCaptureQualityPreference.QUALITY
            StillCaptureQualityPreference.QUALITY -> StillCaptureQualityPreference.LATENCY
        }
        runtimeConfiguration = runtimeConfiguration.copy(stillCaptureQuality = nextQuality)
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
        if (blockIfCommandNotAdmitted(SessionCommandKind.STILL_CAPTURE_RESOLUTION, "still-resolution.blocked")) {
            return
        }

        if (_state.value.activeDeviceGraph.template != CaptureTemplate.STILL_CAPTURE) {
            updateState(lastAction = "Still resolution is only available in photo modes")
            trace.record("still-resolution.unavailable", _state.value.activeMode.name)
            return
        }

        val nextOutputSize = runtimeConfiguration.deviceCapabilities.availableStillCaptureOutputSizes
            .takeIf { it.isNotEmpty() }
            ?.let { sizes ->
                nextStillCaptureOutputSize(
                    current = runtimeConfiguration.stillCaptureOutputSize,
                    available = sizes
                )
            }
        val nextPreset = nextOutputSize?.let(::resolutionPresetForOutputSize)
            ?: nextStillCaptureResolutionPreset(
                current = runtimeConfiguration.stillCaptureResolutionPreset,
                available = runtimeConfiguration.deviceCapabilities.availableStillCaptureResolutionPresets
            )
        runtimeConfiguration = runtimeConfiguration.copy(
            stillCaptureResolutionPreset = nextPreset,
            stillCaptureOutputSize = nextOutputSize
        )
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
        if (blockIfCommandNotAdmitted(SessionCommandKind.PREVIEW_RATIO, "preview-ratio.blocked")) {
            return
        }
        previewRecoveryProcessor.cancelFocusExposureLockIfActive("preview ratio changed")
        val nextRatio = nextPreviewRatio(runtimeConfiguration.previewRatio)
        runtimeConfiguration = runtimeConfiguration.copy(previewRatio = nextRatio)
        val activeGraph = resolveActiveDeviceGraph(
            requestedOutputSize = _state.value.activeDeviceGraph.stillCapture.outputSize,
            requestedZoomRatio = _state.value.activeDeviceGraph.preview.zoomRatio,
            requestedPreviewStreamAspect = nextRatio.toPreviewStreamAspect()
        )
        updateState(
            previewRatio = nextRatio,
            activeDeviceGraph = activeGraph,
            lastAction = "Preview ratio set to ${nextRatio.label}",
            lastError = null
        )
        if (
            _state.value.permissionState.cameraGranted &&
            _state.value.previewHostAvailable &&
            _state.value.previewStatus in setOf(PreviewStatus.STARTING, PreviewStatus.ACTIVE, PreviewStatus.RECOVERING)
        ) {
            _effects.emit(
                SessionEffect.BindPreview(
                    modeId = currentController.id,
                    deviceGraph = activeGraph,
                    reason = "preview ratio changed to ${nextRatio.label}",
                    isRecovery = false
                )
            )
        }
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

                            is ModeIntent.ScenarioSelected ->
                                "Stop recording before changing scenario"

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
    }

    private suspend fun handleDeviceCapabilitiesUpdated(
        deviceCapabilities: DeviceCapabilities
    ) {
        if (deviceCapabilities == _state.value.activeDeviceCapabilities) {
            return
        }

        runtimeConfiguration = runtimeConfiguration.copy(deviceCapabilities = deviceCapabilities)
        currentController.onDeviceCapabilitiesChanged(deviceCapabilities)
        val clampedResolutionPreset = clampStillCaptureResolutionPreset(
            current = runtimeConfiguration.stillCaptureResolutionPreset,
            available = deviceCapabilities.availableStillCaptureResolutionPresets
        )
        val resolutionAdjusted = clampedResolutionPreset != runtimeConfiguration.stillCaptureResolutionPreset
        if (resolutionAdjusted) {
            runtimeConfiguration = runtimeConfiguration.copy(
                stillCaptureResolutionPreset = clampedResolutionPreset,
                stillCaptureOutputSize = resolvedStillCaptureOutputSizeSelection(
                    current = null,
                    available = deviceCapabilities.availableStillCaptureOutputSizes,
                    fallbackPreset = clampedResolutionPreset
                )
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
        if (_state.value.activeMode != ModeId.PHOTO && _state.value.activeMode != ModeId.HUMANISTIC) {
            updateState(lastAction = "Brightness is only available in photo or humanistic mode")
            return
        }
        if (_state.value.previewStatus != PreviewStatus.ACTIVE) {
            return
        }
        if (!_state.value.permissionState.cameraGranted || _state.value.previewHostAvailable.not()) {
            return
        }
        if (blockIfCommandNotAdmitted(SessionCommandKind.PREVIEW_BRIGHTNESS)) {
            return
        }

        val range = _state.value.activeDeviceCapabilities.previewBrightnessRange
        val clamped = range.clamp(targetSteps)
        previewRecoveryProcessor.cancelFocusExposureLockIfActive("preview brightness changed")
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
        applyDocumentBatchTransition(DocumentBatchReducer.clear(_state.value.presentation.documentBatch))
    }

    private fun handleDocumentBatchRemoveItem(itemId: String) {
        applyDocumentBatchTransition(DocumentBatchReducer.remove(_state.value.presentation.documentBatch, itemId))
    }

    private fun handleDocumentBatchMoveItem(itemId: String, direction: DocumentBatchMoveDirection) {
        applyDocumentBatchTransition(
            DocumentBatchReducer.move(_state.value.presentation.documentBatch, itemId, direction)
        )
    }

    private fun handleDocumentBatchReorder(orderedItemIds: List<String>) {
        applyDocumentBatchTransition(
            DocumentBatchReducer.reorder(_state.value.presentation.documentBatch, orderedItemIds)
        )
    }

    private fun handleDocumentBatchUpdateCropStatus(
        itemId: String,
        cropStatus: DocumentBatchCropStatus,
        cropRect: CropRect?
    ) {
        applyDocumentBatchTransition(
            DocumentBatchReducer.updateCrop(_state.value.presentation.documentBatch, itemId, cropStatus, cropRect)
        )
    }

    private fun handleDocumentBatchFinish() {
        applyDocumentBatchTransition(DocumentBatchReducer.finish(_state.value.presentation.documentBatch))
    }

    private fun applyDocumentBatchTransition(transition: DocumentBatchTransition) {
        updateState(documentBatch = transition.batch, lastAction = transition.lastAction)
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
        pendingPostprocess: PendingPostprocessUiState? = _state.value.presentation.pendingPostprocess,
        captureReadiness: com.opencamera.core.device.CaptureReadiness? = _state.value.presentation.captureReadiness,
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
                pendingPostprocess = pendingPostprocess,
                captureReadiness = captureReadiness,
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
        val currentPreviewZoom = _state.value.activeDeviceGraph.preview.previewZoomRatio
        _effects.emit(
            SessionEffect.ApplyZoomRatio(
                zoomRatio = normalizedZoomRatioValue(zoomRatio),
                previewZoomRatio = currentPreviewZoom
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
        requestedZoomRatio: Float? = baseGraph.preview.zoomRatio,
        requestedPreviewStreamAspect: PreviewStreamAspect = runtimeConfiguration.previewRatio.toPreviewStreamAspect()
    ): DeviceGraphSpec {
        val graphForResolution = if (requestedOutputSize != null) {
            baseGraph.copy(stillCapture = baseGraph.stillCapture.copy(outputSize = requestedOutputSize))
        } else {
            baseGraph
        }
        val recipe = EffectiveStillCaptureRecipe.build(graphForResolution, deviceCapabilities)
        val resolvedOutputSize = recipe.resolvedOutputSize
        val resolvedZoomRatio = resolvedZoomRatioSelection(
            current = requestedZoomRatio,
            capability = deviceCapabilities.zoomRatioCapability
        )
        val resolvedPreviewZoomRatio = computePreviewZoomRatio(
            resolvedZoomRatio,
            deviceCapabilities.zoomRatioCapability
        )
        return baseGraph.copy(
            activeLensFacing = runtimeConfiguration.lensFacing,
            preview = baseGraph.preview.copy(
                zoomRatio = resolvedZoomRatio,
                previewZoomRatio = resolvedPreviewZoomRatio,
                streamAspect = requestedPreviewStreamAspect
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
        runtimeConfiguration = runtimeConfiguration.copy(
            deviceCapabilities = deviceCapabilities,
            lensFacing = lensFacing,
            stillCaptureResolutionPreset = clampedStillCaptureResolutionPreset
        )
        return registry.createController(
            modeId = modeId,
            context = ModeContext(
                deviceCapabilities = deviceCapabilities,
                initialLensFacing = lensFacing,
                initialStillCaptureResolutionPreset = clampedStillCaptureResolutionPreset,
                runtimeState = {
                    ModeRuntimeState(
                        deviceCapabilities = runtimeConfiguration.deviceCapabilities,
                        lensFacing = runtimeConfiguration.lensFacing,
                        stillCaptureResolutionPreset = runtimeConfiguration.stillCaptureResolutionPreset,
                        stillCaptureQuality = runtimeConfiguration.stillCaptureQuality,
                        stillCaptureOutputSize = runtimeConfiguration.stillCaptureOutputSize
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
                settingsActionSink = { action ->
                    val updatedPersisted = runtimeConfiguration.settings.persisted.reduce(action)
                    handleSettingsUpdated(
                        runtimeConfiguration.settings.copy(persisted = updatedPersisted)
                    )
                },
                settingsSnapshotProvider = ::effectiveStylePreviewSettings
            )
        )
    }

    private fun defaultLensFacing(availableLensFacings: Set<LensFacing>): LensFacing {
        return SessionDevicePolicies.defaultLensFacing(availableLensFacings)
    }

    private fun nextLensFacing(
        current: LensFacing,
        available: Set<LensFacing>
    ): LensFacing {
        return SessionDevicePolicies.nextLensFacing(current, available)
    }

    private fun nextPreviewRatio(current: PreviewRatio): PreviewRatio {
        return SessionDevicePolicies.nextPreviewRatio(current)
    }

    private fun PreviewRatio.toPreviewStreamAspect(): PreviewStreamAspect =
        SessionDevicePolicies.previewStreamAspect(this)

    private fun clampStillCaptureResolutionPreset(
        current: StillCaptureResolutionPreset,
        available: Set<StillCaptureResolutionPreset>
    ): StillCaptureResolutionPreset {
        return SessionDevicePolicies.clampResolutionPreset(current, available)
    }

    private fun resolvedStillCaptureOutputSizeSelection(
        current: StillCaptureOutputSize?,
        available: List<StillCaptureOutputSize>,
        fallbackPreset: StillCaptureResolutionPreset
    ): StillCaptureOutputSize? {
        return SessionDevicePolicies.resolveOutputSizeSelection(current, available, fallbackPreset)
    }

    private fun nextStillCaptureOutputSize(
        current: StillCaptureOutputSize?,
        available: List<StillCaptureOutputSize>
    ): StillCaptureOutputSize {
        return SessionDevicePolicies.nextOutputSize(current, available)
    }

    private fun resolutionPresetForOutputSize(
        outputSize: StillCaptureOutputSize
    ): StillCaptureResolutionPreset {
        return SessionDevicePolicies.resolutionPresetFor(outputSize)
    }

    private fun nextStillCaptureResolutionPreset(
        current: StillCaptureResolutionPreset,
        available: Set<StillCaptureResolutionPreset>
    ): StillCaptureResolutionPreset {
        return SessionDevicePolicies.nextResolutionPreset(current, available)
    }

    private val LensFacing.label: String
        get() = when (this) {
            LensFacing.BACK -> "back"
            LensFacing.FRONT -> "front"
        }

}
