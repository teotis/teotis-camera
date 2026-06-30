package com.opencamera.feature.humanistic

import com.opencamera.core.effect.EffectBridge
import com.opencamera.core.effect.EffectEntry
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.effect.WatermarkEffect
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.mode.AbstractStillCaptureMode
import com.opencamera.core.mode.CameraModePlugin
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeIntent
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.mode.ProVariantState
import com.opencamera.core.mode.StillShotSessionEventText
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.WatermarkTemplate
import com.opencamera.core.settings.renderStyleColorSpecWithRecipe
import com.opencamera.core.settings.watermarkStyleFor
import com.opencamera.core.settings.defaultFilterRenderSpecOrNull
import com.opencamera.core.settings.liveWatermarkMetadataTags
import com.opencamera.core.settings.compactSummary

class HumanisticModePlugin : CameraModePlugin {
    override val id: ModeId = ModeId.HUMANISTIC

    override fun isSupported(deviceCapabilities: DeviceCapabilities): Boolean {
        return deviceCapabilities.supportsStillCapture
    }

    override fun create(context: ModeContext): ModeController {
        return HumanisticModeController(context)
    }
}

private class HumanisticModeController(
    context: ModeContext
) : AbstractStillCaptureMode(context) {

    private val styles = resolveHumanisticStyles()
    private var styleIndex = resolvedDefaultStyleIndex()
    override val proVariantState = ProVariantState(context)

    override val id: ModeId = ModeId.HUMANISTIC
    override fun modeEventPrefix() = "humanistic"
    override fun initialHeadline() = "人文模式已就绪"

    override suspend fun onModeEnter() {
        styleIndex = resolvedDefaultStyleIndex()
    }

    override fun enterHeadline() = "人文模式已就绪"
    override fun exitHeadline() = "人文模式已退出"
    override fun exitDetail(): String =
        "切换回人文模式以继续街拍。"

    override fun sessionEventText() = StillShotSessionEventText(
        shotStartedHeadline = "街拍拍摄中",
        shotStartedDetail = "街拍请求已提交",
        shotCompletedHeadline = "街拍照片已保存",
        shotFailedHeadline = "街拍失败"
    )

    override suspend fun handleSecondaryAction(): ModeSignal = cycleStyle()

    override suspend fun handleProAction(): ModeSignal = toggleProVariant()

    override suspend fun handleTertiaryAction(): ModeSignal =
        frameRatioDelegate.cycleFrameRatio(
            snapshotHeadline = "画幅已切换",
            updateSnapshot = { headline -> updateSnapshot(headline = headline) }
        )

    override suspend fun handleFrameRatioSelected(ratio: FrameRatio): ModeSignal =
        frameRatioDelegate.selectFrameRatio(
            ratio = ratio,
            updateSnapshot = { headline -> updateSnapshot(headline = headline) }
        )

    override suspend fun handleShutterPressed(): ModeSignal {
        val style = currentStyle()
        context.eventSink("humanistic.capture.requested.${style.id}")
        return super.handleShutterPressed()
    }

    // ── EffectSpec ────────────────────────────────────────────────────

    override fun buildEffectSpec(): EffectSpec {
        val style = currentStyle()
        val photoSettings = context.settingsSnapshot.persisted.photo
        val pipelineResult = renderStyleColorSpecWithRecipe(
            profileId = style.id,
            baseRenderSpec = style.renderSpec,
            colorLabSpec = photoSettings.colorLabSpec,
            styleStrength = photoSettings.styleStrength
        )
        val adjustedRenderSpec = pipelineResult?.finalRenderSpec
        val recipe = pipelineResult?.recipe
            ?: com.opencamera.core.settings.PerceptualColorRecipe.NEUTRAL
        val effects = mutableListOf<EffectEntry>(
            FilterEffect(style.id, adjustedRenderSpec, recipe = recipe)
        )
        if (photoWatermarkEnabled()) {
            val tmpl = selectedWatermarkTemplate()
            val watermarkStyle = context.settingsSnapshot.persisted.photo.watermarkStyleFor(tmpl.id)
            effects += WatermarkEffect(
                templateId = tmpl.id,
                tokens = watermarkTokens(
                    cameraParams = buildString {
                        append(runtimeState().stillCaptureResolutionPreset.label)
                        append(" • ")
                        append(currentFrameRatio().label)
                    }
                ),
                style = watermarkStyle
            )
        }
        effects += FrameEffect(currentFrameRatio())
        return EffectSpec(effects)
    }

    // ── CaptureStrategy ───────────────────────────────────────────────

    override fun buildCaptureStrategy(
        effectSpec: EffectSpec,
        countdownSeconds: Int
    ): ModeSignal.SubmitCapture {
        val style = currentStyle()
        val basePostProcess = EffectBridge.toPostProcessSpec(effectSpec)
        val postProcessSpec = basePostProcess.copy(
            watermarkText = resolvedWatermarkText(style),
            exifOverrides = basePostProcess.exifOverrides + buildMap {
                put("SceneCaptureType", "Humanistic")
                put("HumanisticStyle", style.exifLabel)
                if (proVariantState.isEnabled) {
                    put("HumanisticVariant", proVariantState.variantExifLabel())
                    put("ManualDraft", proVariantState.currentManualDraft().compactSummary())
                }
            },
            algorithmProfile = proVariantState.resolvedAlgorithmProfile(style.algorithmProfile)
        )

        val metadata = captureMetadataTags(
            effectSpec = effectSpec,
            modeTags = buildMap {
                put("mode", "humanistic")
                put("modeDisplay", "humanistic")
                put("style", style.id)
                put("algorithmProfile", style.algorithmProfile)
                if (photoWatermarkEnabled()) {
                    put("watermarkTemplate", selectedWatermarkTemplate().id)
                }
                put(
                    "livePhotoDefault",
                    if (livePhotoEnabled()) "on" else "off"
                )
                putAll(
                    context.settingsSnapshot.catalog.liveMediaBundleDraft.liveWatermarkMetadataTags()
                )
                put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                put("modeVariant", proVariantState.modeVariantTag())
                if (proVariantState.isEnabled) {
                    putAll(proVariantState.metadataTags())
                }
            }
        )

        val saveRequest = SaveRequest.photoLibrary(
            relativePath = "Pictures/OpenCamera/Humanistic",
            fileNamePrefix = "OpenCamera_HUMANISTIC",
            metadata = MediaMetadata(customTags = metadata)
        )

        val strategy = if (livePhotoEnabled()) {
            CaptureStrategy.LivePhoto(
                saveRequest = saveRequest,
                postProcessSpec = postProcessSpec,
                captureProfile = CaptureProfile(
                    manualCaptureParams = proVariantState.currentManualDraftOrNull(),
                    stillCaptureQuality = runtimeState().stillCaptureQuality,
                    stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                ),
                livePhotoSpec = toCaptureSpec(
                    context.settingsSnapshot.persisted.photo.liveSaveFormat
                )
            )
        } else {
            CaptureStrategy.SingleFrame(
                saveRequest = saveRequest,
                postProcessSpec = postProcessSpec,
                captureProfile = CaptureProfile(
                    manualCaptureParams = proVariantState.currentManualDraftOrNull(),
                    stillCaptureQuality = runtimeState().stillCaptureQuality,
                    stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                )
            )
        }

        return ModeSignal.SubmitCapture(strategy, countdownSeconds = countdownSeconds)
    }

    // ── Snapshot ──────────────────────────────────────────────────────

    override fun buildSnapshot(headline: String, detail: String?): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.HUMANISTIC,
            uiSpec = ModeUiSpec(
                title = "人文",
                shutterLabel = "拍摄人文",
                secondaryActionLabel = "切换滤镜",
                tertiaryActionLabel = "切换画幅",
                proActionLabel = proVariantState.proActionLabel()
            ),
            state = ModeState(
                headline = headline,
                detail = detail ?: buildDefaultDetail(),
                isTertiaryActionEnabled = true,
                isProActionEnabled = true,
                isProVariantActive = proVariantState.isEnabled
            )
        )
    }

    override fun buildDefaultDetail(): String {
        val style = currentStyle()
        val defaultDetail = buildString {
            append("默认风格 ${style.label}")
            append(" | 尺寸 ${runtimeState().stillCaptureResolutionPreset.label}")
            val watermarkLabel = if (photoWatermarkEnabled()) selectedWatermarkTemplate().label else "Off"
            append(" | 水印 $watermarkLabel")
            append(" | 动态照片 ${onOffLabel(livePhotoEnabled())}")
            append(" | 定时 ${countdownDuration().label}")
            append(" | 画幅 ${currentFrameRatio().label}")
            append(" | 支持风格、画幅、动态照片、定时及水印共享设置")
        }
        if (!proVariantState.isEnabled) {
            return defaultDetail
        }
        val proSummary = proVariantState.summaryText("humanistic")
        return "$defaultDetail | $proSummary"
    }

    // ── Humanistic-specific helpers ───────────────────────────────────

    private suspend fun cycleStyle(): ModeSignal {
        styleIndex = (styleIndex + 1) % styles.size
        val style = currentStyle()
        context.eventSink("humanistic.style.selected.${style.id}")
        updateSnapshot(headline = "风格已切换")
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("当前风格：${style.label}")
    }

    private suspend fun toggleProVariant(): ModeSignal {
        val result = proVariantState.toggle("Humanistic")
        context.eventSink("humanistic.pro-variant.${result.eventSuffix}")
        updateSnapshot(
            headline = if (proVariantState.isEnabled) {
                if (proVariantState.manualControlsEnabled()) {
                    "专业控制已启用"
                } else {
                    "专业辅助已启用"
                }
            } else {
                "人文模式已就绪"
            }
        )
        return result.signal
    }

    private fun resolvedWatermarkText(style: HumanisticStyle): String {
        return if (!proVariantState.isEnabled) {
            "人文 ${style.label}"
        } else if (proVariantState.manualControlsEnabled()) {
            "人文 专业 ${style.label}"
        } else {
            "人文 专业辅助 ${style.label}"
        }
    }

    private fun currentStyle(): HumanisticStyle = styles[styleIndex]

    private fun resolvedDefaultStyleIndex(): Int {
        val defaultId = context.settingsSnapshot.persisted.photo.defaultHumanisticFilterProfileId
        return styles.indexOfFirst { style -> style.id == defaultId }
            .takeIf { index -> index >= 0 }
            ?: 0
    }

    private fun resolveHumanisticStyles(): List<HumanisticStyle> {
        val catalogStyles = context.settingsSnapshot.catalog.filterProfiles
            .filter { profile -> profile.category == FilterProfileCategory.HUMANISTIC }
            .map { profile ->
                HumanisticStyle(
                    id = profile.id,
                    label = profile.label,
                    exifLabel = profile.label,
                    algorithmProfile = mappedAlgorithmProfile(profile.id),
                    renderSpec = profile.renderSpec
                        ?: defaultFilterRenderSpecOrNull(mappedAlgorithmProfile(profile.id))
                )
            }
        return if (catalogStyles.isNotEmpty()) {
            catalogStyles
        } else {
            DEFAULT_HUMANISTIC_STYLES
        }
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

    private data class HumanisticStyle(
        val id: String,
        val label: String,
        val exifLabel: String,
        val algorithmProfile: String,
        val renderSpec: FilterRenderSpec?
    )

    companion object {
        private val DEFAULT_HUMANISTIC_STYLES = listOf(
            HumanisticStyle(
                id = "humanistic-original",
                label = "经典",
                exifLabel = "Humanistic Original",
                algorithmProfile = "photo-original",
                renderSpec = defaultFilterRenderSpecOrNull("photo-original")
            ),
            HumanisticStyle(
                id = "humanistic-vivid",
                label = "鲜亮",
                exifLabel = "Humanistic Vivid",
                algorithmProfile = "photo-vivid",
                renderSpec = defaultFilterRenderSpecOrNull("photo-vivid")
            ),
            HumanisticStyle(
                id = "humanistic-street",
                label = "街拍",
                exifLabel = "Humanistic Street",
                algorithmProfile = "photo-chasing-light",
                renderSpec = defaultFilterRenderSpecOrNull("photo-chasing-light")
            ),
            HumanisticStyle(
                id = "humanistic-portrait",
                label = "人像",
                exifLabel = "Humanistic Portrait",
                algorithmProfile = "portrait-original",
                renderSpec = defaultFilterRenderSpecOrNull("humanistic-portrait")
            ),
            HumanisticStyle(
                id = "humanistic-life",
                label = "生活",
                exifLabel = "Humanistic Life",
                algorithmProfile = "photo-rich",
                renderSpec = defaultFilterRenderSpecOrNull("photo-rich")
            )
        )
    }
}
