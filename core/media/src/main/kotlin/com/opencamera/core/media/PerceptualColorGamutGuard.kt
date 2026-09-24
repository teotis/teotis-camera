package com.opencamera.core.media

import kotlin.math.min

/**
 * Output-side color guard for perceptual Color Lab recipes.
 *
 * The guard keeps deliberately vivid styles intact when Color Lab is inactive. When a
 * perceptual recipe is active, it spends less saturation and color-bias budget on pixels
 * that are already close to the RGB gamut boundary, and gently restores chroma reserve
 * for unusually pure source colors before the final JPEG encode.
 */
object PerceptualColorGamutGuard {
    fun saturationFor(
        red: Float,
        green: Float,
        blue: Float,
        requestedSaturation: Float
    ): Float {
        if (requestedSaturation <= 1f) return requestedSaturation
        val chromaCompression = smoothstep(
            edge0 = CHROMA_COMPRESSION_START,
            edge1 = CHROMA_COMPRESSION_FULL,
            value = relativeSaturation(red, green, blue)
        ) * coolDominance(red, green, blue) * MAX_CHROMA_COMPRESSION
        val guardedBase = 1f - chromaCompression
        val guardedBoost = (requestedSaturation - 1f) * saturationBoostHeadroom(red, green, blue)
        return (guardedBase + guardedBoost).coerceAtLeast(MIN_GUARDED_SATURATION)
    }

    fun biasScale(red: Float, green: Float, blue: Float): Float {
        return MIN_BIAS_SCALE +
            (1f - MIN_BIAS_SCALE) * saturationBoostHeadroom(red, green, blue)
    }

    fun saturationBoostHeadroom(red: Float, green: Float, blue: Float): Float {
        val maximum = maxOf(red, green, blue)
        val minimum = minOf(red, green, blue)
        val chromaReserve = 1f - smoothstep(
            edge0 = CHROMA_HEADROOM_START,
            edge1 = CHROMA_HEADROOM_END,
            value = absoluteChromaNorm(red, green, blue)
        )
        val highlightReserve = ((OUTPUT_MAX - maximum) / HIGHLIGHT_RESERVE_RANGE).coerceIn(0f, 1f)
        val shadowReserve = ((minimum - OUTPUT_MIN) / SHADOW_RESERVE_RANGE).coerceIn(0f, 1f)
        return minOf(chromaReserve, highlightReserve, shadowReserve).coerceIn(0f, 1f)
    }

    fun compressToOutputGamut(
        red: Float,
        green: Float,
        blue: Float,
        out: FloatArray
    ) {
        require(out.size >= CHANNEL_COUNT) { "RGB output scratch must contain at least three channels" }
        if (
            red in OUTPUT_MIN..OUTPUT_MAX &&
            green in OUTPUT_MIN..OUTPUT_MAX &&
            blue in OUTPUT_MIN..OUTPUT_MAX
        ) {
            out[RED] = red
            out[GREEN] = green
            out[BLUE] = blue
            return
        }

        val luma = (red * RED_LUMA + green * GREEN_LUMA + blue * BLUE_LUMA)
            .coerceIn(OUTPUT_MIN, OUTPUT_MAX)
        var chromaScale = 1f
        chromaScale = min(chromaScale, outputGamutScale(red, luma))
        chromaScale = min(chromaScale, outputGamutScale(green, luma))
        chromaScale = min(chromaScale, outputGamutScale(blue, luma))
        chromaScale = chromaScale.coerceIn(0f, 1f)
        out[RED] = luma + (red - luma) * chromaScale
        out[GREEN] = luma + (green - luma) * chromaScale
        out[BLUE] = luma + (blue - luma) * chromaScale
    }

    private fun absoluteChromaNorm(red: Float, green: Float, blue: Float): Float {
        return ((maxOf(red, green, blue) - minOf(red, green, blue)) / 255f).coerceIn(0f, 1f)
    }

    private fun relativeSaturation(red: Float, green: Float, blue: Float): Float {
        val maximum = maxOf(red, green, blue).coerceAtLeast(0f)
        if (maximum == 0f) return 0f
        return ((maximum - minOf(red, green, blue)) / maximum).coerceIn(0f, 1f)
    }

    private fun coolDominance(red: Float, green: Float, blue: Float): Float {
        val dominance = (blue - maxOf(red, green)) / 255f
        return smoothstep(
            edge0 = COOL_DOMINANCE_START,
            edge1 = COOL_DOMINANCE_FULL,
            value = dominance
        )
    }

    private fun outputGamutScale(channel: Float, luma: Float): Float {
        val delta = channel - luma
        return when {
            delta > 0f -> (OUTPUT_MAX - luma) / delta
            delta < 0f -> (OUTPUT_MIN - luma) / delta
            else -> 1f
        }
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        if (edge0 == edge1) return if (value >= edge1) 1f else 0f
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private const val CHANNEL_COUNT = 3
    private const val RED = 0
    private const val GREEN = 1
    private const val BLUE = 2
    private const val OUTPUT_MIN = 0f
    private const val OUTPUT_MAX = 253f
    private const val RED_LUMA = 0.2126f
    private const val GREEN_LUMA = 0.7152f
    private const val BLUE_LUMA = 0.0722f
    private const val CHROMA_HEADROOM_START = 0.45f
    private const val CHROMA_HEADROOM_END = 0.82f
    private const val CHROMA_COMPRESSION_START = 0.68f
    private const val CHROMA_COMPRESSION_FULL = 0.90f
    private const val COOL_DOMINANCE_START = 0.02f
    private const val COOL_DOMINANCE_FULL = 0.18f
    private const val MAX_CHROMA_COMPRESSION = 0.28f
    private const val MIN_GUARDED_SATURATION = 0.72f
    private const val MIN_BIAS_SCALE = 0.28f
    private const val HIGHLIGHT_RESERVE_RANGE = 64f
    private const val SHADOW_RESERVE_RANGE = 48f
}
