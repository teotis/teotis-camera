package com.opencamera.core.media

import java.io.File

data class CaptureProfile(
    val frameCount: Int = 1,
    val longExposureMillis: Long? = null,
    val requiresTripod: Boolean = false,
    val flashMode: FlashMode = FlashMode.OFF,
    val torchEnabled: Boolean = false,
    val manualCaptureParams: com.opencamera.core.settings.ManualCaptureParams? = null,
    val stillCaptureQuality: StillCaptureQualityPreference? = null,
    val stillCaptureResolutionPreset: StillCaptureResolutionPreset? = null
)

sealed interface CaptureStrategy {
    val saveRequest: SaveRequest
    val thumbnailPolicy: ThumbnailPolicy
    val postProcessSpec: PostProcessSpec
    val captureProfile: CaptureProfile

    data class SingleFrame(
        override val saveRequest: SaveRequest = SaveRequest.photoLibrary(),
        override val thumbnailPolicy: ThumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
        override val postProcessSpec: PostProcessSpec = PostProcessSpec(),
        override val captureProfile: CaptureProfile = CaptureProfile()
    ) : CaptureStrategy

    data class MultiFrame(
        override val saveRequest: SaveRequest = SaveRequest.photoLibrary(),
        override val thumbnailPolicy: ThumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
        override val postProcessSpec: PostProcessSpec = PostProcessSpec(),
        override val captureProfile: CaptureProfile
    ) : CaptureStrategy

    data class LivePhoto(
        override val saveRequest: SaveRequest = SaveRequest.photoLibrary(),
        override val thumbnailPolicy: ThumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
        override val postProcessSpec: PostProcessSpec = PostProcessSpec(),
        override val captureProfile: CaptureProfile = CaptureProfile(),
        val livePhotoSpec: LivePhotoCaptureSpec = LivePhotoCaptureSpec()
    ) : CaptureStrategy

    data class VideoRecording(
        override val saveRequest: SaveRequest = SaveRequest.videoLibrary(),
        override val thumbnailPolicy: ThumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
        override val postProcessSpec: PostProcessSpec = PostProcessSpec(),
        override val captureProfile: CaptureProfile = CaptureProfile()
    ) : CaptureStrategy
}

data class ShotRequest(
    val shotId: String,
    val shotKind: ShotKind,
    val mediaType: MediaType,
    val saveRequest: SaveRequest,
    val thumbnailPolicy: ThumbnailPolicy,
    val postProcessSpec: PostProcessSpec,
    val captureProfile: CaptureProfile,
    val livePhotoSpec: LivePhotoCaptureSpec? = null
)

data class MediaSaveTask(
    val shotId: String,
    val mediaType: MediaType,
    val saveRequest: SaveRequest,
    val thumbnailPolicy: ThumbnailPolicy,
    val postProcessSpec: PostProcessSpec,
    val captureProfile: CaptureProfile,
    val livePhotoSpec: LivePhotoCaptureSpec? = null
)

data class ShotPlan(
    val request: ShotRequest,
    val saveTask: MediaSaveTask,
    val graph: ShotGraph
)

sealed interface ThumbnailSource {
    data object None : ThumbnailSource
    data object Pending : ThumbnailSource
    data class PreviewSnapshot(val outputPath: String) : ThumbnailSource
    data class SavedMedia(
        val outputPath: String,
        val renderUri: String? = null
    ) : ThumbnailSource
}

data class CaptureFeedbackPreview(
    val shotId: String,
    val outputPath: String
)

data class MediaOutputHandle(
    val displayPath: String,
    val filePath: String? = null,
    val contentUri: String? = null
) {
    fun renderUriOrNull(): String? {
        contentUri?.let { return it }
        filePath?.let { return File(it).toURI().toString() }
        return displayPath.takeIf { File(it).isAbsolute }?.let { File(it).toURI().toString() }
    }
}

data class ShotTiming(
    val requestedAtElapsedMillis: Long? = null,
    val deviceCaptureStartedAtElapsedMillis: Long? = null,
    val deviceCaptureCompletedAtElapsedMillis: Long? = null,
    val postProcessCompletedAtElapsedMillis: Long? = null
)

data class ShotResult(
    val shotId: String,
    val mediaType: MediaType,
    val outputPath: String,
    val outputHandle: MediaOutputHandle = MediaOutputHandle(displayPath = outputPath),
    val saveRequest: SaveRequest,
    val thumbnailSource: ThumbnailSource,
    val captureProfile: CaptureProfile = CaptureProfile(),
    val metadata: MediaMetadata,
    val livePhotoBundle: LivePhotoBundle? = null,
    val intermediateOutputPaths: List<String> = emptyList(),
    val pipelineNotes: List<String> = emptyList(),
    val timing: ShotTiming = ShotTiming()
)

fun ThumbnailSource.outputPathOrNull(): String? {
    return when (this) {
        ThumbnailSource.None -> null
        ThumbnailSource.Pending -> null
        is ThumbnailSource.PreviewSnapshot -> outputPath
        is ThumbnailSource.SavedMedia -> outputPath
    }
}

fun ThumbnailSource.renderUriOrNull(): String? {
    return when (this) {
        ThumbnailSource.None -> null
        ThumbnailSource.Pending -> null
        is ThumbnailSource.PreviewSnapshot -> null
        is ThumbnailSource.SavedMedia ->
            renderUri ?: outputPath.takeIf { File(it).isAbsolute }?.let { File(it).toURI().toString() }
    }
}

fun ShotResult.hasPostProcessFailures(): Boolean =
    pipelineNotes.any { it.contains(":failed:") }

fun ShotResult.postProcessFailureSummary(): String? {
    val failures = pipelineNotes.filter { it.contains(":failed:") }
    return failures.takeIf { it.isNotEmpty() }?.joinToString("; ")
}
