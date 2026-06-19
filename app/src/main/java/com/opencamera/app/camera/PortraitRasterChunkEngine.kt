package com.opencamera.app.camera

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Isolated CPU row-chunk renderer that reproduces the mask-aware and focus portrait
 * pixel formulas exactly while bounding blend buffers to O(width * chunkRows).
 *
 * Reads and writes pixels in bounded row chunks via per-chunk getPixels/setPixels
 * calls. This keeps both the working memory and the per-chunk computation at
 * O(width * chunkRows), avoiding full-frame IntArray(width * height) allocations.
 *
 * Not wired into production; integration is owned by a downstream package.
 */
internal class PortraitRasterChunkEngine(
    chunkRows: Int = DEFAULT_CHUNK_ROWS
) {
    val chunkRows: Int

    init {
        require(chunkRows > 0) { "chunkRows must be positive, was $chunkRows" }
        this.chunkRows = chunkRows
    }

    /**
     * Buffer capacity in pixels: width * min(chunkRows, height).
     * Visible for testing buffer-bound assertions; not part of public product settings.
     */
    fun bufferSize(width: Int, height: Int): Int {
        val effective = minOf(chunkRows, height)
        return width * effective
    }

    fun renderMaskAware(
        original: Bitmap,
        blurred: Bitmap,
        spec: PortraitRenderSpec,
        mask: SavedPhotoMaskPixels
    ) {
        require(original.width == blurred.width && original.height == blurred.height) {
            "Bitmap dimensions must match: original=${original.width}x${original.height} " +
                "blurred=${blurred.width}x${blurred.height}"
        }
        val width = original.width
        val height = original.height
        require(width > 0 && height > 0) { "Bitmap dimensions must be positive: ${width}x$height" }

        val effective = minOf(chunkRows, height)

        val maskMapper = SceneMaskCoordinateMapper(
            maskWidth = mask.maskWidth,
            maskHeight = mask.maskHeight,
            targetWidth = width,
            targetHeight = height
        )
        val frameCenterX = width / 2f
        val frameCenterY = height / 2f
        val maxDistance = max(1f, sqrt(frameCenterX * frameCenterX + frameCenterY * frameCenterY))

        var startRow = 0
        while (startRow < height) {
            val endRow = minOf(startRow + effective, height)
            val rowCount = endRow - startRow
            val chunkPx = width * rowCount
            val srcChunk = IntArray(chunkPx)
            val bldChunk = IntArray(chunkPx)
            original.getPixels(srcChunk, 0, width, 0, startRow, width, rowCount)
            blurred.getPixels(bldChunk, 0, width, 0, startRow, width, rowCount)

            for (row in 0 until rowCount) {
                val srcRowOff = row * width
                val y = startRow + row
                for (x in 0 until width) {
                    val src = srcChunk[srcRowOff + x]
                    val bld = bldChunk[srcRowOff + x]
                    val alpha = src ushr 24 and 0xFF

                    val mx = maskMapper.maskX(x)
                    val my = maskMapper.maskY(y)
                    val rawAlpha = mask.sampleAlpha(mx, my)
                    val subjectWeight = smoothstep(0.15f, 0.85f, rawAlpha)
                    val blurMix = 1f - subjectWeight

                    var red = ((src ushr 16) and 0xFF).toFloat()
                    var green = ((src ushr 8) and 0xFF).toFloat()
                    var blue = (src and 0xFF).toFloat()
                    val bldR = ((bld ushr 16) and 0xFF).toFloat()
                    val bldG = ((bld ushr 8) and 0xFF).toFloat()
                    val bldB = (bld and 0xFF).toFloat()

                    red = mixChannel(red, bldR, blurMix)
                    green = mixChannel(green, bldG, blurMix)
                    blue = mixChannel(blue, bldB, blurMix)

                    val fdx = x - frameCenterX
                    val fdy = y - frameCenterY
                    val fd = sqrt(fdx * fdx + fdy * fdy)
                    val v = 1f - ((fd / maxDistance) * spec.vignetteStrength).coerceIn(0f, 0.28f)
                    red *= v; green *= v; blue *= v

                    val sm = (spec.subjectSmoothing * subjectWeight).coerceIn(0f, 0.28f)
                    if (sm > 0f) {
                        red = mixChannel(red, bldR, sm)
                        green = mixChannel(green, bldG, sm)
                        blue = mixChannel(blue, bldB, sm)
                    }

                    val lum = red * 0.299f + green * 0.587f + blue * 0.114f
                    val satB = spec.subjectSaturationBoost * subjectWeight
                    if (satB > 0f) {
                        red = lum + (red - lum) * (1f + satB)
                        green = lum + (green - lum) * (1f + satB)
                        blue = lum + (blue - lum) * (1f + satB)
                    }

                    val lift = spec.subjectLift * subjectWeight
                    if (lift > 0f) {
                        red += (255f - red) * lift
                        green += (255f - green) * lift
                        blue += (255f - blue) * lift
                    }

                    val hf = ((lum / 255f) - 0.52f).coerceIn(0f, 1f)
                    val subBloom = spec.highlightBloom * subjectWeight * hf
                    val bgBloom = spec.backgroundBloom * blurMix * hf
                    val totalBloom = (subBloom + bgBloom).coerceIn(0f, 0.24f)
                    if (totalBloom > 0f) {
                        red += (255f - red) * totalBloom
                        green += (255f - green) * totalBloom
                        blue += (255f - blue) * totalBloom
                    }

                    srcChunk[srcRowOff + x] = (alpha shl 24) or
                        (clampChannel(red).toInt() shl 16) or
                        (clampChannel(green).toInt() shl 8) or
                        clampChannel(blue).toInt()
                }
            }

            original.setPixels(srcChunk, 0, width, 0, startRow, width, rowCount)
            startRow = endRow
        }
    }

    fun renderFocus(
        original: Bitmap,
        blurred: Bitmap,
        spec: PortraitRenderSpec
    ) {
        require(original.width == blurred.width && original.height == blurred.height) {
            "Bitmap dimensions must match: original=${original.width}x${original.height} " +
                "blurred=${blurred.width}x${blurred.height}"
        }
        val width = original.width
        val height = original.height
        require(width > 0 && height > 0) { "Bitmap dimensions must be positive: ${width}x$height" }

        val effective = minOf(chunkRows, height)

        val focusCenterX = width * 0.5f
        val focusCenterY = height * if (spec.subjectTracking) 0.42f else 0.46f
        val radiusX = max(1f, width * spec.focusRadiusXFraction)
        val radiusY = max(1f, height * spec.focusRadiusYFraction)
        val frameCenterX = width / 2f
        val frameCenterY = height / 2f
        val maxDistance = max(1f, sqrt(frameCenterX * frameCenterX + frameCenterY * frameCenterY))

        var startRow = 0
        while (startRow < height) {
            val endRow = minOf(startRow + effective, height)
            val rowCount = endRow - startRow
            val chunkPx = width * rowCount
            val srcChunk = IntArray(chunkPx)
            val bldChunk = IntArray(chunkPx)
            original.getPixels(srcChunk, 0, width, 0, startRow, width, rowCount)
            blurred.getPixels(bldChunk, 0, width, 0, startRow, width, rowCount)

            for (row in 0 until rowCount) {
                val srcRowOff = row * width
                val y = startRow + row
                for (x in 0 until width) {
                    val src = srcChunk[srcRowOff + x]
                    val bld = bldChunk[srcRowOff + x]
                    val alpha = src ushr 24 and 0xFF

                    val dx = (x - focusCenterX) / radiusX
                    val dy = (y - focusCenterY) / radiusY
                    val nd = sqrt(dx * dx + dy * dy)
                    val blurMix = smoothstep(1f, 1f + spec.edgeSoftness, nd)
                    val subjectWeight = 1f - blurMix

                    var red = ((src ushr 16) and 0xFF).toFloat()
                    var green = ((src ushr 8) and 0xFF).toFloat()
                    var blue = (src and 0xFF).toFloat()
                    val bldR = ((bld ushr 16) and 0xFF).toFloat()
                    val bldG = ((bld ushr 8) and 0xFF).toFloat()
                    val bldB = (bld and 0xFF).toFloat()

                    red = mixChannel(red, bldR, blurMix)
                    green = mixChannel(green, bldG, blurMix)
                    blue = mixChannel(blue, bldB, blurMix)

                    val fdx = x - frameCenterX
                    val fdy = y - frameCenterY
                    val fd = sqrt(fdx * fdx + fdy * fdy)
                    val v = 1f - ((fd / maxDistance) * spec.vignetteStrength).coerceIn(0f, 0.28f)
                    red *= v; green *= v; blue *= v

                    val sm = (spec.subjectSmoothing * subjectWeight).coerceIn(0f, 0.28f)
                    if (sm > 0f) {
                        red = mixChannel(red, bldR, sm)
                        green = mixChannel(green, bldG, sm)
                        blue = mixChannel(blue, bldB, sm)
                    }

                    val lum = red * 0.299f + green * 0.587f + blue * 0.114f
                    val satB = spec.subjectSaturationBoost * subjectWeight
                    if (satB > 0f) {
                        red = lum + (red - lum) * (1f + satB)
                        green = lum + (green - lum) * (1f + satB)
                        blue = lum + (blue - lum) * (1f + satB)
                    }

                    val lift = spec.subjectLift * subjectWeight
                    if (lift > 0f) {
                        red += (255f - red) * lift
                        green += (255f - green) * lift
                        blue += (255f - blue) * lift
                    }

                    val hf = ((lum / 255f) - 0.52f).coerceIn(0f, 1f)
                    val subBloom = spec.highlightBloom * subjectWeight * hf
                    val bgBloom = spec.backgroundBloom * blurMix * hf
                    val totalBloom = (subBloom + bgBloom).coerceIn(0f, 0.24f)
                    if (totalBloom > 0f) {
                        red += (255f - red) * totalBloom
                        green += (255f - green) * totalBloom
                        blue += (255f - blue) * totalBloom
                    }

                    srcChunk[srcRowOff + x] = (alpha shl 24) or
                        (clampChannel(red).toInt() shl 16) or
                        (clampChannel(green).toInt() shl 8) or
                        clampChannel(blue).toInt()
                }
            }

            original.setPixels(srcChunk, 0, width, 0, startRow, width, rowCount)
            startRow = endRow
        }
    }

    companion object {
        const val DEFAULT_CHUNK_ROWS = 64
    }
}

private fun mixChannel(source: Float, target: Float, mix: Float): Float {
    return source + (target - source) * mix.coerceIn(0f, 1f)
}

private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
    if (edge0 == edge1) {
        return if (value >= edge1) 1f else 0f
    }
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun clampChannel(value: Float): Float = value.coerceIn(0f, 255f)
