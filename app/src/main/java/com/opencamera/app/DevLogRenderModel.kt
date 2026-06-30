package com.opencamera.app

import com.opencamera.core.media.ResourceDiagnosticsSnapshot
import com.opencamera.core.session.PerformanceLinkEvent
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.SessionTraceEvent
import com.opencamera.core.session.TraceEventDomain
import com.opencamera.core.session.buildSessionDebugDump
import com.opencamera.app.i18n.AppTextResolver

internal enum class DevLogTab { KEY, CORE, ERROR, ALL }

internal fun initialDevLogTab(): DevLogTab = DevLogTab.ALL

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

/** Sealed hierarchy for RecyclerView adapter items in the dev log panel. */
sealed class DevLogEventItem(
    val type: Int,
    open val displayText: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DevLogEventItem) return false
        return type == other.type && displayText == other.displayText
    }

    override fun hashCode(): Int = 31 * type + displayText.hashCode()
}

class TraceEventItem(
    val sequence: Int,
    override val displayText: String
) : DevLogEventItem(TYPE, displayText) {
    companion object {
        const val TYPE = 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TraceEventItem) return false
        return sequence == other.sequence && displayText == other.displayText
    }

    override fun hashCode(): Int = 31 * TYPE + sequence.hashCode() + 31 * displayText.hashCode()
}

class SectionHeaderItem(
    override val displayText: String
) : DevLogEventItem(TYPE, displayText) {
    companion object {
        const val TYPE = 1
    }
}

class LinkEventItem(
    override val displayText: String
) : DevLogEventItem(TYPE, displayText) {
    companion object {
        const val TYPE = 2
    }
}

internal data class DevLogRenderModel(
    val isAvailable: Boolean,
    val selectedTab: DevLogTab,
    val title: String,
    val summaryText: String,
    val visibleEvents: List<DevLogEventItem>,
    val storageUsedDisplay: String = "",
    val storageCapacityDisplay: String = "",
    val storageUsageRatio: Float = 0f,
    val canCleanup: Boolean = false,
    val domainTabs: List<DomainTabCount> = emptyList(),
    val selectedDomain: TraceEventDomain? = null
)
