package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResourceAdmissionPolicyTest {

    private fun budget(
        thermal: CameraThermalState = CameraThermalState.NORMAL,
        performance: CameraPerformanceClass = CameraPerformanceClass.MID,
        memoryBytes: Long = 256L * 1024 * 1024
    ) = defaultBudgetFor(performance, thermal).copy(memoryBytes = memoryBytes)

    // ── Preview Analysis ──────────────────────────────────────────────────────

    @Test
    fun `thermal warm reduces preview analysis fps by half`() {
        val admission = admitPreviewAnalysis(budget(thermal = CameraThermalState.WARM))
        assertTrue(admission.enabled)
        assertEquals(5, admission.effectiveFps) // 10 / 2 = 5
    }

    @Test
    fun `thermal critical disables preview analysis entirely`() {
        val admission = admitPreviewAnalysis(budget(thermal = CameraThermalState.CRITICAL))
        assertFalse(admission.enabled)
        assertEquals(0, admission.effectiveFps)
    }

    @Test
    fun `thermal hot disables preview analysis`() {
        val admission = admitPreviewAnalysis(budget(thermal = CameraThermalState.HOT))
        assertFalse(admission.enabled)
        assertEquals(0, admission.effectiveFps)
    }

    @Test
    fun `low performance class uses lower preview analysis fps`() {
        val admission = admitPreviewAnalysis(budget(performance = CameraPerformanceClass.LOW))
        assertTrue(admission.enabled)
        assertTrue(admission.effectiveFps <= 8)
    }

    // ── Live Photo ────────────────────────────────────────────────────────────

    @Test
    fun `thermal hot degrades live frame buffer`() {
        val admission = admitLivePhoto(
            budget(thermal = CameraThermalState.HOT),
            requestedMotionDurationMillis = 1_500
        )
        assertTrue(admission.enabled)
        assertTrue(admission.degraded)
        assertEquals(750L, admission.effectiveMotionDurationMillis)
    }

    @Test
    fun `live photo motion duration halved under thermal hot`() {
        val admission = admitLivePhoto(
            budget(thermal = CameraThermalState.HOT),
            requestedMotionDurationMillis = 2_000
        )
        assertEquals(1_000L, admission.effectiveMotionDurationMillis)
    }

    @Test
    fun `live photo disabled under thermal critical`() {
        val admission = admitLivePhoto(
            budget(thermal = CameraThermalState.CRITICAL),
            requestedMotionDurationMillis = 1_500
        )
        assertFalse(admission.enabled)
        assertTrue(admission.degraded)
        assertEquals(0L, admission.effectiveMotionDurationMillis)
    }

    // ── Multi-Frame / Night ───────────────────────────────────────────────────

    @Test
    fun `multi frame capped at 4 under thermal hot`() {
        val admission = admitMultiFrame(
            budget(thermal = CameraThermalState.HOT),
            requestedFrameCount = 8
        )
        assertEquals(4, admission.effectiveFrameCount)
        assertTrue(admission.degraded)
    }

    @Test
    fun `multi frame capped at 2 under thermal critical`() {
        val admission = admitMultiFrame(
            budget(thermal = CameraThermalState.CRITICAL),
            requestedFrameCount = 8
        )
        assertEquals(2, admission.effectiveFrameCount)
        assertTrue(admission.degraded)
    }

    @Test
    fun `multi frame capped at 3 under low memory`() {
        val admission = admitMultiFrame(
            budget(memoryBytes = 64L * 1024 * 1024),
            requestedFrameCount = 8
        )
        assertEquals(3, admission.effectiveFrameCount)
        assertTrue(admission.degraded)
    }

    // ── Portrait ──────────────────────────────────────────────────────────────

    @Test
    fun `portrait falls back to focus metadata when segmentation unavailable`() {
        val admission = admitPortrait(
            budget(),
            segmentationAvailable = false
        )
        assertTrue(admission.enabled)
        assertTrue(admission.fallbackToFocusMetadata)
        assertTrue(admission.degraded)
    }

    @Test
    fun `portrait falls back to focus metadata under thermal critical`() {
        val admission = admitPortrait(
            budget(thermal = CameraThermalState.CRITICAL),
            segmentationAvailable = true
        )
        assertTrue(admission.enabled)
        assertTrue(admission.fallbackToFocusMetadata)
        assertTrue(admission.degraded)
    }

    // ── Filter / Watermark ────────────────────────────────────────────────────

    @Test
    fun `filter watermark deferred under thermal hot`() {
        val admission = admitFilterWatermark(budget(thermal = CameraThermalState.HOT))
        assertTrue(admission.enabled)
        assertTrue(admission.deferred)
    }

    @Test
    fun `filter watermark deferred under low memory`() {
        val admission = admitFilterWatermark(budget(memoryBytes = 32L * 1024 * 1024))
        assertTrue(admission.enabled)
        assertTrue(admission.deferred)
    }

    // ── Video Recording ───────────────────────────────────────────────────────

    @Test
    fun `video recording degraded under thermal hot`() {
        val admission = admitVideoRecording(budget(thermal = CameraThermalState.HOT))
        assertTrue(admission.enabled)
        assertTrue(admission.degraded)
    }

    @Test
    fun `video recording disabled under thermal critical`() {
        val admission = admitVideoRecording(budget(thermal = CameraThermalState.CRITICAL))
        assertFalse(admission.enabled)
        assertTrue(admission.degraded)
    }

    // ── Normal conditions ─────────────────────────────────────────────────────

    @Test
    fun `normal thermal and high class admits all features without degradation`() {
        val b = budget(thermal = CameraThermalState.NORMAL, performance = CameraPerformanceClass.HIGH)
        assertTrue(admitPreviewAnalysis(b).enabled)
        assertFalse(admitLivePhoto(b, 1_500).degraded)
        assertFalse(admitMultiFrame(b, 8).degraded)
        assertFalse(admitPortrait(b, true).degraded)
        assertFalse(admitFilterWatermark(b).deferred)
        assertTrue(admitVideoRecording(b).enabled)
    }

    @Test
    fun `budget defaults match performance class expectations`() {
        val high = defaultBudgetFor(CameraPerformanceClass.HIGH)
        val low = defaultBudgetFor(CameraPerformanceClass.LOW)
        assertTrue(high.maxAnalysisFps > low.maxAnalysisFps)
        assertTrue(high.maxProtectedFrames > low.maxProtectedFrames)
        assertTrue(high.memoryBytes > low.memoryBytes)
    }

    // ── Aggregate ─────────────────────────────────────────────────────────────

    @Test
    fun `capture context aggregates all admissions into pipeline notes`() {
        val admission = admitCaptureContext(
            budget(thermal = CameraThermalState.HOT),
            CaptureContextRequest(
                isLivePhoto = true,
                requestedMotionDurationMillis = 1_500,
                isMultiFrame = true,
                requestedFrameCount = 8,
                isPortrait = true,
                segmentationAvailable = false
            )
        )
        assertTrue(admission.pipelineNotes.any { it.contains("resource:class=") })
        assertTrue(admission.pipelineNotes.any { it.contains("resource:thermal=hot") })
        assertNotNull(admission.livePhoto)
        assertTrue(admission.livePhoto!!.degraded)
        assertNotNull(admission.multiFrame)
        assertEquals(4, admission.multiFrame!!.effectiveFrameCount)
        assertNotNull(admission.portrait)
        assertTrue(admission.portrait!!.fallbackToFocusMetadata)
    }

    @Test
    fun `thermal critical blocks new heavy work and reports runtime issue`() {
        val admission = admitCaptureContext(
            budget(thermal = CameraThermalState.CRITICAL),
            CaptureContextRequest(
                isLivePhoto = true,
                requestedMotionDurationMillis = 1_500,
                isMultiFrame = true,
                requestedFrameCount = 8
            )
        )
        assertNotNull(admission.livePhoto)
        assertFalse(admission.livePhoto!!.enabled)
        assertNotNull(admission.multiFrame)
        assertEquals(2, admission.multiFrame!!.effectiveFrameCount)
        assertFalse(admission.previewAnalysis.enabled)
    }
}
