package com.opencamera.core.effect

import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PerceptualColorRecipe
import com.opencamera.core.settings.PreviewColorFidelity
import kotlin.math.abs

import com.opencamera.core.media.SceneMaskQuality

/**
 * Pure-JVM ARGB color constants (matching android.graphics.Color values).
 */
private const val COLOR_WHITE: Int = -1 // 0xFFFFFFFF
private val COLOR_BAR_DARK: Int = 0xFE000000.toInt() // 0xFE000000
private val COLOR_TRANSLUCENT_BOTTOM_BAR: Int = 0xCC071321.toInt()

class PreviewEffectAdapter {

    fun adapt(
        spec: EffectSpec,
        maskSnapshot: PreviewSceneMaskSnapshot = PreviewSceneMaskSnapshot.UNAVAILABLE
    ): PreviewEffectRenderModel {
        val filter = spec.find<FilterEffect>()
        val watermark = spec.find<WatermarkEffect>()
        val frame = spec.find<FrameEffect>()

        val colorTransform = filter?.let { buildColorTransform(it) }

        return PreviewEffectRenderModel(
            filterOverlay = filter?.let { buildFilterOverlay(it) },
            watermarkHint = watermark?.let { buildWatermarkHint(it) },
            frameGuideline = frame?.let { buildFrameGuideline(it) },
            compositionGrid = null,
            subjectMaskPreview = buildSubjectMaskPreview(maskSnapshot),
            colorTransform = resolveColorTransform(maskSnapshot, filter?.recipe, colorTransform)
        )
    }

    private fun buildSubjectMaskPreview(
        snapshot: PreviewSceneMaskSnapshot
    ): SubjectMaskPreviewDescriptor {
        return SubjectMaskPreviewDescriptor(
            isAvailable = snapshot.isAvailable,
            backendId = snapshot.backendId,
            isApproximate = snapshot.quality != SceneMaskQuality.SAVED_PHOTO
        )
    }

    private fun resolveColorTransform(
        snapshot: PreviewSceneMaskSnapshot,
        recipe: PerceptualColorRecipe? = null,
        specTransform: PreviewColorTransform? = null
    ): PreviewColorTransform {
        // Prefer the spec-based color matrix (from FilterRenderSpec) when available
        if (specTransform != null && !specTransform.isIdentity) {
            return specTransform
        }
        val recipeTransform = recipe?.takeUnless { it.isNeutral }?.let { buildRecipeColorTransform(it) }
        val maskTransform = when {
            snapshot.isAvailable -> PreviewColorTransform.MASK_AWARE
            snapshot.backendId != "none" && snapshot.isStale -> PreviewColorTransform.FALLBACK
            else -> null
        }
        return recipeTransform ?: maskTransform ?: PreviewColorTransform.NONE
    }

    private fun buildRecipeColorTransform(recipe: PerceptualColorRecipe): PreviewColorTransform {
        val warmthStrength = (recipe.warmthBias * 25f).coerceIn(-1f, 1f)
        val tintStrength = (recipe.tintBias * 10f).coerceIn(-1f, 1f)
        val chromaStrength = (recipe.chromaBoost * 0.35f).coerceIn(0f, 0.55f)
        val toneLiftFactor = (recipe.toneLift * 0.55f).coerceIn(0f, 0.55f)
        val toneDepthFactor = (recipe.toneDepth * 0.50f).coerceIn(0f, 0.50f)
        val highlightTintFactor = (recipe.highlightTint * 0.18f).coerceIn(0f, 0.18f)
        val shadowTintFactor = (recipe.shadowTint * 0.20f).coerceIn(0f, 0.20f)
        val alpha = (chromaStrength + toneLiftFactor * 0.3f + toneDepthFactor * 0.35f +
            highlightTintFactor * 0.25f + shadowTintFactor * 0.25f)
            .coerceIn(0.05f, 0.52f)

        val r = (128 + warmthStrength * 90f + tintStrength * 25f).toInt().coerceIn(0, 255)
        val g = (128 - tintStrength * 30f - toneDepthFactor * 30f + toneLiftFactor * 20f)
            .toInt().coerceIn(0, 255)
        val b = (128 - warmthStrength * 90f + tintStrength * 20f - toneLiftFactor * 15f)
            .toInt().coerceIn(0, 255)
        val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        val effectiveStrength = chromaStrength + (toneLiftFactor + toneDepthFactor) * 0.4f +
            abs(recipe.warmthBias) * 0.3f
        val fidelity = if (effectiveStrength > 0.08f) {
            PreviewColorFidelity.APPROXIMATE
        } else {
            PreviewColorFidelity.DEGRADED
        }

        return PreviewColorTransform(
            tintColor = color,
            tintAlpha = alpha,
            fidelity = fidelity
        )
    }

    private fun buildColorTransform(effect: FilterEffect): PreviewColorTransform {
        return PreviewColorTransform.fromSpec(effect.renderSpec)
    }

    private fun buildFilterOverlay(effect: FilterEffect): FilterOverlaySpec {
        val spec = effect.renderSpec
        return FilterOverlaySpec(
            tintColor = resolveTintColor(spec),
            tintAlpha = resolveOverlayOpacity(spec),
            vignetteStrength = spec?.vignetteStrength ?: 0f,
            warmthShift = (spec?.warmthShift ?: 0).toFloat()
        )
    }

    private fun buildWatermarkHint(effect: WatermarkEffect): WatermarkHintSpec {
        return WatermarkHintSpec(
            templateId = effect.templateId,
            placement = effect.style.textPlacement,
            previewText = resolvePreviewText(effect),
            opacity = effect.style.textOpacity.alphaFraction * 0.6f,
            shape = resolveWatermarkShape(effect.templateId),
            textScale = effect.style.textScale.multiplier,
            previewLabels = buildPreviewLabels(effect),
            barBackground = resolveBarBackground(effect),
            decoration = resolveWatermarkDecoration(effect.templateId)
        )
    }

    private fun resolveWatermarkShape(templateId: String): WatermarkPreviewShape {
        return when (templateId) {
            "pure-text" -> WatermarkPreviewShape.BOTTOM_BAR
            "blur-four-border" -> WatermarkPreviewShape.FOUR_BORDER
            "professional-bottom-bar" -> WatermarkPreviewShape.BOTTOM_BAR
            "travel-polaroid",
            "retro-frame",
            "night-street",
            "van-gogh-starry",
            "blue-hour" -> WatermarkPreviewShape.EXPANDED_FRAME
            else -> WatermarkPreviewShape.BACKED_TEXT
        }
    }

    private fun resolveWatermarkDecoration(templateId: String): WatermarkPreviewDecoration {
        return when (templateId) {
            "travel-polaroid" -> WatermarkPreviewDecoration.TRAVEL_MAP
            "retro-frame" -> WatermarkPreviewDecoration.ARCHIVAL_PAPER
            "night-street" -> WatermarkPreviewDecoration.NIGHT_MEMORY
            "van-gogh-starry" -> WatermarkPreviewDecoration.STARRY_MOON
            "blue-hour" -> WatermarkPreviewDecoration.BLUE_HOUR
            "blur-four-border" -> WatermarkPreviewDecoration.IMPRESSION_CHROMA
            else -> WatermarkPreviewDecoration.NONE
        }
    }

    private fun resolvePreviewText(effect: WatermarkEffect): String {
        return when (effect.templateId) {
            "blue-hour" -> "蓝调时刻"
            "van-gogh-starry" -> metadataPreviewLabels(effect).joinToString(" · ")
                .ifBlank { "Watermark" }
            else -> effect.tokens["watermarkModel"] ?: "Watermark"
        }
    }

    private fun buildPreviewLabels(effect: WatermarkEffect): List<String> {
        if (
            effect.templateId != "professional-bottom-bar" &&
            effect.templateId != "pure-text" &&
            effect.templateId != "blur-four-border" &&
            effect.templateId != "van-gogh-starry" &&
            effect.templateId != "blue-hour"
        ) {
            return emptyList()
        }
        if (effect.templateId == "van-gogh-starry" || effect.templateId == "blue-hour") {
            return metadataPreviewLabels(effect)
        }
        val labels = mutableListOf<String>()
        effect.tokens["watermarkModel"]?.takeIf { it.isNotBlank() }?.let { labels.add(it) }
        effect.tokens["datetime"]?.takeIf { it.isNotBlank() }?.let { labels.add(it) }
        effect.tokens["camera-params"]?.takeIf { it.isNotBlank() }?.let { labels.add(it) }
        return labels.ifEmpty { listOf("Watermark") }
    }

    private fun metadataPreviewLabels(effect: WatermarkEffect): List<String> {
        val labels = mutableListOf<String>()
        effect.tokens["datetime"]?.takeIf { it.isNotBlank() }?.let { labels.add(it) }
        effect.tokens["location"]?.takeIf { it.isNotBlank() }?.let { labels.add(it) }
        effect.tokens["camera-params"]?.takeIf { it.isNotBlank() }?.let { labels.add(it) }
        return labels.ifEmpty { listOf("Watermark") }
    }

    private fun resolveBarBackground(effect: WatermarkEffect): Int {
        if (effect.templateId == "pure-text") return COLOR_TRANSLUCENT_BOTTOM_BAR
        if (effect.templateId != "professional-bottom-bar") return 0
        return when (effect.style.frameBackground) {
            com.opencamera.core.settings.WatermarkFrameBackground.DARK -> COLOR_BAR_DARK
            com.opencamera.core.settings.WatermarkFrameBackground.WHITE -> COLOR_WHITE
            com.opencamera.core.settings.WatermarkFrameBackground.SOURCE_BLUR -> COLOR_BAR_DARK
            com.opencamera.core.settings.WatermarkFrameBackground.SOURCE_LIGHT_BLUR -> COLOR_BAR_DARK
            com.opencamera.core.settings.WatermarkFrameBackground.SOURCE_VIVID_BLUR -> COLOR_BAR_DARK
        }
    }

    private fun buildFrameGuideline(effect: FrameEffect): FrameGuidelineSpec {
        return FrameGuidelineSpec(
            ratio = effect.ratio,
            borderColor = COLOR_WHITE,
            borderAlpha = 0.25f
        )
    }

    private fun resolveTintColor(spec: FilterRenderSpec?): Int {
        val warmth = (spec?.warmthShift ?: 0).toFloat()
        val tint = (spec?.tintShift ?: 0).toFloat()
        val warmBoost = (spec?.warmBoost ?: 0f)
        val coolBoost = (spec?.coolBoost ?: 0f)
        val effectiveWarmth = warmth + warmBoost * 6f - coolBoost * 6f
        val r = (128 + effectiveWarmth * 4.0f + tint * 2.8f).toInt().coerceIn(0, 255)
        val g = (128 - tint * 2.8f).toInt().coerceIn(0, 255)
        val b = (128 - effectiveWarmth * 4.0f + tint * 2.8f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun resolveOverlayOpacity(spec: FilterRenderSpec?): Float {
        val saturation = spec?.saturation ?: 1f
        val contrast = spec?.contrast ?: 1f
        // Overlay provides warm/cool tint supplement on top of the color matrix
        // applied to the preview surface (TextureView mode).
        return ((2f - saturation) * 0.10f + (contrast - 1f) * 0.08f)
            .coerceIn(0f, 0.30f)
    }
}
