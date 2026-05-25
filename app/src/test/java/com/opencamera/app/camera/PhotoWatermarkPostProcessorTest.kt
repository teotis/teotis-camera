package com.opencamera.app.camera

import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.ThumbnailSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoWatermarkPostProcessorTest {
    @Test
    fun `photo result with watermark and content uri is rendered`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "PHOTO Auto",
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/OpenCamera_1.jpg",
                    contentUri = "content://media/external/images/media/101"
                )
            )
        )

        assertEquals(1, editor.invocations.size)
        assertEquals(
            ProcessorTarget.ContentUri("content://media/external/images/media/101"),
            editor.invocations.single().target
        )
        assertEquals("travel-polaroid", editor.invocations.single().templateId)
        assertTrue(result.pipelineNotes.contains("watermark:rendered:travel-polaroid"))
    }

    @Test
    fun `missing watermark text leaves result untouched`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val input = photoResult(watermarkText = null)
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    @Test
    fun `watermark without editable handle records diagnostic skip`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "DOC Receipt",
                watermarkTemplate = "retro-frame",
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/Documents/OpenCamera_DOC_1.jpg"
                )
            )
        )

        assertEquals(0, editor.invocations.size)
        assertTrue(result.pipelineNotes.contains("watermark:template:retro-frame"))
        assertTrue(result.pipelineNotes.contains("watermark:skipped:missing-output-handle"))
    }

    @Test
    fun `editor failure is captured as pipeline diagnostic`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = ProcessorEditorResult.Failed("decode-failed")
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "Night Street",
                watermarkTemplate = "classic-overlay",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/night.jpg",
                    filePath = "/tmp/night.jpg"
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("watermark:template:classic-overlay"))
        assertTrue(result.pipelineNotes.contains("watermark:failed:decode-failed"))
    }

    @Test
    fun `editor warning is included in pipeline notes`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied(warning = "archive-embed-failed")
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "Night Street",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/night.jpg",
                    filePath = "/tmp/night.jpg"
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("watermark:rendered:travel-polaroid"))
        assertTrue(result.pipelineNotes.contains("watermark:warning:archive-embed-failed"))
    }

    @Test
    fun `pure text template passes through to pipeline notes`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "OpenCamera",
                watermarkTemplate = "pure-text",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/pure.jpg",
                    filePath = "/tmp/pure.jpg"
                )
            )
        )

        assertEquals("pure-text", editor.invocations.single().templateId)
        assertTrue(result.pipelineNotes.contains("watermark:rendered:pure-text"))
    }

    @Test
    fun `blur four border template passes through to pipeline notes`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "OpenCamera",
                watermarkTemplate = "blur-four-border",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/blur.jpg",
                    filePath = "/tmp/blur.jpg"
                )
            )
        )

        assertEquals("blur-four-border", editor.invocations.single().templateId)
        assertTrue(result.pipelineNotes.contains("watermark:rendered:blur-four-border"))
    }

    @Test
    fun `professional bottom bar template passes through to pipeline notes`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "Scenery Handheld",
                watermarkTemplate = "professional-bottom-bar",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/pro.jpg",
                    filePath = "/tmp/pro.jpg"
                )
            )
        )

        assertEquals("professional-bottom-bar", editor.invocations.single().templateId)
        assertTrue(result.pipelineNotes.contains("watermark:rendered:professional-bottom-bar"))
    }

    @Test
    fun `non photo result is ignored`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val input = photoResult(
            mediaType = MediaType.VIDEO,
            saveRequest = SaveRequest.videoLibrary(
                metadata = MediaMetadata(watermarkText = "VIDEO Torch On")
            )
        )
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    private fun photoResult(
        mediaType: MediaType = MediaType.PHOTO,
        watermarkText: String? = "PHOTO Auto",
        watermarkTemplate: String = "travel-polaroid",
        outputHandle: MediaOutputHandle = MediaOutputHandle(
            displayPath = "/tmp/photo.jpg",
            filePath = "/tmp/photo.jpg"
        ),
        saveRequest: SaveRequest = SaveRequest.photoLibrary(
            metadata = MediaMetadata(
                watermarkText = watermarkText,
                customTags = mapOf("watermarkTemplate" to watermarkTemplate)
            )
        )
    ): ShotResult {
        return ShotResult(
            shotId = "shot-photo",
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

    private class FakePhotoWatermarkEditor(
        private val result: ProcessorEditorResult
    ) : PhotoWatermarkEditor {
        val invocations = mutableListOf<Invocation>()

        override suspend fun apply(
            target: ProcessorTarget,
            metadata: MediaMetadata,
            watermarkText: String,
            templateId: String
        ): ProcessorEditorResult {
            invocations += Invocation(target, watermarkText, templateId)
            return result
        }
    }

    private data class Invocation(
        val target: ProcessorTarget,
        val watermarkText: String,
        val templateId: String
    )
}
