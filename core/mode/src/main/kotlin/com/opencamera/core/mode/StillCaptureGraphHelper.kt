package com.opencamera.core.mode

import com.opencamera.core.device.DeviceGraphSpec

fun stillCaptureDeviceGraph(runtimeState: ModeRuntimeState): DeviceGraphSpec {
    return DeviceGraphSpec.stillCapture(
        preferredLensFacing = runtimeState.lensFacing,
        enablePreviewSnapshots = runtimeState.deviceCapabilities.supportsPreviewSnapshots,
        qualityPreference = runtimeState.stillCaptureQuality,
        resolutionPreset = runtimeState.stillCaptureResolutionPreset
    )
}
