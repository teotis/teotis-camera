package com.opencamera.feature.video

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.nextQuickVideoSpec
import com.opencamera.core.device.quickLabel
import com.opencamera.core.device.resolveVideoSpec
import com.opencamera.core.settings.VideoSpec
import com.opencamera.core.effect.EffectBridge
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.FrameRatio
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
import com.opencamera.core.mode.CaptureMetadataLayers
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.StillCaptureResolutionPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.renderStyleColorSpecWithRecipe

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
        context.onEffectSpecChanged(buildEffectSpec())
    }

    override suspend fun onExit() {
        context.eventSink("video.exit")
        isRecording = false
        torchEnabled = false
        mutableSnapshot.value = buildSnapshot(
            headline = "Video mode inactive",
            detail = "Recording decisions are delegated to Session Kernel."
        )
    }

    override suspend fun handle(intent: ModeIntent): ModeSignal {
        return when (intent) {
            ModeIntent.ShutterPressed -> toggleRecording()
            ModeIntent.SecondaryActionPressed -> toggleTorch()
            ModeIntent.TertiaryActionPressed -> cycleQuality()
            is ModeIntent.FrameRatioSelected -> ModeSignal.ShowHint("视频模式暂不支持画幅切换")
            ModeIntent.ProActionPressed -> ModeSignal.None
            is ModeIntent.ScenarioSelected -> ModeSignal.None
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
            mutableSnapshot.value = buildSnapshot(
                headline = "Recording requested",
                detail = "Waiting for Session Kernel to start the ${resolvedVideoSpec.summaryLabel} recording task."
            )
            val modeTags = mapOf(
                "mode" to "video",
                "torch" to torchTag(),
                "videoQuality" to activeGraph.recording.qualityPreset.tagValue,
                "defaultVideoResolution" to requestedVideoSpec.resolution.storageKey,
                "defaultVideoFrameRate" to requestedVideoSpec.frameRate.storageKey,
                "dynamicFpsPolicy" to requestedVideoSpec.dynamicFpsPolicy.storageKey,
                "audioProfile" to requestedVideoSpec.audioProfile.storageKey,
                "resolvedVideoResolution" to resolvedVideoSpec.resolution.storageKey,
                "resolvedVideoFrameRate" to resolvedVideoSpec.frameRate.storageKey,
                "resolvedDynamicFpsPolicy" to resolvedVideoSpec.dynamicFpsPolicy.storageKey,
                "resolvedAudioProfile" to resolvedVideoSpec.audioProfile.storageKey
            )
            val composedTags = CaptureMetadataLayers(
                effectTags = EffectBridge.toMetadataTags(effectSpec),
                modeTags = modeTags
            ).compose()
            ModeSignal.SubmitCapture(
                    CaptureStrategy.VideoRecording(
                        saveRequest = SaveRequest.videoLibrary(
                            metadata = MediaMetadata(customTags = composedTags)
                        ),
                    postProcessSpec = EffectBridge.toPostProcessSpec(effectSpec),
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
            detail = "Session Kernel is completing the current recording."
        )
        return ModeSignal.StopActiveCapture
    }

    private fun buildEffectSpec(): EffectSpec {
        val filter = selectedFilter()
        val photoSettings = context.settingsSnapshot.persisted.photo
        val pipelineResult = renderStyleColorSpecWithRecipe(
            profileId = filter.id,
            baseRenderSpec = filter.renderSpec,
            colorLabSpec = photoSettings.colorLabSpec,
            styleStrength = photoSettings.styleStrength
        )
        val adjustedRenderSpec = pipelineResult?.finalRenderSpec
        val recipe = pipelineResult?.recipe
            ?: com.opencamera.core.settings.PerceptualColorRecipe.NEUTRAL
        return EffectSpec(listOf(
            FilterEffect(filter.id, adjustedRenderSpec, recipe = recipe),
            FrameEffect(FrameRatio.RATIO_16_9)
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
        val constraints = runtimeState().deviceCapabilities.videoSpecConstraints
        val nextSpec = constraints.nextQuickVideoSpec(
            current = requestedVideoSpec,
            preserve = requestedVideoSpec
        )
        if (nextSpec == null) {
            return ModeSignal.ShowHint("Video quality unavailable")
        }
        requestedVideoSpec = requestedVideoSpec.copy(resolution = nextSpec.resolution)
        context.eventSink(
            "video.quality.selected.${requestedVideoSpec.resolution.storageKey}.${requestedVideoSpec.frameRate.storageKey}"
        )
        mutableSnapshot.value = buildSnapshot(
            headline = "Video quality updated"
        )
        val activeVideoSpec = resolvedVideoSpec()
        val requestedLabel = requestedVideoSpec.quickLabel()
        val suffix = if (
            activeVideoSpec.resolution != requestedVideoSpec.resolution ||
            activeVideoSpec.frameRate != requestedVideoSpec.frameRate
        ) {
            " (active ${activeVideoSpec.quickLabel()})"
        } else {
            ""
        }
        return ModeSignal.ShowHint("Video quality: $requestedLabel$suffix")
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
                    "Cycle Torch"
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
            "Active ${resolvedVideoSpec.quickLabel()}"
        } else {
            buildString {
                append("Active ${resolvedVideoSpec.quickLabel()} fallback")
                buildList {
                    if (requestedVideoSpec.resolution != resolvedVideoSpec.resolution) {
                        add("resolution")
                    }
                    if (requestedVideoSpec.frameRate != resolvedVideoSpec.frameRate) {
                        add("framerate")
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
            append("Quality ${resolvedVideoSpec.quickLabel()}")
            append(" | ")
            append("Default ${requestedVideoSpec.quickLabel()} | ")
            append(videoSpecText)
            append(" | Mic ${requestedVideoSpec.audioProfile.label}")
            append(" | Filter ${selectedFilter().label}")
            append(" | ")
            append(
                if (requestedVideoSpec.dynamicFpsPolicy == DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS) {
                    "Low-light auto 24fps ready"
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
            "Unified pipeline started ${resolvedVideoSpec.summaryLabel} recording task. Torch ${if (torchEnabled) "On" else "Off"}."
        } else {
            "Unified pipeline started ${resolvedVideoSpec.summaryLabel} recording task. Torch unavailable on this device."
        }
    }

    private fun currentDeviceGraph(): DeviceGraphSpec {
        val resolvedVideoSpec = resolvedVideoSpec()
        return DeviceGraphSpec.videoRecording(
            preferredLensFacing = runtimeState().lensFacing,
            enablePreviewSnapshots = runtimeState().deviceCapabilities.supportsPreviewSnapshots,
            audioEnabledWhenPermitted = runtimeState().deviceCapabilities.supportsAudioRecording,
            requestedVideoSpec = requestedVideoSpec,
            resolvedVideoSpec = resolvedVideoSpec
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

    private fun VideoSpec.quickLabel(): String = summaryLabel
}
