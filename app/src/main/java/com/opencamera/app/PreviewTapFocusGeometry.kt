package com.opencamera.app

import android.graphics.RectF

internal data class NormalizedPreviewTap(
    val x: Float,
    val y: Float
)

internal data class PreviewTapFrameBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float
        get() = right - left

    val height: Float
        get() = bottom - top

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    fun isValid(): Boolean = width > 0f && height > 0f
}

internal fun normalizedPreviewTapOrNull(
    tapX: Float,
    tapY: Float,
    viewWidth: Int,
    viewHeight: Int,
    activeFrameRect: RectF? = null
): NormalizedPreviewTap? {
    return normalizedPreviewTapOrNull(
        tapX = tapX,
        tapY = tapY,
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        activeFrameBounds = activeFrameRect?.let {
            PreviewTapFrameBounds(
                left = it.left,
                top = it.top,
                right = it.right,
                bottom = it.bottom
            )
        }
    )
}

internal fun normalizedPreviewTapOrNull(
    tapX: Float,
    tapY: Float,
    viewWidth: Int,
    viewHeight: Int,
    activeFrameBounds: PreviewTapFrameBounds?
): NormalizedPreviewTap? {
    if (viewWidth <= 0 || viewHeight <= 0) return null
    if (activeFrameBounds != null) {
        if (!activeFrameBounds.isValid() || !activeFrameBounds.contains(tapX, tapY)) return null
        return NormalizedPreviewTap(
            x = (tapX / viewWidth.toFloat()).coerceIn(0f, 1f),
            y = (tapY / viewHeight.toFloat()).coerceIn(0f, 1f)
        )
    }
    return NormalizedPreviewTap(
        x = (tapX / viewWidth.toFloat()).coerceIn(0f, 1f),
        y = (tapY / viewHeight.toFloat()).coerceIn(0f, 1f)
    )
}
