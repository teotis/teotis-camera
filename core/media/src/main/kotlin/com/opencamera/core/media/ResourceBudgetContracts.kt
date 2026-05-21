package com.opencamera.core.media

// ──────────────────────────────────────────────────────────────────────────────
// Thermal State
// ──────────────────────────────────────────────────────────────────────────────

enum class CameraThermalState(val tagValue: String) {
    NORMAL("normal"),
    WARM("warm"),
    HOT("hot"),
    CRITICAL("critical")
}

// ──────────────────────────────────────────────────────────────────────────────
// Performance Class
// ──────────────────────────────────────────────────────────────────────────────

enum class CameraPerformanceClass(val tagValue: String) {
    LOW("low"),
    MID("mid"),
    HIGH("high"),
    UNKNOWN("unknown")
}

// ──────────────────────────────────────────────────────────────────────────────
// Resource Budget
// ──────────────────────────────────────────────────────────────────────────────

data class CameraResourceBudget(
    val memoryBytes: Long = 256L * 1024 * 1024,
    val maxAnalysisFps: Int = 15,
    val maxProtectedFrames: Int = 8,
    val maxConcurrentAlgorithmJobs: Int = 2,
    val maxPostProcessMillis: Long = 5_000,
    val thermalState: CameraThermalState = CameraThermalState.NORMAL,
    val performanceClass: CameraPerformanceClass = CameraPerformanceClass.UNKNOWN
)

// ──────────────────────────────────────────────────────────────────────────────
// Admission Result
// ──────────────────────────────────────────────────────────────────────────────

data class CameraWorkAdmission(
    val admitted: Boolean,
    val degraded: Boolean = false,
    val reason: String = "",
    val effectiveBudget: CameraResourceBudget,
    val featureAdjustments: Map<String, String> = emptyMap()
)

// ──────────────────────────────────────────────────────────────────────────────
// Resource Diagnostics Snapshot
// ──────────────────────────────────────────────────────────────────────────────

data class ResourceDiagnosticsSnapshot(
    val thermalState: CameraThermalState,
    val performanceClass: CameraPerformanceClass,
    val memoryBudgetBytes: Long,
    val activeAlgorithmJobs: Int,
    val maxConcurrentAlgorithmJobs: Int,
    val featureDegradations: Map<String, String>,
    val pipelineNotes: List<String>
)

// ──────────────────────────────────────────────────────────────────────────────
// Algorithm Job Class (4 priority tiers)
// ──────────────────────────────────────────────────────────────────────────────

enum class AlgorithmJobClass {
    REALTIME_PREVIEW,
    CAPTURE_CRITICAL,
    CAPTURE_OPTIONAL,
    CLEANUP
}

// ──────────────────────────────────────────────────────────────────────────────
// Algorithm Job Specification
// ──────────────────────────────────────────────────────────────────────────────

data class AlgorithmJobSpec(
    val jobClass: AlgorithmJobClass,
    val request: AlgorithmRequest,
    val timeoutMillis: Long,
    val cancellable: Boolean = false
)

// ──────────────────────────────────────────────────────────────────────────────
// Algorithm Job Result (extends AlgorithmResult with budget/timeout semantics)
// ──────────────────────────────────────────────────────────────────────────────

sealed interface AlgorithmJobResult {
    data class Completed(val result: AlgorithmResult) : AlgorithmJobResult
    data class TimedOut(
        val reason: String,
        val recoverable: Boolean
    ) : AlgorithmJobResult

    data class Rejected(val reason: String) : AlgorithmJobResult
}
