package com.opencamera.app.camera

import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.FocusStackFrameRole

internal const val DEFAULT_FOCUS_STACK_NEAR_FOCUS_DISTANCE_DIOPTERS = 6.0f
internal const val DEFAULT_FOCUS_STACK_MID_FOCUS_DISTANCE_DIOPTERS = 2.0f
internal const val FOCUS_STACK_FAR_FOCUS_DISTANCE_DIOPTERS = 0.0f

internal fun focusStackManualFocusDistance(
    role: FocusStackFrameRole,
    cameraProfiles: List<CameraLensProfile>,
    preferredLensFacing: LensFacing?
): Float? {
    val nearestSupportedDistance = cameraProfiles
        .filter { profile ->
            preferredLensFacing == null || profile.lensFacing == preferredLensFacing
        }
        .mapNotNull(CameraLensProfile::minimumFocusDistanceDiopters)
        .filter { it > 0f }
        .maxOrNull()
    val nearDistance = nearestSupportedDistance
        ?: DEFAULT_FOCUS_STACK_NEAR_FOCUS_DISTANCE_DIOPTERS
    val midDistance = minOf(
        DEFAULT_FOCUS_STACK_MID_FOCUS_DISTANCE_DIOPTERS,
        nearDistance.coerceAtLeast(DEFAULT_FOCUS_STACK_MID_FOCUS_DISTANCE_DIOPTERS) / 2f
    )
    return when (role) {
        FocusStackFrameRole.NEAR -> nearDistance
        FocusStackFrameRole.MID -> midDistance
        FocusStackFrameRole.FAR -> FOCUS_STACK_FAR_FOCUS_DISTANCE_DIOPTERS
        FocusStackFrameRole.NONE -> null
    }
}
