package com.opencamera.app.camera

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.opencamera.core.media.ProcessorTarget
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

internal object BoundedMaskBitmapDecoder {
    private const val MAX_MASK_EDGE_PIXELS = 2_048
    private const val MAX_COMPRESSED_MASK_BYTES = 32L * 1024L * 1024L

    fun decode(
        target: ProcessorTarget,
        contentResolver: ContentResolver
    ): Bitmap? {
        return when (target) {
            is ProcessorTarget.FilePath -> decodeFile(target.path)
            is ProcessorTarget.ContentUri -> decodeContentUri(contentResolver, Uri.parse(target.value))
        }
    }

    internal fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxEdgePixels: Int = MAX_MASK_EDGE_PIXELS
    ): Int {
        if (width <= 0 || height <= 0 || maxEdgePixels <= 0) return 1
        var sampleSize = 1
        while (maxOf(width / sampleSize, height / sampleSize) > maxEdgePixels) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun decodeFile(path: String): Bitmap? {
        val file = File(path)
        if (!file.isFile || file.length() > MAX_COMPRESSED_MASK_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeFile(path, options)
    }

    private fun decodeContentUri(
        contentResolver: ContentResolver,
        uri: Uri
    ): Bitmap? {
        val bytes = contentResolver.openInputStream(uri)?.use(::readBoundedBytes) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun readBoundedBytes(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            totalBytes += read.toLong()
            if (totalBytes > MAX_COMPRESSED_MASK_BYTES) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
