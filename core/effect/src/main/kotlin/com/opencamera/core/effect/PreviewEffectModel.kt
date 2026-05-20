package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.WatermarkTextPlacement

data class PreviewEffectRenderModel(
    val filterOverlay: FilterOverlaySpec?,
    val watermarkHint: WatermarkHintSpec?,
    val frameGuideline: FrameGuidelineSpec?,
    val compositionGrid: CompositionGridSpec?
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
    val opacity: Float
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
