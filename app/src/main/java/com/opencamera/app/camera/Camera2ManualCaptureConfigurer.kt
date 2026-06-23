package com.opencamera.app.camera

import androidx.camera.core.ImageCapture
import androidx.camera.camera2.interop.Camera2Interop
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import com.opencamera.core.device.DeviceShotRequest
import com.opencamera.core.device.ManualControlCapabilityMatrix
import com.opencamera.core.device.filterToExecutableCapabilities
import com.opencamera.core.device.supportSummary
import com.opencamera.core.settings.ManualCaptureParams
import kotlin.math.ln

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
    config.whiteBalanceKelvin?.let { kelvin ->
        applyWhiteBalanceCorrection(extender, kelvin)
    }
}

/**
 * Disables automatic white balance and applies a colour-temperature correction
 * derived from the requested Kelvin value. Uses the CIE daylight locus
 * approximation to convert correlated colour temperature to an RGB gain ratio
 * that normalises to 1.0 on the green channel.
 *
 */
private fun applyWhiteBalanceCorrection(
    extender: Camera2Interop.Extender<*>,
    kelvin: Int
) {
    extender.setCaptureRequestOption(
        CaptureRequest.CONTROL_AWB_MODE,
        CaptureRequest.CONTROL_AWB_MODE_OFF
    )
    extender.setCaptureRequestOption(
        CaptureRequest.COLOR_CORRECTION_MODE,
        CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
    )
    extender.setCaptureRequestOption(
        CaptureRequest.COLOR_CORRECTION_GAINS,
        kelvinToRggbChannelVector(kelvin)
    )
}

/**
 * Approximate conversion from correlated colour temperature (K) to linear sRGB
 * channel ratios via the CIE daylight locus. The returned values are normalized
 * so the green channel equals 1.0; suitable for use as a Camera2
 * [CaptureRequest.COLOR_CORRECTION_TRANSFORM] matrix diagonal.
 */
internal fun kelvinToRgb(kelvin: Int): FloatArray {
    val temp = kelvin.coerceIn(1000, 40000).toDouble() / 100.0

    val r: Double
    val g: Double
    val b: Double

    when {
        temp <= 66.0 -> {
            r = 255.0
            g = 99.4708025861 * ln(temp) - 161.1195681661
            b = if (temp <= 19.0) 0.0
            else 138.5177312231 * ln(temp - 10.0) - 305.0447927307
        }

        else -> {
            r = 329.698727446 * Math.pow(temp - 60.0, -0.1332047592)
            g = 288.1221695283 * Math.pow(temp - 60.0, -0.0755148492)
            b = 255.0
        }
    }

    val clampedR = r.coerceIn(1.0, 255.0)
    val clampedG = g.coerceIn(1.0, 255.0)
    val clampedB = b.coerceIn(1.0, 255.0)

    val max = maxOf(clampedR, clampedG, clampedB)
    if (max == 0.0) return floatArrayOf(1f, 1f, 1f)

    return floatArrayOf(
        (clampedR / max).toFloat(),
        (clampedG / max).toFloat(),
        (clampedB / max).toFloat()
    )
}

internal fun kelvinToRggbChannelVector(kelvin: Int): RggbChannelVector {
    val gains = kelvinToRggbGains(kelvin)
    return RggbChannelVector(
        gains[0],
        gains[1],
        gains[2],
        gains[3]
    )
}

internal fun kelvinToRggbGains(kelvin: Int): FloatArray {
    val rgb = kelvinToRgb(kelvin)
    val green = rgb[1].coerceAtLeast(0.001f)
    return floatArrayOf(
        rgb[0] / green,
        1f,
        1f,
        rgb[2] / green
    )
}
