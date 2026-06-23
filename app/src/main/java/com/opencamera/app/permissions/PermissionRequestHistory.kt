package com.opencamera.app.permissions

import android.content.SharedPreferences

class PermissionRequestHistory(private val prefs: SharedPreferences) {

    fun markCameraRequested() {
        prefs.edit().putBoolean(KEY_CAMERA_REQUESTED, true).apply()
    }

    fun markMicrophoneRequested() {
        prefs.edit().putBoolean(KEY_MICROPHONE_REQUESTED, true).apply()
    }

    fun hasRequestedCamera(): Boolean = prefs.getBoolean(KEY_CAMERA_REQUESTED, false)

    fun hasRequestedMicrophone(): Boolean = prefs.getBoolean(KEY_MICROPHONE_REQUESTED, false)

    companion object {
        private const val KEY_CAMERA_REQUESTED = "permission_camera_requested"
        private const val KEY_MICROPHONE_REQUESTED = "permission_microphone_requested"
    }
}
