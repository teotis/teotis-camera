package com.opencamera.core.session

import java.util.concurrent.atomic.AtomicInteger

data class SessionTraceEvent(
    val sequence: Int,
    val name: String,
    val detail: String,
    val timestampMillis: Long
)

data class TraceHandle(
    val name: String,
    val startNanos: Long,
    val sequence: Int
)

interface SessionTrace {
    fun record(name: String, detail: String)

    fun begin(name: String): TraceHandle

    fun end(handle: TraceHandle, detail: String = "")

    fun snapshot(): List<SessionTraceEvent>
}

class InMemorySessionTrace(
    private val maxEvents: Int = 200
) : SessionTrace {
    private val sequence = AtomicInteger(0)
    private val events = mutableListOf<SessionTraceEvent>()

    override fun record(name: String, detail: String) {
        synchronized(events) {
            events += SessionTraceEvent(
                sequence = sequence.incrementAndGet(),
                name = name,
                detail = detail,
                timestampMillis = System.currentTimeMillis()
            )
            if (events.size > maxEvents) {
                events.removeAt(0)
            }
        }
    }

    override fun begin(name: String): TraceHandle {
        val seq = sequence.incrementAndGet()
        synchronized(events) {
            events += SessionTraceEvent(
                sequence = seq,
                name = "$name.started",
                detail = "",
                timestampMillis = System.currentTimeMillis()
            )
            if (events.size > maxEvents) {
                events.removeAt(0)
            }
        }
        return TraceHandle(name, System.nanoTime(), seq)
    }

    override fun end(handle: TraceHandle, detail: String) {
        val elapsedMs = (System.nanoTime() - handle.startNanos) / 1_000_000L
        val detailWithTiming = if (detail.isEmpty()) {
            "${elapsedMs}ms"
        } else {
            "$detail,${elapsedMs}ms"
        }
        synchronized(events) {
            events += SessionTraceEvent(
                sequence = sequence.incrementAndGet(),
                name = "${handle.name}.completed",
                detail = detailWithTiming,
                timestampMillis = System.currentTimeMillis()
            )
            if (events.size > maxEvents) {
                events.removeAt(0)
            }
        }
    }

    override fun snapshot(): List<SessionTraceEvent> {
        return synchronized(events) { events.toList() }
    }
}
