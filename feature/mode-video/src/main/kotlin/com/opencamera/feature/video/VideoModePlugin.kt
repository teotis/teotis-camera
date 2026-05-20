package com.opencamera.feature.video

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.resolveVideoSpec
import com.opencamera.core.effect.EffectBridge
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.mode.CameraModePlugin
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeIntent
import com.opencamera.core.mode.ModeSessionEvent
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.VideoResolution

class VideoModePlugin : CameraModePlugin {
    override val id: ModeId = ModeId.VIDEO

    override fun isSupported(deviceCapabilities: DeviceCapabilities): Boolean {
        return deviceCapabilities.supportsVideoRecording
    }

    override fun create(context: ModeContext): ModeController {
        return VideoModeController(context)
    }
}

private class VideoModeController(
    private val context: ModeContext
) : ModeController {
    private var requestedVideoSpec = context.settingsSnapshot.persisted.video.defaultVideoSpec
    private var isRecording = false
    private var torchEnabled = false

    private val mutableSnapshot = MutableStateFlow(
        buildSnapshot(
            headline = "Video pipeline ready"
        )
    )

    override val id: ModeId = ModeId.VIDEO
    override val snapshot: StateFlow<ModeSnapshot> = mutableSnapshot.asStateFlow()

    override fun deviceGraph(): DeviceGraphSpec = currentDeviceGraph()

    override suspend fun onDeviceCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) {
        if (!currentTorchSupported()) {
            torchEnabled = false
        }
        mutableSnapshot.value = buildSnapshot(
            headline = if (isRecording) {
                "Recording in progress"
            } else {
                "Video mode active"
            },
            detail = if (isRecording) {
                recordingDetail()
            } else {
                defaultDetail()
            }
        )
    }

    override suspend fun onLensFacingChanged(lensFacing: LensFacing) = Unit

    override suspend fun onStillCaptureQualityChanged(
        stillCaptureQuality: StillCaptureQualityPreference
    ) = Unit

    override suspend fun onStillCaptureResolutionChanged(
        stillCaptureResolutionPreset: StillCaptureResolutionPreset
    ) = Unit

    override suspend fun onEnter() {
        context.eventSink("video.enter")
        requestedVideoSpec = context.settingsSnapshot.persisted.video.defaultVideoSpec
        isRecording = false
        torchEnabled = false
        mutableSnapshot.value = buildSnapshot(
            headline = "Video mode active"
        )
    }

    override suspend fun onExit() {
        context.eventSink("video.exit")
        isRecording = false
        torchEnabled = false
        mutableSnapshot.value = buildSnapshot(
            headline = "Video mode inactive",
            detail = "Leave recording decisions to the Session Kernel."
        )
    }

    override suspend fun handle(intent: ModeIntent): ModeSignal {
        return when (intent) {
            ModeIntent.ShutterPressed -> toggleRecording()
            ModeIntent.SecondaryActionPressed -> toggleTorch()
            ModeIntent.TertiaryActionPressed -> cycleQuality()
            ModeIntent.ProActionPressed -> ModeSignal.None
        }
    }

    override suspend fun onSessionEvent(event: ModeSessionEvent) {
        when (event) {
            is ModeSessionEvent.ShotStarted -> {
                if (event.shot.mediaType != MediaType.VIDEO) {
                    return
                }
                isRecording = true
                mutableSnapshot.value = buildSnapshot(
                    headline = "Recording in progress",
                    detail = recordingDetail()
                )
            }

            is ModeSessionEvent.ShotCompleted -> {
                if (event.result.mediaType != MediaType.VIDEO) {
                    return
                }
                isRecording = false
                torchEnabled = false
                mutableSnapshot.value = buildSnapshot(
                    headline = "Video saved",
                    detail = event.result.outputPath
                )
            }

            is ModeSessionEvent.ShotFailed -> {
                if (event.mediaType != MediaType.VIDEO) {
                    return
                }
                isRecording = false
                torchEnabled = false
                mutableSnapshot.value = buildSnapshot(
                    headline = "Recording failed",
                    detail = event.reason
                )
            }

            else -> Unit
        }
    }

    private suspend fun toggleRecording(): ModeSignal {
        return if (isRecording) {
            stopRecording()
        } else {
            context.eventSink("video.recording.start.requested.torch-${torchTag()}")
            val resolvedVideoSpec = resolvedVideoSpec()
            val activeGraph = currentDeviceGraph()
            val effectSpec = buildEffectSpec()
            val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
            val basePostProcess = EffectBridge.toPostProcessSpec(effectSpec)
            val postProcessSpec = basePostProcess.copy(
                watermarkText = videoWatermarkText()
            )
            mutableSnapshot.value = buildSnapshot(
                headline = "Recording requested",
                detail = "Waiting for Session Kernel to start the ${resolvedVideoSpec.summaryLabel} recording task."
            )
            ModeSignal.SubmitCapture(
                    CaptureStrategy.VideoRecording(
                        saveRequest = SaveRequest.videoLibrary(
                            metadata = MediaMetadata(
                                customTags = buildMap {
                                    put("mode", "video")
                                    put("torch", torchTag())
                                    put("videoQuality", activeGraph.recording.qualityPreset.tagValue)
                                    put(
                                        "defaultVideoResolution",
                                        requestedVideoSpec.resolution.storageKey
                                    )
                                    put(
                                        "defaultVideoFrameRate",
                                        requestedVideoSpec.frameRate.storageKey
                                    )
                                    put(
                                        "dynamicFpsPolicy",
                                        requestedVideoSpec.dynamicFpsPolicy.storageKey
                                    )
                                    put("audioProfile", requestedVideoSpec.audioProfile.storageKey)
                                    put(
                                        "resolvedVideoResolution",
                                        resolvedVideoSpec.resolution.storageKey
                                    )
                                    put(
                                        "resolvedVideoFrameRate",
                                        resolvedVideoSpec.frameRate.storageKey
                                    )
                                    put(
                                        "resolvedDynamicFpsPolicy",
                                        resolvedVideoSpec.dynamicFpsPolicy.storageKey
                                    )
                                    put(
                                        "resolvedAudioProfile",
                                        resolvedVideoSpec.audioProfile.storageKey
                                    )
                                    putAll(bridgeTags)
                                }
                            )
                        ),
                    postProcessSpec = postProcessSpec,
                    captureProfile = CaptureProfile(
                        torchEnabled = torchEnabled
                    )
                )
            )
        }
    }

    private suspend fun stopRecording(): ModeSignal {
        context.eventSink("video.recording.stop.requested")
        mutableSnapshot.value = buildSnapshot(
            headline = "Stopping recording",
            detail = "Session Kernel is finalizing the active recording."
        )
        return ModeSignal.StopActiveCapture
    }

    private fun buildEffectSpec(): EffectSpec {
        val filter = selectedFilter()
        return EffectSpec(listOf(
            FilterEffect(filter.id, filter.renderSpec)
        ))
    }

    private suspend fun toggleTorch(): ModeSignal {
        if (!currentTorchSupported()) {
            return ModeSignal.ShowHint("Torch is unavailable on this device")
        }
        if (isRecording) {
            return ModeSignal.ShowHint("Torch can only be changed before recording starts")
        }
        torchEnabled = !torchEnabled
        context.eventSink("video.torch.selected.${torchTag()}")
        mutableSnapshot.value = buildSnapshot(
            headline = "Video torch updated"
        )
        return ModeSignal.ShowHint("Torch: ${if (torchEnabled) "On" else "Off"}")
    }

    private suspend fun cycleQuality(): ModeSignal {
        if (isRecording) {
            return ModeSignal.ShowHint("Video quality can only be changed before recording starts")
        }
        val supportedResolutions = runtimeState()
            .deviceCapabilities
            .videoSpecConstraints
            .resolutions
            .sortedBy(VideoResolution::ordinal)
            .ifEmpty { listOf(VideoResolution.UHD_4K) }
        val currentIndex = supportedResolutions.indexOf(requestedVideoSpec.resolution)
        val nextResolution = if (currentIndex == -1) {
            supportedResolutions.first()
        } else {
            supportedResolutions[(currentIndex + 1) % supportedResolutions.size]
        }
        requestedVideoSpec = requestedVideoSpec.copy(resolution = nextResolution)
        context.eventSink("video.quality.selected.${nextResolution.storageKey}")
        mutableSnapshot.value = buildSnapshot(
            headline = "Video quality updated"
        )
        val activeVideoSpec = resolvedVideoSpec()
        val suffix = if (activeVideoSpec.resolution != nextResolution) {
            " (active ${activeVideoSpec.resolution.label})"
        } else {
            ""
        }
        return ModeSignal.ShowHint("Video quality: ${nextResolution.label}$suffix")
    }

    private fun buildSnapshot(
        headline: String,
        detail: String = defaultDetail()
    ): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.VIDEO,
            uiSpec = ModeUiSpec(
                title = "Video",
                shutterLabel = "Start / Stop Recording",
                secondaryActionLabel = if (currentTorchSupported()) {
                    "Toggle Torch"
                } else {
                    "Torch Unsupported"
                },
                tertiaryActionLabel = "Cycle Quality"
            ),
            state = ModeState(
                headline = headline,
                detail = detail,
                isSecondaryActionEnabled = currentTorchSupported() && !isRecording,
                isTertiaryActionEnabled = !isRecording
            )
        )
    }

    private fun defaultDetail(): String {
        val resolvedVideoSpec = resolvedVideoSpec()
        val torchText = if (currentTorchSupported()) {
            "Torch ${if (torchEnabled) "On" else "Off"}"
        } else {
            "Torch unavailable on this device"
        }
        val videoSpecText = if (requestedVideoSpec == resolvedVideoSpec) {
            "Active ${resolvedVideoSpec.summaryLabel}"
        } else {
            buildString {
                append("Active ${resolvedVideoSpec.summaryLabel} fallback")
                buildList {
                    if (requestedVideoSpec.resolution != resolvedVideoSpec.resolution) {
                        add("resolution")
                    }
                    if (requestedVideoSpec.frameRate != resolvedVideoSpec.frameRate) {
                        add("fps")
                    }
                    if (requestedVideoSpec.dynamicFpsPolicy != resolvedVideoSpec.dynamicFpsPolicy) {
                        add("dynamic fps")
                    }
                    if (requestedVideoSpec.audioProfile != resolvedVideoSpec.audioProfile) {
                        add("audio scene")
                    }
                }.takeIf { it.isNotEmpty() }?.let { degradedFields ->
                    append(" (")
                    append(degradedFields.joinToString())
                    append(')')
                }
            }
        }
        return buildString {
            append("Quality ${resolvedVideoSpec.resolution.label}")
            append(" | ")
            append("Default ${requestedVideoSpec.summaryLabel} | ")
            append(videoSpecText)
            append(" | Mic ${requestedVideoSpec.audioProfile.label}")
            append(" | Filter ${selectedFilter().label}")
            append(" | ")
            append(
                if (requestedVideoSpec.dynamicFpsPolicy == DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS) {
                    "Low-light auto 24fps staged"
                } else {
                    "Locked fps"
                }
            )
            append(" | ")
            append(torchText)
            append(" | Press shutter to start or stop recording.")
        }
    }

    private fun recordingDetail(): String {
        val resolvedVideoSpec = resolvedVideoSpec()
        return if (currentTorchSupported()) {
            "Unified shot pipeline started the ${resolvedVideoSpec.summaryLabel} video recording task. Torch ${if (torchEnabled) "On" else "Off"}."
        } else {
            "Unified shot pipeline started the ${resolvedVideoSpec.summaryLabel} video recording task. Torch unavailable on this device."
        }
    }

    private fun videoWatermarkText(): String {
        return "VIDEO Torch ${if (torchEnabled) "On" else "Off"}"
    }

    private fun currentDeviceGraph(): DeviceGraphSpec {
        val resolvedVideoSpec = resolvedVideoSpec()
        return DeviceGraphSpec.videoRecording(
            preferredLensFacing = runtimeState().lensFacing,
            enablePreviewSnapshots = runtimeState().deviceCapabilities.supportsPreviewSnapshots,
            audioEnabledWhenPermitted = runtimeState().deviceCapabilities.supportsAudioRecording,
            requestedVideoSpec = requestedVideoSpec,
            resolvedVideoSpec = resolvedVideoSpec,
            stillQualityPreference = runtimeState().stillCaptureQuality,
            stillResolutionPreset = runtimeState().stillCaptureResolutionPreset
        )
    }

    private fun currentTorchSupported(): Boolean = runtimeState().deviceCapabilities.supportsFlashControl
    private fun torchTag(): String = if (torchEnabled) "on" else "off"
    private fun runtimeState() = context.runtimeState()
    private fun selectedFilter(): FilterProfile {
        return context.settingsSnapshot.catalog.filterProfileOrNull(
            context.settingsSnapshot.persisted.video.defaultFilterProfileId
        ) ?: FilterProfile(
            id = context.settingsSnapshot.persisted.video.defaultFilterProfileId,
            label = context.settingsSnapshot.persisted.video.defaultFilterProfileId,
            category = com.opencamera.core.settings.FilterProfileCategory.PHOTO,
            builtIn = false
        )
    }

    private fun resolvedVideoSpec() = runtimeState()
        .deviceCapabilities
        .resolveVideoSpec(requestedVideoSpec)
        .applied
}
