package com.opencamera.app.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Bitmap
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.os.SystemClock
import android.util.Log
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraState
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.opencamera.app.camera.device.CameraDeviceAdapter
import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceCommand
import com.opencamera.core.device.DeviceEvent
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.DeviceRuntimeIssueKind
import com.opencamera.core.device.DeviceShotRequest
import com.opencamera.core.device.DeviceShotRequestTranslator
import com.opencamera.core.device.DefaultDeviceShotRequestTranslator
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.LensNode
import com.opencamera.core.device.LensNodeAvailability
import com.opencamera.core.device.ManualControlCapabilityMatrix
import com.opencamera.core.device.PreviewBrightnessRange
import com.opencamera.core.device.PreviewBrightnessRequest
import com.opencamera.core.device.PreviewBrightnessResult
import com.opencamera.core.device.PreviewBrightnessResultStatus
import com.opencamera.core.device.PreviewMeteringMode
import com.opencamera.core.device.PreviewMeteringRequest
import com.opencamera.core.device.PreviewMeteringResult
import com.opencamera.core.device.PreviewMeteringResultStatus
import com.opencamera.core.device.PreviewStreamAspect
import com.opencamera.core.device.PhysicalStillCaptureOutputProbe
import com.opencamera.core.device.ManualControlSupport
import com.opencamera.core.device.MultiFrameCaptureExecutionPlanner
import com.opencamera.core.device.MultiFrameCaptureStep
import com.opencamera.core.device.RecordingQualityPreset
import com.opencamera.core.device.StillCaptureCameraProbe
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.VideoSceneSignal
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.device.filterToExecutableCapabilities
import com.opencamera.core.device.normalizedZoomRatioValue
import com.opencamera.core.device.resolveRuntimeVideoSpec
import com.opencamera.core.device.toRecordingQualityPreset
import com.opencamera.core.device.supportSummary
import com.opencamera.core.device.CameraExtensionMode
import com.opencamera.core.device.CameraExtensionResolution
import com.opencamera.core.device.CameraExtensionAvailability
import com.opencamera.core.device.ExtensionCaptureStrategy
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.ManualCaptureParams
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSpec
import com.opencamera.core.settings.VideoSpecConstraints
import com.opencamera.core.media.CompositeMediaPostProcessor
import com.opencamera.core.media.FrameBundle
import com.opencamera.core.media.FocusStackFrameRole
import com.opencamera.core.media.withSaveIoTiming
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MultiFrameFusionProcessor
import com.opencamera.core.media.PipelineMetadataPostProcessor
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotExecutor
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotPlan
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ShotTiming
import com.opencamera.core.media.primaryStillNode
import com.opencamera.core.media.primaryVideoNode
import com.opencamera.core.media.temporaryFrameNode
import com.opencamera.app.camera.live.CameraXLivePreviewFrameSource
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.ThumbnailSource
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import com.opencamera.core.session.LinkEventStatus
import com.opencamera.core.session.PerformanceLinkEvent
import com.opencamera.core.session.PerformanceLinkRecorder

private const val TAG = "CameraXCaptureAdapter"
private const val VIDEO_COVER_THUMBNAIL_SIZE = 512
private const val FOCUS_STACK_METERING_POINT_SIZE = 0.18f
private const val FOCUS_STACK_METERING_AUTO_CANCEL_MILLIS = 1_500L

internal class CameraXCaptureWorkScopes(
    private val callbackDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val postProcessScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val postProcessDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private var callbackJob: Job = SupervisorJob()
    private var callbackScope: CoroutineScope = CoroutineScope(callbackJob + callbackDispatcher)

    fun activeCallbackScope(): CoroutineScope {
        if (!callbackJob.isActive) {
            callbackJob = SupervisorJob()
            callbackScope = CoroutineScope(callbackJob + callbackDispatcher)
        }
        return callbackScope
    }

    fun cancelCallbackScope() {
        callbackJob.cancel()
    }

    fun launchPostProcess(block: suspend CoroutineScope.() -> Unit): Job {
        return postProcessScope.launch(postProcessDispatcher, block = block)
    }
}

/**
 * Resolves an extension-enabled CameraSelector for a requested extension mode and lens facing.
 * Implementations wrap CameraX ExtensionsManager calls; test fakes simulate availability.
 */
fun interface ExtensionSelectorResolver {
    fun resolve(
        desiredMode: CameraExtensionMode,
        lensFacing: LensFacing
    ): ExtensionSelectorResult
}

/**
 * Result of attempting to resolve an extension-enabled camera selector.
 * Carries both the resolution metadata and, when usable, the resolved CameraSelector.
 */
sealed interface ExtensionSelectorResult {
    /** Extension selector resolved successfully. */
    data class Resolved(
        val selector: CameraSelector,
        val resolution: CameraExtensionResolution
    ) : ExtensionSelectorResult

    /** Extension not usable; fall back to ordinary selector. */
    data class Fallback(val resolution: CameraExtensionResolution) : ExtensionSelectorResult

    /** No extension was requested. */
    data object NotRequested : ExtensionSelectorResult
}

class CameraXCaptureAdapter(
    private val context: Context,
    private val cameraProfiles: List<CameraLensProfile> = detectCameraLensProfiles(context),
    private val baseCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
    override val capabilities: DeviceCapabilities = resolveDeviceCapabilities(
        baseCapabilities = baseCapabilities,
        cameraProfiles = cameraProfiles
    ),
    private val shotExecutor: ShotExecutor = ShotExecutor(),
    private val shotRequestTranslatorFactory: (DeviceCapabilities) -> DeviceShotRequestTranslator =
        { resolvedCapabilities -> DefaultDeviceShotRequestTranslator(resolvedCapabilities) },
    private val multiFrameExecutionPlanner: MultiFrameCaptureExecutionPlanner =
        MultiFrameCaptureExecutionPlanner(),
    private val videoSceneSignalProvider: (DeviceGraphSpec) -> VideoSceneSignal =
        { VideoSceneSignal() },
    private val mediaPostProcessor: MediaPostProcessor = CompositeMediaPostProcessor(
        listOf(
            AndroidFocusStackFusionProcessor(context),
            MultiFrameFusionProcessor(),
            PipelineMetadataPostProcessor()
        )
    ),
    private val livePreviewFrameSource: com.opencamera.app.camera.live.LivePreviewFrameSource? = null,
    private val linkRecorder: com.opencamera.core.session.PerformanceLinkRecorder? = null,
    private val extensionSelectorResolver: ExtensionSelectorResolver? = null,
    private val sceneMaskSource: PreviewSceneMaskSource? = null,
    private val captureOutputFactory: CaptureOutputFactory = CaptureOutputFactory(context),
    postProcessScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : CameraDeviceAdapter {
    private val stillCaptureExecutor = StillCaptureExecutor(
        context = context,
        captureOutputFactory = captureOutputFactory,
        multiFrameExecutionPlanner = multiFrameExecutionPlanner
    )
    private val workScopes = CameraXCaptureWorkScopes(
        postProcessScope = postProcessScope
    )
    @Volatile private var captureCommittedArmedShotId: String? = null
    @Volatile private var captureCommittedArmedMediaType: MediaType? = null
    @Volatile private var captureCommittedElapsedMs: Long? = null
    private val sessionCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
            val intent = result.get(CaptureResult.CONTROL_CAPTURE_INTENT)
            if (intent != CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE) return
            val shotId = captureCommittedArmedShotId ?: return
            val mediaType = captureCommittedArmedMediaType ?: return
            val elapsed = SystemClock.elapsedRealtime()
            captureCommittedElapsedMs = elapsed
            _events.tryEmit(
                DeviceEvent.CaptureCommitted(
                    shotId = shotId,
                    mediaType = mediaType,
                    source = "Camera2CaptureSession.onCaptureCompleted",
                    elapsedTimestampMs = elapsed
                )
            )
        }
    }
    private var activeRecording: Recording? = null
    private val recordingController = VideoRecordingController(
        isAudioPermissionGranted = ::hasAudioPermission,
        onTorchChange = ::applyVideoTorchHardware,
        qualityTrackerStart = { fps -> videoRecordingQualityTracker.startRecording(fps) },
        qualityTrackerStop = { videoRecordingQualityTracker.stopRecording() }
    )
    private val previewFpsTracker = com.opencamera.core.media.PreviewFpsTracker()
    val videoRecordingQualityTracker = com.opencamera.core.media.VideoRecordingQualityTracker()
    private val _bindingController = CameraBindingController(
        context = context,
        capabilities = capabilities,
        cameraProfiles = cameraProfiles,
        emitEvent = { _events.tryEmit(it) },
        extensionSelectorResolver = extensionSelectorResolver,
        recordingController = recordingController,
        adapterCallbacks = AdapterBindingCallbacks(
            onImageCaptureChanged = {},
            onVideoCaptureChanged = {},
            onBoundCameraChanged = {},
            onPreviewFpsFrame = { previewFpsTracker.recordFrame(it) },
            onVideoQualityTrackerFrame = { videoRecordingQualityTracker.recordFrame(it) },
            sessionCaptureCallback = sessionCaptureCallback,
            livePreviewFrameSource = livePreviewFrameSource as? CameraXLivePreviewFrameSource
        )
    )

    fun runtimeMetricsSnapshot(): com.opencamera.core.media.RuntimeMetricsSnapshot {
        return com.opencamera.core.media.RuntimeMetricsSnapshot(
            previewFps = previewFpsTracker.toSnapshot(),
            algorithmQueue = com.opencamera.core.media.queryAlgorithmQueueSnapshot(
                com.opencamera.core.media.CameraResourceBudget()
            ),
            videoRecordingQuality = videoRecordingQualityTracker.toSnapshot(),
            memoryPressure = com.opencamera.core.media.MemoryPressureSnapshot.sample(
                com.opencamera.core.media.CameraResourceBudget()
            )
        )
    }
    val currentExtensionResolution: CameraExtensionResolution?
        get() = _bindingController.currentSnapshot().extensionResolution
    private val _events = MutableSharedFlow<DeviceEvent>(extraBufferCapacity = 16)

    override val events = _events.asSharedFlow()

    override fun capabilitiesFor(deviceGraph: DeviceGraphSpec): DeviceCapabilities {
        return resolveDeviceCapabilities(
            baseCapabilities = capabilities,
            cameraProfiles = cameraProfiles,
            preferredLensFacing = deviceGraph.preferredLensFacing
        )
    }

    override suspend fun bindUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        deviceGraph: DeviceGraphSpec
    ) {
        workScopes.activeCallbackScope()
        runCatching {
            _bindingController.bind(lifecycleOwner, previewView, deviceGraph)
        }.getOrElse { throwable ->
            _bindingController.invalidateCachedProviderState(
                classifyPreviewBindingFailure(throwable)
            )
            throw throwable
        }
    }

    override suspend fun dispatch(command: DeviceCommand) {
        when (command) {
            is DeviceCommand.ExecuteShot -> runCatching {
                executeShot(command.plan)
            }.getOrElse { throwable ->
                emitShotFailure(
                    shotId = command.plan.request.shotId,
                    mediaType = command.plan.request.mediaType,
                    reason = throwable.message ?: "Shot execution failed"
                )
            }

            is DeviceCommand.StopActiveShot -> runCatching {
                stopActiveShot(command.shotId)
            }.getOrElse { throwable ->
                val activePlan = recordingController.activePlan()
                recordingController.stopRecording()
                emitShotFailure(
                    shotId = activePlan?.request?.shotId ?: command.shotId,
                    mediaType = activePlan?.request?.mediaType ?: MediaType.VIDEO,
                    reason = throwable.message ?: "Stop video command failed"
                )
            }

            is DeviceCommand.UpdateZoomRatio -> runCatching {
                _bindingController.updateZoomRatio(command.zoomRatio, command.previewZoomRatio)
            }.onFailure { throwable ->
                val issue = classifyPreviewBindingFailure(throwable)
                _bindingController.invalidateCachedProviderState(issue)
                _events.emit(
                    DeviceEvent.RuntimeIssue(
                        issue
                    )
                )
            }

            is DeviceCommand.SwitchLensNode -> runCatching {
                _bindingController.switchLensNode(command.lensNode, command.reason)
            }.onFailure { throwable ->
                val issue = classifyPreviewBindingFailure(throwable)
                _bindingController.invalidateCachedProviderState(issue)
                _events.emit(
                    DeviceEvent.RuntimeIssue(
                        issue
                    )
                )
            }

            is DeviceCommand.ApplyPreviewMetering -> runCatching {
                _bindingController.applyPreviewMetering(command.request)
            }.onFailure { throwable ->
                _events.emit(
                    DeviceEvent.PreviewMeteringCompleted(
                        PreviewMeteringResult(
                            requestId = command.request.requestId,
                            point = command.request.point.clamped(),
                            status = PreviewMeteringResultStatus.FAILED,
                            reason = throwable.message ?: "Preview metering failed"
                        )
                    )
                )
            }
            is DeviceCommand.UpdateOutputRotation -> runCatching {
                _bindingController.applyOutputRotation(command.rotation)
            }.onFailure { throwable ->
                _events.emit(
                    DeviceEvent.RuntimeIssue(
                        DeviceRuntimeIssue(
                            kind = DeviceRuntimeIssueKind.CAMERA_RECOVERABLE,
                            reason = "Failed to update output rotation: ${throwable.message}",
                            isRecoverable = true
                        )
                    )
                )
            }

            is DeviceCommand.ApplyPreviewBrightness -> runCatching {
                _bindingController.applyPreviewBrightness(command.request)
            }.onFailure { throwable ->
                _events.emit(
                    DeviceEvent.PreviewBrightnessApplied(
                        PreviewBrightnessResult(
                            requestId = command.request.requestId,
                            exposureCompensationSteps = command.request.exposureCompensationSteps,
                            status = PreviewBrightnessResultStatus.FAILED,
                            reason = throwable.message ?: "Preview brightness failed"
                        )
                    )
                )
            }
        }
    }

    private suspend fun applyOutputRotation(rotation: com.opencamera.core.device.CameraOutputRotation) {
        _bindingController.applyOutputRotation(rotation)
    }

    override suspend fun release() {
        withContext(Dispatchers.Main.immediate) {
            workScopes.cancelCallbackScope()
            if (activeRecording != null) {
                recordingController.release()
                recordingController.outcomes.toList().forEach { outcome ->
                    when (outcome) {
                        is RecordingOutcome.Released -> _events.tryEmit(
                            DeviceEvent.ShotFailed(
                                shotId = outcome.shotId,
                                mediaType = outcome.mediaType,
                                reason = "Recording interrupted by lifecycle stop"
                            )
                        )
                        else -> {}
                    }
                }
                recordingController.outcomes.clear()
                applyVideoTorchHardware(false)
                activeRecording?.close()
                activeRecording = null
            }
            _bindingController.release()
        }
    }

    private suspend fun updateZoomRatio(zoomRatio: Float, previewZoomRatio: Float) {
        _bindingController.updateZoomRatio(zoomRatio, previewZoomRatio)
    }

    override fun boundGraph(): DeviceGraphSpec? = _bindingController.currentGraph

    private suspend fun executeShot(plan: ShotPlan) {
        val requestedAt = SystemClock.elapsedRealtime()
        val currentGraph = _bindingController.currentGraph
        val resolvedCapabilities = currentGraph
            ?.let(::capabilitiesFor)
            ?: capabilities
        val deviceRequest = shotRequestTranslatorFactory(resolvedCapabilities).translate(plan)
        val graph = plan.graph
        when {
            graph.primaryVideoNode() != null -> startVideoRecording(plan, deviceRequest, requestedAt)
            graph.temporaryFrameNode() != null -> captureStillImage(plan, deviceRequest, requestedAt)
            graph.primaryStillNode() != null -> captureStillImage(plan, deviceRequest, requestedAt)
            else -> error("ShotGraph has no executable capture node for ${plan.request.shotId}")
        }
    }

    private data class ShotCompletedParams(
        val plan: ShotPlan,
        val outputPath: String,
        val outputHandle: MediaOutputHandle,
        val livePhotoBundle: LivePhotoBundle?,
        val frameBundle: FrameBundle?,
        val intermediateOutputPaths: List<String>,
        val deviceDiagnostics: List<String>,
        val requestedAtElapsedMillis: Long,
        val deviceCaptureStartedAtElapsedMillis: Long,
        val deviceCaptureCompletedAtElapsedMillis: Long
    )

    private suspend fun captureStillImage(
        plan: ShotPlan,
        deviceRequest: DeviceShotRequest,
        requestedAt: Long
    ) {
        ensureStillCaptureRequest(deviceRequest)
        val capture = _bindingController.currentImageCapture ?: error("ImageCapture is not bound")
        stillCaptureExecutor.applyFlashMode(capture, deviceRequest.flashMode)
        _events.emit(DeviceEvent.ShotStarted(plan.request))
        captureCaptureFeedbackSnapshot(plan.request.shotId)
        val adapterManualDiagnostics = manualCaptureExecutionDiagnostics(
            requestedParams = deviceRequest.manualCaptureParams,
            capabilityMatrix = deviceRequest.manualControlCapabilities
        )

        if (plan.request.shotKind == ShotKind.STILL_CAPTURE) {
            captureCommittedArmedShotId = plan.request.shotId
            captureCommittedArmedMediaType = plan.request.mediaType
        }

        val execution = when (plan.request.shotKind) {
            ShotKind.STILL_CAPTURE -> {
                val request = captureOutputFactory.createPhotoOutputRequest(plan.saveTask.saveRequest)
                stillCaptureExecutor.captureSinglePhoto(capture, request)
            }

            ShotKind.MULTI_FRAME_CAPTURE -> {
                stillCaptureExecutor.captureMultiFrame(
                    capture = capture,
                    plan = plan,
                    deviceRequest = deviceRequest,
                    beforeFrameCapture = { step ->
                        applyFocusStackFramePreparation(step)
                    }
                )
            }

            ShotKind.LIVE_PHOTO -> stillCaptureExecutor.captureLivePhoto(
                capture = capture,
                plan = plan,
                livePreviewFrameSource = livePreviewFrameSource,
                writeContentUriPayload = ::writeTextToContentUri,
                deleteContentUri = ::deleteContentUriQuietly
            )

            ShotKind.VIDEO_RECORDING -> error("Video shots are not handled by still capture path")
        }

        when (execution) {
            is PhotoCaptureOutcome.Failure -> {
                captureCommittedArmedShotId = null
                captureCommittedArmedMediaType = null
                captureCommittedElapsedMs = null
                cleanupAbsoluteFilePaths(execution.cleanupPaths)
                emitShotFailure(
                    shotId = plan.request.shotId,
                    mediaType = plan.request.mediaType,
                    reason = execution.reason
                )
                return
            }

            is PhotoCaptureOutcome.Success -> {
                val committedElapsed = captureCommittedElapsedMs
                captureCommittedArmedShotId = null
                captureCommittedArmedMediaType = null
                captureCommittedElapsedMs = null
                _events.emit(DeviceEvent.DataReceived(
                    shotId = plan.request.shotId,
                    mediaType = plan.request.mediaType
                ))
                val captureTimingDiagnostics = buildList {
                    add("timing:requested-at-elapsed=$requestedAt")
                    if (committedElapsed != null) {
                        add("timing:capture-committed-at-elapsed=$committedElapsed")
                        add("timing:request-to-committed-ms=${committedElapsed - requestedAt}")
                    }
                }
                val shotCompletedParams = ShotCompletedParams(
                    plan = plan,
                    outputPath = execution.outputPath,
                    outputHandle = execution.outputHandle,
                    livePhotoBundle = execution.livePhotoBundle,
                    frameBundle = execution.frameBundle,
                    intermediateOutputPaths = execution.intermediateOutputPaths,
                    deviceDiagnostics = deviceRequest.diagnostics +
                        adapterManualDiagnostics +
                        captureTimingDiagnostics +
                        execution.diagnostics,
                    requestedAtElapsedMillis = requestedAt,
                    deviceCaptureStartedAtElapsedMillis = execution.deviceCaptureStartedAtElapsedMillis,
                    deviceCaptureCompletedAtElapsedMillis = execution.deviceCaptureCompletedAtElapsedMillis
                )
                if (plan.request.shotKind == ShotKind.STILL_CAPTURE ||
                    plan.request.shotKind == ShotKind.LIVE_PHOTO
                ) {
                    // Re-armable still captures: postprocess and ShotCompleted delivery
                    // run off the critical path so the session can re-arm the shutter
                    // after DataReceived without waiting for postprocess.
                    // Move postprocess off Dispatchers.Main.immediate to avoid main-thread
                    // contention during UI-heavy capture feedback (P6 latency phase).
                    workScopes.launchPostProcess {
                        runCatching {
                            emitShotCompleted(
                                plan = shotCompletedParams.plan,
                                outputPath = shotCompletedParams.outputPath,
                                outputHandle = shotCompletedParams.outputHandle,
                                livePhotoBundle = shotCompletedParams.livePhotoBundle,
                                frameBundle = shotCompletedParams.frameBundle,
                                intermediateOutputPaths = shotCompletedParams.intermediateOutputPaths,
                                deviceDiagnostics = shotCompletedParams.deviceDiagnostics,
                                requestedAtElapsedMillis = shotCompletedParams.requestedAtElapsedMillis,
                                deviceCaptureStartedAtElapsedMillis = shotCompletedParams.deviceCaptureStartedAtElapsedMillis,
                                deviceCaptureCompletedAtElapsedMillis = shotCompletedParams.deviceCaptureCompletedAtElapsedMillis
                            )
                        }.getOrElse { throwable ->
                            cleanupStillCaptureArtifacts(
                                outputPath = shotCompletedParams.outputPath,
                                outputHandle = shotCompletedParams.outputHandle,
                                livePhotoBundle = shotCompletedParams.livePhotoBundle,
                                intermediateOutputPaths = shotCompletedParams.intermediateOutputPaths,
                                deleteContentUri = ::deleteContentUriQuietly
                            )
                            emitShotFailure(
                                shotId = plan.request.shotId,
                                mediaType = plan.request.mediaType,
                                reason = "Postprocess failed: ${throwable.message}"
                            )
                        }
                    }
                } else {
                    // Multi-frame and live photo: keep conservative synchronous
                    // semantics until real-device evidence proves otherwise.
                    runCatching {
                        emitShotCompleted(
                            plan = shotCompletedParams.plan,
                            outputPath = shotCompletedParams.outputPath,
                            outputHandle = shotCompletedParams.outputHandle,
                            livePhotoBundle = shotCompletedParams.livePhotoBundle,
                            frameBundle = shotCompletedParams.frameBundle,
                            intermediateOutputPaths = shotCompletedParams.intermediateOutputPaths,
                            deviceDiagnostics = shotCompletedParams.deviceDiagnostics,
                            requestedAtElapsedMillis = shotCompletedParams.requestedAtElapsedMillis,
                            deviceCaptureStartedAtElapsedMillis = shotCompletedParams.deviceCaptureStartedAtElapsedMillis,
                            deviceCaptureCompletedAtElapsedMillis = shotCompletedParams.deviceCaptureCompletedAtElapsedMillis
                        )
                    }.getOrElse { throwable ->
                        cleanupStillCaptureArtifacts(
                            outputPath = shotCompletedParams.outputPath,
                            outputHandle = shotCompletedParams.outputHandle,
                            livePhotoBundle = shotCompletedParams.livePhotoBundle,
                            intermediateOutputPaths = shotCompletedParams.intermediateOutputPaths,
                            deleteContentUri = ::deleteContentUriQuietly
                        )
                        throw throwable
                    }
                }
            }
        }
    }

    private suspend fun applyFocusStackFramePreparation(
        step: MultiFrameCaptureStep
    ): List<String> {
        val role = step.focusStackRole
        if (role == FocusStackFrameRole.NONE) return emptyList()

        return withContext(Dispatchers.Main.immediate) {
            val camera = _bindingController.currentBoundCamera
            val previewView = _bindingController.currentBoundPreviewView
            if (camera == null || previewView == null || previewView.width <= 0 || previewView.height <= 0) {
                return@withContext listOf(
                    "device:focus-stack-metering=${role.name.lowercase()}:failed-preview-unbound"
                )
            }

            val normalized = when (role) {
                FocusStackFrameRole.NEAR -> 0.50f to 0.58f
                FocusStackFrameRole.FAR -> 0.50f to 0.28f
                FocusStackFrameRole.MID -> 0.50f to 0.45f
                FocusStackFrameRole.NONE -> 0.50f to 0.50f
            }
            val meteringPoint = previewView.meteringPointFactory.createPoint(
                previewView.width * normalized.first,
                previewView.height * normalized.second,
                FOCUS_STACK_METERING_POINT_SIZE
            )
            val focusAndAeAction = FocusMeteringAction.Builder(
                meteringPoint,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(FOCUS_STACK_METERING_AUTO_CANCEL_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            val aeOnlyAction = FocusMeteringAction.Builder(
                meteringPoint,
                FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(FOCUS_STACK_METERING_AUTO_CANCEL_MILLIS, TimeUnit.MILLISECONDS)
                .build()

            val selected = when {
                camera.cameraInfo.isFocusMeteringSupported(focusAndAeAction) ->
                    focusAndAeAction to "af-ae"
                camera.cameraInfo.isFocusMeteringSupported(aeOnlyAction) ->
                    aeOnlyAction to "ae-only"
                else -> null
            }
                ?: return@withContext listOf(
                    "device:focus-stack-metering=${role.name.lowercase()}:unsupported"
                )

            runCatching {
                camera.cameraControl.startFocusAndMetering(selected.first).await()
            }.fold(
                onSuccess = { result ->
                    val status = if (selected.second == "af-ae" && !result.isFocusSuccessful) {
                        "af-not-confirmed"
                    } else {
                        selected.second
                    }
                    listOf(
                        "device:focus-stack-metering=${role.name.lowercase()}:$status",
                        "device:focus-stack-metering-point=${role.name.lowercase()}:${normalized.first},${normalized.second}"
                    )
                },
                onFailure = { throwable ->
                    listOf(
                        "device:focus-stack-metering=${role.name.lowercase()}:failed:${throwable.message ?: "unknown"}"
                    )
                }
            )
        }
    }

    private suspend fun emitShotCompleted(
        plan: ShotPlan,
        outputPath: String,
        outputHandle: MediaOutputHandle = MediaOutputHandle(displayPath = outputPath),
        livePhotoBundle: LivePhotoBundle? = null,
        frameBundle: FrameBundle? = null,
        intermediateOutputPaths: List<String> = emptyList(),
        deviceDiagnostics: List<String> = emptyList(),
        requestedAtElapsedMillis: Long = 0L,
        deviceCaptureStartedAtElapsedMillis: Long = 0L,
        deviceCaptureCompletedAtElapsedMillis: Long = 0L
    ) {
        val rawResult = shotExecutor.resultFor(
            saveTask = plan.saveTask,
            outputPath = outputPath,
            outputHandle = outputHandle,
            livePhotoBundle = livePhotoBundle,
            frameBundle = frameBundle,
            intermediateOutputPaths = intermediateOutputPaths
        ).copy(
            pipelineNotes = deviceDiagnostics,
            timing = ShotTiming(
                requestedAtElapsedMillis = requestedAtElapsedMillis,
                deviceCaptureStartedAtElapsedMillis = deviceCaptureStartedAtElapsedMillis,
                deviceCaptureCompletedAtElapsedMillis = deviceCaptureCompletedAtElapsedMillis
            )
        )
        val processedResult = mediaPostProcessor.process(rawResult)
        val postProcessCompletedAt = SystemClock.elapsedRealtime()
        val timedResult = processedResult.copy(
            timing = processedResult.timing.copy(
                postProcessCompletedAtElapsedMillis = postProcessCompletedAt
            )
        )
        val t = timedResult.timing
        val deviceStarted = t.deviceCaptureStartedAtElapsedMillis
        val deviceCompleted = t.deviceCaptureCompletedAtElapsedMillis
        val requested = t.requestedAtElapsedMillis
        val timingNotes = buildList {
            if (deviceStarted != null && deviceCompleted != null && deviceStarted > 0 && deviceCompleted > 0) {
                add("timing:device=${deviceCompleted - deviceStarted}ms")
                add("timing:save-io=${deviceCompleted - deviceStarted}ms")
            }
            if (deviceCompleted != null && deviceCompleted > 0) {
                add("timing:postprocess=${postProcessCompletedAt - deviceCompleted}ms")
            }
            if (requested != null && requested > 0) {
                add("timing:total=${postProcessCompletedAt - requested}ms")
            }
        }
        val resultWithTiming = timedResult.copy(
            pipelineNotes = timedResult.pipelineNotes + timingNotes
        )
        val saveIoResult = if (deviceStarted != null && deviceCompleted != null && deviceStarted > 0 && deviceCompleted > 0) {
            val resultWithSaveIo = resultWithTiming.withSaveIoTiming(deviceStarted, deviceCompleted)
            linkRecorder?.recordEvent(PerformanceLinkEvent(
                flow = "save",
                stage = "file-io",
                status = LinkEventStatus.COMPLETED,
                correlationId = plan.request.shotId,
                startElapsedMillis = deviceStarted,
                endElapsedMillis = deviceCompleted,
                durationMillis = deviceCompleted - deviceStarted,
                detail = plan.request.mediaType.name.lowercase(),
                source = "CameraXCaptureAdapter"
            ))
            resultWithSaveIo
        } else {
            resultWithTiming
        }
        _events.emit(DeviceEvent.ShotCompleted(saveIoResult))
    }

    private suspend fun emitShotFailure(
        shotId: String,
        mediaType: MediaType,
        reason: String
    ) {
        withContext(Dispatchers.Main.immediate) {
            activeRecording?.close()
            activeRecording = null
        }
        _events.emit(
            DeviceEvent.ShotFailed(
                shotId = shotId,
                mediaType = mediaType,
                reason = reason
            )
        )
    }

    private fun writeTextToContentUri(
        contentUri: String,
        payload: String
    ) {
        val uri = Uri.parse(contentUri)
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(payload)
        } ?: error("Failed to open output stream for $contentUri")
    }

    private fun deleteContentUriQuietly(contentUri: String) {
        runCatching {
            context.contentResolver.delete(Uri.parse(contentUri), null, null)
        }
    }

    private suspend fun startVideoRecording(
        plan: ShotPlan,
        deviceRequest: DeviceShotRequest,
        requestedAt: Long
    ) {
        val currentVideoGraph = _bindingController.currentGraph ?: error("Video graph is not bound")
        val runtimeSceneSignal = videoSceneSignalProvider(currentVideoGraph)
        val runtimeVideoSpec = capabilitiesFor(currentVideoGraph).resolveRuntimeVideoSpec(
            base = currentVideoGraph.recording.videoSpec,
            sceneSignal = runtimeSceneSignal
        )
        recordingController.startRecording(
            plan = plan,
            expectedFrameRate = runtimeVideoSpec.frameRate.storageKey.toIntOrNull()
        )
        ensureVideoRecordingRequest(runtimeVideoSpec)
        val capture = _bindingController.currentVideoCapture ?: error("VideoCapture is not bound")
        val runtimeDiagnostics = buildList {
            if (runtimeSceneSignal.isLowLight) {
                add("device:video-scene=low-light")
            }
            if (runtimeVideoSpec.frameRate != currentVideoGraph.recording.videoSpec.frameRate) {
                add("device:video-runtime-fps=${runtimeVideoSpec.frameRate.storageKey}")
            }
            add("device:video-bound-fps=${runtimeVideoSpec.frameRate.storageKey}")
        }

        val request = captureOutputFactory.createVideoOutputRequest(plan.saveTask.saveRequest)
        withContext(Dispatchers.Main.immediate) {
            var pending: PendingRecording = when (request) {
                is VideoOutputRequest.FileRequest -> capture.output.prepareRecording(
                    context,
                    request.outputOptions
                )

                is VideoOutputRequest.MediaStoreRequest -> capture.output.prepareRecording(
                    context,
                    request.outputOptions
                )
            }
            if (_bindingController.currentGraph?.recording?.audioEnabledWhenPermitted == true && recordingController.hasAudioPermission()) {
                pending = pending.withAudioEnabled()
            }
            recordingController.applyTorch(deviceRequest.torchEnabled)

            activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        val recordingStartedAt = SystemClock.elapsedRealtime()
                        linkRecorder?.recordEvent(PerformanceLinkEvent(
                            flow = "recording",
                            stage = "start",
                            status = LinkEventStatus.COMPLETED,
                            correlationId = plan.request.shotId,
                            startElapsedMillis = requestedAt,
                            endElapsedMillis = recordingStartedAt,
                            durationMillis = recordingStartedAt - requestedAt,
                            detail = null,
                            source = "CameraXCaptureAdapter"
                        ))
                        recordingController.handleEvent(RecordingControllerEvent.Started)
                        _events.tryEmit(DeviceEvent.ShotStarted(plan.request))
                    }

                    is VideoRecordEvent.Finalize -> {
                        if (event.hasError()) {
                            Log.e(
                                TAG,
                                "Video finalize failed: shotId=${plan.request.shotId} error=${event.error}",
                                event.cause
                            )
                        }
                        recordingController.handleEvent(RecordingControllerEvent.Finalized(
                            hasError = event.hasError(),
                            errorMessage = event.cause?.message ?: event.error.toString(),
                            outputUri = event.outputResults.outputUri?.toString()
                        ))
                        activeRecording = null

                        workScopes.activeCallbackScope().launch {
                            if (!isActive) return@launch
                            recordingController.outcomes.toList().let { recentOutcomes ->
                                recordingController.outcomes.clear()
                                recentOutcomes.forEach { outcome ->
                                    when (outcome) {
                                        is RecordingOutcome.FinalizeError -> emitShotFailure(
                                            shotId = outcome.shotId,
                                            mediaType = outcome.mediaType,
                                            reason = outcome.errorMessage
                                        )
                                        is RecordingOutcome.FinalizeSuccess -> {
                                            val outputHandle = request.resolveOutputHandle(
                                                event.outputResults.outputUri
                                            )
                                            val thumbnailDiagnostics = withContext(Dispatchers.IO) {
                                                warmVideoThumbnailForGallery(
                                                    outputHandle = outputHandle,
                                                    mimeType = plan.saveTask.saveRequest.mimeType
                                                )
                                            }
                                            emitShotCompleted(
                                                plan = outcome.plan,
                                                outputPath = request.outputPath,
                                                outputHandle = outputHandle,
                                                deviceDiagnostics = deviceRequest.diagnostics +
                                                    runtimeDiagnostics +
                                                    thumbnailDiagnostics,
                                                requestedAtElapsedMillis = requestedAt
                                            )
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun warmVideoThumbnailForGallery(
        outputHandle: MediaOutputHandle,
        mimeType: String
    ): List<String> {
        val diagnostics = mutableListOf<String>()
        val contentUri = outputHandle.contentUri?.takeUnless { it.isBlank() }
        if (contentUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val warmed = runCatching {
                context.contentResolver.loadThumbnail(
                    Uri.parse(contentUri),
                    Size(VIDEO_COVER_THUMBNAIL_SIZE, VIDEO_COVER_THUMBNAIL_SIZE),
                    null
                ).recycle()
            }
            diagnostics += warmed.fold(
                onSuccess = { "video-thumbnail:media-store-warmed" },
                onFailure = { throwable ->
                    val reason = throwable.message ?: throwable::class.java.simpleName
                    "video-thumbnail:media-store-warm-failed:$reason"
                }
            )
        }

        val filePath = outputHandle.filePath?.takeUnless { it.isBlank() }
        if (filePath != null) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(filePath),
                arrayOf(mimeType),
                null
            )
            diagnostics += "video-thumbnail:media-scan-requested"
        }
        return diagnostics.ifEmpty { listOf("video-thumbnail:no-warmup-target") }
    }

    private suspend fun stopActiveShot(shotId: String) {
        withContext(Dispatchers.Main.immediate) {
            val recording = activeRecording ?: error("No active recording to stop")
            val plan = recordingController.activePlan() ?: error("No active video shot plan")
            check(plan.request.shotId == shotId) {
                "Active recording ${plan.request.shotId} does not match stop request $shotId"
            }
            recording.stop()
            recordingController.stopRecording()
        }
    }

    private suspend fun applyVideoTorchHardware(enabled: Boolean) {
        withContext(Dispatchers.Main.immediate) {
            val camera = _bindingController.currentBoundCamera ?: return@withContext
            camera.cameraControl.enableTorch(enabled).await()
        }
    }

    private suspend fun applyVideoTorch(enabled: Boolean) {
        recordingController.applyTorch(enabled)
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private suspend fun ensureStillCaptureRequest(deviceRequest: DeviceShotRequest) {
        check(deviceRequest.template == CaptureTemplate.STILL_CAPTURE) {
            "Expected still capture request but was ${deviceRequest.template}"
        }
        val deviceGraph = _bindingController.currentGraph ?: error("Device graph is not bound")
        check(deviceGraph.template == CaptureTemplate.STILL_CAPTURE) {
            "Cannot apply still capture request while bound graph is ${deviceGraph.template}"
        }
        val requestedQuality = resolvedStillCaptureQuality(
            deviceGraph = deviceGraph,
            deviceRequest = deviceRequest
        )
        val requestedResolutionPreset = resolvedStillCaptureResolutionPreset(deviceGraph)
        val requestedOutputSize = resolvedStillCaptureOutputSize(
            deviceGraph = deviceGraph,
            availableOutputSizes = capabilitiesFor(deviceGraph).availableStillCaptureOutputSizes
        )
        val requestedManualCaptureConfig = resolveCamera2ManualCaptureConfig(deviceRequest)
        if (_bindingController.ensureStillCaptureRequestConfigChanged(
                requestedQuality,
                requestedResolutionPreset,
                requestedOutputSize,
                requestedManualCaptureConfig
            )
        ) {
            return
        }

        withContext(Dispatchers.Main.immediate) {
            _bindingController.rebindForStillCapture(
                lifecycleOwner = _bindingController.currentLifecycleOwner
                    ?: error("LifecycleOwner is not attached"),
                previewView = _bindingController.currentBoundPreviewView
                    ?: error("PreviewView is not attached"),
                deviceGraph = deviceGraph,
                stillCaptureQuality = requestedQuality,
                stillCaptureResolutionPreset = requestedResolutionPreset,
                manualCaptureConfigOverride = requestedManualCaptureConfig
            )
        }
    }

    private suspend fun ensureVideoRecordingRequest(runtimeVideoSpec: VideoSpec) {
        val deviceGraph = _bindingController.currentGraph ?: error("Device graph is not bound")
        check(deviceGraph.template == CaptureTemplate.VIDEO_RECORDING) {
            "Cannot apply video recording request while bound graph is ${deviceGraph.template}"
        }
        if (_bindingController.ensureVideoRecordingRequestConfigChanged(runtimeVideoSpec)) {
            return
        }

        withContext(Dispatchers.Main.immediate) {
            _bindingController.rebindForVideoRecording(
                lifecycleOwner = _bindingController.currentLifecycleOwner
                    ?: error("LifecycleOwner is not attached"),
                previewView = _bindingController.currentBoundPreviewView
                    ?: error("PreviewView is not attached"),
                deviceGraph = deviceGraph,
                videoSpecOverride = runtimeVideoSpec
            )
        }
    }

    private fun captureCaptureFeedbackSnapshot(shotId: String) {
        val previewView = _bindingController.currentBoundPreviewView ?: return
        workScopes.activeCallbackScope().launch {
            if (!isActive) return@launch
            val bitmap = awaitPreviewBitmap(previewView) ?: return@launch
            try {
                runCatching {
                    saveCaptureFeedbackBitmap(shotId, bitmap)
                }.onSuccess { outputPath ->
                    _events.emit(
                        DeviceEvent.CaptureFeedbackSnapshotAvailable(
                            shotId = shotId,
                            outputPath = outputPath
                        )
                    )
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private suspend fun saveCaptureFeedbackBitmap(shotId: String, bitmap: Bitmap): String {
        return withContext(Dispatchers.IO) {
            val outputDir = File(context.cacheDir, "capture-feedback").apply {
                mkdirs()
            }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val outputFile = File(outputDir, "feedback_${shotId}_$stamp.jpg")
            FileOutputStream(outputFile).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                    "Capture feedback snapshot compression failed"
                }
            }
            outputDir.listFiles { file ->
                file.isFile && file.name.startsWith("feedback_") && file.name.endsWith(".jpg")
            }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(5)
                ?.forEach { it.delete() }
            outputFile.absolutePath
        }
    }

    private fun capturePreviewSnapshot() = Unit

    private suspend fun awaitPreviewBitmap(previewView: PreviewView): Bitmap? {
        repeat(4) { attempt ->
            val bitmap = withContext(Dispatchers.Main.immediate) {
                previewView.bitmap?.copy(Bitmap.Config.ARGB_8888, false)
            }
            if (bitmap != null) {
                return bitmap
            }
            if (attempt < 3) {
                delay(48)
            }
        }
        return null
    }

}

internal fun resolvePhotoOutputHandle(
    outputHandle: MediaOutputHandle,
    savedUriString: String?
): MediaOutputHandle {
    val resolvedContentUri = savedUriString?.takeIf { it.isNotBlank() }
    return if (resolvedContentUri == null) {
        outputHandle
    } else {
        outputHandle.copy(contentUri = resolvedContentUri)
    }
}

internal fun mapOutputRotationToSurface(
    rotation: com.opencamera.core.device.CameraOutputRotation
): Int = when (rotation) {
    com.opencamera.core.device.CameraOutputRotation.ROTATION_0 -> Surface.ROTATION_0
    com.opencamera.core.device.CameraOutputRotation.ROTATION_90 -> Surface.ROTATION_90
    com.opencamera.core.device.CameraOutputRotation.ROTATION_180 -> Surface.ROTATION_180
    com.opencamera.core.device.CameraOutputRotation.ROTATION_270 -> Surface.ROTATION_270
}
