package com.opencamera.feature.portrait

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.effect.EffectBridge
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.effect.PortraitEffect
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.LivePhotoCaptureSpec
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
import com.opencamera.core.mode.stillCaptureDeviceGraph
import com.opencamera.core.mode.eventTag
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.renderStyleColorSpec
import com.opencamera.core.settings.compactSummary
import com.opencamera.core.settings.defaultFilterRenderSpecOrNull
import com.opencamera.core.settings.filterProfilesFor
import com.opencamera.core.settings.liveWatermarkMetadataTags
import com.opencamera.core.settings.toMetadataTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PortraitModePlugin : CameraModePlugin {
    override val id: ModeId = ModeId.PORTRAIT

    override fun isSupported(deviceCapabilities: DeviceCapabilities): Boolean {
        return deviceCapabilities.supportsStillCapture
    }

    override fun create(context: ModeContext): ModeController {
        return PortraitModeController(context)
    }
}

private class PortraitModeController(
    private val context: ModeContext
) : ModeController {
    private val frameRatios = listOf(
        FrameRatio.RATIO_4_3,
        FrameRatio.RATIO_16_9,
        FrameRatio.RATIO_1_1
    )
    private val portraitFilters = resolvePortraitFilters()
    private var styleIndex = resolvedDefaultStyleIndex()
    private var frameRatioIndex = 0
    private var proVariantEnabled = false

    private val mutableSnapshot = MutableStateFlow(
        buildSnapshot(
            headline = "Portrait pipeline ready"
        )
    )

    override val id: ModeId = ModeId.PORTRAIT
    override val snapshot: StateFlow<ModeSnapshot> = mutableSnapshot.asStateFlow()

    override fun deviceGraph(): DeviceGraphSpec = currentDeviceGraph()

    override suspend fun onDeviceCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) {
        styleIndex = styleIndex.coerceAtMost(currentStyles().lastIndex)
        mutableSnapshot.value = buildSnapshot(
            headline = if (depthEffectEnabled()) {
                "Portrait mode active"
            } else {
                "Portrait focus active"
            }
        )
    }

    override suspend fun onLensFacingChanged(lensFacing: LensFacing) = Unit

    override suspend fun onStillCaptureQualityChanged(
        stillCaptureQuality: StillCaptureQualityPreference
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (depthEffectEnabled()) {
                "Portrait quality updated"
            } else {
                "Focus quality updated"
            }
        )
    }

    override suspend fun onStillCaptureResolutionChanged(
        stillCaptureResolutionPreset: StillCaptureResolutionPreset
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (depthEffectEnabled()) {
                "Portrait resolution updated"
            } else {
                "Focus resolution updated"
            }
        )
    }

    override suspend fun onEnter() {
        context.eventSink("portrait.enter")
        styleIndex = resolvedDefaultStyleIndex().coerceAtMost(currentStyles().lastIndex)
        mutableSnapshot.value = buildSnapshot(
            headline = if (depthEffectEnabled()) {
                "Portrait mode active"
            } else {
                "Portrait focus active"
            }
        )
        context.onEffectSpecChanged(buildEffectSpec())
    }

    override suspend fun onExit() {
        context.eventSink("portrait.exit")
        mutableSnapshot.value = buildSnapshot(
            headline = "Portrait mode inactive"
        )
    }

    override suspend fun handle(intent: ModeIntent): ModeSignal {
        return when (intent) {
            ModeIntent.ShutterPressed -> submitCurrentStyle()
            ModeIntent.SecondaryActionPressed -> cycleStyle()
            ModeIntent.TertiaryActionPressed -> cycleFrameRatio()
            is ModeIntent.FrameRatioSelected -> selectFrameRatio(intent.ratio)
            ModeIntent.ProActionPressed -> toggleProVariant()
        }
    }

    override suspend fun onSessionEvent(event: ModeSessionEvent) {
        when (event) {
            is ModeSessionEvent.ShotStarted -> {
                if (event.shot.mediaType != MediaType.PHOTO) {
                    return
                }
                mutableSnapshot.value = buildSnapshot(
                    headline = "Portrait capture in progress"
                )
            }

            is ModeSessionEvent.ShotCompleted -> {
                if (event.result.mediaType != MediaType.PHOTO) {
                    return
                }
                mutableSnapshot.value = buildSnapshot(
                    headline = "Portrait saved",
                    detail = event.result.outputPath
                )
            }

            is ModeSessionEvent.ShotFailed -> {
                if (event.mediaType != MediaType.PHOTO) {
                    return
                }
                mutableSnapshot.value = buildSnapshot(
                    headline = "Portrait capture failed",
                    detail = event.reason
                )
            }
        }
    }

    private suspend fun submitCurrentStyle(): ModeSignal {
        val style = currentStyle()
        val portraitSettings = portraitSettings()
        context.eventSink("portrait.capture.requested.${style.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (countdownDuration() == CountdownDuration.OFF) {
                "Portrait capture requested"
            } else {
                "Portrait countdown armed"
            }
        )
        val effectSpec = buildEffectSpec()
        val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
        val basePostProcess = EffectBridge.toPostProcessSpec(effectSpec)
        val postProcessSpec = basePostProcess.copy(
            watermarkText = resolvedWatermarkText(style),
            exifOverrides = basePostProcess.exifOverrides + portraitExifOverrides(style, portraitSettings),
            algorithmProfile = resolvedAlgorithmProfile(style.id)
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
                                put("modeVariant", if (proVariantEnabled) "pro" else "standard")
                                if (proVariantEnabled) {
                                    put("controlMode", resolvedControlMode())
                                    put("manualDraftState", manualDraftState())
                                    putAll(currentManualDraft().toMetadataTags())
                                }
                                putAll(context.captureAidMetadataTags())
                                style.bokehStrength?.let { put("bokehStrength", it.toString()) }
                                putAll(bridgeTags)
                            }
                        )
                    ),
                    postProcessSpec = postProcessSpec,
                    captureProfile = CaptureProfile(
                        manualCaptureParams = currentManualDraftOrNull(),
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
                            put("modeVariant", if (proVariantEnabled) "pro" else "standard")
                            if (proVariantEnabled) {
                                put("controlMode", resolvedControlMode())
                                put("manualDraftState", manualDraftState())
                                putAll(currentManualDraft().toMetadataTags())
                            }
                            putAll(context.captureAidMetadataTags())
                            style.bokehStrength?.let { put("bokehStrength", it.toString()) }
                            putAll(bridgeTags)
                        }
                    )
                ),
                postProcessSpec = postProcessSpec,
                captureProfile = CaptureProfile(
                    manualCaptureParams = currentManualDraftOrNull(),
                    stillCaptureQuality = runtimeState().stillCaptureQuality,
                    stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                )
                )
            },
            countdownSeconds = countdownDuration().seconds
        )
    }

    private fun buildEffectSpec(): EffectSpec {
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
            FrameEffect(currentFrameRatio())
        ))
    }

    private fun portraitExifOverrides(
        style: PortraitStyle,
        portraitSettings: PhotoSettings
    ): Map<String, String> = buildMap {
        put("SceneCaptureType", "Portrait")
        put("PortraitStyle", style.label)
        if (proVariantEnabled) {
            put("PortraitVariant", if (manualControlsEnabled()) "Pro" else "Pro Assist")
            put("ManualDraft", currentManualDraft().compactSummary())
        }
        put("PortraitProfile", portraitSettings.portraitProfile.label)
        put("PortraitBeautyPreset", portraitSettings.portraitBeautyPreset.label)
        put("PortraitBeautyStrength", portraitSettings.portraitBeautyStrength.label)
        put("PortraitBokehEffect", portraitSettings.portraitBokehEffect.label)
        put("DepthEffect", if (depthEffectEnabled()) "simulated-bokeh" else "focus-priority")
        style.bokehStrength?.let { put("DepthStrength", "${it}f") }
    }

    private fun resolvedWatermarkText(style: PortraitStyle): String {
        return if (proVariantEnabled) {
            if (manualControlsEnabled()) {
                "Portrait Pro ${style.label}"
            } else {
                "Portrait Pro Assist ${style.label}"
            }
        } else if (depthEffectEnabled()) {
            "Portrait ${style.label}"
        } else {
            "Portrait Focus ${style.label}"
        }
    }

    private suspend fun cycleStyle(): ModeSignal {
        styleIndex = (styleIndex + 1) % currentStyles().size
        val style = currentStyle()
        context.eventSink("portrait.style.selected.${style.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (depthEffectEnabled()) {
                "Portrait style updated"
            } else {
                "Portrait focus style updated"
            }
        )
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("Portrait style: ${style.label}")
    }

    private suspend fun toggleProVariant(): ModeSignal {
        proVariantEnabled = !proVariantEnabled
        context.eventSink("portrait.pro-variant.${if (proVariantEnabled) "entered" else "exited"}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (proVariantEnabled) {
                if (manualControlsEnabled()) {
                    "Portrait Pro active"
                } else {
                    "Portrait Pro assist active"
                }
            } else if (depthEffectEnabled()) {
                "Portrait mode active"
            } else {
                "Portrait focus active"
            }
        )
        return ModeSignal.ShowHint(
            if (proVariantEnabled) {
                if (manualControlsEnabled()) {
                    "Portrait Pro on"
                } else {
                    "Portrait Pro assist on"
                }
            } else {
                "Portrait Pro off"
            }
        )
    }

    private fun buildSnapshot(
        headline: String,
        detail: String = styleSummary(currentStyle())
    ): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.PORTRAIT,
            uiSpec = ModeUiSpec(
                title = "Portrait",
                shutterLabel = "Capture Portrait",
                secondaryActionLabel = "Cycle Portrait Style",
                tertiaryActionLabel = "Cycle Frame",
                proActionLabel = if (proVariantEnabled) {
                    if (manualControlsEnabled()) {
                        "Exit Pro"
                    } else {
                        "Exit Pro Assist"
                    }
                } else if (manualControlsEnabled()) {
                    "Enter Pro"
                } else {
                    "Enter Pro Assist"
                }
            ),
            state = ModeState(
                headline = headline,
                detail = detail,
                isTertiaryActionEnabled = true,
                isProActionEnabled = true,
                isProVariantActive = proVariantEnabled
            )
        )
    }

    private fun currentDeviceGraph(): DeviceGraphSpec =
        stillCaptureDeviceGraph(runtimeState())

    private fun depthEffectEnabled(): Boolean =
        runtimeState().deviceCapabilities.supportsPortraitDepthEffect
    private fun runtimeState() = context.runtimeState()
    private fun manualControlsEnabled(): Boolean =
        runtimeState().deviceCapabilities.supportsAppliedManualControls
    private fun currentManualDraft() = context.settingsSnapshot.catalog.manualCaptureDraft
    private fun currentManualDraftOrNull() = currentManualDraft().takeIf { proVariantEnabled }
    private fun resolvedControlMode(): String = if (manualControlsEnabled()) "manual" else "assisted"
    private fun manualDraftState(): String = if (manualControlsEnabled()) "metadata-draft" else "unsupported"
    private fun resolvedAlgorithmProfile(base: String): String {
        return if (!proVariantEnabled) {
            base
        } else if (manualControlsEnabled()) {
            "${base}-pro"
        } else {
            "${base}-pro-assist"
        }
    }

    private fun currentStyles(): List<PortraitStyle> {
        return portraitFilters
    }

    private fun resolvePortraitFilters(): List<PortraitStyle> {
        val catalogProfiles = context.settingsSnapshot.catalog
            .filterProfilesFor(FilterProfileCategory.PORTRAIT)
            .map(::portraitStyle)
        return if (catalogProfiles.isNotEmpty()) {
            catalogProfiles
        } else {
            DEFAULT_PORTRAIT_STYLES
        }
    }

    private fun resolvedDefaultStyleIndex(): Int {
        val defaultId = context.settingsSnapshot.persisted.photo.defaultPortraitFilterProfileId
        return portraitFilters.indexOfFirst { style -> style.id == defaultId }
            .takeIf { index -> index >= 0 }
            ?: 0
    }

    private fun portraitStyle(profile: FilterProfile): PortraitStyle {
        val characteristics = portraitCharacteristics(profile.id)
        return PortraitStyle(
            id = profile.id,
            label = profile.label,
            bokehStrength = characteristics.bokehStrength,
            subjectTracking = characteristics.subjectTracking,
            renderSpec = profile.renderSpec ?: defaultFilterRenderSpecOrNull(profile.id)
        )
    }

    private fun portraitCharacteristics(styleId: String): PortraitCharacteristics {
        return when (styleId) {
            "portrait-blue" -> PortraitCharacteristics(bokehStrength = 2.1f, subjectTracking = true)
            "portrait-retro" -> PortraitCharacteristics(bokehStrength = 2.4f, subjectTracking = true)
            "portrait-ccd" -> PortraitCharacteristics(bokehStrength = 1.7f, subjectTracking = false)
            "portrait-vivid" -> PortraitCharacteristics(bokehStrength = 2.0f, subjectTracking = true)
            "portrait-original" -> PortraitCharacteristics(bokehStrength = 1.8f, subjectTracking = true)
            "portrait-chasing-light" -> PortraitCharacteristics(
                bokehStrength = 1.6f,
                subjectTracking = true
            )
            "portrait-rich" -> PortraitCharacteristics(bokehStrength = 2.2f, subjectTracking = false)
            else -> PortraitCharacteristics(bokehStrength = 1.8f, subjectTracking = true)
        }
    }

    private fun currentStyle(): PortraitStyle = currentStyles()[styleIndex]

    private fun styleSummary(style: PortraitStyle): String {
        val portraitSettings = portraitSettings()
        val commonSummary = buildString {
            append("Profile ${portraitSettings.portraitProfile.label}")
            append(" | Beauty ${portraitSettings.portraitBeautyPreset.label}")
            append(" ${portraitSettings.portraitBeautyStrength.label}")
            append(" | Bokeh ${portraitSettings.portraitBokehEffect.label}")
        }
        val standardSummary = if (depthEffectEnabled()) {
            "Style ${style.label} | $commonSummary | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Live ${onOffLabel(livePhotoEnabledByDefault())} | Depth strength ${style.bokehStrength} | Subject tracking ${style.subjectTracking} | Timer ${countdownDuration().label} | Frame ${currentFrameRatio().label} | Portrait depth rendering active."
        } else {
            "Style ${style.label} | $commonSummary | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Live ${onOffLabel(livePhotoEnabledByDefault())} | Timer ${countdownDuration().label} | Frame ${currentFrameRatio().label} | Focus-priority portrait fallback because depth effect is unavailable on this device."
        }
        if (!proVariantEnabled) {
            return standardSummary
        }
        val proSummary = if (manualControlsEnabled()) {
            "Pro draft ${currentManualDraft().compactSummary()} is attached to the portrait request."
        } else {
            "Pro assist keeps ${currentManualDraft().compactSummary()} as saved-only draft because manual controls are unavailable on this device."
        }
        return "$standardSummary | $proSummary"
    }

    private fun countdownDuration(): CountdownDuration =
        context.settingsSnapshot.persisted.photo.countdownDuration

    private fun portraitSettings(): PhotoSettings = context.settingsSnapshot.persisted.photo

    private fun livePhotoEnabledByDefault(): Boolean =
        context.settingsSnapshot.persisted.photo.livePhotoEnabledByDefault

    private suspend fun cycleFrameRatio(): ModeSignal {
        frameRatioIndex = (frameRatioIndex + 1) % frameRatios.size
        val frameRatio = currentFrameRatio()
        context.eventSink("portrait.frame-ratio.selected.${frameRatio.eventTag()}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (depthEffectEnabled()) {
                "Portrait frame updated"
            } else {
                "Focus frame updated"
            }
        )
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("Frame: ${frameRatio.label}")
    }

    private suspend fun selectFrameRatio(ratio: FrameRatio): ModeSignal {
        val nextIndex = frameRatios.indexOf(ratio)
        if (nextIndex < 0) return ModeSignal.ShowHint("当前模式不支持 ${ratio.label} 画幅")
        frameRatioIndex = nextIndex
        context.eventSink("portrait.frame-ratio.selected.${ratio.eventTag()}")
        mutableSnapshot.value = buildSnapshot(headline = "画幅已更新")
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("画幅：${ratio.label}")
    }

    private fun currentFrameRatio(): FrameRatio = frameRatios[frameRatioIndex]

    private fun onOffLabel(enabled: Boolean): String = if (enabled) "On" else "Off"

    private fun com.opencamera.core.settings.LiveMediaBundle.toCaptureSpec(): LivePhotoCaptureSpec {
        return LivePhotoCaptureSpec(
            motionDurationMillis = motionDurationMillis,
            motionMimeType = motionContainer,
            sidecarMimeType = sidecarMimeType
        )
    }

    private data class PortraitStyle(
        val id: String,
        val label: String,
        val bokehStrength: Float? = null,
        val subjectTracking: Boolean,
        val renderSpec: FilterRenderSpec?
    )

    private data class PortraitCharacteristics(
        val bokehStrength: Float?,
        val subjectTracking: Boolean
    )

    companion object {
        private val DEFAULT_PORTRAIT_STYLES = listOf(
            PortraitStyle(
                id = "portrait-original",
                label = "Portrait Original",
                bokehStrength = 1.8f,
                subjectTracking = true,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-original")
            ),
            PortraitStyle(
                id = "portrait-vivid",
                label = "Portrait Vivid",
                bokehStrength = 2.0f,
                subjectTracking = true,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-vivid")
            ),
            PortraitStyle(
                id = "portrait-blue",
                label = "Portrait Blue",
                bokehStrength = 2.1f,
                subjectTracking = true,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-blue")
            ),
            PortraitStyle(
                id = "portrait-retro",
                label = "Portrait Retro",
                bokehStrength = 2.4f,
                subjectTracking = true,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-retro")
            ),
            PortraitStyle(
                id = "portrait-ccd",
                label = "Portrait CCD",
                bokehStrength = 1.7f,
                subjectTracking = false,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-ccd")
            ),
            PortraitStyle(
                id = "portrait-chasing-light",
                label = "Portrait Chasing Light",
                bokehStrength = 1.6f,
                subjectTracking = true,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-chasing-light")
            ),
            PortraitStyle(
                id = "portrait-rich",
                label = "Portrait Rich",
                bokehStrength = 2.2f,
                subjectTracking = false,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-rich")
            )
        )
    }

}
