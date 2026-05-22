package com.opencamera.core.settings

enum class CompositionGridMode(
    val storageKey: String,
    val label: String
) {
    OFF(
        storageKey = "off",
        label = "Off"
    ),
    RULE_OF_THIRDS(
        storageKey = "rule-of-thirds",
        label = "3x3"
    ),
    GOLDEN_RATIO(
        storageKey = "golden-ratio",
        label = "Golden"
    );

    companion object {
        fun fromStorageKey(value: String?): CompositionGridMode? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class CountdownDuration(
    val storageKey: String,
    val seconds: Int,
    val label: String
) {
    OFF(
        storageKey = "off",
        seconds = 0,
        label = "Off"
    ),
    SECONDS_3(
        storageKey = "3s",
        seconds = 3,
        label = "3s"
    ),
    SECONDS_5(
        storageKey = "5s",
        seconds = 5,
        label = "5s"
    ),
    SECONDS_10(
        storageKey = "10s",
        seconds = 10,
        label = "10s"
    );

    companion object {
        fun fromStorageKey(value: String?): CountdownDuration? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class AudioProfile(
    val storageKey: String,
    val label: String
) {
    STANDARD(
        storageKey = "standard",
        label = "Standard"
    ),
    CONCERT(
        storageKey = "concert",
        label = "Concert"
    );

    companion object {
        fun fromStorageKey(value: String?): AudioProfile? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class VideoResolution(
    val storageKey: String,
    val label: String
) {
    UHD_8K(
        storageKey = "8k",
        label = "8K"
    ),
    UHD_4K(
        storageKey = "4k",
        label = "4K"
    ),
    FHD_1080P(
        storageKey = "1080p",
        label = "1080p"
    ),
    HD_720P(
        storageKey = "720p",
        label = "720p"
    ),
    SD_480P(
        storageKey = "480p",
        label = "480p"
    );

    companion object {
        fun fromStorageKey(value: String?): VideoResolution? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class VideoFrameRate(
    val storageKey: String,
    val fps: Int
) {
    FPS_24(
        storageKey = "24",
        fps = 24
    ),
    FPS_25(
        storageKey = "25",
        fps = 25
    ),
    FPS_30(
        storageKey = "30",
        fps = 30
    ),
    FPS_60(
        storageKey = "60",
        fps = 60
    ),
    FPS_100(
        storageKey = "100",
        fps = 100
    ),
    FPS_120(
        storageKey = "120",
        fps = 120
    );

    val label: String
        get() = "${fps}fps"

    companion object {
        fun fromStorageKey(value: String?): VideoFrameRate? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class DynamicVideoFpsPolicy(
    val storageKey: String,
    val label: String
) {
    LOCKED(
        storageKey = "locked",
        label = "Locked fps"
    ),
    LOW_LIGHT_AUTO_24FPS(
        storageKey = "low-light-auto-24fps",
        label = "Low-light auto 24fps"
    );

    companion object {
        fun fromStorageKey(value: String?): DynamicVideoFpsPolicy? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class FilterProfileCategory {
    PHOTO,
    PORTRAIT,
    HUMANISTIC,
    CUSTOM
}

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

enum class LiveWatermarkMotionBehavior(
    val storageKey: String,
    val label: String,
    val isDynamic: Boolean,
    val brightnessCouplingKey: String,
    val opacityCouplingKey: String
) {
    STATIC_OVERLAY(
        storageKey = "static-overlay",
        label = "Static Overlay",
        isDynamic = false,
        brightnessCouplingKey = "locked",
        opacityCouplingKey = "locked"
    ),
    FOLLOW_FRAME_LUMA(
        storageKey = "follow-frame-luma",
        label = "Follow Frame Luma",
        isDynamic = true,
        brightnessCouplingKey = "follow-frame-luma",
        opacityCouplingKey = "locked"
    ),
    FOLLOW_FRAME_LUMA_AND_MOTION(
        storageKey = "follow-frame-luma-and-motion",
        label = "Follow Frame Luma + Motion",
        isDynamic = true,
        brightnessCouplingKey = "follow-frame-luma",
        opacityCouplingKey = "follow-frame-motion"
    );

    companion object {
        fun fromStorageKey(value: String?): LiveWatermarkMotionBehavior? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

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

enum class WatermarkTextPlacement(
    val storageKey: String,
    val label: String
) {
    TOP_LEFT("top-left", "Top Left"),
    TOP_RIGHT("top-right", "Top Right"),
    BOTTOM_LEFT("bottom-left", "Bottom Left"),
    BOTTOM_RIGHT("bottom-right", "Bottom Right"),
    BOTTOM_CENTER("bottom-center", "Bottom Center");

    companion object {
        fun fromStorageKey(value: String?): WatermarkTextPlacement? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class WatermarkTextScale(
    val storageKey: String,
    val label: String,
    val multiplier: Float
) {
    COMPACT("compact", "Compact", 0.85f),
    NORMAL("normal", "Normal", 1f),
    LARGE("large", "Large", 1.2f);

    companion object {
        fun fromStorageKey(value: String?): WatermarkTextScale? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class WatermarkTextOpacity(
    val storageKey: String,
    val label: String,
    val alphaFraction: Float
) {
    SUBTLE("subtle", "Subtle", 0.55f),
    SOFT("soft", "Soft", 0.8f),
    SOLID("solid", "Solid", 1f);

    companion object {
        fun fromStorageKey(value: String?): WatermarkTextOpacity? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class WatermarkFrameBackground(
    val storageKey: String,
    val label: String
) {
    DARK("dark", "Dark"),
    WHITE("white", "White"),
    SOURCE_BLUR("source-blur", "Source Blur"),
    SOURCE_LIGHT_BLUR("source-light-blur", "Light Blur"),
    SOURCE_VIVID_BLUR("source-vivid-blur", "Vivid Blur");

    companion object {
        fun fromStorageKey(value: String?): WatermarkFrameBackground? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

data class WatermarkStyleSettings(
    val textPlacement: WatermarkTextPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
    val textScale: WatermarkTextScale = WatermarkTextScale.NORMAL,
    val textOpacity: WatermarkTextOpacity = WatermarkTextOpacity.SOLID,
    val frameBackground: WatermarkFrameBackground = WatermarkFrameBackground.DARK
)

enum class PortraitProfile(
    val storageKey: String,
    val label: String
) {
    NATIVE(
        storageKey = "native",
        label = "Native Portrait"
    ),
    LUMINOUS(
        storageKey = "luminous",
        label = "Luminous Portrait"
    );

    companion object {
        fun fromStorageKey(value: String?): PortraitProfile? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class PortraitBeautyPreset(
    val storageKey: String,
    val label: String
) {
    AUTHENTIC(
        storageKey = "authentic",
        label = "Authentic"
    ),
    CLEAR(
        storageKey = "clear",
        label = "Clear"
    ),
    RADIANT(
        storageKey = "radiant",
        label = "Radiant"
    );

    companion object {
        fun fromStorageKey(value: String?): PortraitBeautyPreset? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class PortraitBeautyStrength(
    val storageKey: String,
    val label: String,
    val intensity: Float
) {
    OFF(
        storageKey = "off",
        label = "Off",
        intensity = 0f
    ),
    SOFT(
        storageKey = "soft",
        label = "Soft",
        intensity = 0.35f
    ),
    BALANCED(
        storageKey = "balanced",
        label = "Balanced",
        intensity = 0.6f
    ),
    ELEVATED(
        storageKey = "elevated",
        label = "Elevated",
        intensity = 0.85f
    );

    companion object {
        fun fromStorageKey(value: String?): PortraitBeautyStrength? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

enum class PortraitBokehEffect(
    val storageKey: String,
    val label: String
) {
    NATURAL(
        storageKey = "natural",
        label = "Natural"
    ),
    CREAMY(
        storageKey = "creamy",
        label = "Creamy"
    ),
    DREAMY(
        storageKey = "dreamy",
        label = "Dreamy"
    );

    companion object {
        fun fromStorageKey(value: String?): PortraitBokehEffect? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}

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
    val defaultFilterProfileId: String = "photo-vivid",
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
    val defaultFilterProfileId: String = "photo-vivid"
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

sealed interface FeatureCatalogAction {
    data class UpdateManualRawEnabled(val enabled: Boolean) : FeatureCatalogAction
    data class UpdateManualIso(val iso: Int?) : FeatureCatalogAction
    data class UpdateManualShutterSpeedMillis(val shutterSpeedMillis: Long?) : FeatureCatalogAction
    data class UpdateManualExposureCompensationSteps(
        val exposureCompensationSteps: Int?
    ) : FeatureCatalogAction
    data class UpdateManualFocusDistanceDiopters(
        val focusDistanceDiopters: Float?
    ) : FeatureCatalogAction
    data class UpdateManualApertureFNumber(val apertureFNumber: Float?) : FeatureCatalogAction
    data class UpdateManualWhiteBalanceKelvin(val whiteBalanceKelvin: Int?) : FeatureCatalogAction
}

fun FeatureCatalog.createCustomFilterProfile(
    sourceProfileId: String
): FilterProfile? {
    val sourceProfile = filterProfileOrNull(sourceProfileId) ?: return null
    if (!sourceProfile.builtIn) {
        return null
    }
    val slugBase = sourceProfile.label.slugify()
    val nextIndex = filterProfiles.mapNotNull { profile ->
        profile.id.removePrefix("custom-$slugBase-").toIntOrNull()
            .takeIf { profile.id.startsWith("custom-$slugBase-") }
    }.maxOrNull()?.plus(1) ?: 1
    return FilterProfile(
        id = "custom-$slugBase-$nextIndex",
        label = "${sourceProfile.label} Custom $nextIndex",
        category = FilterProfileCategory.CUSTOM,
        builtIn = false,
        renderSpec = sourceProfile.renderSpec ?: FilterRenderSpec()
    )
}

fun FeatureCatalog.updateCustomFilterProfile(
    profileId: String,
    renderSpec: FilterRenderSpec
): FeatureCatalog? {
    val existing = filterProfileOrNull(profileId) ?: return null
    if (existing.builtIn) {
        return null
    }
    return withImportedFilterProfile(
        existing.copy(
            builtIn = false,
            renderSpec = renderSpec
        )
    )
}

data class SessionSettingsSnapshot(
    val persisted: PersistedSettings = PersistedSettings(),
    val catalog: FeatureCatalog = FeatureCatalog()
)

object FilterProfileShareCodec {
    private const val HEADER = "OPEN_CAMERA_FILTER_PROFILE_V1"

    fun export(profile: FilterProfile): String {
        val renderSpec = profile.renderSpec ?: FilterRenderSpec()
        return buildString {
            appendLine(HEADER)
            appendLine("id=${profile.id}")
            appendLine("label=${profile.label}")
            appendLine("category=${profile.category.name}")
            appendLine("builtIn=${profile.builtIn}")
            appendLine("brightnessShift=${renderSpec.brightnessShift}")
            appendLine("contrast=${renderSpec.contrast}")
            appendLine("saturation=${renderSpec.saturation}")
            appendLine("warmthShift=${renderSpec.warmthShift}")
            appendLine("tintShift=${renderSpec.tintShift}")
            appendLine("monochromeMix=${renderSpec.monochromeMix}")
            appendLine("vignetteStrength=${renderSpec.vignetteStrength}")
            appendLine("softGlowStrength=${renderSpec.softGlowStrength}")
            appendLine("haloStrength=${renderSpec.haloStrength}")
            appendLine("grainStrength=${renderSpec.grainStrength}")
            appendLine("sharpnessBoost=${renderSpec.sharpnessBoost}")
            appendLine("highlightCompression=${renderSpec.highlightCompression}")
            appendLine("shadowLift=${renderSpec.shadowLift}")
            appendLine("warmBoost=${renderSpec.warmBoost}")
            appendLine("coolBoost=${renderSpec.coolBoost}")
        }
    }

    fun import(serialized: String): FilterProfile {
        val lines = serialized.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        require(lines.firstOrNull() == HEADER) {
            "Invalid filter profile share header"
        }
        val values = lines.drop(1).associate { line ->
            val separatorIndex = line.indexOf('=')
            require(separatorIndex > 0) {
                "Malformed filter profile share line: $line"
            }
            line.substring(0, separatorIndex) to line.substring(separatorIndex + 1)
        }
        return FilterProfile(
            id = values.getValue("id"),
            label = values.getValue("label"),
            category = runCatching {
                FilterProfileCategory.valueOf(values.getValue("category"))
            }.getOrElse { error("Unknown filter profile category: ${values["category"]}") },
            builtIn = values["builtIn"]?.toBooleanStrictOrNull() ?: false,
            renderSpec = FilterRenderSpec(
                brightnessShift = values["brightnessShift"]?.toIntOrNull() ?: 0,
                contrast = values["contrast"]?.toFloatOrNull() ?: 1f,
                saturation = values["saturation"]?.toFloatOrNull() ?: 1f,
                warmthShift = values["warmthShift"]?.toIntOrNull() ?: 0,
                tintShift = values["tintShift"]?.toIntOrNull() ?: 0,
                monochromeMix = values["monochromeMix"]?.toFloatOrNull() ?: 0f,
                vignetteStrength = values["vignetteStrength"]?.toFloatOrNull() ?: 0f,
                softGlowStrength = values["softGlowStrength"]?.toFloatOrNull() ?: 0f,
                haloStrength = values["haloStrength"]?.toFloatOrNull() ?: 0f,
                grainStrength = values["grainStrength"]?.toFloatOrNull() ?: 0f,
                sharpnessBoost = values["sharpnessBoost"]?.toFloatOrNull() ?: 0f,
                highlightCompression = values["highlightCompression"]?.toFloatOrNull() ?: 0f,
                shadowLift = values["shadowLift"]?.toFloatOrNull() ?: 0f,
                warmBoost = values["warmBoost"]?.toFloatOrNull() ?: 0f,
                coolBoost = values["coolBoost"]?.toFloatOrNull() ?: 0f
            )
        )
    }
}

object ImportedFilterProfilesSerializer {
    private const val DELIMITER = "\n<<<OPEN_CAMERA_IMPORTED_FILTER>>>\n"

    fun serialize(profiles: List<FilterProfile>): String {
        return profiles
            .filterNot(FilterProfile::builtIn)
            .joinToString(separator = DELIMITER) { profile ->
                FilterProfileShareCodec.export(profile).trim()
            }
    }

    fun deserialize(serialized: String): List<FilterProfile> {
        return serialized
            .trim()
            .takeIf(String::isNotEmpty)
            ?.split(DELIMITER)
            ?.map { chunk -> FilterProfileShareCodec.import(chunk.trim()) }
            ?.map { profile -> profile.copy(builtIn = false) }
            ?: emptyList()
    }
}

fun mergeCatalog(
    baseCatalog: FeatureCatalog,
    importedProfiles: List<FilterProfile>
): FeatureCatalog {
    return importedProfiles.fold(baseCatalog) { catalog, profile ->
        catalog.withImportedFilterProfile(profile.copy(builtIn = false))
    }
}

fun FeatureCatalog.filterProfilesFor(
    category: FilterProfileCategory,
    includeCustom: Boolean = false
): List<FilterProfile> {
    return filterProfiles.filter { filterProfile ->
        filterProfile.category == category ||
            (includeCustom && filterProfile.category == FilterProfileCategory.CUSTOM)
    }
}

private fun String.slugify(): String {
    return lowercase()
        .map { character ->
            if (character.isLetterOrDigit()) {
                character
            } else {
                '-'
            }
        }
        .joinToString(separator = "")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifEmpty { "filter" }
}

fun ManualCaptureParams.compactSummary(): String {
    return buildString {
        append("RAW ")
        append(if (rawEnabled) "On" else "Off")
        append(" | ISO ")
        append(iso?.toString() ?: "Auto")
        append(" | S ")
        append(shutterSpeedMillis?.let { "${it}ms" } ?: "Auto")
        append(" | WB ")
        append(whiteBalanceKelvin?.let { "${it}K" } ?: "Auto")
    }
}

fun ManualCaptureParams.toMetadataTags(prefix: String = "manualDraft"): Map<String, String> {
    return buildMap {
        put("${prefix}Raw", if (rawEnabled) "on" else "off")
        put("${prefix}Iso", iso?.toString() ?: "auto")
        put("${prefix}ShutterSpeedMillis", shutterSpeedMillis?.toString() ?: "auto")
        put(
            "${prefix}ExposureCompensationSteps",
            exposureCompensationSteps?.toString() ?: "auto"
        )
        put(
            "${prefix}FocusDistanceDiopters",
            focusDistanceDiopters?.toString() ?: "auto"
        )
        put("${prefix}ApertureFNumber", apertureFNumber?.toString() ?: "auto")
        put("${prefix}WhiteBalanceKelvin", whiteBalanceKelvin?.toString() ?: "auto")
    }
}

object ManualCaptureDraftSerializer {
    fun serialize(params: ManualCaptureParams): String {
        return buildString {
            appendLine("rawEnabled=${params.rawEnabled}")
            appendLine("iso=${params.iso ?: "auto"}")
            appendLine("shutterSpeedMillis=${params.shutterSpeedMillis ?: "auto"}")
            appendLine(
                "exposureCompensationSteps=${params.exposureCompensationSteps ?: "auto"}"
            )
            appendLine(
                "focusDistanceDiopters=${params.focusDistanceDiopters ?: "auto"}"
            )
            appendLine("apertureFNumber=${params.apertureFNumber ?: "auto"}")
            appendLine("whiteBalanceKelvin=${params.whiteBalanceKelvin ?: "auto"}")
        }.trim()
    }

    fun deserialize(serialized: String?): ManualCaptureParams {
        val values = mutableMapOf<String, String>()
        serialized
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.forEach { line ->
                val separatorIndex = line.indexOf('=')
                if (separatorIndex > 0) {
                    values[line.substring(0, separatorIndex)] =
                        line.substring(separatorIndex + 1)
                }
            }
        return ManualCaptureParams(
            rawEnabled = values["rawEnabled"]?.toBooleanStrictOrNull() ?: false,
            iso = values["iso"].decodeAutoInt(),
            shutterSpeedMillis = values["shutterSpeedMillis"].decodeAutoLong(),
            exposureCompensationSteps = values["exposureCompensationSteps"].decodeAutoInt(),
            focusDistanceDiopters = values["focusDistanceDiopters"].decodeAutoFloat(),
            apertureFNumber = values["apertureFNumber"].decodeAutoFloat(),
            whiteBalanceKelvin = values["whiteBalanceKelvin"].decodeAutoInt()
        )
    }
}

private fun String?.decodeAutoInt(): Int? = this?.takeUnless { it == "auto" }?.toIntOrNull()
private fun String?.decodeAutoLong(): Long? = this?.takeUnless { it == "auto" }?.toLongOrNull()
private fun String?.decodeAutoFloat(): Float? = this?.takeUnless { it == "auto" }?.toFloatOrNull()

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
    data class UpdateCountdownDuration(val countdownDuration: CountdownDuration) :
        PersistedSettingsAction
    data class UpdateDefaultVideoSpec(val videoSpec: VideoSpec) : PersistedSettingsAction
    data class UpdateVideoFilter(val filterProfileId: String) : PersistedSettingsAction
    data class UpdateColorLabSpec(val spec: ColorLabSpec) : PersistedSettingsAction
    data class UpdatePhotoStyleStrength(val strength: Float) : PersistedSettingsAction
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
    }
}

fun PhotoSettings.watermarkStyleFor(
    templateId: String
): WatermarkStyleSettings {
    return when (templateId) {
        "travel-polaroid" -> travelPolaroidWatermarkStyle
        "retro-frame" -> retroFrameWatermarkStyle
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
        else -> copy(
            classicOverlayWatermarkStyle = transform(classicOverlayWatermarkStyle)
        )
    }
}

fun defaultFilterRenderSpecOrNull(profileId: String): FilterRenderSpec? {
    return DEFAULT_FILTER_PROFILES.firstOrNull { it.id == profileId }?.renderSpec
}

private fun builtInFilterProfile(
    id: String,
    label: String,
    category: FilterProfileCategory,
    renderSpec: FilterRenderSpec
): FilterProfile {
    return FilterProfile(
        id = id,
        label = label,
        category = category,
        renderSpec = renderSpec
    )
}

private fun renderSpec(
    brightnessShift: Int = 0,
    contrast: Float = 1f,
    saturation: Float = 1f,
    warmthShift: Int = 0,
    tintShift: Int = 0,
    monochromeMix: Float = 0f,
    vignetteStrength: Float = 0f,
    softGlowStrength: Float = 0f,
    haloStrength: Float = 0f,
    grainStrength: Float = 0f,
    sharpnessBoost: Float = 0f,
    highlightCompression: Float = 0f,
    shadowLift: Float = 0f,
    warmBoost: Float = 0f,
    coolBoost: Float = 0f
): FilterRenderSpec {
    return FilterRenderSpec(
        brightnessShift = brightnessShift,
        contrast = contrast,
        saturation = saturation,
        warmthShift = warmthShift,
        tintShift = tintShift,
        monochromeMix = monochromeMix,
        vignetteStrength = vignetteStrength,
        softGlowStrength = softGlowStrength,
        haloStrength = haloStrength,
        grainStrength = grainStrength,
        sharpnessBoost = sharpnessBoost,
        highlightCompression = highlightCompression,
        shadowLift = shadowLift,
        warmBoost = warmBoost,
        coolBoost = coolBoost
    )
}

val DEFAULT_FILTER_PROFILES: List<FilterProfile> = listOf(
    builtInFilterProfile(
        id = "photo-vivid",
        label = "Vivid",
        category = FilterProfileCategory.PHOTO,
        renderSpec = renderSpec(
            contrast = 1.08f,
            saturation = 1.14f
        )
    ),
    builtInFilterProfile(
        id = "photo-original",
        label = "Original",
        category = FilterProfileCategory.PHOTO,
        renderSpec = renderSpec(
            contrast = 1.01f,
            saturation = 1.01f
        )
    ),
    builtInFilterProfile(
        id = "photo-chasing-light",
        label = "Chasing Light",
        category = FilterProfileCategory.PHOTO,
        renderSpec = renderSpec(
            brightnessShift = 9,
            contrast = 1.04f,
            saturation = 1.06f,
            warmthShift = -2
        )
    ),
    builtInFilterProfile(
        id = "photo-rich",
        label = "Rich",
        category = FilterProfileCategory.PHOTO,
        renderSpec = renderSpec(
            contrast = 1.12f,
            saturation = 1.08f,
            warmthShift = 5,
            vignetteStrength = 0.08f
        )
    ),
    builtInFilterProfile(
        id = "photo-texture",
        label = "Texture",
        category = FilterProfileCategory.PHOTO,
        renderSpec = renderSpec(
            brightnessShift = -3,
            contrast = 1.18f,
            saturation = 0.82f,
            warmthShift = 2,
            grainStrength = 0.08f,
            vignetteStrength = 0.08f
        )
    ),
    builtInFilterProfile(
        id = "photo-bw",
        label = "B&W",
        category = FilterProfileCategory.PHOTO,
        renderSpec = renderSpec(
            contrast = 1.12f,
            saturation = 0f,
            monochromeMix = 1f
        )
    ),
    builtInFilterProfile(
        id = "humanistic-original",
        label = "Humanistic Original",
        category = FilterProfileCategory.HUMANISTIC,
        renderSpec = renderSpec(
            contrast = 1.01f,
            saturation = 1.01f
        )
    ),
    builtInFilterProfile(
        id = "humanistic-vivid",
        label = "Humanistic Vivid",
        category = FilterProfileCategory.HUMANISTIC,
        renderSpec = renderSpec(
            contrast = 1.08f,
            saturation = 1.14f,
            warmthShift = 2
        )
    ),
    builtInFilterProfile(
        id = "humanistic-street",
        label = "Humanistic Street",
        category = FilterProfileCategory.HUMANISTIC,
        renderSpec = renderSpec(
            brightnessShift = 9,
            contrast = 1.04f,
            saturation = 1.06f,
            warmthShift = -2
        )
    ),
    builtInFilterProfile(
        id = "humanistic-portrait",
        label = "Humanistic Portrait",
        category = FilterProfileCategory.HUMANISTIC,
        renderSpec = renderSpec(
            brightnessShift = 6,
            contrast = 1.08f,
            saturation = 1.02f,
            warmthShift = 3,
            vignetteStrength = 0.12f
        )
    ),
    builtInFilterProfile(
        id = "humanistic-life",
        label = "Humanistic Life",
        category = FilterProfileCategory.HUMANISTIC,
        renderSpec = renderSpec(
            contrast = 1.12f,
            saturation = 1.08f,
            warmthShift = 5,
            vignetteStrength = 0.08f
        )
    ),
    builtInFilterProfile(
        id = "portrait-blue",
        label = "Portrait Blue",
        category = FilterProfileCategory.PORTRAIT,
        renderSpec = renderSpec(
            brightnessShift = 2,
            contrast = 1.06f,
            saturation = 0.94f,
            warmthShift = -6,
            vignetteStrength = 0.18f
        )
    ),
    builtInFilterProfile(
        id = "portrait-retro",
        label = "Portrait Retro",
        category = FilterProfileCategory.PORTRAIT,
        renderSpec = renderSpec(
            brightnessShift = 4,
            contrast = 1.08f,
            saturation = 0.88f,
            warmthShift = 6,
            monochromeMix = 0.12f,
            vignetteStrength = 0.16f
        )
    ),
    builtInFilterProfile(
        id = "portrait-ccd",
        label = "Portrait CCD",
        category = FilterProfileCategory.PORTRAIT,
        renderSpec = renderSpec(
            brightnessShift = 6,
            contrast = 1.04f,
            saturation = 0.82f,
            warmthShift = 2,
            vignetteStrength = 0.22f
        )
    ),
    builtInFilterProfile(
        id = "portrait-vivid",
        label = "Portrait Vivid",
        category = FilterProfileCategory.PORTRAIT,
        renderSpec = renderSpec(
            contrast = 1.08f,
            saturation = 1.14f,
            warmthShift = 2
        )
    ),
    builtInFilterProfile(
        id = "portrait-original",
        label = "Portrait Original",
        category = FilterProfileCategory.PORTRAIT,
        renderSpec = renderSpec(
            contrast = 1.01f,
            saturation = 1.01f
        )
    ),
    builtInFilterProfile(
        id = "portrait-chasing-light",
        label = "Portrait Chasing Light",
        category = FilterProfileCategory.PORTRAIT,
        renderSpec = renderSpec(
            brightnessShift = 9,
            contrast = 1.04f,
            saturation = 1.06f,
            warmthShift = -2
        )
    ),
    builtInFilterProfile(
        id = "portrait-rich",
        label = "Portrait Rich",
        category = FilterProfileCategory.PORTRAIT,
        renderSpec = renderSpec(
            contrast = 1.12f,
            saturation = 1.08f,
            warmthShift = 5,
            vignetteStrength = 0.08f
        )
    )
)

val DEFAULT_WATERMARK_TEMPLATES: List<WatermarkTemplate> = listOf(
    WatermarkTemplate(
        id = "classic-overlay",
        label = "Classic Overlay",
        tokenKeys = setOf("model", "datetime", "location", "camera-params")
    ),
    WatermarkTemplate(
        id = "travel-polaroid",
        label = "Travel Polaroid",
        tokenKeys = setOf("model", "datetime", "location"),
        supportsFrameBorder = true
    ),
    WatermarkTemplate(
        id = "retro-frame",
        label = "Retro Frame",
        tokenKeys = setOf("model", "datetime", "camera-params"),
        supportsFrameBorder = true
    )
)
