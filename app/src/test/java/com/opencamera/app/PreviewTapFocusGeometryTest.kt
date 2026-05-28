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
}
