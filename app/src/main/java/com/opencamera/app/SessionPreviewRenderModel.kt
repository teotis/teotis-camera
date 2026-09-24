package com.opencamera.app

import com.opencamera.core.effect.DocumentEffect
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.effect.PreviewEffectAdapter
import com.opencamera.core.effect.PreviewEffectRenderModel
import com.opencamera.core.effect.PreviewSceneMaskSnapshot
import com.opencamera.core.effect.WatermarkHintSpec
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PreviewMeteringFeedback
import com.opencamera.core.session.PreviewMeteringFeedbackStatus
import com.opencamera.core.device.PreviewMeteringPersistence
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
    val previewContentAspect: PreviewContentAspect? = null,
    val isPreviewMirrored: Boolean = false,
    val isGeometryLocked: Boolean = false,
    val scanGuide: PreviewScanGuideRenderModel? = null
) {
    val isVisible: Boolean
        get() = isGridVisible || isCountdownVisible || effectModel != null || frame != null || scanGuide != null
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
    val previewZoomRatio: Float = 1f,
    val frameScrimAlpha: Int = PreviewOverlayView.FRAME_SCRIM_ALPHA_DEFAULT
)

internal data class PreviewScanGuideRenderModel(
    val isVisible: Boolean,
    val label: String,
    val contentAspect: PreviewContentAspect,
    val cornerLengthDp: Float
)

internal fun focusReticleRenderModel(
    feedback: PreviewMeteringFeedback
): FocusReticleRenderModel {
    val status = when (feedback.status) {
        PreviewMeteringFeedbackStatus.REQUESTED -> when (feedback.persistence) {
            PreviewMeteringPersistence.AUTO_CANCEL -> FocusReticleStatus.REQUESTED
            PreviewMeteringPersistence.HOLD_UNTIL_CANCELLED -> FocusReticleStatus.LOCK_REQUESTED
        }
        PreviewMeteringFeedbackStatus.SUCCEEDED -> FocusReticleStatus.SUCCEEDED
        PreviewMeteringFeedbackStatus.LOCKED -> FocusReticleStatus.LOCKED
        PreviewMeteringFeedbackStatus.DEGRADED_FOCUS_LOCK_ONLY,
        PreviewMeteringFeedbackStatus.DEGRADED_EXPOSURE_LOCK_ONLY -> FocusReticleStatus.LOCKED_DEGRADED
        PreviewMeteringFeedbackStatus.DEGRADED_AUTO_EXPOSURE_ONLY -> FocusReticleStatus.DEGRADED
        PreviewMeteringFeedbackStatus.FAILED -> FocusReticleStatus.FAILED
        PreviewMeteringFeedbackStatus.UNSUPPORTED -> FocusReticleStatus.UNSUPPORTED
    }
    val lockLabel = when (feedback.status) {
        PreviewMeteringFeedbackStatus.REQUESTED ->
            "AE/AF".takeIf { feedback.persistence == PreviewMeteringPersistence.HOLD_UNTIL_CANCELLED }
        PreviewMeteringFeedbackStatus.LOCKED -> "AE/AF"
        PreviewMeteringFeedbackStatus.DEGRADED_FOCUS_LOCK_ONLY -> "AF"
        PreviewMeteringFeedbackStatus.DEGRADED_EXPOSURE_LOCK_ONLY -> "AE"
        PreviewMeteringFeedbackStatus.SUCCEEDED,
        PreviewMeteringFeedbackStatus.DEGRADED_AUTO_EXPOSURE_ONLY,
        PreviewMeteringFeedbackStatus.FAILED,
        PreviewMeteringFeedbackStatus.UNSUPPORTED -> null
    }
    return FocusReticleRenderModel(
        normalizedX = feedback.normalizedX,
        normalizedY = feedback.normalizedY,
        status = status,
        lockLabel = lockLabel
    )
}

internal fun previewOverlayRenderModel(
    state: SessionState,
    effectAdapter: PreviewEffectAdapter? = null,
    maskSnapshot: PreviewSceneMaskSnapshot? = null,
    previewContentAspect: PreviewContentAspect? = null,
    stagedWatermarkHint: WatermarkHintSpec? = null,
    isGeometryLocked: Boolean = false
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
    val watermarkPreviewSupported = state.activeMode in watermarkPreviewCapableModes
    val adaptedEffectModel = effectAdapter?.adapt(state.activeEffectSpec, resolvedSnapshot)
    val effectModel = when {
        !watermarkPreviewSupported ->
            adaptedEffectModel.withoutWatermarkHint()
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
    val frame = if (frameRatio != null) {
        PreviewFrameRenderModel(
            ratio = frameRatio,
            label = frameRatio.label,
            dimOutsideFrame = true,
            zoomRatio = state.activeDeviceGraph.preview.zoomRatio,
            previewZoomRatio = state.activeDeviceGraph.preview.previewZoomRatio
        )
    } else {
        null
    }
    val docEffect = state.activeEffectSpec.find<DocumentEffect>()
    val scanGuide = if (docEffect != null && docEffect.scanGuide && frame == null) {
        val aspect = previewContentAspect ?: PreviewContentAspect(4, 3)
        PreviewScanGuideRenderModel(
            isVisible = true,
            label = "对准文档",
            contentAspect = aspect,
            cornerLengthDp = 48f
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
        previewContentAspect = previewContentAspect,
        isPreviewMirrored = com.opencamera.core.mode.selfieMirrorPolicy(
            activeLensFacing = state.activeDeviceGraph.activeLensFacing,
            preferredLensFacing = state.activeDeviceGraph.preferredLensFacing,
            selfieMirrorEnabled = state.settings.persisted.common.selfieMirrorEnabled
        ).shouldMirrorPreview,
        isGeometryLocked = isGeometryLocked,
        scanGuide = scanGuide
    )
}

private val watermarkPreviewCapableModes = setOf(
    ModeId.PHOTO,
    ModeId.CHECK_IN,
    ModeId.HUMANISTIC
)

private fun PreviewEffectRenderModel?.withoutWatermarkHint(): PreviewEffectRenderModel? {
    val stripped = this?.copy(watermarkHint = null) ?: return null
    return stripped.takeUnless { it == PreviewEffectRenderModel.EMPTY }
}

internal fun previewRatioToContentAspect(ratio: PreviewRatio): PreviewContentAspect? = when (ratio) {
    PreviewRatio.FULL -> null
    PreviewRatio.RATIO_4_3 -> PreviewContentAspect(4, 3)
    PreviewRatio.RATIO_16_9 -> PreviewContentAspect(16, 9)
    PreviewRatio.RATIO_1_1 -> PreviewContentAspect(1, 1)
}
