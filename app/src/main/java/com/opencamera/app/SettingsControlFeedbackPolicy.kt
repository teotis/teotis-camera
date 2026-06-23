package com.opencamera.app

import com.opencamera.app.i18n.AppTextResolver

internal fun settingsControlApplyToastMessage(
    control: SettingsControlRenderModel?,
    text: AppTextResolver
): String? {
    return when {
        control == null -> text.get(R.string.settings_not_loaded)
        control.nextAction == null -> text.get(R.string.settings_action_unsupported)
        else -> null
    }
}

internal fun featureCatalogControlApplyToastMessage(
    control: FeatureCatalogControlRenderModel?,
    text: AppTextResolver
): String? {
    return when {
        control == null -> text.get(R.string.settings_not_loaded)
        control.nextAction == null -> text.get(R.string.settings_action_unsupported)
        else -> null
    }
}
