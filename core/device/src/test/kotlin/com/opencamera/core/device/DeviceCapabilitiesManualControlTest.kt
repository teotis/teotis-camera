package com.opencamera.core.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceCapabilitiesManualControlTest {

    @Test
    fun `resolvedManualControlCapabilities returns probe value when manualControlCapabilities is non-null`() {
        val probe = ManualControlCapabilityMatrix(
            raw = ManualControlSupport.SAVED_ONLY,
            iso = ManualControlSupport.APPLY,
            shutter = ManualControlSupport.UNSUPPORTED,
            exposureCompensation = ManualControlSupport.APPLY,
            focusDistance = ManualControlSupport.UNSUPPORTED,
            aperture = ManualControlSupport.UNSUPPORTED,
            whiteBalance = ManualControlSupport.SAVED_ONLY
        )
        val caps = DeviceCapabilities(manualControlCapabilities = probe)
        assertEquals(probe, caps.resolvedManualControlCapabilities)
        assertEquals(ManualControlSupport.APPLY, caps.resolvedManualControlCapabilities.iso)
        assertEquals(ManualControlSupport.UNSUPPORTED, caps.resolvedManualControlCapabilities.shutter)
    }

    @Test
    fun `resolvedManualControlCapabilities falls back to CAMERA2_INTEROP_DEFAULT when probe is null and supportsManualControls true`() {
        val caps = DeviceCapabilities(
            manualControlCapabilities = null,
            supportsManualControls = true
        )
        assertEquals(
            ManualControlCapabilityMatrix.CAMERA2_INTEROP_DEFAULT,
            caps.resolvedManualControlCapabilities
        )
    }

    @Test
    fun `resolvedManualControlCapabilities falls back to SAVED_ONLY_DEFAULT when supportsManualControls false`() {
        val caps = DeviceCapabilities(
            manualControlCapabilities = null,
            supportsManualControls = false
        )
        assertEquals(
            ManualControlCapabilityMatrix.SAVED_ONLY_DEFAULT,
            caps.resolvedManualControlCapabilities
        )
    }

    @Test
    fun `resolvedManualControlCapabilities does not fall back to CAMERA2_INTEROP_DEFAULT when probe is explicitly set`() {
        val probe = ManualControlCapabilityMatrix(
            raw = ManualControlSupport.SAVED_ONLY,
            iso = ManualControlSupport.UNSUPPORTED,
            shutter = ManualControlSupport.UNSUPPORTED,
            exposureCompensation = ManualControlSupport.UNSUPPORTED,
            focusDistance = ManualControlSupport.UNSUPPORTED,
            aperture = ManualControlSupport.UNSUPPORTED,
            whiteBalance = ManualControlSupport.UNSUPPORTED
        )
        val caps = DeviceCapabilities(manualControlCapabilities = probe)
        assertEquals(
            ManualControlSupport.UNSUPPORTED,
            caps.resolvedManualControlCapabilities.iso
        )
        assertTrue(
            caps.resolvedManualControlCapabilities != ManualControlCapabilityMatrix.CAMERA2_INTEROP_DEFAULT,
            "Probe value must not be replaced by CAMERA2_INTEROP_DEFAULT"
        )
    }

    @Test
    fun `supportsAppliedManualControls reflects resolved capabilities`() {
        val probeWithApply = ManualControlCapabilityMatrix(
            raw = ManualControlSupport.SAVED_ONLY,
            iso = ManualControlSupport.APPLY,
            shutter = ManualControlSupport.UNSUPPORTED,
            exposureCompensation = ManualControlSupport.UNSUPPORTED,
            focusDistance = ManualControlSupport.UNSUPPORTED,
            aperture = ManualControlSupport.UNSUPPORTED,
            whiteBalance = ManualControlSupport.UNSUPPORTED
        )
        val caps = DeviceCapabilities(manualControlCapabilities = probeWithApply)
        assertTrue(caps.supportsAppliedManualControls)
    }
}
