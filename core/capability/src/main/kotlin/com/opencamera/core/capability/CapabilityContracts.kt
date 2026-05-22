package com.opencamera.core.capability

enum class CapabilitySupport(val tagValue: String) {
    SUPPORTED("supported"),
    DEGRADED("degraded"),
    SAVED_ONLY("saved-only"),
    PREVIEW_ONLY("preview-only"),
    UNSUPPORTED("unsupported")
}

enum class CapabilityRequirementKind {
    STILL_CAPTURE,
    VIDEO_RECORDING,
    PREVIEW_FRAME_STREAM,
    ANALYSIS_FRAME_STREAM,
    MULTI_FRAME_CAPTURE,
    TEMPORAL_RING_BUFFER,
    MOTION_SIDE_CAR,
    MANUAL_CONTROL,
    RAW_OUTPUT,
    FILTER_CAPTURE_RENDER,
    FILTER_PREVIEW_RENDER,
    PORTRAIT_SEGMENTATION,
    DOCUMENT_GEOMETRY,
    WATERMARK_RENDER,
    SAVE_TRANSACTION,
    THUMBNAIL_RESULT
}

enum class CapabilityUseSite {
    PREVIEW,
    CAPTURE,
    VIDEO,
    LIVE_PHOTO,
    DIAGNOSTICS
}

data class CapabilityRequirement(
    val id: String,
    val kind: CapabilityRequirementKind,
    val requiredFor: Set<CapabilityUseSite>,
    val fallbackIds: List<String> = emptyList()
)

data class CapabilityResolution(
    val requirement: CapabilityRequirement,
    val support: CapabilitySupport,
    val reason: String,
    val selectedFallbackId: String? = null,
    val diagnostics: Map<String, String> = emptyMap()
) {
    val isApplied: Boolean
        get() = support == CapabilitySupport.SUPPORTED || support == CapabilitySupport.DEGRADED

    val pipelineNote: String
        get() = buildString {
            append("capability:${requirement.id}=${support.tagValue}")
            selectedFallbackId?.let { append(":$it") }
        }
}

data class CapabilityGraphReport(
    val featureId: String,
    val requested: List<CapabilityRequirement>,
    val resolved: List<CapabilityResolution>
) {
    val allApplied: Boolean
        get() = resolved.all { it.isApplied }

    val hasUnsupported: Boolean
        get() = resolved.any { it.support == CapabilitySupport.UNSUPPORTED }

    val pipelineNotes: List<String>
        get() = resolved.map { it.pipelineNote }

    fun resolutionFor(id: String): CapabilityResolution? =
        resolved.firstOrNull { it.requirement.id == id }
}
