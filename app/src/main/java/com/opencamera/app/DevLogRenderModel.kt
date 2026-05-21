package com.opencamera.app

internal enum class DevLogTab { KEY, CORE, ERROR, ALL }

internal data class DevLogRenderModel(
    val isAvailable: Boolean,
    val selectedTab: DevLogTab,
    val title: String,
    val summaryText: String,
    val content: String,
    val exportContent: String
)
