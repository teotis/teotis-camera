package com.opencamera.core.media

import com.opencamera.core.settings.ManualCaptureParams
import java.util.concurrent.atomic.AtomicLong
import java.io.File

enum class MediaType {
    PHOTO,
    VIDEO
}

enum class ShotKind {
    STILL_CAPTURE,
    MULTI_FRAME_CAPTURE,
    LIVE_PHOTO,
    VIDEO_RECORDING
}

enum class ThumbnailPolicy {
    NONE,
    KEEP_PREVIEW_FRAME,
    USE_SAVED_MEDIA
}

enum class FlashMode {
    OFF,
    AUTO,
    ON
}

enum class StillCaptureQualityPreference(
    val tagValue: String,
    val label: String
) {
    LATENCY(
        tagValue = "latency",
        label = "Fast"
    ),
    QUALITY(
        tagValue = "quality",
        label = "Max"
    );

    companion object {
        fun fromTag(value: String?): StillCaptureQualityPreference? {
            return entries.firstOrNull { it.tagValue == value }
        }
    }
}

enum class StillCaptureResolutionPreset(
    val tagValue: String,
    val label: String,
    val targetWidth: Int,
    val targetHeight: Int
) {
    LARGE_12MP(
        tagValue = "12mp",
        label = "12MP",
        targetWidth = 4000,
        targetHeight = 3000
    ),
    MEDIUM_8MP(
        tagValue = "8mp",
        label = "8MP",
        targetWidth = 3264,
        targetHeight = 2448
    ),
    SMALL_2MP(
        tagValue = "2mp",
        label = "2MP",
        targetWidth = 1600,
        targetHeight = 1200
    );

    companion object {
        fun fromTag(value: String?): StillCaptureResolutionPreset? {
            return entries.firstOrNull { it.tagValue == value }
        }
    }
}

enum class FrameRatio(
    val tagValue: String,
    val width: Int,
    val height: Int,
    val label: String
) {
    RATIO_4_3(
        tagValue = "4:3",
        width = 4,
        height = 3,
        label = "4:3"
    ),
    RATIO_16_9(
        tagValue = "16:9",
        width = 16,
        height = 9,
        label = "16:9"
    ),
    RATIO_1_1(
        tagValue = "1:1",
        width = 1,
        height = 1,
        label = "1:1"
    );

    companion object {
        fun fromTag(value: String?): FrameRatio? {
            return entries.firstOrNull { it.tagValue == value }
        }
    }
}

data class MediaMetadata(
    val exifOverrides: Map<String, String> = emptyMap(),
    val watermarkText: String? = null,
    val algorithmProfile: String? = null,
    val customTags: Map<String, String> = emptyMap()
)

data class SaveRequest(
    val relativePath: String,
    val fileNamePrefix: String,
    val fileExtension: String,
    val mimeType: String,
    val metadata: MediaMetadata = MediaMetadata()
) {
    fun buildDisplayName(stamp: String): String = "${fileNamePrefix}_$stamp.$fileExtension"

    companion object {
        fun photoLibrary(
            relativePath: String = "Pictures/OpenCamera",
            fileNamePrefix: String = "OpenCamera",
            metadata: MediaMetadata = MediaMetadata()
        ): SaveRequest {
            return SaveRequest(
                relativePath = relativePath,
                fileNamePrefix = fileNamePrefix,
                fileExtension = "jpg",
                mimeType = "image/jpeg",
                metadata = metadata
            )
        }

        fun videoLibrary(
            relativePath: String = "Movies/OpenCamera",
            fileNamePrefix: String = "OpenCamera",
            metadata: MediaMetadata = MediaMetadata()
        ): SaveRequest {
            return SaveRequest(
                relativePath = relativePath,
                fileNamePrefix = fileNamePrefix,
                fileExtension = "mp4",
                mimeType = "video/mp4",
                metadata = metadata
            )
        }
    }
}

data class PostProcessSpec(
    val watermarkText: String? = null,
    val exifOverrides: Map<String, String> = emptyMap(),
    val algorithmProfile: String? = null
)

data class LivePhotoCaptureSpec(
    val motionDurationMillis: Long = 1_500,
    val motionMimeType: String = "video/mp4",
    val sidecarMimeType: String = "application/vnd.opencamera.live+json"
)

data class LivePhotoBundle(
    val stillPath: String,
    val motionPath: String,
    val sidecarPath: String,
    val thumbnailPath: String = stillPath,
    val motionDurationMillis: Long,
    val motionMimeType: String,
    val sidecarMimeType: String,
    val motionHandle: MediaOutputHandle = MediaOutputHandle(displayPath = motionPath),
    val sidecarHandle: MediaOutputHandle = MediaOutputHandle(displayPath = sidecarPath),
    val thumbnailHandle: MediaOutputHandle = MediaOutputHandle(displayPath = thumbnailPath)
)

data class CaptureProfile(
    val frameCount: Int = 1,
    val longExposureMillis: Long? = null,
    val requiresTripod: Boolean = false,
    val flashMode: FlashMode = FlashMode.OFF,
    val torchEnabled: Boolean = false,
    val manualCaptureParams: ManualCaptureParams? = null,
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
    val saveTask: MediaSaveTask
)

sealed interface ThumbnailSource {
    data object None : ThumbnailSource
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
        is ThumbnailSource.PreviewSnapshot -> outputPath
        is ThumbnailSource.SavedMedia -> outputPath
    }
}

fun ThumbnailSource.renderUriOrNull(): String? {
    return when (this) {
        ThumbnailSource.None -> null
        is ThumbnailSource.PreviewSnapshot ->
            outputPath.takeIf { File(it).isAbsolute }?.let { File(it).toURI().toString() }
        is ThumbnailSource.SavedMedia ->
            renderUri ?: outputPath.takeIf { File(it).isAbsolute }?.let { File(it).toURI().toString() }
    }
}

interface MediaPostProcessor {
    suspend fun process(result: ShotResult): ShotResult
}

class CompositeMediaPostProcessor(
    private val processors: List<MediaPostProcessor>
) : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        var current = result
        processors.forEach { processor ->
            current = processor.process(current)
        }
        return current
    }
}

class PipelineMetadataPostProcessor : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        val deviceNotes = buildList {
            if (result.captureProfile.frameCount > 1) {
                add("frames:${result.captureProfile.frameCount}")
            }
            result.captureProfile.longExposureMillis?.let { add("exposure:${it}ms") }
            if (result.captureProfile.requiresTripod) {
                add("stability:tripod")
            }
            if (result.captureProfile.flashMode != FlashMode.OFF) {
                add("flash:${result.captureProfile.flashMode.name.lowercase()}")
            }
            if (result.captureProfile.torchEnabled) {
                add("torch:on")
            }
            result.captureProfile.stillCaptureQuality?.let { add("stillQuality:${it.tagValue}") }
            result.captureProfile.stillCaptureResolutionPreset
                ?.let { add("stillResolution:${it.tagValue}") }
        }

        val algorithmNotes = buildList {
            result.metadata.algorithmProfile?.let { add("algorithm:$it") }
            result.metadata.watermarkText?.let { add("watermark:$it") }
            result.metadata.customTags["livePhotoDefault"]?.let { add("live-default:$it") }
            result.metadata.customTags["liveWatermarkBehavior"]
                ?.let { behavior -> add("live-watermark:$behavior") }
        }

        val transactionNotes = buildList {
            result.livePhotoBundle?.let { bundle ->
                add("live-photo:bundle")
                add("live-photo:motion=${bundle.motionMimeType}")
                add("live-photo:sidecar=${bundle.sidecarMimeType}")
            }
            result.metadata.customTags["shutterSoundEnabled"]
                ?.let { enabled -> add("shutter-sound:$enabled") }
            if (result.metadata.customTags["selfieMirrorApply"].toBoolean()) {
                add("selfie-mirror:requested")
            }
            result.metadata.customTags["manualDraftState"]?.let { state ->
                val raw = result.metadata.customTags["manualDraftRaw"] ?: "unknown"
                val iso = result.metadata.customTags["manualDraftIso"] ?: "unknown"
                val shutter = result.metadata.customTags["manualDraftShutterSpeedMillis"] ?: "unknown"
                val whiteBalance = result.metadata.customTags["manualDraftWhiteBalanceKelvin"]
                    ?: "unknown"
                add("manual-draft:$state:raw-$raw:iso-$iso:s-$shutter:wb-$whiteBalance")
            }
            if (result.metadata.exifOverrides.isNotEmpty()) {
                add("exif:${result.metadata.exifOverrides.keys.sorted().joinToString(",")}")
            }
        }

        val allNotes = deviceNotes + algorithmNotes + transactionNotes
        if (allNotes.isEmpty()) {
            return result
        }
        return result.copy(pipelineNotes = result.pipelineNotes + allNotes)
    }
}

class MultiFrameMergePlaceholderPostProcessor : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        val intermediatePaths = result.intermediateOutputPaths
        if (result.captureProfile.frameCount <= 1 || intermediatePaths.isEmpty()) {
            return result
        }

        val existingInputs = intermediatePaths.map(::File).filter(File::exists)
        val totalInputBytes = existingInputs.sumOf(File::length)
        val notes = buildList {
            add("merge:placeholder")
            add("merge:inputs=${existingInputs.size + 1}")
            add("merge:temp-frames=${existingInputs.size}")
            add("merge:temp-bytes=$totalInputBytes")
            add("merge:strategy=burst-placeholder")
        }

        return try {
            result.copy(
                pipelineNotes = result.pipelineNotes + notes
            )
        } finally {
            existingInputs.forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }
}

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
            )
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

// ──────────────────────────────────────────────────────────────────────────────
// ShotGraph 2.0 Contracts
// ──────────────────────────────────────────────────────────────────────────────

enum class CaptureNodeRole {
    PRIMARY_STILL,
    TEMPORARY_FRAME,
    PRE_SHUTTER_FRAME,
    MOTION_SEGMENT,
    METADATA_SAMPLE
}

data class CaptureTimingPolicy(
    val sequential: Boolean = true,
    val maxConcurrent: Int = 1,
    val interFrameDelayMillis: Long = 0
)

data class CaptureFrameFormat(
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null
)

data class CaptureNode(
    val id: String,
    val role: CaptureNodeRole,
    val frameCount: Int,
    val timingPolicy: CaptureTimingPolicy = CaptureTimingPolicy(),
    val requiredFormat: CaptureFrameFormat
)

enum class AlgorithmType {
    FILTER_RENDER,
    WATERMARK_RENDER,
    MULTI_FRAME_MERGE,
    NIGHT_ENHANCE,
    PORTRAIT_RENDER,
    DOCUMENT_ENHANCE,
    LIVE_ASSEMBLE,
    THUMBNAIL_SELECT
}

enum class AlgorithmRequirement {
    REQUIRED,
    OPTIONAL,
    DEGRADED
}

enum class AlgorithmFallback {
    SKIP,
    USE_ORIGINAL,
    FAIL_SHOT
}

data class AlgorithmNode(
    val id: String,
    val type: AlgorithmType,
    val inputs: List<String>,
    val output: String,
    val requirement: AlgorithmRequirement,
    val fallback: AlgorithmFallback
)

enum class MediaArtifactRole {
    PRIMARY_STILL,
    PRIMARY_VIDEO,
    TEMP_FRAME,
    MOTION_SEGMENT,
    LIVE_SIDECAR,
    THUMBNAIL,
    DEBUG_TRACE
}

data class OutputNode(
    val id: String,
    val role: MediaArtifactRole,
    val targetPath: String? = null,
    val mimeType: String
)

data class ShotGraph(
    val shotId: String,
    val captureNodes: List<CaptureNode>,
    val algorithmNodes: List<AlgorithmNode>,
    val outputNodes: List<OutputNode>,
    val diagnostics: List<String> = emptyList()
)

// ──────────────────────────────────────────────────────────────────────────────
// AlgorithmProcessor Contracts
// ──────────────────────────────────────────────────────────────────────────────

data class MediaInputRef(
    val path: String,
    val handle: MediaOutputHandle,
    val mimeType: String
)

data class AlgorithmBudget(
    val maxDurationMillis: Long = 30_000,
    val maxMemoryBytes: Long = 256L * 1024 * 1024
)

data class AlgorithmRequest(
    val node: AlgorithmNode,
    val inputs: List<MediaInputRef>,
    val metadata: MediaMetadata,
    val budget: AlgorithmBudget = AlgorithmBudget()
)

sealed interface AlgorithmResult {
    data class Applied(
        val output: MediaOutputHandle,
        val notes: List<String>
    ) : AlgorithmResult

    data class Skipped(
        val reason: String,
        val notes: List<String>
    ) : AlgorithmResult

    data class Failed(
        val reason: String,
        val recoverable: Boolean
    ) : AlgorithmResult
}

interface AlgorithmProcessor {
    val type: AlgorithmType
    fun canProcess(request: AlgorithmRequest): Boolean
    suspend fun process(request: AlgorithmRequest): AlgorithmResult
}

fun MultiFrameMergePlaceholderPostProcessor.toAlgorithmProcessor(): AlgorithmProcessor {
    val delegate = this
    return object : AlgorithmProcessor {
        override val type: AlgorithmType = AlgorithmType.MULTI_FRAME_MERGE

        override fun canProcess(request: AlgorithmRequest): Boolean {
            val frameCount = request.metadata.customTags["frameCount"]?.toIntOrNull() ?: 0
            return frameCount > 1 && request.inputs.isNotEmpty()
        }

        override suspend fun process(request: AlgorithmRequest): AlgorithmResult {
            val frameCount = request.metadata.customTags["frameCount"]?.toIntOrNull() ?: 0
            val inputPaths = request.inputs.map { it.path }
            val syntheticResult = ShotResult(
                shotId = request.node.id,
                mediaType = MediaType.PHOTO,
                outputPath = request.node.output,
                saveRequest = SaveRequest.photoLibrary(),
                thumbnailSource = ThumbnailSource.None,
                captureProfile = CaptureProfile(frameCount = frameCount),
                metadata = request.metadata,
                intermediateOutputPaths = inputPaths
            )
            val processed = delegate.process(syntheticResult)
            val mergeNotes = processed.pipelineNotes.filter { it.startsWith("merge:") }
            return if (mergeNotes.isNotEmpty()) {
                AlgorithmResult.Applied(
                    output = MediaOutputHandle(displayPath = processed.outputPath),
                    notes = mergeNotes
                )
            } else {
                AlgorithmResult.Skipped(
                    reason = "single-frame-or-no-intermediates",
                    notes = emptyList()
                )
            }
        }
    }
}

fun ShotPlan.toShotGraph(): ShotGraph {
    val request = this.request
    val captureNodes = mutableListOf<CaptureNode>()
    val algorithmNodes = mutableListOf<AlgorithmNode>()
    val outputNodes = mutableListOf<OutputNode>()

    val imageFormat = CaptureFrameFormat(mimeType = request.saveRequest.mimeType)
    val videoFormat = CaptureFrameFormat(mimeType = "video/mp4")

    when (request.shotKind) {
        ShotKind.STILL_CAPTURE -> {
            captureNodes.add(
                CaptureNode(
                    id = "${request.shotId}:primary",
                    role = CaptureNodeRole.PRIMARY_STILL,
                    frameCount = 1,
                    requiredFormat = imageFormat
                )
            )
            outputNodes.add(
                OutputNode(
                    id = "${request.shotId}:out:primary",
                    role = MediaArtifactRole.PRIMARY_STILL,
                    mimeType = request.saveRequest.mimeType
                )
            )
        }

        ShotKind.MULTI_FRAME_CAPTURE -> {
            captureNodes.add(
                CaptureNode(
                    id = "${request.shotId}:temp-frames",
                    role = CaptureNodeRole.TEMPORARY_FRAME,
                    frameCount = request.captureProfile.frameCount,
                    timingPolicy = CaptureTimingPolicy(sequential = true),
                    requiredFormat = imageFormat
                )
            )
            captureNodes.add(
                CaptureNode(
                    id = "${request.shotId}:primary",
                    role = CaptureNodeRole.PRIMARY_STILL,
                    frameCount = 1,
                    requiredFormat = imageFormat
                )
            )
            algorithmNodes.add(
                AlgorithmNode(
                    id = "${request.shotId}:alg:merge",
                    type = AlgorithmType.MULTI_FRAME_MERGE,
                    inputs = listOf("${request.shotId}:temp-frames"),
                    output = "${request.shotId}:primary",
                    requirement = AlgorithmRequirement.REQUIRED,
                    fallback = AlgorithmFallback.FAIL_SHOT
                )
            )
            outputNodes.add(
                OutputNode(
                    id = "${request.shotId}:out:primary",
                    role = MediaArtifactRole.PRIMARY_STILL,
                    mimeType = request.saveRequest.mimeType
                )
            )
        }

        ShotKind.LIVE_PHOTO -> {
            captureNodes.add(
                CaptureNode(
                    id = "${request.shotId}:still",
                    role = CaptureNodeRole.PRIMARY_STILL,
                    frameCount = 1,
                    requiredFormat = imageFormat
                )
            )
            captureNodes.add(
                CaptureNode(
                    id = "${request.shotId}:motion",
                    role = CaptureNodeRole.MOTION_SEGMENT,
                    frameCount = 1,
                    requiredFormat = videoFormat
                )
            )
            algorithmNodes.add(
                AlgorithmNode(
                    id = "${request.shotId}:alg:live-assemble",
                    type = AlgorithmType.LIVE_ASSEMBLE,
                    inputs = listOf("${request.shotId}:still", "${request.shotId}:motion"),
                    output = "${request.shotId}:live-bundle",
                    requirement = AlgorithmRequirement.REQUIRED,
                    fallback = AlgorithmFallback.USE_ORIGINAL
                )
            )
            outputNodes.add(
                OutputNode(
                    id = "${request.shotId}:out:still",
                    role = MediaArtifactRole.PRIMARY_STILL,
                    mimeType = request.saveRequest.mimeType
                )
            )
            outputNodes.add(
                OutputNode(
                    id = "${request.shotId}:out:motion",
                    role = MediaArtifactRole.MOTION_SEGMENT,
                    mimeType = request.livePhotoSpec?.motionMimeType ?: "video/mp4"
                )
            )
            outputNodes.add(
                OutputNode(
                    id = "${request.shotId}:out:sidecar",
                    role = MediaArtifactRole.LIVE_SIDECAR,
                    mimeType = request.livePhotoSpec?.sidecarMimeType
                        ?: "application/vnd.opencamera.live+json"
                )
            )
        }

        ShotKind.VIDEO_RECORDING -> {
            captureNodes.add(
                CaptureNode(
                    id = "${request.shotId}:video",
                    role = CaptureNodeRole.PRIMARY_STILL,
                    frameCount = 1,
                    requiredFormat = videoFormat
                )
            )
            outputNodes.add(
                OutputNode(
                    id = "${request.shotId}:out:video",
                    role = MediaArtifactRole.PRIMARY_VIDEO,
                    mimeType = request.saveRequest.mimeType
                )
            )
        }
    }

    val spec = request.postProcessSpec
    if (spec.algorithmProfile != null) {
        algorithmNodes.add(
            AlgorithmNode(
                id = "${request.shotId}:alg:filter",
                type = AlgorithmType.FILTER_RENDER,
                inputs = listOf("${request.shotId}:primary"),
                output = "${request.shotId}:filtered",
                requirement = AlgorithmRequirement.OPTIONAL,
                fallback = AlgorithmFallback.USE_ORIGINAL
            )
        )
    }
    if (spec.watermarkText != null) {
        algorithmNodes.add(
            AlgorithmNode(
                id = "${request.shotId}:alg:watermark",
                type = AlgorithmType.WATERMARK_RENDER,
                inputs = listOf("${request.shotId}:primary"),
                output = "${request.shotId}:watermarked",
                requirement = AlgorithmRequirement.OPTIONAL,
                fallback = AlgorithmFallback.USE_ORIGINAL
            )
        )
    }
    if (request.thumbnailPolicy != ThumbnailPolicy.NONE) {
        algorithmNodes.add(
            AlgorithmNode(
                id = "${request.shotId}:alg:thumbnail",
                type = AlgorithmType.THUMBNAIL_SELECT,
                inputs = listOf("${request.shotId}:primary"),
                output = "${request.shotId}:thumbnail",
                requirement = AlgorithmRequirement.OPTIONAL,
                fallback = AlgorithmFallback.SKIP
            )
        )
    }

    return ShotGraph(
        shotId = request.shotId,
        captureNodes = captureNodes,
        algorithmNodes = algorithmNodes,
        outputNodes = outputNodes
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Media Save Transaction
// ──────────────────────────────────────────────────────────────────────────────

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

    return MediaSaveTransactionResult(
        primaryOutput = primaryHandle,
        artifacts = artifacts,
        status = MediaTransactionStatus.SUCCESS,
        cleanupNotes = cleanupNotes
    )
}
