package com.opencamera.core.settings

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
        label = "街头 Street",
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
        label = "人像 Portrait",
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
        label = "生活 Life",
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
    ),
    WatermarkTemplate(
        id = "pure-text",
        label = "Pure Text",
        tokenKeys = setOf("model", "datetime", "camera-params"),
        supportsFrameBorder = false,
        kind = WatermarkTemplateKind.TEXT_OVERLAY
    ),
    WatermarkTemplate(
        id = "blur-four-border",
        label = "Blur Four Border",
        tokenKeys = setOf("model", "datetime", "location", "camera-params"),
        supportsFrameBorder = true,
        kind = WatermarkTemplateKind.EXPANDED_FRAME,
        allowedPlacements = setOf(
            WatermarkTextPlacement.BOTTOM_LEFT,
            WatermarkTextPlacement.BOTTOM_CENTER,
            WatermarkTextPlacement.BOTTOM_RIGHT
        ),
        allowedFrameBackgrounds = setOf(
            WatermarkFrameBackground.SOURCE_BLUR,
            WatermarkFrameBackground.SOURCE_LIGHT_BLUR,
            WatermarkFrameBackground.SOURCE_VIVID_BLUR
        )
    )
)
