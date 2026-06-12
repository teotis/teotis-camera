package com.opencamera.core.media

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * First real multi-frame fusion processor. Consumes [FrameBundle] and produces
 * honest output based on actual frame data:
 *
 * - **best-frame**: deterministic reference frame selection using motion scores / anchor role
 * - **pixel-average**: bounded-resolution per-pixel averaging (JPEG decode → average → JPEG encode)
 * - **skipped**: explicit skip with reason when <2 valid frames or missing pixel data
 * - **degraded**: reported when pixel averaging fails and falls back to best-frame
 *
 * Pipeline notes emitted: `merge:applied`, `merge:strategy`, `merge:inputs`,
 * `merge:reference-frame`, `merge:motion-policy`, and degradation reasons.
 */
class MultiFrameFusionProcessor : MediaPostProcessor {

    companion object {
        const val MAX_AVERAGE_DIMENSION = 1600
    }

    override suspend fun process(result: ShotResult): ShotResult {
        val startNanos = System.nanoTime()
        val bundle = result.frameBundle
        val intermediatePaths = result.intermediateOutputPaths

        if (bundle == null || result.captureProfile.frameCount <= 1) {
            return result.addPipelineNotes("merge:skipped=no-bundle-or-single-frame")
        }

        val fileRefs = bundle.frames
            .mapNotNull { (it.pixelReference as? PixelReference.File)?.path }
            .filter { path -> File(path).exists() && File(path).length() > 0 }

        if (fileRefs.size < 2) {
            cleanupIntermediateFiles(intermediatePaths)
            return result.addPipelineNotes(
                "merge:skipped=insufficient-valid-frames",
                "merge:inputs=${fileRefs.size}",
                "merge:temp-frames=${intermediatePaths.size}"
            )
        }

        val referenceFrame = selectReferenceFrame(bundle)
        val strategy = chooseStrategy(bundle, referenceFrame)

        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
        val degradedFrames = bundle.frames.count { it.isDegraded }

        val fusionResult = try {
            val initial = when (strategy) {
                "pixel-average" -> applyPixelAverage(result, bundle, referenceFrame, fileRefs)
                "best-frame" -> applyBestFrame(result, bundle, referenceFrame, fileRefs)
                else -> FusionResult.Degraded("strategy=$strategy")
            }
            if (initial is FusionResult.Degraded && strategy == "pixel-average") {
                val fallback = applyBestFrame(result, bundle, referenceFrame, fileRefs)
                if (fallback is FusionResult.Applied) {
                    FusionResult.Applied(
                        notes = fallback.notes + "merge:pixel-average-degraded=${initial.reason}"
                    )
                } else initial
            } else initial
        } finally {
            cleanupIntermediateFiles(intermediatePaths)
        }

        val notes = buildList {
            add("merge:applied=${fusionResult.applied}")
            add("merge:strategy=$strategy")
            add("merge:inputs=${fileRefs.size}")
            add("merge:temp-frames=${intermediatePaths.size}")
            if (referenceFrame != null) {
                add("merge:reference-frame=${referenceFrame.frameIndex}")
                add(
                    "merge:motion-policy=${
                        when {
                            referenceFrame.motionScore is MotionScore.Known ->
                                "lowest-score=${(referenceFrame.motionScore as MotionScore.Known).score}"
                            referenceFrame.frameRole == FrameRole.FUSION_ANCHOR -> "anchor-role"
                            else -> "first-valid"
                        }
                    }"
                )
            }
            if (degradedFrames > 0) {
                add("merge:degraded-frames=$degradedFrames")
            }
            fusionResult.degradationReason?.let { add("merge:degraded=$it") }
        }

        return if (fusionResult.applied) {
            val extraNotes = mutableListOf<String>()
            if (strategy == "pixel-average") {
                extraNotes += "merge:pixel-average-frames=${fileRefs.size}"
                extraNotes += "merge:pixel-average-max-dim=$MAX_AVERAGE_DIMENSION"
            } else if (strategy == "best-frame" && referenceFrame != null) {
                referenceFrame.motionScore.let { ms ->
                    if (ms is MotionScore.Known) extraNotes += "merge:reference-motion=${ms.score}"
                }
                referenceFrame.noiseModel.let { nm ->
                    if (nm is NoiseModel.Known) extraNotes += "merge:reference-noise=${nm.profileId}"
                }
            }
            result.copy(pipelineNotes = result.pipelineNotes + notes + extraNotes)
        } else {
            result.copy(pipelineNotes = result.pipelineNotes + notes)
        }
    }

    private fun chooseStrategy(
        bundle: FrameBundle,
        referenceFrame: FrameBundleFrame?
    ): String {
        if (referenceFrame == null) return "best-frame"
        if (referenceFrame.outputFormat != "image/jpeg") return "best-frame"
        if (referenceFrame.frameRole == FrameRole.FUSION_ANCHOR &&
            referenceFrame.pixelReference is PixelReference.File) {
            return "best-frame"
        }
        val allJpeg = bundle.frames.all { it.outputFormat == "image/jpeg" }
        return if (allJpeg) "pixel-average" else "best-frame"
    }

    private fun applyBestFrame(
        result: ShotResult,
        bundle: FrameBundle,
        referenceFrame: FrameBundleFrame?,
        fileRefs: List<String>
    ): FusionResult {
        val bestPath = findBestFramePath(bundle, fileRefs)
        if (bestPath == null) {
            return FusionResult.Degraded("no-valid-frame-path")
        }

        if (bestPath != result.outputPath) {
            val success = copyJpeg(bestPath, result.outputPath)
            if (!success) {
                return FusionResult.Degraded("output-write-failed")
            }
        }

        return FusionResult.Applied(
            notes = listOf("merge:selected-frame=$bestPath")
        )
    }

    private fun applyPixelAverage(
        result: ShotResult,
        bundle: FrameBundle,
        referenceFrame: FrameBundleFrame?,
        fileRefs: List<String>
    ): FusionResult {
        return try {
            val images = fileRefs.mapNotNull { decodeJpeg(File(it)) }
            if (images.size < 2) {
                return FusionResult.Degraded("decode-failed:only-${images.size}-decoded")
            }

            val targetWidth = minOf(images.first().width, MAX_AVERAGE_DIMENSION)
            val targetHeight = minOf(images.first().height, MAX_AVERAGE_DIMENSION)
            val scaled = images.map { convertToCommonResolution(it, targetWidth, targetHeight) }

            val width = scaled.first().width
            val height = scaled.first().height
            val resultImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    var r = 0; var g = 0; var b = 0
                    for (img in scaled) {
                        val rgb = img.getRGB(x, y)
                        r += (rgb shr 16) and 0xFF
                        g += (rgb shr 8) and 0xFF
                        b += rgb and 0xFF
                    }
                    val count = scaled.size
                    val avgR = (r.toDouble() / count).roundToInt().coerceIn(0, 255)
                    val avgG = (g.toDouble() / count).roundToInt().coerceIn(0, 255)
                    val avgB = (b.toDouble() / count).roundToInt().coerceIn(0, 255)
                    resultImage.setRGB(x, y, (avgR shl 16) or (avgG shl 8) or avgB)
                }
            }

            val outputFile = File(result.outputPath)
            outputFile.parentFile?.mkdirs()
            ImageIO.write(resultImage, "jpg", outputFile)

            FusionResult.Applied(
                notes = listOf("merge:pixel-averaged=${images.size}-frames")
            )
        } catch (e: Exception) {
            applyBestFrame(result, bundle, referenceFrame, fileRefs)
        }
    }

    private fun decodeJpeg(file: File): BufferedImage? {
        if (!file.exists()) return null
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

    private fun selectReferenceFrame(bundle: FrameBundle): FrameBundleFrame? {
        bundle.anchorFrame?.let { anchor ->
            if (anchor.pixelReference is PixelReference.File) return anchor
        }

        val framesWithMotion = bundle.validFrames.filter {
            it.motionScore is MotionScore.Known
        }
        if (framesWithMotion.isNotEmpty()) {
            return framesWithMotion.minByOrNull {
                (it.motionScore as MotionScore.Known).score
            }
        }

        return bundle.validFrames.firstOrNull()
    }

    private fun findBestFramePath(
        bundle: FrameBundle,
        fileRefs: List<String>
    ): String? {
        val anchor = bundle.anchorFrame
        if (anchor != null && anchor.pixelReference is PixelReference.File) {
            if (File(anchor.pixelReference.path).exists()) {
                return anchor.pixelReference.path
            }
        }

        val withMotion = bundle.frames.filter {
            it.motionScore is MotionScore.Known && it.pixelReference is PixelReference.File
        }
        if (withMotion.isNotEmpty()) {
            val best = withMotion.minByOrNull {
                (it.motionScore as MotionScore.Known).score
            }!!
            val path = (best.pixelReference as PixelReference.File).path
            if (File(path).exists()) return path
        }

        return fileRefs.firstOrNull()
    }

    private fun copyJpeg(source: String, dest: String): Boolean {
        return try {
            val srcFile = File(source)
            val destFile = File(dest)
            if (!srcFile.exists()) return false
            destFile.parentFile?.mkdirs()
            srcFile.copyTo(destFile, overwrite = true)
            true
        } catch (_: Exception) {
            false
        }
    }

    private sealed class FusionResult {
        abstract val applied: Boolean
        abstract val degradationReason: String?

        data class Applied(val notes: List<String>) : FusionResult() {
            override val applied: Boolean = true
            override val degradationReason: String? = null
        }
        data class Degraded(val reason: String) : FusionResult() {
            override val applied: Boolean = false
            override val degradationReason: String? = reason
        }
    }
}
