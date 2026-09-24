package com.opencamera.app.camera.live

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * MediaStore fault injection for the Capture Reliability Closure.
 *
 * Exercises the real [LivePhotoMediaStoreWriter] code path against a
 * deterministic in-memory MediaStore model: storage-full insert failures,
 * write exceptions and pending-clear failures must roll back pending rows
 * (INV-2e / INV-4d: MediaStore pending rows always converge).
 */
@RunWith(RobolectricTestRunner::class)
class LivePhotoMediaStoreWriterFailureTest {

    @Test
    fun `normal sidecar insert commits pending row`() {
        val io = FaultInjectionMediaStoreIo()
        val writer = LivePhotoMediaStoreWriter(RuntimeEnvironment.getApplication(), io)

        val result = writer.insertMotionMp4Sidecar(
            jpegRelativePath = "DCIM/Camera",
            mp4DisplayNamePrefix = "OpenCamera_20260701_001",
            mp4Bytes = byteArrayOf(1, 2, 3, 4)
        )

        assertTrue(result.isSuccess)
        val uri = result.getOrThrow()
        assertEquals(1, io.rows.size)
        assertEquals(uri.toString(), io.rows[0].uri)
        assertFalse(io.rows[0].pending, "row must be committed (IS_PENDING=0)")
        assertFalse(io.rows[0].deleted)
        assertEquals(0, io.pendingRowCount())
    }

    @Test
    fun `write exception rolls back the pending row`() {
        val io = FaultInjectionMediaStoreIo(failOutputStream = true)
        val writer = LivePhotoMediaStoreWriter(RuntimeEnvironment.getApplication(), io)

        val result = writer.insertMotionMp4Sidecar(
            jpegRelativePath = "DCIM/Camera",
            mp4DisplayNamePrefix = "OpenCamera_20260701_002",
            mp4Bytes = byteArrayOf(1, 2, 3)
        )

        assertTrue(result.isFailure)
        assertEquals(0, io.pendingRowCount(), "pending row must be rolled back after write failure")
        assertEquals(0, io.rows.count { !it.deleted }, "no visible rows may remain")
    }

    @Test
    fun `storage full insert failure leaves no pending row`() {
        val io = FaultInjectionMediaStoreIo(failInsert = true)
        val writer = LivePhotoMediaStoreWriter(RuntimeEnvironment.getApplication(), io)

        val result = writer.insertMotionMp4Sidecar(
            jpegRelativePath = "DCIM/Camera",
            mp4DisplayNamePrefix = "OpenCamera_20260701_003",
            mp4Bytes = byteArrayOf(1)
        )

        assertTrue(result.isFailure)
        assertEquals(0, io.rows.size)
    }

    @Test
    fun `pending clear update failure rolls back the pending row`() {
        val io = FaultInjectionMediaStoreIo(failUpdate = true)
        val writer = LivePhotoMediaStoreWriter(RuntimeEnvironment.getApplication(), io)

        val result = writer.insertMotionMp4Sidecar(
            jpegRelativePath = "DCIM/Camera",
            mp4DisplayNamePrefix = "OpenCamera_20260701_004",
            mp4Bytes = byteArrayOf(1, 2)
        )

        assertTrue(result.isFailure)
        assertEquals(0, io.pendingRowCount())
        assertEquals(0, io.rows.count { !it.deleted })
    }

    @Test
    fun `pending clear zero-row update rolls back the pending row`() {
        val io = FaultInjectionMediaStoreIo(returnZeroOnUpdate = true)
        val writer = LivePhotoMediaStoreWriter(RuntimeEnvironment.getApplication(), io)

        val result = writer.insertMotionMp4Sidecar(
            jpegRelativePath = "DCIM/Camera",
            mp4DisplayNamePrefix = "OpenCamera_20260701_004_zero",
            mp4Bytes = byteArrayOf(1, 2)
        )

        assertTrue(result.isFailure)
        assertEquals(0, io.pendingRowCount())
        assertEquals(0, io.rows.count { !it.deleted })
    }

    @Test
    fun `overwrite failure is reported and leaves original row untouched`() {
        val io = FaultInjectionMediaStoreIo()
        val uri = io.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "existing.jpg")
        })
        assertNotNull(uri)
        io.failOutputStream = true

        val writer = LivePhotoMediaStoreWriter(RuntimeEnvironment.getApplication(), io)
        val result = writer.overwriteMotionPhotoJpeg(uri, byteArrayOf(9, 9))

        assertTrue(result.isFailure)
        assertEquals(1, io.rows.size)
        assertFalse(io.rows[0].deleted)
    }

    @Test
    fun `verify reads committed row state`() {
        val io = FaultInjectionMediaStoreIo()
        val writer = LivePhotoMediaStoreWriter(RuntimeEnvironment.getApplication(), io)

        val result = writer.insertMotionMp4Sidecar(
            jpegRelativePath = "DCIM/Camera",
            mp4DisplayNamePrefix = "OpenCamera_20260701_005",
            mp4Bytes = byteArrayOf(7)
        )
        val uri = result.getOrThrow()

        val verified = writer.verifyMotionMp4Sidecar(uri)
        assertTrue(verified.isSuccess)
        val record = verified.getOrThrow()
        assertEquals("OpenCamera_20260701_005.live.mp4", record.displayName)
        assertEquals("0", record.isPending)
    }

    class FaultInjectionMediaStoreIo(
        var failInsert: Boolean = false,
        var failOutputStream: Boolean = false,
        var failUpdate: Boolean = false,
        var returnZeroOnUpdate: Boolean = false
    ) : MediaStoreIo {
        class Row(
            val uri: String,
            val values: MutableMap<String, Any?>,
            var pending: Boolean,
            var deleted: Boolean = false
        )

        val rows = mutableListOf<Row>()
        private val idCounter = AtomicLong(1)

        fun pendingRowCount(): Int = rows.count { it.pending && !it.deleted }

        override fun insert(collection: Uri, values: ContentValues): Uri? {
            if (failInsert) return null
            val id = idCounter.getAndIncrement()
            val uri = Uri.parse("content://fake/$id")
            val row = Row(
                uri = uri.toString(),
                values = values.valueSet().associate { it.key to it.value }.toMutableMap(),
                pending = values.getAsInteger(MediaStore.Video.Media.IS_PENDING) == 1
            )
            rows += row
            return uri
        }

        override fun openOutputStream(uri: Uri, mode: String?): OutputStream? {
            if (failOutputStream) throw IOException("injected output stream failure for $uri")
            rowFor(uri) ?: return null
            return ByteArrayOutputStream()
        }

        override fun openInputStream(uri: Uri): InputStream? {
            val row = rowFor(uri)
            return ByteArrayInputStream(ByteArray(4))
        }

        override fun update(
            uri: Uri,
            values: ContentValues,
            selection: String?,
            selectionArgs: Array<String>?
        ): Int {
            if (failUpdate) throw IOException("injected update failure for $uri")
            if (returnZeroOnUpdate) return 0
            val row = rowFor(uri) ?: return 0
            if (row.deleted) return 0
            values.valueSet().forEach { (k, v) -> row.values[k] = v }
            row.pending = row.values[MediaStore.Video.Media.IS_PENDING] == 1
            return 1
        }

        override fun delete(uri: Uri): Int {
            val row = rowFor(uri) ?: return 0
            row.deleted = true
            return 1
        }

        override fun query(
            uri: Uri,
            projection: Array<String>?,
            selection: String?,
            selectionArgs: Array<String>?,
            sortOrder: String?
        ): android.database.Cursor? {
            val row = rowFor(uri) ?: return null
            if (row.deleted) return null
            val values = projection?.associateWith { key ->
                when (key) {
                    MediaStore.Video.Media.DISPLAY_NAME -> row.values[MediaStore.Video.Media.DISPLAY_NAME] as? String
                    MediaStore.Video.Media.MIME_TYPE -> row.values[MediaStore.Video.Media.MIME_TYPE] as? String
                    MediaStore.Video.Media.SIZE -> (row.values[MediaStore.Video.Media.SIZE] as? Long)?.toString()
                    MediaStore.Video.Media.RELATIVE_PATH -> row.values[MediaStore.Video.Media.RELATIVE_PATH] as? String
                    MediaStore.Video.Media.DURATION -> "n/a"
                    MediaStore.Video.Media.IS_PENDING -> if (row.pending) "1" else "0"
                    else -> null
                }
            } ?: emptyMap()
            return FakeCursor(values)
        }

        override fun notifyChange(uri: Uri) = Unit

        private fun rowFor(uri: Uri): Row? = rows.firstOrNull { it.uri == uri.toString() }
    }

    private class FakeCursor(
        private val values: Map<String, Any?>
    ) : android.database.Cursor {
        private var moved = false

        override fun getColumnIndex(columnName: String?): Int {
            val keys = values.keys.toList()
            return keys.indexOf(columnName)
        }

        override fun getColumnIndexOrThrow(columnName: String?): Int {
            val index = getColumnIndex(columnName)
            if (index < 0) throw IllegalArgumentException("No column $columnName")
            return index
        }

        override fun getString(columnIndex: Int): String? {
            val key = values.keys.toList()[columnIndex]
            return values[key] as? String
        }

        override fun moveToFirst(): Boolean {
            moved = true
            return values.isNotEmpty()
        }

        override fun moveToNext(): Boolean {
            if (!moved) {
                moved = true
                return true
            }
            return false
        }

        override fun close() = Unit
        override fun isClosed(): Boolean = false
        override fun isAfterLast(): Boolean = false
        override fun isBeforeFirst(): Boolean = false
        override fun isFirst(): Boolean = moved
        override fun isLast(): Boolean = true
        override fun getCount(): Int = if (values.isNotEmpty()) 1 else 0
        override fun getPosition(): Int = if (moved) 0 else -1
        override fun moveToPosition(position: Int): Boolean = position == 0
        override fun move(offset: Int): Boolean = false
        override fun moveToPrevious(): Boolean = false
        override fun moveToLast(): Boolean = values.isNotEmpty()
        override fun getColumnName(columnIndex: Int): String? = values.keys.toList().getOrNull(columnIndex)
        override fun getColumnNames(): Array<String> = values.keys.toTypedArray()
        override fun getColumnCount(): Int = values.size
        override fun getBlob(columnIndex: Int): ByteArray? = null
        override fun getDouble(columnIndex: Int): Double = 0.0
        override fun getFloat(columnIndex: Int): Float = 0f
        override fun getInt(columnIndex: Int): Int = 0
        override fun getLong(columnIndex: Int): Long = 0
        override fun getShort(columnIndex: Int): Short = 0
        override fun isNull(columnIndex: Int): Boolean = false
        override fun deactivate() = Unit
        override fun requery(): Boolean = true
        override fun registerContentObserver(observer: android.database.ContentObserver?) = Unit
        override fun unregisterContentObserver(observer: android.database.ContentObserver?) = Unit
        override fun registerDataSetObserver(observer: android.database.DataSetObserver?) = Unit
        override fun unregisterDataSetObserver(observer: android.database.DataSetObserver?) = Unit
        override fun setNotificationUri(cr: android.content.ContentResolver?, uri: Uri?) = Unit
        override fun getNotificationUri(): Uri? = null
        override fun getWantsAllOnMoveCalls(): Boolean = false
        override fun getExtras(): android.os.Bundle = android.os.Bundle()
        override fun respond(extras: android.os.Bundle?): android.os.Bundle = android.os.Bundle()
        override fun setExtras(extras: android.os.Bundle?) = Unit
        override fun copyStringToBuffer(columnIndex: Int, buffer: android.database.CharArrayBuffer?) = Unit
        override fun getType(columnIndex: Int): Int = android.database.Cursor.FIELD_TYPE_STRING
    }
}
