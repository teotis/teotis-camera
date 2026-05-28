package com.opencamera.core.device

import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSpec
import com.opencamera.core.settings.VideoSpecConstraints

data class VideoQuickSpecOption(
    val resolution: VideoResolution,
    val frameRate: VideoFrameRate
) {
    val spec: VideoSpec
        get() = VideoSpec(resolution = resolution, frameRate = frameRate)

    val label: String
        get() = "${resolution.quickLabel}${frameRate.fps}"
}

val VideoResolution.quickLabel: String
    get() = when (this) {
        VideoResolution.UHD_8K -> "8K"
        VideoResolution.UHD_4K -> "4K"
        VideoResolution.FHD_1080P -> "1080p"
        VideoResolution.HD_720P -> "720p"
        VideoResolution.SD_480P -> "480p"
    }

fun VideoSpecConstraints.quickVideoSpecOptions(): List<VideoQuickSpecOption> {
    val preferred = listOf(
        VideoQuickSpecOption(VideoResolution.UHD_4K, VideoFrameRate.FPS_30),
        VideoQuickSpecOption(VideoResolution.UHD_4K, VideoFrameRate.FPS_60),
        VideoQuickSpecOption(VideoResolution.UHD_4K, VideoFrameRate.FPS_25),
        VideoQuickSpecOption(VideoResolution.FHD_1080P, VideoFrameRate.FPS_60),
        VideoQuickSpecOption(VideoResolution.FHD_1080P, VideoFrameRate.FPS_30),
        VideoQuickSpecOption(VideoResolution.FHD_1080P, VideoFrameRate.FPS_25),
        VideoQuickSpecOption(VideoResolution.HD_720P, VideoFrameRate.FPS_60),
        VideoQuickSpecOption(VideoResolution.HD_720P, VideoFrameRate.FPS_30),
        VideoQuickSpecOption(VideoResolution.HD_720P, VideoFrameRate.FPS_25),
        VideoQuickSpecOption(VideoResolution.UHD_8K, VideoFrameRate.FPS_30),
        VideoQuickSpecOption(VideoResolution.UHD_8K, VideoFrameRate.FPS_25),
        VideoQuickSpecOption(VideoResolution.UHD_8K, VideoFrameRate.FPS_60),
        VideoQuickSpecOption(VideoResolution.UHD_4K, VideoFrameRate.FPS_120),
        VideoQuickSpecOption(VideoResolution.UHD_4K, VideoFrameRate.FPS_100),
        VideoQuickSpecOption(VideoResolution.FHD_1080P, VideoFrameRate.FPS_120),
        VideoQuickSpecOption(VideoResolution.FHD_1080P, VideoFrameRate.FPS_100),
        VideoQuickSpecOption(VideoResolution.HD_720P, VideoFrameRate.FPS_120),
        VideoQuickSpecOption(VideoResolution.HD_720P, VideoFrameRate.FPS_100),
        VideoQuickSpecOption(VideoResolution.SD_480P, VideoFrameRate.FPS_30),
        VideoQuickSpecOption(VideoResolution.SD_480P, VideoFrameRate.FPS_25)
    )
    val supported = preferred.filter { option ->
        option.frameRate in frameRatesFor(option.resolution)
    }
    val extra = supportedFrameRatesByResolution
        .flatMap { (resolution, rates) ->
            rates.map { rate -> VideoQuickSpecOption(resolution, rate) }
        }
        .filterNot { option -> preferred.any { it == option } }
        .sortedWith(
            compareBy<VideoQuickSpecOption> { it.resolution.ordinal }
                .thenBy { it.frameRate.ordinal }
        )
    return (supported + extra).distinct()
}

fun VideoSpecConstraints.nextQuickVideoSpec(
    current: VideoSpec,
    preserve: VideoSpec = current
): VideoSpec? {
    val options = quickVideoSpecOptions()
    if (options.isEmpty()) return null
    val currentIndex = options.indexOfFirst {
        it.resolution == current.resolution && it.frameRate == current.frameRate
    }
    val next = options[(currentIndex + 1).mod(options.size)]
    return preserve.copy(
        resolution = next.resolution,
        frameRate = next.frameRate
    )
}

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
