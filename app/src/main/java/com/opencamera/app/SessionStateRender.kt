package com.opencamera.app

import com.opencamera.app.camera.resolveStillCaptureOutputSize
import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.session.SessionState

internal fun displayedStillCaptureOutputSize(state: SessionState): StillCaptureOutputSize {
    return state.activeDeviceGraph.stillCapture.outputSize
        ?: resolveStillCaptureOutputSize(
            preset = state.activeDeviceGraph.stillCapture.resolutionPreset,
            availableOutputSizes = state.activeDeviceCapabilities.availableStillCaptureOutputSizes
        )
}

internal fun selectedNativeStillCaptureOutputSizeOrNull(
    state: SessionState
): StillCaptureOutputSize? {
    return state.activeDeviceGraph.stillCapture.outputSize
        ?: state.activeDeviceCapabilities.availableStillCaptureOutputSizes
            .takeIf { it.isNotEmpty() }
            ?.let {
                resolveStillCaptureOutputSize(
                    preset = state.activeDeviceGraph.stillCapture.resolutionPreset,
                    availableOutputSizes = it
                )
            }
}

internal fun isStillResolutionToggleEnabled(state: SessionState): Boolean {
    if (state.activeDeviceGraph.template != CaptureTemplate.STILL_CAPTURE) {
        return false
    }
    return state.activeDeviceCapabilities.availableStillCaptureOutputSizes.size > 1 ||
        state.activeDeviceCapabilities.availableStillCaptureResolutionPresets.size > 1
}
