package com.opencamera.app

import com.opencamera.core.session.PreviewRatio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PreviewContentGeometryTest {

    // PreviewContentGeometry uses android.graphics.RectF which is not fully mockable
    // in pure JVM tests (isReturnDefaultValues returns 0 for all fields).
    // Geometry correctness is verified via computeFrameRect in PreviewOverlayGeometryTest.
    // Here we verify the structural contract of the geometry helper.

    @Test
    fun `previewContentGeometry returns non-null for valid inputs`() {
        val geo = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920,
            ratioWidth = 4,
            ratioHeight = 3
        )
        assertNotNull(geo)
        assertEquals(1080, geo.viewWidth)
        assertEquals(1920, geo.viewHeight)
        assertNotNull(geo.contentRect)
        assertNotNull(geo.activeFrameRect)
    }

    @Test
    fun `previewContentGeometry with no ratio returns geometry`() {
        val geo = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920
        )
        assertNotNull(geo)
        assertEquals(1080, geo.viewWidth)
        assertEquals(1920, geo.viewHeight)
    }

    @Test
    fun `previewContentGeometry preserves view dimensions`() {
        val configs = listOf(
            1080 to 2400,
            2400 to 1080,
            1080 to 1920,
            1920 to 1080
        )
        for ((w, h) in configs) {
            val geo = previewContentGeometry(viewWidth = w, viewHeight = h)
            assertEquals(w, geo.viewWidth, "viewWidth mismatch for ${w}x${h}")
            assertEquals(h, geo.viewHeight, "viewHeight mismatch for ${w}x${h}")
        }
    }

    @Test
    fun `previewContentGeometry with previewContentAspect preserves view dimensions`() {
        val geo = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920,
            previewContentAspect = PreviewContentAspect(4, 3)
        )
        assertEquals(1080, geo.viewWidth)
        assertEquals(1920, geo.viewHeight)
    }

    @Test
    fun `previewContentGeometry with null previewContentAspect returns valid geometry`() {
        val geo = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920,
            previewContentAspect = null
        )
        assertNotNull(geo)
        assertEquals(1080, geo.viewWidth)
        assertEquals(1920, geo.viewHeight)
    }

    // --- previewRatioToContentAspect tests ---

    @Test
    fun `previewRatioToContentAspect FULL returns null`() {
        assertNull(previewRatioToContentAspect(PreviewRatio.FULL))
    }

    @Test
    fun `previewRatioToContentAspect RATIO_4_3 returns 4_3`() {
        val result = previewRatioToContentAspect(PreviewRatio.RATIO_4_3)
        assertNotNull(result)
        assertEquals(4, result.width)
        assertEquals(3, result.height)
    }

    @Test
    fun `previewRatioToContentAspect RATIO_16_9 returns 16_9`() {
        val result = previewRatioToContentAspect(PreviewRatio.RATIO_16_9)
        assertNotNull(result)
        assertEquals(16, result.width)
        assertEquals(9, result.height)
    }

    @Test
    fun `previewRatioToContentAspect RATIO_1_1 returns 1_1`() {
        val result = previewRatioToContentAspect(PreviewRatio.RATIO_1_1)
        assertNotNull(result)
        assertEquals(1, result.width)
        assertEquals(1, result.height)
    }

    // --- previewContentGeometry content rect computation tests ---
    // These verify the content rect is computed correctly by testing the underlying
    // computeFrameRect logic (which previewContentGeometry delegates to).

    @Test
    fun `4_3 preview in 16_9 portrait view produces letterboxed content rect`() {
        // View: 1080x1920 (9:16). Preview: 4:3.
        // computeFrameRect(1080, 1920, 4, 3) => 1080x1440, centered vertically.
        // This means content rect is smaller than full view (letterboxed).
        val contentRect = computeFrameRect(1080, 1920, 4, 3)
        // Full view height is 1920, content height is 1440 => letterboxed
        assertEquals(1080f, contentRect.width, 1f)
        assertEquals(1440f, contentRect.height, 1f)
        // Centered vertically
        assertEquals(960f, contentRect.centerY, 1f)
    }

    @Test
    fun `4_3 preview in 16_9 landscape view produces pillarboxed content rect`() {
        // View: 1920x1080 (16:9). Preview: 4:3.
        // computeFrameRect(1920, 1080, 4, 3) => 1440x1080, centered horizontally.
        val contentRect = computeFrameRect(1920, 1080, 4, 3)
        assertEquals(1440f, contentRect.width, 1f)
        assertEquals(1080f, contentRect.height, 1f)
        assertEquals(960f, contentRect.centerX, 1f)
    }

    @Test
    fun `16_9 preview in 4_3 portrait view produces pillarboxed content rect`() {
        // View: 1080x1920. Preview: 16:9.
        // computeFrameRect(1080, 1920, 16, 9) => 1080x1920 (exact match 9:16)
        val contentRect = computeFrameRect(1080, 1920, 16, 9)
        assertEquals(1080f, contentRect.width, 1f)
        assertEquals(1920f, contentRect.height, 1f)
    }

    @Test
    fun `16_9 preview in 4_3 landscape view produces letterboxed content rect`() {
        // View: 1920x1080. Preview: 16:9.
        // computeFrameRect(1920, 1080, 16, 9) => 1920x1080 (exact match)
        val contentRect = computeFrameRect(1920, 1080, 16, 9)
        assertEquals(1920f, contentRect.width, 1f)
        assertEquals(1080f, contentRect.height, 1f)
    }

    @Test
    fun `1_1 preview in portrait view produces centered square content rect`() {
        // View: 1080x1920. Preview: 1:1.
        val contentRect = computeFrameRect(1080, 1920, 1, 1)
        assertEquals(1080f, contentRect.width, 1f)
        assertEquals(1080f, contentRect.height, 1f)
        assertEquals(540f, contentRect.centerX, 1f)
        assertEquals(960f, contentRect.centerY, 1f)
    }

    @Test
    fun `1_1 preview in landscape view produces centered square content rect`() {
        // View: 1920x1080. Preview: 1:1.
        val contentRect = computeFrameRect(1920, 1080, 1, 1)
        assertEquals(1080f, contentRect.width, 1f)
        assertEquals(1080f, contentRect.height, 1f)
        assertEquals(960f, contentRect.centerX, 1f)
        assertEquals(540f, contentRect.centerY, 1f)
    }

    // --- Frame overlay must not exceed content bounds ---

    @Test
    fun `16_9 frame inside 4_3 preview content does not exceed content bounds`() {
        // View: 1080x1920. Preview: 4:3 => content = 1080x1440.
        // Frame: 16:9 inside content 1080x1440.
        val contentRect = computeFrameRect(1080, 1920, 4, 3)
        val frameRect = computeFrameRect(
            contentRect.width.toInt(),
            contentRect.height.toInt(),
            16, 9
        )
        // Frame must fit within content
        assert(frameRect.width <= contentRect.width + 1f) {
            "Frame width ${frameRect.width} exceeds content width ${contentRect.width}"
        }
        assert(frameRect.height <= contentRect.height + 1f) {
            "Frame height ${frameRect.height} exceeds content height ${contentRect.height}"
        }
    }

    @Test
    fun `4_3 frame inside 16_9 preview content does not exceed content bounds`() {
        // View: 1920x1080. Preview: 16:9 => content = 1920x1080.
        // Frame: 4:3 inside content 1920x1080.
        val contentRect = computeFrameRect(1920, 1080, 16, 9)
        val frameRect = computeFrameRect(
            contentRect.width.toInt(),
            contentRect.height.toInt(),
            4, 3
        )
        assert(frameRect.width <= contentRect.width + 1f) {
            "Frame width ${frameRect.width} exceeds content width ${contentRect.width}"
        }
        assert(frameRect.height <= contentRect.height + 1f) {
            "Frame height ${frameRect.height} exceeds content height ${contentRect.height}"
        }
    }

    @Test
    fun `1_1 frame inside 4_3 portrait preview content does not exceed content bounds`() {
        // View: 1080x1920. Preview: 4:3 => content = 1080x1440.
        val contentRect = computeFrameRect(1080, 1920, 4, 3)
        val frameRect = computeFrameRect(
            contentRect.width.toInt(),
            contentRect.height.toInt(),
            1, 1
        )
        assert(frameRect.width <= contentRect.width + 1f) {
            "Frame width ${frameRect.width} exceeds content width ${contentRect.width}"
        }
        assert(frameRect.height <= contentRect.height + 1f) {
            "Frame height ${frameRect.height} exceeds content height ${contentRect.height}"
        }
    }

    @Test
    fun `16_9 frame inside 4_3 landscape preview content does not exceed content bounds`() {
        // View: 1920x1080. Preview: 4:3 => content = 1440x1080.
        val contentRect = computeFrameRect(1920, 1080, 4, 3)
        val frameRect = computeFrameRect(
            contentRect.width.toInt(),
            contentRect.height.toInt(),
            16, 9
        )
        assert(frameRect.width <= contentRect.width + 1f) {
            "Frame width ${frameRect.width} exceeds content width ${contentRect.width}"
        }
        assert(frameRect.height <= contentRect.height + 1f) {
            "Frame height ${frameRect.height} exceeds content height ${contentRect.height}"
        }
    }
}
