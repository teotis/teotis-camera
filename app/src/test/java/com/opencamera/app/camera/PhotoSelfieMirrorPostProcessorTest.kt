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

class PhotoSelfieMirrorPostProcessorTest {
    @Test
    fun `photo result with selfie mirror request is rendered`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = PhotoSelfieMirrorApplied()
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val result = processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/OpenCamera_2.jpg",
                    contentUri = "content://media/external/images/media/202"
                )
            )
        )

        assertEquals(1, editor.invocations.size)
        assertEquals(
            ProcessorTarget.ContentUri("content://media/external/images/media/202"),
            editor.invocations.single()
        )
        assertTrue(result.pipelineNotes.contains("selfie-mirror:applied"))
    }

    @Test
    fun `selfie mirror request without editable handle records diagnostic skip`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = PhotoSelfieMirrorApplied()
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val result = processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/OpenCamera_3.jpg"
                )
            )
        )

        assertEquals(0, editor.invocations.size)
        assertTrue(result.pipelineNotes.contains("selfie-mirror:skipped:missing-output-handle"))
    }

    @Test
    fun `selfie mirror disabled leaves result untouched`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = PhotoSelfieMirrorApplied()
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val input = photoResult(
            saveRequest = SaveRequest.photoLibrary(
                metadata = MediaMetadata(
                    customTags = mapOf(
                        "captureLensFacing" to "front",
                        "selfieMirrorEnabled" to "on",
                        "selfieMirrorApply" to "false"
                    )
                )
            )
        )
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    @Test
    fun `non photo result is ignored`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = PhotoSelfieMirrorApplied()
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val input = photoResult(
            mediaType = MediaType.VIDEO,
            saveRequest = SaveRequest.videoLibrary(
                metadata = MediaMetadata(
                    customTags = mapOf("selfieMirrorApply" to "true")
                )
            )
        )
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    private fun photoResult(
        mediaType: MediaType = MediaType.PHOTO,
        outputHandle: MediaOutputHandle = MediaOutputHandle(
            displayPath = "/tmp/selfie.jpg",
            filePath = "/tmp/selfie.jpg"
        ),
        saveRequest: SaveRequest = SaveRequest.photoLibrary(
            metadata = MediaMetadata(
                customTags = mapOf(
                    "captureLensFacing" to "front",
                    "selfieMirrorEnabled" to "on",
                    "selfieMirrorApply" to "true"
                )
            )
        )
    ): ShotResult {
        return ShotResult(
            shotId = "shot-selfie",
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

    private class FakePhotoSelfieMirrorEditor(
        private val result: ProcessorEditorResult
    ) : PhotoSelfieMirrorEditor {
        val invocations = mutableListOf<ProcessorTarget>()

        override suspend fun apply(target: ProcessorTarget): ProcessorEditorResult {
            invocations += target
            return result
        }
    }
}
