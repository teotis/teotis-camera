package com.opencamera.core.session

import com.opencamera.core.media.CameraPerformanceClass
import com.opencamera.core.media.CameraResourceBudget
import com.opencamera.core.media.CameraThermalState
import com.opencamera.core.media.ResourceDiagnosticsSnapshot

// ──────────────────────────────────────────────────────────────────────────────
// Android Thermal Status -> CameraThermalState mapping
// ──────────────────────────────────────────────────────────────────────────────

fun androidThermalStatusToCameraState(androidStatus: Int): CameraThermalState {
    return when (androidStatus) {
        0, 1 -> CameraThermalState.NORMAL    // THERMAL_STATUS_NONE, THERMAL_STATUS_LIGHT
        2 -> CameraThermalState.WARM          // THERMAL_STATUS_MODERATE
        3 -> CameraThermalState.HOT           // THERMAL_STATUS_SEVERE
        4, 5, 6 -> CameraThermalState.CRITICAL // THERMAL_STATUS_CRITICAL, EMERGENCY, SHUTDOWN
        else -> CameraThermalState.NORMAL
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Budget derivation from thermal + performance class
// ──────────────────────────────────────────────────────────────────────────────

fun deriveBudget(
    performanceClass: CameraPerformanceClass,
    thermalState: CameraThermalState,
    baseMemoryBytes: Long = 256L * 1024 * 1024
): CameraResourceBudget {
    val baseAnalysisFps = when (performanceClass) {
        CameraPerformanceClass.HIGH -> 15
        CameraPerformanceClass.MID -> 10
        CameraPerformanceClass.LOW -> 8
        CameraPerformanceClass.UNKNOWN -> 12
    }
    val baseProtectedFrames = when (performanceClass) {
        CameraPerformanceClass.HIGH -> 12
        CameraPerformanceClass.MID -> 8
        CameraPerformanceClass.LOW -> 4
        CameraPerformanceClass.UNKNOWN -> 8
    }
    val baseConcurrentJobs = when (performanceClass) {
        CameraPerformanceClass.HIGH -> 3
        CameraPerformanceClass.MID -> 2
        CameraPerformanceClass.LOW -> 1
        CameraPerformanceClass.UNKNOWN -> 2
    }

    val thermalFpsMultiplier = when (thermalState) {
        CameraThermalState.NORMAL -> 1.0
        CameraThermalState.WARM -> 0.75
        CameraThermalState.HOT -> 0.5
        CameraThermalState.CRITICAL -> 0.0
    }
    val thermalFrameMultiplier = when (thermalState) {
        CameraThermalState.NORMAL -> 1.0
        CameraThermalState.WARM -> 0.75
        CameraThermalState.HOT -> 0.5
        CameraThermalState.CRITICAL -> 0.25
    }
    val thermalJobMultiplier = when (thermalState) {
        CameraThermalState.NORMAL -> 1.0
        CameraThermalState.WARM -> 1.0
        CameraThermalState.HOT -> 0.5
        CameraThermalState.CRITICAL -> 0.5
    }

    return CameraResourceBudget(
        memoryBytes = baseMemoryBytes,
        maxAnalysisFps = (baseAnalysisFps * thermalFpsMultiplier).toInt().coerceAtLeast(0),
        maxProtectedFrames = (baseProtectedFrames * thermalFrameMultiplier).toInt().coerceAtLeast(1),
        maxConcurrentAlgorithmJobs = (baseConcurrentJobs * thermalJobMultiplier).toInt().coerceAtLeast(1),
        maxPostProcessMillis = 5_000,
        thermalState = thermalState,
        performanceClass = performanceClass
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Low-frequency budget snapshot builder
// ──────────────────────────────────────────────────────────────────────────────

fun buildResourceDiagnosticsSnapshot(
    budget: CameraResourceBudget,
    activeAlgorithmJobs: Int = 0,
    featureDegradations: Map<String, String> = emptyMap()
): ResourceDiagnosticsSnapshot {
    val notes = buildList {
        add("resource:class=${budget.performanceClass.tagValue}")
        add("resource:thermal=${budget.thermalState.tagValue}")
        add("resource:analysis-fps=${budget.maxAnalysisFps}")
        if (activeAlgorithmJobs >= budget.maxConcurrentAlgorithmJobs) {
            add("resource:algorithm-queue=busy")
        }
        featureDegradations.forEach { (feature, detail) ->
            add("resource:$feature=$detail")
        }
    }
    return ResourceDiagnosticsSnapshot(
        thermalState = budget.thermalState,
        performanceClass = budget.performanceClass,
        memoryBudgetBytes = budget.memoryBytes,
        activeAlgorithmJobs = activeAlgorithmJobs,
        maxConcurrentAlgorithmJobs = budget.maxConcurrentAlgorithmJobs,
        featureDegradations = featureDegradations,
        pipelineNotes = notes
    )
}
