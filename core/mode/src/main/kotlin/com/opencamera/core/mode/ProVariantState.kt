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
            isEnabled && manualControlsEnabled() -> "$modeDisplayName Pro on"
            isEnabled -> "$modeDisplayName Pro assist on"
            else -> "$modeDisplayName Pro off"
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
            if (manualControlsEnabled()) "Exit Pro" else "Exit Pro Assist"
        } else if (manualControlsEnabled()) {
            "Enter Pro"
        } else {
            "Enter Pro Assist"
        }
    }

    fun variantExifLabel(): String =
        if (manualControlsEnabled()) "Pro" else "Pro Assist"

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
            "Pro draft ${currentManualDraft().compactSummary()} is attached to the $requestName request."
        } else {
            "Pro assist keeps ${currentManualDraft().compactSummary()} as saved-only draft because manual controls are unavailable on this device."
        }
    }
}
