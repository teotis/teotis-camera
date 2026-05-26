package com.opencamera.app.gesture

import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.normalizedZoomRatioValue

data class ZoomDragResult(
    val targetRatio: Float,
    val snappedToPreset: Boolean
)

class ZoomScaleMapper(
    private val capability: ZoomRatioCapability,
    private val stripLeft: Float,
    private val stripRight: Float
) {
    private val presets = capability.normalizedSupportedRatios
    private val minRatio = presets.firstOrNull() ?: 1f
    private val maxRatio = presets.lastOrNull() ?: 1f
    private val isContinuous = capability.support == ZoomControlSupport.CONTINUOUS

    fun mapPositionToRatio(x: Float): ZoomDragResult {
        val clampedX = x.coerceIn(stripLeft, stripRight)
        val fraction = if (stripRight > stripLeft) {
            (clampedX - stripLeft) / (stripRight - stripLeft)
        } else 0f

        return if (isContinuous) {
            val ratio = normalizedZoomRatioValue(minRatio + fraction * (maxRatio - minRatio))
            ZoomDragResult(targetRatio = ratio, snappedToPreset = false)
        } else {
            val index = (fraction * (presets.size - 1)).toInt().coerceIn(0, presets.size - 1)
            ZoomDragResult(targetRatio = presets[index], snappedToPreset = true)
        }
    }
}
