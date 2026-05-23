package com.opencamera.app

import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.opencamera.app.i18n.AppTextResolver

internal class PermissionUiController(
    private val activity: AppCompatActivity,
    private val permissionStatus: TextView,
    private val text: () -> AppTextResolver
) {
    fun renderGrantedState(cameraGranted: Boolean, microphoneGranted: Boolean) {
        permissionStatus.text = when {
            cameraGranted && microphoneGranted -> activity.getString(R.string.permission_granted)
            cameraGranted -> activity.getString(R.string.permission_camera_only)
            else -> activity.getString(R.string.permission_denied)
        }
        permissionStatus.setOnClickListener(null)
    }

    fun renderPermanentlyDenied() {
        permissionStatus.text = text().permissionPermanentlyDenied()
        permissionStatus.visibility = View.VISIBLE
        permissionStatus.setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
            }
            activity.startActivity(intent)
        }
    }

    fun renderRationalePrompt() {
        permissionStatus.text = activity.getString(R.string.permission_pending)
    }
}
