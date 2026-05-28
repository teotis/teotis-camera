package com.opencamera.app

import android.view.OrientationEventListener
import com.opencamera.core.device.CameraOutputRotation

internal enum class CameraPhysicalOrientation {
    PORTRAIT,
    LANDSCAPE_LEFT,
    REVERSE_PORTRAIT,
    LANDSCAPE_RIGHT
}

internal data class CameraOrientationRenderModel(
    val physicalOrientation: CameraPhysicalOrientation,
    val contentRotationDegrees: Float,
    val outputRotation: CameraOutputRotation
)

internal fun orientationBucketFromDegrees(degrees: Int): CameraPhysicalOrientation? {
    if (degrees == OrientationEventListener.ORIENTATION_UNKNOWN) return null
    val normalized = (degrees + 360) % 360
    return when {
        normalized >= 315 || normalized < 45 -> CameraPhysicalOrientation.PORTRAIT
        normalized in 45..134 -> CameraPhysicalOrientation.LANDSCAPE_LEFT
        normalized in 135..224 -> CameraPhysicalOrientation.REVERSE_PORTRAIT
        else -> CameraPhysicalOrientation.LANDSCAPE_RIGHT
    }
}

internal fun orientationRenderModelFromBucket(
    bucket: CameraPhysicalOrientation
): CameraOrientationRenderModel = when (bucket) {
    CameraPhysicalOrientation.PORTRAIT -> CameraOrientationRenderModel(
        physicalOrientation = CameraPhysicalOrientation.PORTRAIT,
        contentRotationDegrees = 0f,
        outputRotation = CameraOutputRotation.ROTATION_0
    )
    CameraPhysicalOrientation.LANDSCAPE_LEFT -> CameraOrientationRenderModel(
        physicalOrientation = CameraPhysicalOrientation.LANDSCAPE_LEFT,
        contentRotationDegrees = 90f,
        outputRotation = CameraOutputRotation.ROTATION_90
    )
    CameraPhysicalOrientation.REVERSE_PORTRAIT -> CameraOrientationRenderModel(
        physicalOrientation = CameraPhysicalOrientation.REVERSE_PORTRAIT,
        contentRotationDegrees = 180f,
        outputRotation = CameraOutputRotation.ROTATION_180
    )
    CameraPhysicalOrientation.LANDSCAPE_RIGHT -> CameraOrientationRenderModel(
        physicalOrientation = CameraPhysicalOrientation.LANDSCAPE_RIGHT,
        contentRotationDegrees = -90f,
        outputRotation = CameraOutputRotation.ROTATION_270
    )
}
