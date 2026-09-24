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
    fun `write places motion bytes at end of file`() {
        val jpeg = makeMinimalJpeg()
        val mp4 = makeFakeMp4()

        val result = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )

        val resultMp4 = result.copyOfRange(result.size - mp4.size, result.size)
        assertArrayEquals(mp4, resultMp4)
    }

    @Test
    fun `write includes GCamera MotionPhoto fields`() {
        val jpeg = makeMinimalJpeg()
        val mp4 = makeFakeMp4()

        val result = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )

        val resultStr = String(result, Charsets.UTF_8)
        assertTrue(resultStr.contains("GCamera:MotionPhoto=\"1\""))
        assertTrue(resultStr.contains("GCamera:MotionPhotoVersion=\"1\""))
        assertTrue(resultStr.contains("GCamera:MotionPhotoPresentationTimestampUs"))
        assertTrue("XMP must declare the Camera: prefix alias", resultStr.contains("xmlns:Camera="))
        assertTrue("XMP must declare the GCamera: prefix alias", resultStr.contains("xmlns:GCamera="))
    }

    @Test
    fun `write declares both namespace prefixes with the same google URI`() {
        val jpeg = makeMinimalJpeg()
        val mp4 = makeFakeMp4()

        val result = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )

        val resultStr = String(result, Charsets.UTF_8)
        val googleCameraNs = "http://ns.google.com/photos/1.0/camera/"
        assertTrue(resultStr.contains("xmlns:Camera=\"$googleCameraNs\""))
        assertTrue(resultStr.contains("xmlns:GCamera=\"$googleCameraNs\""))
        assertTrue(resultStr.contains("Camera:MotionPhoto=\"1\""))
        assertTrue(resultStr.contains("Camera:MotionPhotoVersion=\"1\""))
    }

    @Test
    fun `write sets both prefix presentation timestamps and defaults to zero`() {
        val jpeg = makeMinimalJpeg()
        val mp4 = makeFakeMp4()

        val defaultResult = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )
        val defaultStr = String(defaultResult, Charsets.UTF_8)
        assertTrue(defaultStr.contains("Camera:MotionPhotoPresentationTimestampUs=\"0\""))
        assertTrue(defaultStr.contains("GCamera:MotionPhotoPresentationTimestampUs=\"0\""))

        val timedResult = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(
                motionLengthBytes = mp4.size.toLong(),
                presentationTimestampUs = 12345L
            )
        )
        val timedStr = String(timedResult, Charsets.UTF_8)
        assertTrue(timedStr.contains("Camera:MotionPhotoPresentationTimestampUs=\"12345\""))
        assertTrue(timedStr.contains("GCamera:MotionPhotoPresentationTimestampUs=\"12345\""))
    }

    @Test
    fun `write embeds micro video offset equal to jpeg part size`() {
        val jpeg = makeMinimalJpeg()
        val mp4 = makeFakeMp4()

        val result = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )

        val jpegPartSize = result.size - mp4.size
        val resultStr = String(result, Charsets.UTF_8)
        assertTrue(resultStr.contains("GCamera:MicroVideoOffset=\"$jpegPartSize\""))
    }

    @Test
    fun `write inserts xmp after multiple appn segments`() {
        val jpeg = makeMultiAppnJpeg()
        val mp4 = makeFakeMp4()

        val result = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )

        // APP0 (JFIF) and APP1 (EXIF) must both precede our XMP APP1, which precedes SOS.
        val resultStr = String(result, Charsets.UTF_8)
        val xmpPos = resultStr.indexOf("xmlns:GCamera")
        val exifPos = resultStr.indexOf("Exif")
        val sosPos = indexOfMarker(result, 0xDA)
        assertTrue("EXIF APP1 must be before XMP", exifPos in 0 until xmpPos)
        assertTrue("XMP must be before SOS", xmpPos < sosPos)
    }

    @Test
    fun `write replaces an existing adobe xmp app1 instead of duplicating`() {
        val once = MotionPhotoJpegContainer.write(
            jpegBytes = makeMinimalJpeg(),
            motionBytes = makeFakeMp4(),
            spec = MotionPhotoContainerSpec(motionLengthBytes = makeFakeMp4().size.toLong())
        )
        val twice = MotionPhotoJpegContainer.write(
            jpegBytes = once,
            motionBytes = makeFakeMp4(),
            spec = MotionPhotoContainerSpec(motionLengthBytes = makeFakeMp4().size.toLong())
        )

        val segments = MotionPhotoContainerParser.readJpegSegments(twice)
        val xmpSegments = segments.filter { it.isAdobeXmp }
        assertEquals("exactly one XMP APP1", 1, xmpSegments.size)
        val analysis = MotionPhotoContainerParser.analyze(twice)
        assertTrue("re-materialized container must validate, issues=${analysis.issues}", analysis.valid)
    }

    @Test
    fun `write preserves unrelated adobe xmp metadata while adding motion fields`() {
        val jpeg = makeJpegWithDescriptiveXmp()
        val mp4 = makeFakeMp4()

        val result = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )

        val resultStr = String(result, Charsets.UTF_8)
        assertTrue(resultStr.contains("xmp:Rating=\"5\""))
        assertTrue(resultStr.contains("<dc:creator>Teotis Camera</dc:creator>"))
        assertTrue(resultStr.contains("GCamera:MotionPhoto=\"1\""))
        assertEquals(
            "motion materialization should keep one standard XMP packet",
            1,
            MotionPhotoContainerParser.readJpegSegments(result).count { it.isAdobeXmp }
        )
    }

    @Test
    fun `strip motion segment removes motion fields but preserves unrelated xmp metadata`() {
        val mp4 = makeFakeMp4()
        val materialized = MotionPhotoJpegContainer.write(
            jpegBytes = makeJpegWithDescriptiveXmp(),
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )

        val stripped = MotionPhotoJpegContainer.stripMotionSegment(materialized)
        val strippedStr = String(stripped, Charsets.UTF_8)

        assertTrue(strippedStr.contains("xmp:Rating=\"5\""))
        assertTrue(strippedStr.contains("<dc:creator>Teotis Camera</dc:creator>"))
        assertFalse(strippedStr.contains("MotionPhoto=\"1\""))
        assertFalse(strippedStr.contains("Container:Directory"))
        assertEquals(
            "descriptive XMP packet should remain after removing motion metadata",
            1,
            MotionPhotoContainerParser.readJpegSegments(stripped).count { it.isAdobeXmp }
        )
    }

    @Test
    fun `strip motion segment returns clean jpeg from rematerialized file`() {
        val mp4 = makeFakeMp4()
        val materialized = MotionPhotoJpegContainer.write(
            jpegBytes = makeMinimalJpeg(),
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )

        val stripped = MotionPhotoJpegContainer.stripMotionSegment(materialized)

        assertTrue("stripped must be smaller than materialized", stripped.size < materialized.size)
        assertEquals(0xFF.toByte(), stripped[0])
        assertEquals(0xD8.toByte(), stripped[1])
        assertEquals(0xFF.toByte(), stripped[stripped.size - 2])
        assertEquals(0xD9.toByte(), stripped[stripped.size - 1])
        // No XMP remains.
        assertFalse(MotionPhotoContainerParser.readJpegSegments(stripped).any { it.isAdobeXmp })

        // Re-materializing the stripped still yields a valid container with exactly one MP4.
        val recombined = MotionPhotoJpegContainer.write(
            jpegBytes = stripped,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )
        val analysis = MotionPhotoContainerParser.analyze(recombined)
        assertTrue("re-materialized container must validate, issues=${analysis.issues}", analysis.valid)
        assertEquals(mp4.size.toLong(), analysis.motionLength)
    }

    @Test
    fun `strip motion segment leaves plain jpeg unchanged`() {
        val plain = makeMinimalJpeg()
        val stripped = MotionPhotoJpegContainer.stripMotionSegment(plain)
        assertTrue(stripped.contentEquals(plain))
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

    @Test
    fun `write output contains jpeg followed immediately by motion bytes`() {
        val jpeg = makeMinimalJpeg()
        val mp4 = makeFakeMp4()

        val result = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )

        val jpegPart = result.copyOfRange(0, result.size - mp4.size)
        val motionPart = result.copyOfRange(result.size - mp4.size, result.size)
        assertArrayEquals(mp4, motionPart)
        assertEquals(0xFF.toByte(), jpegPart[jpegPart.size - 2])
        assertEquals(0xD9.toByte(), jpegPart[jpegPart.size - 1])
    }


    @Test
    fun `write item length matches actual motion bytes not just label`() {
        val jpeg = makeMinimalJpeg()
        val mp4 = makeFakeMp4()

        val result = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )

        val resultStr = String(result, Charsets.UTF_8)
        // Extract Item:Length value for the MotionPhoto semantic item
        val itemPattern = Regex("Item:Semantic=\"MotionPhoto\" Item:Length=\"(\\d+)\"")
        val match = itemPattern.find(resultStr)
        assertNotNull("MotionPhoto Item:Length must be present in XMP", match)
        val declaredLength = match!!.groupValues[1].toLong()
        assertEquals(mp4.size.toLong(), declaredLength)
    }

    @Test
    fun `write replaces stale requested motion length with actual bytes`() {
        val jpeg = makeMinimalJpeg()
        val mp4 = makeFakeMp4()

        val result = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = 1)
        )

        val resultStr = String(result, Charsets.UTF_8)
        val itemPattern = Regex("Item:Semantic=\"MotionPhoto\" Item:Length=\"(\\d+)\"")
        val declaredLength = itemPattern.find(resultStr)!!.groupValues[1].toLong()
        assertEquals(mp4.size.toLong(), declaredLength)
    }

    @Test
    fun `write primary item length equals jpeg part bytes`() {
        val jpeg = makeMinimalJpeg()
        val mp4 = makeFakeMp4()

        val result = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )

        val jpegPartSize = result.size - mp4.size
        val resultStr = String(result, Charsets.UTF_8)
        val primaryPattern = Regex("Item:Semantic=\"Primary\" Item:Length=\"(\\d+)\"")
        val match = primaryPattern.find(resultStr)
        assertNotNull("Primary Item:Length must be present in XMP", match)
        val declaredPrimaryLength = match!!.groupValues[1].toLong()
        assertEquals("Primary Item:Length must equal JPEG part bytes", jpegPartSize.toLong(), declaredPrimaryLength)
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

    private fun makeMultiAppnJpeg(): ByteArray {
        // SOI + APP0 (JFIF) + APP1 (EXIF) + SOS + entropy + EOI
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // SOI
            0xFF.toByte(), 0xE0.toByte(), // APP0 marker
            0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00,
            0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0xFF.toByte(), 0xE1.toByte(), // APP1 (EXIF-like)
            0x00, 0x0A,
            0x45, 0x78, 0x69, 0x66, 0x00, 0x00, 0x00, 0x00, // "Exif\0\0\0\0"
            0xFF.toByte(), 0xDA.toByte(), // SOS marker
            0x00, 0x08,
            0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, 0x7F, 0x00,
            0xFF.toByte(), 0xD9.toByte()  // EOI
        )
    }

    private fun makeJpegWithDescriptiveXmp(): ByteArray {
        val xmp = """
            <?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about=""
                  xmlns:xmp="http://ns.adobe.com/xap/1.0/"
                  xmlns:dc="http://purl.org/dc/elements/1.1/"
                  xmp:Rating="5">
                  <dc:creator>Teotis Camera</dc:creator>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
            <?xpacket end="w"?>
        """.trimIndent()
        val identifier = MotionPhotoContainerParser.ADOBE_XMP_IDENTIFIER
        val payload = identifier + xmp.toByteArray(Charsets.UTF_8)
        val length = payload.size + 2
        val segment = ByteArray(4 + payload.size).apply {
            this[0] = 0xFF.toByte()
            this[1] = 0xE1.toByte()
            this[2] = (length shr 8).toByte()
            this[3] = (length and 0xFF).toByte()
            System.arraycopy(payload, 0, this, 4, payload.size)
        }
        val plain = makeMinimalJpeg()
        return plain.copyOfRange(0, 2) + segment + plain.copyOfRange(2, plain.size)
    }

    private fun indexOfMarker(bytes: ByteArray, marker: Int): Int {
        for (i in 0 until bytes.size - 1) {
            if (bytes[i] == 0xFF.toByte() && (bytes[i + 1].toInt() and 0xFF) == marker) {
                return i
            }
        }
        return -1
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
