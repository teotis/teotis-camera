# 工作包 4：Video 模式迁移到 BaseModeController

## 目标

将 `VideoModeController` 从独立实现改为继承 `BaseModeController`。

## 依赖

工作包 1 必须已完成。

## 文件

**路径**: `feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt`

## 当前结构分析

VideoModeController 的特征：
- **modeName**: "video"
- **mediaType**: `MediaType.VIDEO`（唯一使用 VIDEO 的模式）
- **使用 FrameRatio**: 否（拒绝画幅切换，返回提示）
- **使用 ProVariant**: 否
- **SecondaryAction**: `toggleTorch()` — 切换手电筒
- **TertiaryAction**: `cycleQuality()` — 循环视频质量
- **自定义 deviceGraph**: 是 — 使用 `DeviceGraphSpec.videoRecording()`
- **自定义 onEnter**: 是 — 重置 videoSpec、isRecording、torchEnabled
- **自定义 onExit**: 是 — 重置 isRecording、torchEnabled
- **自定义 onSessionEvent**: 是 — 额外维护 isRecording 状态
- **自定义 onDeviceCapabilitiesChanged**: 是 — 根据 isRecording 切换 headline
- **不使用 buildEffectSpec 参数**: buildEffectSpec() 无参数

## 目标代码

```kotlin
private class VideoModeController(
    context: ModeContext
) : BaseModeController(context) {
    override val id: ModeId = ModeId.VIDEO
    override val modeName: String = "video"
    override val mediaType: MediaType = MediaType.VIDEO

    private var requestedVideoSpec = context.settingsSnapshot.persisted.video.defaultVideoSpec
    private var isRecording = false
    private var torchEnabled = false

    // ── 抽象方法实现 ────────────────────────────────────────

    override fun buildEffectSpec(): EffectSpec {
        val filter = selectedFilter()
        val photoSettings = context.settingsSnapshot.persisted.photo
        val adjustedRenderSpec = renderStyleColorSpec(
            profileId = filter.id,
            baseRenderSpec = filter.renderSpec,
            colorLabSpec = photoSettings.colorLabSpec,
            styleStrength = photoSettings.styleStrength
        )
        return EffectSpec(listOf(FilterEffect(filter.id, adjustedRenderSpec)))
    }

    override fun buildSnapshot(headline: String, detail: String): ModeSnapshot {
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

    override fun defaultDetail(): String {
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
                    if (requestedVideoSpec.resolution != resolvedVideoSpec.resolution) add("resolution")
                    if (requestedVideoSpec.frameRate != resolvedVideoSpec.frameRate) add("fps")
                    if (requestedVideoSpec.dynamicFpsPolicy != resolvedVideoSpec.dynamicFpsPolicy) add("dynamic fps")
                    if (requestedVideoSpec.audioProfile != resolvedVideoSpec.audioProfile) add("audio scene")
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

    override fun submitCapture(): ModeSignal {
        return if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    override suspend fun cycleSecondary(): ModeSignal {
        if (!currentTorchSupported()) {
            return ModeSignal.ShowHint("Torch is unavailable on this device")
        }
        if (isRecording) {
            return ModeSignal.ShowHint("Torch can only be changed before recording starts")
        }
        torchEnabled = !torchEnabled
        context.eventSink("video.torch.selected.${torchTag()}")
        mutableSnapshot.value = buildSnapshot(headline = "Video torch updated", detail = defaultDetail())
        return ModeSignal.ShowHint("Torch: ${if (torchEnabled) "On" else "Off"}")
    }

    // ── 可选覆盖 ────────────────────────────────────────────

    override fun onModeEnter() {
        requestedVideoSpec = context.settingsSnapshot.persisted.video.defaultVideoSpec
        isRecording = false
        torchEnabled = false
    }

    override fun onModeExit() {
        isRecording = false
        torchEnabled = false
    }

    override fun enterHeadline(): String {
        return if (isRecording) "Recording in progress" else "Video mode active"
    }

    override fun rebuildSnapshotOnEnter(): Boolean = true

    override fun onDeviceCapabilitiesChangedImpl(dc: DeviceCapabilities) {
        if (!currentTorchSupported()) {
            torchEnabled = false
        }
    }

    override fun handleTertiary(): ModeSignal {
        return cycleQuality()
    }

    // 覆盖 onDeviceCapabilitiesChanged 以使用自定义 headline
    override suspend fun onDeviceCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) {
        onDeviceCapabilitiesChangedImpl(deviceCapabilities)
        mutableSnapshot.value = buildSnapshot(
            headline = if (isRecording) "Recording in progress" else "Video mode active",
            detail = if (isRecording) recordingDetail() else defaultDetail()
        )
    }

    // 覆盖 onSessionEvent 以维护 isRecording 状态
    override suspend fun onSessionEvent(event: ModeSessionEvent) {
        when (event) {
            is ModeSessionEvent.ShotStarted -> {
                if (event.shot.mediaType != mediaType) return
                isRecording = true
                mutableSnapshot.value = buildSnapshot(
                    headline = "Recording in progress",
                    detail = recordingDetail()
                )
            }
            is ModeSessionEvent.ShotCompleted -> {
                if (event.result.mediaType != mediaType) return
                isRecording = false
                torchEnabled = false
                mutableSnapshot.value = buildSnapshot(
                    headline = "Video saved",
                    detail = event.result.outputPath
                )
            }
            is ModeSessionEvent.ShotFailed -> {
                if (event.mediaType != mediaType) return
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

    // 覆盖 deviceGraph 以使用 videoRecording
    override fun currentDeviceGraph(): DeviceGraphSpec {
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

    override fun handleFrameRatioSelected(ratio: FrameRatio): ModeSignal {
        return ModeSignal.ShowHint("视频模式暂不支持画幅切换")
    }

    // ── 私有辅助方法 ────────────────────────────────────────

    private fun startRecording(): ModeSignal {
        context.eventSink("video.recording.start.requested.torch-${torchTag()}")
        val resolvedVideoSpec = resolvedVideoSpec()
        val activeGraph = currentDeviceGraph()
        val effectSpec = buildEffectSpec()
        val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
        val postProcessSpec = EffectBridge.toPostProcessSpec(effectSpec)
        mutableSnapshot.value = buildSnapshot(
            headline = "Recording requested",
            detail = "Waiting for Session Kernel to start the ${resolvedVideoSpec.summaryLabel} recording task."
        )
        return ModeSignal.SubmitCapture(
            CaptureStrategy.VideoRecording(
                saveRequest = SaveRequest.videoLibrary(
                    metadata = MediaMetadata(
                        customTags = buildMap {
                            put("mode", "video")
                            put("torch", torchTag())
                            put("videoQuality", activeGraph.recording.qualityPreset.tagValue)
                            put("defaultVideoResolution", requestedVideoSpec.resolution.storageKey)
                            put("defaultVideoFrameRate", requestedVideoSpec.frameRate.storageKey)
                            put("dynamicFpsPolicy", requestedVideoSpec.dynamicFpsPolicy.storageKey)
                            put("audioProfile", requestedVideoSpec.audioProfile.storageKey)
                            put("resolvedVideoResolution", resolvedVideoSpec.resolution.storageKey)
                            put("resolvedVideoFrameRate", resolvedVideoSpec.frameRate.storageKey)
                            put("resolvedDynamicFpsPolicy", resolvedVideoSpec.dynamicFpsPolicy.storageKey)
                            put("resolvedAudioProfile", resolvedVideoSpec.audioProfile.storageKey)
                            putAll(bridgeTags)
                        }
                    )
                ),
                postProcessSpec = postProcessSpec,
                captureProfile = CaptureProfile(torchEnabled = torchEnabled)
            )
        )
    }

    private fun stopRecording(): ModeSignal {
        context.eventSink("video.recording.stop.requested")
        mutableSnapshot.value = buildSnapshot(
            headline = "Stopping recording",
            detail = "Session Kernel is finalizing the active recording."
        )
        return ModeSignal.StopActiveCapture
    }

    private fun cycleQuality(): ModeSignal {
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
        mutableSnapshot.value = buildSnapshot(headline = "Video quality updated", detail = defaultDetail())
        val activeVideoSpec = resolvedVideoSpec()
        val suffix = if (activeVideoSpec.resolution != nextResolution) {
            " (active ${activeVideoSpec.resolution.label})"
        } else {
            ""
        }
        return ModeSignal.ShowHint("Video quality: ${nextResolution.label}$suffix")
    }

    private fun currentTorchSupported(): Boolean = runtimeState().deviceCapabilities.supportsFlashControl
    private fun torchTag(): String = if (torchEnabled) "on" else "off"
    private fun recordingDetail(): String {
        val resolvedVideoSpec = resolvedVideoSpec()
        return if (currentTorchSupported()) {
            "Unified shot pipeline started the ${resolvedVideoSpec.summaryLabel} video recording task. Torch ${if (torchEnabled) "On" else "Off"}."
        } else {
            "Unified shot pipeline started the ${resolvedVideoSpec.summaryLabel} video recording task. Torch unavailable on this device."
        }
    }
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
```

## 关键变更点

1. **Video 是最特殊的模式**：它覆盖了 `currentDeviceGraph()`、`onSessionEvent()`、`onDeviceCapabilitiesChanged()`，并且不使用 FrameRatio 和 ProVariant。
2. **`submitCapture()` 内部拆分**：原 `handle()` 中的 `ShutterPressed -> toggleRecording()` 拆分为 `submitCapture()` 内部调用 `startRecording()` / `stopRecording()`。
3. **`onExit()` 状态重置**：基类的 `onExit()` 钩子 `onModeExit()` 处理 isRecording/torchEnabled 重置。
4. **`handleFrameRatioSelected` 覆盖**：返回中文提示而非基类默认的"不支持"。

## 验证清单

- [ ] 继承 `BaseModeController(context)`
- [ ] 覆盖 `currentDeviceGraph()` 使用 `videoRecording()`
- [ ] 覆盖 `onSessionEvent()` 维护 isRecording 状态
- [ ] `frameRatioDelegate` 和 `proVariantDelegate` 未被使用
- [ ] 编译通过：`./gradlew :feature:mode-video:compileDebugKotlin`
- [ ] 现有测试通过：`./gradlew :feature:mode-video:test`
- [ ] 预估行数：416 → ~300 行
