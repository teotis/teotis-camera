package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.WatermarkTextPlacement

enum class WatermarkPreviewShape {
    TEXT_ONLY,
    BACKED_TEXT,
    EXPANDED_FRAME,
    FOUR_BORDER
}

enum class PreviewColorFidelity {
    /** No color transform — identity pass-through */
    NONE,
    /** Color matrix computed but not yet applied to preview surface */
    APPROXIMATE,
    /** Color matrix applied via ColorMatrixColorFilter */
    GOOD
}

data class PreviewEffectRenderModel(
    val filterOverlay: FilterOverlaySpec?,
    val watermarkHint: WatermarkHintSpec?,
    val frameGuideline: FrameGuidelineSpec?,
    val compositionGrid: CompositionGridSpec?,
    val colorTransform: PreviewColorTransform? = null,
    val colorFidelity: PreviewColorFidelity = PreviewColorFidelity.NONE
) {
    companion object {
        val EMPTY = PreviewEffectRenderModel(null, null, null, null)
    }
}

data class FilterOverlaySpec(
    val tintColor: Int,
    val tintAlpha: Float,
    val vignetteStrength: Float,
    val warmthShift: Float
)

data class WatermarkHintSpec(
    val templateId: String,
    val placement: WatermarkTextPlacement,
    val previewText: String,
    val opacity: Float,
    val shape: WatermarkPreviewShape = WatermarkPreviewShape.BACKED_TEXT
)

data class FrameGuidelineSpec(
    val ratio: FrameRatio,
    val borderColor: Int,
    val borderAlpha: Float
)

data class CompositionGridSpec(
    val mode: CompositionGridMode,
    val isVisible: Boolean
)
