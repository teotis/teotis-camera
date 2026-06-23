package com.opencamera.core.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class MediaPostProcessorWatchdogTest {

    // ── Single processor timeout ─────────────────────────────────────────

    @Test
    fun `single processor times out when budget exhausted`() = runTest {
        val deadline = PostProcessLivenessDeadline.forShot("test-shot", 0L, budgetMs = 50L)
        val timeSource = PostProcessTimeSource { testScheduler.currentTime }

        val slow = object : MediaPostProcessor {
            override suspend fun process(result: ShotResult): ShotResult {
                delay(200L)
                return result.addPipelineNotes("slow:done")
            }
        }
        val composite = CompositeMediaPostProcessor(listOf(slow))

        val result = composite.processWithLiveness(
            PostProcessRequest(result = baseResult(), liveness = deadline),
            timeSource
        )

        assertTrue(
            result.pipelineNotes.any { it.startsWith("liveness:postprocess-timeout=") },
            "Expected liveness timeout note, got: ${result.pipelineNotes}"
        )
        assertFalse(
            result.pipelineNotes.contains("slow:done"),
            "Processor should not complete after timeout"
        )
        assertEquals(1, result.structuredPostProcessFailures.size)
        val failure = result.structuredPostProcessFailures.single()
        assertEquals(PostProcessFailureCause.TIMEOUT, failure.cause)
        assertEquals(PostProcessFailureDisposition.RECOVERABLE, failure.disposition)
    }

    @Test
    fun `single processor completes before deadline passes`() = runTest {
        val deadline = PostProcessLivenessDeadline.forShot("test-shot", 0L, budgetMs = 5000L)
        val timeSource = PostProcessTimeSource { testScheduler.currentTime }

        val fast = NoteAppendingProcessor("fast:done")
        val composite = CompositeMediaPostProcessor(listOf(fast))

        val result = composite.processWithLiveness(
            PostProcessRequest(result = baseResult(), liveness = deadline),
            timeSource
        )

        assertTrue(result.pipelineNotes.contains("fast:done"))
        assertTrue(
            result.pipelineNotes.none { it.startsWith("liveness:postprocess-timeout=") }
        )
        assertTrue(result.structuredPostProcessFailures.isEmpty())
    }

    // ── Composite second processor timeout ───────────────────────────────

    @Test
    fun `second processor times out when first exhausts budget`() = runTest {
        val deadline = PostProcessLivenessDeadline.forShot("test-shot", 0L, budgetMs = 30L)
        val timeSource = PostProcessTimeSource { testScheduler.currentTime }

        val first = object : MediaPostProcessor {
            override suspend fun process(result: ShotResult): ShotResult {
                delay(25L)
                return result.addPipelineNotes("first:done")
            }
        }
        val second = object : MediaPostProcessor {
            override suspend fun process(result: ShotResult): ShotResult {
                delay(200L)
                return result.addPipelineNotes("second:done")
            }
        }
        val composite = CompositeMediaPostProcessor(listOf(first, second))

        val result = composite.processWithLiveness(
            PostProcessRequest(result = baseResult(), liveness = deadline),
            timeSource
        )

        assertTrue(result.pipelineNotes.contains("first:done"))
        assertFalse(result.pipelineNotes.contains("second:done"))
        val timeoutNote = result.pipelineNotes.find { it.startsWith("liveness:postprocess-timeout=") }
        assertNotNull(timeoutNote, "Should have timeout note: ${result.pipelineNotes}")
        assertTrue(
            timeoutNote.startsWith("liveness:postprocess-timeout="),
            "Timeout note should use liveness prefix: $timeoutNote"
        )
    }

    @Test
    fun `deadline check between processors catches expiry before second starts`() = runTest {
        // First processor uses 20ms of a 30ms budget → 10ms remaining
        // withTimeoutOrNull(10ms) catches the second processor before it completes
        val deadline = PostProcessLivenessDeadline.forShot("test-shot", 0L, budgetMs = 30L)
        val timeSource = PostProcessTimeSource { testScheduler.currentTime }

        val first = object : MediaPostProcessor {
            override suspend fun process(result: ShotResult): ShotResult {
                delay(20L)
                return result.addPipelineNotes("first:done")
            }
        }
        val second = object : MediaPostProcessor {
            override suspend fun process(result: ShotResult): ShotResult {
                delay(200L)
                return result.addPipelineNotes("second:applied")
            }
        }
        val composite = CompositeMediaPostProcessor(listOf(first, second))

        val result = composite.processWithLiveness(
            PostProcessRequest(result = baseResult(), liveness = deadline),
            timeSource
        )

        assertTrue(result.pipelineNotes.contains("first:done"))
        assertFalse(result.pipelineNotes.contains("second:applied"))
        assertTrue(
            result.pipelineNotes.any { it.startsWith("liveness:postprocess-timeout=") }
        )
    }

    // ── Cooperative cancel vs watchdog timeout distinction ───────────────

    @Test
    fun `CancellationException from processor propagates not treated as timeout`() = runTest {
        val deadline = PostProcessLivenessDeadline.forShot("test-shot", 0L, budgetMs = 5000L)
        val timeSource = PostProcessTimeSource { testScheduler.currentTime }

        val cancelProcessor = object : MediaPostProcessor {
            override suspend fun process(result: ShotResult): ShotResult {
                throw CancellationException("cooperative cancel from lifecycle")
            }
        }
        val composite = CompositeMediaPostProcessor(listOf(cancelProcessor))

        try {
            composite.processWithLiveness(
                PostProcessRequest(result = baseResult(), liveness = deadline),
                timeSource
            )
            fail("Expected CancellationException to propagate")
        } catch (e: CancellationException) {
            assertEquals("cooperative cancel from lifecycle", e.message)
        }
    }

    @Test
    fun `CancellationException is not confused with deadline expiry`() = runTest {
        val deadline = PostProcessLivenessDeadline.forShot("test-shot", 0L, budgetMs = 5000L)
        val timeSource = PostProcessTimeSource { testScheduler.currentTime }

        val cancelProcessor = object : MediaPostProcessor {
            override suspend fun process(result: ShotResult): ShotResult {
                delay(5L)
                throw CancellationException("late cancel")
            }
        }
        val after = NoteAppendingProcessor("after-cancel:should-not-run")
        val composite = CompositeMediaPostProcessor(listOf(cancelProcessor, after))

        try {
            composite.processWithLiveness(
                PostProcessRequest(result = baseResult(), liveness = deadline),
                timeSource
            )
            fail("Expected CancellationException to propagate")
        } catch (_: CancellationException) {
            // Correct — CancellationException propagated, not treated as timeout
        }
    }

    @Test
    fun `watchdog timeout returns typed failure not CancellationException`() = runTest {
        val deadline = PostProcessLivenessDeadline.forShot("test-shot", 0L, budgetMs = 5L)
        val timeSource = PostProcessTimeSource { testScheduler.currentTime }

        val slow = object : MediaPostProcessor {
            override suspend fun process(result: ShotResult): ShotResult {
                delay(200L)
                return result.addPipelineNotes("should-not-finish")
            }
        }
        val composite = CompositeMediaPostProcessor(listOf(slow))

        // Must not throw — timeout is a typed result, not an exception
        val result = composite.processWithLiveness(
            PostProcessRequest(result = baseResult(), liveness = deadline),
            timeSource
        )

        assertTrue(
            result.pipelineNotes.any { it.startsWith("liveness:postprocess-timeout=") }
        )
        assertEquals(PostProcessFailureCause.TIMEOUT, result.structuredPostProcessFailures.single().cause)
    }

    // ── Entry deadline check ─────────────────────────────────────────────

    @Test
    fun `deadline already expired at entry returns immediate timeout`() = runTest {
        // Start at time 1000, budget 100ms, now is 1200 — already expired
        val deadline = PostProcessLivenessDeadline.forShot("test-shot", 500L, budgetMs = 100L)
        val timeSource = PostProcessTimeSource { 1000L }

        val processor = NoteAppendingProcessor("should-not-run")
        val composite = CompositeMediaPostProcessor(listOf(processor))

        val result = composite.processWithLiveness(
            PostProcessRequest(result = baseResult(), liveness = deadline),
            timeSource
        )

        assertFalse(result.pipelineNotes.contains("should-not-run"))
        val timeoutNote = result.pipelineNotes.find { it.startsWith("liveness:postprocess-timeout=") }
        assertNotNull(timeoutNote)
        assertTrue(timeoutNote!!.contains("entry"))
        assertEquals(1, result.structuredPostProcessFailures.size)
        assertEquals(PostProcessFailureCause.TIMEOUT, result.structuredPostProcessFailures.single().cause)
    }

    // ── Temp file cleanup (intermediate paths preserved for cleanup) ─────

    @Test
    fun `timeout preserves intermediateOutputPaths for cleanup`() = runTest {
        val deadline = PostProcessLivenessDeadline.forShot("test-shot", 0L, budgetMs = 5L)
        val timeSource = PostProcessTimeSource { testScheduler.currentTime }

        val slow = object : MediaPostProcessor {
            override suspend fun process(result: ShotResult): ShotResult {
                delay(200L)
                return result
            }
        }
        val composite = CompositeMediaPostProcessor(listOf(slow))

        val input = baseResult().copy(
            intermediateOutputPaths = listOf("/tmp/intermediate-frame-1.jpg", "/tmp/temp-crop.jpg")
        )
        val result = composite.processWithLiveness(
            PostProcessRequest(result = input, liveness = deadline),
            timeSource
        )

        assertEquals(
            input.intermediateOutputPaths,
            result.intermediateOutputPaths,
            "Intermediate output paths must be preserved for existing cleanup path"
        )
    }

    @Test
    fun `timeout preserves output path and handle for downstream cleanup`() = runTest {
        val deadline = PostProcessLivenessDeadline.forShot("test-shot", 0L, budgetMs = 5L)
        val timeSource = PostProcessTimeSource { testScheduler.currentTime }

        val slow = object : MediaPostProcessor {
            override suspend fun process(result: ShotResult): ShotResult {
                delay(200L)
                return result
            }
        }
        val composite = CompositeMediaPostProcessor(listOf(slow))

        val input = baseResult()
        val result = composite.processWithLiveness(
            PostProcessRequest(result = input, liveness = deadline),
            timeSource
        )

        assertEquals(input.outputPath, result.outputPath)
        assertEquals(input.outputHandle, result.outputHandle)
    }

    // ── Pipeline note format verification ────────────────────────────────

    @Test
    fun `timeout note has greppable liveness postprocess-timeout format`() = runTest {
        val deadline = PostProcessLivenessDeadline.forShot("test-shot", 0L, budgetMs = 5L)
        val timeSource = PostProcessTimeSource { testScheduler.currentTime }

        val slow = object : MediaPostProcessor {
            override suspend fun process(result: ShotResult): ShotResult {
                delay(200L)
                return result
            }
        }
        val composite = CompositeMediaPostProcessor(listOf(slow))

        val result = composite.processWithLiveness(
            PostProcessRequest(result = baseResult(), liveness = deadline),
            timeSource
        )

        val timeoutNote = result.pipelineNotes.find { it.startsWith("liveness:postprocess-timeout=") }
        assertNotNull(timeoutNote)
        assertTrue(timeoutNote.startsWith("liveness:postprocess-timeout="))
    }

    @Test
    fun `timeout structured failure has correct shape`() = runTest {
        val deadline = PostProcessLivenessDeadline.forShot("test-shot", 0L, budgetMs = 5L)
        val timeSource = PostProcessTimeSource { testScheduler.currentTime }

        val slow = object : MediaPostProcessor {
            override suspend fun process(result: ShotResult): ShotResult {
                delay(200L)
                return result
            }
        }
        val composite = CompositeMediaPostProcessor(listOf(slow))

        val result = composite.processWithLiveness(
            PostProcessRequest(result = baseResult(), liveness = deadline),
            timeSource
        )

        val failure = result.structuredPostProcessFailures.single()
        assertEquals(PostProcessFailureStage.COMPOSITE, failure.stage)
        assertEquals(PostProcessFailureCause.TIMEOUT, failure.cause)
        assertEquals(PostProcessOutputIntegrity.POSSIBLY_MODIFIED, failure.integrity)
        assertEquals(PostProcessFailureDisposition.RECOVERABLE, failure.disposition)
        assertNotNull(failure.processorName)
    }

    @Test
    fun `timeout failure legacy note is greppable`() = runTest {
        val deadline = PostProcessLivenessDeadline.forShot("test-shot", 0L, budgetMs = 5L)
        val timeSource = PostProcessTimeSource { testScheduler.currentTime }

        val slow = object : MediaPostProcessor {
            override suspend fun process(result: ShotResult): ShotResult {
                delay(200L)
                return result
            }
        }
        val composite = CompositeMediaPostProcessor(listOf(slow))

        val result = composite.processWithLiveness(
            PostProcessRequest(result = baseResult(), liveness = deadline),
            timeSource
        )

        // Structured failure projects to a greppable legacy note
        val failure = result.structuredPostProcessFailures.single()
        val legacyNote = failure.toLegacyNote()
        assertTrue(legacyNote.contains(":failed:"))
    }

    // ── No liveness (null deadline) behaves like original process ────────

    @Test
    fun `null liveness delegates to original process behavior`() = runTest {
        val timeSource = PostProcessTimeSource { testScheduler.currentTime }
        val fast = NoteAppendingProcessor("fast:done")
        val composite = CompositeMediaPostProcessor(listOf(fast))

        val result = composite.processWithLiveness(
            PostProcessRequest(result = baseResult(), liveness = null),
            timeSource
        )

        assertTrue(result.pipelineNotes.contains("fast:done"))
        assertTrue(result.structuredPostProcessFailures.isEmpty())
    }

    // ── pre-existing pipeline notes preserved through timeout ────────────

    @Test
    fun `pre-existing pipeline notes survive timeout`() = runTest {
        val deadline = PostProcessLivenessDeadline.forShot("test-shot", 0L, budgetMs = 5L)
        val timeSource = PostProcessTimeSource { testScheduler.currentTime }

        val slow = object : MediaPostProcessor {
            override suspend fun process(result: ShotResult): ShotResult {
                delay(200L)
                return result
            }
        }
        val composite = CompositeMediaPostProcessor(listOf(slow))

        val input = baseResult().addPipelineNotes("pre-existing:note", "another:pre-note")
        val result = composite.processWithLiveness(
            PostProcessRequest(result = input, liveness = deadline),
            timeSource
        )

        assertTrue(result.pipelineNotes.contains("pre-existing:note"))
        assertTrue(result.pipelineNotes.contains("another:pre-note"))
    }

    // ── Multiple processors: timeout recovery preserves first result ─────

    @Test
    fun `first processor result preserved when second times out`() = runTest {
        val deadline = PostProcessLivenessDeadline.forShot("test-shot", 0L, budgetMs = 30L)
        val timeSource = PostProcessTimeSource { testScheduler.currentTime }

        val first = object : MediaPostProcessor {
            override suspend fun process(result: ShotResult): ShotResult {
                delay(10L)
                return result.addPipelineNotes("first:modified")
            }
        }
        val second = object : MediaPostProcessor {
            override suspend fun process(result: ShotResult): ShotResult {
                delay(200L)
                return result.addPipelineNotes("second:modified")
            }
        }
        val composite = CompositeMediaPostProcessor(listOf(first, second))

        val result = composite.processWithLiveness(
            PostProcessRequest(result = baseResult(), liveness = deadline),
            timeSource
        )

        assertTrue(result.pipelineNotes.contains("first:modified"))
        assertFalse(result.pipelineNotes.contains("second:modified"))
    }

    // ── Inapplicable processor is skipped by watchdog deadline check ─────

    @Test
    fun `inapplicable processor is skipped before deadline check`() = runTest {
        val deadline = PostProcessLivenessDeadline.forShot("test-shot", 0L, budgetMs = 10L)
        val timeSource = PostProcessTimeSource { testScheduler.currentTime }

        val inapplicable = object : MediaPostProcessor {
            override fun isApplicable(result: ShotResult): Boolean = false
            override suspend fun process(result: ShotResult): ShotResult =
                result.addPipelineNotes("should-not-run")
        }
        val composite = CompositeMediaPostProcessor(listOf(inapplicable))

        val result = composite.processWithLiveness(
            PostProcessRequest(result = baseResult(), liveness = deadline),
            timeSource
        )

        assertFalse(result.pipelineNotes.contains("should-not-run"))
        assertTrue(result.structuredPostProcessFailures.isEmpty())
    }

    // ── RECOVER_RELEASE action for timeout ───────────────────────────────

    @Test
    fun `evaluateTimeoutPolicy returns RECOVER_RELEASE`() {
        assertEquals(RecoveryAction.RECOVER_RELEASE, evaluateTimeoutPolicy())
    }

    // ── Helpers ──────────────────────────────────────────────────────────

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

    private class NoteAppendingProcessor(private val note: String) : MediaPostProcessor {
        override suspend fun process(result: ShotResult): ShotResult {
            return result.addPipelineNotes(note)
        }
    }
}
