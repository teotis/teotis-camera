package com.opencamera.app

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

internal object VideoFrameExtractor {

    private const val THUMBNAIL_DIR = "video-thumbnails"
    private const val MAX_DIMENSION = 512
    private const val JPEG_QUALITY = 80

    fun extract(context: Context, sourceUri: String): String? {
        val uri = Uri.parse(sourceUri)
        val cacheDir = File(context.cacheDir, THUMBNAIL_DIR)
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val cacheKey = uri.lastPathSegment?.replace(Regex("[^a-zA-Z0-9]"), "_") ?: return null
        val cachedFile = File(cacheDir, "$cacheKey.jpg")
        if (cachedFile.exists() && cachedFile.length() > 0) {
            return cachedFile.toURI().toString()
        }

        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                if (sourceUri.startsWith("content://")) {
                    retriever.setDataSource(context, uri)
                } else {
                    retriever.setDataSource(sourceUri)
                }
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return null
                val scaled = scaleDown(frame)
                FileOutputStream(cachedFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                if (scaled !== frame) scaled.recycle()
                frame.recycle()
                cachedFile.toURI().toString()
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= MAX_DIMENSION && h <= MAX_DIMENSION) return bitmap
        val scale = MAX_DIMENSION.toFloat() / maxOf(w, h)
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
