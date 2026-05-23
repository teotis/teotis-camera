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

fun ShotGraph.primaryStillNode(): CaptureNode? =
    captureNodes.firstOrNull { it.role == CaptureNodeRole.PRIMARY_STILL }

fun ShotGraph.primaryVideoNode(): CaptureNode? =
    captureNodes.firstOrNull { it.role == CaptureNodeRole.PRIMARY_VIDEO }

fun ShotGraph.temporaryFrameNode(): CaptureNode? =
    captureNodes.firstOrNull { it.role == CaptureNodeRole.TEMPORARY_FRAME }

fun ShotGraph.requiresAlgorithm(type: AlgorithmType): Boolean =
    algorithmNodes.any { it.type == type }

fun ShotGraph.validateConsistency(shotKind: ShotKind): List<String> {
    val errors = mutableListOf<String>()
    when (shotKind) {
        ShotKind.VIDEO_RECORDING -> {
            if (primaryVideoNode() == null) {
                errors.add("VIDEO_RECORDING requires PRIMARY_VIDEO node")
            }
        }
        ShotKind.STILL_CAPTURE -> {
            if (primaryStillNode() == null) {
                errors.add("STILL_CAPTURE requires PRIMARY_STILL node")
            }
            if (temporaryFrameNode() != null) {
                errors.add("STILL_CAPTURE must not have TEMPORARY_FRAME node")
            }
        }
        ShotKind.MULTI_FRAME_CAPTURE -> {
            if (primaryStillNode() == null) {
                errors.add("MULTI_FRAME_CAPTURE requires PRIMARY_STILL node")
            }
            if (temporaryFrameNode() == null) {
                errors.add("MULTI_FRAME_CAPTURE requires TEMPORARY_FRAME node")
            }
            if (!requiresAlgorithm(AlgorithmType.MULTI_FRAME_MERGE)) {
                errors.add("MULTI_FRAME_CAPTURE requires MULTI_FRAME_MERGE algorithm")
            }
        }
        ShotKind.LIVE_PHOTO -> {
            if (primaryStillNode() == null) {
                errors.add("LIVE_PHOTO requires PRIMARY_STILL node")
            }
            if (captureNodes.none { it.role == CaptureNodeRole.MOTION_SEGMENT }) {
                errors.add("LIVE_PHOTO requires MOTION_SEGMENT node")
            }
            if (!requiresAlgorithm(AlgorithmType.LIVE_ASSEMBLE)) {
                errors.add("LIVE_PHOTO requires LIVE_ASSEMBLE algorithm")
            }
        }
    }
    return errors
}
