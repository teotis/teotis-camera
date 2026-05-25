package com.opencamera.app.camera

import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.media.StillCaptureResolutionOption

internal fun smartFilterResolutionOptions(
    availableOutputSizes: List<StillCaptureOutputSize>
): List<StillCaptureResolutionOption> {
    if (availableOutputSizes.isEmpty()) {
        return emptyList()
    }

    val sortedSizes = availableOutputSizes
        .distinctBy { it.width to it.height }
        .sortedByDescending { it.pixelCount }

    if (sortedSizes.size <= 3) {
        return sortedSizes.map { size ->
            StillCaptureResolutionOption.fromOutputSize(size.width, size.height)
        }
    }

    val highest = sortedSizes.first()
    val lowest = sortedSizes.last()

    val midPixelCount = (highest.pixelCount + lowest.pixelCount) / 2
    val medium = sortedSizes
        .filter { it.pixelCount in (lowest.pixelCount + 1) until highest.pixelCount }
        .minByOrNull { kotlin.math.abs(it.pixelCount - midPixelCount) }
        ?: sortedSizes[sortedSizes.size / 2]

    return listOf(highest, medium, lowest)
        .distinctBy { it.width to it.height }
        .sortedByDescending { it.pixelCount }
        .map { size ->
            StillCaptureResolutionOption.fromOutputSize(size.width, size.height)
        }
}
