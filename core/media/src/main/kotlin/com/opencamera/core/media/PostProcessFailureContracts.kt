package com.opencamera.core.media

/**
 * Orthogonal typed failure model for post-process failures.
 *
 * Each dimension is independent — no giant enum combines stage, cause, integrity, and disposition.
 * Legacy pipeline notes are generated from structured failures via [PostProcessFailure.toLegacyNote].
 */
enum class PostProcessFailureStage {
    SELFIE_MIRROR,
    WATERMARK,
    ALGORITHM,
    PORTRAIT_RENDER,
    FRAME_RATIO,
    DOCUMENT_AUTO_CROP,
    COMPOSITE
}

enum class PostProcessFailureCause {
    DECODE_FAILED,
    OUT_OF_MEMORY,
    BITMAP_OPERATION,
    ENCODE,
    OUTPUT_UNAVAILABLE,
    EXCEPTION,
    TIMEOUT
}

enum class PostProcessOutputIntegrity {
    ORIGINAL_INTACT,
    POSSIBLY_MODIFIED,
    UNKNOWN
}

enum class PostProcessFailureDisposition {
    RECOVERABLE,
    UNRECOVERABLE
}

data class PostProcessFailure(
    val stage: PostProcessFailureStage,
    val cause: PostProcessFailureCause,
    val integrity: PostProcessOutputIntegrity,
    val disposition: PostProcessFailureDisposition,
    val processorName: String? = null
) {
    fun toLegacyNote(): String = when (stage) {
        PostProcessFailureStage.COMPOSITE -> {
            val name = processorName ?: "Unknown"
            "postprocess:failed:$name:composite-failure"
        }
        else -> "${stage.legacyNotePrefix}:failed:${cause.legacyNoteSuffix}"
    }
}

val PostProcessFailureStage.legacyNotePrefix: String
    get() = when (this) {
        PostProcessFailureStage.SELFIE_MIRROR -> "selfie-mirror"
        PostProcessFailureStage.WATERMARK -> "watermark"
        PostProcessFailureStage.ALGORITHM -> "algorithm-render"
        PostProcessFailureStage.PORTRAIT_RENDER -> "portrait-render"
        PostProcessFailureStage.FRAME_RATIO -> "frame-ratio"
        PostProcessFailureStage.DOCUMENT_AUTO_CROP -> "document:auto-crop"
        PostProcessFailureStage.COMPOSITE -> "postprocess"
    }

val PostProcessFailureCause.legacyNoteSuffix: String
    get() = when (this) {
        PostProcessFailureCause.DECODE_FAILED -> "decode-failed"
        PostProcessFailureCause.OUT_OF_MEMORY -> "out-of-memory"
        PostProcessFailureCause.BITMAP_OPERATION -> "bitmap-operation"
        PostProcessFailureCause.ENCODE -> "encode-failed"
        PostProcessFailureCause.OUTPUT_UNAVAILABLE -> "output-unavailable"
        PostProcessFailureCause.EXCEPTION -> "exception"
        PostProcessFailureCause.TIMEOUT -> "timeout"
    }

fun ShotResult.addStructuredPostProcessFailure(failure: PostProcessFailure): ShotResult {
    return copy(structuredPostProcessFailures = structuredPostProcessFailures + failure)
}
