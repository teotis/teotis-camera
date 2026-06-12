package com.opencamera.feature.checkin

import com.opencamera.core.effect.EffectBridge
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.effect.PortraitEffect
import com.opencamera.core.effect.WatermarkEffect
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ThumbnailPolicy
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
import com.opencamera.core.mode.StillShotSessionEventText
import com.opencamera.core.mode.captureAidMetadataTags
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.defaultFilterRenderSpecOrNull
import com.opencamera.core.settings.filterProfilesFor
import com.opencamera.core.settings.renderStyleColorSpecWithRecipe
import com.opencamera.core.settings.watermarkStyleFor
import com.opencamera.core.device.DeviceCapabilities

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
    context: ModeContext
) : AbstractStillCaptureMode(context) {

    private var scenarioIndex = resolvedDefaultScenarioIndex()
    private val checkInFilters = resolveCheckInFilters()
    private var styleIndex = resolvedDefaultStyleIndex()

    override val id: ModeId = ModeId.CHECK_IN

    // ── Subclass contract ──────────────────────────────────────────────

    override fun modeEventPrefix(): String = "checkin"

    override fun initialHeadline(): String = "Check-in ready"

    override fun buildEffectSpec(): EffectSpec {
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
                tokens = watermarkTokens(),
                style = watermarkStyle
            ),
            FrameEffect(currentFrameRatio())
        ))
    }

    override fun buildCaptureStrategy(
        effectSpec: EffectSpec,
        countdownSeconds: Int
    ): ModeSignal.SubmitCapture {
        val scenario = currentScenario()
        if (scenario.captureStrategyType == CheckInScenario.StrategyType.MULTI_FRAME) {
            return buildClarityStrategy(scenario)
        }
        return buildSingleFrameStrategy(scenario)
    }

    override fun buildSnapshot(headline: String, detail: String?): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.CHECK_IN,
            uiSpec = ModeUiSpec(
                title = "打卡",
                shutterLabel = "Check-in Capture",
                secondaryActionLabel = "Cycle Check-in Style",
                tertiaryActionLabel = "Cycle Frame",
                proActionLabel = if (currentScenario().captureStrategyType == CheckInScenario.StrategyType.MULTI_FRAME) "超清" else "氛围"
            ),
            state = ModeState(
                headline = headline,
                detail = detail ?: buildDefaultDetail(),
                isShutterEnabled = true,
                isSecondaryActionEnabled = true,
                isTertiaryActionEnabled = true,
                isProActionEnabled = true,
                isProVariantActive = currentScenario().captureStrategyType == CheckInScenario.StrategyType.MULTI_FRAME
            )
        )
    }

    override fun buildDefaultDetail(): String = styleSummary(currentStyle())

    // ── Headlines ─────────────────────────────────────────────────────

    override fun enterHeadline(): String = resolvedEnterHeadline()

    override fun exitHeadline(): String = "Check-in inactive"

    override fun resolutionChangedHeadline(): String = "Check-in resolution updated"

    override fun qualityChangedHeadline(): String = "Check-in quality updated"

    override fun sessionEventText(): StillShotSessionEventText {
        val scenarioLabel = currentScenario().label
        return StillShotSessionEventText(
            shotStartedHeadline = "Check-in $scenarioLabel capture in progress",
            shotCompletedHeadline = "Check-in $scenarioLabel saved",
            shotFailedHeadline = "Check-in $scenarioLabel capture failed"
        )
    }

    // ── Capability coercion ───────────────────────────────────────────

    override suspend fun onModeCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) {
        styleIndex = styleIndex.coerceAtMost(currentStyles().lastIndex)
    }

    // ── Intent overrides ──────────────────────────────────────────────

    override suspend fun handle(intent: ModeIntent): ModeSignal {
        return when (intent) {
            is ModeIntent.ScenarioSelected -> selectScenario(intent.scenarioId)
            else -> super.handle(intent)
        }
    }

    override suspend fun handleShutterPressed(): ModeSignal {
        val scenario = currentScenario()
        context.eventSink("checkin.capture.requested.${scenario.id}")
        return super.handleShutterPressed()
    }

    override suspend fun handleSecondaryAction(): ModeSignal {
        styleIndex = (styleIndex + 1) % currentStyles().size
        val style = currentStyle()
        val scenario = currentScenario()
        context.eventSink("checkin.style.selected.${scenario.id}.${style.id}")
        updateSnapshot(headline = "Check-in style updated")
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("Check-in ${scenario.label} style: ${style.label}")
    }

    override suspend fun handleProAction(): ModeSignal {
        val wasClarity = currentScenario().captureStrategyType == CheckInScenario.StrategyType.MULTI_FRAME
        scenarioIndex = if (wasClarity) 0 else CheckInScenario.entries.indexOfFirst {
            it.captureStrategyType == CheckInScenario.StrategyType.MULTI_FRAME
        }
        val scenario = currentScenario()
        context.settingsActionSink(
            PersistedSettingsAction.UpdateCheckInScenario(scenario.id)
        )
        context.eventSink("checkin.scenario.toggled.${scenario.id}")
        updateSnapshot(headline = resolvedEnterHeadline())
        context.onEffectSpecChanged(buildEffectSpec())
        val label = if (scenario.captureStrategyType == CheckInScenario.StrategyType.MULTI_FRAME) "超清" else "氛围"
        return ModeSignal.ShowHint("打卡: $label")
    }

    // ── Scenario-specific helpers ─────────────────────────────────────

    private suspend fun selectScenario(scenarioId: String): ModeSignal {
        val index = CheckInScenario.entries.indexOfFirst { it.id == scenarioId }
            .takeIf { it >= 0 }
            ?: scenarioIndex
        scenarioIndex = index
        val scenario = currentScenario()
        context.settingsActionSink(
            PersistedSettingsAction.UpdateCheckInScenario(scenario.id)
        )
        context.eventSink("checkin.scenario.selected.${scenario.id}")
        updateSnapshot(headline = resolvedEnterHeadline())
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("打卡: ${scenario.label}")
    }

    // ── Capture strategies ────────────────────────────────────────────

    private fun buildClarityStrategy(scenario: CheckInScenario): ModeSignal.SubmitCapture {
        val metadataTags = buildMap {
            put("mode", "check-in")
            put("checkInScenario", scenario.id)
            put("compatMode", "clarity-assist")
            put("focus-bracket-capture", "V1")
            put("best-frame-clarity-assist", "V1")
            put("degradation-policy", "multi-frame-best-frame")
            putAll(context.captureAidMetadataTags())
        }
        return if (supportsMultiFrameCapture()) {
            ModeSignal.SubmitCapture(
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
                            "CompatSceneCaptureType" to "Clarity Assist"
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
        } else {
            val fallbackTags = metadataTags + mapOf(
                "captureStrategyFallback" to "single-frame",
                "degradationReason" to "multi-frame-unsupported",
                "degradation-policy" to "single-frame-best-frame"
            )
            ModeSignal.SubmitCapture(
                CaptureStrategy.SingleFrame(
                    saveRequest = SaveRequest.photoLibrary(
                        relativePath = "Pictures/OpenCamera/Check-in",
                        fileNamePrefix = "OpenCamera_CHECKIN",
                        metadata = MediaMetadata(customTags = fallbackTags)
                    ),
                    postProcessSpec = PostProcessSpec(
                        algorithmProfile = "checkin-clarity-best-frame-v1",
                        exifOverrides = mapOf(
                            "SceneCaptureType" to "Check-in Clarity",
                            "CheckInScenario" to scenario.label,
                            "CompatSceneCaptureType" to "Clarity Assist"
                        )
                    ),
                    captureProfile = CaptureProfile(
                        stillCaptureQuality = runtimeState().stillCaptureQuality,
                        stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                    )
                )
            )
        }
    }

    private fun buildSingleFrameStrategy(scenario: CheckInScenario): ModeSignal.SubmitCapture {
        val style = currentStyle()
        val portraitSettings = portraitSettings()
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
            )
        )
    }

    // ── CheckIn-specific helpers ──────────────────────────────────────

    private fun checkInExifOverrides(
        scenario: CheckInScenario,
        style: CheckInStyle,
        portraitSettings: com.opencamera.core.settings.PhotoSettings
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

    private fun currentScenario(): CheckInScenario =
        CheckInScenario.entries[scenarioIndex]

    private fun resolvedDefaultScenarioIndex(): Int {
        val persistedId = context.settingsSnapshot.persisted.photo.defaultCheckInScenario
        return CheckInScenario.entries.indexOfFirst { it.id == persistedId }
            .takeIf { it >= 0 }
            ?: 0
    }

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
            if (scenario.captureStrategyType == CheckInScenario.StrategyType.MULTI_FRAME && !supportsMultiFrameCapture()) {
                append(" [降级: 设备不支持多帧, 已切换为单帧]")
            }
            append(" | Profile ${portraitSettings.portraitProfile.label}")
            append(" | Beauty ${portraitSettings.portraitBeautyPreset.label}")
            append(" ${portraitSettings.portraitBeautyStrength.label}")
            append(" | Bokeh ${portraitSettings.portraitBokehEffect.label}")
        }
        val clarityHint = if (scenario.captureStrategyType == CheckInScenario.StrategyType.MULTI_FRAME) {
            " | 超清增强: 多帧最佳帧选择，点击远近可对焦区域可辅助清晰度"
        } else ""
        return if (depthEffectEnabled()) {
            "Style ${style.label} | $commonSummary | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Timer ${countdownDuration().label} | Frame ${currentFrameRatio().label} | Portrait depth rendering active.$clarityHint"
        } else {
            "Style ${style.label} | $commonSummary | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Timer ${countdownDuration().label} | Frame ${currentFrameRatio().label} | Focus-priority Check-in degraded (depth effect is unavailable).$clarityHint"
        }
    }

    private fun depthEffectEnabled(): Boolean =
        runtimeState().deviceCapabilities.supportsPortraitDepthEffect

    private fun supportsMultiFrameCapture(): Boolean =
        runtimeState().deviceCapabilities.supportsNightMultiFrame

    private fun resolvedEnterHeadline(): String {
        val scenario = currentScenario()
        return when {
            scenario.captureStrategyType == CheckInScenario.StrategyType.MULTI_FRAME &&
                !supportsMultiFrameCapture() ->
                "Check-in ${scenario.label} active (single-frame fallback)"
            depthEffectEnabled() -> "Check-in ${scenario.label} active"
            else -> "Check-in ${scenario.label} focus active"
        }
    }

    private fun portraitSettings(): com.opencamera.core.settings.PhotoSettings =
        context.settingsSnapshot.persisted.photo

    private fun resolvedAlgorithmProfile(base: String): String = "checkin-${base}"

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
