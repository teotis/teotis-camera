package com.opencamera.app.camera

import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.RecordingQualityPreset
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSpec
import com.opencamera.core.settings.VideoSpecConstraints
import androidx.camera.video.Quality

internal fun orderedRecordingQualities(
    preset: RecordingQualityPreset
): List<Quality> {
    return when (preset) {
        RecordingQualityPreset.UHD -> listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
        RecordingQualityPreset.FHD -> listOf(Quality.FHD, Quality.HD, Quality.SD)
        RecordingQualityPreset.HD -> listOf(Quality.HD, Quality.FHD, Quality.SD)
        RecordingQualityPreset.SD -> listOf(Quality.SD, Quality.HD, Quality.FHD)
    }
}

internal fun targetFrameRateRange(
    videoSpec: VideoSpec
): android.util.Range<Int> {
    val bounds = targetFrameRateBounds(videoSpec)
    return android.util.Range(bounds.first, bounds.last)
}

internal fun targetFrameRateBounds(
    videoSpec: VideoSpec
): IntRange {
    return videoSpec.frameRate.fps..videoSpec.frameRate.fps
}

internal fun buildVideoSpecConstraints(
    standardProfileFrameRates: Map<VideoResolution, Int>,
    highSpeedProfileFrameRates: Map<VideoResolution, Int>,
    fallback: VideoSpecConstraints = DeviceCapabilities.DEFAULT.videoSpecConstraints
): VideoSpecConstraints {
    val mergedFrameRatesByResolution = linkedMapOf<VideoResolution, MutableSet<VideoFrameRate>>()
    standardProfileFrameRates.forEach { (resolution, rawFps) ->
        val supportedFrameRates = mergedFrameRatesByResolution.getOrPut(resolution) { linkedSetOf() }
        supportedFrameRates += standardVideoFrameRatesForProfile(rawFps)
    }
    highSpeedProfileFrameRates.forEach { (resolution, rawFps) ->
        val supportedFrameRates = mergedFrameRatesByResolution.getOrPut(resolution) { linkedSetOf() }
        supportedFrameRates += highSpeedVideoFrameRatesForProfile(rawFps)
    }
    if (mergedFrameRatesByResolution.isEmpty()) {
        return fallback
    }
    val dynamicPolicies = buildSet {
        add(DynamicVideoFpsPolicy.LOCKED)
        if (mergedFrameRatesByResolution.values.any { VideoFrameRate.FPS_24 in it }) {
            add(DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS)
        }
    }
    return VideoSpecConstraints(
        supportedFrameRatesByResolution = mergedFrameRatesByResolution
            .mapValues { (_, frameRates) -> frameRates.sortedBy(VideoFrameRate::ordinal).toSet() },
        dynamicPolicies = dynamicPolicies,
        audioProfiles = fallback.audioProfiles.ifEmpty { setOf(AudioProfile.STANDARD) }
    )
}

private fun standardVideoFrameRatesForProfile(rawFps: Int): Set<VideoFrameRate> {
    return buildSet {
        when {
            rawFps <= 24 -> add(VideoFrameRate.FPS_24)
            rawFps == 25 -> add(VideoFrameRate.FPS_25)
            rawFps in 26..30 -> {
                add(VideoFrameRate.FPS_25)
                add(VideoFrameRate.FPS_30)
            }
            rawFps in 31..59 -> add(VideoFrameRate.FPS_30)
            else -> {
                add(VideoFrameRate.FPS_30)
                add(VideoFrameRate.FPS_60)
            }
        }
    }
}

private fun highSpeedVideoFrameRatesForProfile(rawFps: Int): Set<VideoFrameRate> {
    return buildSet {
        if (rawFps >= 100) {
            add(VideoFrameRate.FPS_100)
        }
        if (rawFps >= 120) {
            add(VideoFrameRate.FPS_120)
        }
    }
}

internal fun detectVideoSpecConstraints(
    cameraId: String,
    characteristics: CameraCharacteristics,
    fallback: VideoSpecConstraints = DeviceCapabilities.DEFAULT.videoSpecConstraints
): VideoSpecConstraints {
    val numericCameraId = cameraId.toIntOrNull() ?: return fallback
    val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    val standardProfileFrameRates = linkedMapOf<VideoResolution, Int>()
    standardVideoQualityMapping().forEach { (resolution, quality) ->
        if (!CamcorderProfile.hasProfile(numericCameraId, quality)) {
            return@forEach
        }
        val profile = runCatching {
            CamcorderProfile.get(numericCameraId, quality)
        }.getOrNull() ?: return@forEach
        if (supportsVideoResolution(streamMap, resolution)) {
            standardProfileFrameRates[resolution] = profile.videoFrameRate
        }
    }
    val highSpeedProfileFrameRates = linkedMapOf<VideoResolution, Int>()
    highSpeedVideoQualityMapping().forEach { (resolution, quality) ->
        if (!CamcorderProfile.hasProfile(numericCameraId, quality)) {
            return@forEach
        }
        val profile = runCatching {
            CamcorderProfile.get(numericCameraId, quality)
        }.getOrNull() ?: return@forEach
        if (supportsVideoResolution(streamMap, resolution)) {
            highSpeedProfileFrameRates[resolution] = profile.videoFrameRate
        }
    }
    return buildVideoSpecConstraints(
        standardProfileFrameRates = standardProfileFrameRates,
        highSpeedProfileFrameRates = highSpeedProfileFrameRates,
        fallback = fallback
    )
}

private fun standardVideoQualityMapping(): Map<VideoResolution, Int> {
    return buildMap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            put(VideoResolution.UHD_8K, CamcorderProfile.QUALITY_8KUHD)
        }
        put(VideoResolution.UHD_4K, CamcorderProfile.QUALITY_2160P)
        put(VideoResolution.FHD_1080P, CamcorderProfile.QUALITY_1080P)
        put(VideoResolution.HD_720P, CamcorderProfile.QUALITY_720P)
        put(VideoResolution.SD_480P, CamcorderProfile.QUALITY_480P)
    }
}

private fun highSpeedVideoQualityMapping(): Map<VideoResolution, Int> {
    return mapOf(
        VideoResolution.UHD_4K to CamcorderProfile.QUALITY_HIGH_SPEED_2160P,
        VideoResolution.FHD_1080P to CamcorderProfile.QUALITY_HIGH_SPEED_1080P,
        VideoResolution.HD_720P to CamcorderProfile.QUALITY_HIGH_SPEED_720P,
        VideoResolution.SD_480P to CamcorderProfile.QUALITY_HIGH_SPEED_480P
    )
}

private fun supportsVideoResolution(
    streamMap: StreamConfigurationMap?,
    resolution: VideoResolution
): Boolean {
    val availableSizes = streamMap?.getOutputSizes(MediaRecorder::class.java).orEmpty()
    if (availableSizes.isEmpty()) {
        return true
    }
    val target = when (resolution) {
        VideoResolution.UHD_8K -> 7680 to 4320
        VideoResolution.UHD_4K -> 3840 to 2160
        VideoResolution.FHD_1080P -> 1920 to 1080
        VideoResolution.HD_720P -> 1280 to 720
        VideoResolution.SD_480P -> 720 to 480
    }
    return availableSizes.any { size ->
        val widthMatches = kotlin.math.abs(size.width - target.first) <= 32
        val heightMatches = kotlin.math.abs(size.height - target.second) <= 32
        widthMatches && heightMatches
    }
}
