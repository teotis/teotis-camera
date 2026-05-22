# 工作包 6：Pro 模式迁移到 BaseModeController

## 目标

将 `ProModeController` 从独立实现改为继承 `BaseModeController`。

## 依赖

工作包 1 必须已完成。

## 文件

**路径**: `feature/mode-pro/src/main/kotlin/com/opencamera/feature/pro/ProModePlugin.kt`

## 当前结构分析

ProModeController 的特征：
- **modeName**: "pro"
- **mediaType**: `MediaType.PHOTO`
- **使用 FrameRatio**: 是
- **使用 ProVariant**: 否（Pro 本身就是 Pro 模式，没有切换）
- **SecondaryAction**: `cyclePreset()` — 切换预设
- **TertiaryAction**: `cycleFrameRatio()`
- **ProAction**: `ModeSignal.None`（不需要 Pro 变体切换）
- **自定义 enterHeadline**: 是 — 根据 manualControlsEnabled 判断
- **自定义 onDeviceCapabilitiesChanged**: 是 — 约束 presetIndex
- **自定义 onStillCaptureQualityChanged/ResolutionChanged**: 是 — 条件 headline
- **固定 uiSpec**: 是 — 在属性中定义而非 buildSnapshot 内

## 目标代码

```kotlin
private class ProModeController(
    context: ModeContext
) : BaseModeController(context) {
    override val id: ModeId = ModeId.PRO
    override val modeName: String = "pro"
    override val mediaType: MediaType = MediaType.PHOTO

    private var presetIndex = 0

    private val fixedUiSpec = ModeUiSpec(
        title = "Pro",
        shutterLabel = "Capture Pro Still",
        secondaryActionLabel = "Cycle Preset",
        tertiaryActionLabel = "Cycle Frame"
    )

    // ── 抽象方法实现 ────────────────────────────────────────

    override fun buildEffectSpec(): EffectSpec {
        return EffectSpec(listOf(FrameEffect(frameRatioDelegate.currentFrameRatio())))
    }

    override fun buildSnapshot(headline: String, detail: String): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.PRO,
            uiSpec = fixedUiSpec,
            state = ModeState(
                headline = headline,
                detail = detail,
                isTertiaryActionEnabled = true,
                isProVariantActive = true
            )
        )
    }

    override fun defaultDetail(): String = presetSummary(currentPreset())

    override fun submitCapture(): ModeSignal {
        val preset = currentPreset()
        val flashMode = resolvedFlashMode(preset)
        context.eventSink("pro.capture.requested.${preset.id}.flash-${flashMode.name.lowercase()}")
        mutableSnapshot.value = buildSnapshot(headline = "Pro capture requested", detail = defaultDetail())
        val effectSpec = buildEffectSpec()
        val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
        val basePostProcess = EffectBridge.toPostProcessSpec(effectSpec)
        val postProcessSpec = basePostProcess.copy(
            watermarkText = if (manualControlsEnabled()) "PRO ${preset.label}" else "PRO Assist ${preset.label}",
            exifOverrides = basePostProcess.exifOverrides + if (manualControlsEnabled()) {
                buildMap {
                    preset.iso?.let { put("ISOSpeedRatings", it.toString()) }
                    preset.exposureTime?.let { put("ExposureTime", it) }
                    preset.whiteBalanceKelvin?.let { put("WhiteBalance", "${it}K") }
                    preset.focusMode?.let { put("FocusMode", it) }
                }
            } else {
                emptyMap()
            },
            algorithmProfile = preset.algorithmProfile
        )
        return ModeSignal.SubmitCapture(
            CaptureStrategy.SingleFrame(
                saveRequest = SaveRequest.photoLibrary(
                    relativePath = "Pictures/OpenCamera/Pro",
                    fileNamePrefix = "OpenCamera_PRO",
                    metadata = MediaMetadata(
                        customTags = buildMap {
                            put("mode", "pro")
                            put("preset", preset.id)
                            put("controlMode", if (manualControlsEnabled()) "manual" else "assisted")
                            put("manualDraftState", if (manualControlsEnabled()) "metadata-draft" else "unsupported")
                            putAll(context.settingsSnapshot.catalog.manualCaptureDraft.toMetadataTags())
                            put("flash", flashMode.name.lowercase())
                            put("stillQuality", runtimeState().stillCaptureQuality.tagValue)
                            put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                            putAll(context.captureAidMetadataTags())
                            putAll(bridgeTags)
                        }
                    )
                ),
                postProcessSpec = postProcessSpec,
                captureProfile = CaptureProfile(
                    flashMode = flashMode,
                    manualCaptureParams = context.settingsSnapshot.catalog.manualCaptureDraft,
                    stillCaptureQuality = runtimeState().stillCaptureQuality,
                    stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                )
            )
        )
    }

    override suspend fun cycleSecondary(): ModeSignal {
        presetIndex = (presetIndex + 1) % currentPresets().size
        val preset = currentPreset()
        context.eventSink("pro.preset.selected.${preset.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (manualControlsEnabled()) "Manual preset updated" else "Assist preset updated",
            detail = defaultDetail()
        )
        return ModeSignal.ShowHint("Preset: ${preset.label}")
    }

    // ── 可选覆盖 ────────────────────────────────────────────

    override fun enterHeadline(): String {
        return if (manualControlsEnabled()) "Pro mode active" else "Pro assist active"
    }

    override fun onDeviceCapabilitiesChangedImpl(dc: DeviceCapabilities) {
        presetIndex = presetIndex.coerceAtMost(currentPresets().lastIndex)
    }

    override fun onStillCaptureQualityChanged(stillCaptureQuality: StillCaptureQualityPreference) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (manualControlsEnabled()) "Pro quality updated" else "Assist quality updated",
            detail = defaultDetail()
        )
    }

    override fun onStillCaptureResolutionChanged(stillCaptureResolutionPreset: StillCaptureResolutionPreset) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (manualControlsEnabled()) "Pro resolution updated" else "Assist resolution updated",
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

    // handleProAction 不覆盖 — 基类默认返回 None（Pro 模式没有 Pro 变体切换）

    // ── 私有辅助方法 ────────────────────────────────────────

    private fun manualControlsEnabled(): Boolean = runtimeState().deviceCapabilities.supportsAppliedManualControls
    private fun flashSupported(): Boolean = runtimeState().deviceCapabilities.supportsFlashControl
    private fun currentPresets(): List<ProPreset> = if (manualControlsEnabled()) MANUAL_PRESETS else ASSISTED_PRESETS
    private fun currentPreset(): ProPreset = currentPresets()[presetIndex]

    private fun presetSummary(preset: ProPreset): String {
        val flashSummary = if (flashSupported()) "Flash ${resolvedFlashMode(preset).label}"
            else "Flash unavailable on this device"
        return if (manualControlsEnabled()) {
            "Preset ${preset.label} | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | ISO ${preset.iso} | ${preset.exposureTime} | WB ${preset.whiteBalanceKelvin}K | Focus ${preset.focusMode} | Draft ${context.settingsSnapshot.catalog.manualCaptureDraft.compactSummary()} | Frame ${frameRatioDelegate.currentFrameRatio().label} | $flashSummary"
        } else {
            "Preset ${preset.label} | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Guided tuning active because manual controls are unavailable on this device. | Draft ${context.settingsSnapshot.catalog.manualCaptureDraft.compactSummary()} saved only. | Frame ${frameRatioDelegate.currentFrameRatio().label} | $flashSummary"
        }
    }

    private fun resolvedFlashMode(preset: ProPreset): FlashMode {
        return if (flashSupported()) preset.flashMode else FlashMode.OFF
    }

    private data class ProPreset(
        val id: String, val label: String, val iso: Int? = null,
        val exposureTime: String? = null, val whiteBalanceKelvin: Int? = null,
        val focusMode: String? = null, val flashMode: FlashMode = FlashMode.OFF,
        val algorithmProfile: String
    )

    companion object {
        private val MANUAL_PRESETS = listOf(/* ... 保持不变 ... */)
        private val ASSISTED_PRESETS = listOf(/* ... 保持不变 ... */)
    }
}
```

## 关键变更点

1. **Pro 不使用 ProVariantDelegate**：Pro 模式本身就是 Pro，没有切换逻辑。`handleProAction()` 使用基类默认的 `ModeSignal.None`。
2. **Pro 使用手动控制辅助方法**：但不是通过 ProVariantDelegate，而是直接从 `runtimeState()` 判断 `manualControlsEnabled()`。这是 Pro 特有的逻辑，保留在私有方法中。
3. **固定 uiSpec**：Pro 模式的 uiSpec 在属性中定义（不随状态变化），传入 `buildSnapshot()`。

## 验证清单

- [ ] 继承 `BaseModeController(context)`
- [ ] `proVariantDelegate` 未被使用（Pro 模式不需要）
- [ ] FrameRatio 逻辑替换为委托调用
- [ ] `handleProAction()` 使用基类默认（不覆盖）
- [ ] 编译通过：`./gradlew :feature:mode-pro:compileDebugKotlin`
- [ ] 现有测试通过：`./gradlew :feature:mode-pro:test`
- [ ] 预估行数：415 → ~280 行
