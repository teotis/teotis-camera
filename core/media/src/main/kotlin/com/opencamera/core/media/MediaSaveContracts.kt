package com.opencamera.core.media

import java.io.File

enum class MediaTransactionStatus {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED
}

data class MediaSaveTransactionResult(
    val primaryOutput: MediaOutputHandle,
    val artifacts: Map<MediaArtifactRole, List<MediaOutputHandle>>,
    val status: MediaTransactionStatus,
    val cleanupNotes: List<String>
)

fun ShotResult.withSaveIoTiming(saveIoStartMs: Long, saveIoEndMs: Long): ShotResult {
    val deltaMs = saveIoEndMs - saveIoStartMs
    return addPipelineNotes("timing:save-io=${deltaMs}ms")
}

fun ShotResult.toTransactionResult(
    additionalArtifacts: Map<MediaArtifactRole, List<MediaOutputHandle>> = emptyMap()
): MediaSaveTransactionResult {
    val primaryRole = when (mediaType) {
        MediaType.PHOTO -> MediaArtifactRole.PRIMARY_STILL
        MediaType.VIDEO -> MediaArtifactRole.PRIMARY_VIDEO
    }
    val primaryHandle = outputHandle
    val artifacts = mutableMapOf<MediaArtifactRole, List<MediaOutputHandle>>()
    artifacts[primaryRole] = listOf(primaryHandle)

    livePhotoBundle?.let { bundle ->
        artifacts[MediaArtifactRole.MOTION_SEGMENT] = listOf(bundle.motionHandle)
        artifacts[MediaArtifactRole.LIVE_SIDECAR] = listOf(bundle.sidecarHandle)
        artifacts[MediaArtifactRole.THUMBNAIL] = listOf(bundle.thumbnailHandle)
    }

    additionalArtifacts.forEach { (role, handles) ->
        artifacts.merge(role, handles) { existing, new -> existing + new }
    }

    val cleanupNotes = intermediateOutputPaths.map { path ->
        val file = File(path)
        if (file.exists()) {
            "cleanup:pending:$path"
        } else {
            "cleanup:already-gone:$path"
        }
    }

    val transactionStatus = if (hasPostProcessFailures()) {
        MediaTransactionStatus.PARTIAL_SUCCESS
    } else {
        MediaTransactionStatus.SUCCESS
    }

    return MediaSaveTransactionResult(
        primaryOutput = primaryHandle,
        artifacts = artifacts,
        status = transactionStatus,
        cleanupNotes = cleanupNotes
    )
}
