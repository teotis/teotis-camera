package com.opencamera.app

internal sealed interface ThumbnailRenderCommand {
    data object NoOp : ThumbnailRenderCommand
    data object Clear : ThumbnailRenderCommand
    data class Load(val uri: String) : ThumbnailRenderCommand
}

internal fun nextThumbnailRenderCommand(
    previousRequestedUri: String?,
    nextRequestedUri: String?
): ThumbnailRenderCommand {
    return when {
        previousRequestedUri == nextRequestedUri -> ThumbnailRenderCommand.NoOp
        nextRequestedUri == null -> ThumbnailRenderCommand.Clear
        else -> ThumbnailRenderCommand.Load(nextRequestedUri)
    }
}
