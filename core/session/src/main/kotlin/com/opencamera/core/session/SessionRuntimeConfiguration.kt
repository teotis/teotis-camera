package com.opencamera.core.session

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.settings.SessionSettingsSnapshot

data class SessionRuntimeConfiguration(
    val deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
    val lensFacing: LensFacing = LensFacing.BACK,
    val stillCaptureQuality: StillCaptureQualityPreference = StillCaptureQualityPreference.QUALITY,
    val stillCaptureResolutionPreset: StillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
    val stillCaptureOutputSize: StillCaptureOutputSize? = null,
    val previewRatio: PreviewRatio = PreviewRatio.FULL,
    val settings: SessionSettingsSnapshot = SessionSettingsSnapshot()
)
