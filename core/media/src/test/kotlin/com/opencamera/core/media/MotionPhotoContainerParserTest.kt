package com.opencamera.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPhotoContainerParserTest {

    // --- Google Photos vector (dual namespace, full container) ---

    @Test
    fun `google vector parses dual namespaces and container items`() {
        val bytes = MotionPhotoFixtureCorpus.googleDualNamespace()
        val xmp = MotionPhotoContainerParser.extractXmp(bytes)

        assertNotNull(xmp)
        assertTrue(xmp!!.hasCameraNamespace)
        assertTrue(xmp.hasGCameraNamespace)
        assertTrue(xmp.cameraMotionPhoto)
        assertTrue(xmp.gCameraMotionPhoto)
        assertEquals(42L, xmp.presentationTimestampUs)
        assertEquals(2, xmp.items.size)
        assertEquals("Primary", xmp.items[0].semantic)
        assertEquals("MotionPhoto", xmp.items[1].semantic)
        assertEquals("video/mp4", xmp.items[1].mimeType)
        assertNotNull(xmp.microVideoOffset)
    }

    @Test
    fun `google vector validates structurally`() {
        val bytes = MotionPhotoFixtureCorpus.googleDualNamespace()
        val analysis = MotionPhotoContainerParser.analyze(bytes)

        assertTrue("issues=${analysis.issues}", analysis.valid)
        assertEquals(bytes.size.toLong(), analysis.motionOffset!! + analysis.motionLength!!)
        val mp4 = MotionPhotoFixtureCorpus.fakeMp4()
        assertEquals(mp4.size.toLong(), analysis.motionLength)
    }

    @Test
    fun `google vector micro video offset equals jpeg part length`() {
        val bytes = MotionPhotoFixtureCorpus.googleDualNamespace()
        val analysis = MotionPhotoContainerParser.analyze(bytes)

        assertEquals(analysis.motionOffset, analysis.xmp!!.microVideoOffset)
        assertEquals(analysis.motionOffset, analysis.xmp!!.containerPrimaryLength)
    }

    // --- Samsung micro-video vector (GCamera-only + MicroVideoOffset) ---

    @Test
    fun `samsung vector parses GCamera namespace and micro video offset`() {
        val bytes = MotionPhotoFixtureCorpus.samsungMicroVideo()
        val analysis = MotionPhotoContainerParser.analyze(bytes)

        val xmp = analysis.xmp
        assertNotNull(xmp)
        assertFalse("no Camera: prefix in samsung vector", xmp!!.hasCameraNamespace)
        assertTrue(xmp.hasGCameraNamespace)
        assertTrue(xmp.gCameraMotionPhoto)
        assertNotNull(xmp.microVideoOffset)
        assertEquals(analysis.motionOffset, xmp.microVideoOffset)
    }

    @Test
    fun `samsung vector without container directory is still valid`() {
        val bytes = MotionPhotoFixtureCorpus.samsungMicroVideo()
        val analysis = MotionPhotoContainerParser.analyze(bytes)

        // Legacy vector: no Container Directory, so item checks are skipped; offset via
        // MicroVideoOffset is authoritative.
        assertTrue("issues=${analysis.issues}", analysis.valid)
        assertTrue(analysis.motionDeclared)
    }

    // --- Legacy Camera-only vector ---

    @Test
    fun `legacy camera-only vector resolves offset from primary length`() {
        val bytes = MotionPhotoFixtureCorpus.legacyCameraOnly()
        val analysis = MotionPhotoContainerParser.analyze(bytes)

        val xmp = analysis.xmp
        assertNotNull(xmp)
        assertTrue(xmp!!.hasCameraNamespace)
        assertFalse(xmp.hasGCameraNamespace)
        assertTrue(xmp.cameraMotionPhoto)
        assertNull("no MicroVideoOffset in legacy vector", xmp.microVideoOffset)
        assertEquals(analysis.motionOffset, xmp.containerPrimaryLength)
        assertTrue("issues=${analysis.issues}", analysis.valid)
    }

    // --- Multi-APPn vector ---

    @Test
    fun `xmp app1 is inserted after full appn sequence`() {
        val bytes = MotionPhotoFixtureCorpus.googleDualNamespace(
            jpegBytes = MotionPhotoFixtureCorpus.multiAppnJpeg()
        )
        val segments = MotionPhotoContainerParser.readJpegSegments(bytes)

        val markers = segments.map { it.marker }
        assertTrue(markers.containsAll(listOf(0xE0, 0xE1)))
        val app0 = segments.first { it.marker == 0xE0 }
        val exifApp1 = segments.filter { it.marker == 0xE1 && !it.isAdobeXmp }
        val xmpApp1 = segments.first { it.isAdobeXmp }
        assertEquals(1, exifApp1.size)
        assertTrue("XMP must come after APP0", xmpApp1.start > app0.end)
        assertTrue("XMP must start at EXIF APP1 end", xmpApp1.start >= exifApp1.first().end)
        // XMP must be before SOS (0xDA).
        val sos = segments.first { it.marker == 0xDA }
        assertTrue(xmpApp1.start < sos.start)
    }

    @Test
    fun `multi appn vector validates`() {
        val bytes = MotionPhotoFixtureCorpus.googleDualNamespace(
            jpegBytes = MotionPhotoFixtureCorpus.multiAppnJpeg()
        )
        val analysis = MotionPhotoContainerParser.analyze(bytes)
        assertTrue("issues=${analysis.issues}", analysis.valid)
    }

    // --- Negative vectors ---

    @Test
    fun `plain jpeg without xmp is invalid motion photo`() {
        val analysis = MotionPhotoContainerParser.analyze(MotionPhotoFixtureCorpus.minimalJpeg())
        assertFalse(analysis.valid)
        assertTrue(analysis.issues.contains("xmp-app1-missing"))
    }

    @Test
    fun `corrupted length total is rejected`() {
        val bytes = MotionPhotoFixtureCorpus.googleDualNamespace()
        // Truncate 3 bytes: declared total now exceeds the real file size.
        val truncated = bytes.copyOf(bytes.size - 3)
        val analysis = MotionPhotoContainerParser.analyze(truncated)
        assertFalse(analysis.valid)
        assertTrue(analysis.issues.contains("length-total-mismatch"))
    }

    @Test
    fun `offset pointing into jpeg data is rejected`() {
        val bytes = MotionPhotoFixtureCorpus.samsungMicroVideo()
        // Shift the appended MP4 by 10 bytes so the declared offset is wrong.
        val moved = bytes.copyOfRange(0, bytes.size - MotionPhotoFixtureCorpus.fakeMp4().size) +
            ByteArray(10) + MotionPhotoFixtureCorpus.fakeMp4()
        val analysis = MotionPhotoContainerParser.analyze(moved)
        assertFalse(analysis.valid)
        assertTrue(
            analysis.issues.contains("motion-offset-not-mp4") ||
                analysis.issues.contains("length-total-mismatch")
        )
    }

    @Test
    fun `non jpeg input is invalid`() {
        val analysis = MotionPhotoContainerParser.analyze(ByteArray(16) { 0x00 })
        assertFalse(analysis.valid)
    }
}
