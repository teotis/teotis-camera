package com.opencamera.app

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.opencamera.core.media.ThumbnailSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun queryLatestGalleryImage(context: Context): ThumbnailSource.SavedMedia? {
    return withContext(Dispatchers.IO) {
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
