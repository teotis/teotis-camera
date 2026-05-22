package com.opencamera.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class PreviewTapFocusGeometryTest {

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
    fun `zero size returns null`() {
        assertNull(normalizedPreviewTapOrNull(500f, 400f, 0, 800))
        assertNull(normalizedPreviewTapOrNull(500f, 400f, 1000, 0))
        assertNull(normalizedPreviewTapOrNull(500f, 400f, 0, 0))
        assertNull(normalizedPreviewTapOrNull(500f, 400f, -1, 800))
    }

    @Test
    fun `tap outside active frame returns null`() {
        // Frame: left=100, top=100, right=900, bottom=700
        assertNull(normalizedPreviewTapOrNull(50f, 400f, 1000, 800,
            activeFrameLeft = 100f, activeFrameTop = 100f, activeFrameRight = 900f, activeFrameBottom = 700f))
        assertNull(normalizedPreviewTapOrNull(500f, 50f, 1000, 800,
            activeFrameLeft = 100f, activeFrameTop = 100f, activeFrameRight = 900f, activeFrameBottom = 700f))
        assertNull(normalizedPreviewTapOrNull(950f, 400f, 1000, 800,
            activeFrameLeft = 100f, activeFrameTop = 100f, activeFrameRight = 900f, activeFrameBottom = 700f))
        assertNull(normalizedPreviewTapOrNull(500f, 750f, 1000, 800,
            activeFrameLeft = 100f, activeFrameTop = 100f, activeFrameRight = 900f, activeFrameBottom = 700f))
    }

    @Test
    fun `tap inside active frame returns normalized full-view coordinates`() {
        // Tap at (500, 400) inside frame (100, 100, 900, 700)
        val result = normalizedPreviewTapOrNull(500f, 400f, 1000, 800,
            activeFrameLeft = 100f, activeFrameTop = 100f, activeFrameRight = 900f, activeFrameBottom = 700f)
        assertNotNull(result)
        // Normalized against full view (1000x800), not the frame
        assertEquals(0.5f, result.x)
        assertEquals(0.5f, result.y)
    }
}
