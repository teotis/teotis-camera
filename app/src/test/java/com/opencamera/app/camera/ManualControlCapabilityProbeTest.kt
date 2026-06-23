package com.opencamera.app.camera

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.ManualControlCapabilityMatrix
import com.opencamera.core.device.ManualControlSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ManualControlCapabilityProbeTest {

    @Test
    fun `mergeManualControlCapabilities returns null when no profiles have capabilities`() {
        val profiles = listOf(
            CameraLensProfile(lensFacing = LensFacing.BACK, hasFlashUnit = true)
        )
        val result = mergeManualControlCapabilities(profiles)
        assertEquals(null, result)
    }

    @Test
    fun `mergeManualControlCapabilities returns null for empty list`() {
        assertEquals(null, mergeManualControlCapabilities(emptyList()))
    }

    @Test
    fun `mergeManualControlCapabilities propagates single profile capabilities verbatim`() {
        val matrix = ManualControlCapabilityMatrix(
            raw = ManualControlSupport.SAVED_ONLY,
            iso = ManualControlSupport.APPLY,
            shutter = ManualControlSupport.APPLY,
            exposureCompensation = ManualControlSupport.APPLY,
            focusDistance = ManualControlSupport.UNSUPPORTED,
            aperture = ManualControlSupport.UNSUPPORTED,
            whiteBalance = ManualControlSupport.SAVED_ONLY
        )
        val profiles = listOf(
            CameraLensProfile(
                lensFacing = LensFacing.BACK,
                hasFlashUnit = true,
                manualControlCapabilities = matrix
            )
        )
        val result = mergeManualControlCapabilities(profiles)
        assertNotNull(result)
        assertEquals(matrix, result)
    }

    @Test
    fun `mergeManualControlCapabilities takes maximum support across profiles per field`() {
        val backMatrix = ManualControlCapabilityMatrix(
            raw = ManualControlSupport.SAVED_ONLY,
            iso = ManualControlSupport.APPLY,
            shutter = ManualControlSupport.APPLY,
            exposureCompensation = ManualControlSupport.APPLY,
            focusDistance = ManualControlSupport.APPLY,
            aperture = ManualControlSupport.UNSUPPORTED,
            whiteBalance = ManualControlSupport.APPLY
        )
        val frontMatrix = ManualControlCapabilityMatrix(
            raw = ManualControlSupport.SAVED_ONLY,
            iso = ManualControlSupport.UNSUPPORTED,
            shutter = ManualControlSupport.UNSUPPORTED,
            exposureCompensation = ManualControlSupport.APPLY,
            focusDistance = ManualControlSupport.UNSUPPORTED,
            aperture = ManualControlSupport.UNSUPPORTED,
            whiteBalance = ManualControlSupport.SAVED_ONLY
        )
        val profiles = listOf(
            CameraLensProfile(
                lensFacing = LensFacing.BACK,
                hasFlashUnit = true,
                manualControlCapabilities = backMatrix
            ),
            CameraLensProfile(
                lensFacing = LensFacing.FRONT,
                hasFlashUnit = false,
                manualControlCapabilities = frontMatrix
            )
        )
        val result = mergeManualControlCapabilities(profiles)!!

        assertEquals(ManualControlSupport.APPLY, result.iso)
        assertEquals(ManualControlSupport.APPLY, result.shutter)
        assertEquals(ManualControlSupport.APPLY, result.exposureCompensation)
        assertEquals(ManualControlSupport.APPLY, result.focusDistance)
        assertEquals(ManualControlSupport.UNSUPPORTED, result.aperture)
        assertEquals(ManualControlSupport.APPLY, result.whiteBalance)
    }

    @Test
    fun `mergeManualControlCapabilities picks best whiteBalance among SAVED_ONLY and UNSUPPORTED`() {
        val profiles = listOf(
            CameraLensProfile(
                lensFacing = LensFacing.BACK,
                hasFlashUnit = true,
                manualControlCapabilities = ManualControlCapabilityMatrix(
                    raw = ManualControlSupport.SAVED_ONLY,
                    iso = ManualControlSupport.UNSUPPORTED,
                    shutter = ManualControlSupport.UNSUPPORTED,
                    exposureCompensation = ManualControlSupport.UNSUPPORTED,
                    focusDistance = ManualControlSupport.UNSUPPORTED,
                    aperture = ManualControlSupport.UNSUPPORTED,
                    whiteBalance = ManualControlSupport.UNSUPPORTED
                )
            ),
            CameraLensProfile(
                lensFacing = LensFacing.FRONT,
                hasFlashUnit = false,
                manualControlCapabilities = ManualControlCapabilityMatrix(
                    raw = ManualControlSupport.SAVED_ONLY,
                    iso = ManualControlSupport.UNSUPPORTED,
                    shutter = ManualControlSupport.UNSUPPORTED,
                    exposureCompensation = ManualControlSupport.UNSUPPORTED,
                    focusDistance = ManualControlSupport.UNSUPPORTED,
                    aperture = ManualControlSupport.UNSUPPORTED,
                    whiteBalance = ManualControlSupport.SAVED_ONLY
                )
            )
        )
        val result = mergeManualControlCapabilities(profiles)!!
        assertEquals(ManualControlSupport.SAVED_ONLY, result.whiteBalance)
    }

    @Test
    fun `resolveDeviceCapabilities uses probe value when manualControlCapabilities is non-null`() {
        val probeMatrix = ManualControlCapabilityMatrix(
            raw = ManualControlSupport.SAVED_ONLY,
            iso = ManualControlSupport.APPLY,
            shutter = ManualControlSupport.APPLY,
            exposureCompensation = ManualControlSupport.UNSUPPORTED,
            focusDistance = ManualControlSupport.APPLY,
            aperture = ManualControlSupport.UNSUPPORTED,
            whiteBalance = ManualControlSupport.SAVED_ONLY
        )
        val capabilities = resolveDeviceCapabilities(
            baseCapabilities = DeviceCapabilities.DEFAULT,
            cameraProfiles = listOf(
                CameraLensProfile(
                    lensFacing = LensFacing.BACK,
                    hasFlashUnit = true,
                    manualControlCapabilities = probeMatrix
                )
            )
        )
        assertEquals(probeMatrix, capabilities.manualControlCapabilities)
        assertEquals(ManualControlSupport.APPLY, capabilities.resolvedManualControlCapabilities.iso)
        assertEquals(
            ManualControlSupport.UNSUPPORTED,
            capabilities.resolvedManualControlCapabilities.exposureCompensation
        )
    }

    @Test
    fun `resolvedManualControlCapabilities falls back to CAMERA2_INTEROP_DEFAULT when probe is null`() {
        val capabilities = resolveDeviceCapabilities(
            baseCapabilities = DeviceCapabilities.DEFAULT,
            cameraProfiles = listOf(
                CameraLensProfile(
                    lensFacing = LensFacing.BACK,
                    hasFlashUnit = true,
                    manualControlCapabilities = null
                )
            )
        )
        assertEquals(null, capabilities.manualControlCapabilities)
        assertEquals(
            ManualControlCapabilityMatrix.CAMERA2_INTEROP_DEFAULT,
            capabilities.resolvedManualControlCapabilities
        )
    }

    @Test
    fun `resolvedManualControlCapabilities falls back to SAVED_ONLY_DEFAULT when supportsManualControls is false`() {
        val base = DeviceCapabilities.DEFAULT.copy(supportsManualControls = false)
        val capabilities = resolveDeviceCapabilities(
            baseCapabilities = base,
            cameraProfiles = listOf(
                CameraLensProfile(
                    lensFacing = LensFacing.BACK,
                    hasFlashUnit = true,
                    manualControlCapabilities = null
                )
            )
        )
        assertEquals(
            ManualControlCapabilityMatrix.SAVED_ONLY_DEFAULT,
            capabilities.resolvedManualControlCapabilities
        )
    }

    @Test
    fun `resolvedManualControlCapabilities does not fall back when probe value is explicitly set`() {
        val probeMatrix = ManualControlCapabilityMatrix(
            raw = ManualControlSupport.SAVED_ONLY,
            iso = ManualControlSupport.UNSUPPORTED,
            shutter = ManualControlSupport.UNSUPPORTED,
            exposureCompensation = ManualControlSupport.UNSUPPORTED,
            focusDistance = ManualControlSupport.UNSUPPORTED,
            aperture = ManualControlSupport.UNSUPPORTED,
            whiteBalance = ManualControlSupport.UNSUPPORTED
        )
        val capabilities = resolveDeviceCapabilities(
            baseCapabilities = DeviceCapabilities.DEFAULT,
            cameraProfiles = listOf(
                CameraLensProfile(
                    lensFacing = LensFacing.BACK,
                    hasFlashUnit = true,
                    manualControlCapabilities = probeMatrix
                )
            )
        )
        assertEquals(
            ManualControlSupport.UNSUPPORTED,
            capabilities.resolvedManualControlCapabilities.iso
        )
        assertTrue(
            capabilities.resolvedManualControlCapabilities.iso != ManualControlCapabilityMatrix.CAMERA2_INTEROP_DEFAULT.iso,
            "resolved ISO must come from the probe, not from the static CAMERA2_INTEROP_DEFAULT"
        )
    }

    @Test
    fun `mergeManualControlCapabilities handles many profiles`() {
        val profiles = (1..5).map { i ->
            CameraLensProfile(
                lensFacing = if (i % 2 == 0) LensFacing.FRONT else LensFacing.BACK,
                hasFlashUnit = i % 2 == 1,
                manualControlCapabilities = ManualControlCapabilityMatrix(
                    raw = ManualControlSupport.SAVED_ONLY,
                    iso = if (i == 1 || i == 3) ManualControlSupport.APPLY else ManualControlSupport.UNSUPPORTED,
                    shutter = ManualControlSupport.UNSUPPORTED,
                    exposureCompensation = ManualControlSupport.APPLY,
                    focusDistance = if (i == 5) ManualControlSupport.APPLY else ManualControlSupport.UNSUPPORTED,
                    aperture = ManualControlSupport.UNSUPPORTED,
                    whiteBalance = ManualControlSupport.SAVED_ONLY
                )
            )
        }
        val result = mergeManualControlCapabilities(profiles)!!
        assertEquals(ManualControlSupport.APPLY, result.iso)
        assertEquals(ManualControlSupport.UNSUPPORTED, result.shutter)
        assertEquals(ManualControlSupport.APPLY, result.exposureCompensation)
        assertEquals(ManualControlSupport.APPLY, result.focusDistance)
        assertEquals(ManualControlSupport.UNSUPPORTED, result.aperture)
        assertEquals(ManualControlSupport.SAVED_ONLY, result.whiteBalance)
    }
}
