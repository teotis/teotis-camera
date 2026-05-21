package com.opencamera.feature.pro

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.effect.EffectBridge
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.FlashMode
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
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
import com.opencamera.core.mode.captureAidMetadataTags
import com.opencamera.core.mode.eventTag
import com.opencamera.core.mode.label
import com.opencamera.core.settings.compactSummary
import com.opencamera.core.settings.toMetadataTags
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
    private val frameRatios = listOf(
        FrameRatio.RATIO_4_3,
        FrameRatio.RATIO_16_9,
        FrameRatio.RATIO_1_1
    )
    private var presetIndex = 0
    private var frameRatioIndex = 0

    private val uiSpec = ModeUiSpec(
        title = "Pro",
        shutterLabel = "Capture Pro Still",
        secondaryActionLabel = "Cycle Preset",
        tertiaryActionLabel = "Cycle Frame"
    )

    private val mutableSnapshot = MutableStateFlow(
        buildSnapshot(
            headline = "Pro pipeline ready"
        )
    )

    override val id: ModeId = ModeId.PRO
    override val snapshot: StateFlow<ModeSnapshot> = mutableSnapshot.asStateFlow()

    override fun deviceGraph(): DeviceGraphSpec = currentDeviceGraph()

    override suspend fun onDeviceCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) {
        presetIndex = presetIndex.coerceAtMost(currentPresets().lastIndex)
        mutableSnapshot.value = buildSnapshot(
            headline = if (manualControlsEnabled()) {
                "Pro mode active"
            } else {
                "Pro assist active"
            }
        )
    }

    override suspend fun onLensFacingChanged(lensFacing: LensFacing) = Unit

    override suspend fun onStillCaptureQualityChanged(
        stillCaptureQuality: StillCaptureQualityPreference
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (manualControlsEnabled()) {
                "Pro quality updated"
            } else {
                "Assist quality updated"
            }
        )
    }

    override suspend fun onStillCaptureResolutionChanged(
        stillCaptureResolutionPreset: StillCaptureResolutionPreset
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (manualControlsEnabled()) {
                "Pro resolution updated"
            } else {
                "Assist resolution updated"
            }
        )
    }

    override suspend fun onEnter() {
        context.eventSink("pro.enter")
        mutableSnapshot.value = buildSnapshot(
            headline = if (manualControlsEnabled()) {
                "Pro mode active"
            } else {
                "Pro assist active"
            }
        )
        context.onEffectSpecChanged(buildEffectSpec())
    }

    override suspend fun onExit() {
        context.eventSink("pro.exit")
        mutableSnapshot.value = buildSnapshot(
            headline = "Pro mode inactive"
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
        when (event) {
            is ModeSessionEvent.ShotStarted -> {
                if (event.shot.mediaType != MediaType.PHOTO) {
                    return
                }
                mutableSnapshot.value = buildSnapshot(
                    headline = "Pro capture in progress"
                )
            }

            is ModeSessionEvent.ShotCompleted -> {
                if (event.result.mediaType != MediaType.PHOTO) {
                    return
                }
                mutableSnapshot.value = buildSnapshot(
                    headline = "Pro photo saved",
                    detail = event.result.outputPath
                )
            }

            is ModeSessionEvent.ShotFailed -> {
                if (event.mediaType != MediaType.PHOTO) {
                    return
                }
                mutableSnapshot.value = buildSnapshot(
                    headline = "Pro capture failed",
                    detail = event.reason
                )
            }
        }
    }

    private suspend fun submitCurrentPreset(): ModeSignal {
        val preset = currentPreset()
        val flashMode = resolvedFlashMode(preset)
        context.eventSink("pro.capture.requested.${preset.id}.flash-${flashMode.name.lowercase()}")
        mutableSnapshot.value = buildSnapshot(
            headline = "Pro capture requested"
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

    private fun buildEffectSpec(): EffectSpec {
        return EffectSpec(listOf(
            FrameEffect(currentFrameRatio())
        ))
    }

    private suspend fun cyclePreset(): ModeSignal {
        presetIndex = (presetIndex + 1) % currentPresets().size
        val preset = currentPreset()
        context.eventSink("pro.preset.selected.${preset.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (manualControlsEnabled()) {
                "Manual preset updated"
            } else {
                "Assist preset updated"
            }
        )
        return ModeSignal.ShowHint("Preset: ${preset.label}")
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

    private fun currentDeviceGraph(): DeviceGraphSpec {
        return DeviceGraphSpec.stillCapture(
            preferredLensFacing = runtimeState().lensFacing,
            enablePreviewSnapshots = runtimeState().deviceCapabilities.supportsPreviewSnapshots,
            qualityPreference = runtimeState().stillCaptureQuality,
            resolutionPreset = runtimeState().stillCaptureResolutionPreset
        )
    }

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
            "Flash ${resolvedFlashMode(preset).label}"
        } else {
            "Flash unavailable on this device"
        }
        return if (manualControlsEnabled()) {
            "Preset ${preset.label} | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | ISO ${preset.iso} | ${preset.exposureTime} | WB ${preset.whiteBalanceKelvin}K | Focus ${preset.focusMode} | Draft ${context.settingsSnapshot.catalog.manualCaptureDraft.compactSummary()} | Frame ${currentFrameRatio().label} | $flashSummary"
        } else {
            "Preset ${preset.label} | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Guided tuning active because manual controls are unavailable on this device. | Draft ${context.settingsSnapshot.catalog.manualCaptureDraft.compactSummary()} saved only. | Frame ${currentFrameRatio().label} | $flashSummary"
        }
    }

    private fun resolvedFlashMode(preset: ProPreset): FlashMode {
        return if (flashSupported()) {
            preset.flashMode
        } else {
            FlashMode.OFF
        }
    }

    private suspend fun cycleFrameRatio(): ModeSignal {
        frameRatioIndex = (frameRatioIndex + 1) % frameRatios.size
        val frameRatio = currentFrameRatio()
        context.eventSink("pro.frame-ratio.selected.${frameRatio.eventTag()}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (manualControlsEnabled()) {
                "Frame ratio updated"
            } else {
                "Assist frame ratio updated"
            }
        )
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("Frame: ${frameRatio.label}")
    }

    private suspend fun selectFrameRatio(ratio: FrameRatio): ModeSignal {
        val nextIndex = frameRatios.indexOf(ratio)
        if (nextIndex < 0) return ModeSignal.ShowHint("当前模式不支持 ${ratio.label} 画幅")
        frameRatioIndex = nextIndex
        context.eventSink("pro.frame-ratio.selected.${ratio.eventTag()}")
        mutableSnapshot.value = buildSnapshot(headline = "画幅已更新")
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("画幅：${ratio.label}")
    }

    private fun currentFrameRatio(): FrameRatio = frameRatios[frameRatioIndex]
    private fun runtimeState() = context.runtimeState()

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
                label = "Neutral",
                iso = 100,
                exposureTime = "1/125s",
                whiteBalanceKelvin = 5200,
                focusMode = "Auto",
                flashMode = FlashMode.OFF,
                algorithmProfile = "pro-manual-neutral"
            ),
            ProPreset(
                id = "street",
                label = "Street",
                iso = 200,
                exposureTime = "1/250s",
                whiteBalanceKelvin = 5000,
                focusMode = "Continuous",
                flashMode = FlashMode.AUTO,
                algorithmProfile = "pro-manual-street"
            ),
            ProPreset(
                id = "night",
                label = "Night",
                iso = 800,
                exposureTime = "1/15s",
                whiteBalanceKelvin = 3600,
                focusMode = "Infinity",
                flashMode = FlashMode.ON,
                algorithmProfile = "pro-manual-night"
            )
        )

        private val ASSISTED_PRESETS = listOf(
            ProPreset(
                id = "balanced",
                label = "Balanced",
                flashMode = FlashMode.OFF,
                algorithmProfile = "pro-assisted-balanced"
            ),
            ProPreset(
                id = "contrast",
                label = "Contrast",
                flashMode = FlashMode.AUTO,
                algorithmProfile = "pro-assisted-contrast"
            ),
            ProPreset(
                id = "lowlight",
                label = "Low Light",
                flashMode = FlashMode.ON,
                algorithmProfile = "pro-assisted-lowlight"
            )
        )
    }

}
