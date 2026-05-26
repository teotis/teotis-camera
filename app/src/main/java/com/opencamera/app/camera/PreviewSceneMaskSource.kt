package com.opencamera.app.camera

import androidx.camera.core.ImageProxy
import com.opencamera.core.media.SceneMaskDescriptor
import com.opencamera.core.media.SceneMaskQuality
import com.opencamera.core.media.SceneMaskRole
import com.opencamera.core.media.SceneMaskPipelineNotes
import com.opencamera.core.media.SceneMaskSupport
import com.opencamera.core.media.SceneMaskTransform

enum class PreviewSceneMaskCapability {
    READY,
    DEGRADED,
    UNSUPPORTED
}

fun PreviewSceneMaskCapability.toCoreSupport(): SceneMaskSupport = when (this) {
    PreviewSceneMaskCapability.READY -> SceneMaskSupport.SUPPORTED
    PreviewSceneMaskCapability.DEGRADED -> SceneMaskSupport.DEGRADED
    PreviewSceneMaskCapability.UNSUPPORTED -> SceneMaskSupport.UNSUPPORTED
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
    val sourceWidth: Int = width,
    val sourceHeight: Int = height,
    val diagnostics: List<String> = emptyList()
) {
    fun toDescriptor(): SceneMaskDescriptor = SceneMaskDescriptor(
        maskId = "preview-$timestampMillis",
        role = SceneMaskRole.PERSON_SUBJECT,
        quality = SceneMaskQuality.PREVIEW_APPROXIMATE,
        backendId = "mlkit-selfie",
        confidence = 0.5f,
        transform = SceneMaskTransform(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            maskWidth = width,
            maskHeight = height,
            rotationDegrees = rotationDegrees
        ),
        diagnostics = diagnostics
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreviewSceneMaskPayload) return false
        return width == other.width &&
            height == other.height &&
            confidenceMask.contentEquals(other.confidenceMask) &&
            rotationDegrees == other.rotationDegrees &&
            timestampMillis == other.timestampMillis &&
            sourceWidth == other.sourceWidth &&
            sourceHeight == other.sourceHeight
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + confidenceMask.contentHashCode()
        result = 31 * result + rotationDegrees
        result = 31 * result + timestampMillis.hashCode()
        result = 31 * result + sourceWidth
        result = 31 * result + sourceHeight
        return result
    }
}

fun PreviewSceneMaskPayload?.toSnapshot(
    staleThresholdMs: Long = 500
): com.opencamera.core.effect.PreviewSceneMaskSnapshot {
    if (this == null) return com.opencamera.core.effect.PreviewSceneMaskSnapshot.UNAVAILABLE
    val now = System.currentTimeMillis()
    val age = now - timestampMillis
    val isStale = age > staleThresholdMs
    val descriptor = toDescriptor()
    val staleNote = if (isStale) {
        listOf(SceneMaskPipelineNotes.staleMask(age, staleThresholdMs))
    } else {
        emptyList()
    }
    val qualityDescriptor = descriptor.copy(
        diagnostics = descriptor.diagnostics + staleNote
    )
    return com.opencamera.core.effect.PreviewSceneMaskSnapshot(
        descriptor = qualityDescriptor,
        timestampMillis = timestampMillis,
        isStale = isStale,
        backendId = descriptor.backendId,
        quality = if (isStale) SceneMaskQuality.DEGRADED else descriptor.quality,
        isAvailable = !isStale
    )
}

interface PreviewSceneMaskSource {
    val capability: PreviewSceneMaskCapability
    fun start(config: PreviewSceneMaskConfig)
    fun stop(reason: String)
    fun latestMask(): PreviewSceneMaskPayload?
    fun onAnalyzeFrame(image: ImageProxy, rotationDegrees: Int)
}
