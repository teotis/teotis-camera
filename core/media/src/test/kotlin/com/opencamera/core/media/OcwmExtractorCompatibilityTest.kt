package com.opencamera.core.media

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Generates a small archived JPEG fixture using OcwmJpegContainer,
 * then verifies round-trip extraction produces the original payload.
 *
 * This test exists to prove Kotlin-side archive generation is correct
 * before invoking the standalone Python extractor.
 */
class OcwmExtractorCompatibilityTest {

    @Test
    fun kotlinRoundTripProducesOriginalPayload() {
        val visible = syntheticVisibleJpeg()
        val original = syntheticOriginalJpeg()

        val manifest = ReversibleWatermarkArchiveManifest(
            watermarkTemplateId = "compat-test",
            visibleImageSha256 = sha256Hex(visible),
            payloadSha256 = sha256Hex(original),
            payloadLength = original.size.toLong()
        )
        val archive = OcwmJpegContainer.EmbeddedArchive(manifest, original)

        val archived = OcwmJpegContainer.embedArchive(visible, archive)
        val extracted = OcwmJpegContainer.extractArchive(archived)

        assertNotNull(extracted, "extractArchive must return non-null for valid archive")
        assertEquals(manifest, extracted.manifest)
        assertContentEquals(original, extracted.payload, "extracted payload must match original")
    }

    @Test
    fun `kotlinRoundTripProducesOriginalPayload with professional-bottom-bar template`() {
        val visible = syntheticVisibleJpeg()
        val original = syntheticOriginalJpeg()

        val manifest = ReversibleWatermarkArchiveManifest(
            watermarkTemplateId = "professional-bottom-bar",
            visibleImageSha256 = sha256Hex(visible),
            payloadSha256 = sha256Hex(original),
            payloadLength = original.size.toLong()
        )
        val archive = OcwmJpegContainer.EmbeddedArchive(manifest, original)

        val archived = OcwmJpegContainer.embedArchive(visible, archive)
        val extracted = OcwmJpegContainer.extractArchive(archived)

        assertNotNull(extracted, "extractArchive must return non-null for professional-bottom-bar template")
        assertEquals(manifest, extracted.manifest)
        assertEquals("professional-bottom-bar", extracted.manifest.watermarkTemplateId)
        assertContentEquals(original, extracted.payload, "extracted payload must match original")
    }

    @Test
    fun archivedJpegIsValidJpegStructure() {
        val visible = syntheticVisibleJpeg()
        val original = syntheticOriginalJpeg()

        val manifest = ReversibleWatermarkArchiveManifest(
            watermarkTemplateId = "compat-test",
            visibleImageSha256 = sha256Hex(visible),
            payloadSha256 = sha256Hex(original),
            payloadLength = original.size.toLong()
        )
        val archive = OcwmJpegContainer.EmbeddedArchive(manifest, original)

        val archived = OcwmJpegContainer.embedArchive(visible, archive)

        // Must start with SOI
        assertEquals(0xFF.toByte(), archived[0], "byte 0 must be 0xFF")
        assertEquals(0xD8.toByte(), archived[1], "byte 1 must be 0xD8")

        // Must contain SOS marker
        var sosFound = false
        var i = 2
        while (i < archived.size - 1) {
            if (archived[i] == 0xFF.toByte() && archived[i + 1] == 0xDA.toByte()) {
                sosFound = true
                break
            }
            if (archived[i] == 0xFF.toByte() && i + 3 < archived.size) {
                val segLen = ((archived[i + 2].toInt() and 0xFF) shl 8) or (archived[i + 3].toInt() and 0xFF)
                if (segLen >= 2) i += 2 + segLen else break
            } else break
        }
        assert(sosFound) { "archived JPEG must contain SOS marker" }
    }

    @Test
    fun fixtureWrittenToTempDirectory() {
        val visible = syntheticVisibleJpeg()
        val original = syntheticOriginalJpeg()

        val manifest = ReversibleWatermarkArchiveManifest(
            watermarkTemplateId = "compat-test",
            visibleImageSha256 = sha256Hex(visible),
            payloadSha256 = sha256Hex(original),
            payloadLength = original.size.toLong()
        )
        val archive = OcwmJpegContainer.EmbeddedArchive(manifest, original)
        val archived = OcwmJpegContainer.embedArchive(visible, archive)

        // Write fixture to Gradle test temp directory
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "ocwm-compat-test")
        tmpDir.mkdirs()
        val fixtureFile = File(tmpDir, "test-archived.jpg")
        fixtureFile.writeBytes(archived)
        val originalFile = File(tmpDir, "test-original.jpg")
        originalFile.writeBytes(original)

        assert(fixtureFile.exists()) { "fixture file must exist" }
        assert(fixtureFile.length() > original.size) { "archived must be larger than original" }

        // Verify the written fixture is extractable
        val readBack = OcwmJpegContainer.extractArchive(fixtureFile.readBytes())
        assertNotNull(readBack, "written fixture must be extractable")
        assertContentEquals(original, readBack.payload)

        // Clean up
        fixtureFile.delete()
        originalFile.delete()
        tmpDir.delete()
    }

    @Test
    fun pythonExtractorRecoversOriginalPayload() {
        val scriptPath = findPythonExtractor() ?: return // skip if script not found
        val pythonBin = findPython3() ?: return // skip if python3 not available

        val visible = syntheticVisibleJpeg()
        val original = syntheticOriginalJpeg()

        val manifest = ReversibleWatermarkArchiveManifest(
            watermarkTemplateId = "python-compat",
            visibleImageSha256 = sha256Hex(visible),
            payloadSha256 = sha256Hex(original),
            payloadLength = original.size.toLong()
        )
        val archive = OcwmJpegContainer.EmbeddedArchive(manifest, original)
        val archived = OcwmJpegContainer.embedArchive(visible, archive)

        val tmpDir = File(System.getProperty("java.io.tmpdir"), "ocwm-python-compat")
        tmpDir.mkdirs()
        val fixtureFile = File(tmpDir, "test-archived.jpg")
        fixtureFile.writeBytes(archived)
        val outputFile = File(tmpDir, "extracted-output.jpg")

        try {
            val process = ProcessBuilder(pythonBin, scriptPath, fixtureFile.absolutePath, outputFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            val stdout = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            assertEquals(0, exitCode, "Python extractor must exit 0, stdout: $stdout")
            assert(outputFile.exists()) { "Python extractor must produce output file" }
            assertContentEquals(original, outputFile.readBytes(), "Python-extracted payload must match original")
        } finally {
            fixtureFile.delete()
            outputFile.delete()
            tmpDir.delete()
        }
    }

    @Test
    fun fixtureWrittenWhenRequestedByVerificationScript() {
        val fixtureDir = System.getenv("OCWM_FIXTURE_DIR")
            ?.takeIf { it.isNotBlank() }
            ?: return
        val visible = syntheticVisibleJpeg()
        val original = syntheticOriginalJpeg()
        val archived = archivedJpeg(
            visible = visible,
            original = original,
            templateId = "script-compat"
        )

        val directory = File(fixtureDir)
        require(directory.exists() || directory.mkdirs()) {
            "failed to create OCWM fixture directory: $fixtureDir"
        }
        File(directory, "test-archived.jpg").writeBytes(archived)
        File(directory, "test-original.jpg").writeBytes(original)
    }

    private fun archivedJpeg(
        visible: ByteArray,
        original: ByteArray,
        templateId: String
    ): ByteArray {
        val manifest = ReversibleWatermarkArchiveManifest(
            watermarkTemplateId = templateId,
            visibleImageSha256 = sha256Hex(visible),
            payloadSha256 = sha256Hex(original),
            payloadLength = original.size.toLong()
        )
        return OcwmJpegContainer.embedArchive(
            visibleJpeg = visible,
            archive = OcwmJpegContainer.EmbeddedArchive(manifest, original)
        )
    }

    private fun findPythonExtractor(): String? {
        val candidates = listOf(
            "tool/extract_ocwm_original.py",
            "../tool/extract_ocwm_original.py",
            "../../tool/extract_ocwm_original.py"
        )
        for (c in candidates) {
            val f = File(c).canonicalFile
            if (f.exists()) return f.absolutePath
        }
        return null
    }

    private fun findPython3(): String? {
        return try {
            val p = ProcessBuilder("python3", "--version").start()
            p.waitFor()
            if (p.exitValue() == 0) "python3" else null
        } catch (_: Exception) {
            null
        }
    }

    // ── Helpers ──

    private fun syntheticVisibleJpeg(): ByteArray {
        // Minimal JPEG: SOI + APP0 + SOS + scan data + EOI
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x04, 0x00, 0x00,
            0xFF.toByte(), 0xDA.toByte(), 0x00, 0x04, 0x00, 0x00,
            0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(),
            0xFF.toByte(), 0xD9.toByte()
        )
    }

    private fun syntheticOriginalJpeg(): ByteArray {
        // Synthetic "original" JPEG payload (not a real image, just bytes)
        val header = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00,
            0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0xFF.toByte(), 0xDA.toByte(), 0x00, 0x08,
            0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, 0x7F, 0x42,
            0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
            0xFF.toByte(), 0xD9.toByte()
        )
        // Pad to make it larger (simulates a real JPEG payload)
        return header + ByteArray(500) { (it and 0xFF).toByte() }
    }
}
