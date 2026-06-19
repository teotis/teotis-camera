package com.opencamera.app.camera

import android.graphics.Bitmap
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class MutableArgbBitmapDecoderTest {

    private fun createMinimalJpegBytes(width: Int = 4, height: Int = 4): ByteArray {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, output)
        bmp.recycle()
        return output.toByteArray()
    }

    @Test
    fun `decode returns mutable ARGB_8888 bitmap for valid JPEG`() {
        val jpegBytes = createMinimalJpegBytes()
        val decoded = MutableArgbBitmapDecoder.decode(jpegBytes)
        assertNotNull(decoded)
        assertTrue(decoded.isMutable)
        assertEquals(Bitmap.Config.ARGB_8888, decoded.config)
        decoded.recycle()
    }

    @Test
    fun `decode returns null for empty bytes`() {
        assertNull(MutableArgbBitmapDecoder.decode(ByteArray(0)))
    }

    @Test
    fun `decode preserves bitmap dimensions`() {
        val jpegBytes = createMinimalJpegBytes(8, 6)
        val decoded = MutableArgbBitmapDecoder.decode(jpegBytes)
        assertNotNull(decoded)
        assertEquals(8, decoded.width)
        assertEquals(6, decoded.height)
        decoded.recycle()
    }
}
