package com.opencamera.app

import com.opencamera.app.i18n.AppTextResolver
import com.opencamera.core.device.resolveVideoSpec
import com.opencamera.core.session.SessionState
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.LiveSaveFormat
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.ResetTarget
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.WatermarkTemplate
import com.opencamera.core.settings.compactSummary
import com.opencamera.core.settings.hasUserAdjustments

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
    override val headline: String,
    override val supportingText: String,
    override val heroSummary: String,
    override val editingEnabled: Boolean,
    override val editingHint: String,
    val commonSection: CommonSettingsSectionRenderModel,
    val photoSection: PhotoSettingsSectionRenderModel,
    val videoSection: VideoSettingsSectionRenderModel,
    val catalogFooter: String,
    val hasSettingsUserAdjustments: Boolean = false,
    val resetSettingsAction: PersistedSettingsAction.ResetToDefaults? = null
) : EditableSettingsPageRenderModel

internal fun sessionSettingsRenderModel(
    state: SessionState,
    text: AppTextResolver
): SessionSettingsRenderModel {
    val settings = state.settings.persisted
    val catalog = state.settings.catalog
    val photoFilterLabel = catalog.filterProfileOrNull(settings.photo.defaultFilterProfileId)?.localizedLabel(text)
        ?: settings.photo.defaultFilterProfileId
    val watermarkTemplateLabel = catalog.watermarkTemplateOrNull(
        settings.photo.defaultWatermarkTemplateId
    )?.localizedLabel(text) ?: settings.photo.defaultWatermarkTemplateId
    val videoFilterLabel = catalog.filterProfileOrNull(settings.video.defaultFilterProfileId)?.localizedLabel(text)
        ?: settings.video.defaultFilterProfileId
    val videoSpec = settings.video.defaultVideoSpec
    return SessionSettingsRenderModel(
        commonSummary = buildString {
            append(text.get(R.string.settings_joiner_grid))
            append(text.gridModeLabel(settings.common.gridMode))
            append(text.get(R.string.settings_joiner_shutter_sound))
            append(text.onOff(settings.common.shutterSoundEnabled))
            append(text.get(R.string.settings_joiner_selfie_mirror))
            append(text.onOff(settings.common.selfieMirrorEnabled))
        },
        photoSummary = buildString {
            append(text.get(R.string.settings_joiner_filter))
            append(photoFilterLabel)
            append(text.get(R.string.settings_joiner_portrait))
            append(text.portraitProfileEnumLabel(settings.photo.portraitProfile))
            append(text.get(R.string.settings_joiner_watermark))
            append(watermarkTemplateLabel)
            append(text.get(R.string.settings_joiner_live))
            append(text.onOff(settings.photo.livePhotoEnabledByDefault))
            append(" (")
            append(text.liveSaveFormatValueLabel(settings.photo.liveSaveFormat))
            append(")")
            append(text.get(R.string.settings_joiner_timer))
            append(text.countdownValueLabel(settings.photo.countdownDuration))
        },
        videoSummary = buildString {
            append(videoSpec.summaryLabel)
            append(text.get(R.string.settings_joiner_mic))
            append(text.audioProfileLabel(videoSpec.audioProfile))
            append(" | ")
            append(
                if (videoSpec.dynamicFpsPolicy == DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS) {
                    text.get(R.string.video_low_light_auto)
                } else {
                    text.get(R.string.video_locked_fps)
                }
            )
            append(" | ")
            append(text.get(R.string.settings_joiner_filter))
            append(videoFilterLabel)
        },
        catalogSummary = buildString {
            append(catalog.filterProfiles.size)
            append(" ")
            append(text.get(R.string.settings_summary_filters))
            append(" | ")
            append(catalog.watermarkTemplates.size)
            append(" ")
            append(text.get(R.string.settings_summary_watermark_templates))
            append(" | ")
            append(text.get(R.string.button_quick_live))
            append(" ")
            append(catalog.liveMediaBundleDraft.motionDurationMillis)
            append(" ")
            append(text.get(R.string.settings_summary_ms_bundle))
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
    val editingEnabled = settingsPageEditingEnabled(state)
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
        headline = text.get(R.string.button_settings_entry),
        supportingText = text.get(R.string.settings_page_supporting),
        heroSummary = "",
        editingEnabled = editingEnabled,
        editingHint = if (editingEnabled) {
            text.get(R.string.lens_lab_editing_enabled)
        } else {
            text.get(R.string.lens_lab_editing_disabled)
        },
        commonSection = CommonSettingsSectionRenderModel(
            summary = "",
            languageControl = SettingsControlRenderModel(
                label = text.get(R.string.label_language_setting),
                value = text.languageDisplayName(settings),
                availability = SettingsControlAvailability.SUPPORTED,
                availabilityLabel = text.availabilityLabel(SettingsControlAvailability.SUPPORTED),
                supportLabel = text.get(R.string.label_language_restart_hint),
                nextAction = PersistedSettingsAction.UpdateAppLanguage(
                    when (settings.common.appLanguage) {
                        com.opencamera.core.settings.AppLanguage.ZH -> com.opencamera.core.settings.AppLanguage.EN
                        com.opencamera.core.settings.AppLanguage.EN -> com.opencamera.core.settings.AppLanguage.ZH
                    }
                )
            ),
            gridMode = SettingsControlRenderModel(
                label = text.get(R.string.label_composition_grid),
                value = text.gridModeLabel(settings.common.gridMode),
                availability = SettingsControlAvailability.SUPPORTED,
                availabilityLabel = text.availabilityLabel(SettingsControlAvailability.SUPPORTED),
                supportLabel = text.gridSupportLabel(CompositionGridMode.entries.size),
                nextAction = PersistedSettingsAction.UpdateGridMode(
                    nextListValue(settings.common.gridMode, CompositionGridMode.entries.toList())
                )
            ),
            shutterSound = SettingsControlRenderModel(
                label = text.get(R.string.label_shutter_tone),
                value = onOffLabel(settings.common.shutterSoundEnabled, text),
                availabilityLabel = text.availabilityLabel(SettingsControlAvailability.SUPPORTED),
                nextAction = PersistedSettingsAction.UpdateShutterSoundEnabled(
                    !settings.common.shutterSoundEnabled
                )
            ),
            selfieMirror = SettingsControlRenderModel(
                label = text.get(R.string.label_selfie_mirror),
                value = onOffLabel(settings.common.selfieMirrorEnabled, text),
                availabilityLabel = text.availabilityLabel(SettingsControlAvailability.SUPPORTED),
                nextAction = PersistedSettingsAction.UpdateSelfieMirrorEnabled(
                    !settings.common.selfieMirrorEnabled
                )
            )
        ),
        photoSection = PhotoSettingsSectionRenderModel(
            summary = "",
            defaultFilter = SettingsControlRenderModel(
                label = text.get(R.string.label_default_photo_filter),
                value = photoFilterLabel(settings.photo.defaultFilterProfileId, photoFilters, text),
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
                    text.get(R.string.error_no_compatible_filters)
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
                label = text.get(R.string.label_portrait_lab),
                value = buildString {
                    append(text.portraitProfileEnumLabel(settings.photo.portraitProfile))
                    append(" / ")
                    append(text.portraitBeautyPresetLabel(settings.photo.portraitBeautyPreset))
                    append(" / ")
                    append(text.portraitBokehEffectLabel(settings.photo.portraitBokehEffect))
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
                    text.get(R.string.settings_page_portrait_support)
                } else {
                    text.get(R.string.error_still_capture_unavailable)
                },
                nextAction = null
            ),
            watermarkTemplate = SettingsControlRenderModel(
                label = text.get(R.string.label_watermark_lab),
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
                    text.get(R.string.error_still_capture_unavailable)
                } else {
                    text.get(R.string.error_no_watermark_templates)
                },
                nextAction = null
            ),
            livePhoto = SettingsControlRenderModel(
                label = text.get(R.string.label_live_photo_default),
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
                    text.liveSupportLabel(catalog.liveMediaBundleDraft.motionDurationMillis.toInt(), text.liveWatermarkMotionBehaviorLabel(catalog.liveMediaBundleDraft.watermarkMotionBehavior))
                } else {
                    text.get(R.string.error_still_capture_unavailable)
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
                label = text.get(R.string.label_live_save_format),
                value = text.liveSaveFormatValueLabel(settings.photo.liveSaveFormat),
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
                    text.get(R.string.error_still_capture_unavailable)
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
                label = text.get(R.string.label_countdown),
                value = text.countdownValueLabel(settings.photo.countdownDuration),
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
                    text.get(R.string.error_still_capture_unavailable)
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
                label = text.get(R.string.label_resolution),
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
                    !supportsVideoRecording -> text.get(R.string.error_video_recording_unavailable)
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
                label = text.get(R.string.label_frame_rate),
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
                    !supportsVideoRecording -> text.get(R.string.error_video_recording_unavailable)
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
                label = text.get(R.string.label_dynamic_fps),
                value = text.dynamicFpsPolicyLabel(selectedVideoSpec.dynamicFpsPolicy),
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
                    !supportsVideoRecording -> text.get(R.string.error_video_recording_unavailable)
                    resolvedVideoSelection.dynamicPolicyDegraded ->
                        text.degradedDynamicFpsLabel(text.dynamicFpsPolicyLabel(selectedVideoSpec.dynamicFpsPolicy), text.dynamicFpsPolicyLabel(activeVideoSpec.dynamicFpsPolicy))
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
                label = text.get(R.string.label_audio_scene),
                value = text.audioProfileLabel(selectedVideoSpec.audioProfile),
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
                    !supportsVideoRecording -> text.get(R.string.error_video_recording_unavailable)
                    !supportsAudioRecording -> text.get(R.string.error_microphone_unavailable)
                    resolvedVideoSelection.audioProfileDegraded ->
                        text.degradedAudioLabel(text.audioProfileLabel(selectedVideoSpec.audioProfile), text.audioProfileLabel(activeVideoSpec.audioProfile))
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
                label = text.get(R.string.label_video_filter_seed),
                value = videoFilterLabel(settings.video.defaultFilterProfileId, videoFilters, text),
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
                    text.get(R.string.error_video_recording_unavailable)
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

private fun onOffLabel(enabled: Boolean, text: AppTextResolver): String = text.onOff(enabled)

private fun photoFilterLabel(
    filterId: String,
    filters: List<FilterProfile>,
    text: AppTextResolver
): String {
    val profile = filters.firstOrNull { it.id == filterId } ?: return filterId
    return profile.localizedLabel(text)
}

private fun videoFilterLabel(
    filterId: String,
    filters: List<FilterProfile>,
    text: AppTextResolver
): String {
    val profile = filters.firstOrNull { it.id == filterId } ?: return filterId
    return profile.localizedLabel(text)
}

private fun watermarkTemplateLabel(
    templateId: String,
    templates: List<WatermarkTemplate>,
    text: AppTextResolver
): String {
    return templates.firstOrNull { it.id == templateId }?.localizedLabel(text) ?: templateId
}
