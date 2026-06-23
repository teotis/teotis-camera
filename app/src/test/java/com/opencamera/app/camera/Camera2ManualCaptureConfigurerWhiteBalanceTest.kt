package com.opencamera.app.camera

import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.DeviceShotRequest
import com.opencamera.core.device.ManualControlCapabilityMatrix
import com.opencamera.core.device.ManualControlSupport
import com.opencamera.core.media.ShotKind
import com.opencamera.core.settings.ManualCaptureParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Camera2ManualCaptureConfigurerWhiteBalanceTest {

    @Test
    fun `WB APPLY with Kelvin produces config with whiteBalanceKelvin set`() {
        val config = resolveCamera2ManualCaptureConfig(
            DeviceShotRequest(
                shotId = "shot-wb-apply",
                template = CaptureTemplate.STILL_CAPTURE,
                shotKind = ShotKind.STILL_CAPTURE,
                manualCaptureParams = ManualCaptureParams(
                    whiteBalanceKelvin = 5500
                )
            )
        )

        assertNotNull(config)
        assertEquals(5500, config.whiteBalanceKelvin)
    }

    @Test
    fun `WB APPLY with Kelvin filters to null when capability is UNSUPPORTED`() {
        val request = DeviceShotRequest(
            shotId = "shot-wb-unsupported",
            template = CaptureTemplate.STILL_CAPTURE,
            shotKind = ShotKind.STILL_CAPTURE,
            manualCaptureParams = ManualCaptureParams(
                whiteBalanceKelvin = 4000
            ),
            manualControlCapabilities = ManualControlCapabilityMatrix(
                raw = ManualControlSupport.SAVED_ONLY,
                iso = ManualControlSupport.APPLY,
                shutter = ManualControlSupport.APPLY,
                exposureCompensation = ManualControlSupport.APPLY,
                focusDistance = ManualControlSupport.APPLY,
                aperture = ManualControlSupport.APPLY,
                whiteBalance = ManualControlSupport.UNSUPPORTED
            )
        )

        val config = resolveCamera2ManualCaptureConfig(request)

        assertNull(config)
        val diagnostics = manualCaptureExecutionDiagnostics(
            requestedParams = request.manualCaptureParams,
            capabilityMatrix = request.manualControlCapabilities
        )
        assertTrue("adapter:manual-request=unsupported" in diagnostics)
        assertTrue(diagnostics.any { it.contains("wb") })
    }

    @Test
    fun `WB SAVED_ONLY filters Kelvin to null and diagnostics reports saved-only`() {
        val request = DeviceShotRequest(
            shotId = "shot-wb-saved",
            template = CaptureTemplate.STILL_CAPTURE,
            shotKind = ShotKind.STILL_CAPTURE,
            manualCaptureParams = ManualCaptureParams(
                whiteBalanceKelvin = 4200
            ),
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

        val config = resolveCamera2ManualCaptureConfig(request)

        assertNull(config)
        val diagnostics = manualCaptureExecutionDiagnostics(
            requestedParams = request.manualCaptureParams,
            capabilityMatrix = request.manualControlCapabilities
        )
        assertTrue("adapter:manual-request=saved-only" in diagnostics)
        assertTrue(diagnostics.any { it.contains("wb") })
    }

    @Test
    fun `WB auto with null Kelvin filters to null and produces no wb diagnostics`() {
        val request = DeviceShotRequest(
            shotId = "shot-wb-auto",
            template = CaptureTemplate.STILL_CAPTURE,
            shotKind = ShotKind.STILL_CAPTURE,
            manualCaptureParams = ManualCaptureParams(
                whiteBalanceKelvin = null
            ),
            manualControlCapabilities = ManualControlCapabilityMatrix.CAMERA2_INTEROP_DEFAULT
        )

        val config = resolveCamera2ManualCaptureConfig(request)

        assertNull(config)
        val diagnostics = manualCaptureExecutionDiagnostics(
            requestedParams = request.manualCaptureParams,
            capabilityMatrix = request.manualControlCapabilities
        )
        assertTrue(diagnostics.isEmpty())
    }

    @Test
    fun `kelvinToRgb produces normalised ratios for 5500K daylight`() {
        val rgb = kelvinToRgb(5500)

        assertEquals(3, rgb.size)
        assertTrue(rgb.all { it > 0f && it <= 1f })
        assertEquals(1f, rgb.maxOrNull() ?: 0f, 0.01f)
        assertTrue(rgb[0] >= rgb[1])
        assertTrue(rgb[1] >= rgb[2])
    }

    @Test
    fun `kelvinToRgb produces warm ratio for 2700K tungsten`() {
        val rgb = kelvinToRgb(2700)

        assertEquals(3, rgb.size)
        assertTrue(rgb.all { it > 0f && it <= 1f })
        assertTrue(rgb[0] > rgb[2])
    }

    @Test
    fun `kelvinToRggbGains normalises green gains`() {
        val gains = kelvinToRggbGains(5500)

        assertTrue(gains[0] > 0f)
        assertEquals(1f, gains[1], 0.001f)
        assertEquals(1f, gains[2], 0.001f)
        assertTrue(gains[3] > 0f)
    }

    @Test
    fun `kelvinToRgb clamps extreme kelvin to valid range`() {
        val low = kelvinToRgb(500)
        val high = kelvinToRgb(50000)

        assertTrue(low.all { it > 0f && it <= 1f })
        assertTrue(high.all { it > 0f && it <= 1f })
    }
}
