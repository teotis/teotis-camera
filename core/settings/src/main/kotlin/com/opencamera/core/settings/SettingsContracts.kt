package com.opencamera.core.settings

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
