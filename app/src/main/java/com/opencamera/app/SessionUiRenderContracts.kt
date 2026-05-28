package com.opencamera.app

import com.opencamera.core.settings.FeatureCatalogAction
import com.opencamera.core.settings.PersistedSettingsAction

internal data class SessionUiStrings(
    val buttonSwitchToFront: String,
    val buttonSwitchToBack: String,
    val buttonSingleLens: String,
    val buttonZoomPrefix: String,
    val buttonZoomUnavailable: String,
    val buttonStillFast: String,
    val buttonStillMax: String,
    val buttonStillQualityUnavailable: String,
    val buttonStill12Mp: String,
    val buttonStill8Mp: String,
    val buttonStill2Mp: String,
    val buttonStillResolutionUnavailable: String,
    val outputErrorPrefix: String,
    val outputVideoPrefix: String,
    val outputLivePrefix: String,
    val outputSavedPrefix: String,
    val outputPreviewPrefix: String,
    val outputWaiting: String
)

internal enum class SettingsControlAvailability {
    SUPPORTED,
    DEGRADED,
    UNSUPPORTED
}

internal data class SettingsControlRenderModel(
    val label: String,
    val value: String,
    val availability: SettingsControlAvailability = SettingsControlAvailability.SUPPORTED,
    val availabilityLabel: String = "",
    val supportLabel: String? = null,
    val nextAction: PersistedSettingsAction? = null,
    val enabled: Boolean = true,
    val disabledReason: String? = null
) {
    val isInteractive: Boolean
        get() = enabled && availability != SettingsControlAvailability.UNSUPPORTED && nextAction != null

    val buttonLabel: String
        get() = buildString {
            append(label)
            append('\n')
            append(value)
            append('\n')
            append(availabilityLabel.ifEmpty { availability.name.lowercase().replaceFirstChar(Char::titlecase) })
            supportLabel?.let {
                append(" • ")
                append(it)
            }
        }
}

internal data class FeatureCatalogControlRenderModel(
    val label: String,
    val value: String,
    val availability: SettingsControlAvailability = SettingsControlAvailability.SUPPORTED,
    val availabilityLabel: String = "",
    val supportLabel: String? = null,
    val nextAction: FeatureCatalogAction? = null
) {
    val isInteractive: Boolean
        get() = nextAction != null

    val buttonLabel: String
        get() = buildString {
            append(label)
            append('\n')
            append(value)
            append('\n')
            append(availabilityLabel.ifEmpty { availability.name.lowercase().replaceFirstChar(Char::titlecase) })
            supportLabel?.let {
                append(" • ")
                append(it)
            }
        }
}
