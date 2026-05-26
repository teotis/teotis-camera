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
    fun `active capture frame is centered in full preview content`() {
        val rect = computeFrameRect(
            viewWidth = 1080,
            viewHeight = 1920,
            ratioWidth = 4,
            ratioHeight = 3
        )
        assertApprox(1080f, rect.width)
        assertApprox(0f, rect.left)
        assertRectCentered(1080, 1920, rect)
    }

    @Test
    fun `active capture frame stays centered in full preview content despite bottom controls`() {
        val rect = computeFrameRect(
            viewWidth = 1080,
            viewHeight = 2400,
            ratioWidth = 4,
            ratioHeight = 3
        )

        assertEquals(1200f, rect.centerY, 1f)
    }

    // --- scaleFrameRect tests ---

    @Test
    fun `scaleFrameRect with scale 1_0 returns same rect`() {
        val rect = FrameRect(0f, 240f, 1080f, 1680f)
        val result = scaleFrameRect(rect, 1f)
        assertApprox(0f, result.left)
        assertApprox(240f, result.top)
        assertApprox(1080f, result.right)
        assertApprox(1680f, result.bottom)
    }

    @Test
    fun `scaleFrameRect with scale 0_5 halves rect around center`() {
        val rect = FrameRect(0f, 0f, 1080f, 1440f)
        val result = scaleFrameRect(rect, 0.5f)
        // center = (540, 720), half-size = (270, 360)
        assertApprox(270f, result.left)
        assertApprox(360f, result.top)
        assertApprox(810f, result.right)
        assertApprox(1080f, result.bottom)
    }

    @Test
    fun `scaleFrameRect preserves center point`() {
        val rect = FrameRect(100f, 200f, 500f, 800f)
        val result = scaleFrameRect(rect, 0.3f)
        assertApprox(300f, result.centerX)
        assertApprox(500f, result.centerY)
    }

    // --- zoom-scaled frame rect tests ---

    @Test
    fun `zoom 2x halves the frame rect dimensions`() {
        val base = computeFrameRect(1080, 1920, 4, 3)
        val scaled = scaleFrameRect(base, 1f / 2f)
        assertApprox(base.width / 2f, scaled.width)
        assertApprox(base.height / 2f, scaled.height)
    }

    @Test
    fun `zoom 1x leaves frame rect unchanged`() {
        val base = computeFrameRect(1080, 1920, 4, 3)
        val scaled = scaleFrameRect(base, 1f / 1f)
        assertApprox(base.width, scaled.width)
        assertApprox(base.height, scaled.height)
    }

    @Test
    fun `zoom 3x produces third-sized frame centered`() {
        val base = computeFrameRect(1080, 1920, 4, 3)
        val scaled = scaleFrameRect(base, 1f / 3f)
        assertApprox(base.centerX, scaled.centerX)
        assertApprox(base.centerY, scaled.centerY)
        assertApprox(base.width / 3f, scaled.width)
        assertApprox(base.height / 3f, scaled.height)
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

    // --- clampReticleCenter tests ---

    @Test
    fun `center tap in full-view bounds stays at center`() {
        val result = clampReticleCenter(540f, 960f, 24f, 8f, 0f, 0f, 1080f, 1920f)
        assertEquals(540f, result.x)
        assertEquals(960f, result.y)
    }

    @Test
    fun `tap near left edge is clamped`() {
        val result = clampReticleCenter(5f, 960f, 24f, 8f, 0f, 0f, 1080f, 1920f)
        assertEquals(32f, result.x) // 0 + 24 + 8
    }

    @Test
    fun `tap near right edge is clamped`() {
        val result = clampReticleCenter(1075f, 960f, 24f, 8f, 0f, 0f, 1080f, 1920f)
        assertEquals(1048f, result.x) // 1080 - 24 - 8
    }

    @Test
    fun `tap near top edge is clamped`() {
        val result = clampReticleCenter(540f, 5f, 24f, 8f, 0f, 0f, 1080f, 1920f)
        assertEquals(32f, result.y) // 0 + 24 + 8
    }

    @Test
    fun `tap near bottom edge is clamped`() {
        val result = clampReticleCenter(540f, 1915f, 24f, 8f, 0f, 0f, 1080f, 1920f)
        assertEquals(1888f, result.y) // 1920 - 24 - 8
    }

    @Test
    fun `reticle stays within inset 4_3 frame`() {
        // 16:9 view with 4:3 frame: frame has horizontal insets
        val result = clampReticleCenter(100f, 960f, 24f, 8f, 180f, 0f, 900f, 1920f)
        assertEquals(212f, result.x) // 180 + 24 + 8
    }

    @Test
    fun `reticle at exact boundary remains unchanged`() {
        val result = clampReticleCenter(32f, 32f, 24f, 8f, 0f, 0f, 1080f, 1920f)
        assertEquals(32f, result.x)
        assertEquals(32f, result.y)
    }

    // --- content-bounds-aware frame rect tests ---
    // When the preview content aspect differs from the view, the content rect
    // is a fitCenter sub-rect of the view. Frame overlays must fit inside it.

    @Test
    fun `4_3 preview content in portrait 16_9 view is letterboxed`() {
        // View: 1080x1920 (9:16). Preview 4:3 => content = 1080x1440 centered.
        val content = computeFrameRect(1080, 1920, 4, 3)
        assertApprox(1080f, content.width)
        assertApprox(1440f, content.height)
        assertRectCentered(1080, 1920, content)
        // Content is smaller than full view
        assert(content.height < 1920f) { "Content should be letterboxed" }
    }

    @Test
    fun `4_3 preview content in landscape 16_9 view is pillarboxed`() {
        // View: 1920x1080. Preview 4:3 => content = 1440x1080 centered.
        val content = computeFrameRect(1920, 1080, 4, 3)
        assertApprox(1440f, content.width)
        assertApprox(1080f, content.height)
        assertRectCentered(1920, 1080, content)
        assert(content.width < 1920f) { "Content should be pillarboxed" }
    }

    @Test
    fun `16_9 preview content in portrait 4_3 view fills width`() {
        // View: 1080x1920. Preview 16:9 => content = 1080x1920 (9:16 = 16:9 portrait).
        val content = computeFrameRect(1080, 1920, 16, 9)
        assertApprox(1080f, content.width)
        assertApprox(1920f, content.height)
    }

    @Test
    fun `16_9 preview content in landscape 4_3 view is letterboxed`() {
        // View: 1920x1080. Preview 16:9 => content = 1920x1080 (exact match).
        val content = computeFrameRect(1920, 1080, 16, 9)
        assertApprox(1920f, content.width)
        assertApprox(1080f, content.height)
    }

    @Test
    fun `1_1 preview content in portrait view is centered square`() {
        val content = computeFrameRect(1080, 1920, 1, 1)
        assertApprox(1080f, content.width)
        assertApprox(1080f, content.height)
        assertRectCentered(1080, 1920, content)
    }

    @Test
    fun `1_1 preview content in landscape view is centered square`() {
        val content = computeFrameRect(1920, 1080, 1, 1)
        assertApprox(1080f, content.width)
        assertApprox(1080f, content.height)
        assertRectCentered(1920, 1080, content)
    }

    // --- nested frame inside preview content ---

    @Test
    fun `16_9 frame nested inside 4_3 preview content fits within content`() {
        // View: 1080x1920. Preview: 4:3 => content = 1080x1440.
        val content = computeFrameRect(1080, 1920, 4, 3)
        // Frame: 16:9 inside content
        val frame = computeFrameRect(content.width.toInt(), content.height.toInt(), 16, 9)
        // Frame must not exceed content
        assert(frame.width <= content.width + 1f) { "Frame width exceeds content" }
        assert(frame.height <= content.height + 1f) { "Frame height exceeds content" }
    }

    @Test
    fun `4_3 frame nested inside 16_9 preview content fits within content`() {
        // View: 1920x1080. Preview: 16:9 => content = 1920x1080.
        val content = computeFrameRect(1920, 1080, 16, 9)
        val frame = computeFrameRect(content.width.toInt(), content.height.toInt(), 4, 3)
        assert(frame.width <= content.width + 1f) { "Frame width exceeds content" }
        assert(frame.height <= content.height + 1f) { "Frame height exceeds content" }
    }

    @Test
    fun `1_1 frame nested inside letterboxed preview content fits`() {
        // View: 1080x1920. Preview: 4:3 => content = 1080x1440.
        val content = computeFrameRect(1080, 1920, 4, 3)
        val frame = computeFrameRect(content.width.toInt(), content.height.toInt(), 1, 1)
        assert(frame.width <= content.width + 1f) { "Frame width exceeds content" }
        assert(frame.height <= content.height + 1f) { "Frame height exceeds content" }
    }

    @Test
    fun `nested frame is centered within content rect`() {
        val content = computeFrameRect(1080, 1920, 4, 3)
        val frame = computeFrameRect(content.width.toInt(), content.height.toInt(), 16, 9)
        // Nested frame is local to the content rect; offset it before comparing.
        assertEquals(content.centerX, content.left + frame.centerX, 1f)
        assertEquals(content.centerY, content.top + frame.centerY, 1f)
    }
}
