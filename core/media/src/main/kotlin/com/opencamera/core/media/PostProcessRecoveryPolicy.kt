package com.opencamera.core.media

import kotlinx.coroutines.CancellationException

/**
 * Recovery action returned by the postprocess recovery policy.
 *
 * Each action maps to a specific continuation behavior:
 * - CONTINUE: processor failed but output is safe to pass to next processor.
 * - STOP_POSTPROCESS: stop the pipeline, keep the current (possibly modified) output.
 * - TERMINATE: stop the pipeline and discard current output, fall back to primary capture.
 * - PROPAGATE: rethrow the failure to the caller (used for process-fatal errors).
 */
enum class RecoveryAction {
    CONTINUE,
    STOP_POSTPROCESS,
    TERMINATE,
    PROPAGATE,
    RECOVER_RELEASE
}

/**
 * Evaluate the recovery policy for a structured [PostProcessFailure].
 *
 * Truth table:
 * ```
 * | Disposition  | Integrity         | Action           |
 * |--------------|-------------------|------------------|
 * | RECOVERABLE  | ORIGINAL_INTACT   | CONTINUE         |
 * | RECOVERABLE  | POSSIBLY_MODIFIED | STOP_POSTPROCESS |
 * | RECOVERABLE  | UNKNOWN           | STOP_POSTPROCESS |
 * | UNRECOVERABLE| ORIGINAL_INTACT   | TERMINATE        |
 * | UNRECOVERABLE| POSSIBLY_MODIFIED | TERMINATE        |
 * | UNRECOVERABLE| UNKNOWN           | PROPAGATE        |
 * ```
 *
 * This function is pure and stateless — safe for direct unit testing.
 */
fun evaluateRecoveryPolicy(failure: PostProcessFailure): RecoveryAction {
    if (failure.disposition == PostProcessFailureDisposition.UNRECOVERABLE &&
        failure.integrity == PostProcessOutputIntegrity.UNKNOWN
    ) {
        return RecoveryAction.PROPAGATE
    }
    if (failure.disposition == PostProcessFailureDisposition.UNRECOVERABLE) {
        return RecoveryAction.TERMINATE
    }
    // RECOVERABLE
    if (failure.integrity == PostProcessOutputIntegrity.ORIGINAL_INTACT) {
        return RecoveryAction.CONTINUE
    }
    // POSSIBLY_MODIFIED or UNKNOWN with RECOVERABLE disposition
    return RecoveryAction.STOP_POSTPROCESS
}

/**
 * Classify a thrown exception into a typed [PostProcessFailure].
 *
 * Bridges untyped exception throws into the structured recovery policy.
 * CancellationException is classified as UNRECOVERABLE (propagation handled
 * separately in CompositeMediaPostProcessor).
 */
internal fun classifyExceptionForRecovery(
    throwable: Throwable,
    processorName: String
): PostProcessFailure {
    val cause = when {
        throwable is OutOfMemoryError -> PostProcessFailureCause.OUT_OF_MEMORY
        throwable is CancellationException -> PostProcessFailureCause.EXCEPTION
        throwable.message?.contains("decode", ignoreCase = true) == true ->
            PostProcessFailureCause.DECODE_FAILED
        throwable.message?.contains("encode", ignoreCase = true) == true ->
            PostProcessFailureCause.ENCODE
        throwable.message?.contains("bitmap", ignoreCase = true) == true ->
            PostProcessFailureCause.BITMAP_OPERATION
        else -> PostProcessFailureCause.EXCEPTION
    }

    val integrity = when {
        throwable is CancellationException -> PostProcessOutputIntegrity.UNKNOWN
        throwable is Error -> PostProcessOutputIntegrity.UNKNOWN
        else -> PostProcessOutputIntegrity.POSSIBLY_MODIFIED
    }

    val disposition = when {
        throwable is CancellationException -> PostProcessFailureDisposition.UNRECOVERABLE
        throwable is Error -> PostProcessFailureDisposition.UNRECOVERABLE
        throwable is OutOfMemoryError -> PostProcessFailureDisposition.UNRECOVERABLE
        else -> PostProcessFailureDisposition.RECOVERABLE
    }

    return PostProcessFailure(
        stage = PostProcessFailureStage.COMPOSITE,
        cause = cause,
        integrity = integrity,
        disposition = disposition,
        processorName = processorName
    )
}

/**
 * Lightweight monotonic time source so the watchdog can check deadline expiry without
 * coupling to `android.os.SystemClock`. Production callers supply
 * `SystemClock.elapsedRealtime()` or `System.nanoTime() / 1_000_000`; tests supply
 * a controllable virtual clock.
 */
fun interface PostProcessTimeSource {
    fun elapsedMillis(): Long
}

/**
 * Evaluate the recovery policy for a timeout failure.
 *
 * Timeout failures are always [RecoveryAction.RECOVER_RELEASE]: the session must
 * release `activeShot` and `pendingPostprocess` so the capture link is never
 * occupied indefinitely by a single shot.
 */
fun evaluateTimeoutPolicy(): RecoveryAction = RecoveryAction.RECOVER_RELEASE
