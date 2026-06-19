package com.opencamera.app.camera

import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset

private const val FOUR_THIRDS_RATIO = 4.0 / 3.0
private const val ASPECT_RATIO_TOLERANCE = 0.05

internal data class StillCaptureTargetResolution(
    val width: Int,
    val height: Int,
    val resolutionSource: com.opencamera.core.device.StillCaptureResolutionSource = com.opencamera.core.device.StillCaptureResolutionSource.STANDARD
)

internal fun resolvedStillCaptureQuality(
    deviceGraph: DeviceGraphSpec,
    deviceRequest: com.opencamera.core.device.DeviceShotRequest? = null
): StillCaptureQualityPreference {
    return when (deviceGraph.template) {
        CaptureTemplate.STILL_CAPTURE ->
            deviceGraph.stillCapture.qualityPreference
        CaptureTemplate.VIDEO_RECORDING ->
            deviceGraph.stillCapture.qualityPreference
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
        StillCaptureResolutionPreset.LARGE_12MP -> sortedByPixels.last()

        StillCaptureResolutionPreset.MEDIUM_8MP,
        StillCaptureResolutionPreset.SMALL_2MP -> sortedByPixels
            .lastOrNull { it.pixelCount <= desiredPixels }
            ?: sortedByPixels.first()
    }
}

internal fun targetSizeForStillCaptureOutputSize(
    outputSize: StillCaptureOutputSize
): android.util.Size {
    return android.util.Size(outputSize.width, outputSize.height)
}

internal fun normalizeStillCaptureOutputSizes(
    availableOutputSizes: List<StillCaptureTargetResolution>
): List<StillCaptureOutputSize> {
    if (availableOutputSizes.isEmpty()) {
        return emptyList()
    }

    val normalized = availableOutputSizes
        .map { size ->
            StillCaptureOutputSize(
                width = size.width,
                height = size.height,
                resolutionSource = size.resolutionSource
            )
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
    availableOutputSizes: List<android.util.Size>,
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
