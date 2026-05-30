package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class MediaSaveTransactionTest {

    @Test
    fun `withSaveIoTiming adds save-io timing to pipelineNotes`() {
        val result = photoResult().withSaveIoTiming(
            saveIoStartMs = 1000L,
            saveIoEndMs = 1050L
        )
        assertTrue(result.pipelineNotes.contains("timing:save-io=50ms"))
    }

    @Test
    fun `withSaveIoTiming zero delta records zero`() {
        val result = photoResult().withSaveIoTiming(
            saveIoStartMs = 2000L,
            saveIoEndMs = 2000L
        )
        assertTrue(result.pipelineNotes.contains("timing:save-io=0ms"))
    }

    // Original tests below

    private fun photoResult(
        intermediatePaths: List<String> = emptyList(),
        livePhotoBundle: LivePhotoBundle? = null
    ): ShotResult {
        return ShotResult(
            shotId = "shot-photo-tx",
            mediaType = MediaType.PHOTO,
            outputPath = "Pictures/OpenCamera/photo.jpg",
            outputHandle = MediaOutputHandle(displayPath = "Pictures/OpenCamera/photo.jpg"),
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.SavedMedia("Pictures/OpenCamera/photo.jpg"),
            metadata = MediaMetadata(),
            livePhotoBundle = livePhotoBundle,
            intermediateOutputPaths = intermediatePaths
        )
    }

    private fun videoResult(): ShotResult {
        return ShotResult(
            shotId = "shot-video-tx",
            mediaType = MediaType.VIDEO,
            outputPath = "Movies/OpenCamera/video.mp4",
            outputHandle = MediaOutputHandle(displayPath = "Movies/OpenCamera/video.mp4"),
            saveRequest = SaveRequest.videoLibrary(),
            thumbnailSource = ThumbnailSource.None,
            metadata = MediaMetadata()
        )
    }

    @Test
    fun `photo shot result produces primary still transaction`() {
        val result = photoResult().toTransactionResult()

        assertEquals(MediaTransactionStatus.SUCCESS, result.status)
        assertTrue(result.artifacts.containsKey(MediaArtifactRole.PRIMARY_STILL))
        assertEquals(
            "Pictures/OpenCamera/photo.jpg",
            result.artifacts[MediaArtifactRole.PRIMARY_STILL]?.first()?.displayPath
        )
    }

    @Test
    fun `video shot result produces primary video transaction`() {
        val result = videoResult().toTransactionResult()

        assertEquals(MediaTransactionStatus.SUCCESS, result.status)
        assertTrue(result.artifacts.containsKey(MediaArtifactRole.PRIMARY_VIDEO))
        assertEquals(
            "Movies/OpenCamera/video.mp4",
            result.artifacts[MediaArtifactRole.PRIMARY_VIDEO]?.first()?.displayPath
        )
    }

    @Test
    fun `live photo bundle produces still plus motion plus sidecar artifacts`() {
        val bundle = LivePhotoBundle(
            stillPath = "Pictures/live.jpg",
            motionPath = "Pictures/live.mp4",
            sidecarPath = "Pictures/live.json",
            motionDurationMillis = 1500,
            motionMimeType = "video/mp4",
            sidecarMimeType = "application/vnd.opencamera.live+json"
        )
        val result = photoResult(livePhotoBundle = bundle).toTransactionResult()

        assertTrue(result.artifacts.containsKey(MediaArtifactRole.PRIMARY_STILL))
        assertTrue(result.artifacts.containsKey(MediaArtifactRole.MOTION_SEGMENT))
        assertTrue(result.artifacts.containsKey(MediaArtifactRole.LIVE_SIDECAR))
        assertTrue(result.artifacts.containsKey(MediaArtifactRole.THUMBNAIL))
        assertEquals(
            "Pictures/live.mp4",
            result.artifacts[MediaArtifactRole.MOTION_SEGMENT]?.first()?.displayPath
        )
        assertEquals(
            "Pictures/live.json",
            result.artifacts[MediaArtifactRole.LIVE_SIDECAR]?.first()?.displayPath
        )
    }

    @Test
    fun `transaction with empty additional artifacts has single primary entry`() {
        val result = photoResult().toTransactionResult()

        assertEquals(1, result.artifacts.size)
        assertTrue(result.artifacts.containsKey(MediaArtifactRole.PRIMARY_STILL))
    }

    @Test
    fun `cleanup notes list intermediate paths`() {
        val tempDir = createTempDir(prefix = "tx-cleanup-")
        val tempFile = File(tempDir, "temp_frame.jpg").apply { writeText("temp") }

        try {
            val result = photoResult(
                intermediatePaths = listOf(tempFile.absolutePath, "/nonexistent/path.jpg")
            ).toTransactionResult()

            assertEquals(2, result.cleanupNotes.size)
            assertTrue(result.cleanupNotes.any { it.contains(tempFile.absolutePath) })
            assertTrue(result.cleanupNotes.any { it.contains("already-gone") })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `additional artifacts are merged with auto-derived artifacts`() {
        val extra = mapOf(
            MediaArtifactRole.DEBUG_TRACE to listOf(
                MediaOutputHandle(displayPath = "/tmp/trace.bin")
            )
        )
        val result = photoResult().toTransactionResult(additionalArtifacts = extra)

        assertTrue(result.artifacts.containsKey(MediaArtifactRole.PRIMARY_STILL))
        assertTrue(result.artifacts.containsKey(MediaArtifactRole.DEBUG_TRACE))
        assertEquals(
            "/tmp/trace.bin",
            result.artifacts[MediaArtifactRole.DEBUG_TRACE]?.first()?.displayPath
        )
    }
}
