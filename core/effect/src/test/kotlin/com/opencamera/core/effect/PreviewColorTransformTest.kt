package com.opencamera.core.effect

import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PreviewColorFidelity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewColorTransformTest {

    @Test
    fun `identity transform for null spec`() {
        val transform = PreviewColorTransform.fromSpec(null)

        assertTrue(transform.isIdentity)
        assertEquals(PreviewColorFidelity.NONE, transform.fidelity)
    }

    @Test
    fun `default spec returns no matrix`() {
        assertNull(PreviewColorMatrixBuilder.buildMatrix(FilterRenderSpec()))
    }

    @Test
    fun `saturation 0 produces grayscale matrix`() {
        val matrix = PreviewColorMatrixBuilder.buildMatrix(FilterRenderSpec(saturation = 0f))

        assertNotNull(matrix)
        assertEquals(0.213f, matrix[0], 0.001f)
        assertEquals(0.715f, matrix[1], 0.001f)
        assertEquals(0.072f, matrix[2], 0.001f)
    }

    @Test
    fun `warmth shift adds to red and subtracts from blue`() {
        val transform = PreviewColorTransform.fromSpec(FilterRenderSpec(warmthShift = 10))
        val matrix = transform.matrix

        assertNotNull(matrix)
        assertTrue(matrix[4] > 0f, "R translation should be positive for warm shift")
        assertTrue(matrix[14] < 0f, "B translation should be negative for warm shift")
        assertEquals(PreviewColorFidelity.APPROXIMATE, transform.fidelity)
    }

    @Test
    fun `contrast scales around midpoint`() {
        val matrix = PreviewColorMatrixBuilder.buildMatrix(FilterRenderSpec(contrast = 1.5f))

        assertNotNull(matrix)
        assertEquals(1.5f, matrix[0], 0.001f)
        assertEquals(1.5f, matrix[6], 0.001f)
        assertEquals(1.5f, matrix[12], 0.001f)
        assertEquals(-64f, matrix[4], 0.5f)
    }

    @Test
    fun `brightness shifts all channels equally`() {
        val matrix = PreviewColorMatrixBuilder.buildMatrix(FilterRenderSpec(brightnessShift = 20))

        assertNotNull(matrix)
        assertEquals(20f, matrix[4], 0.001f)
        assertEquals(20f, matrix[9], 0.001f)
        assertEquals(20f, matrix[14], 0.001f)
    }

    @Test
    fun `multiply identity preserves matrix`() {
        val matrix = floatArrayOf(
            2f, 0f, 0f, 0f, 10f,
            0f, 1.5f, 0f, 0f, 5f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )

        val result = PreviewColorTransform.multiply(matrix, PreviewColorMatrixBuilder.IDENTITY)

        for (i in 0..19) {
            assertEquals(matrix[i], result[i], 0.001f, "Element $i should be preserved")
        }
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
        assertNotNull(transform.matrix)
        assertEquals(20, transform.matrix!!.size)
    }
}
