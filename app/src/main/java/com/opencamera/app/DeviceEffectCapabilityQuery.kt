package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.effect.EffectCapabilityQuery

class DeviceCapabilitiesEffectQuery(
    private val capabilities: DeviceCapabilities
) : EffectCapabilityQuery {
    override fun supportsPortraitDepth(): Boolean =
        capabilities.supportsPortraitDepthEffect

    override fun supportsDocumentGeometry(): Boolean =
        capabilities.supportsDocumentScanEnhancement

    override fun supportsManualControls(): Boolean =
        capabilities.supportsAppliedManualControls
}

fun DeviceCapabilities.asEffectCapabilityQuery(): EffectCapabilityQuery =
    DeviceCapabilitiesEffectQuery(this)
