package com.opencamera.core.media

data class LivePhotoCaptureSpec(
    val motionDurationMillis: Long = 1_500,
    val motionMimeType: String = "video/mp4",
    val sidecarMimeType: String = "application/vnd.opencamera.live+json"
)

enum class LiveWatermarkResult(
    val storageKey: String,
    val label: String
) {
    STILL_ONLY("still-only", "Still Only"),
    MOTION_METADATA_ONLY("metadata-only", "Motion Metadata Only"),
    MOTION_BURNED_IN("burned-in", "Motion Burned In"),
    UNSUPPORTED("unsupported", "Unsupported");

    companion object {
        fun fromStorageKey(value: String?): LiveWatermarkResult? =
            entries.firstOrNull { it.storageKey == value }
    }
}

data class LivePhotoBundle(
    val stillPath: String,
    val motionPath: String,
    val sidecarPath: String,
    val thumbnailPath: String = stillPath,
    val motionDurationMillis: Long,
    val motionMimeType: String,
    val sidecarMimeType: String,
    val motionHandle: MediaOutputHandle = MediaOutputHandle(displayPath = motionPath),
    val sidecarHandle: MediaOutputHandle = MediaOutputHandle(displayPath = sidecarPath),
    val thumbnailHandle: MediaOutputHandle = MediaOutputHandle(displayPath = thumbnailPath),
    val bundleStatus: LiveBundleStatus = LiveBundleStatus.COMPLETE,
    val temporalWindow: LiveTemporalWindow? = null,
    val watermarkRequested: String? = null,
    val watermarkResult: LiveWatermarkResult? = null,
    val watermarkDegradeReason: String? = null
)

enum class LiveMotionSource {
    PREVIEW_RING_BUFFER,
    VIDEO_RECORDER_SEGMENT,
    POST_SHUTTER_FRAMES,
    METADATA_ONLY
}

enum class LiveBundleStatus {
    COMPLETE,
    DEGRADED_MOTION,
    STILL_ONLY_FALLBACK
}

data class LiveTemporalWindow(
    val requestedDurationMillis: Long,
    val preShutterMillis: Long,
    val postShutterMillis: Long,
    val frameCount: Int,
    val source: LiveMotionSource
)

fun LiveWatermarkResult?.statusLabel(): String? {
    return this?.let { result ->
        when (result) {
            LiveWatermarkResult.STILL_ONLY -> "Watermark: Still Only"
            LiveWatermarkResult.MOTION_METADATA_ONLY -> "Watermark: Metadata Only"
            LiveWatermarkResult.MOTION_BURNED_IN -> "Watermark: Burned In"
            LiveWatermarkResult.UNSUPPORTED -> "Watermark: Unsupported"
        }
    }
}

fun LivePhotoBundle.watermarkStatusLine(): String? {
    val result = watermarkResult ?: return null
    val requestedLabel = watermarkRequested
        ?.replace("-", " ")
        ?.replaceFirstChar { it.uppercase() }
    return buildString {
        append(result.statusLabel())
        requestedLabel?.let { append(" (requested: $it)") }
    }
}

fun LivePhotoBundle.isTemporalMedia(): Boolean {
    return bundleStatus == LiveBundleStatus.COMPLETE ||
        bundleStatus == LiveBundleStatus.DEGRADED_MOTION
}

fun LivePhotoBundle.temporalNotes(): List<String> {
    return buildList {
        add("live:status=${bundleStatus.name.lowercase().replace('_', '-')}")
        temporalWindow?.let { window ->
            add("live:source=${window.source.name.lowercase().replace('_', '-')}")
            add("live:frames=${window.frameCount}")
            add("live:window=-${window.preShutterMillis}ms,+${window.postShutterMillis}ms")
        }
        if (bundleStatus == LiveBundleStatus.STILL_ONLY_FALLBACK) {
            add("live:degraded=metadata-only")
        }
        watermarkRequested?.let { add("live-watermark:requested=$it") }
        watermarkResult?.let { result ->
            add("live-watermark:actual=${result.storageKey}")
            watermarkDegradeReason?.let { add("live-watermark:reason=$it") }
        }
    }
}
