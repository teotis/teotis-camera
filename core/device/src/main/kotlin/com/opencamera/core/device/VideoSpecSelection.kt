package com.opencamera.core.device

import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSpec

data class VideoSceneSignal(
    val isLowLight: Boolean = false,
    val brightnessScore: Float? = null
)

data class ResolvedVideoSpec(
    val requested: VideoSpec,
    val applied: VideoSpec
) {
    val resolutionDegraded: Boolean
        get() = requested.resolution != applied.resolution

    val frameRateDegraded: Boolean
        get() = requested.frameRate != applied.frameRate

    val dynamicPolicyDegraded: Boolean
        get() = requested.dynamicFpsPolicy != applied.dynamicFpsPolicy

    val audioProfileDegraded: Boolean
        get() = requested.audioProfile != applied.audioProfile

    val isDegraded: Boolean
        get() = resolutionDegraded || frameRateDegraded || dynamicPolicyDegraded || audioProfileDegraded
}

fun DeviceCapabilities.resolveVideoSpec(
    requested: VideoSpec
): ResolvedVideoSpec {
    val constraints = videoSpecConstraints
    val availableResolutions = constraints.resolutions
    val resolvedResolution = when {
        requested.resolution in availableResolutions -> requested.resolution
        else -> fallbackVideoResolution(
            requested = requested.resolution,
            available = availableResolutions
        )
    }
    val supportedFrameRates = constraints.frameRatesFor(resolvedResolution)
    val resolvedFrameRate = when {
        requested.frameRate in supportedFrameRates -> requested.frameRate
        else -> fallbackVideoFrameRate(
            requested = requested.frameRate,
            available = supportedFrameRates
        )
    }
    val resolvedDynamicPolicy = requested.dynamicFpsPolicy
        .takeIf { it in constraints.dynamicPolicies }
        ?: DynamicVideoFpsPolicy.LOCKED
    val resolvedAudioProfile = requested.audioProfile.takeIf {
        supportsAudioRecording && it in constraints.audioProfiles
    } ?: AudioProfile.STANDARD
    return ResolvedVideoSpec(
        requested = requested,
        applied = requested.copy(
            resolution = resolvedResolution,
            frameRate = resolvedFrameRate,
            dynamicFpsPolicy = resolvedDynamicPolicy,
            audioProfile = resolvedAudioProfile
        )
    )
}

fun DeviceCapabilities.resolveRuntimeVideoSpec(
    base: VideoSpec,
    sceneSignal: VideoSceneSignal = VideoSceneSignal()
): VideoSpec {
    if (
        base.dynamicFpsPolicy != DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS ||
            !sceneSignal.isLowLight
    ) {
        return base
    }
    val supportedFrameRates = videoSpecConstraints.frameRatesFor(base.resolution)
    if (supportedFrameRates.isEmpty()) {
        return base
    }
    val lowLightFrameRate = if (VideoFrameRate.FPS_24 in supportedFrameRates) {
        VideoFrameRate.FPS_24
    } else {
        fallbackVideoFrameRate(
            requested = VideoFrameRate.FPS_24,
            available = supportedFrameRates
        )
    }
    return base.copy(frameRate = lowLightFrameRate)
}

fun VideoResolution.toRecordingQualityPreset(): RecordingQualityPreset {
    return when (this) {
        VideoResolution.UHD_8K,
        VideoResolution.UHD_4K -> RecordingQualityPreset.UHD
        VideoResolution.FHD_1080P -> RecordingQualityPreset.FHD
        VideoResolution.HD_720P -> RecordingQualityPreset.HD
        VideoResolution.SD_480P -> RecordingQualityPreset.SD
    }
}

fun RecordingQualityPreset.defaultVideoSpec(): VideoSpec {
    return when (this) {
        RecordingQualityPreset.UHD -> VideoSpec(
            resolution = VideoResolution.UHD_4K,
            frameRate = VideoFrameRate.FPS_25
        )
        RecordingQualityPreset.FHD -> VideoSpec(
            resolution = VideoResolution.FHD_1080P,
            frameRate = VideoFrameRate.FPS_30
        )
        RecordingQualityPreset.HD -> VideoSpec(
            resolution = VideoResolution.HD_720P,
            frameRate = VideoFrameRate.FPS_30
        )
        RecordingQualityPreset.SD -> VideoSpec(
            resolution = VideoResolution.SD_480P,
            frameRate = VideoFrameRate.FPS_30
        )
    }
}

private fun fallbackVideoResolution(
    requested: VideoResolution,
    available: Set<VideoResolution>
): VideoResolution {
    val ordered = VideoResolution.entries
    val requestedIndex = ordered.indexOf(requested)
        .takeIf { it >= 0 }
        ?: 0
    for (index in requestedIndex + 1..ordered.lastIndex) {
        val candidate = ordered[index]
        if (candidate in available) {
            return candidate
        }
    }
    for (index in requestedIndex - 1 downTo 0) {
        val candidate = ordered[index]
        if (candidate in available) {
            return candidate
        }
    }
    return available.firstOrNull() ?: requested
}

private fun fallbackVideoFrameRate(
    requested: VideoFrameRate,
    available: Set<VideoFrameRate>
): VideoFrameRate {
    if (available.isEmpty()) {
        return requested
    }
    return available.minWithOrNull(
        compareBy<VideoFrameRate> { kotlin.math.abs(it.fps - requested.fps) }
            .thenByDescending { it.fps <= requested.fps }
            .thenBy { it.fps }
    ) ?: requested
}
