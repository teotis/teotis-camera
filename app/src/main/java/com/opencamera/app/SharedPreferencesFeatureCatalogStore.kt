package com.opencamera.app

import android.content.Context
import com.opencamera.core.settings.FeatureCatalog
import com.opencamera.core.settings.FeatureCatalogStore
import com.opencamera.core.settings.ImportedFilterProfilesSerializer
import com.opencamera.core.settings.ManualCaptureDraftSerializer
import com.opencamera.core.settings.mergeCatalog

class SharedPreferencesFeatureCatalogStore(
    context: Context,
    private val baseCatalog: FeatureCatalog = FeatureCatalog()
) : FeatureCatalogStore {
    private val sharedPreferences = context.getSharedPreferences(
        SHARED_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun load(): FeatureCatalog {
        val importedProfiles = ImportedFilterProfilesSerializer.deserialize(
            sharedPreferences.getString(KEY_IMPORTED_FILTER_PROFILES, "").orEmpty()
        )
        return mergeCatalog(
            baseCatalog = baseCatalog,
            importedProfiles = importedProfiles
        ).copy(
            manualCaptureDraft = ManualCaptureDraftSerializer.deserialize(
                sharedPreferences.getString(KEY_MANUAL_CAPTURE_DRAFT, null)
            )
        )
    }

    override fun save(catalog: FeatureCatalog) {
        val importedProfiles = catalog.filterProfiles.filter { profile ->
            !profile.builtIn && baseCatalog.filterProfileOrNull(profile.id) != profile
        }
        sharedPreferences.edit()
            .putString(
                KEY_IMPORTED_FILTER_PROFILES,
                ImportedFilterProfilesSerializer.serialize(importedProfiles)
            )
            .putString(
                KEY_MANUAL_CAPTURE_DRAFT,
                ManualCaptureDraftSerializer.serialize(catalog.manualCaptureDraft)
            )
            .apply()
    }

    private companion object {
        const val SHARED_PREFERENCES_NAME = "open_camera_feature_catalog"
        const val KEY_IMPORTED_FILTER_PROFILES = "imported_filter_profiles"
        const val KEY_MANUAL_CAPTURE_DRAFT = "manual_capture_draft"
    }
}
