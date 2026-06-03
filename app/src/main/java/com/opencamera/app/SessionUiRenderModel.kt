package com.opencamera.app

import com.opencamera.core.media.ResourceDiagnosticsSnapshot
import com.opencamera.core.device.ManualControlCapabilityMatrix
import com.opencamera.core.device.ManualControlSupport
import com.opencamera.core.device.resolveVideoSpec
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.DocumentBatchCropStatus
import com.opencamera.core.session.DocumentBatchStatus
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.SessionTraceEvent
import com.opencamera.core.session.TraceEventDomain
import com.opencamera.core.session.buildSessionDebugDump
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.ColorLabSpec
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.FeatureCatalogAction
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.LiveSaveFormat
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.ResetTarget
import com.opencamera.core.settings.hasUserAdjustments
import com.opencamera.core.settings.PortraitBeautyPreset
import com.opencamera.core.settings.PortraitBeautyStrength
import com.opencamera.core.settings.PortraitBokehEffect
import com.opencamera.core.settings.PortraitProfile
import com.opencamera.core.settings.applyColorLab
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



internal data class SessionSettingsRenderModel(
    val commonSummary: String,
    val photoSummary: String,
    val videoSummary: String,
    val catalogSummary: String,
    val manualDraftSummary: String
)


internal data class CommonSettingsSectionRenderModel(
    val summary: String,
    val languageControl: SettingsControlRenderModel,
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
    val liveSaveFormat: SettingsControlRenderModel,
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
    val catalogFooter: String,
    val hasSettingsUserAdjustments: Boolean = false,
    val resetSettingsAction: PersistedSettingsAction.ResetToDefaults? = null
)


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
    val depthStrength: Int,
    val depthStrengthLabel: String,
    val updateDepthStrengthAction: PersistedSettingsAction.UpdatePortraitDepthStrength?,
    val footer: String
)

internal enum class FilterLabFamily {
    PHOTO,
    HUMANISTIC,
    PORTRAIT,
    VIDEO
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

internal enum class FilterAdvancedControl {
    EXPOSURE,
    SOFT_GLOW,
    HALO,
    GRAIN,
    SHARPNESS,
    VIGNETTE,
    HIGHLIGHTS,
    SHADOWS,
    WARM_BOOST,
    COOL_BOOST,
    TEMPERATURE_SHIFT,
    TINT_SHIFT
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
    val advancedControls: List<FilterAdvancedControlRenderModel>,
    val needsAutoPrepare: Boolean = false
)

internal enum class StyleAndColorLabRole {
    STYLE,
    COLOR_LAB
}

internal data class ColorLabPanelRenderModel(
    val title: String,
    val colorAxis: Float,
    val toneAxis: Float,
    val strength: Float,
    val summary: String,
    val resetAction: PersistedSettingsAction.UpdateColorLabSpec,
    val hasUserAdjustments: Boolean = false
)

internal fun colorLabPanelRenderModel(
    state: SessionState,
    text: AppTextResolver
): ColorLabPanelRenderModel {
    val settings = state.settings.persisted
    val spec = settings.photo.colorLabSpec
    return ColorLabPanelRenderModel(
        title = text.colorLabEntry(),
        colorAxis = spec.colorAxis,
        toneAxis = spec.toneAxis,
        strength = spec.strength,
        summary = text.colorToneSummary(spec.colorAxis, spec.toneAxis),
        resetAction = PersistedSettingsAction.UpdateColorLabSpec(ColorLabSpec()),
        hasUserAdjustments = settings.hasUserAdjustments(ResetTarget.COLOR_LAB)
    )
}

internal data class FilterLabPageRenderModel(
    val headline: String,
    val supportingText: String,
    val heroSummary: String,
    val currentFilterSummary: String,
    val rosterText: String,
    val editingEnabled: Boolean,
    val editingHint: String,
    val panelRole: StyleAndColorLabRole = StyleAndColorLabRole.STYLE,
    val showFamilyTabs: Boolean = true,
    val showFilterItems: Boolean = true,
    val showAdjustmentPanel: Boolean = true,
    val showAdvancedControls: Boolean = true,
    val showModeToggle: Boolean = true,
    val styleStrength: Float = 1f,
    val updateStyleStrengthAction: PersistedSettingsAction? = null,
    val photoTab: FilterLabTabRenderModel,
    val humanisticTab: FilterLabTabRenderModel,
    val portraitTab: FilterLabTabRenderModel,
    val videoTab: FilterLabTabRenderModel,
    val filterItems: List<FilterLabFilterItemRenderModel>,
    val adjustControl: FilterLabAdjustRenderModel,
    val adjustmentPanel: FilterAdjustmentPanelRenderModel,
    val cycleControl: SettingsControlRenderModel,
    val saveCustomControl: FilterLabSaveCustomRenderModel,
    val footer: String,
    val hasStyleUserAdjustments: Boolean = false,
    val resetStyleAction: PersistedSettingsAction.ResetToDefaults? = null
)

internal data class FilterLabSaveCustomRenderModel(
    val buttonLabel: String,
    val family: FilterLabFamily,
    val sourceProfileId: String?,
    val isEnabled: Boolean
)


internal fun sessionDiagnosticsText(
    state: SessionState,
    traceEvents: List<SessionTraceEvent>,
    resourceDiagnostics: ResourceDiagnosticsSnapshot? = null
): String {
    val debugDump = buildSessionDebugDump(state, traceEvents, resourceDiagnostics = resourceDiagnostics)
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
        debugDump.resourceDiagnostics?.let { res ->
            val degradations = res.featureDegradations.entries.joinToString(", ") { "${it.key}=${it.value}" }
            appendLine(
                "Resource: thermal=${res.thermalState.tagValue} | class=${res.performanceClass.tagValue} | " +
                    "memory=${res.memoryBudgetBytes / 1024 / 1024}MB | " +
                    "jobs=${res.activeAlgorithmJobs}/${res.maxConcurrentAlgorithmJobs}" +
                    if (degradations.isNotEmpty()) " | degradations=$degradations" else ""
            )
        }
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
    )?.localizedLabel(text) ?: settings.photo.defaultWatermarkTemplateId
    val videoFilterLabel = catalog.filterProfileOrNull(settings.video.defaultFilterProfileId)?.label
        ?: settings.video.defaultFilterProfileId
    val videoSpec = settings.video.defaultVideoSpec
    return SessionSettingsRenderModel(
        commonSummary = buildString {
            append(text.settingsJoinerGrid())
            append(settings.common.gridMode.label)
            append(text.settingsJoinerShutterSound())
            append(text.onOff(settings.common.shutterSoundEnabled))
            append(text.settingsJoinerSelfieMirror())
            append(text.onOff(settings.common.selfieMirrorEnabled))
        },
        photoSummary = buildString {
            append(text.settingsJoinerFilter())
            append(photoFilterLabel)
            append(text.settingsJoinerPortrait())
            append(settings.photo.portraitProfile.label)
            append(text.settingsJoinerWatermark())
            append(watermarkTemplateLabel)
            append(text.settingsJoinerLive())
            append(text.onOff(settings.photo.livePhotoEnabledByDefault))
            append(" (")
            append(settings.photo.liveSaveFormat.label)
            append(")")
            append(text.settingsJoinerTimer())
            append(settings.photo.countdownDuration.label)
        },
        videoSummary = buildString {
            append(videoSpec.summaryLabel)
            append(text.settingsJoinerMic())
            append(videoSpec.audioProfile.label)
            append(" | ")
            append(
                if (videoSpec.dynamicFpsPolicy == DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS) {
                    text.lowLightAuto24fps()
                } else {
                    text.lockedFps()
                }
            )
            append(" | ")
            append(text.settingsJoinerFilter())
            append(videoFilterLabel)
        },
        catalogSummary = buildString {
            append(catalog.filterProfiles.size)
            append(" ")
            append(text.settingsSummaryFilters())
            append(" | ")
            append(catalog.watermarkTemplates.size)
            append(" ")
            append(text.settingsSummaryWatermarkTemplates())
            append(" | Live ")
            append(catalog.liveMediaBundleDraft.motionDurationMillis)
            append(" ")
            append(text.settingsSummaryMsBundle())
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
        headline = text.settingsEntry(),
        supportingText = text.settingsPageSupporting(),
        heroSummary = "",
        editingEnabled = editingEnabled,
        editingHint = if (editingEnabled) {
            text.lensLabEditingEnabled()
        } else {
            text.lensLabEditingDisabled()
        },
        commonSection = CommonSettingsSectionRenderModel(
            summary = "",
            languageControl = SettingsControlRenderModel(
                label = text.languageLabel(),
                value = text.languageDisplayName(settings),
                availability = SettingsControlAvailability.SUPPORTED,
                availabilityLabel = text.availabilityLabel(SettingsControlAvailability.SUPPORTED),
                supportLabel = text.languageRestartHint(),
                nextAction = PersistedSettingsAction.UpdateAppLanguage(
                    when (settings.common.appLanguage) {
                        com.opencamera.core.settings.AppLanguage.ZH -> com.opencamera.core.settings.AppLanguage.EN
                        com.opencamera.core.settings.AppLanguage.EN -> com.opencamera.core.settings.AppLanguage.ZH
                    }
                )
            ),
            gridMode = SettingsControlRenderModel(
                label = text.compositionGridLabel(),
                value = settings.common.gridMode.label,
                availability = SettingsControlAvailability.SUPPORTED,
                availabilityLabel = text.availabilityLabel(SettingsControlAvailability.SUPPORTED),
                supportLabel = text.gridSupportLabel(CompositionGridMode.entries.size),
                nextAction = PersistedSettingsAction.UpdateGridMode(
                    nextListValue(settings.common.gridMode, CompositionGridMode.entries.toList())
                )
            ),
            shutterSound = SettingsControlRenderModel(
                label = text.shutterToneLabel(),
                value = onOffLabel(settings.common.shutterSoundEnabled, text),
                nextAction = PersistedSettingsAction.UpdateShutterSoundEnabled(
                    !settings.common.shutterSoundEnabled
                )
            ),
            selfieMirror = SettingsControlRenderModel(
                label = text.selfieMirrorLabel(),
                value = onOffLabel(settings.common.selfieMirrorEnabled, text),
                nextAction = PersistedSettingsAction.UpdateSelfieMirrorEnabled(
                    !settings.common.selfieMirrorEnabled
                )
            )
        ),
        photoSection = PhotoSettingsSectionRenderModel(
            summary = "",
            defaultFilter = SettingsControlRenderModel(
                label = text.defaultPhotoFilterLabel(),
                value = photoFilterLabel(settings.photo.defaultFilterProfileId, photoFilters),
                availability = if (supportsStillCapture && photoFilters.isNotEmpty()) {
                    SettingsControlAvailability.SUPPORTED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                },
                availabilityLabel = text.availabilityLabel(if (supportsStillCapture && photoFilters.isNotEmpty()) {
                    SettingsControlAvailability.SUPPORTED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                }),
                supportLabel = if (photoFilters.isNotEmpty()) {
                    text.looksCount(photoFilters.size)
                } else {
                    text.noCompatibleFilters()
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
                label = text.portraitLabSettingLabel(),
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
                availabilityLabel = text.availabilityLabel(if (supportsStillCapture) {
                    SettingsControlAvailability.DEGRADED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                }),
                supportLabel = if (supportsStillCapture) {
                    text.portraitTuningLabel()
                } else {
                    text.stillCaptureUnavailable()
                },
                nextAction = null
            ),
            watermarkTemplate = SettingsControlRenderModel(
                label = text.watermarkLabSettingLabel(),
                value = watermarkTemplateLabel(
                    settings.photo.defaultWatermarkTemplateId,
                    watermarkTemplates,
                    text
                ),
                availability = if (supportsStillCapture && watermarkTemplates.isNotEmpty()) {
                    SettingsControlAvailability.DEGRADED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                },
                availabilityLabel = text.availabilityLabel(if (supportsStillCapture && watermarkTemplates.isNotEmpty()) {
                    SettingsControlAvailability.DEGRADED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                }),
                supportLabel = if (supportsStillCapture && watermarkTemplates.isNotEmpty()) {
                    text.watermarkTuningLabel(watermarkTemplates.size)
                } else if (!supportsStillCapture) {
                    text.stillCaptureUnavailable()
                } else {
                    text.noWatermarkTemplates()
                },
                nextAction = null
            ),
            livePhoto = SettingsControlRenderModel(
                label = text.livePhotoDefaultLabel(),
                value = onOffLabel(settings.photo.livePhotoEnabledByDefault, text),
                availability = if (supportsStillCapture) {
                    SettingsControlAvailability.DEGRADED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                },
                availabilityLabel = text.availabilityLabel(if (supportsStillCapture) {
                    SettingsControlAvailability.DEGRADED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                }),
                supportLabel = if (supportsStillCapture) {
                    text.liveSupportLabel(catalog.liveMediaBundleDraft.motionDurationMillis.toInt(), catalog.liveMediaBundleDraft.watermarkMotionBehavior.label)
                } else {
                    text.stillCaptureUnavailable()
                },
                nextAction = if (supportsStillCapture) {
                    PersistedSettingsAction.UpdateLivePhotoDefault(
                        !settings.photo.livePhotoEnabledByDefault
                    )
                } else {
                    null
                }
            ),
            liveSaveFormat = SettingsControlRenderModel(
                label = text.liveSaveFormatLabel(),
                value = settings.photo.liveSaveFormat.label,
                availability = if (supportsStillCapture) {
                    SettingsControlAvailability.SUPPORTED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                },
                availabilityLabel = text.availabilityLabel(if (supportsStillCapture) {
                    SettingsControlAvailability.SUPPORTED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                }),
                supportLabel = if (supportsStillCapture) {
                    text.liveSaveFormatSupportLabel(LiveSaveFormat.entries.size)
                } else {
                    text.stillCaptureUnavailable()
                },
                nextAction = if (supportsStillCapture) {
                    PersistedSettingsAction.UpdateLiveSaveFormat(
                        nextListValue(settings.photo.liveSaveFormat, LiveSaveFormat.entries.toList())
                    )
                } else {
                    null
                }
            ),
            countdown = SettingsControlRenderModel(
                label = text.countdownLabel(),
                value = settings.photo.countdownDuration.label,
                availability = if (supportsStillCapture) {
                    SettingsControlAvailability.SUPPORTED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                },
                availabilityLabel = text.availabilityLabel(if (supportsStillCapture) {
                    SettingsControlAvailability.SUPPORTED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                }),
                supportLabel = if (supportsStillCapture) {
                    text.presetsCount(catalog.countdownOptions.size)
                } else {
                    text.stillCaptureUnavailable()
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
            summary = "",
            resolution = SettingsControlRenderModel(
                label = text.resolutionLabel(),
                value = selectedVideoSpec.resolution.label,
                availability = when {
                    !supportsVideoRecording -> SettingsControlAvailability.UNSUPPORTED
                    resolvedVideoSelection.resolutionDegraded ->
                        SettingsControlAvailability.DEGRADED
                    else -> SettingsControlAvailability.SUPPORTED
                },
                availabilityLabel = text.availabilityLabel(when {
                    !supportsVideoRecording -> SettingsControlAvailability.UNSUPPORTED
                    resolvedVideoSelection.resolutionDegraded ->
                        SettingsControlAvailability.DEGRADED
                    else -> SettingsControlAvailability.SUPPORTED
                }),
                supportLabel = when {
                    !supportsVideoRecording -> text.videoRecordingUnavailable()
                    resolvedVideoSelection.resolutionDegraded ->
                        text.degradedResolutionLabel(selectedVideoSpec.resolution.label, activeVideoSpec.resolution.label)
                    else -> text.supportCount(videoConstraints.resolutions.size)
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
                label = text.frameRateLabel(),
                value = selectedVideoSpec.frameRate.label,
                availability = when {
                    !supportsVideoRecording -> SettingsControlAvailability.UNSUPPORTED
                    resolvedVideoSelection.frameRateDegraded ->
                        SettingsControlAvailability.DEGRADED
                    else -> SettingsControlAvailability.SUPPORTED
                },
                availabilityLabel = text.availabilityLabel(when {
                    !supportsVideoRecording -> SettingsControlAvailability.UNSUPPORTED
                    resolvedVideoSelection.frameRateDegraded ->
                        SettingsControlAvailability.DEGRADED
                    else -> SettingsControlAvailability.SUPPORTED
                }),
                supportLabel = when {
                    !supportsVideoRecording -> text.videoRecordingUnavailable()
                    resolvedVideoSelection.frameRateDegraded ->
                        text.degradedFramerateLabel(selectedVideoSpec.frameRate.label, activeVideoSpec.frameRate.label)
                    else -> text.supportCount(videoConstraints.frameRatesFor(activeVideoSpec.resolution).size)
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
                label = text.dynamicFpsLabel(),
                value = selectedVideoSpec.dynamicFpsPolicy.label,
                availability = when {
                    !supportsVideoRecording -> SettingsControlAvailability.UNSUPPORTED
                    resolvedVideoSelection.dynamicPolicyDegraded ->
                        SettingsControlAvailability.DEGRADED
                    else -> SettingsControlAvailability.SUPPORTED
                },
                availabilityLabel = text.availabilityLabel(when {
                    !supportsVideoRecording -> SettingsControlAvailability.UNSUPPORTED
                    resolvedVideoSelection.dynamicPolicyDegraded ->
                        SettingsControlAvailability.DEGRADED
                    else -> SettingsControlAvailability.SUPPORTED
                }),
                supportLabel = when {
                    !supportsVideoRecording -> text.videoRecordingUnavailable()
                    resolvedVideoSelection.dynamicPolicyDegraded ->
                        text.degradedDynamicFpsLabel(selectedVideoSpec.dynamicFpsPolicy.label, activeVideoSpec.dynamicFpsPolicy.label)
                    else ->
                        text.supportCount(videoConstraints.dynamicPolicies.size)
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
                label = text.audioSceneLabel(),
                value = selectedVideoSpec.audioProfile.label,
                availability = when {
                    !supportsVideoRecording || !supportsAudioRecording ->
                        SettingsControlAvailability.UNSUPPORTED
                    resolvedVideoSelection.audioProfileDegraded ->
                        SettingsControlAvailability.DEGRADED
                    else -> SettingsControlAvailability.SUPPORTED
                },
                availabilityLabel = text.availabilityLabel(when {
                    !supportsVideoRecording || !supportsAudioRecording ->
                        SettingsControlAvailability.UNSUPPORTED
                    resolvedVideoSelection.audioProfileDegraded ->
                        SettingsControlAvailability.DEGRADED
                    else -> SettingsControlAvailability.SUPPORTED
                }),
                supportLabel = when {
                    !supportsVideoRecording -> text.videoRecordingUnavailable()
                    !supportsAudioRecording -> text.microphoneUnavailable()
                    resolvedVideoSelection.audioProfileDegraded ->
                        text.degradedAudioLabel(selectedVideoSpec.audioProfile.label, activeVideoSpec.audioProfile.label)
                    else ->
                        text.supportCount(videoConstraints.audioProfiles.size)
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
                label = text.videoFilterSeedLabel(),
                value = videoFilterLabel(settings.video.defaultFilterProfileId, videoFilters),
                availability = if (supportsVideoRecording) {
                    SettingsControlAvailability.DEGRADED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                },
                availabilityLabel = text.availabilityLabel(if (supportsVideoRecording) {
                    SettingsControlAvailability.DEGRADED
                } else {
                    SettingsControlAvailability.UNSUPPORTED
                }),
                supportLabel = if (supportsVideoRecording) {
                    text.videoFilterSeedCountLabel(videoFilters.size)
                } else {
                    text.videoRecordingUnavailable()
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
        catalogFooter = "",
        hasSettingsUserAdjustments = settings.hasUserAdjustments(ResetTarget.SETTINGS),
        resetSettingsAction = PersistedSettingsAction.ResetToDefaults(ResetTarget.SETTINGS)
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
    val runtimeSupportLabel = manualSupportSummary(text, manualCapabilities)
    return RuntimeProControlsRenderModel(
        isVisible = isVisible,
        headline = when (state.activeMode) {
            ModeId.NIGHT -> text.proControlsScenery()
            ModeId.PORTRAIT -> text.proControlsPortrait()
            ModeId.HUMANISTIC -> text.proControlsHumanistic()
            ModeId.PRO -> text.proControlsDefault()
            else -> text.proControlsDefault()
        },
        supportingText = if (hasAppliedManualControls) {
            text.proControlsSupportingEditable()
        } else {
            text.proControlsSupportingReadonly()
        },
        summary = buildString {
            append(draft.compactSummary())
            append(" | ")
            append(runtimeSupportLabel)
            if (!editingEnabled) {
                append(" ")
                append(text.proControlsFinishCaptureHint())
            }
        },
        rawControl = FeatureCatalogControlRenderModel(
            label = text.rawLabel(),
            value = if (manualCapabilities.raw == ManualControlSupport.SAVED_ONLY) {
                text.rawSavedOnlyValue()
            } else {
                onOffLabel(draft.rawEnabled, text)
            },
            availability = manualCapabilities.raw.toSettingsAvailability(),
            availabilityLabel = text.availabilityLabel(manualCapabilities.raw.toSettingsAvailability()),
            supportLabel = manualCapabilities.raw.manualSupportLabel(text),
            nextAction = FeatureCatalogAction.UpdateManualRawEnabled(!draft.rawEnabled)
                .takeIf { isVisible && editingEnabled }
        ),
        isoControl = FeatureCatalogControlRenderModel(
            label = text.isoLabel(),
            value = draft.iso?.toString() ?: text.autoLabel(),
            availability = manualCapabilities.iso.toSettingsAvailability(),
            availabilityLabel = text.availabilityLabel(manualCapabilities.iso.toSettingsAvailability()),
            supportLabel = manualCapabilities.iso.manualSupportLabel(text),
            nextAction = nextListValueOrNull(draft.iso, MANUAL_ISO_OPTIONS)
                ?.let(FeatureCatalogAction::UpdateManualIso)
                ?.takeIf { isVisible && editingEnabled }
        ),
        shutterControl = FeatureCatalogControlRenderModel(
            label = text.shutterLabel(),
            value = draft.shutterSpeedMillis?.let { "${it}ms" } ?: text.autoLabel(),
            availability = manualCapabilities.shutter.toSettingsAvailability(),
            availabilityLabel = text.availabilityLabel(manualCapabilities.shutter.toSettingsAvailability()),
            supportLabel = manualCapabilities.shutter.manualSupportLabel(text),
            nextAction = nextListValueOrNull(
                draft.shutterSpeedMillis,
                MANUAL_SHUTTER_OPTIONS
            )?.let(FeatureCatalogAction::UpdateManualShutterSpeedMillis)
                ?.takeIf { isVisible && editingEnabled }
        ),
        exposureControl = FeatureCatalogControlRenderModel(
            label = text.evLabel(),
            value = draft.exposureCompensationSteps?.let(::manualEvLabel) ?: text.autoLabel(),
            availability = manualCapabilities.exposureCompensation.toSettingsAvailability(),
            availabilityLabel = text.availabilityLabel(manualCapabilities.exposureCompensation.toSettingsAvailability()),
            supportLabel = manualCapabilities.exposureCompensation.manualSupportLabel(text),
            nextAction = nextListValueOrNull(
                draft.exposureCompensationSteps,
                MANUAL_EXPOSURE_OPTIONS
            )?.let(FeatureCatalogAction::UpdateManualExposureCompensationSteps)
                ?.takeIf { isVisible && editingEnabled }
        ),
        focusControl = FeatureCatalogControlRenderModel(
            label = text.focusLabel(),
            value = draft.focusDistanceDiopters?.let { String.format(Locale.US, "%.1fD", it) }
                ?: text.autoLabel(),
            availability = manualCapabilities.focusDistance.toSettingsAvailability(),
            availabilityLabel = text.availabilityLabel(manualCapabilities.focusDistance.toSettingsAvailability()),
            supportLabel = manualCapabilities.focusDistance.manualSupportLabel(text),
            nextAction = nextListValueOrNull(
                draft.focusDistanceDiopters,
                MANUAL_FOCUS_OPTIONS
            )?.let(FeatureCatalogAction::UpdateManualFocusDistanceDiopters)
                ?.takeIf { isVisible && editingEnabled }
        ),
        apertureControl = FeatureCatalogControlRenderModel(
            label = text.apertureLabel(),
            value = draft.apertureFNumber?.let { "f/${manualOneDecimal(it)}" } ?: text.autoLabel(),
            availability = manualCapabilities.aperture.toSettingsAvailability(),
            availabilityLabel = text.availabilityLabel(manualCapabilities.aperture.toSettingsAvailability()),
            supportLabel = manualCapabilities.aperture.manualSupportLabel(text),
            nextAction = nextListValueOrNull(
                draft.apertureFNumber,
                MANUAL_APERTURE_OPTIONS
            )?.let(FeatureCatalogAction::UpdateManualApertureFNumber)
                ?.takeIf { isVisible && editingEnabled }
        ),
        whiteBalanceControl = FeatureCatalogControlRenderModel(
            label = text.wbLabel(),
            value = draft.whiteBalanceKelvin?.let { "${it}K" } ?: text.autoLabel(),
            availability = manualCapabilities.whiteBalance.toSettingsAvailability(),
            availabilityLabel = text.availabilityLabel(manualCapabilities.whiteBalance.toSettingsAvailability()),
            supportLabel = manualCapabilities.whiteBalance.manualSupportLabel(text),
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

private fun ManualControlSupport.manualSupportLabel(text: AppTextResolver): String {
    return when (this) {
        ManualControlSupport.APPLY -> text.camera2Interop()
        ManualControlSupport.SAVED_ONLY -> text.savedOnly()
        ManualControlSupport.UNSUPPORTED -> text.temporarilyUnsupported()
    }
}

private fun manualSupportSummary(
    text: AppTextResolver,
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
            append(text.manualAdapterApplies())
            append(" ")
            append(applied.joinToString(separator = " / "))
        }
        if (savedOnly.isNotEmpty()) {
            if (isNotEmpty()) {
                append(" | ")
            }
            append(savedOnly.joinToString(separator = " / "))
            append(" ")
            append(text.manualStaySavedOnly())
        }
        if (unsupported.isNotEmpty()) {
            if (isNotEmpty()) {
                append(" | ")
            }
            append(unsupported.joinToString(separator = " / "))
            append(" ")
            append(text.manualTempUnsupportedSuffix())
        }
        if (isEmpty()) {
            append(text.manualControlsUnavailable())
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
        headline = text.portraitLab(),
        supportingText = text.portraitLabSupporting(),
        heroSummary = "",
        editingEnabled = editingEnabled,
        editingHint = if (editingEnabled) {
            text.portraitLabEditingEnabled()
        } else {
            text.portraitLabEditingDisabled()
        },
        profileControl = SettingsControlRenderModel(
            label = text.portraitProfileLabel(),
            value = settings.photo.portraitProfile.label,
            availability = availability,
            availabilityLabel = text.availabilityLabel(availability),
            supportLabel = if (supportsStillCapture) {
                text.productProfilesCount(PortraitProfile.entries.size)
            } else {
                text.stillCaptureUnavailable()
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
            label = text.beautyPresetLabel(),
            value = settings.photo.portraitBeautyPreset.label,
            availability = availability,
            availabilityLabel = text.availabilityLabel(availability),
            supportLabel = if (supportsStillCapture) {
                text.plansCount(PortraitBeautyPreset.entries.size)
            } else {
                text.stillCaptureUnavailable()
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
            label = text.beautyStrengthLabel(),
            value = settings.photo.portraitBeautyStrength.label,
            availability = availability,
            availabilityLabel = text.availabilityLabel(availability),
            supportLabel = if (supportsStillCapture) {
                text.levelsCount(PortraitBeautyStrength.entries.size)
            } else {
                text.stillCaptureUnavailable()
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
            label = text.bokehEffectLabel(),
            value = settings.photo.portraitBokehEffect.label,
            availability = availability,
            availabilityLabel = text.availabilityLabel(availability),
            supportLabel = if (supportsStillCapture) {
                text.renderingFeelsCount(PortraitBokehEffect.entries.size)
            } else {
                text.stillCaptureUnavailable()
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
        depthStrength = settings.photo.portraitDepthStrength,
        depthStrengthLabel = "${settings.photo.portraitDepthStrength}%",
        updateDepthStrengthAction = if (supportsStillCapture && editingEnabled) {
            PersistedSettingsAction.UpdatePortraitDepthStrength(
                settings.photo.portraitDepthStrength
            )
        } else {
            null
        },
        footer = text.portraitLabFooter()
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
    return WatermarkLabSelectorRenderModel(
        headline = text.watermarkLab(),
        supportingText = text.watermarkSelectorSupporting(),
        heroSummary = "",
        editingEnabled = editingEnabled,
        editingHint = if (editingEnabled) {
            text.watermarkSelectorEditingEnabled()
        } else {
            text.watermarkSelectorEditingDisabled()
        },
        items = catalog.watermarkTemplates.map { template ->
            val style = settings.photo.watermarkStyleFor(template.id)
            val isSelected = template.id == settings.photo.defaultWatermarkTemplateId
            val templateLabel = template.localizedLabel(text)
            WatermarkLabTemplateItemRenderModel(
                templateId = template.id,
                title = templateLabel,
                supportingText = buildString {
                    append(template.kindLabel(text))
                    append(" | ")
                    append(text.tokensLabel())
                    append(" ")
                    append(template.tokenKeys.prettyWatermarkTokens(text))
                    append(" | ")
                    append(text.watermarkAttrPlacementPrefix())
                    append(text.watermarkPlacementLabel(style.textPlacement))
                    append(" | ")
                    append(text.watermarkAttrScalePrefix())
                    append(text.watermarkTextScaleLabel(style.textScale))
                    append(" | ")
                    append(text.watermarkAttrOpacityPrefix())
                    append(text.watermarkTextOpacityLabel(style.textOpacity))
                    if (template.supportsFrameBorder) {
                        append(" | ")
                        append(text.watermarkAttrBackgroundPrefix())
                        append(text.watermarkFrameBackgroundLabel(style.frameBackground))
                    }
                    if (isSelected) {
                        append(text.watermarkSelectorCurrentDefault())
                    }
                },
                isSelected = isSelected,
                useAction = if (supportsStillCapture && !isSelected) {
                    PersistedSettingsAction.UpdatePhotoWatermarkTemplate(template.id)
                } else {
                    null
                },
                editButtonLabel = if (supportsStillCapture) {
                    text.watermarkStylePageShort()
                } else {
                    null
                }
            )
        },
        footer = if (supportsStillCapture) {
            text.watermarkSelectorFooterSupported()
        } else {
            text.watermarkSelectorFooterUnsupported()
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
    val allowedPlacements = template.allowedPlacements
        .takeIf { it.isNotEmpty() }
        ?: WatermarkTextPlacement.entries.toSet()
    val allowedBackgrounds = template.allowedFrameBackgrounds
        .takeIf { it.isNotEmpty() }
        ?: WatermarkFrameBackground.entries.toSet()
    val controlAvailability = if (supportsStillCapture) {
        SettingsControlAvailability.SUPPORTED
        } else {
            SettingsControlAvailability.UNSUPPORTED
        }
    return WatermarkLabDetailRenderModel(
        headline = template.localizedLabel(text),
        supportingText = if (template.id == settings.photo.defaultWatermarkTemplateId) {
            text.watermarkDetailSupportingSelected()
        } else {
            text.watermarkDetailSupportingNotSelected()
        },
        heroSummary = "",
        templateId = template.id,
        editingEnabled = editingEnabled,
        editingHint = if (editingEnabled) {
            text.watermarkDetailEditingEnabled()
        } else {
            text.watermarkDetailEditingDisabled()
        },
        placementControl = SettingsControlRenderModel(
            label = text.textPlacementLabel(),
            value = text.watermarkPlacementLabel(style.textPlacement),
            availability = controlAvailability,
            availabilityLabel = text.availabilityLabel(controlAvailability),
            supportLabel = if (supportsStillCapture) {
                text.placementsCount(allowedPlacements.size)
            } else {
                text.stillCaptureUnavailable()
            },
            nextAction = if (supportsStillCapture) {
                PersistedSettingsAction.UpdateWatermarkTextPlacement(
                    templateId = template.id,
                    placement = nextListValue(style.textPlacement, allowedPlacements.toList())
                )
            } else {
                null
            }
        ),
        textScaleControl = SettingsControlRenderModel(
            label = text.textScaleLabel(),
            value = text.watermarkTextScaleLabel(style.textScale),
            availability = controlAvailability,
            availabilityLabel = text.availabilityLabel(controlAvailability),
            supportLabel = if (supportsStillCapture) {
                text.stepsCount(WatermarkTextScale.entries.size)
            } else {
                text.stillCaptureUnavailable()
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
            label = text.textOpacityLabel(),
            value = text.watermarkTextOpacityLabel(style.textOpacity),
            availability = controlAvailability,
            availabilityLabel = text.availabilityLabel(controlAvailability),
            supportLabel = if (supportsStillCapture) {
                text.stepsCount(WatermarkTextOpacity.entries.size)
            } else {
                text.stillCaptureUnavailable()
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
                label = text.frameBackgroundLabel(),
                value = text.watermarkFrameBackgroundLabel(style.frameBackground),
                availability = controlAvailability,
                supportLabel = if (supportsStillCapture) {
                    text.moodsCount(allowedBackgrounds.size)
                } else {
                    text.stillCaptureUnavailable()
                },
                nextAction = if (supportsStillCapture) {
                    PersistedSettingsAction.UpdateWatermarkFrameBackground(
                        templateId = template.id,
                        background = nextListValue(
                            style.frameBackground,
                            allowedBackgrounds.toList()
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
            append(text.watermarkDetailTokensPrefix())
            append(template.tokenKeys.prettyWatermarkTokens(text))
            append(". ")
            append(
                when {
                    template.id == "pure-text" -> text.watermarkDetailFooterPureText()
                    template.supportsFrameBorder -> text.watermarkDetailFooterFrame()
                    else -> text.watermarkDetailFooterOverlay()
                }
            )
        }
    )
}

internal fun filterLabPageRenderModel(
    state: SessionState,
    text: AppTextResolver,
    selectedFamily: FilterLabFamily = defaultFilterLabFamily(state.activeMode),
    panelRole: StyleAndColorLabRole = StyleAndColorLabRole.STYLE,
    showAdjustmentPanel: Boolean = true,
    adjustmentMode: FilterAdjustmentMode = FilterAdjustmentMode.LIGHT
): FilterLabPageRenderModel {
    val settings = state.settings.persisted
    val editingEnabled = state.activeShot == null && state.countdownRemainingSeconds == null
    val family = filterLabFamilyState(
        state = state,
        text = text,
        selectedFamily = selectedFamily
    )
    val isColorLab = panelRole == StyleAndColorLabRole.COLOR_LAB
    val colorLabModel = colorLabPanelRenderModel(state, text)
    val colorLabRenderSpec = FilterRenderSpec().applyColorLab(settings.photo.colorLabSpec)
    val currentProfile = family.filters.firstOrNull { profile -> profile.id == family.currentFilterId }
    val currentFilterLabel = currentProfile.localizedLabel(family.currentFilterId, text)
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
        headline = when (panelRole) {
            StyleAndColorLabRole.STYLE -> text.stylePanelTitle()
            StyleAndColorLabRole.COLOR_LAB -> text.colorLabPanelTitle()
        },
        supportingText = text.filterLabSupportingText(),
        heroSummary = "",
        currentFilterSummary = if (isColorLab) "" else text.filterLabCurrentDefault(currentFilterLabel),
        rosterText = if (isColorLab) {
            ""
        } else {
            family.filters.joinToString(separator = "\n") { profile ->
                val marker = if (profile.id == family.currentFilterId) "•" else "·"
                val customBadge = if (profile.builtIn) "" else text.statusCustomBadge()
                "$marker ${profile.localizedLabel(text)}${customBadge}"
            }
        },
        editingEnabled = editingEnabled,
        editingHint = if (isColorLab) {
            ""
        } else if (editingEnabled) {
            text.filterLabEditingEnabled()
        } else {
            text.filterLabEditingDisabled()
        },
        panelRole = panelRole,
        showFamilyTabs = panelRole == StyleAndColorLabRole.STYLE,
        showFilterItems = panelRole == StyleAndColorLabRole.STYLE,
        showAdjustmentPanel = panelRole == StyleAndColorLabRole.COLOR_LAB,
        showAdvancedControls = false,
        showModeToggle = panelRole == StyleAndColorLabRole.STYLE,
        photoTab = filterLabTabRenderModel(
            family = FilterLabFamily.PHOTO,
            familyLabel = text.filterFamilyPhoto(),
            selectedFamily = family.family
        ),
        humanisticTab = filterLabTabRenderModel(
            family = FilterLabFamily.HUMANISTIC,
            familyLabel = text.filterFamilyHumanistic(),
            selectedFamily = family.family
        ),
        portraitTab = filterLabTabRenderModel(
            family = FilterLabFamily.PORTRAIT,
            familyLabel = text.filterFamilyPortrait(),
            selectedFamily = family.family
        ),
        videoTab = filterLabTabRenderModel(
            family = FilterLabFamily.VIDEO,
            familyLabel = text.filterFamilyVideo(),
            selectedFamily = family.family
        ),
        adjustControl = FilterLabAdjustRenderModel(
            buttonLabel = currentFilterLabel,
            family = family.family,
            sourceProfileId = currentProfile?.id,
            isEnabled = editingEnabled && family.supported && currentProfile != null,
            willCreateCustomCopy = currentProfile?.builtIn == true
        ),
        filterItems = if (isColorLab) {
            emptyList()
        } else {
            family.filters.map { profile ->
                val isSelected = profile.id == family.currentFilterId
                val profileLabel = profile.localizedLabel(text)
                FilterLabFilterItemRenderModel(
                    filterProfileId = profile.id,
                    title = profileLabel,
                    supportingText = buildString {
                        append(family.label)
                        if (!profile.builtIn) {
                            append(text.statusCustomBadge())
                        }
                        if (isSelected) {
                            append(text.filterLabSelectedDefault())
                        }
                    },
                    isSelected = isSelected,
                    nextAction = if (isSelected) {
                        null
                    } else {
                        family.updateAction(profile.id)
                    },
                    adjustButtonLabel = null
                )
            }
        },
        adjustmentPanel = FilterAdjustmentPanelRenderModel(
            isVisible = if (isColorLab) showAdjustmentPanel else showAdjustmentPanel && currentProfile != null && family.supported,
            mode = adjustmentMode,
            selectedProfileId = if (isColorLab) null else currentProfile?.id,
            selectedProfileLabel = if (isColorLab) colorLabModel.summary else currentFilterLabel,
            renderSpec = if (isColorLab) colorLabRenderSpec else currentRenderSpec,
            modeToggleLabel = if (adjustmentMode == FilterAdjustmentMode.LIGHT) {
                text.switchToAdvanced()
            } else {
                text.switchToLight()
            },
            lightPalette = FilterLightPaletteRenderModel(
                summary = if (isColorLab) "" else currentRenderSpec.lightPaletteSummary(text),
                supportingText = if (isColorLab) {
                    text.filterLabLightPaletteHint()
                } else if (currentProfile?.builtIn == true) {
                    text.filterLabDragToCreateCustom()
                } else {
                    text.filterLabLightPaletteHint()
                }
            ),
            advancedControls = FilterAdvancedControl.entries.map { control ->
                FilterAdvancedControlRenderModel(
                    control = control,
                    buttonLabel = buildString {
                        append(text.filterCtrlLabel(control))
                        append('\n')
                        append(currentRenderSpec.levelLabel(control, text))
                        append('\n')
                        append(text.tapToCycleLabel())
                    }
                )
            },
            needsAutoPrepare = currentProfile?.builtIn == true
        ),
        cycleControl = SettingsControlRenderModel(
            label = text.filterLabNextLook(family.label),
            value = currentFilterLabel,
            availability = if (family.supported && family.filters.isNotEmpty()) {
                SettingsControlAvailability.SUPPORTED
            } else {
                SettingsControlAvailability.UNSUPPORTED
            },
            availabilityLabel = text.availabilityLabel(if (family.supported && family.filters.isNotEmpty()) {
                SettingsControlAvailability.SUPPORTED
            } else {
                SettingsControlAvailability.UNSUPPORTED
            }),
            supportLabel = when {
                !family.supported -> family.unsupportedReason
                family.filters.isEmpty() -> text.noCompatibleLooks()
                else -> text.filterLabLooksDeferred(family.filters.size)
            },
            nextAction = cycleAction
        ),
        saveCustomControl = FilterLabSaveCustomRenderModel(
            buttonLabel = if (isColorLab) "" else buildString {
                append(text.saveAsCustom())
                append('\n')
                append(currentFilterLabel)
                append('\n')
                append(
                    when {
                        currentProfile == null -> text.filterLabSaveCustomUnavailableProfile()
                        !currentProfile.builtIn -> text.filterLabSaveCustomAlreadyCustom()
                        else -> text.filterLabSaveCustomReady(family.label)
                    }
                )
            },
            family = family.family,
            sourceProfileId = if (isColorLab) null else currentProfile?.id,
            isEnabled = !isColorLab && currentProfile?.builtIn == true
        ),
        styleStrength = settings.photo.styleStrength,
        updateStyleStrengthAction = PersistedSettingsAction.UpdatePhotoStyleStrength(
            settings.photo.styleStrength.coerceIn(0f, 1f)
        ),
        footer = if (isColorLab) "" else text.filterLabFooter(),
        hasStyleUserAdjustments = !isColorLab && settings.hasUserAdjustments(ResetTarget.STYLE),
        resetStyleAction = if (!isColorLab && settings.hasUserAdjustments(ResetTarget.STYLE)) {
            PersistedSettingsAction.ResetToDefaults(ResetTarget.STYLE)
        } else {
            null
        }
    )
}

private fun onOffLabel(enabled: Boolean, text: AppTextResolver): String = text.onOff(enabled)

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
    templates: List<WatermarkTemplate>,
    text: AppTextResolver
): String {
    return templates.firstOrNull { it.id == templateId }?.localizedLabel(text) ?: templateId
}

internal fun WatermarkTemplate.localizedLabel(text: AppTextResolver): String {
    return when (id) {
        "classic-overlay" -> text.watermarkTemplateClassicOverlay()
        "travel-polaroid" -> text.watermarkTemplateTravelPolaroid()
        "retro-frame" -> text.watermarkTemplateRetroFrame()
        "pure-text" -> text.watermarkTemplatePureText()
        "blur-four-border" -> text.watermarkTemplateBlurFourBorder()
        "professional-bottom-bar" -> text.watermarkTemplateProfessionalBottomBar()
        else -> label
    }
}

private fun WatermarkTemplate.kindLabel(text: AppTextResolver): String {
    return when (id) {
        "pure-text" -> text.watermarkTemplatePureText()
        "blur-four-border" -> text.watermarkTemplateBlurFourBorder()
        else -> if (supportsFrameBorder) {
            text.watermarkTemplateExpandedFrame()
        } else {
            text.watermarkTemplateClassicOverlay()
        }
    }
}

private fun FilterProfile?.localizedLabel(fallback: String, text: AppTextResolver): String {
    return this?.localizedLabel(text) ?: fallback
}

private fun FilterProfile.localizedLabel(text: AppTextResolver): String {
    return text.filterProfileLabel(id, label)
}

private fun Set<String>.prettyWatermarkTokens(text: AppTextResolver): String {
    return sorted().joinToString(separator = ", ") { token ->
        when (token) {
            "camera-params" -> text.watermarkTokenCameraParams()
            "datetime" -> text.watermarkTokenDateTime()
            "location" -> text.watermarkTokenLocation()
            "model" -> text.watermarkTokenModel()
            else -> token.replace('-', ' ').replaceFirstChar(Char::titlecase)
        }
    }
}

internal fun defaultFilterLabFamily(activeMode: ModeId): FilterLabFamily {
    return when (activeMode) {
        ModeId.HUMANISTIC -> FilterLabFamily.HUMANISTIC
        ModeId.PORTRAIT -> FilterLabFamily.PORTRAIT
        ModeId.VIDEO -> FilterLabFamily.VIDEO
        ModeId.PHOTO,
        ModeId.DOCUMENT,
        ModeId.NIGHT,
        ModeId.PRO,
        ModeId.FULL_CLEAR -> FilterLabFamily.PHOTO
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
    "capture.photo", "capture.saved", "capture.timing",
    "capture.shutter.to.device",
    "recording.started", "recording.saved", "recording.timing",
    "recording.startup.latency",
    "mode.switch.completed", "lens.switch.completed",
    "permissions.updated", "device.capabilities.updated", "settings.updated"
)

private val CORE_EVENT_NAMES = setOf(
    "preview.binding.started", "preview.recovery.started", "preview.recovery.requested",
    "preview.stopped", "preview.snapshot.updated", "preview.snapshot.ignored",
    "capture.countdown.started", "capture.countdown.tick", "capture.countdown.completed",
    "capture.saving",
    "capture.feedback.snapshot.requested", "capture.feedback.snapshot.updated",
    "capture.feedback.snapshot.skipped",
    "recording.requested",
    "shot.plan.failed",
    "mode.switch.started", "lens.switch.started",
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

private fun escapeLinkValue(value: String): String {
    return value.replace(" ", "_").replace("=", "_")
}

private fun formatTimestamp(timestampMillis: Long): String {
    if (timestampMillis <= 0) return "??:??:??"
    val seconds = (timestampMillis / 1000) % 60
    val minutes = (timestampMillis / 60_000) % 60
    val hours = (timestampMillis / 3_600_000) % 24
    val millis = timestampMillis % 1000
    return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
}

internal fun devLogRenderModel(
    state: SessionState,
    traceEvents: List<SessionTraceEvent>,
    isDebugBuild: Boolean,
    selectedTab: DevLogTab,
    text: AppTextResolver,
    resourceDiagnostics: ResourceDiagnosticsSnapshot? = null,
    storageSummary: StorageSummary? = null,
    selectedDomain: TraceEventDomain? = null,
    linkEvents: List<com.opencamera.core.session.PerformanceLinkEvent> = emptyList(),
    deviceProbeSummary: String? = null,
    clearCutoffs: DevLogClearCutoffs = DevLogClearCutoffs()
): DevLogRenderModel {
    if (!isDebugBuild) {
        return DevLogRenderModel(
            isAvailable = false,
            selectedTab = selectedTab,
            title = text.devEntry(),
            summaryText = "",
            content = "",
            exportContent = ""
        )
    }

    val keyEvents = traceEvents.filter {
        it.sequence > clearCutoffs.sequenceFor(DevLogTab.KEY) && it.name in KEY_EVENT_NAMES
    }
    val coreEvents = traceEvents.filter {
        it.sequence > clearCutoffs.sequenceFor(DevLogTab.CORE) && it.name in CORE_EVENT_NAMES
    }
    val errorEvents = traceEvents.filter {
        it.sequence > clearCutoffs.sequenceFor(DevLogTab.ERROR) && isErrorEvent(it.name)
    }
    val allEvents = traceEvents.filter { it.sequence > clearCutoffs.sequenceFor(DevLogTab.ALL) }
    val visibleLinkEvents = linkEvents.drop(clearCutoffs.linkEventCount.coerceAtMost(linkEvents.size))

    val domainTabs = TraceEventDomain.entries.map { domain ->
        DomainTabCount(domain = domain, count = allEvents.count { it.domain == domain })
    }.filter { it.count > 0 }

    fun escapeLinkValue(value: String): String {
        return value.replace(" ", "_").replace("=", "_")
    }

    fun formatTimestamp(timestampMillis: Long): String {
        if (timestampMillis <= 0) return "??:??:??"
        val seconds = (timestampMillis / 1000) % 60
        val minutes = (timestampMillis / 60_000) % 60
        val hours = (timestampMillis / 3_600_000) % 24
        val millis = timestampMillis % 1000
        return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    }

    fun formatEvents(events: List<SessionTraceEvent>): String {
        return events.joinToString("\n") { event ->
            val timeStr = formatTimestamp(event.timestampMillis)
            "[$timeStr] [${event.domain.label}] ${event.sequence}. ${event.name} -> ${event.detail}"
        }
    }

    fun formatLinkEvents(events: List<com.opencamera.core.session.PerformanceLinkEvent>): String {
        return events.joinToString("\n") { event ->
            val parts = mutableListOf(
                "[Link] flow=${escapeLinkValue(event.flow)}",
                "stage=${escapeLinkValue(event.stage)}",
                "status=${event.status.label}"
            )
            if (event.correlationId.isNotBlank()) {
                parts += "id=${escapeLinkValue(event.correlationId)}"
            }
            if (event.startElapsedMillis >= 0) {
                parts += "start=${event.startElapsedMillis}"
            }
            if (event.endElapsedMillis != null) {
                parts += "end=${event.endElapsedMillis}"
            }
            if (event.durationMillis != null) {
                parts += "duration=${event.durationMillis}ms"
            }
            if (!event.detail.isNullOrBlank()) {
                val detail = event.detail!!
                parts += "detail=${escapeLinkValue(detail)}"
            }
            parts += "source=${escapeLinkValue(event.source)}"
            parts.joinToString(" ")
        }
    }

    val debugDump = buildSessionDebugDump(state, allEvents, resourceDiagnostics = resourceDiagnostics)
    val perf = debugDump.perfSnapshot
    val recovery = debugDump.recoveryTrace
    val resolvedProbeSummary = deviceProbeSummary
        ?: computeDeviceProbeSummary(state.activeDeviceCapabilities)

    val coreSummary = buildString {
        appendLine("DebugDump: ${debugDump.lifecycle} | ${debugDump.activeMode.name} | preview=${debugDump.previewStatus} | capture=${debugDump.captureStatus} | recording=${debugDump.recordingStatus}")
        appendLine("PerfSnapshot: last=${perf.lastFirstFrameLatencyMillis ?: "--"} ms, best=${perf.bestFirstFrameLatencyMillis ?: "--"} ms, worst=${perf.worstFirstFrameLatencyMillis ?: "--"} ms, binds=${perf.bindCount}, recoveries=${perf.recoveryCount}")
        appendLine("RecoveryTrace: ${if (recovery.isRecoveryActive) "active" else "idle"}, last=${recovery.lastRecoveryReason ?: "--"}, recoveredFrame=${recovery.recoveredFirstFrameLatencyMillis ?: "--"} ms, failure=${recovery.lastFailureReason ?: "--"}")
        debugDump.resourceDiagnostics?.let { res ->
            appendLine("Resource: thermal=${res.thermalState.tagValue} | class=${res.performanceClass.tagValue} | jobs=${res.activeAlgorithmJobs}/${res.maxConcurrentAlgorithmJobs}")
        }
    }

    val domainFiltered = if (selectedDomain != null) {
        when (selectedTab) {
            DevLogTab.KEY -> keyEvents.filter { it.domain == selectedDomain }
            DevLogTab.CORE -> coreEvents.filter { it.domain == selectedDomain }
            DevLogTab.ERROR -> errorEvents.filter { it.domain == selectedDomain }
            DevLogTab.ALL -> allEvents.filter { it.domain == selectedDomain }
        }
    } else {
        null
    }

    val tabContent = if (domainFiltered != null) {
        formatEvents(domainFiltered)
    } else {
        val baseContent = when (selectedTab) {
            DevLogTab.KEY -> formatEvents(keyEvents)
            DevLogTab.CORE -> formatEvents(coreEvents)
            DevLogTab.ERROR -> formatEvents(errorEvents)
            DevLogTab.ALL -> formatEvents(allEvents)
        }
        if (selectedTab == DevLogTab.CORE && visibleLinkEvents.isNotEmpty()) {
            buildString {
                if (baseContent.isNotBlank()) {
                    appendLine(baseContent)
                    appendLine()
                }
                appendLine("--- 链路耗时 ---")
                append(formatLinkEvents(visibleLinkEvents))
            }
        } else {
            baseContent
        }
    }

    val exportContent = buildString {
        appendLine("=== KEY EVENTS ===")
        appendLine(formatEvents(keyEvents))
        appendLine("=== CORE EVENTS ===")
        appendLine(formatEvents(coreEvents))
        appendLine("=== LINK EVENTS ===")
        appendLine(formatLinkEvents(visibleLinkEvents))
        appendLine("=== ERROR EVENTS ===")
        appendLine(formatEvents(errorEvents))
        appendLine("=== ALL EVENTS ===")
        appendLine(formatEvents(allEvents))
        debugDump.resourceDiagnostics?.let { res ->
            appendLine("=== RESOURCE DIAGNOSTICS ===")
            res.pipelineNotes.forEach { note -> appendLine(note) }
        }
        if (!resolvedProbeSummary.isNullOrBlank()) {
            appendLine("=== DEVICE PROBE ===")
            appendLine(resolvedProbeSummary)
        }
        appendLine("=== CORE SUMMARY ===")
        append(coreSummary)
    }

    val lastTiming = traceEvents.lastOrNull { it.name.endsWith(".timing") }
    val lastIssue = errorEvents.lastOrNull()
    val summaryText = buildString {
        append("状态: ${debugDump.previewStatus} | ")
        append("模式: ${debugDump.activeMode.name} | ")
        append("拍摄: ${debugDump.captureStatus} | ")
        append("录制: ${debugDump.recordingStatus}")
        if (lastTiming != null) {
            append(" | 最后耗时: ${lastTiming.detail}")
        }
        if (lastIssue != null) {
            append(" | 最近问题: ${lastIssue.name}")
        }
    }

    val domainFilteredCount = domainFiltered?.size
    return DevLogRenderModel(
        isAvailable = true,
        selectedTab = selectedTab,
        title = when (selectedTab) {
            DevLogTab.KEY -> text.devLogTitleKey(domainFilteredCount ?: keyEvents.size)
            DevLogTab.CORE -> text.devLogTitleCore(domainFilteredCount ?: coreEvents.size)
            DevLogTab.ERROR -> text.devLogTitleError(domainFilteredCount ?: errorEvents.size)
            DevLogTab.ALL -> text.devLogTitleAll(domainFilteredCount ?: allEvents.size)
        },
        summaryText = summaryText,
        content = tabContent,
        exportContent = exportContent,
        storageUsedDisplay = storageSummary?.usedDisplay ?: "",
        storageCapacityDisplay = storageSummary?.capacityDisplay ?: "",
        storageUsageRatio = storageSummary?.usageRatio ?: 0f,
        canCleanup = (storageSummary?.usedBytes ?: 0L) > 0L,
        domainTabs = domainTabs,
        selectedDomain = selectedDomain
    )
}

internal fun computeDeviceProbeSummary(capabilities: com.opencamera.core.device.DeviceCapabilities): String {
    val camCount = capabilities.availableLensFacings.size
    val lensNodes = capabilities.zoomRatioCapability.lensNodeMap
    val outputSizes = capabilities.availableStillCaptureOutputSizes
    val zoomRatios = capabilities.zoomRatioCapability.normalizedSupportedRatios
    return buildString {
        appendLine("cameras: $camCount | lens-facings: ${capabilities.availableLensFacings.joinToString { it.name }}")
        if (lensNodes.isNotEmpty()) {
            appendLine("lens-nodes: ${lensNodes.entries.joinToString { (node, avail) -> "${node.label}(id=${avail.physicalCameraId ?: "?"},threshold=${avail.thresholdRatio})" }}")
        }
        appendLine("zoom: ratios=${zoomRatios.joinToString()},support=${capabilities.zoomRatioCapability.support.label}")
        if (outputSizes.isNotEmpty()) {
            val totalPixels = outputSizes.sumOf { it.pixelCount }
            appendLine("still-output: ${outputSizes.size} sizes, total-pixels=${totalPixels}, largest=${outputSizes.first().width}x${outputSizes.first().height}")
            appendLine("still-output-sizes: ${outputSizes.joinToString { it.probeSummaryLabel() }}")
        } else {
            appendLine("still-output: none reported")
        }
        capabilities.stillCaptureCameraProbes.forEach { probe ->
            val physicalIds = probe.physicalCameraIds.joinToString("|").ifBlank { "none" }
            appendLine(
                "still-camera-probe: id=${probe.cameraId},lens=${probe.lensFacing?.name ?: "?"}," +
                    "physical=$physicalIds,sizes=${probe.outputSizes.probeSummaryList()}"
            )
            probe.physicalOutputProbes.forEach { physicalProbe ->
                appendLine(
                    "physical-still-probe: parent=${probe.cameraId},id=${physicalProbe.cameraId}," +
                        "sizes=${physicalProbe.outputSizes.probeSummaryList()}"
                )
            }
        }
        val facts = mutableListOf<String>()
        if (!capabilities.supportsStillCapture) facts += "stillCapture=UNSUPPORTED"
        if (!capabilities.supportsVideoRecording) facts += "videoRecording=UNSUPPORTED"
        if (!capabilities.supportsAudioRecording) facts += "audio=UNSUPPORTED"
        if (!capabilities.supportsFlashControl) facts += "flash=DEGRADED"
        if (!capabilities.supportsManualControls) facts += "manualControls=UNSUPPORTED"
        if (!capabilities.supportsNightMultiFrame) facts += "nightMultiFrame=DEGRADED"
        if (!capabilities.supportsPortraitDepthEffect) facts += "portraitDepth=DEGRADED"
        if (facts.isNotEmpty()) {
            appendLine("facts: ${facts.joinToString()}")
        }
    }.trimEnd()
}

private fun List<com.opencamera.core.device.StillCaptureOutputSize>.probeSummaryList(): String {
    return if (isEmpty()) {
        "none"
    } else {
        joinToString { it.probeSummaryLabel() }
    }
}

private fun com.opencamera.core.device.StillCaptureOutputSize.probeSummaryLabel(): String {
    val megapixels = kotlin.math.round(pixelCount / 1_000_000.0).toInt()
    return "${megapixels}MP:${width}x${height}(${resolutionSource.tagValue})"
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
    text: AppTextResolver,
    selectedFamily: FilterLabFamily
): FilterLabFamilyState {
    val settings = state.settings.persisted
    val catalog = state.settings.catalog
    return when (selectedFamily) {
        FilterLabFamily.PHOTO -> FilterLabFamilyState(
            family = selectedFamily,
            label = text.filterFamilyPhoto(),
            currentFilterId = settings.photo.defaultFilterProfileId,
            filters = catalog.photoSettingsFilterProfiles(),
            supported = state.activeDeviceCapabilities.supportsStillCapture,
            unsupportedReason = text.stillCaptureUnavailable(),
            updateAction = PersistedSettingsAction::UpdatePhotoFilter
        )

        FilterLabFamily.HUMANISTIC -> FilterLabFamilyState(
            family = selectedFamily,
            label = text.filterFamilyHumanistic(),
            currentFilterId = catalog.humanisticStyleSubitems().let { filters ->
                settings.photo.defaultHumanisticFilterProfileId.takeIf { current ->
                    filters.any { profile -> profile.id == current }
                } ?: filters.firstOrNull()?.id ?: settings.photo.defaultHumanisticFilterProfileId
            },
            filters = catalog.humanisticStyleSubitems(),
            supported = state.activeDeviceCapabilities.supportsStillCapture,
            unsupportedReason = text.stillCaptureUnavailable(),
            updateAction = PersistedSettingsAction::UpdateHumanisticFilter
        )

        FilterLabFamily.PORTRAIT -> FilterLabFamilyState(
            family = selectedFamily,
            label = text.filterFamilyPortrait(),
            currentFilterId = settings.photo.defaultPortraitFilterProfileId,
            filters = catalog.filterProfilesFor(FilterProfileCategory.PORTRAIT, includeCustom = true),
            supported = state.activeDeviceCapabilities.supportsStillCapture,
            unsupportedReason = text.stillCaptureUnavailable(),
            updateAction = PersistedSettingsAction::UpdatePortraitFilter
        )

        FilterLabFamily.VIDEO -> FilterLabFamilyState(
            family = selectedFamily,
            label = text.filterFamilyVideo(),
            currentFilterId = settings.video.defaultFilterProfileId,
            filters = catalog.videoSettingsFilterProfiles(),
            supported = state.activeDeviceCapabilities.supportsVideoRecording,
            unsupportedReason = text.videoRecordingUnavailable(),
            updateAction = PersistedSettingsAction::UpdateVideoFilter
        )
    }
}

private fun com.opencamera.core.settings.FeatureCatalog.humanisticStyleSubitems(): List<FilterProfile> {
    val primaryIds = setOf("humanistic-street", "humanistic-portrait", "humanistic-life")
    return filterProfiles.filter { profile ->
        profile.category == FilterProfileCategory.HUMANISTIC &&
            (profile.id in primaryIds || !profile.builtIn)
    }
}

private fun filterLabTabRenderModel(
    family: FilterLabFamily,
    familyLabel: String,
    selectedFamily: FilterLabFamily
): FilterLabTabRenderModel {
    val label = familyLabel
    return FilterLabTabRenderModel(
        family = family,
        label = label,
        isSelected = family == selectedFamily
    )
}

private fun com.opencamera.core.settings.FeatureCatalog.watermarkTemplateOrNull(
    templateId: String?
): WatermarkTemplate? {
    return watermarkTemplates.firstOrNull { template -> template.id == templateId }
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
    return applyColorLab(
        com.opencamera.core.settings.ColorLabSpec(
            colorAxis = colorAxis,
            toneAxis = toneAxis
        )
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
    val floatValue: Float,
    val brightnessValue: Int
) {
    OFF(0f, 0),
    LOW(0.10f, 6),
    MEDIUM(0.20f, 12),
    HIGH(0.30f, 18)
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

private fun FilterRenderSpec.levelLabel(control: FilterAdvancedControl, text: AppTextResolver): String {
    return when (control) {
        FilterAdvancedControl.TEMPERATURE_SHIFT -> warmthShiftLevel(warmthShift).labelForTemperature(text)
        FilterAdvancedControl.TINT_SHIFT -> tintShiftLevel(tintShift).labelForTint(text)
        else -> currentLevel(control).resolveLabel(text)
    }
}

private fun FilterAdjustmentLevel.resolveLabel(text: AppTextResolver): String = when (this) {
    FilterAdjustmentLevel.OFF -> text.filterAdjustmentOff()
    FilterAdjustmentLevel.LOW -> text.filterAdjustmentLow()
    FilterAdjustmentLevel.MEDIUM -> text.filterAdjustmentMedium()
    FilterAdjustmentLevel.HIGH -> text.filterAdjustmentHigh()
}

private fun FilterRenderSpec.lightPaletteSummary(text: AppTextResolver): String {
    return buildString {
        append(text.filterLightPaletteColor())
        append(" ")
        append(
            when {
                tintShift >= 4 -> text.colorMagentaWarm()
                tintShift <= -4 -> text.colorGreenCool()
                warmthShift > 3 || saturation > 1.08f -> text.levelWarmPlus()
                warmthShift < -3 || saturation < 0.96f -> text.colorCoolMuted()
                else -> text.colorNeutral()
            }
        )
        append(" | ")
        append(text.filterLightPaletteTone())
        append(" ")
        append(
            when {
                brightnessShift >= 8 || contrast <= 0.96f -> text.toneSoftLift()
                brightnessShift <= -4 || contrast >= 1.12f -> text.toneDeepContrast()
                else -> text.toneBalanced()
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

private fun FilterSignedAdjustmentLevel.labelForTemperature(text: AppTextResolver): String {
    return when (this) {
        FilterSignedAdjustmentLevel.OFF -> text.filterSignedOff()
        FilterSignedAdjustmentLevel.LOW_POSITIVE -> text.filterSignedWarm()
        FilterSignedAdjustmentLevel.HIGH_POSITIVE -> text.filterSignedWarmPlus()
        FilterSignedAdjustmentLevel.LOW_NEGATIVE -> text.filterSignedCool()
        FilterSignedAdjustmentLevel.HIGH_NEGATIVE -> text.filterSignedCoolPlus()
    }
}

private fun FilterSignedAdjustmentLevel.labelForTint(text: AppTextResolver): String {
    return when (this) {
        FilterSignedAdjustmentLevel.OFF -> text.filterSignedOff()
        FilterSignedAdjustmentLevel.LOW_POSITIVE -> text.filterSignedMagenta()
        FilterSignedAdjustmentLevel.HIGH_POSITIVE -> text.filterSignedMagentaPlus()
        FilterSignedAdjustmentLevel.LOW_NEGATIVE -> text.filterSignedGreen()
        FilterSignedAdjustmentLevel.HIGH_NEGATIVE -> text.filterSignedGreenPlus()
    }
}

private fun nearestBrightnessLevel(value: Int): FilterAdjustmentLevel {
    return FilterAdjustmentLevel.entries.minBy { level ->
        kotlin.math.abs(level.brightnessValue - value)
    }
}

internal data class DocumentBatchOrganizerRenderModel(
    val visible: Boolean,
    val title: String,
    val countText: String,
    val items: List<DocumentBatchOrganizerItemRenderModel>
)

internal data class DocumentBatchOrganizerItemRenderModel(
    val itemId: String,
    val pageNumber: Int,
    val renderUri: String?,
    val cropStatusLabel: String?,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean
)

internal fun documentBatchOrganizerRenderModel(
    state: SessionState,
    text: AppTextResolver
): DocumentBatchOrganizerRenderModel {
    val batch = state.presentation.documentBatch
    val isDocumentMode = state.activeMode == ModeId.DOCUMENT
    val isActive = batch.status == DocumentBatchStatus.ACTIVE

    if (!isDocumentMode || !isActive) {
        return DocumentBatchOrganizerRenderModel(
            visible = false,
            title = text.documentBatchOrganizerTitle(),
            countText = "",
            items = emptyList()
        )
    }

    val items = batch.items.mapIndexed { index, item ->
        DocumentBatchOrganizerItemRenderModel(
            itemId = item.itemId,
            pageNumber = index + 1,
            renderUri = item.renderUri ?: item.outputPath,
            cropStatusLabel = item.cropStatus.toLabel(text),
            canMoveUp = index > 0,
            canMoveDown = index < batch.items.lastIndex
        )
    }

    return DocumentBatchOrganizerRenderModel(
        visible = true,
        title = text.documentBatchOrganizerTitle(),
        countText = text.documentBatchPageCount(items.size),
        items = items
    )
}

private fun DocumentBatchCropStatus.toLabel(text: AppTextResolver): String? {
    return when (this) {
        DocumentBatchCropStatus.NOT_REQUESTED -> null
        DocumentBatchCropStatus.APPLIED -> text.documentBatchCropApplied()
        DocumentBatchCropStatus.SKIPPED -> text.documentBatchCropSkipped()
        DocumentBatchCropStatus.FAILED -> text.documentBatchCropFailed()
    }
}

internal data class DocumentBatchRailRenderModel(
    val visible: Boolean,
    val countText: String,
    val items: List<DocumentBatchRailItemRenderModel>,
    val latestItemId: String?,
    val organizeEnabled: Boolean
)

internal data class DocumentBatchRailItemRenderModel(
    val itemId: String,
    val pageNumber: Int,
    val renderUri: String?,
    val statusLabel: String?,
    val isLatest: Boolean,
    val removeContentDescription: String
)

internal fun documentBatchRailRenderModel(
    state: SessionState,
    text: AppTextResolver
): DocumentBatchRailRenderModel {
    val batch = state.presentation.documentBatch
    val isDocumentMode = state.activeMode == ModeId.DOCUMENT
    val isActive = batch.status == DocumentBatchStatus.ACTIVE

    if (!isDocumentMode || !isActive) {
        return DocumentBatchRailRenderModel(
            visible = false,
            countText = "",
            items = emptyList(),
            latestItemId = null,
            organizeEnabled = false
        )
    }

    val sortedItems = batch.items.sortedBy { it.orderIndex }
    val items = sortedItems.mapIndexed { index, item ->
        DocumentBatchRailItemRenderModel(
            itemId = item.itemId,
            pageNumber = index + 1,
            renderUri = item.renderUri ?: item.outputPath,
            statusLabel = item.cropStatus.toLabel(text),
            isLatest = item.itemId == batch.latestItemId,
            removeContentDescription = text.documentBatchRemoveLabel()
        )
    }

    return DocumentBatchRailRenderModel(
        visible = items.isNotEmpty(),
        countText = text.documentBatchPageCount(items.size),
        items = items,
        latestItemId = batch.latestItemId,
        organizeEnabled = items.isNotEmpty()
    )
}
