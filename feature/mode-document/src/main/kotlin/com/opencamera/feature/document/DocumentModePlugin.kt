package com.opencamera.feature.document

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.effect.DocumentEffect
import com.opencamera.core.effect.EffectBridge
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.media.CaptureStrategy
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
import com.opencamera.core.mode.captureAidMetadataTags
import com.opencamera.core.mode.stillCaptureDeviceGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DocumentModePlugin : CameraModePlugin {
    override val id: ModeId = ModeId.DOCUMENT

    override fun isSupported(deviceCapabilities: DeviceCapabilities): Boolean {
        return deviceCapabilities.supportsStillCapture
    }

    override fun create(context: ModeContext): ModeController {
        return DocumentModeController(context)
    }
}

private class DocumentModeController(
    private val context: ModeContext
) : ModeController {
    private var profileIndex = 0

    private val uiSpec = ModeUiSpec(
        title = "Document",
        shutterLabel = "Scan Document",
        secondaryActionLabel = "Cycle Scan Style"
    )

    private val mutableSnapshot = MutableStateFlow(
        buildSnapshot(
            headline = "Document pipeline ready"
        )
    )

    override val id: ModeId = ModeId.DOCUMENT
    override val snapshot: StateFlow<ModeSnapshot> = mutableSnapshot.asStateFlow()

    override fun deviceGraph(): DeviceGraphSpec = currentDeviceGraph()

    override suspend fun onDeviceCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) {
        profileIndex = profileIndex.coerceAtMost(currentProfiles().lastIndex)
        mutableSnapshot.value = buildSnapshot(
            headline = if (enhancementEnabled()) {
                "Document scan active"
            } else {
                "Document archive active"
            }
        )
    }

    override suspend fun onLensFacingChanged(lensFacing: LensFacing) = Unit

                "Archive quality updated"
            }
        )
    }

    override suspend fun onStillCaptureResolutionChanged(
        stillCaptureResolutionPreset: StillCaptureResolutionPreset
    ) {
        mutableSnapshot.value = buildSnapshot(
            headline = if (enhancementEnabled()) {
                "Document resolution updated"
            } else {
                "Archive resolution updated"
            }
        )
    }

    override suspend fun onEnter() {
        context.eventSink("document.enter")
        mutableSnapshot.value = buildSnapshot(
            headline = if (enhancementEnabled()) {
                "Document scan active"
            } else {
                "Document archive active"
            }
        )
        context.onEffectSpecChanged(buildEffectSpec())
    }

    override suspend fun onExit() {
        context.eventSink("document.exit")
        mutableSnapshot.value = buildSnapshot(
            headline = "Document mode inactive"
        )
    }

    override suspend fun handle(intent: ModeIntent): ModeSignal {
        return when (intent) {
            ModeIntent.ShutterPressed -> submitCurrentProfile()
            ModeIntent.SecondaryActionPressed -> cycleProfile()
            ModeIntent.TertiaryActionPressed -> ModeSignal.None
            is ModeIntent.FrameRatioSelected -> ModeSignal.ShowHint("文档模式使用自动裁边，不使用普通画幅")
            ModeIntent.ProActionPressed -> ModeSignal.None
        }
    }

    override suspend fun onSessionEvent(event: ModeSessionEvent) {
        reduceStillShotSessionEvent(
            event = event,
            text = StillShotSessionEventText(
                shotStartedHeadline = "Document scan in progress",
                shotCompletedHeadline = "Document saved",
                shotFailedHeadline = "Document capture failed"
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
        context.eventSink("document.capture.requested.${profile.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = "Document capture requested"
        )
        val effectSpec = buildEffectSpec()
        val bridgeTags = EffectBridge.toMetadataTags(effectSpec)
        val basePostProcess = EffectBridge.toPostProcessSpec(effectSpec)
        val postProcessSpec = basePostProcess.copy(
            watermarkText = if (enhancementEnabled()) {
                "DOC ${profile.label}"
            } else {
                "DOC Basic ${profile.label}"
            },
            exifOverrides = basePostProcess.exifOverrides + buildMap {
                put("SceneCaptureType", "Document")
                put("ProcessingRendered", if (enhancementEnabled()) "enhanced-scan" else "basic-archive")
                if (enhancementEnabled()) {
                    put("Contrast", profile.contrastLabel)
                }
            },
            algorithmProfile = profile.algorithmProfile
        )
        return ModeSignal.SubmitCapture(
            CaptureStrategy.SingleFrame(
                saveRequest = SaveRequest.photoLibrary(
                    relativePath = "Pictures/OpenCamera/Documents",
                    fileNamePrefix = "OpenCamera_DOC",
                    metadata = MediaMetadata(
                        customTags = buildMap {
                            put("mode", "document")
                            put("profile", profile.id)
                            put("scanMode", if (enhancementEnabled()) "enhanced" else "basic")
                            put("outputClass", "scan")
                            put("stillQuality", runtimeState().stillCaptureQuality.tagValue)
                            put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                            putAll(context.captureAidMetadataTags())
                            putAll(bridgeTags)
                        }
                    )
                ),
                postProcessSpec = postProcessSpec,
                captureProfile = com.opencamera.core.media.CaptureProfile(
                    stillCaptureQuality = runtimeState().stillCaptureQuality,
                    stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset
                )
            )
        )
    }

    private fun buildEffectSpec(): EffectSpec {
        val profile = currentProfile()
        return EffectSpec(listOf(
            DocumentEffect(
                autoCrop = profile.autoCrop,
                contrastProfile = if (enhancementEnabled()) profile.contrastLabel else null
            )
        ))
    }

    private suspend fun cycleProfile(): ModeSignal {
        profileIndex = (profileIndex + 1) % currentProfiles().size
        val profile = currentProfile()
        context.eventSink("document.profile.selected.${profile.id}")
        mutableSnapshot.value = buildSnapshot(
            headline = if (enhancementEnabled()) {
                "Document style updated"
            } else {
                "Archive style updated"
            }
        )
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("Scan style: ${profile.label}")
    }

    private fun buildSnapshot(
        headline: String,
        detail: String = profileSummary(currentProfile())
    ): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.DOCUMENT,
            uiSpec = uiSpec,
            state = ModeState(
                headline = headline,
                detail = detail
            )
        )
    }

    private fun currentDeviceGraph(): DeviceGraphSpec =
        stillCaptureDeviceGraph(runtimeState())

    private fun enhancementEnabled(): Boolean =
        runtimeState().deviceCapabilities.supportsDocumentScanEnhancement
    private fun runtimeState() = context.runtimeState()

    private fun currentProfiles(): List<DocumentProfile> {
        return if (enhancementEnabled()) {
            ENHANCED_PROFILES
        } else {
            BASIC_PROFILES
        }
    }

    private fun currentProfile(): DocumentProfile = currentProfiles()[profileIndex]

    private fun profileSummary(profile: DocumentProfile): String {
        return if (enhancementEnabled()) {
            "Style ${profile.label} | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Auto crop ${profile.autoCrop} | Contrast ${profile.contrastLabel} | Output tuned for scanned documents."
        } else {
            "Style ${profile.label} | Still ${runtimeState().stillCaptureQuality.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Basic capture only because document enhancement is unavailable on this device."
        }
    }

    private data class DocumentProfile(
        val id: String,
        val label: String,
        val autoCrop: Boolean,
        val contrastLabel: String,
        val algorithmProfile: String
    )

    companion object {
        private val ENHANCED_PROFILES = listOf(
            DocumentProfile(
                id = "receipt",
                label = "Receipt",
                autoCrop = true,
                contrastLabel = "High",
                algorithmProfile = "document-receipt-scan"
            ),
            DocumentProfile(
                id = "whiteboard",
                label = "Whiteboard",
                autoCrop = true,
                contrastLabel = "Balanced",
                algorithmProfile = "document-whiteboard-scan"
            ),
            DocumentProfile(
                id = "contract",
                label = "Contract",
                autoCrop = false,
                contrastLabel = "Natural",
                algorithmProfile = "document-contract-scan"
            )
        )

        private val BASIC_PROFILES = listOf(
            DocumentProfile(
                id = "archive",
                label = "Archive",
                autoCrop = false,
                contrastLabel = "Natural",
                algorithmProfile = "document-basic-archive"
            ),
            DocumentProfile(
                id = "color",
                label = "Color Copy",
                autoCrop = false,
                contrastLabel = "Balanced",
                algorithmProfile = "document-basic-color"
            )
        )
    }

}
