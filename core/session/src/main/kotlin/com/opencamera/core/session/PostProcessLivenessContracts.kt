package com.opencamera.core.session

import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessLivenessDeadline
import com.opencamera.core.media.ShotConfigSnapshot
import com.opencamera.core.mode.ModeId

/**
 * Stage at which a post-process liveness event was raised.
 *
 * Distinct from [com.opencamera.core.media.PostProcessFailureStage] — that enum is
 * scoped to in-pipeline failures of specific processors. This one is scoped to
 * *liveness* (timeout / forced release) and is intentionally coarser so a single
 * grep can find all liveness exits.
 */
enum class PostProcessLivenessStage {
    MEDIA_POST_PROCESS,
    MEDIA_SAVE,
    DOCUMENT_BATCH,
    THUMBNAIL,
    UNKNOWN
}

/**
 * Structured diagnostic event emitted whenever post-process liveness fires.
 *
 * The session is the only emitter — packages 03/04/05 surface their failures via
 * variant constructors. Every variant must populate every field below; missing data
 * should fall back to a sentinel (UNKNOWN / -1) so structured grepping never misses.
 *
 * Variants:
 * - [DeadlineExpired]: the per-shot [PostProcessLivenessDeadline] elapsed.
 * - [PipelineFailed]: a stage threw a typed failure before the deadline.
 * - [ForceReleasedFromDocumentBatch]: document batch reducer forced release to
 *   unblock the shutter (item recorded FAILED / SKIPPED by package 04).
 */
sealed interface PostProcessLivenessEvent {
    val shotId: String
    val mediaType: MediaType
    val mode: ModeId
    val stage: PostProcessLivenessStage
    val reason: String
    val elapsedSinceShutterMs: Long
    val elapsedSincePostprocessStartMs: Long

    data class DeadlineExpired(
        override val shotId: String,
        override val mediaType: MediaType,
        override val mode: ModeId,
        override val stage: PostProcessLivenessStage,
        override val reason: String,
        override val elapsedSinceShutterMs: Long,
        override val elapsedSincePostprocessStartMs: Long,
        val budgetMillis: Long
    ) : PostProcessLivenessEvent

    data class PipelineFailed(
        override val shotId: String,
        override val mediaType: MediaType,
        override val mode: ModeId,
        override val stage: PostProcessLivenessStage,
        override val reason: String,
        override val elapsedSinceShutterMs: Long,
        override val elapsedSincePostprocessStartMs: Long
    ) : PostProcessLivenessEvent

    data class ForceReleasedFromDocumentBatch(
        override val shotId: String,
        override val mediaType: MediaType,
        override val mode: ModeId,
        override val stage: PostProcessLivenessStage,
        override val reason: String,
        override val elapsedSinceShutterMs: Long,
        override val elapsedSincePostprocessStartMs: Long,
        val itemId: String?
    ) : PostProcessLivenessEvent
}

/**
 * Stable, greppable summary of a [PostProcessLivenessEvent]. Designed so log scrapers
 * and CI assertions can match a single substring per variant. Format:
 *
 * `postprocess-liveness:<variant>:shotId=<id>:mediaType=<t>:mode=<m>:stage=<s>:reason=<r>` ...
 */
fun PostProcessLivenessEvent.toDiagnosticString(): String {
    val variant = when (this) {
        is PostProcessLivenessEvent.DeadlineExpired -> "deadline-expired"
        is PostProcessLivenessEvent.PipelineFailed -> "pipeline-failed"
        is PostProcessLivenessEvent.ForceReleasedFromDocumentBatch -> "force-released-from-document-batch"
    }
    val tail = buildString {
        append("postprocess-liveness:")
        append(variant)
        append(":shotId=").append(shotId)
        append(":mediaType=").append(mediaType.name)
        append(":mode=").append(mode.name)
        append(":stage=").append(stage.name)
        append(":reason=").append(reason)
        append(":elapsedSinceShutterMs=").append(elapsedSinceShutterMs)
        append(":elapsedSincePostprocessStartMs=").append(elapsedSincePostprocessStartMs)
        when (val event = this@toDiagnosticString) {
            is PostProcessLivenessEvent.DeadlineExpired ->
                append(":budgetMillis=").append(event.budgetMillis)
            is PostProcessLivenessEvent.ForceReleasedFromDocumentBatch ->
                append(":itemId=").append(event.itemId ?: "")
            is PostProcessLivenessEvent.PipelineFailed -> Unit
        }
    }
    return tail
}

/**
 * Session-side sibling that pairs a pending post-process state with its config snapshot
 * and deadline. Mirrors [com.opencamera.core.media.ShotRequestLivenessEnvelope] on the
 * media side. Stored as a `null`-defaulted attachment on
 * [PendingPostprocessUiState] in [SessionContracts] so existing renderers keep working.
 */
data class PendingPostprocessLivenessAttachment(
    val configSnapshot: ShotConfigSnapshot?,
    val liveness: PostProcessLivenessDeadline?
)
