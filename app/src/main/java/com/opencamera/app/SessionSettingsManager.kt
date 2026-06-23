package com.opencamera.app

import com.opencamera.core.settings.FeatureCatalog
import com.opencamera.core.settings.FeatureCatalogAction
import com.opencamera.core.settings.FeatureCatalogStore
import com.opencamera.core.settings.FilterProfileShareCodec
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PersistedSettingsStore
import com.opencamera.core.settings.ResetTarget
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.settings.MapFeatureCatalogStore
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.createCustomFilterProfile
import com.opencamera.core.settings.updateCustomFilterProfile
import com.opencamera.core.settings.reduce
import com.opencamera.core.session.CameraSession
import com.opencamera.core.session.SessionIntent

sealed interface SessionSettingsApplyResult {
    data object Applied : SessionSettingsApplyResult
    data object NoOp : SessionSettingsApplyResult
    data object BlockedByActiveShot : SessionSettingsApplyResult
}

class SessionSettingsManager(
    private val session: CameraSession,
    private val store: PersistedSettingsStore,
    private val catalogStore: FeatureCatalogStore = MapFeatureCatalogStore(FeatureCatalog())
) {
    fun loadSnapshot(): SessionSettingsSnapshot {
        return SessionSettingsSnapshot(
            persisted = store.load(),
            catalog = catalogStore.load()
        )
    }

    suspend fun apply(action: PersistedSettingsAction): SessionSettingsApplyResult {
        return apply(
            session.state.value.settings.persisted.reduce(action)
        )
    }

    suspend fun resetToDefaults(target: ResetTarget): SessionSettingsApplyResult {
        return apply(PersistedSettingsAction.ResetToDefaults(target))
    }

    suspend fun apply(action: FeatureCatalogAction): SessionSettingsApplyResult {
        return apply(
            session.state.value.settings.catalog.reduce(action)
        )
    }

    suspend fun apply(settings: PersistedSettings): SessionSettingsApplyResult {
        if (
            session.state.value.activeShot != null ||
            session.state.value.countdownRemainingSeconds != null
        ) {
            return SessionSettingsApplyResult.BlockedByActiveShot
        }
        if (session.state.value.settings.persisted == settings) {
            return SessionSettingsApplyResult.NoOp
        }
        store.save(settings)
        session.dispatch(
            SessionIntent.SettingsUpdated(
                SessionSettingsSnapshot(
                    persisted = settings,
                    catalog = catalogStore.load()
                )
            )
        )
        return SessionSettingsApplyResult.Applied
    }

    suspend fun apply(catalog: FeatureCatalog): SessionSettingsApplyResult {
        if (
            session.state.value.activeShot != null ||
            session.state.value.countdownRemainingSeconds != null
        ) {
            return SessionSettingsApplyResult.BlockedByActiveShot
        }
        if (session.state.value.settings.catalog == catalog) {
            return SessionSettingsApplyResult.NoOp
        }
        catalogStore.save(catalog)
        session.dispatch(
            SessionIntent.SettingsUpdated(
                SessionSettingsSnapshot(
                    persisted = session.state.value.settings.persisted,
                    catalog = catalog
                )
            )
        )
        return SessionSettingsApplyResult.Applied
    }

    suspend fun importFilterProfile(sharedProfile: String): SessionSettingsApplyResult {
        if (
            session.state.value.activeShot != null ||
            session.state.value.countdownRemainingSeconds != null
        ) {
            return SessionSettingsApplyResult.BlockedByActiveShot
        }
        val importedProfile = FilterProfileShareCodec.import(sharedProfile).copy(builtIn = false)
        val currentCatalog = catalogStore.load()
        val updatedCatalog = currentCatalog.withImportedFilterProfile(importedProfile)
        if (currentCatalog == updatedCatalog) {
            return SessionSettingsApplyResult.NoOp
        }
        catalogStore.save(updatedCatalog)
        session.dispatch(
            SessionIntent.SettingsUpdated(
                SessionSettingsSnapshot(
                    persisted = session.state.value.settings.persisted,
                    catalog = updatedCatalog
                )
            )
        )
        return SessionSettingsApplyResult.Applied
    }

    internal suspend fun prepareFilterForAdjustment(
        family: FilterLabFamily,
        sourceProfileId: String
    ): String? {
        if (
            session.state.value.activeShot != null ||
            session.state.value.countdownRemainingSeconds != null
        ) {
            return null
        }
        val currentCatalog = catalogStore.load()
        val sourceProfile = currentCatalog.filterProfileOrNull(sourceProfileId) ?: return null
        if (!sourceProfile.builtIn) {
            return sourceProfile.id
        }
        val customProfile = currentCatalog.createCustomFilterProfile(sourceProfileId) ?: return null
        val updatedCatalog = currentCatalog.withImportedFilterProfile(customProfile)
        val updatedSettings = session.state.value.settings.persisted.reduce(
            familyDefaultAction(family, customProfile.id)
        )
        catalogStore.save(updatedCatalog)
        store.save(updatedSettings)
        session.dispatch(
            SessionIntent.SettingsUpdated(
                SessionSettingsSnapshot(
                    persisted = updatedSettings,
                    catalog = updatedCatalog
                )
            )
        )
        return customProfile.id
    }

    internal suspend fun saveCurrentFilterAsCustom(
        family: FilterLabFamily,
        sourceProfileId: String
    ): SessionSettingsApplyResult {
        if (
            session.state.value.activeShot != null ||
            session.state.value.countdownRemainingSeconds != null
        ) {
            return SessionSettingsApplyResult.BlockedByActiveShot
        }
        val currentCatalog = catalogStore.load()
        val customProfile = currentCatalog.createCustomFilterProfile(sourceProfileId)
            ?: return SessionSettingsApplyResult.NoOp
        val updatedCatalog = currentCatalog.withImportedFilterProfile(customProfile)
        val currentSettings = session.state.value.settings.persisted
        val updatedSettings = currentSettings.reduce(familyDefaultAction(family, customProfile.id))
        if (updatedCatalog == currentCatalog && updatedSettings == currentSettings) {
            return SessionSettingsApplyResult.NoOp
        }
        catalogStore.save(updatedCatalog)
        store.save(updatedSettings)
        session.dispatch(
            SessionIntent.SettingsUpdated(
                SessionSettingsSnapshot(
                    persisted = updatedSettings,
                    catalog = updatedCatalog
                )
            )
        )
        return SessionSettingsApplyResult.Applied
    }

    internal suspend fun updateCustomFilterRenderSpec(
        filterProfileId: String,
        renderSpec: FilterRenderSpec
    ): SessionSettingsApplyResult {
        if (
            session.state.value.activeShot != null ||
            session.state.value.countdownRemainingSeconds != null
        ) {
            return SessionSettingsApplyResult.BlockedByActiveShot
        }
        val currentCatalog = catalogStore.load()
        val updatedCatalog = currentCatalog.updateCustomFilterProfile(
            profileId = filterProfileId,
            renderSpec = renderSpec
        ) ?: return SessionSettingsApplyResult.NoOp
        if (updatedCatalog == currentCatalog) {
            return SessionSettingsApplyResult.NoOp
        }
        catalogStore.save(updatedCatalog)
        session.dispatch(
            SessionIntent.SettingsUpdated(
                SessionSettingsSnapshot(
                    persisted = session.state.value.settings.persisted,
                    catalog = updatedCatalog
                )
            )
        )
        return SessionSettingsApplyResult.Applied
    }

    private fun familyDefaultAction(
        family: FilterLabFamily,
        filterProfileId: String
    ): PersistedSettingsAction {
        return when (family) {
            FilterLabFamily.PHOTO -> PersistedSettingsAction.UpdatePhotoFilter(filterProfileId)
            FilterLabFamily.HUMANISTIC ->
                PersistedSettingsAction.UpdateHumanisticFilter(filterProfileId)
            FilterLabFamily.PORTRAIT ->
                PersistedSettingsAction.UpdatePortraitFilter(filterProfileId)
            FilterLabFamily.VIDEO -> PersistedSettingsAction.UpdateVideoFilter(filterProfileId)
            FilterLabFamily.DOCUMENT -> PersistedSettingsAction.UpdateDocumentFilter(filterProfileId)
        }
    }
}
