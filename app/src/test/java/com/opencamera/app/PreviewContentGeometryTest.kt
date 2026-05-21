package com.opencamera.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewContentGeometryTest {

    @Test
    fun `portrait 1080x2400 with 16_9 ratio produces centered 9_16 frame`() {
        val geo = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 2400,
            ratioWidth = 16,
            ratioHeight = 9
        )
        // Oriented: 9:16. targetRatio = 0.5625, availableRatio = 1080/2400 = 0.45
        // targetRatio > availableRatio => width-limited: w=1080, h=1080/0.5625=1920
        assertApprox(1080f, geo.activeFrameRect.width())
        assertApprox(1920f, geo.activeFrameRect.height())
        assertRectCentered(geo.contentRect, geo.activeFrameRect)
    }

    @Test
    fun `landscape 2400x1080 with 16_9 ratio produces centered 16_9 frame`() {
        val geo = previewContentGeometry(
            viewWidth = 2400,
            viewHeight = 1080,
            ratioWidth = 16,
            ratioHeight = 9
        )
        // Oriented: 16:9. targetRatio = 1.778, availableRatio = 2400/1080 = 2.222
        // targetRatio < availableRatio => height-limited: h=1080, w=1080*1.778=1920
        assertApprox(1920f, geo.activeFrameRect.width())
        assertApprox(1080f, geo.activeFrameRect.height())
        assertRectCentered(geo.contentRect, geo.activeFrameRect)
    }

    @Test
    fun `portrait 1080x2400 with 4_3 ratio produces centered 3_4 frame`() {
        val geo = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 2400,
            ratioWidth = 4,
            ratioHeight = 3
        )
        // Oriented: 3:4. targetRatio = 0.75, availableRatio = 0.45
        // targetRatio > availableRatio => width-limited: w=1080, h=1080/0.75=1440
        assertApprox(1080f, geo.activeFrameRect.width())
        assertApprox(1440f, geo.activeFrameRect.height())
        assertRectCentered(geo.contentRect, geo.activeFrameRect)
    }

    @Test
    fun `landscape 2400x1080 with 4_3 ratio produces centered 4_3 frame`() {
        val geo = previewContentGeometry(
            viewWidth = 2400,
            viewHeight = 1080,
            ratioWidth = 4,
            ratioHeight = 3
        )
        // Oriented: 4:3. targetRatio = 1.333, availableRatio = 2.222
        // targetRatio < availableRatio => height-limited: h=1080, w=1080*1.333=1440
        assertApprox(1440f, geo.activeFrameRect.width())
        assertApprox(1080f, geo.activeFrameRect.height())
        assertRectCentered(geo.contentRect, geo.activeFrameRect)
    }

    @Test
    fun `portrait 1080x2400 with 1_1 ratio produces centered square`() {
        val geo = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 2400,
            ratioWidth = 1,
            ratioHeight = 1
        )
        assertApprox(1080f, geo.activeFrameRect.width())
        assertApprox(1080f, geo.activeFrameRect.height())
        assertRectCentered(geo.contentRect, geo.activeFrameRect)
    }

    @Test
    fun `landscape 2400x1080 with 1_1 ratio produces centered square`() {
        val geo = previewContentGeometry(
            viewWidth = 2400,
            viewHeight = 1080,
            ratioWidth = 1,
            ratioHeight = 1
        )
        assertApprox(1080f, geo.activeFrameRect.width())
        assertApprox(1080f, geo.activeFrameRect.height())
        assertRectCentered(geo.contentRect, geo.activeFrameRect)
    }

    @Test
    fun `frame center equals content center within 1px for all ratios`() {
        val configs = listOf(
            Triple(1080, 2400, listOf(1 to 1, 4 to 3, 16 to 9)),
            Triple(2400, 1080, listOf(1 to 1, 4 to 3, 16 to 9)),
            Triple(1080, 1920, listOf(1 to 1, 4 to 3, 16 to 9))
        )
        for ((vw, vh, ratios) in configs) {
            for ((rw, rh) in ratios) {
                val geo = previewContentGeometry(vw, vh, rw, rh)
                assertEquals(
                    geo.contentCenterX, geo.frameCenterX, 1f,
                    "Center X mismatch for ${vw}x${vh} ratio ${rw}:${rh}"
                )
                assertEquals(
                    geo.contentCenterY, geo.frameCenterY, 1f,
                    "Center Y mismatch for ${vw}x${vh} ratio ${rw}:${rh}"
                )
            }
        }
    }

    @Test
    fun `grid line positions are inside active frame rect`() {
        val geo = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920,
            ratioWidth = 4,
            ratioHeight = 3
        )
        val frame = geo.activeFrameRect
        val lines = gridLinePositions(
            frame.left, frame.top, frame.width(), frame.height(),
            listOf(1f / 3f, 2f / 3f)
        )
        lines.forEach { seg ->
            assertTrue(seg.x1 >= frame.left, "x1=${seg.x1} < left=${frame.left}")
            assertTrue(seg.x1 <= frame.right, "x1=${seg.x1} > right=${frame.right}")
            assertTrue(seg.y1 >= frame.top, "y1=${seg.y1} < top=${frame.top}")
            assertTrue(seg.y1 <= frame.bottom, "y1=${seg.y1} > bottom=${frame.bottom}")
            assertTrue(seg.x2 >= frame.left, "x2=${seg.x2} < left=${frame.left}")
            assertTrue(seg.x2 <= frame.right, "x2=${seg.x2} > right=${frame.right}")
            assertTrue(seg.y2 >= frame.top, "y2=${seg.y2} < top=${frame.top}")
            assertTrue(seg.y2 <= frame.bottom, "y2=${seg.y2} > bottom=${frame.bottom}")
        }
    }

    @Test
    fun `no ratio returns full view as active frame`() {
        val geo = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920
        )
        assertEquals(geo.contentRect.left, geo.activeFrameRect.left)
        assertEquals(geo.contentRect.top, geo.activeFrameRect.top)
        assertEquals(geo.contentRect.right, geo.activeFrameRect.right)
        assertEquals(geo.contentRect.bottom, geo.activeFrameRect.bottom)
    }

    @Test
    fun `horizontal padding shrinks content rect symmetrically`() {
        val geo = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920,
            horizontalPaddingPx = 24f
        )
        assertApprox(24f, geo.contentRect.left)
        assertApprox(1056f, geo.contentRect.right)
        assertApprox(0f, geo.contentRect.top)
        assertApprox(1920f, geo.contentRect.bottom)
    }

    private fun assertApprox(expected: Float, actual: Float, tolerance: Float = 1f) {
        assertEquals(expected, actual, tolerance)
    }

    private fun assertRectCentered(outer: android.graphics.RectF, inner: android.graphics.RectF) {
        assertEquals(outer.centerX(), inner.centerX(), 1f)
        assertEquals(outer.centerY(), inner.centerY(), 1f)
    }
}
