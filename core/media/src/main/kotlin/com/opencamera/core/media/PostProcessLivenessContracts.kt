package com.opencamera.core.media

/**
 * Per-shot post-process liveness deadline.
 *
 * Every shot that enters the post-process pipeline (media post-process, save, document
 * batch write, thumbnail update) must carry a [PostProcessLivenessDeadline]. Any stage
 * that crosses [deadlineElapsedMillis] is required to take the timeout-release path so
 * a single shot cannot occupy the capture link indefinitely.
 *
 * Quantities use `SystemClock.elapsedRealtime` semantics — the same monotonic clock as
 * existing [ResourceBudget.timeoutMillis] / [AlgorithmJobSpec.timeoutMillis] fields.
 */
data class PostProcessLivenessDeadline(
    val shotId: String,
    val deadlineElapsedMillis: Long,
    val startedAtElapsedMillis: Long
) {
    /** Convenience: this deadline's budget window in millis (always > 0 by construction). */
    val budgetMillis: Long
        get() = deadlineElapsedMillis - startedAtElapsedMillis

    /** Returns true when [nowElapsedMillis] is strictly past [deadlineElapsedMillis]. */
    fun isExpired(nowElapsedMillis: Long): Boolean = nowElapsedMillis > deadlineElapsedMillis

    companion object {
        /**
         * Default per-shot budget. Chosen to align with the longest [AlgorithmJobSpec.timeoutMillis]
         * presently used in the capture pipeline (see `ResourceBudgetContracts.AlgorithmJobSpec`).
         * The constant is intentionally exposed so downstream callers (03/04/05) can
         * derive plan-specific overrides without hardcoding a "looks reasonable" number.
         */
        const val DEFAULT_BUDGET_MILLIS: Long = 8_000L

        /**
         * Builds a deadline from a [start] elapsed-millis timestamp and a positive
         * [budgetMs]. Callers attach a real `shotId` via [copy] or the [forShot] helper.
         *
         * Throws [IllegalArgumentException] when [budgetMs] <= 0 or [start] < 0 — both
         * are treated as programmer errors, not silently coerced.
         */
        fun from(start: Long, budgetMs: Long): PostProcessLivenessDeadline {
            require(start >= 0L) { "start must be >= 0, got $start" }
            require(budgetMs > 0L) { "budgetMs must be > 0, got $budgetMs" }
            return PostProcessLivenessDeadline(
                shotId = "",
                deadlineElapsedMillis = start + budgetMs,
                startedAtElapsedMillis = start
            )
        }

        /** Same as [from] but attaches a real [shotId] up-front. */
        fun forShot(shotId: String, start: Long, budgetMs: Long): PostProcessLivenessDeadline =
            from(start, budgetMs).copy(shotId = shotId)
    }
}

/**
 * Immutable snapshot of UI configuration captured when the shutter is pressed.
 *
 * Post-process must read every configuration value that affects the saved artifact
 * (watermark template, frame ratio, color recipe, document-mode flag) from this snapshot
 * rather than from live settings. Subsequent UI changes after the shutter press affect
 * the *next* shot, never the in-flight one.
 *
 * Explicit non-goals: the snapshot does NOT cover preview rendering, camera state
 * (zoom/lens/focus), or the algorithm registry. Those remain live by design — only
 * "what determines the file we save" is frozen here.
 */
data class ShotConfigSnapshot(
    val watermarkTemplateId: String?,
    val frameRatio: FrameRatio,
    val colorRecipeId: String?,
    val isDocumentMode: Boolean
)

/**
 * Sibling envelope pairing a [ShotRequest] with its liveness deadline and config snapshot.
 *
 * Adding fields directly to [ShotRequest] would force every existing call-site to update
 * in a single change; this envelope lets 03/04/05 thread liveness through the pipeline
 * incrementally while keeping [ShotRequest] structurally unchanged.
 *
 * Both attachments are nullable with defaults of `null` so existing call-sites continue
 * to compile and behave identically until they opt in.
 */
data class ShotRequestLivenessEnvelope(
    val request: ShotRequest,
    val configSnapshot: ShotConfigSnapshot? = null,
    val liveness: PostProcessLivenessDeadline? = null
)
