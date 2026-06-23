package com.opencamera.core.session

import java.util.concurrent.atomic.AtomicInteger

enum class TraceEventDomain(val label: String, val prefix: String) {
    SESSION("Session", "session."),
    PREVIEW("Preview", "preview."),
    CAPTURE("Capture", "capture."),
    RECORDING("Recording", "recording."),
    MODE("Mode", "mode."),
    LENS("Lens", "lens."),
    ZOOM("Zoom", "zoom."),
    SETTINGS("Settings", "settings."),
    PHOTO_FEATURE("Photo Feature", "photo."),
    ORIENTATION("Orientation", "orientation."),
    DIAGNOSTICS("Diagnostics", ""),
    SYSTEM("System", "");

    companion object {
        private val PREFIX_MAP = listOf(
            SESSION to "session.",
            PREVIEW to "preview.",
            CAPTURE to "capture.",
            RECORDING to "recording.",
            MODE to "mode.",
            LENS to "lens.",
            ZOOM to "zoom.",
            SETTINGS to "settings.",
            PHOTO_FEATURE to "photo.",
            ORIENTATION to "orientation.",
        )

        private val DIAGNOSTICS_PREFIXES = listOf("intent.", "device.", "permissions.", "resource:")
        private val SETTINGS_EXTRA_PREFIXES = listOf("still-quality.", "still-resolution.", "preview-ratio.")

        fun domainFor(eventName: String): TraceEventDomain {
            for ((domain, prefix) in PREFIX_MAP) {
                if (eventName.startsWith(prefix)) return domain
            }
            for (prefix in SETTINGS_EXTRA_PREFIXES) {
                if (eventName.startsWith(prefix)) return SETTINGS
            }
            for (prefix in DIAGNOSTICS_PREFIXES) {
                if (eventName.startsWith(prefix)) return DIAGNOSTICS
            }
            return SYSTEM
        }
    }
}

data class SessionTraceEvent(
    val sequence: Int,
    val name: String,
    val detail: String,
    val timestampMillis: Long,
    val domain: TraceEventDomain = TraceEventDomain.domainFor(name)
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

    fun snapshotForDisplay(window: Int = 1000): List<SessionTraceEvent> =
        snapshot().takeLast(window)
}

@Suppress("DEPRECATION")
class InMemorySessionTrace(
    @Deprecated("Use memoryRetention instead", ReplaceWith("memoryRetention(maxEvents)"))
    private val maxEvents: Int = 1000,
    private val memoryRetention: Int = maxEvents,
    private val displayWindow: Int = 1000,
) : SessionTrace {
    private val sequence = AtomicInteger(0)
    private val events = ArrayDeque<SessionTraceEvent>()

    override fun record(name: String, detail: String) {
        synchronized(events) {
            events += SessionTraceEvent(
                sequence = sequence.incrementAndGet(),
                name = name,
                detail = detail,
                timestampMillis = System.currentTimeMillis()
            )
            if (events.size > memoryRetention) {
                events.removeFirst()
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
            if (events.size > memoryRetention) {
                events.removeFirst()
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
            if (events.size > memoryRetention) {
                events.removeFirst()
            }
        }
    }

    override fun snapshot(): List<SessionTraceEvent> {
        return synchronized(events) { events.toList() }
    }

    override fun snapshotForDisplay(window: Int): List<SessionTraceEvent> {
        val effectiveWindow = window.coerceAtMost(displayWindow)
        return synchronized(events) {
            val start = (events.size - effectiveWindow).coerceAtLeast(0)
            events.toList().subList(start, events.size)
        }
    }
}
