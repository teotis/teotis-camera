package com.opencamera.core.device

import com.opencamera.core.capability.CapabilityGraphDeviceQuery
import com.opencamera.core.capability.CapabilityManualControlSummary
import com.opencamera.core.capability.CapabilitySupport

class DeviceCapabilitiesGraphQuery(
    private val capabilities: DeviceCapabilities
) : CapabilityGraphDeviceQuery {
    override fun supportsStillCapture(): Boolean = capabilities.supportsStillCapture
    override fun supportsVideoRecording(): Boolean = capabilities.supportsVideoRecording
    override fun supportsPreviewSnapshots(): Boolean = capabilities.supportsPreviewSnapshots
    override fun supportsNightMultiFrame(): Boolean = capabilities.supportsNightMultiFrame
    override fun supportsPortraitDepth(): Boolean = capabilities.supportsPortraitDepthEffect
    override fun supportsDocumentGeometry(): Boolean = capabilities.supportsDocumentScanEnhancement

    override fun manualControlSummary(): CapabilityManualControlSummary {
        val matrix = capabilities.resolvedManualControlCapabilities
        val controls = listOf(
            matrix.raw,
            matrix.iso,
            matrix.shutter,
            matrix.exposureCompensation,
            matrix.focusDistance,
            matrix.aperture,
            matrix.whiteBalance
        )
        return CapabilityManualControlSummary(
            hasAppliedControls = controls.any { it == ManualControlSupport.APPLY },
            hasSavedOnlyControls = controls.any { it == ManualControlSupport.SAVED_ONLY }
        )
    }

    override fun rawOutputSupport(): CapabilitySupport {
        return when (capabilities.resolvedManualControlCapabilities.raw) {
            ManualControlSupport.APPLY -> CapabilitySupport.SUPPORTED
            ManualControlSupport.SAVED_ONLY -> CapabilitySupport.SAVED_ONLY
            ManualControlSupport.UNSUPPORTED -> CapabilitySupport.UNSUPPORTED
        }
    }
}

fun DeviceCapabilities.asCapabilityGraphQuery(): CapabilityGraphDeviceQuery =
    DeviceCapabilitiesGraphQuery(this)
