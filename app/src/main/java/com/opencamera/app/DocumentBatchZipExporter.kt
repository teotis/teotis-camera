package com.opencamera.app

import com.opencamera.core.session.DocumentBatchItem
import com.opencamera.core.session.DocumentBatchState
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class DocumentBatchZipExportResult(
    val file: File,
    val exportedPages: Int,
    val skippedItemIds: List<String>
)

internal class DocumentBatchZipExporter(
    private val outputDir: File
) {
    fun export(
        batch: DocumentBatchState,
        nowMillis: Long = System.currentTimeMillis(),
        openInput: (DocumentBatchItem) -> InputStream?
    ): DocumentBatchZipExportResult {
        if (!outputDir.exists()) outputDir.mkdirs()
        val outputFile = File(outputDir, "document-batch-$nowMillis.zip")
        val orderedItems = batch.items.sortedBy { it.orderIndex }
        val skipped = mutableListOf<String>()
        var exported = 0

        ZipOutputStream(outputFile.outputStream().buffered()).use { zip ->
            orderedItems.forEachIndexed { index, item ->
                val input = openInput(item)
                if (input == null) {
                    skipped += item.itemId
                    return@forEachIndexed
                }
                input.use { stream ->
                    zip.putNextEntry(ZipEntry(entryNameFor(index, orderedItems.size, item)))
                    stream.copyTo(zip)
                    zip.closeEntry()
                    exported += 1
                }
            }
            zip.putNextEntry(ZipEntry("manifest.txt"))
            zip.write(buildManifest(batch, orderedItems, skipped).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        return DocumentBatchZipExportResult(
            file = outputFile,
            exportedPages = exported,
            skippedItemIds = skipped
        )
    }

    private fun entryNameFor(index: Int, totalCount: Int, item: DocumentBatchItem): String {
        val width = totalCount.toString().length.coerceAtLeast(2)
        val prefix = (index + 1).toString().padStart(width, '0')
        return "${prefix}_${safeSourceName(item)}"
    }

    private fun safeSourceName(item: DocumentBatchItem): String {
        val rawName = item.outputPath
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: item.renderUri
                ?.substringAfterLast('/')
                ?.substringBefore('?')
                ?.takeIf { it.isNotBlank() }
            ?: "${item.itemId}.jpg"
        return rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun buildManifest(
        batch: DocumentBatchState,
        orderedItems: List<DocumentBatchItem>,
        skipped: List<String>
    ): String = buildString {
        appendLine("batchId=${batch.batchId}")
        orderedItems.forEachIndexed { index, item ->
            appendLine("${index + 1}. itemId=${item.itemId} source=${item.outputPath ?: item.renderUri ?: ""}")
        }
        if (skipped.isNotEmpty()) {
            appendLine("skipped=${skipped.joinToString(",")}")
        }
    }
}
