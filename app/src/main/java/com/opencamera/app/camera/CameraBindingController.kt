package com.opencamera.app.camera

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraState
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.FocusMeteringAction
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.opencamera.app.camera.live.CameraXLivePreviewFrameSource
import com.opencamera.core.device.CameraExtensionAvailability
import com.opencamera.core.device.CameraExtensionMode
import com.opencamera.core.device.CameraExtensionResolution
import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.DeviceRuntimeIssueKind
import com.opencamera.core.device.ExtensionCaptureStrategy
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.LensNode
import com.opencamera.core.device.PreviewBrightnessRequest
import com.opencamera.core.device.PreviewBrightnessResult
import com.opencamera.core.device.PreviewBrightnessResultStatus
import com.opencamera.core.device.PreviewMeteringMode
import com.opencamera.core.device.PreviewMeteringRequest
import com.opencamera.core.device.PreviewMeteringResult
import com.opencamera.core.device.PreviewMeteringResultStatus
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.device.toRecordingQualityPreset
import com.opencamera.core.settings.VideoSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "CameraBindingController"
private const val PREVIEW_METERING_POINT_SIZE = 0.15f
private const val MIN_PREVIEW_METERING_AUTO_CANCEL_MILLIS = 1L

internal class CameraBindingExecutionContext(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    suspend fun <T> run(block: suspend () -> T): T = withContext(dispatcher) {
        block()
    }
}

/**
 * Immutable snapshot of the current binding configuration.
 * Produced by [CameraBindingController.snapshot] for diagnostic reads.
 */
internal data class BindingSnapshot(
    val graph: DeviceGraphSpec?,
    val extensionResolution: CameraExtensionResolution?,
    val stillCaptureQuality: StillCaptureQualityPreference?,
    val stillCaptureResolutionPreset: StillCaptureResolutionPreset?,
    val stillCaptureOutputSize: StillCaptureOutputSize?,
    val manualCaptureConfig: Camera2ManualCaptureConfig?,
    val videoSpec: VideoSpec?,
    val outputRotation: com.opencamera.core.device.CameraOutputRotation
)

/**
 * Callbacks the Adapter provides to the controller for CameraX object lifecycle
 * and event emission. Keeps CameraX framework types out of the controller's public contract.
 */
internal class AdapterBindingCallbacks(
    val onImageCaptureChanged: (ImageCapture?) -> Unit,
    val onVideoCaptureChanged: (androidx.camera.video.VideoCapture<androidx.camera.video.Recorder>?) -> Unit,
    val onBoundCameraChanged: (Camera?) -> Unit,
    val onPreviewFpsFrame: (Long) -> Unit,
    val onVideoQualityTrackerFrame: (Long) -> Unit,
    val sessionCaptureCallback: android.hardware.camera2.CameraCaptureSession.CaptureCallback,
    val livePreviewFrameSource: CameraXLivePreviewFrameSource? = null
)

/**
 * Sole owner of camera binding state: ProcessCameraProvider, bound use cases (ImageCapture,
 * VideoCapture, Camera), DeviceGraphSpec, lifecycle/preview hosts, observers, and current
 * binding configuration (still quality/resolution/output, manual config, video spec).
 *
 * Exposes narrow operations: bind, release, switchLensNode, updateZoomRatio,
 * applyOutputRotation, applyPreviewMetering, applyPreviewBrightness,
 * ensureStillCaptureRequest, ensureVideoRecordingRequest, and currentSnapshot.
 *
 * Follows the VideoRecordingController pattern: adapter provides lambda callbacks for
 * CameraX integration; typed events are emitted via the [emitEvent] lambda.
 */
internal class CameraBindingController(
    private val context: Context,
    private val capabilities: DeviceCapabilities,
    private val cameraProfiles: List<CameraLensProfile>,
    private val emitEvent: (com.opencamera.core.device.DeviceEvent) -> Unit,
    private val extensionSelectorResolver: ExtensionSelectorResolver?,
    private val recordingController: VideoRecordingController,
    private val adapterCallbacks: AdapterBindingCallbacks,
    private val bindingExecutionContext: CameraBindingExecutionContext =
        CameraBindingExecutionContext(),
    private val physicalCameraIdResolver: PhysicalCameraIdResolver? =
        CompositePhysicalCameraIdResolver()
) {
    // -- Binding state (sole owner) --

    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: androidx.camera.video.VideoCapture<androidx.camera.video.Recorder>? = null
    private var boundCamera: Camera? = null
    private var _currentGraph: DeviceGraphSpec? = null
    private var currentExtensionResolution: CameraExtensionResolution? = null
    private var boundLifecycleOwner: LifecycleOwner? = null
    private var boundPreviewView: PreviewView? = null
    private var previewStreamObserver: Observer<PreviewView.StreamState>? = null
    private var cameraStateLiveData: androidx.lifecycle.LiveData<CameraState>? = null
    private var cameraStateObserver: Observer<CameraState>? = null
    private var lastCameraRuntimeIssueSignature: String? = null

    // -- Binding config --

    private var _currentStillCaptureQuality: StillCaptureQualityPreference? = null
    private var _currentStillCaptureResolutionPreset: StillCaptureResolutionPreset? = null
    private var _currentStillCaptureOutputSize: StillCaptureOutputSize? = null
    private var _currentManualCaptureConfig: Camera2ManualCaptureConfig? = null
    private var _currentVideoSpec: VideoSpec? = null
    private var _currentOutputRotation: com.opencamera.core.device.CameraOutputRotation =
        com.opencamera.core.device.CameraOutputRotation.ROTATION_0

    // -- Preview metrics --

    private var suppressPreviewStateEvents = false
    private var firstFrameReportedForCurrentBind = false
    private var bindStartElapsedRealtimeNanos: Long = 0L
    private var previewSnapshotGeneration: Int = 0

    // -- Public accessors (read-only) --

    val currentGraph: DeviceGraphSpec? get() = _currentGraph
    val currentStillCaptureQuality: StillCaptureQualityPreference? get() = _currentStillCaptureQuality
    val currentStillCaptureResolutionPreset: StillCaptureResolutionPreset? get() = _currentStillCaptureResolutionPreset
    val currentStillCaptureOutputSize: StillCaptureOutputSize? get() = _currentStillCaptureOutputSize
    val currentManualCaptureConfig: Camera2ManualCaptureConfig? get() = _currentManualCaptureConfig
    val currentVideoSpec: VideoSpec? get() = _currentVideoSpec
    val currentOutputRotation: com.opencamera.core.device.CameraOutputRotation get() = _currentOutputRotation
    val currentImageCapture: ImageCapture? get() = imageCapture
    val currentVideoCapture: androidx.camera.video.VideoCapture<androidx.camera.video.Recorder>? get() = videoCapture
    val currentBoundCamera: Camera? get() = boundCamera
    val currentBoundPreviewView: PreviewView? get() = boundPreviewView
    val currentLifecycleOwner: LifecycleOwner? get() = boundLifecycleOwner

    fun currentSnapshot(): BindingSnapshot = BindingSnapshot(
        graph = _currentGraph,
        extensionResolution = currentExtensionResolution,
        stillCaptureQuality = _currentStillCaptureQuality,
        stillCaptureResolutionPreset = _currentStillCaptureResolutionPreset,
        stillCaptureOutputSize = _currentStillCaptureOutputSize,
        manualCaptureConfig = _currentManualCaptureConfig,
        videoSpec = _currentVideoSpec,
        outputRotation = _currentOutputRotation
    )

    // -- Bind --

    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        deviceGraph: DeviceGraphSpec,
        resetPreviewObserver: Boolean = true,
        resetPreviewMetrics: Boolean = true,
        closeActiveRecording: Boolean = true
    ) {
        val p = ProcessCameraProvider.getInstance(context).await()
        val resolvedQuality = resolvedStillCaptureQuality(deviceGraph)
        val resolvedPreset = resolvedStillCaptureResolutionPreset(deviceGraph)
        bindingExecutionContext.run {
            bindInternal(
                provider = p,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                deviceGraph = deviceGraph,
                stillCaptureQuality = resolvedQuality,
                stillCaptureResolutionPreset = resolvedPreset,
                resetPreviewObserver = resetPreviewObserver,
                resetPreviewMetrics = resetPreviewMetrics,
                closeActiveRecording = closeActiveRecording
            )
        }
    }

    // -- Release --

    fun release() {
        suppressPreviewStateEvents = true
        removePreviewStreamObserver()
        removeCameraStateObserver()
        adapterCallbacks.livePreviewFrameSource?.stop("release")
        provider?.unbindAll()
        provider = null
        imageCapture = null
        videoCapture = null
        boundCamera = null
        _currentGraph = null
        boundLifecycleOwner = null
        boundPreviewView = null
        _currentStillCaptureQuality = null
        _currentStillCaptureResolutionPreset = null
        _currentStillCaptureOutputSize = null
        _currentManualCaptureConfig = null
        _currentVideoSpec = null
        firstFrameReportedForCurrentBind = false
        bindStartElapsedRealtimeNanos = 0L
        suppressPreviewStateEvents = false
        adapterCallbacks.onImageCaptureChanged(null)
        adapterCallbacks.onVideoCaptureChanged(null)
        adapterCallbacks.onBoundCameraChanged(null)
    }

    // -- Switch lens --

    suspend fun switchLensNode(lensNode: LensNode, reason: String) {
        val activeGraph = _currentGraph ?: return
        val availability = capabilities.zoomRatioCapability.lensNodeMap[lensNode]

        if (availability == null || !availability.available || availability.physicalCameraId == null) {
            emitEvent(
                com.opencamera.core.device.DeviceEvent.RuntimeIssue(
                    DeviceRuntimeIssue(
                        kind = DeviceRuntimeIssueKind.USER_ACTION_REQUIRED,
                        reason = "Lens node ${lensNode.tagValue} is not available on this device: $reason",
                        isRecoverable = true
                    )
                )
            )
            return
        }

        val physicalCameraId = availability.physicalCameraId ?: return
        val p = provider ?: return
        val lifecycleOwner = boundLifecycleOwner ?: return
        val previewView = boundPreviewView ?: return

        bindingExecutionContext.run {
            _currentGraph = activeGraph.copy(
                preview = activeGraph.preview.copy(requestedLensNode = lensNode)
            )

            val prevSuppress = suppressPreviewStateEvents
            suppressPreviewStateEvents = true
            removePreviewStreamObserver()
            removeCameraStateObserver()
            adapterCallbacks.livePreviewFrameSource?.stop("lens-switch")
            p.unbindAll()

            val selector = cameraSelectorForLensNode(lensNode, physicalCameraId)
            val preview = Preview.Builder()
                .setResolutionSelector(previewResolutionSelectorForAspect(activeGraph.preview.streamAspect))
                .build()
                .also { useCase -> useCase.setSurfaceProvider(previewView.surfaceProvider) }

            val boundUseCaseCamera: Camera? = try {
                bindWithSelector(
                    p, lifecycleOwner, selector, preview, activeGraph
                )
            } catch (e: IllegalArgumentException) {
                // Primary selector (public API) failed. Try reflection fallback.
                Log.w(TAG, "Public API bind failed for $physicalCameraId (lens ${lensNode.tagValue}), trying reflection fallback: ${e.message}")
                val fallbackSelector = reflectionFallbackSelector(physicalCameraId)
                if (fallbackSelector != null) {
                    try {
                        bindWithSelector(
                            p, lifecycleOwner, fallbackSelector, preview, activeGraph
                        )
                    } catch (e2: IllegalArgumentException) {
                        Log.w(TAG, "Reflection fallback also failed for $physicalCameraId (lens ${lensNode.tagValue}): ${e2.message}")
                        _currentGraph = activeGraph
                        suppressPreviewStateEvents = prevSuppress
                        emitLensNodeBindFailure(physicalCameraId, lensNode, "${e.message}; reflection fallback also failed: ${e2.message}")
                        return@run
                    }
                } else {
                    _currentGraph = activeGraph
                    suppressPreviewStateEvents = prevSuppress
                    emitLensNodeBindFailure(physicalCameraId, lensNode, e.message ?: "no matching camera and no reflection fallback available")
                    return@run
                }
            }

            if (boundUseCaseCamera == null) {
                _currentGraph = activeGraph
                suppressPreviewStateEvents = prevSuppress
                emitLensNodeBindFailure(physicalCameraId, lensNode, "resolved to null; no matching camera found")
                return@run
            }

            boundCamera = boundUseCaseCamera
            suppressPreviewStateEvents = prevSuppress

            boundUseCaseCamera.cameraControl.setZoomRatio(activeGraph.preview.previewZoomRatio)
        }
    }

    // -- Zoom --

    fun updateZoomRatio(zoomRatio: Float, previewZoomRatio: Float) {
        val normalized = com.opencamera.core.device.normalizedZoomRatioValue(zoomRatio)
        val normalizedPreview = com.opencamera.core.device.normalizedZoomRatioValue(previewZoomRatio)
        val activeGraph = _currentGraph ?: return
        _currentGraph = activeGraph.copy(
            preview = activeGraph.preview.copy(
                zoomRatio = normalized,
                previewZoomRatio = normalizedPreview
            )
        )
        val camera = boundCamera ?: return
        camera.cameraControl.setZoomRatio(normalizedPreview)
    }

    // -- Output rotation --

    fun applyOutputRotation(rotation: com.opencamera.core.device.CameraOutputRotation) {
        if (rotation == _currentOutputRotation) return
        _currentOutputRotation = rotation
        val surfaceRotation = mapOutputRotationToSurface(rotation)
        imageCapture?.targetRotation = surfaceRotation
        videoCapture?.targetRotation = surfaceRotation
    }

    // -- Preview metering --

    suspend fun applyPreviewMetering(request: PreviewMeteringRequest) {
        val result = withContext(Dispatchers.Main.immediate) {
            val camera = boundCamera
            val previewView = boundPreviewView
            if (camera == null || previewView == null) {
                return@withContext previewMeteringResult(
                    request = request,
                    status = PreviewMeteringResultStatus.FAILED,
                    reason = "Preview is not bound"
                )
            }

            val point = request.point.clamped()
            val pixel = previewMeteringPixelPoint(
                normalizedX = point.normalizedX,
                normalizedY = point.normalizedY,
                viewWidth = previewView.width,
                viewHeight = previewView.height
            )
            val meteringPoint = previewView.meteringPointFactory.createPoint(
                pixel.x,
                pixel.y,
                PREVIEW_METERING_POINT_SIZE
            )
            val autoCancelMillis = request.autoCancelMillis
                .coerceAtLeast(MIN_PREVIEW_METERING_AUTO_CANCEL_MILLIS)

            val focusAndAeAction = FocusMeteringAction.Builder(
                meteringPoint,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(autoCancelMillis, TimeUnit.MILLISECONDS)
                .build()

            val aeOnlyAction = FocusMeteringAction.Builder(
                meteringPoint,
                FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(autoCancelMillis, TimeUnit.MILLISECONDS)
                .build()

            val selected = when (request.mode) {
                PreviewMeteringMode.FOCUS_AND_AUTO_EXPOSURE -> when {
                    camera.cameraInfo.isFocusMeteringSupported(focusAndAeAction) ->
                        focusAndAeAction to PreviewMeteringResultStatus.SUCCEEDED

                    camera.cameraInfo.isFocusMeteringSupported(aeOnlyAction) ->
                        aeOnlyAction to PreviewMeteringResultStatus.DEGRADED_AUTO_EXPOSURE_ONLY

                    else -> null
                }

                PreviewMeteringMode.AUTO_EXPOSURE_ONLY -> when {
                    camera.cameraInfo.isFocusMeteringSupported(aeOnlyAction) ->
                        aeOnlyAction to PreviewMeteringResultStatus.DEGRADED_AUTO_EXPOSURE_ONLY

                    else -> null
                }
            }

            if (selected == null) {
                return@withContext PreviewMeteringResult(
                    requestId = request.requestId,
                    point = point,
                    status = PreviewMeteringResultStatus.UNSUPPORTED,
                    reason = "Focus and exposure metering are unsupported"
                )
            }

            val cameraXResult = camera.cameraControl.startFocusAndMetering(selected.first).await()
            val status = selected.second
            val reason = if (
                status == PreviewMeteringResultStatus.SUCCEEDED &&
                !cameraXResult.isFocusSuccessful
            ) {
                "Focus did not lock"
            } else {
                null
            }
            PreviewMeteringResult(
                requestId = request.requestId,
                point = point,
                status = status,
                reason = reason
            )
        }

        emitEvent(com.opencamera.core.device.DeviceEvent.PreviewMeteringCompleted(result))
    }

    private fun previewMeteringResult(
        request: PreviewMeteringRequest,
        status: PreviewMeteringResultStatus,
        reason: String? = null
    ): PreviewMeteringResult {
        return PreviewMeteringResult(
            requestId = request.requestId,
            point = request.point.clamped(),
            status = status,
            reason = reason
        )
    }

    // -- Preview brightness --

    suspend fun applyPreviewBrightness(request: PreviewBrightnessRequest) {
        val result = withContext(Dispatchers.Main.immediate) {
            val camera = boundCamera
            if (camera == null) {
                return@withContext PreviewBrightnessResult(
                    requestId = request.requestId,
                    exposureCompensationSteps = request.exposureCompensationSteps,
                    status = PreviewBrightnessResultStatus.FAILED,
                    reason = "No bound camera"
                )
            }

            val exposureState = camera.cameraInfo.exposureState
            val range = exposureState.exposureCompensationRange
            if (request.exposureCompensationSteps !in range) {
                return@withContext PreviewBrightnessResult(
                    requestId = request.requestId,
                    exposureCompensationSteps = request.exposureCompensationSteps,
                    status = PreviewBrightnessResultStatus.UNSUPPORTED,
                    reason = "Value ${request.exposureCompensationSteps} outside range $range"
                )
            }

            runCatching {
                camera.cameraControl
                    .setExposureCompensationIndex(request.exposureCompensationSteps)
                    .await()
            }.fold(
                onSuccess = {
                    PreviewBrightnessResult(
                        requestId = request.requestId,
                        exposureCompensationSteps = request.exposureCompensationSteps,
                        status = PreviewBrightnessResultStatus.APPLIED
                    )
                },
                onFailure = { throwable ->
                    val isSavedOnly = capabilities.manualControlCapabilities
                        ?.exposureCompensation == com.opencamera.core.device.ManualControlSupport.SAVED_ONLY
                    if (isSavedOnly) {
                        PreviewBrightnessResult(
                            requestId = request.requestId,
                            exposureCompensationSteps = request.exposureCompensationSteps,
                            status = PreviewBrightnessResultStatus.DEGRADED_SAVED_ONLY,
                            reason = throwable.message ?: "Saved-only exposure"
                        )
                    } else {
                        PreviewBrightnessResult(
                            requestId = request.requestId,
                            exposureCompensationSteps = request.exposureCompensationSteps,
                            status = PreviewBrightnessResultStatus.FAILED,
                            reason = throwable.message ?: "setExposureCompensationIndex failed"
                        )
                    }
                }
            )
        }
        emitEvent(com.opencamera.core.device.DeviceEvent.PreviewBrightnessApplied(result))
    }

    // -- Ensure still capture request (rebind if config changed) --

    fun ensureStillCaptureRequestConfigChanged(
        requestedQuality: StillCaptureQualityPreference,
        requestedResolutionPreset: StillCaptureResolutionPreset,
        requestedOutputSize: StillCaptureOutputSize?,
        requestedManualCaptureConfig: Camera2ManualCaptureConfig?
    ): Boolean {
        return _currentStillCaptureQuality == requestedQuality &&
            _currentStillCaptureResolutionPreset == requestedResolutionPreset &&
            _currentStillCaptureOutputSize == requestedOutputSize &&
            _currentManualCaptureConfig == requestedManualCaptureConfig
    }

    suspend fun rebindForStillCapture(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        deviceGraph: DeviceGraphSpec,
        stillCaptureQuality: StillCaptureQualityPreference,
        stillCaptureResolutionPreset: StillCaptureResolutionPreset,
        manualCaptureConfigOverride: Camera2ManualCaptureConfig? = null
    ) {
        val p = provider ?: ProcessCameraProvider.getInstance(context).await()
        bindingExecutionContext.run {
            bindInternal(
                provider = p,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                deviceGraph = deviceGraph,
                stillCaptureQuality = stillCaptureQuality,
                stillCaptureResolutionPreset = stillCaptureResolutionPreset,
                resetPreviewObserver = false,
                resetPreviewMetrics = false,
                closeActiveRecording = false,
                manualCaptureConfigOverride = manualCaptureConfigOverride
            )
        }
    }

    // -- Ensure video recording request (rebind if spec changed) --

    fun ensureVideoRecordingRequestConfigChanged(runtimeVideoSpec: VideoSpec): Boolean {
        return _currentVideoSpec == runtimeVideoSpec
    }

    suspend fun rebindForVideoRecording(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        deviceGraph: DeviceGraphSpec,
        videoSpecOverride: VideoSpec
    ) {
        val p = provider ?: ProcessCameraProvider.getInstance(context).await()
        bindingExecutionContext.run {
            bindInternal(
                provider = p,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                deviceGraph = deviceGraph,
                stillCaptureQuality = deviceGraph.stillCapture.qualityPreference,
                stillCaptureResolutionPreset = deviceGraph.stillCapture.resolutionPreset,
                resetPreviewObserver = false,
                resetPreviewMetrics = false,
                closeActiveRecording = false,
                videoSpecOverride = videoSpecOverride
            )
        }
    }

    // -- Preview stream state callbacks --

    fun handlePreviewStreamState(state: PreviewView.StreamState) {
        if (suppressPreviewStateEvents) return

        when (state) {
            PreviewView.StreamState.STREAMING -> {
                if (firstFrameReportedForCurrentBind) return
                firstFrameReportedForCurrentBind = true
                val firstFrameLatencyMillis = if (bindStartElapsedRealtimeNanos == 0L) {
                    0L
                } else {
                    (SystemClock.elapsedRealtimeNanos() - bindStartElapsedRealtimeNanos) / 1_000_000L
                }
                emitEvent(
                    com.opencamera.core.device.DeviceEvent.PreviewFirstFrameAvailable(firstFrameLatencyMillis)
                )
                if (_currentGraph?.preview?.snapshotsEnabled == true) {
                    previewSnapshotGeneration++
                    emitEvent(
                        com.opencamera.core.device.DeviceEvent.PreviewSnapshotAvailable(
                            source = com.opencamera.core.media.ThumbnailSource.PreviewSnapshot(""),
                            generation = previewSnapshotGeneration
                        )
                    )
                }
            }

            PreviewView.StreamState.IDLE -> {
                if (firstFrameReportedForCurrentBind && _currentGraph != null) {
                    firstFrameReportedForCurrentBind = false
                    emitEvent(
                        com.opencamera.core.device.DeviceEvent.PreviewSurfaceLost("Preview stream returned to IDLE")
                    )
                }
            }
        }
    }

    // -- Camera state callbacks --

    fun handleCameraState(state: CameraState) {
        val error = state.error ?: run {
            if (state.type == CameraState.Type.OPEN) {
                lastCameraRuntimeIssueSignature = null
            }
            return
        }
        val issue = cameraStateRuntimeIssue(
            errorCode = error.code,
            causeMessage = error.cause?.message
                ?.takeIf { it.isNotBlank() }
                ?: "CameraState code=${error.code}"
        )
        val signature = "${state.type.name}:${issue.kind.name}:${issue.reason}"
        if (signature == lastCameraRuntimeIssueSignature) return
        lastCameraRuntimeIssueSignature = signature
        invalidateCachedProviderState(issue)
        emitEvent(com.opencamera.core.device.DeviceEvent.RuntimeIssue(issue))
    }

    fun invalidateCachedProviderState(issue: DeviceRuntimeIssue) {
        if (!shouldInvalidateCachedProviderState(issue)) return
        provider = null
        boundCamera = null
        imageCapture = null
        videoCapture = null
        adapterCallbacks.onImageCaptureChanged(null)
        adapterCallbacks.onVideoCaptureChanged(null)
        adapterCallbacks.onBoundCameraChanged(null)
        recordingController.resetTorchState()
        removeCameraStateObserver()
    }

    // -- Private: bindGraphInternal --

    private fun bindInternal(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        deviceGraph: DeviceGraphSpec,
        stillCaptureQuality: StillCaptureQualityPreference,
        stillCaptureResolutionPreset: StillCaptureResolutionPreset,
        resetPreviewObserver: Boolean,
        resetPreviewMetrics: Boolean,
        closeActiveRecording: Boolean,
        videoSpecOverride: VideoSpec? = null,
        manualCaptureConfigOverride: Camera2ManualCaptureConfig? = null
    ) {
        val baseSelector = cameraSelectorFor(deviceGraph.preferredLensFacing)
        val extResult = resolveExtensionForBinding(
            deviceGraph.stillCapture.extensionStrategy,
            deviceGraph.preferredLensFacing
        )
        val (selector, extResolution) = when (extResult) {
            is ExtensionSelectorResult.Resolved -> extResult.selector to extResult.resolution
            is ExtensionSelectorResult.Fallback -> baseSelector to extResult.resolution
            is ExtensionSelectorResult.NotRequested -> baseSelector to CameraExtensionResolution(
                requestedMode = CameraExtensionMode.NONE,
                availability = CameraExtensionAvailability.NOT_REQUESTED,
                reason = "No extension requested"
            )
        }
        currentExtensionResolution = extResolution

        suppressPreviewStateEvents = true
        if (resetPreviewObserver) {
            removePreviewStreamObserver()
        }
        removeCameraStateObserver()

        val preview = Preview.Builder()
            .setResolutionSelector(previewResolutionSelectorForAspect(deviceGraph.preview.streamAspect))
            .build()
            .also { useCase -> useCase.setSurfaceProvider(previewView.surfaceProvider) }

        adapterCallbacks.livePreviewFrameSource?.stop("unbind")
        provider.unbindAll()
        if (closeActiveRecording) {
            recordingController.clearRecording()
        }

        val boundUseCaseCamera = when (deviceGraph.template) {
            CaptureTemplate.STILL_CAPTURE -> {
                val capture = createImageCapture(
                    deviceGraph = deviceGraph,
                    stillCaptureQuality = stillCaptureQuality,
                    stillCaptureResolutionPreset = stillCaptureResolutionPreset,
                    manualCaptureConfig = manualCaptureConfigOverride
                )
                val useCases = mutableListOf<androidx.camera.core.UseCase>(preview, capture)
                buildLiveAnalysisUseCase()?.let { useCases.add(it) }
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    *useCases.toTypedArray()
                )
                imageCapture = capture
                videoCapture = null
                _currentStillCaptureQuality = stillCaptureQuality
                _currentStillCaptureResolutionPreset = stillCaptureResolutionPreset
                _currentStillCaptureOutputSize = resolvedStillCaptureOutputSize(
                    deviceGraph = deviceGraph,
                    availableOutputSizes = capabilitiesForBinding(deviceGraph).availableStillCaptureOutputSizes
                )
                _currentManualCaptureConfig = manualCaptureConfigOverride
                _currentVideoSpec = null
                adapterCallbacks.onImageCaptureChanged(capture)
                adapterCallbacks.onVideoCaptureChanged(null)
                camera
            }

            CaptureTemplate.VIDEO_RECORDING -> {
                val effectiveVideoSpec = videoSpecOverride ?: deviceGraph.recording.videoSpec
                val recorder = androidx.camera.video.Recorder.Builder()
                    .setQualitySelector(
                        androidx.camera.video.QualitySelector.fromOrderedList(
                            orderedRecordingQualities(
                                effectiveVideoSpec.resolution.toRecordingQualityPreset()
                            )
                        )
                    )
                    .build()
                val capture = androidx.camera.video.VideoCapture.Builder(recorder)
                    .setTargetFrameRate(targetFrameRateRange(effectiveVideoSpec))
                    .setTargetRotation(mapOutputRotationToSurface(_currentOutputRotation))
                    .build()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    capture
                )
                imageCapture = null
                videoCapture = capture
                _currentStillCaptureQuality = deviceGraph.stillCapture.qualityPreference
                _currentStillCaptureResolutionPreset = deviceGraph.stillCapture.resolutionPreset
                _currentStillCaptureOutputSize = deviceGraph.stillCapture.outputSize
                _currentManualCaptureConfig = null
                _currentVideoSpec = effectiveVideoSpec
                adapterCallbacks.onImageCaptureChanged(null)
                adapterCallbacks.onVideoCaptureChanged(capture)
                camera
            }
        }

        this.provider = provider
        boundCamera = boundUseCaseCamera
        adapterCallbacks.onBoundCameraChanged(boundUseCaseCamera)
        observeCameraState(boundUseCaseCamera)
        boundUseCaseCamera.cameraControl.setZoomRatio(deviceGraph.preview.previewZoomRatio)
        recordingController.resetTorchState()
        _currentGraph = deviceGraph
        boundLifecycleOwner = lifecycleOwner
        boundPreviewView = previewView
        if (resetPreviewMetrics) {
            firstFrameReportedForCurrentBind = false
            bindStartElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        if (resetPreviewObserver || previewStreamObserver == null) {
            observePreviewStream(previewView, lifecycleOwner)
        }

        if (deviceGraph.template == CaptureTemplate.STILL_CAPTURE) {
            adapterCallbacks.livePreviewFrameSource?.start(
                com.opencamera.core.media.FrameBufferPolicy.LIVE_PREVIEW_DEFAULT
            )
        }

        suppressPreviewStateEvents = false
    }

    // -- Private: use-case construction --

    private fun createImageCapture(
        deviceGraph: DeviceGraphSpec,
        stillCaptureQuality: StillCaptureQualityPreference,
        stillCaptureResolutionPreset: StillCaptureResolutionPreset,
        manualCaptureConfig: Camera2ManualCaptureConfig? = null
    ): ImageCapture {
        val captureMode = when (stillCaptureQuality) {
            StillCaptureQualityPreference.LATENCY -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            StillCaptureQualityPreference.QUALITY -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
        val resolvedOutputSize = resolvedStillCaptureOutputSize(
            deviceGraph = deviceGraph,
            availableOutputSizes = capabilitiesForBinding(deviceGraph).availableStillCaptureOutputSizes
        )
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            )
            .setResolutionStrategy(
                ResolutionStrategy(
                    targetSizeForStillCaptureOutputSize(resolvedOutputSize),
                    resolutionFallbackRule(stillCaptureResolutionPreset)
                )
            )
            .setAllowedResolutionMode(
                ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE
            )
            .build()
        val builder = ImageCapture.Builder()
            .setCaptureMode(captureMode)
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(mapOutputRotationToSurface(_currentOutputRotation))
        androidx.camera.camera2.interop.Camera2Interop.Extender(builder)
            .setSessionCaptureCallback(adapterCallbacks.sessionCaptureCallback)
        manualCaptureConfig?.let { config ->
            applyCamera2ManualCaptureConfig(builder, config)
        }
        return builder.build()
    }

    private fun resolutionFallbackRule(
        preset: StillCaptureResolutionPreset
    ): Int {
        return when (preset) {
            StillCaptureResolutionPreset.LARGE_12MP ->
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
            StillCaptureResolutionPreset.MEDIUM_8MP ->
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
            StillCaptureResolutionPreset.SMALL_2MP ->
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
        }
    }

    private fun buildLiveAnalysisUseCase(): ImageAnalysis? {
        val source = adapterCallbacks.livePreviewFrameSource ?: return null
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(720, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            val frameNanos = System.nanoTime()
            adapterCallbacks.onPreviewFpsFrame(frameNanos)
            adapterCallbacks.onVideoQualityTrackerFrame(frameNanos)
            (source as? CameraXLivePreviewFrameSource)?.onAnalyzeFrame(
                imageProxy,
                imageProxy.imageInfo.rotationDegrees
            ) ?: imageProxy.close()
        }
        return analysis
    }

    // -- Private: observer management --

    private fun observePreviewStream(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner
    ) {
        val observer = Observer<PreviewView.StreamState> { state ->
            handlePreviewStreamState(state)
        }
        previewStreamObserver = observer
        previewView.previewStreamState.observe(lifecycleOwner, observer)
    }

    private fun removePreviewStreamObserver() {
        val previewView = boundPreviewView ?: return
        val observer = previewStreamObserver ?: return
        previewView.previewStreamState.removeObserver(observer)
        previewStreamObserver = null
    }

    private fun observeCameraState(camera: Camera) {
        removeCameraStateObserver()
        val stateLiveData = camera.cameraInfo.cameraState
        val observer = Observer<CameraState> { state ->
            handleCameraState(state)
        }
        cameraStateLiveData = stateLiveData
        cameraStateObserver = observer
        stateLiveData.observeForever(observer)
    }

    private fun removeCameraStateObserver() {
        val stateLiveData = cameraStateLiveData
        val observer = cameraStateObserver
        if (stateLiveData != null && observer != null) {
            stateLiveData.removeObserver(observer)
        }
        cameraStateLiveData = null
        cameraStateObserver = null
        lastCameraRuntimeIssueSignature = null
    }

    // -- Private: extension resolution --

    private fun resolveExtensionForBinding(
        strategy: ExtensionCaptureStrategy,
        lensFacing: LensFacing
    ): ExtensionSelectorResult {
        if (strategy.desiredMode == CameraExtensionMode.NONE) {
            return ExtensionSelectorResult.NotRequested
        }
        val resolver = extensionSelectorResolver
            ?: return ExtensionSelectorResult.Fallback(
                CameraExtensionResolution(
                    requestedMode = strategy.desiredMode,
                    availability = CameraExtensionAvailability.MANAGER_UNAVAILABLE,
                    reason = "Extension resolver not configured"
                )
            )
        return resolver.resolve(strategy.desiredMode, lensFacing)
    }

    // -- Private: lens node binding helpers --

    private fun bindWithSelector(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        selector: CameraSelector,
        preview: Preview,
        deviceGraph: DeviceGraphSpec
    ): Camera? {
        return when (deviceGraph.template) {
            CaptureTemplate.STILL_CAPTURE -> {
                val capture = imageCapture ?: createImageCapture(
                    deviceGraph = deviceGraph,
                    stillCaptureQuality = _currentStillCaptureQuality ?: StillCaptureQualityPreference.LATENCY,
                    stillCaptureResolutionPreset = _currentStillCaptureResolutionPreset ?: StillCaptureResolutionPreset.LARGE_12MP,
                    manualCaptureConfig = _currentManualCaptureConfig
                )
                val useCases = mutableListOf<androidx.camera.core.UseCase>(preview, capture)
                buildLiveAnalysisUseCase()?.let { useCases.add(it) }
                provider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
            }
            CaptureTemplate.VIDEO_RECORDING -> {
                val videoSpec = _currentVideoSpec ?: deviceGraph.recording.videoSpec
                val recorder = androidx.camera.video.Recorder.Builder()
                    .setQualitySelector(
                        androidx.camera.video.QualitySelector.fromOrderedList(
                            orderedRecordingQualities(videoSpec.resolution.toRecordingQualityPreset())
                        )
                    )
                    .build()
                val capture = androidx.camera.video.VideoCapture.Builder(recorder)
                    .setTargetFrameRate(targetFrameRateRange(videoSpec))
                    .setTargetRotation(mapOutputRotationToSurface(_currentOutputRotation))
                    .build()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            }
        }
    }

    private fun reflectionFallbackSelector(physicalCameraId: String): CameraSelector? {
        val resolver = physicalCameraIdResolver
        if (resolver is CompositePhysicalCameraIdResolver) {
            Log.w(TAG, "Using reflection fallback for physical camera $physicalCameraId")
            return resolver.resolveSelectorViaReflection(physicalCameraId)
        }
        return null
    }

    private fun emitLensNodeBindFailure(
        physicalCameraId: String,
        lensNode: LensNode,
        detail: String
    ) {
        emitEvent(
            com.opencamera.core.device.DeviceEvent.RuntimeIssue(
                DeviceRuntimeIssue(
                    kind = DeviceRuntimeIssueKind.USER_ACTION_REQUIRED,
                    reason = "Physical camera $physicalCameraId (lens ${lensNode.tagValue}) could not be bound: $detail",
                    isRecoverable = true
                )
            )
        )
    }

    // -- Private: camera selectors --

    private fun cameraSelectorFor(lensFacing: LensFacing): CameraSelector {
        return when (lensFacing) {
            LensFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            LensFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }

    private fun cameraSelectorForLensNode(
        lensNode: LensNode,
        physicalCameraId: String
    ): CameraSelector {
        val resolver = physicalCameraIdResolver
            ?: return CameraSelector.DEFAULT_BACK_CAMERA.also {
                Log.w(TAG, "No physicalCameraIdResolver configured for lens ${lensNode.tagValue}; falling back to default back selector")
            }

        return resolver.resolveSelector(physicalCameraId)
    }

    // -- Private: capabilities --

    private fun capabilitiesForBinding(deviceGraph: DeviceGraphSpec): DeviceCapabilities {
        return resolveDeviceCapabilities(
            baseCapabilities = capabilities,
            cameraProfiles = cameraProfiles,
            preferredLensFacing = deviceGraph.preferredLensFacing
        )
    }
}
