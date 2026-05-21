package com.opencamera.core.session

import com.opencamera.core.mode.ModeId

enum class PreviewStartCategory(
    val label: String
) {
    COLD_START("Cold start"),
    FOREGROUND_RESUME("Foreground resume"),
    RECOVERY("Recovery"),
    RECONFIGURE("Reconfigure"),
    UNKNOWN("Unknown")
}

enum class PerfBudgetStatus(
    val label: String
) {
    WITHIN_BUDGET("within budget"),
    WARNING("warning"),
    EXCEEDED("over budget"),
    UNAVAILABLE("unavailable")
}

data class FirstFrameBudgetSnapshot(
    val startCategory: PreviewStartCategory,
    val status: PerfBudgetStatus,
    val warnThresholdMillis: Long,
    val failThresholdMillis: Long
)

data class PerfSnapshot(
    val previewStatus: PreviewStatus,
    val bindCount: Int,
    val recoveryCount: Int,
    val lastFirstFrameLatencyMillis: Long?,
    val bestFirstFrameLatencyMillis: Long?,
    val worstFirstFrameLatencyMillis: Long?,
    val lastStartReason: String?,
    val firstFrameBudget: FirstFrameBudgetSnapshot
)

data class RecoveryTraceSnapshot(
    val isRecoveryActive: Boolean,
    val recoveryCount: Int,
    val lastRecoveryReason: String?,
    val lastFailureReason: String?,
    val recoveredFirstFrameLatencyMillis: Long?,
    val events: List<SessionTraceEvent>
)

data class SessionDebugDump(
    val lifecycle: SessionLifecycle,
    val activeMode: ModeId,
    val previewStatus: PreviewStatus,
    val captureStatus: CaptureStatus,
    val recordingStatus: RecordingStatus,
    val lastAction: String,
    val lastError: String?,
    val perfSnapshot: PerfSnapshot,
    val recoveryTrace: RecoveryTraceSnapshot,
    val recentEvents: List<SessionTraceEvent>
)

fun SessionState.toPerfSnapshot(): PerfSnapshot {
    val firstFrameBudget = assessFirstFrameBudget(
        lastStartReason = previewMetrics.lastStartReason,
        latencyMillis = previewMetrics.lastFirstFrameLatencyMillis
    )
    return PerfSnapshot(
        previewStatus = previewStatus,
        bindCount = previewMetrics.bindCount,
        recoveryCount = previewMetrics.recoveryCount,
        lastFirstFrameLatencyMillis = previewMetrics.lastFirstFrameLatencyMillis,
        bestFirstFrameLatencyMillis = previewMetrics.bestFirstFrameLatencyMillis,
        worstFirstFrameLatencyMillis = previewMetrics.worstFirstFrameLatencyMillis,
        lastStartReason = previewMetrics.lastStartReason,
        firstFrameBudget = firstFrameBudget
    )
}

fun buildRecoveryTraceSnapshot(
    state: SessionState,
    traceEvents: List<SessionTraceEvent>,
    maxEvents: Int = 8
): RecoveryTraceSnapshot {
    val recoveryEvents = traceEvents
        .filter { it.name in RECOVERY_TRACE_EVENT_NAMES }
        .takeLast(maxEvents)
    val lastRecoveryStart = traceEvents.lastOrNull { it.name == "preview.recovery.started" }
    val recoveredFirstFrame = lastRecoveryStart?.let { start ->
        traceEvents.lastOrNull { it.name == "preview.first.frame" && it.sequence > start.sequence }
    }
    val lastFailureReason = state.lastError
        ?: traceEvents.lastOrNull { it.name in RECOVERY_FAILURE_EVENT_NAMES }?.detail

    return RecoveryTraceSnapshot(
        isRecoveryActive = state.previewStatus == PreviewStatus.RECOVERING,
        recoveryCount = state.previewMetrics.recoveryCount,
        lastRecoveryReason = lastRecoveryStart?.detail,
        lastFailureReason = lastFailureReason,
        recoveredFirstFrameLatencyMillis = recoveredFirstFrame
            ?.detail
            ?.removeSuffix("ms")
            ?.toLongOrNull(),
        events = recoveryEvents
    )
}

fun buildSessionDebugDump(
    state: SessionState,
    traceEvents: List<SessionTraceEvent>,
    recentEventLimit: Int = 12,
    recoveryEventLimit: Int = 8
): SessionDebugDump {
    return SessionDebugDump(
        lifecycle = state.lifecycle,
        activeMode = state.activeMode,
        previewStatus = state.previewStatus,
        captureStatus = state.captureStatus,
        recordingStatus = state.recordingStatus,
        lastAction = state.lastAction,
        lastError = state.lastError,
        perfSnapshot = state.toPerfSnapshot(),
        recoveryTrace = buildRecoveryTraceSnapshot(
            state = state,
            traceEvents = traceEvents,
            maxEvents = recoveryEventLimit
        ),
        recentEvents = traceEvents.takeLast(recentEventLimit)
    )
}

private val RECOVERY_TRACE_EVENT_NAMES = setOf(
    "preview.binding.started",
    "preview.host.recovery.requested",
    "preview.recovery.requested",
    "preview.recovery.started",
    "preview.recovery.failed",
    "preview.first.frame",
    "preview.runtime.issue",
    "preview.surface.lost",
    "preview.error",
    "preview.blocked",
    "preview.host.detached",
    "preview.stopped"
)

private val RECOVERY_FAILURE_EVENT_NAMES = setOf(
    "preview.runtime.issue",
    "preview.recovery.failed",
    "preview.surface.lost",
    "preview.error",
    "preview.blocked"
)

private data class FirstFrameBudget(
    val warnThresholdMillis: Long,
    val failThresholdMillis: Long
)

internal fun assessFirstFrameBudget(
    lastStartReason: String?,
    latencyMillis: Long?
): FirstFrameBudgetSnapshot {
    val startCategory = classifyPreviewStartCategory(lastStartReason)
    val budget = firstFrameBudgetFor(startCategory)
    val status = when {
        latencyMillis == null -> PerfBudgetStatus.UNAVAILABLE
        latencyMillis <= budget.warnThresholdMillis -> PerfBudgetStatus.WITHIN_BUDGET
        latencyMillis <= budget.failThresholdMillis -> PerfBudgetStatus.WARNING
        else -> PerfBudgetStatus.EXCEEDED
    }
    return FirstFrameBudgetSnapshot(
        startCategory = startCategory,
        status = status,
        warnThresholdMillis = budget.warnThresholdMillis,
        failThresholdMillis = budget.failThresholdMillis
    )
}

fun classifyPreviewStartCategory(lastStartReason: String?): PreviewStartCategory {
    val reason = lastStartReason?.lowercase() ?: return PreviewStartCategory.UNKNOWN
    return when {
        "recover after preview host detached" in reason -> PreviewStartCategory.FOREGROUND_RESUME
        reason.startsWith("recover after") -> PreviewStartCategory.RECOVERY
        reason == "session boot" ||
            reason == "preview host attached" ||
            reason == "camera permission granted" -> PreviewStartCategory.COLD_START

        reason == "preview host reattached" ||
            reason.startsWith("mode switched to ") ||
            reason.startsWith("lens switched to ") ||
            reason.startsWith("still quality updated to ") ||
            reason.startsWith("still resolution updated to ") ||
            reason == "device capabilities refreshed" ||
            reason == "session settings updated" -> PreviewStartCategory.RECONFIGURE

        else -> PreviewStartCategory.UNKNOWN
    }
}

fun previewStartTimeoutMillis(lastStartReason: String?): Long {
    val budget = firstFrameBudgetFor(classifyPreviewStartCategory(lastStartReason))
    return budget.failThresholdMillis + FIRST_FRAME_TIMEOUT_GRACE_MILLIS
}

fun previewStartWatchdogMillis(lastStartReason: String?): Long {
    return when (classifyPreviewStartCategory(lastStartReason)) {
        PreviewStartCategory.COLD_START -> 1200L
        PreviewStartCategory.FOREGROUND_RESUME -> 1200L
        PreviewStartCategory.RECOVERY -> 1500L
        PreviewStartCategory.RECONFIGURE -> 1000L
        PreviewStartCategory.UNKNOWN -> 1200L
    }
}

private fun firstFrameBudgetFor(
    startCategory: PreviewStartCategory
): FirstFrameBudget {
    return when (startCategory) {
        PreviewStartCategory.COLD_START -> FirstFrameBudget(
            warnThresholdMillis = 120,
            failThresholdMillis = 220
        )

        PreviewStartCategory.FOREGROUND_RESUME -> FirstFrameBudget(
            warnThresholdMillis = 150,
            failThresholdMillis = 260
        )

        PreviewStartCategory.RECOVERY -> FirstFrameBudget(
            warnThresholdMillis = 180,
            failThresholdMillis = 320
        )

        PreviewStartCategory.RECONFIGURE -> FirstFrameBudget(
            warnThresholdMillis = 140,
            failThresholdMillis = 240
        )

        PreviewStartCategory.UNKNOWN -> FirstFrameBudget(
            warnThresholdMillis = 160,
            failThresholdMillis = 280
        )
    }
}

private const val FIRST_FRAME_TIMEOUT_GRACE_MILLIS = 80L
