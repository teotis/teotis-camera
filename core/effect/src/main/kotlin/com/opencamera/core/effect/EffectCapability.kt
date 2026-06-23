package com.opencamera.core.effect

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

interface EffectCapabilityQuery {
    fun supportsPortraitDepth(): Boolean
    fun supportsDocumentGeometry(): Boolean
    fun supportsManualControls(): Boolean

    companion object {
        val DefaultSupported = object : EffectCapabilityQuery {
            override fun supportsPortraitDepth(): Boolean = true
            override fun supportsDocumentGeometry(): Boolean = true
            override fun supportsManualControls(): Boolean = true
        }
    }
}

class EffectCapabilityResolver(
    private val capabilities: EffectCapabilityQuery = EffectCapabilityQuery.DefaultSupported
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
        return if (capabilities.supportsPortraitDepth()) {
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
        return if (capabilities.supportsDocumentGeometry()) {
            EffectCapabilityResult(entry, EffectSupport.SUPPORTED)
        } else {
            val degraded = entry.copy(
                autoCrop = false,
                contrastProfile = null,
                colorMode = null,
                scanGuide = false
            )
            EffectCapabilityResult(
                degraded,
                EffectSupport.DEGRADED,
                "Device does not support document scan enhancement"
            )
        }
    }
}
