package com.opencamera.feature.photo

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.effect.EffectBridge
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.effect.WatermarkEffect
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.FlashMode
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.mode.AbstractStillCaptureMode
import com.opencamera.core.mode.CameraModePlugin
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.mode.PhotoLowLightRuntimeState
import com.opencamera.core.mode.StillShotSessionEventText
import com.opencamera.core.mode.captureAidMetadataTags
import com.opencamera.core.device.PhotoLowLightStrategySupport
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.renderStyleColorSpecWithRecipe
import com.opencamera.core.settings.liveWatermarkMetadataTags
import com.opencamera.core.settings.watermarkStyleFor

class PhotoModePlugin : CameraModePlugin {
    override val id: ModeId = ModeId.PHOTO
    override fun isSupported(deviceCapabilities: DeviceCapabilities) =
        deviceCapabilities.supportsStillCapture
    override fun create(context: ModeContext): ModeController = PhotoModeController(context)
}

private class PhotoModeController(
    context: ModeContext
) : AbstractStillCaptureMode(context) {

    override val id: ModeId = ModeId.PHOTO
    private var selectedFilter = resolvedDefaultFilter()

    override fun modeEventPrefix() = "photo"
    override fun initialHeadline() = "Photo pipeline ready"

    override fun availableFlashModes() =
        if (flashSupported()) listOf(FlashMode.OFF, FlashMode.AUTO, FlashMode.ON)
        else listOf(FlashMode.OFF)

    override suspend fun onModeEnter() { selectedFilter = resolvedDefaultFilter() }

    override fun capabilitiesChangedHeadline() = "Photo mode active"
    override fun enterHeadline() = "Photo mode active"
    override fun exitHeadline() = "Photo mode inactive"
    override fun resolutionChangedHeadline() = "Photo resolution updated"
    override fun qualityChangedHeadline() = "Photo quality updated"

    override fun sessionEventText() = StillShotSessionEventText(
        shotStartedHeadline = "Photo capture in progress",
        shotStartedDetail = "Unified capture pipeline accepted the still save task.",
        shotCompletedHeadline = "Photo saved",
        shotFailedHeadline = "Photo capture failed"
    )

    override fun buildEffectSpec(): EffectSpec {
        val flashMode = currentFlashMode()
        val filter = selectedFilter
        val photoSettings = context.settingsSnapshot.persisted.photo
        val pipelineResult = renderStyleColorSpecWithRecipe(
            profileId = filter.id, baseRenderSpec = filter.renderSpec,
            colorLabSpec = photoSettings.colorLabSpec, styleStrength = photoSettings.styleStrength
        )
        val adjustedRenderSpec = pipelineResult?.finalRenderSpec
        val recipe = pipelineResult?.recipe ?: com.opencamera.core.settings.PerceptualColorRecipe.NEUTRAL
        val tmpl = selectedWatermarkTemplate()
        val watermarkStyle = context.settingsSnapshot.persisted.photo.watermarkStyleFor(tmpl.id)
        return EffectSpec(listOf(
            FilterEffect(filter.id, adjustedRenderSpec, recipe = recipe),
            WatermarkEffect(tmpl.id, mapOf(
                "watermarkModel" to "OpenCamera",
                "watermarkDatetime" to watermarkDateTime(),
                "watermarkCameraParams" to watermarkCameraParams("Flash ${flashMode.label}")
            ), style = watermarkStyle),
            FrameEffect(currentFrameRatio())
        ))
    }

    override fun buildCaptureStrategy(effectSpec: EffectSpec, countdownSeconds: Int): ModeSignal.SubmitCapture {
        val flashMode = currentFlashMode()
        val flashLabel = flashMode.name.lowercase().replaceFirstChar { it.titlecase() }
        val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
        val postProcessSpec = EffectBridge.toPostProcessSpec(effectSpec).copy(watermarkText = "PHOTO $flashLabel")
        val lowLight = context.photoLowLightRuntimeState

        val strategy = when {
            lowLight.shouldUseNightAssist -> buildLowLightStrategy(flashMode, postProcessSpec, bridgeTags, lowLight)
            livePhotoEnabled() -> CaptureStrategy.LivePhoto(
                saveRequest = buildSaveRequest(flashMode, "on", bridgeTags),
                postProcessSpec = postProcessSpec, captureProfile = captureProfile(flashMode),
                livePhotoSpec = toCaptureSpec(context.settingsSnapshot.persisted.photo.liveSaveFormat)
            )
            else -> CaptureStrategy.SingleFrame(
                saveRequest = buildSaveRequest(flashMode, "off", bridgeTags),
                postProcessSpec = postProcessSpec, captureProfile = captureProfile(flashMode)
            )
        }
        return ModeSignal.SubmitCapture(strategy, countdownSeconds = countdownSeconds)
    }

    override suspend fun handleSecondaryAction(): ModeSignal {
        if (!flashSupported()) return ModeSignal.ShowHint("Flash control unavailable on this device")
        flashModeIndex = (flashModeIndex + 1) % availableFlashModes().size
        val flashMode = currentFlashMode()
        context.eventSink("photo.flash.selected.${flashMode.name.lowercase()}")
        updateSnapshot(headline = "Flash mode updated")
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("Flash: ${flashMode.label}")
    }

    override suspend fun handleShutterPressed(): ModeSignal {
        context.eventSink("photo.capture.requested.${selectedFilter.id}.flash-${currentFlashMode().name.lowercase()}")
        updateSnapshot(headline = if (countdownDuration() == CountdownDuration.OFF) "Still capture requested" else "Countdown started")
        return buildCaptureStrategy(buildEffectSpec(), countdownDuration().seconds)
    }

    override fun buildSnapshot(headline: String, detail: String?) = ModeSnapshot(
        id = ModeId.PHOTO,
        uiSpec = ModeUiSpec("Photo", "Capture Still",
            secondaryActionLabel = if (flashSupported()) "Toggle Flash" else "Flash Unsupported",
            tertiaryActionLabel = "Cycle Frame"),
        state = ModeState(headline = headline, detail = detail ?: buildDefaultDetail(),
            isSecondaryActionEnabled = flashSupported(), isTertiaryActionEnabled = true)
    )

    override fun buildDefaultDetail(): String {
        val flash = if (flashSupported()) "Flash ${currentFlashMode().label}" else "Flash control unavailable."
        return "Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Filter ${selectedFilter.label} | Watermark ${selectedWatermarkTemplate().label} | Live ${onOffLabel(livePhotoEnabled())} | Timer ${countdownDuration().label} | $flash | Frame ${currentFrameRatio().label} | Press shutter to request still capture."
    }

    private fun flashSupported() = runtimeState().deviceCapabilities.supportsFlashControl

    private fun buildSaveRequest(flashMode: FlashMode, liveTag: String, bridgeTags: Map<String, String>) =
        SaveRequest.photoLibrary(metadata = MediaMetadata(customTags = buildMap {
            put("mode", "photo"); put("flash", flashMode.name.lowercase())
            put("livePhotoDefault", liveTag); put("watermarkModeName", "Photo")
            put("watermarkProfileName", flashMode.name.lowercase().replaceFirstChar { it.titlecase() })
            if (liveTag == "on") putAll(context.settingsSnapshot.catalog.liveMediaBundleDraft.liveWatermarkMetadataTags())
            put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
            putAll(context.captureAidMetadataTags()); putAll(bridgeTags)
        }))

    private fun captureProfile(flashMode: FlashMode) = CaptureProfile(
        flashMode = flashMode, stillCaptureQuality = runtimeState().stillCaptureQuality,
        stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset)

    private fun buildLowLightStrategy(flashMode: FlashMode, postProcessSpec: PostProcessSpec,
        bridgeTags: Map<String, String>, lowLight: PhotoLowLightRuntimeState): CaptureStrategy {
        val signal = lowLight.sceneSignal
        val base = buildMap {
            put("mode", "photo"); put("flash", flashMode.name.lowercase())
            put("photoLowLightNightAssist", "on")
            put("photoLowLightBrightnessScore", signal.brightnessScore?.toString() ?: "unknown")
            put("photoLowLightSignalSource", signal.source)
            put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
            putAll(context.captureAidMetadataTags()); putAll(bridgeTags)
        }
        return when (lowLight.support) {
            PhotoLowLightStrategySupport.SUPPORTED_MULTI_FRAME -> {
                val metadata = MediaMetadata(customTags = base + ("photoLowLightStrategy" to "multi-frame"))
                CaptureStrategy.MultiFrame(
                    saveRequest = SaveRequest.photoLibrary(metadata = metadata),
                    postProcessSpec = postProcessSpec,
                    captureProfile = CaptureProfile(
                        frameCount = 6,
                        longExposureMillis = 450L,
                        requiresTripod = false,
                        flashMode = flashMode,
                        stillCaptureQuality = runtimeState().stillCaptureQuality,
                        stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset))
            }
            PhotoLowLightStrategySupport.DEGRADED_SINGLE_FRAME -> {
                val metadata = MediaMetadata(customTags = base + ("photoLowLightStrategy" to "single-frame-degraded"))
                CaptureStrategy.SingleFrame(
                    saveRequest = SaveRequest.photoLibrary(metadata = metadata),
                    postProcessSpec = postProcessSpec.copy(algorithmProfile = "photo-low-light-single-frame"),
                    captureProfile = captureProfile(flashMode))
            }
            PhotoLowLightStrategySupport.UNSUPPORTED -> {
                val metadata = MediaMetadata(customTags = base + ("photoLowLightStrategy" to "unsupported-fallback"))
                CaptureStrategy.SingleFrame(
                    saveRequest = SaveRequest.photoLibrary(metadata = metadata),
                    postProcessSpec = postProcessSpec,
                    captureProfile = captureProfile(flashMode))
            }
        }
    }

    private fun resolvedDefaultFilter() = context.settingsSnapshot.catalog.filterProfileOrNull(
        context.settingsSnapshot.persisted.photo.defaultFilterProfileId) ?: DEFAULT_PHOTO_FILTER

    companion object {
        private val DEFAULT_PHOTO_FILTER = FilterProfile("photo-vivid", "Vivid", FilterProfileCategory.PHOTO,
            renderSpec = com.opencamera.core.settings.defaultFilterRenderSpecOrNull("photo-vivid"))
    }
}
