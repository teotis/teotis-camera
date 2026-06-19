package com.opencamera.app.camera

import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotResult
import kotlinx.coroutines.CancellationException

internal enum class PhotoJpegInput {
    EDITABLE,
    NOT_PHOTO,
    UNSUPPORTED_MIME
}

internal fun ShotResult.photoJpegInput(): PhotoJpegInput {
    return when {
        mediaType != MediaType.PHOTO -> PhotoJpegInput.NOT_PHOTO
        !saveRequest.mimeType.equals("image/jpeg", ignoreCase = true) ->
            PhotoJpegInput.UNSUPPORTED_MIME
        else -> PhotoJpegInput.EDITABLE
    }
}

internal fun Throwable.rethrowIfCancellationOrFatal() {
    if (this is CancellationException) throw this
    if (this is Error && this !is OutOfMemoryError) throw this
}
