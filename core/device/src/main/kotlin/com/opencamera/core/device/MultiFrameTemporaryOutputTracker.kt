package com.opencamera.core.device

import java.io.File

class MultiFrameTemporaryOutputTracker {
    private val temporaryOutputs = mutableListOf<File>()

    fun register(file: File?) {
        if (file != null) {
            temporaryOutputs += file
        }
    }

    fun outputPaths(): List<String> = temporaryOutputs.map(File::getAbsolutePath)

    fun cleanup() {
        temporaryOutputs.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
