package com.opencamera.app.camera

import android.hardware.camera2.CameraCharacteristics
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `extension probe summary is appended to vendor probe text`() {
        val output = StringBuilder()
        val report = CameraExtensionProbe.probe(
            object : CameraExtensionProbeClient {
                override fun isExtensionAvailable(
                    lensFacing: CameraExtensionLensFacing,
                    mode: CameraExtensionMode
                ): Boolean = lensFacing == CameraExtensionLensFacing.BACK &&
                    mode == CameraExtensionMode.NIGHT

                override fun verifyExtensionSelector(
                    lensFacing: CameraExtensionLensFacing,
                    mode: CameraExtensionMode
                ) = Unit
            }
        )

        appendCameraExtensionProbe(output, report)

        assertTrue(output.toString().contains("[camera-extensions]"))
        assertTrue(output.toString().contains("extensions: BACK night=supported"))
    }

    @Test
    fun `extension probe failure is recorded without throwing`() {
        val output = StringBuilder()

        val completed = appendProbeSection(output, "camera-extensions") {
            throw IllegalStateException("extensions unavailable")
        }

        assertFalse(completed)
        assertTrue(output.toString().contains("[camera-extensions] ERROR: extensions unavailable"))
    }

    @Test
    fun `extended scene modes probe failure is recorded without throwing`() {
        val chars = mock(CameraCharacteristics::class.java)
        val output = StringBuilder()

        val completed = appendProbeSection(output, "extended-scene-modes") {
            invokeVendorProbeMethod("appendExtendedSceneModeProbe", output, chars)
        }

        val text = output.toString()
        assertFalse(completed, "expected completed=false but got true; output: $text")
        assertTrue(text.contains("[extended-scene-modes] ERROR:"),
            "expected ERROR header in output but got: $text")
        assertTrue(
            text.contains("InvocationTargetException") || text.contains("not accessible"),
            "expected reflective exception or compileSdk message in output but got: $text"
        )
    }

    @Test
    fun `extended scene modes probe formats known mode labels`() {
        val method = VendorCameraProbe::class.java.getDeclaredMethod(
            "extendedSceneModeLabel", Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        assertEquals("disabled", method.invoke(VendorCameraProbe, 0))
        assertEquals("bokeh-still-capture", method.invoke(VendorCameraProbe, 1))
        assertEquals("bokeh-continuous", method.invoke(VendorCameraProbe, 2))
        assertEquals("vendor-0x40", method.invoke(VendorCameraProbe, 0x40))
        assertEquals("vendor-0xff", method.invoke(VendorCameraProbe, 0xff))
        assertEquals("unknown-0x3", method.invoke(VendorCameraProbe, 3))
        assertEquals("unknown-0x3f", method.invoke(VendorCameraProbe, 0x3f))
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
