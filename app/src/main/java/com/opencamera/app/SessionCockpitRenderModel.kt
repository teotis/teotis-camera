package com.opencamera.app

import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.quickLabel
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.device.normalizedZoomRatioValue
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.SavedMediaType
import com.opencamera.core.mode.modeDirectoryDeclaration
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.PhotoLowLightPromptStatus
import com.opencamera.app.i18n.AppTextResolver
import kotlin.math.roundToInt

internal data class ZoomCapsuleRenderModel(
    val label: String,
    val ratio: Float,
    val isActive: Boolean
)

internal data class SessionControlsRenderModel(
    val lensFacingButtonLabel: String,
    val lensFacingEnabled: Boolean,
    val zoomCapsules: List<ZoomCapsuleRenderModel>,
    val isZoomCapsuleRowVisible: Boolean
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

internal data class QuickPanelRowRenderModel(
    val title: String,
    val value: String,
    val isEnabled: Boolean,
    val disabledReason: String? = null
)

internal data class QuickBrightnessRenderModel(
    val title: String,
    val value: String,
    val canDecrease: Boolean,
    val canIncrease: Boolean,
    val canReset: Boolean,
    val isVisible: Boolean,
    val disabledReason: String?
)

internal data class LowLightNightPromptRenderModel(
    val label: String,
    val contentDescription: String,
    val isVisible: Boolean,
    val isEnabled: Boolean
)

internal data class QuickPanelSheetRenderModel(
    val gridRow: QuickPanelRowRenderModel,
    val qualityRow: QuickPanelRowRenderModel,
    val resolutionRow: QuickPanelRowRenderModel,
    val brightnessRow: QuickBrightnessRenderModel,
    val frameRatioRow: QuickPanelRowRenderModel,
    val frameRatioOptions: List<FrameRatioOptionRenderModel>,
    val frameRatioEnabled: Boolean,
    val frameRatioDisabledReason: String?,
    val liveRow: QuickPanelRowRenderModel,
    val timerRow: QuickPanelRowRenderModel
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
    if (!state.permissionState.cameraGranted) return text.disabledPermission()
    if (state.previewStatus == PreviewStatus.RECOVERING) return text.disabledPreviewRecovering()
    if (state.countdownRemainingSeconds != null) return text.disabledCountdown()
    if (state.activeShot != null && state.recordingStatus == RecordingStatus.REQUESTING) return text.disabledPreparingRecording()
    if (state.recordingStatus == RecordingStatus.RECORDING) return text.disabledRecording()
    if (state.recordingStatus == RecordingStatus.STOPPING) return text.disabledStoppingRecording()
    if (state.captureStatus == CaptureStatus.SAVING) return text.disabledSavingPhoto()
    return null
}

internal fun shutterVisualState(state: SessionState): ShutterVisualState {
    if (state.previewStatus == PreviewStatus.RECOVERING) return ShutterVisualState.BLOCKED
    if (!state.permissionState.cameraGranted) return ShutterVisualState.BLOCKED
    if (state.countdownRemainingSeconds != null) return ShutterVisualState.COUNTDOWN
    if (state.captureStatus == CaptureStatus.SAVING) return ShutterVisualState.SAVING
    val activeShot = state.activeShot
    if (activeShot != null && activeShot.mediaType == com.opencamera.core.media.MediaType.PHOTO) {
        return ShutterVisualState.SAVING
    }
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
    strings: SessionUiStrings
): SessionControlsRenderModel {
    return SessionControlsRenderModel(
        lensFacingButtonLabel = lensFacingButtonLabel(state, strings),
        lensFacingEnabled = state.activeDeviceCapabilities.availableLensFacings.size > 1,
        zoomCapsules = zoomCapsuleModels(state),
        isZoomCapsuleRowVisible = state.activeDeviceCapabilities.zoomRatioCapability.isSwitchingSupported
    )
}

private val stillModesWithFrameRatio = setOf(
    ModeId.PHOTO,
    ModeId.NIGHT,
    ModeId.PORTRAIT,
    ModeId.HUMANISTIC,
    ModeId.PRO
)

internal fun brightnessRenderModel(state: SessionState, text: AppTextResolver): QuickBrightnessRenderModel {
    val isPhotoMode = state.activeMode == ModeId.PHOTO
    val isPreviewActive = state.previewStatus == PreviewStatus.ACTIVE
    val isBusy = state.activeShot != null || state.countdownRemainingSeconds != null
    val isVisible = isPhotoMode
    val range = state.activeDeviceCapabilities.previewBrightnessRange
    val isUnsupported = range == com.opencamera.core.device.PreviewBrightnessRange.UNSUPPORTED
    val steps = state.presentation.previewBrightnessSteps
    val canInteract = isPhotoMode && isPreviewActive && !isBusy && !isUnsupported

    val valueLabel = if (isUnsupported) {
        text.quickBrightnessNa()
    } else {
        brightnessValueLabel(steps)
    }

    val disabledReason = when {
        !isPhotoMode -> null
        isUnsupported -> text.disabledBrightnessUnsupported()
        isBusy -> text.disabledBrightnessActiveShot()
        else -> null
    }

    return QuickBrightnessRenderModel(
        title = text.quickBrightness(),
        value = valueLabel,
        canDecrease = canInteract && steps > range.minSteps,
        canIncrease = canInteract && steps < range.maxSteps,
        canReset = canInteract && steps != 0,
        isVisible = isVisible,
        disabledReason = disabledReason
    )
}

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
        isDegraded -> text.buttonLowLightNightPromptDegraded()
        isEnabled -> text.buttonLowLightNightPromptEnabled()
        else -> text.buttonLowLightNightPromptDisabled()
    }
    return LowLightNightPromptRenderModel(
        label = label,
        contentDescription = text.lowLightNightAssistContentDescription(),
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
        !isSupportedMode -> text.disabledFrameRatioUnsupportedMode()
        state.activeShot != null -> text.disabledFrameRatioActiveShot()
        state.countdownRemainingSeconds != null -> text.disabledFrameRatioCountdown()
        else -> null
    }
    return FrameRatioControlRenderModel(
        title = text.frameRatioTitle(),
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

private fun videoQualityQuickLabel(state: SessionState): String {
    val requested = state.activeDeviceGraph.recording.requestedVideoSpec
    val applied = state.activeDeviceGraph.recording.videoSpec
    val appliedLabel = "${applied.resolution.quickLabel}${applied.frameRate.fps}"
    return if (requested.resolution == applied.resolution && requested.frameRate == applied.frameRate) {
        appliedLabel
    } else {
        "$appliedLabel*"
    }
}

internal fun quickPanelSheetRenderModel(
    state: SessionState,
    text: AppTextResolver,
    strings: SessionUiStrings
): QuickPanelSheetRenderModel {
    val settingsPage = sessionSettingsPageRenderModel(state, text)
    val grid = settingsPage.commonSection.gridMode
    val live = settingsPage.photoSection.livePhoto
    val timer = settingsPage.photoSection.countdown
    val frameControl = frameRatioControlRenderModel(state, text)
    val brightnessControl = brightnessRenderModel(state, text)

    val stillTemplate = state.activeDeviceGraph.template == CaptureTemplate.STILL_CAPTURE
    val videoTemplate = state.activeDeviceGraph.template == CaptureTemplate.VIDEO_RECORDING
    val stillBusy = state.activeShot != null || state.countdownRemainingSeconds != null
    val stillQualityEnabled = stillTemplate && !stillBusy && state.activeDeviceCapabilities.supportsStillCapture
    val videoQualityEnabled = videoTemplate && state.recordingStatus == RecordingStatus.IDLE
    val qualityEnabled = stillQualityEnabled || videoQualityEnabled
    val qualityValue = if (videoTemplate) {
        videoQualityQuickLabel(state)
    } else {
        stillQualityQuickLabel(state, strings)
    }
    val resolutionEnabled = stillQualityEnabled && isStillResolutionToggleEnabled(state)

    return QuickPanelSheetRenderModel(
        gridRow = QuickPanelRowRenderModel(
            title = text.quickGrid(),
            value = grid.value,
            isEnabled = grid.isInteractive
        ),
        qualityRow = QuickPanelRowRenderModel(
            title = text.quickQuality(),
            value = qualityValue,
            isEnabled = qualityEnabled
        ),
        resolutionRow = QuickPanelRowRenderModel(
            title = text.quickResolution(),
            value = stillResolutionQuickLabel(state, strings),
            isEnabled = resolutionEnabled
        ),
        brightnessRow = brightnessControl,
        frameRatioRow = QuickPanelRowRenderModel(
            title = text.frameRatioTitle(),
            value = frameControl.currentLabel,
            isEnabled = frameControl.isEnabled,
            disabledReason = frameControl.disabledReason
        ),
        frameRatioOptions = frameControl.options,
        frameRatioEnabled = frameControl.isEnabled,
        frameRatioDisabledReason = frameControl.disabledReason,
        liveRow = QuickPanelRowRenderModel(
            title = text.quickLive(),
            value = live.value,
            isEnabled = live.isInteractive
        ),
        timerRow = QuickPanelRowRenderModel(
            title = text.quickTimer(),
            value = timer.value,
            isEnabled = timer.isInteractive
        )
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
            "Still quality: ${state.activeDeviceGraph.stillCapture.qualityPreference.label}"
        )
        appendLine(
            "Still resolution: ${state.activeDeviceGraph.stillCapture.resolutionPreset.label} " +
                "(available: ${
                    state.activeDeviceCapabilities.availableStillCaptureResolutionPresets
                        .stillResolutionPresetSummary()
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
    ModeId.VIDEO,
    ModeId.DOCUMENT
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
    val statusText = buildString {
        append(state.previewStatus.name.lowercase().replaceFirstChar(Char::titlecase))
        if (state.captureStatus != CaptureStatus.IDLE) {
            append(" · ${state.captureStatus.name.lowercase().replaceFirstChar(Char::titlecase)}")
        }
        state.countdownRemainingSeconds?.let { append(" · ${it}s") }
        when (state.recordingStatus) {
            RecordingStatus.REQUESTING -> append(" · ${text.statusRecordingStarting()}")
            RecordingStatus.RECORDING -> append(" · ${text.statusRecordingActive()}")
            RecordingStatus.STOPPING -> append(" · ${text.statusRecordingSaving()}")
            RecordingStatus.IDLE -> Unit
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
    strings: SessionUiStrings
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
                appendLiveAssetLine(
                    label = "Still",
                    path = livePhotoBundle.stillPath,
                    renderUri = livePhotoBundle.thumbnailHandle.contentUri
                )
                append('\n')
                appendLiveAssetLine(
                    label = "Motion",
                    path = livePhotoBundle.motionPath,
                    renderUri = livePhotoBundle.motionHandle.contentUri
                )
                append('\n')
                appendLiveAssetLine(
                    label = "Sidecar",
                    path = livePhotoBundle.sidecarPath,
                    renderUri = livePhotoBundle.sidecarHandle.contentUri
                )
                append('\n')
                appendLiveAssetLine(
                    label = "Thumbnail",
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

private fun Set<StillCaptureResolutionPreset>.stillResolutionPresetSummary(): String {
    return this.sortedBy { it.targetWidth * it.targetHeight }
        .joinToString(separator = "/") { it.label }
}

private fun List<StillCaptureOutputSize>.stillCaptureOutputSizeSummary(): String {
    return this.take(4).joinToString(separator = "/") { it.label }
}

private fun stillQualityQuickLabel(state: SessionState, strings: SessionUiStrings): String {
    return when (state.activeDeviceGraph.stillCapture.qualityPreference) {
        StillCaptureQualityPreference.LATENCY -> strings.buttonStillFast
        StillCaptureQualityPreference.QUALITY -> strings.buttonStillMax
    }
}

private fun stillResolutionQuickLabel(state: SessionState, strings: SessionUiStrings): String {
    val native = selectedNativeStillCaptureOutputSizeOrNull(state)
    return native?.quickMegapixelLabel()
        ?: state.activeDeviceGraph.stillCapture.resolutionPreset.label
}

private fun StillCaptureOutputSize.quickMegapixelLabel(): String {
    val megapixels = (pixelCount / 1_000_000.0).roundToInt()
    return "${megapixels}MP"
}
