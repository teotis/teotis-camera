package com.opencamera.app

import com.opencamera.core.effect.FilterOverlaySpec
import com.opencamera.core.effect.PreviewColorMatrixBuilder
import com.opencamera.core.effect.PreviewColorTransform

internal fun previewColorTransformOverlaySpec(
    transform: PreviewColorTransform
): FilterOverlaySpec? {
    val matrix = transform.matrix
        ?.takeUnless { PreviewColorMatrixBuilder.isIdentity(it) }
    val hasTint = transform.tintAlpha > 0f && transform.tintColor != 0
    if (matrix == null && !hasTint) return null
    return FilterOverlaySpec(
        tintColor = transform.tintColor,
        tintAlpha = if (hasTint) transform.tintAlpha.coerceIn(0f, 0.6f) else 0f,
        vignetteStrength = 0f,
        warmthShift = 0f,
        colorMatrix = matrix
    )
}
