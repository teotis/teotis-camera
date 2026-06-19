package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    // ── hasPostProcessFailures characterization ─────────────────────────────

    @Test
    fun `hasPostProcessFailures detects colon-failed-colon in pipeline notes`() {
        val result = photoResult().copy(
            pipelineNotes = listOf("selfie-mirror:failed:decode-failed")
        )
        assertTrue(result.hasPostProcessFailures())
    }

    @Test
    fun `hasPostProcessFailures detects composite-level failure note`() {
        val result = photoResult().copy(
            pipelineNotes = listOf("postprocess:failed:SelfieMirror:something went wrong")
        )
        assertTrue(result.hasPostProcessFailures())
    }

    @Test
    fun `hasPostProcessFailures detects algorithm failure note`() {
        val result = photoResult().copy(
            pipelineNotes = listOf("algorithm-render:failed:oom")
        )
        assertTrue(result.hasPostProcessFailures())
    }

    @Test
    fun `hasPostProcessFailures returns false for empty pipeline notes`() {
        val result = photoResult()
        assertFalse(result.hasPostProcessFailures())
    }

    @Test
    fun `hasPostProcessFailures returns false for success notes only`() {
        val result = photoResult().copy(
            pipelineNotes = listOf(
                "selfie-mirror:applied",
                "algorithm-render:applied:photo-vivid",
                "watermark:rendered:classic-overlay"
            )
        )
        assertFalse(result.hasPostProcessFailures())
    }

    @Test
    fun `hasPostProcessFailures returns false for skipped notes`() {
        val result = photoResult().copy(
            pipelineNotes = listOf(
                "selfie-mirror:skipped:missing-output-handle",
                "algorithm-render:skipped:near-neutral:photo-original",
                "frame-ratio:skipped:already-matched"
            )
        )
        assertFalse(result.hasPostProcessFailures())
    }

    @Test
    fun `hasPostProcessFailures returns false for timing notes`() {
        val result = photoResult().copy(
            pipelineNotes = listOf(
                "timing:postprocess:SelfieMirror=5ms",
                "timing:save-io=12ms"
            )
        )
        assertFalse(result.hasPostProcessFailures())
    }

    @Test
    fun `hasPostProcessFailures returns false for warning notes`() {
        val result = photoResult().copy(
            pipelineNotes = listOf(
                "selfie-mirror:applied",
                "selfie-mirror:warning:exif-restore-partial",
                "watermark:warning:archive-embed-failed"
            )
        )
        assertFalse(result.hasPostProcessFailures())
    }

    @Test
    fun `hasPostProcessFailures returns false for degraded notes`() {
        val result = photoResult().copy(
            pipelineNotes = listOf(
                "portrait-render:degraded:mask-render-exception",
                "portrait-render:fallback-focus"
            )
        )
        assertFalse(result.hasPostProcessFailures())
    }

    // ── Transaction status derivation characterization ──────────────────────

    @Test
    fun `transaction status SUCCESS when no failure notes`() {
        val result = photoResult().copy(
            pipelineNotes = listOf("selfie-mirror:applied", "algorithm-render:applied:photo-vivid")
        )
        assertEquals(MediaTransactionStatus.SUCCESS, result.toTransactionResult().status)
    }

    @Test
    fun `transaction status PARTIAL_SUCCESS when any failure note exists`() {
        val result = photoResult().copy(
            pipelineNotes = listOf(
                "selfie-mirror:applied",
                "watermark:failed:encode-oom",
                "algorithm-render:applied:photo-vivid"
            )
        )
        assertEquals(MediaTransactionStatus.PARTIAL_SUCCESS, result.toTransactionResult().status)
    }

    @Test
    fun `transaction status PARTIAL_SUCCESS for composite-level failure`() {
        val result = photoResult().copy(
            pipelineNotes = listOf("postprocess:failed:SelfieMirror:some error")
        )
        assertEquals(MediaTransactionStatus.PARTIAL_SUCCESS, result.toTransactionResult().status)
    }

    @Test
    fun `transaction status SUCCESS despite degraded portrait render`() {
        val result = photoResult().copy(
            pipelineNotes = listOf(
                "portrait-render:degraded:mask-render-exception",
                "portrait-render:fallback-focus"
            )
        )
        assertEquals(
            MediaTransactionStatus.SUCCESS,
            result.toTransactionResult().status,
            "Degraded notes without :failed: should not affect transaction status"
        )
    }

    @Test
    fun `transaction status PARTIAL_SUCCESS with mixed success and failure`() {
        val result = photoResult().copy(
            pipelineNotes = listOf(
                "selfie-mirror:applied",
                "algorithm-render:applied:photo-vivid",
                "watermark:failed:decode-failed",
                "frame-ratio:applied:4:3",
                "portrait-render:applied:depth"
            )
        )
        assertEquals(MediaTransactionStatus.PARTIAL_SUCCESS, result.toTransactionResult().status)
    }

    // ── postProcessFailureSummary characterization ──────────────────────────

    @Test
    fun `postProcessFailureSummary joins multiple failures with semicolon`() {
        val result = photoResult().copy(
            pipelineNotes = listOf(
                "selfie-mirror:applied",
                "watermark:failed:decode-oom",
                "algorithm-render:failed:style-oom"
            )
        )
        val summary = result.postProcessFailureSummary()

        assertNotNull(summary)
        assertTrue(summary!!.contains("watermark:failed:decode-oom"))
        assertTrue(summary.contains("algorithm-render:failed:style-oom"))
        assertTrue(summary.contains(";"))
    }

    @Test
    fun `postProcessFailureSummary is null for clean result`() {
        val result = photoResult().copy(
            pipelineNotes = listOf("selfie-mirror:applied")
        )
        assertNull(result.postProcessFailureSummary())
    }

    // ── Output integrity characterization ───────────────────────────────────

    @Test
    fun `output handle preserved through pipeline failures`() {
        val handle = MediaOutputHandle(
            displayPath = "Pictures/OpenCamera/photo.jpg",
            filePath = "/data/.../photo.jpg",
            contentUri = "content://media/external/images/media/100"
        )
        val result = photoResult().copy(
            outputHandle = handle,
            pipelineNotes = listOf("watermark:failed:encode-oom")
        )
        val txResult = result.toTransactionResult()

        assertEquals(handle, txResult.primaryOutput)
    }

    @Test
    fun `intermediate paths included in cleanup notes regardless of failure state`() {
        val tempDir = createTempDir(prefix = "tx-integrity-")
        val tempFile = File(tempDir, "intermediate.jpg").apply { writeText("data") }
        try {
            val result = photoResult(
                intermediatePaths = listOf(tempFile.absolutePath)
            ).copy(
                pipelineNotes = listOf("watermark:failed:render-oom")
            ).toTransactionResult()

            assertEquals(1, result.cleanupNotes.size)
            assertTrue(result.cleanupNotes[0].contains("cleanup:pending:"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
