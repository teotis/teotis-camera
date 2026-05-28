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
}
