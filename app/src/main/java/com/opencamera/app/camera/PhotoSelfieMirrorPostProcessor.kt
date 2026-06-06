package com.opencamera.app.camera

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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

internal data class PhotoSelfieMirrorApplied(val warning: String? = null) : ProcessorEditorResult

internal interface PhotoSelfieMirrorEditor {
    suspend fun apply(target: ProcessorTarget): ProcessorEditorResult
}

internal fun decidePhotoSelfieMirrorWork(result: ShotResult): ProcessorWork<ProcessorTarget> {
    if (result.mediaType != MediaType.PHOTO) {
        return ProcessorWork.None
    }
    if (!result.saveRequest.mimeType.equals("image/jpeg", ignoreCase = true)) {
        return ProcessorWork.DiagnosticSkip("unsupported-mime")
    }
    if (!result.metadata.customTags["selfieMirrorApply"].toBoolean()) {
        return ProcessorWork.None
    }
    val target = result.outputHandle.toProcessorTargetOrNull()
        ?: return ProcessorWork.DiagnosticSkip("missing-output-handle")
    return ProcessorWork.Execute(target)
}

internal class PhotoSelfieMirrorPostProcessor(
    private val editor: PhotoSelfieMirrorEditor
) : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        return when (val work = decidePhotoSelfieMirrorWork(result)) {
            ProcessorWork.None -> result
            is ProcessorWork.DiagnosticSkip -> result.addPipelineNotes(
                "selfie-mirror:skipped:${work.reason}"
            )

            is ProcessorWork.Execute -> {
                when (val editorResult = editor.apply(work.payload)) {
                    is PhotoSelfieMirrorApplied -> {
                        if (editorResult.warning == null) {
                            result.addPipelineNotes("selfie-mirror:applied")
                        } else {
                            result.addPipelineNotes(
                                "selfie-mirror:applied",
                                "selfie-mirror:warning:${editorResult.warning}"
                            )
                        }
                    }

                    is ProcessorEditorResult.Skipped -> result.addPipelineNotes(
                        "selfie-mirror:skipped:${editorResult.reason}"
                    )

                    is ProcessorEditorResult.Failed -> result.addPipelineNotes(
                        "selfie-mirror:failed:${editorResult.reason}"
                    )

                    else -> result
                }
            }
        }
    }
}

internal class AndroidPhotoSelfieMirrorEditor(
    context: Context
) : PhotoSelfieMirrorEditor {
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
                val mirrored = Bitmap.createBitmap(
                    decoded,
                    0,
                    0,
                    decoded.width,
                    decoded.height,
                    Matrix().apply { preScale(-1f, 1f) },
                    true
                )
                val encodedBytes = ByteArrayOutputStream().use { output ->
                    check(mirrored.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                        "Selfie mirror JPEG compression failed"
                    }
                    output.toByteArray()
                }
                mirrored.recycle()
                if (!writeEncodedBytes(target, encodedBytes)) {
                    return@withContext ProcessorEditorResult.Failed("output-unavailable")
                }

                val exifWarning = restorePreservedExif(target, preservedExif)
                PhotoSelfieMirrorApplied(exifWarning)
            } catch (_: Throwable) {
                ProcessorEditorResult.Failed("mirror-exception")
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
                contentResolver.openOutputStream(Uri.parse(target.value), "rwt")?.use {
                    it.write(bytes)
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

        return runCatching {
            val exif = when (target) {
                is ProcessorTarget.FilePath -> ExifInterface(target.path)
                is ProcessorTarget.ContentUri -> {
                    contentResolver.openFileDescriptor(Uri.parse(target.value), "rw")?.use { descriptor ->
                        ExifInterface(descriptor.fileDescriptor)
                    } ?: return "exif-open-failed"
                }
            }
            preservedExif.forEach { (attribute, value) ->
                exif.setAttribute(attribute, value)
            }
            exif.saveAttributes()
            null
        }.getOrElse { "exif-save-failed" }
    }

    companion object {
        private const val JPEG_QUALITY = 94

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
