package com.opencamera.app.camera

import com.opencamera.core.media.OcwmJpegContainer
import com.opencamera.core.media.ReversibleWatermarkArchiveManifest
import com.opencamera.core.media.sha256Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoWatermarkArchiveEditorTest {

    private fun minimalJpeg(): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(),
        0xFF.toByte(), 0xE0.toByte(), 0x00, 0x04, 0x00, 0x00,
        0xFF.toByte(), 0xDA.toByte(), 0x00, 0x04, 0x00, 0x00,
        0x11, 0x22, 0x33,
        0xFF.toByte(), 0xD9.toByte()
    )

    @Test
    fun `buildWatermarkArchive returns archive for valid inputs`() {
        val original = minimalJpeg()
        val visible = minimalJpeg()
        val result = buildWatermarkArchive(
            originalBytes = original,
            visibleBytes = visible,
            templateId = "classic-overlay",
            originalWidth = 4000,
            originalHeight = 3000
        )
        assertNotNull(result)
        assertEquals("classic-overlay", result.manifest.watermarkTemplateId)
        assertEquals("after-upstream-postprocessors-before-watermark", result.manifest.pipelineStage)
        assertEquals(4000, result.manifest.originalWidth)
        assertEquals(3000, result.manifest.originalHeight)
        assertTrue(original.contentEquals(result.payload))
        assertEquals(sha256Hex(original), result.manifest.payloadSha256)
        assertEquals(sha256Hex(visible), result.manifest.visibleImageSha256)
    }

    @Test
    fun `buildWatermarkArchive returns null for empty originalBytes`() {
        val visible = minimalJpeg()
        val result = buildWatermarkArchive(
            originalBytes = ByteArray(0),
            visibleBytes = visible,
            templateId = "classic-overlay",
            originalWidth = 100,
            originalHeight = 100
        )
        assertNull(result)
    }

    @Test
    fun `buildWatermarkArchive returns null for empty visibleBytes`() {
        val original = minimalJpeg()
        val result = buildWatermarkArchive(
            originalBytes = original,
            visibleBytes = ByteArray(0),
            templateId = "classic-overlay",
            originalWidth = 100,
            originalHeight = 100
        )
        assertNull(result)
    }

    @Test
    fun `embedArchiveAfterVisibleWrite returns archive bytes for valid inputs`() {
        val original = minimalJpeg()
        val visible = minimalJpeg()
        val (bytes, warning) = embedArchiveAfterVisibleWrite(
            originalBytes = original,
            visibleBytesAfterExifRestore = visible,
            templateId = "travel-polaroid",
            originalWidth = 2000,
            originalHeight = 1500
        )
        assertNotNull(bytes)
        assertEquals(null, warning)

        val extracted = OcwmJpegContainer.extractArchive(bytes!!)
        assertNotNull(extracted)
        assertTrue(original.contentEquals(extracted.payload))
        assertEquals("travel-polaroid", extracted.manifest.watermarkTemplateId)
    }

    @Test
    fun `embedArchiveAfterVisibleWrite returns warning when visible bytes unavailable`() {
        val original = minimalJpeg()
        val (bytes, warning) = embedArchiveAfterVisibleWrite(
            originalBytes = original,
            visibleBytesAfterExifRestore = null,
            templateId = "classic-overlay",
            originalWidth = 100,
            originalHeight = 100
        )
        assertNull(bytes)
        assertEquals("archive-visible-unavailable", warning)
    }

    @Test
    fun `embedArchiveAfterVisibleWrite returns warning when original bytes empty`() {
        val visible = minimalJpeg()
        val (bytes, warning) = embedArchiveAfterVisibleWrite(
            originalBytes = ByteArray(0),
            visibleBytesAfterExifRestore = visible,
            templateId = "classic-overlay",
            originalWidth = 100,
            originalHeight = 100
        )
        assertNull(bytes)
        assertEquals("archive-input-empty", warning)
    }

    @Test
    fun `embedArchiveAfterVisibleWrite covers pure-text template`() {
        val original = minimalJpeg()
        val visible = minimalJpeg()
        val (bytes, warning) = embedArchiveAfterVisibleWrite(
            originalBytes = original,
            visibleBytesAfterExifRestore = visible,
            templateId = "pure-text",
            originalWidth = 3000,
            originalHeight = 2000
        )
        assertNotNull(bytes)
        assertEquals(null, warning)

        val extracted = OcwmJpegContainer.extractArchive(bytes!!)
        assertNotNull(extracted)
        assertTrue(original.contentEquals(extracted.payload))
        assertEquals("pure-text", extracted.manifest.watermarkTemplateId)
    }

    @Test
    fun `embedArchiveAfterVisibleWrite covers blur-four-border template`() {
        val original = minimalJpeg()
        val visible = minimalJpeg()
        val (bytes, warning) = embedArchiveAfterVisibleWrite(
            originalBytes = original,
            visibleBytesAfterExifRestore = visible,
            templateId = "blur-four-border",
            originalWidth = 3000,
            originalHeight = 2000
        )
        assertNotNull(bytes)
        assertEquals(null, warning)

        val extracted = OcwmJpegContainer.extractArchive(bytes!!)
        assertNotNull(extracted)
        assertTrue(original.contentEquals(extracted.payload))
        assertEquals("blur-four-border", extracted.manifest.watermarkTemplateId)
    }

    @Test
    fun `embedArchiveAfterVisibleWrite covers professional-bottom-bar template`() {
        val original = minimalJpeg()
        val visible = minimalJpeg()
        val (bytes, warning) = embedArchiveAfterVisibleWrite(
            originalBytes = original,
            visibleBytesAfterExifRestore = visible,
            templateId = "professional-bottom-bar",
            originalWidth = 3000,
            originalHeight = 2000
        )
        assertNotNull(bytes)
        assertEquals(null, warning)

        val extracted = OcwmJpegContainer.extractArchive(bytes!!)
        assertNotNull(extracted)
        assertTrue(original.contentEquals(extracted.payload))
        assertEquals("professional-bottom-bar", extracted.manifest.watermarkTemplateId)
    }

    @Test
    fun `embedArchiveAfterVisibleWrite covers retro-frame template`() {
        val original = minimalJpeg()
        val visible = minimalJpeg()
        val (bytes, warning) = embedArchiveAfterVisibleWrite(
            originalBytes = original,
            visibleBytesAfterExifRestore = visible,
            templateId = "retro-frame",
            originalWidth = 3000,
            originalHeight = 2000
        )
        assertNotNull(bytes)
        assertEquals(null, warning)

        val extracted = OcwmJpegContainer.extractArchive(bytes!!)
        assertNotNull(extracted)
        assertTrue(original.contentEquals(extracted.payload))
        assertEquals("retro-frame", extracted.manifest.watermarkTemplateId)
    }
}
