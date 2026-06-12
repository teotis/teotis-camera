package com.opencamera.app.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.opencamera.core.media.ProcessorTarget
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ExifPreservationHelperTest {

    private fun createMinimalJpegWithExif(
        make: String = "TestMake",
        model: String = "TestModel",
        orientation: Int = ExifInterface.ORIENTATION_NORMAL
    ): ByteArray {
        val tmpFile = File(RuntimeEnvironment.getApplication().cacheDir, "exif_test_${System.nanoTime()}.jpg")
        tmpFile.deleteOnExit()
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        tmpFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()

        val exif = ExifInterface(tmpFile.absolutePath)
        exif.setAttribute(ExifInterface.TAG_MAKE, make)
        exif.setAttribute(ExifInterface.TAG_MODEL, model)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        exif.saveAttributes()
        return tmpFile.readBytes()
    }

    @Test
    fun `readPreservedExif extracts configured tags from JPEG bytes`() {
        val jpegBytes = createMinimalJpegWithExif(make = "Nikon", model = "Z9")
        val result = readPreservedExif(jpegBytes)

        assertEquals("Nikon", result[ExifInterface.TAG_MAKE])
        assertEquals("Z9", result[ExifInterface.TAG_MODEL])
    }

    @Test
    fun `readPreservedExif returns empty map for bytes without EXIF`() {
        val tmpFile = File(RuntimeEnvironment.getApplication().cacheDir, "plain_${System.nanoTime()}.jpg")
        tmpFile.deleteOnExit()
        val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        tmpFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 50, it) }
        bmp.recycle()
        val plainJpeg = tmpFile.readBytes()

        val result = readPreservedExif(plainJpeg)
        assertTrue(result.isEmpty() || !result.containsKey(ExifInterface.TAG_MAKE))
    }

    @Test
    fun `writeEncodedBytes writes to file path successfully`() {
        val context = RuntimeEnvironment.getApplication()
        val tmpFile = File(context.cacheDir, "test_write.jpg")
        tmpFile.deleteOnExit()
        val target = ProcessorTarget.FilePath(tmpFile.absolutePath)
        val data = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

        val success = context.contentResolver.writeEncodedBytes(target, data)

        assertTrue(success)
        assertTrue(tmpFile.exists())
        assertTrue(tmpFile.readBytes().contentEquals(data))
    }

    @Test
    fun `restorePreservedExif restores tags to file`() {
        val context = RuntimeEnvironment.getApplication()
        val tmpFile = File(context.cacheDir, "test_exif.jpg")
        tmpFile.deleteOnExit()

        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        bmp.recycle()
        tmpFile.writeBytes(baos.toByteArray())

        val target = ProcessorTarget.FilePath(tmpFile.absolutePath)
        val preserved = mapOf(
            ExifInterface.TAG_MAKE to "RestoredMake",
            ExifInterface.TAG_MODEL to "RestoredModel"
        )

        val error = context.contentResolver.restorePreservedExif(target, preserved)

        assertNull(error)
        val restored = readPreservedExif(tmpFile.readBytes())
        assertEquals("RestoredMake", restored[ExifInterface.TAG_MAKE])
        assertEquals("RestoredModel", restored[ExifInterface.TAG_MODEL])
    }

    @Test
    fun `restorePreservedExif returns null for empty map`() {
        val context = RuntimeEnvironment.getApplication()
        val tmpFile = File(context.cacheDir, "test_empty_exif.jpg")
        tmpFile.deleteOnExit()
        tmpFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))

        val target = ProcessorTarget.FilePath(tmpFile.absolutePath)
        val result = context.contentResolver.restorePreservedExif(target, emptyMap())

        assertNull(result)
    }
}
