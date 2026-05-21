package com.opencamera.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
}
