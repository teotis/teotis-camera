package com.opencamera.app.camera

import android.graphics.Rect
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentAutoCropPostProcessorTest {
    @Test
    fun `document jpeg with auto crop enabled is rendered`() = runTest {
        val editor = FakeDocumentAutoCropEditor(
            result = DocumentAutoCropEditorResult.Applied(
                cropBounds = Rect(12, 18, 132, 198)
            )
        )
        val processor = DocumentAutoCropPostProcessor(editor)
        val result = processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/Documents/OpenCamera_DOC_1.jpg",
                    contentUri = "content://media/external/images/media/57"
                )
            )
        )

        assertEquals(1, editor.invocations.size)
        assertEquals(
            DocumentAutoCropTarget.ContentUri("content://media/external/images/media/57"),
            editor.invocations.single()
        )
        assertTrue(result.pipelineNotes.contains("document:auto-crop:applied"))
        assertTrue(result.pipelineNotes.any { it.startsWith("document:auto-crop:bounds=") })
    }

    @Test
    fun `non document result leaves pipeline untouched`() = runTest {
        val editor = FakeDocumentAutoCropEditor(
            result = DocumentAutoCropEditorResult.Applied(
                cropBounds = Rect(0, 0, 10, 10)
            )
        )
        val processor = DocumentAutoCropPostProcessor(editor)
        val input = photoResult(mode = "photo")
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    @Test
    fun `missing editable handle records diagnostic skip`() = runTest {
        val editor = FakeDocumentAutoCropEditor(
            result = DocumentAutoCropEditorResult.Applied(
                cropBounds = Rect(0, 0, 10, 10)
            )
        )
        val processor = DocumentAutoCropPostProcessor(editor)
        val result = processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/Documents/OpenCamera_DOC_2.jpg"
                )
            )
        )

        assertEquals(0, editor.invocations.size)
        assertTrue(result.pipelineNotes.contains("document:auto-crop:skipped:missing-output-handle"))
    }

    @Test
    fun `editor failure is recorded as pipeline diagnostic`() = runTest {
        val editor = FakeDocumentAutoCropEditor(
            result = DocumentAutoCropEditorResult.Failed("decode-failed")
        )
        val processor = DocumentAutoCropPostProcessor(editor)
        val result = processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/document.jpg",
                    filePath = "/tmp/document.jpg"
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("document:auto-crop:failed:decode-failed"))
    }

    @Test
    fun `detect document crop bounds trims bright margins`() {
        val bounds = detectDocumentCropBounds(
            width = 120,
            height = 160
        ) { x, y ->
            if (x in 16..103 && y in 16..143) 96 else 250
        }

        assertNotNull(bounds)
    }

    @Test
    fun `detect document crop bounds returns null when margins are absent`() {
        val bounds = detectDocumentCropBounds(
            width = 120,
            height = 160
        ) { _, _ -> 120 }

        assertNull(bounds)
    }

    private fun photoResult(
        mode: String = "document",
        autoCrop: Boolean = true,
        mediaType: MediaType = MediaType.PHOTO,
        outputHandle: MediaOutputHandle = MediaOutputHandle(
            displayPath = "/tmp/document.jpg",
            filePath = "/tmp/document.jpg"
        ),
        saveRequest: SaveRequest = SaveRequest.photoLibrary(
            metadata = MediaMetadata(
                customTags = mapOf(
                    "mode" to mode,
                    "autoCrop" to autoCrop.toString()
                )
            )
        )
    ): ShotResult {
        return ShotResult(
            shotId = "shot-document",
            mediaType = mediaType,
            outputPath = outputHandle.displayPath,
            outputHandle = outputHandle,
            saveRequest = saveRequest,
            thumbnailSource = ThumbnailSource.SavedMedia(
                outputPath = outputHandle.displayPath,
                renderUri = outputHandle.contentUri
            ),
            metadata = saveRequest.metadata
        )
    }

    private class FakeDocumentAutoCropEditor(
        private val result: DocumentAutoCropEditorResult
    ) : DocumentAutoCropEditor {
        val invocations = mutableListOf<DocumentAutoCropTarget>()

        override suspend fun apply(target: DocumentAutoCropTarget): DocumentAutoCropEditorResult {
            invocations += target
            return result
        }
    }
}
