package com.opencamera.core.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class PostProcessRecoveryPolicyTest {

    // ── Policy truth table ────────────────────────────────────────────────────

    @Test
    fun `RECOVERABLE + ORIGINAL_INTACT returns CONTINUE`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.ALGORITHM,
            cause = PostProcessFailureCause.EXCEPTION,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.RECOVERABLE,
            processorName = "TestProcessor"
        )
        assertEquals(RecoveryAction.CONTINUE, evaluateRecoveryPolicy(failure))
    }

    @Test
    fun `RECOVERABLE + POSSIBLY_MODIFIED returns STOP_POSTPROCESS`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.WATERMARK,
            cause = PostProcessFailureCause.BITMAP_OPERATION,
            integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
            disposition = PostProcessFailureDisposition.RECOVERABLE,
            processorName = "TestProcessor"
        )
        assertEquals(RecoveryAction.STOP_POSTPROCESS, evaluateRecoveryPolicy(failure))
    }

    @Test
    fun `RECOVERABLE + UNKNOWN returns STOP_POSTPROCESS`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.WATERMARK,
            cause = PostProcessFailureCause.EXCEPTION,
            integrity = PostProcessOutputIntegrity.UNKNOWN,
            disposition = PostProcessFailureDisposition.RECOVERABLE,
            processorName = "TestProcessor"
        )
        assertEquals(RecoveryAction.STOP_POSTPROCESS, evaluateRecoveryPolicy(failure))
    }

    @Test
    fun `UNRECOVERABLE + ORIGINAL_INTACT returns TERMINATE`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.COMPOSITE,
            cause = PostProcessFailureCause.EXCEPTION,
            integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE,
            processorName = "TestProcessor"
        )
        assertEquals(RecoveryAction.TERMINATE, evaluateRecoveryPolicy(failure))
    }

    @Test
    fun `UNRECOVERABLE + POSSIBLY_MODIFIED returns TERMINATE`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.COMPOSITE,
            cause = PostProcessFailureCause.EXCEPTION,
            integrity = PostProcessOutputIntegrity.POSSIBLY_MODIFIED,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE,
            processorName = "TestProcessor"
        )
        assertEquals(RecoveryAction.TERMINATE, evaluateRecoveryPolicy(failure))
    }

    @Test
    fun `UNRECOVERABLE + UNKNOWN returns PROPAGATE`() {
        val failure = PostProcessFailure(
            stage = PostProcessFailureStage.COMPOSITE,
            cause = PostProcessFailureCause.EXCEPTION,
            integrity = PostProcessOutputIntegrity.UNKNOWN,
            disposition = PostProcessFailureDisposition.UNRECOVERABLE,
            processorName = "TestProcessor"
        )
        assertEquals(RecoveryAction.PROPAGATE, evaluateRecoveryPolicy(failure))
    }

    // ── CancellationException propagation ─────────────────────────────────────

    @Test
    fun `cancellation exception propagates through composite`() = runTest {
        val cancelProcessor = ThrowingProcessor(CancellationException("job cancelled"))
        val composite = CompositeMediaPostProcessor(listOf(cancelProcessor))

        try {
            composite.process(baseResult())
            fail("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            assertEquals("job cancelled", e.message)
        }
    }

    @Test
    fun `cancellation exception from first processor stops pipeline`() = runTest {
        val cancelProcessor = ThrowingProcessor(CancellationException("cancel"))
        val afterProcessor = NoteAppendingProcessor("should-not-run")
        val composite = CompositeMediaPostProcessor(listOf(cancelProcessor, afterProcessor))

        val result = try {
            composite.process(baseResult())
            fail("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            null
        }

        assertEquals(null, result)
    }

    // ── Error propagation ──────────────────────────────────────────────────────

    @Test
    fun `OutOfMemoryError propagates through composite`() = runTest {
        val oomProcessor = ThrowingProcessor(OutOfMemoryError("bitmap allocation"))
        val composite = CompositeMediaPostProcessor(listOf(oomProcessor))

        try {
            composite.process(baseResult())
            fail("Expected OutOfMemoryError to propagate")
        } catch (e: OutOfMemoryError) {
            assertEquals("bitmap allocation", e.message)
        }
    }

    @Test
    fun `StackOverflowError propagates through composite`() = runTest {
        val processor = ThrowingProcessor(StackOverflowError("deep recursion"))
        val composite = CompositeMediaPostProcessor(listOf(processor))

        try {
            composite.process(baseResult())
            fail("Expected StackOverflowError to propagate")
        } catch (e: StackOverflowError) {
            assertEquals("deep recursion", e.message)
        }
    }

    // ── classifyExceptionForRecovery ───────────────────────────────────────────

    @Test
    fun `classifyExceptionForRecovery maps CancellationException to UNRECOVERABLE UNKNOWN`() {
        val failure = classifyExceptionForRecovery(
            CancellationException("cancelled"),
            "TestProcessor"
        )
        assertEquals(PostProcessFailureStage.COMPOSITE, failure.stage)
        assertEquals(PostProcessFailureCause.EXCEPTION, failure.cause)
        assertEquals(PostProcessOutputIntegrity.UNKNOWN, failure.integrity)
        assertEquals(PostProcessFailureDisposition.UNRECOVERABLE, failure.disposition)
        assertEquals("TestProcessor", failure.processorName)
    }

    @Test
    fun `classifyExceptionForRecovery maps OutOfMemoryError to UNRECOVERABLE`() {
        val failure = classifyExceptionForRecovery(
            OutOfMemoryError("alloc failed"),
            "TestProcessor"
        )
        assertEquals(PostProcessFailureCause.OUT_OF_MEMORY, failure.cause)
        assertEquals(PostProcessOutputIntegrity.UNKNOWN, failure.integrity)
        assertEquals(PostProcessFailureDisposition.UNRECOVERABLE, failure.disposition)
    }

    @Test
    fun `classifyExceptionForRecovery maps RuntimeException to RECOVERABLE`() {
        val failure = classifyExceptionForRecovery(
            RuntimeException("something broke"),
            "TestProcessor"
        )
        assertEquals(PostProcessFailureDisposition.RECOVERABLE, failure.disposition)
        assertEquals(PostProcessOutputIntegrity.POSSIBLY_MODIFIED, failure.integrity)
    }

    @Test
    fun `classifyExceptionForRecovery detects decode in message`() {
        val failure = classifyExceptionForRecovery(
            RuntimeException("decode failed for input"),
            "TestProcessor"
        )
        assertEquals(PostProcessFailureCause.DECODE_FAILED, failure.cause)
    }

    @Test
    fun `classifyExceptionForRecovery detects encode in message`() {
        val failure = classifyExceptionForRecovery(
            RuntimeException("encode operation failed"),
            "TestProcessor"
        )
        assertEquals(PostProcessFailureCause.ENCODE, failure.cause)
    }

    @Test
    fun `classifyExceptionForRecovery detects bitmap in message`() {
        val failure = classifyExceptionForRecovery(
            RuntimeException("bitmap too large"),
            "TestProcessor"
        )
        assertEquals(PostProcessFailureCause.BITMAP_OPERATION, failure.cause)
    }

    @Test
    fun `classifyExceptionForRecovery uses stage COMPOSITE`() {
        val failure = classifyExceptionForRecovery(
            RuntimeException("any error"),
            "AnyProcessor"
        )
        assertEquals(PostProcessFailureStage.COMPOSITE, failure.stage)
    }

    // ── Policy applied via classify → evaluate round-trip ─────────────────────

    @Test
    fun `RuntimeException classified and evaluated as CONTINUE when ORIGINAL_INTACT`() {
        val classified = classifyExceptionForRecovery(
            RuntimeException("boom"),
            "TestProcessor"
        )
        // Override integrity for testing the round-trip
        val failure = classified.copy(integrity = PostProcessOutputIntegrity.ORIGINAL_INTACT)
        assertEquals(RecoveryAction.CONTINUE, evaluateRecoveryPolicy(failure))
    }

    // ── TIMEOUT recovery policy ───────────────────────────────────────────────

    @Test
    fun `evaluateTimeoutPolicy returns RECOVER_RELEASE`() {
        assertEquals(RecoveryAction.RECOVER_RELEASE, evaluateTimeoutPolicy())
    }

    @Test
    fun `core evaluateRecoveryPolicy never returns RECOVER_RELEASE`() {
        // RECOVER_RELEASE is only for timeout — the core policy doesn't return it
        val combinations = listOf(
            Triple(PostProcessFailureDisposition.RECOVERABLE, PostProcessOutputIntegrity.ORIGINAL_INTACT, RecoveryAction.CONTINUE),
            Triple(PostProcessFailureDisposition.RECOVERABLE, PostProcessOutputIntegrity.POSSIBLY_MODIFIED, RecoveryAction.STOP_POSTPROCESS),
            Triple(PostProcessFailureDisposition.RECOVERABLE, PostProcessOutputIntegrity.UNKNOWN, RecoveryAction.STOP_POSTPROCESS),
            Triple(PostProcessFailureDisposition.UNRECOVERABLE, PostProcessOutputIntegrity.ORIGINAL_INTACT, RecoveryAction.TERMINATE),
            Triple(PostProcessFailureDisposition.UNRECOVERABLE, PostProcessOutputIntegrity.POSSIBLY_MODIFIED, RecoveryAction.TERMINATE),
            Triple(PostProcessFailureDisposition.UNRECOVERABLE, PostProcessOutputIntegrity.UNKNOWN, RecoveryAction.PROPAGATE)
        )
        for ((disposition, integrity, expected) in combinations) {
            val failure = PostProcessFailure(
                stage = PostProcessFailureStage.COMPOSITE,
                cause = PostProcessFailureCause.TIMEOUT,
                integrity = integrity,
                disposition = disposition,
                processorName = "Test"
            )
            val action = evaluateRecoveryPolicy(failure)
            assertEquals(
                expected, action,
                "evaluateRecoveryPolicy($disposition, $integrity) should be $expected, got $action"
            )
            assertTrue(
                action != RecoveryAction.RECOVER_RELEASE,
                "Core policy should never return RECOVER_RELEASE"
            )
        }
    }

    // ── RECOVER_RELEASE is in RecoveryAction enum ─────────────────────────

    @Test
    fun `RecoveryAction entries include RECOVER_RELEASE`() {
        assertTrue(RecoveryAction.entries.contains(RecoveryAction.RECOVER_RELEASE))
    }

    // ── Ordinary exception behavior (backward compatibility) ───────────────────

    @Test
    fun `ordinary exception stops pipeline with failure note`() = runTest {
        val throwing = ThrowingProcessor(RuntimeException("boom"))
        val after = NoteAppendingProcessor("after-failure:applied")
        val composite = CompositeMediaPostProcessor(listOf(throwing, after))

        val result = composite.process(baseResult())

        assertTrue(result.pipelineNotes.any { it.contains(":failed:") })
        assertFalse(
            result.pipelineNotes.contains("after-failure:applied"),
            "Downstream processor should not run after STOP_POSTPROCESS"
        )
    }

    @Test
    fun `ordinary exception preserves output path`() = runTest {
        val input = baseResult()
        val throwing = ThrowingProcessor(RuntimeException("partial"))
        val composite = CompositeMediaPostProcessor(listOf(throwing))

        val result = composite.process(input)

        assertEquals(input.outputPath, result.outputPath)
        assertEquals(input.outputHandle, result.outputHandle)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

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
}
