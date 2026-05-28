package com.opencamera.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
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

internal suspend fun queryLatestGalleryImage(context: Context): ThumbnailSource.SavedMedia? {
    return withContext(Dispatchers.IO) {
        if (!hasReadMediaImagesPermission(context)) {
            return@withContext null
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    ThumbnailSource.SavedMedia(outputPath = path, renderUri = uri.toString())
                } else null
            }
        }.getOrNull()
    }
}

internal suspend fun queryLatestGalleryMedia(context: Context): LatestGalleryMedia? {
    return withContext(Dispatchers.IO) {
        val imageCandidate = queryLatestMedia(
            context,
            uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            idColumn = MediaStore.Images.Media._ID,
            dataColumn = MediaStore.Images.Media.DATA,
            dateColumn = MediaStore.Images.Media.DATE_ADDED,
            readPermission = Manifest.permission.READ_MEDIA_IMAGES
        )
        val videoCandidate = queryLatestMedia(
            context,
            uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            idColumn = MediaStore.Video.Media._ID,
            dataColumn = MediaStore.Video.Media.DATA,
            dateColumn = MediaStore.Video.Media.DATE_ADDED,
            readPermission = Manifest.permission.READ_MEDIA_VIDEO
        )

        val candidates = listOfNotNull(
            imageCandidate?.let { it to SavedMediaType.PHOTO },
            videoCandidate?.let { it to SavedMediaType.VIDEO }
        )
        if (candidates.isEmpty()) return@withContext null

        val (mediaColumns, dateColumn) = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
        ) to MediaStore.Images.Media.DATE_ADDED

        // Both queries already sort by DATE_ADDED DESC and return the first row,
        // so each candidate is the newest of its type. Compare timestamps.
        // We re-query to get the actual date for comparison.
        val imageDate = imageCandidate?.let { getLatestDate(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI) } ?: 0L
        val videoDate = videoCandidate?.let { getLatestDate(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI) } ?: 0L

        if (videoDate >= imageDate && videoCandidate != null) {
            LatestGalleryMedia(videoCandidate, SavedMediaType.VIDEO)
        } else if (imageCandidate != null) {
            LatestGalleryMedia(imageCandidate, SavedMediaType.PHOTO)
        } else {
            null
        }
    }
}

private suspend fun queryLatestMedia(
    context: Context,
    uri: android.net.Uri,
    idColumn: String,
    dataColumn: String,
    dateColumn: String,
    readPermission: String
): ThumbnailSource.SavedMedia? {
    if (!hasPermission(context, readPermission)) return null

    val projection = arrayOf(idColumn, dataColumn, dateColumn)
    val sortOrder = "$dateColumn DESC"

    return runCatching {
        context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(idColumn))
                val path = cursor.getString(cursor.getColumnIndexOrThrow(dataColumn))
                val contentUri = ContentUris.withAppendedId(uri, id)
                ThumbnailSource.SavedMedia(outputPath = path, renderUri = contentUri.toString())
            } else null
        }
    }.getOrNull()
}

private suspend fun getLatestDate(context: Context, uri: android.net.Uri): Long {
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.DATE_ADDED),
            null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun hasReadMediaImagesPermission(context: Context): Boolean {
    return hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
}
