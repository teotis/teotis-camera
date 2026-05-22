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
    fun toMetadataTags(prefix: String = METADATA_PREFIX): Map<String, String> {
        return linkedMapOf(
            "${prefix}.version" to "1",
            "${prefix}.brightnessShift" to brightnessShift.toString(),
            "${prefix}.contrast" to contrast.toString(),
            "${prefix}.saturation" to saturation.toString(),
            "${prefix}.warmthShift" to warmthShift.toString(),
            "${prefix}.tintShift" to tintShift.toString(),
            "${prefix}.monochromeMix" to monochromeMix.toString(),
            "${prefix}.vignetteStrength" to vignetteStrength.toString(),
            "${prefix}.softGlowStrength" to softGlowStrength.toString(),
            "${prefix}.haloStrength" to haloStrength.toString(),
            "${prefix}.grainStrength" to grainStrength.toString(),
            "${prefix}.sharpnessBoost" to sharpnessBoost.toString(),
            "${prefix}.highlightCompression" to highlightCompression.toString(),
            "${prefix}.shadowLift" to shadowLift.toString(),
            "${prefix}.warmBoost" to warmBoost.toString(),
            "${prefix}.coolBoost" to coolBoost.toString()
        )
    }

    companion object {
        private const val METADATA_PREFIX = "filterSpec"

        fun fromMetadataTags(
            tags: Map<String, String>,
            prefix: String = METADATA_PREFIX
        ): FilterRenderSpec? {
            if (tags["${prefix}.version"] == null) {
                return null
            }
            return FilterRenderSpec(
                brightnessShift = tags["${prefix}.brightnessShift"]?.toIntOrNull() ?: 0,
                contrast = tags["${prefix}.contrast"]?.toFloatOrNull() ?: 1f,
                saturation = tags["${prefix}.saturation"]?.toFloatOrNull() ?: 1f,
                warmthShift = tags["${prefix}.warmthShift"]?.toIntOrNull() ?: 0,
                tintShift = tags["${prefix}.tintShift"]?.toIntOrNull() ?: 0,
                monochromeMix = tags["${prefix}.monochromeMix"]?.toFloatOrNull() ?: 0f,
                vignetteStrength = tags["${prefix}.vignetteStrength"]?.toFloatOrNull() ?: 0f,
                softGlowStrength = tags["${prefix}.softGlowStrength"]?.toFloatOrNull() ?: 0f,
                haloStrength = tags["${prefix}.haloStrength"]?.toFloatOrNull() ?: 0f,
                grainStrength = tags["${prefix}.grainStrength"]?.toFloatOrNull() ?: 0f,
                sharpnessBoost = tags["${prefix}.sharpnessBoost"]?.toFloatOrNull() ?: 0f,
                highlightCompression = tags["${prefix}.highlightCompression"]?.toFloatOrNull() ?: 0f,
                shadowLift = tags["${prefix}.shadowLift"]?.toFloatOrNull() ?: 0f,
                warmBoost = tags["${prefix}.warmBoost"]?.toFloatOrNull() ?: 0f,
                coolBoost = tags["${prefix}.coolBoost"]?.toFloatOrNull() ?: 0f
            )
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
    val supportsFrameBorder: Boolean = false
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

    val frameRates: Set<VideoFrameRate>
        get() = supportedFrameRatesByResolution.values
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
    val portraitProfile: PortraitProfile = PortraitProfile.NATIVE,
    val portraitBeautyPreset: PortraitBeautyPreset = PortraitBeautyPreset.AUTHENTIC,
    val portraitBeautyStrength: PortraitBeautyStrength = PortraitBeautyStrength.SOFT,
    val portraitBokehEffect: PortraitBokehEffect = PortraitBokehEffect.NATURAL,
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
    val livePhotoEnabledByDefault: Boolean = false,
    val countdownDuration: CountdownDuration = CountdownDuration.OFF,
    val colorLabSpec: ColorLabSpec = ColorLabSpec(),
    val styleStrength: Float = 1f
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

    fun reduce(action: FeatureCatalogAction): FeatureCatalog {
        return when (action) {
            is FeatureCatalogAction.UpdateManualRawEnabled -> copy(
                manualCaptureDraft = manualCaptureDraft.copy(rawEnabled = action.enabled)
            )

            is FeatureCatalogAction.UpdateManualIso -> copy(
                manualCaptureDraft = manualCaptureDraft.copy(iso = action.iso)
            )

            is FeatureCatalogAction.UpdateManualShutterSpeedMillis -> copy(
                manualCaptureDraft = manualCaptureDraft.copy(
                    shutterSpeedMillis = action.shutterSpeedMillis
                )
            )

            is FeatureCatalogAction.UpdateManualExposureCompensationSteps -> copy(
                manualCaptureDraft = manualCaptureDraft.copy(
                    exposureCompensationSteps = action.exposureCompensationSteps
                )
            )

            is FeatureCatalogAction.UpdateManualFocusDistanceDiopters -> copy(
                manualCaptureDraft = manualCaptureDraft.copy(
                    focusDistanceDiopters = action.focusDistanceDiopters
                )
            )

            is FeatureCatalogAction.UpdateManualApertureFNumber -> copy(
                manualCaptureDraft = manualCaptureDraft.copy(
                    apertureFNumber = action.apertureFNumber
                )
            )

            is FeatureCatalogAction.UpdateManualWhiteBalanceKelvin -> copy(
                manualCaptureDraft = manualCaptureDraft.copy(
                    whiteBalanceKelvin = action.whiteBalanceKelvin
                )
            )
        }
    }
}

data class SessionSettingsSnapshot(
    val persisted: PersistedSettings = PersistedSettings(),
    val catalog: FeatureCatalog = FeatureCatalog()
)
