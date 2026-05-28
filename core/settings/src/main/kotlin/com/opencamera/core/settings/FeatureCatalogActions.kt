package com.opencamera.core.settings

sealed interface FeatureCatalogAction {
    data class UpdateManualRawEnabled(val enabled: Boolean) : FeatureCatalogAction
    data class UpdateManualIso(val iso: Int?) : FeatureCatalogAction
    data class UpdateManualShutterSpeedMillis(val shutterSpeedMillis: Long?) : FeatureCatalogAction
    data class UpdateManualExposureCompensationSteps(
        val exposureCompensationSteps: Int?
    ) : FeatureCatalogAction
    data class UpdateManualFocusDistanceDiopters(
        val focusDistanceDiopters: Float?
    ) : FeatureCatalogAction
    data class UpdateManualApertureFNumber(val apertureFNumber: Float?) : FeatureCatalogAction
    data class UpdateManualWhiteBalanceKelvin(val whiteBalanceKelvin: Int?) : FeatureCatalogAction
}

fun FeatureCatalog.reduce(action: FeatureCatalogAction): FeatureCatalog {
    return when (action) {
        is FeatureCatalogAction.UpdateManualRawEnabled -> copy(
            manualCaptureDraft = manualCaptureDraft.copy(rawEnabled = action.enabled)
        )

        is FeatureCatalogAction.UpdateManualIso -> copy(
            manualCaptureDraft = manualCaptureDraft.copy(iso = action.iso)
        )

        is FeatureCatalogAction.UpdateManualShutterSpeedMillis -> copy(
            manualCaptureDraft = manualCaptureDraft.copy(
                shutterSpeedMillis = action.shutterSpeedMillis
            )
        )

        is FeatureCatalogAction.UpdateManualExposureCompensationSteps -> copy(
            manualCaptureDraft = manualCaptureDraft.copy(
                exposureCompensationSteps = action.exposureCompensationSteps
            )
        )

        is FeatureCatalogAction.UpdateManualFocusDistanceDiopters -> copy(
            manualCaptureDraft = manualCaptureDraft.copy(
                focusDistanceDiopters = action.focusDistanceDiopters
            )
        )

        is FeatureCatalogAction.UpdateManualApertureFNumber -> copy(
            manualCaptureDraft = manualCaptureDraft.copy(
                apertureFNumber = action.apertureFNumber
            )
        )

        is FeatureCatalogAction.UpdateManualWhiteBalanceKelvin -> copy(
            manualCaptureDraft = manualCaptureDraft.copy(
                whiteBalanceKelvin = action.whiteBalanceKelvin
            )
        )
    }
}
