package com.opencamera.core.settings

import kotlin.math.abs

/**
 * A user-facing style preset that wraps a [FilterProfile] with enough
 * metadata for a horizontal visual card: preview descriptor, family
 * grouping, selected state, and apply target action.
 *
 * Presets are projection-only; persisted selection fields remain unchanged.
 */
data class StylePreset(
    val profileId: String,
    val label: String,
    val family: StylePresetFamily,
    val preview: StylePresetPreview,
    val isSelected: Boolean,
    val applyAction: PersistedSettingsAction,
    val sortIndex: Int
)

/**
 * Deterministic preview metadata derived from [FilterRenderSpec].
 * The card UI uses these values to render a preview block without
 * requiring bitmap assets or hardcoded color guesses.
 */
data class StylePresetPreview(
    val monochromeLevel: Float,
    val contrastTier: PreviewTier,
    val brightnessTier: PreviewTier,
    val warmthDirection: PreviewWarmth,
    val vignetteStrength: Float,
    val grainStrength: Float,
    val moodDescriptor: StyleMoodDescriptor,
    val rawSaturation: Float = 1f,
    val rawContrast: Float = 1f,
    val rawBrightnessShift: Int = 0,
    val rawWarmthShift: Int = 0
)

/**
 * Structured mood descriptor derived from [FilterRenderSpec].
 * Carries boolean flags for each mood dimension; the display layer
 * resolves these to localized strings via [AppTextResolver.styleCardMoodLabel].
 */
data class StyleMoodDescriptor(
    val isBw: Boolean = false,
    val isMonochrome: Boolean = false,
    val isVivid: Boolean = false,
    val isMuted: Boolean = false,
    val isPunchy: Boolean = false,
    val isSoft: Boolean = false,
    val isWarm: Boolean = false,
    val isCool: Boolean = false,
    val isFilm: Boolean = false,
    val isNatural: Boolean = false
) {
    /** Ordered list of non-default flag names for diagnostic logging. */
    fun activeFlags(): List<String> = buildList {
        if (isBw) add("bw")
        if (isMonochrome) add("monochrome")
        if (isVivid) add("vivid")
        if (isMuted) add("muted")
        if (isPunchy) add("punchy")
        if (isSoft) add("soft")
        if (isWarm) add("warm")
        if (isCool) add("cool")
        if (isFilm) add("film")
        if (isNatural) add("natural")
    }

    /** Legacy English label for backward-compat tests only. */
    fun legacyLabel(): String {
        if (isBw) return "B&W"
        if (isMonochrome) return "Monochrome"
        val parts = mutableListOf<String>()
        if (isVivid) parts.add("Vivid")
        if (isMuted) parts.add("Muted")
        if (isPunchy) parts.add("Punchy")
        if (isSoft) parts.add("Soft")
        if (isWarm) parts.add("Warm")
        if (isCool) parts.add("Cool")
        if (isFilm) parts.add("Film")
        if (parts.isEmpty() || isNatural) return "Natural"
        return parts.joinToString(" ")
    }
}

enum class PreviewTier {
    LOW,
    NEUTRAL,
    HIGH
}

enum class PreviewWarmth {
    COOL,
    NEUTRAL,
    WARM
}

/**
 * Maps a user-facing style family to the [FilterProfileCategory] it
 * draws presets from, the mode key used for persisted selection lookup,
 * and the action constructor for applying a selection.
 */
enum class StylePresetFamily(
    val profileCategory: FilterProfileCategory,
    val modeKey: String,
    val label: String,
    val selectionField: SelectionField
) {
    PHOTO(
        profileCategory = FilterProfileCategory.PHOTO,
        modeKey = "photo",
        label = "Photo",
        selectionField = SelectionField.PHOTO_FILTER
    ),
    CHECK_IN(
        profileCategory = FilterProfileCategory.PHOTO,
        modeKey = "check-in",
        label = "Check-in",
        selectionField = SelectionField.PHOTO_FILTER
    ),
    HUMANISTIC(
        profileCategory = FilterProfileCategory.HUMANISTIC,
        modeKey = "humanistic",
        label = "Humanistic",
        selectionField = SelectionField.HUMANISTIC_FILTER
    ),
    PORTRAIT(
        profileCategory = FilterProfileCategory.PORTRAIT,
        modeKey = "portrait",
        label = "Portrait",
        selectionField = SelectionField.PORTRAIT_FILTER
    ),
    VIDEO(
        profileCategory = FilterProfileCategory.PHOTO,
        modeKey = "video",
        label = "Video",
        selectionField = SelectionField.VIDEO_FILTER
    ),
    DOCUMENT(
        profileCategory = FilterProfileCategory.DOCUMENT,
        modeKey = "document",
        label = "Document",
        selectionField = SelectionField.DOCUMENT_FILTER
    )
}

enum class SelectionField {
    PHOTO_FILTER,
    HUMANISTIC_FILTER,
    PORTRAIT_FILTER,
    VIDEO_FILTER,
    DOCUMENT_FILTER
}

fun StylePresetFamily.resolveSelectedProfileId(settings: PersistedSettings): String {
    return when (selectionField) {
        SelectionField.PHOTO_FILTER -> settings.photo.defaultFilterProfileId
        SelectionField.HUMANISTIC_FILTER -> settings.photo.defaultHumanisticFilterProfileId
        SelectionField.PORTRAIT_FILTER -> settings.photo.defaultPortraitFilterProfileId
        SelectionField.VIDEO_FILTER -> settings.video.defaultFilterProfileId
        SelectionField.DOCUMENT_FILTER -> settings.photo.defaultDocumentFilterProfileId
    }
}

fun StylePresetFamily.createApplyAction(profileId: String): PersistedSettingsAction {
    return when (selectionField) {
        SelectionField.PHOTO_FILTER -> PersistedSettingsAction.UpdatePhotoFilter(profileId)
        SelectionField.HUMANISTIC_FILTER -> PersistedSettingsAction.UpdateHumanisticFilter(profileId)
        SelectionField.PORTRAIT_FILTER -> PersistedSettingsAction.UpdatePortraitFilter(profileId)
        SelectionField.VIDEO_FILTER -> PersistedSettingsAction.UpdateVideoFilter(profileId)
        SelectionField.DOCUMENT_FILTER -> PersistedSettingsAction.UpdateDocumentFilter(profileId)
    }
}

/**
 * Build a [StylePresetPreview] deterministically from a [FilterRenderSpec].
 * No hardcoded UI color guesses; all values derive from the spec.
 */
fun FilterRenderSpec.toStylePresetPreview(): StylePresetPreview {
    val monochromeLevel = monochromeMix.coerceIn(0f, 1f)
    val contrastTier = when {
        contrast >= 1.12f -> PreviewTier.HIGH
        contrast <= 0.96f -> PreviewTier.LOW
        else -> PreviewTier.NEUTRAL
    }
    val brightnessTier = when {
        brightnessShift >= 6 -> PreviewTier.HIGH
        brightnessShift <= -3 -> PreviewTier.LOW
        else -> PreviewTier.NEUTRAL
    }
    val warmthDirection = when {
        warmthShift >= 3 || warmBoost > 0.1f -> PreviewWarmth.WARM
        warmthShift <= -2 || coolBoost > 0.1f -> PreviewWarmth.COOL
        else -> PreviewWarmth.NEUTRAL
    }
    val moodDescriptor = deriveMoodDescriptor(
        monochromeLevel = monochromeLevel,
        saturation = saturation,
        contrast = contrast,
        warmthDirection = warmthDirection,
        grainStrength = grainStrength
    )
    return StylePresetPreview(
        monochromeLevel = monochromeLevel,
        contrastTier = contrastTier,
        brightnessTier = brightnessTier,
        warmthDirection = warmthDirection,
        vignetteStrength = vignetteStrength.coerceIn(0f, 1f),
        grainStrength = grainStrength.coerceIn(0f, 1f),
        moodDescriptor = moodDescriptor,
        rawSaturation = saturation,
        rawContrast = contrast,
        rawBrightnessShift = brightnessShift,
        rawWarmthShift = warmthShift
    )
}

private fun deriveMoodDescriptor(
    monochromeLevel: Float,
    saturation: Float,
    contrast: Float,
    warmthDirection: PreviewWarmth,
    grainStrength: Float
): StyleMoodDescriptor {
    if (saturation <= 0.12f) return StyleMoodDescriptor(isBw = true)
    if (monochromeLevel >= 0.65f) return StyleMoodDescriptor(isMonochrome = true)
    var vivid = false; var muted = false
    var punchy = false; var soft = false
    var warm = false; var cool = false
    when {
        saturation >= 1.12f -> vivid = true
        saturation <= 0.84f -> muted = true
    }
    when {
        contrast >= 1.15f -> punchy = true
        contrast <= 0.96f -> soft = true
    }
    when (warmthDirection) {
        PreviewWarmth.WARM -> warm = true
        PreviewWarmth.COOL -> cool = true
        PreviewWarmth.NEUTRAL -> {}
    }
    val film = grainStrength >= 0.06f
    val isNatural = !vivid && !muted && !punchy && !soft && !warm && !cool && !film
    return StyleMoodDescriptor(
        isVivid = vivid,
        isMuted = muted,
        isPunchy = punchy,
        isSoft = soft,
        isWarm = warm,
        isCool = cool,
        isFilm = film,
        isNatural = isNatural
    )
}
