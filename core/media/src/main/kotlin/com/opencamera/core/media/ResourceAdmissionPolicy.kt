package com.opencamera.core.media

// ──────────────────────────────────────────────────────────────────────────────
// Default Budget by Performance Class
// ──────────────────────────────────────────────────────────────────────────────

fun defaultBudgetFor(
    performanceClass: CameraPerformanceClass,
    thermalState: CameraThermalState = CameraThermalState.NORMAL
): CameraResourceBudget {
    val baseMemory = when (performanceClass) {
        CameraPerformanceClass.HIGH -> 512L * 1024 * 1024
        CameraPerformanceClass.MID -> 256L * 1024 * 1024
        CameraPerformanceClass.LOW -> 128L * 1024 * 1024
        CameraPerformanceClass.UNKNOWN -> 256L * 1024 * 1024
    }
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
    return CameraResourceBudget(
        memoryBytes = baseMemory,
        maxAnalysisFps = baseAnalysisFps,
        maxProtectedFrames = baseProtectedFrames,
        maxConcurrentAlgorithmJobs = baseConcurrentJobs,
        maxPostProcessMillis = 5_000,
        thermalState = thermalState,
        performanceClass = performanceClass
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Preview Analysis Admission
// ──────────────────────────────────────────────────────────────────────────────

data class PreviewAnalysisAdmission(
    val enabled: Boolean,
    val effectiveFps: Int,
    val note: String?
)

fun admitPreviewAnalysis(budget: CameraResourceBudget): PreviewAnalysisAdmission {
    return when (budget.thermalState) {
        CameraThermalState.CRITICAL, CameraThermalState.HOT -> PreviewAnalysisAdmission(
            enabled = false,
            effectiveFps = 0,
            note = "resource:analysis-fps=0"
        )

        CameraThermalState.WARM -> {
            val fps = (budget.maxAnalysisFps / 2).coerceAtLeast(4)
            PreviewAnalysisAdmission(
                enabled = true,
                effectiveFps = fps,
                note = "resource:analysis-fps=$fps"
            )
        }

        CameraThermalState.NORMAL -> {
            val fps = if (budget.performanceClass == CameraPerformanceClass.LOW) {
                budget.maxAnalysisFps.coerceAtMost(8)
            } else {
                budget.maxAnalysisFps
            }
            PreviewAnalysisAdmission(
                enabled = true,
                effectiveFps = fps,
                note = if (fps != budget.maxAnalysisFps) "resource:analysis-fps=$fps" else null
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Live Photo Admission
// ──────────────────────────────────────────────────────────────────────────────

data class LivePhotoAdmission(
    val enabled: Boolean,
    val effectiveMotionDurationMillis: Long,
    val degraded: Boolean,
    val note: String?
)

fun admitLivePhoto(
    budget: CameraResourceBudget,
    requestedMotionDurationMillis: Long = 1_500
): LivePhotoAdmission {
    return when (budget.thermalState) {
        CameraThermalState.CRITICAL -> LivePhotoAdmission(
            enabled = false,
            effectiveMotionDurationMillis = 0,
            degraded = true,
            note = "resource:live=disabled:thermal-critical"
        )

        CameraThermalState.HOT -> {
            val reduced = requestedMotionDurationMillis / 2
            LivePhotoAdmission(
                enabled = true,
                effectiveMotionDurationMillis = reduced,
                degraded = true,
                note = "resource:live=degraded:max-frames"
            )
        }

        CameraThermalState.WARM, CameraThermalState.NORMAL -> {
            val adjusted = if (budget.memoryBytes < 128L * 1024 * 1024) {
                (requestedMotionDurationMillis * 3 / 4)
            } else {
                requestedMotionDurationMillis
            }
            val degraded = adjusted < requestedMotionDurationMillis
            LivePhotoAdmission(
                enabled = true,
                effectiveMotionDurationMillis = adjusted,
                degraded = degraded,
                note = if (degraded) "resource:live=degraded:low-memory" else null
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Multi-Frame / Night Admission
// ──────────────────────────────────────────────────────────────────────────────

data class MultiFrameAdmission(
    val enabled: Boolean,
    val effectiveFrameCount: Int,
    val degraded: Boolean,
    val note: String?
)

fun admitMultiFrame(
    budget: CameraResourceBudget,
    requestedFrameCount: Int
): MultiFrameAdmission {
    val thermalCap = when (budget.thermalState) {
        CameraThermalState.CRITICAL -> 2
        CameraThermalState.HOT -> 4
        CameraThermalState.WARM -> 6
        CameraThermalState.NORMAL -> requestedFrameCount
    }
    val memoryCap = if (budget.memoryBytes < 128L * 1024 * 1024) 3 else requestedFrameCount
    val effective = minOf(requestedFrameCount, thermalCap, memoryCap)
    val degraded = effective < requestedFrameCount
    val note = if (degraded) {
        when {
            effective <= 2 && budget.thermalState == CameraThermalState.CRITICAL ->
                "resource:night=degraded:frame-count-$effective"

            effective <= 4 && budget.thermalState == CameraThermalState.HOT ->
                "resource:night=degraded:frame-count-$effective"

            else -> "resource:night=degraded:frame-count-$effective"
        }
    } else {
        null
    }
    return MultiFrameAdmission(
        enabled = true,
        effectiveFrameCount = effective,
        degraded = degraded,
        note = note
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Portrait Admission
// ──────────────────────────────────────────────────────────────────────────────

data class PortraitAdmission(
    val enabled: Boolean,
    val fallbackToFocusMetadata: Boolean,
    val degraded: Boolean,
    val note: String?
)

fun admitPortrait(
    budget: CameraResourceBudget,
    segmentationAvailable: Boolean
): PortraitAdmission {
    return when {
        !segmentationAvailable -> PortraitAdmission(
            enabled = true,
            fallbackToFocusMetadata = true,
            degraded = true,
            note = "resource:portrait=degraded:segmentation-unavailable"
        )

        budget.thermalState == CameraThermalState.CRITICAL -> PortraitAdmission(
            enabled = true,
            fallbackToFocusMetadata = true,
            degraded = true,
            note = "resource:portrait=degraded:thermal-critical"
        )

        else -> PortraitAdmission(
            enabled = true,
            fallbackToFocusMetadata = false,
            degraded = false,
            note = null
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Filter / Watermark Admission
// ──────────────────────────────────────────────────────────────────────────────

data class FilterWatermarkAdmission(
    val enabled: Boolean,
    val deferred: Boolean,
    val note: String?
)

fun admitFilterWatermark(budget: CameraResourceBudget): FilterWatermarkAdmission {
    val shouldDefer = budget.thermalState == CameraThermalState.HOT ||
        budget.thermalState == CameraThermalState.CRITICAL ||
        budget.memoryBytes < 64L * 1024 * 1024
    return FilterWatermarkAdmission(
        enabled = true,
        deferred = shouldDefer,
        note = if (shouldDefer) "resource:deferred=filter-watermark" else null
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Video Recording Admission
// ──────────────────────────────────────────────────────────────────────────────

data class VideoAdmission(
    val enabled: Boolean,
    val degraded: Boolean,
    val note: String?
)

fun admitVideoRecording(budget: CameraResourceBudget): VideoAdmission {
    return when (budget.thermalState) {
        CameraThermalState.CRITICAL -> VideoAdmission(
            enabled = false,
            degraded = true,
            note = "resource:video=disabled:thermal-critical"
        )

        CameraThermalState.HOT -> VideoAdmission(
            enabled = true,
            degraded = true,
            note = "resource:video=degraded:thermal-warm"
        )

        CameraThermalState.WARM, CameraThermalState.NORMAL -> VideoAdmission(
            enabled = true,
            degraded = false,
            note = null
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Aggregate Capture Context
// ──────────────────────────────────────────────────────────────────────────────

data class CaptureContextRequest(
    val isLivePhoto: Boolean = false,
    val requestedMotionDurationMillis: Long = 1_500,
    val isMultiFrame: Boolean = false,
    val requestedFrameCount: Int = 1,
    val isPortrait: Boolean = false,
    val segmentationAvailable: Boolean = true,
    val isFilterWatermark: Boolean = false
)

data class CaptureContextAdmission(
    val previewAnalysis: PreviewAnalysisAdmission,
    val livePhoto: LivePhotoAdmission?,
    val multiFrame: MultiFrameAdmission?,
    val portrait: PortraitAdmission?,
    val filterWatermark: FilterWatermarkAdmission,
    val pipelineNotes: List<String>
)

fun admitCaptureContext(
    budget: CameraResourceBudget,
    request: CaptureContextRequest
): CaptureContextAdmission {
    val previewAnalysis = admitPreviewAnalysis(budget)
    val livePhoto = if (request.isLivePhoto) {
        admitLivePhoto(budget, request.requestedMotionDurationMillis)
    } else {
        null
    }
    val multiFrame = if (request.isMultiFrame) {
        admitMultiFrame(budget, request.requestedFrameCount)
    } else {
        null
    }
    val portrait = if (request.isPortrait) {
        admitPortrait(budget, request.segmentationAvailable)
    } else {
        null
    }
    val filterWatermark = if (request.isFilterWatermark) {
        admitFilterWatermark(budget)
    } else {
        FilterWatermarkAdmission(enabled = true, deferred = false, note = null)
    }

    val notes = buildList {
        add("resource:class=${budget.performanceClass.tagValue}")
        add("resource:thermal=${budget.thermalState.tagValue}")
        previewAnalysis.note?.let(::add)
        livePhoto?.note?.let(::add)
        multiFrame?.note?.let(::add)
        portrait?.note?.let(::add)
        filterWatermark.note?.let(::add)
    }

    return CaptureContextAdmission(
        previewAnalysis = previewAnalysis,
        livePhoto = livePhoto,
        multiFrame = multiFrame,
        portrait = portrait,
        filterWatermark = filterWatermark,
        pipelineNotes = notes
    )
}
