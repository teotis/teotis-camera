package com.opencamera.app.camera

import com.opencamera.core.media.FrameDescriptor
import com.opencamera.core.media.FrameRole
import com.opencamera.core.media.LiveBundleStatus
import com.opencamera.core.media.LiveMotionSource
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.LiveWatermarkResult
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.SelectedFrameSet
import com.opencamera.core.media.ShotPlan
import java.io.File

internal fun materializeLivePhotoSidecar(
    bundle: LivePhotoBundle,
    writeContentUriPayload: (String, String) -> Unit = { _, _ -> }
) {
    val payload = buildLivePhotoSidecarPayload(bundle)
    bundle.sidecarHandle.contentUri?.let { contentUri ->
        writeContentUriPayload(contentUri, payload)
        return
    }
    val sidecarFile = bundle.sidecarHandle.filePath?.let(::File) ?: File(bundle.sidecarPath)
    if (!sidecarFile.isAbsolute) {
        return
    }
    sidecarFile.parentFile?.mkdirs()
    sidecarFile.writeText(payload)
}

data class LiveMotionSourceResult(
    val source: LiveMotionSource,
    val selectedFrameSet: com.opencamera.core.media.SelectedFrameSet,
    val ringBufferDepthMillis: Long,
    val postShutterBudgetMillis: Long,
    val diagnostics: List<String>
)

internal fun resolveLiveMotionSource(
    frameSource: com.opencamera.app.camera.live.LivePreviewFrameSource,
    shutterTimestampNanos: Long,
    spec: com.opencamera.core.media.LivePhotoCaptureSpec
): LiveMotionSourceResult {
    if (!frameSource.isActive) {
        return LiveMotionSourceResult(
            source = LiveMotionSource.METADATA_ONLY,
            selectedFrameSet = com.opencamera.core.media.SelectedFrameSet(
                frames = emptyList(),
                preShutterCount = 0,
                postShutterCount = 0,
                coveredPreShutterMillis = 0,
                coveredPostShutterMillis = 0,
                diagnostics = listOf("frame-source:not-active")
            ),
            ringBufferDepthMillis = 0,
            postShutterBudgetMillis = 0,
            diagnostics = listOf("live:source=metadata-only")
        )
    }

    val selectedFrameSet = frameSource.selectForLive(shutterTimestampNanos, spec)

    return if (selectedFrameSet.frames.isNotEmpty()) {
        LiveMotionSourceResult(
            source = LiveMotionSource.PREVIEW_RING_BUFFER,
            selectedFrameSet = selectedFrameSet,
            ringBufferDepthMillis = spec.motionDurationMillis,
            postShutterBudgetMillis = spec.motionDurationMillis / 5,
            diagnostics = buildList {
                add("live:source=preview-ring-buffer")
                add("frame-buffer:selected=${selectedFrameSet.frames.size}")
                add("frame-buffer:window=-${selectedFrameSet.coveredPreShutterMillis}ms,+${selectedFrameSet.coveredPostShutterMillis}ms")
            }
        )
    } else {
        LiveMotionSourceResult(
            source = LiveMotionSource.METADATA_ONLY,
            selectedFrameSet = selectedFrameSet,
            ringBufferDepthMillis = 0,
            postShutterBudgetMillis = 0,
            diagnostics = buildList {
                add("live:source=metadata-only")
                add("live:degraded=no-frames-near-shutter")
            }
        )
    }
}

internal data class LiveMotionPhotoMaterializationResult(
    val bundle: LivePhotoBundle,
    val diagnostics: List<String>
)

internal fun materializeMotionPhotoBundleIfPossible(
    bundle: LivePhotoBundle,
    motionSourceResult: LiveMotionSourceResult,
    prepareMotionSegment: (List<FrameDescriptor>, String) -> Result<String> =
        { _, motionPath -> Result.success(motionPath) },
    materialize: (String) -> Result<String>
): LiveMotionPhotoMaterializationResult {
    val hasSelectedFrames = motionSourceResult.source == LiveMotionSource.PREVIEW_RING_BUFFER &&
        motionSourceResult.selectedFrameSet.frames.isNotEmpty()
    if (!hasSelectedFrames) {
        return LiveMotionPhotoMaterializationResult(
            bundle = bundle,
            diagnostics = emptyList()
        )
    }

    val motionSegmentResult = prepareMotionSegment(
        motionSourceResult.selectedFrameSet.frames,
        bundle.motionPath
    )
    if (motionSegmentResult.isFailure) {
        val reason = motionSegmentResult.exceptionOrNull()?.message ?: "unknown"
        return LiveMotionPhotoMaterializationResult(
            bundle = bundle.copy(
                bundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK
            ),
            diagnostics = listOf("motion-photo:motion-segment=failed:$reason")
        )
    }

    val motionPath = motionSegmentResult.getOrDefault(bundle.motionPath)
    val motionPhotoResult = materialize(motionPath)
    return if (motionPhotoResult.isSuccess) {
        val outputPath = motionPhotoResult.getOrDefault(bundle.stillPath)
        LiveMotionPhotoMaterializationResult(
            bundle = bundle.copy(
                stillPath = outputPath,
                motionPath = motionPath,
                thumbnailPath = outputPath,
                thumbnailHandle = MediaOutputHandle(displayPath = outputPath)
            ),
            diagnostics = listOf(
                "motion-photo:motion-segment=materialized",
                "motion-photo:container=google-jpeg",
                "motion-photo:xmp=present"
            )
        )
    } else {
        val reason = motionPhotoResult.exceptionOrNull()?.message ?: "unknown"
        LiveMotionPhotoMaterializationResult(
            bundle = bundle.copy(
                bundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK
            ),
            diagnostics = listOf("motion-photo:container=failed:$reason")
        )
    }
}

internal data class LiveWatermarkOutcome(
    val requested: String?,
    val result: LiveWatermarkResult?,
    val degradeReason: String?
)

internal fun resolveLiveWatermarkOutcome(plan: ShotPlan): LiveWatermarkOutcome {
    val customTags = plan.saveTask.saveRequest.metadata.customTags
    val watermarkText = plan.saveTask.postProcessSpec.watermarkText
    val requestedBehavior = customTags["liveWatermarkBehavior"]

    if (requestedBehavior == null && watermarkText == null) {
        return LiveWatermarkOutcome(
            requested = null,
            result = null,
            degradeReason = null
        )
    }

    val hasStillWatermark = !watermarkText.isNullOrBlank()

    return if (hasStillWatermark) {
        LiveWatermarkOutcome(
            requested = requestedBehavior,
            result = LiveWatermarkResult.STILL_ONLY,
            degradeReason = if (requestedBehavior != null) {
                "motion-burn-in-not-implemented"
            } else {
                null
            }
        )
    } else {
        LiveWatermarkOutcome(
            requested = requestedBehavior,
            result = LiveWatermarkResult.UNSUPPORTED,
            degradeReason = null
        )
    }
}

internal fun buildLivePhotoSidecarPayload(
    bundle: LivePhotoBundle
): String {
    return buildString {
        append("{\n")
        append("  \"stillPath\": ")
        append(jsonStringLiteral(bundle.stillPath))
        append(",\n")
        append("  \"motionPath\": ")
        append(jsonStringLiteral(bundle.motionPath))
        append(",\n")
        append("  \"sidecarPath\": ")
        append(jsonStringLiteral(bundle.sidecarPath))
        append(",\n")
        append("  \"thumbnailPath\": ")
        append(jsonStringLiteral(bundle.thumbnailPath))
        append(",\n")
        append("  \"motionDurationMillis\": ")
        append(bundle.motionDurationMillis)
        append(",\n")
        append("  \"motionMimeType\": ")
        append(jsonStringLiteral(bundle.motionMimeType))
        append(",\n")
        append("  \"sidecarMimeType\": ")
        append(jsonStringLiteral(bundle.sidecarMimeType))
        bundle.temporalWindow?.let { window ->
            append(",\n")
            append("  \"temporalWindow\": {\n")
            append("    \"requestedDurationMillis\": ${window.requestedDurationMillis},\n")
            append("    \"preShutterMillis\": ${window.preShutterMillis},\n")
            append("    \"postShutterMillis\": ${window.postShutterMillis},\n")
            append("    \"frameCount\": ${window.frameCount},\n")
            append("    \"source\": ")
            append(jsonStringLiteral(window.source.name.lowercase()))
            append("\n")
            append("  }")
        }
        append(",\n")
        append("  \"bundleStatus\": ")
        append(jsonStringLiteral(bundle.bundleStatus.name.lowercase().replace('_', '-')))
        bundle.watermarkRequested?.let { requested ->
            append(",\n")
            append("  \"watermarkRequested\": ")
            append(jsonStringLiteral(requested))
        }
        bundle.watermarkResult?.let { result ->
            append(",\n")
            append("  \"watermarkResult\": ")
            append(jsonStringLiteral(result.storageKey))
        }
        bundle.watermarkDegradeReason?.let { reason ->
            append(",\n")
            append("  \"watermarkDegradeReason\": ")
            append(jsonStringLiteral(reason))
        }
        append("\n")
        append("}")
    }
}

private fun jsonStringLiteral(value: String): String {
    return buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
}
