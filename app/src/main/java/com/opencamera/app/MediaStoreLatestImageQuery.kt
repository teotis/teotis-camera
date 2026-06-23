package com.opencamera.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.session.SavedMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class LatestGalleryMedia(
    val source: ThumbnailSource.SavedMedia,
    val mediaType: SavedMediaType
)

internal enum class LatestGalleryPhotoSource {
    APP_OUTPUT,
    SYSTEM_GALLERY
}

internal data class LatestGalleryPhotoCandidate(
    val outputPath: String,
    val renderUri: String,
    val dateAdded: Long,
    val source: LatestGalleryPhotoSource
) {
    fun toThumbnailSource(): ThumbnailSource.SavedMedia =
        ThumbnailSource.SavedMedia(outputPath = outputPath, renderUri = renderUri)
}

internal data class LatestGalleryPhotoQueryPlan(
    val queryAppOutputPhotos: Boolean,
    val querySystemGalleryPhotos: Boolean
)

internal fun latestGalleryPhotoQueryPlan(hasGalleryReadPermission: Boolean): LatestGalleryPhotoQueryPlan =
    LatestGalleryPhotoQueryPlan(
        queryAppOutputPhotos = true,
        querySystemGalleryPhotos = hasGalleryReadPermission
    )

internal fun selectLatestGalleryPhoto(
    appOutput: LatestGalleryPhotoCandidate?,
    systemGallery: LatestGalleryPhotoCandidate?
): LatestGalleryPhotoCandidate? {
    return appOutput ?: systemGallery
}

internal suspend fun queryLatestGalleryImage(context: Context): ThumbnailSource.SavedMedia? {
    return withContext(Dispatchers.IO) {
        val plan = latestGalleryPhotoQueryPlan(
            hasGalleryReadPermission = hasReadMediaImagesPermission(context)
        )
        val appOutput = if (plan.queryAppOutputPhotos) {
            queryLatestPhotoCandidate(context, LatestGalleryPhotoSource.APP_OUTPUT)
        } else {
            null
        }
        val systemGallery = if (plan.querySystemGalleryPhotos) {
            queryLatestPhotoCandidate(context, LatestGalleryPhotoSource.SYSTEM_GALLERY)
        } else {
            null
        }
        selectLatestGalleryPhoto(appOutput, systemGallery)?.toThumbnailSource()
    }
}

internal suspend fun queryLatestGalleryMedia(context: Context): LatestGalleryMedia? {
    return queryLatestGalleryImage(context)?.let { source ->
        LatestGalleryMedia(source = source, mediaType = SavedMediaType.PHOTO)
    }
}

private fun queryLatestPhotoCandidate(
    context: Context,
    source: LatestGalleryPhotoSource
): LatestGalleryPhotoCandidate? {
    val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = latestPhotoProjection()
    val queryScope = latestPhotoQueryScope(source)

    return runCatching {
        context.contentResolver.query(
            uri,
            projection,
            queryScope.selection,
            queryScope.selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.toLatestGalleryPhotoCandidate(uri, source)
            } else {
                null
            }
        }
    }.getOrNull()
}

private data class LatestPhotoQueryScope(
    val selection: String?,
    val selectionArgs: Array<String>?
)

private fun latestPhotoQueryScope(source: LatestGalleryPhotoSource): LatestPhotoQueryScope {
    if (source == LatestGalleryPhotoSource.SYSTEM_GALLERY || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return LatestPhotoQueryScope(selection = null, selectionArgs = null)
    }
    return LatestPhotoQueryScope(
        selection = "(${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?)",
        selectionArgs = arrayOf(APP_PHOTO_RELATIVE_PATH, "$APP_PHOTO_RELATIVE_PATH%")
    )
}

private fun latestPhotoProjection(): Array<String> {
    val columns = mutableListOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_ADDED
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        columns += MediaStore.Images.Media.RELATIVE_PATH
        columns += MediaStore.Images.Media.DISPLAY_NAME
    } else {
        columns += MediaStore.Images.Media.DATA
    }
    return columns.toTypedArray()
}

private fun Cursor.toLatestGalleryPhotoCandidate(
    collectionUri: Uri,
    source: LatestGalleryPhotoSource
): LatestGalleryPhotoCandidate? {
    val id = getLong(getColumnIndexOrThrow(MediaStore.Images.Media._ID))
    val dateAdded = getLong(getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
    val outputPath = readOutputPath() ?: return null
    val contentUri = ContentUris.withAppendedId(collectionUri, id)
    return LatestGalleryPhotoCandidate(
        outputPath = outputPath,
        renderUri = contentUri.toString(),
        dateAdded = dateAdded,
        source = source
    )
}

private fun Cursor.readOutputPath(): String? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val relativePath = readStringOrNull(MediaStore.Images.Media.RELATIVE_PATH)
        val displayName = readStringOrNull(MediaStore.Images.Media.DISPLAY_NAME)
        when {
            !relativePath.isNullOrBlank() && !displayName.isNullOrBlank() -> relativePath + displayName
            !displayName.isNullOrBlank() -> displayName
            else -> null
        }
    } else {
        readStringOrNull(MediaStore.Images.Media.DATA)
    }
}

private fun Cursor.readStringOrNull(columnName: String): String? {
    val index = getColumnIndex(columnName)
    return if (index >= 0) getString(index) else null
}

private fun hasReadMediaImagesPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private const val APP_PHOTO_RELATIVE_PATH = "Pictures/OpenCamera/"
