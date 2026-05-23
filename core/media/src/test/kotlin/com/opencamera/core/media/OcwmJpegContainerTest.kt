package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OcwmJpegContainerTest {

    // ── Task 1: Manifest round-trip ──

    @Test
    fun manifestRoundTripPreservesAllFields() {
        val manifest = ReversibleWatermarkArchiveManifest(
            watermarkTemplateId = "classic-overlay",
            visibleImageSha256 = "aabbccdd11223344",
            payloadSha256 = "1122334455667788",
            payloadLength = 123456L,
            originalWidth = 4000,
            originalHeight = 3000
        )
        val json = manifest.toJson()
        val decoded = ReversibleWatermarkArchiveManifest.fromJson(json)
        assertEquals(manifest, decoded)
    }

    @Test
    fun manifestJsonKeyOrderingIsDeterministic() {
        val manifest = ReversibleWatermarkArchiveManifest(
            watermarkTemplateId = "t1",
            visibleImageSha256 = "aa",
            payloadSha256 = "bb",
            payloadLength = 100L
        )
        val json = manifest.toJson()
        val expected = """{"schema":"org.opencamera.reversible-watermark","version":1,"container":"jpeg-app15-ocwm","payloadKind":"embedded-original-jpeg","payloadMimeType":"image/jpeg","payloadCompression":"none","pipelineStage":"after-upstream-postprocessors-before-watermark","watermarkTemplateId":"t1","visibleImageSha256":"aa","payloadSha256":"bb","payloadLength":100,"originalWidth":0,"originalHeight":0}"""
        assertEquals(expected, json)
    }

    @Test
    fun manifestRejectsMissingRequiredKey() {
        val json = """{"schema":"org.opencamera.reversible-watermark","version":1}"""
        assertFailsWith<IllegalArgumentException> {
            ReversibleWatermarkArchiveManifest.fromJson(json)
        }
    }

    @Test
    fun manifestRejectsUnsupportedSchema() {
        val json = """{"schema":"wrong","version":1,"container":"jpeg-app15-ocwm","payloadKind":"embedded-original-jpeg","payloadMimeType":"image/jpeg","payloadCompression":"none","pipelineStage":"s","watermarkTemplateId":"t","visibleImageSha256":"a","payloadSha256":"b","payloadLength":1}"""
        assertFailsWith<IllegalArgumentException> {
            ReversibleWatermarkArchiveManifest.fromJson(json)
        }
    }

    @Test
    fun manifestRejectsUnsupportedVersion() {
        val json = """{"schema":"org.opencamera.reversible-watermark","version":2,"container":"jpeg-app15-ocwm","payloadKind":"embedded-original-jpeg","payloadMimeType":"image/jpeg","payloadCompression":"none","pipelineStage":"s","watermarkTemplateId":"t","visibleImageSha256":"a","payloadSha256":"b","payloadLength":1}"""
        assertFailsWith<IllegalArgumentException> {
            ReversibleWatermarkArchiveManifest.fromJson(json)
        }
    }

    @Test
    fun manifestRejectsUnsupportedContainer() {
        val json = """{"schema":"org.opencamera.reversible-watermark","version":1,"container":"wrong","payloadKind":"embedded-original-jpeg","payloadMimeType":"image/jpeg","payloadCompression":"none","pipelineStage":"s","watermarkTemplateId":"t","visibleImageSha256":"a","payloadSha256":"b","payloadLength":1}"""
        assertFailsWith<IllegalArgumentException> {
            ReversibleWatermarkArchiveManifest.fromJson(json)
        }
    }

    @Test
    fun manifestRejectsNegativePayloadLength() {
        val json = """{"schema":"org.opencamera.reversible-watermark","version":1,"container":"jpeg-app15-ocwm","payloadKind":"embedded-original-jpeg","payloadMimeType":"image/jpeg","payloadCompression":"none","pipelineStage":"s","watermarkTemplateId":"t","visibleImageSha256":"a","payloadSha256":"b","payloadLength":-1}"""
        assertFailsWith<IllegalArgumentException> {
            ReversibleWatermarkArchiveManifest.fromJson(json)
        }
    }

    @Test
    fun sha256HexProducesCorrectDigest() {
        val data = "hello".toByteArray()
        val hex = sha256Hex(data)
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hex)
    }

    // ── Task 2: Encoder tests ──

    private fun minimalVisibleJpeg(): ByteArray {
        // FF D8 | FF E0 00 04 00 00 | FF DA 00 04 00 00 | 11 22 33 | FF D9
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x04, 0x00, 0x00,
            0xFF.toByte(), 0xDA.toByte(), 0x00, 0x04, 0x00, 0x00,
            0x11, 0x22, 0x33,
            0xFF.toByte(), 0xD9.toByte()
        )
    }

    private fun makeArchive(payloadSize: Int): OcwmJpegContainer.EmbeddedArchive {
        val payload = ByteArray(payloadSize) { (it and 0xFF).toByte() }
        val manifest = ReversibleWatermarkArchiveManifest(
            watermarkTemplateId = "classic-overlay",
            visibleImageSha256 = sha256Hex(minimalVisibleJpeg()),
            payloadSha256 = sha256Hex(payload),
            payloadLength = payload.size.toLong()
        )
        return OcwmJpegContainer.EmbeddedArchive(manifest, payload)
    }

    @Test
    fun encoderInsertsChunksBeforeSos() {
        val visible = minimalVisibleJpeg()
        val archive = makeArchive(100)
        val output = OcwmJpegContainer.embedArchive(visible, archive)

        assertTrue(output[0] == 0xFF.toByte() && output[1] == 0xD8.toByte(), "must start with SOI")

        val sosIndex = findMarkerIndex(output, 0xDA)
        val app15Index = findFirstApp15OcwmIndex(output)
        assertTrue(app15Index < sosIndex, "APP15 must appear before SOS")

        val app0Index = findMarkerIndex(output, 0xE0)
        assertTrue(app0Index < app15Index, "existing APP0 must remain before APP15")
    }

    @Test
    fun encoderPreservesVisibleScanData() {
        val visible = minimalVisibleJpeg()
        val archive = makeArchive(50)
        val output = OcwmJpegContainer.embedArchive(visible, archive)

        val sosIndex = findMarkerIndex(output, 0xDA)
        // SOS segment: marker(2) + length(2, value=4, includes itself) + paramData(2) = 6 bytes
        val sosSegLen = ((output[sosIndex + 2].toInt() and 0xFF) shl 8) or (output[sosIndex + 3].toInt() and 0xFF)
        val scanStart = sosIndex + 2 + sosSegLen
        assertEquals(0x11.toByte(), output[scanStart])
        assertEquals(0x22.toByte(), output[scanStart + 1])
        assertEquals(0x33.toByte(), output[scanStart + 2])
    }

    @Test
    fun encoderRejectsInvalidJpeg() {
        assertFailsWith<IllegalArgumentException> {
            OcwmJpegContainer.embedArchive(byteArrayOf(0x00, 0x01), makeArchive(10))
        }
    }

    @Test
    fun encoderRejectsEmptyPayload() {
        val visible = minimalVisibleJpeg()
        val archive = makeArchive(0)
        assertFailsWith<IllegalArgumentException> {
            OcwmJpegContainer.embedArchive(visible, archive)
        }
    }

    @Test
    fun encoderRejectsPayloadLengthMismatch() {
        val visible = minimalVisibleJpeg()
        val payload = ByteArray(100) { 0 }
        val manifest = ReversibleWatermarkArchiveManifest(
            watermarkTemplateId = "t",
            visibleImageSha256 = sha256Hex(visible),
            payloadSha256 = sha256Hex(payload),
            payloadLength = 999L // mismatch
        )
        assertFailsWith<IllegalArgumentException> {
            OcwmJpegContainer.embedArchive(visible, OcwmJpegContainer.EmbeddedArchive(manifest, payload))
        }
    }

    @Test
    fun encoderRejectsPayloadSha256Mismatch() {
        val visible = minimalVisibleJpeg()
        val payload = ByteArray(100) { 0 }
        val manifest = ReversibleWatermarkArchiveManifest(
            watermarkTemplateId = "t",
            visibleImageSha256 = sha256Hex(visible),
            payloadSha256 = "0000000000000000",
            payloadLength = payload.size.toLong()
        )
        assertFailsWith<IllegalArgumentException> {
            OcwmJpegContainer.embedArchive(visible, OcwmJpegContainer.EmbeddedArchive(manifest, payload))
        }
    }

    @Test
    fun encoderRejectsJpegWithoutSos() {
        val noSos = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x04, 0x00, 0x00,
            0xFF.toByte(), 0xD9.toByte()
        )
        assertFailsWith<IllegalArgumentException>("jpeg-sos-missing") {
            OcwmJpegContainer.embedArchive(noSos, makeArchive(10))
        }
    }

    // ── Task 3: Decoder tests ──

    @Test
    fun decoderExtractsSingleChunkPayload() {
        val visible = minimalVisibleJpeg()
        val payload = ByteArray(200) { (it and 0xFF).toByte() }
        val archive = OcwmJpegContainer.EmbeddedArchive(
            ReversibleWatermarkArchiveManifest(
                watermarkTemplateId = "classic-overlay",
                visibleImageSha256 = sha256Hex(visible),
                payloadSha256 = sha256Hex(payload),
                payloadLength = payload.size.toLong()
            ),
            payload
        )
        val embedded = OcwmJpegContainer.embedArchive(visible, archive)
        val extracted = OcwmJpegContainer.extractArchive(embedded)

        assertNotNull(extracted)
        assertEquals(archive.manifest, extracted.manifest)
        assertTrue(payload.contentEquals(extracted.payload))
    }

    @Test
    fun decoderExtractsMultiChunkPayload() {
        val visible = minimalVisibleJpeg()
        val payload = ByteArray(120000) { (it and 0xFF).toByte() }
        val archive = OcwmJpegContainer.EmbeddedArchive(
            ReversibleWatermarkArchiveManifest(
                watermarkTemplateId = "classic-overlay",
                visibleImageSha256 = sha256Hex(visible),
                payloadSha256 = sha256Hex(payload),
                payloadLength = payload.size.toLong()
            ),
            payload
        )
        val embedded = OcwmJpegContainer.embedArchive(visible, archive)
        val extracted = OcwmJpegContainer.extractArchive(embedded)

        assertNotNull(extracted)
        assertEquals(archive.manifest, extracted.manifest)
        assertTrue(payload.contentEquals(extracted.payload))
    }

    @Test
    fun decoderReturnsNullForJpegWithoutArchive() {
        val visible = minimalVisibleJpeg()
        assertNull(OcwmJpegContainer.extractArchive(visible))
    }

    @Test
    fun decoderRejectsMissingChunk() {
        val visible = minimalVisibleJpeg()
        val payload = ByteArray(120000) { 0 }
        val archive = OcwmJpegContainer.EmbeddedArchive(
            ReversibleWatermarkArchiveManifest(
                watermarkTemplateId = "t",
                visibleImageSha256 = sha256Hex(visible),
                payloadSha256 = sha256Hex(payload),
                payloadLength = payload.size.toLong()
            ),
            payload
        )
        val embedded = OcwmJpegContainer.embedArchive(visible, archive)
        // Corrupt: remove one APP15 chunk by zeroing its magic
        val corrupted = corruptOneApp15Chunk(embedded)
        assertFailsWith<IllegalArgumentException>("ocwm-chunk-missing") {
            OcwmJpegContainer.extractArchive(corrupted)
        }
    }

    @Test
    fun decoderRejectsPayloadSha256Mismatch() {
        val visible = minimalVisibleJpeg()
        val payload = ByteArray(100) { 0 }
        val manifest = ReversibleWatermarkArchiveManifest(
            watermarkTemplateId = "t",
            visibleImageSha256 = sha256Hex(visible),
            payloadSha256 = sha256Hex(payload),
            payloadLength = payload.size.toLong()
        )
        val archive = OcwmJpegContainer.EmbeddedArchive(manifest, payload)
        val embedded = OcwmJpegContainer.embedArchive(visible, archive)
        // Corrupt the payload by flipping a byte after embedding
        val corrupted = embedded.copyOf()
        val sosIdx = findMarkerIndex(corrupted, 0xDA)
        // Find last APP15 before SOS and corrupt its payload
        var scanIdx = 2
        var lastApp15End = -1
        while (scanIdx < sosIdx && scanIdx + 3 < corrupted.size) {
            if (corrupted[scanIdx] != 0xFF.toByte()) break
            val marker = corrupted[scanIdx + 1]
            val segLen = ((corrupted[scanIdx + 2].toInt() and 0xFF) shl 8) or (corrupted[scanIdx + 3].toInt() and 0xFF)
            if (segLen < 2) break
            if (marker == 0xEF.toByte()) {
                lastApp15End = scanIdx + 2 + segLen - 1
            }
            scanIdx += 2 + segLen
        }
        if (lastApp15End > 0 && lastApp15End < corrupted.size) {
            corrupted[lastApp15End] = (corrupted[lastApp15End].toInt() xor 0xFF).toByte()
        }
        assertFailsWith<IllegalArgumentException>("ocwm-payload-sha256-mismatch") {
            OcwmJpegContainer.extractArchive(corrupted)
        }
    }

    @Test
    fun decoderRejectsUnsupportedVersion() {
        val visible = minimalVisibleJpeg()
        val payload = ByteArray(50) { 0 }
        val archive = OcwmJpegContainer.EmbeddedArchive(
            ReversibleWatermarkArchiveManifest(
                watermarkTemplateId = "t",
                visibleImageSha256 = sha256Hex(visible),
                payloadSha256 = sha256Hex(payload),
                payloadLength = payload.size.toLong()
            ),
            payload
        )
        val embedded = OcwmJpegContainer.embedArchive(visible, archive)
        // Corrupt version byte in first APP15 chunk (after magic 5 bytes, version is at offset 5)
        val sosIdx = findMarkerIndex(embedded, 0xDA)
        var scanIdx = 2
        while (scanIdx < sosIdx && scanIdx + 3 < embedded.size) {
            if (embedded[scanIdx] != 0xFF.toByte()) break
            val marker = embedded[scanIdx + 1]
            val segLen = ((embedded[scanIdx + 2].toInt() and 0xFF) shl 8) or (embedded[scanIdx + 3].toInt() and 0xFF)
            if (segLen < 2) break
            if (marker == 0xEF.toByte()) {
                // APP15 found - version is at payload+5 (after magic)
                embedded[scanIdx + 4 + 5] = 99.toByte()
                break
            }
            scanIdx += 2 + segLen
        }
        assertFailsWith<IllegalArgumentException>("ocwm-version-unsupported") {
            OcwmJpegContainer.extractArchive(embedded)
        }
    }

    // ── Helpers ──

    private fun findMarkerIndex(data: ByteArray, markerLow: Int): Int {
        var i = 0
        while (i < data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == markerLow.toByte()) return i
            if (i == 0 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()) {
                i = 2
                continue
            }
            if (data[i] == 0xFF.toByte() && i + 3 < data.size) {
                val segLen = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
                if (segLen < 2) break
                i += 2 + segLen
            } else {
                i++
            }
        }
        return -1
    }

    private fun findFirstApp15OcwmIndex(data: ByteArray): Int {
        var i = 2 // skip SOI
        while (i < data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xEF.toByte()) {
                // Check OCWM magic
                val payloadStart = i + 4
                if (payloadStart + 4 < data.size &&
                    data[payloadStart] == 'O'.code.toByte() &&
                    data[payloadStart + 1] == 'C'.code.toByte() &&
                    data[payloadStart + 2] == 'W'.code.toByte() &&
                    data[payloadStart + 3] == 'M'.code.toByte()
                ) return i
            }
            if (data[i] == 0xFF.toByte() && i + 3 < data.size) {
                val segLen = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
                if (segLen < 2) break
                i += 2 + segLen
            } else {
                i++
            }
        }
        return -1
    }

    private fun corruptOneApp15Chunk(data: ByteArray): ByteArray {
        val copy = data.copyOf()
        val sosIdx = findMarkerIndex(copy, 0xDA)
        var i = 2
        var count = 0
        while (i < sosIdx) {
            if (copy[i] == 0xFF.toByte() && copy[i + 1] == 0xEF.toByte()) {
                if (count == 1) {
                    // Corrupt magic of second chunk
                    copy[i + 4] = 0x00
                    return copy
                }
                count++
            }
            if (copy[i] == 0xFF.toByte() && i + 3 < copy.size) {
                val segLen = ((copy[i + 2].toInt() and 0xFF) shl 8) or (copy[i + 3].toInt() and 0xFF)
                if (segLen < 2) break
                i += 2 + segLen
            } else {
                i++
            }
        }
        return copy
    }
}
