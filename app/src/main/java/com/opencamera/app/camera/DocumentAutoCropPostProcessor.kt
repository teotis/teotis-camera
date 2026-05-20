package com.opencamera.app.camera

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

internal sealed interface DocumentAutoCropWork {
    data object None : DocumentAutoCropWork
    data class Crop(val target: DocumentAutoCropTarget) : DocumentAutoCropWork
    data class DiagnosticSkip(val reason: String) : DocumentAutoCropWork
}

internal sealed interface DocumentAutoCropTarget {
    data class FilePath(val path: String) : DocumentAutoCropTarget
    data class ContentUri(val value: String) : DocumentAutoCropTarget
}

internal sealed interface DocumentAutoCropEditorResult {
    data class Applied(
        val cropBounds: Rect,
        val warning: String? = null
    ) : DocumentAutoCropEditorResult

    data class Skipped(val reason: String) : DocumentAutoCropEditorResult
    data class Failed(val reason: String) : DocumentAutoCropEditorResult
}

internal interface DocumentAutoCropEditor {
    suspend fun apply(target: DocumentAutoCropTarget): DocumentAutoCropEditorResult
}

internal fun decideDocumentAutoCropWork(result: ShotResult): DocumentAutoCropWork {
    if (result.mediaType != MediaType.PHOTO) {
        return DocumentAutoCropWork.None
    }
    if (!result.saveRequest.mimeType.equals("image/jpeg", ignoreCase = true)) {
        return DocumentAutoCropWork.DiagnosticSkip("unsupported-mime")
    }
    val tags = result.metadata.customTags
    if (tags["mode"] != "document") {
        return DocumentAutoCropWork.None
    }
    if (tags["autoCrop"] != "true") {
        return DocumentAutoCropWork.None
    }

    val target = result.outputHandle.toDocumentAutoCropTargetOrNull()
        ?: return DocumentAutoCropWork.DiagnosticSkip("missing-output-handle")
    return DocumentAutoCropWork.Crop(target)
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
            DocumentAutoCropWork.None -> result
            is DocumentAutoCropWork.DiagnosticSkip -> result.withPipelineNotes(
                "document:auto-crop:skipped:${work.reason}"
            )

            is DocumentAutoCropWork.Crop -> {
                when (val cropResult = editor.apply(work.target)) {
                    is DocumentAutoCropEditorResult.Applied -> {
                        val bounds = cropResult.cropBounds
                        val notes = buildList {
                            add("document:auto-crop:applied")
                            add("document:auto-crop:bounds=${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}")
                            cropResult.warning?.let { add("document:auto-crop:warning:$it") }
                        }
                        result.withPipelineNotes(*notes.toTypedArray())
                    }

                    is DocumentAutoCropEditorResult.Skipped -> result.withPipelineNotes(
                        "document:auto-crop:skipped:${cropResult.reason}"
                    )

                    is DocumentAutoCropEditorResult.Failed -> result.withPipelineNotes(
                        "document:auto-crop:failed:${cropResult.reason}"
                    )
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

    override suspend fun apply(target: DocumentAutoCropTarget): DocumentAutoCropEditorResult =
        withContext(Dispatchers.IO) {
            val sourceBytes = readSourceBytes(target)
                ?: return@withContext DocumentAutoCropEditorResult.Skipped("input-unavailable")
            if (sourceBytes.isEmpty()) {
                return@withContext DocumentAutoCropEditorResult.Skipped("empty-source")
            }

            val preservedExif = readPreservedExif(sourceBytes)
            val decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
                ?: return@withContext DocumentAutoCropEditorResult.Failed("decode-failed")

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
                } ?: return@withContext DocumentAutoCropEditorResult.Skipped("bounds-not-found")

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
                if (!writeEncodedBytes(target, encodedBytes)) {
                    return@withContext DocumentAutoCropEditorResult.Failed("output-unavailable")
                }

                val exifWarning = restorePreservedExif(target, preservedExif)
                DocumentAutoCropEditorResult.Applied(
                    cropBounds = cropBounds,
                    warning = exifWarning
                )
            } catch (_: Throwable) {
                DocumentAutoCropEditorResult.Failed("crop-exception")
            } finally {
                decoded.recycle()
            }
        }

    private fun readSourceBytes(target: DocumentAutoCropTarget): ByteArray? {
        return when (target) {
            is DocumentAutoCropTarget.FilePath -> {
                val file = File(target.path)
                if (!file.exists()) null else file.readBytes()
            }

            is DocumentAutoCropTarget.ContentUri -> {
                contentResolver.openInputStream(Uri.parse(target.value))?.use { it.readBytes() }
            }
        }
    }

    private fun writeEncodedBytes(target: DocumentAutoCropTarget, bytes: ByteArray): Boolean {
        return when (target) {
            is DocumentAutoCropTarget.FilePath -> runCatching {
                File(target.path).outputStream().use { it.write(bytes) }
            }.isSuccess

            is DocumentAutoCropTarget.ContentUri -> {
                contentResolver.openOutputStream(Uri.parse(target.value), "rwt")?.use { it.write(bytes) } != null
            }
        }
    }

    private fun readPreservedExif(sourceBytes: ByteArray): Map<String, String> {
        return runCatching {
            ByteArrayInputStream(sourceBytes).use { input ->
                val exif = ExifInterface(input)
                EXIF_TAGS_TO_PRESERVE.mapNotNull { tag ->
                    exif.getAttribute(tag)?.let { value -> tag to value }
                }.toMap()
            }
        }.getOrDefault(emptyMap())
    }

    private fun restorePreservedExif(
        target: DocumentAutoCropTarget,
        preservedExif: Map<String, String>
    ): String? {
        if (preservedExif.isEmpty()) {
            return null
        }
        val restored = runCatching {
            when (target) {
                is DocumentAutoCropTarget.FilePath -> {
                    ExifInterface(target.path).apply {
                        preservedExif.forEach { (tag, value) -> setAttribute(tag, value) }
                        saveAttributes()
                    }
                }

                is DocumentAutoCropTarget.ContentUri -> {
                    contentResolver.openFileDescriptor(Uri.parse(target.value), "rw")?.use { descriptor ->
                        ExifInterface(descriptor.fileDescriptor).apply {
                            preservedExif.forEach { (tag, value) -> setAttribute(tag, value) }
                            saveAttributes()
                        }
                    } ?: error("file-descriptor-unavailable")
                }
            }
        }
        return if (restored.isSuccess) null else "exif-restore-failed"
    }

    companion object {
        private const val JPEG_QUALITY = 92

        private val EXIF_TAGS_TO_PRESERVE = listOf(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP
        )
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

private fun MediaOutputHandle.toDocumentAutoCropTargetOrNull(): DocumentAutoCropTarget? {
    contentUri?.let { return DocumentAutoCropTarget.ContentUri(it) }
    filePath?.let { return DocumentAutoCropTarget.FilePath(it) }
    return displayPath.takeIf { File(it).isAbsolute }?.let(DocumentAutoCropTarget::FilePath)
}

private fun ShotResult.withPipelineNotes(vararg notes: String): ShotResult {
    return copy(pipelineNotes = pipelineNotes + notes)
}
