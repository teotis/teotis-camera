package com.opencamera.app.camera

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
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

private const val TAG = "PhotoFrameRatioPP"

internal data class PhotoFrameRatioPayload(
    val target: ProcessorTarget,
    val frameRatio: FrameRatio,
    val captureCropZoom: Float = 1f
)

internal data class PhotoFrameRatioApplied(
    val frameRatio: FrameRatio,
    val cropBounds: CropBounds,
    val warning: String? = null
) : ProcessorEditorResult

internal interface PhotoFrameRatioEditor {
    suspend fun apply(
        target: ProcessorTarget,
        frameRatio: FrameRatio,
        captureCropZoom: Float = 1f
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
    when (result.photoJpegInput()) {
        PhotoJpegInput.NOT_PHOTO -> return ProcessorWork.None
        PhotoJpegInput.UNSUPPORTED_MIME -> return ProcessorWork.DiagnosticSkip("unsupported-mime")
        PhotoJpegInput.EDITABLE -> Unit
    }
    val frameRatio = FrameRatio.fromTag(result.metadata.customTags["frameRatio"])
        ?: return if (result.metadata.customTags.containsKey("frameRatio")) {
            ProcessorWork.DiagnosticSkip("unsupported-frame-ratio")
        } else {
            ProcessorWork.None
        }
    val target = result.outputHandle.toProcessorTargetOrNull()
        ?: return ProcessorWork.DiagnosticSkip("missing-output-handle")

    val captureCropZoom = result.metadata.customTags["captureCropZoom"]?.toFloatOrNull() ?: 1f

    return ProcessorWork.Execute(PhotoFrameRatioPayload(target, frameRatio, captureCropZoom))
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

internal fun computeZoomCropBounds(
    width: Int,
    height: Int,
    zoomRatio: Float
): CropBounds? {
    if (zoomRatio <= 1f || width <= 1 || height <= 1) {
        return null
    }
    val scale = 1f / zoomRatio
    val croppedWidth = (width * scale).roundToInt().coerceIn(1, width)
    val croppedHeight = (height * scale).roundToInt().coerceIn(1, height)
    if (croppedWidth >= width && croppedHeight >= height) {
        return null
    }
    val left = ((width - croppedWidth) / 2).coerceAtLeast(0)
    val top = ((height - croppedHeight) / 2).coerceAtLeast(0)
    return CropBounds(left, top, left + croppedWidth, top + croppedHeight)
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
    override fun isApplicable(result: ShotResult): Boolean = result.mediaType == MediaType.PHOTO

    override suspend fun process(result: ShotResult): ShotResult {
        return when (val work = decidePhotoFrameRatioWork(result)) {
            ProcessorWork.None -> result
            is ProcessorWork.DiagnosticSkip -> result.addPipelineNotes(
                "frame-ratio:skipped:${work.reason}"
            )

            is ProcessorWork.Execute -> {
                val payload = work.payload
                when (val cropResult = editor.apply(payload.target, payload.frameRatio, payload.captureCropZoom)) {
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
        frameRatio: FrameRatio,
        captureCropZoom: Float
    ): ProcessorEditorResult = withContext(Dispatchers.IO) {
        val sourceBytes = ProcessorIOUtils.readSourceBytes(target, contentResolver)
            ?: return@withContext ProcessorEditorResult.Skipped("input-unavailable")
        if (sourceBytes.isEmpty()) {
            return@withContext ProcessorEditorResult.Skipped("empty-source")
        }

        val preservedExif = readPreservedExif(sourceBytes)
        val decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
            ?: return@withContext ProcessorEditorResult.Failed("decode-failed")

        try {
            val frameCropBounds = computeCenterCropBounds(
                width = decoded.width,
                height = decoded.height,
                frameRatio = frameRatio
            )
            val intermediate = if (frameCropBounds != null) {
                Bitmap.createBitmap(
                    decoded,
                    frameCropBounds.left,
                    frameCropBounds.top,
                    frameCropBounds.width(),
                    frameCropBounds.height()
                )
            } else {
                decoded
            }

            val zoomCropBounds = computeZoomCropBounds(
                width = intermediate.width,
                height = intermediate.height,
                zoomRatio = captureCropZoom
            )
            val finalBitmap = if (zoomCropBounds != null) {
                val cropped = Bitmap.createBitmap(
                    intermediate,
                    zoomCropBounds.left,
                    zoomCropBounds.top,
                    zoomCropBounds.width(),
                    zoomCropBounds.height()
                )
                if (intermediate !== decoded) intermediate.recycle()
                cropped
            } else {
                intermediate
            }

            if (finalBitmap === decoded && frameCropBounds == null) {
                return@withContext ProcessorEditorResult.Skipped("already-matched")
            }

            val encodedBytes = ByteArrayOutputStream().use { output ->
                check(finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "Frame ratio JPEG compression failed"
                }
                output.toByteArray()
            }
            if (finalBitmap !== decoded) finalBitmap.recycle()
            if (!contentResolver.writeEncodedBytes(target, encodedBytes)) {
                return@withContext ProcessorEditorResult.Failed("output-unavailable")
            }

            val exifWarning = contentResolver.restorePreservedExif(target, preservedExif)
            val combinedBounds = if (zoomCropBounds != null && frameCropBounds != null) {
                CropBounds(
                    left = frameCropBounds.left + zoomCropBounds.left,
                    top = frameCropBounds.top + zoomCropBounds.top,
                    right = frameCropBounds.left + zoomCropBounds.left + zoomCropBounds.width(),
                    bottom = frameCropBounds.top + zoomCropBounds.top + zoomCropBounds.height()
                )
            } else {
                frameCropBounds ?: zoomCropBounds
            }
            PhotoFrameRatioApplied(
                frameRatio = frameRatio,
                cropBounds = combinedBounds ?: CropBounds(0, 0, decoded.width, decoded.height),
                warning = exifWarning
            )
        } catch (e: Throwable) {
            e.rethrowIfCancellationOrFatal()
            Log.w(TAG, "frame ratio postprocess failed", e)
            ProcessorEditorResult.Failed("crop-exception")
        } finally {
            decoded.recycle()
        }
    }

    companion object {
        private const val JPEG_QUALITY = 92
    }
}

private const val RATIO_TOLERANCE = 0.01
