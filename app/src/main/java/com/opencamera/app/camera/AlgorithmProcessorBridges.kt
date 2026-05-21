package com.opencamera.app.camera

import com.opencamera.core.media.AlgorithmNode
import com.opencamera.core.media.AlgorithmProcessor
import com.opencamera.core.media.AlgorithmRequest
import com.opencamera.core.media.AlgorithmResult
import com.opencamera.core.media.AlgorithmType
import com.opencamera.core.media.MediaInputRef
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource

// ──────────────────────────────────────────────────────────────────────────────
// Shared helper
// ──────────────────────────────────────────────────────────────────────────────

private fun AlgorithmRequest.toSyntheticShotResult(
    mediaType: MediaType = MediaType.PHOTO
): ShotResult {
    val saveRequest = when (mediaType) {
        MediaType.VIDEO -> SaveRequest.videoLibrary(metadata = metadata)
        MediaType.PHOTO -> SaveRequest.photoLibrary(metadata = metadata)
    }
    return ShotResult(
        shotId = node.id,
        mediaType = mediaType,
        outputPath = node.output,
        outputHandle = inputs.firstOrNull()?.handle
            ?: MediaOutputHandle(displayPath = node.output),
        saveRequest = saveRequest,
        thumbnailSource = ThumbnailSource.None,
        metadata = metadata
    )
}

private fun MediaPostProcessor.toAlgorithmProcessorBridge(
    algorithmType: AlgorithmType,
    canDecide: (AlgorithmRequest) -> Boolean,
    extractNotes: (ShotResult, List<String>) -> List<String> = { result, _ -> result.pipelineNotes }
): AlgorithmProcessor {
    val delegate = this
    return object : AlgorithmProcessor {
        override val type: AlgorithmType = algorithmType

        override fun canProcess(request: AlgorithmRequest): Boolean = canDecide(request)

        override suspend fun process(request: AlgorithmRequest): AlgorithmResult {
            val synthetic = request.toSyntheticShotResult()
            val processed = delegate.process(synthetic)
            val notes = extractNotes(processed, request.node.inputs)
            return if (notes.isNotEmpty()) {
                AlgorithmResult.Applied(
                    output = MediaOutputHandle(displayPath = processed.outputPath),
                    notes = notes
                )
            } else {
                AlgorithmResult.Skipped(
                    reason = "no-work",
                    notes = emptyList()
                )
            }
        }
    }
}

private fun extractNotesWithPrefix(result: ShotResult, prefix: String): List<String> {
    return result.pipelineNotes.filter { it.startsWith(prefix) }
}

// ──────────────────────────────────────────────────────────────────────────────
// Bridge extensions
// ──────────────────────────────────────────────────────────────────────────────

internal fun PhotoAlgorithmPostProcessor.toAlgorithmProcessor(): AlgorithmProcessor {
    return toAlgorithmProcessorBridge(
        algorithmType = AlgorithmType.FILTER_RENDER,
        canDecide = { req ->
            req.metadata.algorithmProfile != null ||
                req.metadata.customTags.containsKey("filterProfile")
        },
        extractNotes = { result, _ -> extractNotesWithPrefix(result, "algorithm-render:") }
    )
}

internal fun PhotoWatermarkPostProcessor.toAlgorithmProcessor(): AlgorithmProcessor {
    return toAlgorithmProcessorBridge(
        algorithmType = AlgorithmType.WATERMARK_RENDER,
        canDecide = { req ->
            req.metadata.watermarkText != null
        },
        extractNotes = { result, _ -> extractNotesWithPrefix(result, "watermark:") }
    )
}

internal fun PortraitRenderPostProcessor.toAlgorithmProcessor(): AlgorithmProcessor {
    return toAlgorithmProcessorBridge(
        algorithmType = AlgorithmType.PORTRAIT_RENDER,
        canDecide = { req ->
            req.metadata.customTags["mode"] == "portrait"
        },
        extractNotes = { result, _ -> extractNotesWithPrefix(result, "portrait-render:") }
    )
}

internal fun DocumentAutoCropPostProcessor.toAlgorithmProcessor(): AlgorithmProcessor {
    return toAlgorithmProcessorBridge(
        algorithmType = AlgorithmType.DOCUMENT_ENHANCE,
        canDecide = { req ->
            req.metadata.customTags["mode"] == "document" &&
                req.metadata.customTags["autoCrop"] == "true"
        },
        extractNotes = { result, _ -> extractNotesWithPrefix(result, "document:") }
    )
}

internal fun PhotoFrameRatioPostProcessor.toAlgorithmProcessor(): AlgorithmProcessor {
    return toAlgorithmProcessorBridge(
        algorithmType = AlgorithmType.THUMBNAIL_SELECT,
        canDecide = { req ->
            req.metadata.customTags.containsKey("frameRatio")
        },
        extractNotes = { result, _ -> extractNotesWithPrefix(result, "frame-ratio:") }
    )
}

internal fun PhotoSelfieMirrorPostProcessor.toAlgorithmProcessor(): AlgorithmProcessor {
    return toAlgorithmProcessorBridge(
        algorithmType = AlgorithmType.FILTER_RENDER,
        canDecide = { req ->
            req.metadata.customTags["selfieMirrorApply"] == "true"
        },
        extractNotes = { result, _ -> extractNotesWithPrefix(result, "selfie-mirror:") }
    )
}

internal fun VideoWatermarkSubtitlePostProcessor.toAlgorithmProcessor(): AlgorithmProcessor {
    return toAlgorithmProcessorBridge(
        algorithmType = AlgorithmType.WATERMARK_RENDER,
        canDecide = { req ->
            req.metadata.watermarkText != null &&
                req.inputs.any { it.mimeType.contains("video") }
        },
        extractNotes = { result, _ -> extractNotesWithPrefix(result, "video-subtitle:") }
    )
}
