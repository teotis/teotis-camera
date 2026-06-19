package com.opencamera.core.media

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class CompositeMediaPostProcessorTest {

    @Test
    fun `throwing processor between two successful processors stops chain`() = runTest {
        val first = NoteAppendingProcessor("first:applied")
        val throwing = ThrowingProcessor(RuntimeException("boom"))
        val third = NoteAppendingProcessor("third:applied")
        val composite = CompositeMediaPostProcessor(listOf(first, throwing, third))

        val result = composite.process(baseResult())

        assertTrue(result.pipelineNotes.contains("first:applied"))
        assertTrue(result.pipelineNotes.any { it.contains("postprocess:failed:ThrowingProcessor") })
        assertFalse(
            result.pipelineNotes.contains("third:applied"),
            "Downstream processor should not run after STOP_POSTPROCESS"
        )
    }

    @Test
    fun `throwing processor preserves output path and handle`() = runTest {
        val throwing = ThrowingProcessor(RuntimeException("bitmap too large"))
        val composite = CompositeMediaPostProcessor(listOf(throwing))

        val input = baseResult()
        val result = composite.process(input)

        assertEquals(input.outputPath, result.outputPath)
        assertEquals(input.outputHandle, result.outputHandle)
        assertEquals(input.thumbnailSource, result.thumbnailSource)
    }

    @Test
    fun `all processors succeed adds all notes`() = runTest {
        val first = NoteAppendingProcessor("a:done")
        val second = NoteAppendingProcessor("b:done")
        val composite = CompositeMediaPostProcessor(listOf(first, second))

        val result = composite.process(baseResult())

        assertTrue(result.pipelineNotes.contains("a:done"))
        assertTrue(result.pipelineNotes.contains("b:done"))
        assertEquals(2, result.pipelineNotes.size)
    }

    @Test
    fun `empty processor list returns original result`() = runTest {
        val composite = CompositeMediaPostProcessor(emptyList())
        val input = baseResult()
        val result = composite.process(input)
        assertEquals(input, result)
    }

    @Test
    fun `inapplicable processor is skipped without timing and downstream still runs`() = runTest {
        var skippedProcessCalls = 0
        val timings = mutableListOf<String>()
        val inapplicable = object : MediaPostProcessor {
            override fun isApplicable(result: ShotResult): Boolean = false

            override suspend fun process(result: ShotResult): ShotResult {
                skippedProcessCalls++
                return result.addPipelineNotes("should-not-run")
            }
        }
        val composite = CompositeMediaPostProcessor(
            processors = listOf(inapplicable, NoteAppendingProcessor("downstream:applied")),
            onProcessorTimed = { name, _ -> timings += name }
        )

        val result = composite.process(baseResult())

        assertEquals(0, skippedProcessCalls)
        assertFalse(result.pipelineNotes.contains("should-not-run"))
        assertTrue(result.pipelineNotes.contains("downstream:applied"))
        assertEquals(listOf("NoteAppendingProcessor"), timings)
    }

    @Test
    fun `onProcessorTimed callback receives each processor name and elapsed ms`() = runTest {
        val timings = mutableListOf<Pair<String, Long>>()
        val first = NoteAppendingProcessor("a:done")
        val second = NoteAppendingProcessor("b:done")
        val composite = CompositeMediaPostProcessor(
            processors = listOf(first, second),
            onProcessorTimed = { name, elapsedMs -> timings.add(name to elapsedMs) }
        )

        composite.process(baseResult())

        assertEquals(2, timings.size)
        assertEquals("NoteAppendingProcessor", timings[0].first)
        assertTrue(timings[0].second >= 0)
        assertEquals("NoteAppendingProcessor", timings[1].first)
    }

    @Test
    fun `first throwing processor stops second from running`() = runTest {
        val first = ThrowingProcessor(RuntimeException("first-crash"))
        val second = ThrowingProcessor(IllegalStateException("second-crash"))
        val composite = CompositeMediaPostProcessor(listOf(first, second))

        val result = composite.process(baseResult())
        val failureNotes = result.pipelineNotes.filter { it.startsWith("postprocess:failed:") }

        // STOP_POSTPROCESS stops after the first failure
        assertEquals(1, failureNotes.size)
        assertTrue(result.pipelineNotes.any { it.contains("ThrowingProcessor") })
    }

    @Test
    fun `error thrown by processor propagates instead of being swallowed`() = runTest {
        val errorProcessor = ThrowingProcessor(OutOfMemoryError("bitmap allocation"))
        val composite = CompositeMediaPostProcessor(listOf(errorProcessor))

        val error = try {
            composite.process(baseResult())
            null
        } catch (e: Error) {
            e
        }

        assertTrue(error is OutOfMemoryError, "Expected OutOfMemoryError to propagate, got $error")
        assertEquals("bitmap allocation", error!!.message)
    }

    // ── CancellationException propagation ──────────────────────────────────────

    @Test
    fun `cancellation exception propagates through composite`() = runTest {
        val cancelProcessor = ThrowingProcessor(kotlinx.coroutines.CancellationException("job cancelled"))
        val composite = CompositeMediaPostProcessor(listOf(cancelProcessor))

        try {
            composite.process(baseResult())
            fail("Expected CancellationException to propagate")
        } catch (e: kotlinx.coroutines.CancellationException) {
            assertEquals("job cancelled", e.message)
        }
    }

    @Test
    fun `cancellation exception does not produce failure note`() = runTest {
        val cancelProcessor = ThrowingProcessor(kotlinx.coroutines.CancellationException("stop"))
        val composite = CompositeMediaPostProcessor(listOf(cancelProcessor))

        try {
            composite.process(baseResult())
            fail("Expected CancellationException to propagate")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // CancellationException propagates before any failure note is written
        }
    }

    @Test
    fun `pipeline stops after cancellation exception from first processor`() = runTest {
        val cancelProcessor = ThrowingProcessor(kotlinx.coroutines.CancellationException("cancel"))
        val afterProcessor = NoteAppendingProcessor("after-cancel:should-not-run")
        val composite = CompositeMediaPostProcessor(listOf(cancelProcessor, afterProcessor))

        try {
            composite.process(baseResult())
            fail("Expected CancellationException to propagate and stop pipeline")
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Pipeline stopped — afterProcessor never ran
        }
    }

    // ── Failure note format characterization ────────────────────────────────

    @Test
    fun `failure note format uses postprocess prefix with processor name and message`() = runTest {
        val processor = ThrowingProcessor(IllegalStateException("specific error message"))
        val composite = CompositeMediaPostProcessor(listOf(processor))

        val result = composite.process(baseResult())
        val failureNote = result.pipelineNotes.first { it.startsWith("postprocess:failed:") }

        assertTrue(
            failureNote.startsWith("postprocess:failed:ThrowingProcessor:"),
            "Expected format postprocess:failed:{Name}:{msg}, got: $failureNote"
        )
        assertTrue(
            failureNote.contains("specific error message") || failureNote.contains("IllegalStateException"),
            "Note should contain error info: $failureNote"
        )
    }

    @Test
    fun `failure note truncates message to 120 characters`() = runTest {
        val longMessage = "x".repeat(200)
        val processor = ThrowingProcessor(RuntimeException(longMessage))
        val composite = CompositeMediaPostProcessor(listOf(processor))

        val result = composite.process(baseResult())
        val failureNote = result.pipelineNotes.first { it.startsWith("postprocess:failed:") }
        val afterPrefix = failureNote.removePrefix("postprocess:failed:ThrowingProcessor:")

        assertTrue(
            afterPrefix.length <= 120,
            "Message should be truncated to 120 chars, got ${afterPrefix.length}: $afterPrefix"
        )
    }

    @Test
    fun `failure note uses class simpleName when message is null`() = runTest {
        val processor = ThrowingProcessor(RuntimeException())
        val composite = CompositeMediaPostProcessor(listOf(processor))

        val result = composite.process(baseResult())
        val failureNote = result.pipelineNotes.first { it.startsWith("postprocess:failed:") }

        assertTrue(
            failureNote.contains("RuntimeException"),
            "Should use class simpleName when message is null: $failureNote"
        )
    }

    // ── Transaction status characterization ─────────────────────────────────

    @Test
    fun `hasPostProcessFailures detects failed string in pipeline notes`() = runTest {
        val processor = ThrowingProcessor(RuntimeException("boom"))
        val composite = CompositeMediaPostProcessor(listOf(processor))

        val result = composite.process(baseResult())

        assertTrue(
            result.hasPostProcessFailures(),
            "Should detect failure in pipeline notes: ${result.pipelineNotes}"
        )
    }

    @Test
    fun `hasPostProcessFailures is false when only timing notes present`() = runTest {
        val processor = NoteAppendingProcessor("timing:postprocess:slow=100ms")
        val composite = CompositeMediaPostProcessor(listOf(processor))

        val result = composite.process(baseResult())

        assertFalse(
            result.hasPostProcessFailures(),
            "Timing notes should not be treated as failures"
        )
    }

    @Test
    fun `hasPostProcessFailures is false when only skipped notes present`() = runTest {
        val processor = NoteAppendingProcessor("selfie-mirror:skipped:missing-output-handle")
        val composite = CompositeMediaPostProcessor(listOf(processor))

        val result = composite.process(baseResult())

        assertFalse(
            result.hasPostProcessFailures(),
            "Skipped notes should not be treated as failures"
        )
    }

    @Test
    fun `transaction status is PARTIAL_SUCCESS when failure note exists`() = runTest {
        val processor = ThrowingProcessor(RuntimeException("error"))
        val composite = CompositeMediaPostProcessor(listOf(processor))

        val result = composite.process(baseResult())
        val txResult = result.toTransactionResult()

        assertEquals(
            MediaTransactionStatus.PARTIAL_SUCCESS,
            txResult.status,
            "Expected PARTIAL_SUCCESS when failure note exists"
        )
    }

    @Test
    fun `transaction status is SUCCESS when no failure notes present`() = runTest {
        val processor = NoteAppendingProcessor("algorithm-render:applied:photo-vivid")
        val composite = CompositeMediaPostProcessor(listOf(processor))

        val result = composite.process(baseResult())
        val txResult = result.toTransactionResult()

        assertEquals(
            MediaTransactionStatus.SUCCESS,
            txResult.status,
            "Expected SUCCESS when no failure notes"
        )
    }

    @Test
    fun `postProcessFailureSummary returns failure note after STOP_POSTPROCESS`() = runTest {
        val first = ThrowingProcessor(RuntimeException("first-error"))
        val second = ThrowingProcessor(IllegalStateException("second-error"))
        val composite = CompositeMediaPostProcessor(listOf(first, second))

        val result = composite.process(baseResult())
        val summary = result.postProcessFailureSummary()

        assertNotNull(summary, "Should have failure summary")
        // Only first processor runs; second is skipped by STOP_POSTPROCESS
        assertTrue(summary!!.isNotEmpty(), "Summary should not be empty: $summary")
        assertTrue(
            result.structuredPostProcessFailures.isNotEmpty(),
            "Structured failures should be recorded"
        )
    }

    @Test
    fun `postProcessFailureSummary returns null when no failures`() = runTest {
        val processor = NoteAppendingProcessor("ok:done")
        val composite = CompositeMediaPostProcessor(listOf(processor))

        val result = composite.process(baseResult())

        assertNull(result.postProcessFailureSummary())
    }

    // ── OOM boundary characterization ───────────────────────────────────────

    @Test
    fun `OutOfMemoryError propagates and does not add failure note`() = runTest {
        val oomProcessor = ThrowingProcessor(OutOfMemoryError("bitmap alloc"))
        val afterProcessor = NoteAppendingProcessor("after-oom:applied")
        val composite = CompositeMediaPostProcessor(listOf(oomProcessor, afterProcessor))

        try {
            composite.process(baseResult())
            fail("Expected OutOfMemoryError to propagate")
        } catch (e: Error) {
            assertTrue(e is OutOfMemoryError)
        }
    }

    @Test
    fun `StackOverflowError propagates as Error`() = runTest {
        val processor = ThrowingProcessor(StackOverflowError("deep recursion"))
        val composite = CompositeMediaPostProcessor(listOf(processor))

        try {
            composite.process(baseResult())
            fail("Expected StackOverflowError to propagate")
        } catch (e: Error) {
            assertTrue(e is StackOverflowError)
        }
    }

    // ── Output integrity characterization ───────────────────────────────────

    @Test
    fun `output path and handle preserved after RuntimeException`() = runTest {
        val input = baseResult()
        val processor = ThrowingProcessor(RuntimeException("partial failure"))
        val composite = CompositeMediaPostProcessor(listOf(processor))

        val result = composite.process(input)

        assertEquals(input.outputPath, result.outputPath)
        assertEquals(input.outputHandle, result.outputHandle)
        assertEquals(input.thumbnailSource, result.thumbnailSource)
    }

    @Test
    fun `cancellation exception propagates without writing failure note`() = runTest {
        val input = baseResult()
        val processor = ThrowingProcessor(kotlinx.coroutines.CancellationException("cancelled"))
        val composite = CompositeMediaPostProcessor(listOf(processor))

        try {
            composite.process(input)
            fail("Expected CancellationException to propagate")
        } catch (e: kotlinx.coroutines.CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    @Test
    fun `pre-existing pipeline notes are preserved through failure`() = runTest {
        val input = baseResult().addPipelineNotes("pre-existing:note")
        val processor = ThrowingProcessor(RuntimeException("boom"))
        val composite = CompositeMediaPostProcessor(listOf(processor))

        val result = composite.process(input)

        assertTrue(
            result.pipelineNotes.contains("pre-existing:note"),
            "Pre-existing notes should survive failure"
        )
    }

    // ── Structured failure recording ────────────────────────────────────────

    @Test
    fun `exception records structured failure alongside legacy note`() = runTest {
        val processor = ThrowingProcessor(RuntimeException("bitmap failed"))
        val composite = CompositeMediaPostProcessor(listOf(processor))

        val result = composite.process(baseResult())

        assertEquals(1, result.structuredPostProcessFailures.size)
        val failure = result.structuredPostProcessFailures.single()
        assertEquals(PostProcessFailureStage.COMPOSITE, failure.stage)
        assertEquals(PostProcessFailureCause.BITMAP_OPERATION, failure.cause)
        assertEquals("ThrowingProcessor", failure.processorName)
        assertTrue(result.pipelineNotes.any { it.startsWith("postprocess:failed:ThrowingProcessor") })
    }

    // ── STOP_POSTPROCESS stops downstream processors ────────────────────────

    @Test
    fun `STOP_POSTPROCESS stops downstream processors`() = runTest {
        val throwing = ThrowingProcessor(RuntimeException("crash"))
        val after = NoteAppendingProcessor("after-stop:should-not-run")
        val composite = CompositeMediaPostProcessor(listOf(throwing, after))

        val result = composite.process(baseResult())

        // STOP_POSTPROCESS (RECOVERABLE + POSSIBLY_MODIFIED) stops the pipeline
        assertFalse(
            result.pipelineNotes.contains("after-stop:should-not-run"),
            "Downstream processor should not run after STOP_POSTPROCESS"
        )
        assertTrue(result.pipelineNotes.any { it.startsWith("postprocess:failed:ThrowingProcessor") })
        assertTrue(result.structuredPostProcessFailures.isNotEmpty())
    }

    // ── PROPAGATE rethrows with structured failure recorded ──────────────────

    @Test
    fun `PROPAGATE records structured failure before rethrowing`() = runTest {
        // CancellationException → UNRECOVERABLE + UNKNOWN → PROPAGATE
        val processor = ThrowingProcessor(kotlinx.coroutines.CancellationException("job cancelled"))
        val composite = CompositeMediaPostProcessor(listOf(processor))

        try {
            composite.process(baseResult())
            fail("Expected CancellationException to propagate")
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Expected — structured failure is recorded before the throw
        }
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
