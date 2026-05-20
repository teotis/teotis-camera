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
    return ModeDirectoryDeclaration(
        catalogProfile = catalogProfile(),
        defaultStyleLabel = when (this) {
            ModeId.PHOTO -> catalog.filterLabelOrId(settings.photo.defaultFilterProfileId)

            ModeId.DOCUMENT -> "Receipt Scan"
            ModeId.HUMANISTIC -> catalog.filterLabelOrId(
                settings.photo.defaultHumanisticFilterProfileId
            )
            ModeId.PORTRAIT -> catalog.filterLabelOrId(
                settings.photo.defaultPortraitFilterProfileId
            )

            ModeId.PRO -> "Neutral"
            ModeId.NIGHT -> if (deviceCapabilities.supportsNightMultiFrame) {
                "Handheld"
            } else {
                "Balanced"
            }

            ModeId.VIDEO -> settings.video.defaultVideoSpec.summaryLabel
        },
        declaredSubfeatures = when (this) {
            ModeId.PHOTO -> if (deviceCapabilities.supportsFlashControl) {
                "Flash, frame ratio, Live default, watermark"
            } else {
                "Frame ratio, Live default, watermark"
            }

            ModeId.DOCUMENT -> "Auto crop, cleanup, watermark"
            ModeId.HUMANISTIC ->
                "Street styles, Pro variant, frame ratio, Live default, timer, watermark"
            ModeId.PORTRAIT -> if (deviceCapabilities.supportsPortraitDepthEffect) {
                "Portrait style, Pro variant, depth render, frame ratio"
            } else {
                "Portrait style, Pro variant, focus fallback, frame ratio"
            }

            ModeId.PRO -> "Manual draft, frame ratio, still tuning"
            ModeId.NIGHT -> if (deviceCapabilities.supportsNightMultiFrame) {
                "Scenery style, Pro variant, night fusion, frame ratio"
            } else {
                "Scenery style, Pro variant, brightening fallback, frame ratio"
            }

            ModeId.VIDEO -> "Resolution, fps, audio profile, filter"
        }
    )
}

private fun FeatureCatalog.filterLabelOrId(filterId: String): String {
    return filterProfileOrNull(filterId)?.label ?: filterId
}
