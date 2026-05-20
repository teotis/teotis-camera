package com.opencamera.core.effect

import com.opencamera.core.device.DeviceCapabilities

enum class EffectSupport { SUPPORTED, DEGRADED, UNSUPPORTED }

data class EffectCapabilityResult(
    val entry: EffectEntry,
    val support: EffectSupport,
    val reason: String? = null
)

data class CapabilityReport(
    val results: List<EffectCapabilityResult>,
    val effectiveSpec: EffectSpec
)

class EffectCapabilityResolver(
    private val deviceCapabilities: DeviceCapabilities
) {
    fun resolve(spec: EffectSpec): CapabilityReport {
        val results = spec.entries.map { resolveEntry(it) }
        val effectiveEntries = results
            .filter { it.support != EffectSupport.UNSUPPORTED }
            .map { it.entry }
        return CapabilityReport(
            results = results,
            effectiveSpec = EffectSpec(effectiveEntries)
        )
    }

    private fun resolveEntry(entry: EffectEntry): EffectCapabilityResult {
        return when (entry) {
            is FilterEffect -> EffectCapabilityResult(entry, EffectSupport.SUPPORTED)
            is WatermarkEffect -> EffectCapabilityResult(entry, EffectSupport.SUPPORTED)
            is FrameEffect -> EffectCapabilityResult(entry, EffectSupport.SUPPORTED)
            is PortraitEffect -> resolvePortrait(entry)
            is DocumentEffect -> resolveDocument(entry)
            is SelfieMirrorEffect -> EffectCapabilityResult(entry, EffectSupport.SUPPORTED)
        }
    }

    private fun resolvePortrait(entry: PortraitEffect): EffectCapabilityResult {
        return if (deviceCapabilities.supportsPortraitDepthEffect) {
            EffectCapabilityResult(entry, EffectSupport.SUPPORTED)
        } else {
            val degraded = entry.copy(renderPath = "focus")
            EffectCapabilityResult(
                degraded,
                EffectSupport.DEGRADED,
                "Device does not support depth effect, using focus mode"
            )
        }
    }

    private fun resolveDocument(entry: DocumentEffect): EffectCapabilityResult {
        return if (deviceCapabilities.supportsDocumentScanEnhancement) {
            EffectCapabilityResult(entry, EffectSupport.SUPPORTED)
        } else {
            val degraded = entry.copy(autoCrop = false, contrastProfile = null)
            EffectCapabilityResult(
                degraded,
                EffectSupport.DEGRADED,
                "Device does not support document scan enhancement"
            )
        }
    }
}
