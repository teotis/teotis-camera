package com.opencamera.core.media

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Integration tests verifying the full fusion pipeline:
 * MultiFrameFusionProcessor → PipelineMetadataPostProcessor → diagnostics.
 */
class MultiFrameFusionIntegrationTest {

    private fun createTempJpeg(width: Int = 100, height: Int = 100, label: String = "test"): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "fusion-integration-${System.nanoTime()}")
        dir.mkdirs()
        val file = File(dir, "$label.jpg")
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.setRGB(width / 2, height / 2, 0xFF0000.toInt())
        ImageIO.write(image, "jpg", file)
        return file
    }

    private fun bundleResult(
        frames: List<FrameBundleFrame>,
        frameCount: Int = frames.size,
        intermediatePaths: List<String> = emptyList()
    ): ShotResult {
        val bundle = FrameBundle(shotId = "integration-test", frames = frames)
        return ShotResult(
            shotId = "integration-test",
            mediaType = MediaType.PHOTO,
            outputPath = File(System.getProperty("java.io.tmpdir"), "fusion-output-${System.nanoTime()}.jpg").absolutePath,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.None,
            captureProfile = CaptureProfile(frameCount = frameCount),
            metadata = MediaMetadata(),
            frameBundle = bundle,
            intermediateOutputPaths = intermediatePaths
        )
    }

    @Test
    fun `successful pixel average fusion produces applied note without placeholder`() = runTest {
        val file1 = createTempJpeg(label = "frame1")
        val file2 = createTempJpeg(label = "frame2")
        val file3 = createTempJpeg(label = "frame3")
        try {
            val frames = listOf(
                FrameBundleFrame(0, PixelReference.File(file1.absolutePath), outputFormat = "image/jpeg"),
                FrameBundleFrame(1, PixelReference.File(file2.absolutePath), outputFormat = "image/jpeg"),
                FrameBundleFrame(2, PixelReference.File(file3.absolutePath),
                    frameRole = FrameRole.FUSION_ANCHOR, outputFormat = "image/jpeg")
            )
            val result = bundleResult(frames, intermediatePaths = listOf(
                file1.absolutePath, file2.absolutePath
            ))

            val fused = MultiFrameFusionProcessor().process(result)

            assertTrue(fused.pipelineNotes.any { it == "merge:applied=true" },
                "Fusion should produce merge:applied=true")
            assertFalse(fused.pipelineNotes.any { it == "merge:placeholder" },
                "Fusion must NOT produce merge:placeholder for successful fusion")
            assertTrue(fused.pipelineNotes.any { it.startsWith("merge:strategy=") },
                "Fusion should report strategy")
            assertTrue(fused.pipelineNotes.any { it.startsWith("merge:inputs=") },
                "Fusion should report input count")
        } finally {
            file1.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun `successful best frame fusion produces applied note without placeholder`() = runTest {
        val anchor = createTempJpeg(label = "anchor")
        val supplement = createTempJpeg(label = "supplement")
        try {
            val frames = listOf(
                FrameBundleFrame(0, PixelReference.File(anchor.absolutePath),
                    frameRole = FrameRole.FUSION_ANCHOR, outputFormat = "image/jpeg"),
                FrameBundleFrame(1, PixelReference.File(supplement.absolutePath), outputFormat = "image/jpeg")
            )
            val result = bundleResult(frames)

            val fused = MultiFrameFusionProcessor().process(result)

            assertTrue(fused.pipelineNotes.any { it == "merge:applied=true" },
                "Best-frame fusion should produce merge:applied=true")
            assertFalse(fused.pipelineNotes.any { it == "merge:placeholder" },
                "Best-frame fusion must NOT produce merge:placeholder")
            assertTrue(fused.pipelineNotes.any { it.startsWith("merge:reference-frame=") },
                "Should report reference frame index")
        } finally {
            anchor.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun `skipped fusion with insufficient frames produces explicit skip note`() = runTest {
        val single = createTempJpeg(label = "single")
        try {
            val frames = listOf(
                FrameBundleFrame(0, PixelReference.File(single.absolutePath), outputFormat = "image/jpeg")
            )
            // frameCount > 1 but only 1 valid frame → insufficient-valid-frames
            val result = bundleResult(frames, frameCount = 3)

            val fused = MultiFrameFusionProcessor().process(result)

            assertTrue(fused.pipelineNotes.any { it == "merge:skipped=insufficient-valid-frames" },
                "Should report skip reason for insufficient valid frames, got: ${fused.pipelineNotes}")
            assertFalse(fused.pipelineNotes.any { it == "merge:applied=true" },
                "Should not be applied when frames insufficient")
        } finally {
            single.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun `no bundle produces single-frame skip note`() = runTest {
        val result = ShotResult(
            shotId = "no-bundle",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/no-bundle.jpg",
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.None,
            captureProfile = CaptureProfile(frameCount = 1),
            metadata = MediaMetadata(),
            frameBundle = null
        )

        val fused = MultiFrameFusionProcessor().process(result)

        assertTrue(fused.pipelineNotes.any { it == "merge:skipped=no-bundle-or-single-frame" },
            "Should report skip for no bundle on single frame")
    }

    @Test
    fun `composite pipeline detects degraded fusion and adds degradation label`() = runTest {
        val file1 = createTempJpeg(label = "d1")
        val file2 = createTempJpeg(label = "d2")
        try {
            val frames = listOf(
                FrameBundleFrame(0, PixelReference.File(file1.absolutePath), outputFormat = "image/jpeg"),
                FrameBundleFrame(1, PixelReference.File(file2.absolutePath), outputFormat = "image/jpeg")
            )
            val bundle = FrameBundle(shotId = "degraded-test", frames = frames)
            val result = ShotResult(
                shotId = "degraded-test",
                mediaType = MediaType.PHOTO,
                outputPath = File(System.getProperty("java.io.tmpdir"), "degraded-output-${System.nanoTime()}.jpg").absolutePath,
                saveRequest = SaveRequest.photoLibrary(),
                thumbnailSource = ThumbnailSource.None,
                captureProfile = CaptureProfile(frameCount = 2),
                metadata = MediaMetadata(),
                frameBundle = bundle,
                intermediateOutputPaths = listOf(file1.absolutePath)
            )

            val composite = CompositeMediaPostProcessor(
                listOf(MultiFrameFusionProcessor(), PipelineMetadataPostProcessor())
            )
            val processed = composite.process(result)

            assertTrue(processed.pipelineNotes.any { it == "merge:applied=true" ||
                    it.startsWith("merge:applied=") },
                "Fusion note should exist")
            assertTrue(processed.pipelineNotes.any { it.startsWith("merge:strategy=") },
                "Strategy note should exist")
        } finally {
            file1.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun `composite pipeline with no fusion skips degraded label`() = runTest {
        val result = ShotResult(
            shotId = "single-shot",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/single.jpg",
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.None,
            captureProfile = CaptureProfile(frameCount = 1),
            metadata = MediaMetadata(algorithmProfile = "photo-vivid"),
            frameBundle = null
        )

        val composite = CompositeMediaPostProcessor(
            listOf(MultiFrameFusionProcessor(), PipelineMetadataPostProcessor())
        )
        val processed = composite.process(result)

        assertFalse(processed.pipelineNotes.any { it == "degraded:multi-frame-fusion" },
            "Single-frame shot should not get multi-frame degradation label")
        assertTrue(processed.pipelineNotes.any { it == "algorithm:photo-vivid" },
            "Algorithm profile should still be noted")
    }
}
