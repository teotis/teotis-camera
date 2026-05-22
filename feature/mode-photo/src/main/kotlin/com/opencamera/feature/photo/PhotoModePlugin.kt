package com.opencamera.feature.photo

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.effect.EffectBridge
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.effect.WatermarkEffect
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
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.FlashMode
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.LivePhotoCaptureSpec
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.WatermarkTemplate
import com.opencamera.core.settings.renderStyleColorSpec
import com.opencamera.core.settings.liveWatermarkMetadataTags
import com.opencamera.core.settings.watermarkStyleFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PhotoModePlugin : CameraModePlugin {
    override val id: ModeId = ModeId.PHOTO

    override fun isSupported(deviceCapabilities: DeviceCapabilities): Boolean {
        return deviceCapabilities.supportsStillCapture
    }

    override fun create(context: ModeContext): ModeController {
        return PhotoModeController(context)
    }
}

private class PhotoModeController(
    private val context: ModeContext
) : ModeController {
    private val frameRatios = listOf(
        FrameRatio.RATIO_4_3,
        FrameRatio.RATIO_16_9,
        FrameRatio.RATIO_1_1
    )
    private var flashModeIndex = 0
    private var frameRatioIndex = 0
    private var selectedFilter = resolvedDefaultFilter()

    private val mutableSnapshot = MutableStateFlow(
        buildSnapshot(
            headline = "Photo pipeline ready"
        )
    )

    override val id: ModeId = ModeId.PHOTO
    override val snapshot: StateFlow<ModeSnapshot> = mutableSnapshot.asStateFlow()

    override fun deviceGraph(): DeviceGraphSpec = currentDeviceGraph()

    override suspend fun onDeviceCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) {
        if (!currentFlashSupported()) {
            flashModeIndex = 0
        }
        mutableSnapshot.value = buildSnapshot(
            headline = "Photo mode active"
        )
    }

    override suspend fun onLensFacingChanged(lensFacing: LensFacing) = Unit

    override suspend fun onStillCaptureQualityChanged(
        stillCaptureQuality: StillCaptureQualityPreference
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = "Photo quality updated"
        )
    }

    override suspend fun onStillCaptureResolutionChanged(
        stillCaptureResolutionPreset: StillCaptureResolutionPreset
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = "Photo resolution updated"
        )
    }

    override suspend fun onEnter() {
        context.eventSink("photo.enter")
        selectedFilter = resolvedDefaultFilter()
        mutableSnapshot.value = buildSnapshot(
            headline = "Photo mode active"
        )
        context.onEffectSpecChanged(buildEffectSpec(currentFlashMode()))
    }

    override suspend fun onExit() {
        context.eventSink("photo.exit")
        mutableSnapshot.value = buildSnapshot(
            headline = "Photo mode inactive",
            detail = "Switch back to photo to resume still capture."
        )
    }

    override suspend fun handle(intent: ModeIntent): ModeSignal {
        return when (intent) {
            ModeIntent.ShutterPressed -> {
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
                    }
                )
                val effectSpec = buildEffectSpec(flashMode)
                val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
                val basePostProcessSpec = EffectBridge.toPostProcessSpec(effectSpec)
                val flashLabel = flashMode.name.lowercase().replaceFirstChar { it.titlecase() }
                val postProcessSpec = basePostProcessSpec.copy(
                    watermarkText = "PHOTO $flashLabel"
                )
                ModeSignal.SubmitCapture(
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
                                        put(
                                            "stillResolution",
                                            runtimeState().stillCaptureResolutionPreset.tagValue
                                        )
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
                                        put(
                                            "stillResolution",
                                            runtimeState().stillCaptureResolutionPreset.tagValue
                                        )
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

            ModeIntent.SecondaryActionPressed -> cycleFlashMode()
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
                    headline = "Photo capture in progress",
                    detail = "Unified shot pipeline accepted the photo save task."
                )
            }

            is ModeSessionEvent.ShotCompleted -> {
                if (event.result.mediaType != MediaType.PHOTO) {
                    return
                }
                mutableSnapshot.value = buildSnapshot(
                    headline = "Photo saved",
                    detail = event.result.outputPath
                )
            }

            is ModeSessionEvent.ShotFailed -> {
                if (event.mediaType != MediaType.PHOTO) {
                    return
                }
                mutableSnapshot.value = buildSnapshot(
                    headline = "Photo capture failed",
                    detail = event.reason
                )
            }

            else -> Unit
        }
    }

    private suspend fun cycleFlashMode(): ModeSignal {
        if (!currentFlashSupported()) {
            return ModeSignal.ShowHint("Flash control is unavailable on this device")
        }
        flashModeIndex = (flashModeIndex + 1) % currentFlashModes().size
        val flashMode = currentFlashMode()
        context.eventSink("photo.flash.selected.${flashMode.name.lowercase()}")
        mutableSnapshot.value = buildSnapshot(
            headline = "Flash mode updated"
        )
        context.onEffectSpecChanged(buildEffectSpec(flashMode))
        return ModeSignal.ShowHint("Flash: ${flashMode.label}")
    }

    private fun buildSnapshot(
        headline: String,
        detail: String = defaultDetail()
    ): ModeSnapshot {
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

    private fun defaultDetail(): String {
        return if (currentFlashSupported()) {
            "Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Filter ${selectedFilter.label} | Watermark ${selectedWatermarkTemplate().label} | Live ${onOffLabel(livePhotoEnabledByDefault())} | Timer ${countdownDuration().label} | Flash ${currentFlashMode().label} | Frame ${currentFrameRatio().label} | Press shutter to emit a still capture request."
        } else {
            "Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Filter ${selectedFilter.label} | Watermark ${selectedWatermarkTemplate().label} | Live ${onOffLabel(livePhotoEnabledByDefault())} | Timer ${countdownDuration().label} | Flash control unavailable on this device. Frame ${currentFrameRatio().label} | Press shutter to emit a still capture request."
        }
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
            append(currentFrameRatio().label)
            append(" • Flash ")
            append(flashMode.label)
        }
    }

    private fun buildEffectSpec(flashMode: FlashMode): EffectSpec {
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
            FrameEffect(currentFrameRatio())
        ))
    }

    private suspend fun cycleFrameRatio(): ModeSignal {
        frameRatioIndex = (frameRatioIndex + 1) % frameRatios.size
        val frameRatio = currentFrameRatio()
        context.eventSink("photo.frame-ratio.selected.${frameRatio.eventTag()}")
        mutableSnapshot.value = buildSnapshot(
            headline = "Frame ratio updated"
        )
        context.onEffectSpecChanged(buildEffectSpec(currentFlashMode()))
        return ModeSignal.ShowHint("Frame: ${frameRatio.label}")
    }

    private suspend fun selectFrameRatio(ratio: FrameRatio): ModeSignal {
        val nextIndex = frameRatios.indexOf(ratio)
        if (nextIndex < 0) return ModeSignal.ShowHint("当前模式不支持 ${ratio.label} 画幅")
        frameRatioIndex = nextIndex
        context.eventSink("photo.frame-ratio.selected.${ratio.eventTag()}")
        mutableSnapshot.value = buildSnapshot(headline = "画幅已更新")
        context.onEffectSpecChanged(buildEffectSpec(currentFlashMode()))
        return ModeSignal.ShowHint("画幅：${ratio.label}")
    }

    private fun currentDeviceGraph(): DeviceGraphSpec {
        return DeviceGraphSpec.stillCapture(
            preferredLensFacing = runtimeState().lensFacing,
            enablePreviewSnapshots = runtimeState().deviceCapabilities.supportsPreviewSnapshots,
            qualityPreference = runtimeState().stillCaptureQuality,
            resolutionPreset = runtimeState().stillCaptureResolutionPreset
        )
    }

    private fun currentFlashModes(): List<FlashMode> {
        return if (currentFlashSupported()) {
            listOf(FlashMode.OFF, FlashMode.AUTO, FlashMode.ON)
        } else {
            listOf(FlashMode.OFF)
        }
    }

    private fun currentFlashSupported(): Boolean = runtimeState().deviceCapabilities.supportsFlashControl
    private fun currentFlashMode(): FlashMode = currentFlashModes()[flashModeIndex]
    private fun currentFrameRatio(): FrameRatio = frameRatios[frameRatioIndex]
    private fun selectedWatermarkTemplate(): WatermarkTemplate {
        val persistedTemplateId = context.settingsSnapshot.persisted.photo.defaultWatermarkTemplateId
        return context.settingsSnapshot.catalog.watermarkTemplates.firstOrNull { template ->
            template.id == persistedTemplateId
        } ?: WatermarkTemplate(
            id = persistedTemplateId,
            label = persistedTemplateId
        )
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
    private fun runtimeState() = context.runtimeState()

    private fun resolvedDefaultFilter(): FilterProfile {
        return context.settingsSnapshot.catalog.filterProfileOrNull(
            context.settingsSnapshot.persisted.photo.defaultFilterProfileId
        ) ?: DEFAULT_PHOTO_FILTER
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
