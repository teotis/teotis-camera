package com.opencamera.app

import android.content.Context
import java.io.File

internal class DevLogExporter(private val context: Context) {
    fun export(content: String, nowMillis: Long = System.currentTimeMillis()): File {
        val dir = context.getExternalFilesDir("debug-logs")
            ?: File(context.filesDir, "debug-logs")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "opencamera-debug-$nowMillis.log")
        file.writeText(content, Charsets.UTF_8)
        return file
    }
}
