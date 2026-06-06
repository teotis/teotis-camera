package com.opencamera.core.mode

import com.opencamera.core.capability.CapabilityRequirementKind

data class ModeProductDeclaration(
    val modeId: ModeId,
    val displayName: String,
    val primaryGate: PrimaryCapabilityGate,
    val requirements: List<ModeCapabilityRequirement>,
    val strategyVariants: List<ModeStrategyVariant>,
    val effectProfile: ModeEffectProfile
)

data class PrimaryCapabilityGate(
    val kind: CapabilityRequirementKind,
    val unsupportedMessage: String
)

data class ModeCapabilityRequirement(
    val id: String,
    val kind: CapabilityRequirementKind,
    val isOptional: Boolean,
    val degradationDescription: String,
    val fallbackId: String? = null
)

data class ModeStrategyVariant(
    val id: String,
    val type: ModeStrategyType,
    val conditionDescription: String,
    val requiredCapabilityIds: List<String> = emptyList()
)

enum class ModeStrategyType {
    SINGLE_FRAME,
    MULTI_FRAME,
    LIVE_PHOTO,
    VIDEO_RECORDING
}

data class ModeEffectProfile(
    val usesFilter: Boolean = false,
    val usesWatermark: Boolean = false,
    val usesPortraitEffect: Boolean = false,
    val usesDocumentEffect: Boolean = false,
    val usesFrameEffect: Boolean = false
)

fun ModeId.modeProductDeclaration(): ModeProductDeclaration {
    return when (this) {
        ModeId.PHOTO -> ModeProductDeclaration(
            modeId = this,
            displayName = "Photo",
            primaryGate = PrimaryCapabilityGate(
                kind = CapabilityRequirementKind.STILL_CAPTURE,
                unsupportedMessage = "Photo mode requires still capture support"
            ),
            requirements = listOf(
                ModeCapabilityRequirement(
                    id = "photo-filter",
                    kind = CapabilityRequirementKind.FILTER_CAPTURE_RENDER,
                    isOptional = true,
                    degradationDescription = "Filter render unavailable; capture proceeds without filter effect"
                ),
                ModeCapabilityRequirement(
                    id = "photo-watermark",
                    kind = CapabilityRequirementKind.WATERMARK_RENDER,
                    isOptional = true,
                    degradationDescription = "Watermark render unavailable; capture proceeds without watermark"
                ),
                ModeCapabilityRequirement(
                    id = "photo-live-motion",
                    kind = CapabilityRequirementKind.TEMPORAL_RING_BUFFER,
                    isOptional = true,
                    degradationDescription = "Live Photo motion unavailable; degrades to still-only capture"
                ),
                ModeCapabilityRequirement(
                    id = "photo-live-sidecar",
                    kind = CapabilityRequirementKind.MOTION_SIDE_CAR,
                    isOptional = true,
                    degradationDescription = "Live Photo sidecar unavailable; degrades to still-only capture"
                )
            ),
            strategyVariants = listOf(
                ModeStrategyVariant(
                    id = "photo-standard",
                    type = ModeStrategyType.SINGLE_FRAME,
                    conditionDescription = "Standard still capture when Live Photo is disabled"
                ),
                ModeStrategyVariant(
                    id = "photo-live",
                    type = ModeStrategyType.LIVE_PHOTO,
                    conditionDescription = "Live Photo when enabled in settings and device supports temporal capture",
                    requiredCapabilityIds = listOf("photo-live-motion", "photo-live-sidecar")
                )
            ),
            effectProfile = ModeEffectProfile(
                usesFilter = true,
                usesWatermark = true,
                usesFrameEffect = true
            )
        )

        ModeId.CHECK_IN -> ModeProductDeclaration(
            modeId = this,
            displayName = "Check-in",
            primaryGate = PrimaryCapabilityGate(
                kind = CapabilityRequirementKind.STILL_CAPTURE,
                unsupportedMessage = "Check-in mode requires still capture support"
            ),
            requirements = listOf(
                ModeCapabilityRequirement(
                    id = "checkin-portrait-segmentation",
                    kind = CapabilityRequirementKind.PORTRAIT_SEGMENTATION,
                    isOptional = false,
                    degradationDescription = "Depth segmentation unavailable; degrades to focus-priority fallback",
                    fallbackId = "checkin-focus"
                ),
                ModeCapabilityRequirement(
                    id = "checkin-multiframe",
                    kind = CapabilityRequirementKind.MULTI_FRAME_CAPTURE,
                    isOptional = false,
                    degradationDescription = "Multi-frame capture unavailable; degrades to single-frame best-effort capture",
                    fallbackId = "checkin-best-frame"
                ),
                ModeCapabilityRequirement(
                    id = "checkin-filter",
                    kind = CapabilityRequirementKind.FILTER_CAPTURE_RENDER,
                    isOptional = true,
                    degradationDescription = "Filter render unavailable; capture proceeds without filter effect"
                ),
                ModeCapabilityRequirement(
                    id = "checkin-watermark",
                    kind = CapabilityRequirementKind.WATERMARK_RENDER,
                    isOptional = true,
                    degradationDescription = "Watermark render unavailable; capture proceeds without watermark"
                ),
                ModeCapabilityRequirement(
                    id = "checkin-focus-stack",
                    kind = CapabilityRequirementKind.MULTI_FRAME_CAPTURE,
                    isOptional = true,
                    degradationDescription = "Focus stack fusion unavailable; degrades to honest best-frame selection",
                    fallbackId = "checkin-best-frame"
                )
            ),
            strategyVariants = listOf(
                ModeStrategyVariant(
                    id = "checkin-portrait-depth",
                    type = ModeStrategyType.SINGLE_FRAME,
                    conditionDescription = "Portrait with depth effect when segmentation is supported",
                    requiredCapabilityIds = listOf("checkin-portrait-segmentation")
                ),
                ModeStrategyVariant(
                    id = "checkin-focus",
                    type = ModeStrategyType.SINGLE_FRAME,
                    conditionDescription = "Focus-priority fallback when depth segmentation is unavailable"
                ),
                ModeStrategyVariant(
                    id = "checkin-multiframe",
                    type = ModeStrategyType.MULTI_FRAME,
                    conditionDescription = "Multi-frame merge for focus-bracket and low-light scenes when device supports multi-frame capture",
                    requiredCapabilityIds = listOf("checkin-multiframe")
                ),
                ModeStrategyVariant(
                    id = "checkin-best-frame",
                    type = ModeStrategyType.SINGLE_FRAME,
                    conditionDescription = "Single-frame best-effort capture when multi-frame is unavailable"
                ),
                ModeStrategyVariant(
                    id = "checkin-static-scene",
                    type = ModeStrategyType.SINGLE_FRAME,
                    conditionDescription = "Static-scene low-light fallback when multi-frame merge is unavailable and scene is static"
                )
            ),
            effectProfile = ModeEffectProfile(
                usesFilter = true,
                usesWatermark = true,
                usesPortraitEffect = true,
                usesFrameEffect = true
            )
        )

        ModeId.HUMANISTIC -> ModeProductDeclaration(
            modeId = this,
            displayName = "Humanistic",
            primaryGate = PrimaryCapabilityGate(
                kind = CapabilityRequirementKind.STILL_CAPTURE,
                unsupportedMessage = "Humanistic mode requires still capture support"
            ),
            requirements = listOf(
                ModeCapabilityRequirement(
                    id = "humanistic-filter",
                    kind = CapabilityRequirementKind.FILTER_CAPTURE_RENDER,
                    isOptional = true,
                    degradationDescription = "Filter render unavailable; capture proceeds without tone effect"
                ),
                ModeCapabilityRequirement(
                    id = "humanistic-manual-control",
                    kind = CapabilityRequirementKind.MANUAL_CONTROL,
                    isOptional = false,
                    degradationDescription = "Manual controls unavailable; degrades to assisted preset mode",
                    fallbackId = "humanistic-auto"
                ),
                ModeCapabilityRequirement(
                    id = "humanistic-live-motion",
                    kind = CapabilityRequirementKind.TEMPORAL_RING_BUFFER,
                    isOptional = true,
                    degradationDescription = "Live Photo motion unavailable; degrades to still-only capture"
                ),
                ModeCapabilityRequirement(
                    id = "humanistic-live-sidecar",
                    kind = CapabilityRequirementKind.MOTION_SIDE_CAR,
                    isOptional = true,
                    degradationDescription = "Live Photo sidecar unavailable; degrades to still-only capture"
                )
            ),
            strategyVariants = listOf(
                ModeStrategyVariant(
                    id = "humanistic-manual",
                    type = ModeStrategyType.SINGLE_FRAME,
                    conditionDescription = "Manual control with ISO, shutter speed, white balance, and focus",
                    requiredCapabilityIds = listOf("humanistic-manual-control")
                ),
                ModeStrategyVariant(
                    id = "humanistic-auto",
                    type = ModeStrategyType.SINGLE_FRAME,
                    conditionDescription = "Assisted preset when manual controls are unavailable"
                ),
                ModeStrategyVariant(
                    id = "humanistic-standard",
                    type = ModeStrategyType.SINGLE_FRAME,
                    conditionDescription = "Standard humanistic capture with filter and frame ratio"
                ),
                ModeStrategyVariant(
                    id = "humanistic-live",
                    type = ModeStrategyType.LIVE_PHOTO,
                    conditionDescription = "Live Photo when enabled in settings",
                    requiredCapabilityIds = listOf("humanistic-live-motion", "humanistic-live-sidecar")
                )
            ),
            effectProfile = ModeEffectProfile(
                usesFilter = true,
                usesFrameEffect = true
            )
        )

        ModeId.DOCUMENT -> ModeProductDeclaration(
            modeId = this,
            displayName = "Document",
            primaryGate = PrimaryCapabilityGate(
                kind = CapabilityRequirementKind.STILL_CAPTURE,
                unsupportedMessage = "Document mode requires still capture support"
            ),
            requirements = listOf(
                ModeCapabilityRequirement(
                    id = "document-geometry",
                    kind = CapabilityRequirementKind.DOCUMENT_GEOMETRY,
                    isOptional = true,
                    degradationDescription = "Document geometry unavailable; degrades to basic archive capture",
                    fallbackId = "document-basic"
                )
            ),
            strategyVariants = listOf(
                ModeStrategyVariant(
                    id = "document-enhanced",
                    type = ModeStrategyType.SINGLE_FRAME,
                    conditionDescription = "Enhanced scan with auto-crop and contrast when geometry is supported",
                    requiredCapabilityIds = listOf("document-geometry")
                ),
                ModeStrategyVariant(
                    id = "document-basic",
                    type = ModeStrategyType.SINGLE_FRAME,
                    conditionDescription = "Basic archive capture when document enhancement is unavailable"
                )
            ),
            effectProfile = ModeEffectProfile(
                usesDocumentEffect = true
            )
        )

        ModeId.VIDEO -> ModeProductDeclaration(
            modeId = this,
            displayName = "Video",
            primaryGate = PrimaryCapabilityGate(
                kind = CapabilityRequirementKind.VIDEO_RECORDING,
                unsupportedMessage = "Video mode requires video recording support"
            ),
            requirements = listOf(
                ModeCapabilityRequirement(
                    id = "video-recording",
                    kind = CapabilityRequirementKind.VIDEO_RECORDING,
                    isOptional = true,
                    degradationDescription = "Video recording not available"
                )
            ),
            strategyVariants = listOf(
                ModeStrategyVariant(
                    id = "video-standard",
                    type = ModeStrategyType.VIDEO_RECORDING,
                    conditionDescription = "Standard video recording with resolution and audio profile"
                )
            ),
            effectProfile = ModeEffectProfile(
                usesFilter = true
            )
        )

    }
}
