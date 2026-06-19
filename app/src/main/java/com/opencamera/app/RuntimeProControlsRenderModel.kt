package com.opencamera.app

import com.opencamera.app.i18n.AppTextResolver
import com.opencamera.core.device.ManualControlCapabilityMatrix
import com.opencamera.core.device.ManualControlSupport
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.SessionState
import com.opencamera.core.settings.FeatureCatalogAction
import com.opencamera.core.settings.ManualCaptureParams
import java.util.Locale

internal data class RuntimeProControlsRenderModel(
    val isVisible: Boolean,
    val headline: String,
    val supportingText: String,
    val summary: String,
    val rawControl: FeatureCatalogControlRenderModel,
    val isoControl: FeatureCatalogControlRenderModel,
    val shutterControl: FeatureCatalogControlRenderModel,
    val exposureControl: FeatureCatalogControlRenderModel,
    val focusControl: FeatureCatalogControlRenderModel,
    val apertureControl: FeatureCatalogControlRenderModel,
    val whiteBalanceControl: FeatureCatalogControlRenderModel
)

internal fun runtimeProControlsRenderModel(
    state: SessionState,
    text: AppTextResolver
): RuntimeProControlsRenderModel {
    val isEditableMode = state.activeMode in setOf(
        ModeId.HUMANISTIC
    )
    val isVisible = isEditableMode && state.modeSnapshot.state.isProVariantActive
    val draft = state.settings.catalog.manualCaptureDraft
    val manualCapabilities = state.activeDeviceCapabilities.resolvedManualControlCapabilities
    val hasAppliedManualControls = state.activeDeviceCapabilities.supportsAppliedManualControls
    val editingEnabled = settingsPageEditingEnabled(state)
    val runtimeSupportLabel = manualSupportSummary(text, manualCapabilities)
    return RuntimeProControlsRenderModel(
        isVisible = isVisible,
        headline = when (state.activeMode) {
            ModeId.HUMANISTIC -> text.get(R.string.pro_controls_humanistic)
            else -> text.get(R.string.pro_controls_default)
        },
        supportingText = if (hasAppliedManualControls) {
            text.get(R.string.pro_controls_supporting_editable)
        } else {
            text.get(R.string.pro_controls_supporting_readonly)
        },
        summary = buildString {
            append(draft.localizedCompactSummary(text))
            append(" | ")
            append(runtimeSupportLabel)
            if (!editingEnabled) {
                append(" ")
                append(text.get(R.string.pro_controls_finish_capture_hint))
            }
        },
        rawControl = FeatureCatalogControlRenderModel(
            label = text.get(R.string.label_raw),
            value = if (manualCapabilities.raw == ManualControlSupport.SAVED_ONLY) {
                text.get(R.string.raw_saved_only_value)
            } else {
                onOffLabel(draft.rawEnabled, text)
            },
            availability = manualCapabilities.raw.toSettingsAvailability(),
            availabilityLabel = text.availabilityLabel(manualCapabilities.raw.toSettingsAvailability()),
            supportLabel = manualCapabilities.raw.manualSupportLabel(text),
            nextAction = FeatureCatalogAction.UpdateManualRawEnabled(!draft.rawEnabled)
                .takeIf { isVisible && editingEnabled }
        ),
        isoControl = FeatureCatalogControlRenderModel(
            label = text.get(R.string.label_iso),
            value = draft.iso?.toString() ?: text.get(R.string.label_auto),
            availability = manualCapabilities.iso.toSettingsAvailability(),
            availabilityLabel = text.availabilityLabel(manualCapabilities.iso.toSettingsAvailability()),
            supportLabel = manualCapabilities.iso.manualSupportLabel(text),
            nextAction = nextListValueOrNull(draft.iso, MANUAL_ISO_OPTIONS)
                ?.let(FeatureCatalogAction::UpdateManualIso)
                ?.takeIf { isVisible && editingEnabled }
        ),
        shutterControl = FeatureCatalogControlRenderModel(
            label = text.get(R.string.label_shutter),
            value = draft.shutterSpeedMillis?.let { "${it}ms" } ?: text.get(R.string.label_auto),
            availability = manualCapabilities.shutter.toSettingsAvailability(),
            availabilityLabel = text.availabilityLabel(manualCapabilities.shutter.toSettingsAvailability()),
            supportLabel = manualCapabilities.shutter.manualSupportLabel(text),
            nextAction = nextListValueOrNull(
                draft.shutterSpeedMillis,
                MANUAL_SHUTTER_OPTIONS
            )?.let(FeatureCatalogAction::UpdateManualShutterSpeedMillis)
                ?.takeIf { isVisible && editingEnabled }
        ),
        exposureControl = FeatureCatalogControlRenderModel(
            label = text.get(R.string.label_ev),
            value = draft.exposureCompensationSteps?.let(::manualEvLabel) ?: text.get(R.string.label_auto),
            availability = manualCapabilities.exposureCompensation.toSettingsAvailability(),
            availabilityLabel = text.availabilityLabel(manualCapabilities.exposureCompensation.toSettingsAvailability()),
            supportLabel = manualCapabilities.exposureCompensation.manualSupportLabel(text),
            nextAction = nextListValueOrNull(
                draft.exposureCompensationSteps,
                MANUAL_EXPOSURE_OPTIONS
            )?.let(FeatureCatalogAction::UpdateManualExposureCompensationSteps)
                ?.takeIf { isVisible && editingEnabled }
        ),
        focusControl = FeatureCatalogControlRenderModel(
            label = text.get(R.string.label_focus),
            value = draft.focusDistanceDiopters?.let { String.format(Locale.US, "%.1fD", it) }
                ?: text.get(R.string.label_auto),
            availability = manualCapabilities.focusDistance.toSettingsAvailability(),
            availabilityLabel = text.availabilityLabel(manualCapabilities.focusDistance.toSettingsAvailability()),
            supportLabel = manualCapabilities.focusDistance.manualSupportLabel(text),
            nextAction = nextListValueOrNull(
                draft.focusDistanceDiopters,
                MANUAL_FOCUS_OPTIONS
            )?.let(FeatureCatalogAction::UpdateManualFocusDistanceDiopters)
                ?.takeIf { isVisible && editingEnabled }
        ),
        apertureControl = FeatureCatalogControlRenderModel(
            label = text.get(R.string.label_aperture),
            value = draft.apertureFNumber?.let { "f/${manualOneDecimal(it)}" } ?: text.get(R.string.label_auto),
            availability = manualCapabilities.aperture.toSettingsAvailability(),
            availabilityLabel = text.availabilityLabel(manualCapabilities.aperture.toSettingsAvailability()),
            supportLabel = manualCapabilities.aperture.manualSupportLabel(text),
            nextAction = nextListValueOrNull(
                draft.apertureFNumber,
                MANUAL_APERTURE_OPTIONS
            )?.let(FeatureCatalogAction::UpdateManualApertureFNumber)
                ?.takeIf { isVisible && editingEnabled }
        ),
        whiteBalanceControl = FeatureCatalogControlRenderModel(
            label = text.get(R.string.label_wb),
            value = draft.whiteBalanceKelvin?.let { "${it}K" } ?: text.get(R.string.label_auto),
            availability = manualCapabilities.whiteBalance.toSettingsAvailability(),
            availabilityLabel = text.availabilityLabel(manualCapabilities.whiteBalance.toSettingsAvailability()),
            supportLabel = manualCapabilities.whiteBalance.manualSupportLabel(text),
            nextAction = nextListValueOrNull(
                draft.whiteBalanceKelvin,
                MANUAL_WHITE_BALANCE_OPTIONS
            )?.let(FeatureCatalogAction::UpdateManualWhiteBalanceKelvin)
                ?.takeIf { isVisible && editingEnabled }
        )
    )
}

private fun ManualControlSupport.toSettingsAvailability(): SettingsControlAvailability {
    return when (this) {
        ManualControlSupport.APPLY -> SettingsControlAvailability.SUPPORTED
        ManualControlSupport.SAVED_ONLY -> SettingsControlAvailability.DEGRADED
        ManualControlSupport.UNSUPPORTED -> SettingsControlAvailability.UNSUPPORTED
    }
}

private fun ManualControlSupport.manualSupportLabel(text: AppTextResolver): String {
    return when (this) {
        ManualControlSupport.APPLY -> text.get(R.string.manual_camera2_interop)
        ManualControlSupport.SAVED_ONLY -> text.get(R.string.manual_saved_only)
        ManualControlSupport.UNSUPPORTED -> text.get(R.string.manual_temporarily_unsupported)
    }
}

private fun manualSupportSummary(
    text: AppTextResolver,
    capabilities: ManualControlCapabilityMatrix
): String {
    val applied = mutableListOf<String>()
    val savedOnly = mutableListOf<String>()
    val unsupported = mutableListOf<String>()

    fun collect(label: String, support: ManualControlSupport) {
        when (support) {
            ManualControlSupport.APPLY -> applied += label
            ManualControlSupport.SAVED_ONLY -> savedOnly += label
            ManualControlSupport.UNSUPPORTED -> unsupported += label
        }
    }

    collect("RAW", capabilities.raw)
    collect("ISO", capabilities.iso)
    collect(text.get(R.string.label_shutter), capabilities.shutter)
    collect("EV", capabilities.exposureCompensation)
    collect(text.get(R.string.label_focus), capabilities.focusDistance)
    collect(text.get(R.string.label_aperture), capabilities.aperture)
    collect("WB", capabilities.whiteBalance)

    return buildString {
        if (applied.isNotEmpty()) {
            append(text.get(R.string.manual_adapter_applies))
            append(" ")
            append(applied.joinToString(separator = " / "))
        }
        if (savedOnly.isNotEmpty()) {
            if (isNotEmpty()) {
                append(" | ")
            }
            append(savedOnly.joinToString(separator = " / "))
            append(" ")
            append(text.get(R.string.manual_stay_saved_only))
        }
        if (unsupported.isNotEmpty()) {
            if (isNotEmpty()) {
                append(" | ")
            }
            append(unsupported.joinToString(separator = " / "))
            append(" ")
            append(text.get(R.string.manual_temporarily_unsupported_suffix))
        }
        if (isEmpty()) {
            append(text.get(R.string.manual_controls_unavailable))
        }
    }
}

private fun ManualCaptureParams.localizedCompactSummary(text: AppTextResolver): String {
    return buildString {
        append(text.get(R.string.label_raw))
        append(" ")
        append(text.onOff(rawEnabled))
        append(" | ")
        append(text.get(R.string.label_iso))
        append(" ")
        append(iso?.toString() ?: text.get(R.string.label_auto))
        append(" | ")
        append(text.get(R.string.label_shutter))
        append(" ")
        append(shutterSpeedMillis?.let { "${it}ms" } ?: text.get(R.string.label_auto))
        append(" | ")
        append(text.get(R.string.label_wb))
        append(" ")
        append(whiteBalanceKelvin?.let { "${it}K" } ?: text.get(R.string.label_auto))
    }
}

private fun onOffLabel(enabled: Boolean, text: AppTextResolver): String = text.onOff(enabled)

private val MANUAL_ISO_OPTIONS = listOf<Int?>(null, 100, 200, 320, 640, 800, 1600)
private val MANUAL_SHUTTER_OPTIONS = listOf<Long?>(null, 8L, 16L, 33L, 50L, 80L, 125L, 250L, 500L)
private val MANUAL_EXPOSURE_OPTIONS = listOf<Int?>(null, -2, -1, 0, 1, 2)
private val MANUAL_FOCUS_OPTIONS = listOf<Float?>(null, 0.5f, 1.0f, 2.0f, 4.0f)
private val MANUAL_APERTURE_OPTIONS = listOf<Float?>(null, 1.4f, 1.8f, 2.2f, 2.8f, 4.0f)
private val MANUAL_WHITE_BALANCE_OPTIONS = listOf<Int?>(null, 3200, 4300, 4800, 5600, 6500)

private fun manualEvLabel(steps: Int): String {
    return when {
        steps > 0 -> "+$steps"
        else -> steps.toString()
    }
}

private fun manualOneDecimal(value: Float): String = String.format(Locale.US, "%.1f", value)
