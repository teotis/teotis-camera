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
import kotlin.math.max
import kotlin.math.roundToInt

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
        AlgorithmJobClass.CAPTURE_CRITICAL

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
            val fusion = fuseLocalContrast(nearScaled, farScaled, width, height)
            val fused = fusion.bitmap
            if (fused == null) {
                val skipReason = fusion.skipReason ?: "fusion-rejected"
                val fallbackWritten = if (skipReason == "foreground-motion") {
                    writeOutput(farScaled, outputTarget)
                } else {
                    false
                }
                val outcome = if (fallbackWritten) {
                    cleanupIntermediateFiles(result.intermediateOutputPaths)
                    result.addPipelineNotes(
                        "focus-stack:skipped=$skipReason",
                        "focus-stack:fallback=far-anchor",
                        *sourceNotes.toTypedArray(),
                        *fusion.diagnostics.toTypedArray()
                    )
                } else {
                    result.addPipelineNotes(
                        "focus-stack:skipped=${if (skipReason == "foreground-motion") "output-write-failed" else skipReason}",
                        *sourceNotes.toTypedArray(),
                        *fusion.diagnostics.toTypedArray()
                    )
                }
                recycleScaled(nearBitmap, nearScaled)
                recycleScaled(farBitmap, farScaled)
                return@withContext outcome
            }
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
                    "focus-stack:memory=striped-rgb565",
                    *sourceNotes.toTypedArray(),
                    *fusion.diagnostics.toTypedArray()
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
                    DecodedFrame(
                        BitmapFactory.decodeFile(file.absolutePath, focusStackDecodeOptions())
                            ?.ensureRgb565(),
                        "file"
                    )
                } else if (fallbackContentUri != null && frame.frameRole == FrameRole.FUSION_ANCHOR) {
                    DecodedFrame(decodeContentUri(fallbackContentUri), "content-uri-fallback")
                } else {
                    DecodedFrame(null, "missing")
                }
            }
            is PixelReference.ContentUri -> DecodedFrame(decodeContentUri(ref.uri), "content-uri")
            is PixelReference.InMemory -> DecodedFrame(
                BitmapFactory.decodeByteArray(
                    ref.bytes,
                    0,
                    ref.bytes.size,
                    focusStackDecodeOptions()
                )?.ensureRgb565(),
                "in-memory"
            )
        }
    }

    private fun decodeContentUri(uri: String): Bitmap? {
        return try {
            val parsed = Uri.parse(uri)
            context.contentResolver.openInputStream(parsed)?.use { input ->
                BitmapFactory.decodeStream(input, null, focusStackDecodeOptions())
                    ?.ensureRgb565()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun focusStackDecodeOptions(): BitmapFactory.Options = BitmapFactory.Options().apply {
        // Camera JPEGs do not need alpha. RGB_565 halves the two source-frame allocation,
        // leaving enough heap for the full-resolution fused output on ordinary Android heaps.
        inPreferredConfig = Bitmap.Config.RGB_565
    }

    private fun scaleTo(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, width, height, true).ensureRgb565()
    }

    private fun Bitmap.ensureRgb565(): Bitmap {
        if (config == Bitmap.Config.RGB_565) return this
        val converted = copy(Bitmap.Config.RGB_565, false)
        if (converted !== this && !isRecycled) recycle()
        return converted
    }

    private data class FocusStackFusion(
        val bitmap: Bitmap?,
        val skipReason: String? = null,
        val diagnostics: List<String> = emptyList()
    )

    private fun fuseLocalContrast(
        near: Bitmap,
        far: Bitmap,
        width: Int,
        height: Int
    ): FocusStackFusion {
        val alignment = estimateTranslation(source = near, anchor = far, width = width, height = height)
        val exposureGain = estimateExposureGain(near, far, alignment, width, height)
        val hasMotionRisk = hasForegroundMotionRisk(
            near = near,
            far = far,
            alignment = alignment,
            exposureGain = exposureGain,
            width = width,
            height = height
        )
        val diagnostics = buildList {
            if (alignment.verified) {
                add("focus-stack:alignment=translation:${alignment.dx},${alignment.dy}")
            } else {
                add("focus-stack:alignment=unverified:${alignment.reason ?: "unknown"}")
                add("focus-stack:alignment-fallback=translation:0,0")
            }
            add("focus-stack:exposure-gain=${"%.3f".format(java.util.Locale.US, exposureGain)}")
            if (hasMotionRisk) {
                add("focus-stack:motion-risk=localized")
            }
        }
        if (hasMotionRisk) {
            return FocusStackFusion(
                bitmap = null,
                skipReason = "foreground-motion",
                diagnostics = diagnostics
            )
        }
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        var stripStart = 0
        while (stripStart < height) {
            val stripHeight = minOf(FUSION_STRIP_HEIGHT, height - stripStart)
            fuseStrip(
                near = near,
                far = far,
                output = output,
                width = width,
                height = height,
                stripStart = stripStart,
                stripHeight = stripHeight,
                alignment = alignment,
                exposureGain = exposureGain
            )
            stripStart += stripHeight
        }
        return FocusStackFusion(
            bitmap = output,
            diagnostics = diagnostics
        )
    }

    private data class TranslationAlignment(
        val dx: Int,
        val dy: Int,
        val verified: Boolean = true,
        val reason: String? = null
    )

    private data class AlignmentCandidate(val dx: Int, val dy: Int, val error: Long)

    private fun estimateTranslation(
        source: Bitmap,
        anchor: Bitmap,
        width: Int,
        height: Int
    ): TranslationAlignment {
        val sampleStride = max(4, minOf(width, height) / ALIGNMENT_SAMPLE_DIVISOR)
        val searchRadius = minOf(ALIGNMENT_SEARCH_RADIUS, max(2, minOf(width, height) / 12))
        val textureScore = alignmentTextureScore(anchor, width, height, sampleStride)
        if (textureScore < ALIGNMENT_MIN_TEXTURE_SCORE) {
            return TranslationAlignment(0, 0, verified = false, reason = "low-texture")
        }
        var best = TranslationAlignment(0, 0)
        val baselineError = translationError(
            source = source,
            anchor = anchor,
            width = width,
            height = height,
            dx = 0,
            dy = 0,
            sampleStride = sampleStride
        )
        var bestError = baselineError
        val candidates = mutableListOf(AlignmentCandidate(0, 0, baselineError))
        for (dy in -searchRadius..searchRadius step ALIGNMENT_COARSE_STEP) {
            for (dx in -searchRadius..searchRadius step ALIGNMENT_COARSE_STEP) {
                val error = translationError(
                    source = source,
                    anchor = anchor,
                    width = width,
                    height = height,
                    dx = dx,
                    dy = dy,
                    sampleStride = sampleStride
                )
                candidates += AlignmentCandidate(dx, dy, error)
                if (isBetterAlignment(error, dx, dy, bestError, best)) {
                    bestError = error
                    best = TranslationAlignment(dx, dy)
                }
            }
        }
        var refined = best
        val refineMinDy = maxOf(-searchRadius, best.dy - ALIGNMENT_REFINEMENT_RADIUS)
        val refineMaxDy = minOf(searchRadius, best.dy + ALIGNMENT_REFINEMENT_RADIUS)
        val refineMinDx = maxOf(-searchRadius, best.dx - ALIGNMENT_REFINEMENT_RADIUS)
        val refineMaxDx = minOf(searchRadius, best.dx + ALIGNMENT_REFINEMENT_RADIUS)
        for (dy in refineMinDy..refineMaxDy) {
            for (dx in refineMinDx..refineMaxDx) {
                val error = translationError(
                    source = source,
                    anchor = anchor,
                    width = width,
                    height = height,
                    dx = dx,
                    dy = dy,
                    sampleStride = sampleStride
                )
                candidates += AlignmentCandidate(dx, dy, error)
                if (isBetterAlignment(error, dx, dy, bestError, refined)) {
                    bestError = error
                    refined = TranslationAlignment(dx, dy)
                }
            }
        }
        if (abs(refined.dx) >= searchRadius || abs(refined.dy) >= searchRadius) {
            return TranslationAlignment(0, 0, verified = false, reason = "search-boundary")
        }
        val runnerUpError = candidates.asSequence()
            .filterNot { it.dx == refined.dx && it.dy == refined.dy }
            .minOfOrNull(AlignmentCandidate::error)
        val ambiguityMargin = max(
            ALIGNMENT_ABSOLUTE_AMBIGUITY_MARGIN,
            (bestError * ALIGNMENT_AMBIGUITY_RELATIVE_MARGIN).roundToInt().toLong()
        )
        if (runnerUpError != null && runnerUpError - bestError <= ambiguityMargin) {
            return TranslationAlignment(0, 0, verified = false, reason = "ambiguous")
        }
        return refined
    }

    private fun isBetterAlignment(
        candidateError: Long,
        candidateDx: Int,
        candidateDy: Int,
        bestError: Long,
        best: TranslationAlignment
    ): Boolean {
        if (candidateError < bestError) return true
        if (candidateError > bestError) return false
        return abs(candidateDx) + abs(candidateDy) < abs(best.dx) + abs(best.dy)
    }

    private fun translationError(
        source: Bitmap,
        anchor: Bitmap,
        width: Int,
        height: Int,
        dx: Int,
        dy: Int,
        sampleStride: Int
    ): Long {
        var error = 0L
        var samples = 0
        val border = ALIGNMENT_SEARCH_RADIUS + 1
        var y = border
        while (y < height - border) {
            var x = border
            while (x < width - border) {
                val sourceX = x + dx
                val sourceY = y + dy
                if (sourceX in 1 until width - 1 && sourceY in 1 until height - 1) {
                    error += abs(
                        lowFrequencyLuma(source, sourceX, sourceY) -
                            lowFrequencyLuma(anchor, x, y)
                    ).toLong()
                    samples += 1
                }
                x += sampleStride
            }
            y += sampleStride
        }
        return if (samples == 0) {
            Long.MAX_VALUE
        } else {
            error * ALIGNMENT_ERROR_SCALE / samples
        }
    }

    private fun alignmentTextureScore(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        sampleStride: Int
    ): Int {
        var score = 0L
        var samples = 0
        var y = ALIGNMENT_LOW_FREQUENCY_RADIUS
        while (y < height - ALIGNMENT_LOW_FREQUENCY_RADIUS) {
            var x = ALIGNMENT_LOW_FREQUENCY_RADIUS
            while (x < width - ALIGNMENT_LOW_FREQUENCY_RADIUS) {
                score += abs(
                    lowFrequencyLuma(bitmap, x - ALIGNMENT_LOW_FREQUENCY_RADIUS, y) -
                        lowFrequencyLuma(bitmap, x + ALIGNMENT_LOW_FREQUENCY_RADIUS, y)
                )
                score += abs(
                    lowFrequencyLuma(bitmap, x, y - ALIGNMENT_LOW_FREQUENCY_RADIUS) -
                        lowFrequencyLuma(bitmap, x, y + ALIGNMENT_LOW_FREQUENCY_RADIUS)
                )
                samples += 2
                x += sampleStride
            }
            y += sampleStride
        }
        return if (samples == 0) 0 else (score / samples).toInt()
    }

    private fun lowFrequencyLuma(bitmap: Bitmap, x: Int, y: Int): Int {
        val radius = ALIGNMENT_LOW_FREQUENCY_RADIUS
        return (
            luma(bitmap.getPixel(x, y)) +
                luma(bitmap.getPixel((x - radius).coerceAtLeast(0), y)) +
                luma(bitmap.getPixel((x + radius).coerceAtMost(bitmap.width - 1), y)) +
                luma(bitmap.getPixel(x, (y - radius).coerceAtLeast(0))) +
                luma(bitmap.getPixel(x, (y + radius).coerceAtMost(bitmap.height - 1)))
            ) / 5
    }

    private fun estimateExposureGain(
        near: Bitmap,
        far: Bitmap,
        alignment: TranslationAlignment,
        width: Int,
        height: Int
    ): Float {
        val sampleStride = max(4, minOf(width, height) / EXPOSURE_SAMPLE_DIVISOR)
        var nearLuma = 0L
        var farLuma = 0L
        var samples = 0
        var y = sampleStride
        while (y < height - sampleStride) {
            var x = sampleStride
            while (x < width - sampleStride) {
                val nearX = x + alignment.dx
                val nearY = y + alignment.dy
                if (nearX in 0 until width && nearY in 0 until height) {
                    val nearValue = luma(near.getPixel(nearX, nearY))
                    val farValue = luma(far.getPixel(x, y))
                    if (nearValue in 12..243 && farValue in 12..243) {
                        nearLuma += nearValue
                        farLuma += farValue
                        samples += 1
                    }
                }
                x += sampleStride
            }
            y += sampleStride
        }
        if (samples == 0 || nearLuma == 0L) return 1f
        return (farLuma.toFloat() / nearLuma.toFloat()).coerceIn(0.80f, 1.25f)
    }

    private fun fuseStrip(
        near: Bitmap,
        far: Bitmap,
        output: Bitmap,
        width: Int,
        height: Int,
        stripStart: Int,
        stripHeight: Int,
        alignment: TranslationAlignment,
        exposureGain: Float
    ) {
        val scoreRows = stripHeight + FOCUS_SCORE_SMOOTH_RADIUS * 2
        val sourceRows = scoreRows + LOCAL_CONTRAST_RADIUS * 2
        val scoreStartY = stripStart - FOCUS_SCORE_SMOOTH_RADIUS
        val nearRows = readRows(
            bitmap = near,
            startY = scoreStartY - LOCAL_CONTRAST_RADIUS + alignment.dy,
            rowCount = sourceRows
        )
        val farRows = readRows(
            bitmap = far,
            startY = scoreStartY - LOCAL_CONTRAST_RADIUS,
            rowCount = sourceRows
        )
        val scoreDiffs = IntArray(width * scoreRows)
        for (row in 0 until scoreRows) {
            val sourceRow = row + LOCAL_CONTRAST_RADIUS
            for (x in 0 until width) {
                val nearX = (x + alignment.dx).coerceIn(0, width - 1)
                val nearScore = localContrastScore(nearRows, width, sourceRows, nearX, sourceRow)
                val farScore = localContrastScore(farRows, width, sourceRows, x, sourceRow)
                scoreDiffs[row * width + x] = (nearScore * exposureGain).roundToInt() - farScore
            }
        }
        val scoreIntegral = scoreIntegralImage(scoreDiffs, width, scoreRows)
        val integralStride = width + 1

        val outPixels = IntArray(width * stripHeight)
        for (localY in 0 until stripHeight) {
            val outputY = stripStart + localY
            val scoreY = localY + FOCUS_SCORE_SMOOTH_RADIUS
            val sourceY = scoreY + LOCAL_CONTRAST_RADIUS
            for (x in 0 until width) {
                val nearSourceX = x + alignment.dx
                val nearSourceY = outputY + alignment.dy
                val nearValid = nearSourceX in 0 until width && nearSourceY in 0 until height
                val smoothedDiff = smoothedScoreDiff(
                    scoreIntegral,
                    integralStride,
                    width,
                    scoreRows,
                    x,
                    scoreY
                )
                val nearWeight = when {
                    !nearValid -> 0f
                    smoothedDiff >= FOCUS_SCORE_SELECT_MARGIN -> 1f
                    smoothedDiff <= -FOCUS_SCORE_SELECT_MARGIN -> 0f
                    else -> (smoothedDiff + FOCUS_SCORE_SELECT_MARGIN) /
                        (FOCUS_SCORE_SELECT_MARGIN * 2f)
                }
                val nearPixel = nearRows[sourceY * width + nearSourceX.coerceIn(0, width - 1)]
                val farPixel = farRows[sourceY * width + x]
                outPixels[localY * width + x] = blend(
                    adjustExposure(nearPixel, exposureGain),
                    farPixel,
                    nearWeight
                )
            }
        }
        output.setPixels(outPixels, 0, width, 0, stripStart, width, stripHeight)
    }

    private fun readRows(bitmap: Bitmap, startY: Int, rowCount: Int): IntArray {
        val width = bitmap.width
        val rows = IntArray(width * rowCount)
        for (row in 0 until rowCount) {
            val sourceY = (startY + row).coerceIn(0, bitmap.height - 1)
            bitmap.getPixels(rows, row * width, width, 0, sourceY, width, 1)
        }
        return rows
    }

    private fun hasForegroundMotionRisk(
        near: Bitmap,
        far: Bitmap,
        alignment: TranslationAlignment,
        exposureGain: Float,
        width: Int,
        height: Int
    ): Boolean {
        var changed = 0
        var total = 0
        val tileChanged = IntArray(MOTION_LOCAL_GRID_SIZE * MOTION_LOCAL_GRID_SIZE)
        val tileTotal = IntArray(tileChanged.size)
        val sampleStride = max(2, minOf(width, height) / MOTION_SAMPLE_DIVISOR)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val nearX = x + alignment.dx
                val nearY = y + alignment.dy
                if (nearX in 0 until width && nearY in 0 until height) {
                    val nearPixel = near.getPixel(nearX, nearY)
                    val farPixel = far.getPixel(x, y)
                    val chromaChanged = chromaDistance(nearPixel, farPixel) >=
                        MOTION_CHROMA_DELTA_THRESHOLD
                    val normalizedNearLuma = (
                        motionAverageLuma(near, nearX, nearY) * exposureGain
                        ).roundToInt().coerceIn(0, 255)
                    val lumaChanged = abs(
                        normalizedNearLuma - motionAverageLuma(far, x, y)
                    ) >= MOTION_LOW_FREQUENCY_LUMA_DELTA_THRESHOLD
                    val tileX = (x * MOTION_LOCAL_GRID_SIZE / width)
                        .coerceIn(0, MOTION_LOCAL_GRID_SIZE - 1)
                    val tileY = (y * MOTION_LOCAL_GRID_SIZE / height)
                        .coerceIn(0, MOTION_LOCAL_GRID_SIZE - 1)
                    val tileIndex = tileY * MOTION_LOCAL_GRID_SIZE + tileX
                    if (chromaChanged || lumaChanged) {
                        changed += 1
                        tileChanged[tileIndex] += 1
                    }
                    total += 1
                    tileTotal[tileIndex] += 1
                }
                x += sampleStride
            }
            y += sampleStride
        }
        if (total == 0) return false
        if (changed.toFloat() / total.toFloat() >= MOTION_PIXEL_RATIO_THRESHOLD) return true
        return tileChanged.indices.any { index ->
            val localTotal = tileTotal[index]
            val localChanged = tileChanged[index]
            localTotal > 0 &&
                localChanged >= MOTION_LOCAL_MIN_CHANGED_SAMPLES &&
                localChanged.toFloat() / localTotal.toFloat() >= MOTION_LOCAL_PIXEL_RATIO_THRESHOLD
        }
    }

    private fun chromaDistance(a: Int, b: Int): Int {
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        return abs((ar - ag) - (br - bg)) + abs((ab - ag) - (bb - bg))
    }

    private fun motionAverageLuma(bitmap: Bitmap, x: Int, y: Int): Int {
        var sum = 0
        var samples = 0
        var sampleY = y - MOTION_LUMA_AVERAGE_RADIUS
        while (sampleY <= y + MOTION_LUMA_AVERAGE_RADIUS) {
            val clampedY = sampleY.coerceIn(0, bitmap.height - 1)
            var sampleX = x - MOTION_LUMA_AVERAGE_RADIUS
            while (sampleX <= x + MOTION_LUMA_AVERAGE_RADIUS) {
                sum += luma(bitmap.getPixel(sampleX.coerceIn(0, bitmap.width - 1), clampedY))
                samples += 1
                sampleX += 1
            }
            sampleY += MOTION_LUMA_VERTICAL_SAMPLE_STEP
        }
        return if (samples == 0) 0 else sum / samples
    }

    private fun smoothedScoreDiff(
        scoreIntegral: LongArray,
        integralStride: Int,
        width: Int,
        height: Int,
        x: Int,
        y: Int
    ): Float {
        val x0 = (x - FOCUS_SCORE_SMOOTH_RADIUS).coerceAtLeast(0)
        val x1 = (x + FOCUS_SCORE_SMOOTH_RADIUS + 1).coerceAtMost(width)
        val y0 = (y - FOCUS_SCORE_SMOOTH_RADIUS).coerceAtLeast(0)
        val y1 = (y + FOCUS_SCORE_SMOOTH_RADIUS + 1).coerceAtMost(height)
        val sum = scoreIntegral[y1 * integralStride + x1] -
            scoreIntegral[y0 * integralStride + x1] -
            scoreIntegral[y1 * integralStride + x0] +
            scoreIntegral[y0 * integralStride + x0]
        val count = (x1 - x0) * (y1 - y0)
        return sum.toFloat() / count.toFloat()
    }

    private fun scoreIntegralImage(scores: IntArray, width: Int, height: Int): LongArray {
        val stride = width + 1
        val integral = LongArray(stride * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0L
            for (x in 0 until width) {
                rowSum += scores[y * width + x]
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + rowSum
            }
        }
        return integral
    }

    private fun adjustExposure(pixel: Int, gain: Float): Int {
        if (gain == 1f) return pixel
        val alpha = pixel ushr 24 and 0xFF
        val red = (((pixel shr 16) and 0xFF) * gain).roundToInt().coerceIn(0, 255)
        val green = (((pixel shr 8) and 0xFF) * gain).roundToInt().coerceIn(0, 255)
        val blue = ((pixel and 0xFF) * gain).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    private fun blend(near: Int, far: Int, nearWeight: Float): Int {
        val farWeight = 1f - nearWeight
        val alpha = (((near ushr 24 and 0xFF) * nearWeight) + ((far ushr 24 and 0xFF) * farWeight))
            .toInt()
            .coerceIn(0, 255)
        val red = ((((near shr 16) and 0xFF) * nearWeight) + (((far shr 16) and 0xFF) * farWeight))
            .toInt()
            .coerceIn(0, 255)
        val green = ((((near shr 8) and 0xFF) * nearWeight) + (((far shr 8) and 0xFF) * farWeight))
            .toInt()
            .coerceIn(0, 255)
        val blue = (((near and 0xFF) * nearWeight) + ((far and 0xFF) * farWeight))
            .toInt()
            .coerceIn(0, 255)
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
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

    private companion object {
        private const val MOTION_CHROMA_DELTA_THRESHOLD = 42
        private const val MOTION_LOW_FREQUENCY_LUMA_DELTA_THRESHOLD = 40
        private const val MOTION_LUMA_AVERAGE_RADIUS = 4
        private const val MOTION_LUMA_VERTICAL_SAMPLE_STEP = 4
        private const val MOTION_PIXEL_RATIO_THRESHOLD = 0.035f
        private const val MOTION_LOCAL_PIXEL_RATIO_THRESHOLD = 0.24f
        private const val MOTION_LOCAL_MIN_CHANGED_SAMPLES = 6
        private const val MOTION_LOCAL_GRID_SIZE = 8
        private const val MOTION_SAMPLE_DIVISOR = 160
        private const val FOCUS_SCORE_SELECT_MARGIN = 36f
        private const val FOCUS_SCORE_SMOOTH_RADIUS = 2
        private const val LOCAL_CONTRAST_RADIUS = 1
        private const val FUSION_STRIP_HEIGHT = 128
        private const val ALIGNMENT_SEARCH_RADIUS = 12
        private const val ALIGNMENT_COARSE_STEP = 2
        private const val ALIGNMENT_REFINEMENT_RADIUS = 1
        private const val ALIGNMENT_SAMPLE_DIVISOR = 112
        private const val ALIGNMENT_LOW_FREQUENCY_RADIUS = 3
        private const val ALIGNMENT_MIN_TEXTURE_SCORE = 3
        private const val ALIGNMENT_ERROR_SCALE = 1_000L
        private const val ALIGNMENT_ABSOLUTE_AMBIGUITY_MARGIN = 50L
        private const val ALIGNMENT_AMBIGUITY_RELATIVE_MARGIN = 0.03f
        private const val EXPOSURE_SAMPLE_DIVISOR = 128
    }
}
