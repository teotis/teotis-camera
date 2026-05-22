# 工作包 2：Photo 模式迁移到 BaseModeController

## 目标

将 `PhotoModeController` 从独立实现改为继承 `BaseModeController`，消除与基类重复的代码。

## 依赖

工作包 1（BaseModeController + 委托类）必须已完成。

## 文件

**路径**: `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`

## 当前结构分析

PhotoModeController 的特征：
- **modeName**: "photo"
- **mediaType**: `MediaType.PHOTO`
- **使用 FrameRatio**: 是（通过 `frameRatioDelegate`）
- **使用 ProVariant**: 否
- **SecondaryAction**: `cycleFlashMode()` — 切换闪光灯
- **自定义 onEnter**: 是 — 重置 `selectedFilter`
- **自定义 onDeviceCapabilitiesChanged**: 是 — 重置闪光灯索引
- **自定义 onSessionEvent**: 否（使用基类默认）
- **自定义 deviceGraph**: 否（使用基类默认 stillCapture）
- **自定义 handleTertiary**: 是 — `cycleFrameRatio()`
- **自定义 handleFrameRatioSelected**: 是 — `selectFrameRatio()`

## 目标代码

`PhotoModePlugin` 工厂类保持不变（`class PhotoModePlugin : CameraModePlugin`）。

`PhotoModeController` 改写如下：

```kotlin
private class PhotoModeController(
    context: ModeContext
) : BaseModeController(context) {
    override val id: ModeId = ModeId.PHOTO
    override val modeName: String = "photo"
    override val mediaType: MediaType = MediaType.PHOTO

    private var flashModeIndex = 0
    private var selectedFilter = resolvedDefaultFilter()

    // ── 必须实现的抽象方法 ──────────────────────────────────

    override fun buildEffectSpec(): EffectSpec {
        val flashMode = currentFlashMode()
        val filter = selectedFilter
        val photoSettings = context.settingsSnapshot.persisted.photo
        val adjustedRenderSpec = renderStyleColorSpec(
            profileId = filter.id,
            baseRenderSpec = filter.renderSpec,
            colorLabSpec = photoSettings.colorLabSpec,
            styleStrength = photoSettings.styleStrength
        )
        val selectedWatermarkTemplate = selectedWatermarkTemplate()
        val watermarkStyle = context.settingsSnapshot.persisted.photo
            .watermarkStyleFor(selectedWatermarkTemplate.id)
        return EffectSpec(listOf(
            FilterEffect(filter.id, adjustedRenderSpec),
            WatermarkEffect(
                templateId = selectedWatermarkTemplate.id,
                tokens = mapOf(
                    "watermarkModel" to "OpenCamera",
                    "watermarkDatetime" to watermarkDateTime(),
                    "watermarkCameraParams" to watermarkCameraParams(flashMode)
                ),
                style = watermarkStyle
            ),
            FrameEffect(frameRatioDelegate.currentFrameRatio())
        ))
    }

    override fun buildSnapshot(headline: String, detail: String): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.PHOTO,
            uiSpec = ModeUiSpec(
                title = "Photo",
                shutterLabel = "Capture Still",
                secondaryActionLabel = if (currentFlashSupported()) {
                    "Cycle Flash"
                } else {
                    "Flash Unsupported"
                },
                tertiaryActionLabel = "Cycle Frame"
            ),
            state = ModeState(
                headline = headline,
                detail = detail,
                isSecondaryActionEnabled = currentFlashSupported(),
                isTertiaryActionEnabled = true
            )
        )
    }

    override fun defaultDetail(): String {
        return if (currentFlashSupported()) {
            "Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Filter ${selectedFilter.label} | Watermark ${selectedWatermarkTemplate().label} | Live ${onOffLabel(livePhotoEnabledByDefault())} | Timer ${countdownDuration().label} | Flash ${currentFlashMode().label} | Frame ${frameRatioDelegate.currentFrameRatio().label} | Press shutter to emit a still capture request."
        } else {
            "Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Filter ${selectedFilter.label} | Watermark ${selectedWatermarkTemplate().label} | Live ${onOffLabel(livePhotoEnabledByDefault())} | Timer ${countdownDuration().label} | Flash control unavailable on this device. Frame ${frameRatioDelegate.currentFrameRatio().label} | Press shutter to emit a still capture request."
        }
    }

    override fun submitCapture(): ModeSignal {
        val flashMode = currentFlashMode()
        val filter = selectedFilter
        context.eventSink(
            "photo.capture.requested.${filter.id}.flash-${flashMode.name.lowercase()}"
        )
        mutableSnapshot.value = buildSnapshot(
            headline = if (countdownDuration() == CountdownDuration.OFF) {
                "Still capture requested"
            } else {
                "Countdown armed"
            },
            detail = defaultDetail()
        )
        val effectSpec = buildEffectSpec()
        val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
        val basePostProcessSpec = EffectBridge.toPostProcessSpec(effectSpec)
        val flashLabel = flashMode.name.lowercase().replaceFirstChar { it.titlecase() }
        val postProcessSpec = basePostProcessSpec.copy(
            watermarkText = "PHOTO $flashLabel"
        )
        return ModeSignal.SubmitCapture(
            if (livePhotoEnabledByDefault()) {
                CaptureStrategy.LivePhoto(
                    saveRequest = SaveRequest.photoLibrary(
                        metadata = com.opencamera.core.media.MediaMetadata(
                            customTags = buildMap {
                                put("mode", "photo")
                                put("flash", flashMode.name.lowercase())
                                put("livePhotoDefault", "on")
                                put("watermarkModeName", "Photo")
                                put("watermarkProfileName", flashLabel)
                                putAll(
                                    context.settingsSnapshot.catalog.liveMediaBundleDraft
                                        .liveWatermarkMetadataTags()
                                )
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
                        stillCaptureQuality = runtimeState().stillCaptureQuality,
                        stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                    ),
                    livePhotoSpec = context.settingsSnapshot.catalog.liveMediaBundleDraft
                        .toCaptureSpec()
                )
            } else {
                CaptureStrategy.SingleFrame(
                    saveRequest = SaveRequest.photoLibrary(
                        metadata = com.opencamera.core.media.MediaMetadata(
                            customTags = buildMap {
                                put("mode", "photo")
                                put("flash", flashMode.name.lowercase())
                                put("livePhotoDefault", "off")
                                put("watermarkModeName", "Photo")
                                put("watermarkProfileName", flashLabel)
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
                        stillCaptureQuality = runtimeState().stillCaptureQuality,
                        stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                    )
                )
            },
            countdownSeconds = countdownDuration().seconds
        )
    }

    override fun cycleSecondary(): ModeSignal {
        if (!currentFlashSupported()) {
            return ModeSignal.ShowHint("Flash control is unavailable on this device")
        }
        flashModeIndex = (flashModeIndex + 1) % currentFlashModes().size
        val flashMode = currentFlashMode()
        context.eventSink("photo.flash.selected.${flashMode.name.lowercase()}")
        mutableSnapshot.value = buildSnapshot(headline = "Flash mode updated", detail = defaultDetail())
        // Note: emitEffectSpec is called in onEnter template, but here we need it after flash change
        // We need to trigger it manually — the base class doesn't do this for cycleSecondary
        return ModeSignal.ShowHint("Flash: ${flashMode.label}")
    }

    // ── 可选覆盖 ────────────────────────────────────────────

    override fun onModeEnter() {
        selectedFilter = resolvedDefaultFilter()
    }

    override fun onDeviceCapabilitiesChangedImpl(dc: DeviceCapabilities) {
        if (!currentFlashSupported()) {
            flashModeIndex = 0
        }
    }

    override fun handleTertiary(): ModeSignal {
        return frameRatioDelegate.cycleFrameRatio(
            updateSnapshot = { headline ->
                mutableSnapshot.value = buildSnapshot(headline = headline, detail = defaultDetail())
            },
            emitEffect = { effectSpec ->
                context.onEffectSpecChanged(effectSpec)
            }
        )
    }

    override fun handleFrameRatioSelected(ratio: FrameRatio): ModeSignal {
        return frameRatioDelegate.selectFrameRatio(
            ratio = ratio,
            updateSnapshot = { headline ->
                mutableSnapshot.value = buildSnapshot(headline = headline, detail = defaultDetail())
            },
            emitEffect = { effectSpec ->
                context.onEffectSpecChanged(effectSpec)
            }
        )
    }

    // ── 私有辅助方法（保留，因为它们是 Photo 特有的） ────────

    private fun currentFlashModes(): List<FlashMode> {
        return if (currentFlashSupported()) {
            listOf(FlashMode.OFF, FlashMode.AUTO, FlashMode.ON)
        } else {
            listOf(FlashMode.OFF)
        }
    }

    private fun currentFlashSupported(): Boolean = runtimeState().deviceCapabilities.supportsFlashControl
    private fun currentFlashMode(): FlashMode = currentFlashModes()[flashModeIndex]
    private fun selectedWatermarkTemplate(): WatermarkTemplate {
        val persistedTemplateId = context.settingsSnapshot.persisted.photo.defaultWatermarkTemplateId
        return context.settingsSnapshot.catalog.watermarkTemplates.firstOrNull { template ->
            template.id == persistedTemplateId
        } ?: WatermarkTemplate(id = persistedTemplateId, label = persistedTemplateId)
    }
    private fun livePhotoEnabledByDefault(): Boolean =
        context.settingsSnapshot.persisted.photo.livePhotoEnabledByDefault
    private fun com.opencamera.core.settings.LiveMediaBundle.toCaptureSpec(): LivePhotoCaptureSpec {
        return LivePhotoCaptureSpec(
            motionDurationMillis = motionDurationMillis,
            motionMimeType = motionContainer,
            sidecarMimeType = sidecarMimeType
        )
    }
    private fun countdownDuration(): CountdownDuration = context.settingsSnapshot.persisted.photo.countdownDuration
    private fun resolvedDefaultFilter(): FilterProfile {
        return context.settingsSnapshot.catalog.filterProfileOrNull(
            context.settingsSnapshot.persisted.photo.defaultFilterProfileId
        ) ?: DEFAULT_PHOTO_FILTER
    }
    private fun watermarkDateTime(): String {
        return LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
        )
    }
    private fun watermarkCameraParams(flashMode: FlashMode): String {
        return buildString {
            append(runtimeState().stillCaptureResolutionPreset.label)
            append(" • ")
            append(frameRatioDelegate.currentFrameRatio().label)
            append(" • Flash ")
            append(flashMode.label)
        }
    }
    private fun onOffLabel(enabled: Boolean): String = if (enabled) "On" else "Off"

    companion object {
        private val DEFAULT_PHOTO_FILTER = FilterProfile(
            id = "photo-vivid",
            label = "Vivid",
            category = FilterProfileCategory.PHOTO,
            renderSpec = com.opencamera.core.settings.defaultFilterRenderSpecOrNull("photo-vivid")
        )
    }
}
```

## 重要注意事项

1. **`cycleSecondary()` 中的 EffectSpec 更新**：原代码在 `cycleFlashMode()` 中调用了 `context.onEffectSpecChanged(buildEffectSpec(flashMode))`。在基类模板中，`cycleSecondary()` 不自动触发 EffectSpec 更新。有两种处理方式：
   - **方式 A**（推荐）：在 `cycleSecondary()` 内部手动调用 `context.onEffectSpecChanged(buildEffectSpec())`
   - **方式 B**：在基类中为 `cycleSecondary` 添加 EffectSpec 更新钩子

   选择方式 A，因为只有 Photo 需要在 SecondaryAction 后更新 EffectSpec（闪光灯影响效果链）。

   因此 `cycleSecondary()` 应改为：
   ```kotlin
   override fun cycleSecondary(): ModeSignal {
       if (!currentFlashSupported()) {
           return ModeSignal.ShowHint("Flash control is unavailable on this device")
       }
       flashModeIndex = (flashModeIndex + 1) % currentFlashModes().size
       val flashMode = currentFlashMode()
       context.eventSink("photo.flash.selected.${flashMode.name.lowercase()}")
       mutableSnapshot.value = buildSnapshot(headline = "Flash mode updated", detail = defaultDetail())
       // 手动触发 EffectSpec 更新（闪光灯影响效果链）
       // 注意：需要在 suspend 上下文中调用，但 cycleSecondary 不是 suspend
       // 解决方案：将 cycleSecondary 改为 suspend，或使用 runBlocking
       // 实际上基类的 cycleSecondary 声明需要是 suspend fun
       return ModeSignal.ShowHint("Flash: ${flashMode.label}")
   }
   ```

   **等等** — 查看基类设计，`cycleSecondary()` 在 `handle()` 中被调用，而 `handle()` 是 `suspend fun`。所以 `cycleSecondary` 可以声明为 `suspend fun`。但当前基类设计中 `cycleSecondary` 不是 suspend 的。

   **修正**：将基类中 `cycleSecondary()` 改为 `protected abstract suspend fun cycleSecondary(): ModeSignal`。

2. **移除的代码**：以下方法由基类提供，子类中删除：
   - `onEnter()` — 基类模板
   - `onExit()` — 基类模板
   - `handle()` — 基类模板
   - `onSessionEvent()` — 基类模板
   - `deviceGraph()` — 基类模板
   - `currentDeviceGraph()` — 基类默认 stillCapture
   - `snapshot` 属性 — 基类提供
   - `mutableSnapshot` 声明 — 基类提供
   - `cycleFrameRatio()` — 委托
   - `selectFrameRatio()` — 委托

3. **保留的代码**：所有 Photo 特有逻辑保留：
   - `submitCapture()` — 独特的闪光灯 + LivePhoto 逻辑
   - `cycleSecondary()` / `cycleFlashMode()` — 闪光灯切换
   - `buildEffectSpec()` — 独特的滤镜 + 水印 + 画幅组合
   - `buildSnapshot()` — 独特的 UI 标签
   - `defaultDetail()` — 独特的详情格式
   - 所有私有辅助方法

## 验证清单

- [ ] `PhotoModeController` 继承 `BaseModeController(context)`
- [ ] 不再手动声明 `mutableSnapshot`、`snapshot`、`id`（使用基类）
- [ ] 不再实现 `onEnter()`、`onExit()`、`handle()`、`onSessionEvent()`、`deviceGraph()`
- [ ] `buildEffectSpec()` 中的 `currentFrameRatio()` 改为 `frameRatioDelegate.currentFrameRatio()`
- [ ] `cycleFrameRatio()` 和 `selectFrameRatio()` 调用替换为委托调用
- [ ] 编译通过：`./gradlew :feature:mode-photo:compileDebugKotlin`
- [ ] 现有测试通过：`./gradlew :feature:mode-photo:test`
- [ ] 预估行数：427 → ~280 行
