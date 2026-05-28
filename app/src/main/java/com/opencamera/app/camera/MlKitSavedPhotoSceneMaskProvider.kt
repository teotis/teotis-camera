package com.opencamera.app.camera

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class MlKitSavedPhotoSceneMaskProvider : SavedPhotoSceneMaskProvider {
    private val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
    )

    override suspend fun createSubjectMask(
        bitmap: Bitmap,
        request: SavedPhotoSceneMaskRequest
    ): SceneMaskResult {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val mask = suspendCoroutine<SegmentationMask?> { cont ->
                segmenter.process(inputImage)
                    .addOnSuccessListener { result -> cont.resume(result as SegmentationMask?) }
                    .addOnFailureListener { cont.resume(null) }
            } ?: return SceneMaskResult.Failed("segmentation-failed")

            val buffer = mask.buffer
            val maskWidth = mask.width
            val maskHeight = mask.height
            val confidenceValues = FloatArray(maskWidth * maskHeight)
            buffer.rewind()
            for (i in confidenceValues.indices) {
                confidenceValues[i] = buffer.float
            }

            val pixels = IntArray(maskWidth * maskHeight)
            var subjectPixelCount = 0
            for (i in pixels.indices) {
                val confidence = confidenceValues[i].coerceIn(0f, 1f)
                val alpha = (confidence * 255f).toInt()
                pixels[i] = (alpha shl 24) or 0x00FFFFFF
                if (confidence > 0.5f) subjectPixelCount++
            }

            val subjectRatio = subjectPixelCount.toFloat() / pixels.size
            if (subjectRatio < 0.02f) {
                return SceneMaskResult.Unavailable("no-person-detected")
            }

            val averageConfidence = confidenceValues.average().toFloat()
            SceneMaskResult.Available(
                SavedPhotoMaskPixels(
                    maskPixels = pixels,
                    maskWidth = maskWidth,
                    maskHeight = maskHeight,
                    confidence = averageConfidence
                )
            )
        } catch (_: Exception) {
            SceneMaskResult.Failed("segmentation-exception")
        }
    }

    fun close() {
        segmenter.close()
    }
}
