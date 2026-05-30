package com.opencamera.core.session

import java.util.concurrent.atomic.AtomicInteger

enum class DevLogTag(val displayName: String) {
    LIFECYCLE("生命周期"),
    MODE("模式"),
    CAPTURE("拍摄"),
    RECORDING("录制"),
    PREVIEW("预览"),
    PERFORMANCE("性能"),
    ERROR("错误"),
    RECOVERY("恢复"),
    LENS("镜头"),
    PERMISSION("权限"),
    SETTINGS("设置"),
    TIMING("耗时"),
    INTENT("意图"),
    RESOURCE("资源"),
    ZOOM("变焦");
}

data class SessionTraceEvent(
    val sequence: Int,
    val name: String,
    val detail: String,
    val timestampMillis: Long,
    val tags: Set<DevLogTag> = emptySet()
)

interface SessionTrace {
    fun record(name: String, detail: String, tags: Set<DevLogTag> = emptySet())

    fun snapshot(): List<SessionTraceEvent>
}

class InMemorySessionTrace(
    private val maxEvents: Int = 200
) : SessionTrace {
    private val sequence = AtomicInteger(0)
    private val events = mutableListOf<SessionTraceEvent>()

    override fun record(name: String, detail: String, tags: Set<DevLogTag>) {
        synchronized(events) {
            events += SessionTraceEvent(
                sequence = sequence.incrementAndGet(),
                name = name,
                detail = detail,
                timestampMillis = System.currentTimeMillis(),
                tags = tags
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
