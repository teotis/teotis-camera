package com.opencamera.core.device

import com.opencamera.core.capability.CapabilitySupport
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import kotlin.math.roundToInt

data class EffectiveStillCaptureRecipe(
    val graphSpec: DeviceGraphSpec,
    val capabilities: DeviceCapabilities,

    val resolvedOutputSize: StillCaptureOutputSize?,
    val resolutionSource: StillCaptureResolutionSource,
    val bindability: CapabilitySupport,
    val bindabilityReason: String
) {
    val qualityPreference: StillCaptureQualityPreference
        get() = graphSpec.stillCapture.qualityPreference

    val resolutionPreset: StillCaptureResolutionPreset
        get() = graphSpec.stillCapture.resolutionPreset

    val pixelLabel: String
        get() {
            val size = resolvedOutputSize ?: return resolutionPreset.label
            val megapixels = (size.pixelCount / 1_000_000.0).roundToInt()
            return if (megapixels > 0) "${megapixels}MP" else resolutionPreset.label
        }

    val quickLabel: String
        get() = when (resolutionSource) {
            StillCaptureResolutionSource.MAXIMUM_RESOLUTION -> "$pixelLabel (Max)"
            StillCaptureResolutionSource.HIGH_RESOLUTION -> "$pixelLabel (High)"
            StillCaptureResolutionSource.STANDARD -> pixelLabel
        }

    val previewResolutionMismatch: Boolean
        get() = resolutionSource != StillCaptureResolutionSource.STANDARD

    val metadataCustomTags: Map<String, String>
        get() = buildMap {
            put("stillQuality", qualityPreference.tagValue)
            put("stillResolution", resolutionPreset.tagValue)
            put("stillResolutionSource", resolutionSource.tagValue)
            resolvedOutputSize?.let { size ->
                put("stillOutputSize", "${size.width}x${size.height}")
            }
            if (bindability != CapabilitySupport.SUPPORTED) {
                put("stillBindability", bindability.tagValue)
                if (bindabilityReason.isNotEmpty()) {
                    put("stillBindabilityReason", bindabilityReason)
                }
            }
        }

    fun enrichDeviceGraph(base: DeviceGraphSpec): DeviceGraphSpec {
        return base.copy(
            stillCapture = base.stillCapture.copy(
                outputSize = resolvedOutputSize
            )
        )
    }

    fun diagnostics(): List<String> {
        val notes = mutableListOf<String>()
        notes.add("still:resolution-source=${resolutionSource.tagValue}")
        notes.add("still:bindability=${bindability.tagValue}")
        resolvedOutputSize?.let {
            notes.add("still:output=${it.width}x${it.height}")
        }
        if (bindabilityReason.isNotEmpty()) {
            notes.add("still:bind-reason=$bindabilityReason")
        }
        if (previewResolutionMismatch) {
            notes.add("still:preview-fidelity=degraded")
        }
        return notes
    }

    companion object {
        fun build(
            graphSpec: DeviceGraphSpec,
            capabilities: DeviceCapabilities
        ): EffectiveStillCaptureRecipe {
            val available = capabilities.availableStillCaptureOutputSizes

            val resolved = resolveOutputSize(graphSpec, available)
            val source = resolved?.resolutionSource ?: StillCaptureResolutionSource.STANDARD

            val (bindability, reason) = determineBindability(available, resolved)

            return EffectiveStillCaptureRecipe(
                graphSpec = graphSpec,
                capabilities = capabilities,
                resolvedOutputSize = resolved,
                resolutionSource = source,
                bindability = bindability,
                bindabilityReason = reason
            )
        }

        internal fun resolveOutputSize(
            graphSpec: DeviceGraphSpec,
            available: List<StillCaptureOutputSize>
        ): StillCaptureOutputSize? {
            val explicit = graphSpec.stillCapture.outputSize
            if (explicit != null && explicit in available) return explicit

            if (available.isEmpty()) return null

            val preset = graphSpec.stillCapture.resolutionPreset
            val sortedByPixels = available.sortedBy { it.pixelCount }
            val desiredPixels = preset.targetWidth.toLong() * preset.targetHeight.toLong()

            return when (preset) {
                StillCaptureResolutionPreset.LARGE_12MP -> sortedByPixels.last()
                StillCaptureResolutionPreset.MEDIUM_8MP,
                StillCaptureResolutionPreset.SMALL_2MP -> sortedByPixels
                    .lastOrNull { it.pixelCount <= desiredPixels }
                    ?: sortedByPixels.first()
            }
        }

        private fun determineBindability(
            available: List<StillCaptureOutputSize>,
            resolved: StillCaptureOutputSize?
        ): Pair<CapabilitySupport, String> {
            if (resolved == null || available.isEmpty()) {
                return CapabilitySupport.UNSUPPORTED to "No still capture sizes available"
            }
            return CapabilitySupport.SUPPORTED to ""
        }
    }
}
