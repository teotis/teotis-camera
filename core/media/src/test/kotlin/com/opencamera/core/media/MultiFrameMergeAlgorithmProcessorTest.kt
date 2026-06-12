package com.opencamera.core.media

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MultiFrameMergeAlgorithmProcessorTest {

    private fun createSyntheticJpeg(
        dir: File,
        name: String,
        width: Int = 64,
        height: Int = 64,
        baseRgb: Int = 0x808080
    ): File {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (baseRgb shr 16) and 0xFF
                val g = (baseRgb shr 8) and 0xFF
                val b = baseRgb and 0xFF
                image.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
        }
        val file = File(dir, name)
        val written = ImageIO.write(image, "jpg", file)
        require(written) { "ImageIO.write failed for $name" }
        val readBack = ImageIO.read(file)
        require(readBack != null) { "ImageIO.read failed for written JPEG: ${file.absolutePath}" }
        return file
    }

    private fun buildMergeRequest(
        frameCount: Int,
        inputPaths: List<String>
    ): AlgorithmRequest {
        return AlgorithmRequest(
            node = AlgorithmNode(
                id = "merge-night",
                type = AlgorithmType.MULTI_FRAME_MERGE,
                inputs = inputPaths,
                output = "/tmp/merged.jpg",
                requirement = AlgorithmRequirement.REQUIRED,
                fallback = AlgorithmFallback.FAIL_SHOT
            ),
            inputs = inputPaths.map { path ->
                MediaInputRef(
                    path = path,
                    handle = MediaOutputHandle(displayPath = path),
                    mimeType = "image/jpeg"
                )
            },
            metadata = MediaMetadata(
                customTags = mapOf("frameCount" to frameCount.toString())
            )
        )
    }

    @Test
    fun `multi frame fusion processor returns Applied when valid JPEG frames exist`() =
        runTest {
            val tempDir = createTempDir(prefix = "fusion-alg-test-")
            val frameA = createSyntheticJpeg(tempDir, "frame_a.jpg", baseRgb = 0x404040)
            val frameB = createSyntheticJpeg(tempDir, "frame_b.jpg", baseRgb = 0xC0C0C0)

            try {
                val processor = MultiFrameFusionProcessor().toAlgorithmProcessor()
                val request = buildMergeRequest(
                    frameCount = 3,
                    inputPaths = listOf(frameA.absolutePath, frameB.absolutePath)
                )

                assertTrue(processor.canProcess(request))
                val result = processor.process(request)

                assertTrue(result is AlgorithmResult.Applied)
                assertTrue((result as AlgorithmResult.Applied).notes.any { it.startsWith("merge:applied=true") })
                assertTrue(result.notes.any { it.startsWith("merge:inputs=") })
                assertTrue(result.notes.any { it.startsWith("merge:strategy=") })
            } finally {
                tempDir.deleteRecursively()
            }
        }

    @Test
    fun `multi frame fusion processor returns Skipped when frame count is one`() = runTest {
        val processor = MultiFrameFusionProcessor().toAlgorithmProcessor()
        val request = buildMergeRequest(frameCount = 1, inputPaths = listOf("/tmp/single.jpg"))

        assertFalse(processor.canProcess(request))
        val result = processor.process(request)

        assertTrue(result is AlgorithmResult.Skipped)
        assertTrue(
            (result as AlgorithmResult.Skipped).reason.contains("no-bundle-or-single-frame")
        )
    }

    @Test
    fun `multi frame fusion processor returns Skipped when no intermediate inputs`() = runTest {
        val processor = MultiFrameFusionProcessor().toAlgorithmProcessor()
        val request = buildMergeRequest(frameCount = 5, inputPaths = emptyList())

        assertFalse(processor.canProcess(request))
        val result = processor.process(request)

        assertTrue(result is AlgorithmResult.Skipped)
    }

    @Test
    fun `temp files are deleted after fusion regardless of result`() = runTest {
        val tempDir = createTempDir(prefix = "fusion-cleanup-")
        val frameA = createSyntheticJpeg(tempDir, "burst_a.jpg", baseRgb = 0x404040)
        val frameB = createSyntheticJpeg(tempDir, "burst_b.jpg", baseRgb = 0x808080)

        try {
            val processor = MultiFrameFusionProcessor().toAlgorithmProcessor()
            val request = buildMergeRequest(
                frameCount = 4,
                inputPaths = listOf(frameA.absolutePath, frameB.absolutePath)
            )

            val result = processor.process(request)
            assertTrue(result is AlgorithmResult.Applied)
            // Only intermediate frames (all but last) are cleaned up; last is the reference/output
            assertTrue(frameA.exists().not(), "intermediate frame should be cleaned up")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
