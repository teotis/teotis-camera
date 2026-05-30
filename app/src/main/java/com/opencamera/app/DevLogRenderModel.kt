package com.opencamera.app

import com.opencamera.core.session.TraceEventDomain

internal enum class DevLogTab { KEY, CORE, ERROR, ALL }

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
