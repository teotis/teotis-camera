package com.opencamera.app

import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.filterProfilesFor
import com.opencamera.core.session.SessionState
import com.opencamera.app.i18n.AppTextResolver

internal data class CheckInScenarioCardRenderModel(
    val scenarioId: String,
    val label: String,
    val description: String,
    val isActive: Boolean,
    val isDegraded: Boolean = false,
    val degradedLabel: String? = null,
    val selectAction: PersistedSettingsAction?
)

internal data class CheckInStyleItemRenderModel(
    val filterProfileId: String,
    val title: String,
    val isSelected: Boolean,
    val bokehLabel: String? = null,
    val selectAction: PersistedSettingsAction?
)

internal data class CheckInStylePanelRenderModel(
    val headline: String,
    val scenarioSummary: String,
    val scenarioCards: List<CheckInScenarioCardRenderModel>,
    val styleItems: List<CheckInStyleItemRenderModel>,
    val compositionGuidance: String,
    val degradationLabel: String?,
    val editingEnabled: Boolean
)

private val CHECK_IN_SCENARIOS = listOf(
    CheckInScenarioDef("portrait"),
    CheckInScenarioDef("people-place"),
    CheckInScenarioDef("object-place"),
    CheckInScenarioDef("clarity")
)

internal fun checkInStylePanelRenderModel(
    state: SessionState,
    text: AppTextResolver
): CheckInStylePanelRenderModel {
    val settings = state.settings.persisted
    val catalog = state.settings.catalog
    val activeScenarioId = settings.photo.defaultCheckInScenario
    val activeStyleId = settings.photo.defaultPortraitFilterProfileId
    val depthSupported = state.activeDeviceCapabilities.supportsPortraitDepthEffect
    val editingEnabled = settingsPageEditingEnabled(state)

    val scenarioCards = CHECK_IN_SCENARIOS.map { scenario ->
        val isActive = scenario.id == activeScenarioId
        val isClarity = scenario.id == "clarity"
        val isDegraded = isClarity && !state.activeDeviceCapabilities.supportsNightMultiFrame
        CheckInScenarioCardRenderModel(
            scenarioId = scenario.id,
            label = scenario.label(text),
            description = scenario.description(text),
            isActive = isActive,
            isDegraded = isDegraded,
            degradedLabel = if (isDegraded) text.get(R.string.checkin_degraded_badge) else null,
            selectAction = if (editingEnabled && !isActive) PersistedSettingsAction.UpdateCheckInScenario(scenario.id) else null
        )
    }

    val activeScenario = CHECK_IN_SCENARIOS.firstOrNull { it.id == activeScenarioId }
        ?: CHECK_IN_SCENARIOS.first()

    val portraitFilters = catalog.filterProfilesFor(FilterProfileCategory.PORTRAIT)
    val styleItems = portraitFilters.map { profile ->
        val isSelected = profile.id == activeStyleId
        CheckInStyleItemRenderModel(
            filterProfileId = profile.id,
            title = profile.localizedLabel(text),
            isSelected = isSelected,
            bokehLabel = if (depthSupported) null else text.get(R.string.label_focus),
            selectAction = if (editingEnabled && !isSelected) PersistedSettingsAction.UpdatePortraitFilter(profile.id) else null
        )
    }

    val scenarioSummary = buildString {
        append(text.get(R.string.checkin_scene_prefix))
        append(activeScenario.label(text))
        if (!depthSupported) append(text.get(R.string.checkin_focus_mode))
    }

    val compositionGuidance = when (activeScenarioId) {
        "portrait" -> text.get(R.string.checkin_guidance_portrait)
        "people-place" -> text.get(R.string.checkin_guidance_people_place)
        "object-place" -> text.get(R.string.checkin_guidance_object_place)
        "clarity" -> text.get(R.string.checkin_guidance_clarity)
        else -> ""
    }

    val degradationLabel = if (!depthSupported) {
        text.get(R.string.checkin_degraded_depth)
    } else if (activeScenarioId == "clarity" && !state.activeDeviceCapabilities.supportsNightMultiFrame) {
        text.get(R.string.checkin_degraded_multiframe)
    } else {
        null
    }

    return CheckInStylePanelRenderModel(
        headline = text.get(R.string.checkin_headline),
        scenarioSummary = scenarioSummary,
        scenarioCards = scenarioCards,
        styleItems = styleItems,
        compositionGuidance = compositionGuidance,
        degradationLabel = degradationLabel,
        editingEnabled = editingEnabled
    )
}

private data class CheckInScenarioDef(
    val id: String
) {
    fun label(text: AppTextResolver): String = when (id) {
        "portrait" -> text.get(R.string.checkin_scenario_portrait)
        "people-place" -> text.get(R.string.checkin_scenario_people_place)
        "object-place" -> text.get(R.string.checkin_scenario_object_place)
        "clarity" -> text.get(R.string.checkin_scenario_clarity)
        else -> id
    }

    fun description(text: AppTextResolver): String = when (id) {
        "portrait" -> text.get(R.string.checkin_scenario_desc_portrait)
        "people-place" -> text.get(R.string.checkin_scenario_desc_people_place)
        "object-place" -> text.get(R.string.checkin_scenario_desc_object_place)
        "clarity" -> text.get(R.string.checkin_scenario_desc_clarity)
        else -> ""
    }
}
