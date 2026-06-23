package com.opencamera.feature.document

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.effect.DocumentColorMode
import com.opencamera.core.effect.DocumentEffect
import com.opencamera.core.effect.EffectBridge
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.WatermarkEffect
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.mode.AbstractStillCaptureMode
import com.opencamera.core.mode.CameraModePlugin
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.mode.StillShotSessionEventText
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.settings.StylePresetCatalog
import com.opencamera.core.settings.StylePresetFamily
import com.opencamera.core.settings.watermarkStyleFor

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
    context: ModeContext
) : AbstractStillCaptureMode(context) {

    private var profileIndex = 0

    private val uiSpec = ModeUiSpec(
        title = "Document",
        shutterLabel = "Scan Document",
        secondaryActionLabel = "切换扫描场景"
    )

    override val id: ModeId = ModeId.DOCUMENT

    // ── Subclass contract ──────────────────────────────────────────────

    override fun modeEventPrefix(): String = "document"

    override fun initialHeadline(): String = "Document pipeline ready"

    override fun buildEffectSpec(): EffectSpec {
        val profile = currentProfile()
        val selectedWatermarkTemplate = selectedWatermarkTemplate()
        val watermarkStyle = context.settingsSnapshot.persisted.photo
            .watermarkStyleFor(selectedWatermarkTemplate.id)
        val colorMode = resolveDocumentColorMode()
        return EffectSpec(listOf(
            DocumentEffect(
                autoCrop = profile.autoCrop,
                contrastProfile = if (enhancementEnabled()) profile.contrastLabel else null,
                colorMode = colorMode,
                scanGuide = enhancementEnabled()
            ),
            WatermarkEffect(
                templateId = selectedWatermarkTemplate.id,
                tokens = watermarkTokens(runtimeState().stillCaptureResolutionPreset.label),
                style = watermarkStyle
            )
        ))
    }

    override suspend fun handleShutterPressed(): ModeSignal {
        val profile = currentProfile()
        context.eventSink("${modeEventPrefix()}.capture.requested.${profile.id}")
        val signal = super.handleShutterPressed()
        updateSnapshot(headline = "Document capture requested")
        return signal
    }

    override fun buildCaptureStrategy(
        effectSpec: EffectSpec,
        countdownSeconds: Int
    ): ModeSignal.SubmitCapture {
        val profile = currentProfile()
        val basePostProcess = EffectBridge.toPostProcessSpec(effectSpec)
        val postProcessSpec = basePostProcess.copy(
            watermarkText = if (enhancementEnabled()) {
                "DOC ${profile.label}"
            } else {
                "DOC Basic ${profile.label}"
            },
            exifOverrides = basePostProcess.exifOverrides + buildMap {
                put("SceneCaptureType", "Document")
                put("ProcessingRendered", if (enhancementEnabled()) "enhanced-scan:${resolveColorModeTag()}" else "basic-archive")
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
                        customTags = captureMetadataTags(
                            effectSpec = effectSpec,
                            modeTags = buildMap {
                                put("mode", "document")
                                put("profile", profile.id)
                                put("scanMode", if (enhancementEnabled()) "enhanced" else "basic")
                                put("outputClass", "scan")
                                put("stillResolution", runtimeState().stillCaptureResolutionPreset.tagValue)
                            }
                        )
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

    override fun buildSnapshot(headline: String, detail: String?): ModeSnapshot {
        return ModeSnapshot(
            id = ModeId.DOCUMENT,
            uiSpec = uiSpec,
            state = ModeState(
                headline = headline,
                detail = detail ?: buildDefaultDetail()
            )
        )
    }

    override fun buildDefaultDetail(): String = profileSummary(currentProfile())

    // ── Headlines ─────────────────────────────────────────────────────

    override fun enterHeadline(): String = if (enhancementEnabled()) {
        "Document scan active"
    } else {
        "Document archive active"
    }

    override fun exitHeadline(): String = "Document mode inactive"

    override fun capabilitiesChangedHeadline(): String = if (enhancementEnabled()) {
        "Document scan active"
    } else {
        "Document archive active"
    }

    override fun resolutionChangedHeadline(): String = if (enhancementEnabled()) {
        "Document resolution updated"
    } else {
        "Archive resolution updated"
    }

    override fun sessionEventText(): StillShotSessionEventText = StillShotSessionEventText(
        shotStartedHeadline = "Document scan in progress",
        shotCompletedHeadline = "Document saved",
        shotFailedHeadline = "Document capture failed"
    )

    // ── Intent overrides ──────────────────────────────────────────────

    override suspend fun handleSecondaryAction(): ModeSignal {
        profileIndex = (profileIndex + 1) % currentProfiles().size
        val profile = currentProfile()
        context.eventSink("${modeEventPrefix()}.profile.selected.${profile.id}")
        updateSnapshot(
            headline = if (enhancementEnabled()) {
                "Document style updated"
            } else {
                "Archive style updated"
            }
        )
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("扫描场景: ${profile.label} (色彩: ${resolveColorModeLabel()})")
    }

    override suspend fun handleTertiaryAction(): ModeSignal = ModeSignal.None

    override suspend fun handleFrameRatioSelected(ratio: FrameRatio): ModeSignal =
        ModeSignal.ShowHint("文档模式使用自动裁边，无需选择画幅比例")

    // ── Capability coercion ───────────────────────────────────────────

    override suspend fun onModeCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) {
        profileIndex = profileIndex.coerceAtMost(currentProfiles().lastIndex)
    }

    // ── Profile helpers ───────────────────────────────────────────────

    private fun enhancementEnabled(): Boolean =
        runtimeState().deviceCapabilities.supportsDocumentScanEnhancement

    private fun currentProfiles(): List<DocumentProfile> {
        return if (enhancementEnabled()) {
            ENHANCED_PROFILES
        } else {
            BASIC_PROFILES
        }
    }

    private fun currentProfile(): DocumentProfile = currentProfiles()[profileIndex]

    private fun profileSummary(profile: DocumentProfile): String {
        val colorLabel = resolveColorModeLabel()
        return if (enhancementEnabled()) {
            "Style ${profile.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Auto crop ${profile.autoCrop} | Contrast ${profile.contrastLabel} | Color: $colorLabel | Output optimized for scanned documents."
        } else {
            "Style ${profile.label} | Size ${runtimeState().stillCaptureResolutionPreset.label} | Basic capture only (document enhancement is unavailable)."
        }
    }

    private fun resolveDocumentColorMode(): DocumentColorMode? {
        val preset = StylePresetCatalog.selectedPreset(
            context.settingsSnapshot.catalog,
            context.settingsSnapshot.persisted,
            StylePresetFamily.DOCUMENT
        ) ?: return null
        return DOCUMENT_PRESET_TO_COLOR_MODE[preset.profileId]
    }

    private fun resolveColorModeLabel(): String {
        return COLOR_MODE_LABELS[resolveDocumentColorMode()] ?: COLOR_MODE_LABELS[DocumentColorMode.COLOR_NEUTRAL]!!
    }

    private fun resolveColorModeTag(): String {
        return (resolveDocumentColorMode() ?: DocumentColorMode.COLOR_NEUTRAL).tagValue
    }

    private data class DocumentProfile(
        val id: String,
        val label: String,
        val autoCrop: Boolean,
        val contrastLabel: String,
        val algorithmProfile: String
    )

    companion object {
        private val COLOR_MODE_LABELS = mapOf(
            DocumentColorMode.COLOR_NEUTRAL to "原色",
            DocumentColorMode.COLOR_ENHANCED to "增强",
            DocumentColorMode.GRAYSCALE to "灰度",
            DocumentColorMode.BLACK_AND_WHITE to "黑白"
        )

        private val DOCUMENT_PRESET_TO_COLOR_MODE = mapOf(
            "doc-color-neutral" to DocumentColorMode.COLOR_NEUTRAL,
            "doc-color-enhanced" to DocumentColorMode.COLOR_ENHANCED,
            "doc-grayscale" to DocumentColorMode.GRAYSCALE,
            "doc-bw" to DocumentColorMode.BLACK_AND_WHITE
        )

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
