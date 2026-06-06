package com.opencamera.core.mode

import com.opencamera.core.settings.compactSummary
import com.opencamera.core.settings.toMetadataTags

data class ProVariantToggleResult(
    val enabled: Boolean,
    val eventSuffix: String,
    val signal: ModeSignal
)

class ProVariantState(
    private val context: ModeContext
) {
    var isEnabled: Boolean = false
        private set

    fun toggle(modeDisplayName: String): ProVariantToggleResult {
        isEnabled = !isEnabled
        val eventSuffix = if (isEnabled) "entered" else "exited"
        val hint = when {
            isEnabled && manualControlsEnabled() -> "$modeDisplayName Professional on"
            isEnabled -> "$modeDisplayName Professional assist on"
            else -> "$modeDisplayName Professional off"
        }
        return ProVariantToggleResult(
            enabled = isEnabled,
            eventSuffix = eventSuffix,
            signal = ModeSignal.ShowHint(hint)
        )
    }

    fun manualControlsEnabled(): Boolean =
        context.runtimeState().deviceCapabilities.supportsAppliedManualControls

    fun currentManualDraft() =
        context.settingsSnapshot.catalog.manualCaptureDraft

    fun currentManualDraftOrNull() =
        currentManualDraft().takeIf { isEnabled }

    fun modeVariantTag(): String =
        if (isEnabled) "pro" else "standard"

    fun resolvedControlMode(): String =
        if (manualControlsEnabled()) "manual" else "assisted"

    fun manualDraftState(): String =
        if (manualControlsEnabled()) "metadata-draft" else "unsupported"

    fun resolvedAlgorithmProfile(base: String): String {
        return if (!isEnabled) {
            base
        } else if (manualControlsEnabled()) {
            "$base-pro"
        } else {
            "$base-pro-assist"
        }
    }

    fun proActionLabel(): String {
        return if (isEnabled) {
            if (manualControlsEnabled()) "Professional on" else "Professional assist on"
        } else if (manualControlsEnabled()) {
            "Professional"
        } else {
            "Professional assist"
        }
    }

    fun variantExifLabel(): String =
        if (manualControlsEnabled()) "Professional" else "Professional assist"

    fun metadataTags(): Map<String, String> {
        if (!isEnabled) return emptyMap()
        return buildMap {
            put("controlMode", resolvedControlMode())
            put("manualDraftState", manualDraftState())
            putAll(currentManualDraft().toMetadataTags())
        }
    }

    fun summaryText(requestName: String): String {
        return if (manualControlsEnabled()) {
            "Professional draft ${currentManualDraft().compactSummary()} is attached to the $requestName request."
        } else {
            "Professional assist keeps ${currentManualDraft().compactSummary()} as saved-only draft because manual controls are unavailable on this device."
        }
    }
}
