package com.opencamera.app.camera

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.opencamera.core.media.ProcessorTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

internal sealed interface BitmapIoExifResult {
    data class Success(val exifWarning: String?) : BitmapIoExifResult
    data class Skipped(val reason: String) : BitmapIoExifResult
    data class Failed(val reason: String) : BitmapIoExifResult
}

internal class BitmapIoExifProcessor(
    private val contentResolver: ContentResolver,
    private val jpegQuality: Int
) {
    suspend fun process(
        target: ProcessorTarget,
        transform: (Bitmap) -> Bitmap
    ): BitmapIoExifResult = withContext(Dispatchers.IO) {
        val sourceBytes = ProcessorIOUtils.readSourceBytes(target, contentResolver)
            ?: return@withContext BitmapIoExifResult.Skipped("input-unavailable")
        if (sourceBytes.isEmpty()) {
            return@withContext BitmapIoExifResult.Skipped("empty-source")
        }

        val preservedExif = readPreservedExif(sourceBytes)
        val decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
            ?: return@withContext BitmapIoExifResult.Failed("decode-failed")

        try {
            val output = transform(decoded)
            val encodedBytes = ByteArrayOutputStream().use { stream ->
                check(output.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)) {
                    "JPEG compression failed"
                }
                stream.toByteArray()
            }
            if (output !== decoded) output.recycle()
            if (!contentResolver.writeEncodedBytes(target, encodedBytes)) {
                return@withContext BitmapIoExifResult.Failed("output-unavailable")
            }
            val exifWarning = contentResolver.restorePreservedExif(target, preservedExif)
            BitmapIoExifResult.Success(exifWarning)
        } finally {
            decoded.recycle()
        }
    }
}
