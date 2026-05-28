package com.opencamera.app.camera

internal data class PreviewMeteringPixelPoint(
    val x: Float,
    val y: Float
)

internal fun previewMeteringPixelPoint(
    normalizedX: Float,
    normalizedY: Float,
    viewWidth: Int,
    viewHeight: Int
): PreviewMeteringPixelPoint {
    val width = viewWidth.coerceAtLeast(1)
    val height = viewHeight.coerceAtLeast(1)
    return PreviewMeteringPixelPoint(
        x = normalizedX.coerceIn(0f, 1f) * width.toFloat(),
        y = normalizedY.coerceIn(0f, 1f) * height.toFloat()
    )
}
