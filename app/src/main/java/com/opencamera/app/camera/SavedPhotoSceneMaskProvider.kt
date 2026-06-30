package com.opencamera.app.camera

import android.graphics.Bitmap
import com.opencamera.core.media.ContentUnderstandingSnapshot
import com.opencamera.core.media.SceneMaskDescriptor
import com.opencamera.core.media.SceneMaskPayload
import com.opencamera.core.media.SceneMaskQuality
import com.opencamera.core.media.SceneMaskRole
import com.opencamera.core.media.SceneMaskTransform

internal interface SavedPhotoSceneMaskProvider {
    suspend fun createSubjectMask(
        bitmap: Bitmap,
        request: SavedPhotoSceneMaskRequest
    ): SceneMaskResult
}

internal data class SavedPhotoSceneMaskRequest(
    val shotId: String,
    val outputHandleTag: String
)

internal sealed interface SceneMaskResult {
    data class Available(val mask: SavedPhotoMaskPixels) : SceneMaskResult
    data class Unavailable(val reason: String) : SceneMaskResult
    data class Failed(val reason: String) : SceneMaskResult
}

internal data class SavedPhotoMaskPixels(
    val maskPixels: IntArray,
    val maskWidth: Int,
    val maskHeight: Int,
    val confidence: Float
) : SceneMaskPayload {

    override val descriptor: SceneMaskDescriptor
        get() = toDescriptor("saved-photo", maskWidth, maskHeight)

    override fun alphaAt(maskX: Int, maskY: Int): Float = sampleAlpha(maskX, maskY)

    fun toDescriptor(
        maskId: String,
        sourceWidth: Int,
        sourceHeight: Int
    ): SceneMaskDescriptor = SceneMaskDescriptor(
        maskId = maskId,
        role = SceneMaskRole.PERSON_SUBJECT,
        quality = SceneMaskQuality.SAVED_PHOTO,
        backendId = "mlkit-selfie",
        confidence = confidence,
        transform = SceneMaskTransform(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            maskWidth = maskWidth,
            maskHeight = maskHeight,
            rotationDegrees = 0
        )
    )

    fun sampleAlpha(x: Int, y: Int): Float {
        if (x < 0 || x >= maskWidth || y < 0 || y >= maskHeight) return 0f
        val pixel = maskPixels[y * maskWidth + x]
        return (pixel ushr 24 and 0xFF) / 255f
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SavedPhotoMaskPixels) return false
        return maskWidth == other.maskWidth &&
            maskHeight == other.maskHeight &&
            confidence == other.confidence &&
            maskPixels.contentEquals(other.maskPixels)
    }

    override fun hashCode(): Int {
        var result = maskWidth
        result = 31 * result + maskHeight
        result = 31 * result + confidence.hashCode()
        result = 31 * result + maskPixels.contentHashCode()
        return result
    }
}

internal fun SavedPhotoMaskPixels.toContentUnderstandingSnapshot(
    maskId: String,
    sourceWidth: Int,
    sourceHeight: Int,
    timestampMillis: Long
): ContentUnderstandingSnapshot = ContentUnderstandingSnapshot.fromSceneMask(
    descriptor = toDescriptor(
        maskId = maskId,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight
    ),
    timestampMillis = timestampMillis
)

internal class SceneMaskCoordinateMapper(
    private val maskWidth: Int,
    private val maskHeight: Int,
    private val targetWidth: Int,
    private val targetHeight: Int
) {
    private val scaleX = maskWidth.toFloat() / targetWidth
    private val scaleY = maskHeight.toFloat() / targetHeight

    fun maskX(targetX: Int): Int = (targetX * scaleX).toInt().coerceIn(0, maskWidth - 1)
    fun maskY(targetY: Int): Int = (targetY * scaleY).toInt().coerceIn(0, maskHeight - 1)
}

internal class NoOpSavedPhotoSceneMaskProvider : SavedPhotoSceneMaskProvider {
    override suspend fun createSubjectMask(
        bitmap: Bitmap,
        request: SavedPhotoSceneMaskRequest
    ): SceneMaskResult {
        return SceneMaskResult.Unavailable("no-op-provider")
    }
}
