package com.opencamera.core.device

/**
 * Extracts deterministic scene metrics from raw pixel data to classify
 * light state beyond pure average luma. Metrics include:
 * - averageLuma: ITU-R BT.709 luminance
 * - blueCyanRatio: fraction of pixels with blue/cyan hue dominance
 * - highlightRatio: fraction of pixels above a highlight threshold
 * - confidence: overall classification confidence [0,1]
 */
data class BlueHourMetrics(
    val averageLuma: Float,
    val blueCyanRatio: Float,
    val highlightRatio: Float,
    val confidence: Float
)

object BlueHourSceneMetrics {

    // Blue-hour classification thresholds
    private const val BLUE_HOUR_LUMA_MIN = 0.08f
    private const val BLUE_HOUR_LUMA_MAX = 0.40f
    private const val BLUE_HOUR_BLUE_CYAN_MIN = 0.35f
    private const val BLUE_HOUR_HIGHLIGHT_MIN = 0.05f
    private const val BLUE_HOUR_HIGHLIGHT_MAX = 0.55f

    // Low-light thresholds
    private const val LOW_LIGHT_LUMA_MAX = 0.18f

    // Normal thresholds
    private const val NORMAL_LUMA_MIN = 0.24f
    private const val NORMAL_BLUE_CYAN_MAX = 0.30f

    /**
     * Analyze raw ARGB pixel data to compute scene metrics.
     * Pure function, no Android dependency. Testable with synthetic pixel arrays.
     */
    fun analyzePixels(pixels: IntArray): BlueHourMetrics? {
        if (pixels.isEmpty()) return null

        var totalLuma = 0.0
        var blueCyanCount = 0
        var highlightCount = 0

        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 255.0
            val g = (pixel shr 8 and 0xFF) / 255.0
            val b = (pixel and 0xFF) / 255.0

            // ITU-R BT.709 luma
            val luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
            totalLuma += luma

            // Blue/cyan detection: blue channel dominant, or green+blue (cyan) dominant
            val maxChannel = maxOf(r, g, b)
            if (maxChannel > 0.01) {
                val isBlueDominated = b > r && b > g * 0.8
                val isCyanish = g > 0.15 && b > 0.15 && r < 0.4 && b > r
                if (isBlueDominated || isCyanish) {
                    blueCyanCount++
                }
            }

            // Highlight detection: luminance above threshold
            if (luma > 0.70) {
                highlightCount++
            }
        }

        val count = pixels.size.toFloat()
        val avgLuma = (totalLuma / count).toFloat()
        val blueCyanRatio = blueCyanCount / count
        val highlightRatio = highlightCount / count

        return BlueHourMetrics(
            averageLuma = avgLuma,
            blueCyanRatio = blueCyanRatio,
            highlightRatio = highlightRatio,
            confidence = computeConfidence(avgLuma, blueCyanRatio, highlightRatio)
        )
    }

    /**
     * Classify scene light state from metrics.
     * Average luma alone is insufficient to enter BLUE_HOUR.
     */
    fun classify(metrics: BlueHourMetrics): SceneLightState {
        // Blue-hour: moderate luma + strong blue/cyan + some highlights
        if (metrics.averageLuma in BLUE_HOUR_LUMA_MIN..BLUE_HOUR_LUMA_MAX &&
            metrics.blueCyanRatio >= BLUE_HOUR_BLUE_CYAN_MIN &&
            metrics.highlightRatio in BLUE_HOUR_HIGHLIGHT_MIN..BLUE_HOUR_HIGHLIGHT_MAX
        ) {
            return SceneLightState.BLUE_HOUR
        }

        // Low light: very low average luma regardless of color
        if (metrics.averageLuma <= LOW_LIGHT_LUMA_MAX) {
            return SceneLightState.LOW_LIGHT
        }

        // Normal: adequate luma and not dominated by blue/cyan
        if (metrics.averageLuma >= NORMAL_LUMA_MIN &&
            metrics.blueCyanRatio <= NORMAL_BLUE_CYAN_MAX
        ) {
            return SceneLightState.NORMAL
        }

        // Transitional: not enough signal for confident classification
        return SceneLightState.UNKNOWN
    }

    private fun computeConfidence(
        luma: Float,
        blueCyanRatio: Float,
        @Suppress("UNUSED_PARAMETER") highlightRatio: Float
    ): Float {
        val lumaConfidence = when {
            luma < BLUE_HOUR_LUMA_MIN * 0.5f -> 0.3f
            luma in BLUE_HOUR_LUMA_MIN..BLUE_HOUR_LUMA_MAX -> 0.7f
            luma >= NORMAL_LUMA_MIN -> 0.8f
            else -> 0.4f
        }
        val colorConfidence = (blueCyanRatio * 1.5f).coerceIn(0f, 1f)
        return ((lumaConfidence + colorConfidence) / 2f).coerceIn(0f, 1f)
    }
}
