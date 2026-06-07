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
                if (!contentResolver.writeEncodedBytes(target, encodedBytes)) {
                    return@withContext ProcessorEditorResult.Failed("output-unavailable")
                }

                val exifWarning = contentResolver.restorePreservedExif(target, preservedExif)
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

    companion object {
        private const val JPEG_QUALITY = 94
    }
}
