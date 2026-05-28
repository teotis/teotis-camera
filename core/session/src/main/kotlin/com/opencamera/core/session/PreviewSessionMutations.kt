package com.opencamera.core.session

import com.opencamera.core.device.PreviewMeteringPoint
import com.opencamera.core.device.PreviewMeteringResult
import com.opencamera.core.media.ThumbnailSource

/**
 * Narrow contract for preview-related state mutations.
 *
 * Decouples [PreviewRecoverySessionProcessor] from the full
 * [DefaultCameraSession.updateState] signature (~30 parameters),
 * exposing only the surface area needed for preview lifecycle,
 * thumbnail updates, capture feedback, and metering state.
 */
internal interface PreviewSessionMutations {
    fun updatePreviewBlocked(reason: String)
    fun updatePreviewStarting(reason: String, isRecovery: Boolean)
    fun updatePreviewActive(firstFrameLatencyMillis: Long)
    fun updatePreviewError(reason: String, action: String)
    fun updatePreviewStopped(reason: String)
    fun updatePreviewThumbnail(source: ThumbnailSource, generation: Int)
    fun updateCaptureFeedback(shotId: String, outputPath: String)
    fun updatePreviewMeteringRequested(requestId: String, point: PreviewMeteringPoint)
    fun updatePreviewMeteringCompleted(result: PreviewMeteringResult)
    fun clearPreviewMeteringFeedback(requestId: String)
    fun updatePreviewHostAttached(lastAction: String)
    fun updatePreviewHostDetached(reason: String, hasPermission: Boolean)
    fun updatePreviewSurfaceLost(reason: String)
    fun updatePreviewRuntimeError(detail: String, action: String)
    fun updatePreviewMetrics(metrics: PreviewMetrics)
}
