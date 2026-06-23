package com.opencamera.app

import com.opencamera.app.i18n.AppTextResolver
import com.opencamera.core.media.ResourceDiagnosticsSnapshot
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.SessionTraceEvent
import com.opencamera.core.session.TraceEventDomain
import com.opencamera.core.session.buildSessionDebugDump

internal fun sessionDiagnosticsText(
    state: SessionState,
    traceEvents: List<SessionTraceEvent>,
    resourceDiagnostics: ResourceDiagnosticsSnapshot? = null
): String {
    val debugDump = buildSessionDebugDump(state, traceEvents, resourceDiagnostics = resourceDiagnostics)
    val perf = debugDump.perfSnapshot
    val recovery = debugDump.recoveryTrace
    return buildString {
        appendLine(
            "DebugDump: ${debugDump.lifecycle} | ${debugDump.activeMode.name} | " +
                "preview=${debugDump.previewStatus} | capture=${debugDump.captureStatus} | " +
                "recording=${debugDump.recordingStatus}"
        )
        appendLine(
            "PerfSnapshot: last=${perf.lastFirstFrameLatencyMillis ?: "--"} ms, " +
                "best=${perf.bestFirstFrameLatencyMillis ?: "--"} ms, " +
                "worst=${perf.worstFirstFrameLatencyMillis ?: "--"} ms, " +
                "binds=${perf.bindCount}, recoveries=${perf.recoveryCount}, " +
                "budget=${perf.firstFrameBudget.status.label} " +
                "(${perf.firstFrameBudget.startCategory.label}, " +
                "warn=${perf.firstFrameBudget.warnThresholdMillis} ms, " +
                "fail=${perf.firstFrameBudget.failThresholdMillis} ms)"
        )
        appendLine(
            "RecoveryTrace: ${if (recovery.isRecoveryActive) "active" else "idle"}, " +
                "last=${recovery.lastRecoveryReason ?: "--"}, " +
                "recoveredFrame=${recovery.recoveredFirstFrameLatencyMillis ?: "--"} ms, " +
                "failure=${recovery.lastFailureReason ?: "--"}"
        )
        appendLine("Action: ${debugDump.lastAction}")
        debugDump.resourceDiagnostics?.let { res ->
            val degradations = res.featureDegradations.entries.joinToString(", ") { "${it.key}=${it.value}" }
            appendLine(
                "Resource: thermal=${res.thermalState.tagValue} | class=${res.performanceClass.tagValue} | " +
                    "memory=${res.memoryBudgetBytes / 1024 / 1024}MB | " +
                    "jobs=${res.activeAlgorithmJobs}/${res.maxConcurrentAlgorithmJobs}" +
                    if (degradations.isNotEmpty()) " | degradations=$degradations" else ""
            )
        }
        if (debugDump.lastError != null) {
            appendLine("Error: ${debugDump.lastError}")
        }
        append(
            debugDump.recentEvents.joinToString(separator = "\n") { event ->
                "${event.sequence}. ${event.name} -> ${event.detail}"
            }
        )
    }
}

// -- Dev Log --

internal val KEY_EVENT_NAMES = setOf(
    "session.created", "session.booted", "session.stopped",
    "mode.switched", "lens.switched", "zoom.updated",
    "preview.first.frame", "preview.host.attached", "preview.host.detached",
    "capture.photo", "capture.saved", "capture.timing",
    "capture.shutter.to.device",
    "recording.started", "recording.saved", "recording.timing",
    "recording.startup.latency",
    "mode.switch.completed", "lens.switch.completed",
    "permissions.updated", "device.capabilities.updated", "settings.updated"
)

internal val CORE_EVENT_NAMES = setOf(
    "preview.binding.started", "preview.recovery.started", "preview.recovery.requested",
    "preview.stopped", "preview.snapshot.updated", "preview.snapshot.ignored",
    "capture.countdown.started", "capture.countdown.tick", "capture.countdown.cancelled",
    "capture.saving",
    "capture.feedback.snapshot.requested", "capture.feedback.snapshot.updated",
    "capture.feedback.snapshot.skipped",
    "recording.requested",
    "shot.plan.failed",
    "mode.switch.started", "lens.switch.started",
    "mode.signal", "mode.event", "mode.hint",
    "intent.received"
)

internal val ERROR_EVENT_NAMES = setOf(
    "preview.error", "preview.surface.lost", "preview.runtime.issue",
    "preview.recovery.failed", "preview.blocked",
    "capture.failed", "recording.failed", "recording.stop.blocked",
    "mode.switch.blocked", "mode.intent.blocked",
    "lens.switch.blocked", "zoom.switch.blocked",
    "still-quality.blocked", "still-resolution.blocked", "settings.update.blocked"
)

internal fun isErrorEvent(name: String): Boolean {
    return name in ERROR_EVENT_NAMES
}

internal fun escapeLinkValue(value: String): String {
    return value.replace(" ", "_").replace("=", "_")
}

internal fun formatTimestamp(timestampMillis: Long): String {
    if (timestampMillis <= 0) return "??:??:??"
    val seconds = (timestampMillis / 1000) % 60
    val minutes = (timestampMillis / 60_000) % 60
    val hours = (timestampMillis / 3_600_000) % 24
    val millis = timestampMillis % 1000
    return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
}

internal fun devLogRenderModel(
    state: SessionState,
    traceEvents: List<SessionTraceEvent>,
    isDebugBuild: Boolean,
    selectedTab: DevLogTab,
    text: AppTextResolver,
    resourceDiagnostics: ResourceDiagnosticsSnapshot? = null,
    storageSummary: StorageSummary? = null,
    selectedDomain: TraceEventDomain? = null,
    linkEvents: List<com.opencamera.core.session.PerformanceLinkEvent> = emptyList(),
    deviceProbeSummary: String? = null,
    latestPipelineNotes: List<String> = emptyList(),
    clearCutoffs: DevLogClearCutoffs = DevLogClearCutoffs()
): DevLogRenderModel {
    if (!isDebugBuild) {
        return DevLogRenderModel(
            isAvailable = false,
            selectedTab = selectedTab,
            title = text.get(R.string.button_dev_entry),
            summaryText = "",
            visibleEvents = emptyList()
        )
    }

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

    val domainTabs = TraceEventDomain.entries.map { domain ->
        DomainTabCount(domain = domain, count = allEvents.count { it.domain == domain })
    }.filter { it.count > 0 }

    fun escapeLinkValue(value: String): String {
        return value.replace(" ", "_").replace("=", "_")
    }

    fun formatTimestamp(timestampMillis: Long): String {
        if (timestampMillis <= 0) return "??:??:??"
        val seconds = (timestampMillis / 1000) % 60
        val minutes = (timestampMillis / 60_000) % 60
        val hours = (timestampMillis / 3_600_000) % 24
        val millis = timestampMillis % 1000
        return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    }

    fun timingTotalMillis(event: SessionTraceEvent): Long? {
        if (!event.name.endsWith(".timing")) return null
        val marker = "total="
        val start = event.detail.indexOf(marker)
        if (start < 0) return null
        val valueStart = start + marker.length
        val valueEnd = event.detail.indexOf("ms", valueStart).takeIf { it >= 0 } ?: event.detail.length
        return event.detail.substring(valueStart, valueEnd).trim().toLongOrNull()
    }

    val debugDump = buildSessionDebugDump(state, allEvents, resourceDiagnostics = resourceDiagnostics)
    val resolvedProbeSummary = deviceProbeSummary
        ?: computeDeviceProbeSummary(state.activeDeviceCapabilities)

    fun deviceProbeBlock(): String {
        return resolvedProbeSummary
            .takeIf { it.isNotBlank() }
            ?.let { "=== DEVICE PROBE ===\n$it" }
            .orEmpty()
    }

    val domainFiltered = if (selectedDomain != null) {
        when (selectedTab) {
            DevLogTab.KEY -> keyEvents.filter { it.domain == selectedDomain }
            DevLogTab.CORE -> coreEvents.filter { it.domain == selectedDomain }
            DevLogTab.ERROR -> errorEvents.filter { it.domain == selectedDomain }
            DevLogTab.ALL -> allEvents.filter { it.domain == selectedDomain }
        }
    } else {
        null
    }

    val visibleEvents: List<DevLogEventItem> = buildList {
        val baseEvents = domainFiltered ?: when (selectedTab) {
            DevLogTab.KEY -> keyEvents
            DevLogTab.CORE -> coreEvents
            DevLogTab.ERROR -> errorEvents
            DevLogTab.ALL -> allEvents
        }
        baseEvents.forEach { event ->
            val timeStr = formatTimestamp(event.timestampMillis)
            add(TraceEventItem(event.sequence, "[$timeStr] [${event.domain.label}] ${event.sequence}. ${event.name} -> ${event.detail}"))
        }
        if (selectedTab == DevLogTab.CORE && visibleLinkEvents.isNotEmpty()) {
            add(SectionHeaderItem(text.get(R.string.dev_link_timing_header)))
            visibleLinkEvents.forEach { event ->
                val parts = mutableListOf(
                    "[Link] flow=${escapeLinkValue(event.flow)}",
                    "stage=${escapeLinkValue(event.stage)}",
                    "status=${event.status.label}"
                )
                if (event.correlationId.isNotBlank()) parts += "id=${escapeLinkValue(event.correlationId)}"
                if (event.startElapsedMillis >= 0) parts += "start=${event.startElapsedMillis}"
                if (event.endElapsedMillis != null) parts += "end=${event.endElapsedMillis}"
                if (event.durationMillis != null) parts += "duration=${event.durationMillis}ms"
                if (!event.detail.isNullOrBlank()) parts += "detail=${escapeLinkValue(event.detail!!)}"
                parts += "source=${escapeLinkValue(event.source)}"
                add(LinkEventItem(parts.joinToString(" ")))
            }
            val probeBlock = deviceProbeBlock()
            if (probeBlock.isNotBlank()) {
                add(SectionHeaderItem(probeBlock))
            }
        } else if (selectedTab == DevLogTab.CORE && deviceProbeBlock().isNotBlank()) {
            add(SectionHeaderItem(deviceProbeBlock()))
        } else if (selectedTab == DevLogTab.ALL && latestPipelineNotes.isNotEmpty()) {
            add(SectionHeaderItem("--- Pipeline Notes ---"))
            latestPipelineNotes.forEach { note -> add(SectionHeaderItem(note)) }
        }
    }

    val lastTiming = traceEvents.lastOrNull { it.name.endsWith(".timing") }
    val slowestTiming = traceEvents.maxByOrNull { timingTotalMillis(it) ?: Long.MIN_VALUE }
        ?.takeIf { timingTotalMillis(it) != null }
    val lastIssue = errorEvents.lastOrNull()
    val summaryText = buildString {
        append(text.get(R.string.dev_status_prefix)); append(debugDump.previewStatus); append(" | ")
        append(text.get(R.string.dev_mode_prefix)); append(debugDump.activeMode.name); append(" | ")
        append(text.get(R.string.dev_capture_prefix)); append(debugDump.captureStatus); append(" | ")
        append(text.get(R.string.dev_recording_prefix)); append(debugDump.recordingStatus)
        if (lastTiming != null) {
            append(text.get(R.string.dev_last_timing_prefix)); append(lastTiming.detail)
        }
        if (slowestTiming != null && slowestTiming != lastTiming) {
            append(text.get(R.string.dev_slowest_timing_prefix)); append(slowestTiming.detail)
        }
        if (lastIssue != null) {
            append(text.get(R.string.dev_last_issue_prefix)); append(lastIssue.name)
        }
    }

    val domainFilteredCount = domainFiltered?.size
    return DevLogRenderModel(
        isAvailable = true,
        selectedTab = selectedTab,
        title = when (selectedTab) {
            DevLogTab.KEY -> text.devLogTitleKey(domainFilteredCount ?: keyEvents.size)
            DevLogTab.CORE -> text.devLogTitleCore(domainFilteredCount ?: coreEvents.size)
            DevLogTab.ERROR -> text.devLogTitleError(domainFilteredCount ?: errorEvents.size)
            DevLogTab.ALL -> text.devLogTitleAll(domainFilteredCount ?: allEvents.size)
        },
        summaryText = summaryText,
        visibleEvents = visibleEvents,
        storageUsedDisplay = storageSummary?.usedDisplay ?: "",
        storageCapacityDisplay = storageSummary?.capacityDisplay ?: "",
        storageUsageRatio = storageSummary?.usageRatio ?: 0f,
        canCleanup = (storageSummary?.usedBytes ?: 0L) > 0L,
        domainTabs = domainTabs,
        selectedDomain = selectedDomain
    )
}

internal fun computeDeviceProbeSummary(capabilities: com.opencamera.core.device.DeviceCapabilities): String {
    val camCount = capabilities.availableLensFacings.size
    val lensNodes = capabilities.zoomRatioCapability.lensNodeMap
    val outputSizes = capabilities.availableStillCaptureOutputSizes
    val zoomRatios = capabilities.zoomRatioCapability.normalizedSupportedRatios
    val previewBases = capabilities.zoomRatioCapability.normalizedPreviewBaseRatios
    return buildString {
        appendLine("cameras: $camCount | lens-facings: ${capabilities.availableLensFacings.joinToString { it.name }}")
        if (lensNodes.isNotEmpty()) {
            appendLine("lens-nodes: ${lensNodes.entries.joinToString { (node, avail) -> "${node.label}(id=${avail.physicalCameraId ?: "?"},threshold=${avail.thresholdRatio})" }}")
        }
        appendLine("zoom: ratios=${zoomRatios.joinToString()},preview-bases=${previewBases.joinToString().ifBlank { "none" }},support=${capabilities.zoomRatioCapability.support.label}")
        if (outputSizes.isNotEmpty()) {
            val totalPixels = outputSizes.sumOf { it.pixelCount }
            appendLine("still-output: ${outputSizes.size} sizes, total-pixels=${totalPixels}, largest=${outputSizes.first().width}x${outputSizes.first().height}")
            appendLine("still-output-sizes: ${outputSizes.joinToString { it.probeSummaryLabel() }}")
        } else {
            appendLine("still-output: none reported")
        }
        capabilities.stillCaptureCameraProbes.forEach { probe ->
            val physicalIds = probe.physicalCameraIds.joinToString("|").ifBlank { "none" }
            appendLine(
                "still-camera-probe: id=${probe.cameraId},lens=${probe.lensFacing?.name ?: "?"}," +
                    "physical=$physicalIds,sizes=${probe.outputSizes.probeSummaryList()}"
            )
            probe.physicalOutputProbes.forEach { physicalProbe ->
                appendLine(
                    "physical-still-probe: parent=${probe.cameraId},id=${physicalProbe.cameraId}," +
                        "sizes=${physicalProbe.outputSizes.probeSummaryList()}"
                )
            }
        }
        val facts = mutableListOf<String>()
        if (!capabilities.supportsStillCapture) facts += "stillCapture=UNSUPPORTED"
        if (!capabilities.supportsVideoRecording) facts += "videoRecording=UNSUPPORTED"
        if (!capabilities.supportsAudioRecording) facts += "audio=UNSUPPORTED"
        if (!capabilities.supportsFlashControl) facts += "flash=DEGRADED"
        if (!capabilities.supportsManualControls) facts += "manualControls=UNSUPPORTED"
        if (!capabilities.supportsNightMultiFrame) facts += "nightMultiFrame=DEGRADED"
        if (!capabilities.supportsPortraitDepthEffect) facts += "portraitDepth=DEGRADED"
        if (facts.isNotEmpty()) {
            appendLine("facts: ${facts.joinToString()}")
        }
    }.trimEnd()
}

private fun List<com.opencamera.core.device.StillCaptureOutputSize>.probeSummaryList(): String {
    return if (isEmpty()) {
        "none"
    } else {
        joinToString { it.probeSummaryLabel() }
    }
}

private fun com.opencamera.core.device.StillCaptureOutputSize.probeSummaryLabel(): String {
    val megapixels = kotlin.math.round(pixelCount / 1_000_000.0).toInt()
    return "${megapixels}MP:${width}x${height}(${resolutionSource.tagValue})"
}
