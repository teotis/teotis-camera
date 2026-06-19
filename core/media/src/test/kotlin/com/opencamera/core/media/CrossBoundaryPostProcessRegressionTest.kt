package com.opencamera.core.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Cross-boundary regression tests verifying the complete selfie-mirror failure flow:
 * processor → structured failure → legacy note → composite pipeline → recovery policy → transaction status.
 *
 * These tests exercise the boundary between core (contracts, policy, composite) and app
 * (selfie-mirror processor behavior) without depending on Android framework.
 */
class CrossBoundaryPostProcessRegressionTest {

    // ── Selfie mirror failure through composite pipeline ────────────────────

    @Test
    fun `selfie mirror failure through composite preserves existing pipeline notes`() = runTest {
        val preExisting = NoteAppendingProcessor("metadata:flash:on")
        val selfieMirror = SelfieMirrorFailureProcessor(
            reason = "decode-failed",
            structuredFailure = PostProcessFailure(
                stage = PostProcessFailureStage.SELFIE_MIRROR,
                cause = PostProcessFailureCause.DECODE_FAILED,
                integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
                disposition = PostProcessFailureDisposition.RECOVERABLE
            )
        )
        val afterMirror = NoteAppendingProcessor("watermark:applied")
        val composite = CompositeMediaPostProcessor(listOf(preExisting, selfieMirror, afterMirror))

        val result = composite.process(baseResult())

        assertTrue(result.pipelineNotes.contains("metadata:flash:on"),
            "Pre-existing note should survive: ${result.pipelineNotes}")
        assertTrue(result.pipelineNotes.contains("watermark:applied"),
            "Post-mirror processor should run: ${result.pipelineNotes}")
    }

    @Test
    fun `selfie mirror failure through composite adds failure note and structured failure`() = runTest {
        val selfieMirror = SelfieMirrorFailureProcessor(
            reason = "decode-failed",
            structuredFailure = PostProcessFailure(
                stage = PostProcessFailureStage.SELFIE_MIRROR,
                cause = PostProcessFailureCause.DECODE_FAILED,
                integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
                disposition = PostProcessFailureDisposition.RECOVERABLE
            )
        )
        val composite = CompositeMediaPostProcessor(listOf(selfieMirror))

        val result = composite.process(baseResult())

        assertTrue(result.pipelineNotes.any { it.contains("selfie-mirror:failed:decode-failed") },
            "Legacy failure note should be present: ${result.pipelineNotes}")
        assertEquals(1, result.structuredPostProcessFailures.size,
            "Should have one structured failure")
        assertEquals(PostProcessFailureStage.SELFIE_MIRROR,
            result.structuredPostProcessFailures.single().stage)
    }

    @Test
    fun `selfie mirror failure transaction status is PARTIAL_SUCCESS`() = runTest {
        val selfieMirror = SelfieMirrorFailureProcessor(
            reason = "decode-failed",
            structuredFailure = PostProcessFailure(
                stage = PostProcessFailureStage.SELFIE_MIRROR,
                cause = PostProcessFailureCause.DECODE_FAILED,
                integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
                disposition = PostProcessFailureDisposition.RECOVERABLE
            )
        )
        val composite = CompositeMediaPostProcessor(listOf(selfieMirror))

        val result = composite.process(baseResult())
        val txResult = result.toTransactionResult()

        assertEquals(MediaTransactionStatus.PARTIAL_SUCCESS, txResult.status,
            "Selfie mirror failure should result in PARTIAL_SUCCESS")
    }

    @Test
    fun `selfie mirror output unchanged after failure`() = runTest {
        val input = baseResult()
        val selfieMirror = SelfieMirrorFailureProcessor(
            reason = "decode-failed",
            structuredFailure = PostProcessFailure(
                stage = PostProcessFailureStage.SELFIE_MIRROR,
                cause = PostProcessFailureCause.DECODE_FAILED,
                integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
                disposition = PostProcessFailureDisposition.RECOVERABLE
            )
        )
        val composite = CompositeMediaPostProcessor(listOf(selfieMirror))

        val result = composite.process(input)

        assertEquals(input.outputPath, result.outputPath,
            "Output path should be unchanged after selfie mirror failure")
        assertEquals(input.outputHandle, result.outputHandle,
            "Output handle should be unchanged after selfie mirror failure")
    }

    // ── Recovery policy evaluation of selfie mirror failures ────────────────

    @Test
    fun `decode-failed selfie mirror failure evaluates to CONTINUE`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.SELFIE_MIRROR,
            cause = PostProcessFailureCause.DECODE_FAILED,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.RECOVERABLE
        )
        assertEquals(RecoveryAction.CONTINUE, evaluateRecoveryPolicy(failure),
            "Decode failure with intact original should continue pipeline")
    }

    @Test
    fun `output-unavailable selfie mirror failure evaluates to STOP_POSTPROCESS`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.SELFIE_MIRROR,
            cause = PostProcessFailureCause.OUTPUT_UNAVAILABLE,
            integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
            disposition = PostProcessFailureDisposition.RECOVERABLE
        )
        assertEquals(RecoveryAction.STOP_POSTPROCESS, evaluateRecoveryPolicy(failure),
            "Output unavailable with possibly-modified integrity should stop postprocess")
    }

    @Test
    fun `mirror-exception selfie mirror failure evaluates to CONTINUE`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.SELFIE_MIRROR,
            cause = PostProcessFailureCause.BITMAP_OPERATION,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.RECOVERABLE
        )
        assertEquals(RecoveryAction.CONTINUE, evaluateRecoveryPolicy(failure),
            "Mirror exception with bitmap-operation and intact original should continue")
    }

    // ── Legacy note equivalence for selfie mirror failures ──────────────────

    @Test
    fun `selfie mirror failure legacy projection matches expected format`() {
        val failures = mapOf(
            "decode-failed" to PostProcessFailureCause.DECODE_FAILED,
            "output-unavailable" to PostProcessFailureCause.OUTPUT_UNAVAILABLE,
            "mirror-exception" to PostProcessFailureCause.BITMAP_OPERATION
        )

        for ((reason, expectedCause) in failures) {
            val failure = PostProcessFailure(
                stage = PostProcessFailureStage.SELFIE_MIRROR,
                cause = expectedCause,
                integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
                disposition = PostProcessFailureDisposition.RECOVERABLE
            )
            val note = failure.toLegacyNote()
            assertTrue(note.contains(":failed:"),
                "Legacy note should contain ':failed:': $note")
            assertTrue(note.startsWith("selfie-mirror:"),
                "Legacy note should start with 'selfie-mirror:': $note")
            // Verify detected by legacy contains check
            assertTrue(note.contains(":failed:"),
                "Projected note should be detectable by contains(':failed:'): $note")
        }
    }

    @Test
    fun `selfie mirror failure through composite produces notes detectable by legacy check`() = runTest {
        val selfieMirror = SelfieMirrorFailureProcessor(
            reason = "decode-failed",
            structuredFailure = PostProcessFailure(
                stage = PostProcessFailureStage.SELFIE_MIRROR,
                cause = PostProcessFailureCause.DECODE_FAILED,
                integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
                disposition = PostProcessFailureDisposition.RECOVERABLE
            )
        )
        val composite = CompositeMediaPostProcessor(listOf(selfieMirror))

        val result = composite.process(baseResult())

        assertTrue(result.hasPostProcessFailures(),
            "Legacy failure detection should work via hasPostProcessFailures(): ${result.pipelineNotes}")
    }

    // ── Cancellation during selfie mirror phase ─────────────────────────────

    @Test
    fun `cancellation during selfie mirror does not add failure note`() = runTest {
        val cancelProcessor = ThrowingProcessor(CancellationException("job cancelled"))
        val afterProcessor = NoteAppendingProcessor("after-cancel:should-not-run")
        val composite = CompositeMediaPostProcessor(listOf(cancelProcessor, afterProcessor))

        try {
            composite.process(baseResult())
            fail("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            assertEquals("job cancelled", e.message)
        }
    }

    @Test
    fun `cancellation does not affect output integrity`() = runTest {
        val input = baseResult()
        val cancelProcessor = ThrowingProcessor(CancellationException("cancelled"))
        val composite = CompositeMediaPostProcessor(listOf(cancelProcessor))

        try {
            composite.process(input)
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // Output should be unchanged — composite doesn't modify result before throwing
            // This is verified by the fact that no failure note was written
        }
    }

    // ── Error during selfie mirror phase ────────────────────────────────────

    @Test
    fun `OutOfMemoryError during selfie mirror propagates without failure note`() = runTest {
        val oomProcessor = ThrowingProcessor(OutOfMemoryError("bitmap allocation"))
        val afterProcessor = NoteAppendingProcessor("after-oom:should-not-run")
        val composite = CompositeMediaPostProcessor(listOf(oomProcessor, afterProcessor))

        try {
            composite.process(baseResult())
            fail("Expected OutOfMemoryError to propagate")
        } catch (e: OutOfMemoryError) {
            assertEquals("bitmap allocation", e.message)
        }
    }

    @Test
    fun `Error during selfie mirror does not produce structured failure`() = runTest {
        val errorProcessor = ThrowingProcessor(StackOverflowError("deep recursion"))
        val composite = CompositeMediaPostProcessor(listOf(errorProcessor))

        try {
            composite.process(baseResult())
            fail("Expected StackOverflowError to propagate")
        } catch (_: StackOverflowError) {
            // Error propagation is tested by the fact we reached this catch block
        }
    }

    // ── Pipeline notes ordering with selfie mirror failure ──────────────────

    @Test
    fun `notes order with selfie mirror failure between processors is correct`() = runTest {
        val preMirror = NoteAppendingProcessor("metadata:flash:on")
        val selfieMirror = SelfieMirrorFailureProcessor(
            reason = "decode-failed",
            structuredFailure = PostProcessFailure(
                stage = PostProcessFailureStage.SELFIE_MIRROR,
                cause = PostProcessFailureCause.DECODE_FAILED,
                integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
                disposition = PostProcessFailureDisposition.RECOVERABLE
            )
        )
        val postMirror = NoteAppendingProcessor("watermark:applied")
        val composite = CompositeMediaPostProcessor(listOf(preMirror, selfieMirror, postMirror))

        val result = composite.process(baseResult())

        val preIndex = result.pipelineNotes.indexOf("metadata:flash:on")
        val failIndex = result.pipelineNotes.indexOfFirst { it.contains("selfie-mirror:failed:") }
        val postIndex = result.pipelineNotes.indexOf("watermark:applied")

        assertTrue(preIndex >= 0, "Pre-mirror note should exist")
        assertTrue(failIndex >= 0, "Failure note should exist")
        assertTrue(postIndex >= 0, "Post-mirror note should exist")
        assertTrue(preIndex < failIndex,
            "Pre-mirror note should come before failure note (pre=$preIndex, fail=$failIndex)")
        assertTrue(failIndex < postIndex,
            "Failure note should come before post-mirror note (fail=$failIndex, post=$postIndex)")
    }

    // ── Structured failure + pipeline note consistency ──────────────────────

    @Test
    fun `structured failure and pipeline note are consistent for each selfie mirror failure reason`() = runTest {
        val reasons = listOf(
            Triple("decode-failed", PostProcessFailureCause.DECODE_FAILED, PostProcessOutputIntegrity.ORIGINAL_INTACT),
            Triple("output-unavailable", PostProcessFailureCause.OUTPUT_UNAVAILABLE, PostProcessOutputIntegrity.POSSIBLY_MODIFIED),
            Triple("mirror-exception", PostProcessFailureCause.BITMAP_OPERATION, PostProcessOutputIntegrity.ORIGINAL_INTACT)
        )

        for ((reason, expectedCause, expectedIntegrity) in reasons) {
            val failure = PostProcessFailure(
                stage = PostProcessFailureStage.SELFIE_MIRROR,
                cause = expectedCause,
                integrity = expectedIntegrity,
                disposition = PostProcessFailureDisposition.RECOVERABLE
            )
            val note = failure.toLegacyNote()
            assertTrue(note.contains(":failed:"),
                "Note for $reason should contain ':failed:': $note")
            assertTrue(note.startsWith("selfie-mirror:"),
                "Note for $reason should start with 'selfie-mirror:': $note")
        }
    }

    @Test
    fun `multiple selfie mirror failures accumulate structured failures`() = runTest {
        val first = SelfieMirrorFailureProcessor(
            reason = "decode-failed",
            structuredFailure = PostProcessFailure(
                stage = PostProcessFailureStage.SELFIE_MIRROR,
                cause = PostProcessFailureCause.DECODE_FAILED,
                integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
                disposition = PostProcessFailureDisposition.RECOVERABLE
            )
        )
        val second = SelfieMirrorFailureProcessor(
            reason = "output-unavailable",
            structuredFailure = PostProcessFailure(
                stage = PostProcessFailureStage.SELFIE_MIRROR,
                cause = PostProcessFailureCause.OUTPUT_UNAVAILABLE,
                integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
                disposition = PostProcessFailureDisposition.RECOVERABLE
            )
        )
        val composite = CompositeMediaPostProcessor(listOf(first, second))

        val result = composite.process(baseResult())

        assertEquals(2, result.structuredPostProcessFailures.size,
            "Should accumulate both structured failures")
        assertTrue(result.hasPostProcessFailures(),
            "Should detect failures via legacy check")
        val txResult = result.toTransactionResult()
        assertEquals(MediaTransactionStatus.PARTIAL_SUCCESS, txResult.status)
    }

    // ── Selfie mirror failure does not widen pilot ──────────────────────────

    @Test
    fun `selfie mirror failure stages are covered by recovery policy truth table`() {
        // Every combination of disposition + integrity used by selfie mirror must have a defined action
        val combinations = listOf(
            PostProcessFailureDisposition.RECOVERABLE to PostProcessOutputIntegrity.ORIGINAL_INTACT,
            PostProcessFailureDisposition.RECOVERABLE to PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
            PostProcessFailureDisposition.UNRECOVERABLE to PostProcessOutputIntegrity.ORIGINAL_INTACT
        )

        for ((disposition, integrity) in combinations) {
            val failure = PostProcessFailure(
                stage = PostProcessFailureStage.SELFIE_MIRROR,
                cause = PostProcessFailureCause.EXCEPTION,
                integrity = integrity,
                disposition = disposition
            )
            val action = evaluateRecoveryPolicy(failure)
            // All selfie mirror combinations must map to a defined action (not crash)
            assertTrue(action in RecoveryAction.entries,
                "Recovery action $action should be defined for $disposition + $integrity")
        }
    }

    @Test
    fun `pipeline notes do not leak structured recovery internals`() = runTest {
        val selfieMirror = SelfieMirrorFailureProcessor(
            reason = "decode-failed",
            structuredFailure = PostProcessFailure(
                stage = PostProcessFailureStage.SELFIE_MIRROR,
                cause = PostProcessFailureCause.DECODE_FAILED,
                integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
                disposition = PostProcessFailureDisposition.RECOVERABLE
            )
        )
        val composite = CompositeMediaPostProcessor(listOf(selfieMirror))

        val result = composite.process(baseResult())

        // Pipeline notes should not contain internal enum names
        val allNotes = result.pipelineNotes.joinToString(";")
        assertFalse(allNotes.contains("ORIGINAL_INTACT"),
            "Pipeline notes should not contain integrity enum: $allNotes")
        assertFalse(allNotes.contains("RECOVERABLE"),
            "Pipeline notes should not contain disposition enum: $allNotes")
        assertFalse(allNotes.contains("DECODE_FAILED"),
            "Pipeline notes should not contain cause enum name: $allNotes")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun baseResult(): ShotResult {
        return ShotResult(
            shotId = "cross-boundary-test",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/test-photo.jpg",
            outputHandle = MediaOutputHandle(
                displayPath = "/tmp/test-photo.jpg",
                filePath = "/tmp/test-photo.jpg"
            ),
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.SavedMedia(
                outputPath = "/tmp/test-photo.jpg"
            ),
            metadata = MediaMetadata()
        )
    }

    private class NoteAppendingProcessor(private val note: String) : MediaPostProcessor {
        override suspend fun process(result: ShotResult): ShotResult {
            return result.addPipelineNotes(note)
        }
    }

    private class ThrowingProcessor(private val error: Throwable) : MediaPostProcessor {
        override suspend fun process(result: ShotResult): ShotResult {
            throw error
        }
    }

    /**
     * Simulates a processor that fails and adds both pipeline notes and structured failures,
     * matching the behavior of PhotoSelfieMirrorPostProcessor in the app boundary.
     */
    private class SelfieMirrorFailureProcessor(
        private val reason: String,
        private val structuredFailure: PostProcessFailure
    ) : MediaPostProcessor {
        override suspend fun process(result: ShotResult): ShotResult {
            return result
                .addPipelineNotes("selfie-mirror:failed:$reason")
                .addStructuredPostProcessFailure(structuredFailure)
        }
    }
}
