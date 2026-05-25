package com.opencamera.core.settings

fun ManualCaptureParams.compactSummary(): String {
    return buildString {
        append("RAW ")
        append(if (rawEnabled) "On" else "Off")
        append(" | ISO ")
        append(iso?.toString() ?: "Auto")
        append(" | S ")
        append(shutterSpeedMillis?.let { "${it}ms" } ?: "Auto")
        append(" | WB ")
        append(whiteBalanceKelvin?.let { "${it}K" } ?: "Auto")
    }
}

fun ManualCaptureParams.toMetadataTags(prefix: String = "manualDraft"): Map<String, String> {
    return buildMap {
        put("${prefix}Raw", if (rawEnabled) "on" else "off")
        put("${prefix}Iso", iso?.toString() ?: "auto")
        put("${prefix}ShutterSpeedMillis", shutterSpeedMillis?.toString() ?: "auto")
        put(
            "${prefix}ExposureCompensationSteps",
            exposureCompensationSteps?.toString() ?: "auto"
        )
        put(
            "${prefix}FocusDistanceDiopters",
            focusDistanceDiopters?.toString() ?: "auto"
        )
        put("${prefix}ApertureFNumber", apertureFNumber?.toString() ?: "auto")
        put("${prefix}WhiteBalanceKelvin", whiteBalanceKelvin?.toString() ?: "auto")
    }
}

private const val FILTER_SPEC_METADATA_PREFIX = "filterSpec"

fun FilterRenderSpec.toMetadataTags(prefix: String = FILTER_SPEC_METADATA_PREFIX): Map<String, String> {
    return linkedMapOf(
        "${prefix}.version" to "1",
        "${prefix}.brightnessShift" to brightnessShift.toString(),
        "${prefix}.contrast" to contrast.toString(),
        "${prefix}.saturation" to saturation.toString(),
        "${prefix}.warmthShift" to warmthShift.toString(),
        "${prefix}.tintShift" to tintShift.toString(),
        "${prefix}.monochromeMix" to monochromeMix.toString(),
        "${prefix}.vignetteStrength" to vignetteStrength.toString(),
        "${prefix}.softGlowStrength" to softGlowStrength.toString(),
        "${prefix}.haloStrength" to haloStrength.toString(),
        "${prefix}.grainStrength" to grainStrength.toString(),
        "${prefix}.sharpnessBoost" to sharpnessBoost.toString(),
        "${prefix}.highlightCompression" to highlightCompression.toString(),
        "${prefix}.shadowLift" to shadowLift.toString(),
        "${prefix}.warmBoost" to warmBoost.toString(),
        "${prefix}.coolBoost" to coolBoost.toString()
    )
}

fun parseFilterRenderSpec(
    tags: Map<String, String>,
    prefix: String = FILTER_SPEC_METADATA_PREFIX
): FilterRenderSpec? {
    if (tags["${prefix}.version"] == null) {
        return null
    }
    return FilterRenderSpec(
        brightnessShift = tags["${prefix}.brightnessShift"]?.toIntOrNull() ?: 0,
        contrast = tags["${prefix}.contrast"]?.toFloatOrNull() ?: 1f,
        saturation = tags["${prefix}.saturation"]?.toFloatOrNull() ?: 1f,
        warmthShift = tags["${prefix}.warmthShift"]?.toIntOrNull() ?: 0,
        tintShift = tags["${prefix}.tintShift"]?.toIntOrNull() ?: 0,
        monochromeMix = tags["${prefix}.monochromeMix"]?.toFloatOrNull() ?: 0f,
        vignetteStrength = tags["${prefix}.vignetteStrength"]?.toFloatOrNull() ?: 0f,
        softGlowStrength = tags["${prefix}.softGlowStrength"]?.toFloatOrNull() ?: 0f,
        haloStrength = tags["${prefix}.haloStrength"]?.toFloatOrNull() ?: 0f,
        grainStrength = tags["${prefix}.grainStrength"]?.toFloatOrNull() ?: 0f,
        sharpnessBoost = tags["${prefix}.sharpnessBoost"]?.toFloatOrNull() ?: 0f,
        highlightCompression = tags["${prefix}.highlightCompression"]?.toFloatOrNull() ?: 0f,
        shadowLift = tags["${prefix}.shadowLift"]?.toFloatOrNull() ?: 0f,
        warmBoost = tags["${prefix}.warmBoost"]?.toFloatOrNull() ?: 0f,
        coolBoost = tags["${prefix}.coolBoost"]?.toFloatOrNull() ?: 0f
    )
}

fun PerceptualColorRecipe.toMetadataTags(): Map<String, String> {
    if (isNeutral) return emptyMap()
    return linkedMapOf(
        "recipeToneLift" to toneLift.toString(),
        "recipeToneDepth" to toneDepth.toString(),
        "recipeChromaBoost" to chromaBoost.toString(),
        "recipeWarmthBias" to warmthBias.toString(),
        "recipeTintBias" to tintBias.toString(),
        "recipeShadowTint" to shadowTint.toString(),
        "recipeHighlightTint" to highlightTint.toString(),
        "recipeNeutralProtection" to neutralProtection.toString(),
        "recipeSkinProtection" to skinProtection.toString()
    )
}

fun parsePerceptualColorRecipe(tags: Map<String, String>): PerceptualColorRecipe {
    val toneLift = tags["recipeToneLift"]?.toFloatOrNull() ?: return PerceptualColorRecipe.NEUTRAL
    return PerceptualColorRecipe(
        toneLift = toneLift,
        toneDepth = tags["recipeToneDepth"]?.toFloatOrNull() ?: 0f,
        chromaBoost = tags["recipeChromaBoost"]?.toFloatOrNull() ?: 0f,
        warmthBias = tags["recipeWarmthBias"]?.toFloatOrNull() ?: 0f,
        tintBias = tags["recipeTintBias"]?.toFloatOrNull() ?: 0f,
        shadowTint = tags["recipeShadowTint"]?.toFloatOrNull() ?: 0f,
        highlightTint = tags["recipeHighlightTint"]?.toFloatOrNull() ?: 0f,
        neutralProtection = tags["recipeNeutralProtection"]?.toFloatOrNull() ?: 0f,
        skinProtection = tags["recipeSkinProtection"]?.toFloatOrNull() ?: 0f
    )
}
