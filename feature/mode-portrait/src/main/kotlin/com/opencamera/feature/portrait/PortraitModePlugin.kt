package com.opencamera.feature.portrait

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.effect.EffectBridge
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.effect.PortraitEffect
import com.opencamera.core.effect.WatermarkEffect
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.LivePhotoCaptureSpec
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
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.mode.StillShotSessionEventText
import com.opencamera.core.mode.reduceStillShotSessionEvent
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.mode.FrameRatioDelegate
import com.opencamera.core.mode.ProVariantState
import com.opencamera.core.mode.captureAidMetadataTags
import com.opencamera.core.mode.stillCaptureDeviceGraph
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.WatermarkTemplate
import com.opencamera.core.settings.renderStyleColorSpec
import com.opencamera.core.settings.renderStyleColorSpecWithRecipe
import com.opencamera.core.settings.watermarkStyleFor
import com.opencamera.core.settings.compactSummary
import com.opencamera.core.settings.defaultFilterRenderSpecOrNull
import com.opencamera.core.settings.filterProfilesFor
import com.opencamera.core.settings.liveWatermarkMetadataTags
import com.opencamera.core.settings.toMetadataTags
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    private val portraitFilters = resolvePortraitFilters()
    private var styleIndex = resolvedDefaultStyleIndex()
    private val frameRatioDelegate = FrameRatioDelegate(
        context = context,
        modeEventPrefix = "portrait",
        effectSpecProvider = { buildEffectSpec() }
    )
    private val proVariantState = ProVariantState(context)
    private val proVariantEnabled: Boolean
        get() = proVariantState.isEnabled

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

    override suspend fun onStillCaptureQualityChanged(
        stillCaptureQuality: com.opencamera.core.media.StillCaptureQualityPreference
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (depthEffectEnabled()) {
                "Portrait quality updated"
            } else {
                "Focus quality updated"
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
        reduceStillShotSessionEvent(
            event = event,
            text = StillShotSessionEventText(
                shotStartedHeadline = "Portrait capture in progress",
                shotCompletedHeadline = "Portrait saved",
                shotFailedHeadline = "Portrait capture failed"
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
                                put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                                put("modeVariant", proVariantState.modeVariantTag())
                                if (proVariantEnabled) {
                                    putAll(proVariantState.metadataTags())
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
                    livePhotoSpec = context.settingsSnapshot.catalog.liveMediaBundleDraft.toCaptureSpec(context.settingsSnapshot.persisted.photo.liveSaveFormat)
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
        val pipelineResult = renderStyleColorSpecWithRecipe(
            profileId = style.id,
            baseRenderSpec = style.renderSpec,
            colorLabSpec = photoSettings.colorLabSpec,
            styleStrength = photoSettings.styleStrength
        )
        val adjustedRenderSpec = pipelineResult?.finalRenderSpec
        val recipe = pipelineResult?.recipe
            ?: com.opencamera.core.settings.PerceptualColorRecipe.NEUTRAL
        val selectedWatermarkTemplate = selectedWatermarkTemplate()
        val watermarkStyle = context.settingsSnapshot.persisted.photo
            .watermarkStyleFor(selectedWatermarkTemplate.id)
        return EffectSpec(listOf(
            FilterEffect(style.id, adjustedRenderSpec, recipe = recipe),
            PortraitEffect(
                profileId = portraitSettings.portraitProfile.storageKey,
                renderPath = if (depthEffectEnabled()) "depth" else "focus",
                beautyPreset = portraitSettings.portraitBeautyPreset.storageKey,
                beautyStrength = portraitSettings.portraitBeautyStrength.storageKey,
                bokehEffect = portraitSettings.portraitBokehEffect.storageKey,
                depthStrength = portraitSettings.portraitDepthStrength
            ),
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

    private fun portraitExifOverrides(
        style: PortraitStyle,
        portraitSettings: PhotoSettings
    ): Map<String, String> = buildMap {
        put("SceneCaptureType", "Portrait")
        put("PortraitStyle", style.label)
        if (proVariantEnabled) {
            put("PortraitVariant", proVariantState.variantExifLabel())
            put("ManualDraft", currentManualDraft().compactSummary())
        }
        put("PortraitProfile", portraitSettings.portraitProfile.label)
        put("PortraitBeautyPreset", portraitSettings.portraitBeautyPreset.label)
        put("PortraitBeautyStrength", portraitSettings.portraitBeautyStrength.label)
        put("PortraitBokehEffect", portraitSettings.portraitBokehEffect.label)
        put("DepthEffect", if (depthEffectEnabled()) "simulated-bokeh" else "focus-priority")
        style.bokehStrength?.let { put("DepthStrength", "${it}f") }
        put("PortraitDepthStrength", portraitSettings.portraitDepthStrength.toString())
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
        val result = proVariantState.toggle("Portrait")
        context.eventSink("portrait.pro-variant.${result.eventSuffix}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (proVariantEnabled) {
                if (manualControlsEnabled()) {
                    "Portrait Pro active"
                } else {
                    "Portrait Pro assisted active"
                }
            } else if (depthEffectEnabled()) {
                "Portrait mode active"
            } else {
                "Portrait focus active"
            }
        )
        return result.signal
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
                secondaryActionLabel = "Toggle Portrait Style",
                tertiaryActionLabel = "Cycle Frame",
                proActionLabel = proVariantState.proActionLabel()
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
        proVariantState.manualControlsEnabled()
    private fun currentManualDraft() =
        proVariantState.currentManualDraft()
    private fun currentManualDraftOrNull() =
        proVariantState.currentManualDraftOrNull()
    private fun resolvedControlMode(): String =
        proVariantState.resolvedControlMode()
    private fun manualDraftState(): String =
        proVariantState.manualDraftState()
    private fun resolvedAlgorithmProfile(base: String): String =
        proVariantState.resolvedAlgorithmProfile(base)

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
            "Style ${style.label} | $commonSummary | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Live ${onOffLabel(livePhotoEnabledByDefault())} | Bokeh strength ${style.bokehStrength} | Subject tracking ${style.subjectTracking} | Timer ${countdownDuration().label} | Frame ${currentFrameRatio().label} | Portrait depth rendering active."
        } else {
            "Style ${style.label} | $commonSummary | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Live ${onOffLabel(livePhotoEnabledByDefault())} | Timer ${countdownDuration().label} | Frame ${currentFrameRatio().label} | Focus-priority portrait degraded (device does not support depth effect)."
        }
        if (!proVariantEnabled) {
            return standardSummary
        }
        val proSummary = proVariantState.summaryText("portrait")
        return "$standardSummary | $proSummary"
    }

    private fun countdownDuration(): CountdownDuration =
        context.settingsSnapshot.persisted.photo.countdownDuration

    private fun portraitSettings(): PhotoSettings = context.settingsSnapshot.persisted.photo

    private fun livePhotoEnabledByDefault(): Boolean =
        context.settingsSnapshot.persisted.photo.livePhotoEnabledByDefault

    private suspend fun cycleFrameRatio(): ModeSignal =
        frameRatioDelegate.cycleFrameRatio(
            snapshotHeadline = if (depthEffectEnabled()) {
                "Portrait frame ratio updated"
            } else {
                "Focus frame ratio updated"
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

    private fun onOffLabel(enabled: Boolean): String = if (enabled) "On" else "Off"

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

    private fun com.opencamera.core.settings.LiveMediaBundle.toCaptureSpec(
        saveFormat: com.opencamera.core.settings.LiveSaveFormat
    ): LivePhotoCaptureSpec {
        return LivePhotoCaptureSpec(
            motionDurationMillis = motionDurationMillis,
            motionMimeType = motionContainer,
            sidecarMimeType = sidecarMimeType,
            saveFormat = saveFormat
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
                label = "人像-原色",
                bokehStrength = 1.8f,
                subjectTracking = true,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-original")
            ),
            PortraitStyle(
                id = "portrait-vivid",
                label = "人像-鲜艳",
                bokehStrength = 2.0f,
                subjectTracking = true,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-vivid")
            ),
            PortraitStyle(
                id = "portrait-blue",
                label = "人像-蓝调",
                bokehStrength = 2.1f,
                subjectTracking = true,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-blue")
            ),
            PortraitStyle(
                id = "portrait-retro",
                label = "人像-复古",
                bokehStrength = 2.4f,
                subjectTracking = true,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-retro")
            ),
            PortraitStyle(
                id = "portrait-ccd",
                label = "人像-CCD",
                bokehStrength = 1.7f,
                subjectTracking = false,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-ccd")
            ),
            PortraitStyle(
                id = "portrait-chasing-light",
                label = "人像-追光",
                bokehStrength = 1.6f,
                subjectTracking = true,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-chasing-light")
            ),
            PortraitStyle(
                id = "portrait-rich",
                label = "人像-浓郁",
                bokehStrength = 2.2f,
                subjectTracking = false,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-rich")
            )
        )
    }

}
