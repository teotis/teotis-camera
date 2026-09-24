package com.opencamera.app.camera.live

import android.content.Context
import android.net.Uri
import android.util.Log
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MotionPhotoContainerParser
import com.opencamera.core.settings.LiveSaveFormat
import java.io.File

private const val TAG = "MotionPhotoPlayback"

enum class MotionPlaybackKind {
    /** Motion MP4 embedded at the tail of the motion-photo JPEG. */
    EMBEDDED_MP4,

    /** Standalone MP4 sidecar in MediaStore. */
    SIDECAR_MP4
}

/**
 * A playable motion segment. For [MotionPlaybackKind.EMBEDDED_MP4] the bytes live in
 * [location] at [offsetBytes] with length [lengthBytes]; for sidecars [location] is the
 * full MP4 source.
 */
data class MotionPlaybackSegment(
    val kind: MotionPlaybackKind,
    val location: String,
    val offsetBytes: Long? = null,
    val lengthBytes: Long? = null
)

/**
 * Resolves the motion segment of the latest live photo for in-app playback.
 *
 * This is the minimum in-app truth proof for the Motion Photo container: playing the
 * exact appended MP4 bytes (or the exact sidecar MP4) demonstrates the motion data was
 * written and is decodable, without claiming anything about system-gallery recognition.
 */
object MotionPhotoPlaybackResolver {

    private const val HEAD_BYTES = 64 * 1024

    fun resolve(
        bundle: LivePhotoBundle,
        saveFormat: LiveSaveFormat,
        pipelineNotes: List<String>,
        readFileBytes: (String, Int) -> Result<ByteArray>,
        readContentHead: (Context, Uri, Int) -> Result<ByteArray>,
        fileLength: (String) -> Result<Long>
    ): Result<MotionPlaybackSegment> = runCatching {
        when (saveFormat) {
            LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG -> {
                val materialized = pipelineNotes.any { it == "live-motion:status=materialized" } ||
                    pipelineNotes.any { it.startsWith("gallery-recognition=container-validated") }
                if (!materialized) {
                    throw IllegalStateException("motion photo not materialized")
                }
                resolveEmbedded(
                    location = bundle.stillPath,
                    readFileBytes = readFileBytes,
                    fileLength = fileLength
                ).getOrThrow()
            }

            LiveSaveFormat.MOTION_MP4_SIDECAR -> {
                val contentUri = bundle.motionHandle.contentUri
                if (contentUri != null) {
                    MotionPlaybackSegment(kind = MotionPlaybackKind.SIDECAR_MP4, location = contentUri)
                } else if (File(bundle.motionPath).isAbsolute && File(bundle.motionPath).exists()) {
                    MotionPlaybackSegment(kind = MotionPlaybackKind.SIDECAR_MP4, location = bundle.motionPath)
                } else {
                    throw IllegalStateException("sidecar MP4 not available")
                }
            }

            LiveSaveFormat.STILL_JPEG_ONLY -> throw IllegalStateException("live photo disabled (still only)")
        }
    }.onFailure { e -> Log.w(TAG, "resolve motion segment failed", e) }

    fun resolveEmbedded(
        location: String,
        readFileBytes: (String, Int) -> Result<ByteArray> = ::defaultReadHead,
        fileLength: (String) -> Result<Long> = ::defaultFileLength
    ): Result<MotionPlaybackSegment> = runCatching {
        val head = readFileBytes(location, HEAD_BYTES).getOrThrow()
        val xmp = MotionPhotoContainerParser.extractXmp(head)
        val offset = xmp?.microVideoOffset ?: xmp?.containerPrimaryLength
            ?: throw IllegalStateException("no motion offset in XMP")
        if (offset <= 0) {
            throw IllegalStateException("invalid motion offset $offset")
        }
        val total = fileLength(location).getOrThrow()
        val length = total - offset
        if (length <= 0) {
            throw IllegalStateException("invalid motion length $length")
        }
        MotionPlaybackSegment(
            kind = MotionPlaybackKind.EMBEDDED_MP4,
            location = location,
            offsetBytes = offset,
            lengthBytes = length
        )
    }.onFailure { e -> Log.w(TAG, "resolve embedded motion segment failed location=$location", e) }

    fun resolveEmbeddedFromContent(
        context: Context,
        contentUri: Uri,
        readContentHead: (Context, Uri, Int) -> Result<ByteArray> = ::defaultContentHead,
        contentLength: (Context, Uri) -> Result<Long> = ::defaultContentLength
    ): Result<MotionPlaybackSegment> = runCatching {
        val head = readContentHead(context, contentUri, HEAD_BYTES).getOrThrow()
        val xmp = MotionPhotoContainerParser.extractXmp(head)
        val offset = xmp?.microVideoOffset ?: xmp?.containerPrimaryLength
            ?: throw IllegalStateException("no motion offset in XMP")
        if (offset <= 0) {
            throw IllegalStateException("invalid motion offset $offset")
        }
        val total = contentLength(context, contentUri).getOrThrow()
        val length = total - offset
        if (length <= 0) {
            throw IllegalStateException("invalid motion length $length")
        }
        MotionPlaybackSegment(
            kind = MotionPlaybackKind.EMBEDDED_MP4,
            location = contentUri.toString(),
            offsetBytes = offset,
            lengthBytes = length
        )
    }.onFailure { e -> Log.w(TAG, "resolve embedded motion segment failed uri=$contentUri", e) }

    private fun defaultReadHead(path: String, maxBytes: Int): Result<ByteArray> = runCatching {
        File(path).inputStream().use { input -> readBoundedHead(input, maxBytes) }
    }

    private fun defaultFileLength(path: String): Result<Long> = runCatching { File(path).length() }

    private fun defaultContentHead(context: Context, uri: Uri, maxBytes: Int): Result<ByteArray> =
        runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("cannot open $uri")
            input.use { stream -> readBoundedHead(stream, maxBytes) }
        }

    private fun defaultContentLength(context: Context, uri: Uri): Result<Long> = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            ?: throw IllegalStateException("cannot open asset fd for $uri")
    }
}

/**
 * Reads at most [maxBytes] bytes from [input] without materializing the whole
 * stream. Prevents a large (or adversarial) source from being fully buffered
 * just to inspect a fixed-size header.
 */
internal fun readBoundedHead(input: java.io.InputStream, maxBytes: Int): ByteArray {
    if (maxBytes <= 0) return ByteArray(0)
    val out = java.io.ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
    val buffer = ByteArray(8 * 1024)
    var remaining = maxBytes
    while (remaining > 0) {
        val read = input.read(buffer, 0, minOf(buffer.size, remaining))
        if (read < 0) break
        out.write(buffer, 0, read)
        remaining -= read
    }
    return out.toByteArray()
}
