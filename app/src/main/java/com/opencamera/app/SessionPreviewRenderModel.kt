package com.opencamera.app

import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.effect.PreviewEffectAdapter
import com.opencamera.core.effect.PreviewEffectRenderModel
import com.opencamera.core.effect.PreviewSceneMaskSnapshot
import com.opencamera.core.effect.WatermarkHintSpec
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PreviewMeteringFeedback
import com.opencamera.core.session.PreviewMeteringFeedbackStatus
import com.opencamera.core.session.PreviewRatio
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.SessionState
import com.opencamera.core.settings.CompositionGridMode

internal data class PreviewOverlayRenderModel(
    val gridMode: CompositionGridMode,
    val isGridVisible: Boolean,
    val countdownLabel: String?,
    val isCountdownVisible: Boolean,
    val effectModel: PreviewEffectRenderModel? = null,
    val frame: PreviewFrameRenderModel? = null,
    val previewContentAspect: PreviewContentAspect? = null
) {
    val isVisible: Boolean
        get() = isGridVisible || isCountdownVisible || effectModel != null || frame != null
}

internal data class PreviewContentAspect(
    val width: Int,
    val height: Int
)

internal data class PreviewFrameRenderModel(
    val ratio: FrameRatio,
    val label: String,
    val dimOutsideFrame: Boolean,
    val bottomInsetPx: Float = 0f,
    val zoomRatio: Float = 1f,
    val previewZoomRatio: Float = 1f
)

internal fun focusReticleRenderModel(
    feedback: PreviewMeteringFeedback
): FocusReticleRenderModel = FocusReticleRenderModel(
    normalizedX = feedback.normalizedX,
    normalizedY = feedback.normalizedY,
    status = when (feedback.status) {
        PreviewMeteringFeedbackStatus.REQUESTED -> FocusReticleStatus.REQUESTED
        PreviewMeteringFeedbackStatus.SUCCEEDED -> FocusReticleStatus.SUCCEEDED
        PreviewMeteringFeedbackStatus.DEGRADED_AUTO_EXPOSURE_ONLY -> FocusReticleStatus.DEGRADED
        PreviewMeteringFeedbackStatus.FAILED -> FocusReticleStatus.FAILED
        PreviewMeteringFeedbackStatus.UNSUPPORTED -> FocusReticleStatus.UNSUPPORTED
    }
)

internal fun previewOverlayRenderModel(
    state: SessionState,
    effectAdapter: PreviewEffectAdapter? = null,
    maskSnapshot: PreviewSceneMaskSnapshot? = null,
    previewContentAspect: PreviewContentAspect? = null,
    stagedWatermarkHint: WatermarkHintSpec? = null
): PreviewOverlayRenderModel {
    val resolvedSnapshot = maskSnapshot ?: PreviewSceneMaskSnapshot.UNAVAILABLE
    val gridMode = state.settings.persisted.common.gridMode
    val previewSupportsOverlay = state.permissionState.cameraGranted &&
        state.previewHostAvailable &&
        state.previewStatus in setOf(
            PreviewStatus.STARTING,
            PreviewStatus.ACTIVE,
            PreviewStatus.RECOVERING
        )
    val countdownLabel = state.countdownRemainingSeconds?.let { "${it}s" }
    val adaptedEffectModel = effectAdapter?.adapt(state.activeEffectSpec, resolvedSnapshot)
    val effectModel = when {
        stagedWatermarkHint != null && adaptedEffectModel != null ->
            adaptedEffectModel.copy(watermarkHint = stagedWatermarkHint)
        stagedWatermarkHint != null ->
            PreviewEffectRenderModel(
                filterOverlay = null,
                watermarkHint = stagedWatermarkHint,
                frameGuideline = null,
                compositionGrid = null
            )
        else -> adaptedEffectModel
    }
    val frameRatio = state.activeEffectSpec.find<FrameEffect>()?.ratio
    val frame = if (previewSupportsOverlay && frameRatio != null) {
        PreviewFrameRenderModel(
            ratio = frameRatio,
            label = frameRatio.label,
            dimOutsideFrame = true,
            zoomRatio = state.activeDeviceGraph.preview.zoomRatio,
            // TODO(pkg02): replace with state.activeDeviceGraph.preview.previewZoomRatio once merged
            previewZoomRatio = state.activeDeviceGraph.preview.zoomRatio
        )
    } else {
        null
    }
    return PreviewOverlayRenderModel(
        gridMode = gridMode,
        isGridVisible = previewSupportsOverlay && gridMode != CompositionGridMode.OFF,
        countdownLabel = countdownLabel,
        isCountdownVisible = state.captureStatus == CaptureStatus.REQUESTED &&
            countdownLabel != null &&
            state.permissionState.cameraGranted,
        effectModel = effectModel,
        frame = frame,
        previewContentAspect = previewContentAspect
    )
}

internal fun previewRatioToContentAspect(ratio: PreviewRatio): PreviewContentAspect? = when (ratio) {
    PreviewRatio.FULL -> null
    PreviewRatio.RATIO_4_3 -> PreviewContentAspect(4, 3)
    PreviewRatio.RATIO_16_9 -> PreviewContentAspect(16, 9)
    PreviewRatio.RATIO_1_1 -> PreviewContentAspect(1, 1)
}
