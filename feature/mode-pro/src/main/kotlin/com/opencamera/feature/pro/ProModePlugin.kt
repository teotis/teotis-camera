package com.opencamera.feature.pro

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.effect.EffectBridge
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.effect.WatermarkEffect
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.FlashMode
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.MediaMetadata

import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.mode.CameraModePlugin
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeIntent
import com.opencamera.core.mode.ModeSessionEvent
import com.opencamera.core.mode.StillShotSessionEventText
import com.opencamera.core.mode.reduceStillShotSessionEvent
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.mode.FrameRatioDelegate
import com.opencamera.core.mode.captureAidMetadataTags
import com.opencamera.core.mode.stillCaptureDeviceGraph
import com.opencamera.core.mode.label
import com.opencamera.core.settings.WatermarkTemplate
import com.opencamera.core.settings.compactSummary
import com.opencamera.core.settings.toMetadataTags
import com.opencamera.core.settings.watermarkStyleFor
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProModePlugin : CameraModePlugin {
    override val id: ModeId = ModeId.PRO

    override fun isSupported(deviceCapabilities: DeviceCapabilities): Boolean {
        return deviceCapabilities.supportsStillCapture
    }

    override fun create(context: ModeContext): ModeController {
        return ProModeController(context)
    }
}

private class ProModeController(
    private val context: ModeContext
) : ModeController {
    private var presetIndex = 0
    private val frameRatioDelegate = FrameRatioDelegate(
        context = context,
        modeEventPrefix = "pro",
        effectSpecProvider = { buildEffectSpec() }
    )

    private val uiSpec = ModeUiSpec(
        title = "专业",
        shutterLabel = "专业拍摄",
        secondaryActionLabel = "切换预设",
        tertiaryActionLabel = "切换画幅"
    )

    private val mutableSnapshot = MutableStateFlow(
        buildSnapshot(
            headline = "专业管线就绪"
        )
    )

    override val id: ModeId = ModeId.PRO
    override val snapshot: StateFlow<ModeSnapshot> = mutableSnapshot.asStateFlow()

    override fun deviceGraph(): DeviceGraphSpec = currentDeviceGraph()

    override suspend fun onDeviceCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) {
        presetIndex = presetIndex.coerceAtMost(currentPresets().lastIndex)
        mutableSnapshot.value = buildSnapshot(
            headline = if (manualControlsEnabled()) {
                "专业模式已激活"
            } else {
                "专业辅助已激活"
            }
        )
    }

    override suspend fun onLensFacingChanged(lensFacing: LensFacing) = Unit

    override suspend fun onStillCaptureResolutionChanged(
        stillCaptureResolutionPreset: StillCaptureResolutionPreset
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (manualControlsEnabled()) {
                "专业分辨率已更新"
            } else {
                "辅助分辨率已更新"
            }
        )
    }

    override suspend fun onStillCaptureQualityChanged(
        stillCaptureQuality: com.opencamera.core.media.StillCaptureQualityPreference
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (manualControlsEnabled()) {
                "专业画质已更新"
            } else {
                "辅助画质已更新"
            }
        )
    }

    override suspend fun onEnter() {
        context.eventSink("pro.enter")
        mutableSnapshot.value = buildSnapshot(
            headline = if (manualControlsEnabled()) {
                "专业模式已激活"
            } else {
                "专业辅助已激活"
            }
        )
        context.onEffectSpecChanged(buildEffectSpec())
    }

    override suspend fun onExit() {
        context.eventSink("pro.exit")
        mutableSnapshot.value = buildSnapshot(
            headline = "专业模式未激活"
        )
    }

    override suspend fun handle(intent: ModeIntent): ModeSignal {
        return when (intent) {
            ModeIntent.ShutterPressed -> submitCurrentPreset()
            ModeIntent.SecondaryActionPressed -> cyclePreset()
            ModeIntent.TertiaryActionPressed -> cycleFrameRatio()
            is ModeIntent.FrameRatioSelected -> selectFrameRatio(intent.ratio)
            ModeIntent.ProActionPressed -> ModeSignal.None
        }
    }

    override suspend fun onSessionEvent(event: ModeSessionEvent) {
        reduceStillShotSessionEvent(
            event = event,
            text = StillShotSessionEventText(
                shotStartedHeadline = "专业拍摄进行中",
                shotCompletedHeadline = "专业照片已保存",
                shotFailedHeadline = "专业拍摄失败"
            ),
            updateSnapshot = { headline, detail ->
                mutableSnapshot.value = if (detail == null) {
                    buildSnapshot(headline = headline)
                } else {
                    buildSnapshot(headline = headline, detail = detail)
                }
            }
        )
    }

    private suspend fun submitCurrentPreset(): ModeSignal {
        val preset = currentPreset()
        val flashMode = resolvedFlashMode(preset)
        context.eventSink("pro.capture.requested.${preset.id}.flash-${flashMode.name.lowercase()}")
        mutableSnapshot.value = buildSnapshot(
            headline = "专业拍摄已请求"
        )
        val effectSpec = buildEffectSpec()
        val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
        val basePostProcess = EffectBridge.toPostProcessSpec(effectSpec)
        val postProcessSpec = basePostProcess.copy(
            watermarkText = if (manualControlsEnabled()) {
                "PRO ${preset.label}"
            } else {
                "PRO Assist ${preset.label}"
            },
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
                            put(
                                "manualDraftState",
                                if (manualControlsEnabled()) "metadata-draft" else "unsupported"
                            )
                            putAll(context.settingsSnapshot.catalog.manualCaptureDraft.toMetadataTags())
                            put("flash", flashMode.name.lowercase())
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

    private fun buildEffectSpec(): EffectSpec {
        val selectedWatermarkTemplate = selectedWatermarkTemplate()
        val watermarkStyle = context.settingsSnapshot.persisted.photo
            .watermarkStyleFor(selectedWatermarkTemplate.id)
        return EffectSpec(listOf(
            WatermarkEffect(
                templateId = selectedWatermarkTemplate.id,
                tokens = mapOf(
                    "watermarkModel" to "OpenCamera",
                    "watermarkDatetime" to watermarkDateTime(),
                    "watermarkCameraParams" to watermarkCameraParams()
                ),
                style = watermarkStyle
            ),
            FrameEffect(currentFrameRatio())
        ))
    }

    private suspend fun cyclePreset(): ModeSignal {
        presetIndex = (presetIndex + 1) % currentPresets().size
        val preset = currentPreset()
        context.eventSink("pro.preset.selected.${preset.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (manualControlsEnabled()) {
                "手动预设已更新"
            } else {
                "辅助预设已更新"
            }
        )
        return ModeSignal.ShowHint("预设: ${preset.label}")
    }

    private fun buildSnapshot(
        headline: String,
        detail: String = presetSummary(currentPreset())
    ): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.PRO,
            uiSpec = uiSpec,
            state = ModeState(
                headline = headline,
                detail = detail,
                isTertiaryActionEnabled = true,
                isProVariantActive = true
            )
        )
    }

    private fun currentDeviceGraph(): DeviceGraphSpec =
        stillCaptureDeviceGraph(runtimeState())

    private fun manualControlsEnabled(): Boolean =
        runtimeState().deviceCapabilities.supportsAppliedManualControls
    private fun flashSupported(): Boolean = runtimeState().deviceCapabilities.supportsFlashControl

    private fun currentPresets(): List<ProPreset> {
        return if (manualControlsEnabled()) {
            MANUAL_PRESETS
        } else {
            ASSISTED_PRESETS
        }
    }

    private fun currentPreset(): ProPreset = currentPresets()[presetIndex]

    private fun presetSummary(preset: ProPreset): String {
        val flashSummary = if (flashSupported()) {
            "闪光灯 ${resolvedFlashMode(preset).label}"
        } else {
            "此设备不支持闪光灯"
        }
        return if (manualControlsEnabled()) {
            "预设 ${preset.label} | 静态 ${runtimeState().stillCaptureQuality.label} | 尺寸 ${runtimeState().stillCaptureResolutionPreset.label} | ISO ${preset.iso} | ${preset.exposureTime} | 白平衡 ${preset.whiteBalanceKelvin}K | 对焦 ${preset.focusMode} | 草案 ${context.settingsSnapshot.catalog.manualCaptureDraft.compactSummary()} | 画幅 ${currentFrameRatio().label} | $flashSummary"
        } else {
            "预设 ${preset.label} | 静态 ${runtimeState().stillCaptureQuality.label} | 尺寸 ${runtimeState().stillCaptureResolutionPreset.label} | 引导调谐已激活，因为此设备不支持手动控制。 | 草案 ${context.settingsSnapshot.catalog.manualCaptureDraft.compactSummary()} 仅保存。 | 画幅 ${currentFrameRatio().label} | $flashSummary"
        }
    }

    private fun resolvedFlashMode(preset: ProPreset): FlashMode {
        return if (flashSupported()) {
            preset.flashMode
        } else {
            FlashMode.OFF
        }
    }

    private suspend fun cycleFrameRatio(): ModeSignal =
        frameRatioDelegate.cycleFrameRatio(
            snapshotHeadline = if (manualControlsEnabled()) {
                "画幅已更新"
            } else {
                "辅助画幅已更新"
            },
            updateSnapshot = { headline ->
                mutableSnapshot.value = buildSnapshot(headline = headline)
            }
        )

    private suspend fun selectFrameRatio(ratio: FrameRatio): ModeSignal =
        frameRatioDelegate.selectFrameRatio(
            ratio = ratio,
            updateSnapshot = { headline ->
                mutableSnapshot.value = buildSnapshot(headline = headline)
            }
        )

    private fun currentFrameRatio(): FrameRatio = frameRatioDelegate.currentFrameRatio()
    private fun runtimeState() = context.runtimeState()

    private fun selectedWatermarkTemplate(): WatermarkTemplate {
        val persistedTemplateId = context.settingsSnapshot.persisted.photo.defaultWatermarkTemplateId
        return context.settingsSnapshot.catalog.watermarkTemplates.firstOrNull { template ->
            template.id == persistedTemplateId
        } ?: WatermarkTemplate(
            id = persistedTemplateId,
            label = persistedTemplateId
        )
    }

    private fun watermarkDateTime(): String {
        return LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
        )
    }

    private fun watermarkCameraParams(): String {
        return buildString {
            append(runtimeState().stillCaptureResolutionPreset.label)
            append(" • ")
            append(currentFrameRatio().label)
        }
    }

    private data class ProPreset(
        val id: String,
        val label: String,
        val iso: Int? = null,
        val exposureTime: String? = null,
        val whiteBalanceKelvin: Int? = null,
        val focusMode: String? = null,
        val flashMode: FlashMode = FlashMode.OFF,
        val algorithmProfile: String
    )

    companion object {
        private val MANUAL_PRESETS = listOf(
            ProPreset(
                id = "neutral",
                label = "中性",
                iso = 100,
                exposureTime = "1/125s",
                whiteBalanceKelvin = 5200,
                focusMode = "自动",
                flashMode = FlashMode.OFF,
                algorithmProfile = "pro-manual-neutral"
            ),
            ProPreset(
                id = "street",
                label = "街拍",
                iso = 200,
                exposureTime = "1/250s",
                whiteBalanceKelvin = 5000,
                focusMode = "连续",
                flashMode = FlashMode.AUTO,
                algorithmProfile = "pro-manual-street"
            ),
            ProPreset(
                id = "night",
                label = "夜景",
                iso = 800,
                exposureTime = "1/15s",
                whiteBalanceKelvin = 3600,
                focusMode = "无限远",
                flashMode = FlashMode.ON,
                algorithmProfile = "pro-manual-night"
            )
        )

        private val ASSISTED_PRESETS = listOf(
            ProPreset(
                id = "balanced",
                label = "平衡",
                flashMode = FlashMode.OFF,
                algorithmProfile = "pro-assisted-balanced"
            ),
            ProPreset(
                id = "contrast",
                label = "高对比",
                flashMode = FlashMode.AUTO,
                algorithmProfile = "pro-assisted-contrast"
            ),
            ProPreset(
                id = "lowlight",
                label = "弱光",
                flashMode = FlashMode.ON,
                algorithmProfile = "pro-assisted-lowlight"
            )
        )
    }

}
