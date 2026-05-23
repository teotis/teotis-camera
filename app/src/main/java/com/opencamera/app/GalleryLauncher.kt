package com.opencamera.app

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

internal class GalleryLauncher(
    private val activity: AppCompatActivity
) {
    fun open(target: GalleryOpenTarget): Boolean {
        val uri = when (target.kind) {
            GalleryOpenUriKind.CONTENT_URI -> Uri.parse(target.uri)
            GalleryOpenUriKind.ABSOLUTE_FILE -> {
                val file = File(target.uri)
                if (!file.exists()) return false
                FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, target.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching { activity.startActivity(intent) }.isSuccess
    }
}
