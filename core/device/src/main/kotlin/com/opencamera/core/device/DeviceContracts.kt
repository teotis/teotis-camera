package com.opencamera.core.device

import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotPlan
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.settings.ManualCaptureParams
import com.opencamera.core.settings.VideoSpec
import com.opencamera.core.settings.VideoSpecConstraints

enum class LensFacing {
    BACK,
    FRONT
}

enum class CaptureTemplate {
    STILL_CAPTURE,
    VIDEO_RECORDING
}

data class StillCaptureOutputSize(
    val width: Int,
    val height: Int
) {
    val pixelCount: Long
        get() = width.toLong() * height.toLong()
}

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
    val supportsManualControls: Boolean = true,
    val manualControlCapabilities: ManualControlCapabilityMatrix? = null,
    val supportsDocumentScanEnhancement: Boolean = true,
    val supportsPortraitDepthEffect: Boolean = true,
    val supportsNightMultiFrame: Boolean = true,
    val supportsFlashControl: Boolean = true,
    val availableLensFacings: Set<LensFacing> = setOf(LensFacing.BACK),
    val availableStillCaptureOutputSizes: List<StillCaptureOutputSize> = emptyList(),
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

data class PreviewConfig(
    val snapshotsEnabled: Boolean = true,
    val zoomRatio: Float = 1f
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
    val defaultRatio: Float = 1f
) {
    val normalizedSupportedRatios: List<Float>
        get() = supportedRatios
            .map(::normalizedZoomRatioValue)
            .filter { it > 0f }
            .distinct()
            .sorted()
            .ifEmpty { listOf(1f) }

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
    if (normalizedCurrent != null && normalizedCurrent in availableRatios) {
        return normalizedCurrent
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
    val qualityPreference: StillCaptureQualityPreference = StillCaptureQualityPreference.LATENCY,
    val resolutionPreset: StillCaptureResolutionPreset =
        StillCaptureResolutionPreset.LARGE_12MP,
    val outputSize: StillCaptureOutputSize? = null
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
            qualityPreference: StillCaptureQualityPreference =
                StillCaptureQualityPreference.LATENCY,
            resolutionPreset: StillCaptureResolutionPreset =
                StillCaptureResolutionPreset.LARGE_12MP,
            outputSize: StillCaptureOutputSize? = null
        ): DeviceGraphSpec {
            return DeviceGraphSpec(
                template = CaptureTemplate.STILL_CAPTURE,
                preferredLensFacing = preferredLensFacing,
                stillCapture = StillCaptureConfig(
                    qualityPreference = qualityPreference,
                    resolutionPreset = resolutionPreset,
                    outputSize = outputSize
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
            stillQualityPreference: StillCaptureQualityPreference =
                StillCaptureQualityPreference.LATENCY,
            stillResolutionPreset: StillCaptureResolutionPreset =
                StillCaptureResolutionPreset.LARGE_12MP,
            stillOutputSize: StillCaptureOutputSize? = null
        ): DeviceGraphSpec {
            val requestSpec = requestedVideoSpec ?: qualityPreset.defaultVideoSpec()
            val appliedVideoSpec = resolvedVideoSpec ?: requestSpec
            return DeviceGraphSpec(
                template = CaptureTemplate.VIDEO_RECORDING,
                preferredLensFacing = preferredLensFacing,
                stillCapture = StillCaptureConfig(
                    qualityPreference = stillQualityPreference,
                    resolutionPreset = stillResolutionPreset,
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
    data class UpdateZoomRatio(val zoomRatio: Float) : DeviceCommand
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
        DeviceRuntimeIssueKind.THERMAL_CRITICAL -> "Thermal critical"
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
        DeviceRuntimeIssueKind.THERMAL_CRITICAL -> "thermal critical"
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
}
