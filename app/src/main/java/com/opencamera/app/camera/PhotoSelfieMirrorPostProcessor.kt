package com.opencamera.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessFailure
import com.opencamera.core.media.PostProcessFailureCause
import com.opencamera.core.media.PostProcessFailureDisposition
import com.opencamera.core.media.PostProcessFailureStage
import com.opencamera.core.media.PostProcessOutputIntegrity
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.ProcessorWork
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.addPipelineNotes
import com.opencamera.core.media.addStructuredPostProcessFailure
import com.opencamera.core.media.toProcessorTargetOrNull

private const val TAG = "PhotoSelfieMirrorPP"

internal data class PhotoSelfieMirrorApplied(val warning: String? = null) : ProcessorEditorResult

internal interface PhotoSelfieMirrorEditor {
    suspend fun apply(target: ProcessorTarget): ProcessorEditorResult
}

internal fun decidePhotoSelfieMirrorWork(result: ShotResult): ProcessorWork<ProcessorTarget> {
    when (result.photoJpegInput()) {
        PhotoJpegInput.NOT_PHOTO -> return ProcessorWork.None
        PhotoJpegInput.UNSUPPORTED_MIME -> return ProcessorWork.DiagnosticSkip("unsupported-mime")
        PhotoJpegInput.EDITABLE -> Unit
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
    override fun isApplicable(result: ShotResult): Boolean = result.mediaType == MediaType.PHOTO

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

                    is ProcessorEditorResult.Failed -> {
                        val (cause, integrity) = selfieMirrorFailureMapping(editorResult.reason)
                        val structuredFailure = PostProcessFailure(
                            stage = PostProcessFailureStage.SELFIE_MIRROR,
                            cause = cause,
                            integrity = integrity,
                            disposition = PostProcessFailureDisposition.RECOVERABLE
                        )
                        result.addPipelineNotes("selfie-mirror:failed:${editorResult.reason}")
                            .addStructuredPostProcessFailure(structuredFailure)
                    }

                    else -> result
                }
            }
        }
    }
}

internal fun selfieMirrorFailureMapping(reason: String): Pair<PostProcessFailureCause, PostProcessOutputIntegrity> =
    when (reason) {
        "decode-failed" -> PostProcessFailureCause.DECODE_FAILED to PostProcessOutputIntegrity.ORIGINAL_INTACT
        "mirror-exception" -> PostProcessFailureCause.BITMAP_OPERATION to PostProcessOutputIntegrity.ORIGINAL_INTACT
        "output-unavailable" -> PostProcessFailureCause.OUTPUT_UNAVAILABLE to PostProcessOutputIntegrity.POSSIBLY_MODIFIED
        else -> PostProcessFailureCause.EXCEPTION to PostProcessOutputIntegrity.ORIGINAL_INTACT
    }

internal class AndroidPhotoSelfieMirrorEditor(
    context: Context
) : PhotoSelfieMirrorEditor {
    private val appContext = context.applicationContext
    private val ioExifProcessor = BitmapIoExifProcessor(
        contentResolver = appContext.contentResolver,
        jpegQuality = JPEG_QUALITY
    )

    override suspend fun apply(target: ProcessorTarget): ProcessorEditorResult =
        try {
            when (val result = ioExifProcessor.process(target) { decoded ->
                Bitmap.createBitmap(
                    decoded,
                    0,
                    0,
                    decoded.width,
                    decoded.height,
                    Matrix().apply { preScale(-1f, 1f) },
                    true
                )
            }) {
                is BitmapIoExifResult.Success -> PhotoSelfieMirrorApplied(result.exifWarning)
                is BitmapIoExifResult.Skipped -> ProcessorEditorResult.Skipped(result.reason)
                is BitmapIoExifResult.Failed -> ProcessorEditorResult.Failed(result.reason)
            }
        } catch (e: Throwable) {
            e.rethrowIfCancellationOrFatal()
            Log.w(TAG, "selfie mirror postprocess failed", e)
            ProcessorEditorResult.Failed("mirror-exception")
        }

    companion object {
        private const val JPEG_QUALITY = 94
    }
}
