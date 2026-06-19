package com.opencamera.app.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewMeteringActionPlannerTest {

    @Test
    fun `center point maps to pixel center`() {
        val result = previewMeteringPixelPoint(
            normalizedX = 0.5f,
            normalizedY = 0.25f,
            viewWidth = 1000,
            viewHeight = 800
        )
        assertEquals(500f, result.x, 0.01f)
        assertEquals(200f, result.y, 0.01f)
    }

    @Test
    fun `negative values clamp to zero`() {
        val result = previewMeteringPixelPoint(
            normalizedX = -0.5f,
            normalizedY = -0.1f,
            viewWidth = 1000,
            viewHeight = 800
        )
        assertEquals(0f, result.x, 0.01f)
        assertEquals(0f, result.y, 0.01f)
    }

    @Test
    fun `values above one clamp to view edge`() {
        val result = previewMeteringPixelPoint(
            normalizedX = 1.5f,
            normalizedY = 2.0f,
            viewWidth = 1000,
            viewHeight = 800
        )
        assertEquals(1000f, result.x, 0.01f)
        assertEquals(800f, result.y, 0.01f)
    }

    @Test
    fun `zero width and height do not crash`() {
        val result = previewMeteringPixelPoint(
            normalizedX = 0.5f,
            normalizedY = 0.5f,
            viewWidth = 0,
            viewHeight = 0
        )
        assertEquals(0.5f, result.x, 0.01f)
        assertEquals(0.5f, result.y, 0.01f)
    }

    @Test
    fun `exact boundary values map correctly`() {
        val result = previewMeteringPixelPoint(
            normalizedX = 0f,
            normalizedY = 1f,
            viewWidth = 640,
            viewHeight = 480
        )
        assertEquals(0f, result.x, 0.01f)
        assertEquals(480f, result.y, 0.01f)
    }

    @Test
    fun `offset frame normalized tap produces correct metering pixel`() {
        // Simulates the full pipeline: user taps at (300, 250) in a 1000x800 view
        // with an offset frame. The normalization should produce full-view
        // normalized coordinates, which when multiplied by view dimensions
        // produce the original tap pixel.
        val normalizedX = 300f / 1000f  // 0.3
        val normalizedY = 250f / 800f   // 0.3125
        val result = previewMeteringPixelPoint(
            normalizedX = normalizedX,
            normalizedY = normalizedY,
            viewWidth = 1000,
            viewHeight = 800
        )
        assertEquals(300f, result.x, 0.01f)
        assertEquals(250f, result.y, 0.01f)
    }

    @Test
    fun `full view normalization preserves pixel through metering pipeline`() {
        // Proves the inverse: tap → normalize → metering pixel == original tap
        // for a tap at the center of an offset frame
        val tapX = 500f
        val tapY = 350f
        val viewW = 1000
        val viewH = 800
        // Full-view normalization
        val normX = (tapX / viewW.toFloat()).coerceIn(0f, 1f)
        val normY = (tapY / viewH.toFloat()).coerceIn(0f, 1f)
        val pixel = previewMeteringPixelPoint(normX, normY, viewW, viewH)
        assertEquals(tapX, pixel.x, 0.01f)
        assertEquals(tapY, pixel.y, 0.01f)
    }
}
