package com.opencamera.app

import android.graphics.RectF
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PreviewOverlayWatermarkRenderTest {

    @Test
    fun `portrait preview content is aligned to bottom cockpit edge`() {
        val geometry = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920,
            previewContentAspect = PreviewContentAspect(4, 3)
        )

        assertEquals(0f, geometry.contentRect.left)
        assertEquals(480f, geometry.contentRect.top)
        assertEquals(1080f, geometry.contentRect.right)
        assertEquals(1920f, geometry.contentRect.bottom)
    }

    @Test
    fun `expanded frame watermark reserves bottom band outside active capture frame`() {
        val rect = activeSquareFrame()

        val band = expandedFrameBottomBandRect(rect, viewHeight = 1920, density = 1f)

        requireNotNull(band)
        assertEquals(rect.bottom, band.top)
        assertTrue(band.bottom > rect.bottom, "rect=$rect band=$band")
        assertTrue(band.height() >= 56f, "rect=$rect band=$band")
    }

    @Test
    fun `professional bottom bar previews as expanded band when there is space below frame`() {
        val rect = activeSquareFrame()

        val bar = bottomBarPreviewRect(rect, viewHeight = 1920, density = 1f)

        assertEquals(rect.bottom, bar.top)
        assertTrue(bar.bottom > rect.bottom, "rect=$rect bar=$bar")
        assertTrue(bar.height() >= 48f, "rect=$rect bar=$bar")
    }

    @Test
    fun `professional bottom bar falls back inside frame when no outside space exists`() {
        val rect = RectF(0f, 0f, 1080f, 1920f)

        val bar = bottomBarPreviewRect(rect, viewHeight = 1920, density = 1f)

        assertTrue(bar.top < rect.bottom, "rect=$rect bar=$bar")
        assertEquals(rect.bottom, bar.bottom)
    }

    @Test
    fun `four border watermark uses saved-output-like edge band width`() {
        val rect = activeSquareFrame()

        val band = fourBorderPreviewBandWidth(rect, density = 1f)

        assertTrue(band >= 48f, "four border preview should read as a brand-paper frame, band=$band")
        assertTrue(band <= 64f)
    }

    private fun activeSquareFrame(): RectF {
        val geometry = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920,
            ratioWidth = 1,
            ratioHeight = 1
        )
        return geometry.activeFrameRect
    }
}
