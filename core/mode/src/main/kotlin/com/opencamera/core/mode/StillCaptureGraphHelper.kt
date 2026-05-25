package com.opencamera.core.mode

import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.media.StillCaptureResolutionOption

fun stillCaptureDeviceGraph(runtimeState: ModeRuntimeState): DeviceGraphSpec {
    val preset = runtimeState.stillCaptureResolutionPreset
    val resolutionOption = StillCaptureResolutionOption(
        tagValue = preset.tagValue,
        label = preset.label,
        targetWidth = preset.targetWidth,
        targetHeight = preset.targetHeight
    )
    return DeviceGraphSpec.stillCapture(
        preferredLensFacing = runtimeState.lensFacing,
        enablePreviewSnapshots = runtimeState.deviceCapabilities.supportsPreviewSnapshots,
        resolutionOption = resolutionOption
    )
}
