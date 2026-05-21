package com.opencamera.app

import android.graphics.RectF
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewContentGeometryTest {

    // With isReturnDefaultValues=true, RectF methods (width/height/centerX/centerY) return 0.
    // We verify geometry correctness via computeFrameRect (tested in PreviewOverlayGeometryTest)
    // and here verify PreviewContentGeometry structure and field values.

    @Test
    fun `content geometry with no ratio returns full view rect`() {
        val geo = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920
        )
        assertEquals(0f, geo.contentRect.left)
        assertEquals(0f, geo.contentRect.top)
        assertEquals(1080f, geo.contentRect.right)
        assertEquals(1920f, geo.contentRect.bottom)
        assertEquals(geo.contentRect.left, geo.activeFrameRect.left)
        assertEquals(geo.contentRect.top, geo.activeFrameRect.top)
        assertEquals(geo.contentRect.right, geo.activeFrameRect.right)
        assertEquals(geo.contentRect.bottom, geo.activeFrameRect.bottom)
    }

    @Test
    fun `content geometry with ratio produces frame inside content rect`() {
        val geo = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920,
            ratioWidth = 4,
            ratioHeight = 3
        )
        assertTrue(geo.activeFrameRect.left >= geo.contentRect.left)
        assertTrue(geo.activeFrameRect.top >= geo.contentRect.top)
        assertTrue(geo.activeFrameRect.right <= geo.contentRect.right)
        assertTrue(geo.activeFrameRect.bottom <= geo.contentRect.bottom)
    }

    @Test
    fun `horizontal padding shrinks content rect`() {
        val geo = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920,
            horizontalPaddingPx = 24f
        )
        assertEquals(24f, geo.contentRect.left)
        assertEquals(1056f, geo.contentRect.right)
        assertEquals(0f, geo.contentRect.top)
        assertEquals(1920f, geo.contentRect.bottom)
    }

    @Test
    fun `view dimensions are preserved`() {
        val geo = previewContentGeometry(
            viewWidth = 2400,
            viewHeight = 1080,
            ratioWidth = 16,
            ratioHeight = 9
        )
        assertEquals(2400, geo.viewWidth)
        assertEquals(1080, geo.viewHeight)
    }

    @Test
    fun `frame rect fields match computeFrameRect output`() {
        val geo = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920,
            ratioWidth = 4,
            ratioHeight = 3
        )
        val fr = computeFrameRect(1080, 1920, 4, 3)
        assertEquals(fr.left, geo.activeFrameRect.left)
        assertEquals(fr.top, geo.activeFrameRect.top)
        assertEquals(fr.right, geo.activeFrameRect.right)
        assertEquals(fr.bottom, geo.activeFrameRect.bottom)
    }

    @Test
    fun `frame rect fields match computeFrameRect for landscape 16_9`() {
        val geo = previewContentGeometry(
            viewWidth = 2400,
            viewHeight = 1080,
            ratioWidth = 16,
            ratioHeight = 9
        )
        val fr = computeFrameRect(2400, 1080, 16, 9)
        assertEquals(fr.left, geo.activeFrameRect.left)
        assertEquals(fr.top, geo.activeFrameRect.top)
        assertEquals(fr.right, geo.activeFrameRect.right)
        assertEquals(fr.bottom, geo.activeFrameRect.bottom)
    }

    @Test
    fun `frame rect fields match computeFrameRect for square`() {
        val geo = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920,
            ratioWidth = 1,
            ratioHeight = 1
        )
        val fr = computeFrameRect(1080, 1920, 1, 1)
        assertEquals(fr.left, geo.activeFrameRect.left)
        assertEquals(fr.top, geo.activeFrameRect.top)
        assertEquals(fr.right, geo.activeFrameRect.right)
        assertEquals(fr.bottom, geo.activeFrameRect.bottom)
    }

    @Test
    fun `frame is centered in content rect`() {
        val configs = listOf(
            Triple(1080, 2400, listOf(1 to 1, 4 to 3, 16 to 9)),
            Triple(2400, 1080, listOf(1 to 1, 4 to 3, 16 to 9)),
            Triple(1080, 1920, listOf(1 to 1, 4 to 3, 16 to 9))
        )
        for ((vw, vh, ratios) in configs) {
            for ((rw, rh) in ratios) {
                val geo = previewContentGeometry(vw, vh, rw, rh)
                val contentCenterX = (geo.contentRect.left + geo.contentRect.right) / 2f
                val contentCenterY = (geo.contentRect.top + geo.contentRect.bottom) / 2f
                val frameCenterX = (geo.activeFrameRect.left + geo.activeFrameRect.right) / 2f
                val frameCenterY = (geo.activeFrameRect.top + geo.activeFrameRect.bottom) / 2f
                assertEquals(
                    contentCenterX, frameCenterX, 1f,
                    "Center X mismatch for ${vw}x${vh} ratio ${rw}:${rh}"
                )
                assertEquals(
                    contentCenterY, frameCenterY, 1f,
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
        }
    }
}
