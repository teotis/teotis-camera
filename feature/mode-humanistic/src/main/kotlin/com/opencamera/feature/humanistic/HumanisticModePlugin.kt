package com.opencamera.feature.humanistic

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
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
import com.opencamera.core.mode.eventTag
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.WatermarkTemplate
import com.opencamera.core.settings.compactSummary
import com.opencamera.core.settings.defaultFilterRenderSpecOrNull
import com.opencamera.core.settings.liveWatermarkMetadataTags
import com.opencamera.core.settings.toMetadataTags
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
    private val frameRatios = listOf(
        FrameRatio.RATIO_4_3,
        FrameRatio.RATIO_16_9,
        FrameRatio.RATIO_1_1
    )
    private val styles = resolveHumanisticStyles()
    private var styleIndex = resolvedDefaultStyleIndex()
    private var frameRatioIndex = 0
    private var proVariantEnabled = false

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

    override suspend fun onStillCaptureQualityChanged(
        stillCaptureQuality: StillCaptureQualityPreference
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = "Humanistic quality updated"
        )
    }

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
    }

    override suspend fun onExit() {
        context.eventSink("humanistic.exit")
        mutableSnapshot.value = buildSnapshot(
            headline = "Humanistic mode inactive",
            detail = "Switch back to Humanistic to resume street-life capture."
        )
    }

    override suspend fun handle(intent: ModeIntent): ModeSignal {
        return when (intent) {
            ModeIntent.ShutterPressed -> submitCurrentStyle()
            ModeIntent.SecondaryActionPressed -> cycleStyle()
            ModeIntent.TertiaryActionPressed -> cycleFrameRatio()
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
                    headline = "Humanistic capture in progress",
                    detail = "Street-life still request accepted by the unified shot pipeline."
                )
            }

            is ModeSessionEvent.ShotCompleted -> {
                if (event.result.mediaType != MediaType.PHOTO) {
                    return
                }
                mutableSnapshot.value = buildSnapshot(
                    headline = "Humanistic photo saved",
                    detail = event.result.outputPath
                )
            }

            is ModeSessionEvent.ShotFailed -> {
                if (event.mediaType != MediaType.PHOTO) {
                    return
                }
                mutableSnapshot.value = buildSnapshot(
                    headline = "Humanistic capture failed",
                    detail = event.reason
                )
            }
        }
    }

    private suspend fun submitCurrentStyle(): ModeSignal {
        val style = currentStyle()
        context.eventSink("humanistic.capture.requested.${style.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (countdownDuration() == CountdownDuration.OFF) {
                "Humanistic capture requested"
            } else {
                "Humanistic countdown armed"
            }
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
                                put("filterProfile", style.id)
                                put("algorithmProfile", style.algorithmProfile)
                                put("watermarkTemplate", selectedWatermarkTemplate().id)
                                put("livePhotoDefault", "on")
                                putAll(context.settingsSnapshot.catalog.liveMediaBundleDraft.liveWatermarkMetadataTags())
                                put("frameRatio", currentFrameRatio().tagValue)
                                put("stillQuality", runtimeState().stillCaptureQuality.tagValue)
                                put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                                put("modeVariant", if (proVariantEnabled) "pro" else "standard")
                                if (proVariantEnabled) {
                                    put("controlMode", resolvedControlMode())
                                    put("manualDraftState", manualDraftState())
                                    putAll(currentManualDraft().toMetadataTags())
                                }
                                putAll(context.captureAidMetadataTags())
                                style.renderSpec?.let { putAll(it.toMetadataTags()) }
                            }
                        )
                    ),
                    postProcessSpec = PostProcessSpec(
                        watermarkText = resolvedWatermarkText(style),
                        algorithmProfile = resolvedAlgorithmProfile(style.algorithmProfile),
                        exifOverrides = buildMap {
                            put("SceneCaptureType", "Humanistic")
                            put("HumanisticStyle", style.label)
                            if (proVariantEnabled) {
                                put(
                                    "HumanisticVariant",
                                    if (manualControlsEnabled()) "Pro" else "Pro Assist"
                                )
                                put("ManualDraft", currentManualDraft().compactSummary())
                            }
                        }
                    ),
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
                        relativePath = "Pictures/OpenCamera/Humanistic",
                        fileNamePrefix = "OpenCamera_HUMANISTIC",
                        metadata = MediaMetadata(
                            customTags = buildMap {
                                put("mode", "humanistic")
                                put("modeDisplay", "humanistic")
                                put("style", style.id)
                                put("filterProfile", style.id)
                                put("algorithmProfile", style.algorithmProfile)
                                put("watermarkTemplate", selectedWatermarkTemplate().id)
                                put("livePhotoDefault", "off")
                                put("frameRatio", currentFrameRatio().tagValue)
                                put("stillQuality", runtimeState().stillCaptureQuality.tagValue)
                                put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                                put("modeVariant", if (proVariantEnabled) "pro" else "standard")
                                if (proVariantEnabled) {
                                    put("controlMode", resolvedControlMode())
                                    put("manualDraftState", manualDraftState())
                                    putAll(currentManualDraft().toMetadataTags())
                                }
                                putAll(context.captureAidMetadataTags())
                                style.renderSpec?.let { putAll(it.toMetadataTags()) }
                            }
                        )
                    ),
                    postProcessSpec = PostProcessSpec(
                        watermarkText = resolvedWatermarkText(style),
                        algorithmProfile = resolvedAlgorithmProfile(style.algorithmProfile),
                        exifOverrides = buildMap {
                            put("SceneCaptureType", "Humanistic")
                            put("HumanisticStyle", style.label)
                            if (proVariantEnabled) {
                                put(
                                    "HumanisticVariant",
                                    if (manualControlsEnabled()) "Pro" else "Pro Assist"
                                )
                                put("ManualDraft", currentManualDraft().compactSummary())
                            }
                        }
                    ),
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

    private suspend fun cycleStyle(): ModeSignal {
        styleIndex = (styleIndex + 1) % styles.size
        val style = currentStyle()
        context.eventSink("humanistic.style.selected.${style.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = "Humanistic style updated"
        )
        return ModeSignal.ShowHint("Humanistic style: ${style.label}")
    }

    private suspend fun toggleProVariant(): ModeSignal {
        proVariantEnabled = !proVariantEnabled
        context.eventSink("humanistic.pro-variant.${if (proVariantEnabled) "entered" else "exited"}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (proVariantEnabled) {
                if (manualControlsEnabled()) {
                    "Humanistic Pro active"
                } else {
                    "Humanistic Pro assist active"
                }
            } else {
                "Humanistic mode active"
            }
        )
        return ModeSignal.ShowHint(
            if (proVariantEnabled) {
                if (manualControlsEnabled()) {
                    "Humanistic Pro on"
                } else {
                    "Humanistic Pro assist on"
                }
            } else {
                "Humanistic Pro off"
            }
        )
    }

    private suspend fun cycleFrameRatio(): ModeSignal {
        frameRatioIndex = (frameRatioIndex + 1) % frameRatios.size
        val frameRatio = currentFrameRatio()
        context.eventSink("humanistic.frame-ratio.selected.${frameRatio.eventTag()}")
        mutableSnapshot.value = buildSnapshot(
            headline = "Humanistic frame updated"
        )
        return ModeSignal.ShowHint("Frame: ${frameRatio.label}")
    }

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

    private fun defaultDetail(): String {
        val style = currentStyle()
        val standardSummary = buildString {
            append("Default style ${style.label}")
            append(" | Still ${runtimeState().stillCaptureQuality.label}")
            append(" | Size ${runtimeState().stillCaptureResolutionPreset.label}")
            append(" | Watermark ${selectedWatermarkTemplate().label}")
            append(" | Live ${onOffLabel(livePhotoEnabledByDefault())}")
            append(" | Timer ${countdownDuration().label}")
            append(" | Frame ${currentFrameRatio().label}")
            append(" | Subfeatures style, frame ratio, Live default, timer, and watermark ride the shared settings spine.")
        }
        if (!proVariantEnabled) {
            return standardSummary
        }
        val proSummary = if (manualControlsEnabled()) {
            "Pro draft ${currentManualDraft().compactSummary()} is attached to the humanistic request."
        } else {
            "Pro assist keeps ${currentManualDraft().compactSummary()} as saved-only draft because manual controls are unavailable on this device."
        }
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

    private fun currentDeviceGraph(): DeviceGraphSpec {
        return DeviceGraphSpec.stillCapture(
            preferredLensFacing = runtimeState().lensFacing,
            enablePreviewSnapshots = runtimeState().deviceCapabilities.supportsPreviewSnapshots,
            qualityPreference = runtimeState().stillCaptureQuality,
            resolutionPreset = runtimeState().stillCaptureResolutionPreset
        )
    }

    private fun currentFrameRatio(): FrameRatio = frameRatios[frameRatioIndex]
    private fun resolvedWatermarkText(style: HumanisticStyle): String {
        return if (!proVariantEnabled) {
            "Humanistic ${style.label}"
        } else if (manualControlsEnabled()) {
            "Humanistic Pro ${style.label}"
        } else {
            "Humanistic Pro Assist ${style.label}"
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

    private fun livePhotoEnabledByDefault(): Boolean =
        context.settingsSnapshot.persisted.photo.livePhotoEnabledByDefault
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

    private fun com.opencamera.core.settings.LiveMediaBundle.toCaptureSpec(): LivePhotoCaptureSpec {
        return LivePhotoCaptureSpec(
            motionDurationMillis = motionDurationMillis,
            motionMimeType = motionContainer,
            sidecarMimeType = sidecarMimeType
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
