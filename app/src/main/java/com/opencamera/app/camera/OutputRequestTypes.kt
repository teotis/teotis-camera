package com.opencamera.app.camera

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
    fun resolveOutputHandle(
        savedUri: Uri?,
        contentResolver: ContentResolver? = null
    ): MediaOutputHandle {
        val uriString = savedUri
            ?.takeUnless { it == Uri.EMPTY }
            ?.toString()
            ?: contentResolver?.resolvePhotoMediaStoreUri(outputPath)
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

private fun ContentResolver.resolvePhotoMediaStoreUri(outputPath: String): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val displayName = outputPath.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return null
    val relativePath = outputPath.substringBeforeLast('/', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?: return null
    val relativePathWithSlash = if (relativePath.endsWith('/')) relativePath else "$relativePath/"
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val pathSelection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
        "(${MediaStore.MediaColumns.RELATIVE_PATH}=? OR ${MediaStore.MediaColumns.RELATIVE_PATH}=?)"
    val pathSelectionArgs = arrayOf(displayName, relativePath, relativePathWithSlash)
    val displayNameSelection = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
    val displayNameSelectionArgs = arrayOf(displayName)
    return queryPhotoMediaStoreUri(projection, pathSelection, pathSelectionArgs)
        ?: queryPhotoMediaStoreUri(projection, displayNameSelection, displayNameSelectionArgs)
}

private fun ContentResolver.queryPhotoMediaStoreUri(
    projection: Array<String>,
    selection: String,
    selectionArgs: Array<String>
): String? {
    return runCatching {
        query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media._ID} DESC"
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString()
        }
    }.getOrNull()
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
