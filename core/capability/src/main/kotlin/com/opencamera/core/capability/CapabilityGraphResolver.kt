package com.opencamera.core.capability

import com.opencamera.core.effect.DocumentEffect
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.PortraitEffect
import com.opencamera.core.effect.WatermarkEffect
import com.opencamera.core.media.MediaProcessorAvailability

class CapabilityGraphResolver(
    private val deviceQuery: CapabilityGraphDeviceQuery,
    private val mediaProcessors: MediaProcessorAvailability
) {
    fun resolve(
        featureId: String,
        requirements: List<CapabilityRequirement>,
        effectSpec: EffectSpec = EffectSpec.EMPTY
    ): CapabilityGraphReport {
        val resolved = requirements.map { resolveRequirement(it, effectSpec) }
        return CapabilityGraphReport(
            featureId = featureId,
            requested = requirements,
            resolved = resolved
        )
    }

    private fun resolveRequirement(
        req: CapabilityRequirement,
        effectSpec: EffectSpec
    ): CapabilityResolution {
        return when (req.kind) {
            CapabilityRequirementKind.STILL_CAPTURE -> resolveStillCapture(req)
            CapabilityRequirementKind.VIDEO_RECORDING -> resolveVideoRecording(req)
            CapabilityRequirementKind.PREVIEW_FRAME_STREAM -> resolvePreviewFrameStream(req)
            CapabilityRequirementKind.ANALYSIS_FRAME_STREAM -> resolveAnalysisFrameStream(req)
            CapabilityRequirementKind.MULTI_FRAME_CAPTURE -> resolveMultiFrameCapture(req)
            CapabilityRequirementKind.TEMPORAL_RING_BUFFER -> resolveTemporalRingBuffer(req)
            CapabilityRequirementKind.MOTION_SIDE_CAR -> resolveMotionSideCar(req)
            CapabilityRequirementKind.MANUAL_CONTROL -> resolveManualControl(req)
            CapabilityRequirementKind.RAW_OUTPUT -> resolveRawOutput(req)
            CapabilityRequirementKind.FILTER_CAPTURE_RENDER -> resolveFilterCaptureRender(req, effectSpec)
            CapabilityRequirementKind.FILTER_PREVIEW_RENDER -> resolveFilterPreviewRender(req, effectSpec)
            CapabilityRequirementKind.PORTRAIT_SEGMENTATION -> resolvePortraitSegmentation(req, effectSpec)
            CapabilityRequirementKind.DOCUMENT_GEOMETRY -> resolveDocumentGeometry(req, effectSpec)
            CapabilityRequirementKind.WATERMARK_RENDER -> resolveWatermarkRender(req, effectSpec)
            CapabilityRequirementKind.SAVE_TRANSACTION -> resolveSaveTransaction(req)
            CapabilityRequirementKind.THUMBNAIL_RESULT -> resolveThumbnailResult(req)
        }
    }

    private fun resolveStillCapture(req: CapabilityRequirement): CapabilityResolution {
        return if (deviceQuery.supportsStillCapture()) {
            ok(req)
        } else {
            unsupported(req, "Device does not support still capture")
        }
    }

    private fun resolveVideoRecording(req: CapabilityRequirement): CapabilityResolution {
        return if (deviceQuery.supportsVideoRecording()) {
            ok(req)
        } else {
            unsupported(req, "Device does not support video recording")
        }
    }

    private fun resolvePreviewFrameStream(req: CapabilityRequirement): CapabilityResolution {
        return if (deviceQuery.supportsPreviewSnapshots()) {
            ok(req)
        } else {
            degraded(req, "Preview snapshots unavailable, using direct capture only", req.fallbackIds.firstOrNull())
        }
    }

    private fun resolveAnalysisFrameStream(req: CapabilityRequirement): CapabilityResolution {
        return degraded(
            req,
            "Analysis frame stream not yet formalized in device capabilities",
            req.fallbackIds.firstOrNull()
        )
    }

    private fun resolveMultiFrameCapture(req: CapabilityRequirement): CapabilityResolution {
        if (!deviceQuery.supportsNightMultiFrame()) {
            return degraded(req, "Night multi-frame not supported, single-frame fallback", "single-frame")
        }
        if (!mediaProcessors.multiFrameMergeAvailable) {
            return degraded(req, "Multi-frame merge processor unavailable, single-frame fallback", "single-frame")
        }
        return ok(req)
    }

    private fun resolveTemporalRingBuffer(req: CapabilityRequirement): CapabilityResolution {
        return if (mediaProcessors.temporalMediaAssemblerAvailable) {
            ok(req)
        } else {
            unsupported(req, "Temporal media assembler unavailable")
        }
    }

    private fun resolveMotionSideCar(req: CapabilityRequirement): CapabilityResolution {
        return if (mediaProcessors.temporalMediaAssemblerAvailable) {
            ok(req)
        } else {
            unsupported(req, "Motion sidecar requires temporal media assembler")
        }
    }

    private fun resolveManualControl(req: CapabilityRequirement): CapabilityResolution {
        val summary = deviceQuery.manualControlSummary()
        return when {
            summary.hasAppliedControls -> ok(req)
            summary.hasSavedOnlyControls ->
                savedOnly(req, "Manual controls available as saved-only draft only")
            else -> unsupported(req, "Manual controls not available on this device")
        }
    }

    private fun resolveRawOutput(req: CapabilityRequirement): CapabilityResolution {
        return when (deviceQuery.rawOutputSupport()) {
            CapabilitySupport.SUPPORTED -> ok(req)
            CapabilitySupport.SAVED_ONLY ->
                savedOnly(req, "RAW saved as metadata draft only, not applied to capture")
            else -> unsupported(req, "RAW output not available on this device")
        }
    }

    private fun resolveFilterCaptureRender(
        req: CapabilityRequirement,
        spec: EffectSpec
    ): CapabilityResolution {
        val hasFilter = spec.find<FilterEffect>() != null
        if (!hasFilter) return ok(req)
        return if (mediaProcessors.filterRenderAvailable) {
            ok(req)
        } else {
            unsupported(req, "Filter capture render processor unavailable")
        }
    }

    private fun resolveFilterPreviewRender(
        req: CapabilityRequirement,
        spec: EffectSpec
    ): CapabilityResolution {
        val hasFilter = spec.find<FilterEffect>() != null
        if (!hasFilter) return ok(req)
        return if (mediaProcessors.filterRenderAvailable) {
            ok(req)
        } else {
            previewOnly(req, "Filter preview available but capture render processor missing")
        }
    }

    private fun resolvePortraitSegmentation(
        req: CapabilityRequirement,
        spec: EffectSpec
    ): CapabilityResolution {
        val hasPortrait = spec.find<PortraitEffect>() != null
        if (!hasPortrait) return ok(req)
        if (!mediaProcessors.portraitRenderAvailable) {
            return unsupported(req, "Portrait render processor unavailable")
        }
        return if (deviceQuery.supportsPortraitDepth()) {
            ok(req)
        } else {
            degraded(req, "Depth effect not supported, using focus fallback", "focus-fallback")
        }
    }

    private fun resolveDocumentGeometry(
        req: CapabilityRequirement,
        spec: EffectSpec
    ): CapabilityResolution {
        val hasDocument = spec.find<DocumentEffect>() != null
        if (!hasDocument) return ok(req)
        if (!mediaProcessors.documentProcessorAvailable) {
            return unsupported(req, "Document processor unavailable")
        }
        return if (deviceQuery.supportsDocumentGeometry()) {
            ok(req)
        } else {
            degraded(
                req,
                "Document scan enhancement not supported, basic archive mode",
                req.fallbackIds.firstOrNull()
            )
        }
    }

    private fun resolveWatermarkRender(
        req: CapabilityRequirement,
        spec: EffectSpec
    ): CapabilityResolution {
        val hasWatermark = spec.find<WatermarkEffect>() != null
        if (!hasWatermark) return ok(req)
        return if (mediaProcessors.watermarkRenderAvailable) {
            ok(req)
        } else {
            unsupported(req, "Watermark render processor unavailable")
        }
    }

    private fun resolveSaveTransaction(req: CapabilityRequirement): CapabilityResolution {
        return ok(req)
    }

    private fun resolveThumbnailResult(req: CapabilityRequirement): CapabilityResolution {
        return ok(req)
    }

    private fun ok(req: CapabilityRequirement) = CapabilityResolution(
        requirement = req,
        support = CapabilitySupport.SUPPORTED,
        reason = "OK"
    )

    private fun degraded(
        req: CapabilityRequirement,
        reason: String,
        fallbackId: String? = null
    ) = CapabilityResolution(
        requirement = req,
        support = CapabilitySupport.DEGRADED,
        reason = reason,
        selectedFallbackId = fallbackId
    )

    private fun savedOnly(req: CapabilityRequirement, reason: String) = CapabilityResolution(
        requirement = req,
        support = CapabilitySupport.SAVED_ONLY,
        reason = reason
    )

    private fun previewOnly(req: CapabilityRequirement, reason: String) = CapabilityResolution(
        requirement = req,
        support = CapabilitySupport.PREVIEW_ONLY,
        reason = reason
    )

    private fun unsupported(req: CapabilityRequirement, reason: String) = CapabilityResolution(
        requirement = req,
        support = CapabilitySupport.UNSUPPORTED,
        reason = reason
    )
}
