package com.opencamera.app.camera

import android.graphics.Bitmap

internal object SceneMaskTestUtils {
    fun createSyntheticMask(
        width: Int,
        height: Int,
        subjectRegion: (x: Int, y: Int) -> Float
    ): SavedPhotoMaskPixels {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = (subjectRegion(x, y).coerceIn(0f, 1f) * 255f).toInt()
                pixels[y * width + x] = (alpha shl 24) or 0x00FFFFFF
            }
        }
        return SavedPhotoMaskPixels(
            maskPixels = pixels,
            maskWidth = width,
            maskHeight = height,
            confidence = 0.85f
        )
    }

    fun createCenterSubjectMask(
        maskWidth: Int,
        maskHeight: Int,
        subjectFractionX: Float = 0.4f,
        subjectFractionY: Float = 0.5f,
        edgeSoftness: Float = 0.08f
    ): SavedPhotoMaskPixels {
        val centerX = maskWidth / 2f
        val centerY = maskHeight * 0.4f
        val radiusX = maskWidth * subjectFractionX
        val radiusY = maskHeight * subjectFractionY
        return createSyntheticMask(maskWidth, maskHeight) { x, y ->
            val dx = (x - centerX) / radiusX
            val dy = (y - centerY) / radiusY
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            smoothstep(1f, 1f + edgeSoftness, dist).let { 1f - it }
        }
    }

    fun createUniformMask(
        width: Int,
        height: Int,
        alpha: Float
    ): SavedPhotoMaskPixels {
        return createSyntheticMask(width, height) { _, _ -> alpha }
    }

    fun createLeftRightSplitMask(
        width: Int,
        height: Int,
        subjectFraction: Float = 0.5f
    ): SavedPhotoMaskPixels {
        val splitX = (width * subjectFraction).toInt()
        return createSyntheticMask(width, height) { x, _ ->
            if (x < splitX) 1f else 0f
        }
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        if (edge0 == edge1) return if (value >= edge1) 1f else 0f
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

internal class FakeSavedPhotoSceneMaskProvider(
    private val result: SceneMaskResult
) : SavedPhotoSceneMaskProvider {
    val invocations = mutableListOf<SavedPhotoSceneMaskRequest>()

    override suspend fun createSubjectMask(
        bitmap: Bitmap,
        request: SavedPhotoSceneMaskRequest
    ): SceneMaskResult {
        invocations += request
        return result
    }
}
