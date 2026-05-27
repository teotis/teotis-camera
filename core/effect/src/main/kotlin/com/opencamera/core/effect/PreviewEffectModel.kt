package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.media.SceneMaskDescriptor
import com.opencamera.core.media.SceneMaskQuality
import com.opencamera.core.media.SceneMaskRole
import com.opencamera.core.media.SceneMaskTransform
import com.opencamera.core.settings.WatermarkTextPlacement

enum class WatermarkPreviewShape {
    TEXT_ONLY,
    BACKED_TEXT,
    EXPANDED_FRAME,
    FOUR_BORDER
}

data class PreviewEffectRenderModel(
    val filterOverlay: FilterOverlaySpec?,
    val watermarkHint: WatermarkHintSpec?,
    val frameGuideline: FrameGuidelineSpec?,
    val compositionGrid: CompositionGridSpec?,
    val colorTransform: PreviewColorTransform = PreviewColorTransform.NONE,
    val subjectMaskPreview: SubjectMaskPreviewDescriptor = SubjectMaskPreviewDescriptor.UNAVAILABLE
) {
    companion object {
        val EMPTY = PreviewEffectRenderModel(null, null, null, null)
    }
}

data class SubjectMaskPreviewDescriptor(
    val isAvailable: Boolean,
    val backendId: String,
    val isApproximate: Boolean
) {
    companion object {
        val UNAVAILABLE = SubjectMaskPreviewDescriptor(
            isAvailable = false,
            backendId = "none",
            isApproximate = true
        )
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
    val shape: WatermarkPreviewShape = WatermarkPreviewShape.BACKED_TEXT,
    val textScale: Float = 1f
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

data class PreviewSceneMaskSnapshot(
    val descriptor: SceneMaskDescriptor,
    val timestampMillis: Long,
    val isStale: Boolean,
    val backendId: String,
    val quality: SceneMaskQuality,
    val isAvailable: Boolean
) {
    companion object {
        val UNAVAILABLE = PreviewSceneMaskSnapshot(
            descriptor = SceneMaskDescriptor(
                maskId = "preview-unavailable",
                role = SceneMaskRole.PERSON_SUBJECT,
                quality = SceneMaskQuality.UNAVAILABLE,
                backendId = "none",
                confidence = 0f,
                transform = SceneMaskTransform(
                    sourceWidth = 0, sourceHeight = 0,
                    maskWidth = 0, maskHeight = 0,
                    rotationDegrees = 0
                )
            ),
            timestampMillis = 0L,
            isStale = true,
            backendId = "none",
            quality = SceneMaskQuality.UNAVAILABLE,
            isAvailable = false
        )
    }
}
