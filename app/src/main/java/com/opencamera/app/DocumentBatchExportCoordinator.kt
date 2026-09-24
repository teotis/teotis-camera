package com.opencamera.app

import android.content.Context
import android.net.Uri
import com.opencamera.core.session.DocumentBatchItem
import com.opencamera.core.session.DocumentBatchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** Owns document-batch export I/O; the activity remains responsible for UI state. */
internal class DocumentBatchExportCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val exporter = DocumentBatchZipExporter(
        appContext.getExternalFilesDir("document-exports")
            ?: File(appContext.filesDir, "document-exports")
    )

    suspend fun export(batch: DocumentBatchState): DocumentBatchZipExportResult = withContext(Dispatchers.IO) {
        exporter.export(batch = batch, openInput = ::openItemInput)
    }

    private fun openItemInput(item: DocumentBatchItem): InputStream? {
        val candidates = listOfNotNull(item.renderUri, item.outputPath).distinct()
        for (candidate in candidates) {
            openSourceInput(candidate)?.let { return it }
        }
        return null
    }

    private fun openSourceInput(source: String): InputStream? {
        if (source.startsWith("content://") || source.startsWith("file://")) {
            return runCatching { appContext.contentResolver.openInputStream(Uri.parse(source)) }.getOrNull()
        }
        val file = File(source)
        if (file.isAbsolute && file.isFile) {
            return runCatching { file.inputStream() }.getOrNull()
        }
        val mediaStoreUri = appContext.contentResolver.resolveDocumentImageUri(source) ?: return null
        return runCatching { appContext.contentResolver.openInputStream(mediaStoreUri) }.getOrNull()
    }
}
