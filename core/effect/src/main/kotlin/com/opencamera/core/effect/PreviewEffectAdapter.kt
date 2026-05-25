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

class PreviewEffectAdapter {

    fun adapt(
        spec: EffectSpec,
        maskSnapshot: PreviewSceneMaskSnapshot = PreviewSceneMaskSnapshot.UNAVAILABLE
    ): PreviewEffectRenderModel {
        val filter = spec.find<FilterEffect>()
        val watermark = spec.find<WatermarkEffect>()
        val frame = spec.find<FrameEffect>()

        return PreviewEffectRenderModel(
            filterOverlay = filter?.let { buildFilterOverlay(it) },
            watermarkHint = watermark?.let { buildWatermarkHint(it) },
            frameGuideline = frame?.let { buildFrameGuideline(it) },
            compositionGrid = null,
            subjectMaskPreview = buildSubjectMaskPreview(maskSnapshot),
            colorTransform = resolveColorTransform(maskSnapshot, filter?.recipe)
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
        recipe: PerceptualColorRecipe? = null
    ): PreviewColorTransform {
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
            previewText = effect.tokens["watermarkModel"] ?: "Watermark",
            opacity = effect.style.textOpacity.alphaFraction * 0.6f,
            shape = resolveWatermarkShape(effect.templateId)
        )
    }

    private fun resolveWatermarkShape(templateId: String): WatermarkPreviewShape {
        return when (templateId) {
            "pure-text" -> WatermarkPreviewShape.TEXT_ONLY
            "blur-four-border" -> WatermarkPreviewShape.FOUR_BORDER
            "travel-polaroid", "retro-frame" -> WatermarkPreviewShape.EXPANDED_FRAME
            else -> WatermarkPreviewShape.BACKED_TEXT
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
        return ((2f - saturation) * 0.15f + (contrast - 1f) * 0.1f)
            .coerceIn(0f, 0.4f)
    }
}
