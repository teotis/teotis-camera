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
    activeFrameLeft: Float = Float.NEGATIVE_INFINITY,
    activeFrameTop: Float = Float.NEGATIVE_INFINITY,
    activeFrameRight: Float = Float.POSITIVE_INFINITY,
    activeFrameBottom: Float = Float.POSITIVE_INFINITY
): NormalizedPreviewTap? {
    if (viewWidth <= 0 || viewHeight <= 0) return null
    if (tapX < activeFrameLeft || tapX > activeFrameRight ||
        tapY < activeFrameTop || tapY > activeFrameBottom) return null
    return NormalizedPreviewTap(
        x = (tapX / viewWidth.toFloat()).coerceIn(0f, 1f),
        y = (tapY / viewHeight.toFloat()).coerceIn(0f, 1f)
    )
}

/** Convenience for callers that already hold a [RectF]. */
internal fun normalizedPreviewTapOrNull(
    tapX: Float,
    tapY: Float,
    viewWidth: Int,
    viewHeight: Int,
    activeFrameRect: RectF
): NormalizedPreviewTap? = normalizedPreviewTapOrNull(
    tapX, tapY, viewWidth, viewHeight,
    activeFrameLeft = activeFrameRect.left,
    activeFrameTop = activeFrameRect.top,
    activeFrameRight = activeFrameRect.right,
    activeFrameBottom = activeFrameRect.bottom
)

/** Convenience for callers that may hold a nullable [RectF]. */
internal fun normalizedPreviewTapOrNull(
    tapX: Float,
    tapY: Float,
    viewWidth: Int,
    viewHeight: Int,
    activeFrameRect: RectF?
): NormalizedPreviewTap? = if (activeFrameRect != null) {
    normalizedPreviewTapOrNull(tapX, tapY, viewWidth, viewHeight, activeFrameRect)
} else {
    normalizedPreviewTapOrNull(tapX, tapY, viewWidth, viewHeight)
}
