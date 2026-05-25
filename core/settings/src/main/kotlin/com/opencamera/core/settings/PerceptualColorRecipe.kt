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
            toneLift = airyTone * 0.32f,
            toneDepth = deepTone * 0.38f,
            chromaBoost = colorMagnitude * 0.28f,
            warmthBias = color * 0.30f,
            tintBias = (-color * 0.10f),
            shadowTint = airyTone * 0.15f,
            highlightTint = deepTone * 0.10f,
            neutralProtection = 0.75f,
            skinProtection = 0.70f
        )

        StyleColorScience.TEXTURE -> PerceptualColorRecipe(
            toneLift = airyTone * 0.18f,
            toneDepth = deepTone * 0.22f,
            chromaBoost = colorMagnitude * 0.12f,
            warmthBias = color * 0.15f,
            tintBias = (-color * 0.06f),
            shadowTint = airyTone * 0.20f,
            highlightTint = deepTone * 0.08f,
            neutralProtection = 0.82f,
            skinProtection = 0.78f
        )

        StyleColorScience.VIVID -> PerceptualColorRecipe(
            toneLift = airyTone * 0.26f,
            toneDepth = deepTone * 0.30f,
            chromaBoost = colorMagnitude * 0.32f,
            warmthBias = color * 0.22f,
            tintBias = (-color * 0.08f),
            shadowTint = airyTone * 0.10f,
            highlightTint = deepTone * 0.12f,
            neutralProtection = 0.70f,
            skinProtection = 0.68f
        )

        StyleColorScience.MONOCHROME -> PerceptualColorRecipe(
            toneLift = airyTone * 0.30f,
            toneDepth = deepTone * 0.30f,
            chromaBoost = 0f,
            warmthBias = 0f,
            tintBias = 0f,
            shadowTint = airyTone * 0.12f,
            highlightTint = deepTone * 0.10f,
            neutralProtection = 0f,
            skinProtection = 0f
        )
    }
}

private fun signedPaletteCurveForRecipe(value: Float): Float {
    val normalized = value.coerceIn(-1f, 1f)
    val magnitude = abs(normalized)
    val curved = magnitude.pow(0.72f)
    return if (normalized < 0f) -curved else curved
}
