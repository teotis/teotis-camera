package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostProcessFailureContractsTest {

    // ── Orthogonal dimension independence ──────────────────────────────────

    @Test
    fun `stage and cause are independent dimensions`() {
        val decode = PostProcessFailure(
            stage = PostProcessFailureStage.SELFIE_MIRROR,
            cause = PostProcessFailureCause.DECODE_FAILED,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.RECOVERABLE
        )
        val oom = PostProcessFailure(
            stage = PostProcessFailureStage.SELFIE_MIRROR,
            cause = PostProcessFailureCause.OUT_OF_MEMORY,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE
        )
        val encode = PostProcessFailure(
            stage = PostProcessFailureStage.WATERMARK,
            cause = PostProcessFailureCause.ENCODE,
            integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE
        )

        assertEquals(PostProcessFailureStage.SELFIE_MIRROR, decode.stage)
        assertEquals(PostProcessFailureCause.DECODE_FAILED, decode.cause)
        assertEquals(PostProcessFailureStage.WATERMARK, encode.stage)
        assertEquals(PostProcessFailureCause.ENCODE, encode.cause)
        assertEquals(PostProcessOutputIntegrity.POSSIBLY_MODIFIED, encode.integrity)
        assertEquals(PostProcessFailureDisposition.UNRECOVERABLE, oom.disposition)
    }

    @Test
    fun `integrity dimension is independent of stage and cause`() {
        val intact = PostProcessFailure(
            stage = PostProcessFailureStage.ALGORITHM,
            cause = PostProcessFailureCause.OUT_OF_MEMORY,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE
        )
        val modified = PostProcessFailure(
            stage = PostProcessFailureStage.ALGORITHM,
            cause = PostProcessFailureCause.OUT_OF_MEMORY,
            integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE
        )

        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, intact.integrity)
        assertEquals(PostProcessOutputIntegrity.POSSIBLY_MODIFIED, modified.integrity)
    }

    @Test
    fun `disposition dimension is independent of integrity`() {
        val recoverable = PostProcessFailure(
            stage = PostProcessFailureStage.WATERMARK,
            cause = PostProcessFailureCause.DECODE_FAILED,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.RECOVERABLE
        )
        val unrecoverable = PostProcessFailure(
            stage = PostProcessFailureStage.WATERMARK,
            cause = PostProcessFailureCause.DECODE_FAILED,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE
        )

        assertEquals(PostProcessFailureDisposition.RECOVERABLE, recoverable.disposition)
        assertEquals(PostProcessFailureDisposition.UNRECOVERABLE, unrecoverable.disposition)
    }

    // ── Legacy note projection ─────────────────────────────────────────────

    @Test
    fun `processor failure projects to stage colon failed colon cause`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.SELFIE_MIRROR,
            cause = PostProcessFailureCause.DECODE_FAILED,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.RECOVERABLE
        )
        assertEquals("selfie-mirror:failed:decode-failed", failure.toLegacyNote())
    }

    @Test
    fun `watermark failure projects correctly`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.WATERMARK,
            cause = PostProcessFailureCause.OUT_OF_MEMORY,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE
        )
        assertEquals("watermark:failed:out-of-memory", failure.toLegacyNote())
    }

    @Test
    fun `algorithm failure projects correctly`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.ALGORITHM,
            cause = PostProcessFailureCause.BITMAP_OPERATION,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE
        )
        assertEquals("algorithm-render:failed:bitmap-operation", failure.toLegacyNote())
    }

    @Test
    fun `portrait render failure projects correctly`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.PORTRAIT_RENDER,
            cause = PostProcessFailureCause.OUTPUT_UNAVAILABLE,
            integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE
        )
        assertEquals("portrait-render:failed:output-unavailable", failure.toLegacyNote())
    }

    @Test
    fun `frame ratio failure projects correctly`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.FRAME_RATIO,
            cause = PostProcessFailureCause.BITMAP_OPERATION,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.RECOVERABLE
        )
        assertEquals("frame-ratio:failed:bitmap-operation", failure.toLegacyNote())
    }

    @Test
    fun `document auto crop failure projects correctly`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.DOCUMENT_AUTO_CROP,
            cause = PostProcessFailureCause.EXCEPTION,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE
        )
        assertEquals("document:auto-crop:failed:exception", failure.toLegacyNote())
    }

    @Test
    fun `composite failure projects with processor name`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.COMPOSITE,
            cause = PostProcessFailureCause.EXCEPTION,
            integrity = PostProcessOutputIntegrity.UNKNOWN,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE,
            processorName = "SelfieMirror"
        )
        assertEquals("postprocess:failed:SelfieMirror:composite-failure", failure.toLegacyNote())
    }

    @Test
    fun `composite failure without processor name uses Unknown`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.COMPOSITE,
            cause = PostProcessFailureCause.EXCEPTION,
            integrity = PostProcessOutputIntegrity.UNKNOWN,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE
        )
        assertEquals("postprocess:failed:Unknown:composite-failure", failure.toLegacyNote())
    }

    // ── Legacy note projection is detected by existing string check ────────

    @Test
    fun `projected legacy notes are detected by contains failed check`() {
        val stages = listOf(
            PostProcessFailureStage.SELFIE_MIRROR,
            PostProcessFailureStage.WATERMARK,
            PostProcessFailureStage.ALGORITHM,
            PostProcessFailureStage.PORTRAIT_RENDER,
            PostProcessFailureStage.FRAME_RATIO,
            PostProcessFailureStage.DOCUMENT_AUTO_CROP,
            PostProcessFailureStage.COMPOSITE
        )
        for (stage in stages) {
            val failure = PostProcessFailure(
                stage = stage,
                cause = PostProcessFailureCause.EXCEPTION,
                integrity = PostProcessOutputIntegrity.UNKNOWN,
                disposition = PostProcessFailureDisposition.UNRECOVERABLE
            )
            val note = failure.toLegacyNote()
            assertTrue(
                note.contains(":failed:"),
                "Legacy note from $stage should contain ':failed:': $note"
            )
        }
    }

    // ── ShotResult structured failures integration ─────────────────────────

    @Test
    fun `shotResult hasPostProcessFailures returns true when structured failures present`() {
        val result = baseResult().copy(
            structuredPostProcessFailures = listOf(
                PostProcessFailure(
                    stage = PostProcessFailureStage.SELFIE_MIRROR,
                    cause = PostProcessFailureCause.DECODE_FAILED,
                    integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
                    disposition = PostProcessFailureDisposition.RECOVERABLE
                )
            )
        )
        assertTrue(result.hasPostProcessFailures())
    }

    @Test
    fun `shotResult hasPostProcessFailures returns false when structured failures empty`() {
        val result = baseResult()
        assertFalse(result.hasPostProcessFailures())
    }

    @Test
    fun `shotResult hasPostProcessFailures returns true for legacy pipeline notes when structured empty`() {
        val result = baseResult().copy(
            pipelineNotes = listOf("selfie-mirror:failed:decode-failed")
        )
        assertTrue(result.hasPostProcessFailures())
    }

    @Test
    fun `shotResult hasPostProcessFailures prefers structured over pipeline notes`() {
        val result = baseResult().copy(
            structuredPostProcessFailures = listOf(
                PostProcessFailure(
                    stage = PostProcessFailureStage.ALGORITHM,
                    cause = PostProcessFailureCause.OUT_OF_MEMORY,
                    integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
                    disposition = PostProcessFailureDisposition.UNRECOVERABLE
                )
            ),
            pipelineNotes = listOf("selfie-mirror:applied")
        )
        assertTrue(result.hasPostProcessFailures())
    }

    @Test
    fun `shotResult postProcessFailureSummary returns structured failure summary`() {
        val result = baseResult().copy(
            structuredPostProcessFailures = listOf(
                PostProcessFailure(
                    stage = PostProcessFailureStage.SELFIE_MIRROR,
                    cause = PostProcessFailureCause.DECODE_FAILED,
                    integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
                    disposition = PostProcessFailureDisposition.RECOVERABLE
                ),
                PostProcessFailure(
                    stage = PostProcessFailureStage.WATERMARK,
                    cause = PostProcessFailureCause.OUT_OF_MEMORY,
                    integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
                    disposition = PostProcessFailureDisposition.UNRECOVERABLE
                )
            )
        )
        val summary = result.postProcessFailureSummary()

        assertNotNull(summary)
        assertTrue(summary!!.contains("selfie-mirror:decode-failed"))
        assertTrue(summary.contains("watermark:out-of-memory"))
        assertTrue(summary.contains(";"))
    }

    @Test
    fun `shotResult postProcessFailureSummary falls back to pipeline notes when structured empty`() {
        val result = baseResult().copy(
            pipelineNotes = listOf("watermark:failed:encode-oom")
        )
        val summary = result.postProcessFailureSummary()

        assertNotNull(summary)
        assertTrue(summary!!.contains("watermark:failed:encode-oom"))
    }

    @Test
    fun `shotResult postProcessFailureSummary returns null when no failures at all`() {
        val result = baseResult().copy(
            pipelineNotes = listOf("selfie-mirror:applied")
        )
        assertNull(result.postProcessFailureSummary())
    }

    @Test
    fun `structuredPostProcessFailures defaults to empty list`() {
        val result = baseResult()
        assertTrue(result.structuredPostProcessFailures.isEmpty())
    }

    // ── Transaction status from structured failures ────────────────────────

    @Test
    fun `transaction status PARTIAL_SUCCESS when structured failures present`() {
        val result = baseResult().copy(
            structuredPostProcessFailures = listOf(
                PostProcessFailure(
                    stage = PostProcessFailureStage.WATERMARK,
                    cause = PostProcessFailureCause.ENCODE,
                    integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
                    disposition = PostProcessFailureDisposition.UNRECOVERABLE
                )
            )
        )
        assertEquals(
            MediaTransactionStatus.PARTIAL_SUCCESS,
            result.toTransactionResult().status
        )
    }

    @Test
    fun `transaction status SUCCESS when no structured failures and no pipeline failure notes`() {
        val result = baseResult().copy(
            pipelineNotes = listOf("selfie-mirror:applied")
        )
        assertEquals(
            MediaTransactionStatus.SUCCESS,
            result.toTransactionResult().status
        )
    }

    @Test
    fun `transaction status PARTIAL_SUCCESS for legacy pipeline notes when structured empty`() {
        val result = baseResult().copy(
            pipelineNotes = listOf("watermark:failed:encode-oom")
        )
        assertEquals(
            MediaTransactionStatus.PARTIAL_SUCCESS,
            result.toTransactionResult().status
        )
    }

    @Test
    fun `transaction status prefers structured failures over pipeline notes`() {
        val result = baseResult().copy(
            structuredPostProcessFailures = listOf(
                PostProcessFailure(
                    stage = PostProcessFailureStage.ALGORITHM,
                    cause = PostProcessFailureCause.OUT_OF_MEMORY,
                    integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
                    disposition = PostProcessFailureDisposition.UNRECOVERABLE
                )
            ),
            pipelineNotes = listOf("selfie-mirror:applied")
        )
        assertEquals(
            MediaTransactionStatus.PARTIAL_SUCCESS,
            result.toTransactionResult().status
        )
    }

    // ── Typed-legacy projection equivalence for covered cases ──────────────

    @Test
    fun `projected selfie mirror decode note matches legacy string format`() {
        val structured = PostProcessFailure(
            stage = PostProcessFailureStage.SELFIE_MIRROR,
            cause = PostProcessFailureCause.DECODE_FAILED,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.RECOVERABLE
        )
        val legacyNote = "selfie-mirror:failed:decode-failed"
        assertEquals(legacyNote, structured.toLegacyNote())
    }

    @Test
    fun `projected watermark out-of-memory note matches legacy string format`() {
        val structured = PostProcessFailure(
            stage = PostProcessFailureStage.WATERMARK,
            cause = PostProcessFailureCause.OUT_OF_MEMORY,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE
        )
        val legacyNote = "watermark:failed:out-of-memory"
        assertEquals(legacyNote, structured.toLegacyNote())
    }

    @Test
    fun `projected algorithm style exception note matches legacy pattern`() {
        val structured = PostProcessFailure(
            stage = PostProcessFailureStage.ALGORITHM,
            cause = PostProcessFailureCause.BITMAP_OPERATION,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE
        )
        assertTrue(structured.toLegacyNote().contains("algorithm-render:failed:"))
    }

    @Test
    fun `projected portrait render output unavailable note matches legacy pattern`() {
        val structured = PostProcessFailure(
            stage = PostProcessFailureStage.PORTRAIT_RENDER,
            cause = PostProcessFailureCause.OUTPUT_UNAVAILABLE,
            integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE
        )
        assertTrue(structured.toLegacyNote().contains("portrait-render:failed:output-unavailable"))
    }

    // ── No giant enum: dimensions are separate data class fields ───────────

    @Test
    fun `PostProcessFailure is a data class with orthogonal fields`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.FRAME_RATIO,
            cause = PostProcessFailureCause.BITMAP_OPERATION,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.RECOVERABLE
        )
        val copy = failure.copy(integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED)

        assertEquals(PostProcessOutputIntegrity.POSSIBLY_MODIFIED, copy.integrity)
        assertEquals(PostProcessFailureStage.FRAME_RATIO, copy.stage)
        assertEquals(PostProcessFailureCause.BITMAP_OPERATION, copy.cause)
        assertEquals(PostProcessFailureDisposition.RECOVERABLE, copy.disposition)
    }

    @Test
    fun `failure stages cover all processor families plus composite`() {
        val stages = PostProcessFailureStage.entries
        assertEquals(7, stages.size, "Expected 6 processor families + composite")
        assertTrue(stages.contains(PostProcessFailureStage.SELFIE_MIRROR))
        assertTrue(stages.contains(PostProcessFailureStage.WATERMARK))
        assertTrue(stages.contains(PostProcessFailureStage.ALGORITHM))
        assertTrue(stages.contains(PostProcessFailureStage.PORTRAIT_RENDER))
        assertTrue(stages.contains(PostProcessFailureStage.FRAME_RATIO))
        assertTrue(stages.contains(PostProcessFailureStage.DOCUMENT_AUTO_CROP))
        assertTrue(stages.contains(PostProcessFailureStage.COMPOSITE))
    }

    @Test
    fun `failure causes cover all characterized failure categories`() {
        val causes = PostProcessFailureCause.entries
        assertEquals(7, causes.size)
        assertTrue(causes.contains(PostProcessFailureCause.DECODE_FAILED))
        assertTrue(causes.contains(PostProcessFailureCause.OUT_OF_MEMORY))
        assertTrue(causes.contains(PostProcessFailureCause.BITMAP_OPERATION))
        assertTrue(causes.contains(PostProcessFailureCause.ENCODE))
        assertTrue(causes.contains(PostProcessFailureCause.OUTPUT_UNAVAILABLE))
        assertTrue(causes.contains(PostProcessFailureCause.EXCEPTION))
        assertTrue(causes.contains(PostProcessFailureCause.TIMEOUT))
    }

    // ── TIMEOUT cause ────────────────────────────────────────────────────

    @Test
    fun `TIMEOUT cause projects legacy note suffix correctly`() {
        assertEquals("timeout", PostProcessFailureCause.TIMEOUT.legacyNoteSuffix)
    }

    @Test
    fun `timeout failure legacy note is greppable`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.COMPOSITE,
            cause = PostProcessFailureCause.TIMEOUT,
            integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
            disposition = PostProcessFailureDisposition.RECOVERABLE,
            processorName = "SlowProcessor"
        )
        val note = failure.toLegacyNote()
        assertTrue(note.contains(":failed:"))
        assertEquals("postprocess:failed:SlowProcessor:composite-failure", note)
    }

    // ── Helper function ────────────────────────────────────────────────────

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
}
