package com.opencamera.feature.humanistic

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.effect.EffectBridge
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.FrameEffect
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
import com.opencamera.core.mode.StillShotSessionEventText
import com.opencamera.core.mode.reduceStillShotSessionEvent
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.mode.FrameRatioDelegate
import com.opencamera.core.mode.ProVariantState
import com.opencamera.core.mode.captureAidMetadataTags
import com.opencamera.core.mode.stillCaptureDeviceGraph
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.WatermarkTemplate
import com.opencamera.core.settings.renderStyleColorSpecWithRecipe
import com.opencamera.core.settings.renderStyleColorSpec
import com.opencamera.core.settings.watermarkStyleFor
import com.opencamera.core.settings.compactSummary
import com.opencamera.core.settings.defaultFilterRenderSpecOrNull
import com.opencamera.core.settings.liveWatermarkMetadataTags
import com.opencamera.core.settings.toMetadataTags
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private val context: ModeContext
) : ModeController {
    private val styles = resolveHumanisticStyles()
    private var styleIndex = resolvedDefaultStyleIndex()
    private val frameRatioDelegate = FrameRatioDelegate(
        context = context,
        modeEventPrefix = "humanistic",
        effectSpecProvider = { buildEffectSpec() }
    )
    private val proVariantState = ProVariantState(context)
    private val proVariantEnabled: Boolean
        get() = proVariantState.isEnabled

    private val mutableSnapshot = MutableStateFlow(
        buildSnapshot(
            headline = "Humanistic pipeline ready"
        )
    )

    override val id: ModeId = ModeId.HUMANISTIC
    override val snapshot: StateFlow<ModeSnapshot> = mutableSnapshot.asStateFlow()

    override fun deviceGraph(): DeviceGraphSpec = currentDeviceGraph()

    override suspend fun onDeviceCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) {
        mutableSnapshot.value = buildSnapshot(
            headline = "Humanistic mode active"
        )
    }

    override suspend fun onLensFacingChanged(lensFacing: LensFacing) = Unit


    override suspend fun onStillCaptureResolutionChanged(
        stillCaptureResolutionPreset: StillCaptureResolutionPreset
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = "Humanistic resolution updated"
        )
    }

    override suspend fun onEnter() {
        context.eventSink("humanistic.enter")
        styleIndex = resolvedDefaultStyleIndex()
        mutableSnapshot.value = buildSnapshot(
            headline = "Humanistic mode active"
        )
        context.onEffectSpecChanged(buildEffectSpec())
    }

    override suspend fun onExit() {
        context.eventSink("humanistic.exit")
        mutableSnapshot.value = buildSnapshot(
            headline = "Humanistic mode inactive",
            detail = "Switch back to Humanistic mode to continue street photography."
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
                shotStartedHeadline = "Humanistic capture in progress",
                shotStartedDetail = "Street capture request accepted by unified pipeline.",
                shotCompletedHeadline = "Humanistic photo saved",
                shotFailedHeadline = "Humanistic capture failed"
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
        context.eventSink("humanistic.capture.requested.${style.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (countdownDuration() == CountdownDuration.OFF) {
                "Humanistic capture requested"
            } else {
                "Humanistic countdown started"
            }
        )
        val effectSpec = buildEffectSpec()
        val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
        val basePostProcess = EffectBridge.toPostProcessSpec(effectSpec)
        val postProcessSpec = basePostProcess.copy(
            watermarkText = resolvedWatermarkText(style),
            exifOverrides = basePostProcess.exifOverrides + buildMap {
                put("SceneCaptureType", "Humanistic")
                put("HumanisticStyle", style.label)
                if (proVariantEnabled) {
                    put("HumanisticVariant", proVariantState.variantExifLabel())
                    put("ManualDraft", currentManualDraft().compactSummary())
                }
            },
            algorithmProfile = resolvedAlgorithmProfile(style.algorithmProfile)
        )
        return ModeSignal.SubmitCapture(
            if (livePhotoEnabledByDefault()) {
                CaptureStrategy.LivePhoto(
                    saveRequest = SaveRequest.photoLibrary(
                        relativePath = "Pictures/OpenCamera/Humanistic",
                        fileNamePrefix = "OpenCamera_HUMANISTIC",
                        metadata = MediaMetadata(
                            customTags = buildMap {
                                put("mode", "humanistic")
                                put("modeDisplay", "humanistic")
                                put("style", style.id)
                                put("algorithmProfile", style.algorithmProfile)
                                put("watermarkTemplate", selectedWatermarkTemplate().id)
                                put("livePhotoDefault", "on")
                                putAll(context.settingsSnapshot.catalog.liveMediaBundleDraft.liveWatermarkMetadataTags())
                                put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                                put("modeVariant", proVariantState.modeVariantTag())
                                if (proVariantEnabled) {
                                    putAll(proVariantState.metadataTags())
                                }
                                putAll(context.captureAidMetadataTags())
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
                        relativePath = "Pictures/OpenCamera/Humanistic",
                        fileNamePrefix = "OpenCamera_HUMANISTIC",
                        metadata = MediaMetadata(
                            customTags = buildMap {
                                put("mode", "humanistic")
                                put("modeDisplay", "humanistic")
                                put("style", style.id)
                                put("algorithmProfile", style.algorithmProfile)
                                put("watermarkTemplate", selectedWatermarkTemplate().id)
                                put("livePhotoDefault", "off")
                                put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                                put("modeVariant", proVariantState.modeVariantTag())
                                if (proVariantEnabled) {
                                    putAll(proVariantState.metadataTags())
                                }
                                putAll(context.captureAidMetadataTags())
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

    private suspend fun cycleStyle(): ModeSignal {
        styleIndex = (styleIndex + 1) % styles.size
        val style = currentStyle()
        context.eventSink("humanistic.style.selected.${style.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = "Humanistic style updated"
        )
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("Humanistic style: ${style.label}")
    }

    private suspend fun toggleProVariant(): ModeSignal {
        val result = proVariantState.toggle("Humanistic")
        context.eventSink("humanistic.pro-variant.${result.eventSuffix}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (proVariantEnabled) {
                if (manualControlsEnabled()) {
                    "Professional controls active"
                } else {
                    "Professional assist active"
                }
            } else {
                "人文模式已激活"
            }
        )
        return result.signal
    }

    private suspend fun cycleFrameRatio(): ModeSignal =
        frameRatioDelegate.cycleFrameRatio(
            snapshotHeadline = "Humanistic frame ratio updated",
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

    private fun buildSnapshot(
        headline: String,
        detail: String = defaultDetail()
    ): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.HUMANISTIC,
            uiSpec = ModeUiSpec(
                title = "Humanistic",
                shutterLabel = "Capture Humanistic",
                secondaryActionLabel = "Cycle Humanistic Style",
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

    private fun defaultDetail(): String {
        val style = currentStyle()
        val standardSummary = buildString {
            append("Default style ${style.label}")
            append(" | Size ${runtimeState().stillCaptureResolutionPreset.label}")
            append(" | Watermark ${selectedWatermarkTemplate().label}")
            append(" | Live ${onOffLabel(livePhotoEnabledByDefault())}")
            append(" | Timer ${countdownDuration().label}")
            append(" | Frame ${currentFrameRatio().label}")
            append(" | Subfeatures style, frame ratio, Live default, timer, and watermark share settings.")
        }
        if (!proVariantEnabled) {
            return standardSummary
        }
        val proSummary = proVariantState.summaryText("humanistic")
        return "$standardSummary | $proSummary"
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

    private fun currentDeviceGraph(): DeviceGraphSpec =
        stillCaptureDeviceGraph(runtimeState())

    private fun currentFrameRatio(): FrameRatio = frameRatioDelegate.currentFrameRatio()
    private fun resolvedWatermarkText(style: HumanisticStyle): String {
        return if (!proVariantEnabled) {
            "Humanistic ${style.label}"
        } else if (manualControlsEnabled()) {
            "Humanistic Professional ${style.label}"
        } else {
            "Humanistic Professional Assist ${style.label}"
        }
    }

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

    private fun livePhotoEnabledByDefault(): Boolean =
        context.settingsSnapshot.persisted.photo.livePhotoEnabledByDefault
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

    private fun countdownDuration(): CountdownDuration =
        context.settingsSnapshot.persisted.photo.countdownDuration

    private fun runtimeState() = context.runtimeState()

    private fun onOffLabel(enabled: Boolean): String = if (enabled) "On" else "Off"

    private data class HumanisticStyle(
        val id: String,
        val label: String,
        val algorithmProfile: String,
        val renderSpec: FilterRenderSpec?
    )

    companion object {
        private val DEFAULT_HUMANISTIC_STYLES = listOf(
            HumanisticStyle(
                id = "humanistic-original",
                label = "Humanistic Original",
                algorithmProfile = "photo-original",
                renderSpec = defaultFilterRenderSpecOrNull("photo-original")
            ),
            HumanisticStyle(
                id = "humanistic-vivid",
                label = "Humanistic Vivid",
                algorithmProfile = "photo-vivid",
                renderSpec = defaultFilterRenderSpecOrNull("photo-vivid")
            ),
            HumanisticStyle(
                id = "humanistic-street",
                label = "Humanistic Street",
                algorithmProfile = "photo-chasing-light",
                renderSpec = defaultFilterRenderSpecOrNull("photo-chasing-light")
            ),
            HumanisticStyle(
                id = "humanistic-portrait",
                label = "Humanistic Portrait",
                algorithmProfile = "portrait-original",
                renderSpec = defaultFilterRenderSpecOrNull("humanistic-portrait")
            ),
            HumanisticStyle(
                id = "humanistic-life",
                label = "Humanistic Life",
                algorithmProfile = "photo-rich",
                renderSpec = defaultFilterRenderSpecOrNull("photo-rich")
            )
        )
    }
}
