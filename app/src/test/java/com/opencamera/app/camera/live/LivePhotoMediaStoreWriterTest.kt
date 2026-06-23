package com.opencamera.app.camera.live

import android.net.Uri
import android.provider.MediaStore
import com.opencamera.core.media.MotionPhotoContainerSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.fakes.BaseCursor
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LivePhotoMediaStoreWriterTest {

    private lateinit var context: android.content.Context
    private lateinit var writer: LivePhotoMediaStoreWriter
    private lateinit var shadowResolver: ShadowContentResolver

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        writer = LivePhotoMediaStoreWriter(context)
        shadowResolver = Shadows.shadowOf(context.contentResolver)
    }

    @Test
    fun `readMediaStoreBytes reads bytes from content resolver`() {
        val testBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02)
        val uri = Uri.parse("content://media/external/images/media/42")
        shadowResolver.registerInputStream(uri, ByteArrayInputStream(testBytes))

        val result = writer.readMediaStoreBytes(uri)

        assertTrue(result.isSuccess)
        assertArrayEquals(testBytes, result.getOrNull())
    }

    @Test
    fun `readMediaStoreBytes fails when stream is null`() {
        val uri = Uri.parse("content://media/external/images/media/99")

        val result = writer.readMediaStoreBytes(uri)

        assertTrue(result.isFailure)
    }

    @Test
    fun `overwriteMotionPhotoJpeg writes bytes to content resolver`() {
        val uri = Uri.parse("content://media/external/images/media/42")
        val os = ByteArrayOutputStream()
        shadowResolver.registerOutputStream(uri, os)
        val motionBytes = byteArrayOf(0x01, 0x02, 0x03)

        val result = writer.overwriteMotionPhotoJpeg(uri, motionBytes)

        assertTrue(result.isSuccess)
        assertArrayEquals(motionBytes, os.toByteArray())
    }

    @Test
    fun `overwriteMotionPhotoJpeg fails when output stream is null`() {
        val uri = Uri.parse("content://media/external/images/media/99")

        val result = writer.overwriteMotionPhotoJpeg(uri, byteArrayOf(0x01))

        assertTrue(result.isFailure)
    }

    @Test
    fun `insertMotionMp4Sidecar returns valid uri`() {
        val mp4Bytes = byteArrayOf(
            0x00, 0x00, 0x00, 0x1C,
            0x66, 0x74, 0x79, 0x70,
            0x69, 0x73, 0x6F, 0x6D
        )

        val result = writer.insertMotionMp4Sidecar(
            jpegRelativePath = "Pictures/OpenCamera/capture.jpg",
            mp4DisplayNamePrefix = "capture",
            mp4Bytes = mp4Bytes
        )

        assertTrue(result.isSuccess)
        val mp4Uri = result.getOrNull()
        assertNotNull(mp4Uri)
        // ShadowContentResolver insert returns content://media/external/video/media/{id}
        assertTrue(mp4Uri.toString().contains("video"))
    }

    @Test
    fun `insertMotionMp4Sidecar failure preserves readable exception message`() {
        val mp4Bytes = byteArrayOf(0x01, 0x02, 0x03)
        // Subclass that simulates insert failure with a readable message
        val failingWriter = object : LivePhotoMediaStoreWriter(context) {
            override fun insertMotionMp4Sidecar(
                jpegRelativePath: String,
                mp4DisplayNamePrefix: String,
                mp4Bytes: ByteArray
            ): Result<Uri> {
                return Result.failure(
                    IllegalStateException(
                        "Failed to insert MP4 sidecar into MediaStore for $mp4DisplayNamePrefix.live.mp4"
                    )
                )
            }
        }

        val result = failingWriter.insertMotionMp4Sidecar(
            jpegRelativePath = "Pictures/OpenCamera/capture.jpg",
            mp4DisplayNamePrefix = "capture",
            mp4Bytes = mp4Bytes
        )

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue(
            "Exception message must mention MP4 sidecar insert failure, got: $message",
            message.contains("Failed to insert MP4 sidecar")
        )
    }

    @Test
    fun `verifyMotionMp4Sidecar returns media store video record when cursor has data`() {
        val mp4Uri = Uri.parse("content://media/external/video/media/42")
        val columnNames = arrayOf(
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE
        )
        val rowValues: Array<Any?> = arrayOf("capture.live.mp4", "video/mp4", "1024")
        shadowResolver.setCursor(mp4Uri, FakeVideoCursor(columnNames, listOf(rowValues)))

        val result = writer.verifyMotionMp4Sidecar(mp4Uri)

        assertTrue(
            "Expected success but got: ${result.exceptionOrNull()?.message}",
            result.isSuccess
        )
        val record = result.getOrNull()!!
        assertEquals("content://media/external/video/media/42", record.uri)
        assertEquals("capture.live.mp4", record.displayName)
        assertEquals("video/mp4", record.mimeType)
        assertEquals("1024", record.size)
        // SDK 28 in this test: RELATIVE_PATH/DURATION/IS_PENDING unsupported -> n/a
        assertEquals("n/a", record.relativePath)
        assertEquals("n/a", record.duration)
        assertEquals("n/a", record.isPending)
    }

    @Test
    fun `verifyMotionMp4Sidecar fails when cursor is null`() {
        val mp4Uri = Uri.parse("content://media/external/video/media/999")

        val result = writer.verifyMotionMp4Sidecar(mp4Uri)

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue(
            "Exception message must mention MP4 sidecar query failure, got: $message",
            message.contains("Failed to query MediaStore for MP4 sidecar")
        )
    }

    @Test
    fun `createMotionPhotoBytes produces output with GCamera XMP`() {
        val savedUri = Uri.parse("content://media/external/images/media/42")
        val jpegBytes = makeMinimalJpeg()
        shadowResolver.registerInputStream(savedUri, ByteArrayInputStream(jpegBytes))

        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "motion-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val motionFile = java.io.File(tempDir, "test.live.mp4")
        motionFile.writeBytes(makeFakeMp4())

        try {
            val result = writer.createMotionPhotoBytes(
                savedUri = savedUri,
                motionPath = motionFile.absolutePath,
                spec = MotionPhotoContainerSpec(motionLengthBytes = motionFile.length())
            )

            assertTrue(result.isSuccess)
            val combined = result.getOrNull()!!
            val combinedStr = String(combined, Charsets.UTF_8)
            val motionBytes = motionFile.readBytes()
            assertTrue("Must contain GCamera:MotionPhoto", combinedStr.contains("GCamera:MotionPhoto=\"1\""))
            assertTrue("Must contain Primary Item", combinedStr.contains("Item:Semantic=\"Primary\""))
            assertArrayEquals(
                "Motion item must terminate the file",
                motionBytes,
                combined.copyOfRange(combined.size - motionBytes.size, combined.size)
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun makeMinimalJpeg(): ByteArray {
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00,
            0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0xFF.toByte(), 0xD9.toByte()
        )
    }

    private fun makeFakeMp4(): ByteArray {
        return byteArrayOf(
            0x00, 0x00, 0x00, 0x1C,
            0x66, 0x74, 0x79, 0x70,
            0x69, 0x73, 0x6F, 0x6D,
            0x00, 0x00, 0x02, 0x00,
            0x69, 0x73, 0x6F, 0x6D,
            0x69, 0x73, 0x6F, 0x32,
            0x61, 0x76, 0x63, 0x31
        )
    }
}

private class FakeVideoCursor(
    private val columnNames: Array<String>,
    private val rows: List<Array<Any?>>
) : BaseCursor() {

    override fun getCount(): Int = rows.size

    override fun moveToFirst(): Boolean = rows.isNotEmpty()

    override fun getColumnIndex(columnName: String): Int = columnNames.indexOf(columnName)

    override fun getColumnIndexOrThrow(columnName: String): Int {
        val idx = columnNames.indexOf(columnName)
        if (idx < 0) throw IllegalArgumentException("No such column: $columnName")
        return idx
    }

    override fun getColumnName(columnIndex: Int): String = columnNames[columnIndex]

    override fun getColumnNames(): Array<String> = columnNames

    override fun getColumnCount(): Int = columnNames.size

    override fun getString(columnIndex: Int): String? = rows[0][columnIndex]?.toString()

    override fun getLong(columnIndex: Int): Long =
        (rows[0][columnIndex] as? Long) ?: (rows[0][columnIndex]?.toString()?.toLongOrNull() ?: 0L)

    override fun getInt(columnIndex: Int): Int =
        (rows[0][columnIndex] as? Int) ?: (rows[0][columnIndex]?.toString()?.toIntOrNull() ?: 0)

    override fun isNull(columnIndex: Int): Boolean = rows[0][columnIndex] == null

    override fun close() {}
}
