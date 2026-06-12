package com.opencamera.core.media

import java.util.concurrent.atomic.AtomicLong

class ShotExecutor(
    private val idGenerator: () -> String = {
        val sequence = sequenceGenerator.incrementAndGet()
        "shot-$sequence"
    }
) {
    fun plan(
        strategy: CaptureStrategy,
        activeShot: ShotRequest? = null
    ): ShotPlan {
        check(activeShot == null) {
            "Shot already in progress: ${activeShot?.shotId}"
        }

        val (shotKind, mediaType) = when (strategy) {
            is CaptureStrategy.SingleFrame -> ShotKind.STILL_CAPTURE to MediaType.PHOTO
            is CaptureStrategy.MultiFrame -> ShotKind.MULTI_FRAME_CAPTURE to MediaType.PHOTO
            is CaptureStrategy.LivePhoto -> ShotKind.LIVE_PHOTO to MediaType.PHOTO
            is CaptureStrategy.VideoRecording -> ShotKind.VIDEO_RECORDING to MediaType.VIDEO
        }
        val request = ShotRequest(
            shotId = idGenerator(),
            shotKind = shotKind,
            mediaType = mediaType,
            saveRequest = strategy.saveRequest,
            thumbnailPolicy = strategy.thumbnailPolicy,
            postProcessSpec = strategy.postProcessSpec,
            captureProfile = strategy.captureProfile,
            livePhotoSpec = (strategy as? CaptureStrategy.LivePhoto)?.livePhotoSpec
        )
        return ShotPlan(
            request = request,
            saveTask = MediaSaveTask(
                shotId = request.shotId,
                mediaType = request.mediaType,
                saveRequest = request.saveRequest,
                thumbnailPolicy = request.thumbnailPolicy,
                postProcessSpec = request.postProcessSpec,
                captureProfile = request.captureProfile,
                livePhotoSpec = request.livePhotoSpec
            ),
            graph = ShotGraphBuilder.build(request)
        )
    }

    fun requireStoppableShot(activeShot: ShotRequest?): ShotRequest {
        requireNotNull(activeShot) { "No active shot to stop" }
        check(activeShot.shotKind == ShotKind.VIDEO_RECORDING) {
            "Only video recording shots can be stopped"
        }
        return activeShot
    }

    fun resultFor(
        saveTask: MediaSaveTask,
        outputPath: String,
        outputHandle: MediaOutputHandle = MediaOutputHandle(displayPath = outputPath),
        livePhotoBundle: LivePhotoBundle? = null,
        frameBundle: FrameBundle? = null,
        intermediateOutputPaths: List<String> = emptyList()
    ): ShotResult {
        val metadata = saveTask.saveRequest.metadata.mergeWith(saveTask.postProcessSpec)
        val thumbnailSource = when (saveTask.thumbnailPolicy) {
            ThumbnailPolicy.NONE -> ThumbnailSource.None
            ThumbnailPolicy.KEEP_PREVIEW_FRAME -> ThumbnailSource.None
            ThumbnailPolicy.USE_SAVED_MEDIA -> ThumbnailSource.SavedMedia(
                outputPath = outputPath,
                renderUri = outputHandle.renderUriOrNull()
            )
        }
        return ShotResult(
            shotId = saveTask.shotId,
            mediaType = saveTask.mediaType,
            outputPath = outputPath,
            outputHandle = outputHandle,
            saveRequest = saveTask.saveRequest,
            thumbnailSource = thumbnailSource,
            captureProfile = saveTask.captureProfile,
            metadata = metadata,
            livePhotoBundle = livePhotoBundle,
            frameBundle = frameBundle,
            intermediateOutputPaths = intermediateOutputPaths
        )
    }

    private fun MediaMetadata.mergeWith(postProcessSpec: PostProcessSpec): MediaMetadata {
        return copy(
            exifOverrides = exifOverrides + postProcessSpec.exifOverrides,
            watermarkText = postProcessSpec.watermarkText ?: watermarkText,
            algorithmProfile = postProcessSpec.algorithmProfile ?: algorithmProfile
        )
    }

    companion object {
        private val sequenceGenerator = AtomicLong(0)
    }
}
