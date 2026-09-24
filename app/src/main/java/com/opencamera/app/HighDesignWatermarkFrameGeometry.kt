package com.opencamera.app

import android.graphics.RectF

/**
 * Shared photo-slot geometry for the static high-design watermark packages.
 *
 * Preview may simplify text and material, but it must preserve the same photo
 * window and frame proportions as the saved render.
 */
internal data class HighDesignWatermarkFrameMetrics(
    val sideBorder: Float,
    val topBorder: Float,
    val bottomBandHeight: Float
) {
    fun destinationAround(photoSlot: RectF): RectF = RectF(
        photoSlot.left - sideBorder,
        photoSlot.top - topBorder,
        photoSlot.right + sideBorder,
        photoSlot.bottom + bottomBandHeight
    )
}

internal data class HighDesignWatermarkPreviewLayout(
    val photoSlot: RectF,
    val destination: RectF
)

internal fun highDesignWatermarkFrameMetrics(
    photoWidth: Float,
    photoHeight: Float,
    minimumSideBorder: Float = 0f,
    minimumTopBorder: Float = 0f,
    minimumBottomBandHeight: Float = 0f
): HighDesignWatermarkFrameMetrics = HighDesignWatermarkFrameMetrics(
    sideBorder = (photoWidth * 0.027f).coerceAtLeast(minimumSideBorder),
    topBorder = (photoHeight * 0.083f).coerceAtLeast(minimumTopBorder),
    bottomBandHeight = (photoHeight * 0.128f).coerceAtLeast(minimumBottomBandHeight)
)

internal fun highDesignWatermarkAssetSuffix(outputAspect: Float): String {
    val portraitAspect = 1080f / 1660f
    val squareAspect = 1f
    val landscapeAspect = 1660f / 1080f
    return listOf(
        "portrait" to portraitAspect,
        "square" to squareAspect,
        "landscape" to landscapeAspect
    ).minBy { (_, referenceAspect) -> kotlin.math.abs(referenceAspect - outputAspect) }.first
}

internal fun isStaticHighDesignWatermarkTemplate(templateId: String?): Boolean =
    templateId == "blue-hour" || templateId == "van-gogh-starry"

internal fun highDesignWatermarkPreviewLayout(
    basePhotoSlot: RectF,
    availableBounds: RectF,
    preferredPhotoScale: Float = 0.80f
): HighDesignWatermarkPreviewLayout {
    val baseWidth = basePhotoSlot.width().coerceAtLeast(1f)
    val baseHeight = basePhotoSlot.height().coerceAtLeast(1f)
    val unitMetrics = highDesignWatermarkFrameMetrics(1f, 1f)
    val widthFactor = 1f + unitMetrics.sideBorder * 2f
    val heightFactor = 1f + unitMetrics.topBorder + unitMetrics.bottomBandHeight
    val photoScale = minOf(
        preferredPhotoScale,
        availableBounds.width() / (baseWidth * widthFactor),
        availableBounds.height() / (baseHeight * heightFactor),
        1f
    ).coerceAtLeast(0.01f)
    val photoWidth = baseWidth * photoScale
    val photoHeight = baseHeight * photoScale
    val metrics = highDesignWatermarkFrameMetrics(photoWidth, photoHeight)
    val destinationWidth = photoWidth + metrics.sideBorder * 2f
    val destinationHeight = photoHeight + metrics.topBorder + metrics.bottomBandHeight
    val centeredLeft = basePhotoSlot.centerX() - destinationWidth / 2f
    val centeredTop = basePhotoSlot.centerY() - destinationHeight / 2f
    val destinationLeft = centeredLeft.coerceIn(
        availableBounds.left,
        (availableBounds.right - destinationWidth).coerceAtLeast(availableBounds.left)
    )
    val destinationTop = centeredTop.coerceIn(
        availableBounds.top,
        (availableBounds.bottom - destinationHeight).coerceAtLeast(availableBounds.top)
    )
    val destination = RectF(
        destinationLeft,
        destinationTop,
        destinationLeft + destinationWidth,
        destinationTop + destinationHeight
    )
    return HighDesignWatermarkPreviewLayout(
        photoSlot = RectF(
            destination.left + metrics.sideBorder,
            destination.top + metrics.topBorder,
            destination.right - metrics.sideBorder,
            destination.bottom - metrics.bottomBandHeight
        ),
        destination = destination
    )
}
