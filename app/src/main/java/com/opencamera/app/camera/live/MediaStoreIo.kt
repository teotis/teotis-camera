package com.opencamera.app.camera.live

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

/**
 * Narrow seam over ContentResolver for MediaStore photo/video sidecar IO.
 *
 * Production wiring uses [ContentResolverMediaStoreIo]; tests inject a
 * fault-injecting implementation to exercise storage-full, write-exception and
 * pending-row rollback paths deterministically (Capture Reliability Closure).
 */
interface MediaStoreIo {
    fun insert(collection: Uri, values: ContentValues): Uri?

    /** @param mode null means "w" (append-friendly default, mirrors the single-arg ContentResolver call). */
    fun openOutputStream(uri: Uri, mode: String? = null): OutputStream?
    fun openInputStream(uri: Uri): InputStream?
    fun update(uri: Uri, values: ContentValues, selection: String?, selectionArgs: Array<String>?): Int
    fun delete(uri: Uri): Int
    fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): android.database.Cursor?
    fun notifyChange(uri: Uri)
}

class ContentResolverMediaStoreIo(
    private val contentResolver: ContentResolver
) : MediaStoreIo {
    override fun insert(collection: Uri, values: ContentValues): Uri? =
        contentResolver.insert(collection, values)

    override fun openOutputStream(uri: Uri, mode: String?): OutputStream? =
        // Single-arg form keeps the historical "w" semantics for callers that
        // pass null and behaves identically on shadowed content resolvers.
        if (mode == null) contentResolver.openOutputStream(uri)
        else contentResolver.openOutputStream(uri, mode)

    override fun openInputStream(uri: Uri): InputStream? =
        contentResolver.openInputStream(uri)

    override fun update(
        uri: Uri,
        values: ContentValues,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = contentResolver.update(uri, values, selection, selectionArgs)

    override fun delete(uri: Uri): Int = contentResolver.delete(uri, null, null)

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): android.database.Cursor? = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)

    override fun notifyChange(uri: Uri) {
        contentResolver.notifyChange(uri, null)
    }
}
