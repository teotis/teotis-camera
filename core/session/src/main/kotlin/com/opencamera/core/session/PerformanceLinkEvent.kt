package com.opencamera.core.session

import kotlin.math.max

enum class LinkEventStatus(val label: String) {
    STARTED("started"),
    COMPLETED("completed"),
    DEGRADED("degraded"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    UNAVAILABLE("unavailable")
}

data class PerformanceLinkEvent(
    val flow: String,
    val stage: String,
    val status: LinkEventStatus,
    val correlationId: String,
    val startElapsedMillis: Long,
    val endElapsedMillis: Long?,
    val durationMillis: Long?,
    val detail: String?,
    val source: String
)

data class PerformanceSpanSnapshot(
    val flow: String,
    val stage: String,
    val correlationId: String,
    val startElapsedMillis: Long,
    val endElapsedMillis: Long?,
    val status: LinkEventStatus,
    val detail: String?,
    val source: String
) {
    val durationMillis: Long?
        get() = endElapsedMillis?.let { end ->
            max(0L, end - startElapsedMillis)
        }

    fun toEvent(): PerformanceLinkEvent = PerformanceLinkEvent(
        flow = flow,
        stage = stage,
        status = status,
        correlationId = correlationId,
        startElapsedMillis = startElapsedMillis,
        endElapsedMillis = endElapsedMillis,
        durationMillis = durationMillis,
        detail = detail,
        source = source
    )
}

interface PerformanceLinkRecorder {
    fun startSpan(
        flow: String,
        stage: String,
        correlationId: String,
        detail: String? = null,
        source: String
    ): PerformanceSpanSnapshot

    fun completeSpan(
        snapshot: PerformanceSpanSnapshot,
        status: LinkEventStatus = LinkEventStatus.COMPLETED,
        detail: String? = null
    ): PerformanceLinkEvent

    fun recordEvent(event: PerformanceLinkEvent)

    fun snapshot(): List<PerformanceLinkEvent>
}

internal typealias ElapsedTimeSource = () -> Long

internal class InMemoryPerformanceLinkRecorder(
    private val maxEvents: Int = 100,
    private val elapsedTimeSource: ElapsedTimeSource = { System.nanoTime() / 1_000_000L }
) : PerformanceLinkRecorder {
    private val events = mutableListOf<PerformanceLinkEvent>()

    override fun startSpan(
        flow: String,
        stage: String,
        correlationId: String,
        detail: String?,
        source: String
    ): PerformanceSpanSnapshot {
        return PerformanceSpanSnapshot(
            flow = flow,
            stage = stage,
            correlationId = correlationId,
            startElapsedMillis = elapsedTimeSource(),
            endElapsedMillis = null,
            status = LinkEventStatus.STARTED,
            detail = detail,
            source = source
        )
    }

    override fun completeSpan(
        snapshot: PerformanceSpanSnapshot,
        status: LinkEventStatus,
        detail: String?
    ): PerformanceLinkEvent {
        val resolvedEnd = when (status) {
            LinkEventStatus.STARTED -> elapsedTimeSource()
            LinkEventStatus.UNAVAILABLE -> null
            else -> snapshot.endElapsedMillis ?: elapsedTimeSource()
        }
        val event = snapshot.copy(
            endElapsedMillis = resolvedEnd,
            status = status,
            detail = detail ?: snapshot.detail
        ).toEvent()
        recordEvent(event)
        return event
    }

    override fun recordEvent(event: PerformanceLinkEvent) {
        synchronized(events) {
            events += event
            if (events.size > maxEvents) {
                events.removeAt(0)
            }
        }
    }

    override fun snapshot(): List<PerformanceLinkEvent> {
        return synchronized(events) { events.toList() }
    }
}

fun PerformanceLinkEvent.toLinkLogLine(): String {
    val parts = mutableListOf(
        "link",
        "flow=${escapeLogValue(flow)}",
        "stage=${escapeLogValue(stage)}",
        "status=${status.label}",
        "id=${escapeLogValue(correlationId)}",
        "startElapsed=$startElapsedMillis"
    )
    if (endElapsedMillis != null) {
        parts += "endElapsed=$endElapsedMillis"
    }
    if (durationMillis != null) {
        parts += "duration=${durationMillis}ms"
    }
    if (!detail.isNullOrBlank()) {
        parts += "detail=${escapeLogValue(detail)}"
    }
    parts += "source=${escapeLogValue(source)}"
    return parts.joinToString(" ")
}

private fun escapeLogValue(value: String): String {
    return value.replace(" ", "_").replace("=", "_")
}
