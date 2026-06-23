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
    FOUR_BORDER,
    BOTTOM_BAR
}

enum class WatermarkPreviewDecoration {
    NONE,
    TRAVEL_MAP,
    ARCHIVAL_PAPER,
    NIGHT_MEMORY,
    STARRY_MOON,
    BLUE_HOUR,
    IMPRESSION_CHROMA
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

/**
 * Overlay spec for the preview filter surface.
 *
 * [colorMatrix] carries the non-identity 4x5 color matrix produced by
 * [PreviewColorTransform]. When non-null, downstream overlay views must apply
 * it via [android.graphics.ColorMatrixColorFilter] so non-tint transforms
 * (black-and-white, warmth, coolness, contrast) reach the preview surface
 * instead of being silently dropped when [tintAlpha] is zero.
 */
data class FilterOverlaySpec(
    val tintColor: Int,
    val tintAlpha: Float,
    val vignetteStrength: Float,
    val warmthShift: Float,
    val colorMatrix: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FilterOverlaySpec) return false
        if (tintColor != other.tintColor) return false
        if (tintAlpha != other.tintAlpha) return false
        if (vignetteStrength != other.vignetteStrength) return false
        if (warmthShift != other.warmthShift) return false
        return when {
            colorMatrix == null && other.colorMatrix == null -> true
            colorMatrix != null && other.colorMatrix != null ->
                colorMatrix.contentEquals(other.colorMatrix)
            else -> false
        }
    }

    override fun hashCode(): Int {
        var result = tintColor
        result = 31 * result + tintAlpha.hashCode()
        result = 31 * result + vignetteStrength.hashCode()
        result = 31 * result + warmthShift.hashCode()
        result = 31 * result + (colorMatrix?.contentHashCode() ?: 0)
        return result
    }
}

data class WatermarkHintSpec(
    val templateId: String,
    val placement: WatermarkTextPlacement,
    val previewText: String,
    val opacity: Float,
    val shape: WatermarkPreviewShape = WatermarkPreviewShape.BACKED_TEXT,
    val textScale: Float = 1f,
    val previewLabels: List<String> = emptyList(),
    val barBackground: Int = 0,
    val decoration: WatermarkPreviewDecoration = WatermarkPreviewDecoration.NONE
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
