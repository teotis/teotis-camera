package com.opencamera.core.session

import java.util.concurrent.atomic.AtomicInteger

enum class TraceEventDomain(val label: String, val prefix: String) {
    SESSION("会话", "session."),
    PREVIEW("预览", "preview."),
    CAPTURE("拍照", "capture."),
    RECORDING("录制", "recording."),
    MODE("模式", "mode."),
    LENS("镜头", "lens."),
    ZOOM("变焦", "zoom."),
    SETTINGS("设置", "settings."),
    PHOTO_FEATURE("拍照特性", "photo."),
    ORIENTATION("方向", "orientation."),
    DIAGNOSTICS("诊断", ""),
    SYSTEM("系统", "");

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
