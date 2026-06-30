package com.opencamera.app

import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.EffectiveStillCaptureRecipe
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.device.normalizedZoomRatioValue
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.SavedMediaType
import com.opencamera.core.mode.modeDirectoryDeclaration
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.PreviewBrightnessFeedbackStatus
import com.opencamera.core.settings.VideoSpec
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.hasUserAdjustments
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.PhotoLowLightPromptStatus
import com.opencamera.app.i18n.AppTextResolver
import com.opencamera.app.camera.live.LivePhotoStatusProjection
import com.opencamera.core.settings.LiveSaveFormat
import kotlin.math.roundToInt

internal data class ZoomCapsuleRenderModel(
    val label: String,
    val ratio: Float,
    val isActive: Boolean
)

internal data class FocalLengthSliderRenderModel(
    val presetRatios: List<Float>,
    val currentRatio: Float,
    val isVisible: Boolean,
    val isEnabled: Boolean = true,
    val disabledReason: String? = null
)

internal data class SessionControlsRenderModel(
    val lensFacingButtonLabel: String,
    val lensFacingEnabled: Boolean,
    val lensFacingVisible: Boolean,
    val zoomCapsules: List<ZoomCapsuleRenderModel>,
    val isZoomCapsuleRowVisible: Boolean,
    val focalLengthSlider: FocalLengthSliderRenderModel,
    val currentZoomLabel: String? = null,
    val isContinuousZoomActive: Boolean = false,
    val nearestPresetRatio: Float? = null
)

internal data class FrameRatioOptionRenderModel(
    val label: String,
    val ratio: FrameRatio,
    val isSelected: Boolean,
    val isEnabled: Boolean
)

internal data class FrameRatioControlRenderModel(
    val title: String,
    val currentLabel: String,
    val options: List<FrameRatioOptionRenderModel>,
    val isVisible: Boolean,
    val isEnabled: Boolean,
    val disabledReason: String?
)

internal enum class QuickControlKind {
    TOGGLE,
    CYCLE,
    SLIDER,
    SEGMENTED
}

internal data class QuickPanelRowRenderModel(
    val title: String,
    val value: String,
    val isEnabled: Boolean,
    val disabledReason: String? = null,
    val controlKind: QuickControlKind = QuickControlKind.CYCLE,
    val isSelected: Boolean = false
)

internal data class QuickBrightnessRenderModel(
    val title: String,
    val value: String,
    val steps: Int,
    val minSteps: Int,
    val maxSteps: Int,
    val isInteractive: Boolean,
    val isVisible: Boolean,
    val disabledReason: String?
) {
    val canDecrease: Boolean
        get() = isInteractive && steps > minSteps
    val canReset: Boolean
        get() = isInteractive && steps != 0
    val canIncrease: Boolean
        get() = isInteractive && steps < maxSteps
}

internal data class LowLightNightPromptRenderModel(
    val label: String,
    val contentDescription: String,
    val isVisible: Boolean,
    val isEnabled: Boolean
)

internal data class QuickPanelSheetRenderModel(
    val gridRow: QuickPanelRowRenderModel,
    val resolutionRow: QuickPanelRowRenderModel,
    val brightnessRow: QuickBrightnessRenderModel,
    val frameRatioRow: QuickPanelRowRenderModel,
    val frameRatioNext: FrameRatio?,
    val frameRatioOptions: List<FrameRatioOptionRenderModel>,
    val frameRatioEnabled: Boolean,
    val frameRatioDisabledReason: String?,
    val watermarkRow: QuickPanelRowRenderModel,
    val watermarkNextTemplateId: String?,
    val watermarkAction: PersistedSettingsAction?,
    val liveRow: QuickPanelRowRenderModel,
    val timerRow: QuickPanelRowRenderModel,
    val hasQuickUserAdjustments: Boolean = false,
    val resetQuickAction: PersistedSettingsAction.ResetToDefaults? = null
)

internal data class ModeDirectoryItemRenderModel(
    val modeId: ModeId,
    val displayName: String,
    val buttonLabel: String,
    val defaultStyleLabel: String,
    val declaredSubfeatures: String,
    val isActive: Boolean
)

internal data class ModeDirectoryRenderModel(
    val items: List<ModeDirectoryItemRenderModel>
)

internal data class ModeTrackItemRenderModel(
    val modeId: ModeId,
    val trackLabel: String,
    val isActive: Boolean,
    val isAvailable: Boolean
)

internal data class ModeTrackRenderModel(
    val items: List<ModeTrackItemRenderModel>
)

internal data class PrimaryStatusRenderModel(
    val modeLabel: String,
    val statusText: String
)

internal fun captureDisabledReason(state: SessionState, text: AppTextResolver): String? {
    if (!state.permissionState.cameraGranted) return text.get(R.string.disabled_permission)
    if (state.previewStatus == PreviewStatus.RECOVERING) return text.get(R.string.disabled_preview_recovering)
    if (state.countdownRemainingSeconds != null) return text.get(R.string.disabled_countdown)
    if (state.activeShot != null && state.recordingStatus == RecordingStatus.REQUESTING) return text.get(R.string.disabled_preparing_recording)
    if (state.recordingStatus == RecordingStatus.RECORDING) return text.get(R.string.disabled_recording)
    if (state.recordingStatus == RecordingStatus.STOPPING) return text.get(R.string.disabled_stopping_recording)
    if (state.activeShot != null && (state.captureStatus == CaptureStatus.REQUESTED || state.captureStatus == CaptureStatus.SAVING || state.captureStatus == CaptureStatus.DATA_RECEIVED)) return text.get(R.string.disabled_saving_photo)
    return null
}

/**
 * Shutter-specific disabled reason.
 * Unlike [captureDisabledReason], this keeps the shutter enabled during active recording
 * (shutter is the stop-recording command) and when camera permission is missing
 * (shutter tap requests permission).
 */
internal fun shutterDisabledReason(state: SessionState, text: AppTextResolver): String? {
    if (state.previewStatus == PreviewStatus.RECOVERING) return text.get(R.string.disabled_preview_recovering)
    if (state.countdownRemainingSeconds != null) return text.get(R.string.disabled_countdown)
    val activeShot = state.activeShot
    if (activeShot != null && activeShot.mediaType == com.opencamera.core.media.MediaType.PHOTO) {
        // CaptureReadiness marks the explicit user-facing readiness boundary:
        // the frame is acquired and the shutter is safe to re-arm.
        if (state.presentation.captureReadiness != null) return null
        return text.get(R.string.disabled_saving_photo)
    }
    // Session rearm policy: ordinary still capture clears activeShot at DATA_RECEIVED,
    // so the shutter is safe to re-arm even though postprocess/save may still be finishing.
    // BACKGROUND_SAVING visual state shows a subtle indicator while the shutter remains enabled.
    // Conservative capture kinds (multi-frame, live photo) keep activeShot until ShotCompleted.
    if (activeShot == null && (state.captureStatus == CaptureStatus.DATA_RECEIVED || state.captureStatus == CaptureStatus.SAVING)) return null
    if (state.recordingStatus == RecordingStatus.REQUESTING) return text.get(R.string.disabled_preparing_recording)
    if (state.recordingStatus == RecordingStatus.STOPPING) return text.get(R.string.disabled_stopping_recording)
    return null
}

internal fun shutterVisualState(state: SessionState): ShutterVisualState {
    if (state.previewStatus == PreviewStatus.RECOVERING) return ShutterVisualState.BLOCKED
    if (!state.permissionState.cameraGranted) return ShutterVisualState.BLOCKED
    if (state.countdownRemainingSeconds != null) return ShutterVisualState.COUNTDOWN
    val activeShot = state.activeShot
    if (activeShot != null && activeShot.mediaType == com.opencamera.core.media.MediaType.PHOTO) {
        // CaptureReadiness is the explicit user-facing readiness boundary.
        // Once readiness is signaled, the button shows "ready" even while
        // post-processing continues in the background.
        if (state.presentation.captureReadiness != null) {
            return ShutterVisualState.PHOTO_READY
        }
        return when (state.captureStatus) {
            CaptureStatus.REQUESTED -> ShutterVisualState.PHOTO_PRESSED
            else -> ShutterVisualState.SAVING
        }
    }
    // Background save indicator: captureStatus is still SAVING but activeShot is already cleared
    // (session rearm policy). Shutter is enabled; show subtle background work indicator.
    if (state.captureStatus == CaptureStatus.SAVING) return ShutterVisualState.BACKGROUND_SAVING
    when (state.recordingStatus) {
        RecordingStatus.REQUESTING -> return ShutterVisualState.VIDEO_REQUESTING
        RecordingStatus.RECORDING -> return ShutterVisualState.VIDEO_RECORDING
        RecordingStatus.STOPPING -> return ShutterVisualState.VIDEO_STOPPING
        RecordingStatus.IDLE -> Unit
    }
    if (state.captureStatus == CaptureStatus.FAILED) return ShutterVisualState.FAILURE_OR_DEGRADED
    return ShutterVisualState.PHOTO_READY
}

internal fun sessionControlsRenderModel(
    state: SessionState,
    strings: SessionUiStrings,
    text: AppTextResolver
): SessionControlsRenderModel {
    val capability = state.activeDeviceCapabilities.zoomRatioCapability
    val currentRatio = normalizedZoomRatioValue(state.activeDeviceGraph.preview.zoomRatio)
    val presets = capability.normalizedSupportedRatios
    val exactMatch = currentRatio in presets
    val sliderEnabled = capability.isSwitchingSupported && !isZoomBlockedBySession(state, capability.support)
    val sliderDisabledReason = if (capability.isSwitchingSupported && !sliderEnabled) {
        zoomDisabledReasonText(state, capability.support, text)
    } else null
    val lensFacingVisible = state.activeMode != ModeId.DOCUMENT

    return SessionControlsRenderModel(
        lensFacingButtonLabel = lensFacingButtonLabel(state, strings),
        lensFacingEnabled = lensFacingVisible && state.activeDeviceCapabilities.availableLensFacings.size > 1,
        lensFacingVisible = lensFacingVisible,
        zoomCapsules = zoomCapsuleModels(state),
        isZoomCapsuleRowVisible = capability.isSwitchingSupported,
        focalLengthSlider = FocalLengthSliderRenderModel(
            presetRatios = if (capability.isSwitchingSupported) presets else emptyList(),
            currentRatio = currentRatio,
            isVisible = capability.isSwitchingSupported,
            isEnabled = sliderEnabled,
            disabledReason = sliderDisabledReason
        ),
        currentZoomLabel = if (capability.isSwitchingSupported && !exactMatch) compactZoomLabel(currentRatio) else null,
        isContinuousZoomActive = false,
        nearestPresetRatio = if (!exactMatch && presets.isNotEmpty()) {
            presets.minByOrNull { kotlin.math.abs(it - currentRatio) }
        } else null
    )
}

private val stillModesWithFrameRatio = setOf(
    ModeId.PHOTO,
    ModeId.CHECK_IN,
    ModeId.HUMANISTIC
)

private val livePhotoCapableModes = setOf(
    ModeId.PHOTO,
    ModeId.CHECK_IN,
    ModeId.HUMANISTIC
)

private val watermarkCapableModes = setOf(
    ModeId.PHOTO,
    ModeId.CHECK_IN,
    ModeId.HUMANISTIC
)

internal fun brightnessRenderModel(state: SessionState, text: AppTextResolver): QuickBrightnessRenderModel {
    val isBrightnessCapableMode = state.activeMode in BRIGHTNESS_CAPABLE_MODES
    val isPreviewActive = state.previewStatus == PreviewStatus.ACTIVE
    val isBusy = state.activeShot != null || state.countdownRemainingSeconds != null
    val isVisible = isBrightnessCapableMode
    val range = state.activeDeviceCapabilities.previewBrightnessRange
    val isUnsupported = range == com.opencamera.core.device.PreviewBrightnessRange.UNSUPPORTED
    val feedback = state.presentation.previewBrightnessFeedback
    val steps = if (feedback?.status == PreviewBrightnessFeedbackStatus.REQUESTED) {
        feedback.requestedSteps
    } else {
        state.presentation.previewBrightnessSteps
    }
    val canInteract = isBrightnessCapableMode && isPreviewActive && !isBusy && !isUnsupported

    val valueLabel = if (isUnsupported) {
        text.get(R.string.button_quick_brightness_na)
    } else {
        brightnessValueLabel(steps)
    }

    val disabledReason = when {
        !isBrightnessCapableMode -> null
        isUnsupported -> text.get(R.string.disabled_brightness_unsupported)
        isBusy -> text.get(R.string.disabled_brightness_active_shot)
        !isPreviewActive -> text.get(R.string.disabled_brightness_preview_inactive)
        else -> null
    }

    return QuickBrightnessRenderModel(
        title = text.get(R.string.button_quick_brightness),
        value = valueLabel,
        steps = steps,
        minSteps = range.minSteps,
        maxSteps = range.maxSteps,
        isInteractive = canInteract,
        isVisible = isVisible,
        disabledReason = disabledReason
    )
}

private val BRIGHTNESS_CAPABLE_MODES = setOf(
    ModeId.PHOTO,
    ModeId.HUMANISTIC,
    ModeId.DOCUMENT,
    ModeId.CHECK_IN,
    ModeId.VIDEO
)

private fun brightnessValueLabel(steps: Int): String {
    return if (steps >= 0) "+$steps" else "$steps"
}

internal fun lowLightNightPromptRenderModel(
    state: SessionState,
    text: AppTextResolver
): LowLightNightPromptRenderModel {
    val prompt = state.presentation.photoLowLightPrompt
    val isVisible = prompt != null &&
        prompt.status != PhotoLowLightPromptStatus.HIDDEN &&
        prompt.status != PhotoLowLightPromptStatus.UNSUPPORTED
    val isEnabled = prompt?.status == PhotoLowLightPromptStatus.AVAILABLE_ENABLED ||
        prompt?.status == PhotoLowLightPromptStatus.DEGRADED_ENABLED
    val isDegraded = prompt?.status == PhotoLowLightPromptStatus.DEGRADED_ENABLED ||
        prompt?.status == PhotoLowLightPromptStatus.DEGRADED_DISABLED
    val label = when {
        prompt == null -> ""
        isDegraded -> text.get(R.string.button_low_light_night_prompt_degraded)
        isEnabled -> text.get(R.string.button_low_light_night_prompt_enabled)
        else -> text.get(R.string.button_low_light_night_prompt_disabled)
    }
    return LowLightNightPromptRenderModel(
        label = label,
        contentDescription = text.get(R.string.low_light_night_assist_content_description),
        isVisible = isVisible,
        isEnabled = isEnabled
    )
}

internal fun frameRatioControlRenderModel(state: SessionState, text: AppTextResolver): FrameRatioControlRenderModel {
    val current = state.activeEffectSpec.find<FrameEffect>()?.ratio ?: FrameRatio.RATIO_4_3
    val isSupportedMode = state.activeMode in stillModesWithFrameRatio
    val isBusy = state.activeShot != null || state.countdownRemainingSeconds != null
    val enabled = isSupportedMode && !isBusy
    val reason = when {
        !isSupportedMode -> text.get(R.string.disabled_frame_ratio_unsupported_mode)
        state.activeShot != null -> text.get(R.string.disabled_frame_ratio_active_shot)
        state.countdownRemainingSeconds != null -> text.get(R.string.disabled_frame_ratio_countdown)
        else -> null
    }
    return FrameRatioControlRenderModel(
        title = text.get(R.string.label_frame_ratio_title),
        currentLabel = current.label,
        options = FrameRatio.entries.map { ratio ->
            FrameRatioOptionRenderModel(
                label = ratio.label,
                ratio = ratio,
                isSelected = ratio == current,
                isEnabled = enabled
            )
        },
        isVisible = true,
        isEnabled = enabled,
        disabledReason = reason
    )
}

internal fun quickPanelSheetRenderModel(
    state: SessionState,
    text: AppTextResolver,
    strings: SessionUiStrings
): QuickPanelSheetRenderModel {
    val settings = state.settings.persisted
    val frameControl = frameRatioControlRenderModel(state, text)
    val brightnessControl = brightnessRenderModel(state, text)

    val stillTemplate = state.activeDeviceGraph.template == CaptureTemplate.STILL_CAPTURE
    val stillBusy = state.activeShot != null || state.countdownRemainingSeconds != null
    val stillQualityEnabled = stillTemplate && !stillBusy && state.activeDeviceCapabilities.supportsStillCapture
    val supportsStillCapture = state.activeDeviceCapabilities.supportsStillCapture

    val resolutionEnabled = stillQualityEnabled && isStillResolutionToggleEnabled(state)

    val gridValue = text.gridModeLabel(settings.common.gridMode)
    val gridEnabled = !stillBusy

    val liveModeSupported = state.activeMode in livePhotoCapableModes && stillTemplate
    val liveEnabled = liveModeSupported && supportsStillCapture && !stillBusy
    val liveDisabledReason = when {
        !liveModeSupported -> text.get(R.string.disabled_live_photo_unsupported_mode)
        state.activeShot != null -> text.get(R.string.disabled_saving_photo)
        state.countdownRemainingSeconds != null -> text.get(R.string.disabled_countdown)
        else -> null
    }
    val liveValue = text.onOff(settings.photo.livePhotoEnabledByDefault)
    val liveSelected = settings.photo.livePhotoEnabledByDefault

    val countdownLabel = text.countdownValueLabel(settings.photo.countdownDuration)
    val timerEnabled = supportsStillCapture && !stillBusy

    val catalog = state.settings.catalog
    val watermarkTemplates = catalog.watermarkTemplates
    val currentTemplateId = settings.photo.defaultWatermarkTemplateId
    val currentTemplate = watermarkTemplates.firstOrNull { it.id == currentTemplateId }
    val watermarkModeSupported = state.activeMode in watermarkCapableModes
    val watermarkControlEnabled = watermarkModeSupported && stillTemplate && !stillBusy && supportsStillCapture && watermarkTemplates.isNotEmpty()
    val watermarkSelected = settings.photo.photoWatermarkEnabledByDefault
    val watermarkDisabledReason = when {
        !watermarkModeSupported -> text.get(R.string.watermark_unsupported)
        !supportsStillCapture -> null
        !stillTemplate -> null
        stillBusy -> text.get(R.string.disabled_saving_photo)
        watermarkTemplates.isEmpty() -> text.get(R.string.error_no_watermark_templates)
        else -> null
    }
    val nextTemplateId = if (watermarkControlEnabled && watermarkSelected && watermarkTemplates.size > 1) {
        val currentIndex = watermarkTemplates.indexOfFirst { it.id == currentTemplateId }
        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % watermarkTemplates.size else 0
        watermarkTemplates[nextIndex].id
    } else {
        null
    }
    val watermarkLabel = if (watermarkSelected) {
        currentTemplate?.localizedLabel(text) ?: currentTemplateId
    } else {
        text.onOff(false)
    }
    val watermarkAction = when {
        !watermarkControlEnabled -> null
        !watermarkSelected -> PersistedSettingsAction.UpdatePhotoWatermarkEnabled(true)
        nextTemplateId != null -> PersistedSettingsAction.UpdatePhotoWatermarkTemplate(nextTemplateId)
        else -> null
    }

    val currentRatio = state.activeEffectSpec.find<FrameEffect>()?.ratio ?: FrameRatio.RATIO_4_3
    val entries = FrameRatio.entries
    val nextRatio = if (frameControl.isEnabled) {
        val nextIndex = (entries.indexOf(currentRatio) + 1) % entries.size
        entries[nextIndex]
    } else {
        null
    }

    return QuickPanelSheetRenderModel(
        gridRow = QuickPanelRowRenderModel(
            title = text.get(R.string.button_quick_grid),
            value = gridValue,
            isEnabled = gridEnabled,
            controlKind = QuickControlKind.CYCLE
        ),
        resolutionRow = QuickPanelRowRenderModel(
            title = text.get(R.string.button_quick_resolution),
            value = stillResolutionQuickLabel(state, strings),
            isEnabled = resolutionEnabled,
            controlKind = QuickControlKind.CYCLE
        ),
        brightnessRow = brightnessControl,
        frameRatioRow = QuickPanelRowRenderModel(
            title = text.get(R.string.label_frame_ratio_title),
            value = frameControl.currentLabel,
            isEnabled = frameControl.isEnabled,
            disabledReason = frameControl.disabledReason,
            controlKind = QuickControlKind.SEGMENTED
        ),
        frameRatioNext = nextRatio,
        frameRatioOptions = frameControl.options,
        frameRatioEnabled = frameControl.isEnabled,
        frameRatioDisabledReason = frameControl.disabledReason,
        watermarkRow = QuickPanelRowRenderModel(
            title = text.get(R.string.button_quick_watermark),
            value = watermarkLabel,
            isEnabled = watermarkControlEnabled,
            disabledReason = watermarkDisabledReason,
            controlKind = QuickControlKind.CYCLE,
            isSelected = watermarkSelected
        ),
        watermarkNextTemplateId = nextTemplateId,
        watermarkAction = watermarkAction,
        liveRow = QuickPanelRowRenderModel(
            title = text.get(R.string.button_quick_live),
            value = liveValue,
            isEnabled = liveEnabled,
            disabledReason = liveDisabledReason,
            controlKind = QuickControlKind.TOGGLE,
            isSelected = liveSelected
        ),
        timerRow = QuickPanelRowRenderModel(
            title = text.get(R.string.button_quick_timer),
            value = countdownLabel,
            isEnabled = timerEnabled,
            controlKind = QuickControlKind.CYCLE
        ),
        hasQuickUserAdjustments = false,
        resetQuickAction = null
    )
}

internal fun quickLivePhotoToggleAction(
    state: SessionState?,
    sheet: QuickPanelSheetRenderModel?
): PersistedSettingsAction.UpdateLivePhotoDefault? {
    if (state == null || sheet?.liveRow?.isEnabled != true) return null
    return PersistedSettingsAction.UpdateLivePhotoDefault(
        enabled = !state.settings.persisted.photo.livePhotoEnabledByDefault
    )
}

internal fun sessionSummaryText(state: SessionState, text: AppTextResolver): String {
    val presentation = state.presentation
    val settingsRenderModel = sessionSettingsRenderModel(state, text)
    return buildString {
        appendLine("Lifecycle: ${state.lifecycle}")
        appendLine(
            "Permissions: camera=${state.permissionState.cameraGranted}, " +
                "mic=${state.permissionState.microphoneGranted}"
        )
        appendLine(
            "Preview: ${state.previewStatus}" +
                state.previewStatusDetail?.let { " ($it)" }.orEmpty()
        )
        appendLine("Capture: ${state.captureStatus}")
        state.countdownRemainingSeconds?.let { countdown ->
            appendLine("Countdown: ${countdown}s")
        }
        appendLine("Recording: ${state.recordingStatus}")
        appendLine(
            "First frame: ${state.previewMetrics.lastFirstFrameLatencyMillis ?: "--"} ms"
        )
        appendLine(
            "Preview binds: ${state.previewMetrics.bindCount}, " +
                "recoveries: ${state.previewMetrics.recoveryCount}"
        )
        appendLine(
            "Lens: ${state.activeDeviceGraph.preferredLensFacing.label} " +
                "(available: ${state.activeDeviceCapabilities.availableLensFacings.lensFacingSummary()})"
        )
        appendLine(
            "Zoom: ${zoomRatioLabel(state.activeDeviceGraph.preview.zoomRatio)} " +
                "(available: ${state.activeDeviceCapabilities.zoomRatioCapability.zoomRatioSummary()})"
        )
        appendLine(
            "Still resolution: ${state.activeDeviceGraph.stillCapture.resolutionOption?.label ?: "Auto"} " +
                "(available: ${
                    state.activeDeviceCapabilities.availableStillCaptureResolutionOptions
                        .joinToString("/") { it.label }
                })"
        )
        appendLine(
            "Still native output: ${displayedStillCaptureOutputSize(state).label}"
        )
        if (state.activeDeviceCapabilities.availableStillCaptureOutputSizes.isNotEmpty()) {
            appendLine(
                "Native size list: ${
                    state.activeDeviceCapabilities.availableStillCaptureOutputSizes
                        .stillCaptureOutputSizeSummary()
                }"
            )
        }
        appendLine("Settings: ${settingsRenderModel.commonSummary}")
        appendLine("Photo defaults: ${settingsRenderModel.photoSummary}")
        appendLine("Video defaults: ${settingsRenderModel.videoSummary}")
        appendLine("Catalog: ${settingsRenderModel.catalogSummary}")
        appendLine("Manual draft: ${settingsRenderModel.manualDraftSummary}")
        append("Action: ${presentation.lastAction}")
    }
}

internal fun modeSummaryText(state: SessionState): String {
    return buildString {
        appendLine("Mode: ${state.modeSnapshot.uiSpec.title}")
        appendLine(state.modeSnapshot.state.headline)
        append(state.modeSnapshot.state.detail)
    }
}

internal fun modeDirectoryRenderModel(
    state: SessionState,
    text: AppTextResolver
): ModeDirectoryRenderModel {
    return ModeDirectoryRenderModel(
        items = visibleModeEntryOrder(state.availableModes).map { modeId ->
            val declaration = modeId.modeDirectoryDeclaration(
                deviceCapabilities = state.activeDeviceCapabilities,
                settingsSnapshot = state.settings
            )
            ModeDirectoryItemRenderModel(
                modeId = modeId,
                displayName = text.modeDisplayName(modeId),
                buttonLabel = declaration.catalogProfile.buttonLabel,
                defaultStyleLabel = declaration.defaultStyleLabel,
                declaredSubfeatures = declaration.declaredSubfeatures,
                isActive = modeId == state.activeMode
            )
        }
    )
}

internal fun modeTrackRenderModel(
    state: SessionState,
    text: AppTextResolver
): ModeTrackRenderModel {
    return ModeTrackRenderModel(
        items = visibleModeEntryOrder(state.availableModes).map { modeId ->
            ModeTrackItemRenderModel(
                modeId = modeId,
                trackLabel = text.modeTrackLabel(modeId),
                isActive = modeId == state.activeMode,
                isAvailable = true
            )
        }
    )
}

private val PRODUCT_MODE_ENTRY_ORDER = listOf(
    ModeId.PHOTO,
    ModeId.CHECK_IN,
    ModeId.HUMANISTIC,
    ModeId.DOCUMENT,
    ModeId.VIDEO
)

private fun visibleModeEntryOrder(availableModes: List<ModeId>): List<ModeId> {
    val available = availableModes.toSet()
    return PRODUCT_MODE_ENTRY_ORDER.filter { modeId -> modeId in available }
}

internal fun primaryStatusRenderModel(
    state: SessionState,
    text: AppTextResolver
): PrimaryStatusRenderModel {
    val modeLabel = text.modeDisplayName(state.activeMode)
    val pendingPp = state.presentation.pendingPostprocess
    val statusText = if (pendingPp != null) {
        text.get(R.string.status_processing_photo_keep_open)
    } else {
        buildString {
            append(text.previewStatusLabel(state.previewStatus))
            if (state.captureStatus != CaptureStatus.IDLE) {
                append(" · ${text.captureStatusLabel(state.captureStatus)}")
            }
            state.countdownRemainingSeconds?.let { append(" · ${it}s") }
            when (state.recordingStatus) {
                RecordingStatus.REQUESTING -> append(" · ${text.get(R.string.status_recording_starting)}")
                RecordingStatus.RECORDING -> append(" · ${text.get(R.string.status_recording_active)}")
                RecordingStatus.STOPPING -> append(" · ${text.get(R.string.status_recording_saving)}")
                RecordingStatus.IDLE -> Unit
            }
            if (state.modeSnapshot.state.isProVariantActive) {
                append(" · ${text.get(R.string.status_pro_active)}")
            }
        }
    }
    return PrimaryStatusRenderModel(
        modeLabel = modeLabel,
        statusText = statusText
    )
}

internal fun modeDirectoryText(
    state: SessionState,
    text: AppTextResolver
): String {
    return modeDirectoryRenderModel(state, text).items.joinToString(separator = "\n") { item ->
        val marker = if (item.isActive) "•" else "·"
        "$marker ${item.displayName} | Default ${item.defaultStyleLabel} | ${item.declaredSubfeatures}"
    }
}

internal fun sessionCaptureOutputText(
    state: SessionState,
    strings: SessionUiStrings,
    text: AppTextResolver
): String {
    val presentation = state.presentation
    val livePhotoBundle = presentation.latestLivePhotoBundle
    return when {
        presentation.lastError != null -> "${strings.outputErrorPrefix} ${presentation.lastError}"
        presentation.latestSavedMediaType == SavedMediaType.VIDEO &&
            presentation.latestVideoPath != null ->
            buildString {
                append(strings.outputVideoPrefix)
                append('\n')
                append(presentation.latestVideoPath)
                appendPipelineNotes(presentation)
            }

        presentation.latestSavedMediaType == SavedMediaType.PHOTO &&
            livePhotoBundle != null ->
            buildString {
                append(strings.outputLivePrefix)
                append('\n')
                val hasLivePhotoFormat = presentation.latestPipelineNotes.any {
                    it.startsWith("live-export:format=") || it.startsWith("live-motion:status=")
                }
                if (hasLivePhotoFormat) {
                    val status = LivePhotoStatusProjection.project(
                        pipelineNotes = presentation.latestPipelineNotes,
                        bundle = livePhotoBundle,
                        saveFormat = extractLiveSaveFormat(presentation.latestPipelineNotes)
                    )
                    LivePhotoStatusProjection.statusText(status)?.let { statusText ->
                        append(statusText)
                        append('\n')
                    }
                }
                appendLiveAssetLine(
                    label = text.get(R.string.live_photo_asset_still),
                    path = livePhotoBundle.stillPath,
                    renderUri = livePhotoBundle.thumbnailHandle.contentUri
                )
                append('\n')
                appendLiveAssetLine(
                    label = text.get(R.string.live_photo_asset_motion),
                    path = livePhotoBundle.motionPath,
                    renderUri = livePhotoBundle.motionHandle.contentUri
                )
                append('\n')
                appendLiveAssetLine(
                    label = text.get(R.string.live_photo_asset_sidecar),
                    path = livePhotoBundle.sidecarPath,
                    renderUri = livePhotoBundle.sidecarHandle.contentUri
                )
                append('\n')
                appendLiveAssetLine(
                    label = text.get(R.string.live_photo_asset_thumbnail),
                    path = livePhotoBundle.thumbnailPath,
                    renderUri = livePhotoBundle.thumbnailHandle.contentUri
                )
                appendPipelineNotes(presentation)
            }

        presentation.latestSavedMediaType == SavedMediaType.PHOTO &&
            presentation.latestCapturePath != null ->
            buildString {
                append(strings.outputSavedPrefix)
                append('\n')
                append(presentation.latestCapturePath)
                appendPipelineNotes(presentation)
            }

        presentation.previewThumbnailPath != null ->
            strings.outputPreviewPrefix

        else -> strings.outputWaiting
    }
}

// -- Private helpers --

private fun lensFacingButtonLabel(
    state: SessionState,
    strings: SessionUiStrings
): String {
    val availableLensFacings = state.activeDeviceCapabilities.availableLensFacings
    if (availableLensFacings.size < 2) {
        return strings.buttonSingleLens
    }
    return when (state.activeDeviceGraph.preferredLensFacing) {
        LensFacing.BACK -> strings.buttonSwitchToFront
        LensFacing.FRONT -> strings.buttonSwitchToBack
    }
}

private fun zoomCapsuleModels(state: SessionState): List<ZoomCapsuleRenderModel> {
    val capability = state.activeDeviceCapabilities.zoomRatioCapability
    if (!capability.isSwitchingSupported) return emptyList()
    val currentRatio = normalizedZoomRatioValue(state.activeDeviceGraph.preview.zoomRatio)
    return capability.normalizedSupportedRatios.map { ratio ->
        ZoomCapsuleRenderModel(
            label = compactZoomLabel(ratio),
            ratio = ratio,
            isActive = ratio == currentRatio
        )
    }
}

private fun zoomRatioLabel(zoomRatio: Float): String {
    return "${normalizedZoomRatioValue(zoomRatio)}x"
}

private fun compactZoomLabel(ratio: Float): String {
    if (ratio == 1.0f) return "1x"
    val formatted = String.format(java.util.Locale.US, "%.1f", ratio)
    return if (formatted.endsWith(".0")) formatted.dropLast(2) else formatted
}

private fun isZoomBlockedBySession(state: SessionState, zoomSupport: ZoomControlSupport = ZoomControlSupport.UNSUPPORTED): Boolean {
    if (state.countdownRemainingSeconds != null) return true
    val activeShot = state.activeShot
    if (activeShot != null && activeShot.mediaType == com.opencamera.core.media.MediaType.PHOTO) return true
    if (state.recordingStatus == RecordingStatus.REQUESTING) return true
    if (state.recordingStatus == RecordingStatus.STOPPING) return true
    if (state.recordingStatus == RecordingStatus.RECORDING && zoomSupport == ZoomControlSupport.DISCRETE_PRESET) return true
    return false
}

private fun zoomDisabledReasonText(
    state: SessionState,
    zoomSupport: ZoomControlSupport = ZoomControlSupport.UNSUPPORTED,
    text: AppTextResolver
): String {
    if (state.countdownRemainingSeconds != null) return text.get(R.string.disabled_countdown)
    val activeShot = state.activeShot
    if (activeShot != null && activeShot.mediaType == com.opencamera.core.media.MediaType.PHOTO) return text.get(R.string.disabled_saving_photo)
    if (state.recordingStatus == RecordingStatus.REQUESTING) return text.get(R.string.disabled_preparing_recording)
    if (state.recordingStatus == RecordingStatus.STOPPING) return text.get(R.string.disabled_stopping_recording)
    if (state.recordingStatus == RecordingStatus.RECORDING && zoomSupport == ZoomControlSupport.DISCRETE_PRESET) {
        return text.get(R.string.disabled_preset_switching_recording)
    }
    return text.get(R.string.disabled_zoom_unavailable)
}

private fun ZoomRatioCapability.zoomRatioSummary(): String {
    return buildString {
        append(
            normalizedSupportedRatios.joinToString(separator = "/") { ratio ->
                zoomRatioLabel(ratio)
            }
        )
        append(" | ")
        append(support.label)
    }
}

private fun StringBuilder.appendPipelineNotes(presentation: SessionPresentationState) {
    if (presentation.latestPipelineNotes.isEmpty()) {
        return
    }
    append("\nPipeline: ")
    append(presentation.latestPipelineNotes.joinToString())
}

private fun StringBuilder.appendLiveAssetLine(
    label: String,
    path: String,
    renderUri: String?
) {
    append(label)
    append(": ")
    append(path)
    renderUri?.let {
        append(" (")
        append(it)
        append(')')
    }
}

private val StillCaptureOutputSize.label: String
    get() = "${width}x${height}"

private val LensFacing.label: String
    get() = when (this) {
        LensFacing.BACK -> "Back"
        LensFacing.FRONT -> "Front"
    }

private fun Set<LensFacing>.lensFacingSummary(): String {
    return this.sortedBy { it.ordinal }
        .joinToString(separator = "/") { it.label.lowercase() }
}

private fun stillResolutionQuickLabel(state: SessionState, strings: SessionUiStrings): String {
    val recipe = EffectiveStillCaptureRecipe.build(
        state.activeDeviceGraph,
        state.activeDeviceCapabilities
    )
    return recipe.quickLabel
}

private fun Set<StillCaptureResolutionPreset>.stillResolutionPresetSummary(): String {
    return this.sortedBy { it.targetWidth * it.targetHeight }
        .joinToString(separator = "/") { it.label }
}

private fun List<StillCaptureOutputSize>.stillCaptureOutputSizeSummary(): String {
    return this.take(4).joinToString(separator = "/") { it.label }
}

private fun extractLiveSaveFormat(pipelineNotes: List<String>): LiveSaveFormat {
    val line = pipelineNotes.firstOrNull { it.startsWith("live-export:format=") }
    val key = line?.substringAfter("=")
    return LiveSaveFormat.fromStorageKey(key) ?: LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG
}
