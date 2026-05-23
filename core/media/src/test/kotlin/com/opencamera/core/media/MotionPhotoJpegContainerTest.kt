package com.opencamera.core.media

import org.junit.Assert.*
import org.junit.Test

class MotionPhotoJpegContainerTest {

    @Test
    fun `write rejects non jpeg input`() {
        val notJpeg = ByteArray(10) { 0x00 }
        val mp4 = makeFakeMp4()

        assertThrows(IllegalArgumentException::class.java) {
            MotionPhotoJpegContainer.write(
                jpegBytes = notJpeg,
                motionBytes = mp4,
                spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
            )
        }
    }

    @Test
    fun `write rejects empty motion bytes`() {
        val jpeg = makeMinimalJpeg()
        val emptyMp4 = ByteArray(0)

        assertThrows(IllegalArgumentException::class.java) {
            MotionPhotoJpegContainer.write(
                jpegBytes = jpeg,
                motionBytes = emptyMp4,
                spec = MotionPhotoContainerSpec(motionLengthBytes = 0)
            )
        }
    }

    @Test
    fun `write appends motion bytes at end`() {
        val jpeg = makeMinimalJpeg()
        val mp4 = makeFakeMp4()

        val result = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )

        // Result should end with MP4 bytes
        val resultMp4 = result.copyOfRange(result.size - mp4.size, result.size)
        assertArrayEquals(mp4, resultMp4)
    }

    @Test
    fun `write includes Camera MotionPhoto fields`() {
        val jpeg = makeMinimalJpeg()
        val mp4 = makeFakeMp4()

        val result = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )

        val resultStr = String(result, Charsets.UTF_8)
        assertTrue(resultStr.contains("Camera:MotionPhoto=\"1\""))
        assertTrue(resultStr.contains("Camera:MotionPhotoVersion=\"1\""))
        assertTrue(resultStr.contains("Camera:MotionPhotoPresentationTimestampUs"))
    }

    @Test
    fun `write includes Container primary and motion items`() {
        val jpeg = makeMinimalJpeg()
        val mp4 = makeFakeMp4()

        val result = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )

        val resultStr = String(result, Charsets.UTF_8)
        assertTrue(resultStr.contains("Item:Semantic=\"Primary\""))
        assertTrue(resultStr.contains("Item:Semantic=\"MotionPhoto\""))
        assertTrue(resultStr.contains("Item:Mime=\"image/jpeg\""))
        assertTrue(resultStr.contains("Item:Mime=\"video/mp4\""))
    }

    @Test
    fun `write uses exact motion length`() {
        val jpeg = makeMinimalJpeg()
        val mp4 = makeFakeMp4()
        val expectedLength = mp4.size.toLong()

        val result = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = expectedLength)
        )

        val resultStr = String(result, Charsets.UTF_8)
        assertTrue(resultStr.contains("Item:Length=\"$expectedLength\""))
    }

    @Test
    fun `write keeps output jpeg prefix valid`() {
        val jpeg = makeMinimalJpeg()
        val mp4 = makeFakeMp4()

        val result = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )

        // Should start with JPEG SOI marker
        assertEquals(0xFF.toByte(), result[0])
        assertEquals(0xD8.toByte(), result[1])
    }

    private fun makeMinimalJpeg(): ByteArray {
        // SOI + APP0 (JFIF) + SOS + fake entropy + EOI
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // SOI
            0xFF.toByte(), 0xE0.toByte(), // APP0 marker
            0x00, 0x10, // Length (16 bytes)
            0x4A, 0x46, 0x49, 0x46, 0x00, // "JFIF\0"
            0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, // Rest of APP0
            0xFF.toByte(), 0xDA.toByte(), // SOS marker
            0x00, 0x08, // Length
            0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, 0x7F, 0x00, // SOS data
            0xFF.toByte(), 0xD9.toByte()  // EOI
        )
    }

    private fun makeFakeMp4(): ByteArray {
        // Simple ftyp header-like bytes
        return byteArrayOf(
            0x00, 0x00, 0x00, 0x1C, // Size
            0x66, 0x74, 0x79, 0x70, // "ftyp"
            0x69, 0x73, 0x6F, 0x6D, // "isom"
            0x00, 0x00, 0x02, 0x00, // Minor version
            0x69, 0x73, 0x6F, 0x6D, // "isom"
            0x69, 0x73, 0x6F, 0x32, // "iso2"
            0x61, 0x76, 0x63, 0x31  // "avc1"
        )
    }
}
