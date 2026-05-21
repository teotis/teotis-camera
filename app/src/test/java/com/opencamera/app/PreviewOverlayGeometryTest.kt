package com.opencamera.app

import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewOverlayGeometryTest {

    // --- orientedFrameRatio tests ---

    @Test
    fun `4_3 ratio in portrait becomes 3_4 oriented`() {
        val result = orientedFrameRatio(4, 3, PreviewDisplayOrientation.PORTRAIT)
        assertEquals(3, result.orientedWidth)
        assertEquals(4, result.orientedHeight)
    }

    @Test
    fun `4_3 ratio in landscape stays 4_3 oriented`() {
        val result = orientedFrameRatio(4, 3, PreviewDisplayOrientation.LANDSCAPE)
        assertEquals(4, result.orientedWidth)
        assertEquals(3, result.orientedHeight)
    }

    @Test
    fun `16_9 ratio in portrait becomes 9_16 oriented`() {
        val result = orientedFrameRatio(16, 9, PreviewDisplayOrientation.PORTRAIT)
        assertEquals(9, result.orientedWidth)
        assertEquals(16, result.orientedHeight)
    }

    @Test
    fun `16_9 ratio in landscape stays 16_9 oriented`() {
        val result = orientedFrameRatio(16, 9, PreviewDisplayOrientation.LANDSCAPE)
        assertEquals(16, result.orientedWidth)
        assertEquals(9, result.orientedHeight)
    }

    @Test
    fun `1_1 ratio is unchanged by orientation`() {
        val portrait = orientedFrameRatio(1, 1, PreviewDisplayOrientation.PORTRAIT)
        val landscape = orientedFrameRatio(1, 1, PreviewDisplayOrientation.LANDSCAPE)
        assertEquals(portrait, landscape)
        assertEquals(1, portrait.orientedWidth)
        assertEquals(1, portrait.orientedHeight)
    }

    // --- computeFrameRect orientation inference tests ---

    @Test
    fun `portrait view with 4_3 ratio produces portrait-tall frame`() {
        val rect = computeFrameRect(
            viewWidth = 1080,
            viewHeight = 1920,
            ratioWidth = 4,
            ratioHeight = 3
        )
        // Oriented ratio is 3:4. targetRatio = 0.75, availableRatio = 0.5625
        // targetRatio > availableRatio => width-limited: w=1080, h=1080/0.75=1440
        assertApprox(1080f, rect.width)
        assertApprox(1440f, rect.height)
        assertRectCentered(1080, 1920, rect)
    }

    @Test
    fun `landscape view with 4_3 ratio produces landscape-wide frame`() {
        val rect = computeFrameRect(
            viewWidth = 1920,
            viewHeight = 1080,
            ratioWidth = 4,
            ratioHeight = 3
        )
        // Oriented ratio is 4:3. targetRatio = 1.333, availableRatio = 1.778
        // targetRatio < availableRatio => height-limited: h=1080, w=1080*1.333=1440
        assertApprox(1440f, rect.width)
        assertApprox(1080f, rect.height)
        assertRectCentered(1920, 1080, rect)
    }

    @Test
    fun `portrait view with 16_9 ratio produces narrow tall frame`() {
        val rect = computeFrameRect(
            viewWidth = 1080,
            viewHeight = 1920,
            ratioWidth = 16,
            ratioHeight = 9
        )
        // Oriented: 9:16 = 0.5625. Available: 1080/1920 = 0.5625
        // targetRatio == availableRatio => fills entire view
        assertApprox(1080f, rect.width)
        assertApprox(1920f, rect.height)
    }

    @Test
    fun `landscape view with 16_9 ratio produces wide frame`() {
        val rect = computeFrameRect(
            viewWidth = 1920,
            viewHeight = 1080,
            ratioWidth = 16,
            ratioHeight = 9
        )
        // Oriented: 16:9 = 1.778. Available: 1920/1080 = 1.778
        // targetRatio == availableRatio => fills entire view
        assertApprox(1920f, rect.width)
        assertApprox(1080f, rect.height)
    }

    @Test
    fun `square ratio always produces centered square`() {
        val portrait = computeFrameRect(1080, 1920, 1, 1)
        val landscape = computeFrameRect(1920, 1080, 1, 1)
        assertApprox(1080f, portrait.width)
        assertApprox(1080f, portrait.height)
        assertRectCentered(1080, 1920, portrait)
        assertApprox(1080f, landscape.width)
        assertApprox(1080f, landscape.height)
        assertRectCentered(1920, 1080, landscape)
    }

    @Test
    fun `frame respects horizontal padding`() {
        val rect = computeFrameRect(
            viewWidth = 1080,
            viewHeight = 1920,
            ratioWidth = 4,
            ratioHeight = 3,
            horizontalPaddingPx = 24f
        )
        // Available width is 1080 - 48 = 1032
        assertApprox(1032f, rect.width)
        assertApprox(24f, rect.left)
    }

    // --- gridLinePositions tests ---

    @Test
    fun `rule of thirds grid lines at 1_3 and 2_3 within a 900x600 frame`() {
        val lines = gridLinePositions(0f, 0f, 900f, 600f, listOf(1f / 3f, 2f / 3f))
        assertEquals(4, lines.size)
        // Vertical lines at x=300 and x=600
        assertApprox(300f, lines[0].x1)
        assertApprox(600f, lines[2].x1)
        // Horizontal lines at y=200 and y=400
        assertApprox(200f, lines[1].y1)
        assertApprox(400f, lines[3].y1)
    }

    @Test
    fun `golden ratio grid lines at correct fractions`() {
        val lines = gridLinePositions(100f, 50f, 800f, 600f, listOf(0.38196602f, 0.61803395f))
        // Vertical line at x = 100 + 800*0.38196602 = 405.57
        assertApprox(405.57f, lines[0].x1, 0.5f)
        // Horizontal line at y = 50 + 600*0.38196602 = 279.18
        assertApprox(279.18f, lines[1].y1, 0.5f)
    }

    @Test
    fun `grid lines stay within frame bounds`() {
        val lines = gridLinePositions(50f, 30f, 400f, 300f, listOf(1f / 3f, 2f / 3f))
        lines.forEach { seg ->
            assertInRange(50f, 450f, seg.x1)
            assertInRange(50f, 450f, seg.x2)
            assertInRange(30f, 330f, seg.y1)
            assertInRange(30f, 330f, seg.y2)
        }
    }

    private fun assertApprox(expected: Float, actual: Float, tolerance: Float = 1f) {
        assertEquals(expected, actual, tolerance)
    }

    private fun assertRectCentered(viewW: Int, viewH: Int, rect: FrameRect) {
        val cx = viewW / 2f
        val cy = viewH / 2f
        assertEquals(cx, rect.centerX, 1f)
        assertEquals(cy, rect.centerY, 1f)
    }

    private fun assertInRange(min: Float, max: Float, value: Float) {
        assert(value in min..max) { "Expected $value to be in [$min, $max]" }
    }
}
