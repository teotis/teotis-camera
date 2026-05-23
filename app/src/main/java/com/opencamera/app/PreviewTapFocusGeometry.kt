package com.opencamera.app

import android.graphics.RectF

internal data class NormalizedPreviewTap(
    val x: Float,
    val y: Float
)

internal fun normalizedPreviewTapOrNull(
    tapX: Float,
    tapY: Float,
    viewWidth: Int,
    viewHeight: Int,
    activeFrameRect: RectF? = null
): NormalizedPreviewTap? {
    if (viewWidth <= 0 || viewHeight <= 0) return null
    if (activeFrameRect != null && !activeFrameRect.contains(tapX, tapY)) return null
    return if (activeFrameRect != null) {
        NormalizedPreviewTap(
            x = ((tapX - activeFrameRect.left) / activeFrameRect.width()).coerceIn(0f, 1f),
            y = ((tapY - activeFrameRect.top) / activeFrameRect.height()).coerceIn(0f, 1f)
        )
    } else {
        NormalizedPreviewTap(
            x = (tapX / viewWidth.toFloat()).coerceIn(0f, 1f),
            y = (tapY / viewHeight.toFloat()).coerceIn(0f, 1f)
        )
    }
}
