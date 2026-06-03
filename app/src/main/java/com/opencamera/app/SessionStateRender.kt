package com.opencamera.app

import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.EffectiveStillCaptureRecipe
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.session.SessionState

internal fun displayedStillCaptureOutputSize(state: SessionState): StillCaptureOutputSize {
    val recipe = EffectiveStillCaptureRecipe.build(
        state.activeDeviceGraph,
        state.activeDeviceCapabilities
    )
    return recipe.resolvedOutputSize
        ?: StillCaptureOutputSize(
            state.activeDeviceGraph.stillCapture.resolutionPreset.targetWidth,
            state.activeDeviceGraph.stillCapture.resolutionPreset.targetHeight
        )
}

internal fun selectedNativeStillCaptureOutputSizeOrNull(
    state: SessionState
): StillCaptureOutputSize? {
    val recipe = EffectiveStillCaptureRecipe.build(
        state.activeDeviceGraph,
        state.activeDeviceCapabilities
    )
    return recipe.resolvedOutputSize
}

internal fun isStillResolutionToggleEnabled(state: SessionState): Boolean {
    if (state.activeDeviceGraph.template != CaptureTemplate.STILL_CAPTURE) {
        return false
    }
    return state.activeDeviceCapabilities.availableStillCaptureOutputSizes.size > 1 ||
        state.activeDeviceCapabilities.availableStillCaptureResolutionPresets.size > 1
}
