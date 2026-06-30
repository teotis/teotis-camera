package com.opencamera.core.settings

enum class ColorIntentPreset(
    val id: String,
    val defaultSpec: ColorLabSpec
) {
    NATURAL(
        id = "natural",
        defaultSpec = ColorLabSpec(presetId = "natural")
    ),
    SOFT_WHITE(
        id = "soft-white",
        defaultSpec = ColorLabSpec(colorAxis = 0.10f, toneAxis = 0.58f, strength = 0.78f, presetId = "soft-white")
    ),
    BLUE_TONE(
        id = "blue-tone",
        defaultSpec = ColorLabSpec(colorAxis = -0.56f, toneAxis = 0.12f, strength = 0.76f, presetId = "blue-tone")
    ),
    CLEAR(
        id = "clear",
        defaultSpec = ColorLabSpec(colorAxis = -0.08f, toneAxis = 0.48f, strength = 0.72f, presetId = "clear")
    ),
    CUSTOM(
        id = "custom",
        defaultSpec = ColorLabSpec(presetId = "custom")
    );

    companion object {
        fun fromId(id: String?): ColorIntentPreset? =
            entries.firstOrNull { preset -> preset.id == id }
    }
}

data class ColorIntentRequest(
    val styleProfileId: String,
    val baseRenderSpec: FilterRenderSpec,
    val colorLabSpec: ColorLabSpec = ColorLabSpec(),
    val colorScience: StyleColorScience = StyleColorPipeline.resolveColorScience(
        styleProfileId,
        baseRenderSpec
    ),
    val styleStrength: Float = 1f
)

data class ColorIntentPlan(
    val intent: ColorIntentPreset,
    val effectiveColorLabSpec: ColorLabSpec,
    val colorScience: StyleColorScience,
    val inheritedBaseSpec: FilterRenderSpec,
    val finalRenderSpec: FilterRenderSpec,
    val recipe: PerceptualColorRecipe,
    val diagnostics: List<String>
)

object ColorIntentEngine {
    fun resolve(request: ColorIntentRequest): ColorIntentPlan {
        val normalizedSpec = request.colorLabSpec.normalized()
        val intent = resolveIntent(normalizedSpec)
        val effectiveSpec = resolveEffectiveSpec(normalizedSpec, intent)
        val pipelineResult = StyleColorPipeline.render(
            StyleColorPipelineRequest(
                styleProfileId = request.styleProfileId,
                baseRenderSpec = request.baseRenderSpec,
                colorLabSpec = effectiveSpec,
                colorScience = request.colorScience,
                styleStrength = request.styleStrength
            )
        )

        return ColorIntentPlan(
            intent = intent,
            effectiveColorLabSpec = effectiveSpec,
            colorScience = pipelineResult.colorScience,
            inheritedBaseSpec = pipelineResult.inheritedBaseSpec,
            finalRenderSpec = pipelineResult.finalRenderSpec,
            recipe = pipelineResult.perceptualColorRecipe.tunedForIntent(intent),
            diagnostics = listOf("color-intent:${intent.id}") + pipelineResult.notes
        )
    }

    private fun resolveIntent(spec: ColorLabSpec): ColorIntentPreset {
        ColorIntentPreset.fromId(spec.presetId)?.let { return it }
        return if (spec.colorAxis == 0f && spec.toneAxis == 0f && spec.strength > 0f) {
            ColorIntentPreset.NATURAL
        } else {
            ColorIntentPreset.CUSTOM
        }
    }

    private fun resolveEffectiveSpec(
        spec: ColorLabSpec,
        intent: ColorIntentPreset
    ): ColorLabSpec {
        val hasExplicitAxes = spec.colorAxis != 0f || spec.toneAxis != 0f
        if (intent == ColorIntentPreset.CUSTOM || hasExplicitAxes) {
            return spec.copy(presetId = intent.id.takeUnless { intent == ColorIntentPreset.CUSTOM && spec.presetId == null })
        }
        return intent.defaultSpec.copy(
            strength = (intent.defaultSpec.strength * spec.strength).coerceIn(0f, 1f),
            version = spec.version
        ).normalized()
    }
}

private fun PerceptualColorRecipe.tunedForIntent(intent: ColorIntentPreset): PerceptualColorRecipe {
    if (isNeutral) return this
    return when (intent) {
        ColorIntentPreset.SOFT_WHITE -> copy(
            neutralProtection = neutralProtection.coerceAtLeast(0.78f),
            skinProtection = skinProtection.coerceAtLeast(0.76f)
        )
        ColorIntentPreset.BLUE_TONE -> copy(
            neutralProtection = neutralProtection.coerceAtLeast(0.80f),
            skinProtection = skinProtection.coerceAtLeast(0.72f)
        )
        ColorIntentPreset.CLEAR -> copy(
            neutralProtection = neutralProtection.coerceAtLeast(0.82f),
            skinProtection = skinProtection.coerceAtLeast(0.74f)
        )
        ColorIntentPreset.NATURAL,
        ColorIntentPreset.CUSTOM -> this
    }
}
