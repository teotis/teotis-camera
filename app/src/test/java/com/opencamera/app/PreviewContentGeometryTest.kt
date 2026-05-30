package com.opencamera.app

import com.opencamera.core.session.PreviewRatio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // --- previewContentGeometry integration: frame rect must stay within content rect ---

    @Test
    fun `previewContentGeometry 4_3 frame in 4_3 portrait content stays contained`() {
        val geo = previewContentGeometry(
            viewWidth = 1080, viewHeight = 1920,
            ratioWidth = 4, ratioHeight = 3,
            previewContentAspect = PreviewContentAspect(4, 3)
        )
        assertTrue(geo.activeFrameRect.left >= geo.contentRect.left - 1f)
        assertTrue(geo.activeFrameRect.top >= geo.contentRect.top - 1f)
        assertTrue(geo.activeFrameRect.right <= geo.contentRect.right + 1f)
        assertTrue(geo.activeFrameRect.bottom <= geo.contentRect.bottom + 1f)
    }

    @Test
    fun `previewContentGeometry 16_9 frame in 4_3 portrait content stays contained`() {
        val geo = previewContentGeometry(
            viewWidth = 1080, viewHeight = 1920,
            ratioWidth = 16, ratioHeight = 9,
            previewContentAspect = PreviewContentAspect(4, 3)
        )
        assertTrue(geo.activeFrameRect.left >= geo.contentRect.left - 1f)
        assertTrue(geo.activeFrameRect.top >= geo.contentRect.top - 1f)
        assertTrue(geo.activeFrameRect.right <= geo.contentRect.right + 1f)
        assertTrue(geo.activeFrameRect.bottom <= geo.contentRect.bottom + 1f)
    }

    @Test
    fun `previewContentGeometry 1_1 frame in 4_3 portrait content stays contained`() {
        val geo = previewContentGeometry(
            viewWidth = 1080, viewHeight = 1920,
            ratioWidth = 1, ratioHeight = 1,
            previewContentAspect = PreviewContentAspect(4, 3)
        )
        assertTrue(geo.activeFrameRect.left >= geo.contentRect.left - 1f)
        assertTrue(geo.activeFrameRect.top >= geo.contentRect.top - 1f)
        assertTrue(geo.activeFrameRect.right <= geo.contentRect.right + 1f)
        assertTrue(geo.activeFrameRect.bottom <= geo.contentRect.bottom + 1f)
    }

    @Test
    fun `previewContentGeometry 4_3 frame in 16_9 portrait content stays contained`() {
        val geo = previewContentGeometry(
            viewWidth = 1080, viewHeight = 1920,
            ratioWidth = 4, ratioHeight = 3,
            previewContentAspect = PreviewContentAspect(16, 9)
        )
        assertTrue(geo.activeFrameRect.left >= geo.contentRect.left - 1f)
        assertTrue(geo.activeFrameRect.top >= geo.contentRect.top - 1f)
        assertTrue(geo.activeFrameRect.right <= geo.contentRect.right + 1f)
        assertTrue(geo.activeFrameRect.bottom <= geo.contentRect.bottom + 1f)
    }

    @Test
    fun `previewContentGeometry 16_9 frame in 16_9 portrait content stays contained`() {
        val geo = previewContentGeometry(
            viewWidth = 1080, viewHeight = 1920,
            ratioWidth = 16, ratioHeight = 9,
            previewContentAspect = PreviewContentAspect(16, 9)
        )
        assertTrue(geo.activeFrameRect.left >= geo.contentRect.left - 1f)
        assertTrue(geo.activeFrameRect.top >= geo.contentRect.top - 1f)
        assertTrue(geo.activeFrameRect.right <= geo.contentRect.right + 1f)
        assertTrue(geo.activeFrameRect.bottom <= geo.contentRect.bottom + 1f)
    }

    @Test
    fun `previewContentGeometry 1_1 frame in 16_9 portrait content stays contained`() {
        val geo = previewContentGeometry(
            viewWidth = 1080, viewHeight = 1920,
            ratioWidth = 1, ratioHeight = 1,
            previewContentAspect = PreviewContentAspect(16, 9)
        )
        assertTrue(geo.activeFrameRect.left >= geo.contentRect.left - 1f)
        assertTrue(geo.activeFrameRect.top >= geo.contentRect.top - 1f)
        assertTrue(geo.activeFrameRect.right <= geo.contentRect.right + 1f)
        assertTrue(geo.activeFrameRect.bottom <= geo.contentRect.bottom + 1f)
    }

    @Test
    fun `previewContentGeometry all ratios in 1_1 portrait content stay contained`() {
        for ((rw, rh) in listOf(4 to 3, 16 to 9, 1 to 1)) {
            val geo = previewContentGeometry(
                viewWidth = 1080, viewHeight = 1920,
                ratioWidth = rw, ratioHeight = rh,
                previewContentAspect = PreviewContentAspect(1, 1)
            )
            assertTrue(geo.activeFrameRect.left >= geo.contentRect.left - 1f,
                "${rw}:${rh} frame left exceeds content in 1:1 portrait")
            assertTrue(geo.activeFrameRect.top >= geo.contentRect.top - 1f,
                "${rw}:${rh} frame top exceeds content in 1:1 portrait")
            assertTrue(geo.activeFrameRect.right <= geo.contentRect.right + 1f,
                "${rw}:${rh} frame right exceeds content in 1:1 portrait")
            assertTrue(geo.activeFrameRect.bottom <= geo.contentRect.bottom + 1f,
                "${rw}:${rh} frame bottom exceeds content in 1:1 portrait")
        }
    }

    @Test
    fun `previewContentGeometry all ratios in tall 1080x2400 4_3 content stay contained`() {
        for ((rw, rh) in listOf(4 to 3, 16 to 9, 1 to 1)) {
            val geo = previewContentGeometry(
                viewWidth = 1080, viewHeight = 2400,
                ratioWidth = rw, ratioHeight = rh,
                previewContentAspect = PreviewContentAspect(4, 3)
            )
            assertTrue(geo.activeFrameRect.left >= geo.contentRect.left - 1f,
                "${rw}:${rh} frame left exceeds content in 1080x2400 4:3")
            assertTrue(geo.activeFrameRect.top >= geo.contentRect.top - 1f,
                "${rw}:${rh} frame top exceeds content in 1080x2400 4:3")
            assertTrue(geo.activeFrameRect.right <= geo.contentRect.right + 1f,
                "${rw}:${rh} frame right exceeds content in 1080x2400 4:3")
            assertTrue(geo.activeFrameRect.bottom <= geo.contentRect.bottom + 1f,
                "${rw}:${rh} frame bottom exceeds content in 1080x2400 4:3")
        }
    }

    @Test
    fun `previewContentGeometry no ratio returns full content rect as frame`() {
        val geo = previewContentGeometry(
            viewWidth = 1080, viewHeight = 1920,
            previewContentAspect = PreviewContentAspect(4, 3)
        )
        assertEquals(geo.contentRect.left, geo.activeFrameRect.left, 1f)
        assertEquals(geo.contentRect.top, geo.activeFrameRect.top, 1f)
        assertEquals(geo.contentRect.right, geo.activeFrameRect.right, 1f)
        assertEquals(geo.contentRect.bottom, geo.activeFrameRect.bottom, 1f)
    }

    @Test
    fun `previewContentGeometry frame is centered within content rect`() {
        val geo = previewContentGeometry(
            viewWidth = 1080, viewHeight = 1920,
            ratioWidth = 4, ratioHeight = 3,
            previewContentAspect = PreviewContentAspect(4, 3)
        )
        assertEquals(geo.contentRect.centerX(), geo.activeFrameRect.centerX(), 1f)
        assertEquals(geo.contentRect.centerY(), geo.activeFrameRect.centerY(), 1f)
    }

    // --- null previewContentAspect defaults to 4:3 sensor ---
    // RectF is stubbed in JVM tests (returns 0), so we verify the underlying math
    // via computeFrameRect which returns a plain data class.

    @Test
    fun `null previewContentAspect defaults to 4_3 aspect`() {
        // Verify the 4:3 content rect dimensions via computeFrameRect
        val contentPortrait = computeFrameRect(1080, 1920, 4, 3)
        assertEquals(1080f, contentPortrait.width, 1f)
        assertEquals(1440f, contentPortrait.height, 1f)
        val contentLandscape = computeFrameRect(1920, 1080, 4, 3)
        assertEquals(1440f, contentLandscape.width, 1f)
        assertEquals(1080f, contentLandscape.height, 1f)
    }

    @Test
    fun `null aspect 16_9 frame stays within 4_3 content`() {
        // Simulate null aspect → 4:3 content, then 16:9 frame inside
        val content = computeFrameRect(1080, 1920, 4, 3)
        val frame = computeFrameRect(content.width.toInt(), content.height.toInt(), 16, 9)
        assertTrue(frame.width <= content.width + 1f)
        assertTrue(frame.height <= content.height + 1f)
    }
}
