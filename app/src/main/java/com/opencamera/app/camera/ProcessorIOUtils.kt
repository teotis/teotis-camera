package com.opencamera.app.camera

import android.content.ContentResolver
import android.net.Uri
import com.opencamera.core.media.ProcessorTarget
import java.io.File

object ProcessorIOUtils {
    fun readSourceBytes(target: ProcessorTarget, contentResolver: ContentResolver): ByteArray? {
        return when (target) {
            is ProcessorTarget.FilePath -> {
                val file = File(target.path)
                if (!file.exists()) null else file.readBytes()
            }
            is ProcessorTarget.ContentUri -> {
                contentResolver.openInputStream(Uri.parse(target.value))?.use { it.readBytes() }
            }
        }
    }
}
