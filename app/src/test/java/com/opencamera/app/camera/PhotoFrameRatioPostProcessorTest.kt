package com.opencamera.app.camera

import com.opencamera.core.media.FrameRatio
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

class PhotoFrameRatioPostProcessorTest {
    @Test
    fun `jpeg with frame ratio tag is cropped through content uri`() = runTest {
        val editor = FakePhotoFrameRatioEditor(
            result = PhotoFrameRatioEditorResult.Applied(
                frameRatio = FrameRatio.RATIO_16_9,
                cropBounds = CropBounds(0, 120, 4000, 2380)
            )
        )
        val processor = PhotoFrameRatioPostProcessor(editor)
        val result = processor.process(
            photoResult(
                frameRatio = "16:9",
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/OpenCamera_1.jpg",
                    contentUri = "content://media/external/images/media/99"
                )
            )
        )

        assertEquals(1, editor.invocations.size)
        val invocation = editor.invocations.single()
        assertEquals(
            PhotoFrameRatioTarget.ContentUri("content://media/external/images/media/99"),
            invocation.target
        )
        assertEquals(FrameRatio.RATIO_16_9, invocation.frameRatio)
        assertTrue(result.pipelineNotes.contains("frame-ratio:applied:16:9"))
        assertTrue(result.pipelineNotes.any { it.startsWith("frame-ratio:bounds=") })
    }

    @Test
    fun `missing frame ratio tag leaves result untouched`() = runTest {
        val editor = FakePhotoFrameRatioEditor(
            result = PhotoFrameRatioEditorResult.Skipped("already-matched")
        )
        val processor = PhotoFrameRatioPostProcessor(editor)
        val input = photoResult(frameRatio = null)
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    @Test
    fun `unsupported frame ratio tag records diagnostic`() = runTest {
        val editor = FakePhotoFrameRatioEditor(
            result = PhotoFrameRatioEditorResult.Skipped("already-matched")
        )
        val processor = PhotoFrameRatioPostProcessor(editor)
        val result = processor.process(photoResult(frameRatio = "5:4"))

        assertEquals(0, editor.invocations.size)
        assertTrue(result.pipelineNotes.contains("frame-ratio:skipped:unsupported-frame-ratio"))
    }

    @Test
    fun `missing editable handle records diagnostic skip`() = runTest {
        val editor = FakePhotoFrameRatioEditor(
            result = PhotoFrameRatioEditorResult.Skipped("already-matched")
        )
        val processor = PhotoFrameRatioPostProcessor(editor)
        val result = processor.process(
            photoResult(
                frameRatio = "1:1",
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/OpenCamera_2.jpg"
                )
            )
        )

        assertEquals(0, editor.invocations.size)
        assertTrue(result.pipelineNotes.contains("frame-ratio:skipped:missing-output-handle"))
    }

    @Test
    fun `compute center crop bounds trims top and bottom for widescreen`() {
        val bounds = computeCenterCropBounds(
            width = 4000,
            height = 3000,
            frameRatio = FrameRatio.RATIO_16_9
        )

        assertNotNull(bounds)
        assertEquals(0, bounds.left)
        assertEquals(375, bounds.top)
        assertEquals(4000, bounds.right)
        assertEquals(2625, bounds.bottom)
    }

    @Test
    fun `compute center crop bounds trims sides for square`() {
        val bounds = computeCenterCropBounds(
            width = 4000,
            height = 3000,
            frameRatio = FrameRatio.RATIO_1_1
        )

        assertNotNull(bounds)
        assertEquals(500, bounds.left)
        assertEquals(0, bounds.top)
        assertEquals(3500, bounds.right)
        assertEquals(3000, bounds.bottom)
    }

    @Test
    fun `compute center crop bounds returns null when already matched`() {
        val bounds = computeCenterCropBounds(
            width = 4000,
            height = 3000,
            frameRatio = FrameRatio.RATIO_4_3
        )

        assertNull(bounds)
    }

    @Test
    fun `compute center crop bounds handles portrait source 4_3 ratio`() {
        // Portrait photo: 3000w x 4000h (3:4 aspect)
        val bounds = computeCenterCropBounds(
            width = 3000,
            height = 4000,
            frameRatio = FrameRatio.RATIO_4_3
        )

        assertNull(bounds)
    }

    @Test
    fun `compute center crop bounds handles portrait source 16_9 ratio`() {
        // Portrait photo: 3000w x 4000h
        // Visible preview frame is oriented as 9:16, so saved output must crop
        // the sides to a portrait-tall frame instead of producing a landscape crop.
        val bounds = computeCenterCropBounds(
            width = 3000,
            height = 4000,
            frameRatio = FrameRatio.RATIO_16_9
        )

        assertNotNull(bounds)
        assertEquals(375, bounds.left)
        assertEquals(0, bounds.top)
        assertEquals(2625, bounds.right)
        assertEquals(4000, bounds.bottom)
    }

    private fun photoResult(
        frameRatio: String?,
        outputHandle: MediaOutputHandle = MediaOutputHandle(
            displayPath = "/tmp/frame-ratio.jpg",
            filePath = "/tmp/frame-ratio.jpg"
        )
    ): ShotResult {
        val tags = buildMap {
            frameRatio?.let { put("frameRatio", it) }
        }
        val saveRequest = SaveRequest.photoLibrary(
            metadata = MediaMetadata(
                customTags = tags
            )
        )
        return ShotResult(
            shotId = "shot-frame-ratio",
            mediaType = MediaType.PHOTO,
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

    private class FakePhotoFrameRatioEditor(
        private val result: PhotoFrameRatioEditorResult
    ) : PhotoFrameRatioEditor {
        val invocations = mutableListOf<Invocation>()

        override suspend fun apply(
            target: PhotoFrameRatioTarget,
            frameRatio: FrameRatio
        ): PhotoFrameRatioEditorResult {
            invocations += Invocation(target, frameRatio)
            return result
        }
    }

    private data class Invocation(
        val target: PhotoFrameRatioTarget,
        val frameRatio: FrameRatio
    )
}
