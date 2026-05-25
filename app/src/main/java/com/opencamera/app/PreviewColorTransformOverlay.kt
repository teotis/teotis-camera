package com.opencamera.app

import com.opencamera.core.effect.FilterOverlaySpec
import com.opencamera.core.effect.PreviewColorTransform

internal fun previewColorTransformOverlaySpec(
    transform: PreviewColorTransform
): FilterOverlaySpec? {
    if (transform.tintAlpha <= 0f || transform.tintColor == 0) return null
    return FilterOverlaySpec(
        tintColor = transform.tintColor,
        tintAlpha = transform.tintAlpha.coerceIn(0f, 0.6f),
        vignetteStrength = 0f,
        warmthShift = 0f
    )
}
