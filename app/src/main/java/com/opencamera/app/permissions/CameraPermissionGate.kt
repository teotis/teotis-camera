package com.opencamera.app.permissions

class CameraPermissionGate {

    fun resolve(
        cameraGranted: Boolean,
        hasRequestedCamera: Boolean,
        shouldShowRationale: Boolean,
    ): CameraPermissionState = when {
        cameraGranted -> CameraPermissionState.GRANTED
        !hasRequestedCamera -> CameraPermissionState.NEVER_ASKED
        shouldShowRationale -> CameraPermissionState.SHOW_RATIONALE
        else -> CameraPermissionState.PERMANENTLY_DENIED
    }

    fun actionFor(state: CameraPermissionState): CameraPermissionAction = when (state) {
        CameraPermissionState.NEVER_ASKED,
        CameraPermissionState.SHOW_RATIONALE -> CameraPermissionAction.REQUEST_SYSTEM_PERMISSION

        CameraPermissionState.PERMANENTLY_DENIED -> CameraPermissionAction.OPEN_APPLICATION_SETTINGS
        CameraPermissionState.GRANTED -> CameraPermissionAction.NONE
    }
}

enum class CameraPermissionAction {
    NONE,
    REQUEST_SYSTEM_PERMISSION,
    OPEN_APPLICATION_SETTINGS,
}
