package com.opencamera.app.camera

import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaOutputHandle
import java.io.File

data class PhotoOutputRequest(
    val outputOptions: ImageCapture.OutputFileOptions,
    val outputPath: String,
    val outputHandle: MediaOutputHandle,
    val cleanupFile: File? = null
) {
    fun resolveOutputHandle(savedUri: Uri?): MediaOutputHandle {
        val uriString = savedUri
            ?.takeUnless { it == Uri.EMPTY }
            ?.toString()
        return resolvePhotoOutputHandle(outputHandle, uriString)
    }

    fun cleanupPaths(): List<String> {
        return buildList {
            cleanupFile?.absolutePath?.let(::add)
            outputHandle.filePath?.let(::add)
            outputPath.takeIf { File(it).isAbsolute }?.let(::add)
        }.distinct()
    }
}

sealed interface PhotoCaptureOutcome {
    data class Success(
        val outputPath: String,
        val outputHandle: MediaOutputHandle,
        val diagnostics: List<String> = emptyList(),
        val intermediateOutputPaths: List<String> = emptyList(),
        val livePhotoBundle: LivePhotoBundle? = null,
        val frameBundle: com.opencamera.core.media.FrameBundle? = null,
        val deviceCaptureStartedAtElapsedMillis: Long = 0L,
        val deviceCaptureCompletedAtElapsedMillis: Long = 0L
    ) : PhotoCaptureOutcome

    data class Failure(
        val reason: String,
        val cleanupPaths: List<String> = emptyList()
    ) : PhotoCaptureOutcome
}

sealed interface VideoOutputRequest {
    val outputPath: String
    val outputHandle: MediaOutputHandle

    data class MediaStoreRequest(
        val outputOptions: MediaStoreOutputOptions,
        override val outputPath: String,
        override val outputHandle: MediaOutputHandle
    ) : VideoOutputRequest

    data class FileRequest(
        val outputOptions: FileOutputOptions,
        override val outputPath: String,
        override val outputHandle: MediaOutputHandle
    ) : VideoOutputRequest
}

internal fun VideoOutputRequest.resolveOutputHandle(outputUri: Uri?): MediaOutputHandle {
    val resolvedContentUri = outputUri?.takeUnless { it == Uri.EMPTY }?.toString()
    return if (resolvedContentUri == null) {
        outputHandle
    } else {
        outputHandle.copy(contentUri = resolvedContentUri)
    }
}
