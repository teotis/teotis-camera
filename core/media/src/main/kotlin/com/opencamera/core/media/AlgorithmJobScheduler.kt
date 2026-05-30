package com.opencamera.core.media

// ──────────────────────────────────────────────────────────────────────────────
// Job Class to Budget Mapping
// ──────────────────────────────────────────────────────────────────────────────

fun timeoutForJobClass(
    jobClass: AlgorithmJobClass,
    budget: CameraResourceBudget
): Long {
    return when (jobClass) {
        AlgorithmJobClass.REALTIME_PREVIEW -> 500L
        AlgorithmJobClass.CAPTURE_CRITICAL -> budget.maxPostProcessMillis
        AlgorithmJobClass.CAPTURE_OPTIONAL -> budget.maxPostProcessMillis / 2
        AlgorithmJobClass.CLEANUP -> budget.maxPostProcessMillis * 2
    }
}

fun cancellableForJobClass(jobClass: AlgorithmJobClass): Boolean {
    return when (jobClass) {
        AlgorithmJobClass.REALTIME_PREVIEW -> true
        AlgorithmJobClass.CAPTURE_CRITICAL -> false
        AlgorithmJobClass.CAPTURE_OPTIONAL -> true
        AlgorithmJobClass.CLEANUP -> false
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Job Spec Builder
// ──────────────────────────────────────────────────────────────────────────────

fun AlgorithmRequest.toJobSpec(
    jobClass: AlgorithmJobClass,
    budget: CameraResourceBudget
): AlgorithmJobSpec {
    return AlgorithmJobSpec(
        jobClass = jobClass,
        request = this,
        timeoutMillis = timeoutForJobClass(jobClass, budget),
        cancellable = cancellableForJobClass(jobClass)
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Job Class Assignment for Shot Graph nodes
// ──────────────────────────────────────────────────────────────────────────────

fun AlgorithmNode.defaultJobClass(): AlgorithmJobClass {
    return when {
        type == AlgorithmType.THUMBNAIL_SELECT -> AlgorithmJobClass.CAPTURE_OPTIONAL
        requirement == AlgorithmRequirement.OPTIONAL -> AlgorithmJobClass.CAPTURE_OPTIONAL
        requirement == AlgorithmRequirement.REQUIRED &&
            fallback == AlgorithmFallback.FAIL_SHOT -> AlgorithmJobClass.CAPTURE_CRITICAL
        else -> AlgorithmJobClass.CAPTURE_CRITICAL
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Timeout Result Mapping
// ──────────────────────────────────────────────────────────────────────────────

fun mapTimeoutToJobResult(
    jobClass: AlgorithmJobClass,
    node: AlgorithmNode
): AlgorithmJobResult {
    return when (jobClass) {
        AlgorithmJobClass.CAPTURE_CRITICAL -> AlgorithmJobResult.TimedOut(
            reason = "algorithm-timeout:${node.type.name.lowercase()}",
            recoverable = false
        )

        AlgorithmJobClass.CAPTURE_OPTIONAL -> AlgorithmJobResult.Completed(
            AlgorithmResult.Skipped(
                reason = "algorithm-timeout:${node.type.name.lowercase()}",
                notes = listOf("resource:algorithm-timeout=${node.type.name.lowercase()}")
            )
        )

        AlgorithmJobClass.REALTIME_PREVIEW -> AlgorithmJobResult.Completed(
            AlgorithmResult.Skipped(
                reason = "preview-timeout",
                notes = listOf("resource:preview-timeout")
            )
        )

        AlgorithmJobClass.CLEANUP -> AlgorithmJobResult.TimedOut(
            reason = "cleanup-timeout",
            recoverable = true
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// G7: Algorithm Job Queue Depth
// ──────────────────────────────────────────────────────────────────────────────

fun queryAlgorithmQueueSnapshot(budget: CameraResourceBudget): AlgorithmQueueSnapshot {
    return algorithmQueueTracker.toSnapshot(budget)
}

fun recordAlgorithmJobDispatched() {
    algorithmQueueTracker.incrementActive()
}

fun recordAlgorithmJobCompleted() {
    algorithmQueueTracker.decrementActive()
}

fun recordAlgorithmJobEnqueued() {
    algorithmQueueTracker.incrementPending()
}

fun recordAlgorithmJobDequeued() {
    algorithmQueueTracker.decrementPending()
}

private val algorithmQueueTracker = AlgorithmQueueTracker()

// ──────────────────────────────────────────────────────────────────────────────
// Capture-Critical vs Optional Failure Mapping
// ──────────────────────────────────────────────────────────────────────────────

fun mapFailureToJobResult(
    jobClass: AlgorithmJobClass,
    result: AlgorithmResult.Failed,
    node: AlgorithmNode
): AlgorithmJobResult {
    return when (jobClass) {
        AlgorithmJobClass.CAPTURE_CRITICAL -> AlgorithmJobResult.Completed(result)

        AlgorithmJobClass.CAPTURE_OPTIONAL -> AlgorithmJobResult.Completed(
            AlgorithmResult.Skipped(
                reason = result.reason,
                notes = listOf(
                    "resource:optional-degraded:${node.type.name.lowercase()}",
                    "resource:optional-reason=${result.reason}"
                )
            )
        )

        AlgorithmJobClass.REALTIME_PREVIEW -> AlgorithmJobResult.Completed(
            AlgorithmResult.Skipped(
                reason = "preview-failed:${result.reason}",
                notes = listOf("resource:preview-failed")
            )
        )

        AlgorithmJobClass.CLEANUP -> AlgorithmJobResult.Completed(result)
    }
}
