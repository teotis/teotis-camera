package com.opencamera.core.settings

sealed interface PersistedSettingsAction {
    data class UpdateAppLanguage(val language: AppLanguage) : PersistedSettingsAction
    data class UpdateGridMode(val gridMode: CompositionGridMode) : PersistedSettingsAction
    data class UpdateShutterSoundEnabled(val enabled: Boolean) : PersistedSettingsAction
    data class UpdateSelfieMirrorEnabled(val enabled: Boolean) : PersistedSettingsAction
    data class UpdatePhotoFilter(val filterProfileId: String) : PersistedSettingsAction
    data class UpdateHumanisticFilter(val filterProfileId: String) : PersistedSettingsAction
    data class UpdatePortraitFilter(val filterProfileId: String) : PersistedSettingsAction
    data class UpdatePortraitProfile(val profile: PortraitProfile) : PersistedSettingsAction
    data class UpdatePortraitBeautyPreset(
        val preset: PortraitBeautyPreset
    ) : PersistedSettingsAction
    data class UpdatePortraitBeautyStrength(
        val strength: PortraitBeautyStrength
    ) : PersistedSettingsAction
    data class UpdatePortraitBokehEffect(
        val effect: PortraitBokehEffect
    ) : PersistedSettingsAction
    data class UpdatePhotoWatermarkTemplate(val templateId: String) : PersistedSettingsAction
    data class UpdateWatermarkTextPlacement(
        val templateId: String,
        val placement: WatermarkTextPlacement
    ) : PersistedSettingsAction
    data class UpdateWatermarkTextScale(
        val templateId: String,
        val scale: WatermarkTextScale
    ) : PersistedSettingsAction
    data class UpdateWatermarkTextOpacity(
        val templateId: String,
        val opacity: WatermarkTextOpacity
    ) : PersistedSettingsAction
    data class UpdateWatermarkFrameBackground(
        val templateId: String,
        val background: WatermarkFrameBackground
    ) : PersistedSettingsAction
    data class UpdateLivePhotoDefault(val enabled: Boolean) : PersistedSettingsAction
    data class UpdateLiveSaveFormat(val format: LiveSaveFormat) : PersistedSettingsAction
    data class UpdateCountdownDuration(val countdownDuration: CountdownDuration) :
        PersistedSettingsAction
    data class UpdateDefaultVideoSpec(val videoSpec: VideoSpec) : PersistedSettingsAction
    data class UpdateVideoFilter(val filterProfileId: String) : PersistedSettingsAction
    data class UpdateColorLabSpec(val spec: ColorLabSpec) : PersistedSettingsAction
    data class UpdatePhotoStyleStrength(val strength: Float) : PersistedSettingsAction
    data class UpdatePhotoLowLightNightAssistEnabled(val enabled: Boolean) : PersistedSettingsAction
}

fun PersistedSettings.reduce(action: PersistedSettingsAction): PersistedSettings {
    return when (action) {
        is PersistedSettingsAction.UpdateAppLanguage -> copy(
            common = common.copy(appLanguage = action.language)
        )
        is PersistedSettingsAction.UpdateGridMode -> copy(
            common = common.copy(gridMode = action.gridMode)
        )

        is PersistedSettingsAction.UpdateShutterSoundEnabled -> copy(
            common = common.copy(shutterSoundEnabled = action.enabled)
        )

        is PersistedSettingsAction.UpdateSelfieMirrorEnabled -> copy(
            common = common.copy(selfieMirrorEnabled = action.enabled)
        )

        is PersistedSettingsAction.UpdatePhotoFilter -> copy(
            photo = photo.copy(defaultFilterProfileId = action.filterProfileId)
        )

        is PersistedSettingsAction.UpdateHumanisticFilter -> copy(
            photo = photo.copy(defaultHumanisticFilterProfileId = action.filterProfileId)
        )

        is PersistedSettingsAction.UpdatePortraitFilter -> copy(
            photo = photo.copy(defaultPortraitFilterProfileId = action.filterProfileId)
        )

        is PersistedSettingsAction.UpdatePortraitProfile -> copy(
            photo = photo.copy(portraitProfile = action.profile)
        )

        is PersistedSettingsAction.UpdatePortraitBeautyPreset -> copy(
            photo = photo.copy(portraitBeautyPreset = action.preset)
        )

        is PersistedSettingsAction.UpdatePortraitBeautyStrength -> copy(
            photo = photo.copy(portraitBeautyStrength = action.strength)
        )

        is PersistedSettingsAction.UpdatePortraitBokehEffect -> copy(
            photo = photo.copy(portraitBokehEffect = action.effect)
        )

        is PersistedSettingsAction.UpdatePhotoWatermarkTemplate -> copy(
            photo = photo.copy(defaultWatermarkTemplateId = action.templateId)
        )

        is PersistedSettingsAction.UpdateWatermarkTextPlacement -> copy(
            photo = photo.updateWatermarkStyle(action.templateId) { style ->
                style.copy(textPlacement = action.placement)
            }
        )

        is PersistedSettingsAction.UpdateWatermarkTextScale -> copy(
            photo = photo.updateWatermarkStyle(action.templateId) { style ->
                style.copy(textScale = action.scale)
            }
        )

        is PersistedSettingsAction.UpdateWatermarkTextOpacity -> copy(
            photo = photo.updateWatermarkStyle(action.templateId) { style ->
                style.copy(textOpacity = action.opacity)
            }
        )

        is PersistedSettingsAction.UpdateWatermarkFrameBackground -> copy(
            photo = photo.updateWatermarkStyle(action.templateId) { style ->
                style.copy(frameBackground = action.background)
            }
        )

        is PersistedSettingsAction.UpdateLivePhotoDefault -> copy(
            photo = photo.copy(livePhotoEnabledByDefault = action.enabled)
        )

        is PersistedSettingsAction.UpdateLiveSaveFormat -> copy(
            photo = photo.copy(liveSaveFormat = action.format)
        )

        is PersistedSettingsAction.UpdateCountdownDuration -> copy(
            photo = photo.copy(countdownDuration = action.countdownDuration)
        )

        is PersistedSettingsAction.UpdateDefaultVideoSpec -> copy(
            video = video.copy(defaultVideoSpec = action.videoSpec)
        )

        is PersistedSettingsAction.UpdateVideoFilter -> copy(
            video = video.copy(defaultFilterProfileId = action.filterProfileId)
        )

        is PersistedSettingsAction.UpdateColorLabSpec -> copy(
            photo = photo.copy(colorLabSpec = action.spec.normalized())
        )

        is PersistedSettingsAction.UpdatePhotoStyleStrength -> copy(
            photo = photo.copy(styleStrength = action.strength.coerceIn(0f, 1f))
        )

        is PersistedSettingsAction.UpdatePhotoLowLightNightAssistEnabled -> copy(
            photo = photo.copy(lowLightNightAssistEnabled = action.enabled)
        )
    }
}

fun PhotoSettings.watermarkStyleFor(
    templateId: String
): WatermarkStyleSettings {
    return when (templateId) {
        "travel-polaroid" -> travelPolaroidWatermarkStyle
        "retro-frame" -> retroFrameWatermarkStyle
        "pure-text" -> pureTextWatermarkStyle
        "blur-four-border" -> blurFourBorderWatermarkStyle
        else -> classicOverlayWatermarkStyle
    }
}

fun LiveMediaBundle.liveWatermarkMetadataTags(prefix: String = "liveWatermark"): Map<String, String> {
    return linkedMapOf(
        "${prefix}Mode" to if (watermarkMotionBehavior.isDynamic) "dynamic" else "static",
        "${prefix}Behavior" to watermarkMotionBehavior.storageKey,
        "${prefix}BrightnessCoupling" to watermarkMotionBehavior.brightnessCouplingKey,
        "${prefix}OpacityCoupling" to watermarkMotionBehavior.opacityCouplingKey,
        "${prefix}MotionDurationMillis" to motionDurationMillis.toString()
    )
}

private fun PhotoSettings.updateWatermarkStyle(
    templateId: String,
    transform: (WatermarkStyleSettings) -> WatermarkStyleSettings
): PhotoSettings {
    return when (templateId) {
        "travel-polaroid" -> copy(
            travelPolaroidWatermarkStyle = transform(travelPolaroidWatermarkStyle)
        )
        "retro-frame" -> copy(
            retroFrameWatermarkStyle = transform(retroFrameWatermarkStyle)
        )
        "pure-text" -> copy(
            pureTextWatermarkStyle = transform(pureTextWatermarkStyle)
        )
        "blur-four-border" -> copy(
            blurFourBorderWatermarkStyle = transform(blurFourBorderWatermarkStyle)
        )
        else -> copy(
            classicOverlayWatermarkStyle = transform(classicOverlayWatermarkStyle)
        )
    }
}
