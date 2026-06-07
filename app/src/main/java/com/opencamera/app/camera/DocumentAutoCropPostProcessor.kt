package com.opencamera.app.camera

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.ProcessorWork
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.addPipelineNotes
import com.opencamera.core.media.toProcessorTargetOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

internal data class DocumentAutoCropApplied(
    val cropBounds: Rect,
    val warning: String? = null
) : ProcessorEditorResult

internal interface DocumentAutoCropEditor {
    suspend fun apply(target: ProcessorTarget): ProcessorEditorResult
}

internal fun decideDocumentAutoCropWork(result: ShotResult): ProcessorWork<ProcessorTarget> {
    if (result.mediaType != MediaType.PHOTO) {
        return ProcessorWork.None
    }
    if (!result.saveRequest.mimeType.equals("image/jpeg", ignoreCase = true)) {
        return ProcessorWork.DiagnosticSkip("unsupported-mime")
    }
    val tags = result.metadata.customTags
    if (tags["mode"] != "document") {
        return ProcessorWork.None
    }
    if (tags["autoCrop"] != "true") {
        return ProcessorWork.None
    }

    val target = result.outputHandle.toProcessorTargetOrNull()
        ?: return ProcessorWork.DiagnosticSkip("missing-output-handle")
    return ProcessorWork.Execute(target)
}

internal fun detectDocumentCropBounds(
    width: Int,
    height: Int,
    luminance: (x: Int, y: Int) -> Int
): Rect? {
    if (width <= 1 || height <= 1) {
        return null
    }
    val rowThreshold = 235
    val columnThreshold = 235
    val varianceThreshold = 18
    val minMargin = 12

    var top = 0
    while (top < height / 3) {
        val stats = rowStats(width, top, luminance)
        if (stats.average < rowThreshold || stats.range > varianceThreshold) {
            break
        }
        top++
    }

    var bottom = height - 1
    while (bottom > height * 2 / 3) {
        val stats = rowStats(width, bottom, luminance)
        if (stats.average < rowThreshold || stats.range > varianceThreshold) {
            break
        }
        bottom--
    }

    var left = 0
    while (left < width / 3) {
        val stats = columnStats(height, left, luminance)
        if (stats.average < columnThreshold || stats.range > varianceThreshold) {
            break
        }
        left++
    }

    var right = width - 1
    while (right > width * 2 / 3) {
        val stats = columnStats(height, right, luminance)
        if (stats.average < columnThreshold || stats.range > varianceThreshold) {
            break
        }
        right--
    }

    if (top < minMargin && left < minMargin && (height - 1 - bottom) < minMargin && (width - 1 - right) < minMargin) {
        return null
    }

    val clippedLeft = left.coerceIn(0, width - 2)
    val clippedTop = top.coerceIn(0, height - 2)
    val clippedRight = (right + 1).coerceIn(clippedLeft + 1, width)
    val clippedBottom = (bottom + 1).coerceIn(clippedTop + 1, height)

    val croppedWidth = clippedRight - clippedLeft
    val croppedHeight = clippedBottom - clippedTop
    if (croppedWidth >= width - 8 || croppedHeight >= height - 8) {
        return null
    }
    if (croppedWidth < width / 2 || croppedHeight < height / 2) {
        return null
    }

    return Rect(clippedLeft, clippedTop, clippedRight, clippedBottom)
}

internal class DocumentAutoCropPostProcessor(
    private val editor: DocumentAutoCropEditor
) : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        return when (val work = decideDocumentAutoCropWork(result)) {
            ProcessorWork.None -> result
            is ProcessorWork.DiagnosticSkip -> result.addPipelineNotes(
                "document:auto-crop:skipped:${work.reason}"
            )

            is ProcessorWork.Execute -> {
                when (val cropResult = editor.apply(work.payload)) {
                    is DocumentAutoCropApplied -> {
                        val bounds = cropResult.cropBounds
                        val notes = buildList {
                            add("document:auto-crop:applied")
                            add("document:auto-crop:bounds=${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}")
                            cropResult.warning?.let { add("document:auto-crop:warning:$it") }
                        }
                        result.addPipelineNotes(*notes.toTypedArray())
                    }

                    is ProcessorEditorResult.Skipped -> result.addPipelineNotes(
                        "document:auto-crop:skipped:${cropResult.reason}"
                    )

                    is ProcessorEditorResult.Failed -> result.addPipelineNotes(
                        "document:auto-crop:failed:${cropResult.reason}"
                    )

                    else -> result
                }
            }
        }
    }
}

internal class AndroidDocumentAutoCropEditor(
    context: Context
) : DocumentAutoCropEditor {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun apply(target: ProcessorTarget): ProcessorEditorResult =
        withContext(Dispatchers.IO) {
            val sourceBytes = readSourceBytes(target)
                ?: return@withContext ProcessorEditorResult.Skipped("input-unavailable")
            if (sourceBytes.isEmpty()) {
                return@withContext ProcessorEditorResult.Skipped("empty-source")
            }

            val preservedExif = readPreservedExif(sourceBytes)
            val decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
                ?: return@withContext ProcessorEditorResult.Failed("decode-failed")

            try {
                val cropBounds = detectDocumentCropBounds(
                    width = decoded.width,
                    height = decoded.height
                ) { x, y ->
                    val pixel = decoded.getPixel(x, y)
                    val red = pixel ushr 16 and 0xFF
                    val green = pixel ushr 8 and 0xFF
                    val blue = pixel and 0xFF
                    ((red * 299) + (green * 587) + (blue * 114)) / 1000
                } ?: return@withContext ProcessorEditorResult.Skipped("bounds-not-found")

                val cropped = Bitmap.createBitmap(
                    decoded,
                    cropBounds.left,
                    cropBounds.top,
                    cropBounds.width(),
                    cropBounds.height()
                )
                val encodedBytes = ByteArrayOutputStream().use { output ->
                    check(cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                        "Document auto-crop JPEG compression failed"
                    }
                    output.toByteArray()
                }
                cropped.recycle()
                if (!contentResolver.writeEncodedBytes(target, encodedBytes)) {
                    return@withContext ProcessorEditorResult.Failed("output-unavailable")
                }

                val exifWarning = contentResolver.restorePreservedExif(target, preservedExif)
                DocumentAutoCropApplied(
                    cropBounds = cropBounds,
                    warning = exifWarning
                )
            } catch (_: Throwable) {
                ProcessorEditorResult.Failed("crop-exception")
            } finally {
                decoded.recycle()
            }
        }

    private fun readSourceBytes(target: ProcessorTarget): ByteArray? {
        return when (target) {
            is ProcessorTarget.FilePath -> {
                val file = File(target.path)
                if (!file.exists()) null else file.readBytes()
            }

            is ProcessorTarget.ContentUri -> {
                contentResolver.openInputStream(Uri.parse(target.value))?.use { it.readBytes() }
            }
        }
    }

    companion object {
        private const val JPEG_QUALITY = 92
    }
}

private data class EdgeStats(
    val average: Int,
    val range: Int
)

private fun rowStats(
    width: Int,
    y: Int,
    luminance: (x: Int, y: Int) -> Int
): EdgeStats {
    var sum = 0
    var minValue = 255
    var maxValue = 0
    for (x in 0 until width step max(1, width / 48)) {
        val value = luminance(x, y)
        sum += value
        minValue = min(minValue, value)
        maxValue = max(maxValue, value)
    }
    val sampleCount = max(1, (width + max(1, width / 48) - 1) / max(1, width / 48))
    return EdgeStats(
        average = sum / sampleCount,
        range = maxValue - minValue
    )
}

private fun columnStats(
    height: Int,
    x: Int,
    luminance: (x: Int, y: Int) -> Int
): EdgeStats {
    var sum = 0
    var minValue = 255
    var maxValue = 0
    for (y in 0 until height step max(1, height / 48)) {
        val value = luminance(x, y)
        sum += value
        minValue = min(minValue, value)
        maxValue = max(maxValue, value)
    }
    val sampleCount = max(1, (height + max(1, height / 48) - 1) / max(1, height / 48))
    return EdgeStats(
        average = sum / sampleCount,
        range = maxValue - minValue
    )
}

