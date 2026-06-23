package com.opencamera.app.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.DeviceRuntimeIssueKind
import com.opencamera.core.device.LensFacing
import androidx.camera.core.CameraState
import com.opencamera.core.device.LensNode
import com.opencamera.core.device.LensNodeAvailability
import com.opencamera.core.device.ManualControlCapabilityMatrix
import com.opencamera.core.device.ManualControlSupport
import com.opencamera.core.device.PhysicalStillCaptureOutputProbe
import com.opencamera.core.device.PreviewBrightnessRange
import com.opencamera.core.device.PreviewStreamAspect
import com.opencamera.core.device.StillCaptureCameraProbe
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.device.normalizedZoomRatioValue
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSpecConstraints
import java.util.Locale

private const val TAG = "CameraXCaptureAdapter"

data class CameraLensProfile(
    val lensFacing: LensFacing,
    val hasFlashUnit: Boolean,
    val zoomRatioCapability: ZoomRatioCapability = ZoomRatioCapability(),
    val availableStillCaptureOutputSizes: List<StillCaptureOutputSize> = emptyList(),
    val availableStillCaptureResolutionPresets: Set<StillCaptureResolutionPreset> =
        StillCaptureResolutionPreset.entries.toSet(),
    val videoSpecConstraints: VideoSpecConstraints = DeviceCapabilities.DEFAULT.videoSpecConstraints,
    val manualControlCapabilities: ManualControlCapabilityMatrix? = null,
    val previewBrightnessRange: PreviewBrightnessRange = PreviewBrightnessRange.CONSERVATIVE,
    val stillCaptureCameraProbe: StillCaptureCameraProbe? = null,
    /** Hardware camera ID, used for physical camera selection in multi-camera devices. */
    val physicalCameraId: String? = null
)

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
    val stillCaptureCameraProbes = prioritizedProfiles
        .mapNotNull { it.stillCaptureCameraProbe }
    val availableStillCaptureResolutionPresets = prioritizedProfiles
        .flatMap { it.availableStillCaptureResolutionPresets }
        .toSet()
        .ifEmpty { baseCapabilities.availableStillCaptureResolutionPresets }
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
        stillCaptureCameraProbes = stillCaptureCameraProbes
            .ifEmpty { baseCapabilities.stillCaptureCameraProbes },
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
    val mergedPreviewBaseRatios = explicitCapabilities
        .flatMap(ZoomRatioCapability::normalizedPreviewBaseRatios)
        .map(::normalizedZoomRatioValue)
        .distinct()
        .sorted()
    val mergedSupport = explicitCapabilities.maxValueOf { it.support }
    val lensNodeMap = detectLensNodeMap(cameraProfiles)
    return ZoomRatioCapability(
        support = mergedSupport,
        supportedRatios = mergedRatios,
        defaultRatio = mergedRatios.firstOrNull { it == 1f } ?: mergedRatios.first(),
        previewBaseRatios = mergedPreviewBaseRatios,
        lensNodeMap = lensNodeMap
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

internal fun detectZoomRatioCapability(
    characteristics: CameraCharacteristics,
    fallback: ZoomRatioCapability = DeviceCapabilities.DEFAULT.zoomRatioCapability
): ZoomRatioCapability {
    val supportedRatios = linkedSetOf<Float>()
    var minRatioForPreviewBases = 1f
    var maxRatioForPreviewBases = 1f
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val zoomRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        if (zoomRange != null) {
            minRatioForPreviewBases = zoomRange.lower
            maxRatioForPreviewBases = zoomRange.upper
        }
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
        maxRatioForPreviewBases = maxDigitalZoom
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
        defaultRatio = normalizedRatios.firstOrNull { it == 1f } ?: normalizedRatios.first(),
        previewBaseRatios = previewBaseRatiosForZoomRange(
            minRatio = minRatioForPreviewBases,
            maxRatio = maxRatioForPreviewBases
        )
    )
}

internal fun previewBaseRatiosForZoomRange(minRatio: Float, maxRatio: Float): List<Float> {
    val min = normalizedZoomRatioValue(minRatio.coerceAtLeast(0.1f))
    val max = normalizedZoomRatioValue(maxRatio.coerceAtLeast(min))
    return listOf(min, 1f, 3f, 5f)
        .filter { ratio -> ratio in min..max }
        .distinct()
        .sorted()
}

internal fun detectManualControlCapabilities(
    characteristics: CameraCharacteristics
): ManualControlCapabilityMatrix {
    val iso = if (
        characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) != null
    ) ManualControlSupport.APPLY else ManualControlSupport.UNSUPPORTED
    val shutter = if (
        characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) != null
    ) ManualControlSupport.APPLY else ManualControlSupport.UNSUPPORTED
    val focusDistance = if (
        (characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f) > 0f
    ) ManualControlSupport.APPLY else ManualControlSupport.UNSUPPORTED
    val awbModes = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
    val whiteBalance = if (
        awbModes != null && CameraMetadata.CONTROL_AWB_MODE_OFF in awbModes
    ) ManualControlSupport.APPLY else ManualControlSupport.SAVED_ONLY
    val evRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
    val evSteps = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
    val exposure = if (
        evRange != null && evSteps != null && evSteps.toFloat() > 0f &&
            (evRange.lower != 0 || evRange.upper != 0)
    ) ManualControlSupport.APPLY else ManualControlSupport.UNSUPPORTED
    val apertures = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
    val aperture = if (apertures != null && apertures.size > 1) {
        ManualControlSupport.APPLY
    } else {
        ManualControlSupport.UNSUPPORTED
    }
    return ManualControlCapabilityMatrix(
        raw = ManualControlSupport.SAVED_ONLY,
        iso = iso,
        shutter = shutter,
        exposureCompensation = exposure,
        focusDistance = focusDistance,
        aperture = aperture,
        whiteBalance = whiteBalance
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

private fun collectAllJpegOutputSizes(
    characteristics: CameraCharacteristics
): List<StillCaptureTargetResolution> {
    val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    val allSizes = linkedMapOf<Pair<Int, Int>, StillCaptureTargetResolution>()

    // Maximum-resolution stream map (API 31+) — highest priority, add first so distinctBy keeps it
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val maxResStreamMap = characteristics
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
        maxResStreamMap?.getOutputSizes(ImageFormat.JPEG)?.forEach { size ->
            allSizes[size.width to size.height] = StillCaptureTargetResolution(
                width = size.width,
                height = size.height,
                resolutionSource = com.opencamera.core.device.StillCaptureResolutionSource.MAXIMUM_RESOLUTION
            )
        }
    }

    // High-resolution output sizes (API 23+) — medium priority
    streamConfigMap?.getHighResolutionOutputSizes(ImageFormat.JPEG)?.forEach { size ->
        if (size.width to size.height !in allSizes) {
            allSizes[size.width to size.height] = StillCaptureTargetResolution(
                width = size.width,
                height = size.height,
                resolutionSource = com.opencamera.core.device.StillCaptureResolutionSource.HIGH_RESOLUTION
            )
        }
    }

    // Standard output sizes — lowest priority
    streamConfigMap?.getOutputSizes(ImageFormat.JPEG)?.forEach { size ->
        if (size.width to size.height !in allSizes) {
            allSizes[size.width to size.height] = StillCaptureTargetResolution(
                width = size.width,
                height = size.height,
                resolutionSource = com.opencamera.core.device.StillCaptureResolutionSource.STANDARD
            )
        }
    }

    return allSizes.values.toList()
}

private fun detectPhysicalStillCaptureOutputProbes(
    cameraManager: CameraManager,
    characteristics: CameraCharacteristics
): Pair<Set<String>, List<PhysicalStillCaptureOutputProbe>> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        return emptySet<String>() to emptyList()
    }
    val physicalIds = characteristics.physicalCameraIds
    val probes = physicalIds.mapNotNull { physicalId ->
        runCatching {
            val physicalCharacteristics = cameraManager.getCameraCharacteristics(physicalId)
            PhysicalStillCaptureOutputProbe(
                cameraId = physicalId,
                outputSizes = normalizeStillCaptureOutputSizes(
                    collectAllJpegOutputSizes(physicalCharacteristics)
                )
            )
        }.getOrNull()
    }
    return physicalIds to probes
}

internal fun detectCameraLensProfiles(context: Context): List<CameraLensProfile> {
    val cameraManager = context.getSystemService(CameraManager::class.java) ?: return emptyList()
    return cameraManager.cameraIdList.mapNotNull { cameraId ->
        val characteristics = try {
            cameraManager.getCameraCharacteristics(cameraId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read characteristics for camera $cameraId", e)
            return@mapNotNull null
        }
        val lensFacing = when (
            characteristics.get(CameraCharacteristics.LENS_FACING)
        ) {
            CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
            CameraCharacteristics.LENS_FACING_BACK -> LensFacing.BACK
            else -> null
        } ?: return@mapNotNull null

        val zoomCap = detectZoomRatioCapability(characteristics)
        val manualControlCapabilities = detectManualControlCapabilities(characteristics)

        val allJpegSizes = collectAllJpegOutputSizes(characteristics)
        val normalizedJpegSizes = normalizeStillCaptureOutputSizes(allJpegSizes)
        val (physicalCameraIds, physicalOutputProbes) = detectPhysicalStillCaptureOutputProbes(
            cameraManager = cameraManager,
            characteristics = characteristics
        )

        CameraLensProfile(
            lensFacing = lensFacing,
            hasFlashUnit = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
            zoomRatioCapability = zoomCap,
            manualControlCapabilities = manualControlCapabilities,
            previewBrightnessRange = detectPreviewBrightnessRange(characteristics),
            availableStillCaptureOutputSizes = normalizedJpegSizes,
            availableStillCaptureResolutionPresets = resolveAvailableStillCaptureResolutionPresets(
                allJpegSizes
            ),
            stillCaptureCameraProbe = StillCaptureCameraProbe(
                cameraId = cameraId,
                lensFacing = lensFacing,
                physicalCameraIds = physicalCameraIds,
                outputSizes = normalizedJpegSizes,
                physicalOutputProbes = physicalOutputProbes
            ),
            videoSpecConstraints = detectVideoSpecConstraints(
                cameraId = cameraId,
                characteristics = characteristics
            ),
            physicalCameraId = cameraId
        )
    }
}

internal fun detectLensNodeMap(
    profiles: List<CameraLensProfile>
): Map<LensNode, LensNodeAvailability> {
    val backProfiles = profiles.filter { it.lensFacing == LensFacing.BACK }
    if (backProfiles.isEmpty()) return emptyMap()

    // Find the max zoom ratio for each back camera profile
    data class CameraZoomInfo(
        val profile: CameraLensProfile,
        val maxZoom: Float
    )

    val zoomInfos = backProfiles.map { profile ->
        val ratios = profile.zoomRatioCapability.normalizedSupportedRatios
        val maxZoom = ratios.maxOrNull() ?: 1f
        CameraZoomInfo(profile, maxZoom)
    }.sortedBy { it.maxZoom }

    if (zoomInfos.isEmpty()) return emptyMap()

    val result = mutableMapOf<LensNode, LensNodeAvailability>()

    // The camera with the lowest max zoom is the primary WIDE camera
    val wideInfo = zoomInfos.first()
    result[LensNode.WIDE] = LensNodeAvailability(
        node = LensNode.WIDE,
        available = true,
        thresholdRatio = 0f,
        physicalCameraId = wideInfo.profile.physicalCameraId
    )

    // Classify other cameras by their max zoom range
    for (info in zoomInfos.drop(1)) {
        val maxZoom = info.maxZoom
        val node = when {
            maxZoom >= 4.0f -> LensNode.PERISCOPE
            maxZoom >= 1.6f -> LensNode.TELEPHOTO
            else -> continue
        }
        if (result.containsKey(node)) continue // Keep first (lowest max zoom) match per node
        result[node] = LensNodeAvailability(
            node = node,
            available = true,
            thresholdRatio = when (node) {
                LensNode.TELEPHOTO -> 2.0f
                LensNode.PERISCOPE -> 5.0f
                else -> 0f
            },
            physicalCameraId = info.profile.physicalCameraId
        )
    }

    return result
}

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
