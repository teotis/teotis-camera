package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuntimeMetricsTrackerTest {

    // ── G6: PreviewFpsTracker ────────────────────────────────────────────────

    @Test
    fun `preview fps tracker returns null fps with less than 2 frames`() {
        val tracker = PreviewFpsTracker(windowSize = 30)
        assertNull(tracker.currentFps())
        tracker.recordFrame(System.nanoTime())
        assertNull(tracker.currentFps())
    }

    @Test
    fun `preview fps tracker calculates fps from sliding window`() {
        val tracker = PreviewFpsTracker(windowSize = 30)
        val baseNanos = 1_000_000_000L
        for (i in 0..9) {
            tracker.recordFrame(baseNanos + i * 33_000_000L) // ~30fps at 33ms intervals
        }
        val fps = tracker.currentFps()
        assertNotNull(fps)
        assertTrue(fps in 25f..35f, "Expected ~30fps but got $fps")
    }

    @Test
    fun `preview fps tracker window slides and drops oldest frames`() {
        val tracker = PreviewFpsTracker(windowSize = 5)
        val baseNanos = 1_000_000_000L
        for (i in 0..9) {
            tracker.recordFrame(baseNanos + i * 40_000_000L)
        }
        val fps = tracker.currentFps()
        assertNotNull(fps)
        // Window has frames 5-9 (4 intervals at 40ms each = 25fps)
        assertTrue(fps in 22f..28f, "Expected ~25fps but got $fps")
    }

    @Test
    fun `preview fps tracker records frame drops`() {
        val tracker = PreviewFpsTracker()
        assertEquals(0f, tracker.dropRate())
        tracker.recordFrameDrop() // no frames yet, dropRate stays at 0
        assertEquals(0f, tracker.dropRate())
        tracker.recordFrame(System.nanoTime()) // 1 frame, 1 drop
        assertEquals(1.0f, tracker.dropRate())
        tracker.recordFrame(System.nanoTime()) // 2 frames, 1 drop → 0.5
        assertEquals(0.5f, tracker.dropRate())
    }

    @Test
    fun `preview fps tracker empty snapshot has defaults`() {
        val snapshot = PreviewFpsSnapshot.EMPTY
        assertNull(snapshot.currentFps)
        assertEquals(0f, snapshot.frameDropRate)
        assertEquals(0L, snapshot.totalFrames)
        assertEquals(0L, snapshot.droppedFrames)
    }

    @Test
    fun `preview fps tracker toSnapshot mirrors state`() {
        val tracker = PreviewFpsTracker(windowSize = 5)
        val baseNanos = 1_000_000_000L
        for (i in 0..4) {
            tracker.recordFrame(baseNanos + i * 33_000_000L)
        }
        tracker.recordFrameDrop()
        val snapshot = tracker.toSnapshot()
        assertNotNull(snapshot.currentFps)
        assertEquals(0.2f, snapshot.frameDropRate)
        assertEquals(5L, snapshot.totalFrames)
        assertEquals(1L, snapshot.droppedFrames)
    }

    // ── G7: AlgorithmQueueTracker ─────────────────────────────────────────────

    @Test
    fun `algorithm queue tracker starts with zero counts`() {
        val tracker = AlgorithmQueueTracker()
        val budget = CameraResourceBudget(maxConcurrentAlgorithmJobs = 4)
        val snapshot = tracker.toSnapshot(budget)
        assertEquals(0, snapshot.pendingJobCount)
        assertEquals(0, snapshot.activeJobCount)
        assertEquals(4, snapshot.maxConcurrentJobs)
        assertEquals(0, snapshot.totalDepth)
    }

    @Test
    fun `algorithm queue tracker increments and decrements pending`() {
        val tracker = AlgorithmQueueTracker()
        tracker.incrementPending()
        tracker.incrementPending()
        assertEquals(2, tracker.pendingJobCount)
        tracker.decrementPending()
        assertEquals(1, tracker.pendingJobCount)
        tracker.decrementPending()
        tracker.decrementPending() // underflow guard
        assertEquals(0, tracker.pendingJobCount)
    }

    @Test
    fun `algorithm queue tracker moves pending to active on dispatch`() {
        val tracker = AlgorithmQueueTracker()
        tracker.incrementPending()
        tracker.incrementPending()
        tracker.incrementActive()
        assertEquals(1, tracker.pendingJobCount)
        assertEquals(1, tracker.activeJobCount)
    }

    @Test
    fun `algorithm queue snapshot computes total depth`() {
        val tracker = AlgorithmQueueTracker()
        tracker.incrementPending()
        tracker.incrementPending()
        tracker.incrementActive() // moves one from pending to active
        val budget = CameraResourceBudget(maxConcurrentAlgorithmJobs = 2)
        val snapshot = tracker.toSnapshot(budget)
        assertEquals(2, snapshot.totalDepth) // 1 pending + 1 active
    }

    @Test
    fun `algorithm queue EMPTY snapshot has defaults`() {
        val snapshot = AlgorithmQueueSnapshot.EMPTY
        assertEquals(0, snapshot.pendingJobCount)
        assertEquals(0, snapshot.activeJobCount)
        assertEquals(2, snapshot.maxConcurrentJobs)
        assertEquals(0, snapshot.totalDepth)
    }

    @Test
    fun `algorithm queue tracking functions use module-level tracker`() {
        recordAlgorithmJobEnqueued()
        recordAlgorithmJobEnqueued()
        recordAlgorithmJobDispatched()
        val budget = CameraResourceBudget(maxConcurrentAlgorithmJobs = 3)
        val snapshot = queryAlgorithmQueueSnapshot(budget)
        assertEquals(1, snapshot.pendingJobCount)
        assertEquals(1, snapshot.activeJobCount)
        // Clean up
        recordAlgorithmJobCompleted()
        recordAlgorithmJobDequeued()
    }

    // ── G9: VideoRecordingQualityTracker ──────────────────────────────────────

    @Test
    fun `video recording quality tracker starts in idle state`() {
        val tracker = VideoRecordingQualityTracker()
        val snapshot = tracker.toSnapshot()
        assertEquals(VideoRecordingQualitySnapshot.EMPTY, snapshot)
        assertNull(snapshot.actualFps)
        assertNull(snapshot.targetFps)
        assertEquals(0L, snapshot.frameCount)
    }

    @Test
    fun `video recording quality tracker ignores frames when not recording`() {
        val tracker = VideoRecordingQualityTracker()
        tracker.recordFrame(System.nanoTime())
        assertEquals(0L, tracker.toSnapshot().frameCount)
    }

    @Test
    fun `video recording quality tracker counts frames during recording`() {
        val tracker = VideoRecordingQualityTracker()
        tracker.startRecording(targetFps = 30)
        val baseNanos = System.nanoTime()
        for (i in 0..29) {
            tracker.recordFrame(baseNanos + i * 33_000_000L)
        }
        val snapshot = tracker.toSnapshot()
        assertEquals(30L, snapshot.frameCount)
        assertEquals(30, snapshot.targetFps)
        assertTrue(snapshot.isRecording)
    }

    @Test
    fun `video recording quality tracker stops recording`() {
        val tracker = VideoRecordingQualityTracker()
        tracker.startRecording(targetFps = 30)
        tracker.recordFrame(System.nanoTime())
        tracker.stopRecording()
        assertTrue(!tracker.toSnapshot().isRecording)
    }

    @Test
    fun `video recording quality tracker detects frame drops`() {
        val tracker = VideoRecordingQualityTracker()
        tracker.startRecording(targetFps = 30) // ~33ms per frame
        val baseNanos = System.nanoTime()
        // Normal frames (33ms intervals)
        tracker.recordFrame(baseNanos + 33_000_000L)
        tracker.recordFrame(baseNanos + 66_000_000L)
        // Dropped frame (> 1.5x interval = >49.5ms)
        tracker.recordFrame(baseNanos + 130_000_000L) // 64ms gap
        val snapshot = tracker.toSnapshot()
        assertTrue(snapshot.droppedFrames >= 1L)
    }

    @Test
    fun `video recording quality tracker records duration`() {
        val tracker = VideoRecordingQualityTracker()
        tracker.startRecording(targetFps = 24)
        val snapshot = tracker.toSnapshot()
        assertNotNull(snapshot.recordingDurationMillis)
        assertTrue(snapshot.recordingDurationMillis!! >= 0L)
    }

    @Test
    fun `video recording EMPTY snapshot has defaults`() {
        val snapshot = VideoRecordingQualitySnapshot.EMPTY
        assertNull(snapshot.actualFps)
        assertNull(snapshot.targetFps)
        assertEquals(0L, snapshot.frameCount)
        assertEquals(0L, snapshot.droppedFrames)
        assertNull(snapshot.recordingDurationMillis)
        assertTrue(!snapshot.isRecording)
    }

    // ── G10: MemoryPressureSnapshot ───────────────────────────────────────────

    @Test
    fun `memory pressure snapshot samples actual memory usage`() {
        val budget = CameraResourceBudget(memoryBytes = 256L * 1024 * 1024)
        val snapshot = MemoryPressureSnapshot.sample(budget)
        assertTrue(snapshot.usedMemoryBytes > 0L, "Used memory should be greater than 0")
        assertTrue(snapshot.maxMemoryBytes > 0L, "Max memory should be greater than 0")
        assertEquals(256L * 1024 * 1024, snapshot.budgetBytes)
        assertTrue(snapshot.pressureRatio in 0f..1f, "Pressure ratio should be in [0,1]")
        assertTrue(snapshot.pressurePercent in 0..100, "Pressure percent should be in [0,100]")
    }

    @Test
    fun `memory pressure snapshot handles zero budget`() {
        val budget = CameraResourceBudget(memoryBytes = 0L)
        val snapshot = MemoryPressureSnapshot.sample(budget)
        assertEquals(0f, snapshot.pressureRatio)
        assertEquals(0, snapshot.pressurePercent)
    }

    // ── RuntimeMetricsSnapshot aggregate ──────────────────────────────────────

    @Test
    fun `runtime metrics snapshot empty creates default for all metrics`() {
        val budget = CameraResourceBudget(memoryBytes = 128L * 1024 * 1024)
        val snapshot = RuntimeMetricsSnapshot.empty(budget)
        assertEquals(PreviewFpsSnapshot.EMPTY, snapshot.previewFps)
        assertEquals(AlgorithmQueueSnapshot.EMPTY, snapshot.algorithmQueue)
        assertEquals(VideoRecordingQualitySnapshot.EMPTY, snapshot.videoRecordingQuality)
        assertEquals(128L * 1024 * 1024, snapshot.memoryPressure.budgetBytes)
    }
}
