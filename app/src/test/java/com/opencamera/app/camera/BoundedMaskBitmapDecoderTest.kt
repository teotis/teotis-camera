package com.opencamera.app.camera

import kotlin.test.Test
import kotlin.test.assertEquals

class BoundedMaskBitmapDecoderTest {

    @Test
    fun `large mask dimensions sample down to max edge`() {
        assertEquals(
            8,
            BoundedMaskBitmapDecoder.calculateInSampleSize(
                width = 10_800,
                height = 8_100,
                maxEdgePixels = 2_048
            )
        )
    }

    @Test
    fun `mask dimensions inside max edge keep full resolution`() {
        assertEquals(
            1,
            BoundedMaskBitmapDecoder.calculateInSampleSize(
                width = 1_600,
                height = 1_200,
                maxEdgePixels = 2_048
            )
        )
    }

    @Test
    fun `invalid mask dimensions keep safe default sample size`() {
        assertEquals(1, BoundedMaskBitmapDecoder.calculateInSampleSize(0, 1_200))
        assertEquals(1, BoundedMaskBitmapDecoder.calculateInSampleSize(1_200, -1))
        assertEquals(1, BoundedMaskBitmapDecoder.calculateInSampleSize(1_200, 1_200, maxEdgePixels = 0))
    }
}
