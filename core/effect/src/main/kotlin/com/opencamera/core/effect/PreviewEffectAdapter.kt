package com.opencamera.core.effect

import com.opencamera.core.settings.FilterRenderSpec

/**
 * Pure-JVM ARGB color constants (matching android.graphics.Color values).
 */
private const val COLOR_WHITE: Int = -1 // 0xFFFFFFFF

class PreviewEffectAdapter {

    fun adapt(spec: EffectSpec): PreviewEffectRenderModel {
        val filter = spec.find<FilterEffect>()
        val watermark = spec.find<WatermarkEffect>()
        val frame = spec.find<FrameEffect>()

        return PreviewEffectRenderModel(
            filterOverlay = filter?.let { buildFilterOverlay(it) },
            watermarkHint = watermark?.let { buildWatermarkHint(it) },
            frameGuideline = frame?.let { buildFrameGuideline(it) },
            compositionGrid = null
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
