package com.opencamera.app.camera

import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ShotTiming
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.media.addPipelineNotes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostprocessOuterGuardTest {

    @Test
    fun `throwing postprocessor returns raw result with failure note`() = runTest {
        val result = guardedPostProcess(ThrowingPostProcessor(RuntimeException("boom")), baseResult())

        assertEquals("/tmp/test-photo.jpg", result.outputPath)
        assertTrue(result.pipelineNotes.any { it.startsWith("postprocess:failed:composite:") })
    }

    @Test
    fun `throwing postprocessor preserves timing and live photo bundle`() = runTest {
        val rawResult = baseResult().copy(
            timing = ShotTiming(
                requestedAtElapsedMillis = 100L,
                deviceCaptureStartedAtElapsedMillis = 200L,
                deviceCaptureCompletedAtElapsedMillis = 300L
            ),
            livePhotoBundle = LivePhotoBundle(
                stillPath = "/tmp/test-photo.jpg",
                motionPath = "/tmp/test-photo.live.mp4",
                sidecarPath = "/tmp/sidecar.jpg",
                motionDurationMillis = 1500L,
                motionMimeType = "video/mp4",
                sidecarMimeType = "image/jpeg",
                sidecarHandle = MediaOutputHandle(displayPath = "/tmp/sidecar.jpg")
            )
        )

        val result = guardedPostProcess(ThrowingPostProcessor(OutOfMemoryError("bitmap")), rawResult)

        assertEquals(rawResult.timing, result.timing)
        assertEquals(rawResult.livePhotoBundle, result.livePhotoBundle)
        assertTrue(result.pipelineNotes.any { it.contains("postprocess:failed:composite:") })
    }

    @Test
    fun `successful postprocessor returns processed result`() = runTest {
        val result = guardedPostProcess(NoteAppendingPostProcessor("postprocess:success"), baseResult())

        assertEquals(listOf("postprocess:success"), result.pipelineNotes)
    }

    private fun baseResult(): ShotResult {
        return ShotResult(
            shotId = "test-shot",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/test-photo.jpg",
            outputHandle = MediaOutputHandle(
                displayPath = "/tmp/test-photo.jpg",
                filePath = "/tmp/test-photo.jpg"
            ),
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.SavedMedia(outputPath = "/tmp/test-photo.jpg"),
            metadata = MediaMetadata()
        )
    }

    private class ThrowingPostProcessor(private val error: Throwable) : MediaPostProcessor {
        override suspend fun process(result: ShotResult): ShotResult {
            throw error
        }
    }

    private class NoteAppendingPostProcessor(private val note: String) : MediaPostProcessor {
        override suspend fun process(result: ShotResult): ShotResult {
            return result.addPipelineNotes(note)
        }
    }
}
