package com.opencamera.core.mode

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.settings.FeatureCatalog
import com.opencamera.core.settings.SessionSettingsSnapshot

data class ModeDirectoryDeclaration(
    val catalogProfile: ModeCatalogProfile,
    val defaultStyleLabel: String,
    val declaredSubfeatures: String
)

fun ModeId.modeDirectoryDeclaration(
    deviceCapabilities: DeviceCapabilities,
    settingsSnapshot: SessionSettingsSnapshot
): ModeDirectoryDeclaration {
    val settings = settingsSnapshot.persisted
    val catalog = settingsSnapshot.catalog
    val defaultStyleLabel = when (this) {
        ModeId.PHOTO -> catalog.filterLabelOrId(settings.photo.defaultFilterProfileId)
        ModeId.CHECK_IN -> "Check-in Original"
        ModeId.DOCUMENT -> "Receipt Scan"
        ModeId.HUMANISTIC -> catalog.filterLabelOrId(
            settings.photo.defaultHumanisticFilterProfileId
        )
        ModeId.VIDEO -> settings.video.defaultVideoSpec.summaryLabel
    }

    val declaredSubfeatures = when (this) {
        ModeId.PHOTO -> if (deviceCapabilities.supportsFlashControl) {
            "Flash, frame ratio, Live default, watermark"
        } else {
            "Frame ratio, Live default, watermark"
        }
        ModeId.CHECK_IN -> if (deviceCapabilities.supportsPortraitDepthEffect) {
            "Portrait depth, multi-frame focus bracket, filter, watermark, frame ratio"
        } else {
            "Focus fallback, multi-frame focus bracket, filter, watermark, frame ratio"
        }
        ModeId.DOCUMENT -> "Auto crop, cleanup, watermark"
        ModeId.HUMANISTIC ->
            "Street styles, manual controls, frame ratio, Live default, timer, watermark"
        ModeId.VIDEO -> "Resolution, fps, audio profile, filter"
    }

    return ModeDirectoryDeclaration(
        catalogProfile = catalogProfile(),
        defaultStyleLabel = defaultStyleLabel,
        declaredSubfeatures = declaredSubfeatures
    )
}

private fun FeatureCatalog.filterLabelOrId(filterId: String): String {
    return filterProfileOrNull(filterId)?.label ?: filterId
}
