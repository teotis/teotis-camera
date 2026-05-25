package com.opencamera.core.settings

object PersistedSettingsSerializer {
    private const val KEY_GRID_MODE = "common.gridMode"
    private const val KEY_SHUTTER_SOUND_ENABLED = "common.shutterSoundEnabled"
    private const val KEY_SELFIE_MIRROR_ENABLED = "common.selfieMirrorEnabled"
    private const val KEY_PHOTO_FILTER = "photo.defaultFilterProfileId"
    private const val KEY_HUMANISTIC_FILTER = "photo.defaultHumanisticFilterProfileId"
    private const val KEY_PORTRAIT_FILTER = "photo.defaultPortraitFilterProfileId"
    private const val KEY_PORTRAIT_PROFILE = "photo.portrait.profile"
    private const val KEY_PORTRAIT_BEAUTY_PRESET = "photo.portrait.beautyPreset"
    private const val KEY_PORTRAIT_BEAUTY_STRENGTH = "photo.portrait.beautyStrength"
    private const val KEY_PORTRAIT_BOKEH_EFFECT = "photo.portrait.bokehEffect"
    private const val KEY_PORTRAIT_DEPTH_STRENGTH = "photo.portrait.depthStrength"
    private const val KEY_PHOTO_WATERMARK_TEMPLATE = "photo.defaultWatermarkTemplateId"
    private const val KEY_PHOTO_WATERMARK_CLASSIC_POSITION = "photo.watermark.classicOverlay.position"
    private const val KEY_PHOTO_WATERMARK_CLASSIC_SCALE = "photo.watermark.classicOverlay.scale"
    private const val KEY_PHOTO_WATERMARK_CLASSIC_OPACITY = "photo.watermark.classicOverlay.opacity"
    private const val KEY_PHOTO_WATERMARK_CLASSIC_BACKGROUND = "photo.watermark.classicOverlay.background"
    private const val KEY_PHOTO_WATERMARK_TRAVEL_POSITION = "photo.watermark.travelPolaroid.position"
    private const val KEY_PHOTO_WATERMARK_TRAVEL_SCALE = "photo.watermark.travelPolaroid.scale"
    private const val KEY_PHOTO_WATERMARK_TRAVEL_OPACITY = "photo.watermark.travelPolaroid.opacity"
    private const val KEY_PHOTO_WATERMARK_TRAVEL_BACKGROUND = "photo.watermark.travelPolaroid.background"
    private const val KEY_PHOTO_WATERMARK_RETRO_POSITION = "photo.watermark.retroFrame.position"
    private const val KEY_PHOTO_WATERMARK_RETRO_SCALE = "photo.watermark.retroFrame.scale"
    private const val KEY_PHOTO_WATERMARK_RETRO_OPACITY = "photo.watermark.retroFrame.opacity"
    private const val KEY_PHOTO_WATERMARK_RETRO_BACKGROUND = "photo.watermark.retroFrame.background"
    private const val KEY_PHOTO_WATERMARK_PURE_TEXT_POSITION = "photo.watermark.pureText.position"
    private const val KEY_PHOTO_WATERMARK_PURE_TEXT_SCALE = "photo.watermark.pureText.scale"
    private const val KEY_PHOTO_WATERMARK_PURE_TEXT_OPACITY = "photo.watermark.pureText.opacity"
    private const val KEY_PHOTO_WATERMARK_PURE_TEXT_BACKGROUND = "photo.watermark.pureText.background"
    private const val KEY_PHOTO_WATERMARK_BLUR_FOUR_BORDER_POSITION = "photo.watermark.blurFourBorder.position"
    private const val KEY_PHOTO_WATERMARK_BLUR_FOUR_BORDER_SCALE = "photo.watermark.blurFourBorder.scale"
    private const val KEY_PHOTO_WATERMARK_BLUR_FOUR_BORDER_OPACITY = "photo.watermark.blurFourBorder.opacity"
    private const val KEY_PHOTO_WATERMARK_BLUR_FOUR_BORDER_BACKGROUND = "photo.watermark.blurFourBorder.background"
    private const val KEY_PHOTO_WATERMARK_PROFESSIONAL_BOTTOM_BAR_POSITION = "photo.watermark.professionalBottomBar.position"
    private const val KEY_PHOTO_WATERMARK_PROFESSIONAL_BOTTOM_BAR_SCALE = "photo.watermark.professionalBottomBar.scale"
    private const val KEY_PHOTO_WATERMARK_PROFESSIONAL_BOTTOM_BAR_OPACITY = "photo.watermark.professionalBottomBar.opacity"
    private const val KEY_PHOTO_WATERMARK_PROFESSIONAL_BOTTOM_BAR_BACKGROUND = "photo.watermark.professionalBottomBar.background"
    private const val KEY_PHOTO_LIVE_DEFAULT = "photo.livePhotoEnabledByDefault"
    private const val KEY_PHOTO_LIVE_SAVE_FORMAT = "photo.live.saveFormat"
    private const val KEY_PHOTO_COUNTDOWN = "photo.countdownDuration"
    private const val KEY_VIDEO_FILTER = "video.defaultFilterProfileId"
    private const val KEY_VIDEO_RESOLUTION = "video.defaultVideoResolution"
    private const val KEY_VIDEO_FRAME_RATE = "video.defaultVideoFrameRate"
    private const val KEY_VIDEO_DYNAMIC_FPS_POLICY = "video.defaultVideoDynamicFpsPolicy"
    private const val KEY_VIDEO_AUDIO_PROFILE = "video.defaultVideoAudioProfile"
    private const val KEY_COLOR_LAB_COLOR_AXIS = "photo.colorLab.colorAxis"
    private const val KEY_COLOR_LAB_TONE_AXIS = "photo.colorLab.toneAxis"
    private const val KEY_COLOR_LAB_STRENGTH = "photo.colorLab.strength"
    private const val KEY_COLOR_LAB_PRESET_ID = "photo.colorLab.presetId"
    private const val KEY_PHOTO_STYLE_STRENGTH = "photo.styleStrength"
    private const val KEY_PHOTO_LOW_LIGHT_NIGHT_ASSIST =
        "photo.lowLightNightAssistEnabled"

    fun toMap(settings: PersistedSettings): Map<String, String> {
        return linkedMapOf(
            KEY_GRID_MODE to settings.common.gridMode.storageKey,
            KEY_SHUTTER_SOUND_ENABLED to settings.common.shutterSoundEnabled.toString(),
            KEY_SELFIE_MIRROR_ENABLED to settings.common.selfieMirrorEnabled.toString(),
            KEY_PHOTO_FILTER to settings.photo.defaultFilterProfileId,
            KEY_HUMANISTIC_FILTER to settings.photo.defaultHumanisticFilterProfileId,
            KEY_PORTRAIT_FILTER to settings.photo.defaultPortraitFilterProfileId,
            KEY_PORTRAIT_PROFILE to settings.photo.portraitProfile.storageKey,
            KEY_PORTRAIT_BEAUTY_PRESET to settings.photo.portraitBeautyPreset.storageKey,
            KEY_PORTRAIT_BEAUTY_STRENGTH to settings.photo.portraitBeautyStrength.storageKey,
            KEY_PORTRAIT_BOKEH_EFFECT to settings.photo.portraitBokehEffect.storageKey,
            KEY_PORTRAIT_DEPTH_STRENGTH to settings.photo.portraitDepthStrength.toString(),
            KEY_PHOTO_WATERMARK_TEMPLATE to settings.photo.defaultWatermarkTemplateId,
            KEY_PHOTO_WATERMARK_CLASSIC_POSITION to settings.photo.classicOverlayWatermarkStyle.textPlacement.storageKey,
            KEY_PHOTO_WATERMARK_CLASSIC_SCALE to settings.photo.classicOverlayWatermarkStyle.textScale.storageKey,
            KEY_PHOTO_WATERMARK_CLASSIC_OPACITY to settings.photo.classicOverlayWatermarkStyle.textOpacity.storageKey,
            KEY_PHOTO_WATERMARK_CLASSIC_BACKGROUND to settings.photo.classicOverlayWatermarkStyle.frameBackground.storageKey,
            KEY_PHOTO_WATERMARK_TRAVEL_POSITION to settings.photo.travelPolaroidWatermarkStyle.textPlacement.storageKey,
            KEY_PHOTO_WATERMARK_TRAVEL_SCALE to settings.photo.travelPolaroidWatermarkStyle.textScale.storageKey,
            KEY_PHOTO_WATERMARK_TRAVEL_OPACITY to settings.photo.travelPolaroidWatermarkStyle.textOpacity.storageKey,
            KEY_PHOTO_WATERMARK_TRAVEL_BACKGROUND to settings.photo.travelPolaroidWatermarkStyle.frameBackground.storageKey,
            KEY_PHOTO_WATERMARK_RETRO_POSITION to settings.photo.retroFrameWatermarkStyle.textPlacement.storageKey,
            KEY_PHOTO_WATERMARK_RETRO_SCALE to settings.photo.retroFrameWatermarkStyle.textScale.storageKey,
            KEY_PHOTO_WATERMARK_RETRO_OPACITY to settings.photo.retroFrameWatermarkStyle.textOpacity.storageKey,
            KEY_PHOTO_WATERMARK_RETRO_BACKGROUND to settings.photo.retroFrameWatermarkStyle.frameBackground.storageKey,
            KEY_PHOTO_WATERMARK_PURE_TEXT_POSITION to settings.photo.pureTextWatermarkStyle.textPlacement.storageKey,
            KEY_PHOTO_WATERMARK_PURE_TEXT_SCALE to settings.photo.pureTextWatermarkStyle.textScale.storageKey,
            KEY_PHOTO_WATERMARK_PURE_TEXT_OPACITY to settings.photo.pureTextWatermarkStyle.textOpacity.storageKey,
            KEY_PHOTO_WATERMARK_PURE_TEXT_BACKGROUND to settings.photo.pureTextWatermarkStyle.frameBackground.storageKey,
            KEY_PHOTO_WATERMARK_BLUR_FOUR_BORDER_POSITION to settings.photo.blurFourBorderWatermarkStyle.textPlacement.storageKey,
            KEY_PHOTO_WATERMARK_BLUR_FOUR_BORDER_SCALE to settings.photo.blurFourBorderWatermarkStyle.textScale.storageKey,
            KEY_PHOTO_WATERMARK_BLUR_FOUR_BORDER_OPACITY to settings.photo.blurFourBorderWatermarkStyle.textOpacity.storageKey,
            KEY_PHOTO_WATERMARK_BLUR_FOUR_BORDER_BACKGROUND to settings.photo.blurFourBorderWatermarkStyle.frameBackground.storageKey,
            KEY_PHOTO_WATERMARK_PROFESSIONAL_BOTTOM_BAR_POSITION to settings.photo.professionalBottomBarWatermarkStyle.textPlacement.storageKey,
            KEY_PHOTO_WATERMARK_PROFESSIONAL_BOTTOM_BAR_SCALE to settings.photo.professionalBottomBarWatermarkStyle.textScale.storageKey,
            KEY_PHOTO_WATERMARK_PROFESSIONAL_BOTTOM_BAR_OPACITY to settings.photo.professionalBottomBarWatermarkStyle.textOpacity.storageKey,
            KEY_PHOTO_WATERMARK_PROFESSIONAL_BOTTOM_BAR_BACKGROUND to settings.photo.professionalBottomBarWatermarkStyle.frameBackground.storageKey,
            KEY_PHOTO_LIVE_DEFAULT to settings.photo.livePhotoEnabledByDefault.toString(),
            KEY_PHOTO_LIVE_SAVE_FORMAT to settings.photo.liveSaveFormat.storageKey,
            KEY_PHOTO_COUNTDOWN to settings.photo.countdownDuration.storageKey,
            KEY_VIDEO_FILTER to settings.video.defaultFilterProfileId,
            KEY_VIDEO_RESOLUTION to settings.video.defaultVideoSpec.resolution.storageKey,
            KEY_VIDEO_FRAME_RATE to settings.video.defaultVideoSpec.frameRate.storageKey,
            KEY_VIDEO_DYNAMIC_FPS_POLICY to settings.video.defaultVideoSpec.dynamicFpsPolicy.storageKey,
            KEY_VIDEO_AUDIO_PROFILE to settings.video.defaultVideoSpec.audioProfile.storageKey,
            KEY_COLOR_LAB_COLOR_AXIS to settings.photo.colorLabSpec.colorAxis.toString(),
            KEY_COLOR_LAB_TONE_AXIS to settings.photo.colorLabSpec.toneAxis.toString(),
            KEY_COLOR_LAB_STRENGTH to settings.photo.colorLabSpec.strength.toString(),
            KEY_COLOR_LAB_PRESET_ID to (settings.photo.colorLabSpec.presetId ?: ""),
            KEY_PHOTO_STYLE_STRENGTH to settings.photo.styleStrength.toString(),
            KEY_PHOTO_LOW_LIGHT_NIGHT_ASSIST to settings.photo.lowLightNightAssistEnabled.toString()
        )
    }

    fun fromMap(values: Map<String, String>): PersistedSettings {
        val defaults = PersistedSettings()
        return PersistedSettings(
            common = CommonSettings(
                gridMode = CompositionGridMode.fromStorageKey(values[KEY_GRID_MODE])
                    ?: defaults.common.gridMode,
                shutterSoundEnabled = parseBoolean(
                    values[KEY_SHUTTER_SOUND_ENABLED],
                    defaults.common.shutterSoundEnabled
                ),
                selfieMirrorEnabled = parseBoolean(
                    values[KEY_SELFIE_MIRROR_ENABLED],
                    defaults.common.selfieMirrorEnabled
                )
            ),
            photo = PhotoSettings(
                defaultFilterProfileId = values[KEY_PHOTO_FILTER]
                    ?.takeIf { it.isNotBlank() }
                    ?: defaults.photo.defaultFilterProfileId,
                defaultHumanisticFilterProfileId = values[KEY_HUMANISTIC_FILTER]
                    ?.takeIf { it.isNotBlank() }
                    ?: defaults.photo.defaultHumanisticFilterProfileId,
                defaultPortraitFilterProfileId = values[KEY_PORTRAIT_FILTER]
                    ?.takeIf { it.isNotBlank() }
                    ?: defaults.photo.defaultPortraitFilterProfileId,
                portraitProfile = PortraitProfile.fromStorageKey(values[KEY_PORTRAIT_PROFILE])
                    ?: defaults.photo.portraitProfile,
                portraitBeautyPreset = PortraitBeautyPreset.fromStorageKey(
                    values[KEY_PORTRAIT_BEAUTY_PRESET]
                ) ?: defaults.photo.portraitBeautyPreset,
                portraitBeautyStrength = PortraitBeautyStrength.fromStorageKey(
                    values[KEY_PORTRAIT_BEAUTY_STRENGTH]
                ) ?: defaults.photo.portraitBeautyStrength,
                portraitBokehEffect = PortraitBokehEffect.fromStorageKey(
                    values[KEY_PORTRAIT_BOKEH_EFFECT]
                ) ?: defaults.photo.portraitBokehEffect,
                portraitDepthStrength = values[KEY_PORTRAIT_DEPTH_STRENGTH]?.toIntOrNull()
                    ?.coerceIn(0, 100) ?: defaults.photo.portraitDepthStrength,
                defaultWatermarkTemplateId = values[KEY_PHOTO_WATERMARK_TEMPLATE]
                    ?.takeIf { value ->
                        value.isNotBlank() &&
                            DEFAULT_WATERMARK_TEMPLATES.any { template -> template.id == value }
                    }
                    ?: defaults.photo.defaultWatermarkTemplateId,
                classicOverlayWatermarkStyle = WatermarkStyleSettings(
                    textPlacement = WatermarkTextPlacement.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_CLASSIC_POSITION]
                    ) ?: defaults.photo.classicOverlayWatermarkStyle.textPlacement,
                    textScale = WatermarkTextScale.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_CLASSIC_SCALE]
                    ) ?: defaults.photo.classicOverlayWatermarkStyle.textScale,
                    textOpacity = WatermarkTextOpacity.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_CLASSIC_OPACITY]
                    ) ?: defaults.photo.classicOverlayWatermarkStyle.textOpacity,
                    frameBackground = WatermarkFrameBackground.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_CLASSIC_BACKGROUND]
                    ) ?: defaults.photo.classicOverlayWatermarkStyle.frameBackground
                ),
                travelPolaroidWatermarkStyle = WatermarkStyleSettings(
                    textPlacement = WatermarkTextPlacement.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_TRAVEL_POSITION]
                    ) ?: defaults.photo.travelPolaroidWatermarkStyle.textPlacement,
                    textScale = WatermarkTextScale.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_TRAVEL_SCALE]
                    ) ?: defaults.photo.travelPolaroidWatermarkStyle.textScale,
                    textOpacity = WatermarkTextOpacity.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_TRAVEL_OPACITY]
                    ) ?: defaults.photo.travelPolaroidWatermarkStyle.textOpacity,
                    frameBackground = WatermarkFrameBackground.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_TRAVEL_BACKGROUND]
                    ) ?: defaults.photo.travelPolaroidWatermarkStyle.frameBackground
                ),
                retroFrameWatermarkStyle = WatermarkStyleSettings(
                    textPlacement = WatermarkTextPlacement.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_RETRO_POSITION]
                    ) ?: defaults.photo.retroFrameWatermarkStyle.textPlacement,
                    textScale = WatermarkTextScale.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_RETRO_SCALE]
                    ) ?: defaults.photo.retroFrameWatermarkStyle.textScale,
                    textOpacity = WatermarkTextOpacity.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_RETRO_OPACITY]
                    ) ?: defaults.photo.retroFrameWatermarkStyle.textOpacity,
                    frameBackground = WatermarkFrameBackground.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_RETRO_BACKGROUND]
                    ) ?: defaults.photo.retroFrameWatermarkStyle.frameBackground
                ),
                pureTextWatermarkStyle = WatermarkStyleSettings(
                    textPlacement = WatermarkTextPlacement.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_PURE_TEXT_POSITION]
                    ) ?: defaults.photo.pureTextWatermarkStyle.textPlacement,
                    textScale = WatermarkTextScale.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_PURE_TEXT_SCALE]
                    ) ?: defaults.photo.pureTextWatermarkStyle.textScale,
                    textOpacity = WatermarkTextOpacity.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_PURE_TEXT_OPACITY]
                    ) ?: defaults.photo.pureTextWatermarkStyle.textOpacity,
                    frameBackground = WatermarkFrameBackground.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_PURE_TEXT_BACKGROUND]
                    ) ?: defaults.photo.pureTextWatermarkStyle.frameBackground
                ),
                blurFourBorderWatermarkStyle = WatermarkStyleSettings(
                    textPlacement = WatermarkTextPlacement.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_BLUR_FOUR_BORDER_POSITION]
                    ) ?: defaults.photo.blurFourBorderWatermarkStyle.textPlacement,
                    textScale = WatermarkTextScale.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_BLUR_FOUR_BORDER_SCALE]
                    ) ?: defaults.photo.blurFourBorderWatermarkStyle.textScale,
                    textOpacity = WatermarkTextOpacity.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_BLUR_FOUR_BORDER_OPACITY]
                    ) ?: defaults.photo.blurFourBorderWatermarkStyle.textOpacity,
                    frameBackground = WatermarkFrameBackground.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_BLUR_FOUR_BORDER_BACKGROUND]
                    ) ?: defaults.photo.blurFourBorderWatermarkStyle.frameBackground
                ),
                professionalBottomBarWatermarkStyle = WatermarkStyleSettings(
                    textPlacement = WatermarkTextPlacement.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_PROFESSIONAL_BOTTOM_BAR_POSITION]
                    ) ?: defaults.photo.professionalBottomBarWatermarkStyle.textPlacement,
                    textScale = WatermarkTextScale.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_PROFESSIONAL_BOTTOM_BAR_SCALE]
                    ) ?: defaults.photo.professionalBottomBarWatermarkStyle.textScale,
                    textOpacity = WatermarkTextOpacity.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_PROFESSIONAL_BOTTOM_BAR_OPACITY]
                    ) ?: defaults.photo.professionalBottomBarWatermarkStyle.textOpacity,
                    frameBackground = WatermarkFrameBackground.fromStorageKey(
                        values[KEY_PHOTO_WATERMARK_PROFESSIONAL_BOTTOM_BAR_BACKGROUND]
                    ) ?: defaults.photo.professionalBottomBarWatermarkStyle.frameBackground
                ),
                livePhotoEnabledByDefault = parseBoolean(
                    values[KEY_PHOTO_LIVE_DEFAULT],
                    defaults.photo.livePhotoEnabledByDefault
                ),
                liveSaveFormat = LiveSaveFormat.fromStorageKey(values[KEY_PHOTO_LIVE_SAVE_FORMAT])
                    ?: defaults.photo.liveSaveFormat,
                countdownDuration = CountdownDuration.fromStorageKey(values[KEY_PHOTO_COUNTDOWN])
                    ?: defaults.photo.countdownDuration,
                colorLabSpec = ColorLabSpec(
                    colorAxis = values[KEY_COLOR_LAB_COLOR_AXIS]?.toFloatOrNull() ?: 0f,
                    toneAxis = values[KEY_COLOR_LAB_TONE_AXIS]?.toFloatOrNull() ?: 0f,
                    strength = values[KEY_COLOR_LAB_STRENGTH]?.toFloatOrNull() ?: 1f,
                    presetId = values[KEY_COLOR_LAB_PRESET_ID]?.takeIf { it.isNotBlank() }
                ).normalized(),
                styleStrength = values[KEY_PHOTO_STYLE_STRENGTH]?.toFloatOrNull()?.coerceIn(0f, 1f)
                    ?: defaults.photo.styleStrength,
                lowLightNightAssistEnabled = parseBoolean(
                    values[KEY_PHOTO_LOW_LIGHT_NIGHT_ASSIST],
                    defaults.photo.lowLightNightAssistEnabled
                )
            ),
            video = VideoSettings(
                defaultVideoSpec = VideoSpec(
                    resolution = VideoResolution.fromStorageKey(values[KEY_VIDEO_RESOLUTION])
                        ?: defaults.video.defaultVideoSpec.resolution,
                    frameRate = VideoFrameRate.fromStorageKey(values[KEY_VIDEO_FRAME_RATE])
                        ?: defaults.video.defaultVideoSpec.frameRate,
                    dynamicFpsPolicy = DynamicVideoFpsPolicy.fromStorageKey(
                        values[KEY_VIDEO_DYNAMIC_FPS_POLICY]
                    ) ?: defaults.video.defaultVideoSpec.dynamicFpsPolicy,
                    audioProfile = AudioProfile.fromStorageKey(values[KEY_VIDEO_AUDIO_PROFILE])
                        ?: defaults.video.defaultVideoSpec.audioProfile
                ),
                defaultFilterProfileId = values[KEY_VIDEO_FILTER]
                    ?.takeIf { it.isNotBlank() }
                    ?: defaults.video.defaultFilterProfileId
            )
        )
    }

    private fun parseBoolean(
        value: String?,
        fallback: Boolean
    ): Boolean {
        return when (value) {
            "true" -> true
            "false" -> false
            else -> fallback
        }
    }
}
