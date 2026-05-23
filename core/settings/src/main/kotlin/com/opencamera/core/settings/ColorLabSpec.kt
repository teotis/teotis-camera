package com.opencamera.core.settings

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

data class ColorLabSpec(
    val colorAxis: Float = 0f,
    val toneAxis: Float = 0f,
    val strength: Float = 1f,
    val presetId: String? = null,
    val version: Int = 1
) {
    fun normalized(): ColorLabSpec {
        return copy(
            colorAxis = colorAxis.coerceIn(-1f, 1f),
            toneAxis = toneAxis.coerceIn(-1f, 1f),
            strength = strength.coerceIn(0f, 1f),
            version = version.coerceAtLeast(1)
        )
    }

    fun toMapping(): ColorLabMapping {
        val spec = normalized()
        val color = spec.colorAxis * spec.strength
        val tone = spec.toneAxis * spec.strength
        val colorMagnitude = abs(color)
        val airyTone = tone.coerceAtLeast(0f)
        val deepTone = (-tone).coerceAtLeast(0f)
        val adjustments = ColorLabAdjustments(
            brightnessDelta = (airyTone * 10f - deepTone * 8f).roundToInt(),
            contrastDelta = (-airyTone * 0.12f + deepTone * 0.16f),
            saturationDelta = colorMagnitude * 0.14f - abs(tone) * 0.02f,
            warmthDelta = (color * 12f).roundToInt(),
            tintDelta = (-color * 3f).roundToInt(),
            shadowLiftDelta = airyTone * 0.18f,
            highlightCompressionDelta = (airyTone * 0.08f + deepTone * 0.12f),
            warmBoostDelta = color.coerceAtLeast(0f) * 0.18f,
            coolBoostDelta = (-color).coerceAtLeast(0f) * 0.18f
        )
        return ColorLabMapping(
            spec = spec,
            adjustments = adjustments,
            description = "ColorLabSpec(v=${spec.version}, color=${spec.colorAxis}, tone=${spec.toneAxis}, strength=${spec.strength})"
        )
    }

    fun applyTo(base: FilterRenderSpec): FilterRenderSpec {
        return toMapping().applyTo(base)
    }
}

data class ColorLabMapping(
    val spec: ColorLabSpec,
    val adjustments: ColorLabAdjustments,
    val description: String
) {
    fun applyTo(base: FilterRenderSpec): FilterRenderSpec {
        if (spec.strength == 0f || (spec.colorAxis == 0f && spec.toneAxis == 0f)) {
            return base
        }
        return base.copy(
            brightnessShift = (base.brightnessShift + adjustments.brightnessDelta).coerceIn(-24, 32),
            contrast = (base.contrast + adjustments.contrastDelta).coerceIn(0.82f, 1.32f),
            saturation = (base.saturation + adjustments.saturationDelta).coerceIn(0.72f, 1.38f),
            warmthShift = (base.warmthShift + adjustments.warmthDelta).coerceIn(-24, 24),
            tintShift = (base.tintShift + adjustments.tintDelta).coerceIn(-24, 24),
            shadowLift = (base.shadowLift + adjustments.shadowLiftDelta).coerceIn(0f, 0.38f),
            highlightCompression = (base.highlightCompression + adjustments.highlightCompressionDelta)
                .coerceIn(0f, 0.38f),
            warmBoost = if (adjustments.warmBoostDelta > 0f) {
                (base.warmBoost + adjustments.warmBoostDelta).coerceIn(0f, 0.38f)
            } else {
                base.warmBoost
            },
            coolBoost = if (adjustments.coolBoostDelta > 0f) {
                (base.coolBoost + adjustments.coolBoostDelta).coerceIn(0f, 0.38f)
            } else {
                base.coolBoost
            }
        ).resolveExclusiveWarmCoolBoosts(adjustments)
    }
}

data class ColorLabAdjustments(
    val brightnessDelta: Int,
    val contrastDelta: Float,
    val saturationDelta: Float,
    val warmthDelta: Int,
    val tintDelta: Int,
    val shadowLiftDelta: Float,
    val highlightCompressionDelta: Float,
    val warmBoostDelta: Float,
    val coolBoostDelta: Float
)

fun FilterRenderSpec.applyColorLab(spec: ColorLabSpec): FilterRenderSpec {
    return spec.applyTo(this)
}

fun renderStyleColorSpec(
    profileId: String,
    baseRenderSpec: FilterRenderSpec?,
    colorLabSpec: ColorLabSpec,
    styleStrength: Float
): FilterRenderSpec? {
    val base = baseRenderSpec ?: return null
    return StyleColorPipeline.render(
        StyleColorPipelineRequest(
            styleProfileId = profileId,
            baseRenderSpec = base,
            colorLabSpec = colorLabSpec,
            styleStrength = styleStrength
        )
    ).finalRenderSpec
}

private fun FilterRenderSpec.resolveExclusiveWarmCoolBoosts(
    adjustments: ColorLabAdjustments
): FilterRenderSpec {
    return when {
        adjustments.warmBoostDelta > 0f -> copy(coolBoost = 0f)
        adjustments.coolBoostDelta > 0f -> copy(warmBoost = 0f)
        else -> this
    }
}
