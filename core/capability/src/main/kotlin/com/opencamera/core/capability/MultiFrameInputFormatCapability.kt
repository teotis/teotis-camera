package com.opencamera.core.capability

/**
 * Supported multi-frame input formats for fusion pipelines.
 *
 * Each format describes a different pixel data format that can supply
 * frames to a multi-frame fusion processor. The support level depends on
 * both device hardware capability and CameraX/Camera2 API availability.
 */
enum class MultiFrameInputFormat(val tagValue: String, val label: String) {
    /** JPEG frames from ImageCapture burst. Standard night-mode input. */
    JPEG_BURST("jpeg-burst", "JPEG Burst"),
    /** YUV frames from ImageAnalysis. Available on most CameraX 1.x devices. */
    YUV_BURST("yuv-burst", "YUV Burst"),
    /** RAW DNG frames for high-fidelity multi-frame fusion. */
    RAW_DNG("raw-dng", "RAW/DNG"),
    /** Combined JPEG + DNG input: JPEG for preview/alignment, DNG for fusion detail. */
    JPEG_DNG("jpeg-dng", "JPEG+DNG")
}

/**
 * Capability state for a single multi-frame input format.
 */
data class MultiFrameInputFormatCapability(
    val format: MultiFrameInputFormat,
    val support: CapabilitySupport,
    val reason: String,
    val deviceRawCapable: Boolean = false,
    val cameraXApiSupported: Boolean = false
) {
    val isUsable: Boolean
        get() = support == CapabilitySupport.SUPPORTED || support == CapabilitySupport.DEGRADED

    val diagnosticsTag: String
        get() = "mfi:${format.tagValue}=${support.tagValue}"

    fun summary(): String =
        "${format.label}: ${support.tagValue} — $reason"
}

/**
 * Overall capability matrix for all multi-frame input formats.
 * Provides per-format status plus aggregate diagnostics.
 */
data class MultiFrameInputCapabilityMatrix(
    val jpegBurst: MultiFrameInputFormatCapability,
    val yuvBurst: MultiFrameInputFormatCapability,
    val rawDng: MultiFrameInputFormatCapability,
    val jpegDng: MultiFrameInputFormatCapability
) {
    val allFormats: List<MultiFrameInputFormatCapability>
        get() = listOf(jpegBurst, yuvBurst, rawDng, jpegDng)

    val diagnostics: List<String>
        get() = allFormats.map { it.diagnosticsTag }

    val hasRawCapability: Boolean
        get() = rawDng.deviceRawCapable || jpegDng.deviceRawCapable

    fun forFormat(format: MultiFrameInputFormat): MultiFrameInputFormatCapability = when (format) {
        MultiFrameInputFormat.JPEG_BURST -> jpegBurst
        MultiFrameInputFormat.YUV_BURST -> yuvBurst
        MultiFrameInputFormat.RAW_DNG -> rawDng
        MultiFrameInputFormat.JPEG_DNG -> jpegDng
    }

    fun summary(): String = allFormats.joinToString("; ") { it.summary() }
}

/**
 * Device-side query for multi-frame input API availability.
 * Separates device hardware capability from CameraX API version support.
 */
interface MultiFrameInputApiQuery {
    /** Device hardware supports single-frame RAW capture via ImageCapture. */
    fun deviceRawCaptureSupported(): Boolean
    /** CameraX API supports multi-frame RAW burst delivery for fusion. */
    fun cameraXMultiFrameRawSupported(): Boolean
    /** CameraX API supports delivering YUV frames from ImageAnalysis for multi-frame. */
    fun cameraXYuvBurstSupported(): Boolean
    /** CameraX API supports combined JPEG+DNG multi-frame input. */
    fun cameraXJpegDngCombinedSupported(): Boolean
}
