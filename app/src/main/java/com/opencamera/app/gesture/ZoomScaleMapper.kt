package com.opencamera.app.gesture

import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.normalizedZoomRatioValue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

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
            val logRatio = ln(minRatio) + fraction * (ln(maxRatio) - ln(minRatio))
            val ratio = normalizedZoomRatioValue(exp(logRatio))
            ZoomDragResult(targetRatio = ratio, snappedToPreset = false)
        } else {
            val logRatio = ln(minRatio) + fraction * (ln(maxRatio) - ln(minRatio))
            val nearest = presets.minByOrNull { abs(ln(it) - logRatio) } ?: presets.first()
            ZoomDragResult(targetRatio = nearest, snappedToPreset = true)
        }
    }
}
