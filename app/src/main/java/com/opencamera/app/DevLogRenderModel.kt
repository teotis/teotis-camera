package com.opencamera.app

import com.opencamera.core.session.PerformanceLinkEvent
import com.opencamera.core.session.SessionTraceEvent
import com.opencamera.core.session.TraceEventDomain

internal enum class DevLogTab { KEY, CORE, ERROR, ALL }

internal data class DevLogClearCutoffs(
    val keySequence: Int = 0,
    val coreSequence: Int = 0,
    val errorSequence: Int = 0,
    val allSequence: Int = 0,
    val linkEventCount: Int = 0
) {
    fun markCleared(
        type: DevLogTab,
        traceEvents: List<SessionTraceEvent>,
        linkEvents: List<PerformanceLinkEvent>
    ): DevLogClearCutoffs {
        val maxSequence = traceEvents.maxOfOrNull { it.sequence } ?: 0
        return when (type) {
            DevLogTab.KEY -> copy(keySequence = maxSequence)
            DevLogTab.CORE -> copy(coreSequence = maxSequence, linkEventCount = linkEvents.size)
            DevLogTab.ERROR -> copy(errorSequence = maxSequence)
            DevLogTab.ALL -> copy(
                keySequence = maxSequence,
                coreSequence = maxSequence,
                errorSequence = maxSequence,
                allSequence = maxSequence,
                linkEventCount = linkEvents.size
            )
        }
    }

    fun sequenceFor(type: DevLogTab): Int {
        return when (type) {
            DevLogTab.KEY -> keySequence
            DevLogTab.CORE -> coreSequence
            DevLogTab.ERROR -> errorSequence
            DevLogTab.ALL -> allSequence
        }
    }
}

internal data class DomainTabCount(
    val domain: TraceEventDomain,
    val count: Int
)

internal data class DevLogRenderModel(
    val isAvailable: Boolean,
    val selectedTab: DevLogTab,
    val title: String,
    val summaryText: String,
    val content: String,
    val exportContent: String,
    val storageUsedDisplay: String = "",
    val storageCapacityDisplay: String = "",
    val storageUsageRatio: Float = 0f,
    val canCleanup: Boolean = false,
    val domainTabs: List<DomainTabCount> = emptyList(),
    val selectedDomain: TraceEventDomain? = null
)
