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
    val derivedMoodLabel: String
)

enum class PreviewTier(val label: String) {
    LOW("Soft"),
    NEUTRAL("Neutral"),
    HIGH("Punchy")
}

enum class PreviewWarmth(val label: String) {
    COOL("Cool"),
    NEUTRAL("Neutral"),
    WARM("Warm")
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
    )
}

enum class SelectionField {
    PHOTO_FILTER,
    HUMANISTIC_FILTER,
    PORTRAIT_FILTER,
    VIDEO_FILTER
}

fun StylePresetFamily.resolveSelectedProfileId(settings: PersistedSettings): String {
    return when (selectionField) {
        SelectionField.PHOTO_FILTER -> settings.photo.defaultFilterProfileId
        SelectionField.HUMANISTIC_FILTER -> settings.photo.defaultHumanisticFilterProfileId
        SelectionField.PORTRAIT_FILTER -> settings.photo.defaultPortraitFilterProfileId
        SelectionField.VIDEO_FILTER -> settings.video.defaultFilterProfileId
    }
}

fun StylePresetFamily.createApplyAction(profileId: String): PersistedSettingsAction {
    return when (selectionField) {
        SelectionField.PHOTO_FILTER -> PersistedSettingsAction.UpdatePhotoFilter(profileId)
        SelectionField.HUMANISTIC_FILTER -> PersistedSettingsAction.UpdateHumanisticFilter(profileId)
        SelectionField.PORTRAIT_FILTER -> PersistedSettingsAction.UpdatePortraitFilter(profileId)
        SelectionField.VIDEO_FILTER -> PersistedSettingsAction.UpdateVideoFilter(profileId)
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
    val moodLabel = deriveMoodLabel(
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
        derivedMoodLabel = moodLabel
    )
}

private fun deriveMoodLabel(
    monochromeLevel: Float,
    saturation: Float,
    contrast: Float,
    warmthDirection: PreviewWarmth,
    grainStrength: Float
): String {
    if (saturation <= 0.12f) return "B&W"
    if (monochromeLevel >= 0.65f) return "Monochrome"
    val parts = mutableListOf<String>()
    when {
        saturation >= 1.12f -> parts.add("Vivid")
        saturation <= 0.84f -> parts.add("Muted")
    }
    when {
        contrast >= 1.15f -> parts.add("Punchy")
        contrast <= 0.96f -> parts.add("Soft")
    }
    when (warmthDirection) {
        PreviewWarmth.WARM -> parts.add("Warm")
        PreviewWarmth.COOL -> parts.add("Cool")
        PreviewWarmth.NEUTRAL -> {}
    }
    if (grainStrength >= 0.06f) parts.add("Film")
    if (parts.isEmpty()) return "Natural"
    return parts.joinToString(" ")
}
