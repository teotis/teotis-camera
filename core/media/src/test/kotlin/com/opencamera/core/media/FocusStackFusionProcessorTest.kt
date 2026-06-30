package com.opencamera.core.media

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FocusStackFusionProcessorTest {

    @Test
    fun `applies local contrast fusion from near and far focus frames`() = runTest {
        val tempDir = createTempDir(prefix = "focus-stack-fusion-")
        try {
            val near = File(tempDir, "near.jpg")
            val far = File(tempDir, "far.jpg")
            writeSplitFocusImage(
                near,
                sharpLeft = true,
                sharpRight = false
            )
            writeSplitFocusImage(
                far,
                sharpLeft = false,
                sharpRight = true
            )
            val output = File(tempDir, "out.jpg")
            val result = baseResult(
                outputPath = output.absolutePath,
                frameBundle = FrameBundle(
                    shotId = "focus-shot",
                    frames = listOf(
                        FrameBundleFrame(
                            frameIndex = 0,
                            pixelReference = PixelReference.File(near.absolutePath),
                            focusStackRole = FocusStackFrameRole.NEAR
                        ),
                        FrameBundleFrame(
                            frameIndex = 1,
                            pixelReference = PixelReference.File(far.absolutePath),
                            focusStackRole = FocusStackFrameRole.FAR
                        )
                    )
                )
            )

            val processed = FocusStackFusionProcessor().process(result)
            val fused = ImageIO.read(output)

            assertNotNull(fused)
            assertTrue(processed.pipelineNotes.any { it == "focus-stack:applied=true" })
            assertTrue(processed.pipelineNotes.any { it == "focus-stack:strategy=local-contrast" })
            assertTrue(processed.pipelineNotes.any { it == "focus-stack:roles=near,far" })
            assertTrue(horizontalContrast(fused, x = 10, y = 20) > 80)
            assertTrue(horizontalContrast(fused, x = 70, y = 20) > 80)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `skips when near or far focus role is missing`() = runTest {
        val tempDir = createTempDir(prefix = "focus-stack-skip-")
        try {
            val near = File(tempDir, "near.jpg")
            writeSplitFocusImage(
                near,
                sharpLeft = true,
                sharpRight = false
            )
            val result = baseResult(
                outputPath = File(tempDir, "out.jpg").absolutePath,
                frameBundle = FrameBundle(
                    shotId = "focus-shot",
                    frames = listOf(
                        FrameBundleFrame(
                            frameIndex = 0,
                            pixelReference = PixelReference.File(near.absolutePath),
                            focusStackRole = FocusStackFrameRole.NEAR
                        )
                    )
                )
            )

            val processed = FocusStackFusionProcessor().process(result)

            assertTrue(processed.pipelineNotes.any { it == "focus-stack:skipped=missing-near-far" })
            assertFalse(processed.pipelineNotes.any { it == "focus-stack:applied=true" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `algorithm processor exposes focus stack fusion type`() = runTest {
        val processor = FocusStackFusionProcessor().toAlgorithmProcessor()
        val request = AlgorithmRequest(
            node = AlgorithmNode(
                id = "focus-alg",
                type = AlgorithmType.FOCUS_STACK_FUSION,
                inputs = listOf("near", "far"),
                output = "out",
                requirement = AlgorithmRequirement.REQUIRED,
                fallback = AlgorithmFallback.FAIL_SHOT
            ),
            inputs = listOf(
                MediaInputRef("near", MediaOutputHandle(displayPath = "/tmp/near.jpg"), "image/jpeg"),
                MediaInputRef("far", MediaOutputHandle(displayPath = "/tmp/far.jpg"), "image/jpeg")
            ),
            metadata = MediaMetadata(
                customTags = mapOf("focusStackRoles" to "near,far")
            )
        )

        assertTrue(processor.canProcess(request))
        assertTrue(processor.type == AlgorithmType.FOCUS_STACK_FUSION)
    }

    private fun baseResult(
        outputPath: String,
        frameBundle: FrameBundle
    ): ShotResult {
        return ShotResult(
            shotId = "focus-shot",
            mediaType = MediaType.PHOTO,
            outputPath = outputPath,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.None,
            captureProfile = CaptureProfile(
                frameCount = frameBundle.frameCount,
                focusStackSpec = FocusStackCaptureSpec.guidedNearFar()
            ),
            metadata = MediaMetadata(),
            frameBundle = frameBundle
        )
    }

    private fun writeSplitFocusImage(
        file: File,
        sharpLeft: Boolean,
        sharpRight: Boolean,
        width: Int = 96,
        height: Int = 64
    ) {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val leftHalf = x < width / 2
                val sharp = if (leftHalf) sharpLeft else sharpRight
                val value = if (sharp) {
                    if (x % 2 == 0) 24 else 232
                } else {
                    128
                }
                val rgb = (value shl 16) or (value shl 8) or value
                image.setRGB(x, y, rgb)
            }
        }
        file.parentFile?.mkdirs()
        val written = ImageIO.write(image, "jpg", file)
        require(written) { "ImageIO.write failed for ${file.absolutePath}" }
    }

    private fun horizontalContrast(image: BufferedImage, x: Int, y: Int): Int {
        return abs(luma(image.getRGB(x, y)) - luma(image.getRGB(x + 1, y)))
    }

    private fun luma(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return ((r * 299) + (g * 587) + (b * 114)) / 1000
    }
}
