package com.opencamera.core.settings

fun ManualCaptureParams.compactSummary(): String =
    ManualCaptureParamsCodec.compactSummary(this)

fun ManualCaptureParams.toMetadataTags(
    prefix: String = ManualCaptureParamsCodec.DEFAULT_METADATA_PREFIX
): Map<String, String> = ManualCaptureParamsCodec.toMetadataTags(this, prefix)

fun FilterRenderSpec.toMetadataTags(
    prefix: String = FilterRenderSpecCodec.DEFAULT_METADATA_PREFIX
): Map<String, String> = FilterRenderSpecCodec.toMetadataTags(this, prefix)

fun parseFilterRenderSpec(
    tags: Map<String, String>,
    prefix: String = FilterRenderSpecCodec.DEFAULT_METADATA_PREFIX
): FilterRenderSpec? = FilterRenderSpecCodec.fromMetadataTags(tags, prefix)

private const val RECIPE_METADATA_PREFIX = "recipe"

fun PerceptualColorRecipe.toMetadataTags(): Map<String, String> {
    if (isNeutral) return emptyMap()
    return linkedMapOf(
        "$RECIPE_METADATA_PREFIX.toneLift" to toneLift.toString(),
        "$RECIPE_METADATA_PREFIX.toneDepth" to toneDepth.toString(),
        "$RECIPE_METADATA_PREFIX.chromaBoost" to chromaBoost.toString(),
        "$RECIPE_METADATA_PREFIX.warmthBias" to warmthBias.toString(),
        "$RECIPE_METADATA_PREFIX.tintBias" to tintBias.toString(),
        "$RECIPE_METADATA_PREFIX.shadowTint" to shadowTint.toString(),
        "$RECIPE_METADATA_PREFIX.highlightTint" to highlightTint.toString(),
        "$RECIPE_METADATA_PREFIX.neutralProtection" to neutralProtection.toString(),
        "$RECIPE_METADATA_PREFIX.skinProtection" to skinProtection.toString()
    )
}

fun parsePerceptualColorRecipe(tags: Map<String, String>): PerceptualColorRecipe {
    val toneLift = readRecipeTag(tags, "toneLift") ?: return PerceptualColorRecipe.NEUTRAL
    return PerceptualColorRecipe(
        toneLift = toneLift,
        toneDepth = readRecipeTag(tags, "toneDepth") ?: 0f,
        chromaBoost = readRecipeTag(tags, "chromaBoost") ?: 0f,
        warmthBias = readRecipeTag(tags, "warmthBias") ?: 0f,
        tintBias = readRecipeTag(tags, "tintBias") ?: 0f,
        shadowTint = readRecipeTag(tags, "shadowTint") ?: 0f,
        highlightTint = readRecipeTag(tags, "highlightTint") ?: 0f,
        neutralProtection = readRecipeTag(tags, "neutralProtection") ?: 0f,
        skinProtection = readRecipeTag(tags, "skinProtection") ?: 0f
    )
}

private fun readRecipeTag(tags: Map<String, String>, key: String): Float? {
    tags["$RECIPE_METADATA_PREFIX.$key"]?.toFloatOrNull()?.let { return it }
    tags["recipe${key.replaceFirstChar { it.uppercase() }}"]?.toFloatOrNull()?.let { return it }
    return null
}
