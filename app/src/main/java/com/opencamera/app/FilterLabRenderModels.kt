package com.opencamera.app

import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.SessionState
import com.opencamera.core.settings.ColorLabSpec
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.ResetTarget
import com.opencamera.core.settings.applyColorLab
import com.opencamera.core.settings.filterProfilesFor
import com.opencamera.core.settings.hasUserAdjustments
import com.opencamera.app.i18n.AppTextResolver
import com.opencamera.core.settings.FeatureCatalog
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.StylePreset
import com.opencamera.core.settings.StylePresetCatalog
import com.opencamera.core.settings.StylePresetFamily
import com.opencamera.core.settings.StylePresetPreview
import java.util.Locale

// ── Enums ────────────────────────────────────────────────────────────────

internal enum class FilterLabFamily {
    PHOTO,
    HUMANISTIC,
    PORTRAIT,
    VIDEO,
    DOCUMENT
}

internal enum class FilterAdjustmentMode {
    LIGHT,
    ADVANCED
}

internal enum class FilterAdvancedControl {
    EXPOSURE,
    SOFT_GLOW,
    HALO,
    GRAIN,
    SHARPNESS,
    VIGNETTE,
    HIGHLIGHTS,
    SHADOWS,
    WARM_BOOST,
    COOL_BOOST,
    TEMPERATURE_SHIFT,
    TINT_SHIFT
}

internal enum class StyleAndColorLabRole {
    STYLE,
    COLOR_LAB
}

internal enum class StyleSurfaceRole {
    FILTER_STRIP,
    PANEL,
    HIDDEN
}

// ── Data Classes ─────────────────────────────────────────────────────────

internal data class FilterLabTabRenderModel(
    val family: FilterLabFamily,
    val label: String,
    val isSelected: Boolean
)

internal data class FilterLabFilterItemRenderModel(
    val filterProfileId: String,
    val title: String,
    val supportingText: String,
    val isSelected: Boolean,
    val nextAction: PersistedSettingsAction?,
    val adjustButtonLabel: String? = null
)

internal data class FilterLabAdjustRenderModel(
    val buttonLabel: String,
    val family: FilterLabFamily,
    val sourceProfileId: String?,
    val isEnabled: Boolean,
    val willCreateCustomCopy: Boolean
)

internal data class FilterLightPaletteRenderModel(
    val summary: String,
    val supportingText: String
)

internal data class FilterAdvancedControlRenderModel(
    val control: FilterAdvancedControl,
    val buttonLabel: String
)

internal data class FilterAdjustmentPanelRenderModel(
    val isVisible: Boolean,
    val mode: FilterAdjustmentMode,
    val selectedProfileId: String?,
    val selectedProfileLabel: String,
    val renderSpec: FilterRenderSpec,
    val modeToggleLabel: String,
    val lightPalette: FilterLightPaletteRenderModel,
    val advancedControls: List<FilterAdvancedControlRenderModel>,
    val needsAutoPrepare: Boolean = false
)

internal data class FilterStripItemRenderModel(
    val filterProfileId: String,
    val title: String,
    val isSelected: Boolean,
    val selectAction: PersistedSettingsAction?
)

internal data class FilterStripRenderModel(
    val headline: String,
    val items: List<FilterStripItemRenderModel>,
    val isVisible: Boolean
)

internal data class ColorLabPanelRenderModel(
    val title: String,
    val colorAxis: Float,
    val toneAxis: Float,
    val strength: Float,
    val summary: String,
    val resetAction: PersistedSettingsAction.UpdateColorLabSpec,
    val hasUserAdjustments: Boolean = false
)

internal data class FilterLabPageRenderModel(
    override val headline: String,
    override val supportingText: String,
    override val heroSummary: String,
    val currentFilterSummary: String,
    val rosterText: String,
    override val editingEnabled: Boolean,
    override val editingHint: String,
    val panelRole: StyleAndColorLabRole = StyleAndColorLabRole.STYLE,
    val showFamilyTabs: Boolean = true,
    val showFilterItems: Boolean = true,
    val showAdjustmentPanel: Boolean = true,
    val showAdvancedControls: Boolean = true,
    val showModeToggle: Boolean = true,
    val styleStrength: Float = 1f,
    val updateStyleStrengthAction: PersistedSettingsAction? = null,
    val photoTab: FilterLabTabRenderModel,
    val humanisticTab: FilterLabTabRenderModel,
    val portraitTab: FilterLabTabRenderModel,
    val videoTab: FilterLabTabRenderModel,
    val documentTab: FilterLabTabRenderModel,
    val filterItems: List<FilterLabFilterItemRenderModel>,
    val adjustControl: FilterLabAdjustRenderModel,
    val adjustmentPanel: FilterAdjustmentPanelRenderModel,
    val cycleControl: SettingsControlRenderModel,
    val saveCustomControl: FilterLabSaveCustomRenderModel,
    val footer: String,
    val hasStyleUserAdjustments: Boolean = false,
    val resetStyleAction: PersistedSettingsAction.ResetToDefaults? = null,
    val stylePresetCardRail: StylePresetRailRenderModel? = null
) : EditableSettingsPageRenderModel

internal data class FilterLabSaveCustomRenderModel(
    val buttonLabel: String,
    val family: FilterLabFamily,
    val sourceProfileId: String?,
    val isEnabled: Boolean
)

// ── Style card render models ────────────────────────────────────────────

/**
 * Stable card dimensions for the style preset horizontal rail.
 * All values are in dp; the native UI layer maps them to px at runtime.
 */
object StylePresetCardDimensions {
    const val CARD_WIDTH_DP: Int = 72
    const val CARD_HEIGHT_DP: Int = 96
    const val PREVIEW_HEIGHT_DP: Int = 56
    const val LABEL_HEIGHT_DP: Int = 28
    const val ITEM_SPACING_DP: Int = 8
    const val RAIL_HORIZONTAL_PADDING_DP: Int = 16
    const val RAIL_VERTICAL_PADDING_DP: Int = 8
}

/**
 * Single style preset card suitable for a horizontal preset rail.
 * Platform-light: contains only data for the native renderer, no View dependencies.
 */
internal data class StylePresetCardRenderModel(
    val profileId: String,
    val title: String,
    val family: FilterLabFamily,
    val preview: StylePresetPreview,
    val isSelected: Boolean,
    val isEnabled: Boolean,
    val applyAction: PersistedSettingsAction?,
    val moodLabel: String,
    val spec: FilterRenderSpec? = null
)

/**
 * Horizontal rail of style preset cards shown above the mode track
 * in Style role. Empty list means no presets are available for the
 * active family (fallback to the existing text roster).
 */
internal data class StylePresetRailRenderModel(
    val title: String,
    val activeFamily: FilterLabFamily,
    val cards: List<StylePresetCardRenderModel>,
    val isEnabled: Boolean,
    val supportingText: String
)

internal fun filterLabFamilyToStylePresetFamily(family: FilterLabFamily): StylePresetFamily = when (family) {
    FilterLabFamily.PHOTO -> StylePresetFamily.PHOTO
    FilterLabFamily.HUMANISTIC -> StylePresetFamily.HUMANISTIC
    FilterLabFamily.PORTRAIT -> StylePresetFamily.PORTRAIT
    FilterLabFamily.VIDEO -> StylePresetFamily.VIDEO
    FilterLabFamily.DOCUMENT -> StylePresetFamily.DOCUMENT
}

private fun stylePresetCardRail(
    catalog: FeatureCatalog,
    settings: PersistedSettings,
    family: FilterLabFamily,
    editingEnabled: Boolean,
    supported: Boolean,
    text: AppTextResolver
): StylePresetRailRenderModel {
    val presetFamily = filterLabFamilyToStylePresetFamily(family)
    val presets = StylePresetCatalog.presetsFor(catalog, settings, presetFamily)
    val isEnabled = editingEnabled && supported
    val specByProfileId = catalog.filterProfiles.associateBy { it.id }
    return StylePresetRailRenderModel(
        title = text.styleRailTitle(family),
        activeFamily = family,
        cards = presets.map { preset ->
            StylePresetCardRenderModel(
                profileId = preset.profileId,
                title = text.styleCardTitle(preset.profileId, preset.family),
                family = family,
                preview = preset.preview,
                isSelected = preset.isSelected,
                isEnabled = isEnabled,
                applyAction = if (!preset.isSelected) preset.applyAction else null,
                moodLabel = text.styleCardMoodLabel(preset.preview.moodDescriptor),
                spec = specByProfileId[preset.profileId]?.renderSpec
            )
        },
        isEnabled = isEnabled,
        supportingText = text.styleRailSupportingText(family)
    )
}

// ── Shared helpers (consumed by later Check-in / Settings packages) ──────

internal fun defaultFilterLabFamily(activeMode: ModeId): FilterLabFamily {
    return when (activeMode) {
        ModeId.HUMANISTIC -> FilterLabFamily.HUMANISTIC
        ModeId.VIDEO -> FilterLabFamily.VIDEO
        ModeId.DOCUMENT -> FilterLabFamily.DOCUMENT
        ModeId.PHOTO,
        ModeId.CHECK_IN -> FilterLabFamily.PHOTO
    }
}

internal fun FilterProfile.localizedLabel(text: AppTextResolver): String {
    return text.filterProfileLabel(id, label)
}

// ── Internal surface-role dispatch ───────────────────────────────────────

internal fun styleSurfaceRole(activeMode: ModeId): StyleSurfaceRole {
    return when (activeMode) {
        ModeId.PHOTO,
        ModeId.CHECK_IN,
        ModeId.HUMANISTIC,
        ModeId.DOCUMENT -> StyleSurfaceRole.PANEL
        ModeId.VIDEO -> StyleSurfaceRole.FILTER_STRIP
    }
}

// ── Private state / family resolution ────────────────────────────────────

private data class FilterLabFamilyState(
    val family: FilterLabFamily,
    val label: String,
    val currentFilterId: String,
    val filters: List<FilterProfile>,
    val supported: Boolean,
    val unsupportedReason: String,
    val updateAction: (String) -> PersistedSettingsAction
)

private fun filterLabFamilyState(
    state: SessionState,
    text: AppTextResolver,
    selectedFamily: FilterLabFamily
): FilterLabFamilyState {
    val settings = state.settings.persisted
    val catalog = state.settings.catalog
    return when (selectedFamily) {
        FilterLabFamily.PHOTO -> FilterLabFamilyState(
            family = selectedFamily,
            label = text.get(R.string.filter_family_photo),
            currentFilterId = settings.photo.defaultFilterProfileId,
            filters = catalog.photoSettingsFilterProfiles(),
            supported = state.activeDeviceCapabilities.supportsStillCapture,
            unsupportedReason = text.get(R.string.error_still_capture_unavailable),
            updateAction = PersistedSettingsAction::UpdatePhotoFilter
        )

        FilterLabFamily.HUMANISTIC -> FilterLabFamilyState(
            family = selectedFamily,
            label = text.get(R.string.filter_family_humanistic),
            currentFilterId = catalog.humanisticStyleSubitems().let { filters ->
                settings.photo.defaultHumanisticFilterProfileId.takeIf { current ->
                    filters.any { profile -> profile.id == current }
                } ?: filters.firstOrNull()?.id ?: settings.photo.defaultHumanisticFilterProfileId
            },
            filters = catalog.humanisticStyleSubitems(),
            supported = state.activeDeviceCapabilities.supportsStillCapture,
            unsupportedReason = text.get(R.string.error_still_capture_unavailable),
            updateAction = PersistedSettingsAction::UpdateHumanisticFilter
        )

        FilterLabFamily.PORTRAIT -> FilterLabFamilyState(
            family = selectedFamily,
            label = text.get(R.string.filter_family_portrait),
            currentFilterId = settings.photo.defaultPortraitFilterProfileId,
            filters = catalog.filterProfilesFor(FilterProfileCategory.PORTRAIT, includeCustom = true),
            supported = state.activeDeviceCapabilities.supportsStillCapture,
            unsupportedReason = text.get(R.string.error_still_capture_unavailable),
            updateAction = PersistedSettingsAction::UpdatePortraitFilter
        )

        FilterLabFamily.VIDEO -> FilterLabFamilyState(
            family = selectedFamily,
            label = text.get(R.string.filter_family_video),
            currentFilterId = settings.video.defaultFilterProfileId,
            filters = catalog.videoSettingsFilterProfiles(),
            supported = state.activeDeviceCapabilities.supportsVideoRecording,
            unsupportedReason = text.get(R.string.error_video_recording_unavailable),
            updateAction = PersistedSettingsAction::UpdateVideoFilter
        )

        FilterLabFamily.DOCUMENT -> FilterLabFamilyState(
            family = selectedFamily,
            label = text.styleRailTitle(FilterLabFamily.DOCUMENT),
            currentFilterId = settings.photo.defaultDocumentFilterProfileId,
            filters = catalog.filterProfilesFor(FilterProfileCategory.DOCUMENT, includeCustom = false),
            supported = state.activeDeviceCapabilities.supportsStillCapture,
            unsupportedReason = text.get(R.string.error_still_capture_unavailable),
            updateAction = PersistedSettingsAction::UpdateDocumentFilter
        )
    }
}

private fun filterLabTabRenderModel(
    family: FilterLabFamily,
    familyLabel: String,
    selectedFamily: FilterLabFamily
): FilterLabTabRenderModel {
    val label = familyLabel
    return FilterLabTabRenderModel(
        family = family,
        label = label,
        isSelected = family == selectedFamily
    )
}

private fun com.opencamera.core.settings.FeatureCatalog.humanisticStyleSubitems(): List<FilterProfile> {
    val primaryIds = setOf("humanistic-street", "humanistic-portrait", "humanistic-life")
    return filterProfiles.filter { profile ->
        profile.category == FilterProfileCategory.HUMANISTIC &&
            (profile.id in primaryIds || !profile.builtIn)
    }
}

internal fun com.opencamera.core.settings.FeatureCatalog.photoSettingsFilterProfiles(): List<FilterProfile> {
    return filterProfiles.filter { filterProfile ->
        filterProfile.category in setOf(
            FilterProfileCategory.PHOTO,
            FilterProfileCategory.HUMANISTIC,
            FilterProfileCategory.PORTRAIT,
            FilterProfileCategory.CUSTOM
        )
    }
}

internal fun com.opencamera.core.settings.FeatureCatalog.videoSettingsFilterProfiles(): List<FilterProfile> {
    return filterProfiles
}

// ── Simple public builders ───────────────────────────────────────────────

internal fun FilterProfile?.localizedLabel(fallback: String, text: AppTextResolver): String {
    return this?.localizedLabel(text) ?: fallback
}

internal fun filterStripRenderModel(
    state: SessionState,
    text: AppTextResolver
): FilterStripRenderModel {
    val activeMode = state.activeMode
    val family = defaultFilterLabFamily(activeMode)
    val familyState = filterLabFamilyState(state, text, family)
    val items = familyState.filters.map { profile ->
        val isSelected = profile.id == familyState.currentFilterId
        FilterStripItemRenderModel(
            filterProfileId = profile.id,
            title = profile.localizedLabel(text),
            isSelected = isSelected,
            selectAction = if (!isSelected) familyState.updateAction(profile.id) else null
        )
    }
    return FilterStripRenderModel(
        headline = text.get(R.string.style_panel_title),
        items = items,
        isVisible = items.isNotEmpty()
    )
}

internal fun colorLabPanelRenderModel(
    state: SessionState,
    text: AppTextResolver
): ColorLabPanelRenderModel {
    val settings = state.settings.persisted
    val spec = settings.photo.colorLabSpec
    return ColorLabPanelRenderModel(
        title = text.get(R.string.button_color_lab_entry),
        colorAxis = spec.colorAxis,
        toneAxis = spec.toneAxis,
        strength = spec.strength,
        summary = text.colorToneSummary(spec.colorAxis, spec.toneAxis),
        resetAction = PersistedSettingsAction.UpdateColorLabSpec(ColorLabSpec()),
        hasUserAdjustments = settings.hasUserAdjustments(ResetTarget.COLOR_LAB)
    )
}

// ── FilterLab page builder ───────────────────────────────────────────────

internal fun filterLabPageRenderModel(
    state: SessionState,
    text: AppTextResolver,
    selectedFamily: FilterLabFamily = defaultFilterLabFamily(state.activeMode),
    panelRole: StyleAndColorLabRole = StyleAndColorLabRole.STYLE,
    showAdjustmentPanel: Boolean = true,
    adjustmentMode: FilterAdjustmentMode = FilterAdjustmentMode.LIGHT
): FilterLabPageRenderModel {
    val settings = state.settings.persisted
    val editingEnabled = settingsPageEditingEnabled(state)
    val family = filterLabFamilyState(
        state = state,
        text = text,
        selectedFamily = selectedFamily
    )
    val isColorLab = panelRole == StyleAndColorLabRole.COLOR_LAB
    val colorLabModel = colorLabPanelRenderModel(state, text)
    val colorLabRenderSpec = FilterRenderSpec().applyColorLab(settings.photo.colorLabSpec)
    val currentProfile = family.filters.firstOrNull { profile -> profile.id == family.currentFilterId }
    val currentFilterLabel = currentProfile.localizedLabel(family.currentFilterId, text)
    val currentRenderSpec = currentProfile?.renderSpec ?: FilterRenderSpec()
    val cycleAction = if (family.supported) {
        nextListValueOrNull(
            current = family.currentFilterId,
            values = family.filters.map(FilterProfile::id)
        )?.let(family.updateAction)
    } else {
        null
    }
    return FilterLabPageRenderModel(
        headline = when (panelRole) {
            StyleAndColorLabRole.STYLE -> text.get(R.string.style_panel_title)
            StyleAndColorLabRole.COLOR_LAB -> text.get(R.string.color_lab_panel_title)
        },
        supportingText = if (isColorLab) {
            text.get(R.string.filter_lab_color_summary)
        } else {
            text.get(R.string.filter_lab_supporting_text)
        },
        heroSummary = if (isColorLab) text.get(R.string.filter_lab_color_hint) else "",
        currentFilterSummary = if (isColorLab) "" else text.filterLabCurrentDefault(currentFilterLabel),
        rosterText = if (isColorLab) {
            ""
        } else {
            family.filters.joinToString(separator = "\n") { profile ->
                val marker = if (profile.id == family.currentFilterId) "•" else "·"
                val customBadge = if (profile.builtIn) "" else text.get(R.string.status_custom_badge)
                "$marker ${profile.localizedLabel(text)}${customBadge}"
            }
        },
        editingEnabled = editingEnabled,
        editingHint = if (isColorLab) {
            ""
        } else if (editingEnabled) {
            text.get(R.string.filter_lab_editing_enabled)
        } else {
            text.get(R.string.filter_lab_editing_disabled)
        },
        panelRole = panelRole,
        showFamilyTabs = false,
        showFilterItems = panelRole == StyleAndColorLabRole.STYLE,
        showAdjustmentPanel = panelRole == StyleAndColorLabRole.COLOR_LAB,
        showAdvancedControls = false,
        showModeToggle = panelRole == StyleAndColorLabRole.STYLE,
        photoTab = filterLabTabRenderModel(
            family = FilterLabFamily.PHOTO,
            familyLabel = text.get(R.string.filter_family_photo),
            selectedFamily = family.family
        ),
        humanisticTab = filterLabTabRenderModel(
            family = FilterLabFamily.HUMANISTIC,
            familyLabel = text.get(R.string.filter_family_humanistic),
            selectedFamily = family.family
        ),
        portraitTab = filterLabTabRenderModel(
            family = FilterLabFamily.PORTRAIT,
            familyLabel = text.get(R.string.filter_family_portrait),
            selectedFamily = family.family
        ),
        videoTab = filterLabTabRenderModel(
            family = FilterLabFamily.VIDEO,
            familyLabel = text.get(R.string.filter_family_video),
            selectedFamily = family.family
        ),
        documentTab = filterLabTabRenderModel(
            family = FilterLabFamily.DOCUMENT,
            familyLabel = text.styleRailTitle(FilterLabFamily.DOCUMENT),
            selectedFamily = family.family
        ),
        adjustControl = FilterLabAdjustRenderModel(
            buttonLabel = currentFilterLabel,
            family = family.family,
            sourceProfileId = currentProfile?.id,
            isEnabled = editingEnabled && family.supported && currentProfile != null,
            willCreateCustomCopy = currentProfile?.builtIn == true
        ),
        filterItems = if (isColorLab) {
            emptyList()
        } else {
            family.filters.map { profile ->
                val isSelected = profile.id == family.currentFilterId
                val profileLabel = profile.localizedLabel(text)
                FilterLabFilterItemRenderModel(
                    filterProfileId = profile.id,
                    title = profileLabel,
                    supportingText = buildString {
                        append(family.label)
                        if (!profile.builtIn) {
                            append(text.get(R.string.status_custom_badge))
                        }
                        if (isSelected) {
                            append(text.get(R.string.filter_lab_selected_default))
                        }
                    },
                    isSelected = isSelected,
                    nextAction = if (isSelected) {
                        null
                    } else {
                        family.updateAction(profile.id)
                    },
                    adjustButtonLabel = null
                )
            }
        },
        adjustmentPanel = FilterAdjustmentPanelRenderModel(
            isVisible = if (isColorLab) showAdjustmentPanel else showAdjustmentPanel && currentProfile != null && family.supported,
            mode = adjustmentMode,
            selectedProfileId = if (isColorLab) null else currentProfile?.id,
            selectedProfileLabel = if (isColorLab) colorLabModel.summary else currentFilterLabel,
            renderSpec = if (isColorLab) colorLabRenderSpec else currentRenderSpec,
            modeToggleLabel = if (adjustmentMode == FilterAdjustmentMode.LIGHT) {
                text.get(R.string.button_switch_to_advanced)
            } else {
                text.get(R.string.button_switch_to_light)
            },
            lightPalette = FilterLightPaletteRenderModel(
                summary = if (isColorLab) {
                    text.get(R.string.filter_lab_color_summary)
                } else {
                    currentRenderSpec.lightPaletteSummary(text)
                },
                supportingText = if (isColorLab) {
                    text.get(R.string.filter_lab_color_hint)
                } else if (currentProfile?.builtIn == true) {
                    text.get(R.string.filter_lab_drag_to_create_custom)
                } else {
                    text.get(R.string.filter_lab_light_palette_hint)
                }
            ),
            advancedControls = FilterAdvancedControl.entries.map { control ->
                FilterAdvancedControlRenderModel(
                    control = control,
                    buttonLabel = buildString {
                        append(text.filterCtrlLabel(control))
                        append('\n')
                        append(currentRenderSpec.levelLabel(control, text))
                        append('\n')
                        append(text.get(R.string.button_tap_to_cycle))
                    }
                )
            },
            needsAutoPrepare = currentProfile?.builtIn == true
        ),
        cycleControl = SettingsControlRenderModel(
            label = text.filterLabNextLook(family.label),
            value = currentFilterLabel,
            availability = if (family.supported && family.filters.isNotEmpty()) {
                SettingsControlAvailability.SUPPORTED
            } else {
                SettingsControlAvailability.UNSUPPORTED
            },
            availabilityLabel = text.availabilityLabel(if (family.supported && family.filters.isNotEmpty()) {
                SettingsControlAvailability.SUPPORTED
            } else {
                SettingsControlAvailability.UNSUPPORTED
            }),
            supportLabel = when {
                !family.supported -> family.unsupportedReason
                family.filters.isEmpty() -> text.get(R.string.status_no_compatible_looks)
                else -> text.filterLabLooksDeferred(family.filters.size)
            },
            nextAction = cycleAction
        ),
        saveCustomControl = FilterLabSaveCustomRenderModel(
            buttonLabel = if (isColorLab) "" else buildString {
                append(text.get(R.string.button_save_as_custom))
                append('\n')
                append(currentFilterLabel)
                append('\n')
                append(
                    when {
                        currentProfile == null -> text.get(R.string.filter_lab_save_custom_unavailable_profile)
                        !currentProfile.builtIn -> text.get(R.string.filter_lab_save_custom_already_custom)
                        else -> text.filterLabSaveCustomReady(family.label)
                    }
                )
            },
            family = family.family,
            sourceProfileId = if (isColorLab) null else currentProfile?.id,
            isEnabled = !isColorLab && currentProfile?.builtIn == true
        ),
        styleStrength = settings.photo.styleStrength,
        updateStyleStrengthAction = PersistedSettingsAction.UpdatePhotoStyleStrength(
            settings.photo.styleStrength.coerceIn(0f, 1f)
        ),
        footer = if (isColorLab) "" else text.get(R.string.filter_lab_footer),
        hasStyleUserAdjustments = !isColorLab && settings.hasUserAdjustments(ResetTarget.STYLE),
        resetStyleAction = if (!isColorLab && settings.hasUserAdjustments(ResetTarget.STYLE)) {
            PersistedSettingsAction.ResetToDefaults(ResetTarget.STYLE)
        } else {
            null
        },
        stylePresetCardRail = if (panelRole == StyleAndColorLabRole.STYLE && !isColorLab) {
            stylePresetCardRail(
                catalog = state.settings.catalog,
                settings = settings,
                family = family.family,
                editingEnabled = editingEnabled,
                supported = family.supported,
                text = text
            )
        } else null
    )
}

// ── FilterRenderSpec adjustment algorithm ─────────────────────────────────

internal fun FilterRenderSpec.applyLightPalette(
    colorAxis: Float,
    toneAxis: Float
): FilterRenderSpec {
    return applyColorLab(
        com.opencamera.core.settings.ColorLabSpec(
            colorAxis = colorAxis,
            toneAxis = toneAxis
        )
    )
}

internal fun FilterRenderSpec.nextAdvancedControl(
    control: FilterAdvancedControl
): FilterRenderSpec {
    return when (control) {
        FilterAdvancedControl.EXPOSURE -> copy(
            brightnessShift = nextLevel(control).brightnessValue
        )

        FilterAdvancedControl.SOFT_GLOW -> copy(
            softGlowStrength = nextLevel(control).floatValue
        )

        FilterAdvancedControl.HALO -> copy(
            haloStrength = nextLevel(control).floatValue
        )

        FilterAdvancedControl.GRAIN -> copy(
            grainStrength = nextLevel(control).floatValue
        )

        FilterAdvancedControl.SHARPNESS -> copy(
            sharpnessBoost = nextLevel(control).floatValue
        )

        FilterAdvancedControl.VIGNETTE -> copy(
            vignetteStrength = nextLevel(control).floatValue
        )

        FilterAdvancedControl.HIGHLIGHTS -> copy(
            highlightCompression = nextLevel(control).floatValue
        )

        FilterAdvancedControl.SHADOWS -> copy(
            shadowLift = nextLevel(control).floatValue
        )

        FilterAdvancedControl.WARM_BOOST -> nextLevel(control).let { nextLevel ->
            copy(
                warmBoost = nextLevel.floatValue,
                coolBoost = if (nextLevel == FilterAdjustmentLevel.OFF) coolBoost else 0f
            )
        }

        FilterAdvancedControl.COOL_BOOST -> nextLevel(control).let { nextLevel ->
            copy(
                coolBoost = nextLevel.floatValue,
                warmBoost = if (nextLevel == FilterAdjustmentLevel.OFF) warmBoost else 0f
            )
        }

        FilterAdvancedControl.TEMPERATURE_SHIFT -> copy(
            warmthShift = nextWarmthShiftValue(warmthShift)
        )

        FilterAdvancedControl.TINT_SHIFT -> copy(
            tintShift = nextTintShiftValue(tintShift)
        )
    }
}

private fun FilterRenderSpec.nextLevel(
    control: FilterAdvancedControl
): FilterAdjustmentLevel {
    return when (currentLevel(control)) {
        FilterAdjustmentLevel.OFF -> FilterAdjustmentLevel.LOW
        FilterAdjustmentLevel.LOW -> FilterAdjustmentLevel.MEDIUM
        FilterAdjustmentLevel.MEDIUM -> FilterAdjustmentLevel.HIGH
        FilterAdjustmentLevel.HIGH -> FilterAdjustmentLevel.OFF
    }
}

private enum class FilterAdjustmentLevel(
    val floatValue: Float,
    val brightnessValue: Int
) {
    OFF(0f, 0),
    LOW(0.10f, 6),
    MEDIUM(0.20f, 12),
    HIGH(0.30f, 18)
}

private fun FilterRenderSpec.currentLevel(
    control: FilterAdvancedControl
): FilterAdjustmentLevel {
    val value = when (control) {
        FilterAdvancedControl.EXPOSURE -> return nearestBrightnessLevel(brightnessShift)
        FilterAdvancedControl.SOFT_GLOW -> softGlowStrength
        FilterAdvancedControl.HALO -> haloStrength
        FilterAdvancedControl.GRAIN -> grainStrength
        FilterAdvancedControl.SHARPNESS -> sharpnessBoost
        FilterAdvancedControl.VIGNETTE -> vignetteStrength
        FilterAdvancedControl.HIGHLIGHTS -> highlightCompression
        FilterAdvancedControl.SHADOWS -> shadowLift
        FilterAdvancedControl.WARM_BOOST -> warmBoost
        FilterAdvancedControl.COOL_BOOST -> coolBoost
        FilterAdvancedControl.TEMPERATURE_SHIFT,
        FilterAdvancedControl.TINT_SHIFT -> error("Signed adjustments are modeled separately")
    }
    return nearestFloatLevel(value)
}

private fun FilterRenderSpec.levelLabel(control: FilterAdvancedControl, text: AppTextResolver): String {
    return when (control) {
        FilterAdvancedControl.TEMPERATURE_SHIFT -> warmthShiftLevel(warmthShift).labelForTemperature(text)
        FilterAdvancedControl.TINT_SHIFT -> tintShiftLevel(tintShift).labelForTint(text)
        else -> currentLevel(control).resolveLabel(text)
    }
}

private fun FilterAdjustmentLevel.resolveLabel(text: AppTextResolver): String = when (this) {
    FilterAdjustmentLevel.OFF -> text.get(R.string.filter_adjustment_off)
    FilterAdjustmentLevel.LOW -> text.get(R.string.filter_adjustment_low)
    FilterAdjustmentLevel.MEDIUM -> text.get(R.string.filter_adjustment_medium)
    FilterAdjustmentLevel.HIGH -> text.get(R.string.filter_adjustment_high)
}

private fun FilterRenderSpec.lightPaletteSummary(text: AppTextResolver): String {
    return buildString {
        append(text.get(R.string.filter_light_palette_color))
        append(" ")
        append(
            when {
                tintShift >= 4 -> text.get(R.string.color_magenta_warm)
                tintShift <= -4 -> text.get(R.string.color_green_cool)
                warmthShift > 3 || saturation > 1.08f -> text.get(R.string.level_warm_plus)
                warmthShift < -3 || saturation < 0.96f -> text.get(R.string.color_cool_muted)
                else -> text.get(R.string.color_neutral)
            }
        )
        append(" | ")
        append(text.get(R.string.filter_light_palette_tone))
        append(" ")
        append(
            when {
                brightnessShift >= 8 || contrast <= 0.96f -> text.get(R.string.tone_soft_lift)
                brightnessShift <= -4 || contrast >= 1.12f -> text.get(R.string.tone_deep_contrast)
                else -> text.get(R.string.tone_balanced)
            }
        )
    }
}

private fun nearestFloatLevel(value: Float): FilterAdjustmentLevel {
    return FilterAdjustmentLevel.entries.minBy { level ->
        kotlin.math.abs(level.floatValue - value)
    }
}

private fun nextWarmthShiftValue(current: Int): Int {
    return when (warmthShiftLevel(current)) {
        FilterSignedAdjustmentLevel.OFF -> 6
        FilterSignedAdjustmentLevel.LOW_POSITIVE -> 12
        FilterSignedAdjustmentLevel.HIGH_POSITIVE -> -6
        FilterSignedAdjustmentLevel.LOW_NEGATIVE -> -12
        FilterSignedAdjustmentLevel.HIGH_NEGATIVE -> 0
    }
}

private fun warmthShiftLevel(value: Int): FilterSignedAdjustmentLevel {
    return when {
        value >= 10 -> FilterSignedAdjustmentLevel.HIGH_POSITIVE
        value >= 4 -> FilterSignedAdjustmentLevel.LOW_POSITIVE
        value <= -10 -> FilterSignedAdjustmentLevel.HIGH_NEGATIVE
        value <= -4 -> FilterSignedAdjustmentLevel.LOW_NEGATIVE
        else -> FilterSignedAdjustmentLevel.OFF
    }
}

private fun nextTintShiftValue(current: Int): Int {
    return when (tintShiftLevel(current)) {
        FilterSignedAdjustmentLevel.OFF -> 6
        FilterSignedAdjustmentLevel.LOW_POSITIVE -> 12
        FilterSignedAdjustmentLevel.HIGH_POSITIVE -> -6
        FilterSignedAdjustmentLevel.LOW_NEGATIVE -> -12
        FilterSignedAdjustmentLevel.HIGH_NEGATIVE -> 0
    }
}

private fun tintShiftLevel(value: Int): FilterSignedAdjustmentLevel {
    return when {
        value >= 10 -> FilterSignedAdjustmentLevel.HIGH_POSITIVE
        value >= 4 -> FilterSignedAdjustmentLevel.LOW_POSITIVE
        value <= -10 -> FilterSignedAdjustmentLevel.HIGH_NEGATIVE
        value <= -4 -> FilterSignedAdjustmentLevel.LOW_NEGATIVE
        else -> FilterSignedAdjustmentLevel.OFF
    }
}

private enum class FilterSignedAdjustmentLevel {
    OFF,
    LOW_POSITIVE,
    HIGH_POSITIVE,
    LOW_NEGATIVE,
    HIGH_NEGATIVE
}

private fun FilterSignedAdjustmentLevel.labelForTemperature(text: AppTextResolver): String {
    return when (this) {
        FilterSignedAdjustmentLevel.OFF -> text.get(R.string.filter_signed_off)
        FilterSignedAdjustmentLevel.LOW_POSITIVE -> text.get(R.string.filter_signed_warm)
        FilterSignedAdjustmentLevel.HIGH_POSITIVE -> text.get(R.string.filter_signed_warm_plus)
        FilterSignedAdjustmentLevel.LOW_NEGATIVE -> text.get(R.string.filter_signed_cool)
        FilterSignedAdjustmentLevel.HIGH_NEGATIVE -> text.get(R.string.filter_signed_cool_plus)
    }
}

private fun FilterSignedAdjustmentLevel.labelForTint(text: AppTextResolver): String {
    return when (this) {
        FilterSignedAdjustmentLevel.OFF -> text.get(R.string.filter_signed_off)
        FilterSignedAdjustmentLevel.LOW_POSITIVE -> text.get(R.string.filter_signed_magenta)
        FilterSignedAdjustmentLevel.HIGH_POSITIVE -> text.get(R.string.filter_signed_magenta_plus)
        FilterSignedAdjustmentLevel.LOW_NEGATIVE -> text.get(R.string.filter_signed_green)
        FilterSignedAdjustmentLevel.HIGH_NEGATIVE -> text.get(R.string.filter_signed_green_plus)
    }
}

private fun nearestBrightnessLevel(value: Int): FilterAdjustmentLevel {
    return FilterAdjustmentLevel.entries.minBy { level ->
        kotlin.math.abs(level.brightnessValue - value)
    }
}

// ── FilterRenderSpec compact summary (used by FilterLab domain) ──────────

private fun FilterRenderSpec.compactSummary(): String {
    return buildString {
        append("B ")
        append(brightnessShift)
        append(" | C ")
        append(formatFilterMetric(contrast))
        append(" | S ")
        append(formatFilterMetric(saturation))
        append(" | W ")
        append(warmthShift)
        append(" | Mono ")
        append(formatFilterMetric(monochromeMix))
        append(" | Vig ")
        append(formatFilterMetric(vignetteStrength))
        if (tintShift != 0) {
            append(" | Tint ")
            append(tintShift)
        }
        if (haloStrength > 0f) {
            append(" | Halo ")
            append(formatFilterMetric(haloStrength))
        }
    }
}

private fun formatFilterMetric(value: Float): String = String.format(Locale.US, "%.2f", value)
