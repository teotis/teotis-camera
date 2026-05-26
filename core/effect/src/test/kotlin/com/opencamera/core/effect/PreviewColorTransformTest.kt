package com.opencamera.core.effect

import com.opencamera.core.settings.FilterRenderSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewColorTransformTest {

    @Test
    fun `identity transform for null spec`() {
        val transform = PreviewColorTransform.fromSpec(null)
        assertTrue(transform.isIdentity)
        assertEquals(PreviewColorTransform.IDENTITY, transform)
    }

    @Test
    fun `identity transform for default spec`() {
        val transform = PreviewColorTransform.fromSpec(FilterRenderSpec())
        assertTrue(transform.isIdentity)
    }

    @Test
    fun `identity transform for no-op spec`() {
        val spec = FilterRenderSpec(
            brightnessShift = 0,
            contrast = 1f,
            saturation = 1f,
            warmthShift = 0,
            tintShift = 0,
            monochromeMix = 0f,
            shadowLift = 0f,
            highlightCompression = 0f,
            warmBoost = 0f,
            coolBoost = 0f
        )
        val transform = PreviewColorTransform.fromSpec(spec)
        assertTrue(transform.isIdentity)
    }

    @Test
    fun `saturation 0 produces grayscale matrix`() {
        val spec = FilterRenderSpec(saturation = 0f)
        val transform = PreviewColorTransform.fromSpec(spec)
        assertFalse(transform.isIdentity)
        // Grayscale: R row should be [0.213, 0.715, 0.072, 0, 0]
        val m = transform.matrix!!
        assertEquals(0.213f, m[0], 0.001f)
        assertEquals(0.715f, m[1], 0.001f)
        assertEquals(0.072f, m[2], 0.001f)
    }

    @Test
    fun `warmth shift adds to red and subtracts from blue`() {
        val spec = FilterRenderSpec(warmthShift = 10)
        val transform = PreviewColorTransform.fromSpec(spec)
        val m = transform.matrix!!
        // Translation column: R should be positive, B should be negative
        assertTrue(m[4] > 0f, "R translation should be positive for warm shift")
        assertTrue(m[14] < 0f, "B translation should be negative for warm shift")
    }

    @Test
    fun `contrast scales around midpoint`() {
        val spec = FilterRenderSpec(contrast = 1.5f)
        val transform = PreviewColorTransform.fromSpec(spec)
        val m = transform.matrix!!
        // Diagonal should be 1.5
        assertEquals(1.5f, m[0], 0.001f)
        assertEquals(1.5f, m[6], 0.001f)
        assertEquals(1.5f, m[12], 0.001f)
        // Translation should be 128 * (1 - 1.5) = -64
        assertEquals(-64f, m[4], 0.5f)
    }

    @Test
    fun `brightness shifts all channels equally`() {
        val spec = FilterRenderSpec(brightnessShift = 20)
        val transform = PreviewColorTransform.fromSpec(spec)
        val m = transform.matrix!!
        assertEquals(20f, m[4], 0.001f)
        assertEquals(20f, m[9], 0.001f)
        assertEquals(20f, m[14], 0.001f)
    }

    @Test
    fun `multiply identity preserves matrix`() {
        val m = floatArrayOf(
            2f, 0f, 0f, 0f, 10f,
            0f, 1.5f, 0f, 0f, 5f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        val result = PreviewColorTransform.multiply(m, identity)
        for (i in 0..19) {
            assertEquals(m[i], result[i], 0.001f, "Element $i should be preserved")
        }
    }

    @Test
    fun `multiply combines translation correctly`() {
        // First: scale 2x, translate 10
        val first = floatArrayOf(
            2f, 0f, 0f, 0f, 10f,
            0f, 2f, 0f, 0f, 10f,
            0f, 0f, 2f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        )
        // Second: scale 0.5x, translate 5
        val second = floatArrayOf(
            0.5f, 0f, 0f, 0f, 5f,
            0f, 0.5f, 0f, 0f, 5f,
            0f, 0f, 0.5f, 0f, 5f,
            0f, 0f, 0f, 1f, 0f
        )
        val result = PreviewColorTransform.multiply(first, second)
        // Expected: scale = 2*0.5 = 1, translate = 2*5 + 10 = 20
        assertEquals(1f, result[0], 0.001f)
        assertEquals(20f, result[4], 0.001f)
    }

    @Test
    fun `combined spec produces non-identity transform`() {
        val spec = FilterRenderSpec(
            saturation = 0.8f,
            contrast = 1.2f,
            warmthShift = 5,
            brightnessShift = 10,
            shadowLift = 0.2f
        )
        val transform = PreviewColorTransform.fromSpec(spec)
        assertFalse(transform.isIdentity)
        assertEquals(20, transform.matrix!!.size)
    }
}
