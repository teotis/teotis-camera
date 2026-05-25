package com.opencamera.core.media

import com.opencamera.core.settings.LiveSaveFormat

data class LivePhotoCaptureSpec(
    val motionDurationMillis: Long = 1_500,
    val motionMimeType: String = "video/mp4",
    val sidecarMimeType: String = "application/vnd.opencamera.live+json",
    val saveFormat: LiveSaveFormat = LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG
)

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
    val temporalWindow: LiveTemporalWindow? = null
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
    }
}
