package com.opencamera.app.camera

import androidx.camera.core.ImageCapture
import androidx.camera.camera2.interop.Camera2Interop
import android.hardware.camera2.CaptureRequest
import com.opencamera.core.device.DeviceShotRequest
import com.opencamera.core.device.ManualControlCapabilityMatrix
import com.opencamera.core.device.filterToExecutableCapabilities
import com.opencamera.core.device.supportSummary
import com.opencamera.core.settings.ManualCaptureParams

internal data class Camera2ManualCaptureConfig(
    val rawEnabled: Boolean = false,
    val iso: Int? = null,
    val shutterTimeNanos: Long? = null,
    val exposureCompensationSteps: Int? = null,
    val focusDistanceDiopters: Float? = null,
    val apertureFNumber: Float? = null,
    val whiteBalanceKelvin: Int? = null
)

internal fun resolveCamera2ManualCaptureConfig(
    deviceRequest: DeviceShotRequest
): Camera2ManualCaptureConfig? {
    val params = deviceRequest.manualCaptureParams
        ?.filterToExecutableCapabilities(deviceRequest.manualControlCapabilities)
        ?: return null
    return params.toCamera2ManualCaptureConfig()
}

internal fun ManualCaptureParams.toCamera2ManualCaptureConfig(): Camera2ManualCaptureConfig {
    return Camera2ManualCaptureConfig(
        rawEnabled = rawEnabled,
        iso = iso,
        shutterTimeNanos = shutterSpeedMillis?.times(1_000_000L),
        exposureCompensationSteps = exposureCompensationSteps,
        focusDistanceDiopters = focusDistanceDiopters,
        apertureFNumber = apertureFNumber,
        whiteBalanceKelvin = whiteBalanceKelvin
    )
}

internal fun manualCaptureExecutionDiagnostics(
    requestedParams: ManualCaptureParams?,
    capabilityMatrix: ManualControlCapabilityMatrix
): List<String> {
    if (requestedParams == null || requestedParams.isAuto()) {
        return emptyList()
    }
    val summary = capabilityMatrix.supportSummary(requestedParams)
    return buildList {
        when {
            summary.applied.isNotEmpty() &&
                summary.savedOnly.isEmpty() &&
                summary.unsupported.isEmpty() ->
                add("adapter:manual-request=applied")

            summary.applied.isNotEmpty() ->
                add("adapter:manual-request=partial")

            summary.savedOnly.isNotEmpty() ->
                add("adapter:manual-request=saved-only")

            summary.unsupported.isNotEmpty() ->
                add("adapter:manual-request=unsupported")
        }
        if (summary.applied.isNotEmpty()) {
            add("adapter:manual-applied=${summary.applied.joinToString(separator = "+")}")
        }
        if (summary.savedOnly.isNotEmpty()) {
            add("adapter:manual-saved-only=${summary.savedOnly.joinToString(separator = "+")}")
        }
        if (summary.unsupported.isNotEmpty()) {
            add(
                "adapter:manual-unsupported=${summary.unsupported.joinToString(separator = "+")}"
            )
        }
    }
}

private fun ManualCaptureParams.isAuto(): Boolean {
    return !rawEnabled &&
        iso == null &&
        shutterSpeedMillis == null &&
        exposureCompensationSteps == null &&
        focusDistanceDiopters == null &&
        apertureFNumber == null &&
        whiteBalanceKelvin == null
}

internal fun applyCamera2ManualCaptureConfig(
    builder: ImageCapture.Builder,
    config: Camera2ManualCaptureConfig
) {
    val extender = Camera2Interop.Extender(builder)
    if (config.iso != null || config.shutterTimeNanos != null) {
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_OFF
        )
        config.iso?.let { iso ->
            extender.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
        }
        config.shutterTimeNanos?.let { shutterTimeNanos ->
            extender.setCaptureRequestOption(
                CaptureRequest.SENSOR_EXPOSURE_TIME,
                shutterTimeNanos
            )
        }
    }
    config.exposureCompensationSteps?.let { steps ->
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, steps)
    }
    config.focusDistanceDiopters?.let { focusDistanceDiopters ->
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_OFF
        )
        extender.setCaptureRequestOption(
            CaptureRequest.LENS_FOCUS_DISTANCE,
            focusDistanceDiopters
        )
    }
    config.apertureFNumber?.let { apertureFNumber ->
        extender.setCaptureRequestOption(CaptureRequest.LENS_APERTURE, apertureFNumber)
    }
}
