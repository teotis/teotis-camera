package com.opencamera.core.media

enum class CaptureNodeRole {
    PRIMARY_STILL,
    PRIMARY_VIDEO,
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
