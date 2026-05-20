package com.opencamera.app.camera

import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoWatermarkSubtitlePostProcessorTest {
    @Test
    fun `video result with watermark and content uri creates subtitle sidecar`() = runTest {
        val editor = FakeVideoWatermarkSubtitleEditor(
            result = VideoWatermarkSubtitleEditorResult.Applied(
                subtitlePath = "/tmp/OpenCamera_1.srt",
                warning = "app-private-sidecar"
            )
        )
        val processor = VideoWatermarkSubtitlePostProcessor(editor)
        val result = processor.process(
            videoResult(
                watermarkText = "VIDEO Torch On",
                outputHandle = MediaOutputHandle(
                    displayPath = "Movies/OpenCamera/OpenCamera_1.mp4",
                    contentUri = "content://media/external/video/media/101"
                )
            )
        )

        assertEquals(1, editor.invocations.size)
        assertEquals(
            VideoWatermarkSubtitleTarget.ContentUri(
                value = "content://media/external/video/media/101",
                fileNameBase = "OpenCamera_1",
                relativePath = "Movies/OpenCamera"
            ),
            editor.invocations.single().target
        )
        assertTrue(result.intermediateOutputPaths.contains("/tmp/OpenCamera_1.srt"))
        assertTrue(result.pipelineNotes.contains("video-watermark:subtitle-created"))
        assertTrue(
            result.pipelineNotes.contains("video-watermark:subtitle-path:/tmp/OpenCamera_1.srt")
        )
        assertTrue(result.pipelineNotes.contains("video-watermark:warning:app-private-sidecar"))
    }

    @Test
    fun `missing watermark text leaves video result untouched`() = runTest {
        val editor = FakeVideoWatermarkSubtitleEditor(
            result = VideoWatermarkSubtitleEditorResult.Applied("/tmp/ignored.srt")
        )
        val processor = VideoWatermarkSubtitlePostProcessor(editor)
        val input = videoResult(watermarkText = null)
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    @Test
    fun `video watermark without editable handle records diagnostic skip`() = runTest {
        val editor = FakeVideoWatermarkSubtitleEditor(
            result = VideoWatermarkSubtitleEditorResult.Applied("/tmp/ignored.srt")
        )
        val processor = VideoWatermarkSubtitlePostProcessor(editor)
        val result = processor.process(
            videoResult(
                watermarkText = "VIDEO Torch Off",
                outputHandle = MediaOutputHandle(
                    displayPath = "Movies/OpenCamera/OpenCamera_2.mp4"
                )
            )
        )

        assertEquals(0, editor.invocations.size)
        assertTrue(
            result.pipelineNotes.contains("video-watermark:skipped:missing-output-handle")
        )
    }

    @Test
    fun `editor failure is captured as pipeline diagnostic`() = runTest {
        val editor = FakeVideoWatermarkSubtitleEditor(
            result = VideoWatermarkSubtitleEditorResult.Failed("subtitle-write-failed")
        )
        val processor = VideoWatermarkSubtitlePostProcessor(editor)
        val result = processor.process(
            videoResult(
                watermarkText = "VIDEO Torch On",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/video.mp4",
                    filePath = "/tmp/video.mp4"
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("video-watermark:failed:subtitle-write-failed"))
    }

    @Test
    fun `non video result is ignored`() = runTest {
        val editor = FakeVideoWatermarkSubtitleEditor(
            result = VideoWatermarkSubtitleEditorResult.Applied("/tmp/ignored.srt")
        )
        val processor = VideoWatermarkSubtitlePostProcessor(editor)
        val input = videoResult(
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(
                metadata = MediaMetadata(watermarkText = "PHOTO Auto")
            )
        )
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    private fun videoResult(
        mediaType: MediaType = MediaType.VIDEO,
        watermarkText: String? = "VIDEO Torch On",
        outputHandle: MediaOutputHandle = MediaOutputHandle(
            displayPath = "/tmp/video.mp4",
            filePath = "/tmp/video.mp4"
        ),
        saveRequest: SaveRequest = SaveRequest.videoLibrary(
            metadata = MediaMetadata(watermarkText = watermarkText)
        )
    ): ShotResult {
        return ShotResult(
            shotId = "shot-video",
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

    private class FakeVideoWatermarkSubtitleEditor(
        private val result: VideoWatermarkSubtitleEditorResult
    ) : VideoWatermarkSubtitleEditor {
        val invocations = mutableListOf<Invocation>()

        override suspend fun apply(
            target: VideoWatermarkSubtitleTarget,
            watermarkText: String
        ): VideoWatermarkSubtitleEditorResult {
            invocations += Invocation(target, watermarkText)
            return result
        }
    }

    private data class Invocation(
        val target: VideoWatermarkSubtitleTarget,
        val watermarkText: String
    )
}
