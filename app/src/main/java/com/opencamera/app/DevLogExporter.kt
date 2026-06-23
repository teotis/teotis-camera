package com.opencamera.app

import android.content.Context
import com.opencamera.core.session.PerformanceLinkEvent
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.SessionTraceEvent
import com.opencamera.core.session.buildSessionDebugDump
import java.io.File

internal class DevLogExporter(private val context: Context) {

    fun export(
        content: String,
        type: DevLogTab = DevLogTab.ALL,
        nowMillis: Long = System.currentTimeMillis()
    ): File {
        val dir = logDirectory()
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "opencamera-debug-$nowMillis.log")
        file.writeText("# type: ${type.name}\n$content", Charsets.UTF_8)
        runCatching { pruneToCap(dir) }
        return file
    }

    fun exportVendorProbe(
        content: String,
        nowMillis: Long = System.currentTimeMillis()
    ): File {
        val dir = logDirectory()
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "opencamera-vendor-probe-$nowMillis.log")
        file.writeText(content, Charsets.UTF_8)
        runCatching { pruneToCap(dir) }
        return file
    }

    fun storageSummary(): StorageSummary {
        val dir = logDirectory()
        if (!dir.exists()) return StorageSummary(0L, MAX_STORAGE_BYTES)
        val files = dir.listFiles() ?: return StorageSummary(0L, MAX_STORAGE_BYTES)
        val totalBytes = files.filter { it.isFile && it.extension == "log" }.sumOf { it.length() }
        return StorageSummary(totalBytes, MAX_STORAGE_BYTES)
    }

    fun cleanupByType(type: DevLogTab): Int {
        val dir = logDirectory()
        if (!dir.exists()) return 0
        val files = dir.listFiles()?.filter { it.isFile && it.extension == "log" } ?: return 0
        var deleted = 0
        for (file in files) {
            val header = readTypeHeader(file)
            if (matchesType(header, type)) {
                if (file.delete()) deleted++
            }
        }
        return deleted
    }

    fun cleanupAll(): Int {
        val dir = logDirectory()
        if (!dir.exists()) return 0
        val files = dir.listFiles()?.filter { it.isFile && it.extension == "log" } ?: return 0
        var deleted = 0
        for (file in files) {
            if (file.delete()) deleted++
        }
        return deleted
    }

    private fun logDirectory(): File {
        return context.getExternalFilesDir("debug-logs")
            ?: File(context.filesDir, "debug-logs")
    }

    private fun pruneToCap(dir: File) {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.extension == "log" }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_STORAGE_BYTES) return
        for (file in files.reversed()) {
            if (total <= MAX_STORAGE_BYTES) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun readTypeHeader(file: File): String? {
        return runCatching {
            file.bufferedReader().use { reader ->
                val line = reader.readLine() ?: return@use null
                if (line.startsWith("# type: ")) line.removePrefix("# type: ").trim()
                else null
            }
        }.getOrNull()
    }

    private fun matchesType(header: String?, type: DevLogTab): Boolean {
        if (header == null) return type == DevLogTab.ALL
        return header == type.name
    }

    companion object {
        const val MAX_STORAGE_BYTES = 20L * 1024 * 1024 // 20MB
    }
}

internal data class StorageSummary(
    val usedBytes: Long,
    val capacityBytes: Long
) {
    val usedDisplay: String get() = formatBytes(usedBytes)
    val capacityDisplay: String get() = formatBytes(capacityBytes)
    val usageRatio: Float get() = if (capacityBytes > 0) usedBytes.toFloat() / capacityBytes else 0f

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }
}

internal fun buildDevLogExportContent(
    state: SessionState,
    traceEvents: List<SessionTraceEvent>,
    linkEvents: List<PerformanceLinkEvent>,
    resourceDiagnostics: com.opencamera.core.media.ResourceDiagnosticsSnapshot?,
    deviceProbeSummary: String?,
    pipelineNotes: List<String>,
    clearCutoffs: DevLogClearCutoffs
): String {
    val keyEvents = traceEvents.filter {
        it.sequence > clearCutoffs.sequenceFor(DevLogTab.KEY) && it.name in KEY_EVENT_NAMES
    }
    val coreEvents = traceEvents.filter {
        it.sequence > clearCutoffs.sequenceFor(DevLogTab.CORE) && it.name in CORE_EVENT_NAMES
    }
    val errorEvents = traceEvents.filter {
        it.sequence > clearCutoffs.sequenceFor(DevLogTab.ERROR) && isErrorEvent(it.name)
    }
    val allEvents = traceEvents.filter { it.sequence > clearCutoffs.sequenceFor(DevLogTab.ALL) }
    val visibleLinkEvents = linkEvents.drop(clearCutoffs.linkEventCount.coerceAtMost(linkEvents.size))

    fun formatEvents(events: List<SessionTraceEvent>): String {
        return events.joinToString("\n") { event ->
            val timeStr = formatTimestamp(event.timestampMillis)
            "[$timeStr] [${event.domain.label}] ${event.sequence}. ${event.name} -> ${event.detail}"
        }
    }

    fun formatLinkEvents(events: List<PerformanceLinkEvent>): String {
        return events.joinToString("\n") { event ->
            val parts = mutableListOf(
                "[Link] flow=${escapeLinkValue(event.flow)}",
                "stage=${escapeLinkValue(event.stage)}",
                "status=${event.status.label}"
            )
            if (event.correlationId.isNotBlank()) {
                parts += "id=${escapeLinkValue(event.correlationId)}"
            }
            if (event.startElapsedMillis >= 0) {
                parts += "start=${event.startElapsedMillis}"
            }
            if (event.endElapsedMillis != null) {
                parts += "end=${event.endElapsedMillis}"
            }
            if (event.durationMillis != null) {
                parts += "duration=${event.durationMillis}ms"
            }
            if (!event.detail.isNullOrBlank()) {
                val detail = event.detail!!
                parts += "detail=${escapeLinkValue(detail)}"
            }
            parts += "source=${escapeLinkValue(event.source)}"
            parts.joinToString(" ")
        }
    }

    val debugDump = buildSessionDebugDump(state, allEvents, resourceDiagnostics = resourceDiagnostics)
    val perf = debugDump.perfSnapshot
    val recovery = debugDump.recoveryTrace
    val resolvedProbeSummary = deviceProbeSummary
        ?: computeDeviceProbeSummary(state.activeDeviceCapabilities)

    val coreSummary = buildString {
        appendLine("DebugDump: ${debugDump.lifecycle} | ${debugDump.activeMode.name} | preview=${debugDump.previewStatus} | capture=${debugDump.captureStatus} | recording=${debugDump.recordingStatus}")
        appendLine("PerfSnapshot: last=${perf.lastFirstFrameLatencyMillis ?: "--"} ms, best=${perf.bestFirstFrameLatencyMillis ?: "--"} ms, worst=${perf.worstFirstFrameLatencyMillis ?: "--"} ms, binds=${perf.bindCount}, recoveries=${perf.recoveryCount}")
        appendLine("RecoveryTrace: ${if (recovery.isRecoveryActive) "active" else "idle"}, last=${recovery.lastRecoveryReason ?: "--"}, recoveredFrame=${recovery.recoveredFirstFrameLatencyMillis ?: "--"} ms, failure=${recovery.lastFailureReason ?: "--"}")
        debugDump.resourceDiagnostics?.let { res ->
            appendLine("Resource: thermal=${res.thermalState.tagValue} | class=${res.performanceClass.tagValue} | jobs=${res.activeAlgorithmJobs}/${res.maxConcurrentAlgorithmJobs}")
        }
    }

    return buildString {
        appendLine("=== KEY EVENTS ===")
        appendLine(formatEvents(keyEvents))
        appendLine("=== CORE EVENTS ===")
        appendLine(formatEvents(coreEvents))
        appendLine("=== LINK EVENTS ===")
        appendLine(formatLinkEvents(visibleLinkEvents))
        appendLine("=== SHOT PIPELINE ===")
        postProcessTimingBreakdown(pipelineNotes)?.let { breakdown ->
            appendLine(breakdown)
        }
        pipelineNotes.forEach { note -> appendLine(note) }
        appendLine("=== ERROR EVENTS ===")
        appendLine(formatEvents(errorEvents))
        appendLine("=== ALL EVENTS ===")
        appendLine(formatEvents(allEvents))
        debugDump.resourceDiagnostics?.let { res ->
            appendLine("=== RESOURCE DIAGNOSTICS ===")
            res.pipelineNotes.forEach { note -> appendLine(note) }
        }
        if (!resolvedProbeSummary.isNullOrBlank()) {
            appendLine("=== DEVICE PROBE ===")
            appendLine(resolvedProbeSummary)
        }
        appendLine("=== CORE SUMMARY ===")
        append(coreSummary)
    }
}
