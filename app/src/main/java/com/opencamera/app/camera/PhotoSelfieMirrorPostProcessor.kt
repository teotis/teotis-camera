package com.opencamera.app.camera

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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

internal sealed interface PhotoSelfieMirrorWork {
    data object None : PhotoSelfieMirrorWork
    data class Mirror(
        val target: PhotoSelfieMirrorTarget
    ) : PhotoSelfieMirrorWork

    data class DiagnosticSkip(val reason: String) : PhotoSelfieMirrorWork
}

internal sealed interface PhotoSelfieMirrorTarget {
    data class FilePath(val path: String) : PhotoSelfieMirrorTarget
    data class ContentUri(val value: String) : PhotoSelfieMirrorTarget
}

internal sealed interface PhotoSelfieMirrorEditorResult {
    data class Applied(val warning: String? = null) : PhotoSelfieMirrorEditorResult
    data class Skipped(val reason: String) : PhotoSelfieMirrorEditorResult
    data class Failed(val reason: String) : PhotoSelfieMirrorEditorResult
}

internal interface PhotoSelfieMirrorEditor {
    suspend fun apply(target: PhotoSelfieMirrorTarget): PhotoSelfieMirrorEditorResult
}

internal fun decidePhotoSelfieMirrorWork(result: ShotResult): PhotoSelfieMirrorWork {
    if (result.mediaType != MediaType.PHOTO) {
        return PhotoSelfieMirrorWork.None
    }
    if (!result.saveRequest.mimeType.equals("image/jpeg", ignoreCase = true)) {
        return PhotoSelfieMirrorWork.DiagnosticSkip("unsupported-mime")
    }
    if (!result.metadata.customTags["selfieMirrorApply"].toBoolean()) {
        return PhotoSelfieMirrorWork.None
    }
    val target = result.outputHandle.toPhotoSelfieMirrorTargetOrNull()
        ?: return PhotoSelfieMirrorWork.DiagnosticSkip("missing-output-handle")
    return PhotoSelfieMirrorWork.Mirror(target)
}

internal class PhotoSelfieMirrorPostProcessor(
    private val editor: PhotoSelfieMirrorEditor
) : MediaPostProcessor {
    override suspend fun process(result: ShotResult): ShotResult {
        return when (val work = decidePhotoSelfieMirrorWork(result)) {
            PhotoSelfieMirrorWork.None -> result
            is PhotoSelfieMirrorWork.DiagnosticSkip -> result.withPipelineNotes(
                "selfie-mirror:skipped:${work.reason}"
            )

            is PhotoSelfieMirrorWork.Mirror -> {
                when (val editorResult = editor.apply(work.target)) {
                    is PhotoSelfieMirrorEditorResult.Applied -> {
                        if (editorResult.warning == null) {
                            result.withPipelineNotes("selfie-mirror:applied")
                        } else {
                            result.withPipelineNotes(
                                "selfie-mirror:applied",
                                "selfie-mirror:warning:${editorResult.warning}"
                            )
                        }
                    }

                    is PhotoSelfieMirrorEditorResult.Skipped -> result.withPipelineNotes(
                        "selfie-mirror:skipped:${editorResult.reason}"
                    )

                    is PhotoSelfieMirrorEditorResult.Failed -> result.withPipelineNotes(
                        "selfie-mirror:failed:${editorResult.reason}"
                    )
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

    override suspend fun apply(target: PhotoSelfieMirrorTarget): PhotoSelfieMirrorEditorResult =
        withContext(Dispatchers.IO) {
            val sourceBytes = readSourceBytes(target)
                ?: return@withContext PhotoSelfieMirrorEditorResult.Skipped("input-unavailable")
            if (sourceBytes.isEmpty()) {
                return@withContext PhotoSelfieMirrorEditorResult.Skipped("empty-source")
            }

            val preservedExif = readPreservedExif(sourceBytes)
            val decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
                ?: return@withContext PhotoSelfieMirrorEditorResult.Failed("decode-failed")

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
                    return@withContext PhotoSelfieMirrorEditorResult.Failed("output-unavailable")
                }

                val exifWarning = restorePreservedExif(target, preservedExif)
                PhotoSelfieMirrorEditorResult.Applied(exifWarning)
            } catch (_: Throwable) {
                PhotoSelfieMirrorEditorResult.Failed("mirror-exception")
            } finally {
                decoded.recycle()
            }
        }

    private fun readSourceBytes(target: PhotoSelfieMirrorTarget): ByteArray? {
        return when (target) {
            is PhotoSelfieMirrorTarget.FilePath -> {
                val file = File(target.path)
                if (!file.exists()) null else file.readBytes()
            }

            is PhotoSelfieMirrorTarget.ContentUri -> {
                contentResolver.openInputStream(Uri.parse(target.value))?.use { it.readBytes() }
            }
        }
    }

    private fun writeEncodedBytes(target: PhotoSelfieMirrorTarget, bytes: ByteArray): Boolean {
        return when (target) {
            is PhotoSelfieMirrorTarget.FilePath -> runCatching {
                File(target.path).outputStream().use { it.write(bytes) }
            }.isSuccess

            is PhotoSelfieMirrorTarget.ContentUri -> {
                contentResolver.openOutputStream(Uri.parse(target.value), "rwt")?.use {
                    it.write(bytes)
                } != null
            }
        }
    }

    private fun readPreservedExif(sourceBytes: ByteArray): Map<String, String> {
        return runCatching {
            val exif = ExifInterface(ByteArrayInputStream(sourceBytes))
            EXIF_ATTRIBUTES.mapNotNull { attribute ->
                exif.getAttribute(attribute)?.let { attribute to it }
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun restorePreservedExif(
        target: PhotoSelfieMirrorTarget,
        preservedExif: Map<String, String>
    ): String? {
        if (preservedExif.isEmpty()) {
            return null
        }

        return runCatching {
            val exif = when (target) {
                is PhotoSelfieMirrorTarget.FilePath -> ExifInterface(target.path)
                is PhotoSelfieMirrorTarget.ContentUri -> {
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
        private val EXIF_ATTRIBUTES = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_IMAGE_LENGTH
        )
    }
}

private fun MediaOutputHandle.toPhotoSelfieMirrorTargetOrNull(): PhotoSelfieMirrorTarget? {
    return when {
        filePath != null -> filePath?.let(PhotoSelfieMirrorTarget::FilePath)
        contentUri != null -> contentUri?.let(PhotoSelfieMirrorTarget::ContentUri)
        else -> null
    }
}

private fun ShotResult.withPipelineNotes(vararg notes: String): ShotResult {
    return copy(pipelineNotes = pipelineNotes + notes)
}
