package com.opencamera.app

import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.ManualControlCapabilityMatrix
import com.opencamera.core.device.ManualControlSupport
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.device.normalizedZoomRatioValue
import com.opencamera.core.device.resolveVideoSpec
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.modeDirectoryDeclaration
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SavedMediaType
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.SessionTraceEvent
import com.opencamera.core.session.buildSessionDebugDump
import com.opencamera.core.effect.PreviewEffectAdapter
import com.opencamera.core.effect.PreviewEffectRenderModel
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.FeatureCatalogAction
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.ManualCaptureParams
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.PortraitBeautyPreset
import com.opencamera.core.settings.PortraitBeautyStrength
import com.opencamera.core.settings.PortraitBokehEffect
import com.opencamera.core.settings.PortraitProfile
import com.opencamera.core.settings.compactSummary
import com.opencamera.core.settings.filterProfilesFor
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.WatermarkTemplate
import com.opencamera.core.settings.WatermarkFrameBackground
import com.opencamera.core.settings.WatermarkTextOpacity
import com.opencamera.core.settings.WatermarkTextPlacement
import com.opencamera.core.settings.WatermarkTextScale
import com.opencamera.core.settings.watermarkStyleFor
import com.opencamera.core.settings.VideoResolution
import java.util.Locale
import com.opencamera.app.i18n.AppTextResolver

internal data class SessionUiStrings(
    val buttonSwitchToFront: String,
    val buttonSwitchToBack: String,
    val buttonSingleLens: String,
    val buttonZoomPrefix: String,
    val buttonZoomUnavailable: String,
    val buttonStillFast: String,
    val buttonStillMax: String,
    val buttonStillQualityUnavailable: String,
    val buttonStill12Mp: String,
    val buttonStill8Mp: String,
    val buttonStill2Mp: String,
    val buttonStillResolutionUnavailable: String,
    val outputErrorPrefix: String,
    val outputVideoPrefix: String,
    val outputLivePrefix: String,
    val outputSavedPrefix: String,
    val outputPreviewPrefix: String,
    val outputWaiting: String
)

internal data class ZoomCapsuleRenderModel(
    val label: String,
    val ratio: Float,
    val isActive: Boolean
)

internal data class SessionControlsRenderModel(
    val lensFacingButtonLabel: String,
    val zoomButtonLabel: String,
    val lensFacingEnabled: Boolean,
    val zoomEnabled: Boolean,
    val zoomCapsules: List<ZoomCapsuleRenderModel>,
    val isZoomCapsuleRowVisible: Boolean
)

internal data class PreviewOverlayRenderModel(
    val gridMode: CompositionGridMode,
    val isGridVisible: Boolean,
    val countdownLabel: String?,
    val isCountdownVisible: Boolean,
    val effectModel: PreviewEffectRenderModel? = null
) {
    val isVisible: Boolean
        get() = isGridVisible || isCountdownVisible || effectModel != null
}

internal data class SessionSettingsRenderModel(
    val commonSummary: String,
    val photoSummary: String,
    val videoSummary: String,
    val catalogSummary: String,
    val manualDraftSummary: String
)

internal enum class SettingsControlAvailability(
    val label: String
) {
    SUPPORTED("Supported"),
    DEGRADED("Degraded"),
    UNSUPPORTED("Unsupported")
}

internal data class SettingsControlRenderModel(
    val label: String,
    val value: String,
    val availability: SettingsControlAvailability = SettingsControlAvailability.SUPPORTED,
    val supportLabel: String? = null,
    val nextAction: PersistedSettingsAction? = null
) {
    val isInteractive: Boolean
        get() = availability != SettingsControlAvailability.UNSUPPORTED && nextAction != null

    val buttonLabel: String
        get() = buildString {
            append(label)
            append('\n')
            append(value)
            append('\n')
            append(availability.label)
            supportLabel?.let {
                append(" • ")
                append(it)
            }
        }
}

internal data class CommonSettingsSectionRenderModel(
    val summary: String,
    val gridMode: SettingsControlRenderModel,
    val shutterSound: SettingsControlRenderModel,
    val selfieMirror: SettingsControlRenderModel
)

internal data class PhotoSettingsSectionRenderModel(
    val summary: String,
    val defaultFilter: SettingsControlRenderModel,
    val portraitLab: SettingsControlRenderModel,
    val watermarkTemplate: SettingsControlRenderModel,
    val livePhoto: SettingsControlRenderModel,
    val countdown: SettingsControlRenderModel
)

internal data class VideoSettingsSectionRenderModel(
    val summary: String,
    val resolution: SettingsControlRenderModel,
    val frameRate: SettingsControlRenderModel,
    val dynamicFps: SettingsControlRenderModel,
    val audioProfile: SettingsControlRenderModel,
    val defaultFilter: SettingsControlRenderModel
)

internal data class SessionSettingsPageRenderModel(
    val headline: String,
    val supportingText: String,
    val heroSummary: String,
    val editingEnabled: Boolean,
    val editingHint: String,
    val commonSection: CommonSettingsSectionRenderModel,
    val photoSection: PhotoSettingsSectionRenderModel,
    val videoSection: VideoSettingsSectionRenderModel,
    val catalogFooter: String
)

internal data class FeatureCatalogControlRenderModel(
    val label: String,
    val value: String,
    val availability: SettingsControlAvailability = SettingsControlAvailability.SUPPORTED,
    val supportLabel: String? = null,
    val nextAction: FeatureCatalogAction? = null
) {
    val isInteractive: Boolean
        get() = nextAction != null

    val buttonLabel: String
        get() = buildString {
            append(label)
            append('\n')
            append(value)
            append('\n')
            append(availability.label)
            supportLabel?.let {
                append(" • ")
                append(it)
            }
        }
}

internal data class RuntimeProControlsRenderModel(
    val isVisible: Boolean,
    val headline: String,
    val supportingText: String,
    val summary: String,
    val rawControl: FeatureCatalogControlRenderModel,
    val isoControl: FeatureCatalogControlRenderModel,
    val shutterControl: FeatureCatalogControlRenderModel,
    val exposureControl: FeatureCatalogControlRenderModel,
    val focusControl: FeatureCatalogControlRenderModel,
    val apertureControl: FeatureCatalogControlRenderModel,
    val whiteBalanceControl: FeatureCatalogControlRenderModel
)

internal data class WatermarkLabTemplateItemRenderModel(
    val templateId: String,
    val title: String,
    val supportingText: String,
    val isSelected: Boolean,
    val useAction: PersistedSettingsAction?,
    val editButtonLabel: String? = null
)

internal data class WatermarkLabSelectorRenderModel(
    val headline: String,
    val supportingText: String,
    val heroSummary: String,
    val editingEnabled: Boolean,
    val editingHint: String,
    val items: List<WatermarkLabTemplateItemRenderModel>,
    val footer: String
)

internal data class WatermarkLabDetailRenderModel(
    val headline: String,
    val supportingText: String,
    val heroSummary: String,
    val templateId: String,
    val editingEnabled: Boolean,
    val editingHint: String,
    val placementControl: SettingsControlRenderModel,
    val textScaleControl: SettingsControlRenderModel,
    val textOpacityControl: SettingsControlRenderModel,
    val frameBackgroundControl: SettingsControlRenderModel?,
    val footer: String
)

internal data class PortraitLabPageRenderModel(
    val headline: String,
    val supportingText: String,
    val heroSummary: String,
    val editingEnabled: Boolean,
    val editingHint: String,
    val profileControl: SettingsControlRenderModel,
    val beautyPresetControl: SettingsControlRenderModel,
    val beautyStrengthControl: SettingsControlRenderModel,
    val bokehEffectControl: SettingsControlRenderModel,
    val footer: String
)

internal enum class FilterLabFamily(
    val label: String
) {
    PHOTO("Photo"),
    HUMANISTIC("Humanistic"),
    PORTRAIT("Portrait"),
    VIDEO("Video")
}

internal data class FilterLabTabRenderModel(
    val family: FilterLabFamily,
    val label: String,
    val isSelected: Boolean
)

internal enum class FilterAdjustmentMode {
    LIGHT,
    ADVANCED
}

internal enum class FilterAdvancedControl(
    val label: String
) {
    EXPOSURE("Exposure"),
    SOFT_GLOW("Soft Glow"),
    HALO("Halo"),
    GRAIN("Grain"),
    SHARPNESS("Sharpness"),
    VIGNETTE("Vignette"),
    HIGHLIGHTS("Highlights"),
    SHADOWS("Shadows"),
    WARM_BOOST("Warm Boost"),
    COOL_BOOST("Cool Boost"),
    TEMPERATURE_SHIFT("Temp Shift"),
    TINT_SHIFT("Tint Shift")
}

internal data class FilterLabFilterItemRenderModel(
    val filterProfileId: String,
    val title: String,
    val supportingText: String,
    val isSelected: Boolean,
    val nextAction: PersistedSettingsAction?,
    val adjustButtonLabel: String? = null
)

internal data class FilterLabAdjustRenderModel(
    val buttonLabel: String,
    val family: FilterLabFamily,
    val sourceProfileId: String?,
    val isEnabled: Boolean,
    val willCreateCustomCopy: Boolean
)

internal data class FilterLightPaletteRenderModel(
    val summary: String,
    val supportingText: String
)

internal data class FilterAdvancedControlRenderModel(
    val control: FilterAdvancedControl,
    val buttonLabel: String
)

internal data class FilterAdjustmentPanelRenderModel(
    val isVisible: Boolean,
    val mode: FilterAdjustmentMode,
    val selectedProfileId: String?,
    val selectedProfileLabel: String,
    val renderSpec: FilterRenderSpec,
    val modeToggleLabel: String,
    val lightPalette: FilterLightPaletteRenderModel,
    val advancedControls: List<FilterAdvancedControlRenderModel>
)

internal data class FilterLabPageRenderModel(
    val headline: String,
    val supportingText: String,
    val heroSummary: String,
    val currentFilterSummary: String,
    val rosterText: String,
    val editingEnabled: Boolean,
    val editingHint: String,
    val photoTab: FilterLabTabRenderModel,
    val humanisticTab: FilterLabTabRenderModel,
    val portraitTab: FilterLabTabRenderModel,
    val videoTab: FilterLabTabRenderModel,
    val filterItems: List<FilterLabFilterItemRenderModel>,
    val adjustControl: FilterLabAdjustRenderModel,
    val adjustmentPanel: FilterAdjustmentPanelRenderModel,
    val cycleControl: SettingsControlRenderModel,
    val saveCustomControl: FilterLabSaveCustomRenderModel,
    val footer: String
)

internal data class FilterLabSaveCustomRenderModel(
    val buttonLabel: String,
    val family: FilterLabFamily,
    val sourceProfileId: String?,
    val isEnabled: Boolean
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

internal fun sessionControlsRenderModel(
    state: SessionState,
    strings: SessionUiStrings
): SessionControlsRenderModel {
    return SessionControlsRenderModel(
        lensFacingButtonLabel = lensFacingButtonLabel(state, strings),
        zoomButtonLabel = zoomButtonLabel(state, strings),
        lensFacingEnabled = state.activeDeviceCapabilities.availableLensFacings.size > 1,
        zoomEnabled = state.activeDeviceCapabilities.zoomRatioCapability.isSwitchingSupported,
        zoomCapsules = zoomCapsuleModels(state),
        isZoomCapsuleRowVisible = state.activeDeviceCapabilities.zoomRatioCapability.isSwitchingSupported
    )
}

internal fun previewOverlayRenderModel(
    state: SessionState,
    effectAdapter: PreviewEffectAdapter? = null
): PreviewOverlayRenderModel {
    val gridMode = state.settings.persisted.common.gridMode
    val previewSupportsOverlay = state.permissionState.cameraGranted &&
        state.previewHostAvailable &&
        state.previewStatus in setOf(
            PreviewStatus.STARTING,
            PreviewStatus.ACTIVE,
            PreviewStatus.RECOVERING
        )
    val countdownLabel = state.countdownRemainingSeconds?.let { "${it}s" }
    val effectModel = effectAdapter?.adapt(state.activeEffectSpec)
    return PreviewOverlayRenderModel(
        gridMode = gridMode,
        isGridVisible = previewSupportsOverlay && gridMode != CompositionGridMode.OFF,
        countdownLabel = countdownLabel,
        isCountdownVisible = state.captureStatus == CaptureStatus.REQUESTED &&
            countdownLabel != null &&
            state.permissionState.cameraGranted,
        effectModel = effectModel
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
        items = state.availableModes.map { modeId ->
            val declaration = modeId.modeDirectoryDeclaration(
                deviceCapabilities = state.activeDeviceCapabilities,
                settingsSnapshot = state.settings
            )
            ModeDirectoryItemRenderModel(
                modeId = modeId,
                displayName = declaration.catalogProfile.displayName,
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
        items = state.availableModes.map { modeId ->
            val declaration = modeId.modeDirectoryDeclaration(
                deviceCapabilities = state.activeDeviceCapabilities,
                settingsSnapshot = state.settings
            )
            ModeTrackItemRenderModel(
                modeId = modeId,
                trackLabel = declaration.catalogProfile.buttonLabel,
                isActive = modeId == state.activeMode,
                isAvailable = true
            )
        }
    )
}

internal fun primaryStatusRenderModel(
    state: SessionState,
    text: AppTextResolver
): PrimaryStatusRenderModel {
    val modeLabel = state.modeSnapshot.uiSpec.title
    val statusText = buildString {
        append(state.previewStatus.name.lowercase().replaceFirstChar(Char::titlecase))
        if (state.captureStatus != CaptureStatus.IDLE) {
            append(" · ${state.captureStatus.name.lowercase().replaceFirstChar(Char::titlecase)}")
        }
        state.countdownRemainingSeconds?.let { append(" · ${it}s") }
        when (state.recordingStatus) {
            RecordingStatus.REQUESTING -> append(" · Starting...")
            RecordingStatus.RECORDING -> append(" · Recording")
            RecordingStatus.STOPPING -> append(" · Saving...")
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

internal fun sessionDiagnosticsText(
    state: SessionState,
    traceEvents: List<SessionTraceEvent>
): String {
    val debugDump = buildSessionDebugDump(state, traceEvents)
    val perf = debugDump.perfSnapshot
    val recovery = debugDump.recoveryTrace
    return buildString {
        appendLine(
            "DebugDump: ${debugDump.lifecycle} | ${debugDump.activeMode.name} | " +
                "preview=${debugDump.previewStatus} | capture=${debugDump.captureStatus} | " +
                "recording=${debugDump.recordingStatus}"
        )
        appendLine(
            "PerfSnapshot: last=${perf.lastFirstFrameLatencyMillis ?: "--"} ms, " +
                "best=${perf.bestFirstFrameLatencyMillis ?: "--"} ms, " +
                "worst=${perf.worstFirstFrameLatencyMillis ?: "--"} ms, " +
                "binds=${perf.bindCount}, recoveries=${perf.recoveryCount}, " +
                "budget=${perf.firstFrameBudget.status.label} " +
                "(${perf.firstFrameBudget.startCategory.label}, " +
                "warn=${perf.firstFrameBudget.warnThresholdMillis} ms, " +
                "fail=${perf.firstFrameBudget.failThresholdMillis} ms)"
        )
        appendLine(
            "RecoveryTrace: ${if (recovery.isRecoveryActive) "active" else "idle"}, " +
                "last=${recovery.lastRecoveryReason ?: "--"}, " +
                "recoveredFrame=${recovery.recoveredFirstFrameLatencyMillis ?: "--"} ms, " +
                "failure=${recovery.lastFailureReason ?: "--"}"
        )
        appendLine("Action: ${debugDump.lastAction}")
        if (debugDump.lastError != null) {
            appendLine("Error: ${debugDump.lastError}")
        }
        append(
            debugDump.recentEvents.joinToString(separator = "\n") { event ->
                "${event.sequence}. ${event.name} -> ${event.detail}"
            }
        )
    }
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

internal fun sessionSettingsRenderModel(
    state: SessionState,
    text: AppTextResolver
): SessionSettingsRenderModel {
    val settings = state.settings.persisted
    val catalog = state.settings.catalog
    val photoFilterLabel = catalog.filterProfileOrNull(settings.photo.defaultFilterProfileId)?.label
        ?: settings.photo.defaultFilterProfileId
    val watermarkTemplateLabel = catalog.watermarkTemplateOrNull(
        settings.photo.defaultWatermarkTemplateId
    )?.label ?: settings.photo.defaultWatermarkTemplateId
    val videoFilterLabel = catalog.filterProfileOrNull(settings.video.defaultFilterProfileId)?.label
        ?: settings.video.defaultFilterProfileId
    val videoSpec = settings.video.defaultVideoSpec
    return SessionSettingsRenderModel(
        commonSummary = buildString {
            append("Grid ${settings.common.gridMode.label}")
            append(" | Shutter sound ")
            append(if (settings.common.shutterSoundEnabled) "On" else "Off")
            append(" | Selfie mirror ")
            append(if (settings.common.selfieMirrorEnabled) "On" else "Off")
        },
        photoSummary = buildString {
            append("Filter ")
            append(photoFilterLabel)
            append(" | Portrait ")
            append(settings.photo.portraitProfile.label)
            append(" | Watermark ")
            append(watermarkTemplateLabel)
            append(" | Live ")
            append(if (settings.photo.livePhotoEnabledByDefault) "On" else "Off")
            append(" | Timer ")
            append(settings.photo.countdownDuration.label)
        },
        videoSummary = buildString {
            append(videoSpec.summaryLabel)
            append(" | Mic ")
            append(videoSpec.audioProfile.label)
            append(" | ")
            append(
                if (videoSpec.dynamicFpsPolicy == DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS) {
                    "Low-light auto 24fps"
                } else {
                    "Locked fps"
                }
            )
            append(" | Filter ")
            append(videoFilterLabel)
        },
        catalogSummary = buildString {
            append(catalog.filterProfiles.size)
            append(" filters")
            append(" | ")
            append(catalog.watermarkTemplates.size)
            append(" watermark templates")
            append(" | Live ")
            append(catalog.liveMediaBundleDraft.motionDurationMillis)
            append(" ms bundle")
        },
        manualDraftSummary = catalog.manualCaptureDraft.compactSummary()
    )
}

internal fun sessionSettingsPageRenderModel(
    state: SessionState,
    text: AppTextResolver
): SessionSettingsPageRenderModel {
    val settings = state.settings.persisted
    val catalog = state.settings.catalog
    val renderModel = sessionSettingsRenderModel(state, text)
    val editingEnabled = state.activeShot == null && state.countdownRemainingSeconds == null
    val photoFilters = catalog.photoSettingsFilterProfiles()
    val videoFilters = catalog.videoSettingsFilterProfiles()
    val videoConstraints = state.activeDeviceCapabilities.videoSpecConstraints
        .takeIf { it.resolutions.isNotEmpty() }
        ?: catalog.videoSpecConstraints
    val watermarkTemplates = catalog.watermarkTemplates
    val supportsStillCapture = state.activeDeviceCapabilities.supportsStillCapture
    val supportsVideoRecording = state.activeDeviceCapabilities.supportsVideoRecording
    val supportsAudioRecording = state.activeDeviceCapabilities.supportsAudioRecording
    val selectedVideoSpec = settings.video.defaultVideoSpec
    val resolvedVideoSelection = state.activeDeviceCapabilities
        .copy(videoSpecConstraints = videoConstraints)
        .resolveVideoSpec(selectedVideoSpec)
    val activeVideoSpec = resolvedVideoSelection.applied
    return SessionSettingsPageRenderModel(
        headline = "Lens Lab",
        supportingText = "Quick defaults with explicit supported, degraded, and staged capability hints.",
        heroSummary = "${renderModel.commonSummary} • ${renderModel.photoSummary}",
        editingEnabled = editingEnabled,
        editingHint = if (editingEnabled) {
            "Changes save instantly and refresh the active mode defaults."
        } else {
            "Finish the current capture before changing saved defaults."
        },
        commonSection = CommonSettingsSectionRenderModel(
            summary = renderModel.commonSummary,
            gridMode = SettingsControlRenderModel(
                label = "Composition grid",
                value = settings.common.gridMode.label,
                supportLabel = "Cycle ${CompositionGridMode.entries.size} layouts",
                nextAction = PersistedSettingsAction.UpdateGridMode(
                    nextListValue(settings.common.gridMode, CompositionGridMode.entries.toList())
                )
            ),
            shutterSound = SettingsControlRenderModel(
                label = "Shutter tone",
                value = onOffLabel(settings.common.shutterSoundEnabled),
                nextAction = PersistedSettingsAction.UpdateShutterSoundEnabled(
                    !settings.common.shutterSoundEnabled
                )
            ),
            selfieMirror = SettingsControlRenderModel(
                label = "Selfie mirror",
                value = onOffLabel(settings.common.selfieMirrorEnabled),
                nextAction = PersistedSettingsAction.UpdateSelfieMirrorEnabled(
                    !settings.common.selfieMirrorEnabled
                )
            )
        ),
        photoSection = PhotoSettingsSectionRenderModel(
            summary = renderModel.photoSummary,
            defaultFilter = SettingsControlRenderModel(
                label = "Default photo filter",
                value = photoFilterLabel(settings.photo.defaultFilterProfileId, photoFilters),
                availability = if (supportsStillCapture && photoFilters.isNotEmpty()) {
                    SettingsControlAvailability.SUPPORTED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                },
                supportLabel = if (photoFilters.isNotEmpty()) {
                    "${photoFilters.size} curated looks"
                } else {
                    "No compatible filters"
                },
                nextAction = if (supportsStillCapture) {
                    nextListValueOrNull(
                        current = settings.photo.defaultFilterProfileId,
                        values = photoFilters.map(FilterProfile::id)
                    )?.let(PersistedSettingsAction::UpdatePhotoFilter)
                } else {
                    null
                }
            ),
            portraitLab = SettingsControlRenderModel(
                label = "Portrait Lab",
                value = buildString {
                    append(settings.photo.portraitProfile.label)
                    append(" / ")
                    append(settings.photo.portraitBeautyPreset.label)
                    append(" / ")
                    append(settings.photo.portraitBokehEffect.label)
                },
                availability = if (supportsStillCapture) {
                    SettingsControlAvailability.DEGRADED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                },
                supportLabel = if (supportsStillCapture) {
                    "Open profile + beauty + bokeh tuning"
                } else {
                    "Still capture unavailable on this device"
                },
                nextAction = null
            ),
            watermarkTemplate = SettingsControlRenderModel(
                label = "Watermark Lab",
                value = watermarkTemplateLabel(
                    settings.photo.defaultWatermarkTemplateId,
                    watermarkTemplates
                ),
                availability = if (supportsStillCapture && watermarkTemplates.isNotEmpty()) {
                    SettingsControlAvailability.DEGRADED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                },
                supportLabel = if (supportsStillCapture && watermarkTemplates.isNotEmpty()) {
                    "Open selector + per-template tuning; ${watermarkTemplates.size} templates"
                } else if (!supportsStillCapture) {
                    "Still capture unavailable on this device"
                } else {
                    "No watermark templates available"
                },
                nextAction = null
            ),
            livePhoto = SettingsControlRenderModel(
                label = "Live photo default",
                value = onOffLabel(settings.photo.livePhotoEnabledByDefault),
                availability = if (supportsStillCapture) {
                    SettingsControlAvailability.DEGRADED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                },
                supportLabel = if (supportsStillCapture) {
                    "Saved default only; ${catalog.liveMediaBundleDraft.motionDurationMillis} ms bundle | dynamic watermark ${catalog.liveMediaBundleDraft.watermarkMotionBehavior.label}"
                } else {
                    "Still capture unavailable on this device"
                },
                nextAction = if (supportsStillCapture) {
                    PersistedSettingsAction.UpdateLivePhotoDefault(
                        !settings.photo.livePhotoEnabledByDefault
                    )
                } else {
                    null
                }
            ),
            countdown = SettingsControlRenderModel(
                label = "Countdown",
                value = settings.photo.countdownDuration.label,
                availability = if (supportsStillCapture) {
                    SettingsControlAvailability.SUPPORTED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                },
                supportLabel = if (supportsStillCapture) {
                    "${catalog.countdownOptions.size} presets"
                } else {
                    "Still capture unavailable on this device"
                },
                nextAction = if (supportsStillCapture) {
                    nextListValueOrNull(
                        current = settings.photo.countdownDuration,
                        values = catalog.countdownOptions.sortedBy(CountdownDuration::ordinal)
                    )?.let(PersistedSettingsAction::UpdateCountdownDuration)
                } else {
                    null
                }
            )
        ),
        videoSection = VideoSettingsSectionRenderModel(
            summary = renderModel.videoSummary,
            resolution = SettingsControlRenderModel(
                label = "Resolution",
                value = selectedVideoSpec.resolution.label,
                availability = when {
                    !supportsVideoRecording -> SettingsControlAvailability.UNSUPPORTED
                    resolvedVideoSelection.resolutionDegraded ->
                        SettingsControlAvailability.DEGRADED
                    else -> SettingsControlAvailability.SUPPORTED
                },
                supportLabel = when {
                    !supportsVideoRecording -> "Video recording unavailable on this device"
                    resolvedVideoSelection.resolutionDegraded ->
                        "Saved as ${selectedVideoSpec.resolution.label}, active graph uses ${activeVideoSpec.resolution.label}"
                    else -> supportCountLabel(videoConstraints.resolutions.size)
                },
                nextAction = if (supportsVideoRecording) {
                    nextListValueOrNull(
                        current = selectedVideoSpec.resolution,
                        values = videoConstraints.resolutions.sortedBy(VideoResolution::ordinal)
                    )?.let { nextResolution ->
                        PersistedSettingsAction.UpdateDefaultVideoSpec(
                            selectedVideoSpec.copy(resolution = nextResolution)
                        )
                    }
                } else {
                    null
                }
            ),
            frameRate = SettingsControlRenderModel(
                label = "Frame rate",
                value = selectedVideoSpec.frameRate.label,
                availability = when {
                    !supportsVideoRecording -> SettingsControlAvailability.UNSUPPORTED
                    resolvedVideoSelection.frameRateDegraded ->
                        SettingsControlAvailability.DEGRADED
                    else -> SettingsControlAvailability.SUPPORTED
                },
                supportLabel = when {
                    !supportsVideoRecording -> "Video recording unavailable on this device"
                    resolvedVideoSelection.frameRateDegraded ->
                        "Saved as ${selectedVideoSpec.frameRate.label}, active graph uses ${activeVideoSpec.frameRate.label}"
                    else -> supportCountLabel(videoConstraints.frameRatesFor(activeVideoSpec.resolution).size)
                },
                nextAction = if (supportsVideoRecording) {
                    nextListValueOrNull(
                        current = selectedVideoSpec.frameRate,
                        values = videoConstraints
                            .frameRatesFor(selectedVideoSpec.resolution)
                            .ifEmpty { videoConstraints.frameRates }
                            .sortedBy(VideoFrameRate::ordinal)
                    )?.let { nextFrameRate ->
                        PersistedSettingsAction.UpdateDefaultVideoSpec(
                            selectedVideoSpec.copy(frameRate = nextFrameRate)
                        )
                    }
                } else {
                    null
                }
            ),
            dynamicFps = SettingsControlRenderModel(
                label = "Dynamic fps",
                value = selectedVideoSpec.dynamicFpsPolicy.label,
                availability = when {
                    !supportsVideoRecording -> SettingsControlAvailability.UNSUPPORTED
                    resolvedVideoSelection.dynamicPolicyDegraded ->
                        SettingsControlAvailability.DEGRADED
                    else -> SettingsControlAvailability.SUPPORTED
                },
                supportLabel = when {
                    !supportsVideoRecording -> "Video recording unavailable on this device"
                    resolvedVideoSelection.dynamicPolicyDegraded ->
                        "Saved as ${selectedVideoSpec.dynamicFpsPolicy.label}, active graph uses ${activeVideoSpec.dynamicFpsPolicy.label}"
                    else ->
                        supportCountLabel(videoConstraints.dynamicPolicies.size)
                },
                nextAction = if (supportsVideoRecording) {
                    nextListValueOrNull(
                        current = selectedVideoSpec.dynamicFpsPolicy,
                        values = videoConstraints.dynamicPolicies.sortedBy(
                            DynamicVideoFpsPolicy::ordinal
                        )
                    )?.let { nextPolicy ->
                        PersistedSettingsAction.UpdateDefaultVideoSpec(
                            selectedVideoSpec.copy(dynamicFpsPolicy = nextPolicy)
                        )
                    }
                } else {
                    null
                }
            ),
            audioProfile = SettingsControlRenderModel(
                label = "Audio scene",
                value = selectedVideoSpec.audioProfile.label,
                availability = when {
                    !supportsVideoRecording || !supportsAudioRecording ->
                        SettingsControlAvailability.UNSUPPORTED
                    resolvedVideoSelection.audioProfileDegraded ->
                        SettingsControlAvailability.DEGRADED
                    else -> SettingsControlAvailability.SUPPORTED
                },
                supportLabel = when {
                    !supportsVideoRecording -> "Video recording unavailable on this device"
                    !supportsAudioRecording -> "Microphone capture unavailable on this device"
                    resolvedVideoSelection.audioProfileDegraded ->
                        "Saved as ${selectedVideoSpec.audioProfile.label}, active graph uses ${activeVideoSpec.audioProfile.label}"
                    else ->
                        supportCountLabel(videoConstraints.audioProfiles.size)
                },
                nextAction = if (supportsVideoRecording && supportsAudioRecording) {
                    nextListValueOrNull(
                        current = selectedVideoSpec.audioProfile,
                        values = videoConstraints.audioProfiles.sortedBy(AudioProfile::ordinal)
                    )?.let { nextAudioProfile ->
                        PersistedSettingsAction.UpdateDefaultVideoSpec(
                            selectedVideoSpec.copy(audioProfile = nextAudioProfile)
                        )
                    }
                } else {
                    null
                }
            ),
            defaultFilter = SettingsControlRenderModel(
                label = "Video filter seed",
                value = videoFilterLabel(settings.video.defaultFilterProfileId, videoFilters),
                availability = if (supportsVideoRecording) {
                    SettingsControlAvailability.DEGRADED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                },
                supportLabel = if (supportsVideoRecording) {
                    "Saved filter seed only; ${videoFilters.size} looks staged"
                } else {
                    "Video recording unavailable on this device"
                },
                nextAction = if (supportsVideoRecording) {
                    nextListValueOrNull(
                        current = settings.video.defaultFilterProfileId,
                        values = videoFilters.map(FilterProfile::id)
                    )?.let(PersistedSettingsAction::UpdateVideoFilter)
                } else {
                    null
                }
            )
        ),
        catalogFooter = buildString {
            append(renderModel.catalogSummary)
            append(" | Still watermark templates now flow into metadata and photo rendering.")
            append(" | Pro manual draft ")
            append(renderModel.manualDraftSummary)
            append(".")
            append(" | Manual drafts and Live/video defaults remain staged in the same settings spine.")
        }
    )
}

internal fun runtimeProControlsRenderModel(
    state: SessionState,
    text: AppTextResolver
): RuntimeProControlsRenderModel {
    val isEditableMode = state.activeMode in setOf(
        ModeId.NIGHT,
        ModeId.PORTRAIT,
        ModeId.HUMANISTIC,
        ModeId.PRO
    )
    val isVisible = isEditableMode && (
        state.activeMode == ModeId.PRO ||
            state.modeSnapshot.state.isProVariantActive
    )
    val draft = state.settings.catalog.manualCaptureDraft
    val manualCapabilities = state.activeDeviceCapabilities.resolvedManualControlCapabilities
    val hasAppliedManualControls = state.activeDeviceCapabilities.supportsAppliedManualControls
    val editingEnabled = state.activeShot == null && state.countdownRemainingSeconds == null
    val runtimeSupportLabel = manualSupportSummary(manualCapabilities)
    return RuntimeProControlsRenderModel(
        isVisible = isVisible,
        headline = when (state.activeMode) {
            ModeId.NIGHT -> "Scenery Pro Controls"
            ModeId.PORTRAIT -> "Portrait Pro Controls"
            ModeId.HUMANISTIC -> "Humanistic Pro Controls"
            ModeId.PRO -> "Pro Controls"
            else -> "Pro Controls"
        },
        supportingText = if (hasAppliedManualControls) {
            "Upper-layer manual draft is editable now; each control declares whether it is applied, saved-only, or temporarily unsupported."
        } else {
            "Draft changes are allowed, but this device currently keeps every control in saved-only or temporarily unsupported state."
        },
        summary = buildString {
            append(draft.compactSummary())
            append(" | ")
            append(runtimeSupportLabel)
            if (!editingEnabled) {
                append(" Finish the current capture before editing.")
            }
        },
        rawControl = FeatureCatalogControlRenderModel(
            label = "RAW",
            value = onOffLabel(draft.rawEnabled),
            availability = manualCapabilities.raw.toSettingsAvailability(),
            supportLabel = manualCapabilities.raw.manualSupportLabel(),
            nextAction = FeatureCatalogAction.UpdateManualRawEnabled(!draft.rawEnabled)
                .takeIf { isVisible && editingEnabled }
        ),
        isoControl = FeatureCatalogControlRenderModel(
            label = "ISO",
            value = draft.iso?.toString() ?: "Auto",
            availability = manualCapabilities.iso.toSettingsAvailability(),
            supportLabel = manualCapabilities.iso.manualSupportLabel(),
            nextAction = nextListValueOrNull(draft.iso, MANUAL_ISO_OPTIONS)
                ?.let(FeatureCatalogAction::UpdateManualIso)
                ?.takeIf { isVisible && editingEnabled }
        ),
        shutterControl = FeatureCatalogControlRenderModel(
            label = "Shutter",
            value = draft.shutterSpeedMillis?.let { "${it}ms" } ?: "Auto",
            availability = manualCapabilities.shutter.toSettingsAvailability(),
            supportLabel = manualCapabilities.shutter.manualSupportLabel(),
            nextAction = nextListValueOrNull(
                draft.shutterSpeedMillis,
                MANUAL_SHUTTER_OPTIONS
            )?.let(FeatureCatalogAction::UpdateManualShutterSpeedMillis)
                ?.takeIf { isVisible && editingEnabled }
        ),
        exposureControl = FeatureCatalogControlRenderModel(
            label = "EV",
            value = draft.exposureCompensationSteps?.let(::manualEvLabel) ?: "Auto",
            availability = manualCapabilities.exposureCompensation.toSettingsAvailability(),
            supportLabel = manualCapabilities.exposureCompensation.manualSupportLabel(),
            nextAction = nextListValueOrNull(
                draft.exposureCompensationSteps,
                MANUAL_EXPOSURE_OPTIONS
            )?.let(FeatureCatalogAction::UpdateManualExposureCompensationSteps)
                ?.takeIf { isVisible && editingEnabled }
        ),
        focusControl = FeatureCatalogControlRenderModel(
            label = "Focus",
            value = draft.focusDistanceDiopters?.let { String.format(Locale.US, "%.1fD", it) }
                ?: "Auto",
            availability = manualCapabilities.focusDistance.toSettingsAvailability(),
            supportLabel = manualCapabilities.focusDistance.manualSupportLabel(),
            nextAction = nextListValueOrNull(
                draft.focusDistanceDiopters,
                MANUAL_FOCUS_OPTIONS
            )?.let(FeatureCatalogAction::UpdateManualFocusDistanceDiopters)
                ?.takeIf { isVisible && editingEnabled }
        ),
        apertureControl = FeatureCatalogControlRenderModel(
            label = "Aperture",
            value = draft.apertureFNumber?.let { "f/${manualOneDecimal(it)}" } ?: "Auto",
            availability = manualCapabilities.aperture.toSettingsAvailability(),
            supportLabel = manualCapabilities.aperture.manualSupportLabel(),
            nextAction = nextListValueOrNull(
                draft.apertureFNumber,
                MANUAL_APERTURE_OPTIONS
            )?.let(FeatureCatalogAction::UpdateManualApertureFNumber)
                ?.takeIf { isVisible && editingEnabled }
        ),
        whiteBalanceControl = FeatureCatalogControlRenderModel(
            label = "WB",
            value = draft.whiteBalanceKelvin?.let { "${it}K" } ?: "Auto",
            availability = manualCapabilities.whiteBalance.toSettingsAvailability(),
            supportLabel = manualCapabilities.whiteBalance.manualSupportLabel(),
            nextAction = nextListValueOrNull(
                draft.whiteBalanceKelvin,
                MANUAL_WHITE_BALANCE_OPTIONS
            )?.let(FeatureCatalogAction::UpdateManualWhiteBalanceKelvin)
                ?.takeIf { isVisible && editingEnabled }
        )
    )
}

private fun ManualControlSupport.toSettingsAvailability(): SettingsControlAvailability {
    return when (this) {
        ManualControlSupport.APPLY -> SettingsControlAvailability.SUPPORTED
        ManualControlSupport.SAVED_ONLY -> SettingsControlAvailability.DEGRADED
        ManualControlSupport.UNSUPPORTED -> SettingsControlAvailability.UNSUPPORTED
    }
}

private fun ManualControlSupport.manualSupportLabel(): String {
    return when (this) {
        ManualControlSupport.APPLY -> "Camera2 interop"
        ManualControlSupport.SAVED_ONLY -> "Saved only"
        ManualControlSupport.UNSUPPORTED -> "Temporarily unsupported"
    }
}

private fun manualSupportSummary(
    capabilities: ManualControlCapabilityMatrix
): String {
    val applied = mutableListOf<String>()
    val savedOnly = mutableListOf<String>()
    val unsupported = mutableListOf<String>()

    fun collect(label: String, support: ManualControlSupport) {
        when (support) {
            ManualControlSupport.APPLY -> applied += label
            ManualControlSupport.SAVED_ONLY -> savedOnly += label
            ManualControlSupport.UNSUPPORTED -> unsupported += label
        }
    }

    collect("RAW", capabilities.raw)
    collect("ISO", capabilities.iso)
    collect("S", capabilities.shutter)
    collect("EV", capabilities.exposureCompensation)
    collect("Focus", capabilities.focusDistance)
    collect("A", capabilities.aperture)
    collect("WB", capabilities.whiteBalance)

    return buildString {
        if (applied.isNotEmpty()) {
            append("Adapter applies ")
            append(applied.joinToString(separator = " / "))
        }
        if (savedOnly.isNotEmpty()) {
            if (isNotEmpty()) {
                append(" | ")
            }
            append(savedOnly.joinToString(separator = " / "))
            append(" stay saved-only")
        }
        if (unsupported.isNotEmpty()) {
            if (isNotEmpty()) {
                append(" | ")
            }
            append(unsupported.joinToString(separator = " / "))
            append(" temporarily unsupported")
        }
        if (isEmpty()) {
            append("Manual controls currently unavailable")
        }
    }
}

internal fun portraitLabPageRenderModel(
    state: SessionState,
    text: AppTextResolver
): PortraitLabPageRenderModel {
    val settings = state.settings.persisted
    val editingEnabled = state.activeShot == null && state.countdownRemainingSeconds == null
    val supportsStillCapture = state.activeDeviceCapabilities.supportsStillCapture
    val availability = if (supportsStillCapture) {
        SettingsControlAvailability.SUPPORTED
    } else {
        SettingsControlAvailability.UNSUPPORTED
    }
    return PortraitLabPageRenderModel(
        headline = "Portrait Lab",
        supportingText = "Portrait product tuning lives one level below Lens Lab. Use this page to shape the saved portrait profile, beauty behavior, and bokeh feel without changing the active portrait filter roster.",
        heroSummary = buildString {
            append(settings.photo.portraitProfile.label)
            append(" • Beauty ")
            append(settings.photo.portraitBeautyPreset.label)
            append(" ")
            append(settings.photo.portraitBeautyStrength.label)
            append(" • Bokeh ")
            append(settings.photo.portraitBokehEffect.label)
        },
        editingEnabled = editingEnabled,
        editingHint = if (editingEnabled) {
            "Portrait defaults save instantly and apply to the next portrait capture metadata and lightweight render pass."
        } else {
            "Finish the current capture before changing portrait product defaults."
        },
        profileControl = SettingsControlRenderModel(
            label = "Portrait profile",
            value = settings.photo.portraitProfile.label,
            availability = availability,
            supportLabel = if (supportsStillCapture) {
                "${PortraitProfile.entries.size} product profiles"
            } else {
                "Still capture unavailable on this device"
            },
            nextAction = if (supportsStillCapture) {
                PersistedSettingsAction.UpdatePortraitProfile(
                    nextListValue(settings.photo.portraitProfile, PortraitProfile.entries.toList())
                )
            } else {
                null
            }
        ),
        beautyPresetControl = SettingsControlRenderModel(
            label = "Beauty preset",
            value = settings.photo.portraitBeautyPreset.label,
            availability = availability,
            supportLabel = if (supportsStillCapture) {
                "${PortraitBeautyPreset.entries.size} plans"
            } else {
                "Still capture unavailable on this device"
            },
            nextAction = if (supportsStillCapture) {
                PersistedSettingsAction.UpdatePortraitBeautyPreset(
                    nextListValue(
                        settings.photo.portraitBeautyPreset,
                        PortraitBeautyPreset.entries.toList()
                    )
                )
            } else {
                null
            }
        ),
        beautyStrengthControl = SettingsControlRenderModel(
            label = "Beauty strength",
            value = settings.photo.portraitBeautyStrength.label,
            availability = availability,
            supportLabel = if (supportsStillCapture) {
                "${PortraitBeautyStrength.entries.size} levels"
            } else {
                "Still capture unavailable on this device"
            },
            nextAction = if (supportsStillCapture) {
                PersistedSettingsAction.UpdatePortraitBeautyStrength(
                    nextListValue(
                        settings.photo.portraitBeautyStrength,
                        PortraitBeautyStrength.entries.toList()
                    )
                )
            } else {
                null
            }
        ),
        bokehEffectControl = SettingsControlRenderModel(
            label = "Bokeh effect",
            value = settings.photo.portraitBokehEffect.label,
            availability = availability,
            supportLabel = if (supportsStillCapture) {
                "${PortraitBokehEffect.entries.size} rendering feels"
            } else {
                "Still capture unavailable on this device"
            },
            nextAction = if (supportsStillCapture) {
                PersistedSettingsAction.UpdatePortraitBokehEffect(
                    nextListValue(
                        settings.photo.portraitBokehEffect,
                        PortraitBokehEffect.entries.toList()
                    )
                )
            } else {
                null
            }
        ),
        footer = "Filter Lab still owns portrait color style selection. Portrait Lab only governs the product profile, beauty plan/strength, and lightweight bokeh rendering metadata introduced in 6B-5."
    )
}

internal fun watermarkLabSelectorRenderModel(
    state: SessionState,
    text: AppTextResolver
): WatermarkLabSelectorRenderModel {
    val settings = state.settings.persisted
    val catalog = state.settings.catalog
    val editingEnabled = state.activeShot == null && state.countdownRemainingSeconds == null
    val supportsStillCapture = state.activeDeviceCapabilities.supportsStillCapture
    val selectedTemplate = catalog.watermarkTemplateOrNull(settings.photo.defaultWatermarkTemplateId)
    return WatermarkLabSelectorRenderModel(
        headline = "Watermark Lab",
        supportingText = "Watermark selection lives one level below Lens Lab. Choose the active template here, then open the template-specific style page inside this lab.",
        heroSummary = buildString {
            append("Default ")
            append(selectedTemplate?.label ?: settings.photo.defaultWatermarkTemplateId)
            append(" • ")
            append(catalog.watermarkTemplates.size)
            append(" templates staged")
        },
        editingEnabled = editingEnabled,
        editingHint = if (editingEnabled) {
            "Default template changes save instantly. Each template keeps its own placement, scale, opacity, and frame background preset."
        } else {
            "Finish the current capture before changing watermark defaults."
        },
        items = catalog.watermarkTemplates.map { template ->
            val style = settings.photo.watermarkStyleFor(template.id)
            val isSelected = template.id == settings.photo.defaultWatermarkTemplateId
            WatermarkLabTemplateItemRenderModel(
                templateId = template.id,
                title = template.label,
                supportingText = buildString {
                    append(if (template.supportsFrameBorder) "Expanded frame" else "Classic overlay")
                    append(" | Tokens ")
                    append(template.tokenKeys.prettyWatermarkTokens())
                    append(" | Placement ")
                    append(style.textPlacement.label)
                    append(" | Scale ")
                    append(style.textScale.label)
                    append(" | Opacity ")
                    append(style.textOpacity.label)
                    if (template.supportsFrameBorder) {
                        append(" | Background ")
                        append(style.frameBackground.label)
                    }
                    if (isSelected) {
                        append(" | Current default")
                    }
                },
                isSelected = isSelected,
                useAction = if (supportsStillCapture && !isSelected) {
                    PersistedSettingsAction.UpdatePhotoWatermarkTemplate(template.id)
                } else {
                    null
                },
                editButtonLabel = if (supportsStillCapture) {
                    buildString {
                        append("Open Style Page")
                        append('\n')
                        append(template.label)
                        append('\n')
                        append(
                            if (template.supportsFrameBorder) {
                                "Placement, scale, opacity, background"
                            } else {
                                "Placement, scale, opacity"
                            }
                        )
                    }
                } else {
                    null
                }
            )
        },
        footer = if (supportsStillCapture) {
            "Classic Overlay keeps its border background fixed; Travel Polaroid and Retro Frame expose frame background variants on their own style pages."
        } else {
            "Still capture is unavailable on this device, so Watermark Lab stays read-only."
        }
    )
}

internal fun watermarkLabDetailRenderModel(
    state: SessionState,
    templateId: String,
    text: AppTextResolver
): WatermarkLabDetailRenderModel {
    val settings = state.settings.persisted
    val catalog = state.settings.catalog
    val editingEnabled = state.activeShot == null && state.countdownRemainingSeconds == null
    val supportsStillCapture = state.activeDeviceCapabilities.supportsStillCapture
    val template = catalog.watermarkTemplateOrNull(templateId)
        ?: catalog.watermarkTemplateOrNull(settings.photo.defaultWatermarkTemplateId)
        ?: catalog.watermarkTemplates.firstOrNull()
        ?: WatermarkTemplate(id = templateId, label = templateId)
    val style = settings.photo.watermarkStyleFor(template.id)
    val controlAvailability = if (supportsStillCapture) {
        SettingsControlAvailability.SUPPORTED
    } else {
        SettingsControlAvailability.UNSUPPORTED
    }
    return WatermarkLabDetailRenderModel(
        headline = template.label,
        supportingText = if (template.id == settings.photo.defaultWatermarkTemplateId) {
            "This is the active watermark default. Changes here will affect the next static photo rendered with this template."
        } else {
            "This template is not the current default yet. Tune it here first, then switch to it from the selector page when you're ready."
        },
        heroSummary = buildString {
            append("Placement ")
            append(style.textPlacement.label)
            append(" • Scale ")
            append(style.textScale.label)
            append(" • Opacity ")
            append(style.textOpacity.label)
            if (template.supportsFrameBorder) {
                append(" • Background ")
                append(style.frameBackground.label)
            }
        },
        templateId = template.id,
        editingEnabled = editingEnabled,
        editingHint = if (editingEnabled) {
            "Template-specific styles save instantly and stay attached to this watermark preset."
        } else {
            "Finish the current capture before changing watermark styles."
        },
        placementControl = SettingsControlRenderModel(
            label = "Text placement",
            value = style.textPlacement.label,
            availability = controlAvailability,
            supportLabel = if (supportsStillCapture) {
                "${WatermarkTextPlacement.entries.size} placements"
            } else {
                "Still capture unavailable on this device"
            },
            nextAction = if (supportsStillCapture) {
                PersistedSettingsAction.UpdateWatermarkTextPlacement(
                    templateId = template.id,
                    placement = nextListValue(style.textPlacement, WatermarkTextPlacement.entries.toList())
                )
            } else {
                null
            }
        ),
        textScaleControl = SettingsControlRenderModel(
            label = "Text scale",
            value = style.textScale.label,
            availability = controlAvailability,
            supportLabel = if (supportsStillCapture) {
                "${WatermarkTextScale.entries.size} steps"
            } else {
                "Still capture unavailable on this device"
            },
            nextAction = if (supportsStillCapture) {
                PersistedSettingsAction.UpdateWatermarkTextScale(
                    templateId = template.id,
                    scale = nextListValue(style.textScale, WatermarkTextScale.entries.toList())
                )
            } else {
                null
            }
        ),
        textOpacityControl = SettingsControlRenderModel(
            label = "Text opacity",
            value = style.textOpacity.label,
            availability = controlAvailability,
            supportLabel = if (supportsStillCapture) {
                "${WatermarkTextOpacity.entries.size} steps"
            } else {
                "Still capture unavailable on this device"
            },
            nextAction = if (supportsStillCapture) {
                PersistedSettingsAction.UpdateWatermarkTextOpacity(
                    templateId = template.id,
                    opacity = nextListValue(style.textOpacity, WatermarkTextOpacity.entries.toList())
                )
            } else {
                null
            }
        ),
        frameBackgroundControl = if (template.supportsFrameBorder) {
            SettingsControlRenderModel(
                label = "Frame background",
                value = style.frameBackground.label,
                availability = controlAvailability,
                supportLabel = if (supportsStillCapture) {
                    "${WatermarkFrameBackground.entries.size} moods"
                } else {
                    "Still capture unavailable on this device"
                },
                nextAction = if (supportsStillCapture) {
                    PersistedSettingsAction.UpdateWatermarkFrameBackground(
                        templateId = template.id,
                        background = nextListValue(
                            style.frameBackground,
                            WatermarkFrameBackground.entries.toList()
                        )
                    )
                } else {
                    null
                }
            )
        } else {
            null
        },
        footer = buildString {
            append("Tokens: ")
            append(template.tokenKeys.prettyWatermarkTokens())
            append(". ")
            append(
                if (template.supportsFrameBorder) {
                    "Frame border rendering is live for static-photo export."
                } else {
                    "Classic overlay stays inside the source image without an expanded border."
                }
            )
        }
    )
}

internal fun filterLabPageRenderModel(
    state: SessionState,
    text: AppTextResolver,
    selectedFamily: FilterLabFamily = defaultFilterLabFamily(state.activeMode),
    showAdjustmentPanel: Boolean = false,
    adjustmentMode: FilterAdjustmentMode = FilterAdjustmentMode.LIGHT
): FilterLabPageRenderModel {
    val settings = state.settings.persisted
    val catalog = state.settings.catalog
    val editingEnabled = state.activeShot == null && state.countdownRemainingSeconds == null
    val family = filterLabFamilyState(
        state = state,
        selectedFamily = selectedFamily
    )
    val currentProfile = family.filters.firstOrNull { profile -> profile.id == family.currentFilterId }
    val currentFilterLabel = currentProfile?.label ?: family.currentFilterId
    val currentRenderSpec = currentProfile?.renderSpec ?: FilterRenderSpec()
    val cycleAction = if (family.supported) {
        nextListValueOrNull(
            current = family.currentFilterId,
            values = family.filters.map(FilterProfile::id)
        )?.let(family.updateAction)
    } else {
        null
    }
    return FilterLabPageRenderModel(
        headline = "Filter Lab",
        supportingText = "Independent filter panel for mode defaults. Import and export stay deferred in this 6B-2 closure; selected looks can be adjusted or saved as custom.",
        heroSummary = "${family.label} default $currentFilterLabel • ${family.filters.size} looks staged",
        currentFilterSummary = buildString {
            append("Current default ")
            append(currentFilterLabel)
            currentProfile?.renderSpec?.let { renderSpec ->
                append(" | ")
                append(renderSpec.compactSummary())
            }
        },
        rosterText = family.filters.joinToString(separator = "\n") { profile ->
            val marker = if (profile.id == family.currentFilterId) "•" else "·"
            val customBadge = if (profile.builtIn) "" else " | Custom"
            "$marker ${profile.label}${customBadge} | ${profile.renderSpec?.compactSummary() ?: "Renderer pending"}"
        },
        editingEnabled = editingEnabled,
        editingHint = if (editingEnabled) {
            "Selected family defaults save instantly and refresh the active mode when relevant."
        } else {
            "Finish the current capture before changing filter defaults."
        },
        photoTab = filterLabTabRenderModel(
            family = FilterLabFamily.PHOTO,
            currentFilterId = settings.photo.defaultFilterProfileId,
            filters = catalog.photoSettingsFilterProfiles(),
            selectedFamily = family.family
        ),
        humanisticTab = filterLabTabRenderModel(
            family = FilterLabFamily.HUMANISTIC,
            currentFilterId = settings.photo.defaultHumanisticFilterProfileId,
            filters = catalog.filterProfilesFor(FilterProfileCategory.HUMANISTIC, includeCustom = true),
            selectedFamily = family.family
        ),
        portraitTab = filterLabTabRenderModel(
            family = FilterLabFamily.PORTRAIT,
            currentFilterId = settings.photo.defaultPortraitFilterProfileId,
            filters = catalog.filterProfilesFor(FilterProfileCategory.PORTRAIT, includeCustom = true),
            selectedFamily = family.family
        ),
        videoTab = filterLabTabRenderModel(
            family = FilterLabFamily.VIDEO,
            currentFilterId = settings.video.defaultFilterProfileId,
            filters = catalog.videoSettingsFilterProfiles(),
            selectedFamily = family.family
        ),
        adjustControl = FilterLabAdjustRenderModel(
            buttonLabel = buildString {
                append("Adjust Selected")
                append('\n')
                append(currentFilterLabel)
                append('\n')
                append(
                    when {
                        currentProfile == null -> "Unavailable • Missing source profile"
                        currentProfile.builtIn -> "Ready • Opens an editable custom copy"
                        else -> "Ready • Editing current custom look"
                    }
                )
            },
            family = family.family,
            sourceProfileId = currentProfile?.id,
            isEnabled = editingEnabled && family.supported && currentProfile != null,
            willCreateCustomCopy = currentProfile?.builtIn == true
        ),
        filterItems = family.filters.map { profile ->
            val isSelected = profile.id == family.currentFilterId
            FilterLabFilterItemRenderModel(
                filterProfileId = profile.id,
                title = profile.label,
                supportingText = buildString {
                    append(profile.renderSpec?.compactSummary() ?: "Renderer pending")
                    if (!profile.builtIn) {
                        append(" | Custom")
                    }
                    if (isSelected) {
                        append(" | Selected default")
                    }
                },
                isSelected = isSelected,
                nextAction = if (isSelected) {
                    null
                } else {
                    family.updateAction(profile.id)
                },
                adjustButtonLabel = if (isSelected) {
                    buildString {
                        append("Adjust Selected")
                        append('\n')
                        append(profile.label)
                        append('\n')
                        append(
                            if (profile.builtIn) {
                                "Ready • Opens an editable custom copy"
                            } else {
                                "Ready • Editing current custom look"
                            }
                        )
                    }
                } else {
                    null
                }
            )
        },
        adjustmentPanel = FilterAdjustmentPanelRenderModel(
            isVisible = showAdjustmentPanel && currentProfile != null && family.supported,
            mode = adjustmentMode,
            selectedProfileId = currentProfile?.id,
            selectedProfileLabel = currentFilterLabel,
            renderSpec = currentRenderSpec,
            modeToggleLabel = if (adjustmentMode == FilterAdjustmentMode.LIGHT) {
                "Switch to Advanced"
            } else {
                "Switch to Light"
            },
            lightPalette = FilterLightPaletteRenderModel(
                summary = currentRenderSpec.lightPaletteSummary(),
                supportingText = "Horizontal swipe for color, vertical swipe for tone."
            ),
            advancedControls = FilterAdvancedControl.entries.map { control ->
                FilterAdvancedControlRenderModel(
                    control = control,
                    buttonLabel = buildString {
                        append(control.label)
                        append('\n')
                        append(currentRenderSpec.levelLabel(control))
                        append('\n')
                        append("Tap to cycle")
                    }
                )
            }
        ),
        cycleControl = SettingsControlRenderModel(
            label = "Next ${family.label} look",
            value = currentFilterLabel,
            availability = if (family.supported && family.filters.isNotEmpty()) {
                SettingsControlAvailability.SUPPORTED
            } else {
                SettingsControlAvailability.UNSUPPORTED
            },
            supportLabel = when {
                !family.supported -> family.unsupportedReason
                family.filters.isEmpty() -> "No compatible looks"
                else -> "${family.filters.size} looks | import/export deferred"
            },
            nextAction = cycleAction
        ),
        saveCustomControl = FilterLabSaveCustomRenderModel(
            buttonLabel = buildString {
                append("Save as Custom")
                append('\n')
                append(currentFilterLabel)
                append('\n')
                append(
                    when {
                        currentProfile == null -> "Unavailable • Missing source profile"
                        !currentProfile.builtIn -> "Unavailable • Current default already custom"
                else -> "Ready • Becomes the default for ${family.label}"
                    }
                )
            },
            family = family.family,
            sourceProfileId = currentProfile?.id,
            isEnabled = currentProfile?.builtIn == true
        ),
        footer = "Strategy locked for current 6B-2 work: ship an independent Filter Lab first. Import/export remain deferred while in-panel adjustment and custom save move forward."
    )
}

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

private fun zoomButtonLabel(
    state: SessionState,
    strings: SessionUiStrings
): String {
    return if (!state.activeDeviceCapabilities.zoomRatioCapability.isSwitchingSupported) {
        strings.buttonZoomUnavailable
    } else {
        "${strings.buttonZoomPrefix} ${zoomRatioLabel(state.activeDeviceGraph.preview.zoomRatio)}"
    }
}

private fun zoomCapsuleModels(state: SessionState): List<ZoomCapsuleRenderModel> {
    val capability = state.activeDeviceCapabilities.zoomRatioCapability
    if (!capability.isSwitchingSupported) return emptyList()
    val currentRatio = normalizedZoomRatioValue(state.activeDeviceGraph.preview.zoomRatio)
    return capability.normalizedSupportedRatios.map { ratio ->
        ZoomCapsuleRenderModel(
            label = "${ratio}x",
            ratio = ratio,
            isActive = ratio == currentRatio
        )
    }
}

private fun zoomRatioLabel(zoomRatio: Float): String {
    return "${normalizedZoomRatioValue(zoomRatio)}x"
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

private fun onOffLabel(enabled: Boolean): String = if (enabled) "On" else "Off"

private fun photoFilterLabel(
    filterId: String,
    filters: List<FilterProfile>
): String {
    return filters.firstOrNull { it.id == filterId }?.label ?: filterId
}

private fun videoFilterLabel(
    filterId: String,
    filters: List<FilterProfile>
): String {
    return filters.firstOrNull { it.id == filterId }?.label ?: filterId
}

private fun watermarkTemplateLabel(
    templateId: String,
    templates: List<WatermarkTemplate>
): String {
    return templates.firstOrNull { it.id == templateId }?.label ?: templateId
}

private fun Set<String>.prettyWatermarkTokens(): String {
    return sorted().joinToString(separator = ", ") { token ->
        when (token) {
            "camera-params" -> "Camera Params"
            "datetime" -> "Date/Time"
            "location" -> "Location"
            "model" -> "Model"
            else -> token.replace('-', ' ').replaceFirstChar(Char::titlecase)
        }
    }
}

private fun supportCountLabel(count: Int): String = "$count options"

internal fun defaultFilterLabFamily(activeMode: ModeId): FilterLabFamily {
    return when (activeMode) {
        ModeId.HUMANISTIC -> FilterLabFamily.HUMANISTIC
        ModeId.PORTRAIT -> FilterLabFamily.PORTRAIT
        ModeId.VIDEO -> FilterLabFamily.VIDEO
        ModeId.PHOTO,
        ModeId.DOCUMENT,
        ModeId.NIGHT,
        ModeId.PRO -> FilterLabFamily.PHOTO
    }
}

private fun <T> nextListValue(current: T, values: List<T>): T {
    return nextListValueOrNull(current, values) ?: current
}

private fun <T> nextListValueOrNull(current: T, values: List<T>): T? {
    if (values.isEmpty()) {
        return null
    }
    val currentIndex = values.indexOf(current)
    return if (currentIndex == -1 || currentIndex == values.lastIndex) {
        values.first()
    } else {
        values[currentIndex + 1]
    }
}

// -- Dev Log --

private val KEY_EVENT_NAMES = setOf(
    "session.created", "session.booted", "session.stopped",
    "mode.switched", "lens.switched", "zoom.updated",
    "preview.first.frame", "preview.host.attached", "preview.host.detached",
    "capture.photo", "capture.saved",
    "recording.started", "recording.saved",
    "permissions.updated", "device.capabilities.updated", "settings.updated"
)

private val CORE_EVENT_NAMES = setOf(
    "preview.binding.started", "preview.recovery.started", "preview.recovery.requested",
    "preview.stopped", "preview.snapshot.updated",
    "capture.countdown.started", "capture.countdown.tick", "capture.countdown.completed",
    "capture.saving",
    "recording.requested",
    "shot.plan.failed",
    "mode.signal", "mode.event", "mode.hint",
    "intent.received"
)

private val ERROR_EVENT_NAMES = setOf(
    "preview.error", "preview.surface.lost", "preview.runtime.issue",
    "preview.recovery.failed", "preview.blocked",
    "capture.failed", "recording.failed", "recording.stop.blocked",
    "mode.switch.blocked", "mode.intent.blocked",
    "lens.switch.blocked", "zoom.switch.blocked",
    "still-quality.blocked", "still-resolution.blocked", "settings.update.blocked"
)

private val ERROR_SUFFIXES = listOf(".unavailable", ".skipped")

private fun isErrorEvent(name: String): Boolean {
    return name in ERROR_EVENT_NAMES || ERROR_SUFFIXES.any { name.endsWith(it) }
}

internal fun devLogRenderModel(
    state: SessionState,
    traceEvents: List<SessionTraceEvent>,
    isDebugBuild: Boolean,
    selectedTab: DevLogTab
): DevLogRenderModel {
    if (!isDebugBuild) {
        return DevLogRenderModel(
            isAvailable = false,
            selectedTab = selectedTab,
            title = "Dev Log",
            content = "",
            exportContent = ""
        )
    }

    val keyEvents = traceEvents.filter { it.name in KEY_EVENT_NAMES }
    val coreEvents = traceEvents.filter { it.name in CORE_EVENT_NAMES }
    val errorEvents = traceEvents.filter { isErrorEvent(it.name) }
    val allEvents = traceEvents

    fun formatEvents(events: List<SessionTraceEvent>): String {
        return events.joinToString("\n") { event ->
            "${event.sequence}. ${event.name} -> ${event.detail}"
        }
    }

    val debugDump = buildSessionDebugDump(state, traceEvents)
    val perf = debugDump.perfSnapshot
    val recovery = debugDump.recoveryTrace

    val coreSummary = buildString {
        appendLine("DebugDump: ${debugDump.lifecycle} | ${debugDump.activeMode.name} | preview=${debugDump.previewStatus} | capture=${debugDump.captureStatus} | recording=${debugDump.recordingStatus}")
        appendLine("PerfSnapshot: last=${perf.lastFirstFrameLatencyMillis ?: "--"} ms, best=${perf.bestFirstFrameLatencyMillis ?: "--"} ms, worst=${perf.worstFirstFrameLatencyMillis ?: "--"} ms, binds=${perf.bindCount}, recoveries=${perf.recoveryCount}")
        appendLine("RecoveryTrace: ${if (recovery.isRecoveryActive) "active" else "idle"}, last=${recovery.lastRecoveryReason ?: "--"}, recoveredFrame=${recovery.recoveredFirstFrameLatencyMillis ?: "--"} ms, failure=${recovery.lastFailureReason ?: "--"}")
    }

    val tabContent = when (selectedTab) {
        DevLogTab.KEY -> formatEvents(keyEvents)
        DevLogTab.CORE -> formatEvents(coreEvents)
        DevLogTab.ERROR -> formatEvents(errorEvents)
        DevLogTab.ALL -> formatEvents(allEvents)
    }

    val exportContent = buildString {
        appendLine("=== KEY EVENTS ===")
        appendLine(formatEvents(keyEvents))
        appendLine("=== CORE EVENTS ===")
        appendLine(formatEvents(coreEvents))
        appendLine("=== ERROR EVENTS ===")
        appendLine(formatEvents(errorEvents))
        appendLine("=== ALL EVENTS ===")
        appendLine(formatEvents(allEvents))
        appendLine("=== CORE SUMMARY ===")
        append(coreSummary)
    }

    return DevLogRenderModel(
        isAvailable = true,
        selectedTab = selectedTab,
        title = when (selectedTab) {
            DevLogTab.KEY -> "Key Log (${keyEvents.size})"
            DevLogTab.CORE -> "Core Log (${coreEvents.size})"
            DevLogTab.ERROR -> "Error Log (${errorEvents.size})"
            DevLogTab.ALL -> "All Events (${allEvents.size})"
        },
        content = tabContent,
        exportContent = exportContent
    )
}

private val MANUAL_ISO_OPTIONS = listOf<Int?>(null, 100, 200, 320, 640, 800, 1600)
private val MANUAL_SHUTTER_OPTIONS = listOf<Long?>(null, 8L, 16L, 33L, 50L, 80L, 125L, 250L, 500L)
private val MANUAL_EXPOSURE_OPTIONS = listOf<Int?>(null, -2, -1, 0, 1, 2)
private val MANUAL_FOCUS_OPTIONS = listOf<Float?>(null, 0.5f, 1.0f, 2.0f, 4.0f)
private val MANUAL_APERTURE_OPTIONS = listOf<Float?>(null, 1.4f, 1.8f, 2.2f, 2.8f, 4.0f)
private val MANUAL_WHITE_BALANCE_OPTIONS = listOf<Int?>(null, 3200, 4300, 4800, 5600, 6500)

private fun manualEvLabel(steps: Int): String {
    return when {
        steps > 0 -> "+$steps"
        else -> steps.toString()
    }
}

private fun manualOneDecimal(value: Float): String = String.format(Locale.US, "%.1f", value)

private fun com.opencamera.core.settings.FeatureCatalog.photoSettingsFilterProfiles(): List<FilterProfile> {
    return filterProfiles.filter { filterProfile ->
        filterProfile.category in setOf(
            FilterProfileCategory.PHOTO,
            FilterProfileCategory.HUMANISTIC,
            FilterProfileCategory.PORTRAIT,
            FilterProfileCategory.CUSTOM
        )
    }
}

private fun com.opencamera.core.settings.FeatureCatalog.videoSettingsFilterProfiles(): List<FilterProfile> {
    return filterProfiles
}

private data class FilterLabFamilyState(
    val family: FilterLabFamily,
    val label: String,
    val currentFilterId: String,
    val filters: List<FilterProfile>,
    val supported: Boolean,
    val unsupportedReason: String,
    val updateAction: (String) -> PersistedSettingsAction
)

private fun filterLabFamilyState(
    state: SessionState,
    selectedFamily: FilterLabFamily
): FilterLabFamilyState {
    val settings = state.settings.persisted
    val catalog = state.settings.catalog
    return when (selectedFamily) {
        FilterLabFamily.PHOTO -> FilterLabFamilyState(
            family = selectedFamily,
            label = selectedFamily.label,
            currentFilterId = settings.photo.defaultFilterProfileId,
            filters = catalog.photoSettingsFilterProfiles(),
            supported = state.activeDeviceCapabilities.supportsStillCapture,
            unsupportedReason = "Still capture unavailable on this device",
            updateAction = PersistedSettingsAction::UpdatePhotoFilter
        )

        FilterLabFamily.HUMANISTIC -> FilterLabFamilyState(
            family = selectedFamily,
            label = selectedFamily.label,
            currentFilterId = settings.photo.defaultHumanisticFilterProfileId,
            filters = catalog.filterProfilesFor(FilterProfileCategory.HUMANISTIC, includeCustom = true),
            supported = state.activeDeviceCapabilities.supportsStillCapture,
            unsupportedReason = "Still capture unavailable on this device",
            updateAction = PersistedSettingsAction::UpdateHumanisticFilter
        )

        FilterLabFamily.PORTRAIT -> FilterLabFamilyState(
            family = selectedFamily,
            label = selectedFamily.label,
            currentFilterId = settings.photo.defaultPortraitFilterProfileId,
            filters = catalog.filterProfilesFor(FilterProfileCategory.PORTRAIT, includeCustom = true),
            supported = state.activeDeviceCapabilities.supportsStillCapture,
            unsupportedReason = "Still capture unavailable on this device",
            updateAction = PersistedSettingsAction::UpdatePortraitFilter
        )

        FilterLabFamily.VIDEO -> FilterLabFamilyState(
            family = selectedFamily,
            label = selectedFamily.label,
            currentFilterId = settings.video.defaultFilterProfileId,
            filters = catalog.videoSettingsFilterProfiles(),
            supported = state.activeDeviceCapabilities.supportsVideoRecording,
            unsupportedReason = "Video recording unavailable on this device",
            updateAction = PersistedSettingsAction::UpdateVideoFilter
        )
    }
}

private fun filterLabTabRenderModel(
    family: FilterLabFamily,
    currentFilterId: String,
    filters: List<FilterProfile>,
    selectedFamily: FilterLabFamily
): FilterLabTabRenderModel {
    val label = filters.firstOrNull { profile -> profile.id == currentFilterId }?.label ?: currentFilterId
    return FilterLabTabRenderModel(
        family = family,
        label = "${family.label}\n$label",
        isSelected = family == selectedFamily
    )
}

private fun com.opencamera.core.settings.FeatureCatalog.watermarkTemplateOrNull(
    templateId: String?
): WatermarkTemplate? {
    return watermarkTemplates.firstOrNull { template -> template.id == templateId }
}

private val StillCaptureOutputSize.label: String
    get() = "${width}x${height}"

private val LensFacing.label: String
    get() = when (this) {
        LensFacing.BACK -> "Back"
        LensFacing.FRONT -> "Front"
    }

private fun FilterRenderSpec.compactSummary(): String {
    return buildString {
        append("B ")
        append(brightnessShift)
        append(" | C ")
        append(formatFilterMetric(contrast))
        append(" | S ")
        append(formatFilterMetric(saturation))
        append(" | W ")
        append(warmthShift)
        append(" | Mono ")
        append(formatFilterMetric(monochromeMix))
        append(" | Vig ")
        append(formatFilterMetric(vignetteStrength))
        if (tintShift != 0) {
            append(" | Tint ")
            append(tintShift)
        }
        if (haloStrength > 0f) {
            append(" | Halo ")
            append(formatFilterMetric(haloStrength))
        }
    }
}

private fun formatFilterMetric(value: Float): String = String.format(Locale.US, "%.2f", value)

internal fun FilterRenderSpec.applyLightPalette(
    colorAxis: Float,
    toneAxis: Float
): FilterRenderSpec {
    val clampedColor = colorAxis.coerceIn(-1f, 1f)
    val clampedTone = toneAxis.coerceIn(-1f, 1f)
    return copy(
        saturation = (saturation + (clampedColor * 0.16f)).coerceIn(0.72f, 1.38f),
        warmthShift = (warmthShift + (clampedColor * 8f).toInt()).coerceIn(-24, 24),
        brightnessShift = (brightnessShift + (clampedTone * 10f).toInt()).coerceIn(-24, 32),
        contrast = (contrast - (clampedTone * 0.12f)).coerceIn(0.82f, 1.28f)
    )
}

internal fun FilterRenderSpec.nextAdvancedControl(
    control: FilterAdvancedControl
): FilterRenderSpec {
    return when (control) {
        FilterAdvancedControl.EXPOSURE -> copy(
            brightnessShift = nextLevel(control).brightnessValue
        )

        FilterAdvancedControl.SOFT_GLOW -> copy(
            softGlowStrength = nextLevel(control).floatValue
        )

        FilterAdvancedControl.HALO -> copy(
            haloStrength = nextLevel(control).floatValue
        )

        FilterAdvancedControl.GRAIN -> copy(
            grainStrength = nextLevel(control).floatValue
        )

        FilterAdvancedControl.SHARPNESS -> copy(
            sharpnessBoost = nextLevel(control).floatValue
        )

        FilterAdvancedControl.VIGNETTE -> copy(
            vignetteStrength = nextLevel(control).floatValue
        )

        FilterAdvancedControl.HIGHLIGHTS -> copy(
            highlightCompression = nextLevel(control).floatValue
        )

        FilterAdvancedControl.SHADOWS -> copy(
            shadowLift = nextLevel(control).floatValue
        )

        FilterAdvancedControl.WARM_BOOST -> nextLevel(control).let { nextLevel ->
            copy(
                warmBoost = nextLevel.floatValue,
                coolBoost = if (nextLevel == FilterAdjustmentLevel.OFF) coolBoost else 0f
            )
        }

        FilterAdvancedControl.COOL_BOOST -> nextLevel(control).let { nextLevel ->
            copy(
                coolBoost = nextLevel.floatValue,
                warmBoost = if (nextLevel == FilterAdjustmentLevel.OFF) warmBoost else 0f
            )
        }

        FilterAdvancedControl.TEMPERATURE_SHIFT -> copy(
            warmthShift = nextWarmthShiftValue(warmthShift)
        )

        FilterAdvancedControl.TINT_SHIFT -> copy(
            tintShift = nextTintShiftValue(tintShift)
        )
    }
}

private fun FilterRenderSpec.nextLevel(
    control: FilterAdvancedControl
): FilterAdjustmentLevel {
    return when (currentLevel(control)) {
        FilterAdjustmentLevel.OFF -> FilterAdjustmentLevel.LOW
        FilterAdjustmentLevel.LOW -> FilterAdjustmentLevel.MEDIUM
        FilterAdjustmentLevel.MEDIUM -> FilterAdjustmentLevel.HIGH
        FilterAdjustmentLevel.HIGH -> FilterAdjustmentLevel.OFF
    }
}

private enum class FilterAdjustmentLevel(
    val label: String,
    val floatValue: Float,
    val brightnessValue: Int
) {
    OFF("Off", 0f, 0),
    LOW("Low", 0.10f, 6),
    MEDIUM("Medium", 0.20f, 12),
    HIGH("High", 0.30f, 18)
}

private fun FilterRenderSpec.currentLevel(
    control: FilterAdvancedControl
): FilterAdjustmentLevel {
    val value = when (control) {
        FilterAdvancedControl.EXPOSURE -> return nearestBrightnessLevel(brightnessShift)
        FilterAdvancedControl.SOFT_GLOW -> softGlowStrength
        FilterAdvancedControl.HALO -> haloStrength
        FilterAdvancedControl.GRAIN -> grainStrength
        FilterAdvancedControl.SHARPNESS -> sharpnessBoost
        FilterAdvancedControl.VIGNETTE -> vignetteStrength
        FilterAdvancedControl.HIGHLIGHTS -> highlightCompression
        FilterAdvancedControl.SHADOWS -> shadowLift
        FilterAdvancedControl.WARM_BOOST -> warmBoost
        FilterAdvancedControl.COOL_BOOST -> coolBoost
        FilterAdvancedControl.TEMPERATURE_SHIFT,
        FilterAdvancedControl.TINT_SHIFT -> error("Signed adjustments are modeled separately")
    }
    return nearestFloatLevel(value)
}

private fun FilterRenderSpec.levelLabel(control: FilterAdvancedControl): String {
    return when (control) {
        FilterAdvancedControl.TEMPERATURE_SHIFT -> warmthShiftLevel(warmthShift).labelForTemperature()
        FilterAdvancedControl.TINT_SHIFT -> tintShiftLevel(tintShift).labelForTint()
        else -> currentLevel(control).label
    }
}

private fun FilterRenderSpec.lightPaletteSummary(): String {
    return buildString {
        append("Color ")
        append(
            when {
                tintShift >= 4 -> "Magenta/Warm"
                tintShift <= -4 -> "Green/Cool"
                warmthShift > 3 || saturation > 1.08f -> "Warm+"
                warmthShift < -3 || saturation < 0.96f -> "Cool/Muted"
                else -> "Neutral"
            }
        )
        append(" | Tone ")
        append(
            when {
                brightnessShift >= 8 || contrast <= 0.96f -> "Soft Lift"
                brightnessShift <= -4 || contrast >= 1.12f -> "Deep Contrast"
                else -> "Balanced"
            }
        )
    }
}

private fun nearestFloatLevel(value: Float): FilterAdjustmentLevel {
    return FilterAdjustmentLevel.entries.minBy { level ->
        kotlin.math.abs(level.floatValue - value)
    }
}

private fun nextWarmthShiftValue(current: Int): Int {
    return when (warmthShiftLevel(current)) {
        FilterSignedAdjustmentLevel.OFF -> 6
        FilterSignedAdjustmentLevel.LOW_POSITIVE -> 12
        FilterSignedAdjustmentLevel.HIGH_POSITIVE -> -6
        FilterSignedAdjustmentLevel.LOW_NEGATIVE -> -12
        FilterSignedAdjustmentLevel.HIGH_NEGATIVE -> 0
    }
}

private fun warmthShiftLevel(value: Int): FilterSignedAdjustmentLevel {
    return when {
        value >= 10 -> FilterSignedAdjustmentLevel.HIGH_POSITIVE
        value >= 4 -> FilterSignedAdjustmentLevel.LOW_POSITIVE
        value <= -10 -> FilterSignedAdjustmentLevel.HIGH_NEGATIVE
        value <= -4 -> FilterSignedAdjustmentLevel.LOW_NEGATIVE
        else -> FilterSignedAdjustmentLevel.OFF
    }
}

private fun nextTintShiftValue(current: Int): Int {
    return when (tintShiftLevel(current)) {
        FilterSignedAdjustmentLevel.OFF -> 6
        FilterSignedAdjustmentLevel.LOW_POSITIVE -> 12
        FilterSignedAdjustmentLevel.HIGH_POSITIVE -> -6
        FilterSignedAdjustmentLevel.LOW_NEGATIVE -> -12
        FilterSignedAdjustmentLevel.HIGH_NEGATIVE -> 0
    }
}

private fun tintShiftLevel(value: Int): FilterSignedAdjustmentLevel {
    return when {
        value >= 10 -> FilterSignedAdjustmentLevel.HIGH_POSITIVE
        value >= 4 -> FilterSignedAdjustmentLevel.LOW_POSITIVE
        value <= -10 -> FilterSignedAdjustmentLevel.HIGH_NEGATIVE
        value <= -4 -> FilterSignedAdjustmentLevel.LOW_NEGATIVE
        else -> FilterSignedAdjustmentLevel.OFF
    }
}

private enum class FilterSignedAdjustmentLevel(
) {
    OFF,
    LOW_POSITIVE,
    HIGH_POSITIVE,
    LOW_NEGATIVE,
    HIGH_NEGATIVE
}

private fun FilterSignedAdjustmentLevel.labelForTemperature(): String {
    return when (this) {
        FilterSignedAdjustmentLevel.OFF -> "Off"
        FilterSignedAdjustmentLevel.LOW_POSITIVE -> "Warm"
        FilterSignedAdjustmentLevel.HIGH_POSITIVE -> "Warm+"
        FilterSignedAdjustmentLevel.LOW_NEGATIVE -> "Cool"
        FilterSignedAdjustmentLevel.HIGH_NEGATIVE -> "Cool+"
    }
}

private fun FilterSignedAdjustmentLevel.labelForTint(): String {
    return when (this) {
        FilterSignedAdjustmentLevel.OFF -> "Off"
        FilterSignedAdjustmentLevel.LOW_POSITIVE -> "Magenta"
        FilterSignedAdjustmentLevel.HIGH_POSITIVE -> "Magenta+"
        FilterSignedAdjustmentLevel.LOW_NEGATIVE -> "Green"
        FilterSignedAdjustmentLevel.HIGH_NEGATIVE -> "Green+"
    }
}

private fun nearestBrightnessLevel(value: Int): FilterAdjustmentLevel {
    return FilterAdjustmentLevel.entries.minBy { level ->
        kotlin.math.abs(level.brightnessValue - value)
    }
}

private fun Set<LensFacing>.lensFacingSummary(): String {
    return this.sortedBy { it.ordinal }
        .joinToString(separator = "/") { it.label.lowercase() }
}

private fun Set<com.opencamera.core.media.StillCaptureResolutionPreset>.stillResolutionPresetSummary(): String {
    return this.sortedBy { it.targetWidth * it.targetHeight }
        .joinToString(separator = "/") { it.label }
}

private fun List<StillCaptureOutputSize>.stillCaptureOutputSizeSummary(): String {
    return this.take(4).joinToString(separator = "/") { it.label }
}
