package com.opencamera.core.settings

/**
 * Catalog helpers that project [FeatureCatalog] filter profiles into
 * a grouped, ordered list of [StylePreset] values for a given [StylePresetFamily].
 *
 * The catalog is a pure function of inputs; it does not persist anything.
 */
object StylePresetCatalog {

    /**
     * Build the full style preset library, grouped by family.
     * Each family's presets are ordered by their appearance in [FeatureCatalog.filterProfiles]
     * (which matches the built-in authoring order).
     */
    fun buildPresetLibrary(
        catalog: FeatureCatalog,
        settings: PersistedSettings
    ): Map<StylePresetFamily, List<StylePreset>> {
        return StylePresetFamily.entries.associateWith { family ->
            presetsFor(catalog, settings, family)
        }
    }

    /**
     * Build presets for a single [family], projecting matching [FilterProfile]s
     * from the catalog and marking the currently selected one.
     */
    fun presetsFor(
        catalog: FeatureCatalog,
        settings: PersistedSettings,
        family: StylePresetFamily
    ): List<StylePreset> {
        val selectedId = family.resolveSelectedProfileId(settings)
        return catalog.filterProfiles
            .filter { it.category == family.profileCategory }
            .mapIndexed { index, profile ->
                StylePreset(
                    profileId = profile.id,
                    label = profile.label,
                    family = family,
                    preview = (profile.renderSpec ?: FilterRenderSpec()).toStylePresetPreview(),
                    isSelected = profile.id == selectedId,
                    applyAction = family.createApplyAction(profile.id),
                    sortIndex = index
                )
            }
    }

    /**
     * Get presets for a family, returning an empty list if the family
     * has no matching profiles in the catalog.
     */
    fun presetsForOrNull(
        catalog: FeatureCatalog,
        settings: PersistedSettings,
        family: StylePresetFamily
    ): List<StylePreset> {
        return presetsFor(catalog, settings, family)
    }

    /**
     * Find the selected preset for a given family.
     * Returns null if the selected profile id does not match any catalog profile.
     */
    fun selectedPreset(
        catalog: FeatureCatalog,
        settings: PersistedSettings,
        family: StylePresetFamily
    ): StylePreset? {
        return presetsFor(catalog, settings, family).firstOrNull { it.isSelected }
    }

    /**
     * Resolve the [StylePresetPreview] for a specific profile id,
     * regardless of family. Returns null if the profile is not in the catalog.
     */
    fun previewForProfile(
        catalog: FeatureCatalog,
        profileId: String
    ): StylePresetPreview? {
        return catalog.filterProfileOrNull(profileId)
            ?.renderSpec
            ?.toStylePresetPreview()
    }

    /**
     * Get all unique families that have at least one profile in the catalog.
     */
    fun availableFamilies(catalog: FeatureCatalog): List<StylePresetFamily> {
        val presentCategories = catalog.filterProfiles.map { it.category }.toSet()
        return StylePresetFamily.entries.filter { family ->
            family.profileCategory in presentCategories
        }
    }

    /**
     * Total count of presets across all families.
     */
    fun totalPresetCount(catalog: FeatureCatalog): Int {
        return catalog.filterProfiles.size
    }
}
