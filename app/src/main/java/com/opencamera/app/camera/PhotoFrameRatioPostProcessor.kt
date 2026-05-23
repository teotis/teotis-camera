package com.opencamera.app.camera

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.opencamera.core.media.FrameRatio
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
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class PhotoFrameRatioPayload(
    val target: ProcessorTarget,
    val frameRatio: FrameRatio
)

internal data class PhotoFrameRatioApplied(
    val frameRatio: FrameRatio,
    val cropBounds: CropBounds,
    val warning: String? = null
) : ProcessorEditorResult

internal interface PhotoFrameRatioEditor {
    suspend fun apply(
        target: ProcessorTarget,
        frameRatio: FrameRatio
    ): ProcessorEditorResult
}

internal data class CropBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun width(): Int = right - left
    fun height(): Int = bottom - top
}

internal fun decidePhotoFrameRatioWork(result: ShotResult): ProcessorWork<PhotoFrameRatioPayload> {
    if (result.mediaType != MediaType.PHOTO) {
        return ProcessorWork.None
    }
    if (!result.saveRequest.mimeType.equals("image/jpeg", ignoreCase = true)) {
        return ProcessorWork.DiagnosticSkip("unsupported-mime")
    }
    val frameRatio = FrameRatio.fromTag(result.metadata.customTags["frameRatio"])
        ?: return if (result.metadata.customTags.containsKey("frameRatio")) {
            ProcessorWork.DiagnosticSkip("unsupported-frame-ratio")
        } else {
            ProcessorWork.None
        }
    val target = result.outputHandle.toProcessorTargetOrNull()
        ?: return ProcessorWork.DiagnosticSkip("missing-output-handle")

    return ProcessorWork.Execute(PhotoFrameRatioPayload(target, frameRatio))
}

internal fun computeCenterCropBounds(
    width: Int,
    height: Int,
    frameRatio: FrameRatio
): CropBounds? {
    if (width <= 1 || height <= 1) {
        return null
    }

    val targetRatio = orientedFrameRatioValue(
        width = width,
        height = height,
        frameRatio = frameRatio
    )
    val currentRatio = width.toDouble() / height.toDouble()
    if (abs(currentRatio - targetRatio) <= RATIO_TOLERANCE) {
        return null
    }

    return if (currentRatio > targetRatio) {
        val croppedWidth = (height * targetRatio).roundToInt().coerceIn(1, width)
        if (croppedWidth >= width) {
            null
        } else {
            val left = ((width - croppedWidth) / 2).coerceAtLeast(0)
            CropBounds(left, 0, left + croppedWidth, height)
        }
    } else {
        val croppedHeight = (width / targetRatio).roundToInt().coerceIn(1, height)
        if (croppedHeight >= height) {
            null
        } else {
            val top = ((height - croppedHeight) / 2).coerceAtLeast(0)
            CropBounds(0, top, width, top + croppedHeight)
        }
    }
}

private fun orientedFrameRatioValue(
    width: Int,
    height: Int,
    frameRatio: FrameRatio
): Double {
    val narrowSide = minOf(frameRatio.width, frameRatio.height).toDouble()
    val wideSide = maxOf(frameRatio.width, frameRatio.height).toDouble()
    return if (width <= height) {
        narrowSide / wideSide
    } else {
        wideSide / narrowSide
    }
}

internal class PhotoFrameRatioPostProcessor(
    private val editor: PhotoFrameRatioEditor
) : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        return when (val work = decidePhotoFrameRatioWork(result)) {
            ProcessorWork.None -> result
            is ProcessorWork.DiagnosticSkip -> result.addPipelineNotes(
                "frame-ratio:skipped:${work.reason}"
            )

            is ProcessorWork.Execute -> {
                val payload = work.payload
                when (val cropResult = editor.apply(payload.target, payload.frameRatio)) {
                    is PhotoFrameRatioApplied -> {
                        val bounds = cropResult.cropBounds
                        val notes = buildList {
                            add("frame-ratio:applied:${cropResult.frameRatio.tagValue}")
                            add("frame-ratio:bounds=${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}")
                            cropResult.warning?.let { add("frame-ratio:warning:$it") }
                        }
                        result.addPipelineNotes(*notes.toTypedArray())
                    }

                    is ProcessorEditorResult.Skipped -> result.addPipelineNotes(
                        "frame-ratio:skipped:${cropResult.reason}"
                    )

                    is ProcessorEditorResult.Failed -> result.addPipelineNotes(
                        "frame-ratio:failed:${cropResult.reason}"
                    )

                    else -> result
                }
            }
        }
    }
}

internal class AndroidPhotoFrameRatioEditor(
    context: Context
) : PhotoFrameRatioEditor {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun apply(
        target: ProcessorTarget,
        frameRatio: FrameRatio
    ): ProcessorEditorResult = withContext(Dispatchers.IO) {
        val sourceBytes = readSourceBytes(target)
            ?: return@withContext ProcessorEditorResult.Skipped("input-unavailable")
        if (sourceBytes.isEmpty()) {
            return@withContext ProcessorEditorResult.Skipped("empty-source")
        }

        val preservedExif = readPreservedExif(sourceBytes)
        val decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
            ?: return@withContext ProcessorEditorResult.Failed("decode-failed")

        try {
            val cropBounds = computeCenterCropBounds(
                width = decoded.width,
                height = decoded.height,
                frameRatio = frameRatio
            ) ?: return@withContext ProcessorEditorResult.Skipped("already-matched")

            val cropped = Bitmap.createBitmap(
                decoded,
                cropBounds.left,
                cropBounds.top,
                cropBounds.width(),
                cropBounds.height()
            )
            val encodedBytes = ByteArrayOutputStream().use { output ->
                check(cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "Frame ratio JPEG compression failed"
                }
                output.toByteArray()
            }
            cropped.recycle()
            if (!writeEncodedBytes(target, encodedBytes)) {
                return@withContext ProcessorEditorResult.Failed("output-unavailable")
            }

            val exifWarning = restorePreservedExif(target, preservedExif)
            PhotoFrameRatioApplied(
                frameRatio = frameRatio,
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

    private fun writeEncodedBytes(target: ProcessorTarget, bytes: ByteArray): Boolean {
        return when (target) {
            is ProcessorTarget.FilePath -> runCatching {
                File(target.path).outputStream().use { it.write(bytes) }
            }.isSuccess

            is ProcessorTarget.ContentUri -> {
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
                        preservedExif.forEach { (tag, value) -> setAttribute(tag, value) }
                        saveAttributes()
                    }
                }

                is ProcessorTarget.ContentUri -> {
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

private const val RATIO_TOLERANCE = 0.01
