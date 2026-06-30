package com.opencamera.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.opencamera.core.media.AlgorithmJobClass
import com.opencamera.core.media.FocusStackFrameRole
import com.opencamera.core.media.FrameBundleFrame
import com.opencamera.core.media.FrameRole
import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.PixelReference
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.addPipelineNotes
import com.opencamera.core.media.toProcessorTargetOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * Android production focus-stack fusion.
 *
 * The core JVM processor uses java.awt/ImageIO for host-side tests. This implementation keeps the
 * real-device path on android.graphics.Bitmap and writes either MediaStore content URIs or files.
 */
internal class AndroidFocusStackFusionProcessor(
    private val context: Context
) : MediaPostProcessor {
    override fun isApplicable(result: ShotResult): Boolean {
        return result.captureProfile.focusStackSpec != null ||
            result.frameBundle?.frames?.any { it.focusStackRole != FocusStackFrameRole.NONE } == true
    }

    override fun jobClass(result: ShotResult): AlgorithmJobClass =
        AlgorithmJobClass.CAPTURE_OPTIONAL

    override suspend fun process(result: ShotResult): ShotResult = withContext(Dispatchers.Default) {
        if (!isApplicable(result)) {
            return@withContext result.addPipelineNotes("focus-stack:skipped=not-focus-stack")
        }

        val bundle = result.frameBundle
            ?: return@withContext result.addPipelineNotes("focus-stack:skipped=no-frame-bundle")
        val near = bundle.frames.firstOrNull { it.focusStackRole == FocusStackFrameRole.NEAR }
        val far = bundle.frames.firstOrNull { it.focusStackRole == FocusStackFrameRole.FAR }
        if (near == null || far == null) {
            return@withContext result.addPipelineNotes("focus-stack:skipped=missing-near-far")
        }

        val farFallbackUri = result.outputHandle.contentUri
        val nearSource = decodeFrame(near, fallbackContentUri = null)
        val farSource = decodeFrame(far, fallbackContentUri = farFallbackUri)
        val nearBitmap = nearSource.bitmap
        val farBitmap = farSource.bitmap
        val sourceNotes = listOfNotNull(
            "focus-stack:near-source=${nearSource.label}",
            "focus-stack:far-source=${farSource.label}".takeIf { farBitmap != null || farSource.label != "missing" }
        )
        if (nearBitmap == null || farBitmap == null) {
            nearBitmap?.recycle()
            farBitmap?.recycle()
            return@withContext result.addPipelineNotes(
                "focus-stack:skipped=decode-failed",
                *sourceNotes.toTypedArray()
            )
        }

        val outputTarget = result.outputHandle.toProcessorTargetOrNull()
            ?: result.outputPath.takeIf { File(it).isAbsolute }?.let(ProcessorTarget::FilePath)
            ?: run {
                nearBitmap.recycle()
                farBitmap.recycle()
                return@withContext result.addPipelineNotes("focus-stack:skipped=output-unavailable")
            }

        try {
            val width = minOf(nearBitmap.width, farBitmap.width)
            val height = minOf(nearBitmap.height, farBitmap.height)
            if (width <= 1 || height <= 1) {
                return@withContext result.addPipelineNotes("focus-stack:skipped=invalid-dimensions")
            }

            val nearScaled = scaleTo(nearBitmap, width, height)
            val farScaled = scaleTo(farBitmap, width, height)
            val fused = fuseLocalContrast(nearScaled, farScaled, width, height)
            val written = writeOutput(fused, outputTarget)
            if (!written) {
                result.addPipelineNotes("focus-stack:skipped=output-write-failed")
            } else {
                cleanupIntermediateFiles(result.intermediateOutputPaths)
                result.addPipelineNotes(
                    "focus-stack:applied=true",
                    "focus-stack:strategy=android-local-contrast",
                    "focus-stack:inputs=2",
                    "focus-stack:roles=near,far",
                    "focus-stack:output=${width}x$height",
                    *sourceNotes.toTypedArray()
                )
            }.also {
                recycleScaled(nearBitmap, nearScaled)
                recycleScaled(farBitmap, farScaled)
                fused.recycle()
            }
        } catch (_: Throwable) {
            result.addPipelineNotes("focus-stack:skipped=output-write-failed")
        } finally {
            if (!nearBitmap.isRecycled) nearBitmap.recycle()
            if (!farBitmap.isRecycled) farBitmap.recycle()
        }
    }

    private data class DecodedFrame(val bitmap: Bitmap?, val label: String)

    private fun decodeFrame(
        frame: FrameBundleFrame,
        fallbackContentUri: String?
    ): DecodedFrame {
        return when (val ref = frame.pixelReference) {
            is PixelReference.File -> {
                val file = ref.toFile()
                if (file.exists() && file.length() > 0L) {
                    DecodedFrame(BitmapFactory.decodeFile(file.absolutePath), "file")
                } else if (fallbackContentUri != null && frame.frameRole == FrameRole.FUSION_ANCHOR) {
                    DecodedFrame(decodeContentUri(fallbackContentUri), "content-uri-fallback")
                } else {
                    DecodedFrame(null, "missing")
                }
            }
            is PixelReference.ContentUri -> DecodedFrame(decodeContentUri(ref.uri), "content-uri")
            is PixelReference.InMemory -> DecodedFrame(
                BitmapFactory.decodeByteArray(ref.bytes, 0, ref.bytes.size),
                "in-memory"
            )
        }
    }

    private fun decodeContentUri(uri: String): Bitmap? {
        return try {
            val parsed = Uri.parse(uri)
            context.contentResolver.openInputStream(parsed)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleTo(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun fuseLocalContrast(
        near: Bitmap,
        far: Bitmap,
        width: Int,
        height: Int
    ): Bitmap {
        val nearPixels = IntArray(width * height)
        val farPixels = IntArray(width * height)
        near.getPixels(nearPixels, 0, width, 0, 0, width, height)
        far.getPixels(farPixels, 0, width, 0, 0, width, height)

        val outPixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val nearScore = localContrastScore(nearPixels, width, height, x, y)
                val farScore = localContrastScore(farPixels, width, height, x, y)
                outPixels[index] = if (nearScore >= farScore) nearPixels[index] else farPixels[index]
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(outPixels, 0, width, 0, 0, width, height)
        }
    }

    private fun localContrastScore(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int
    ): Int {
        val center = luma(pixels[y * width + x])
        val left = luma(pixels[y * width + (x - 1).coerceAtLeast(0)])
        val right = luma(pixels[y * width + (x + 1).coerceAtMost(width - 1)])
        val up = luma(pixels[(y - 1).coerceAtLeast(0) * width + x])
        val down = luma(pixels[(y + 1).coerceAtMost(height - 1) * width + x])
        return abs(center - left) + abs(center - right) + abs(center - up) + abs(center - down)
    }

    private fun luma(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return ((r * 299) + (g * 587) + (b * 114)) / 1000
    }

    private fun writeOutput(bitmap: Bitmap, target: ProcessorTarget): Boolean {
        return when (target) {
            is ProcessorTarget.FilePath -> {
                val file = File(target.path)
                file.parentFile?.mkdirs()
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                } && file.exists() && file.length() > 0L
            }
            is ProcessorTarget.ContentUri -> {
                val uri = Uri.parse(target.value)
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                } == true
            }
        }
    }

    private fun recycleScaled(original: Bitmap, scaled: Bitmap) {
        if (scaled !== original && !scaled.isRecycled) scaled.recycle()
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
