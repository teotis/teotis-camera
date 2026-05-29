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
            headline = "视频管线就绪"
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
                "录制进行中"
            } else {
                "视频模式已激活"
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
            headline = "视频模式已激活"
        )
        context.onEffectSpecChanged(buildEffectSpec())
    }

    override suspend fun onExit() {
        context.eventSink("video.exit")
        isRecording = false
        torchEnabled = false
        mutableSnapshot.value = buildSnapshot(
            headline = "视频模式未激活",
            detail = "将录制决策交由 Session Kernel 处理。"
        )
    }

    override suspend fun handle(intent: ModeIntent): ModeSignal {
        return when (intent) {
            ModeIntent.ShutterPressed -> toggleRecording()
            ModeIntent.SecondaryActionPressed -> toggleTorch()
            ModeIntent.TertiaryActionPressed -> cycleQuality()
            is ModeIntent.FrameRatioSelected -> ModeSignal.ShowHint("视频模式暂不支持画幅切换")
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
                    headline = "录制进行中",
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
                    headline = "视频已保存",
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
                    headline = "录制失败",
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
            val postProcessSpec = EffectBridge.toPostProcessSpec(effectSpec)
            mutableSnapshot.value = buildSnapshot(
                headline = "录制已请求",
                detail = "等待 Session Kernel 启动 ${resolvedVideoSpec.summaryLabel} 录制任务。"
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
            headline = "停止录制",
            detail = "Session Kernel 正在完成当前录制。"
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
            FilterEffect(filter.id, adjustedRenderSpec, recipe = recipe)
        ))
    }

    private suspend fun toggleTorch(): ModeSignal {
        if (!currentTorchSupported()) {
            return ModeSignal.ShowHint("此设备不支持闪光灯")
        }
        if (isRecording) {
            return ModeSignal.ShowHint("闪光灯只能在录制开始前更改")
        }
        torchEnabled = !torchEnabled
        context.eventSink("video.torch.selected.${torchTag()}")
        mutableSnapshot.value = buildSnapshot(
            headline = "视频闪光灯已更新"
        )
        return ModeSignal.ShowHint("闪光灯: ${if (torchEnabled) "开" else "关"}")
    }

    private suspend fun cycleQuality(): ModeSignal {
        if (isRecording) {
            return ModeSignal.ShowHint("视频画质只能在录制开始前更改")
        }
        val constraints = runtimeState().deviceCapabilities.videoSpecConstraints
        val nextSpec = constraints.nextQuickVideoSpec(
            current = requestedVideoSpec,
            preserve = requestedVideoSpec
        )
        if (nextSpec == null) {
            return ModeSignal.ShowHint("视频画质不可用")
        }
        requestedVideoSpec = requestedVideoSpec.copy(resolution = nextSpec.resolution)
        context.eventSink(
            "video.quality.selected.${requestedVideoSpec.resolution.storageKey}.${requestedVideoSpec.frameRate.storageKey}"
        )
        mutableSnapshot.value = buildSnapshot(
            headline = "视频画质已更新"
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
        return ModeSignal.ShowHint("视频画质: $requestedLabel$suffix")
    }

    private fun buildSnapshot(
        headline: String,
        detail: String = defaultDetail()
    ): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.VIDEO,
            uiSpec = ModeUiSpec(
                title = "视频",
                shutterLabel = "开始 / 停止录制",
                secondaryActionLabel = if (currentTorchSupported()) {
                    "切换闪光灯"
                } else {
                    "闪光灯不支持"
                },
                tertiaryActionLabel = "切换画质"
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
            "闪光灯 ${if (torchEnabled) "开" else "关"}"
        } else {
            "此设备不支持闪光灯"
        }
        val videoSpecText = if (requestedVideoSpec == resolvedVideoSpec) {
            "当前 ${resolvedVideoSpec.quickLabel()}"
        } else {
            buildString {
                append("当前 ${resolvedVideoSpec.quickLabel()} 降级")
                buildList {
                    if (requestedVideoSpec.resolution != resolvedVideoSpec.resolution) {
                        add("分辨率")
                    }
                    if (requestedVideoSpec.frameRate != resolvedVideoSpec.frameRate) {
                        add("帧率")
                    }
                    if (requestedVideoSpec.dynamicFpsPolicy != resolvedVideoSpec.dynamicFpsPolicy) {
                        add("动态帧率")
                    }
                    if (requestedVideoSpec.audioProfile != resolvedVideoSpec.audioProfile) {
                        add("音频场景")
                    }
                }.takeIf { it.isNotEmpty() }?.let { degradedFields ->
                    append(" (")
                    append(degradedFields.joinToString())
                    append(')')
                }
            }
        }
        return buildString {
            append("画质 ${resolvedVideoSpec.quickLabel()}")
            append(" | ")
            append("默认 ${requestedVideoSpec.quickLabel()} | ")
            append(videoSpecText)
            append(" | 麦克风 ${requestedVideoSpec.audioProfile.label}")
            append(" | 滤镜 ${selectedFilter().label}")
            append(" | ")
            append(
                if (requestedVideoSpec.dynamicFpsPolicy == DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS) {
                    "弱光自动 24fps 已就绪"
                } else {
                    "锁定帧率"
                }
            )
            append(" | ")
            append(torchText)
            append(" | 按下快门开始或停止录制。")
        }
    }

    private fun recordingDetail(): String {
        val resolvedVideoSpec = resolvedVideoSpec()
        return if (currentTorchSupported()) {
            "统一拍摄管线启动了 ${resolvedVideoSpec.summaryLabel} 视频录制任务。闪光灯 ${if (torchEnabled) "开" else "关"}。"
        } else {
            "统一拍摄管线启动了 ${resolvedVideoSpec.summaryLabel} 视频录制任务。此设备不支持闪光灯。"
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
