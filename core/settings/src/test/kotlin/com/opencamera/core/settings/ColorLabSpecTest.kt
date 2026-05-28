package com.opencamera.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorLabSpecTest {
    @Test
    fun `neutral palette keeps base render spec unchanged`() {
        val base = FilterRenderSpec(
            brightnessShift = 3,
            contrast = 1.05f,
            saturation = 0.96f,
            warmthShift = -2,
            tintShift = 1,
            shadowLift = 0.04f
        )

        val result = ColorLabSpec(colorAxis = 0f, toneAxis = 0f).applyTo(base)

        assertEquals(base, result)
    }

    @Test
    fun `zero strength keeps base render spec unchanged even at palette edges`() {
        val base = FilterRenderSpec(
            brightnessShift = -4,
            contrast = 1.12f,
            saturation = 1.08f,
            warmthShift = 3
        )

        val result = ColorLabSpec(
            colorAxis = 1f,
            toneAxis = -1f,
            strength = 0f
        ).applyTo(base)

        assertEquals(base, result)
    }

    @Test
    fun `right color axis warms image without enabling cool boost`() {
        val result = ColorLabSpec(colorAxis = 1f, toneAxis = 0f).applyTo(FilterRenderSpec())

        assertTrue(result.warmthShift >= 18)
        assertTrue(result.warmBoost >= 0.24f)
        assertEquals(0f, result.coolBoost)
        assertTrue(result.saturation >= 1.20f)
    }

    @Test
    fun `left color axis cools image without enabling warm boost`() {
        val result = ColorLabSpec(colorAxis = -1f, toneAxis = 0f).applyTo(FilterRenderSpec())

        assertTrue(result.warmthShift <= -18)
        assertTrue(result.coolBoost >= 0.24f)
        assertEquals(0f, result.warmBoost)
        assertTrue(result.saturation >= 1.20f)
    }

    @Test
    fun `positive tone axis creates airy lifted tone`() {
        val result = ColorLabSpec(colorAxis = 0f, toneAxis = 1f).applyTo(FilterRenderSpec())

        assertTrue(result.brightnessShift >= 14)
        assertTrue(result.contrast <= 0.84f)
        assertTrue(result.shadowLift >= 0.24f)
        assertTrue(result.highlightCompression > 0f)
    }

    @Test
    fun `negative tone axis creates deep contrast tone`() {
        val result = ColorLabSpec(colorAxis = 0f, toneAxis = -1f).applyTo(FilterRenderSpec())

        assertTrue(result.brightnessShift <= -12)
        assertTrue(result.contrast >= 1.20f)
        assertEquals(0f, result.shadowLift)
        assertTrue(result.highlightCompression >= 0.16f)
    }

    @Test
    fun `palette inputs are clamped before mapping`() {
        val fromOverflow = ColorLabSpec(
            colorAxis = 4f,
            toneAxis = -3f,
            strength = 5f
        ).applyTo(FilterRenderSpec())

        val fromEdge = ColorLabSpec(
            colorAxis = 1f,
            toneAxis = -1f,
            strength = 1f
        ).applyTo(FilterRenderSpec())

        assertEquals(fromEdge, fromOverflow)
    }

    @Test
    fun `mapping reports normalized coordinates for ui reticle state`() {
        val mapping = ColorLabSpec(
            colorAxis = -2f,
            toneAxis = 0.5f,
            strength = 1.4f,
            presetId = "warm-air"
        ).toMapping()

        assertEquals(-1f, mapping.spec.colorAxis)
        assertEquals(0.5f, mapping.spec.toneAxis)
        assertEquals(1f, mapping.spec.strength)
        assertEquals("warm-air", mapping.spec.presetId)
        assertTrue(mapping.description.contains("ColorLabSpec"))
    }

    @Test
    fun `warm deep corner shows strong warmth contrast and saturation`() {
        val result = ColorLabSpec(colorAxis = 1f, toneAxis = -1f).applyTo(FilterRenderSpec())

        assertTrue(result.warmthShift >= 18)
        assertTrue(result.warmBoost >= 0.24f)
        assertTrue(result.contrast >= 1.20f)
        assertTrue(result.saturation >= 1.20f)
        assertEquals(0f, result.coolBoost)
    }

    @Test
    fun `cool airy corner shows cool tone with shadow lift and brightness`() {
        val result = ColorLabSpec(colorAxis = -1f, toneAxis = 1f).applyTo(FilterRenderSpec())

        assertTrue(result.warmthShift <= -18)
        assertTrue(result.coolBoost >= 0.24f)
        assertTrue(result.brightnessShift >= 14)
        assertTrue(result.shadowLift >= 0.24f)
        assertEquals(0f, result.warmBoost)
    }
}
