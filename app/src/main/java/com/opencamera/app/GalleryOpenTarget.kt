package com.opencamera.app

import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.session.SavedMediaType

internal enum class GalleryOpenUriKind {
    CONTENT_URI,
    ABSOLUTE_FILE
}

internal data class GalleryOpenTarget(
    val uri: String,
    val kind: GalleryOpenUriKind,
    val mimeType: String
)

internal fun galleryOpenTargetFor(
    source: ThumbnailSource?,
    savedMediaType: SavedMediaType?
): GalleryOpenTarget? {
    val saved = source as? ThumbnailSource.SavedMedia ?: return null
    val mimeType = when (savedMediaType) {
        SavedMediaType.VIDEO -> "video/*"
        SavedMediaType.PHOTO,
        null -> "image/*"
    }

    val renderUri = saved.renderUri?.takeIf { it.isNotBlank() }
    if (renderUri != null && renderUri.startsWith("content://")) {
        return GalleryOpenTarget(
            uri = renderUri,
            kind = GalleryOpenUriKind.CONTENT_URI,
            mimeType = mimeType
        )
    }

    val fileUriPath = renderUri
        ?.takeIf { it.startsWith("file://") }
        ?.removePrefix("file://")
    val absolutePath = fileUriPath
        ?: saved.outputPath.takeIf { it.startsWith("/") }

    return absolutePath?.let { path ->
        GalleryOpenTarget(
            uri = path,
            kind = GalleryOpenUriKind.ABSOLUTE_FILE,
            mimeType = mimeType
        )
    }
}
