package com.opencamera.app.camera

import android.hardware.camera2.CameraCharacteristics
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class VendorCameraProbeTest {
    @Test
    fun `probe section records failures instead of throwing`() {
        val output = StringBuilder()

        val completed = appendProbeSection(output, "fragile-section") {
            throw IllegalStateException("vendor key rejected")
        }

        assertFalse(completed)
        assertTrue(output.toString().contains("[fragile-section] ERROR: vendor key rejected"))
    }

    @Test
    fun `available key probe tolerates vendor null list contract violations`() {
        val chars = mock(CameraCharacteristics::class.java)
        `when`(chars.availableCaptureRequestKeys).thenThrow(NullPointerException())
        `when`(chars.availableCaptureResultKeys).thenThrow(NullPointerException())
        `when`(chars.availableSessionKeys).thenThrow(NullPointerException())
        `when`(chars.availablePhysicalCameraRequestKeys).thenThrow(NullPointerException())

        val output = StringBuilder()
        invokeVendorProbeMethod("appendAvailableKeys", output, chars)

        assertTrue(output.isEmpty())
    }

    private fun invokeVendorProbeMethod(
        name: String,
        output: StringBuilder,
        chars: CameraCharacteristics
    ) {
        val method = VendorCameraProbe::class.java.getDeclaredMethod(
            name,
            StringBuilder::class.java,
            CameraCharacteristics::class.java
        )
        method.isAccessible = true
        method.invoke(VendorCameraProbe, output, chars)
    }
}
