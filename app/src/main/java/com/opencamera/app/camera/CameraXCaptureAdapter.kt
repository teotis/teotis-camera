package com.opencamera.app.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.os.SystemClock
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
import com.opencamera.core.device.ManualControlCapabilityMatrix
import com.opencamera.core.device.PreviewBrightnessRange
import com.opencamera.core.device.PreviewBrightnessRequest
import com.opencamera.core.device.PreviewBrightnessResult
import com.opencamera.core.device.PreviewBrightnessResultStatus
import com.opencamera.core.device.PreviewMeteringMode
import com.opencamera.core.device.PreviewMeteringRequest
import com.opencamera.core.device.PreviewMeteringResult
import com.opencamera.core.device.PreviewMeteringResultStatus
import com.opencamera.core.device.ManualControlSupport
import com.opencamera.core.device.MultiFrameCaptureExecutionPlan
import com.opencamera.core.device.MultiFrameCaptureExecutionPlanner
import com.opencamera.core.device.MultiFrameOutputRole
import com.opencamera.core.device.MultiFrameTemporaryOutputTracker
import com.opencamera.core.device.RecordingQualityPreset
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.VideoSceneSignal
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.device.filterToExecutableCapabilities
import com.opencamera.core.device.normalizedZoomRatioValue
import com.opencamera.core.device.resolveRuntimeVideoSpec
import com.opencamera.core.device.toRecordingQualityPreset
import com.opencamera.core.device.supportSummary
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.ManualCaptureParams
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSpec
import com.opencamera.core.settings.VideoSpecConstraints
import com.opencamera.core.media.CompositeMediaPostProcessor
import com.opencamera.core.media.FlashMode as CaptureFlashMode
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.LiveMotionSource
import com.opencamera.core.media.LiveTemporalPlannerInput
import com.opencamera.core.media.planLiveTemporalAssembly
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MultiFrameMergePlaceholderPostProcessor
import com.opencamera.core.media.PipelineMetadataPostProcessor
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotExecutor
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotPlan
import com.opencamera.core.media.ShotTiming
import com.opencamera.core.media.primaryStillNode
import com.opencamera.core.media.primaryVideoNode
import com.opencamera.core.media.temporaryFrameNode
import com.opencamera.app.camera.live.CameraXLivePreviewFrameSource
import com.opencamera.core.media.StillCaptureResolutionOption
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.ThumbnailSource
// smartFilterResolutionOptions is now in ResolutionFilterUtils.kt (local)
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

data class CameraLensProfile(
    val lensFacing: LensFacing,
    val hasFlashUnit: Boolean,
    val zoomRatioCapability: ZoomRatioCapability = ZoomRatioCapability(),
    val availableStillCaptureOutputSizes: List<StillCaptureOutputSize> = emptyList(),
    val availableStillCaptureResolutionPresets: Set<StillCaptureResolutionPreset> =
        StillCaptureResolutionPreset.entries.toSet(),
    val videoSpecConstraints: VideoSpecConstraints = DeviceCapabilities.DEFAULT.videoSpecConstraints,
    val manualControlCapabilities: ManualControlCapabilityMatrix? = null,
    val previewBrightnessRange: PreviewBrightnessRange = PreviewBrightnessRange.CONSERVATIVE
)

internal data class StillCaptureTargetResolution(
    val width: Int,
    val height: Int
)

private const val FOUR_THIRDS_RATIO = 4.0 / 3.0
private const val ASPECT_RATIO_TOLERANCE = 0.05
private const val PREVIEW_METERING_POINT_SIZE = 0.15f
private const val MIN_PREVIEW_METERING_AUTO_CANCEL_MILLIS = 1L

internal fun classifyPreviewBindingFailure(
    throwable: Throwable
): DeviceRuntimeIssue {
    val reason = throwable.message
        ?.takeIf { it.isNotBlank() }
        ?: "Preview binding failed"
    val normalizedReason = reason.lowercase(Locale.ROOT)
    val kind = when {
        "thermal" in normalizedReason -> DeviceRuntimeIssueKind.THERMAL_CRITICAL
        "provider" in normalizedReason -> DeviceRuntimeIssueKind.PROVIDER_FAILURE
        else -> DeviceRuntimeIssueKind.BIND_FAILURE
    }
    return DeviceRuntimeIssue(
        kind = kind,
        reason = reason,
        isRecoverable = kind != DeviceRuntimeIssueKind.THERMAL_CRITICAL
    )
}

internal fun cameraStateRuntimeIssue(
    errorCode: Int,
    causeMessage: String?
): DeviceRuntimeIssue {
    return when (errorCode) {
        CameraState.ERROR_CAMERA_IN_USE,
        CameraState.ERROR_MAX_CAMERAS_IN_USE,
        CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> DeviceRuntimeIssue(
            kind = DeviceRuntimeIssueKind.CAMERA_RECOVERABLE,
            reason = causeMessage ?: "CameraX reported a recoverable camera error",
            isRecoverable = true
        )

        CameraState.ERROR_STREAM_CONFIG -> DeviceRuntimeIssue(
            kind = DeviceRuntimeIssueKind.BIND_FAILURE,
            reason = causeMessage ?: "CameraX stream configuration failed",
            isRecoverable = false
        )

        CameraState.ERROR_CAMERA_DISABLED,
        CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> DeviceRuntimeIssue(
            kind = DeviceRuntimeIssueKind.USER_ACTION_REQUIRED,
            reason = causeMessage ?: "Camera access requires user action",
            isRecoverable = false
        )

        CameraState.ERROR_CAMERA_FATAL_ERROR -> DeviceRuntimeIssue(
            kind = DeviceRuntimeIssueKind.CAMERA_FATAL,
            reason = causeMessage ?: "Camera service reported a fatal error",
            isRecoverable = false
        )

        else -> DeviceRuntimeIssue(
            kind = DeviceRuntimeIssueKind.UNKNOWN,
            reason = causeMessage ?: "Unknown camera runtime issue",
            isRecoverable = false
        )
    }
}

internal fun shouldInvalidateCachedProviderState(
    issue: DeviceRuntimeIssue
): Boolean {
    return issue.kind == DeviceRuntimeIssueKind.PROVIDER_FAILURE ||
        issue.kind == DeviceRuntimeIssueKind.CAMERA_FATAL
}

internal fun orderedRecordingQualities(
    preset: RecordingQualityPreset
): List<Quality> {
    return when (preset) {
        RecordingQualityPreset.UHD -> listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
        RecordingQualityPreset.FHD -> listOf(Quality.FHD, Quality.HD, Quality.SD)
        RecordingQualityPreset.HD -> listOf(Quality.HD, Quality.FHD, Quality.SD)
        RecordingQualityPreset.SD -> listOf(Quality.SD, Quality.HD, Quality.FHD)
    }
}

internal fun targetFrameRateRange(
    videoSpec: VideoSpec
): Range<Int> {
    val bounds = targetFrameRateBounds(videoSpec)
    return Range(bounds.first, bounds.last)
}

internal fun targetFrameRateBounds(
    videoSpec: VideoSpec
): IntRange {
    return videoSpec.frameRate.fps..videoSpec.frameRate.fps
}

internal data class Camera2ManualCaptureConfig(
    val rawEnabled: Boolean = false,
    val iso: Int? = null,
    val shutterTimeNanos: Long? = null,
    val exposureCompensationSteps: Int? = null,
    val focusDistanceDiopters: Float? = null,
    val apertureFNumber: Float? = null,
    val whiteBalanceKelvin: Int? = null
)

internal fun resolveCamera2ManualCaptureConfig(
    deviceRequest: DeviceShotRequest
): Camera2ManualCaptureConfig? {
    val params = deviceRequest.manualCaptureParams
        ?.filterToExecutableCapabilities(deviceRequest.manualControlCapabilities)
        ?: return null
    return params.toCamera2ManualCaptureConfig()
}

internal fun ManualCaptureParams.toCamera2ManualCaptureConfig(): Camera2ManualCaptureConfig {
    return Camera2ManualCaptureConfig(
        rawEnabled = rawEnabled,
        iso = iso,
        shutterTimeNanos = shutterSpeedMillis?.times(1_000_000L),
        exposureCompensationSteps = exposureCompensationSteps,
        focusDistanceDiopters = focusDistanceDiopters,
        apertureFNumber = apertureFNumber,
        whiteBalanceKelvin = whiteBalanceKelvin
    )
}

internal fun manualCaptureExecutionDiagnostics(
    requestedParams: ManualCaptureParams?,
    capabilityMatrix: ManualControlCapabilityMatrix
): List<String> {
    if (requestedParams == null || requestedParams.isAuto()) {
        return emptyList()
    }
    val summary = capabilityMatrix.supportSummary(requestedParams)
    return buildList {
        when {
            summary.applied.isNotEmpty() &&
                summary.savedOnly.isEmpty() &&
                summary.unsupported.isEmpty() ->
                add("adapter:manual-request=applied")

            summary.applied.isNotEmpty() ->
                add("adapter:manual-request=partial")

            summary.savedOnly.isNotEmpty() ->
                add("adapter:manual-request=saved-only")

            summary.unsupported.isNotEmpty() ->
                add("adapter:manual-request=unsupported")
        }
        if (summary.applied.isNotEmpty()) {
            add("adapter:manual-applied=${summary.applied.joinToString(separator = "+")}")
        }
        if (summary.savedOnly.isNotEmpty()) {
            add("adapter:manual-saved-only=${summary.savedOnly.joinToString(separator = "+")}")
        }
        if (summary.unsupported.isNotEmpty()) {
            add(
                "adapter:manual-unsupported=${summary.unsupported.joinToString(separator = "+")}"
            )
        }
    }
}

private fun ManualCaptureParams.isAuto(): Boolean {
    return !rawEnabled &&
        iso == null &&
        shutterSpeedMillis == null &&
        exposureCompensationSteps == null &&
        focusDistanceDiopters == null &&
        apertureFNumber == null &&
        whiteBalanceKelvin == null
}

internal fun applyCamera2ManualCaptureConfig(
    builder: ImageCapture.Builder,
    config: Camera2ManualCaptureConfig
) {
    val extender = Camera2Interop.Extender(builder)
    if (config.iso != null || config.shutterTimeNanos != null) {
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_OFF
        )
        config.iso?.let { iso ->
            extender.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
        }
        config.shutterTimeNanos?.let { shutterTimeNanos ->
            extender.setCaptureRequestOption(
                CaptureRequest.SENSOR_EXPOSURE_TIME,
                shutterTimeNanos
            )
        }
    }
    config.exposureCompensationSteps?.let { steps ->
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, steps)
    }
    config.focusDistanceDiopters?.let { focusDistanceDiopters ->
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_OFF
        )
        extender.setCaptureRequestOption(
            CaptureRequest.LENS_FOCUS_DISTANCE,
            focusDistanceDiopters
        )
    }
    config.apertureFNumber?.let { apertureFNumber ->
        extender.setCaptureRequestOption(CaptureRequest.LENS_APERTURE, apertureFNumber)
    }
}

internal fun stillCaptureCleanupPaths(
    outputPath: String,
    outputHandle: MediaOutputHandle,
    livePhotoBundle: LivePhotoBundle? = null,
    intermediateOutputPaths: List<String> = emptyList()
): List<String> {
    val cleanupPaths = linkedSetOf<String>()

    fun registerAbsolutePath(path: String?) {
        if (!path.isNullOrBlank() && File(path).isAbsolute) {
            cleanupPaths += path
        }
    }

    registerAbsolutePath(outputHandle.filePath)
    registerAbsolutePath(outputPath)
    registerAbsolutePath(livePhotoBundle?.stillPath)
    registerAbsolutePath(livePhotoBundle?.motionHandle?.filePath)
    registerAbsolutePath(livePhotoBundle?.motionPath)
    registerAbsolutePath(livePhotoBundle?.sidecarHandle?.filePath)
    registerAbsolutePath(livePhotoBundle?.sidecarPath)
    registerAbsolutePath(livePhotoBundle?.thumbnailHandle?.filePath)
    registerAbsolutePath(livePhotoBundle?.thumbnailPath)
    intermediateOutputPaths.forEach(::registerAbsolutePath)
    return cleanupPaths.toList()
}

internal fun stillCaptureCleanupContentUris(
    outputHandle: MediaOutputHandle,
    livePhotoBundle: LivePhotoBundle? = null
): List<String> {
    return buildList {
        outputHandle.contentUri?.let(::add)
        livePhotoBundle?.motionHandle?.contentUri?.let(::add)
        livePhotoBundle?.sidecarHandle?.contentUri?.let(::add)
        livePhotoBundle?.thumbnailHandle?.contentUri?.let(::add)
    }.distinct()
}

internal fun cleanupStillCaptureArtifacts(
    outputPath: String,
    outputHandle: MediaOutputHandle,
    livePhotoBundle: LivePhotoBundle? = null,
    intermediateOutputPaths: List<String> = emptyList(),
    deleteContentUri: (String) -> Unit = {}
): List<String> {
    stillCaptureCleanupContentUris(
        outputHandle = outputHandle,
        livePhotoBundle = livePhotoBundle
    ).forEach(deleteContentUri)
    return cleanupAbsoluteFilePaths(
        stillCaptureCleanupPaths(
            outputPath = outputPath,
            outputHandle = outputHandle,
            livePhotoBundle = livePhotoBundle,
            intermediateOutputPaths = intermediateOutputPaths
        )
    )
}

internal fun cleanupAbsoluteFilePaths(
    paths: List<String>
): List<String> {
    return paths
        .filter { File(it).isAbsolute }
        .distinct()
        .filter { path ->
            val file = File(path)
            file.exists() && file.delete()
        }
}

internal fun materializeLivePhotoSidecar(
    bundle: LivePhotoBundle,
    writeContentUriPayload: (String, String) -> Unit = { _, _ -> }
) {
    val payload = buildLivePhotoSidecarPayload(bundle)
    bundle.sidecarHandle.contentUri?.let { contentUri ->
        writeContentUriPayload(contentUri, payload)
        return
    }
    val sidecarFile = bundle.sidecarHandle.filePath?.let(::File) ?: File(bundle.sidecarPath)
    if (!sidecarFile.isAbsolute) {
        return
    }
    sidecarFile.parentFile?.mkdirs()
    sidecarFile.writeText(payload)
}

data class LiveMotionSourceResult(
    val source: LiveMotionSource,
    val selectedFrameSet: com.opencamera.core.media.SelectedFrameSet,
    val ringBufferDepthMillis: Long,
    val postShutterBudgetMillis: Long,
    val diagnostics: List<String>
)

internal fun resolveLiveMotionSource(
    frameSource: com.opencamera.app.camera.live.LivePreviewFrameSource,
    shutterTimestampNanos: Long,
    spec: com.opencamera.core.media.LivePhotoCaptureSpec
): LiveMotionSourceResult {
    if (!frameSource.isActive) {
        return LiveMotionSourceResult(
            source = LiveMotionSource.METADATA_ONLY,
            selectedFrameSet = com.opencamera.core.media.SelectedFrameSet(
                frames = emptyList(),
                preShutterCount = 0,
                postShutterCount = 0,
                coveredPreShutterMillis = 0,
                coveredPostShutterMillis = 0,
                diagnostics = listOf("frame-source:not-active")
            ),
            ringBufferDepthMillis = 0,
            postShutterBudgetMillis = 0,
            diagnostics = listOf("live:source=metadata-only")
        )
    }

    val selectedFrameSet = frameSource.selectForLive(shutterTimestampNanos, spec)

    return if (selectedFrameSet.frames.isNotEmpty()) {
        LiveMotionSourceResult(
            source = LiveMotionSource.PREVIEW_RING_BUFFER,
            selectedFrameSet = selectedFrameSet,
            ringBufferDepthMillis = spec.motionDurationMillis,
            postShutterBudgetMillis = spec.motionDurationMillis / 5,
            diagnostics = buildList {
                add("live:source=preview-ring-buffer")
                add("frame-buffer:selected=${selectedFrameSet.frames.size}")
                add("frame-buffer:window=-${selectedFrameSet.coveredPreShutterMillis}ms,+${selectedFrameSet.coveredPostShutterMillis}ms")
            }
        )
    } else {
        LiveMotionSourceResult(
            source = LiveMotionSource.METADATA_ONLY,
            selectedFrameSet = selectedFrameSet,
            ringBufferDepthMillis = 0,
            postShutterBudgetMillis = 0,
            diagnostics = buildList {
                add("live:source=metadata-only")
                add("live:degraded=no-frames-near-shutter")
            }
        )
    }
}

data class LiveMotionPhotoMaterializationResult(
    val bundle: LivePhotoBundle,
    val diagnostics: List<String>
)

internal fun materializeMotionPhotoBundleIfPossible(
    bundle: LivePhotoBundle,
    motionSourceResult: LiveMotionSourceResult,
    prepareMotionSegment: (List<com.opencamera.core.media.FrameDescriptor>, String) -> Result<String> =
        { _, motionPath -> Result.success(motionPath) },
    materialize: (String) -> Result<String>
): LiveMotionPhotoMaterializationResult {
    val hasSelectedFrames = motionSourceResult.source == LiveMotionSource.PREVIEW_RING_BUFFER &&
        motionSourceResult.selectedFrameSet.frames.isNotEmpty()
    if (!hasSelectedFrames) {
        return LiveMotionPhotoMaterializationResult(
            bundle = bundle,
            diagnostics = emptyList()
        )
    }

    val motionSegmentResult = prepareMotionSegment(
        motionSourceResult.selectedFrameSet.frames,
        bundle.motionPath
    )
    if (motionSegmentResult.isFailure) {
        val reason = motionSegmentResult.exceptionOrNull()?.message ?: "unknown"
        return LiveMotionPhotoMaterializationResult(
            bundle = bundle.copy(
                bundleStatus = com.opencamera.core.media.LiveBundleStatus.STILL_ONLY_FALLBACK
            ),
            diagnostics = listOf("motion-photo:motion-segment=failed:$reason")
        )
    }

    val motionPath = motionSegmentResult.getOrDefault(bundle.motionPath)
    val motionPhotoResult = materialize(motionPath)
    return if (motionPhotoResult.isSuccess) {
        val outputPath = motionPhotoResult.getOrDefault(bundle.stillPath)
        LiveMotionPhotoMaterializationResult(
            bundle = bundle.copy(
                stillPath = outputPath,
                motionPath = motionPath,
                thumbnailPath = outputPath,
                thumbnailHandle = MediaOutputHandle(displayPath = outputPath)
            ),
            diagnostics = listOf(
                "motion-photo:motion-segment=materialized",
                "motion-photo:container=google-jpeg"
            )
        )
    } else {
        val reason = motionPhotoResult.exceptionOrNull()?.message ?: "unknown"
        LiveMotionPhotoMaterializationResult(
            bundle = bundle.copy(
                bundleStatus = com.opencamera.core.media.LiveBundleStatus.STILL_ONLY_FALLBACK
            ),
            diagnostics = listOf("motion-photo:container=failed:$reason")
        )
    }
}

internal fun buildLivePhotoSidecarPayload(
    bundle: LivePhotoBundle
): String {
    return buildString {
        append("{\n")
        append("  \"stillPath\": ")
        append(jsonStringLiteral(bundle.stillPath))
        append(",\n")
        append("  \"motionPath\": ")
        append(jsonStringLiteral(bundle.motionPath))
        append(",\n")
        append("  \"sidecarPath\": ")
        append(jsonStringLiteral(bundle.sidecarPath))
        append(",\n")
        append("  \"thumbnailPath\": ")
        append(jsonStringLiteral(bundle.thumbnailPath))
        append(",\n")
        append("  \"motionDurationMillis\": ")
        append(bundle.motionDurationMillis)
        append(",\n")
        append("  \"motionMimeType\": ")
        append(jsonStringLiteral(bundle.motionMimeType))
        append(",\n")
        append("  \"sidecarMimeType\": ")
        append(jsonStringLiteral(bundle.sidecarMimeType))
        bundle.temporalWindow?.let { window ->
            append(",\n")
            append("  \"temporalWindow\": {\n")
            append("    \"requestedDurationMillis\": ${window.requestedDurationMillis},\n")
            append("    \"preShutterMillis\": ${window.preShutterMillis},\n")
            append("    \"postShutterMillis\": ${window.postShutterMillis},\n")
            append("    \"frameCount\": ${window.frameCount},\n")
            append("    \"source\": ")
            append(jsonStringLiteral(window.source.name.lowercase()))
            append("\n")
            append("  }")
        }
        append(",\n")
        append("  \"bundleStatus\": ")
        append(jsonStringLiteral(bundle.bundleStatus.name.lowercase().replace('_', '-')))
        append("\n")
        append("}")
    }
}

private fun jsonStringLiteral(value: String): String {
    return buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
}

internal fun resolvedStillCaptureQuality(
    deviceGraph: DeviceGraphSpec,
    deviceRequest: DeviceShotRequest? = null
): StillCaptureQualityPreference {
    return when (deviceGraph.template) {
        CaptureTemplate.STILL_CAPTURE ->
            deviceGraph.stillCapture.qualityPreference
        CaptureTemplate.VIDEO_RECORDING ->
            deviceRequest?.stillCaptureQuality ?: deviceGraph.stillCapture.qualityPreference
    }
}

internal fun resolvedStillCaptureResolutionPreset(
    deviceGraph: DeviceGraphSpec
): StillCaptureResolutionPreset {
    return deviceGraph.stillCapture.resolutionPreset
}

internal fun resolvedStillCaptureOutputSize(
    deviceGraph: DeviceGraphSpec,
    availableOutputSizes: List<StillCaptureOutputSize>
): StillCaptureOutputSize {
    return deviceGraph.stillCapture.outputSize
        ?: resolveStillCaptureOutputSize(
            preset = deviceGraph.stillCapture.resolutionPreset,
            availableOutputSizes = availableOutputSizes
        )
}

internal fun targetDimensionsForStillCaptureResolutionPreset(
    preset: StillCaptureResolutionPreset
): StillCaptureTargetResolution {
    return StillCaptureTargetResolution(
        width = preset.targetWidth,
        height = preset.targetHeight
    )
}

internal fun resolveStillCaptureOutputSize(
    preset: StillCaptureResolutionPreset,
    availableOutputSizes: List<StillCaptureOutputSize>
): StillCaptureOutputSize {
    if (availableOutputSizes.isEmpty()) {
        val targetDimensions = targetDimensionsForStillCaptureResolutionPreset(preset)
        return StillCaptureOutputSize(
            width = targetDimensions.width,
            height = targetDimensions.height
        )
    }

    val desiredPixels = preset.targetWidth.toLong() * preset.targetHeight.toLong()
    val sortedByPixels = availableOutputSizes.sortedBy { it.pixelCount }
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

internal fun resolveStillCaptureOutputSize(
    option: StillCaptureResolutionOption,
    availableOutputSizes: List<StillCaptureOutputSize>
): StillCaptureOutputSize {
    if (availableOutputSizes.isEmpty()) {
        return StillCaptureOutputSize(
            width = option.targetWidth,
            height = option.targetHeight
        )
    }

    // 找到最接近目标像素数的分辨率
    val desiredPixels = option.pixelCount
    return availableOutputSizes
        .minByOrNull { kotlin.math.abs(it.pixelCount - desiredPixels) }
        ?: StillCaptureOutputSize(
            width = option.targetWidth,
            height = option.targetHeight
        )
}

internal fun targetSizeForStillCaptureResolutionPreset(
    preset: StillCaptureResolutionPreset
): Size {
    val targetDimensions = targetDimensionsForStillCaptureResolutionPreset(preset)
    return Size(targetDimensions.width, targetDimensions.height)
}

internal fun targetSizeForStillCaptureOutputSize(
    outputSize: StillCaptureOutputSize
): Size {
    return Size(outputSize.width, outputSize.height)
}

internal fun resolveDeviceCapabilities(
    baseCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
    cameraProfiles: List<CameraLensProfile>,
    preferredLensFacing: LensFacing? = null
): DeviceCapabilities {
    if (cameraProfiles.isEmpty()) {
        return baseCapabilities
    }

    val prioritizedProfiles = when (preferredLensFacing) {
        null -> cameraProfiles.filter { it.lensFacing == LensFacing.BACK }
            .ifEmpty { cameraProfiles }
        else -> cameraProfiles.filter { it.lensFacing == preferredLensFacing }
            .ifEmpty { cameraProfiles }
    }
    val supportsFlashControl = prioritizedProfiles.any { it.hasFlashUnit }
    val availableStillCaptureOutputSizes = prioritizedProfiles
        .flatMap { it.availableStillCaptureOutputSizes }
        .distinctBy { it.width to it.height }
        .sortedByDescending { it.pixelCount }
    val availableStillCaptureResolutionPresets = prioritizedProfiles
        .flatMap { it.availableStillCaptureResolutionPresets }
        .toSet()
        .ifEmpty { baseCapabilities.availableStillCaptureResolutionPresets }
    val availableStillCaptureResolutionOptions = smartFilterResolutionOptions(
        availableStillCaptureOutputSizes
    )
    val zoomRatioCapability = mergeZoomRatioCapability(
        baseCapability = baseCapabilities.zoomRatioCapability,
        cameraProfiles = prioritizedProfiles
    )
    val manualControlCapabilities = mergeManualControlCapabilities(prioritizedProfiles)
    val previewBrightnessRange = mergePreviewBrightnessRange(prioritizedProfiles)

    return baseCapabilities.copy(
        supportsFlashControl = supportsFlashControl,
        availableLensFacings = cameraProfiles
            .map { it.lensFacing }
            .toSet()
            .ifEmpty { baseCapabilities.availableLensFacings },
        zoomRatioCapability = zoomRatioCapability,
        previewBrightnessRange = previewBrightnessRange,
        availableStillCaptureOutputSizes = availableStillCaptureOutputSizes
            .ifEmpty { baseCapabilities.availableStillCaptureOutputSizes },
        availableStillCaptureResolutionOptions = availableStillCaptureResolutionOptions,
        availableStillCaptureResolutionPresets = availableStillCaptureResolutionPresets,
        manualControlCapabilities = manualControlCapabilities
            ?: baseCapabilities.manualControlCapabilities,
        videoSpecConstraints = mergeVideoSpecConstraints(
            baseConstraints = baseCapabilities.videoSpecConstraints,
            cameraProfiles = prioritizedProfiles
        )
    )
}

internal fun mergeZoomRatioCapability(
    baseCapability: ZoomRatioCapability,
    cameraProfiles: List<CameraLensProfile>
): ZoomRatioCapability {
    val explicitCapabilities = cameraProfiles.map(CameraLensProfile::zoomRatioCapability)
        .filter { capability ->
            capability.support != ZoomControlSupport.UNSUPPORTED ||
                capability.normalizedSupportedRatios.size > 1
        }
    if (explicitCapabilities.isEmpty()) {
        return baseCapability
    }
    val mergedRatios = explicitCapabilities
        .flatMap(ZoomRatioCapability::normalizedSupportedRatios)
        .map(::normalizedZoomRatioValue)
        .distinct()
        .sorted()
    val mergedSupport = explicitCapabilities.maxValueOf { it.support }
    return ZoomRatioCapability(
        support = mergedSupport,
        supportedRatios = mergedRatios,
        defaultRatio = mergedRatios.firstOrNull { it == 1f } ?: mergedRatios.first()
    )
}

private inline fun <T : Comparable<T>, R> Iterable<R>.maxValueOf(
    selector: (R) -> T
): T {
    return map(selector).maxOrNull() ?: error("Expected non-empty iterable")
}

internal fun mergeManualControlCapabilities(
    cameraProfiles: List<CameraLensProfile>
): ManualControlCapabilityMatrix? {
    val explicitCapabilities = cameraProfiles.mapNotNull(CameraLensProfile::manualControlCapabilities)
    if (explicitCapabilities.isEmpty()) {
        return null
    }
    return ManualControlCapabilityMatrix(
        raw = explicitCapabilities.maxSupportOf { it.raw },
        iso = explicitCapabilities.maxSupportOf { it.iso },
        shutter = explicitCapabilities.maxSupportOf { it.shutter },
        exposureCompensation = explicitCapabilities.maxSupportOf { it.exposureCompensation },
        focusDistance = explicitCapabilities.maxSupportOf { it.focusDistance },
        aperture = explicitCapabilities.maxSupportOf { it.aperture },
        whiteBalance = explicitCapabilities.maxSupportOf { it.whiteBalance }
    )
}

private inline fun List<ManualControlCapabilityMatrix>.maxSupportOf(
    selector: (ManualControlCapabilityMatrix) -> ManualControlSupport
): ManualControlSupport {
    return map(selector).maxBy(ManualControlSupport::ordinal)
}

internal fun mergePreviewBrightnessRange(
    cameraProfiles: List<CameraLensProfile>
): PreviewBrightnessRange {
    val ranges = cameraProfiles.map { it.previewBrightnessRange }
    if (ranges.all { it == PreviewBrightnessRange.UNSUPPORTED }) {
        return PreviewBrightnessRange.UNSUPPORTED
    }
    val supported = ranges.filter { it != PreviewBrightnessRange.UNSUPPORTED }
    if (supported.isEmpty()) {
        return PreviewBrightnessRange.CONSERVATIVE
    }
    return PreviewBrightnessRange(
        minSteps = supported.minOf { it.minSteps },
        maxSteps = supported.maxOf { it.maxSteps }
    )
}

internal fun mergeVideoSpecConstraints(
    baseConstraints: VideoSpecConstraints,
    cameraProfiles: List<CameraLensProfile>
): VideoSpecConstraints {
    val mergedFrameRatesByResolution = linkedMapOf<VideoResolution, Set<VideoFrameRate>>()
    cameraProfiles.forEach { profile ->
        profile.videoSpecConstraints.supportedFrameRatesByResolution.forEach { (resolution, frameRates) ->
            mergedFrameRatesByResolution[resolution] =
                mergedFrameRatesByResolution[resolution].orEmpty() + frameRates
        }
    }
    if (mergedFrameRatesByResolution.isEmpty()) {
        return baseConstraints
    }
    return VideoSpecConstraints(
        supportedFrameRatesByResolution = mergedFrameRatesByResolution
            .mapValues { (_, frameRates) -> frameRates.sortedBy(VideoFrameRate::ordinal).toSet() },
        dynamicPolicies = cameraProfiles
            .flatMap { it.videoSpecConstraints.dynamicPolicies }
            .toSet()
            .ifEmpty { baseConstraints.dynamicPolicies },
        audioProfiles = cameraProfiles
            .flatMap { it.videoSpecConstraints.audioProfiles }
            .toSet()
            .ifEmpty { baseConstraints.audioProfiles }
    )
}

internal fun normalizeStillCaptureOutputSizes(
    availableOutputSizes: List<StillCaptureTargetResolution>
): List<StillCaptureOutputSize> {
    if (availableOutputSizes.isEmpty()) {
        return emptyList()
    }

    val normalized = availableOutputSizes
        .map { size ->
            StillCaptureOutputSize(width = size.width, height = size.height)
        }
        .distinctBy { it.width to it.height }
    val fourThirdsSizes = normalized.filter { size ->
        val ratio = size.width.toDouble() / size.height.toDouble()
        kotlin.math.abs(ratio - FOUR_THIRDS_RATIO) <= ASPECT_RATIO_TOLERANCE
    }
    return (fourThirdsSizes.ifEmpty { normalized })
        .sortedByDescending { it.pixelCount }
}

internal fun resolveAvailableStillCaptureResolutionPresets(
    availableOutputSizes: List<StillCaptureTargetResolution>,
    fallbackPresets: Set<StillCaptureResolutionPreset> = StillCaptureResolutionPreset.entries.toSet()
): Set<StillCaptureResolutionPreset> {
    val normalizedOutputSizes = normalizeStillCaptureOutputSizes(availableOutputSizes)
    if (normalizedOutputSizes.isEmpty()) {
        return fallbackPresets
    }

    val maxPixels = normalizedOutputSizes.maxOf { size ->
        size.pixelCount
    }
    return StillCaptureResolutionPreset.entries
        .filter { preset ->
            maxPixels >= preset.targetWidth.toLong() * preset.targetHeight.toLong()
        }
        .toSet()
        .ifEmpty { setOf(StillCaptureResolutionPreset.SMALL_2MP) }
}

internal fun resolveAvailableStillCaptureResolutionPresetsFromSizes(
    availableOutputSizes: List<Size>,
    fallbackPresets: Set<StillCaptureResolutionPreset> = StillCaptureResolutionPreset.entries.toSet()
): Set<StillCaptureResolutionPreset> {
    return resolveAvailableStillCaptureResolutionPresets(
        availableOutputSizes = availableOutputSizes.map { size ->
            StillCaptureTargetResolution(
                width = size.width,
                height = size.height
            )
        },
        fallbackPresets = fallbackPresets
    )
}

internal fun buildVideoSpecConstraints(
    standardProfileFrameRates: Map<VideoResolution, Int>,
    highSpeedProfileFrameRates: Map<VideoResolution, Int>,
    fallback: VideoSpecConstraints = DeviceCapabilities.DEFAULT.videoSpecConstraints
): VideoSpecConstraints {
    val mergedFrameRatesByResolution = linkedMapOf<VideoResolution, MutableSet<VideoFrameRate>>()
    standardProfileFrameRates.forEach { (resolution, rawFps) ->
        val supportedFrameRates = mergedFrameRatesByResolution.getOrPut(resolution) { linkedSetOf() }
        supportedFrameRates += standardVideoFrameRatesForProfile(rawFps)
    }
    highSpeedProfileFrameRates.forEach { (resolution, rawFps) ->
        val supportedFrameRates = mergedFrameRatesByResolution.getOrPut(resolution) { linkedSetOf() }
        supportedFrameRates += highSpeedVideoFrameRatesForProfile(rawFps)
    }
    if (mergedFrameRatesByResolution.isEmpty()) {
        return fallback
    }
    val dynamicPolicies = buildSet {
        add(DynamicVideoFpsPolicy.LOCKED)
        if (mergedFrameRatesByResolution.values.any { VideoFrameRate.FPS_24 in it }) {
            add(DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS)
        }
    }
    return VideoSpecConstraints(
        supportedFrameRatesByResolution = mergedFrameRatesByResolution
            .mapValues { (_, frameRates) -> frameRates.sortedBy(VideoFrameRate::ordinal).toSet() },
        dynamicPolicies = dynamicPolicies,
        audioProfiles = fallback.audioProfiles.ifEmpty { setOf(AudioProfile.STANDARD) }
    )
}

private fun standardVideoFrameRatesForProfile(rawFps: Int): Set<VideoFrameRate> {
    return buildSet {
        when {
            rawFps <= 24 -> add(VideoFrameRate.FPS_24)
            rawFps == 25 -> add(VideoFrameRate.FPS_25)
            rawFps in 26..30 -> {
                add(VideoFrameRate.FPS_25)
                add(VideoFrameRate.FPS_30)
            }
            rawFps in 31..59 -> add(VideoFrameRate.FPS_30)
            else -> {
                add(VideoFrameRate.FPS_30)
                add(VideoFrameRate.FPS_60)
            }
        }
    }
}

private fun highSpeedVideoFrameRatesForProfile(rawFps: Int): Set<VideoFrameRate> {
    return buildSet {
        if (rawFps >= 100) {
            add(VideoFrameRate.FPS_100)
        }
        if (rawFps >= 120) {
            add(VideoFrameRate.FPS_120)
        }
    }
}

private fun detectVideoSpecConstraints(
    cameraId: String,
    characteristics: CameraCharacteristics,
    fallback: VideoSpecConstraints = DeviceCapabilities.DEFAULT.videoSpecConstraints
): VideoSpecConstraints {
    val numericCameraId = cameraId.toIntOrNull() ?: return fallback
    val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    val standardProfileFrameRates = linkedMapOf<VideoResolution, Int>()
    standardVideoQualityMapping().forEach { (resolution, quality) ->
        if (!CamcorderProfile.hasProfile(numericCameraId, quality)) {
            return@forEach
        }
        val profile = runCatching {
            CamcorderProfile.get(numericCameraId, quality)
        }.getOrNull() ?: return@forEach
        if (supportsVideoResolution(streamMap, resolution)) {
            standardProfileFrameRates[resolution] = profile.videoFrameRate
        }
    }
    val highSpeedProfileFrameRates = linkedMapOf<VideoResolution, Int>()
    highSpeedVideoQualityMapping().forEach { (resolution, quality) ->
        if (!CamcorderProfile.hasProfile(numericCameraId, quality)) {
            return@forEach
        }
        val profile = runCatching {
            CamcorderProfile.get(numericCameraId, quality)
        }.getOrNull() ?: return@forEach
        if (supportsVideoResolution(streamMap, resolution)) {
            highSpeedProfileFrameRates[resolution] = profile.videoFrameRate
        }
    }
    return buildVideoSpecConstraints(
        standardProfileFrameRates = standardProfileFrameRates,
        highSpeedProfileFrameRates = highSpeedProfileFrameRates,
        fallback = fallback
    )
}

private fun standardVideoQualityMapping(): Map<VideoResolution, Int> {
    return buildMap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            put(VideoResolution.UHD_8K, CamcorderProfile.QUALITY_8KUHD)
        }
        put(VideoResolution.UHD_4K, CamcorderProfile.QUALITY_2160P)
        put(VideoResolution.FHD_1080P, CamcorderProfile.QUALITY_1080P)
        put(VideoResolution.HD_720P, CamcorderProfile.QUALITY_720P)
        put(VideoResolution.SD_480P, CamcorderProfile.QUALITY_480P)
    }
}

private fun highSpeedVideoQualityMapping(): Map<VideoResolution, Int> {
    return mapOf(
        VideoResolution.UHD_4K to CamcorderProfile.QUALITY_HIGH_SPEED_2160P,
        VideoResolution.FHD_1080P to CamcorderProfile.QUALITY_HIGH_SPEED_1080P,
        VideoResolution.HD_720P to CamcorderProfile.QUALITY_HIGH_SPEED_720P,
        VideoResolution.SD_480P to CamcorderProfile.QUALITY_HIGH_SPEED_480P
    )
}

private fun supportsVideoResolution(
    streamMap: StreamConfigurationMap?,
    resolution: VideoResolution
): Boolean {
    val availableSizes = streamMap?.getOutputSizes(MediaRecorder::class.java).orEmpty()
    if (availableSizes.isEmpty()) {
        return true
    }
    val target = when (resolution) {
        VideoResolution.UHD_8K -> 7680 to 4320
        VideoResolution.UHD_4K -> 3840 to 2160
        VideoResolution.FHD_1080P -> 1920 to 1080
        VideoResolution.HD_720P -> 1280 to 720
        VideoResolution.SD_480P -> 720 to 480
    }
    return availableSizes.any { size ->
        val widthMatches = kotlin.math.abs(size.width - target.first) <= 32
        val heightMatches = kotlin.math.abs(size.height - target.second) <= 32
        widthMatches && heightMatches
    }
}

internal fun detectZoomRatioCapability(
    characteristics: CameraCharacteristics,
    fallback: ZoomRatioCapability = DeviceCapabilities.DEFAULT.zoomRatioCapability
): ZoomRatioCapability {
    val supportedRatios = linkedSetOf<Float>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val zoomRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        zoomRange?.lower
            ?.takeIf { it > 0f && kotlin.math.abs(it - 1f) > 0.05f }
            ?.let { supportedRatios += normalizedZoomRatioValue(it) }
        supportedRatios += 1f
        zoomRange?.upper
            ?.takeIf { it > 1.05f }
            ?.let { maxRatio ->
                if (maxRatio >= 2f) {
                    supportedRatios += 2f
                }
                if (maxRatio >= 5f) {
                    supportedRatios += 5f
                }
                supportedRatios += normalizedZoomRatioValue(maxRatio)
            }
    } else {
        supportedRatios += 1f
        val maxDigitalZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
            ?: 1f
        if (maxDigitalZoom > 1.05f) {
            if (maxDigitalZoom >= 2f) {
                supportedRatios += 2f
            }
            supportedRatios += normalizedZoomRatioValue(maxDigitalZoom)
        }
    }
    val normalizedRatios = supportedRatios
        .filter { it > 0f }
        .distinct()
        .sorted()
    if (normalizedRatios.size < 2) {
        return fallback
    }
    return ZoomRatioCapability(
        support = ZoomControlSupport.CONTINUOUS,
        supportedRatios = normalizedRatios,
        defaultRatio = normalizedRatios.firstOrNull { it == 1f } ?: normalizedRatios.first()
    )
}

internal fun detectPreviewBrightnessRange(
    characteristics: CameraCharacteristics
): PreviewBrightnessRange {
    val range = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        ?: return PreviewBrightnessRange.CONSERVATIVE
    if (range.lower == 0 && range.upper == 0) {
        return PreviewBrightnessRange.UNSUPPORTED
    }
    return PreviewBrightnessRange(minSteps = range.lower, maxSteps = range.upper)
}

private fun detectCameraLensProfiles(context: Context): List<CameraLensProfile> {
    val cameraManager = context.getSystemService(CameraManager::class.java) ?: return emptyList()
    return runCatching {
        cameraManager.cameraIdList.mapNotNull { cameraId ->
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val lensFacing = when (
                characteristics.get(CameraCharacteristics.LENS_FACING)
            ) {
                CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
                CameraCharacteristics.LENS_FACING_BACK -> LensFacing.BACK
                else -> null
            } ?: return@mapNotNull null

            CameraLensProfile(
                lensFacing = lensFacing,
                hasFlashUnit = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                zoomRatioCapability = detectZoomRatioCapability(characteristics),
                previewBrightnessRange = detectPreviewBrightnessRange(characteristics),
                availableStillCaptureOutputSizes = normalizeStillCaptureOutputSizes(
                    characteristics
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?.getOutputSizes(ImageFormat.JPEG)
                        ?.map { size ->
                            StillCaptureTargetResolution(
                                width = size.width,
                                height = size.height
                            )
                        }
                        .orEmpty()
                ),
                availableStillCaptureResolutionPresets = resolveAvailableStillCaptureResolutionPresetsFromSizes(
                    characteristics
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?.getOutputSizes(ImageFormat.JPEG)
                        ?.toList()
                        .orEmpty()
                ),
                videoSpecConstraints = detectVideoSpecConstraints(
                    cameraId = cameraId,
                    characteristics = characteristics
                )
            )
        }
    }.getOrDefault(emptyList())
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
            MultiFrameMergePlaceholderPostProcessor(),
            PipelineMetadataPostProcessor()
        )
    ),
    private val livePreviewFrameSource: com.opencamera.app.camera.live.LivePreviewFrameSource? = null,
    private val sceneMaskSource: PreviewSceneMaskSource? = null
) : CameraDeviceAdapter {
    private val adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var boundCamera: Camera? = null
    private var activeRecording: Recording? = null
    private var activeVideoPlan: ShotPlan? = null
    private val lifecycleInterruptedShotIds = mutableSetOf<String>()
    private var currentTorchEnabled = false
    private var currentGraph: DeviceGraphSpec? = null
    private var boundLifecycleOwner: LifecycleOwner? = null
    private var boundPreviewView: PreviewView? = null
    private var previewStreamObserver: Observer<PreviewView.StreamState>? = null
    private var cameraStateLiveData: LiveData<CameraState>? = null
    private var cameraStateObserver: Observer<CameraState>? = null
    private var lastCameraRuntimeIssueSignature: String? = null
    private var currentStillCaptureQuality: StillCaptureQualityPreference? = null
    private var currentStillCaptureResolutionPreset: StillCaptureResolutionPreset? = null
    private var currentStillCaptureOutputSize: StillCaptureOutputSize? = null
    private var currentManualCaptureConfig: Camera2ManualCaptureConfig? = null
    private var currentVideoSpec: VideoSpec? = null
    private var previewSnapshotGeneration: Int = 0
    private var suppressPreviewStateEvents = false
    private var currentOutputRotation: com.opencamera.core.device.CameraOutputRotation =
        com.opencamera.core.device.CameraOutputRotation.ROTATION_0
    private var firstFrameReportedForCurrentBind = false
    private var bindStartElapsedRealtimeNanos: Long = 0L
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
        val provider = ProcessCameraProvider.getInstance(context).await()
        runCatching {
            withContext(Dispatchers.Main.immediate) {
                bindGraphInternal(
                    provider = provider,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    deviceGraph = deviceGraph,
                    stillCaptureQuality = resolvedStillCaptureQuality(deviceGraph),
                    stillCaptureResolutionPreset = resolvedStillCaptureResolutionPreset(deviceGraph),
                    resetPreviewObserver = true,
                    resetPreviewMetrics = true,
                    closeActiveRecording = true
                )
            }
        }.getOrElse { throwable ->
            invalidateCachedProviderState(
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
                val activePlan = activeVideoPlan
                emitShotFailure(
                    shotId = activePlan?.request?.shotId ?: command.shotId,
                    mediaType = activePlan?.request?.mediaType ?: MediaType.VIDEO,
                    reason = throwable.message ?: "Stop video command failed"
                )
            }

            is DeviceCommand.UpdateZoomRatio -> runCatching {
                updateZoomRatio(command.zoomRatio)
            }.onFailure { throwable ->
                val issue = classifyPreviewBindingFailure(throwable)
                invalidateCachedProviderState(issue)
                _events.emit(
                    DeviceEvent.RuntimeIssue(
                        issue
                    )
                )
            }

            is DeviceCommand.ApplyPreviewMetering -> runCatching {
                applyPreviewMetering(command.request)
            }.onFailure { throwable ->
                _events.emit(
                    DeviceEvent.PreviewMeteringCompleted(
                        previewMeteringResult(
                            request = command.request,
                            status = PreviewMeteringResultStatus.FAILED,
                            reason = throwable.message ?: "Preview metering failed"
                        )
                    )
                )
            }
            is DeviceCommand.UpdateOutputRotation -> runCatching {
                applyOutputRotation(command.rotation)
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
                applyPreviewBrightness(command.request)
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

    private suspend fun applyPreviewMetering(request: PreviewMeteringRequest) {
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

        _events.emit(DeviceEvent.PreviewMeteringCompleted(result))
    }

    private suspend fun applyPreviewBrightness(request: PreviewBrightnessRequest) {
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
                        ?.exposureCompensation == ManualControlSupport.SAVED_ONLY
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
        _events.emit(DeviceEvent.PreviewBrightnessApplied(result))
    }

    private fun applyOutputRotation(rotation: com.opencamera.core.device.CameraOutputRotation) {
        if (rotation == currentOutputRotation) return
        currentOutputRotation = rotation
        val surfaceRotation = mapOutputRotationToSurface(rotation)
        imageCapture?.targetRotation = surfaceRotation
        videoCapture?.targetRotation = surfaceRotation
    }

    override suspend fun release() {
        withContext(Dispatchers.Main.immediate) {
            suppressPreviewStateEvents = true
            removePreviewStreamObserver()
            removeCameraStateObserver()
            applyVideoTorch(false)
            livePreviewFrameSource?.stop("release")
            sceneMaskSource?.stop("release")
            if (activeRecording != null) {
                activeVideoPlan?.request?.shotId?.let { lifecycleInterruptedShotIds.add(it) }
                val interruptedPlan = activeVideoPlan
                activeRecording?.close()
                activeRecording = null
                activeVideoPlan = null
                interruptedPlan?.let { plan ->
                    _events.tryEmit(
                        DeviceEvent.ShotFailed(
                            shotId = plan.request.shotId,
                            mediaType = plan.request.mediaType,
                            reason = "Recording interrupted by lifecycle stop"
                        )
                    )
                }
            }
            cameraProvider?.unbindAll()
            cameraProvider = null
            imageCapture = null
            videoCapture = null
            boundCamera = null
            currentGraph = null
            boundLifecycleOwner = null
            boundPreviewView = null
            currentStillCaptureQuality = null
            currentStillCaptureResolutionPreset = null
            currentStillCaptureOutputSize = null
            currentManualCaptureConfig = null
            currentVideoSpec = null
            currentTorchEnabled = false
            firstFrameReportedForCurrentBind = false
            bindStartElapsedRealtimeNanos = 0L
            suppressPreviewStateEvents = false
        }
    }

    private suspend fun updateZoomRatio(zoomRatio: Float) {
        val normalizedZoomRatio = normalizedZoomRatioValue(zoomRatio)
        val activeGraph = currentGraph ?: return
        currentGraph = activeGraph.copy(
            preview = activeGraph.preview.copy(
                zoomRatio = normalizedZoomRatio
            )
        )
        val camera = boundCamera ?: return
        camera.cameraControl.setZoomRatio(normalizedZoomRatio).await()
    }

    override fun boundGraph(): DeviceGraphSpec? = currentGraph

    private suspend fun executeShot(plan: ShotPlan) {
        val requestedAt = SystemClock.elapsedRealtime()
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

    private suspend fun captureStillImage(
        plan: ShotPlan,
        deviceRequest: DeviceShotRequest,
        requestedAt: Long
    ) {
        ensureStillCaptureRequest(deviceRequest)
        val capture = imageCapture ?: error("ImageCapture is not bound")
        applyStillCaptureFlashMode(capture, deviceRequest.flashMode)
        _events.emit(DeviceEvent.ShotStarted(plan.request))
        captureCaptureFeedbackSnapshot(plan.request.shotId)
        val adapterManualDiagnostics = manualCaptureExecutionDiagnostics(
            requestedParams = deviceRequest.manualCaptureParams,
            capabilityMatrix = deviceRequest.manualControlCapabilities
        )

        val execution = when (plan.request.shotKind) {
            ShotKind.STILL_CAPTURE -> {
                val request = createPhotoOutputRequest(plan.saveTask.saveRequest)
                captureSinglePhoto(capture, request)
            }

            ShotKind.MULTI_FRAME_CAPTURE -> {
                captureMultiFrameStillImage(
                    capture = capture,
                    plan = plan,
                    deviceRequest = deviceRequest
                )
            }

            ShotKind.LIVE_PHOTO -> captureLivePhoto(capture, plan)

            ShotKind.VIDEO_RECORDING -> error("Video shots are not handled by still capture path")
        }

        when (execution) {
            is PhotoCaptureOutcome.Failure -> {
                cleanupAbsoluteFilePaths(execution.cleanupPaths)
                emitShotFailure(
                    shotId = plan.request.shotId,
                    mediaType = plan.request.mediaType,
                    reason = execution.reason
                )
                return
            }

            is PhotoCaptureOutcome.Success -> {
                runCatching {
                    emitShotCompleted(
                        plan = plan,
                        outputPath = execution.outputPath,
                        outputHandle = execution.outputHandle,
                        livePhotoBundle = execution.livePhotoBundle,
                        intermediateOutputPaths = execution.intermediateOutputPaths,
                        deviceDiagnostics = deviceRequest.diagnostics +
                            adapterManualDiagnostics +
                            execution.diagnostics,
                        requestedAtElapsedMillis = requestedAt,
                        deviceCaptureStartedAtElapsedMillis = execution.deviceCaptureStartedAtElapsedMillis,
                        deviceCaptureCompletedAtElapsedMillis = execution.deviceCaptureCompletedAtElapsedMillis
                    )
                }.getOrElse { throwable ->
                    cleanupStillCaptureArtifacts(
                        outputPath = execution.outputPath,
                        outputHandle = execution.outputHandle,
                        livePhotoBundle = execution.livePhotoBundle,
                        intermediateOutputPaths = execution.intermediateOutputPaths,
                        deleteContentUri = ::deleteContentUriQuietly
                    )
                    throw throwable
                }
            }
        }
    }

    private suspend fun captureLivePhoto(
        capture: ImageCapture,
        plan: ShotPlan
    ): PhotoCaptureOutcome {
        val request = createPhotoOutputRequest(plan.saveTask.saveRequest)
        return when (val result = captureSinglePhoto(capture, request)) {
            is PhotoCaptureOutcome.Failure -> result
            is PhotoCaptureOutcome.Success -> {
                val resolvedSpec = plan.saveTask.livePhotoSpec
                    ?: com.opencamera.core.media.LivePhotoCaptureSpec()

                // Use frame source if available
                val frameSource = livePreviewFrameSource
                val motionSourceResult = if (frameSource != null) {
                    resolveLiveMotionSource(
                        frameSource = frameSource,
                        shutterTimestampNanos = System.nanoTime(),
                        spec = resolvedSpec
                    )
                } else {
                    LiveMotionSourceResult(
                        source = LiveMotionSource.METADATA_ONLY,
                        selectedFrameSet = com.opencamera.core.media.SelectedFrameSet(
                            frames = emptyList(),
                            preShutterCount = 0,
                            postShutterCount = 0,
                            coveredPreShutterMillis = 0,
                            coveredPostShutterMillis = 0,
                            diagnostics = listOf("frame-source:not-configured")
                        ),
                        ringBufferDepthMillis = 0,
                        postShutterBudgetMillis = 0,
                        diagnostics = listOf("live:source=metadata-only")
                    )
                }

                val temporalPlan = planLiveTemporalAssembly(
                    LiveTemporalPlannerInput(
                        captureSpec = resolvedSpec,
                        availableSource = motionSourceResult.source,
                        ringBufferDepthMillis = motionSourceResult.ringBufferDepthMillis,
                        postShutterBudgetMillis = motionSourceResult.postShutterBudgetMillis
                    )
                )
                val livePhotoBundle = createLivePhotoBundle(
                    stillPath = result.outputPath,
                    stillOutputHandle = result.outputHandle,
                    relativePath = plan.saveTask.saveRequest.relativePath,
                    livePhotoSpec = plan.saveTask.livePhotoSpec,
                    bundleStatus = temporalPlan.expectedBundleStatus,
                    temporalWindow = temporalPlan.temporalWindow
                )

                val motionPhotoMaterialization = materializeMotionPhotoBundleIfPossible(
                    bundle = livePhotoBundle,
                    motionSourceResult = motionSourceResult,
                    prepareMotionSegment = { frames, motionPath ->
                        (frameSource as? com.opencamera.app.camera.live.MotionSegmentFrameSource)
                            ?.materializeMotionSegment(frames, motionPath)
                            ?: Result.failure(
                                IllegalStateException("Motion segment source is not available")
                            )
                    }
                ) { motionPath ->
                    com.opencamera.app.camera.live.MotionPhotoFileMaterializer().materialize(
                        stillPath = result.outputPath,
                        motionPath = motionPath,
                        outputPath = result.outputPath.replace(".jpg", "_MP.jpg"),
                        spec = com.opencamera.core.media.MotionPhotoContainerSpec(
                            motionLengthBytes = 0
                        ),
                        cleanupTempMotion = false
                    )
                }
                val finalBundle = motionPhotoMaterialization.bundle

                val sidecarResult = runCatching {
                    materializeLivePhotoSidecar(
                        bundle = finalBundle,
                        writeContentUriPayload = ::writeTextToContentUri
                    )
                }

                val diagnosisBuilder = buildList {
                    add("device:live-photo=bundle")
                    addAll(motionSourceResult.diagnostics)
                    addAll(motionPhotoMaterialization.diagnostics)
                    if (sidecarResult.isSuccess) {
                        add(
                            if (File(finalBundle.sidecarPath).isAbsolute) {
                                "device:live-sidecar=materialized"
                            } else {
                                "device:live-sidecar=planned"
                            }
                        )
                    } else {
                        val sidecarError = sidecarResult.exceptionOrNull()?.message ?: "unknown"
                        add("device:live-sidecar=failed:$sidecarError")
                        add("device:live-photo=still-only-fallback")
                        finalBundle.sidecarHandle.contentUri?.let { deleteContentUriQuietly(it) }
                    }
                }

                // Sidecar failure is non-fatal: still photo was already saved.
                // Return Success with livePhotoBundle = null so consumers
                // treat it as a regular still photo, not a live photo.
                result.copy(
                    livePhotoBundle = if (sidecarResult.isSuccess) finalBundle else null,
                    diagnostics = result.diagnostics + diagnosisBuilder
                )
            }
        }
    }

    private suspend fun captureMultiFrameStillImage(
        capture: ImageCapture,
        plan: ShotPlan,
        deviceRequest: DeviceShotRequest
    ): PhotoCaptureOutcome {
        val executionPlan = multiFrameExecutionPlanner.plan(deviceRequest)
        val temporaryOutputs = MultiFrameTemporaryOutputTracker()
        var finalOutputPath: String? = null
        var finalOutputHandle: MediaOutputHandle? = null
        var firstFrameDeviceCaptureStartedAt: Long = 0L
        var lastFrameDeviceCaptureCompletedAt: Long = 0L

        try {
            executionPlan.steps.forEachIndexed { stepIndex, step ->
                val request = when (step.outputRole) {
                    MultiFrameOutputRole.TEMPORARY -> createTemporaryPhotoOutputRequest(
                        shotId = plan.request.shotId,
                        frameIndex = step.frameIndex
                    )

                    MultiFrameOutputRole.FINAL_OUTPUT -> createPhotoOutputRequest(plan.saveTask.saveRequest)
                }
                temporaryOutputs.register(request.cleanupFile)

                when (val result = captureSinglePhoto(capture, request)) {
                    is PhotoCaptureOutcome.Failure -> {
                        temporaryOutputs.cleanup()
                        return result
                    }
                    is PhotoCaptureOutcome.Success -> {
                        if (stepIndex == 0) {
                            firstFrameDeviceCaptureStartedAt = result.deviceCaptureStartedAtElapsedMillis
                        }
                        lastFrameDeviceCaptureCompletedAt = result.deviceCaptureCompletedAtElapsedMillis
                        if (step.outputRole == MultiFrameOutputRole.FINAL_OUTPUT) {
                            finalOutputPath = result.outputPath
                            finalOutputHandle = result.outputHandle
                        }
                    }
                }

                val hasNextStep = stepIndex < executionPlan.steps.lastIndex
                if (hasNextStep && executionPlan.interFrameDelayMillis > 0L) {
                    delay(executionPlan.interFrameDelayMillis)
                }
            }
        } catch (throwable: Throwable) {
            temporaryOutputs.cleanup()
            throw throwable
        }

        val resolvedFinalOutputPath = finalOutputPath
            ?: run {
                temporaryOutputs.cleanup()
                return PhotoCaptureOutcome.Failure("Multi-frame capture did not produce a final frame")
            }
        val resolvedFinalOutputHandle = finalOutputHandle
            ?: MediaOutputHandle(displayPath = resolvedFinalOutputPath)

        return PhotoCaptureOutcome.Success(
            outputPath = resolvedFinalOutputPath,
            outputHandle = resolvedFinalOutputHandle,
            diagnostics = executionPlan.toExecutionDiagnostics(),
            intermediateOutputPaths = temporaryOutputs.outputPaths(),
            deviceCaptureStartedAtElapsedMillis = firstFrameDeviceCaptureStartedAt,
            deviceCaptureCompletedAtElapsedMillis = lastFrameDeviceCaptureCompletedAt
        )
    }

    private suspend fun captureSinglePhoto(
        capture: ImageCapture,
        request: PhotoOutputRequest
    ): PhotoCaptureOutcome {
        return suspendCancellableCoroutine { continuation ->
            val deviceCaptureStartedAt = SystemClock.elapsedRealtime()
            capture.takePicture(
                request.outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val deviceCaptureCompletedAt = SystemClock.elapsedRealtime()
                        continuation.resume(
                            PhotoCaptureOutcome.Success(
                                outputPath = request.outputPath,
                                outputHandle = request.resolveOutputHandle(outputFileResults.savedUri),
                                deviceCaptureStartedAtElapsedMillis = deviceCaptureStartedAt,
                                deviceCaptureCompletedAtElapsedMillis = deviceCaptureCompletedAt
                            )
                        )
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resume(
                            PhotoCaptureOutcome.Failure(
                                reason = exception.message ?: "Unknown image capture error",
                                cleanupPaths = request.cleanupPaths()
                            )
                        )
                    }
                }
            )
        }
    }

    private suspend fun applyStillCaptureFlashMode(
        capture: ImageCapture,
        flashMode: CaptureFlashMode
    ) {
        withContext(Dispatchers.Main.immediate) {
            capture.flashMode = when (flashMode) {
                CaptureFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                CaptureFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                CaptureFlashMode.ON -> ImageCapture.FLASH_MODE_ON
            }
        }
    }

    private fun MultiFrameCaptureExecutionPlan.toExecutionDiagnostics(): List<String> {
        return listOf(
            "device:burst-executed=$totalFrameCount",
            "device:burst-temp-frames=$temporaryFrameCount",
            "device:burst-final-frame=$finalFrameIndex"
        )
    }

    private fun createTemporaryPhotoOutputRequest(
        shotId: String,
        frameIndex: Int
    ): PhotoOutputRequest {
        val tempDir = File(context.cacheDir, "multi-frame-captures").apply {
            mkdirs()
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val outputFile = File(
            tempDir,
            "${shotId}_frame_${frameIndex}_$stamp.jpg"
        )
        return PhotoOutputRequest(
            outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build(),
            outputPath = outputFile.absolutePath,
            outputHandle = MediaOutputHandle(
                displayPath = outputFile.absolutePath,
                filePath = outputFile.absolutePath
            ),
            cleanupFile = outputFile
        )
    }

    private suspend fun emitShotCompleted(
        plan: ShotPlan,
        outputPath: String,
        outputHandle: MediaOutputHandle = MediaOutputHandle(displayPath = outputPath),
        livePhotoBundle: LivePhotoBundle? = null,
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
        val timingNotes = buildList {
            val t = timedResult.timing
            val deviceStarted = t.deviceCaptureStartedAtElapsedMillis
            val deviceCompleted = t.deviceCaptureCompletedAtElapsedMillis
            val requested = t.requestedAtElapsedMillis
            if (deviceStarted != null && deviceCompleted != null && deviceStarted > 0 && deviceCompleted > 0) {
                add("timing:device=${deviceCompleted - deviceStarted}ms")
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
        _events.emit(DeviceEvent.ShotCompleted(resultWithTiming))
    }

    private suspend fun emitShotFailure(
        shotId: String,
        mediaType: MediaType,
        reason: String
    ) {
        applyVideoTorch(false)
        withContext(Dispatchers.Main.immediate) {
            activeRecording?.close()
            activeRecording = null
            activeVideoPlan = null
        }
        _events.emit(
            DeviceEvent.ShotFailed(
                shotId = shotId,
                mediaType = mediaType,
                reason = reason
            )
        )
    }

    private fun createPhotoOutputRequest(saveRequest: SaveRequest): PhotoOutputRequest {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = saveRequest.buildDisplayName(stamp)
        val outputPath = "${saveRequest.relativePath}/$displayName"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, saveRequest.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, saveRequest.relativePath)
            }
            PhotoOutputRequest(
                outputOptions = ImageCapture.OutputFileOptions.Builder(
                    context.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                ).build(),
                outputPath = outputPath,
                outputHandle = MediaOutputHandle(displayPath = outputPath)
            )
        } else {
            val outputDir = buildLegacyOutputDirectory(saveRequest).apply { mkdirs() }
            val outputFile = File(outputDir, displayName)
            PhotoOutputRequest(
                outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build(),
                outputPath = outputFile.absolutePath,
                outputHandle = MediaOutputHandle(
                    displayPath = outputFile.absolutePath,
                    filePath = outputFile.absolutePath
                )
            )
        }
    }

    private fun createLivePhotoBundle(
        stillPath: String,
        stillOutputHandle: MediaOutputHandle,
        relativePath: String,
        livePhotoSpec: com.opencamera.core.media.LivePhotoCaptureSpec?,
        bundleStatus: com.opencamera.core.media.LiveBundleStatus = com.opencamera.core.media.LiveBundleStatus.COMPLETE,
        temporalWindow: com.opencamera.core.media.LiveTemporalWindow? = null
    ): LivePhotoBundle {
        val resolvedSpec = livePhotoSpec ?: com.opencamera.core.media.LivePhotoCaptureSpec()
        val baseName = stillPath
            .substringAfterLast('/')
            .substringBeforeLast('.', missingDelimiterValue = stillPath.substringAfterLast('/'))
        val liveRelativeDir = relativePath.trimEnd('/')
        val isAbsoluteStillPath = File(stillPath).isAbsolute
        val basePath = stillPath.substringBeforeLast('.', missingDelimiterValue = stillPath)
        val motionPath = if (isAbsoluteStillPath) {
            "$basePath.live.mp4"
        } else {
            "$liveRelativeDir/$baseName.live.mp4"
        }
        val sidecarPath = if (isAbsoluteStillPath) {
            "$basePath.live.json"
        } else {
            "$liveRelativeDir/$baseName.live.json"
        }
        val motionHandle = if (isAbsoluteStillPath) {
            MediaOutputHandle(
                displayPath = motionPath,
                filePath = motionPath
            )
        } else {
            MediaOutputHandle(displayPath = motionPath)
        }
        val sidecarHandle = if (isAbsoluteStillPath) {
            MediaOutputHandle(
                displayPath = sidecarPath,
                filePath = sidecarPath
            )
        } else {
            createLivePhotoSidecarHandle(
                displayName = "$baseName.live.json",
                mimeType = resolvedSpec.sidecarMimeType,
                relativePath = liveRelativeDir,
                fallbackParentDir = context.getExternalFilesDir(null)
                    ?: context.filesDir
            )
        }
        return LivePhotoBundle(
            stillPath = stillPath,
            motionPath = motionPath,
            sidecarPath = sidecarPath,
            thumbnailPath = stillPath,
            motionDurationMillis = resolvedSpec.motionDurationMillis,
            motionMimeType = resolvedSpec.motionMimeType,
            sidecarMimeType = resolvedSpec.sidecarMimeType,
            motionHandle = motionHandle,
            sidecarHandle = sidecarHandle,
            thumbnailHandle = stillOutputHandle,
            bundleStatus = bundleStatus,
            temporalWindow = temporalWindow
        )
    }

    private fun createMediaStoreFileHandle(
        displayName: String,
        mimeType: String,
        relativePath: String
    ): MediaOutputHandle {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        val contentUri = checkNotNull(
            context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                values
            )
        ) {
            "Failed to create MediaStore companion asset for $displayName"
        }
        return MediaOutputHandle(
            displayPath = "$relativePath/$displayName",
            contentUri = contentUri.toString()
        )
    }

    private fun createLivePhotoSidecarHandle(
        displayName: String,
        mimeType: String,
        relativePath: String,
        fallbackParentDir: File
    ): MediaOutputHandle {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val contentUri = context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                values
            )
            if (contentUri != null) {
                return MediaOutputHandle(
                    displayPath = "$relativePath/$displayName",
                    contentUri = contentUri.toString()
                )
            }
        }
        val fallbackFile = File(fallbackParentDir, displayName)
        return MediaOutputHandle(
            displayPath = "$relativePath/$displayName",
            filePath = fallbackFile.absolutePath
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
        check(activeRecording == null) { "Video recording already in progress" }
        val currentVideoGraph = currentGraph ?: error("Video graph is not bound")
        val runtimeSceneSignal = videoSceneSignalProvider(currentVideoGraph)
        val runtimeVideoSpec = capabilitiesFor(currentVideoGraph).resolveRuntimeVideoSpec(
            base = currentVideoGraph.recording.videoSpec,
            sceneSignal = runtimeSceneSignal
        )
        ensureVideoRecordingRequest(runtimeVideoSpec)
        val capture = videoCapture ?: error("VideoCapture is not bound")
        val runtimeDiagnostics = buildList {
            if (runtimeSceneSignal.isLowLight) {
                add("device:video-scene=low-light")
            }
            if (runtimeVideoSpec.frameRate != currentVideoGraph.recording.videoSpec.frameRate) {
                add("device:video-runtime-fps=${runtimeVideoSpec.frameRate.storageKey}")
            }
            add("device:video-bound-fps=${runtimeVideoSpec.frameRate.storageKey}")
        }

        val request = createVideoOutputRequest(plan.saveTask.saveRequest)
        activeVideoPlan = plan
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
            if (currentGraph?.recording?.audioEnabledWhenPermitted == true && hasAudioPermission()) {
                pending = pending.withAudioEnabled()
            }
            applyVideoTorch(deviceRequest.torchEnabled)

            activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _events.tryEmit(DeviceEvent.ShotStarted(plan.request))
                    }

                    is VideoRecordEvent.Finalize -> {
                        adapterScope.launch {
                            applyVideoTorch(false)
                        }
                        val finalizeShotId = plan.request.shotId
                        val wasLifecycleInterrupted = lifecycleInterruptedShotIds.remove(finalizeShotId)
                        activeRecording = null
                        activeVideoPlan = null
                        if (!wasLifecycleInterrupted && event.hasError()) {
                            adapterScope.launch {
                                emitShotFailure(
                                    shotId = finalizeShotId,
                                    mediaType = plan.request.mediaType,
                                    reason = event.cause?.message ?: event.error.toString()
                                )
                            }
                        } else if (!wasLifecycleInterrupted) {
                            adapterScope.launch {
                                emitShotCompleted(
                                    plan = plan,
                                    outputPath = request.outputPath,
                                    outputHandle = request.resolveOutputHandle(
                                        event.outputResults.outputUri
                                    ),
                                    deviceDiagnostics = deviceRequest.diagnostics + runtimeDiagnostics,
                                    requestedAtElapsedMillis = requestedAt
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun stopActiveShot(shotId: String) {
        withContext(Dispatchers.Main.immediate) {
            val recording = activeRecording ?: error("No active recording to stop")
            val plan = activeVideoPlan ?: error("No active video shot plan")
            check(plan.request.shotId == shotId) {
                "Active recording ${plan.request.shotId} does not match stop request $shotId"
            }
            recording.stop()
        }
    }

    private suspend fun applyVideoTorch(enabled: Boolean) {
        withContext(Dispatchers.Main.immediate) {
            if (currentTorchEnabled == enabled) {
                return@withContext
            }
            val camera = boundCamera ?: return@withContext
            camera.cameraControl.enableTorch(enabled).await()
            currentTorchEnabled = enabled
        }
    }

    private fun createVideoOutputRequest(saveRequest: SaveRequest): VideoOutputRequest {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = saveRequest.buildDisplayName(stamp)
        val outputPath = "${saveRequest.relativePath}/$displayName"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, saveRequest.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, saveRequest.relativePath)
            }
            VideoOutputRequest.MediaStoreRequest(
                outputOptions = MediaStoreOutputOptions.Builder(
                    context.contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ).setContentValues(values).build(),
                outputPath = outputPath,
                outputHandle = MediaOutputHandle(displayPath = outputPath)
            )
        } else {
            val outputDir = buildLegacyOutputDirectory(saveRequest).apply { mkdirs() }
            val outputFile = File(outputDir, displayName)
            VideoOutputRequest.FileRequest(
                outputOptions = FileOutputOptions.Builder(outputFile).build(),
                outputPath = outputFile.absolutePath,
                outputHandle = MediaOutputHandle(
                    displayPath = outputFile.absolutePath,
                    filePath = outputFile.absolutePath
                )
            )
        }
    }

    private fun buildLegacyOutputDirectory(saveRequest: SaveRequest): File {
        val root = saveRequest.relativePath.substringBefore("/")
        val nested = saveRequest.relativePath.substringAfter("/", "")
        val baseDir = context.getExternalFilesDir(root) ?: context.filesDir
        return if (nested.isEmpty()) {
            baseDir
        } else {
            File(baseDir, nested)
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

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

    private fun handleCameraState(state: CameraState) {
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
        if (signature == lastCameraRuntimeIssueSignature) {
            return
        }
        lastCameraRuntimeIssueSignature = signature
        invalidateCachedProviderState(issue)
        _events.tryEmit(DeviceEvent.RuntimeIssue(issue))
    }

    private fun invalidateCachedProviderState(issue: DeviceRuntimeIssue) {
        if (!shouldInvalidateCachedProviderState(issue)) {
            return
        }
        cameraProvider = null
        boundCamera = null
        imageCapture = null
        videoCapture = null
        currentTorchEnabled = false
        removeCameraStateObserver()
    }

    private fun handlePreviewStreamState(state: PreviewView.StreamState) {
        if (suppressPreviewStateEvents) {
            return
        }

        when (state) {
            PreviewView.StreamState.STREAMING -> {
                if (firstFrameReportedForCurrentBind) {
                    return
                }
                firstFrameReportedForCurrentBind = true
                val firstFrameLatencyMillis = if (bindStartElapsedRealtimeNanos == 0L) {
                    0L
                } else {
                    (SystemClock.elapsedRealtimeNanos() - bindStartElapsedRealtimeNanos) / 1_000_000L
                }
                _events.tryEmit(
                    DeviceEvent.PreviewFirstFrameAvailable(firstFrameLatencyMillis)
                )
                if (currentGraph?.preview?.snapshotsEnabled == true) {
                    capturePreviewSnapshot()
                }
            }

            PreviewView.StreamState.IDLE -> {
                if (firstFrameReportedForCurrentBind && currentGraph != null) {
                    firstFrameReportedForCurrentBind = false
                    _events.tryEmit(
                        DeviceEvent.PreviewSurfaceLost("Preview stream returned to IDLE")
                    )
                }
            }
        }
    }

    private fun cameraSelectorFor(lensFacing: LensFacing): CameraSelector {
        return when (lensFacing) {
            LensFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            LensFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }

    private suspend fun ensureStillCaptureRequest(deviceRequest: DeviceShotRequest) {
        check(deviceRequest.template == CaptureTemplate.STILL_CAPTURE) {
            "Expected still capture request but was ${deviceRequest.template}"
        }
        val provider = cameraProvider ?: ProcessCameraProvider.getInstance(context).await()
        val lifecycleOwner = boundLifecycleOwner ?: error("LifecycleOwner is not attached")
        val previewView = boundPreviewView ?: error("PreviewView is not attached")
        val deviceGraph = currentGraph ?: error("Device graph is not bound")
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
        if (
            currentStillCaptureQuality == requestedQuality &&
            currentStillCaptureResolutionPreset == requestedResolutionPreset &&
            currentStillCaptureOutputSize == requestedOutputSize &&
            currentManualCaptureConfig == requestedManualCaptureConfig
        ) {
            return
        }

        withContext(Dispatchers.Main.immediate) {
            bindGraphInternal(
                provider = provider,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                deviceGraph = deviceGraph,
                stillCaptureQuality = requestedQuality,
                stillCaptureResolutionPreset = requestedResolutionPreset,
                manualCaptureConfigOverride = requestedManualCaptureConfig,
                resetPreviewObserver = false,
                resetPreviewMetrics = false,
                closeActiveRecording = false
            )
        }
    }

    private suspend fun ensureVideoRecordingRequest(runtimeVideoSpec: VideoSpec) {
        val provider = cameraProvider ?: ProcessCameraProvider.getInstance(context).await()
        val lifecycleOwner = boundLifecycleOwner ?: error("LifecycleOwner is not attached")
        val previewView = boundPreviewView ?: error("PreviewView is not attached")
        val deviceGraph = currentGraph ?: error("Device graph is not bound")
        check(deviceGraph.template == CaptureTemplate.VIDEO_RECORDING) {
            "Cannot apply video recording request while bound graph is ${deviceGraph.template}"
        }
        if (currentVideoSpec == runtimeVideoSpec) {
            return
        }

        withContext(Dispatchers.Main.immediate) {
            bindGraphInternal(
                provider = provider,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                deviceGraph = deviceGraph,
                stillCaptureQuality = deviceGraph.stillCapture.qualityPreference,
                stillCaptureResolutionPreset = deviceGraph.stillCapture.resolutionPreset,
                resetPreviewObserver = false,
                resetPreviewMetrics = false,
                closeActiveRecording = false,
                videoSpecOverride = runtimeVideoSpec
            )
        }
    }

    private fun bindGraphInternal(
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
        val selector = cameraSelectorFor(deviceGraph.preferredLensFacing)

        suppressPreviewStateEvents = true
        if (resetPreviewObserver) {
            removePreviewStreamObserver()
        }
        removeCameraStateObserver()

        val previewBuilder = Preview.Builder()
        Camera2Interop.Extender(previewBuilder).setCaptureRequestOption(
            CaptureRequest.CONTROL_AWB_MODE,
            CaptureRequest.CONTROL_AWB_MODE_AUTO
        )
        val preview = previewBuilder.build().also { useCase ->
            useCase.setSurfaceProvider(previewView.surfaceProvider)
        }

        livePreviewFrameSource?.stop("unbind")
        sceneMaskSource?.stop("unbind")
        provider.unbindAll()
        if (closeActiveRecording) {
            activeRecording?.close()
            activeRecording = null
            activeVideoPlan = null
        }

        val boundUseCaseCamera = when (deviceGraph.template) {
            CaptureTemplate.STILL_CAPTURE -> {
                val capture = createImageCapture(
                    deviceGraph = deviceGraph,
                    stillCaptureQuality = stillCaptureQuality,
                    stillCaptureResolutionPreset = stillCaptureResolutionPreset,
                    manualCaptureConfig = manualCaptureConfigOverride
                )

                // Build use cases list, including ImageAnalysis if live preview frame source is available
                val useCases = mutableListOf<androidx.camera.core.UseCase>(preview, capture)
                if (livePreviewFrameSource != null || sceneMaskSource != null) {
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(android.util.Size(720, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        // Scene mask processes first (needs raw ImageProxy for ML Kit input)
                        sceneMaskSource?.onAnalyzeFrame(imageProxy, rotation)
                        // Live preview copies YUV bytes and closes ImageProxy in its finally block
                        (livePreviewFrameSource as? CameraXLivePreviewFrameSource)?.onAnalyzeFrame(
                            imageProxy,
                            rotation
                        ) ?: imageProxy.close()
                    }
                    useCases.add(analysis)
                }

                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    *useCases.toTypedArray()
                )
                imageCapture = capture
                videoCapture = null
                currentStillCaptureQuality = stillCaptureQuality
                currentStillCaptureResolutionPreset = stillCaptureResolutionPreset
                currentStillCaptureOutputSize = resolvedStillCaptureOutputSize(
                    deviceGraph = deviceGraph,
                    availableOutputSizes = capabilitiesFor(deviceGraph).availableStillCaptureOutputSizes
                )
                currentManualCaptureConfig = manualCaptureConfigOverride
                currentVideoSpec = null
                camera
            }

            CaptureTemplate.VIDEO_RECORDING -> {
                val effectiveVideoSpec = videoSpecOverride ?: deviceGraph.recording.videoSpec
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.fromOrderedList(
                            orderedRecordingQualities(
                                effectiveVideoSpec.resolution.toRecordingQualityPreset()
                            )
                        )
                    )
                    .build()
                val capture = VideoCapture.Builder(recorder)
                    .setTargetFrameRate(targetFrameRateRange(effectiveVideoSpec))
                    .setTargetRotation(mapOutputRotationToSurface(currentOutputRotation))
                    .build()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    capture
                )
                imageCapture = null
                videoCapture = capture
                currentStillCaptureQuality = deviceGraph.stillCapture.qualityPreference
                currentStillCaptureResolutionPreset = deviceGraph.stillCapture.resolutionPreset
                currentStillCaptureOutputSize = deviceGraph.stillCapture.outputSize
                currentManualCaptureConfig = null
                currentVideoSpec = effectiveVideoSpec
                camera
            }
        }

        cameraProvider = provider
        boundCamera = boundUseCaseCamera
        observeCameraState(boundUseCaseCamera)
        boundUseCaseCamera.cameraControl.setZoomRatio(deviceGraph.preview.zoomRatio)
        currentTorchEnabled = false
        currentGraph = deviceGraph
        boundLifecycleOwner = lifecycleOwner
        boundPreviewView = previewView
        if (resetPreviewMetrics) {
            firstFrameReportedForCurrentBind = false
            bindStartElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        if (resetPreviewObserver || previewStreamObserver == null) {
            observePreviewStream(previewView, lifecycleOwner)
        }

        // Start live preview frame source for STILL_CAPTURE template
        if (deviceGraph.template == CaptureTemplate.STILL_CAPTURE) {
            livePreviewFrameSource?.start(
                com.opencamera.core.media.FrameBufferPolicy.LIVE_PREVIEW_DEFAULT
            )
            sceneMaskSource?.start(PreviewSceneMaskConfig())
        }

        suppressPreviewStateEvents = false
    }

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
            availableOutputSizes = capabilitiesFor(deviceGraph).availableStillCaptureOutputSizes
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
            .setTargetRotation(mapOutputRotationToSurface(currentOutputRotation))
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

    private fun captureCaptureFeedbackSnapshot(shotId: String) {
        val previewView = boundPreviewView ?: return
        adapterScope.launch {
            val bitmap = awaitPreviewBitmap(previewView) ?: return@launch
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
            bitmap.recycle()
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

    private fun capturePreviewSnapshot() {
        val previewView = boundPreviewView ?: return
        val capturedGeneration = ++previewSnapshotGeneration
        adapterScope.launch {
            val bitmap = awaitPreviewBitmap(previewView) ?: return@launch

            runCatching {
                savePreviewBitmap(bitmap)
            }.onSuccess { outputPath ->
                _events.emit(
                    DeviceEvent.PreviewSnapshotAvailable(
                        source = ThumbnailSource.PreviewSnapshot(outputPath),
                        generation = capturedGeneration
                    )
                )
            }
            bitmap.recycle()
        }
    }

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

    private suspend fun savePreviewBitmap(bitmap: Bitmap): String {
        return withContext(Dispatchers.IO) {
            val outputDir = File(context.cacheDir, "preview-thumbnails").apply {
                mkdirs()
            }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outputFile = File(outputDir, "preview_$stamp.jpg")
            FileOutputStream(outputFile).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                    "Preview snapshot compression failed"
                }
            }
            outputDir.listFiles { file ->
                file.isFile && file.name.startsWith("preview_") && file.name.endsWith(".jpg")
            }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(3)
                ?.forEach { it.delete() }
            outputFile.absolutePath
        }
    }

    private data class PhotoOutputRequest(
        val outputOptions: ImageCapture.OutputFileOptions,
        val outputPath: String,
        val outputHandle: MediaOutputHandle,
        val cleanupFile: File? = null
    ) {
        fun resolveOutputHandle(savedUri: Uri?): MediaOutputHandle {
            val uriString = savedUri
                ?.takeUnless { it == Uri.EMPTY }
                ?.toString()
            return resolvePhotoOutputHandle(outputHandle, uriString)
        }

        fun cleanupPaths(): List<String> {
            return buildList {
                cleanupFile?.absolutePath?.let(::add)
                outputHandle.filePath?.let(::add)
                outputPath.takeIf { File(it).isAbsolute }?.let(::add)
            }.distinct()
        }
    }

    private sealed interface PhotoCaptureOutcome {
        data class Success(
            val outputPath: String,
            val outputHandle: MediaOutputHandle,
            val diagnostics: List<String> = emptyList(),
            val intermediateOutputPaths: List<String> = emptyList(),
            val livePhotoBundle: LivePhotoBundle? = null,
            val deviceCaptureStartedAtElapsedMillis: Long = 0L,
            val deviceCaptureCompletedAtElapsedMillis: Long = 0L
        ) : PhotoCaptureOutcome

        data class Failure(
            val reason: String,
            val cleanupPaths: List<String> = emptyList()
        ) : PhotoCaptureOutcome
    }

    private sealed interface VideoOutputRequest {
        val outputPath: String
        val outputHandle: MediaOutputHandle

        data class MediaStoreRequest(
            val outputOptions: MediaStoreOutputOptions,
            override val outputPath: String,
            override val outputHandle: MediaOutputHandle
        ) : VideoOutputRequest

        data class FileRequest(
            val outputOptions: FileOutputOptions,
            override val outputPath: String,
            override val outputHandle: MediaOutputHandle
        ) : VideoOutputRequest
    }

    private fun VideoOutputRequest.resolveOutputHandle(outputUri: Uri?): MediaOutputHandle {
        val resolvedContentUri = outputUri.takeUnlessEmpty()?.toString()
        return if (resolvedContentUri == null) {
            outputHandle
        } else {
            outputHandle.copy(contentUri = resolvedContentUri)
        }
    }

    private fun Uri?.takeUnlessEmpty(): Uri? = this?.takeUnless { it == Uri.EMPTY }
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
