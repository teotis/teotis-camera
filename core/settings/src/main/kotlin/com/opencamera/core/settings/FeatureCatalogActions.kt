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
