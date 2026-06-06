package com.opencamera.app.camera

import android.util.Size
import android.util.SizeF
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VendorCameraProbeSensorMathTest {
    @Test
    fun `sensor info uses camera2 millimeter physical size and micrometer pixel pitch`() {
        val physicalSize = SizeF(9.1392f, 6.8812804f)
        val pixelArraySize = Size(4080, 3072)

        assertTrue(formatPhysicalSensorSize(physicalSize).contains("physical-size: 9.1392mm × 6.8812804mm"))
        assertTrue(formatSensorPixelSize(physicalSize, pixelArraySize).contains("pixel-size: 2.24μm × 2.24μm"))
    }

    @Test
    fun `lens info reports realistic thirty five millimeter equivalent focal length`() {
        val equivalentFocalLength = 6.25f * focalLengthCropFactor(SizeF(9.1392f, 6.8812804f))

        assertTrue(equivalentFocalLength in 23.5f..23.8f)
    }
}
