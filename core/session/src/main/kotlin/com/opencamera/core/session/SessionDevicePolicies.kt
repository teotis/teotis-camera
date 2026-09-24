package com.opencamera.core.session

import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.LensNode
import com.opencamera.core.device.LensNodeAvailability
import com.opencamera.core.device.PreviewStreamAspect
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.device.normalizedZoomRatioValue
import com.opencamera.core.media.StillCaptureResolutionPreset

/**
 * Pure selection rules used while reconciling a session's requested camera
 * configuration with the currently available device capabilities.
 *
 * Keeping these rules outside [DefaultCameraSession] makes them independently
 * testable and keeps the session focused on intent ordering and side effects.
 */
internal object SessionDevicePolicies {
    private const val lensNodeHysteresisRatio = 0.05f
    private const val frameBoxScaleMin = 0.775f
    private const val frameBoxScaleMax = 0.949f
    private const val minNonZeroPreviewZoomRatio = 0.01f

    fun evaluateLensNode(
        ratio: Float,
        currentNode: LensNode?,
        lensNodeMap: Map<LensNode, LensNodeAvailability>
    ): LensNode {
        if (lensNodeMap.isEmpty()) return LensNode.WIDE
        val sorted = lensNodeMap.values
            .filter { it.available }
            .sortedByDescending { it.thresholdRatio }
        if (sorted.isEmpty()) return LensNode.WIDE

        var target = LensNode.WIDE
        for (availability in sorted) {
            if (ratio >= availability.thresholdRatio) {
                target = availability.node
                break
            }
        }

        if (currentNode != null && currentNode != target) {
            val currentThreshold = lensNodeMap[currentNode]?.thresholdRatio ?: return target
            val targetThreshold = lensNodeMap[target]?.thresholdRatio ?: return target
            return if (currentThreshold > targetThreshold) {
                val delta = maxOf(0.05f, currentThreshold * lensNodeHysteresisRatio)
                if (ratio <= currentThreshold - delta) target else currentNode
            } else {
                val delta = maxOf(0.05f, targetThreshold * lensNodeHysteresisRatio)
                if (ratio >= targetThreshold + delta) target else currentNode
            }
        }
        return target
    }

    fun previewZoomRatio(captureZoom: Float, capability: ZoomRatioCapability): Float {
        val bases = capability.normalizedPreviewBaseRatios
            .ifEmpty { previewBaseRatiosFromLensNodeMap(captureZoom, capability.lensNodeMap) }
        return previewZoomRatio(captureZoom, bases)
    }

    fun previewZoomRatio(
        captureZoom: Float,
        lensNodeMap: Map<LensNode, LensNodeAvailability>
    ): Float = previewZoomRatio(captureZoom, previewBaseRatiosFromLensNodeMap(captureZoom, lensNodeMap))

    fun defaultLensFacing(available: Set<LensFacing>): LensFacing = when {
        LensFacing.BACK in available -> LensFacing.BACK
        LensFacing.FRONT in available -> LensFacing.FRONT
        else -> LensFacing.BACK
    }

    fun nextLensFacing(current: LensFacing, available: Set<LensFacing>): LensFacing {
        val ordered = available.sortedBy { it.ordinal }.ifEmpty { listOf(current) }
        val currentIndex = ordered.indexOf(current)
        return if (currentIndex == -1) ordered.first() else ordered[(currentIndex + 1) % ordered.size]
    }

    fun nextPreviewRatio(current: PreviewRatio): PreviewRatio {
        val ordered = PreviewRatio.entries
        return ordered[(ordered.indexOf(current) + 1) % ordered.size]
    }

    fun previewStreamAspect(ratio: PreviewRatio): PreviewStreamAspect = when (ratio) {
        PreviewRatio.FULL -> PreviewStreamAspect.FULL
        PreviewRatio.RATIO_4_3 -> PreviewStreamAspect.RATIO_4_3
        PreviewRatio.RATIO_16_9 -> PreviewStreamAspect.RATIO_16_9
        PreviewRatio.RATIO_1_1 -> PreviewStreamAspect.RATIO_1_1
    }

    fun clampResolutionPreset(
        current: StillCaptureResolutionPreset,
        available: Set<StillCaptureResolutionPreset>
    ): StillCaptureResolutionPreset {
        val ordered = resolutionPresets
        if (current in available) return current
        val currentIndex = ordered.indexOf(current)
        if (currentIndex != -1) {
            for (index in currentIndex + 1..ordered.lastIndex) {
                ordered[index].takeIf { it in available }?.let { return it }
            }
        }
        return ordered.firstOrNull { it in available } ?: ordered.last()
    }

    fun resolveOutputSizeSelection(
        current: StillCaptureOutputSize?,
        available: List<StillCaptureOutputSize>,
        fallbackPreset: StillCaptureResolutionPreset
    ): StillCaptureOutputSize? {
        if (available.isEmpty()) return null
        if (current != null && current in available) return current
        return resolveOutputSizeForPreset(fallbackPreset, available)
    }

    fun nextOutputSize(
        current: StillCaptureOutputSize?,
        available: List<StillCaptureOutputSize>
    ): StillCaptureOutputSize {
        val ordered = available.sortedByDescending { it.pixelCount }
            .ifEmpty { error("No still capture output sizes available") }
        val currentIndex = current?.let(ordered::indexOf) ?: -1
        return if (currentIndex == -1) ordered.first() else ordered[(currentIndex + 1) % ordered.size]
    }

    fun resolutionPresetFor(outputSize: StillCaptureOutputSize): StillCaptureResolutionPreset {
        val pixels = outputSize.pixelCount
        return StillCaptureResolutionPreset.entries.minByOrNull { preset ->
            kotlin.math.abs(pixels - preset.targetWidth.toLong() * preset.targetHeight.toLong())
        } ?: StillCaptureResolutionPreset.LARGE_12MP
    }

    fun nextResolutionPreset(
        current: StillCaptureResolutionPreset,
        available: Set<StillCaptureResolutionPreset>
    ): StillCaptureResolutionPreset {
        val ordered = resolutionPresets.filter { it in available }.ifEmpty {
            listOf(clampResolutionPreset(current, available))
        }
        val currentIndex = ordered.indexOf(clampResolutionPreset(current, available))
        return if (currentIndex == -1) ordered.first() else ordered[(currentIndex + 1) % ordered.size]
    }

    private fun previewZoomRatio(captureZoom: Float, previewBases: List<Float>): Float {
        val normalizedCaptureZoom = normalizedZoomRatioValue(captureZoom)
        val bases = previewBases.map(::normalizedZoomRatioValue)
            .filter { it > 0f }
            .distinct()
            .sorted()
        if (bases.isEmpty()) return normalizedCaptureZoom.coerceAtLeast(1f)
        if (normalizedCaptureZoom <= bases.first()) return bases.first()
        for (index in 0 until bases.lastIndex) {
            val currentBase = bases[index]
            val nextBase = bases[index + 1]
            if (normalizedCaptureZoom == currentBase) return currentBase
            if (normalizedCaptureZoom > currentBase && normalizedCaptureZoom < nextBase) {
                val upper = frameBoxScaleMax * normalizedCaptureZoom
                val lower = frameBoxScaleMin * normalizedCaptureZoom
                return if (lower > upper) currentBase else upper.coerceIn(lower, nextBase)
            }
        }
        return bases.last()
    }

    private fun previewBaseRatiosFromLensNodeMap(
        captureZoom: Float,
        lensNodeMap: Map<LensNode, LensNodeAvailability>
    ): List<Float> = lensNodeMap.values
        .filter { it.available }
        .map { availability ->
            if (availability.thresholdRatio <= 0f) {
                captureZoom.coerceAtMost(1f).coerceAtLeast(minNonZeroPreviewZoomRatio)
            } else {
                availability.thresholdRatio
            }
        }
        .map(::normalizedZoomRatioValue)
        .filter { it > 0f }
        .distinct()
        .sorted()

    private fun resolveOutputSizeForPreset(
        preset: StillCaptureResolutionPreset,
        available: List<StillCaptureOutputSize>
    ): StillCaptureOutputSize {
        val desiredPixels = preset.targetWidth.toLong() * preset.targetHeight.toLong()
        val sorted = available.sortedBy { it.pixelCount }
        return when (preset) {
            StillCaptureResolutionPreset.LARGE_12MP -> sorted.last()
            StillCaptureResolutionPreset.MEDIUM_8MP,
            StillCaptureResolutionPreset.SMALL_2MP -> sorted.lastOrNull { it.pixelCount <= desiredPixels }
                ?: sorted.first()
        }
    }

    private val resolutionPresets = listOf(
        StillCaptureResolutionPreset.LARGE_12MP,
        StillCaptureResolutionPreset.MEDIUM_8MP,
        StillCaptureResolutionPreset.SMALL_2MP
    )
}
