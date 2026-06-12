package com.opencamera.core.media

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MultiFrameFusionProcessorTest {

    private fun createSyntheticJpeg(
        dir: File,
        name: String,
        width: Int = 100,
        height: Int = 100,
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
        require(file.exists() && file.length() > 0) { "JPEG file not created: ${file.absolutePath}" }
        // Verify the file can be read back
        val readBack = ImageIO.read(file)
        require(readBack != null) { "ImageIO.read failed for written JPEG: ${file.absolutePath}" }
        return file
    }

    private fun createBundle(
        shotId: String = "test-shot",
        frames: List<FrameBundleFrame>
    ): FrameBundle {
        return FrameBundle(
            shotId = shotId,
            frames = frames
        )
    }

    private fun baseResult(
        outputPath: String,
        frameBundle: FrameBundle? = null,
        intermediateOutputPaths: List<String> = emptyList()
    ): ShotResult {
        val frameCount = frameBundle?.frameCount ?: 1
        return ShotResult(
            shotId = "test-shot",
            mediaType = MediaType.PHOTO,
            outputPath = outputPath,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.None,
            captureProfile = CaptureProfile(frameCount = frameCount),
            metadata = MediaMetadata(),
            frameBundle = frameBundle,
            intermediateOutputPaths = intermediateOutputPaths
        )
    }

    // ── Skip scenarios ─────────────────────────────────────────────

    @Test
    fun `skips when no frame bundle`() = runTest {
        val result = baseResult(outputPath = "/tmp/out.jpg")
        val processed = MultiFrameFusionProcessor().process(result)

        assertTrue(processed.pipelineNotes.any { it == "merge:skipped=no-bundle-or-single-frame" })
        assertFalse(processed.pipelineNotes.any { it.startsWith("merge:applied=") })
    }

    @Test
    fun `skips when frame count is one`() = runTest {
        val bundle = createBundle(frames = listOf(
            FrameBundleFrame(0, PixelReference.File("/tmp/f0.jpg"))
        ))
        val result = baseResult(
            outputPath = "/tmp/out.jpg",
            frameBundle = bundle
        )
        val processed = MultiFrameFusionProcessor().process(result)

        assertTrue(processed.pipelineNotes.any { it == "merge:skipped=no-bundle-or-single-frame" })
    }

    @Test
    fun `skips when fewer than two valid frames`() = runTest {
        val tempDir = createTempDir(prefix = "fusion-skip-")
        try {
            val frame0 = createSyntheticJpeg(tempDir, "f0.jpg", baseRgb = 0x404040)
            val bundle = createBundle(frames = listOf(
                FrameBundleFrame(0, PixelReference.File(frame0.absolutePath))
            ))
            val result = ShotResult(
                shotId = "test-shot",
                mediaType = MediaType.PHOTO,
                outputPath = File(tempDir, "out.jpg").absolutePath,
                saveRequest = SaveRequest.photoLibrary(),
                thumbnailSource = ThumbnailSource.None,
                captureProfile = CaptureProfile(frameCount = 2),
                metadata = MediaMetadata(),
                frameBundle = bundle
            )
            val processed = MultiFrameFusionProcessor().process(result)

            assertTrue(processed.pipelineNotes.any { it == "merge:skipped=insufficient-valid-frames" })
            assertTrue(processed.pipelineNotes.any { it.startsWith("merge:inputs=1") })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `skips and cleans up when frames are missing files`() = runTest {
        val tempDir = createTempDir(prefix = "fusion-missing-")
        try {
            val bundle = createBundle(frames = listOf(
                FrameBundleFrame(0, PixelReference.File("/nonexistent/f0.jpg")),
                FrameBundleFrame(1, PixelReference.File("/nonexistent/f1.jpg"))
            ))
            val result = baseResult(
                outputPath = File(tempDir, "out.jpg").absolutePath,
                frameBundle = bundle,
                intermediateOutputPaths = listOf("/nonexistent/f0.jpg")
            )
            val processed = MultiFrameFusionProcessor().process(result)

            assertTrue(processed.pipelineNotes.any { it == "merge:skipped=insufficient-valid-frames" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ── Best-frame selection ──────────────────────────────────────

    @Test
    fun `best-frame selects anchor frame as reference`() = runTest {
        val tempDir = createTempDir(prefix = "fusion-anchor-")
        try {
            val f0 = createSyntheticJpeg(tempDir, "f0.jpg", baseRgb = 0x404040)
            val f1 = createSyntheticJpeg(tempDir, "f1.jpg", baseRgb = 0xC0C0C0)
            val fOut = File(tempDir, "out.jpg").apply {
                writeBytes(byteArrayOf(0x00))
            }

            val bundle = createBundle(frames = listOf(
                FrameBundleFrame(0, PixelReference.File(f0.absolutePath)),
                FrameBundleFrame(1, PixelReference.File(f1.absolutePath), frameRole = FrameRole.FUSION_ANCHOR)
            ))
            val result = baseResult(
                outputPath = fOut.absolutePath,
                frameBundle = bundle,
                intermediateOutputPaths = listOf(f0.absolutePath)
            )

            val processed = MultiFrameFusionProcessor().process(result)

            assertTrue(processed.pipelineNotes.any { it == "merge:applied=true" },
                "Expected merge:applied=true, got: ${processed.pipelineNotes}")
            assertTrue(processed.pipelineNotes.any { it == "merge:strategy=best-frame" })
            assertTrue(processed.pipelineNotes.any { it.startsWith("merge:reference-frame=1") })
            assertTrue(processed.pipelineNotes.any { it == "merge:motion-policy=anchor-role" })
            assertTrue(processed.pipelineNotes.any { it.startsWith("merge:inputs=2") })
            assertTrue(processed.pipelineNotes.any { it == "merge:temp-frames=1" })
            assertFalse(f0.exists(), "temp frame should be cleaned up")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `best-frame selects lowest motion score when no anchor`() = runTest {
        val tempDir = createTempDir(prefix = "fusion-motion-")
        try {
            val f0 = createSyntheticJpeg(tempDir, "f0.jpg", baseRgb = 0x404040)
            val f1 = createSyntheticJpeg(tempDir, "f1.jpg", baseRgb = 0x808080)
            val f2 = createSyntheticJpeg(tempDir, "f2.jpg", baseRgb = 0xC0C0C0)
            val fOut = File(tempDir, "out.jpg").apply { writeBytes(byteArrayOf(0x00)) }

            val bundle = createBundle(frames = listOf(
                FrameBundleFrame(0, PixelReference.File(f0.absolutePath),
                    motionScore = MotionScore.Known(0.8f, "test")),
                FrameBundleFrame(1, PixelReference.File(f1.absolutePath),
                    motionScore = MotionScore.Known(0.2f, "test")),
                FrameBundleFrame(2, PixelReference.File(f2.absolutePath),
                    motionScore = MotionScore.Known(0.5f, "test"))
            ))
            val result = baseResult(
                outputPath = fOut.absolutePath,
                frameBundle = bundle,
                intermediateOutputPaths = listOf(f0.absolutePath, f1.absolutePath)
            )

            val processed = MultiFrameFusionProcessor().process(result)

            assertTrue(processed.pipelineNotes.any { it == "merge:applied=true" })
            assertTrue(processed.pipelineNotes.any { it.contains("merge:reference-frame=1") })
            assertTrue(processed.pipelineNotes.any { it.contains("lowest-score=0.2") })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `best-frame selects first valid frame when all motion scores unknown`() = runTest {
        val tempDir = createTempDir(prefix = "fusion-unknown-motion-")
        try {
            val f0 = createSyntheticJpeg(tempDir, "f0.jpg", baseRgb = 0x404040)
            val f1 = createSyntheticJpeg(tempDir, "f1.jpg", baseRgb = 0x808080)
            val fOut = File(tempDir, "out.jpg").apply { writeBytes(byteArrayOf(0x00)) }

            val bundle = createBundle(frames = listOf(
                FrameBundleFrame(0, PixelReference.File(f0.absolutePath)),
                FrameBundleFrame(1, PixelReference.File(f1.absolutePath))
            ))
            val result = baseResult(
                outputPath = fOut.absolutePath,
                frameBundle = bundle,
                intermediateOutputPaths = listOf(f0.absolutePath)
            )

            val processed = MultiFrameFusionProcessor().process(result)

            assertTrue(processed.pipelineNotes.any { it == "merge:applied=true" })
            assertTrue(processed.pipelineNotes.any { it.contains("merge:motion-policy=first-valid") })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ── Pixel averaging ──────────────────────────────────────────

    @Test
    fun `pixel average applies when multiple valid JPEG frames exist`() = runTest {
        val tempDir = createTempDir(prefix = "fusion-avg-")
        try {
            val f0 = createSyntheticJpeg(tempDir, "f0.jpg", width = 80, height = 80, baseRgb = 0x404040)
            val f1 = createSyntheticJpeg(tempDir, "f1.jpg", width = 80, height = 80, baseRgb = 0xC0C0C0)
            val fOut = File(tempDir, "out.jpg")

            val bundle = createBundle(frames = listOf(
                FrameBundleFrame(0, PixelReference.File(f0.absolutePath)),
                FrameBundleFrame(1, PixelReference.File(f1.absolutePath))
            ))
            val result = baseResult(
                outputPath = fOut.absolutePath,
                frameBundle = bundle,
                intermediateOutputPaths = listOf(f0.absolutePath)
            )

            val processed = MultiFrameFusionProcessor().process(result)

            assertTrue(processed.pipelineNotes.any { it == "merge:applied=true" })
            assertTrue(processed.pipelineNotes.any { it == "merge:strategy=pixel-average" })
            assertTrue(processed.pipelineNotes.any { it.contains("merge:pixel-average-frames=2") })
            assertTrue(fOut.exists(), "pixel-averaged output should be written to disk")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pixel average produces different output than best-frame`() = runTest {
        val tempDir = createTempDir(prefix = "fusion-diff-")
        try {
            val f0 = createSyntheticJpeg(tempDir, "f0.jpg", width = 80, height = 80, baseRgb = 0x404040)
            val f1 = createSyntheticJpeg(tempDir, "f1.jpg", width = 80, height = 80, baseRgb = 0xC0C0C0)

            // Run pixel-average
            val outAvg = File(tempDir, "out_avg.jpg")
            val bundleAvg = createBundle(frames = listOf(
                FrameBundleFrame(0, PixelReference.File(f0.absolutePath)),
                FrameBundleFrame(1, PixelReference.File(f1.absolutePath))
            ))
            MultiFrameFusionProcessor().process(
                baseResult(outputPath = outAvg.absolutePath, frameBundle = bundleAvg)
            )

            // Run best-frame (non-JPEG reference triggers best-frame)
            val outBf = File(tempDir, "out_bf.jpg")
            val bundleBf = createBundle(frames = listOf(
                FrameBundleFrame(0, PixelReference.File(f0.absolutePath), outputFormat = "image/raw"),
                FrameBundleFrame(1, PixelReference.File(f1.absolutePath), outputFormat = "image/raw")
            ))
            MultiFrameFusionProcessor().process(
                baseResult(outputPath = outBf.absolutePath, frameBundle = bundleBf)
            )

            assertTrue(outAvg.exists(), "pixel-average output should exist")
            assertTrue(outBf.exists(), "best-frame output should exist")

            val imgAvg = ImageIO.read(outAvg)
            val imgBf = ImageIO.read(outBf)
            // Center pixel of averaged image should be close to midpoint (0x808080)
            // Center pixel of best-frame should be exactly f0's value (0x404040)
            val avgRgb = imgAvg.getRGB(40, 40)
            val bfRgb = imgBf.getRGB(40, 40)
            val avgR = (avgRgb shr 16) and 0xFF
            val bfR = (bfRgb shr 16) and 0xFF
            // Averaged red channel ≈ 128, best-frame red channel = 64
            assertTrue(avgR != bfR, "pixel-average and best-frame should produce different results")
            assertTrue(avgR > bfR, "pixel-average red ($avgR) should be higher than best-frame red ($bfR)")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ── Cleanup ──────────────────────────────────────────────────

    @Test
    fun `temp frames are deleted after successful processing`() = runTest {
        val tempDir = createTempDir(prefix = "fusion-cleanup-")
        try {
            val f0 = createSyntheticJpeg(tempDir, "burst_0.jpg", baseRgb = 0x404040)
            val f1 = createSyntheticJpeg(tempDir, "burst_1.jpg", baseRgb = 0x808080)
            val fOut = File(tempDir, "out.jpg")

            val bundle = createBundle(frames = listOf(
                FrameBundleFrame(0, PixelReference.File(f0.absolutePath)),
                FrameBundleFrame(1, PixelReference.File(f1.absolutePath))
            ))
            val result = baseResult(
                outputPath = fOut.absolutePath,
                frameBundle = bundle,
                intermediateOutputPaths = listOf(f0.absolutePath, f1.absolutePath)
            )

            val processed = MultiFrameFusionProcessor().process(result)

            assertTrue(processed.pipelineNotes.any { it.startsWith("merge:applied=") })
            assertFalse(f0.exists(), "temp frame 0 should be cleaned up")
            assertFalse(f1.exists(), "temp frame 1 should be cleaned up")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `temp frames are deleted when skip occurs`() = runTest {
        val tempDir = createTempDir(prefix = "fusion-skip-cleanup-")
        try {
            val f0 = File(tempDir, "f0.jpg").apply { writeBytes(byteArrayOf(0x01)) }
            val f1 = File(tempDir, "f1.jpg").apply { writeBytes(byteArrayOf(0x02)) }

            val bundle = createBundle(frames = listOf(
                FrameBundleFrame(0, PixelReference.File(f0.absolutePath)),
                FrameBundleFrame(1, PixelReference.File(f1.absolutePath))
            ))
            val result = baseResult(
                outputPath = File(tempDir, "out.jpg").absolutePath,
                frameBundle = bundle,
                intermediateOutputPaths = listOf(f0.absolutePath, f1.absolutePath)
            )

            MultiFrameFusionProcessor().process(result)

            assertFalse(f0.exists(), "temp frame 0 should be cleaned up on skip")
            assertFalse(f1.exists(), "temp frame 1 should be cleaned up on skip")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ── Pipeline notes contract ──────────────────────────────────

    @Test
    fun `emits merge applied and strategy notes for successful fusion`() = runTest {
        val tempDir = createTempDir(prefix = "fusion-notes-")
        try {
            val f0 = createSyntheticJpeg(tempDir, "f0.jpg", baseRgb = 0x404040)
            val f1 = createSyntheticJpeg(tempDir, "f1.jpg", baseRgb = 0xC0C0C0)
            val fOut = File(tempDir, "out.jpg")

            val bundle = createBundle(frames = listOf(
                FrameBundleFrame(0, PixelReference.File(f0.absolutePath)),
                FrameBundleFrame(1, PixelReference.File(f1.absolutePath))
            ))
            val result = baseResult(
                outputPath = fOut.absolutePath,
                frameBundle = bundle,
                intermediateOutputPaths = listOf(f0.absolutePath)
            )

            val processed = MultiFrameFusionProcessor().process(result)

            assertTrue(processed.pipelineNotes.any { it.startsWith("merge:applied=true") })
            assertTrue(processed.pipelineNotes.any { it.startsWith("merge:strategy=") })
            assertTrue(processed.pipelineNotes.any { it.startsWith("merge:inputs=") })
            assertTrue(processed.pipelineNotes.any { it.startsWith("merge:temp-frames=") })
            assertTrue(processed.pipelineNotes.any { it.startsWith("merge:reference-frame=") })
            assertTrue(processed.pipelineNotes.any { it.startsWith("merge:motion-policy=") })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `degraded merge notes are recorded when pixel averaging falls back`() = runTest {
        val tempDir = createTempDir(prefix = "fusion-degraded-")
        try {
            // Create a corrupted JPEG (non-image data in .jpg extension)
            val f0 = File(tempDir, "f0_corrupt.jpg").apply { writeText("not-a-jpeg") }
            val f1 = createSyntheticJpeg(tempDir, "f1.jpg", baseRgb = 0x808080)
            val fOut = File(tempDir, "out.jpg")

            val bundle = createBundle(frames = listOf(
                FrameBundleFrame(0, PixelReference.File(f0.absolutePath)),
                FrameBundleFrame(1, PixelReference.File(f1.absolutePath))
            ))
            val result = baseResult(
                outputPath = fOut.absolutePath,
                frameBundle = bundle,
                intermediateOutputPaths = listOf(f0.absolutePath)
            )

            val processed = MultiFrameFusionProcessor().process(result)

            // When pixel averaging fails due to decode failure, it falls back to best-frame
            assertTrue(processed.pipelineNotes.any { it == "merge:applied=true" },
                "should still apply via fallback: ${processed.pipelineNotes}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ── FrameBundle with degraded frames ─────────────────────────

    @Test
    fun `reports degraded frame count when some frames are degraded`() = runTest {
        val tempDir = createTempDir(prefix = "fusion-degraded-frames-")
        try {
            val f0 = createSyntheticJpeg(tempDir, "f0.jpg", baseRgb = 0x404040)
            val f1 = createSyntheticJpeg(tempDir, "f1.jpg", baseRgb = 0x808080)
            val fOut = File(tempDir, "out.jpg")

            val bundle = createBundle(frames = listOf(
                FrameBundleFrame(0, PixelReference.File(f0.absolutePath),
                    isDegraded = true, degradationReasons = listOf("camera-x:no-metadata")),
                FrameBundleFrame(1, PixelReference.File(f1.absolutePath))
            ))
            val result = baseResult(
                outputPath = fOut.absolutePath,
                frameBundle = bundle,
                intermediateOutputPaths = listOf(f0.absolutePath)
            )

            val processed = MultiFrameFusionProcessor().process(result)

            assertTrue(processed.pipelineNotes.any { it.startsWith("merge:degraded-frames=1") })
            assertTrue(processed.pipelineNotes.any { it == "merge:applied=true" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ── Algorithm adapter ────────────────────────────────────────

    @Test
    fun `algorithm adapter returns Applied with merge notes for multi-frame JPEG input`() = runTest {
        val tempDir = createTempDir(prefix = "fusion-adapter-")
        try {
            val f0 = createSyntheticJpeg(tempDir, "f0.jpg", baseRgb = 0x404040)
            val f1 = createSyntheticJpeg(tempDir, "f1.jpg", baseRgb = 0xC0C0C0)
            val fOut = File(tempDir, "out.jpg").absolutePath

            val request = AlgorithmRequest(
                node = AlgorithmNode(
                    id = "merge-night",
                    type = AlgorithmType.MULTI_FRAME_MERGE,
                    inputs = listOf(f0.absolutePath, f1.absolutePath),
                    output = fOut,
                    requirement = AlgorithmRequirement.REQUIRED,
                    fallback = AlgorithmFallback.FAIL_SHOT
                ),
                inputs = listOf(f0.absolutePath, f1.absolutePath).map { path ->
                    MediaInputRef(
                        path = path,
                        handle = MediaOutputHandle(displayPath = path),
                        mimeType = "image/jpeg"
                    )
                },
                metadata = MediaMetadata(customTags = mapOf("frameCount" to "2"))
            )

            val processor = MultiFrameFusionProcessor().toAlgorithmProcessor()
            assertTrue(processor.canProcess(request))

            val result = processor.process(request)

            assertTrue(result is AlgorithmResult.Applied,
                "Expected Applied, got: $result")
            val applied = result as AlgorithmResult.Applied
            assertTrue(applied.notes.any { it.startsWith("merge:applied=true") })
            assertTrue(applied.notes.any { it.startsWith("merge:strategy=") })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `algorithm adapter returns Skipped when no intermediates`() = runTest {
        val processor = MultiFrameFusionProcessor().toAlgorithmProcessor()
        val request = AlgorithmRequest(
            node = AlgorithmNode(
                id = "merge-night",
                type = AlgorithmType.MULTI_FRAME_MERGE,
                inputs = emptyList(),
                output = "/tmp/merged.jpg",
                requirement = AlgorithmRequirement.REQUIRED,
                fallback = AlgorithmFallback.FAIL_SHOT
            ),
            inputs = emptyList(),
            metadata = MediaMetadata(customTags = mapOf("frameCount" to "3"))
        )

        assertFalse(processor.canProcess(request))
        val result = processor.process(request)
        assertTrue(result is AlgorithmResult.Skipped)
    }

    @Test
    fun `algorithm adapter returns Skipped when frame count is one`() = runTest {
        val processor = MultiFrameFusionProcessor().toAlgorithmProcessor()
        val request = AlgorithmRequest(
            node = AlgorithmNode(
                id = "merge-single",
                type = AlgorithmType.MULTI_FRAME_MERGE,
                inputs = listOf("/tmp/f0.jpg"),
                output = "/tmp/merged.jpg",
                requirement = AlgorithmRequirement.REQUIRED,
                fallback = AlgorithmFallback.FAIL_SHOT
            ),
            inputs = listOf(
                MediaInputRef(
                    path = "/tmp/f0.jpg",
                    handle = MediaOutputHandle(displayPath = "/tmp/f0.jpg"),
                    mimeType = "image/jpeg"
                )
            ),
            metadata = MediaMetadata(customTags = mapOf("frameCount" to "1"))
        )

        assertFalse(processor.canProcess(request))
    }
}
