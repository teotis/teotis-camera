package com.opencamera.app

import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.session.DocumentBatchCropStatus
import com.opencamera.core.session.DocumentBatchItem
import com.opencamera.core.session.DocumentBatchState
import com.opencamera.core.session.DocumentBatchStatus
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentBatchZipExporterTest {

    @Test
    fun `zip entries follow reordered batch order with sortable page prefixes`() {
        val outputDir = createTempDir(prefix = "document-batch-export-test-")
        val exporter = DocumentBatchZipExporter(outputDir)
        val batch = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                item("page-2", orderIndex = 1, outputPath = "/camera/original-02.jpg"),
                item("page-1", orderIndex = 0, outputPath = "/camera/original-01.jpg"),
                item("page-3", orderIndex = 2, outputPath = "/camera/original-03.jpg")
            )
        )

        val result = exporter.export(
            batch = batch,
            nowMillis = 1234L,
            openInput = { page ->
                ByteArrayInputStream("bytes-${page.itemId}".toByteArray())
            }
        )

        assertEquals("document-batch-1234.zip", result.file.name)
        ZipFile(result.file).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertEquals(
                listOf(
                    "01_original-01.jpg",
                    "02_original-02.jpg",
                    "03_original-03.jpg",
                    "manifest.txt"
                ),
                names
            )
            assertEquals("bytes-page-1", zip.getInputStream(zip.getEntry("01_original-01.jpg")).reader().readText())
            assertEquals("bytes-page-2", zip.getInputStream(zip.getEntry("02_original-02.jpg")).reader().readText())
        }
    }

    @Test
    fun `zip export skips missing page sources and reports skipped items`() {
        val outputDir = createTempDir(prefix = "document-batch-export-test-")
        val exporter = DocumentBatchZipExporter(outputDir)
        val batch = DocumentBatchState(
            batchId = "batch-1",
            status = DocumentBatchStatus.ACTIVE,
            items = listOf(
                item("page-1", orderIndex = 0, outputPath = "/camera/original-01.jpg"),
                item("page-2", orderIndex = 1, outputPath = "/camera/original-02.jpg")
            )
        )

        val result = exporter.export(
            batch = batch,
            nowMillis = 1234L,
            openInput = { page ->
                if (page.itemId == "page-1") ByteArrayInputStream("ok".toByteArray()) else null
            }
        )

        assertEquals(1, result.exportedPages)
        assertEquals(listOf("page-2"), result.skippedItemIds)
        assertTrue(result.file.isFile)
    }

    private fun item(
        itemId: String,
        orderIndex: Int,
        outputPath: String
    ): DocumentBatchItem {
        return DocumentBatchItem(
            itemId = itemId,
            shotId = "shot-$itemId",
            orderIndex = orderIndex,
            outputPath = outputPath,
            renderUri = outputPath,
            thumbnailSource = ThumbnailSource.SavedMedia(outputPath),
            profileId = null,
            scanMode = null,
            cropStatus = DocumentBatchCropStatus.NOT_REQUESTED,
            pipelineNotes = emptyList()
        )
    }
}
