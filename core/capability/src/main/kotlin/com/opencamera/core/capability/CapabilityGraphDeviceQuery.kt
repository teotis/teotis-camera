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
}

data class CapabilityManualControlSummary(
    val hasAppliedControls: Boolean,
    val hasSavedOnlyControls: Boolean
)
