package com.opencamera.app

import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.session.SessionState
import kotlin.math.abs

internal fun displayedStillCaptureOutputSize(state: SessionState): StillCaptureOutputSize {
    return state.activeDeviceGraph.stillCapture.outputSize
        ?: closestStillCaptureOutputSizeForPreset(
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
                closestStillCaptureOutputSizeForPreset(
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

private fun closestStillCaptureOutputSizeForPreset(
    preset: StillCaptureResolutionPreset,
    availableOutputSizes: List<StillCaptureOutputSize>
): StillCaptureOutputSize {
    if (availableOutputSizes.isEmpty()) {
        return StillCaptureOutputSize(preset.targetWidth, preset.targetHeight)
    }
    val targetPixels = preset.targetWidth.toLong() * preset.targetHeight.toLong()
    return availableOutputSizes.minBy { size -> abs(size.pixelCount - targetPixels) }
}
