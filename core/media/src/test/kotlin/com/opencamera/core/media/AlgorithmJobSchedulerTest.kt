package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlgorithmJobSchedulerTest {

    private fun node(
        type: AlgorithmType = AlgorithmType.FILTER_RENDER,
        requirement: AlgorithmRequirement = AlgorithmRequirement.REQUIRED,
        fallback: AlgorithmFallback = AlgorithmFallback.FAIL_SHOT
    ) = AlgorithmNode(
        id = "test-node",
        type = type,
        inputs = listOf("input-1"),
        output = "output-1",
        requirement = requirement,
        fallback = fallback
    )

    private val defaultBudget = CameraResourceBudget()

    // ── Job class assignment ──────────────────────────────────────────────────

    @Test
    fun `job class assignment maps required fail shot to capture critical`() {
        val n = node(requirement = AlgorithmRequirement.REQUIRED, fallback = AlgorithmFallback.FAIL_SHOT)
        assertEquals(AlgorithmJobClass.CAPTURE_CRITICAL, n.defaultJobClass())
    }

    @Test
    fun `job class assignment maps optional to capture optional`() {
        val n = node(requirement = AlgorithmRequirement.OPTIONAL, fallback = AlgorithmFallback.SKIP)
        assertEquals(AlgorithmJobClass.CAPTURE_OPTIONAL, n.defaultJobClass())
    }

    @Test
    fun `job class assignment maps thumbnail select to capture optional`() {
        val n = node(type = AlgorithmType.THUMBNAIL_SELECT, requirement = AlgorithmRequirement.REQUIRED)
        assertEquals(AlgorithmJobClass.CAPTURE_OPTIONAL, n.defaultJobClass())
    }

    // ── Timeout values ────────────────────────────────────────────────────────

    @Test
    fun `timeout for realtime preview is 500ms regardless of budget`() {
        val budget = CameraResourceBudget(maxPostProcessMillis = 10_000)
        assertEquals(500L, timeoutForJobClass(AlgorithmJobClass.REALTIME_PREVIEW, budget))
    }

    @Test
    fun `timeout for capture critical uses max post process millis`() {
        val budget = CameraResourceBudget(maxPostProcessMillis = 8_000)
        assertEquals(8_000L, timeoutForJobClass(AlgorithmJobClass.CAPTURE_CRITICAL, budget))
    }

    @Test
    fun `timeout for capture optional uses half of max post process millis`() {
        val budget = CameraResourceBudget(maxPostProcessMillis = 8_000)
        assertEquals(4_000L, timeoutForJobClass(AlgorithmJobClass.CAPTURE_OPTIONAL, budget))
    }

    @Test
    fun `timeout for cleanup uses double max post process millis`() {
        val budget = CameraResourceBudget(maxPostProcessMillis = 5_000)
        assertEquals(10_000L, timeoutForJobClass(AlgorithmJobClass.CLEANUP, budget))
    }

    // ── Cancellable ───────────────────────────────────────────────────────────

    @Test
    fun `realtime preview is cancellable`() {
        assertTrue(cancellableForJobClass(AlgorithmJobClass.REALTIME_PREVIEW))
    }

    @Test
    fun `capture critical is not cancellable`() {
        assertFalse(cancellableForJobClass(AlgorithmJobClass.CAPTURE_CRITICAL))
    }

    @Test
    fun `capture optional is cancellable`() {
        assertTrue(cancellableForJobClass(AlgorithmJobClass.CAPTURE_OPTIONAL))
    }

    @Test
    fun `cleanup is not cancellable`() {
        assertFalse(cancellableForJobClass(AlgorithmJobClass.CLEANUP))
    }

    // ── Timeout result mapping ────────────────────────────────────────────────

    @Test
    fun `capture critical timeout produces non recoverable timeout`() {
        val result = mapTimeoutToJobResult(AlgorithmJobClass.CAPTURE_CRITICAL, node())
        assertTrue(result is AlgorithmJobResult.TimedOut)
        assertFalse((result as AlgorithmJobResult.TimedOut).recoverable)
    }

    @Test
    fun `algorithm job timeout produces recoverable skip for optional processors`() {
        val n = node(type = AlgorithmType.FILTER_RENDER, requirement = AlgorithmRequirement.OPTIONAL)
        val result = mapTimeoutToJobResult(AlgorithmJobClass.CAPTURE_OPTIONAL, n)
        assertTrue(result is AlgorithmJobResult.Completed)
        val inner = (result as AlgorithmJobResult.Completed).result
        assertTrue(inner is AlgorithmResult.Skipped)
        assertTrue(inner.notes.any { it.contains("resource:algorithm-timeout") })
    }

    @Test
    fun `realtime preview timeout produces skip result`() {
        val result = mapTimeoutToJobResult(AlgorithmJobClass.REALTIME_PREVIEW, node())
        assertTrue(result is AlgorithmJobResult.Completed)
        val inner = (result as AlgorithmJobResult.Completed).result
        assertTrue(inner is AlgorithmResult.Skipped)
    }

    @Test
    fun `cleanup timeout produces recoverable timeout`() {
        val result = mapTimeoutToJobResult(AlgorithmJobClass.CLEANUP, node())
        assertTrue(result is AlgorithmJobResult.TimedOut)
        assertTrue((result as AlgorithmJobResult.TimedOut).recoverable)
    }

    // ── Failure result mapping ────────────────────────────────────────────────

    @Test
    fun `capture critical processor failure fails shot`() {
        val failed = AlgorithmResult.Failed(reason = "oom", recoverable = false)
        val result = mapFailureToJobResult(AlgorithmJobClass.CAPTURE_CRITICAL, failed, node())
        assertTrue(result is AlgorithmJobResult.Completed)
        assertEquals(failed, (result as AlgorithmJobResult.Completed).result)
    }

    @Test
    fun `capture optional processor failure degrades with notes`() {
        val failed = AlgorithmResult.Failed(reason = "oom", recoverable = true)
        val n = node(type = AlgorithmType.WATERMARK_RENDER, requirement = AlgorithmRequirement.OPTIONAL)
        val result = mapFailureToJobResult(AlgorithmJobClass.CAPTURE_OPTIONAL, failed, n)
        assertTrue(result is AlgorithmJobResult.Completed)
        val inner = (result as AlgorithmJobResult.Completed).result
        assertTrue(inner is AlgorithmResult.Skipped)
        assertTrue(inner.notes.any { it.contains("resource:optional-degraded") })
    }

    // ── Job spec builder ──────────────────────────────────────────────────────

    @Test
    fun `toJobSpec creates spec with correct class and timeout`() {
        val request = AlgorithmRequest(
            node = node(),
            inputs = emptyList(),
            metadata = MediaMetadata()
        )
        val spec = request.toJobSpec(AlgorithmJobClass.CAPTURE_CRITICAL, defaultBudget)
        assertEquals(AlgorithmJobClass.CAPTURE_CRITICAL, spec.jobClass)
        assertEquals(defaultBudget.maxPostProcessMillis, spec.timeoutMillis)
        assertFalse(spec.cancellable)
    }
}
