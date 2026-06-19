package com.opencamera.app

import android.graphics.RectF
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PreviewTapFocusGeometryTest {

    // RectF is an Android stub in pure JVM tests (isReturnDefaultValues).
    // RectF.contains() always returns false, so frame-filtering tests
    // require instrumented tests. Here we verify normalization and clamping
    // with activeFrameRect = null (no frame filtering).

    @Test
    fun `center tap in 1000x800 becomes 0_5 0_5`() {
        val result = normalizedPreviewTapOrNull(500f, 400f, 1000, 800)
        assertNotNull(result)
        assertEquals(0.5f, result.x)
        assertEquals(0.5f, result.y)
    }

    @Test
    fun `negative tap clamps to 0_0`() {
        val result = normalizedPreviewTapOrNull(-100f, -50f, 1000, 800)
        assertNotNull(result)
        assertEquals(0.0f, result.x)
        assertEquals(0.0f, result.y)
    }

    @Test
    fun `over-edge tap clamps to 1_0`() {
        val result = normalizedPreviewTapOrNull(1500f, 900f, 1000, 800)
        assertNotNull(result)
        assertEquals(1.0f, result.x)
        assertEquals(1.0f, result.y)
    }

    @Test
    fun `zero width returns null`() {
        assertNull(normalizedPreviewTapOrNull(500f, 400f, 0, 800))
    }

    @Test
    fun `zero height returns null`() {
        assertNull(normalizedPreviewTapOrNull(500f, 400f, 1000, 0))
    }

    @Test
    fun `negative dimensions return null`() {
        assertNull(normalizedPreviewTapOrNull(500f, 400f, -1, 800))
        assertNull(normalizedPreviewTapOrNull(500f, 400f, 1000, -1))
    }

    @Test
    fun `null activeFrameRect accepts all taps`() {
        val result = normalizedPreviewTapOrNull(500f, 400f, 1000, 800, activeFrameRect = null)
        assertNotNull(result)
        assertEquals(0.5f, result.x)
        assertEquals(0.5f, result.y)
    }

    @Test
    fun `active frame tap normalizes using full view dimensions`() {
        val result = normalizedPreviewTapOrNull(
            tapX = 300f,
            tapY = 250f,
            viewWidth = 1000,
            viewHeight = 800,
            activeFrameBounds = PreviewTapFrameBounds(
                left = 100f,
                top = 50f,
                right = 900f,
                bottom = 650f
            )
        )

        assertNotNull(result)
        // Full-view normalization: tap / viewDimension (not tap relative to frame)
        assertEquals(0.3f, result.x)
        assertEquals(0.3125f, result.y)
    }

    @Test
    fun `active frame tap outside frame returns null`() {
        assertNull(
            normalizedPreviewTapOrNull(
                tapX = 50f,
                tapY = 250f,
                viewWidth = 1000,
                viewHeight = 800,
                activeFrameBounds = PreviewTapFrameBounds(
                    left = 100f,
                    top = 50f,
                    right = 900f,
                    bottom = 650f
                )
            )
        )
    }

    @Test
    fun `non-null activeFrameRect with stub RectF rejects taps`() {
        // RectF stub has all fields = 0, so contains() returns false for any point.
        // This verifies the null-check branch is taken.
        val stubRect = RectF()
        assertNull(normalizedPreviewTapOrNull(500f, 400f, 1000, 800, activeFrameRect = stubRect))
    }

    @Test
    fun `edge tap at exact view size clamps to 1_0`() {
        val result = normalizedPreviewTapOrNull(1000f, 800f, 1000, 800)
        assertNotNull(result)
        assertEquals(1.0f, result.x)
        assertEquals(1.0f, result.y)
    }

    @Test
    fun `zero tap returns 0_0`() {
        val result = normalizedPreviewTapOrNull(0f, 0f, 1000, 800)
        assertNotNull(result)
        assertEquals(0.0f, result.x)
        assertEquals(0.0f, result.y)
    }

    @Test
    fun `offset active frame tap preserves original view pixel for metering`() {
        // Frame at left=100, top=50, right=900, bottom=650 in a 1000x800 view
        // Tap at (300, 250) inside the frame
        val result = normalizedPreviewTapOrNull(
            tapX = 300f,
            tapY = 250f,
            viewWidth = 1000,
            viewHeight = 800,
            activeFrameBounds = PreviewTapFrameBounds(
                left = 100f,
                top = 50f,
                right = 900f,
                bottom = 650f
            )
        )
        assertNotNull(result)

        // Inverse mapping: normalized * viewDimension == original tap pixel
        // This is what CameraX metering and reticle drawing use
        assertEquals(300f, result.x * 1000f, 0.01f)
        assertEquals(250f, result.y * 800f, 0.01f)
    }

    @Test
    fun `offset frame tap at frame edge maps to correct view pixel`() {
        // Tap at frame left boundary (x=100) and bottom boundary (y=650)
        val result = normalizedPreviewTapOrNull(
            tapX = 100f,
            tapY = 650f,
            viewWidth = 1000,
            viewHeight = 800,
            activeFrameBounds = PreviewTapFrameBounds(
                left = 100f,
                top = 50f,
                right = 900f,
                bottom = 650f
            )
        )
        assertNotNull(result)
        assertEquals(100f, result.x * 1000f, 0.01f)
        assertEquals(650f, result.y * 800f, 0.01f)
    }

    @Test
    fun `offset frame tap at frame center maps to correct view pixel`() {
        // Frame center: ((100+900)/2, (50+650)/2) = (500, 350)
        val result = normalizedPreviewTapOrNull(
            tapX = 500f,
            tapY = 350f,
            viewWidth = 1000,
            viewHeight = 800,
            activeFrameBounds = PreviewTapFrameBounds(
                left = 100f,
                top = 50f,
                right = 900f,
                bottom = 650f
            )
        )
        assertNotNull(result)
        assertEquals(500f, result.x * 1000f, 0.01f)
        assertEquals(350f, result.y * 800f, 0.01f)
    }

    @Test
    fun `no frame tap inverse mapping preserves view pixel`() {
        // No active frame: tap at (300, 250) in 1000x800 view
        val result = normalizedPreviewTapOrNull(
            tapX = 300f,
            tapY = 250f,
            viewWidth = 1000,
            viewHeight = 800,
            activeFrameBounds = null
        )
        assertNotNull(result)
        assertEquals(300f, result.x * 1000f, 0.01f)
        assertEquals(250f, result.y * 800f, 0.01f)
    }

    @Test
    fun `no frame tap at origin maps to zero`() {
        val result = normalizedPreviewTapOrNull(
            tapX = 0f,
            tapY = 0f,
            viewWidth = 1000,
            viewHeight = 800,
            activeFrameBounds = null
        )
        assertNotNull(result)
        assertEquals(0f, result.x * 1000f, 0.01f)
        assertEquals(0f, result.y * 800f, 0.01f)
    }

    @Test
    fun `no frame tap at view edge maps to view dimension`() {
        val result = normalizedPreviewTapOrNull(
            tapX = 1000f,
            tapY = 800f,
            viewWidth = 1000,
            viewHeight = 800,
            activeFrameBounds = null
        )
        assertNotNull(result)
        assertEquals(1000f, result.x * 1000f, 0.01f)
        assertEquals(800f, result.y * 800f, 0.01f)
    }

    @Test
    fun `active frame tap far right edge preserves pixel`() {
        // Tap at frame right boundary (x=900) in a 1000x800 view
        val result = normalizedPreviewTapOrNull(
            tapX = 900f,
            tapY = 350f,
            viewWidth = 1000,
            viewHeight = 800,
            activeFrameBounds = PreviewTapFrameBounds(
                left = 100f,
                top = 50f,
                right = 900f,
                bottom = 650f
            )
        )
        assertNotNull(result)
        assertEquals(900f, result.x * 1000f, 0.01f)
        assertEquals(350f, result.y * 800f, 0.01f)
    }
}
