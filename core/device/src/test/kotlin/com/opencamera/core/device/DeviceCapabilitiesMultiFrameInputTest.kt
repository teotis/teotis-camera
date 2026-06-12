package com.opencamera.core.device

import com.opencamera.core.capability.CapabilitySupport
import com.opencamera.core.capability.MultiFrameInputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceCapabilitiesMultiFrameInputTest {

    @Test
    fun `multiFrameInputFormatMatrix returns matrix with all four formats`() {
        val query = DeviceCapabilitiesGraphQuery(DeviceCapabilities.DEFAULT)
        val matrix = query.multiFrameInputFormatMatrix()
        assertNotNull(matrix)
        assertEquals(4, matrix.allFormats.size)
    }

    @Test
    fun `jpeg-burst supported when device has still capture and night multi-frame`() {
        val caps = DeviceCapabilities(
            supportsStillCapture = true,
            supportsNightMultiFrame = true
        )
        val matrix = DeviceCapabilitiesGraphQuery(caps).multiFrameInputFormatMatrix()
        assertEquals(CapabilitySupport.SUPPORTED, matrix.jpegBurst.support)
    }

    @Test
    fun `jpeg-burst degraded when night multi-frame not supported`() {
        val caps = DeviceCapabilities(
            supportsStillCapture = true,
            supportsNightMultiFrame = false
        )
        val matrix = DeviceCapabilitiesGraphQuery(caps).multiFrameInputFormatMatrix()
        assertEquals(CapabilitySupport.DEGRADED, matrix.jpegBurst.support)
    }

    @Test
    fun `raw-dng unsupported on CameraX 1_4 regardless of device raw capability`() {
        val caps = DeviceCapabilities(
            supportsStillCapture = true,
            supportsNightMultiFrame = true,
            manualControlCapabilities = ManualControlCapabilityMatrix(
                raw = ManualControlSupport.APPLY,
                iso = ManualControlSupport.APPLY,
                shutter = ManualControlSupport.APPLY,
                exposureCompensation = ManualControlSupport.APPLY,
                focusDistance = ManualControlSupport.APPLY,
                aperture = ManualControlSupport.APPLY,
                whiteBalance = ManualControlSupport.SAVED_ONLY
            )
        )
        val matrix = DeviceCapabilitiesGraphQuery(caps).multiFrameInputFormatMatrix()
        assertEquals(CapabilitySupport.UNSUPPORTED, matrix.rawDng.support)
        assertTrue(matrix.rawDng.deviceRawCapable)
        assertFalse(matrix.rawDng.cameraXApiSupported)
    }

    @Test
    fun `yuv-burst supported when device has still capture`() {
        val caps = DeviceCapabilities(
            supportsStillCapture = true,
            supportsNightMultiFrame = true
        )
        val matrix = DeviceCapabilitiesGraphQuery(caps).multiFrameInputFormatMatrix()
        assertEquals(CapabilitySupport.SUPPORTED, matrix.yuvBurst.support)
    }

    @Test
    fun `hasRawCapability true when device raw manual control is APPLY`() {
        val caps = DeviceCapabilities(
            supportsStillCapture = true,
            manualControlCapabilities = ManualControlCapabilityMatrix(
                raw = ManualControlSupport.APPLY,
                iso = ManualControlSupport.APPLY,
                shutter = ManualControlSupport.APPLY,
                exposureCompensation = ManualControlSupport.APPLY,
                focusDistance = ManualControlSupport.APPLY,
                aperture = ManualControlSupport.APPLY,
                whiteBalance = ManualControlSupport.SAVED_ONLY
            )
        )
        val matrix = DeviceCapabilitiesGraphQuery(caps).multiFrameInputFormatMatrix()
        assertTrue(matrix.hasRawCapability)
    }

    @Test
    fun `diagnostics contain CameraX 1_4 gate reasons`() {
        val caps = DeviceCapabilities(
            supportsStillCapture = true,
            supportsNightMultiFrame = true
        )
        val matrix = DeviceCapabilitiesGraphQuery(caps).multiFrameInputFormatMatrix()
        val diagnostics = matrix.diagnostics
        assertTrue(diagnostics.any { it.contains("raw-dng=unsupported") })
        assertTrue(diagnostics.any { it.contains("jpeg-dng=unsupported") })
    }

    @Test
    fun `rawOutputSupport and multiFrameInputFormatMatrix are consistent`() {
        val caps = DeviceCapabilities(
            supportsStillCapture = true,
            manualControlCapabilities = ManualControlCapabilityMatrix(
                raw = ManualControlSupport.SAVED_ONLY,
                iso = ManualControlSupport.APPLY,
                shutter = ManualControlSupport.APPLY,
                exposureCompensation = ManualControlSupport.APPLY,
                focusDistance = ManualControlSupport.APPLY,
                aperture = ManualControlSupport.APPLY,
                whiteBalance = ManualControlSupport.SAVED_ONLY
            )
        )
        val query = DeviceCapabilitiesGraphQuery(caps)
        // rawOutputSupport is about single-frame RAW (via manual control)
        assertEquals(CapabilitySupport.SAVED_ONLY, query.rawOutputSupport())
        // multiFrameInputFormatMatrix is about multi-frame fusion RAW (CameraX API gated)
        val matrix = query.multiFrameInputFormatMatrix()
        assertEquals(CapabilitySupport.UNSUPPORTED, matrix.rawDng.support)
    }
}
