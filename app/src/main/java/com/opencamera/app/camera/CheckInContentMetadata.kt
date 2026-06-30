package com.opencamera.app.camera

internal fun effectiveCheckInScenario(customTags: Map<String, String>): String? {
    val contentScenario = customTags["checkInContentScenario"]
        ?.takeIf { it in CHECK_IN_CONTENT_SCENARIOS }
    return contentScenario ?: customTags["checkInScenario"]
}

internal fun contentAwareCheckInProfileName(
    profileName: String?,
    customTags: Map<String, String>
): String? {
    if (!customTags.isCheckInMetadata()) return profileName
    val contentScenario = customTags["checkInContentScenario"]
        ?.takeIf { it in CHECK_IN_CONTENT_SCENARIOS }
        ?: return profileName
    val contentLabel = checkInScenarioLabel(contentScenario) ?: return profileName
    val currentProfile = profileName?.trim().orEmpty()
    if (currentProfile.isBlank()) return contentLabel

    val suffix = CHECK_IN_PROFILE_PREFIXES
        .firstOrNull { currentProfile == it || currentProfile.startsWith("$it ") }
        ?.let { currentProfile.removePrefix(it).trim() }
        ?: currentProfile
    return listOf(contentLabel, suffix)
        .filter { it.isNotBlank() }
        .joinToString(separator = " ")
}

private fun Map<String, String>.isCheckInMetadata(): Boolean =
    this["mode"] == "check-in" ||
        this["watermarkModeName"] == "Check-in" ||
        this.containsKey("checkInScenario")

private fun checkInScenarioLabel(scenarioId: String): String? =
    CHECK_IN_SCENARIO_LABELS[scenarioId]

private val CHECK_IN_SCENARIO_LABELS = mapOf(
    "portrait" to "人像",
    "people-place" to "人景",
    "object-place" to "物景",
    "clarity" to "全清"
)

private val CHECK_IN_PROFILE_PREFIXES = CHECK_IN_SCENARIO_LABELS.values + setOf("超清")

private val CHECK_IN_CONTENT_SCENARIOS = setOf(
    "people-place",
    "object-place",
    "clarity"
)
