package com.opencamera.app.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import com.opencamera.core.media.LiveBundleStatus
import com.opencamera.core.media.LiveMotionSource
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.LivePhotoCaptureSpec
import com.opencamera.core.media.LiveTemporalWindow
import com.opencamera.core.media.LiveWatermarkResult
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.SaveRequest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureOutputFactory(
    private val context: Context
) {
    fun createPhotoOutputRequest(saveRequest: SaveRequest): PhotoOutputRequest {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val displayName = saveRequest.buildDisplayName(stamp)
        val outputPath = "${saveRequest.relativePath}/$displayName"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, saveRequest.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, saveRequest.relativePath)
            }
            PhotoOutputRequest(
                outputOptions = ImageCapture.OutputFileOptions.Builder(
                    context.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                ).build(),
                outputPath = outputPath,
                outputHandle = MediaOutputHandle(displayPath = outputPath)
            )
        } else {
            val outputDir = buildLegacyOutputDirectory(saveRequest).apply { mkdirs() }
            val outputFile = File(outputDir, displayName)
            PhotoOutputRequest(
                outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build(),
                outputPath = outputFile.absolutePath,
                outputHandle = MediaOutputHandle(
                    displayPath = outputFile.absolutePath,
                    filePath = outputFile.absolutePath
                )
            )
        }
    }

    fun createVideoOutputRequest(saveRequest: SaveRequest): VideoOutputRequest {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val displayName = saveRequest.buildDisplayName(stamp)
        val outputPath = "${saveRequest.relativePath}/$displayName"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, saveRequest.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, saveRequest.relativePath)
            }
            VideoOutputRequest.MediaStoreRequest(
                outputOptions = MediaStoreOutputOptions.Builder(
                    context.contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ).setContentValues(values).build(),
                outputPath = outputPath,
                outputHandle = MediaOutputHandle(displayPath = outputPath)
            )
        } else {
            val outputDir = buildLegacyOutputDirectory(saveRequest).apply { mkdirs() }
            val outputFile = File(outputDir, displayName)
            VideoOutputRequest.FileRequest(
                outputOptions = FileOutputOptions.Builder(outputFile).build(),
                outputPath = outputFile.absolutePath,
                outputHandle = MediaOutputHandle(
                    displayPath = outputFile.absolutePath,
                    filePath = outputFile.absolutePath
                )
            )
        }
    }

    fun createTemporaryPhotoOutputRequest(
        shotId: String,
        frameIndex: Int
    ): PhotoOutputRequest {
        val tempDir = File(context.cacheDir, "multi-frame-captures").apply {
            mkdirs()
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val outputFile = File(
            tempDir,
            "${shotId}_frame_${frameIndex}_$stamp.jpg"
        )
        return PhotoOutputRequest(
            outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build(),
            outputPath = outputFile.absolutePath,
            outputHandle = MediaOutputHandle(
                displayPath = outputFile.absolutePath,
                filePath = outputFile.absolutePath
            ),
            cleanupFile = outputFile
        )
    }

    fun createLivePhotoBundle(
        stillPath: String,
        stillOutputHandle: MediaOutputHandle,
        relativePath: String,
        livePhotoSpec: LivePhotoCaptureSpec? = null,
        bundleStatus: LiveBundleStatus = LiveBundleStatus.COMPLETE,
        temporalWindow: LiveTemporalWindow? = null,
        watermarkRequested: String? = null,
        watermarkResult: LiveWatermarkResult? = null,
        watermarkDegradeReason: String? = null
    ): LivePhotoBundle {
        val resolvedSpec = livePhotoSpec ?: LivePhotoCaptureSpec()
        val baseName = stillPath
            .substringAfterLast('/')
            .substringBeforeLast('.', missingDelimiterValue = stillPath.substringAfterLast('/'))
        val liveRelativeDir = relativePath.trimEnd('/')
        val isAbsoluteStillPath = File(stillPath).isAbsolute
        val basePath = stillPath.substringBeforeLast('.', missingDelimiterValue = stillPath)
        val motionPath = if (isAbsoluteStillPath) {
            "$basePath.live.mp4"
        } else {
            "$liveRelativeDir/$baseName.live.mp4"
        }
        val sidecarPath = if (isAbsoluteStillPath) {
            "$basePath.live.json"
        } else {
            "$liveRelativeDir/$baseName.live.json"
        }
        val motionHandle = if (isAbsoluteStillPath) {
            MediaOutputHandle(
                displayPath = motionPath,
                filePath = motionPath
            )
        } else {
            MediaOutputHandle(displayPath = motionPath)
        }
        val sidecarHandle = if (isAbsoluteStillPath) {
            MediaOutputHandle(
                displayPath = sidecarPath,
                filePath = sidecarPath
            )
        } else {
            createLivePhotoSidecarHandle(
                displayName = "$baseName.live.json",
                mimeType = resolvedSpec.sidecarMimeType,
                relativePath = liveRelativeDir,
                fallbackParentDir = context.getExternalFilesDir(null)
                    ?: context.filesDir
            )
        }
        return LivePhotoBundle(
            stillPath = stillPath,
            motionPath = motionPath,
            sidecarPath = sidecarPath,
            thumbnailPath = stillPath,
            motionDurationMillis = resolvedSpec.motionDurationMillis,
            motionMimeType = resolvedSpec.motionMimeType,
            sidecarMimeType = resolvedSpec.sidecarMimeType,
            motionHandle = motionHandle,
            sidecarHandle = sidecarHandle,
            thumbnailHandle = stillOutputHandle,
            bundleStatus = bundleStatus,
            temporalWindow = temporalWindow,
            watermarkRequested = watermarkRequested,
            watermarkResult = watermarkResult,
            watermarkDegradeReason = watermarkDegradeReason
        )
    }

    fun createMediaStoreFileHandle(
        displayName: String,
        mimeType: String,
        relativePath: String
    ): MediaOutputHandle {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        val contentUri = checkNotNull(
            context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                values
            )
        ) {
            "Failed to create MediaStore companion asset for $displayName"
        }
        return MediaOutputHandle(
            displayPath = "$relativePath/$displayName",
            contentUri = contentUri.toString()
        )
    }

    internal fun createLivePhotoSidecarHandle(
        displayName: String,
        mimeType: String,
        relativePath: String,
        fallbackParentDir: File
    ): MediaOutputHandle {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val contentUri = context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                values
            )
            if (contentUri != null) {
                return MediaOutputHandle(
                    displayPath = "$relativePath/$displayName",
                    contentUri = contentUri.toString()
                )
            }
        }
        val fallbackFile = File(fallbackParentDir, displayName)
        return MediaOutputHandle(
            displayPath = "$relativePath/$displayName",
            filePath = fallbackFile.absolutePath
        )
    }

    private fun buildLegacyOutputDirectory(saveRequest: SaveRequest): File {
        val root = saveRequest.relativePath.substringBefore("/")
        val nested = saveRequest.relativePath.substringAfter("/", "")
        val baseDir = context.getExternalFilesDir(root) ?: context.filesDir
        return if (nested.isEmpty()) {
            baseDir
        } else {
            File(baseDir, nested)
        }
    }
}
