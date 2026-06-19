package com.opencamera.core.settings

data class VideoSpec(
    val resolution: VideoResolution = VideoResolution.UHD_4K,
    val frameRate: VideoFrameRate = VideoFrameRate.FPS_25,
    val dynamicFpsPolicy: DynamicVideoFpsPolicy = DynamicVideoFpsPolicy.LOCKED,
    val audioProfile: AudioProfile = AudioProfile.STANDARD
) {
    val summaryLabel: String
        get() = "${resolution.label} ${frameRate.label}"
}

data class FilterRenderSpec(
    val brightnessShift: Int = 0,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmthShift: Int = 0,
    val tintShift: Int = 0,
    val monochromeMix: Float = 0f,
    val vignetteStrength: Float = 0f,
    val softGlowStrength: Float = 0f,
    val haloStrength: Float = 0f,
    val grainStrength: Float = 0f,
    val sharpnessBoost: Float = 0f,
    val highlightCompression: Float = 0f,
    val shadowLift: Float = 0f,
    val warmBoost: Float = 0f,
    val coolBoost: Float = 0f
) {
    companion object {
        fun fromMetadataTags(
            tags: Map<String, String>,
            prefix: String = "filterSpec"
        ): FilterRenderSpec? {
            return parseFilterRenderSpec(tags, prefix)
        }
    }
}

data class ManualCaptureParams(
    val rawEnabled: Boolean = false,
    val iso: Int? = null,
    val shutterSpeedMillis: Long? = null,
    val exposureCompensationSteps: Int? = null,
    val focusDistanceDiopters: Float? = null,
    val apertureFNumber: Float? = null,
    val whiteBalanceKelvin: Int? = null
)

data class LiveMediaBundle(
    val motionDurationMillis: Long = 1_500,
    val motionContainer: String = "video/mp4",
    val sidecarMimeType: String = "application/vnd.opencamera.live+json",
    val watermarkMotionBehavior: LiveWatermarkMotionBehavior =
        LiveWatermarkMotionBehavior.FOLLOW_FRAME_LUMA_AND_MOTION
)

data class FilterProfile(
    val id: String,
    val label: String,
    val category: FilterProfileCategory,
    val builtIn: Boolean = true,
    val renderSpec: FilterRenderSpec? = null
)

data class WatermarkTemplate(
    val id: String,
    val label: String,
    val tokenKeys: Set<String> = emptySet(),
    val supportsFrameBorder: Boolean = false,
    val kind: WatermarkTemplateKind = WatermarkTemplateKind.INFO_OVERLAY,
    val allowedPlacements: Set<WatermarkTextPlacement> = WatermarkTextPlacement.entries.toSet(),
    val allowedFrameBackgrounds: Set<WatermarkFrameBackground> = WatermarkFrameBackground.entries.toSet()
)

data class WatermarkStyleSettings(
    val textPlacement: WatermarkTextPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
    val textScale: WatermarkTextScale = WatermarkTextScale.NORMAL,
    val textOpacity: WatermarkTextOpacity = WatermarkTextOpacity.SOLID,
    val frameBackground: WatermarkFrameBackground = WatermarkFrameBackground.DARK
)

data class VideoSpecConstraints(
    val supportedFrameRatesByResolution: Map<VideoResolution, Set<VideoFrameRate>> = DEFAULT_FRAME_RATES_BY_RESOLUTION,
    val dynamicPolicies: Set<DynamicVideoFpsPolicy> = DynamicVideoFpsPolicy.entries.toSet(),
    val audioProfiles: Set<AudioProfile> = AudioProfile.entries.toSet()
) {
    val resolutions: Set<VideoResolution>
        get() = supportedFrameRatesByResolution.keys

    val frameRates: Set<VideoFrameRate> =
        supportedFrameRatesByResolution.values
            .flatten()
            .toSet()

    fun frameRatesFor(resolution: VideoResolution): Set<VideoFrameRate> {
        return supportedFrameRatesByResolution[resolution].orEmpty()
    }

    companion object {
        val DEFAULT_FRAME_RATES_BY_RESOLUTION: Map<VideoResolution, Set<VideoFrameRate>> =
            linkedMapOf(
                VideoResolution.UHD_8K to setOf(
                    VideoFrameRate.FPS_25,
                    VideoFrameRate.FPS_30,
                    VideoFrameRate.FPS_60
                ),
                VideoResolution.UHD_4K to setOf(
                    VideoFrameRate.FPS_25,
                    VideoFrameRate.FPS_30,
                    VideoFrameRate.FPS_60,
                    VideoFrameRate.FPS_100,
                    VideoFrameRate.FPS_120
                ),
                VideoResolution.FHD_1080P to setOf(
                    VideoFrameRate.FPS_25,
                    VideoFrameRate.FPS_30,
                    VideoFrameRate.FPS_60,
                    VideoFrameRate.FPS_100,
                    VideoFrameRate.FPS_120
                ),
                VideoResolution.HD_720P to setOf(
                    VideoFrameRate.FPS_25,
                    VideoFrameRate.FPS_30,
                    VideoFrameRate.FPS_60,
                    VideoFrameRate.FPS_100,
                    VideoFrameRate.FPS_120
                ),
                VideoResolution.SD_480P to setOf(
                    VideoFrameRate.FPS_25,
                    VideoFrameRate.FPS_30,
                    VideoFrameRate.FPS_60
                )
            )
    }
}

data class CommonSettings(
    val appLanguage: AppLanguage = AppLanguage.ZH,
    val gridMode: CompositionGridMode = CompositionGridMode.OFF,
    val shutterSoundEnabled: Boolean = true,
    val selfieMirrorEnabled: Boolean = false
)

data class PhotoSettings(
    val defaultFilterProfileId: String = "photo-original",
    val defaultHumanisticFilterProfileId: String = "humanistic-original",
    val defaultPortraitFilterProfileId: String = "portrait-original",
    val defaultCheckInScenario: String = "portrait",
    val portraitProfile: PortraitProfile = PortraitProfile.NATIVE,
    val portraitBeautyPreset: PortraitBeautyPreset = PortraitBeautyPreset.AUTHENTIC,
    val portraitBeautyStrength: PortraitBeautyStrength = PortraitBeautyStrength.SOFT,
    val portraitBokehEffect: PortraitBokehEffect = PortraitBokehEffect.NATURAL,
    val portraitDepthStrength: Int = 50,
    val defaultWatermarkTemplateId: String = "classic-overlay",
    val classicOverlayWatermarkStyle: WatermarkStyleSettings = WatermarkStyleSettings(
        textPlacement = WatermarkTextPlacement.BOTTOM_RIGHT,
        textScale = WatermarkTextScale.NORMAL,
        textOpacity = WatermarkTextOpacity.SOFT,
        frameBackground = WatermarkFrameBackground.DARK
    ),
    val travelPolaroidWatermarkStyle: WatermarkStyleSettings = WatermarkStyleSettings(
        textPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
        textScale = WatermarkTextScale.NORMAL,
        textOpacity = WatermarkTextOpacity.SOLID,
        frameBackground = WatermarkFrameBackground.WHITE
    ),
    val retroFrameWatermarkStyle: WatermarkStyleSettings = WatermarkStyleSettings(
        textPlacement = WatermarkTextPlacement.BOTTOM_CENTER,
        textScale = WatermarkTextScale.NORMAL,
        textOpacity = WatermarkTextOpacity.SOFT,
        frameBackground = WatermarkFrameBackground.SOURCE_VIVID_BLUR
    ),
    val pureTextWatermarkStyle: WatermarkStyleSettings = WatermarkStyleSettings(
        textPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
        textScale = WatermarkTextScale.NORMAL,
        textOpacity = WatermarkTextOpacity.SOFT,
        frameBackground = WatermarkFrameBackground.DARK
    ),
    val blurFourBorderWatermarkStyle: WatermarkStyleSettings = WatermarkStyleSettings(
        textPlacement = WatermarkTextPlacement.BOTTOM_CENTER,
        textScale = WatermarkTextScale.NORMAL,
        textOpacity = WatermarkTextOpacity.SOLID,
        frameBackground = WatermarkFrameBackground.SOURCE_LIGHT_BLUR
    ),
    val professionalBottomBarWatermarkStyle: WatermarkStyleSettings = WatermarkStyleSettings(
        textPlacement = WatermarkTextPlacement.BOTTOM_CENTER,
        textScale = WatermarkTextScale.NORMAL,
        textOpacity = WatermarkTextOpacity.SOLID,
        frameBackground = WatermarkFrameBackground.DARK
    ),
    val nightStreetWatermarkStyle: WatermarkStyleSettings = WatermarkStyleSettings(
        textPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
        textScale = WatermarkTextScale.NORMAL,
        textOpacity = WatermarkTextOpacity.SOFT,
        frameBackground = WatermarkFrameBackground.SOURCE_BLUR
    ),
    val livePhotoEnabledByDefault: Boolean = false,
    val liveSaveFormat: LiveSaveFormat = LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG,
    val countdownDuration: CountdownDuration = CountdownDuration.OFF,
    val colorLabSpec: ColorLabSpec = ColorLabSpec(),
    val styleStrength: Float = 1f,
    val lowLightNightAssistEnabled: Boolean = true
)

data class VideoSettings(
    val defaultVideoSpec: VideoSpec = VideoSpec(),
    val defaultFilterProfileId: String = "photo-original"
)

data class PersistedSettings(
    val common: CommonSettings = CommonSettings(),
    val photo: PhotoSettings = PhotoSettings(),
    val video: VideoSettings = VideoSettings()
)

data class FeatureCatalog(
    val filterProfiles: List<FilterProfile> = DEFAULT_FILTER_PROFILES,
    val watermarkTemplates: List<WatermarkTemplate> = DEFAULT_WATERMARK_TEMPLATES,
    val countdownOptions: Set<CountdownDuration> = CountdownDuration.entries.toSet(),
    val videoSpecConstraints: VideoSpecConstraints = VideoSpecConstraints(),
    val liveMediaBundleDraft: LiveMediaBundle = LiveMediaBundle(),
    val manualCaptureDraft: ManualCaptureParams = ManualCaptureParams()
) {
    fun filterProfileOrNull(id: String?): FilterProfile? {
        return filterProfiles.firstOrNull { it.id == id }
    }

    fun withImportedFilterProfile(profile: FilterProfile): FeatureCatalog {
        val withoutExisting = filterProfiles.filterNot { it.id == profile.id }
        val insertIndex = withoutExisting.indexOfFirst { existing ->
            existing.category == FilterProfileCategory.CUSTOM
        }.takeIf { it >= 0 } ?: withoutExisting.size
        val mergedProfiles = withoutExisting.toMutableList().apply {
            add(insertIndex, profile.copy(builtIn = false))
        }
        return copy(filterProfiles = mergedProfiles)
    }
}

data class SessionSettingsSnapshot(
    val persisted: PersistedSettings = PersistedSettings(),
    val catalog: FeatureCatalog = FeatureCatalog()
)

fun PersistedSettings.hasUserAdjustments(target: ResetTarget): Boolean {
    val defaults = PersistedSettings()
    return when (target) {
        ResetTarget.SETTINGS -> common != defaults.common
        ResetTarget.STYLE -> photo.defaultFilterProfileId != defaults.photo.defaultFilterProfileId ||
            photo.defaultHumanisticFilterProfileId != defaults.photo.defaultHumanisticFilterProfileId ||
            photo.defaultPortraitFilterProfileId != defaults.photo.defaultPortraitFilterProfileId ||
            photo.defaultCheckInScenario != defaults.photo.defaultCheckInScenario ||
            photo.styleStrength != defaults.photo.styleStrength ||
            photo.colorLabSpec != defaults.photo.colorLabSpec
        ResetTarget.COLOR_LAB -> photo.colorLabSpec != defaults.photo.colorLabSpec
        ResetTarget.QUICK -> common.gridMode != defaults.common.gridMode ||
            photo.livePhotoEnabledByDefault != defaults.photo.livePhotoEnabledByDefault ||
            photo.countdownDuration != defaults.photo.countdownDuration ||
            video.defaultVideoSpec != defaults.video.defaultVideoSpec
    }
}
