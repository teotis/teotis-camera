package com.opencamera.app

import com.opencamera.core.session.SavedMediaType

internal sealed interface ThumbnailRenderCommand {
    data object NoOp : ThumbnailRenderCommand
    data object Clear : ThumbnailRenderCommand
    data class Load(
        val uri: String,
        val sourceIdentity: String = uri
    ) : ThumbnailRenderCommand
}

internal fun nextThumbnailRenderCommand(
    previousRequestedUri: String?,
    nextRequestedUri: String?,
    previousSourceIdentity: String? = previousRequestedUri,
    nextSourceIdentity: String? = nextRequestedUri
): ThumbnailRenderCommand {
    return when {
        previousSourceIdentity == nextSourceIdentity && previousRequestedUri == nextRequestedUri -> ThumbnailRenderCommand.NoOp
        previousSourceIdentity == nextSourceIdentity && previousRequestedUri != nextRequestedUri -> {
            if (nextRequestedUri == null) ThumbnailRenderCommand.Clear
            else ThumbnailRenderCommand.Load(nextRequestedUri, nextSourceIdentity!!)
        }
        nextRequestedUri == null -> ThumbnailRenderCommand.Clear
        else -> ThumbnailRenderCommand.Load(nextRequestedUri, nextSourceIdentity ?: nextRequestedUri)
    }
}

internal fun sourceIdentityFor(sourceUri: String?, mediaType: SavedMediaType?): String? {
    if (sourceUri == null) return null
    return if (mediaType == SavedMediaType.VIDEO) "video:$sourceUri" else sourceUri
}
