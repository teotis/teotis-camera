package com.opencamera.core.settings

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

enum class PreviewColorFidelity {
    APPROXIMATE,
    MASK_AWARE,
    FALLBACK,
    DEGRADED
}

data class PerceptualColorRecipe(
    val toneLift: Float = 0f,
    val toneDepth: Float = 0f,
    val chromaBoost: Float = 0f,
    val warmthBias: Float = 0f,
    val tintBias: Float = 0f,
    val shadowTint: Float = 0f,
    val highlightTint: Float = 0f,
    val neutralProtection: Float = 0f,
    val skinProtection: Float = 0f,
    val previewFidelity: PreviewColorFidelity = PreviewColorFidelity.APPROXIMATE
) {
    val isNeutral: Boolean
        get() = toneLift == 0f && toneDepth == 0f && chromaBoost == 0f &&
            warmthBias == 0f && tintBias == 0f

    companion object {
        val NEUTRAL = PerceptualColorRecipe()
    }
}

fun ColorLabSpec.toRecipe(
    colorScience: StyleColorScience = StyleColorScience.NATURAL
): PerceptualColorRecipe {
    val spec = normalized()
    if (spec.strength == 0f || (spec.colorAxis == 0f && spec.toneAxis == 0f)) {
        return PerceptualColorRecipe.NEUTRAL
    }

    val color = signedPaletteCurveForRecipe(spec.colorAxis) * spec.strength
    val tone = signedPaletteCurveForRecipe(spec.toneAxis) * spec.strength
    val colorMagnitude = abs(color)
    val airyTone = tone.coerceAtLeast(0f)
    val deepTone = (-tone).coerceAtLeast(0f)

    return when (colorScience) {
        StyleColorScience.NATURAL -> PerceptualColorRecipe(
            toneLift = airyTone * 0.40f,
            toneDepth = deepTone * 0.45f,
            chromaBoost = colorMagnitude * 0.35f,
            warmthBias = color * 0.35f,
            tintBias = (-color * 0.12f),
            shadowTint = airyTone * 0.18f,
            highlightTint = deepTone * 0.12f,
            neutralProtection = 0.72f,
            skinProtection = 0.70f
        )

        StyleColorScience.TEXTURE -> PerceptualColorRecipe(
            toneLift = airyTone * 0.24f,
            toneDepth = deepTone * 0.28f,
            chromaBoost = colorMagnitude * 0.16f,
            warmthBias = color * 0.18f,
            tintBias = (-color * 0.08f),
            shadowTint = airyTone * 0.22f,
            highlightTint = deepTone * 0.10f,
            neutralProtection = 0.80f,
            skinProtection = 0.78f
        )

        StyleColorScience.VIVID -> PerceptualColorRecipe(
            toneLift = airyTone * 0.34f,
            toneDepth = deepTone * 0.38f,
            chromaBoost = colorMagnitude * 0.40f,
            warmthBias = color * 0.26f,
            tintBias = (-color * 0.10f),
            shadowTint = airyTone * 0.12f,
            highlightTint = deepTone * 0.14f,
            neutralProtection = 0.65f,
            skinProtection = 0.65f
        )

        StyleColorScience.MONOCHROME -> PerceptualColorRecipe(
            toneLift = airyTone * 0.36f,
            toneDepth = deepTone * 0.36f,
            chromaBoost = 0f,
            warmthBias = 0f,
            tintBias = 0f,
            shadowTint = airyTone * 0.14f,
            highlightTint = deepTone * 0.12f,
            neutralProtection = 0f,
            skinProtection = 0f
        )
    }
}

private fun signedPaletteCurveForRecipe(value: Float): Float {
    val normalized = value.coerceIn(-1f, 1f)
    val magnitude = abs(normalized)
    if (magnitude < DEAD_ZONE_THRESHOLD) return 0f
    val activeRange = 1f - DEAD_ZONE_THRESHOLD
    val remapped = (magnitude - DEAD_ZONE_THRESHOLD) / activeRange
    val curved = remapped.pow(0.62f)
    val result = curved * (1f + 0.15f * (curved - 1f).coerceAtLeast(0f))
    return if (normalized < 0f) -result.coerceIn(0f, 1f) else result.coerceIn(0f, 1f)
}

private const val DEAD_ZONE_THRESHOLD = 0.10f
