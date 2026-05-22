# 工作包 5：Night 模式迁移到 BaseModeController

## 目标

将 `NightModeController` 从独立实现改为继承 `BaseModeController`。

## 依赖

工作包 1 必须已完成。

## 文件

**路径**: `feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt`

## 当前结构分析

NightModeController 的特征：
- **modeName**: "night"（但显示名是 "Scenery"）
- **mediaType**: `MediaType.PHOTO`
- **使用 FrameRatio**: 是
- **使用 ProVariant**: 是
- **SecondaryAction**: `cycleProfile()` — 切换夜景风格
- **TertiaryAction**: `cycleFrameRatio()`
- **ProAction**: `toggleProVariant()`
- **自定义 onEnter**: 否（使用基类默认）
- **自定义 deviceGraph**: 否（使用默认 stillCapture）
- **自定义 headline**: 是 — 根据 multiFrameEnabled 条件判断
- **多帧降噪**: 核心特性 — `CaptureStrategy.MultiFrame` vs `SingleFrame`

## 关键差异点

Night 模式的 headline 使用 "Scenery" 而非 "Night"，但 eventSink 使用 "night" 前缀。需要：
- `modeName = "night"`（用于 eventSink）
- 重写 `enterHeadline()` 等使用 "Scenery" 显示名

## 目标代码

```kotlin
private class NightModeController(
    context: ModeContext
) : BaseModeController(context) {
    override val id: ModeId = ModeId.NIGHT
    override val modeName: String = "night"
    override val mediaType: MediaType = MediaType.PHOTO

    private var profileIndex = 0

    // ── 抽象方法实现 ────────────────────────────────────────

    override fun buildEffectSpec(): EffectSpec {
        return EffectSpec(listOf(FrameEffect(frameRatioDelegate.currentFrameRatio())))
    }

    override fun buildSnapshot(headline: String, detail: String): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.NIGHT,
            uiSpec = ModeUiSpec(
                title = "Scenery",
                shutterLabel = "Capture Scenery",
                secondaryActionLabel = "Cycle Scenery Style",
                tertiaryActionLabel = "Cycle Frame",
                proActionLabel = proVariantDelegate.proActionLabel()
            ),
            state = ModeState(
                headline = headline,
                detail = detail,
                isTertiaryActionEnabled = true,
                isProActionEnabled = true,
                isProVariantActive = proVariantDelegate.proVariantEnabled
            )
        )
    }

    override fun defaultDetail(): String = profileSummary(currentProfile())

    override fun submitCapture(): ModeSignal {
        val profile = currentProfile()
        val flashMode = resolvedFlashMode(profile)
        context.eventSink("night.capture.requested.${profile.id}.flash-${flashMode.name.lowercase()}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (countdownDuration() == CountdownDuration.OFF) {
                "Scenery capture requested"
            } else {
                "Scenery countdown armed"
            },
            detail = defaultDetail()
        )
        val effectSpec = buildEffectSpec()
        val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
        val basePostProcess = EffectBridge.toPostProcessSpec(effectSpec)
        val postProcessSpec = basePostProcess.copy(
            watermarkText = resolvedWatermarkText(profile),
            exifOverrides = basePostProcess.exifOverrides + buildMap {
                put("SceneCaptureType", "Night")
                put("NightProfile", profile.label)
                if (proVariantDelegate.proVariantEnabled) {
                    put("NightVariant", if (proVariantDelegate.manualControlsEnabled()) "Pro" else "Pro Assist")
                    put("ManualDraft", proVariantDelegate.currentManualDraft().compactSummary())
                }
                profile.longExposureMillis?.let { put("ExposureTime", "${it}ms") }
                put("MergeStrategy", if (multiFrameEnabled()) "multi-frame" else "bright-single-frame")
            },
            algorithmProfile = proVariantDelegate.resolvedAlgorithmProfile(profile.algorithmProfile)
        )
        val saveRequest = SaveRequest.photoLibrary(
            relativePath = "Pictures/OpenCamera/Night",
            fileNamePrefix = "OpenCamera_NIGHT",
            metadata = MediaMetadata(
                customTags = buildMap {
                    put("mode", "night")
                    put("modeDisplay", "scenery")
                    put("profile", profile.id)
                    put("capturePath", if (multiFrameEnabled()) "multi-frame" else "single-frame-fallback")
                    put("stabilization", if (profile.requiresTripod) "tripod" else "handheld")
                    put("mergeFrameCount", profile.frameCount.toString())
                    put("flash", flashMode.name.lowercase())
                    put("stillQuality", runtimeState().stillCaptureQuality.tagValue)
                    put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                    put("modeVariant", if (proVariantDelegate.proVariantEnabled) "pro" else "standard")
                    put("watermarkModeName", "Scenery")
                    put("watermarkProfileName", profile.label)
                    putAll(proVariantDelegate.buildMetadataTags())
                    putAll(context.captureAidMetadataTags())
                    putAll(bridgeTags)
                }
            )
        )
        val strategy = if (multiFrameEnabled()) {
            CaptureStrategy.MultiFrame(
                saveRequest = saveRequest,
                postProcessSpec = postProcessSpec,
                captureProfile = CaptureProfile(
                    frameCount = profile.frameCount,
                    longExposureMillis = profile.longExposureMillis,
                    requiresTripod = profile.requiresTripod,
                    flashMode = flashMode,
                    manualCaptureParams = proVariantDelegate.currentManualDraftOrNull(),
                    stillCaptureQuality = runtimeState().stillCaptureQuality,
                    stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                )
            )
        } else {
            CaptureStrategy.SingleFrame(
                saveRequest = saveRequest,
                postProcessSpec = postProcessSpec,
                captureProfile = CaptureProfile(
                    flashMode = flashMode,
                    manualCaptureParams = proVariantDelegate.currentManualDraftOrNull(),
                    stillCaptureQuality = runtimeState().stillCaptureQuality,
                    stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                )
            )
        }
        return ModeSignal.SubmitCapture(strategy = strategy, countdownSeconds = countdownDuration().seconds)
    }

    override suspend fun cycleSecondary(): ModeSignal {
        profileIndex = (profileIndex + 1) % currentProfiles().size
        val profile = currentProfile()
        context.eventSink("night.profile.selected.${profile.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (multiFrameEnabled()) "Scenery profile updated" else "Scenery assist profile updated",
            detail = defaultDetail()
        )
        return ModeSignal.ShowHint("Scenery style: ${profile.label}")
    }

    // ── 可选覆盖 ────────────────────────────────────────────

    override fun enterHeadline(): String {
        return if (multiFrameEnabled()) "Scenery mode active" else "Scenery brightening active"
    }

    override fun onDeviceCapabilitiesChangedImpl(dc: DeviceCapabilities) {
        profileIndex = profileIndex.coerceAtMost(currentProfiles().lastIndex)
    }

    override fun onStillCaptureQualityChanged(stillCaptureQuality: StillCaptureQualityPreference) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (multiFrameEnabled()) "Scenery quality updated" else "Scenery assist quality updated",
            detail = defaultDetail()
        )
    }

    override fun onStillCaptureResolutionChanged(stillCaptureResolutionPreset: StillCaptureResolutionPreset) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (multiFrameEnabled()) "Scenery resolution updated" else "Scenery assist resolution updated",
            detail = defaultDetail()
        )
    }

    override fun handleTertiary(): ModeSignal {
        return frameRatioDelegate.cycleFrameRatio(
            updateSnapshot = { mutableSnapshot.value = buildSnapshot(headline = it, detail = defaultDetail()) },
            emitEffect = { context.onEffectSpecChanged(it) }
        )
    }

    override fun handleFrameRatioSelected(ratio: FrameRatio): ModeSignal {
        return frameRatioDelegate.selectFrameRatio(
            ratio = ratio,
            updateSnapshot = { mutableSnapshot.value = buildSnapshot(headline = it, detail = defaultDetail()) },
            emitEffect = { context.onEffectSpecChanged(it) }
        )
    }

    override fun handleProAction(): ModeSignal {
        val (newState, signal) = proVariantDelegate.toggleProVariant()
        mutableSnapshot.value = buildSnapshot(
            headline = proVariantDelegate.proActiveHeadline("Scenery"),
            detail = defaultDetail()
        )
        return signal
    }

    // ── 私有辅助方法 ────────────────────────────────────────

    private fun multiFrameEnabled(): Boolean = runtimeState().deviceCapabilities.supportsNightMultiFrame
    private fun flashSupported(): Boolean = runtimeState().deviceCapabilities.supportsFlashControl
    private fun currentProfiles(): List<NightProfile> = if (multiFrameEnabled()) MULTI_FRAME_PROFILES else FALLBACK_PROFILES
    private fun currentProfile(): NightProfile = currentProfiles()[profileIndex]

    private fun profileSummary(profile: NightProfile): String {
        val flashSummary = when {
            !flashSupported() -> "Flash unavailable on this device."
            multiFrameEnabled() -> "Flash ${resolvedFlashMode(profile).label} to preserve multi-frame merge."
            else -> "Flash ${resolvedFlashMode(profile).label}."
        }
        val standardSummary = if (multiFrameEnabled()) {
            "Default style ${profile.label} | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | ${profile.frameCount} frames | Exposure ${profile.longExposureMillis} ms | Tripod ${profile.requiresTripod} | Timer ${countdownDuration().label} | Frame ${frameRatioDelegate.currentFrameRatio().label} | Subfeatures scenery style, timer, frame ratio, and night fusion ride the current mode profile. $flashSummary"
        } else {
            "Default style ${profile.label} | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Single-frame brightening fallback because night multi-frame is unavailable on this device. | Timer ${countdownDuration().label} | Frame ${frameRatioDelegate.currentFrameRatio().label} | Subfeatures scenery style, timer, and frame ratio stay available while fusion degrades. $flashSummary"
        }
        if (!proVariantDelegate.proVariantEnabled) return standardSummary
        val proSummary = proVariantDelegate.proSummaryText()
        return "$standardSummary | $proSummary"
    }

    private fun resolvedWatermarkText(profile: NightProfile): String {
        return if (proVariantDelegate.proVariantEnabled) {
            if (proVariantDelegate.manualControlsEnabled()) "Scenery Pro ${profile.label}"
            else "Scenery Pro Assist ${profile.label}"
        } else if (multiFrameEnabled()) {
            "Scenery ${profile.label}"
        } else {
            "Scenery Assist ${profile.label}"
        }
    }

    private fun countdownDuration(): CountdownDuration = context.settingsSnapshot.persisted.photo.countdownDuration
    private fun resolvedFlashMode(profile: NightProfile): FlashMode {
        return if (flashSupported()) profile.flashMode else FlashMode.OFF
    }

    private data class NightProfile(
        val id: String, val label: String, val frameCount: Int,
        val longExposureMillis: Long?, val requiresTripod: Boolean,
        val flashMode: FlashMode = FlashMode.OFF, val algorithmProfile: String
    )

    companion object {
        private val MULTI_FRAME_PROFILES = listOf(/* ... 保持不变 ... */)
        private val FALLBACK_PROFILES = listOf(/* ... 保持不变 ... */)
    }
}
```

## 验证清单

- [ ] 继承 `BaseModeController(context)`
- [ ] `modeName = "night"` 用于 eventSink，但 UI 标题使用 "Scenery"
- [ ] ProVariant 辅助方法全部替换为委托调用
- [ ] `proVariantEnabled` 引用改为 `proVariantDelegate.proVariantEnabled`
- [ ] FrameRatio 逻辑替换为委托调用
- [ ] 编译通过：`./gradlew :feature:mode-night:compileDebugKotlin`
- [ ] 现有测试通过：`./gradlew :feature:mode-night:test`
- [ ] 预估行数：513 → ~320 行
