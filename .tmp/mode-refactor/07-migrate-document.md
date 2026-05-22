# 工作包 7：Document 模式迁移到 BaseModeController

## 目标

将 `DocumentModeController` 从独立实现改为继承 `BaseModeController`。

## 依赖

工作包 1 必须已完成。

## 文件

**路径**: `feature/mode-document/src/main/kotlin/com/opencamera/feature/document/DocumentModePlugin.kt`

## 当前结构分析

DocumentModeController 的特征：
- **modeName**: "document"
- **mediaType**: `MediaType.PHOTO`
- **使用 FrameRatio**: 否（使用自动裁边，返回提示）
- **使用 ProVariant**: 否
- **SecondaryAction**: `cycleProfile()` — 切换扫描风格
- **TertiaryAction**: `ModeSignal.None`
- **ProAction**: `ModeSignal.None`
- **自定义 onEnter**: 否（使用基类默认）
- **自定义 deviceGraph**: 否（使用默认 stillCapture）
- **自定义 enterHeadline**: 是 — 根据 enhancementEnabled 判断
- **自定义 onSessionEvent**: 否（使用基类默认）
- **固定 uiSpec**: 是 — 在属性中定义

## 目标代码

```kotlin
private class DocumentModeController(
    context: ModeContext
) : BaseModeController(context) {
    override val id: ModeId = ModeId.DOCUMENT
    override val modeName: String = "document"
    override val mediaType: MediaType = MediaType.PHOTO

    private var profileIndex = 0

    private val fixedUiSpec = ModeUiSpec(
        title = "Document",
        shutterLabel = "Scan Document",
        secondaryActionLabel = "Cycle Scan Style"
    )

    // ── 抽象方法实现 ────────────────────────────────────────

    override fun buildEffectSpec(): EffectSpec {
        val profile = currentProfile()
        return EffectSpec(listOf(
            DocumentEffect(
                autoCrop = profile.autoCrop,
                contrastProfile = if (enhancementEnabled()) profile.contrastLabel else null
            )
        ))
    }

    override fun buildSnapshot(headline: String, detail: String): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.DOCUMENT,
            uiSpec = fixedUiSpec,
            state = ModeState(headline = headline, detail = detail)
        )
    }

    override fun defaultDetail(): String = profileSummary(currentProfile())

    override fun submitCapture(): ModeSignal {
        val profile = currentProfile()
        context.eventSink("document.capture.requested.${profile.id}")
        mutableSnapshot.value = buildSnapshot(headline = "Document capture requested", detail = defaultDetail())
        val effectSpec = buildEffectSpec()
        val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
        val basePostProcess = EffectBridge.toPostProcessSpec(effectSpec)
        val postProcessSpec = basePostProcess.copy(
            watermarkText = if (enhancementEnabled()) "DOC ${profile.label}" else "DOC Basic ${profile.label}",
            exifOverrides = basePostProcess.exifOverrides + buildMap {
                put("SceneCaptureType", "Document")
                put("ProcessingRendered", if (enhancementEnabled()) "enhanced-scan" else "basic-archive")
                if (enhancementEnabled()) put("Contrast", profile.contrastLabel)
            },
            algorithmProfile = profile.algorithmProfile
        )
        return ModeSignal.SubmitCapture(
            CaptureStrategy.SingleFrame(
                saveRequest = SaveRequest.photoLibrary(
                    relativePath = "Pictures/OpenCamera/Documents",
                    fileNamePrefix = "OpenCamera_DOC",
                    metadata = MediaMetadata(
                        customTags = buildMap {
                            put("mode", "document")
                            put("profile", profile.id)
                            put("scanMode", if (enhancementEnabled()) "enhanced" else "basic")
                            put("outputClass", "scan")
                            put("stillQuality", runtimeState().stillCaptureQuality.tagValue)
                            put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                            putAll(context.captureAidMetadataTags())
                            putAll(bridgeTags)
                        }
                    )
                ),
                postProcessSpec = postProcessSpec,
                captureProfile = com.opencamera.core.media.CaptureProfile(
                    stillCaptureQuality = runtimeState().stillCaptureQuality,
                    stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                )
            )
        )
    }

    override suspend fun cycleSecondary(): ModeSignal {
        profileIndex = (profileIndex + 1) % currentProfiles().size
        val profile = currentProfile()
        context.eventSink("document.profile.selected.${profile.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (enhancementEnabled()) "Document style updated" else "Archive style updated",
            detail = defaultDetail()
        )
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("Scan style: ${profile.label}")
    }

    // ── 可选覆盖 ────────────────────────────────────────────

    override fun enterHeadline(): String {
        return if (enhancementEnabled()) "Document scan active" else "Document archive active"
    }

    override fun onDeviceCapabilitiesChangedImpl(dc: DeviceCapabilities) {
        profileIndex = profileIndex.coerceAtMost(currentProfiles().lastIndex)
    }

    override fun onStillCaptureQualityChanged(stillCaptureQuality: StillCaptureQualityPreference) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (enhancementEnabled()) "Document quality updated" else "Archive quality updated",
            detail = defaultDetail()
        )
    }

    override fun onStillCaptureResolutionChanged(stillCaptureResolutionPreset: StillCaptureResolutionPreset) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (enhancementEnabled()) "Document resolution updated" else "Archive resolution updated",
            detail = defaultDetail()
        )
    }

    override fun handleFrameRatioSelected(ratio: FrameRatio): ModeSignal {
        return ModeSignal.ShowHint("文档模式使用自动裁边，不使用普通画幅")
    }

    // ── 私有辅助方法 ────────────────────────────────────────

    private fun enhancementEnabled(): Boolean = runtimeState().deviceCapabilities.supportsDocumentScanEnhancement
    private fun currentProfiles(): List<DocumentProfile> = if (enhancementEnabled()) ENHANCED_PROFILES else BASIC_PROFILES
    private fun currentProfile(): DocumentProfile = currentProfiles()[profileIndex]

    private fun profileSummary(profile: DocumentProfile): String {
        return if (enhancementEnabled()) {
            "Style ${profile.label} | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Auto crop ${profile.autoCrop} | Contrast ${profile.contrastLabel} | Output tuned for scanned documents."
        } else {
            "Style ${profile.label} | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Basic capture only because document enhancement is unavailable on this device."
        }
    }

    private data class DocumentProfile(
        val id: String, val label: String, val autoCrop: Boolean,
        val contrastLabel: String, val algorithmProfile: String
    )

    companion object {
        private val ENHANCED_PROFILES = listOf(/* ... 保持不变 ... */)
        private val BASIC_PROFILES = listOf(/* ... 保持不变 ... */)
    }
}
```

## 关键变更点

1. **Document 是最简单的模式之一**：不使用 FrameRatio、ProVariant。
2. **`handleFrameRatioSelected` 覆盖**：返回中文提示而非基类默认的"不支持"。
3. **`onExit` 使用基类默认**：原代码的 onExit 只是发送事件 + 设置 inactive snapshot，基类已覆盖。

## 验证清单

- [ ] 继承 `BaseModeController(context)`
- [ ] FrameRatio 和 ProVariant 委托均未使用
- [ ] `handleTertiary()` 和 `handleProAction()` 使用基类默认（None）
- [ ] 编译通过：`./gradlew :feature:mode-document:compileDebugKotlin`
- [ ] 现有测试通过：`./gradlew :feature:mode-document:test`
- [ ] 预估行数：336 → ~200 行
