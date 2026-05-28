package com.opencamera.app

import android.content.Context
import java.io.File

internal class DevLogExporter(private val context: Context) {

    fun export(
        content: String,
        type: DevLogTab = DevLogTab.ALL,
        nowMillis: Long = System.currentTimeMillis()
    ): File {
        val dir = logDirectory()
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "opencamera-debug-$nowMillis.log")
        file.writeText("# type: ${type.name}\n$content", Charsets.UTF_8)
        runCatching { pruneToCap(dir) }
        return file
    }

    fun storageSummary(): StorageSummary {
        val dir = logDirectory()
        if (!dir.exists()) return StorageSummary(0L, MAX_STORAGE_BYTES)
        val files = dir.listFiles() ?: return StorageSummary(0L, MAX_STORAGE_BYTES)
        val totalBytes = files.filter { it.isFile && it.extension == "log" }.sumOf { it.length() }
        return StorageSummary(totalBytes, MAX_STORAGE_BYTES)
    }

    fun cleanupByType(type: DevLogTab): Int {
        val dir = logDirectory()
        if (!dir.exists()) return 0
        val files = dir.listFiles()?.filter { it.isFile && it.extension == "log" } ?: return 0
        var deleted = 0
        for (file in files) {
            val header = readTypeHeader(file)
            if (matchesType(header, type)) {
                if (file.delete()) deleted++
            }
        }
        return deleted
    }

    fun cleanupAll(): Int {
        val dir = logDirectory()
        if (!dir.exists()) return 0
        val files = dir.listFiles()?.filter { it.isFile && it.extension == "log" } ?: return 0
        var deleted = 0
        for (file in files) {
            if (file.delete()) deleted++
        }
        return deleted
    }

    private fun logDirectory(): File {
        return context.getExternalFilesDir("debug-logs")
            ?: File(context.filesDir, "debug-logs")
    }

    private fun pruneToCap(dir: File) {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.extension == "log" }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_STORAGE_BYTES) return
        for (file in files.reversed()) {
            if (total <= MAX_STORAGE_BYTES) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun readTypeHeader(file: File): String? {
        return runCatching {
            file.bufferedReader().use { reader ->
                val line = reader.readLine() ?: return@use null
                if (line.startsWith("# type: ")) line.removePrefix("# type: ").trim()
                else null
            }
        }.getOrNull()
    }

    private fun matchesType(header: String?, type: DevLogTab): Boolean {
        if (header == null) return type == DevLogTab.ALL
        return header == type.name
    }

    companion object {
        const val MAX_STORAGE_BYTES = 20L * 1024 * 1024 // 20MB
    }
}

internal data class StorageSummary(
    val usedBytes: Long,
    val capacityBytes: Long
) {
    val usedDisplay: String get() = formatBytes(usedBytes)
    val capacityDisplay: String get() = formatBytes(capacityBytes)
    val usageRatio: Float get() = if (capacityBytes > 0) usedBytes.toFloat() / capacityBytes else 0f

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }
}
