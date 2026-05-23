package com.opencamera.feature.night

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
import com.opencamera.core.mode.label
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.compactSummary
import com.opencamera.core.settings.toMetadataTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NightModePlugin : CameraModePlugin {
    override val id: ModeId = ModeId.NIGHT

    override fun isSupported(deviceCapabilities: DeviceCapabilities): Boolean {
        return deviceCapabilities.supportsStillCapture
    }

    override fun create(context: ModeContext): ModeController {
        return NightModeController(context)
    }
}

private class NightModeController(
    private val context: ModeContext
) : ModeController {
    private var profileIndex = 0
    private val frameRatioDelegate = FrameRatioDelegate(
        context = context,
        modeEventPrefix = "night",
        effectSpecProvider = { buildEffectSpec() }
    )
    private val proVariantState = ProVariantState(context)
    private val proVariantEnabled: Boolean
        get() = proVariantState.isEnabled

    private val mutableSnapshot = MutableStateFlow(
        buildSnapshot(
            headline = "Scenery pipeline ready"
        )
    )

    override val id: ModeId = ModeId.NIGHT
    override val snapshot: StateFlow<ModeSnapshot> = mutableSnapshot.asStateFlow()

    override fun deviceGraph(): DeviceGraphSpec = currentDeviceGraph()

    override suspend fun onDeviceCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) {
        profileIndex = profileIndex.coerceAtMost(currentProfiles().lastIndex)
        mutableSnapshot.value = buildSnapshot(
            headline = if (multiFrameEnabled()) {
                "Scenery mode active"
            } else {
                "Scenery brightening active"
            }
        )
    }

    override suspend fun onLensFacingChanged(lensFacing: LensFacing) = Unit

    override suspend fun onStillCaptureQualityChanged(
        stillCaptureQuality: StillCaptureQualityPreference
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (multiFrameEnabled()) {
                "Scenery quality updated"
            } else {
                "Scenery assist quality updated"
            }
        )
    }

    override suspend fun onStillCaptureResolutionChanged(
        stillCaptureResolutionPreset: StillCaptureResolutionPreset
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (multiFrameEnabled()) {
                "Scenery resolution updated"
            } else {
                "Scenery assist resolution updated"
            }
        )
    }

    override suspend fun onEnter() {
        context.eventSink("night.enter")
        mutableSnapshot.value = buildSnapshot(
            headline = if (multiFrameEnabled()) {
                "Scenery mode active"
            } else {
                "Scenery brightening active"
            }
        )
        context.onEffectSpecChanged(buildEffectSpec())
    }

    override suspend fun onExit() {
        context.eventSink("night.exit")
        mutableSnapshot.value = buildSnapshot(
            headline = "Scenery mode inactive"
        )
    }

    override suspend fun handle(intent: ModeIntent): ModeSignal {
        return when (intent) {
            ModeIntent.ShutterPressed -> submitCurrentProfile()
            ModeIntent.SecondaryActionPressed -> cycleProfile()
            ModeIntent.TertiaryActionPressed -> cycleFrameRatio()
            is ModeIntent.FrameRatioSelected -> selectFrameRatio(intent.ratio)
            ModeIntent.ProActionPressed -> toggleProVariant()
        }
    }

    override suspend fun onSessionEvent(event: ModeSessionEvent) {
        reduceStillShotSessionEvent(
            event = event,
            text = StillShotSessionEventText(
                shotStartedHeadline = "Scenery capture in progress",
                shotCompletedHeadline = "Scenery photo saved",
                shotFailedHeadline = "Scenery capture failed"
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

    private suspend fun submitCurrentProfile(): ModeSignal {
        val profile = currentProfile()
        val flashMode = resolvedFlashMode(profile)
        context.eventSink("night.capture.requested.${profile.id}.flash-${flashMode.name.lowercase()}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (countdownDuration() == CountdownDuration.OFF) {
                "Scenery capture requested"
            } else {
                "Scenery countdown armed"
            }
        )
        val effectSpec = buildEffectSpec()
        val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
        val basePostProcess = EffectBridge.toPostProcessSpec(effectSpec)
        val postProcessSpec = basePostProcess.copy(
            watermarkText = if (proVariantEnabled) {
                if (manualControlsEnabled()) {
                    "Scenery Pro ${profile.label}"
                } else {
                    "Scenery Pro Assist ${profile.label}"
                }
            } else if (multiFrameEnabled()) {
                "Scenery ${profile.label}"
            } else {
                "Scenery Assist ${profile.label}"
            },
            exifOverrides = basePostProcess.exifOverrides + buildMap {
                put("SceneCaptureType", "Night")
                put("NightProfile", profile.label)
                if (proVariantEnabled) {
                    put("NightVariant", proVariantState.variantExifLabel())
                    put("ManualDraft", currentManualDraft().compactSummary())
                }
                profile.longExposureMillis?.let { put("ExposureTime", "${it}ms") }
                put("MergeStrategy", if (multiFrameEnabled()) "multi-frame" else "bright-single-frame")
            },
            algorithmProfile = resolvedAlgorithmProfile(profile.algorithmProfile)
        )
        val saveRequest = SaveRequest.photoLibrary(
            relativePath = "Pictures/OpenCamera/Night",
            fileNamePrefix = "OpenCamera_NIGHT",
            metadata = MediaMetadata(
                customTags = buildMap {
                    put("mode", "night")
                    put("modeDisplay", "scenery")
                    put("profile", profile.id)
                    put("capturePath", if (multiFrameEnabled()) "multi-frame" else "single-frame-fallback")
                    put("stabilization", if (profile.requiresTripod) "tripod" else "handheld")
                    put("mergeFrameCount", profile.frameCount.toString())
                    put("flash", flashMode.name.lowercase())
                    put("stillQuality", runtimeState().stillCaptureQuality.tagValue)
                    put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                    put("modeVariant", proVariantState.modeVariantTag())
                    put("watermarkModeName", "Scenery")
                    put("watermarkProfileName", profile.label)
                    if (proVariantEnabled) {
                        putAll(proVariantState.metadataTags())
                    }
                    putAll(context.captureAidMetadataTags())
                    putAll(bridgeTags)
                }
            )
        )
        val strategy = if (multiFrameEnabled()) {
            CaptureStrategy.MultiFrame(
                saveRequest = saveRequest,
                postProcessSpec = postProcessSpec,
                captureProfile = CaptureProfile(
                    frameCount = profile.frameCount,
                    longExposureMillis = profile.longExposureMillis,
                    requiresTripod = profile.requiresTripod,
                    flashMode = flashMode,
                    manualCaptureParams = currentManualDraftOrNull(),
                    stillCaptureQuality = runtimeState().stillCaptureQuality,
                    stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                )
            )
        } else {
            CaptureStrategy.SingleFrame(
                saveRequest = saveRequest,
                postProcessSpec = postProcessSpec,
                captureProfile = CaptureProfile(
                    flashMode = flashMode,
                    manualCaptureParams = currentManualDraftOrNull(),
                    stillCaptureQuality = runtimeState().stillCaptureQuality,
                    stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                )
            )
        }
        return ModeSignal.SubmitCapture(
            strategy = strategy,
            countdownSeconds = countdownDuration().seconds
        )
    }

    private fun buildEffectSpec(): EffectSpec {
        return EffectSpec(listOf(
            FrameEffect(currentFrameRatio())
        ))
    }

    private suspend fun cycleProfile(): ModeSignal {
        profileIndex = (profileIndex + 1) % currentProfiles().size
        val profile = currentProfile()
        context.eventSink("night.profile.selected.${profile.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (multiFrameEnabled()) {
                "Scenery profile updated"
            } else {
                "Scenery assist profile updated"
            }
        )
        return ModeSignal.ShowHint("Scenery style: ${profile.label}")
    }

    private suspend fun toggleProVariant(): ModeSignal {
        val result = proVariantState.toggle("Scenery")
        context.eventSink("night.pro-variant.${result.eventSuffix}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (proVariantEnabled) {
                if (manualControlsEnabled()) {
                    "Scenery Pro active"
                } else {
                    "Scenery Pro assist active"
                }
            } else if (multiFrameEnabled()) {
                "Scenery mode active"
            } else {
                "Scenery brightening active"
            }
        )
        return result.signal
    }

    private fun buildSnapshot(
        headline: String,
        detail: String = profileSummary(currentProfile())
    ): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.NIGHT,
            uiSpec = ModeUiSpec(
                title = "Scenery",
                shutterLabel = "Capture Scenery",
                secondaryActionLabel = "Cycle Scenery Style",
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

    private fun multiFrameEnabled(): Boolean = runtimeState().deviceCapabilities.supportsNightMultiFrame
    private fun flashSupported(): Boolean = runtimeState().deviceCapabilities.supportsFlashControl

    private fun currentProfiles(): List<NightProfile> {
        return if (multiFrameEnabled()) {
            MULTI_FRAME_PROFILES
        } else {
            FALLBACK_PROFILES
        }
    }

    private fun currentProfile(): NightProfile = currentProfiles()[profileIndex]

    private fun profileSummary(profile: NightProfile): String {
        val flashSummary = when {
            !flashSupported() -> "Flash unavailable on this device."
            multiFrameEnabled() -> "Flash ${resolvedFlashMode(profile).label} to preserve multi-frame merge."
            else -> "Flash ${resolvedFlashMode(profile).label}."
        }
        val standardSummary = if (multiFrameEnabled()) {
            "Default style ${profile.label} | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | ${profile.frameCount} frames | Exposure ${profile.longExposureMillis} ms | Tripod ${profile.requiresTripod} | Timer ${countdownDuration().label} | Frame ${currentFrameRatio().label} | Subfeatures scenery style, timer, frame ratio, and night fusion ride the current mode profile. $flashSummary"
        } else {
            "Default style ${profile.label} | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Single-frame brightening fallback because night multi-frame is unavailable on this device. | Timer ${countdownDuration().label} | Frame ${currentFrameRatio().label} | Subfeatures scenery style, timer, and frame ratio stay available while fusion degrades. $flashSummary"
        }
        if (!proVariantEnabled) {
            return standardSummary
        }
        val proSummary = proVariantState.summaryText("scenery capture")
        return "$standardSummary | $proSummary"
    }

    private fun countdownDuration(): CountdownDuration =
        context.settingsSnapshot.persisted.photo.countdownDuration

    private fun resolvedFlashMode(profile: NightProfile): FlashMode {
        return if (flashSupported()) {
            profile.flashMode
        } else {
            FlashMode.OFF
        }
    }

    private suspend fun cycleFrameRatio(): ModeSignal =
        frameRatioDelegate.cycleFrameRatio(
            snapshotHeadline = if (multiFrameEnabled()) {
                "Scenery frame updated"
            } else {
                "Scenery assist frame updated"
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

    private data class NightProfile(
        val id: String,
        val label: String,
        val frameCount: Int,
        val longExposureMillis: Long?,
        val requiresTripod: Boolean,
        val flashMode: FlashMode = FlashMode.OFF,
        val algorithmProfile: String
    )

    companion object {
        private val MULTI_FRAME_PROFILES = listOf(
            NightProfile(
                id = "handheld",
                label = "Handheld",
                frameCount = 6,
                longExposureMillis = 450L,
                requiresTripod = false,
                flashMode = FlashMode.OFF,
                algorithmProfile = "night-multiframe-handheld"
            ),
            NightProfile(
                id = "street",
                label = "Street",
                frameCount = 8,
                longExposureMillis = 700L,
                requiresTripod = false,
                flashMode = FlashMode.OFF,
                algorithmProfile = "night-multiframe-street"
            ),
            NightProfile(
                id = "tripod",
                label = "Tripod",
                frameCount = 12,
                longExposureMillis = 1400L,
                requiresTripod = true,
                flashMode = FlashMode.OFF,
                algorithmProfile = "night-multiframe-tripod"
            )
        )

        private val FALLBACK_PROFILES = listOf(
            NightProfile(
                id = "balanced",
                label = "Balanced",
                frameCount = 1,
                longExposureMillis = null,
                requiresTripod = false,
                flashMode = FlashMode.AUTO,
                algorithmProfile = "night-fallback-balanced"
            ),
            NightProfile(
                id = "warm",
                label = "Warm",
                frameCount = 1,
                longExposureMillis = null,
                requiresTripod = false,
                flashMode = FlashMode.ON,
                algorithmProfile = "night-fallback-warm"
            )
        )
    }

}
