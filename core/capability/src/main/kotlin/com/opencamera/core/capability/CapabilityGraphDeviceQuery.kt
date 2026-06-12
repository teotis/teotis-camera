package com.opencamera.core.capability

interface CapabilityGraphDeviceQuery {
    fun supportsStillCapture(): Boolean
    fun supportsVideoRecording(): Boolean
    fun supportsPreviewSnapshots(): Boolean
    fun supportsNightMultiFrame(): Boolean
    fun manualControlSummary(): CapabilityManualControlSummary
    fun rawOutputSupport(): CapabilitySupport
    fun supportsPortraitDepth(): Boolean
    fun supportsDocumentGeometry(): Boolean

    /**
     * Multi-frame input format capability matrix. Returns null when the resolver
     * has not been wired up (e.g. legacy call sites). Callers must treat null
     * as "unknown, assume unsupported for RAW".
     */
    fun multiFrameInputFormatMatrix(): MultiFrameInputCapabilityMatrix? = null
}

data class CapabilityManualControlSummary(
    val hasAppliedControls: Boolean,
    val hasSavedOnlyControls: Boolean
)
