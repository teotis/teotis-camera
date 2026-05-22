# 工作包 8：Humanistic 模式迁移到 BaseModeController

## 目标

将 `HumanisticModeController` 从独立实现改为继承 `BaseModeController`。

## 依赖

工作包 1 必须已完成。

## 文件

**路径**: `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`

## 当前结构分析

HumanisticModeController 的特征：
- **modeName**: "humanistic"
- **mediaType**: `MediaType.PHOTO`
- **使用 FrameRatio**: 是
- **使用 ProVariant**: 是
- **SecondaryAction**: `cycleStyle()` — 切换人文风格
- **TertiaryAction**: `cycleFrameRatio()`
- **ProAction**: `toggleProVariant()`
- **自定义 onEnter**: 是 — 重置 styleIndex
- **自定义 deviceGraph**: 否（使用默认 stillCapture）
- **自定义 onSessionEvent**: 否（使用基类默认）
- **自定义 enterHeadline**: 否（使用基类默认 "humanistic mode active"）

## 目标代码

```kotlin
private class HumanisticModeController(
    context: ModeContext
) : BaseModeController(context) {
    override val id: ModeId = ModeId.HUMANISTIC
    override val modeName: String = "humanistic"
    override val mediaType: MediaType = MediaType.PHOTO

    private val styles = resolveHumanisticStyles()
    private var styleIndex = resolvedDefaultStyleIndex()

    // ── 抽象方法实现 ────────────────────────────────────────

    override fun buildEffectSpec(): EffectSpec {
        val style = currentStyle()
        val photoSettings = context.settingsSnapshot.persisted.photo
        val adjustedRenderSpec = renderStyleColorSpec(
            profileId = style.id,
            baseRenderSpec = style.renderSpec,
            colorLabSpec = photoSettings.colorLabSpec,
            styleStrength = photoSettings.styleStrength
        )
        return EffectSpec(listOf(
            FilterEffect(style.id, adjustedRenderSpec),
            FrameEffect(frameRatioDelegate.currentFrameRatio())
        ))
    }

    override fun buildSnapshot(headline: String, detail: String): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.HUMANISTIC,
            uiSpec = ModeUiSpec(
                title = "Humanistic",
                shutterLabel = "Capture Humanistic",
                secondaryActionLabel = "Cycle Humanistic Style",
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

    override fun defaultDetail(): String {
        val style = currentStyle()
        val standardSummary = buildString {
            append("Default style ${style.label}")
            append(" | Still ${runtimeState().stillCaptureQuality.label}")
            append(" | Size ${runtimeState().stillCaptureResolutionPreset.label}")
            append(" | Watermark ${selectedWatermarkTemplate().label}")
            append(" | Live ${onOffLabel(livePhotoEnabledByDefault())}")
            append(" | Timer ${countdownDuration().label}")
            append(" | Frame ${frameRatioDelegate.currentFrameRatio().label}")
            append(" | Subfeatures style, frame ratio, Live default, timer, and watermark ride the shared settings spine.")
        }
        if (!proVariantDelegate.proVariantEnabled) return standardSummary
        return "$standardSummary | ${proVariantDelegate.proSummaryText()}"
    }

    override fun submitCapture(): ModeSignal {
        val style = currentStyle()
        context.eventSink("humanistic.capture.requested.${style.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (countdownDuration() == CountdownDuration.OFF) "Humanistic capture requested" else "Humanistic countdown armed",
            detail = defaultDetail()
        )
        val effectSpec = buildEffectSpec()
        val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
        val basePostProcess = EffectBridge.toPostProcessSpec(effectSpec)
        val postProcessSpec = basePostProcess.copy(
            watermarkText = resolvedWatermarkText(style),
            exifOverrides = basePostProcess.exifOverrides + buildMap {
                put("SceneCaptureType", "Humanistic")
                put("HumanisticStyle", style.label)
                if (proVariantDelegate.proVariantEnabled) {
                    put("HumanisticVariant", if (proVariantDelegate.manualControlsEnabled()) "Pro" else "Pro Assist")
                    put("ManualDraft", proVariantDelegate.currentManualDraft().compactSummary())
                }
            },
            algorithmProfile = proVariantDelegate.resolvedAlgorithmProfile(style.algorithmProfile)
        )
        return ModeSignal.SubmitCapture(
            if (livePhotoEnabledByDefault()) {
                CaptureStrategy.LivePhoto(
                    saveRequest = SaveRequest.photoLibrary(
                        relativePath = "Pictures/OpenCamera/Humanistic",
                        fileNamePrefix = "OpenCamera_HUMANISTIC",
                        metadata = MediaMetadata(
                            customTags = buildMap {
                                put("mode", "humanistic")
                                put("modeDisplay", "humanistic")
                                put("style", style.id)
                                put("algorithmProfile", style.algorithmProfile)
                                put("watermarkTemplate", selectedWatermarkTemplate().id)
                                put("livePhotoDefault", "on")
                                putAll(context.settingsSnapshot.catalog.liveMediaBundleDraft.liveWatermarkMetadataTags())
                                put("stillQuality", runtimeState().stillCaptureQuality.tagValue)
                                put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                                put("modeVariant", if (proVariantDelegate.proVariantEnabled) "pro" else "standard")
                                putAll(proVariantDelegate.buildMetadataTags())
                                putAll(context.captureAidMetadataTags())
                                putAll(bridgeTags)
                            }
                        )
                    ),
                    postProcessSpec = postProcessSpec,
                    captureProfile = CaptureProfile(
                        manualCaptureParams = proVariantDelegate.currentManualDraftOrNull(),
                        stillCaptureQuality = runtimeState().stillCaptureQuality,
                        stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                    ),
                    livePhotoSpec = context.settingsSnapshot.catalog.liveMediaBundleDraft.toCaptureSpec()
                )
            } else {
                CaptureStrategy.SingleFrame(
                    saveRequest = SaveRequest.photoLibrary(
                        relativePath = "Pictures/OpenCamera/Humanistic",
                        fileNamePrefix = "OpenCamera_HUMANISTIC",
                        metadata = MediaMetadata(
                            customTags = buildMap {
                                put("mode", "humanistic")
                                put("modeDisplay", "humanistic")
                                put("style", style.id)
                                put("algorithmProfile", style.algorithmProfile)
                                put("watermarkTemplate", selectedWatermarkTemplate().id)
                                put("livePhotoDefault", "off")
                                put("stillQuality", runtimeState().stillCaptureQuality.tagValue)
                                put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                                put("modeVariant", if (proVariantDelegate.proVariantEnabled) "pro" else "standard")
                                putAll(proVariantDelegate.buildMetadataTags())
                                putAll(context.captureAidMetadataTags())
                                putAll(bridgeTags)
                            }
                        )
                    ),
                    postProcessSpec = postProcessSpec,
                    captureProfile = CaptureProfile(
                        manualCaptureParams = proVariantDelegate.currentManualDraftOrNull(),
                        stillCaptureQuality = runtimeState().stillCaptureQuality,
                        stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                    )
                )
            },
            countdownSeconds = countdownDuration().seconds
        )
    }

    override suspend fun cycleSecondary(): ModeSignal {
        styleIndex = (styleIndex + 1) % styles.size
        val style = currentStyle()
        context.eventSink("humanistic.style.selected.${style.id}")
        mutableSnapshot.value = buildSnapshot(headline = "Humanistic style updated", detail = defaultDetail())
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("Humanistic style: ${style.label}")
    }

    // ── 可选覆盖 ────────────────────────────────────────────

    override fun onModeEnter() {
        styleIndex = resolvedDefaultStyleIndex()
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
            headline = proVariantDelegate.proActiveHeadline("Humanistic"),
            detail = defaultDetail()
        )
        return signal
    }

    // ── 私有辅助方法 ────────────────────────────────────────

    private fun currentStyle(): HumanisticStyle = styles[styleIndex]

    private fun resolvedDefaultStyleIndex(): Int {
        val defaultId = context.settingsSnapshot.persisted.photo.defaultHumanisticFilterProfileId
        return styles.indexOfFirst { style -> style.id == defaultId }
            .takeIf { index -> index >= 0 } ?: 0
    }

    private fun resolveHumanisticStyles(): List<HumanisticStyle> {
        val catalogStyles = context.settingsSnapshot.catalog.filterProfiles
            .filter { profile -> profile.category == FilterProfileCategory.HUMANISTIC }
            .map { profile ->
                HumanisticStyle(
                    id = profile.id, label = profile.label,
                    algorithmProfile = mappedAlgorithmProfile(profile.id),
                    renderSpec = profile.renderSpec ?: defaultFilterRenderSpecOrNull(mappedAlgorithmProfile(profile.id))
                )
            }
        return if (catalogStyles.isNotEmpty()) catalogStyles else DEFAULT_HUMANISTIC_STYLES
    }

    private fun mappedAlgorithmProfile(styleId: String): String {
        return when (styleId) {
            "humanistic-original" -> "photo-original"
            "humanistic-vivid" -> "photo-vivid"
            "humanistic-street" -> "photo-chasing-light"
            "humanistic-portrait" -> "portrait-original"
            "humanistic-life" -> "photo-rich"
            else -> "photo-original"
        }
    }

    private fun resolvedWatermarkText(style: HumanisticStyle): String {
        return if (!proVariantDelegate.proVariantEnabled) "Humanistic ${style.label}"
        else if (proVariantDelegate.manualControlsEnabled()) "Humanistic Pro ${style.label}"
        else "Humanistic Pro Assist ${style.label}"
    }

    private fun selectedWatermarkTemplate(): WatermarkTemplate {
        val persistedTemplateId = context.settingsSnapshot.persisted.photo.defaultWatermarkTemplateId
        return context.settingsSnapshot.catalog.watermarkTemplates.firstOrNull { it.id == persistedTemplateId }
            ?: WatermarkTemplate(id = persistedTemplateId, label = persistedTemplateId)
    }

    private fun livePhotoEnabledByDefault(): Boolean = context.settingsSnapshot.persisted.photo.livePhotoEnabledByDefault
    private fun countdownDuration(): CountdownDuration = context.settingsSnapshot.persisted.photo.countdownDuration
    private fun onOffLabel(enabled: Boolean): String = if (enabled) "On" else "Off"

    private fun com.opencamera.core.settings.LiveMediaBundle.toCaptureSpec(): LivePhotoCaptureSpec {
        return LivePhotoCaptureSpec(motionDurationMillis = motionDurationMillis, motionMimeType = motionContainer, sidecarMimeType = sidecarMimeType)
    }

    private data class HumanisticStyle(val id: String, val label: String, val algorithmProfile: String, val renderSpec: FilterRenderSpec?)

    companion object {
        private val DEFAULT_HUMANISTIC_STYLES = listOf(/* ... 保持不变 ... */)
    }
}
```

## 验证清单

- [ ] 继承 `BaseModeController(context)`
- [ ] ProVariant 辅助方法全部替换为委托调用
- [ ] `proVariantEnabled` 引用改为 `proVariantDelegate.proVariantEnabled`
- [ ] FrameRatio 逻辑替换为委托调用
- [ ] 编译通过：`./gradlew :feature:mode-humanistic:compileDebugKotlin`
- [ ] 现有测试通过：`./gradlew :feature:mode-humanistic:test`
- [ ] 预估行数：542 → ~340 行
