package com.opencamera.app

import com.opencamera.core.settings.ColorLabSpec
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PersistedSettingsAction

internal fun colorLabPaletteUpdateAction(
    persisted: PersistedSettings,
    colorAxis: Float,
    toneAxis: Float
): PersistedSettingsAction.UpdateColorLabSpec {
    val current = persisted.photo.colorLabSpec
    return PersistedSettingsAction.UpdateColorLabSpec(
        current.copy(
            colorAxis = colorAxis.coerceIn(-1f, 1f),
            toneAxis = toneAxis.coerceIn(-1f, 1f)
        )
    )
}

internal fun neutralColorLabAction(): PersistedSettingsAction.UpdateColorLabSpec {
    return PersistedSettingsAction.UpdateColorLabSpec(ColorLabSpec())
}
