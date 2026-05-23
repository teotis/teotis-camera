# 工作包 3：Portrait 模式迁移到 BaseModeController

## 目标

将 `PortraitModeController` 从独立实现改为继承 `BaseModeController`。

## 依赖

工作包 1 必须已完成。

## 文件

**路径**: `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`

## 当前结构分析

PortraitModeController 的特征：
- **modeName**: "portrait"
- **mediaType**: `MediaType.PHOTO`
- **使用 FrameRatio**: 是（通过 `frameRatioDelegate`）
- **使用 ProVariant**: 是（通过 `proVariantDelegate`）
- **SecondaryAction**: `cycleStyle()` — 切换人像风格
- **TertiaryAction**: `cycleFrameRatio()`
- **ProAction**: `toggleProVariant()`
- **自定义 onEnter**: 是 — 重置 styleIndex
- **自定义 onDeviceCapabilitiesChanged**: 是 — 约束 styleIndex
- **自定义 deviceGraph**: 否（使用默认 stillCapture）
- **自定义 enterHeadline**: 是 — 根据 depthEffect 条件判断

## 目标代码

```kotlin
private class PortraitModeController(
    context: ModeContext
) : BaseModeController(context) {
    override val id: ModeId = ModeId.PORTRAIT
    override val modeName: String = "portrait"
    override val mediaType: MediaType = MediaType.PHOTO

    private val portraitFilters = resolvePortraitFilters()
    private var styleIndex = resolvedDefaultStyleIndex()

    // ── 抽象方法实现 ────────────────────────────────────────

    override fun buildEffectSpec(): EffectSpec {
        val style = currentStyle()
        val portraitSettings = portraitSettings()
        val photoSettings = context.settingsSnapshot.persisted.photo
        val adjustedRenderSpec = renderStyleColorSpec(
            profileId = style.id,
            baseRenderSpec = style.renderSpec,
            colorLabSpec = photoSettings.colorLabSpec,
            styleStrength = photoSettings.styleStrength
        )
        return EffectSpec(listOf(
            FilterEffect(style.id, adjustedRenderSpec),
            PortraitEffect(
                profileId = portraitSettings.portraitProfile.storageKey,
                renderPath = if (depthEffectEnabled()) "depth" else "focus",
                beautyPreset = portraitSettings.portraitBeautyPreset.storageKey,
                beautyStrength = portraitSettings.portraitBeautyStrength.storageKey,
                bokehEffect = portraitSettings.portraitBokehEffect.storageKey
            ),
            FrameEffect(frameRatioDelegate.currentFrameRatio())
        ))
    }

    override fun buildSnapshot(headline: String, detail: String): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.PORTRAIT,
            uiSpec = ModeUiSpec(
                title = "Portrait",
                shutterLabel = "Capture Portrait",
                secondaryActionLabel = "Cycle Portrait Style",
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

    override fun defaultDetail(): String = styleSummary(currentStyle())

    override fun submitCapture(): ModeSignal {
        val style = currentStyle()
        val portraitSettings = portraitSettings()
        context.eventSink("portrait.capture.requested.${style.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (countdownDuration() == CountdownDuration.OFF) {
                "Portrait capture requested"
            } else {
                "Portrait countdown armed"
            },
            detail = defaultDetail()
        )
        val effectSpec = buildEffectSpec()
        val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
        val basePostProcess = EffectBridge.toPostProcessSpec(effectSpec)
        val postProcessSpec = basePostProcess.copy(
            watermarkText = resolvedWatermarkText(style),
            exifOverrides = basePostProcess.exifOverrides + portraitExifOverrides(style, portraitSettings),
            algorithmProfile = proVariantDelegate.resolvedAlgorithmProfile(style.id)
        )
        return ModeSignal.SubmitCapture(
            if (livePhotoEnabledByDefault()) {
                CaptureStrategy.LivePhoto(
                    saveRequest = SaveRequest.photoLibrary(
                        relativePath = "Pictures/OpenCamera/Portrait",
                        fileNamePrefix = "OpenCamera_PORTRAIT",
                        metadata = MediaMetadata(
                            customTags = buildMap {
                                put("mode", "portrait")
                                put("style", style.id)
                                put("subjectTracking", style.subjectTracking.toString())
                                put("livePhotoDefault", "on")
                                put("watermarkModeName", "Portrait")
                                put("watermarkProfileName", style.label)
                                putAll(context.settingsSnapshot.catalog.liveMediaBundleDraft.liveWatermarkMetadataTags())
                                put("stillQuality", runtimeState().stillCaptureQuality.tagValue)
                                put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                                put("modeVariant", if (proVariantDelegate.proVariantEnabled) "pro" else "standard")
                                putAll(proVariantDelegate.buildMetadataTags())
                                putAll(context.captureAidMetadataTags())
                                style.bokehStrength?.let { put("bokehStrength", it.toString()) }
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
                        relativePath = "Pictures/OpenCamera/Portrait",
                        fileNamePrefix = "OpenCamera_PORTRAIT",
                        metadata = MediaMetadata(
                            customTags = buildMap {
                                put("mode", "portrait")
                                put("style", style.id)
                                put("subjectTracking", style.subjectTracking.toString())
                                put("livePhotoDefault", "off")
                                put("watermarkModeName", "Portrait")
                                put("watermarkProfileName", style.label)
                                put("stillQuality", runtimeState().stillCaptureQuality.tagValue)
                                put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                                put("modeVariant", if (proVariantDelegate.proVariantEnabled) "pro" else "standard")
                                putAll(proVariantDelegate.buildMetadataTags())
                                putAll(context.captureAidMetadataTags())
                                style.bokehStrength?.let { put("bokehStrength", it.toString()) }
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
        styleIndex = (styleIndex + 1) % currentStyles().size
        val style = currentStyle()
        context.eventSink("portrait.style.selected.${style.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (depthEffectEnabled()) "Portrait style updated" else "Portrait focus style updated",
            detail = defaultDetail()
        )
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("Portrait style: ${style.label}")
    }

    // ── 可选覆盖 ────────────────────────────────────────────

    override fun enterHeadline(): String {
        return if (depthEffectEnabled()) "Portrait mode active" else "Portrait focus active"
    }

    override fun onModeEnter() {
        styleIndex = resolvedDefaultStyleIndex().coerceAtMost(currentStyles().lastIndex)
    }

    override fun onDeviceCapabilitiesChangedImpl(dc: DeviceCapabilities) {
        styleIndex = styleIndex.coerceAtMost(currentStyles().lastIndex)
    }

    override fun onStillCaptureQualityChanged(stillCaptureQuality: StillCaptureQualityPreference) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (depthEffectEnabled()) "Portrait quality updated" else "Focus quality updated",
            detail = defaultDetail()
        )
    }

    override fun onStillCaptureResolutionChanged(stillCaptureResolutionPreset: StillCaptureResolutionPreset) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (depthEffectEnabled()) "Portrait resolution updated" else "Focus resolution updated",
            detail = defaultDetail()
        )
    }

    override fun handleTertiary(): ModeSignal {
        return frameRatioDelegate.cycleFrameRatio(
            updateSnapshot = { headline ->
                mutableSnapshot.value = buildSnapshot(headline = headline, detail = defaultDetail())
            },
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
        // 需要触发 event 和 snapshot 更新
        // 注意：proVariantDelegate.toggleProVariant() 只切换状态和返回信号
        // 事件发送和快照更新由调用方负责
        // 但这样太啰嗦。改用更好的 API 设计。
        // 实际上应该让 delegate 的 toggleProVariant 也接受回调。
        // 或者在这里手动处理。
        // 简化：直接在控制器中处理
        proVariantDelegate.emitToggleEvent() // 这需要是 suspend
        mutableSnapshot.value = buildSnapshot(
            headline = proVariantDelegate.proActiveHeadline("Portrait"),
            detail = defaultDetail()
        )
        return signal
    }

    // ── 私有辅助方法 ────────────────────────────────────────

    // (保留所有 Portrait 特有的辅助方法：portraitExifOverrides, resolvedWatermarkText,
    //  styleSummary, portraitSettings, livePhotoEnabledByDefault, depthEffectEnabled,
    //  currentStyles, resolvePortraitFilters, resolvedDefaultStyleIndex, portraitStyle,
    //  portraitCharacteristics, currentStyle, countdownDuration, PortraitStyle,
    //  PortraitCharacteristics, DEFAULT_PORTRAIT_STYLES, toCaptureSpec, onOffLabel)
    // ... 完整保留，不变 ...
}
```

## 关键变更点

1. **ProVariant 辅助方法替换**：
   - `manualControlsEnabled()` → `proVariantDelegate.manualControlsEnabled()`
   - `currentManualDraft()` → `proVariantDelegate.currentManualDraft()`
   - `currentManualDraftOrNull()` → `proVariantDelegate.currentManualDraftOrNull()`
   - `resolvedControlMode()` → `proVariantDelegate.resolvedControlMode()`
   - `manualDraftState()` → `proVariantDelegate.manualDraftState()`
   - `resolvedAlgorithmProfile(base)` → `proVariantDelegate.resolvedAlgorithmProfile(base)`
   - `proVariantEnabled` → `proVariantDelegate.proVariantEnabled`

2. **`toggleProVariant()` 的处理**：基类的 `handleProAction()` 钩子调用 `proVariantDelegate.toggleProVariant()`，然后手动发送事件和更新快照。由于 `emitToggleEvent()` 是 suspend 的，`handleProAction()` 也需要是 suspend 的。

   **修正**：基类中 `handleProAction()` 应为 `protected open suspend fun handleProAction(): ModeSignal`。

3. **`proActionLabel` 替换**：原代码中 buildSnapshot 内部有复杂的 proActionLabel 逻辑，现在统一使用 `proVariantDelegate.proActionLabel()`。

## 验证清单

- [ ] 继承 `BaseModeController(context)`
- [ ] 所有 `proVariantEnabled` 引用改为 `proVariantDelegate.proVariantEnabled`
- [ ] 所有 ProVariant 辅助方法改为委托调用
- [ ] `toggleProVariant()` 逻辑移到 `handleProAction()` 钩子
- [ ] `cycleFrameRatio()` / `selectFrameRatio()` 替换为委托调用
- [ ] 编译通过：`./gradlew :feature:mode-portrait:compileDebugKotlin`
- [ ] 现有测试通过：`./gradlew :feature:mode-portrait:test`
- [ ] 预估行数：626 → ~380 行
