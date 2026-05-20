package com.opencamera.core.settings

interface FeatureCatalogStore {
    fun load(): FeatureCatalog

    fun save(catalog: FeatureCatalog)
}

class MapFeatureCatalogStore(
    private val baseCatalog: FeatureCatalog = FeatureCatalog(),
    private var serializedProfiles: String = "",
    private var serializedManualDraft: String = ""
) : FeatureCatalogStore {
    override fun load(): FeatureCatalog {
        val importedProfiles = ImportedFilterProfilesSerializer.deserialize(serializedProfiles)
        return mergeCatalog(
            baseCatalog = baseCatalog,
            importedProfiles = importedProfiles
        ).copy(
            manualCaptureDraft = ManualCaptureDraftSerializer.deserialize(serializedManualDraft)
        )
    }

    override fun save(catalog: FeatureCatalog) {
        val importedProfiles = catalog.filterProfiles.filter { profile ->
            !profile.builtIn && baseCatalog.filterProfileOrNull(profile.id) != profile
        }
        serializedProfiles = ImportedFilterProfilesSerializer.serialize(importedProfiles)
        serializedManualDraft = ManualCaptureDraftSerializer.serialize(catalog.manualCaptureDraft)
    }
}
