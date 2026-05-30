package com.opencamera.core.media

// ──────────────────────────────────────────────────────────────────────────────
// G6: Preview FPS / Frame Drop Rate
// ──────────────────────────────────────────────────────────────────────────────

data class PreviewFpsSnapshot(
    val currentFps: Float?,
    val frameDropRate: Float,
    val totalFrames: Long,
    val droppedFrames: Long
) {
    companion object {
        val EMPTY = PreviewFpsSnapshot(
            currentFps = null,
            frameDropRate = 0f,
            totalFrames = 0L,
            droppedFrames = 0L
        )
    }
}

class PreviewFpsTracker(private val windowSize: Int = 30) {
    private val frameTimestampsNanos = ArrayDeque<Long>(windowSize)
    private var totalFrameCount = 0L
    private var droppedFrameCount = 0L

    fun recordFrame(timestampNanos: Long) {
        totalFrameCount++
        frameTimestampsNanos.addLast(timestampNanos)
        if (frameTimestampsNanos.size > windowSize) {
            frameTimestampsNanos.removeFirst()
        }
    }

    fun recordFrameDrop() {
        droppedFrameCount++
    }

    fun currentFps(): Float? {
        if (frameTimestampsNanos.size < 2) return null
        val durationSecs = (frameTimestampsNanos.last() - frameTimestampsNanos.first()) / 1_000_000_000f
        if (durationSecs <= 0f) return null
        return (frameTimestampsNanos.size - 1) / durationSecs
    }

    fun dropRate(): Float {
        if (totalFrameCount == 0L) return 0f
        return droppedFrameCount.toFloat() / totalFrameCount
    }

    fun toSnapshot(): PreviewFpsSnapshot = PreviewFpsSnapshot(
        currentFps = currentFps(),
        frameDropRate = dropRate(),
        totalFrames = totalFrameCount,
        droppedFrames = droppedFrameCount
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// G7: Algorithm Job Queue Depth
// ──────────────────────────────────────────────────────────────────────────────

data class AlgorithmQueueSnapshot(
    val pendingJobCount: Int,
    val activeJobCount: Int,
    val maxConcurrentJobs: Int
) {
    val totalDepth: Int get() = pendingJobCount + activeJobCount

    companion object {
        val EMPTY = AlgorithmQueueSnapshot(
            pendingJobCount = 0,
            activeJobCount = 0,
            maxConcurrentJobs = 2
        )
    }
}

class AlgorithmQueueTracker {
    @Volatile var pendingJobCount: Int = 0
        private set

    @Volatile var activeJobCount: Int = 0
        private set

    fun incrementPending() { pendingJobCount++ }
    fun decrementPending() { if (pendingJobCount > 0) pendingJobCount-- }
    fun incrementActive() { activeJobCount++; decrementPending() }
    fun decrementActive() { if (activeJobCount > 0) activeJobCount-- }

    fun toSnapshot(budget: CameraResourceBudget): AlgorithmQueueSnapshot =
        AlgorithmQueueSnapshot(
            pendingJobCount = pendingJobCount,
            activeJobCount = activeJobCount,
            maxConcurrentJobs = budget.maxConcurrentAlgorithmJobs
        )
}

// ──────────────────────────────────────────────────────────────────────────────
// G9: Video Recording Frame Rate / Quality Indicators
// ──────────────────────────────────────────────────────────────────────────────

data class VideoRecordingQualitySnapshot(
    val actualFps: Float?,
    val targetFps: Int?,
    val frameCount: Long,
    val droppedFrames: Long,
    val recordingDurationMillis: Long?,
    val isRecording: Boolean
) {
    companion object {
        val EMPTY = VideoRecordingQualitySnapshot(
            actualFps = null,
            targetFps = null,
            frameCount = 0L,
            droppedFrames = 0L,
            recordingDurationMillis = null,
            isRecording = false
        )
    }
}

class VideoRecordingQualityTracker {
    private var frameTimestampsNanos = ArrayDeque<Long>()
    private var frameCount = 0L
    private var droppedFrameCount = 0L
    private var targetFps: Int? = null
    private var recordingStartNanos: Long? = null
    private var isRecording = false
    private var lastFrameNanos: Long = 0L

    fun startRecording(targetFps: Int?) {
        this.targetFps = targetFps
        frameCount = 0L
        droppedFrameCount = 0L
        frameTimestampsNanos = ArrayDeque()
        recordingStartNanos = System.nanoTime()
        lastFrameNanos = recordingStartNanos!!
        isRecording = true
    }

    fun recordFrame(timestampNanos: Long) {
        if (!isRecording) return
        frameCount++
        frameTimestampsNanos.addLast(timestampNanos)
        if (frameTimestampsNanos.size > 120) {
            frameTimestampsNanos.removeFirst()
        }

        val intervalNanos = timestampNanos - lastFrameNanos
        val targetIntervalNanos = targetFps?.let { 1_000_000_000L / it } ?: return
        if (intervalNanos > targetIntervalNanos * 1.5) {
            droppedFrameCount++
        }
        lastFrameNanos = timestampNanos
    }

    fun stopRecording() {
        isRecording = false
    }

    fun currentFps(): Float? {
        if (!isRecording || frameTimestampsNanos.size < 2) return null
        val windowDurationSecs = (frameTimestampsNanos.last() - frameTimestampsNanos.first()) / 1_000_000_000f
        if (windowDurationSecs <= 0f) return null
        return (frameTimestampsNanos.size - 1) / windowDurationSecs
    }

    fun toSnapshot(): VideoRecordingQualitySnapshot {
        val duration = recordingStartNanos?.let { (System.nanoTime() - it) / 1_000_000 }
        return VideoRecordingQualitySnapshot(
            actualFps = currentFps(),
            targetFps = targetFps,
            frameCount = frameCount,
            droppedFrames = droppedFrameCount,
            recordingDurationMillis = duration,
            isRecording = isRecording
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// G10: Memory Pressure (actual usage vs budget)
// ──────────────────────────────────────────────────────────────────────────────

data class MemoryPressureSnapshot(
    val usedMemoryBytes: Long,
    val maxMemoryBytes: Long,
    val budgetBytes: Long,
    val pressureRatio: Float
) {
    val pressurePercent: Int get() = (pressureRatio * 100).toInt().coerceIn(0, 100)

    companion object {
        fun sample(budget: CameraResourceBudget): MemoryPressureSnapshot {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            return MemoryPressureSnapshot(
                usedMemoryBytes = usedMemory,
                maxMemoryBytes = maxMemory,
                budgetBytes = budget.memoryBytes,
                pressureRatio = if (budget.memoryBytes > 0) {
                    (usedMemory.toFloat() / budget.memoryBytes).coerceIn(0f, 1f)
                } else 0f
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Aggregate Runtime Metrics Snapshot (G6 + G7 + G9 + G10)
// ──────────────────────────────────────────────────────────────────────────────

data class RuntimeMetricsSnapshot(
    val previewFps: PreviewFpsSnapshot,
    val algorithmQueue: AlgorithmQueueSnapshot,
    val videoRecordingQuality: VideoRecordingQualitySnapshot,
    val memoryPressure: MemoryPressureSnapshot
) {
    companion object {
        fun empty(budget: CameraResourceBudget = CameraResourceBudget()): RuntimeMetricsSnapshot =
            RuntimeMetricsSnapshot(
                previewFps = PreviewFpsSnapshot.EMPTY,
                algorithmQueue = AlgorithmQueueSnapshot.EMPTY,
                videoRecordingQuality = VideoRecordingQualitySnapshot.EMPTY,
                memoryPressure = MemoryPressureSnapshot.sample(budget)
            )
    }
}
