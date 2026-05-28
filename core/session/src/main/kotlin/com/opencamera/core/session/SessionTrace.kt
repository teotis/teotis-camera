package com.opencamera.core.session

import java.util.concurrent.atomic.AtomicInteger

data class SessionTraceEvent(
    val sequence: Int,
    val name: String,
    val detail: String,
    val timestampMillis: Long
)

interface SessionTrace {
    fun record(name: String, detail: String)

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

    override fun snapshot(): List<SessionTraceEvent> {
        return synchronized(events) { events.toList() }
    }
}
