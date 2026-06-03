package com.opencamera.core.device

import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotPlan
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.StillCaptureResolutionOption
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.settings.ManualCaptureParams
import com.opencamera.core.settings.VideoSpec
import com.opencamera.core.settings.VideoSpecConstraints

data class CaptureReadiness(
    val shotId: String,
    val mediaType: com.opencamera.core.media.MediaType,
    val source: String,
    val elapsedTimestampMs: Long? = null
)

enum class PhotoLowLightStrategySupport {
    UNSUPPORTED,
    DEGRADED_SINGLE_FRAME,
    SUPPORTED_MULTI_FRAME
}

enum class SceneLightState {
    UNKNOWN,
    NORMAL,
    LOW_LIGHT
}

data class PhotoSceneSignal(
    val lightState: SceneLightState = SceneLightState.UNKNOWN,
    val brightnessScore: Float? = null,
    val source: String = "unknown"
)

fun DeviceCapabilities.photoLowLightStrategySupport(): PhotoLowLightStrategySupport = when {
    !supportsStillCapture -> PhotoLowLightStrategySupport.UNSUPPORTED
    supportsNightMultiFrame -> PhotoLowLightStrategySupport.SUPPORTED_MULTI_FRAME
    else -> PhotoLowLightStrategySupport.DEGRADED_SINGLE_FRAME
}

enum class CameraOutputRotation {
    ROTATION_0,
    ROTATION_90,
    ROTATION_180,
    ROTATION_270
}

enum class LensFacing {
    BACK,
    FRONT
}

/**
 * Represents a physical camera lens node on a multi-camera device.
 * [WIDE] is the primary 1x lens, [TELEPHOTO] is typically 2x,
 * [PERISCOPE] is typically 5x or higher.
 */
enum class LensNode(val tagValue: String, val label: String) {
    WIDE("wide", "Wide"),
    TELEPHOTO("telephoto", "Telephoto"),
    PERISCOPE("periscope", "Periscope")
}

/**
 * Describes whether a [LensNode] is available on the current device and
 * what zoom ratio range triggers it.
 */
data class LensNodeAvailability(
    val node: LensNode,
    val available: Boolean,
    /** Minimum zoom ratio that activates this node (inclusive after hysteresis). */
    val thresholdRatio: Float,
    /** Physical camera ID on the device, or null if unavailable. */
    val physicalCameraId: String? = null
)

enum class CaptureTemplate {
    STILL_CAPTURE,
    VIDEO_RECORDING
}

enum class StillCaptureResolutionSource(val tagValue: String) {
    STANDARD("standard"),
    HIGH_RESOLUTION("high-resolution"),
    MAXIMUM_RESOLUTION("maximum-resolution")
}

data class StillCaptureOutputSize(
    val width: Int,
    val height: Int,
    val resolutionSource: StillCaptureResolutionSource = StillCaptureResolutionSource.STANDARD
) {
    val pixelCount: Long
        get() = width.toLong() * height.toLong()
}

data class PhysicalStillCaptureOutputProbe(
    val cameraId: String,
    val outputSizes: List<StillCaptureOutputSize> = emptyList()
)

data class StillCaptureCameraProbe(
    val cameraId: String,
    val lensFacing: LensFacing? = null,
    val physicalCameraIds: Set<String> = emptySet(),
    val outputSizes: List<StillCaptureOutputSize> = emptyList(),
    val physicalOutputProbes: List<PhysicalStillCaptureOutputProbe> = emptyList()
)

enum class ManualControlSupport(
    val tagValue: String
) {
    UNSUPPORTED("unsupported"),
    SAVED_ONLY("saved-only"),
    APPLY("applied")
}

data class ManualControlCapabilityMatrix(
    val raw: ManualControlSupport,
    val iso: ManualControlSupport,
    val shutter: ManualControlSupport,
    val exposureCompensation: ManualControlSupport,
    val focusDistance: ManualControlSupport,
    val aperture: ManualControlSupport,
    val whiteBalance: ManualControlSupport
) {
    val hasAppliedControls: Boolean
        get() = listOf(
            raw,
            iso,
            shutter,
            exposureCompensation,
            focusDistance,
            aperture,
            whiteBalance
        ).any { support -> support == ManualControlSupport.APPLY }

    companion object {
        val CAMERA2_INTEROP_DEFAULT = ManualControlCapabilityMatrix(
            raw = ManualControlSupport.SAVED_ONLY,
            iso = ManualControlSupport.APPLY,
            shutter = ManualControlSupport.APPLY,
            exposureCompensation = ManualControlSupport.APPLY,
            focusDistance = ManualControlSupport.APPLY,
            aperture = ManualControlSupport.APPLY,
            whiteBalance = ManualControlSupport.SAVED_ONLY
        )

        val SAVED_ONLY_DEFAULT = ManualControlCapabilityMatrix(
            raw = ManualControlSupport.SAVED_ONLY,
            iso = ManualControlSupport.SAVED_ONLY,
            shutter = ManualControlSupport.SAVED_ONLY,
            exposureCompensation = ManualControlSupport.SAVED_ONLY,
            focusDistance = ManualControlSupport.SAVED_ONLY,
            aperture = ManualControlSupport.SAVED_ONLY,
            whiteBalance = ManualControlSupport.SAVED_ONLY
        )
    }
}

data class ManualControlRequestSupportSummary(
    val applied: List<String>,
    val savedOnly: List<String>,
    val unsupported: List<String>
) {
    val overallStatus: ManualControlSupport
        get() = when {
            applied.isNotEmpty() -> {
                if (savedOnly.isEmpty() && unsupported.isEmpty()) {
                    ManualControlSupport.APPLY
                } else {
                    ManualControlSupport.SAVED_ONLY
                }
            }

            savedOnly.isNotEmpty() -> ManualControlSupport.SAVED_ONLY
            else -> ManualControlSupport.UNSUPPORTED
        }
}

data class DeviceCapabilities(
    val supportsStillCapture: Boolean = true,
    val supportsVideoRecording: Boolean = true,
    val supportsPreviewSnapshots: Boolean = true,
    val supportsAudioRecording: Boolean = true,
    val zoomRatioCapability: ZoomRatioCapability = ZoomRatioCapability(),
    val previewBrightnessRange: PreviewBrightnessRange = PreviewBrightnessRange.CONSERVATIVE,
    val supportsManualControls: Boolean = true,
    val manualControlCapabilities: ManualControlCapabilityMatrix? = null,
    val supportsDocumentScanEnhancement: Boolean = true,
    val supportsPortraitDepthEffect: Boolean = true,
    val supportsNightMultiFrame: Boolean = true,
    val supportsFlashControl: Boolean = true,
    val availableLensFacings: Set<LensFacing> = setOf(LensFacing.BACK),
    val availableStillCaptureOutputSizes: List<StillCaptureOutputSize> = emptyList(),
    val stillCaptureCameraProbes: List<StillCaptureCameraProbe> = emptyList(),
    val availableStillCaptureResolutionOptions: List<StillCaptureResolutionOption> = emptyList(),
    val availableStillCaptureResolutionPresets: Set<StillCaptureResolutionPreset> =
        StillCaptureResolutionPreset.entries.toSet(),
    val videoSpecConstraints: VideoSpecConstraints = VideoSpecConstraints(
        supportedFrameRatesByResolution = linkedMapOf(
            com.opencamera.core.settings.VideoResolution.UHD_4K to setOf(
                com.opencamera.core.settings.VideoFrameRate.FPS_25,
                com.opencamera.core.settings.VideoFrameRate.FPS_30
            ),
            com.opencamera.core.settings.VideoResolution.FHD_1080P to setOf(
                com.opencamera.core.settings.VideoFrameRate.FPS_25,
                com.opencamera.core.settings.VideoFrameRate.FPS_30,
                com.opencamera.core.settings.VideoFrameRate.FPS_60
            ),
            com.opencamera.core.settings.VideoResolution.HD_720P to setOf(
                com.opencamera.core.settings.VideoFrameRate.FPS_25,
                com.opencamera.core.settings.VideoFrameRate.FPS_30,
                com.opencamera.core.settings.VideoFrameRate.FPS_60
            ),
            com.opencamera.core.settings.VideoResolution.SD_480P to setOf(
                com.opencamera.core.settings.VideoFrameRate.FPS_25,
                com.opencamera.core.settings.VideoFrameRate.FPS_30
            )
        ),
        dynamicPolicies = setOf(com.opencamera.core.settings.DynamicVideoFpsPolicy.LOCKED),
        audioProfiles = setOf(com.opencamera.core.settings.AudioProfile.STANDARD)
    )
) {
    val resolvedManualControlCapabilities: ManualControlCapabilityMatrix
        get() = manualControlCapabilities ?: if (supportsManualControls) {
            ManualControlCapabilityMatrix.CAMERA2_INTEROP_DEFAULT
        } else {
            ManualControlCapabilityMatrix.SAVED_ONLY_DEFAULT
        }

    val supportsAppliedManualControls: Boolean
        get() = resolvedManualControlCapabilities.hasAppliedControls

    companion object {
        val DEFAULT = DeviceCapabilities()
    }
}

fun ManualControlCapabilityMatrix.supportSummary(
    params: ManualCaptureParams
): ManualControlRequestSupportSummary {
    val applied = mutableListOf<String>()
    val savedOnly = mutableListOf<String>()
    val unsupported = mutableListOf<String>()

    fun classify(controlName: String, support: ManualControlSupport) {
        when (support) {
            ManualControlSupport.APPLY -> applied += controlName
            ManualControlSupport.SAVED_ONLY -> savedOnly += controlName
            ManualControlSupport.UNSUPPORTED -> unsupported += controlName
        }
    }

    if (params.rawEnabled) {
        classify("raw", raw)
    }
    if (params.iso != null) {
        classify("iso", iso)
    }
    if (params.shutterSpeedMillis != null) {
        classify("shutter", shutter)
    }
    if (params.exposureCompensationSteps != null) {
        classify("ev", exposureCompensation)
    }
    if (params.focusDistanceDiopters != null) {
        classify("focus", focusDistance)
    }
    if (params.apertureFNumber != null) {
        classify("aperture", aperture)
    }
    if (params.whiteBalanceKelvin != null) {
        classify("wb", whiteBalance)
    }

    return ManualControlRequestSupportSummary(
        applied = applied,
        savedOnly = savedOnly,
        unsupported = unsupported
    )
}

fun ManualCaptureParams.filterToExecutableCapabilities(
    capabilities: ManualControlCapabilityMatrix
): ManualCaptureParams? {
    val filtered = copy(
        rawEnabled = rawEnabled && capabilities.raw == ManualControlSupport.APPLY,
        iso = iso.takeIf { capabilities.iso == ManualControlSupport.APPLY },
        shutterSpeedMillis = shutterSpeedMillis.takeIf {
            capabilities.shutter == ManualControlSupport.APPLY
        },
        exposureCompensationSteps = exposureCompensationSteps.takeIf {
            capabilities.exposureCompensation == ManualControlSupport.APPLY
        },
        focusDistanceDiopters = focusDistanceDiopters.takeIf {
            capabilities.focusDistance == ManualControlSupport.APPLY
        },
        apertureFNumber = apertureFNumber.takeIf {
            capabilities.aperture == ManualControlSupport.APPLY
        },
        whiteBalanceKelvin = whiteBalanceKelvin.takeIf {
            capabilities.whiteBalance == ManualControlSupport.APPLY
        }
    )
    return filtered.takeUnless(ManualCaptureParams::isAutoRequest)
}

private fun ManualCaptureParams.isAutoRequest(): Boolean {
    return !rawEnabled &&
        iso == null &&
        shutterSpeedMillis == null &&
        exposureCompensationSteps == null &&
        focusDistanceDiopters == null &&
        apertureFNumber == null &&
        whiteBalanceKelvin == null
}

data class PreviewBrightnessRange(
    val minSteps: Int,
    val maxSteps: Int
) {
    fun clamp(value: Int): Int = value.coerceIn(minSteps, maxSteps)

    companion object {
        val CONSERVATIVE = PreviewBrightnessRange(-2, 2)
        val UNSUPPORTED = PreviewBrightnessRange(0, 0)
    }
}

enum class PreviewBrightnessResultStatus {
    APPLIED,
    DEGRADED_SAVED_ONLY,
    FAILED,
    UNSUPPORTED
}

data class PreviewBrightnessRequest(
    val requestId: String,
    val exposureCompensationSteps: Int
)

data class PreviewBrightnessResult(
    val requestId: String,
    val exposureCompensationSteps: Int,
    val status: PreviewBrightnessResultStatus,
    val reason: String? = null
)

data class PreviewMeteringPoint(
    val normalizedX: Float,
    val normalizedY: Float
) {
    fun clamped(): PreviewMeteringPoint = PreviewMeteringPoint(
        normalizedX = normalizedX.coerceIn(0f, 1f),
        normalizedY = normalizedY.coerceIn(0f, 1f)
    )
}

enum class PreviewMeteringMode {
    FOCUS_AND_AUTO_EXPOSURE,
    AUTO_EXPOSURE_ONLY
}

data class PreviewMeteringRequest(
    val requestId: String,
    val point: PreviewMeteringPoint,
    val mode: PreviewMeteringMode = PreviewMeteringMode.FOCUS_AND_AUTO_EXPOSURE,
    val autoCancelMillis: Long = 3_000L
)

enum class PreviewMeteringResultStatus {
    SUCCEEDED,
    DEGRADED_AUTO_EXPOSURE_ONLY,
    FAILED,
    UNSUPPORTED
}

data class PreviewMeteringResult(
    val requestId: String,
    val point: PreviewMeteringPoint,
    val status: PreviewMeteringResultStatus,
    val reason: String? = null
)

enum class PreviewStreamAspect(
    val tagValue: String,
    val width: Int,
    val height: Int
) {
    FULL("full", 0, 0),
    RATIO_4_3("4:3", 4, 3),
    RATIO_16_9("16:9", 16, 9),
    RATIO_1_1("1:1", 1, 1)
}

data class PreviewConfig(
    val snapshotsEnabled: Boolean = true,
    val zoomRatio: Float = 1f,
    /** Discrete preview base zoom ratio, always ≤ zoomRatio, jumps at physical lens switch points. */
    val previewZoomRatio: Float = 1f,
    /** The physical lens node requested for the current zoom level. null = not tracked / wide default. */
    val requestedLensNode: LensNode? = null,
    val streamAspect: PreviewStreamAspect = PreviewStreamAspect.FULL
)

enum class ZoomControlSupport(
    val tagValue: String,
    val label: String
) {
    UNSUPPORTED(
        tagValue = "unsupported",
        label = "Unsupported"
    ),
    DISCRETE_PRESET(
        tagValue = "discrete-preset",
        label = "Preset steps"
    ),
    CONTINUOUS(
        tagValue = "continuous",
        label = "Continuous"
    )
}

data class ZoomRatioCapability(
    val support: ZoomControlSupport = ZoomControlSupport.UNSUPPORTED,
    val supportedRatios: List<Float> = listOf(1f),
    val defaultRatio: Float = 1f,
    /** Discrete preview stream baselines used while capture zoom remains continuous. */
    val previewBaseRatios: List<Float> = emptyList(),
    /** Maps lens nodes to their availability and threshold ratios. Empty when device has no multi-camera. */
    val lensNodeMap: Map<LensNode, LensNodeAvailability> = emptyMap()
) {
    val normalizedSupportedRatios: List<Float>
        get() = supportedRatios
            .map(::normalizedZoomRatioValue)
            .filter { it > 0f }
            .distinct()
            .sorted()
            .ifEmpty { listOf(1f) }

    val normalizedPreviewBaseRatios: List<Float>
        get() = previewBaseRatios
            .map(::normalizedZoomRatioValue)
            .filter { it > 0f }
            .distinct()
            .sorted()

    val resolvedDefaultRatio: Float
        get() {
            val normalizedDefault = normalizedZoomRatioValue(defaultRatio)
            return normalizedSupportedRatios.firstOrNull { ratio ->
                ratio == normalizedDefault
            } ?: normalizedSupportedRatios.first()
        }

    val isSwitchingSupported: Boolean
        get() = support != ZoomControlSupport.UNSUPPORTED && normalizedSupportedRatios.size > 1
}

fun normalizedZoomRatioValue(value: Float): Float {
    return String.format(java.util.Locale.US, "%.1f", value).toFloat()
}

fun resolvedZoomRatioSelection(
    current: Float?,
    capability: ZoomRatioCapability
): Float {
    val availableRatios = capability.normalizedSupportedRatios
    if (availableRatios.isEmpty()) {
        return 1f
    }
    val normalizedCurrent = current?.let(::normalizedZoomRatioValue)
    if (normalizedCurrent != null) {
        if (capability.support == ZoomControlSupport.CONTINUOUS) {
            return normalizedCurrent.coerceIn(availableRatios.first(), availableRatios.last())
        }
        if (normalizedCurrent in availableRatios) {
            return normalizedCurrent
        }
    }
    return capability.resolvedDefaultRatio
}

fun nextZoomRatio(
    current: Float,
    capability: ZoomRatioCapability
): Float {
    val availableRatios = capability.normalizedSupportedRatios
    if (availableRatios.size < 2) {
        return availableRatios.firstOrNull() ?: normalizedZoomRatioValue(current)
    }
    val normalizedCurrent = normalizedZoomRatioValue(current)
    val currentIndex = availableRatios.indexOf(normalizedCurrent).takeIf { it >= 0 } ?: -1
    return availableRatios[(currentIndex + 1).mod(availableRatios.size)]
}

data class StillCaptureConfig(
    val resolutionOption: StillCaptureResolutionOption? = null,
    val outputSize: StillCaptureOutputSize? = null,
    val qualityPreference: com.opencamera.core.media.StillCaptureQualityPreference = com.opencamera.core.media.StillCaptureQualityPreference.QUALITY,
    val resolutionPreset: com.opencamera.core.media.StillCaptureResolutionPreset = com.opencamera.core.media.StillCaptureResolutionPreset.LARGE_12MP
)

enum class RecordingQualityPreset(
    val tagValue: String,
    val label: String
) {
    UHD(
        tagValue = "uhd",
        label = "UHD"
    ),
    FHD(
        tagValue = "fhd",
        label = "FHD"
    ),
    HD(
        tagValue = "hd",
        label = "HD"
    ),
    SD(
        tagValue = "sd",
        label = "SD"
    )
}

data class RecordingConfig(
    val audioEnabledWhenPermitted: Boolean = false,
    val requestedVideoSpec: VideoSpec = VideoSpec(),
    val videoSpec: VideoSpec = requestedVideoSpec,
    val qualityPreset: RecordingQualityPreset = videoSpec.resolution.toRecordingQualityPreset()
)

data class DeviceGraphSpec(
    val template: CaptureTemplate,
    val preferredLensFacing: LensFacing = LensFacing.BACK,
    val stillCapture: StillCaptureConfig = StillCaptureConfig(),
    val preview: PreviewConfig = PreviewConfig(),
    val recording: RecordingConfig = RecordingConfig()
) {
    companion object {
        fun stillCapture(
            preferredLensFacing: LensFacing = LensFacing.BACK,
            enablePreviewSnapshots: Boolean = true,
            zoomRatio: Float = 1f,
            resolutionOption: StillCaptureResolutionOption? = null,
            outputSize: StillCaptureOutputSize? = null,
            qualityPreference: com.opencamera.core.media.StillCaptureQualityPreference =
                com.opencamera.core.media.StillCaptureQualityPreference.QUALITY,
            resolutionPreset: com.opencamera.core.media.StillCaptureResolutionPreset =
                com.opencamera.core.media.StillCaptureResolutionPreset.LARGE_12MP
        ): DeviceGraphSpec {
            return DeviceGraphSpec(
                template = CaptureTemplate.STILL_CAPTURE,
                preferredLensFacing = preferredLensFacing,
                stillCapture = StillCaptureConfig(
                    resolutionOption = resolutionOption,
                    outputSize = outputSize,
                    qualityPreference = qualityPreference,
                    resolutionPreset = resolutionPreset
                ),
                preview = PreviewConfig(
                    snapshotsEnabled = enablePreviewSnapshots,
                    zoomRatio = normalizedZoomRatioValue(zoomRatio)
                )
            )
        }

        fun videoRecording(
            preferredLensFacing: LensFacing = LensFacing.BACK,
            enablePreviewSnapshots: Boolean = true,
            zoomRatio: Float = 1f,
            audioEnabledWhenPermitted: Boolean = false,
            requestedVideoSpec: VideoSpec? = null,
            resolvedVideoSpec: VideoSpec? = null,
            qualityPreset: RecordingQualityPreset = RecordingQualityPreset.UHD,
            stillResolutionOption: StillCaptureResolutionOption? = null,
            stillOutputSize: StillCaptureOutputSize? = null
        ): DeviceGraphSpec {
            val requestSpec = requestedVideoSpec ?: qualityPreset.defaultVideoSpec()
            val appliedVideoSpec = resolvedVideoSpec ?: requestSpec
            return DeviceGraphSpec(
                template = CaptureTemplate.VIDEO_RECORDING,
                preferredLensFacing = preferredLensFacing,
                stillCapture = StillCaptureConfig(
                    resolutionOption = stillResolutionOption,
                    outputSize = stillOutputSize
                ),
                preview = PreviewConfig(
                    snapshotsEnabled = enablePreviewSnapshots,
                    zoomRatio = normalizedZoomRatioValue(zoomRatio)
                ),
                recording = RecordingConfig(
                    audioEnabledWhenPermitted = audioEnabledWhenPermitted,
                    requestedVideoSpec = requestSpec,
                    videoSpec = appliedVideoSpec,
                    qualityPreset = appliedVideoSpec.resolution.toRecordingQualityPreset()
                )
            )
        }
    }
}

sealed interface DeviceCommand {
    data class ExecuteShot(val plan: ShotPlan) : DeviceCommand
    data class StopActiveShot(val shotId: String) : DeviceCommand
    data class UpdateZoomRatio(val zoomRatio: Float, val previewZoomRatio: Float) : DeviceCommand
    data class SwitchLensNode(val lensNode: LensNode, val reason: String) : DeviceCommand
    data class ApplyPreviewMetering(val request: PreviewMeteringRequest) : DeviceCommand
    data class UpdateOutputRotation(val rotation: CameraOutputRotation) : DeviceCommand
    data class ApplyPreviewBrightness(val request: PreviewBrightnessRequest) : DeviceCommand
}

enum class DeviceRuntimeIssueKind {
    BIND_FAILURE,
    PREVIEW_STALL,
    PROVIDER_FAILURE,
    CAMERA_RECOVERABLE,
    CAMERA_FATAL,
    USER_ACTION_REQUIRED,
    THERMAL_CRITICAL,
    UNKNOWN
}

data class DeviceRuntimeIssue(
    val kind: DeviceRuntimeIssueKind,
    val reason: String,
    val isRecoverable: Boolean
)

fun DeviceRuntimeIssue.displayReason(): String {
    val prefix = when (kind) {
        DeviceRuntimeIssueKind.BIND_FAILURE -> "Bind failure"
        DeviceRuntimeIssueKind.PREVIEW_STALL -> "Preview stalled"
        DeviceRuntimeIssueKind.PROVIDER_FAILURE -> "Provider failure"
        DeviceRuntimeIssueKind.CAMERA_RECOVERABLE -> "Camera recoverable error"
        DeviceRuntimeIssueKind.CAMERA_FATAL -> "Camera fatal error"
        DeviceRuntimeIssueKind.USER_ACTION_REQUIRED -> "Camera unavailable"
        DeviceRuntimeIssueKind.THERMAL_CRITICAL -> "Critical thermal"
        DeviceRuntimeIssueKind.UNKNOWN -> "Runtime error"
    }
    return "$prefix: $reason"
}

fun DeviceRuntimeIssue.recoveryReason(): String {
    val suffix = when (kind) {
        DeviceRuntimeIssueKind.BIND_FAILURE -> "bind failure"
        DeviceRuntimeIssueKind.PREVIEW_STALL -> "preview stall"
        DeviceRuntimeIssueKind.PROVIDER_FAILURE -> "provider failure"
        DeviceRuntimeIssueKind.CAMERA_RECOVERABLE -> "camera recoverable error"
        DeviceRuntimeIssueKind.CAMERA_FATAL -> "camera fatal error"
        DeviceRuntimeIssueKind.USER_ACTION_REQUIRED -> "camera unavailable"
        DeviceRuntimeIssueKind.THERMAL_CRITICAL -> "critical thermal"
        DeviceRuntimeIssueKind.UNKNOWN -> "runtime error"
    }
    return "recover after $suffix: $reason"
}

sealed interface DeviceEvent {
    data class PreviewFirstFrameAvailable(val firstFrameLatencyMillis: Long) : DeviceEvent
    data class PreviewSnapshotAvailable(
        val source: ThumbnailSource,
        val generation: Int = 0
    ) : DeviceEvent
    data class PreviewSurfaceLost(val reason: String) : DeviceEvent
    data class PreviewError(val reason: String) : DeviceEvent
    data class RuntimeIssue(val issue: DeviceRuntimeIssue) : DeviceEvent
    data class ShotStarted(val shot: ShotRequest) : DeviceEvent
    data class CaptureCommitted(
        val shotId: String,
        val mediaType: MediaType,
        val source: String,
        val elapsedTimestampMs: Long? = null
    ) : DeviceEvent
    data class DataReceived(val shotId: String, val mediaType: MediaType) : DeviceEvent
    data class ShotCompleted(val result: ShotResult) : DeviceEvent
    data class ShotFailed(
        val shotId: String,
        val mediaType: MediaType,
        val reason: String
    ) : DeviceEvent
    data class CaptureFeedbackSnapshotAvailable(
        val shotId: String,
        val outputPath: String
    ) : DeviceEvent
    data class PreviewMeteringCompleted(val result: PreviewMeteringResult) : DeviceEvent
    data class PreviewBrightnessApplied(val result: PreviewBrightnessResult) : DeviceEvent
}
