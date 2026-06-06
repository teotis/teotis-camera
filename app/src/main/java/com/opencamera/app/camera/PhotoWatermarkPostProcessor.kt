package com.opencamera.app.camera

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.OcwmJpegContainer
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.ReversibleWatermarkArchiveManifest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.addPipelineNotes
import com.opencamera.core.media.sha256Hex
import com.opencamera.core.media.toProcessorTargetOrNull
import com.opencamera.core.settings.WatermarkFrameBackground
import com.opencamera.core.settings.WatermarkTextPlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

internal sealed interface PhotoWatermarkWork {
    data object None : PhotoWatermarkWork
    data class Render(
        val target: ProcessorTarget,
        val metadata: MediaMetadata,
        val watermarkText: String,
        val templateId: String
    ) : PhotoWatermarkWork

    data class DiagnosticSkip(
        val reason: String,
        val templateId: String? = null
    ) : PhotoWatermarkWork
}

internal data class ResolvedPhotoWatermarkTemplate(
    val templateId: String,
    val title: String,
    val supportingLines: List<String>,
    val frameBackground: WatermarkFrameBackground,
    val usesExpandedFrame: Boolean,
    val placement: WatermarkTextPlacement,
    val textScale: Float,
    val textOpacity: Float,
    val captureCropZoom: Float = 1f,
    val warning: String? = null
)

internal data class PhotoWatermarkBitmapRenderResult(
    val bitmap: Bitmap,
    val warning: String? = null
)

internal data class PhotoWatermarkApplied(
    val warning: String? = null,
    val timingNote: String? = null
) : ProcessorEditorResult

internal interface PhotoWatermarkEditor {
    suspend fun apply(
        target: ProcessorTarget,
        metadata: MediaMetadata,
        watermarkText: String,
        templateId: String
    ): ProcessorEditorResult
}

internal fun decidePhotoWatermarkWork(result: ShotResult): PhotoWatermarkWork {
    if (result.mediaType != MediaType.PHOTO) {
        return PhotoWatermarkWork.None
    }
    if (!result.saveRequest.mimeType.equals("image/jpeg", ignoreCase = true)) {
        return PhotoWatermarkWork.DiagnosticSkip("unsupported-mime")
    }
    val watermarkText = result.metadata.watermarkText
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return PhotoWatermarkWork.None
    val templateId = resolveWatermarkTemplateId(
        result.metadata.customTags[PHOTO_WATERMARK_TEMPLATE_KEY]
    )

    val target = result.outputHandle.toProcessorTargetOrNull()
        ?: return PhotoWatermarkWork.DiagnosticSkip(
            reason = "missing-output-handle",
            templateId = templateId
        )

    return PhotoWatermarkWork.Render(
        target = target,
        metadata = result.metadata,
        watermarkText = watermarkText,
        templateId = templateId
    )
}

internal class PhotoWatermarkPostProcessor(
    private val editor: PhotoWatermarkEditor
) : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        return when (val work = decidePhotoWatermarkWork(result)) {
            PhotoWatermarkWork.None -> result
            is PhotoWatermarkWork.DiagnosticSkip -> result.addPipelineNotes(
                *buildList {
                    work.templateId?.let { add("watermark:template:$it") }
                    add("watermark:skipped:${work.reason}")
                }.toTypedArray()
            )

            is PhotoWatermarkWork.Render -> {
                when (val renderResult = editor.apply(
                    target = work.target,
                    metadata = work.metadata,
                    watermarkText = work.watermarkText,
                    templateId = work.templateId
                )) {
                    is PhotoWatermarkApplied -> {
                        result.addPipelineNotes(
                            *buildList {
                                add("watermark:rendered:${work.templateId}")
                                renderResult.warning?.let { add("watermark:warning:$it") }
                                renderResult.timingNote?.let { add(it) }
                            }.toTypedArray()
                        )
                    }

                    is ProcessorEditorResult.Skipped -> result.addPipelineNotes(
                        "watermark:template:${work.templateId}",
                        "watermark:skipped:${renderResult.reason}"
                    )

                    is ProcessorEditorResult.Failed -> result.addPipelineNotes(
                        "watermark:template:${work.templateId}",
                        "watermark:failed:${renderResult.reason}"
                    )

                    else -> result
                }
            }
        }
    }
}

internal class AndroidPhotoWatermarkEditor(
    context: Context
) : PhotoWatermarkEditor {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun apply(
        target: ProcessorTarget,
        metadata: MediaMetadata,
        watermarkText: String,
        templateId: String
    ): ProcessorEditorResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val sourceBytes = readSourceBytes(target)
            ?: return@withContext ProcessorEditorResult.Skipped("input-unavailable")
        if (sourceBytes.isEmpty()) {
            return@withContext ProcessorEditorResult.Skipped("empty-source")
        }

        val preservedExif = readPreservedExif(sourceBytes)
        val decoded = try {
            BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
                ?: return@withContext ProcessorEditorResult.Failed("decode-failed")
        } catch (e: OutOfMemoryError) {
            return@withContext ProcessorEditorResult.Failed("decode-oom")
        } catch (_: Throwable) {
            return@withContext ProcessorEditorResult.Failed("decode-exception")
        }
        val t1 = System.currentTimeMillis()

        val originalWidth = decoded.width
        val originalHeight = decoded.height
        val mutableBitmap = decoded.copy(Bitmap.Config.ARGB_8888, true)
        if (mutableBitmap !== decoded) {
            decoded.recycle()
        }

        var renderedBitmap: Bitmap? = null
        try {
            val resolvedTemplate = resolvePhotoWatermarkTemplate(
                templateId = templateId,
                watermarkText = watermarkText,
                metadata = metadata,
                preservedExif = preservedExif
            )
            val renderResult = renderPhotoWatermarkBitmap(
                bitmap = mutableBitmap,
                template = resolvedTemplate
            )
            renderedBitmap = renderResult.bitmap
            val t2 = System.currentTimeMillis()
            val encodedBytes = try {
                ByteArrayOutputStream().use { output ->
                    check(renderResult.bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                        "Watermark JPEG compression failed"
                    }
                    output.toByteArray()
                }
            } catch (e: OutOfMemoryError) {
                return@withContext ProcessorEditorResult.Failed("encode-oom")
            } catch (_: Throwable) {
                return@withContext ProcessorEditorResult.Failed("encode-failed")
            }
            val t3 = System.currentTimeMillis()
            if (!writeEncodedBytes(target, encodedBytes)) {
                return@withContext ProcessorEditorResult.Failed("output-unavailable")
            }
            val t4 = System.currentTimeMillis()

            val exifWarning = restorePreservedExif(
                target = target,
                preservedExif = preservedExif
            )
            val t5 = System.currentTimeMillis()

            val visibleBytesAfterExif = readSourceBytes(target)
            val (archiveBytes, archiveWarning) = embedArchiveAfterVisibleWrite(
                originalBytes = sourceBytes,
                visibleBytesAfterExifRestore = visibleBytesAfterExif,
                templateId = templateId,
                originalWidth = originalWidth,
                originalHeight = originalHeight
            )
            val archiveWriteWarning = if (archiveBytes != null && !writeEncodedBytes(target, archiveBytes)) {
                "archive-write-failed"
            } else {
                null
            }
            val t6 = System.currentTimeMillis()

            val timingNote = "watermark:timing:$templateId size=${originalWidth}x${originalHeight} " +
                "decode=${t1 - t0}ms render=${t2 - t1}ms encode=${t3 - t2}ms " +
                "write=${t4 - t3}ms exif=${t5 - t4}ms archive=${t6 - t5}ms total=${t6 - t0}ms"

            PhotoWatermarkApplied(
                warning = mergeWarnings(renderResult.warning, exifWarning, archiveWarning, archiveWriteWarning),
                timingNote = timingNote
            )
        } catch (e: OutOfMemoryError) {
            ProcessorEditorResult.Failed("render-oom")
        } catch (_: Throwable) {
            ProcessorEditorResult.Failed("render-exception")
        } finally {
            if (renderedBitmap != null && renderedBitmap !== mutableBitmap) {
                renderedBitmap.recycle()
            }
            mutableBitmap.recycle()
        }
    }

    private fun readSourceBytes(target: ProcessorTarget): ByteArray? {
        return when (target) {
            is ProcessorTarget.FilePath -> {
                val file = File(target.path)
                if (!file.exists()) {
                    null
                } else {
                    file.readBytes()
                }
            }

            is ProcessorTarget.ContentUri -> {
                contentResolver.openInputStream(Uri.parse(target.value))?.use { it.readBytes() }
            }
        }
    }

    private fun writeEncodedBytes(
        target: ProcessorTarget,
        encodedBytes: ByteArray
    ): Boolean {
        return when (target) {
            is ProcessorTarget.FilePath -> runCatching {
                File(target.path).outputStream().use { it.write(encodedBytes) }
            }.isSuccess

            is ProcessorTarget.ContentUri -> {
                contentResolver.openOutputStream(Uri.parse(target.value), "rwt")?.use {
                    it.write(encodedBytes)
                } != null
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
        target: ProcessorTarget,
        preservedExif: Map<String, String>
    ): String? {
        if (preservedExif.isEmpty()) {
            return null
        }

        val restored = runCatching {
            when (target) {
                is ProcessorTarget.FilePath -> {
                    ExifInterface(target.path).apply {
                        applyPreservedExif(preservedExif)
                        saveAttributes()
                    }
                }

                is ProcessorTarget.ContentUri -> {
                    contentResolver.openFileDescriptor(Uri.parse(target.value), "rw")?.use { descriptor ->
                        ExifInterface(descriptor.fileDescriptor).apply {
                            applyPreservedExif(preservedExif)
                            saveAttributes()
                        }
                    } ?: error("file-descriptor-unavailable")
                }
            }
        }
        return if (restored.isSuccess) {
            null
        } else {
            "exif-restore-failed"
        }
    }

    private fun ExifInterface.applyPreservedExif(preservedExif: Map<String, String>) {
        preservedExif.forEach { (tag, value) ->
            setAttribute(tag, value)
        }
    }

    private fun mergeWarnings(
        templateWarning: String?,
        exifWarning: String?,
        archiveWarning: String? = null,
        archiveWriteWarning: String? = null
    ): String? {
        return listOfNotNull(templateWarning, exifWarning, archiveWarning, archiveWriteWarning)
            .takeIf { warnings -> warnings.isNotEmpty() }
            ?.joinToString(separator = ",")
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

internal fun buildWatermarkArchive(
    originalBytes: ByteArray,
    visibleBytes: ByteArray,
    templateId: String,
    originalWidth: Int,
    originalHeight: Int
): OcwmJpegContainer.EmbeddedArchive? {
    if (originalBytes.isEmpty() || visibleBytes.isEmpty()) return null
    val manifest = ReversibleWatermarkArchiveManifest(
        watermarkTemplateId = templateId,
        visibleImageSha256 = sha256Hex(visibleBytes),
        payloadSha256 = sha256Hex(originalBytes),
        payloadLength = originalBytes.size.toLong(),
        originalWidth = originalWidth,
        originalHeight = originalHeight
    )
    return OcwmJpegContainer.EmbeddedArchive(manifest = manifest, payload = originalBytes)
}

internal fun embedArchiveAfterVisibleWrite(
    originalBytes: ByteArray,
    visibleBytesAfterExifRestore: ByteArray?,
    templateId: String,
    originalWidth: Int,
    originalHeight: Int
): Pair<ByteArray?, String?> {
    if (originalBytes.isEmpty()) return null to "archive-input-empty"
    if (visibleBytesAfterExifRestore == null) return null to "archive-visible-unavailable"
    val archive = buildWatermarkArchive(
        originalBytes = originalBytes,
        visibleBytes = visibleBytesAfterExifRestore,
        templateId = templateId,
        originalWidth = originalWidth,
        originalHeight = originalHeight
    ) ?: return null to "archive-embed-failed"
    return try {
        OcwmJpegContainer.embedArchive(visibleBytesAfterExifRestore, archive) to null
    } catch (_: Throwable) {
        null to "archive-embed-failed"
    }
}

private const val TEXT_SIZE_RATIO = 0.042f
private const val DETAIL_TEXT_SCALE = 0.58f
private const val PADDING_RATIO = 0.028f
private const val MIN_TEXT_SIZE_PX = 24f
private const val MIN_DETAIL_TEXT_SIZE_PX = 18f
private const val MAX_TEXT_SIZE_PX = 88f
private const val MAX_TEXT_SCALE = 1.4f
private const val MIN_PADDING_PX = 18f
private const val MIN_CORNER_RADIUS_PX = 12f
private const val BLUR_DOWNSAMPLE_DIVISOR = 18
private const val BLUR_EDGE_DOWNSAMPLE_DIVISOR = 8

internal fun renderPhotoWatermarkBitmap(
    bitmap: Bitmap,
    template: ResolvedPhotoWatermarkTemplate
): PhotoWatermarkBitmapRenderResult {
    val minEdge = minOf(bitmap.width, bitmap.height).toFloat()
    val titleTextSize = (
        (minEdge * TEXT_SIZE_RATIO).coerceIn(MIN_TEXT_SIZE_PX, MAX_TEXT_SIZE_PX) *
            template.textScale
        ).coerceIn(MIN_TEXT_SIZE_PX, MAX_TEXT_SIZE_PX * MAX_TEXT_SCALE)
    val detailTextSize = (titleTextSize * DETAIL_TEXT_SCALE).coerceAtLeast(MIN_DETAIL_TEXT_SIZE_PX)
    val padding = (minEdge * PADDING_RATIO).coerceAtLeast(MIN_PADDING_PX)
    return when (template.templateId) {
        TEMPLATE_CLASSIC_OVERLAY -> {
            val canvas = Canvas(bitmap)
            drawClassicOverlay(
                canvas = canvas,
                bitmap = bitmap,
                template = template,
                titleTextSize = titleTextSize,
                detailTextSize = detailTextSize,
                padding = padding
            )
            PhotoWatermarkBitmapRenderResult(
                bitmap = bitmap,
                warning = template.warning
            )
        }

        TEMPLATE_TRAVEL_POLAROID -> drawExpandedFrame(
            source = bitmap,
            template = template,
            titleTextSize = titleTextSize,
            detailTextSize = detailTextSize,
            padding = padding,
            sideBorderScale = 1.0f,
            topBorderScale = 0.9f,
            bottomBandScale = 4.6f,
            titleColor = Color.argb(255, 74, 54, 38),
            detailColor = Color.argb(255, 132, 103, 72),
            centered = false
        )

        TEMPLATE_PURE_TEXT -> {
            val canvas = Canvas(bitmap)
            drawPureTextOverlay(
                canvas = canvas,
                bitmap = bitmap,
                template = template,
                titleTextSize = titleTextSize,
                detailTextSize = detailTextSize,
                padding = padding
            )
            PhotoWatermarkBitmapRenderResult(bitmap = bitmap, warning = template.warning)
        }

        TEMPLATE_RETRO_FRAME -> drawExpandedFrame(
            source = bitmap,
            template = template,
            titleTextSize = titleTextSize * 0.96f,
            detailTextSize = detailTextSize,
            padding = padding,
            sideBorderScale = 1.05f,
            topBorderScale = 0.92f,
            bottomBandScale = 4.2f,
            titleColor = Color.argb(255, 255, 238, 205),
            detailColor = Color.argb(255, 224, 196, 154),
            centered = true
        )

        TEMPLATE_BLUR_FOUR_BORDER -> drawBlurFourBorderFrame(
            source = bitmap,
            template = template,
            titleTextSize = titleTextSize,
            detailTextSize = detailTextSize,
            padding = padding
        )

        TEMPLATE_PROFESSIONAL_BOTTOM_BAR -> drawProfessionalBottomBar(
            source = bitmap,
            template = template,
            titleTextSize = titleTextSize,
            detailTextSize = detailTextSize,
            padding = padding
        )

        else -> PhotoWatermarkBitmapRenderResult(
            bitmap = bitmap,
            warning = mergeWarnings(template.warning, "template-fallback")
        )
    }
}

private fun drawClassicOverlay(
    canvas: Canvas,
    bitmap: Bitmap,
    template: ResolvedPhotoWatermarkTemplate,
    titleTextSize: Float,
    detailTextSize: Float,
    padding: Float
) {
    val cornerRadius = (padding * 0.8f).coerceAtLeast(MIN_CORNER_RADIUS_PX)
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = titleTextSize
        style = Paint.Style.FILL
        alpha = (255 * template.textOpacity).toInt()
        setShadowLayer(titleTextSize * 0.18f, 0f, titleTextSize * 0.08f, Color.argb(160, 0, 0, 0))
    }
    val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 220, 232, 245)
        textSize = detailTextSize
        style = Paint.Style.FILL
        alpha = (235 * template.textOpacity).toInt()
    }
    val lines = listOf(template.title) + template.supportingLines
    val blockWidth = lines.maxOf { line ->
        titlePaint.measureText(line).coerceAtLeast(detailPaint.measureText(line))
    }
    val lineGap = detailTextSize * 0.28f
    val titleMetrics = titlePaint.fontMetrics
    val detailMetrics = detailPaint.fontMetrics
    val titleHeight = titleMetrics.descent - titleMetrics.ascent
    val detailHeight = detailMetrics.descent - detailMetrics.ascent
    val blockHeight = titleHeight +
        template.supportingLines.size * detailHeight +
        template.supportingLines.size * lineGap
    val left = when (template.placement) {
        WatermarkTextPlacement.TOP_LEFT,
        WatermarkTextPlacement.BOTTOM_LEFT -> padding
        WatermarkTextPlacement.BOTTOM_CENTER -> (bitmap.width - blockWidth) / 2f
        WatermarkTextPlacement.TOP_RIGHT,
        WatermarkTextPlacement.BOTTOM_RIGHT -> bitmap.width - padding - blockWidth - padding
    }
    val top = when (template.placement) {
        WatermarkTextPlacement.TOP_LEFT,
        WatermarkTextPlacement.TOP_RIGHT -> padding
        WatermarkTextPlacement.BOTTOM_LEFT,
        WatermarkTextPlacement.BOTTOM_RIGHT,
        WatermarkTextPlacement.BOTTOM_CENTER -> bitmap.height - padding - blockHeight - padding
    }
    val backgroundRect = RectF(
        left - padding,
        top - padding,
        left + blockWidth + padding,
        top + blockHeight + padding
    )
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb((144 * template.textOpacity).toInt(), 0, 0, 0)
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint)

    var baseline = top - titleMetrics.ascent
    canvas.drawText(template.title, left, baseline, titlePaint)
    template.supportingLines.forEach { line ->
        baseline += detailHeight + lineGap
        canvas.drawText(line, left, baseline, detailPaint)
    }
}

private fun drawPureTextOverlay(
    canvas: Canvas,
    bitmap: Bitmap,
    template: ResolvedPhotoWatermarkTemplate,
    titleTextSize: Float,
    detailTextSize: Float,
    padding: Float
) {
    val align = when (template.placement) {
        WatermarkTextPlacement.TOP_LEFT,
        WatermarkTextPlacement.BOTTOM_LEFT -> Paint.Align.LEFT
        WatermarkTextPlacement.BOTTOM_CENTER -> Paint.Align.CENTER
        WatermarkTextPlacement.TOP_RIGHT,
        WatermarkTextPlacement.BOTTOM_RIGHT -> Paint.Align.RIGHT
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = titleTextSize
        style = Paint.Style.FILL
        textAlign = align
        alpha = (255 * template.textOpacity).toInt()
        setShadowLayer(titleTextSize * 0.22f, 0f, titleTextSize * 0.08f, Color.argb(190, 0, 0, 0))
    }
    val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 235, 238, 242)
        textSize = detailTextSize
        style = Paint.Style.FILL
        textAlign = align
        alpha = (220 * template.textOpacity).toInt()
        setShadowLayer(detailTextSize * 0.22f, 0f, detailTextSize * 0.08f, Color.argb(190, 0, 0, 0))
    }
    val lineGap = detailTextSize * 0.28f
    val titleMetrics = titlePaint.fontMetrics
    val detailMetrics = detailPaint.fontMetrics
    val titleHeight = titleMetrics.descent - titleMetrics.ascent
    val detailHeight = detailMetrics.descent - detailMetrics.ascent
    val blockHeight = titleHeight +
        template.supportingLines.size * detailHeight +
        template.supportingLines.size * lineGap
    val x = when (template.placement) {
        WatermarkTextPlacement.TOP_LEFT,
        WatermarkTextPlacement.BOTTOM_LEFT -> padding
        WatermarkTextPlacement.BOTTOM_CENTER -> bitmap.width / 2f
        WatermarkTextPlacement.TOP_RIGHT,
        WatermarkTextPlacement.BOTTOM_RIGHT -> bitmap.width - padding
    }
    val top = when (template.placement) {
        WatermarkTextPlacement.TOP_LEFT,
        WatermarkTextPlacement.TOP_RIGHT -> padding
        WatermarkTextPlacement.BOTTOM_LEFT,
        WatermarkTextPlacement.BOTTOM_RIGHT,
        WatermarkTextPlacement.BOTTOM_CENTER -> bitmap.height - padding - blockHeight - padding
    }
    val maxWidth = bitmap.width - padding * 2
    var baseline = top - titleMetrics.ascent
    canvas.drawText(template.title, x, baseline, titlePaint)
    template.supportingLines.take(2).forEach { rawLine ->
        baseline += detailHeight + lineGap
        val line = fitText(rawLine, detailPaint, maxWidth)
        canvas.drawText(line, x, baseline, detailPaint)
    }
}

private fun drawExpandedFrame(
    source: Bitmap,
    template: ResolvedPhotoWatermarkTemplate,
    titleTextSize: Float,
    detailTextSize: Float,
    padding: Float,
    sideBorderScale: Float,
    topBorderScale: Float,
    bottomBandScale: Float,
    titleColor: Int,
    detailColor: Int,
    centered: Boolean
): PhotoWatermarkBitmapRenderResult {
    val sideBorder = (padding * sideBorderScale).coerceAtLeast(MIN_PADDING_PX * 0.7f)
    val topBorder = (padding * topBorderScale).coerceAtLeast(MIN_PADDING_PX * 0.65f)
    val bottomBandHeight = maxOf(titleTextSize * bottomBandScale, source.height * 0.18f)
    val framedWidth = (source.width + sideBorder * 2f).toInt()
    val framedHeight = (source.height + topBorder + bottomBandHeight).toInt()
    val framedBitmap = Bitmap.createBitmap(framedWidth, framedHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(framedBitmap)
    val fullRect = RectF(0f, 0f, framedWidth.toFloat(), framedHeight.toFloat())
    drawFrameBackground(canvas, source, fullRect, template.frameBackground)
    canvas.drawBitmap(source, sideBorder, topBorder, null)

    val contentLeft = sideBorder + padding
    val contentRight = framedWidth - sideBorder - padding
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor
        textSize = titleTextSize
        style = Paint.Style.FILL
        alpha = (255 * template.textOpacity).toInt()
        textAlign = when {
            centered || template.placement == WatermarkTextPlacement.BOTTOM_CENTER -> Paint.Align.CENTER
            template.placement == WatermarkTextPlacement.BOTTOM_RIGHT -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
    }
    val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = detailColor
        textSize = detailTextSize
        style = Paint.Style.FILL
        alpha = (255 * template.textOpacity).toInt()
        textAlign = when {
            centered || template.placement == WatermarkTextPlacement.BOTTOM_CENTER -> Paint.Align.CENTER
            template.placement == WatermarkTextPlacement.BOTTOM_RIGHT -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
    }
    val titleMetrics = titlePaint.fontMetrics
    val detailMetrics = detailPaint.fontMetrics
    val blockTop = source.height + topBorder + padding
    var baseline = blockTop - titleMetrics.ascent
    val titleX = when {
        centered || template.placement == WatermarkTextPlacement.BOTTOM_CENTER -> framedWidth / 2f
        template.placement == WatermarkTextPlacement.BOTTOM_RIGHT -> contentRight
        else -> contentLeft
    }
    canvas.drawText(template.title, titleX, baseline, titlePaint)
    val detailX = when {
        centered || template.placement == WatermarkTextPlacement.BOTTOM_CENTER -> framedWidth / 2f
        template.placement == WatermarkTextPlacement.BOTTOM_RIGHT -> contentRight
        else -> contentLeft
    }
    template.supportingLines.take(2).forEach { line ->
        baseline += (detailMetrics.descent - detailMetrics.ascent) + detailTextSize * 0.24f
        canvas.drawText(
            fitText(line, detailPaint, contentRight - contentLeft),
            detailX,
            baseline,
            detailPaint
        )
    }
    return PhotoWatermarkBitmapRenderResult(
        bitmap = framedBitmap,
        warning = template.warning
    )
}

private fun drawBlurFourBorderFrame(
    source: Bitmap,
    template: ResolvedPhotoWatermarkTemplate,
    titleTextSize: Float,
    detailTextSize: Float,
    padding: Float
): PhotoWatermarkBitmapRenderResult {
    val minEdge = minOf(source.width, source.height).toFloat()
    val metrics = blurFourBorderFrameMetrics(
        minEdge = minEdge,
        titleTextSize = titleTextSize
    )
    val sideBorder = metrics.sideBorder
    val topBorder = metrics.topBorder
    val bottomBorder = metrics.bottomBorder
    val framedWidth = (source.width + sideBorder * 2f).toInt()
    val framedHeight = (source.height + topBorder + bottomBorder).toInt()
    val framedBitmap = Bitmap.createBitmap(framedWidth, framedHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(framedBitmap)
    val sideBorderInt = sideBorder.toInt()
    val topBorderInt = topBorder.toInt()
    val bottomBorderInt = bottomBorder.toInt()
    drawContentAwareEdgeBorder(
        canvas, source, framedWidth, framedHeight,
        sideBorderInt, topBorderInt, bottomBorderInt,
        background = template.frameBackground,
        captureCropZoom = template.captureCropZoom
    )
    canvas.drawBitmap(source, sideBorder, topBorder, null)

    drawFourBorderCardAccents(
        canvas = canvas,
        background = template.frameBackground,
        framedWidth = framedWidth,
        framedHeight = framedHeight,
        sideBorder = sideBorder,
        topBorder = topBorder,
        bottomBorder = bottomBorder,
        sourceWidth = source.width,
        sourceHeight = source.height
    )

    val titleColor = when (template.frameBackground) {
        WatermarkFrameBackground.SOURCE_BLUR -> Color.argb(255, 245, 240, 230)
        WatermarkFrameBackground.SOURCE_LIGHT_BLUR -> Color.argb(255, 58, 55, 50)
        WatermarkFrameBackground.SOURCE_VIVID_BLUR -> Color.argb(255, 255, 250, 235)
        else -> Color.WHITE
    }
    val detailColor = when (template.frameBackground) {
        WatermarkFrameBackground.SOURCE_BLUR -> Color.argb(230, 225, 218, 208)
        WatermarkFrameBackground.SOURCE_LIGHT_BLUR -> Color.argb(230, 95, 90, 82)
        WatermarkFrameBackground.SOURCE_VIVID_BLUR -> Color.argb(230, 235, 228, 210)
        else -> Color.argb(220, 235, 238, 242)
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor
        textSize = titleTextSize
        style = Paint.Style.FILL
        alpha = (255 * template.textOpacity).toInt()
        textAlign = when (template.placement) {
            WatermarkTextPlacement.BOTTOM_CENTER -> Paint.Align.CENTER
            WatermarkTextPlacement.BOTTOM_RIGHT -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
    }
    val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = detailColor
        textSize = detailTextSize
        style = Paint.Style.FILL
        alpha = (255 * template.textOpacity).toInt()
        textAlign = when (template.placement) {
            WatermarkTextPlacement.BOTTOM_CENTER -> Paint.Align.CENTER
            WatermarkTextPlacement.BOTTOM_RIGHT -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
    }
    val contentLeft = sideBorder + padding
    val contentRight = framedWidth - sideBorder - padding
    val blockTop = source.height + topBorder + padding
    val titleMetrics = titlePaint.fontMetrics
    val detailMetrics = detailPaint.fontMetrics
    var baseline = blockTop - titleMetrics.ascent
    val titleX = when (template.placement) {
        WatermarkTextPlacement.BOTTOM_CENTER -> framedWidth / 2f
        WatermarkTextPlacement.BOTTOM_RIGHT -> contentRight
        else -> contentLeft
    }
    canvas.drawText(template.title, titleX, baseline, titlePaint)
    template.supportingLines.take(2).forEach { line ->
        baseline += (detailMetrics.descent - detailMetrics.ascent) + detailTextSize * 0.24f
        val detailX = when (template.placement) {
            WatermarkTextPlacement.BOTTOM_CENTER -> framedWidth / 2f
            WatermarkTextPlacement.BOTTOM_RIGHT -> contentRight
            else -> contentLeft
        }
        canvas.drawText(
            fitText(line, detailPaint, contentRight - contentLeft),
            detailX,
            baseline,
            detailPaint
        )
    }

    return PhotoWatermarkBitmapRenderResult(
        bitmap = framedBitmap,
        warning = template.warning
    )
}

internal data class BlurFourBorderFrameMetrics(
    val sideBorder: Float,
    val topBorder: Float,
    val bottomBorder: Float
)

internal fun blurFourBorderFrameMetrics(
    minEdge: Float,
    titleTextSize: Float
): BlurFourBorderFrameMetrics {
    return BlurFourBorderFrameMetrics(
        sideBorder = maxOf(24f, minEdge * 0.06f),
        topBorder = maxOf(24f, minEdge * 0.055f),
        bottomBorder = maxOf(titleTextSize * 3.15f, minEdge * 0.16f)
    )
}

private fun drawFourBorderCardAccents(
    canvas: Canvas,
    background: WatermarkFrameBackground,
    framedWidth: Int,
    framedHeight: Int,
    sideBorder: Float,
    topBorder: Float,
    bottomBorder: Float,
    sourceWidth: Int,
    sourceHeight: Int
) {
    val accentColor = when (background) {
        WatermarkFrameBackground.SOURCE_VIVID_BLUR -> Color.argb(178, 245, 202, 126)
        WatermarkFrameBackground.SOURCE_BLUR -> Color.argb(132, 245, 238, 220)
        else -> Color.argb(150, 220, 248, 246)
    }
    val innerColor = when (background) {
        WatermarkFrameBackground.SOURCE_VIVID_BLUR -> Color.argb(118, 42, 28, 18)
        WatermarkFrameBackground.SOURCE_BLUR -> Color.argb(136, 255, 255, 255)
        else -> Color.argb(148, 255, 255, 255)
    }
    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = innerColor
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    canvas.drawRect(
        1f,
        1f,
        framedWidth - 1f,
        framedHeight - 1f,
        accentPaint
    )
    canvas.drawRect(
        sideBorder - 0.5f,
        topBorder - 0.5f,
        sideBorder + sourceWidth + 0.5f,
        topBorder + sourceHeight + 0.5f,
        innerPaint
    )
    canvas.drawLine(
        sideBorder,
        framedHeight - bottomBorder,
        framedWidth - sideBorder,
        framedHeight - bottomBorder,
        accentPaint
    )
}

private fun drawProfessionalBottomBar(
    source: Bitmap,
    template: ResolvedPhotoWatermarkTemplate,
    titleTextSize: Float,
    detailTextSize: Float,
    padding: Float
): PhotoWatermarkBitmapRenderResult {
    val bottomBandHeight = maxOf(titleTextSize * 3.8f, source.height * 0.15f)
    val framedWidth = source.width
    val framedHeight = (source.height + bottomBandHeight).toInt()
    val framedBitmap = Bitmap.createBitmap(framedWidth, framedHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(framedBitmap)
    val fullRect = RectF(0f, 0f, framedWidth.toFloat(), framedHeight.toFloat())
    drawFrameBackground(canvas, source, fullRect, template.frameBackground)
    canvas.drawBitmap(source, 0f, 0f, null)

    val hairlineColor = when (template.frameBackground) {
        WatermarkFrameBackground.WHITE -> Color.argb(40, 0, 0, 0)
        WatermarkFrameBackground.SOURCE_LIGHT_BLUR -> Color.argb(50, 0, 0, 0)
        else -> Color.argb(40, 255, 255, 255)
    }
    val hairlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = hairlineColor
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    canvas.drawLine(
        0f,
        source.height.toFloat(),
        framedWidth.toFloat(),
        source.height.toFloat(),
        hairlinePaint
    )

    val titleColor = when (template.frameBackground) {
        WatermarkFrameBackground.DARK,
        WatermarkFrameBackground.SOURCE_BLUR -> Color.argb(255, 248, 248, 252)
        WatermarkFrameBackground.SOURCE_VIVID_BLUR -> Color.argb(255, 255, 250, 235)
        else -> Color.argb(255, 44, 48, 58)
    }
    val detailColor = when (template.frameBackground) {
        WatermarkFrameBackground.DARK,
        WatermarkFrameBackground.SOURCE_BLUR -> Color.argb(226, 190, 194, 204)
        WatermarkFrameBackground.SOURCE_VIVID_BLUR -> Color.argb(230, 235, 228, 210)
        else -> Color.argb(220, 120, 118, 112)
    }
    val contentLeft = padding
    val contentRight = framedWidth - padding
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor
        textSize = titleTextSize
        style = Paint.Style.FILL
        alpha = (255 * template.textOpacity).toInt()
        textAlign = when (template.placement) {
            WatermarkTextPlacement.BOTTOM_CENTER -> Paint.Align.CENTER
            WatermarkTextPlacement.BOTTOM_RIGHT -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
    }
    val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = detailColor
        textSize = detailTextSize
        style = Paint.Style.FILL
        alpha = (255 * template.textOpacity).toInt()
        textAlign = when (template.placement) {
            WatermarkTextPlacement.BOTTOM_CENTER -> Paint.Align.CENTER
            WatermarkTextPlacement.BOTTOM_RIGHT -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
    }
    val titleMetrics = titlePaint.fontMetrics
    val detailMetrics = detailPaint.fontMetrics
    val blockTop = source.height + padding
    var baseline = blockTop - titleMetrics.ascent
    val titleX = when (template.placement) {
        WatermarkTextPlacement.BOTTOM_CENTER -> framedWidth / 2f
        WatermarkTextPlacement.BOTTOM_RIGHT -> contentRight
        else -> contentLeft
    }
    canvas.drawText(template.title, titleX, baseline, titlePaint)
    val detailX = when (template.placement) {
        WatermarkTextPlacement.BOTTOM_CENTER -> framedWidth / 2f
        WatermarkTextPlacement.BOTTOM_RIGHT -> contentRight
        else -> contentLeft
    }
    template.supportingLines.take(2).forEach { line ->
        baseline += (detailMetrics.descent - detailMetrics.ascent) + detailTextSize * 0.24f
        canvas.drawText(
            fitText(line, detailPaint, contentRight - contentLeft),
            detailX,
            baseline,
            detailPaint
        )
    }
    return PhotoWatermarkBitmapRenderResult(
        bitmap = framedBitmap,
        warning = template.warning
    )
}

private fun drawContentAwareEdgeBorder(
    canvas: Canvas,
    source: Bitmap,
    framedWidth: Int,
    framedHeight: Int,
    sideBorder: Int,
    topBorder: Int,
    bottomBorder: Int,
    background: WatermarkFrameBackground = WatermarkFrameBackground.SOURCE_LIGHT_BLUR,
    captureCropZoom: Float = 1f
) {
    val expandedBackground = createBlurredExpandedEdgeBitmap(
        source = source,
        framedWidth = framedWidth,
        framedHeight = framedHeight,
        captureCropZoom = captureCropZoom
    )
    canvas.drawBitmap(expandedBackground, 0f, 0f, null)
    expandedBackground.recycle()

    val tintOverlay = contentAwareEdgeTintOverlay(background)
    if (tintOverlay != Color.TRANSPARENT) {
        val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tintOverlay
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, framedWidth.toFloat(), topBorder.toFloat(), tintPaint)
        canvas.drawRect(0f, (framedHeight - bottomBorder).toFloat(), framedWidth.toFloat(), framedHeight.toFloat(), tintPaint)
        canvas.drawRect(0f, topBorder.toFloat(), sideBorder.toFloat(), (framedHeight - bottomBorder).toFloat(), tintPaint)
        canvas.drawRect((framedWidth - sideBorder).toFloat(), topBorder.toFloat(), framedWidth.toFloat(), (framedHeight - bottomBorder).toFloat(), tintPaint)
    }
}

internal fun contentAwareEdgeTintOverlay(background: WatermarkFrameBackground): Int {
    return when (background) {
        WatermarkFrameBackground.SOURCE_BLUR -> Color.argb(64, 20, 20, 20)
        WatermarkFrameBackground.SOURCE_VIVID_BLUR -> Color.argb(72, 245, 210, 170)
        else -> Color.TRANSPARENT
    }
}

private fun createBlurredExpandedEdgeBitmap(
    source: Bitmap,
    framedWidth: Int,
    framedHeight: Int,
    captureCropZoom: Float = 1f
): Bitmap {
    val backgroundSource = createCaptureCropSource(source, captureCropZoom)
    val minFramedEdge = minOf(framedWidth, framedHeight)
    val divisor = if (minFramedEdge < 600) {
        1
    } else {
        BLUR_EDGE_DOWNSAMPLE_DIVISOR
    }
    val dsWidth = maxOf(1, framedWidth / divisor)
    val dsHeight = maxOf(1, framedHeight / divisor)
    val downsampled = Bitmap.createScaledBitmap(backgroundSource, dsWidth, dsHeight, true)
    if (backgroundSource !== source) {
        backgroundSource.recycle()
    }
    val mutable = downsampled.copy(Bitmap.Config.ARGB_8888, true)
    if (mutable !== downsampled) {
        downsampled.recycle()
    }
    applySeparableBoxBlur(
        bitmap = mutable,
        horizontalRadius = maxOf(4, blurRadiusForLength(framedWidth) / divisor),
        verticalRadius = maxOf(4, blurRadiusForLength(framedHeight) / divisor),
        passes = 2
    )
    val result = if (divisor > 1) {
        val upscaled = Bitmap.createScaledBitmap(mutable, framedWidth, framedHeight, true)
        mutable.recycle()
        upscaled
    } else {
        mutable
    }
    return result
}

private fun createCaptureCropSource(source: Bitmap, captureCropZoom: Float): Bitmap {
    val zoom = captureCropZoom.coerceAtLeast(1f)
    if (zoom <= 1.01f) {
        return source
    }
    val cropWidth = (source.width / zoom).toInt().coerceIn(1, source.width)
    val cropHeight = (source.height / zoom).toInt().coerceIn(1, source.height)
    val left = (source.width - cropWidth) / 2
    val top = (source.height - cropHeight) / 2
    return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
}

private fun blurRadiusForLength(length: Int): Int {
    return maxOf(14, minOf(64, length / 28))
}

private fun applySeparableBoxBlur(
    bitmap: Bitmap,
    horizontalRadius: Int,
    verticalRadius: Int,
    passes: Int
) {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 1 || height <= 1) return

    var input = IntArray(width * height)
    var output = IntArray(width * height)
    bitmap.getPixels(input, 0, width, 0, 0, width, height)

    repeat(passes) {
        boxBlurHorizontal(input, output, width, height, horizontalRadius.coerceAtLeast(1))
        boxBlurVertical(output, input, width, height, verticalRadius.coerceAtLeast(1))
    }
    bitmap.setPixels(input, 0, width, 0, 0, width, height)
}

private fun boxBlurHorizontal(
    input: IntArray,
    output: IntArray,
    width: Int,
    height: Int,
    radius: Int
) {
    val windowSize = radius * 2 + 1
    for (y in 0 until height) {
        val rowOffset = y * width
        var alphaSum = 0
        var redSum = 0
        var greenSum = 0
        var blueSum = 0
        for (offset in -radius..radius) {
            val pixel = input[rowOffset + offset.coerceIn(0, width - 1)]
            alphaSum += Color.alpha(pixel)
            redSum += Color.red(pixel)
            greenSum += Color.green(pixel)
            blueSum += Color.blue(pixel)
        }
        for (x in 0 until width) {
            output[rowOffset + x] = Color.argb(
                alphaSum / windowSize,
                redSum / windowSize,
                greenSum / windowSize,
                blueSum / windowSize
            )
            val removePixel = input[rowOffset + (x - radius).coerceIn(0, width - 1)]
            val addPixel = input[rowOffset + (x + radius + 1).coerceIn(0, width - 1)]
            alphaSum += Color.alpha(addPixel) - Color.alpha(removePixel)
            redSum += Color.red(addPixel) - Color.red(removePixel)
            greenSum += Color.green(addPixel) - Color.green(removePixel)
            blueSum += Color.blue(addPixel) - Color.blue(removePixel)
        }
    }
}

private fun boxBlurVertical(
    input: IntArray,
    output: IntArray,
    width: Int,
    height: Int,
    radius: Int
) {
    val windowSize = radius * 2 + 1
    for (x in 0 until width) {
        var alphaSum = 0
        var redSum = 0
        var greenSum = 0
        var blueSum = 0
        for (offset in -radius..radius) {
            val pixel = input[offset.coerceIn(0, height - 1) * width + x]
            alphaSum += Color.alpha(pixel)
            redSum += Color.red(pixel)
            greenSum += Color.green(pixel)
            blueSum += Color.blue(pixel)
        }
        for (y in 0 until height) {
            output[y * width + x] = Color.argb(
                alphaSum / windowSize,
                redSum / windowSize,
                greenSum / windowSize,
                blueSum / windowSize
            )
            val removePixel = input[(y - radius).coerceIn(0, height - 1) * width + x]
            val addPixel = input[(y + radius + 1).coerceIn(0, height - 1) * width + x]
            alphaSum += Color.alpha(addPixel) - Color.alpha(removePixel)
            redSum += Color.red(addPixel) - Color.red(removePixel)
            greenSum += Color.green(addPixel) - Color.green(removePixel)
            blueSum += Color.blue(addPixel) - Color.blue(removePixel)
        }
    }
}

private fun drawFrameBackground(
    canvas: Canvas,
    source: Bitmap,
    destination: RectF,
    background: WatermarkFrameBackground
) {
    when (background) {
        WatermarkFrameBackground.DARK -> {
            canvas.drawColor(Color.argb(220, 24, 20, 18))
        }

        WatermarkFrameBackground.WHITE -> {
            canvas.drawColor(Color.argb(244, 255, 247, 224))
        }

        WatermarkFrameBackground.SOURCE_BLUR,
        WatermarkFrameBackground.SOURCE_LIGHT_BLUR,
        WatermarkFrameBackground.SOURCE_VIVID_BLUR -> {
            val downsampled = Bitmap.createScaledBitmap(
                source,
                maxOf(1, source.width / BLUR_DOWNSAMPLE_DIVISOR),
                maxOf(1, source.height / BLUR_DOWNSAMPLE_DIVISOR),
                true
            )
            val srcRect = Rect(0, 0, downsampled.width, downsampled.height)
            val vividMatrix = if (background == WatermarkFrameBackground.SOURCE_VIVID_BLUR) {
                ColorMatrix().apply { setSaturation(1.2f) }
            } else {
                null
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                isDither = true
                alpha = 228
                vividMatrix?.let { colorFilter = ColorMatrixColorFilter(it) }
            }
            canvas.drawBitmap(downsampled, srcRect, destination, paint)
            downsampled.recycle()
            val overlayColor = when (background) {
                WatermarkFrameBackground.SOURCE_BLUR -> Color.argb(88, 20, 20, 20)
                WatermarkFrameBackground.SOURCE_LIGHT_BLUR -> Color.argb(124, 255, 244, 228)
                WatermarkFrameBackground.SOURCE_VIVID_BLUR -> Color.argb(104, 246, 198, 138)
                else -> Color.TRANSPARENT
            }
            canvas.drawRect(destination, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = overlayColor
                style = Paint.Style.FILL
            })
        }
    }
}

private fun fitText(
    text: String,
    paint: Paint,
    maxWidth: Float
): String {
    if (paint.measureText(text) <= maxWidth) {
        return text
    }
    var endIndex = text.length
    while (endIndex > 1) {
        val candidate = text.take(endIndex).trimEnd() + "…"
        if (paint.measureText(candidate) <= maxWidth) {
            return candidate
        }
        endIndex -= 1
    }
    return text.take(1) + "…"
}

private fun mergeWarnings(
    templateWarning: String?,
    exifWarning: String?,
    archiveWarning: String? = null
): String? {
    return listOfNotNull(templateWarning, exifWarning, archiveWarning)
        .takeIf { warnings -> warnings.isNotEmpty() }
        ?.joinToString(separator = ",")
}

private const val PHOTO_WATERMARK_TEMPLATE_KEY = "watermarkTemplate"
private const val PHOTO_WATERMARK_BACKGROUND_KEY = "watermarkFrameBackground"
private const val PHOTO_WATERMARK_MODEL_KEY = "watermarkModel"
private const val PHOTO_WATERMARK_DATETIME_KEY = "watermarkDatetime"
private const val PHOTO_WATERMARK_LOCATION_KEY = "watermarkLocation"
private const val PHOTO_WATERMARK_CAMERA_PARAMS_KEY = "watermarkCameraParams"
private const val PHOTO_WATERMARK_POSITION_KEY = "watermarkPosition"
private const val PHOTO_WATERMARK_TEXT_SCALE_KEY = "watermarkTextScale"
private const val PHOTO_WATERMARK_TEXT_OPACITY_KEY = "watermarkTextOpacity"
private const val PHOTO_WATERMARK_MODE_NAME_KEY = "watermarkModeName"
private const val PHOTO_WATERMARK_PROFILE_NAME_KEY = "watermarkProfileName"
private const val TEMPLATE_CLASSIC_OVERLAY = "classic-overlay"
private const val TEMPLATE_TRAVEL_POLAROID = "travel-polaroid"
private const val TEMPLATE_RETRO_FRAME = "retro-frame"
private const val TEMPLATE_PURE_TEXT = "pure-text"
private const val TEMPLATE_BLUR_FOUR_BORDER = "blur-four-border"
private const val TEMPLATE_PROFESSIONAL_BOTTOM_BAR = "professional-bottom-bar"

private fun resolveWatermarkTemplateId(templateId: String?): String {
    return templateId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: TEMPLATE_CLASSIC_OVERLAY
}

internal fun resolvePhotoWatermarkTemplate(
    templateId: String,
    watermarkText: String,
    metadata: MediaMetadata,
    preservedExif: Map<String, String>
): ResolvedPhotoWatermarkTemplate {
    val requestedTemplateId = templateId.trim().ifEmpty { TEMPLATE_CLASSIC_OVERLAY }
    val normalizedTemplateId = when (requestedTemplateId) {
        TEMPLATE_CLASSIC_OVERLAY,
        TEMPLATE_TRAVEL_POLAROID,
        TEMPLATE_RETRO_FRAME,
        TEMPLATE_PURE_TEXT,
        TEMPLATE_BLUR_FOUR_BORDER,
        TEMPLATE_PROFESSIONAL_BOTTOM_BAR -> requestedTemplateId
        else -> TEMPLATE_CLASSIC_OVERLAY
    }
    val warning = if (normalizedTemplateId == requestedTemplateId) {
        null
    } else {
        "template-fallback"
    }
    val model = resolveDeviceModel(metadata.customTags, preservedExif)
    val datetime = metadata.customTags[PHOTO_WATERMARK_DATETIME_KEY]
        ?: formatExifDateTime(
            preservedExif[ExifInterface.TAG_DATETIME_ORIGINAL]
                ?: preservedExif[ExifInterface.TAG_DATETIME]
        )
    val location = metadata.customTags[PHOTO_WATERMARK_LOCATION_KEY]
        ?: formatExifLocation(preservedExif)
    val cameraParams = metadata.customTags[PHOTO_WATERMARK_CAMERA_PARAMS_KEY]
        ?: formatCameraParams(preservedExif)
    val profileName = metadata.customTags[PHOTO_WATERMARK_PROFILE_NAME_KEY]
    val supportedTokens = when (normalizedTemplateId) {
        TEMPLATE_CLASSIC_OVERLAY,
        TEMPLATE_PURE_TEXT -> listOfNotNull(datetime, location, cameraParams)
        TEMPLATE_TRAVEL_POLAROID -> listOfNotNull(datetime, location, profileName)
        TEMPLATE_RETRO_FRAME,
        TEMPLATE_BLUR_FOUR_BORDER -> listOfNotNull(datetime, cameraParams, profileName)
        TEMPLATE_PROFESSIONAL_BOTTOM_BAR -> listOfNotNull(datetime, cameraParams)
        else -> emptyList()
    }
    return ResolvedPhotoWatermarkTemplate(
        templateId = normalizedTemplateId,
        title = resolveWatermarkTitle(normalizedTemplateId, watermarkText, metadata.customTags, model),
        supportingLines = chunkWatermarkTokens(supportedTokens),
        frameBackground = resolveWatermarkFrameBackground(
            templateId = normalizedTemplateId,
            customTags = metadata.customTags
        ),
        usesExpandedFrame = normalizedTemplateId == TEMPLATE_TRAVEL_POLAROID ||
            normalizedTemplateId == TEMPLATE_RETRO_FRAME ||
            normalizedTemplateId == TEMPLATE_BLUR_FOUR_BORDER ||
            normalizedTemplateId == TEMPLATE_PROFESSIONAL_BOTTOM_BAR,
        placement = resolveWatermarkPlacement(normalizedTemplateId, metadata.customTags),
        textScale = metadata.customTags[PHOTO_WATERMARK_TEXT_SCALE_KEY]
            ?.toFloatOrNull()
            ?.coerceIn(0.75f, 1.4f)
            ?: 1f,
        textOpacity = metadata.customTags[PHOTO_WATERMARK_TEXT_OPACITY_KEY]
            ?.toFloatOrNull()
            ?.coerceIn(0.35f, 1f)
            ?: 1f,
        captureCropZoom = metadata.customTags["captureCropZoom"]
            ?.toFloatOrNull()
            ?.coerceAtLeast(1f)
            ?: 1f,
        warning = warning
    )
}

private fun resolveWatermarkPlacement(
    templateId: String,
    customTags: Map<String, String>
): WatermarkTextPlacement {
    WatermarkTextPlacement.fromStorageKey(customTags[PHOTO_WATERMARK_POSITION_KEY])?.let {
        return it
    }
    return defaultWatermarkPlacement(templateId)
}

private fun defaultWatermarkPlacement(templateId: String): WatermarkTextPlacement {
    return when (templateId) {
        TEMPLATE_RETRO_FRAME -> WatermarkTextPlacement.BOTTOM_CENTER
        TEMPLATE_PROFESSIONAL_BOTTOM_BAR -> WatermarkTextPlacement.BOTTOM_CENTER
        else -> WatermarkTextPlacement.BOTTOM_LEFT
    }
}

private fun resolveWatermarkFrameBackground(
    templateId: String,
    customTags: Map<String, String>
): WatermarkFrameBackground {
    val resolved = WatermarkFrameBackground.fromStorageKey(customTags[PHOTO_WATERMARK_BACKGROUND_KEY])
        ?: defaultWatermarkFrameBackground(templateId)
    if (templateId == TEMPLATE_BLUR_FOUR_BORDER && resolved !in SUPPORTED_BLUR_BACKGROUNDS) {
        return WatermarkFrameBackground.SOURCE_LIGHT_BLUR
    }
    return resolved
}

private val SUPPORTED_BLUR_BACKGROUNDS = setOf(
    WatermarkFrameBackground.SOURCE_BLUR,
    WatermarkFrameBackground.SOURCE_LIGHT_BLUR,
    WatermarkFrameBackground.SOURCE_VIVID_BLUR
)

private fun defaultWatermarkFrameBackground(templateId: String): WatermarkFrameBackground {
    return when (templateId) {
        TEMPLATE_TRAVEL_POLAROID -> WatermarkFrameBackground.WHITE
        TEMPLATE_RETRO_FRAME -> WatermarkFrameBackground.SOURCE_VIVID_BLUR
        TEMPLATE_BLUR_FOUR_BORDER -> WatermarkFrameBackground.SOURCE_LIGHT_BLUR
        TEMPLATE_PROFESSIONAL_BOTTOM_BAR -> WatermarkFrameBackground.DARK
        else -> WatermarkFrameBackground.DARK
    }
}

private fun resolveWatermarkTitle(
    templateId: String,
    watermarkText: String,
    customTags: Map<String, String>,
    deviceModel: String
): String {
    val modeName = customTags[PHOTO_WATERMARK_MODE_NAME_KEY]
    val profileName = customTags[PHOTO_WATERMARK_PROFILE_NAME_KEY]
    if (modeName != null) {
        val title = buildString {
            append(deviceModel)
            append(" · ")
            append(modeName)
            profileName?.let {
                append(" ")
                append(it)
            }
        }
        return title
    }
    val normalizedText = watermarkText.trim().ifBlank { "OpenCamera" }
    return when {
        templateId == TEMPLATE_TRAVEL_POLAROID && normalizedText.startsWith("PHOTO ") -> "去有天空的地方"
        else -> normalizedText
    }
}

private fun resolveDeviceModel(
    customTags: Map<String, String>,
    preservedExif: Map<String, String>
): String {
    return customTags[PHOTO_WATERMARK_MODEL_KEY]
        ?: preservedExif[ExifInterface.TAG_MODEL]
        ?: Build.MODEL
        ?: "OpenCamera"
}

private fun chunkWatermarkTokens(tokens: List<String>): List<String> {
    return tokens
        .filter { it.isNotBlank() }
        .chunked(2)
        .map { row -> row.joinToString(separator = " • ") }
}

private fun formatExifDateTime(value: String?): String? {
    return value
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.replaceFirst(":", "-")
        ?.replace(":", "-", ignoreCase = false)
}

private fun formatExifLocation(exif: Map<String, String>): String? {
    val latitude = exifCoordinateOrNull(
        coordinate = exif[ExifInterface.TAG_GPS_LATITUDE],
        reference = exif[ExifInterface.TAG_GPS_LATITUDE_REF]
    )
    val longitude = exifCoordinateOrNull(
        coordinate = exif[ExifInterface.TAG_GPS_LONGITUDE],
        reference = exif[ExifInterface.TAG_GPS_LONGITUDE_REF]
    )
    if (latitude == null || longitude == null) {
        return null
    }
    return String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
}

private fun exifCoordinateOrNull(
    coordinate: String?,
    reference: String?
): Double? {
    val raw = coordinate?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val parts = raw.split(",")
    if (parts.size != 3) {
        return null
    }
    val degrees = parts[0].trim().substringBefore('/').toDoubleOrNull() ?: return null
    val minutes = fractionToDouble(parts[1]) ?: return null
    val seconds = fractionToDouble(parts[2]) ?: return null
    val decimal = degrees + (minutes / 60.0) + (seconds / 3600.0)
    return when (reference?.trim()?.uppercase(Locale.US)) {
        "S", "W" -> -decimal
        else -> decimal
    }
}

private fun fractionToDouble(value: String): Double? {
    val normalized = value.trim()
    val parts = normalized.split('/')
    return when (parts.size) {
        1 -> parts[0].toDoubleOrNull()
        2 -> {
            val numerator = parts[0].toDoubleOrNull() ?: return null
            val denominator = parts[1].toDoubleOrNull()?.takeIf { it != 0.0 } ?: return null
            numerator / denominator
        }
        else -> null
    }
}

private fun formatCameraParams(exif: Map<String, String>): String? {
    val params = buildList {
        exif[ExifInterface.TAG_EXPOSURE_TIME]
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { add(it) }
        exif[ExifInterface.TAG_F_NUMBER]
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { add("f/$it") }
        exif[ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY]
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { add("ISO $it") }
    }
    return params.takeIf { it.isNotEmpty() }?.joinToString(separator = " • ")
}
