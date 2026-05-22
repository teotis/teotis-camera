package com.opencamera.core.media

import com.opencamera.core.settings.ManualCaptureParams

enum class MediaType {
    PHOTO,
    VIDEO
}

enum class ShotKind {
    STILL_CAPTURE,
    MULTI_FRAME_CAPTURE,
    LIVE_PHOTO,
    VIDEO_RECORDING
}

enum class ThumbnailPolicy {
    NONE,
    KEEP_PREVIEW_FRAME,
    USE_SAVED_MEDIA
}

enum class FlashMode {
    OFF,
    AUTO,
    ON
}

enum class StillCaptureQualityPreference(
    val tagValue: String,
    val label: String
) {
    LATENCY(
        tagValue = "latency",
        label = "Fast"
    ),
    QUALITY(
        tagValue = "quality",
        label = "Max"
    );

    companion object {
        fun fromTag(value: String?): StillCaptureQualityPreference? {
            return entries.firstOrNull { it.tagValue == value }
        }
    }
}

enum class StillCaptureResolutionPreset(
    val tagValue: String,
    val label: String,
    val targetWidth: Int,
    val targetHeight: Int
) {
    LARGE_12MP(
        tagValue = "12mp",
        label = "12MP",
        targetWidth = 4000,
        targetHeight = 3000
    ),
    MEDIUM_8MP(
        tagValue = "8mp",
        label = "8MP",
        targetWidth = 3264,
        targetHeight = 2448
    ),
    SMALL_2MP(
        tagValue = "2mp",
        label = "2MP",
        targetWidth = 1600,
        targetHeight = 1200
    );

    companion object {
        fun fromTag(value: String?): StillCaptureResolutionPreset? {
            return entries.firstOrNull { it.tagValue == value }
        }
    }
}

enum class FrameRatio(
    val tagValue: String,
    val width: Int,
    val height: Int,
    val label: String
) {
    RATIO_4_3(
        tagValue = "4:3",
        width = 4,
        height = 3,
        label = "4:3"
    ),
    RATIO_16_9(
        tagValue = "16:9",
        width = 16,
        height = 9,
        label = "16:9"
    ),
    RATIO_1_1(
        tagValue = "1:1",
        width = 1,
        height = 1,
        label = "1:1"
    );

    companion object {
        fun fromTag(value: String?): FrameRatio? {
            return entries.firstOrNull { it.tagValue == value }
        }
    }
}

data class MediaMetadata(
    val exifOverrides: Map<String, String> = emptyMap(),
    val watermarkText: String? = null,
    val algorithmProfile: String? = null,
    val customTags: Map<String, String> = emptyMap()
)

data class SaveRequest(
    val relativePath: String,
    val fileNamePrefix: String,
    val fileExtension: String,
    val mimeType: String,
    val metadata: MediaMetadata = MediaMetadata()
) {
    fun buildDisplayName(stamp: String): String = "${fileNamePrefix}_$stamp.$fileExtension"

    companion object {
        fun photoLibrary(
            relativePath: String = "Pictures/OpenCamera",
            fileNamePrefix: String = "OpenCamera",
            metadata: MediaMetadata = MediaMetadata()
        ): SaveRequest {
            return SaveRequest(
                relativePath = relativePath,
                fileNamePrefix = fileNamePrefix,
                fileExtension = "jpg",
                mimeType = "image/jpeg",
                metadata = metadata
            )
        }

        fun videoLibrary(
            relativePath: String = "Movies/OpenCamera",
            fileNamePrefix: String = "OpenCamera",
            metadata: MediaMetadata = MediaMetadata()
        ): SaveRequest {
            return SaveRequest(
                relativePath = relativePath,
                fileNamePrefix = fileNamePrefix,
                fileExtension = "mp4",
                mimeType = "video/mp4",
                metadata = metadata
            )
        }
    }
}

data class PostProcessSpec(
    val watermarkText: String? = null,
    val exifOverrides: Map<String, String> = emptyMap(),
    val algorithmProfile: String? = null
)
