package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerceptualColorGamutGuardTest {
    @Test
    fun `already pure blue receives gentle chroma compression`() {
        val guarded = PerceptualColorGamutGuard.saturationFor(
            red = 20f,
            green = 127f,
            blue = 250f,
            requestedSaturation = 1.11f
        )

        assertTrue(guarded <= 0.74f, "Expected high-purity blue compression, saturation=$guarded")
    }

    @Test
    fun `moderate blue retains room for visible color lab intent`() {
        val guarded = PerceptualColorGamutGuard.saturationFor(
            red = 58f,
            green = 116f,
            blue = 198f,
            requestedSaturation = 1.11f
        )

        assertTrue(guarded > 1f, "Moderate blue should retain a restrained boost, saturation=$guarded")
        assertTrue(guarded < 1.11f)
    }

    @Test
    fun `dark over-pure water receives relative saturation compression`() {
        val guarded = PerceptualColorGamutGuard.saturationFor(
            red = 5f,
            green = 55f,
            blue = 105f,
            requestedSaturation = 1.11f
        )

        assertTrue(guarded <= 0.74f, "Dark pure blue water should regain chroma reserve, saturation=$guarded")
    }

    @Test
    fun `warm accent does not enter cool chroma compression`() {
        val guarded = PerceptualColorGamutGuard.saturationFor(
            red = 226f,
            green = 72f,
            blue = 32f,
            requestedSaturation = 1.11f
        )

        assertTrue(guarded in 1f..1.01f, "Warm accent should not be actively compressed, saturation=$guarded")
    }

    @Test
    fun `gamut compression preserves valid black`() {
        val output = FloatArray(3)

        PerceptualColorGamutGuard.compressToOutputGamut(0f, 0f, 0f, output)

        assertEquals(listOf(0f, 0f, 0f), output.toList())
    }
}
