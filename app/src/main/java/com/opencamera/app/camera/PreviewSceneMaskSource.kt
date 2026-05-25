package com.opencamera.app.camera

import androidx.camera.core.ImageProxy

enum class SceneMaskCapability {
    READY,
    DEGRADED,
    UNSUPPORTED
}

data class PreviewSceneMaskConfig(
    val targetWidth: Int = 256,
    val targetHeight: Int = 256,
    val maxFps: Int = 8,
    val backendId: String = "mlkit-selfie"
)

data class PreviewSceneMaskPayload(
    val width: Int,
    val height: Int,
    val confidenceMask: ByteArray,
    val rotationDegrees: Int,
    val timestampMillis: Long,
    val diagnostics: List<String> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreviewSceneMaskPayload) return false
        return width == other.width &&
            height == other.height &&
            confidenceMask.contentEquals(other.confidenceMask) &&
            rotationDegrees == other.rotationDegrees &&
            timestampMillis == other.timestampMillis
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + confidenceMask.contentHashCode()
        result = 31 * result + rotationDegrees
        result = 31 * result + timestampMillis.hashCode()
        return result
    }
}

interface PreviewSceneMaskSource {
    val capability: SceneMaskCapability
    fun start(config: PreviewSceneMaskConfig)
    fun stop(reason: String)
    fun latestMask(): PreviewSceneMaskPayload?
    fun onAnalyzeFrame(image: ImageProxy, rotationDegrees: Int)
}
