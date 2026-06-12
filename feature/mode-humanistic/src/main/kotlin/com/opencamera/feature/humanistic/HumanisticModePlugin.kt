package com.opencamera.feature.humanistic

import com.opencamera.core.effect.EffectBridge
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
    override fun initialHeadline() = "Humanistic pipeline ready"

    override suspend fun onModeEnter() {
        styleIndex = resolvedDefaultStyleIndex()
    }

    override fun enterHeadline() = "Humanistic mode active"
    override fun exitHeadline() = "Humanistic mode inactive"
    override fun exitDetail(): String =
        "Switch back to Humanistic mode to continue street photography."

    override fun sessionEventText() = StillShotSessionEventText(
        shotStartedHeadline = "Humanistic capture in progress",
        shotStartedDetail = "Street capture request accepted by unified pipeline.",
        shotCompletedHeadline = "Humanistic photo saved",
        shotFailedHeadline = "Humanistic capture failed"
    )

    override suspend fun handleSecondaryAction(): ModeSignal = cycleStyle()

    override suspend fun handleProAction(): ModeSignal = toggleProVariant()

    override suspend fun handleTertiaryAction(): ModeSignal =
        frameRatioDelegate.cycleFrameRatio(
            snapshotHeadline = "Humanistic frame ratio updated",
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
        val tmpl = selectedWatermarkTemplate()
        val watermarkStyle = context.settingsSnapshot.persisted.photo.watermarkStyleFor(tmpl.id)
        return EffectSpec(listOf(
            FilterEffect(style.id, adjustedRenderSpec, recipe = recipe),
            WatermarkEffect(
                templateId = tmpl.id,
                tokens = watermarkTokens(
                    cameraParams = buildString {
                        append(runtimeState().stillCaptureResolutionPreset.label)
                        append(" • ")
                        append(currentFrameRatio().label)
                    }
                ),
                style = watermarkStyle
            ),
            FrameEffect(currentFrameRatio())
        ))
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
                put("HumanisticStyle", style.label)
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
                put("watermarkTemplate", selectedWatermarkTemplate().id)
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
                title = "Humanistic",
                shutterLabel = "Capture Humanistic",
                secondaryActionLabel = "Cycle Humanistic Style",
                tertiaryActionLabel = "Cycle Frame",
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
        val standardSummary = buildString {
            append("Default style ${style.label}")
            append(" | Size ${runtimeState().stillCaptureResolutionPreset.label}")
            append(" | Watermark ${selectedWatermarkTemplate().label}")
            append(" | Live ${onOffLabel(livePhotoEnabled())}")
            append(" | Timer ${countdownDuration().label}")
            append(" | Frame ${currentFrameRatio().label}")
            append(" | Subfeatures style, frame ratio, Live default, timer, and watermark share settings.")
        }
        if (!proVariantState.isEnabled) {
            return standardSummary
        }
        val proSummary = proVariantState.summaryText("humanistic")
        return "$standardSummary | $proSummary"
    }

    // ── Humanistic-specific helpers ───────────────────────────────────

    private suspend fun cycleStyle(): ModeSignal {
        styleIndex = (styleIndex + 1) % styles.size
        val style = currentStyle()
        context.eventSink("humanistic.style.selected.${style.id}")
        updateSnapshot(headline = "Humanistic style updated")
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("Humanistic style: ${style.label}")
    }

    private suspend fun toggleProVariant(): ModeSignal {
        val result = proVariantState.toggle("Humanistic")
        context.eventSink("humanistic.pro-variant.${result.eventSuffix}")
        updateSnapshot(
            headline = if (proVariantState.isEnabled) {
                if (proVariantState.manualControlsEnabled()) {
                    "Professional controls active"
                } else {
                    "Professional assist active"
                }
            } else {
                "Humanistic mode active"
            }
        )
        return result.signal
    }

    private fun resolvedWatermarkText(style: HumanisticStyle): String {
        return if (!proVariantState.isEnabled) {
            "Humanistic ${style.label}"
        } else if (proVariantState.manualControlsEnabled()) {
            "Humanistic Professional ${style.label}"
        } else {
            "Humanistic Professional Assist ${style.label}"
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
