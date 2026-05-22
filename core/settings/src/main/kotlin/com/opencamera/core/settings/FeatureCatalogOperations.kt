package com.opencamera.core.settings

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
