package com.opencamera.app.camera.live

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.opencamera.core.media.MotionPhotoContainerSpec
import com.opencamera.core.media.MotionPhotoJpegContainer

data class MediaStoreVideoRecord(
    val uri: String,
    val displayName: String,
    val relativePath: String,
    val mimeType: String,
    val size: String,
    val duration: String,
    val isPending: String
)

open class LivePhotoMediaStoreWriter(private val context: Context) {

    fun readMediaStoreBytes(uri: Uri): Result<ByteArray> = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Failed to open input stream for $uri")
    }

    fun overwriteMotionPhotoJpeg(savedUri: Uri, motionPhotoBytes: ByteArray): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(savedUri, "wt")?.use { out ->
            out.write(motionPhotoBytes)
        } ?: throw IllegalStateException("Failed to open output stream for $savedUri")
        context.contentResolver.notifyChange(savedUri, null)
    }

    open fun insertMotionMp4Sidecar(
        jpegRelativePath: String,
        mp4DisplayNamePrefix: String,
        mp4Bytes: ByteArray
    ): Result<Uri> = runCatching {
        val relativeDir = jpegRelativePath.substringBeforeLast('/')
        val mp4DisplayName = "$mp4DisplayNamePrefix.live.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, mp4DisplayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, relativeDir)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val mp4Uri = context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: throw IllegalStateException("Failed to insert MP4 sidecar into MediaStore for $mp4DisplayName")

        context.contentResolver.openOutputStream(mp4Uri)?.use { out ->
            out.write(mp4Bytes)
        } ?: throw IllegalStateException("Failed to open output stream for MP4 sidecar $mp4Uri")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val updateValues = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(mp4Uri, updateValues, null, null)
        }

        mp4Uri
    }

    open fun verifyMotionMp4Sidecar(uri: Uri): Result<MediaStoreVideoRecord> = runCatching {
        val projection = buildList {
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.SIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
                add(MediaStore.Video.Media.DURATION)
                add(MediaStore.Video.Media.IS_PENDING)
            }
        }

        val cursor = context.contentResolver.query(
            uri,
            projection.toTypedArray(),
            null,
            null,
            null
        ) ?: throw IllegalStateException("Failed to query MediaStore for MP4 sidecar $uri")

        cursor.use { c ->
            if (!c.moveToFirst()) {
                throw IllegalStateException("MediaStore query returned no rows for MP4 sidecar $uri")
            }

            fun readColumn(name: String): String {
                val idx = c.getColumnIndex(name)
                return if (idx < 0) "n/a" else c.getString(idx) ?: "n/a"
            }

            MediaStoreVideoRecord(
                uri = uri.toString(),
                displayName = readColumn(MediaStore.Video.Media.DISPLAY_NAME),
                relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    readColumn(MediaStore.Video.Media.RELATIVE_PATH) else "n/a",
                mimeType = readColumn(MediaStore.Video.Media.MIME_TYPE),
                size = readColumn(MediaStore.Video.Media.SIZE),
                duration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    readColumn(MediaStore.Video.Media.DURATION) else "n/a",
                isPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    readColumn(MediaStore.Video.Media.IS_PENDING) else "n/a"
            )
        }
    }

    fun createMotionPhotoBytes(
        savedUri: Uri,
        motionPath: String,
        spec: MotionPhotoContainerSpec
    ): Result<ByteArray> = runCatching {
        val stillBytes = readMediaStoreBytes(savedUri).getOrThrow()
        val motionFile = java.io.File(motionPath)
        val motionBytes = motionFile.readBytes()

        MotionPhotoJpegContainer.write(
            jpegBytes = stillBytes,
            motionBytes = motionBytes,
            spec = spec.copy(motionLengthBytes = motionBytes.size.toLong())
        )
    }
}
