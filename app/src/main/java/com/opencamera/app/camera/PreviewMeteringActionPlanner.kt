package com.opencamera.app.camera

import androidx.camera.core.FocusMeteringAction
import com.opencamera.core.device.PreviewMeteringPersistence
import com.opencamera.core.device.PreviewMeteringRequest
import java.util.concurrent.TimeUnit

private const val MIN_PREVIEW_METERING_AUTO_CANCEL_MILLIS = 1L

internal data class PreviewMeteringPixelPoint(
    val x: Float,
    val y: Float
)

internal fun previewMeteringPixelPoint(
    normalizedX: Float,
    normalizedY: Float,
    viewWidth: Int,
    viewHeight: Int
): PreviewMeteringPixelPoint {
    val width = viewWidth.coerceAtLeast(1)
    val height = viewHeight.coerceAtLeast(1)
    return PreviewMeteringPixelPoint(
        x = normalizedX.coerceIn(0f, 1f) * width.toFloat(),
        y = normalizedY.coerceIn(0f, 1f) * height.toFloat()
    )
}

internal fun FocusMeteringAction.Builder.applyPreviewMeteringPersistence(
    request: PreviewMeteringRequest
): FocusMeteringAction.Builder = apply {
    when (request.persistence) {
        PreviewMeteringPersistence.AUTO_CANCEL -> setAutoCancelDuration(
            request.autoCancelMillis.coerceAtLeast(MIN_PREVIEW_METERING_AUTO_CANCEL_MILLIS),
            TimeUnit.MILLISECONDS
        )
        PreviewMeteringPersistence.HOLD_UNTIL_CANCELLED -> disableAutoCancel()
    }
}
