package com.opencamera.core.media

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Focus-stack fusion for guided near/far all-clear captures.
 *
 * This processor intentionally does not reuse [MultiFrameFusionProcessor]: ordinary multi-frame
 * merge can choose a best frame or reduce noise, while focus stacking must preserve locally sharp
 * regions from different focus planes and emit distinct diagnostics.
 */
class FocusStackFusionProcessor : MediaPostProcessor {

    override fun isApplicable(result: ShotResult): Boolean {
        return result.captureProfile.focusStackSpec != null ||
            result.frameBundle?.frames?.any { it.focusStackRole != FocusStackFrameRole.NONE } == true
    }

    override suspend fun process(result: ShotResult): ShotResult {
        if (!isApplicable(result)) {
            return result.addPipelineNotes("focus-stack:skipped=not-focus-stack")
        }

        val bundle = result.frameBundle
            ?: return result.addPipelineNotes("focus-stack:skipped=no-frame-bundle")

        val near = bundle.frames.firstOrNull { it.focusStackRole == FocusStackFrameRole.NEAR }
        val far = bundle.frames.firstOrNull { it.focusStackRole == FocusStackFrameRole.FAR }
        if (near == null || far == null) {
            return result.addPipelineNotes("focus-stack:skipped=missing-near-far")
        }

        val nearFile = (near.pixelReference as? PixelReference.File)?.toFile()
        val farFile = (far.pixelReference as? PixelReference.File)?.toFile()
        if (nearFile == null || farFile == null ||
            !nearFile.exists() || nearFile.length() <= 0L ||
            !farFile.exists() || farFile.length() <= 0L) {
            return result.addPipelineNotes("focus-stack:skipped=missing-pixels")
        }

        val nearImage = decodeImage(nearFile)
        val farImage = decodeImage(farFile)
        if (nearImage == null || farImage == null) {
            return result.addPipelineNotes("focus-stack:skipped=decode-failed")
        }

        val width = minOf(nearImage.width, farImage.width)
        val height = minOf(nearImage.height, farImage.height)
        if (width <= 1 || height <= 1) {
            return result.addPipelineNotes("focus-stack:skipped=invalid-dimensions")
        }

        val nearScaled = convertToCommonResolution(nearImage, width, height)
        val farScaled = convertToCommonResolution(farImage, width, height)
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val nearScore = localContrastScore(nearScaled, x, y)
                val farScore = localContrastScore(farScaled, x, y)
                val rgb = if (nearScore >= farScore) {
                    nearScaled.getRGB(x, y)
                } else {
                    farScaled.getRGB(x, y)
                }
                output.setRGB(x, y, rgb)
            }
        }

        return try {
            val outputFile = File(result.outputPath)
            outputFile.parentFile?.mkdirs()
            val written = ImageIO.write(output, "jpg", outputFile)
            if (!written || !outputFile.exists() || outputFile.length() <= 0L) {
                result.addPipelineNotes("focus-stack:skipped=output-write-failed")
            } else {
                cleanupIntermediateFiles(result.intermediateOutputPaths)
                result.addPipelineNotes(
                    "focus-stack:applied=true",
                    "focus-stack:strategy=local-contrast",
                    "focus-stack:inputs=2",
                    "focus-stack:roles=near,far",
                    "focus-stack:output=${width}x$height"
                )
            }
        } catch (_: Exception) {
            result.addPipelineNotes("focus-stack:skipped=output-write-failed")
        }
    }

    private fun decodeImage(file: File): BufferedImage? {
        return try {
            ImageIO.read(file)
        } catch (_: Exception) {
            null
        }
    }

    private fun convertToCommonResolution(
        image: BufferedImage,
        targetWidth: Int,
        targetHeight: Int
    ): BufferedImage {
        if (image.width == targetWidth && image.height == targetHeight) return image
        val scaled = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = scaled.createGraphics()
        graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null)
        graphics.dispose()
        return scaled
    }

    private fun localContrastScore(image: BufferedImage, x: Int, y: Int): Int {
        val center = luma(image.getRGB(x, y))
        val left = luma(image.getRGB((x - 1).coerceAtLeast(0), y))
        val right = luma(image.getRGB((x + 1).coerceAtMost(image.width - 1), y))
        val up = luma(image.getRGB(x, (y - 1).coerceAtLeast(0)))
        val down = luma(image.getRGB(x, (y + 1).coerceAtMost(image.height - 1)))
        return abs(center - left) + abs(center - right) + abs(center - up) + abs(center - down)
    }

    private fun luma(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return ((r * 299) + (g * 587) + (b * 114)) / 1000
    }

    private fun cleanupIntermediateFiles(intermediatePaths: List<String>) {
        for (path in intermediatePaths) {
            try {
                val file = File(path)
                if (file.exists()) file.delete()
            } catch (_: Exception) {
                // best-effort cleanup
            }
        }
    }
}
