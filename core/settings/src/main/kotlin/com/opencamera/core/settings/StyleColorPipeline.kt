package com.opencamera.core.settings

import kotlin.math.abs
import kotlin.math.roundToInt

enum class StyleColorScience(
    val baseLutId: String
) {
    NATURAL("style-natural-v1"),
    TEXTURE("style-texture-v1"),
    VIVID("style-vivid-v1"),
    MONOCHROME("style-monochrome-v1")
}

enum class StyleColorStage {
    STYLE_BASE,
    COLOR_LAB_SECONDARY
}

data class StyleColorPipelineRequest(
    val styleProfileId: String,
    val baseRenderSpec: FilterRenderSpec,
    val colorLabSpec: ColorLabSpec = ColorLabSpec(),
    val colorScience: StyleColorScience = StyleColorPipeline.resolveColorScience(
        styleProfileId,
        baseRenderSpec
    ),
    val styleStrength: Float = 1f
)

data class StyleColorPipelineResult(
    val styleProfileId: String,
    val colorScience: StyleColorScience,
    val inheritedBaseSpec: FilterRenderSpec,
    val finalRenderSpec: FilterRenderSpec,
    val baseLutId: String,
    val stages: List<StyleColorStage>,
    val notes: List<String>
)

object StyleColorPipeline {
    fun render(request: StyleColorPipelineRequest): StyleColorPipelineResult {
        val inheritedBase = blendFilterRenderSpec(
            from = FilterRenderSpec(),
            to = request.baseRenderSpec,
            amount = request.styleStrength.coerceIn(0f, 1f)
        )
        val mapping = request.colorLabSpec.toMapping()
        val secondary = if (
            mapping.spec.strength == 0f ||
            (mapping.spec.colorAxis == 0f && mapping.spec.toneAxis == 0f)
        ) {
            StyleColorSecondaryResult(inheritedBase, emptyList())
        } else {
            when (request.colorScience) {
                StyleColorScience.NATURAL -> StyleColorSecondaryResult(
                    inheritedBase.applyColorLab(mapping.spec),
                    listOf("color-lab:natural")
                )
                StyleColorScience.TEXTURE -> applyTextureColorLab(inheritedBase, mapping.spec)
                StyleColorScience.VIVID -> applyVividColorLab(inheritedBase, mapping.spec)
                StyleColorScience.MONOCHROME -> applyMonochromeColorLab(inheritedBase, mapping.spec)
            }
        }

        return StyleColorPipelineResult(
            styleProfileId = request.styleProfileId,
            colorScience = request.colorScience,
            inheritedBaseSpec = inheritedBase,
            finalRenderSpec = secondary.renderSpec,
            baseLutId = request.colorScience.baseLutId,
            stages = listOf(
                StyleColorStage.STYLE_BASE,
                StyleColorStage.COLOR_LAB_SECONDARY
            ),
            notes = listOf(
                "style-base:${request.styleProfileId}",
                "color-science:${request.colorScience.name.lowercase()}"
            ) + secondary.notes
        )
    }

    fun resolveColorScience(
        styleProfileId: String,
        baseRenderSpec: FilterRenderSpec
    ): StyleColorScience {
        val id = styleProfileId.lowercase()
        return when {
            baseRenderSpec.monochromeMix >= 0.65f || baseRenderSpec.saturation <= 0.12f ->
                StyleColorScience.MONOCHROME
            "vivid" in id || "rich" in id -> StyleColorScience.VIVID
            "texture" in id ||
                "street" in id ||
                "humanistic" in id ||
                baseRenderSpec.saturation < 0.9f && baseRenderSpec.contrast >= 1.1f ->
                StyleColorScience.TEXTURE
            else -> StyleColorScience.NATURAL
        }
    }

    private fun applyTextureColorLab(
        base: FilterRenderSpec,
        spec: ColorLabSpec
    ): StyleColorSecondaryResult {
        val color = spec.colorAxis * spec.strength
        val tone = spec.toneAxis * spec.strength
        val airyTone = tone.coerceAtLeast(0f)
        val deepTone = (-tone).coerceAtLeast(0f)
        val warm = color.coerceAtLeast(0f)
        val cool = (-color).coerceAtLeast(0f)
        val colorMagnitude = abs(color)
        val result = base.copy(
            brightnessShift = (base.brightnessShift + (airyTone * 3f - deepTone * 5f).roundToInt())
                .coerceIn(-24, 32),
            contrast = (base.contrast - airyTone * 0.18f + deepTone * 0.12f)
                .coerceIn(0.82f, 1.32f),
            saturation = (base.saturation + colorMagnitude * 0.05f - deepTone * 0.03f)
                .coerceIn(0.68f, 1.24f),
            warmthShift = (base.warmthShift + (color * 5f).roundToInt()).coerceIn(-24, 24),
            tintShift = (base.tintShift + (-color * 2f).roundToInt()).coerceIn(-24, 24),
            shadowLift = (base.shadowLift + airyTone * 0.2f).coerceIn(0f, 0.42f),
            highlightCompression = (base.highlightCompression + deepTone * 0.1f + airyTone * 0.04f)
                .coerceIn(0f, 0.42f),
            warmBoost = (base.warmBoost + warm * 0.18f + cool * 0.05f).coerceIn(0f, 0.38f),
            coolBoost = (base.coolBoost + cool * 0.18f + warm * 0.08f).coerceIn(0f, 0.38f)
        )
        return StyleColorSecondaryResult(
            renderSpec = result,
            notes = listOf("color-lab:texture", "texture:split-grade")
        )
    }

    private fun applyVividColorLab(
        base: FilterRenderSpec,
        spec: ColorLabSpec
    ): StyleColorSecondaryResult {
        val color = spec.colorAxis * spec.strength
        val tone = spec.toneAxis * spec.strength
        val airyTone = tone.coerceAtLeast(0f)
        val deepTone = (-tone).coerceAtLeast(0f)
        val colorMagnitude = abs(color)
        val cappedSaturation = (base.saturation + colorMagnitude * 0.08f)
            .coerceIn(0.72f, 1.32f)
        val result = base.copy(
            brightnessShift = (base.brightnessShift + (airyTone * 5f - deepTone * 7f).roundToInt())
                .coerceIn(-24, 32),
            contrast = (base.contrast + deepTone * 0.14f - airyTone * 0.04f)
                .coerceIn(1f, 1.36f),
            saturation = cappedSaturation,
            warmthShift = (base.warmthShift + (color * 10f).roundToInt()).coerceIn(-24, 24),
            tintShift = (base.tintShift + (-color * 2f).roundToInt()).coerceIn(-24, 24),
            shadowLift = (base.shadowLift + airyTone * 0.1f + deepTone * 0.04f).coerceIn(0f, 0.34f),
            highlightCompression = (base.highlightCompression + airyTone * 0.22f + deepTone * 0.06f)
                .coerceIn(0f, 0.42f),
            warmBoost = if (color > 0f) (base.warmBoost + color * 0.12f).coerceIn(0f, 0.3f) else 0f,
            coolBoost = if (color < 0f) (base.coolBoost + -color * 0.12f).coerceIn(0f, 0.3f) else 0f
        )
        return StyleColorSecondaryResult(
            renderSpec = result,
            notes = listOf("color-lab:vivid", "vivid:saturation-guard")
        )
    }

    private fun applyMonochromeColorLab(
        base: FilterRenderSpec,
        spec: ColorLabSpec
    ): StyleColorSecondaryResult {
        val tone = spec.toneAxis * spec.strength
        val airyTone = tone.coerceAtLeast(0f)
        val deepTone = (-tone).coerceAtLeast(0f)
        val result = base.copy(
            brightnessShift = (base.brightnessShift + (airyTone * 6f - deepTone * 6f).roundToInt())
                .coerceIn(-24, 32),
            contrast = (base.contrast - airyTone * 0.08f + deepTone * 0.18f)
                .coerceIn(0.82f, 1.38f),
            saturation = 0f,
            warmthShift = 0,
            tintShift = 0,
            monochromeMix = base.monochromeMix.coerceAtLeast(1f),
            shadowLift = (base.shadowLift + airyTone * 0.12f).coerceIn(0f, 0.34f),
            highlightCompression = (base.highlightCompression + deepTone * 0.1f).coerceIn(0f, 0.34f),
            warmBoost = 0f,
            coolBoost = 0f
        )
        return StyleColorSecondaryResult(
            renderSpec = result,
            notes = listOf("color-lab:monochrome", "monochrome:color-axis-muted")
        )
    }
}

private data class StyleColorSecondaryResult(
    val renderSpec: FilterRenderSpec,
    val notes: List<String>
)

private fun blendFilterRenderSpec(
    from: FilterRenderSpec,
    to: FilterRenderSpec,
    amount: Float
): FilterRenderSpec {
    val clamped = amount.coerceIn(0f, 1f)
    return FilterRenderSpec(
        brightnessShift = blendInt(from.brightnessShift, to.brightnessShift, clamped),
        contrast = blendFloat(from.contrast, to.contrast, clamped),
        saturation = blendFloat(from.saturation, to.saturation, clamped),
        warmthShift = blendInt(from.warmthShift, to.warmthShift, clamped),
        tintShift = blendInt(from.tintShift, to.tintShift, clamped),
        monochromeMix = blendFloat(from.monochromeMix, to.monochromeMix, clamped),
        vignetteStrength = blendFloat(from.vignetteStrength, to.vignetteStrength, clamped),
        softGlowStrength = blendFloat(from.softGlowStrength, to.softGlowStrength, clamped),
        haloStrength = blendFloat(from.haloStrength, to.haloStrength, clamped),
        grainStrength = blendFloat(from.grainStrength, to.grainStrength, clamped),
        sharpnessBoost = blendFloat(from.sharpnessBoost, to.sharpnessBoost, clamped),
        highlightCompression = blendFloat(from.highlightCompression, to.highlightCompression, clamped),
        shadowLift = blendFloat(from.shadowLift, to.shadowLift, clamped),
        warmBoost = blendFloat(from.warmBoost, to.warmBoost, clamped),
        coolBoost = blendFloat(from.coolBoost, to.coolBoost, clamped)
    )
}

private fun blendFloat(
    from: Float,
    to: Float,
    amount: Float
): Float = from + (to - from) * amount

private fun blendInt(
    from: Int,
    to: Int,
    amount: Float
): Int = (from + (to - from) * amount).roundToInt()
