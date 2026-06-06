package com.opencamera.feature.checkin

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
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.mode.CameraModePlugin
import com.opencamera.core.mode.FrameRatioDelegate
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
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.WatermarkTemplate
import com.opencamera.core.settings.defaultFilterRenderSpecOrNull
import com.opencamera.core.settings.filterProfilesFor
import com.opencamera.core.settings.liveWatermarkMetadataTags
import com.opencamera.core.settings.renderStyleColorSpecWithRecipe
import com.opencamera.core.settings.watermarkStyleFor
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CheckInModePlugin : CameraModePlugin {
    override val id: ModeId = ModeId.CHECK_IN

    override fun isSupported(deviceCapabilities: DeviceCapabilities): Boolean {
        return deviceCapabilities.supportsStillCapture
    }

    override fun create(context: ModeContext): ModeController {
        return CheckInModeController(context)
    }
}

enum class CheckInScenario(
    val id: String,
    val label: String,
    val filterCategory: FilterProfileCategory,
    val captureStrategyType: StrategyType
) {
    PORTRAIT(
        id = "portrait",
        label = "人像",
        filterCategory = FilterProfileCategory.PORTRAIT,
        captureStrategyType = StrategyType.SINGLE_FRAME
    ),
    PEOPLE_PLACE(
        id = "people-place",
        label = "人景",
        filterCategory = FilterProfileCategory.PORTRAIT,
        captureStrategyType = StrategyType.SINGLE_FRAME
    ),
    OBJECT_PLACE(
        id = "object-place",
        label = "物景",
        filterCategory = FilterProfileCategory.PORTRAIT,
        captureStrategyType = StrategyType.SINGLE_FRAME
    ),
    CLARITY(
        id = "clarity",
        label = "超清",
        filterCategory = FilterProfileCategory.PORTRAIT,
        captureStrategyType = StrategyType.MULTI_FRAME
    );

    enum class StrategyType { SINGLE_FRAME, MULTI_FRAME }
}

private class CheckInModeController(
    private val context: ModeContext
) : ModeController {
    private var scenarioIndex = resolvedDefaultScenarioIndex()
    private val checkInFilters = resolveCheckInFilters()
    private var styleIndex = resolvedDefaultStyleIndex()

    private val frameRatioDelegate = FrameRatioDelegate(
        context = context,
        modeEventPrefix = "checkin",
        effectSpecProvider = { buildEffectSpec() }
    )

    private val mutableSnapshot = MutableStateFlow(
        buildSnapshot(headline = "Check-in ready")
    )

    override val id: ModeId = ModeId.CHECK_IN
    override val snapshot: StateFlow<ModeSnapshot> = mutableSnapshot.asStateFlow()

    override fun deviceGraph(): DeviceGraphSpec = stillCaptureDeviceGraph(runtimeState())

    override suspend fun onDeviceCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) {
        styleIndex = styleIndex.coerceAtMost(currentStyles().lastIndex)
        mutableSnapshot.value = buildSnapshot(
            headline = if (depthEffectEnabled()) {
                "Check-in ${currentScenario().label} active"
            } else {
                "Check-in ${currentScenario().label} focus active"
            }
        )
    }

    override suspend fun onLensFacingChanged(lensFacing: LensFacing) = Unit

    override suspend fun onStillCaptureResolutionChanged(
        stillCaptureResolutionPreset: StillCaptureResolutionPreset
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = "Check-in resolution updated"
        )
    }

    override suspend fun onStillCaptureQualityChanged(
        stillCaptureQuality: StillCaptureQualityPreference
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = "Check-in quality updated"
        )
    }

    override suspend fun onEnter() {
        context.eventSink("checkin.enter")
        scenarioIndex = resolvedDefaultScenarioIndex()
            .coerceAtMost(CheckInScenario.entries.size - 1)
        styleIndex = resolvedDefaultStyleIndex().coerceAtMost(currentStyles().lastIndex)
        mutableSnapshot.value = buildSnapshot(
            headline = if (depthEffectEnabled()) {
                "Check-in ${currentScenario().label} active"
            } else {
                "Check-in ${currentScenario().label} focus active"
            }
        )
        context.onEffectSpecChanged(buildEffectSpec())
    }

    override suspend fun onExit() {
        context.eventSink("checkin.exit")
        mutableSnapshot.value = buildSnapshot(
            headline = "Check-in inactive"
        )
    }

    override suspend fun handle(intent: ModeIntent): ModeSignal {
        return when (intent) {
            ModeIntent.ShutterPressed -> submitCapture()
            ModeIntent.SecondaryActionPressed -> cycleStyle()
            ModeIntent.TertiaryActionPressed -> cycleFrameRatio()
            is ModeIntent.FrameRatioSelected -> selectFrameRatio(intent.ratio)
            else -> ModeSignal.None
        }
    }

    override suspend fun onSessionEvent(event: ModeSessionEvent) {
        when (event) {
            is ModeSessionEvent.ShotStarted -> {
                if (event.shot.mediaType == MediaType.PHOTO) {
                    mutableSnapshot.value = buildSnapshot(
                        headline = "Check-in capture in progress"
                    )
                }
            }
            is ModeSessionEvent.ShotCompleted -> {
                if (event.result.mediaType == MediaType.PHOTO) {
                    mutableSnapshot.value = buildSnapshot(
                        headline = "Check-in saved"
                    )
                }
            }
            is ModeSessionEvent.ShotFailed -> {
                if (event.mediaType == MediaType.PHOTO) {
                    mutableSnapshot.value = buildSnapshot(
                        headline = "Check-in capture failed"
                    )
                }
            }
        }
    }

    private suspend fun submitCapture(): ModeSignal {
        val scenario = currentScenario()
        context.eventSink("checkin.capture.requested.${scenario.id}")

        if (scenario.captureStrategyType == CheckInScenario.StrategyType.MULTI_FRAME) {
            return submitClarityCapture(scenario)
        }
        return submitSingleFrameCapture(scenario)
    }

    private suspend fun submitClarityCapture(scenario: CheckInScenario): ModeSignal {
        context.eventSink("checkin.clarity.shutter.pressed")
        val metadataTags = buildMap {
            put("mode", "check-in")
            put("checkInScenario", scenario.id)
            put("compatMode", "fullclear")
            put("focus-bracket-capture", "V1")
            put("focus-stack-fusion", "V1")
            put("degradation-policy", "multi-frame-best-frame")
            putAll(context.captureAidMetadataTags())
        }
        return ModeSignal.SubmitCapture(
            CaptureStrategy.MultiFrame(
                saveRequest = SaveRequest.photoLibrary(
                    relativePath = "Pictures/OpenCamera/Check-in",
                    fileNamePrefix = "OpenCamera_CHECKIN",
                    metadata = MediaMetadata(customTags = metadataTags)
                ),
                postProcessSpec = PostProcessSpec(
                    algorithmProfile = "checkin-clarity-best-frame-v1",
                    exifOverrides = mapOf(
                        "SceneCaptureType" to "Check-in Clarity",
                        "CheckInScenario" to scenario.label,
                        "CompatSceneCaptureType" to "Full Clear"
                    )
                ),
                captureProfile = CaptureProfile(
                    frameCount = 3,
                    stillCaptureQuality = runtimeState().stillCaptureQuality,
                    stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                ),
                thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA
            )
        )
    }

    private suspend fun submitSingleFrameCapture(scenario: CheckInScenario): ModeSignal {
        val style = currentStyle()
        val portraitSettings = portraitSettings()
        context.eventSink("checkin.capture.requested.${scenario.id}.${style.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (countdownDuration() == CountdownDuration.OFF) {
                "Check-in capture requested"
            } else {
                "Check-in countdown armed"
            }
        )
        val effectSpec = buildEffectSpec()
        val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
        val basePostProcess = EffectBridge.toPostProcessSpec(effectSpec)
        val postProcessSpec = basePostProcess.copy(
            watermarkText = resolvedWatermarkText(scenario, style),
            exifOverrides = basePostProcess.exifOverrides + checkInExifOverrides(scenario, style, portraitSettings),
            algorithmProfile = resolvedAlgorithmProfile(style.id)
        )
        return ModeSignal.SubmitCapture(
            CaptureStrategy.SingleFrame(
                saveRequest = SaveRequest.photoLibrary(
                    relativePath = "Pictures/OpenCamera/Check-in",
                    fileNamePrefix = "OpenCamera_CHECKIN",
                    metadata = MediaMetadata(
                        customTags = buildMap {
                            putAll(bridgeTags)
                            put("mode", "check-in")
                            put("checkInScenario", scenario.id)
                            put("compatMode", "portrait")
                            put("style", style.id)
                            put("subjectTracking", style.subjectTracking.toString())
                            put("watermarkModeName", "Check-in")
                            put("watermarkProfileName", style.label)
                            put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                            put("modeVariant", "standard")
                            putAll(context.captureAidMetadataTags())
                            style.bokehStrength?.let { put("bokehStrength", it.toString()) }
                        }
                    )
                ),
                postProcessSpec = postProcessSpec,
                captureProfile = CaptureProfile(
                    stillCaptureQuality = runtimeState().stillCaptureQuality,
                    stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                )
            ),
            countdownSeconds = countdownDuration().seconds
        )
    }

    private fun buildEffectSpec(): EffectSpec {
        if (currentScenario().captureStrategyType == CheckInScenario.StrategyType.MULTI_FRAME) {
            return EffectSpec(listOf(FrameEffect(currentFrameRatio())))
        }
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

    private fun checkInExifOverrides(
        scenario: CheckInScenario,
        style: CheckInStyle,
        portraitSettings: PhotoSettings
    ): Map<String, String> = buildMap {
        put("SceneCaptureType", "Check-in")
        put("CheckInScenario", scenario.label)
        put("CompatSceneCaptureType", "Portrait")
        put("CheckInStyle", style.label)
        put("PortraitProfile", portraitSettings.portraitProfile.label)
        put("PortraitBeautyPreset", portraitSettings.portraitBeautyPreset.label)
        put("PortraitBeautyStrength", portraitSettings.portraitBeautyStrength.label)
        put("PortraitBokehEffect", portraitSettings.portraitBokehEffect.label)
        put("DepthEffect", if (depthEffectEnabled()) "simulated-bokeh" else "focus-priority")
        style.bokehStrength?.let { put("DepthStrength", "${it}f") }
        put("PortraitDepthStrength", portraitSettings.portraitDepthStrength.toString())
    }

    private fun resolvedWatermarkText(scenario: CheckInScenario, style: CheckInStyle): String {
        return if (depthEffectEnabled()) {
            "Check-in ${scenario.label} ${style.label}"
        } else {
            "Check-in ${scenario.label} Focus ${style.label}"
        }
    }

    private suspend fun cycleStyle(): ModeSignal {
        styleIndex = (styleIndex + 1) % currentStyles().size
        val style = currentStyle()
        val scenario = currentScenario()
        context.eventSink("checkin.style.selected.${scenario.id}.${style.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = "Check-in style updated"
        )
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("Check-in ${scenario.label} style: ${style.label}")
    }

    private suspend fun cycleFrameRatio(): ModeSignal =
        frameRatioDelegate.cycleFrameRatio(
            snapshotHeadline = "Check-in frame ratio updated",
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

    private fun buildSnapshot(
        headline: String,
        detail: String = styleSummary(currentStyle())
    ): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.CHECK_IN,
            uiSpec = ModeUiSpec(
                title = "打卡",
                shutterLabel = "Check-in Capture",
                secondaryActionLabel = "Toggle Check-in Style",
                tertiaryActionLabel = "Cycle Frame"
            ),
            state = ModeState(
                headline = headline,
                detail = detail,
                isShutterEnabled = true,
                isSecondaryActionEnabled = true,
                isTertiaryActionEnabled = true,
                isProActionEnabled = false,
                isProVariantActive = false
            )
        )
    }

    private fun currentScenario(): CheckInScenario =
        CheckInScenario.entries[scenarioIndex]

    private fun resolvedDefaultScenarioIndex(): Int = 0

    private fun resolvedDefaultStyleIndex(): Int {
        val defaultId = context.settingsSnapshot.persisted.photo.defaultPortraitFilterProfileId
        return checkInFilters.indexOfFirst { style -> style.id == defaultId }
            .takeIf { index -> index >= 0 }
            ?: 0
    }

    private fun currentStyles(): List<CheckInStyle> = checkInFilters

    private fun resolveCheckInFilters(): List<CheckInStyle> {
        val catalogProfiles = context.settingsSnapshot.catalog
            .filterProfilesFor(FilterProfileCategory.PORTRAIT)
            .map(::checkInStyle)
        return if (catalogProfiles.isNotEmpty()) {
            catalogProfiles
        } else {
            DEFAULT_CHECK_IN_STYLES
        }
    }

    private fun checkInStyle(profile: FilterProfile): CheckInStyle {
        val characteristics = checkInCharacteristics(profile.id)
        return CheckInStyle(
            id = profile.id,
            label = profile.label,
            bokehStrength = characteristics.bokehStrength,
            subjectTracking = characteristics.subjectTracking,
            renderSpec = profile.renderSpec ?: defaultFilterRenderSpecOrNull(profile.id)
        )
    }

    private fun checkInCharacteristics(styleId: String): CheckInCharacteristics {
        return when (styleId) {
            "portrait-blue" -> CheckInCharacteristics(bokehStrength = 2.1f, subjectTracking = true)
            "portrait-retro" -> CheckInCharacteristics(bokehStrength = 2.4f, subjectTracking = true)
            "portrait-ccd" -> CheckInCharacteristics(bokehStrength = 1.7f, subjectTracking = false)
            "portrait-vivid" -> CheckInCharacteristics(bokehStrength = 2.0f, subjectTracking = true)
            "portrait-original" -> CheckInCharacteristics(bokehStrength = 1.8f, subjectTracking = true)
            "portrait-chasing-light" -> CheckInCharacteristics(
                bokehStrength = 1.6f,
                subjectTracking = true
            )
            "portrait-rich" -> CheckInCharacteristics(bokehStrength = 2.2f, subjectTracking = false)
            else -> CheckInCharacteristics(bokehStrength = 1.8f, subjectTracking = true)
        }
    }

    private fun currentStyle(): CheckInStyle = currentStyles()[styleIndex]

    private fun styleSummary(style: CheckInStyle): String {
        val scenario = currentScenario()
        val portraitSettings = portraitSettings()
        val commonSummary = buildString {
            append("场景 ${scenario.label}")
            append(" | Profile ${portraitSettings.portraitProfile.label}")
            append(" | Beauty ${portraitSettings.portraitBeautyPreset.label}")
            append(" ${portraitSettings.portraitBeautyStrength.label}")
            append(" | Bokeh ${portraitSettings.portraitBokehEffect.label}")
        }
        return if (depthEffectEnabled()) {
            "Style ${style.label} | $commonSummary | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Timer ${countdownDuration().label} | Frame ${currentFrameRatio().label} | Portrait depth rendering active."
        } else {
            "Style ${style.label} | $commonSummary | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Timer ${countdownDuration().label} | Frame ${currentFrameRatio().label} | Focus-priority Check-in degraded (depth effect is unavailable)."
        }
    }

    private fun depthEffectEnabled(): Boolean =
        runtimeState().deviceCapabilities.supportsPortraitDepthEffect

    private fun runtimeState() = context.runtimeState()

    private fun countdownDuration(): CountdownDuration =
        context.settingsSnapshot.persisted.photo.countdownDuration

    private fun portraitSettings(): PhotoSettings = context.settingsSnapshot.persisted.photo

    private fun resolvedAlgorithmProfile(base: String): String = "checkin-${base}"

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

    private data class CheckInStyle(
        val id: String,
        val label: String,
        val bokehStrength: Float? = null,
        val subjectTracking: Boolean,
        val renderSpec: FilterRenderSpec?
    )

    private data class CheckInCharacteristics(
        val bokehStrength: Float?,
        val subjectTracking: Boolean
    )

    companion object {
        private val DEFAULT_CHECK_IN_STYLES = listOf(
            CheckInStyle(
                id = "portrait-original",
                label = "人像-原色",
                bokehStrength = 1.8f,
                subjectTracking = true,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-original")
            ),
            CheckInStyle(
                id = "portrait-vivid",
                label = "人像-鲜艳",
                bokehStrength = 2.0f,
                subjectTracking = true,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-vivid")
            ),
            CheckInStyle(
                id = "portrait-blue",
                label = "人像-蓝调",
                bokehStrength = 2.1f,
                subjectTracking = true,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-blue")
            ),
            CheckInStyle(
                id = "portrait-retro",
                label = "人像-复古",
                bokehStrength = 2.4f,
                subjectTracking = true,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-retro")
            ),
            CheckInStyle(
                id = "portrait-ccd",
                label = "人像-CCD",
                bokehStrength = 1.7f,
                subjectTracking = false,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-ccd")
            ),
            CheckInStyle(
                id = "portrait-chasing-light",
                label = "人像-追光",
                bokehStrength = 1.6f,
                subjectTracking = true,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-chasing-light")
            ),
            CheckInStyle(
                id = "portrait-rich",
                label = "人像-浓郁",
                bokehStrength = 2.2f,
                subjectTracking = false,
                renderSpec = defaultFilterRenderSpecOrNull("portrait-rich")
            )
        )
    }
}
