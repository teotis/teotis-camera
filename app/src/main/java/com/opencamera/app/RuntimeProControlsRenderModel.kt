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
    val whiteBalanceControl: FeatureCatalogControlRenderModel,
    val primaryControls: List<RuntimeProControlSpec>
)

internal enum class RuntimeProControlId {
    FORMAT,
    ISO,
    SHUTTER,
    EV,
    FOCUS,
    APERTURE,
    WHITE_BALANCE
}

internal data class RuntimeProScaleOption(
    val label: String,
    val action: FeatureCatalogAction?,
    val isSelected: Boolean
)

internal data class RuntimeProControlSpec(
    val id: RuntimeProControlId,
    val railLabel: String,
    val value: String,
    val availability: SettingsControlAvailability,
    val supportLabel: String?,
    val isSelectable: Boolean,
    val options: List<RuntimeProScaleOption>
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
    val rawControl = FeatureCatalogControlRenderModel(
        label = text.get(R.string.label_capture_format),
        value = if (manualCapabilities.raw == ManualControlSupport.APPLY && draft.rawEnabled) {
            text.get(R.string.capture_format_raw_jpg)
        } else {
            text.get(R.string.capture_format_jpg)
        },
        availability = manualCapabilities.raw.toSettingsAvailability(),
        availabilityLabel = text.availabilityLabel(manualCapabilities.raw.toSettingsAvailability()),
        supportLabel = if (manualCapabilities.raw == ManualControlSupport.APPLY) {
            manualCapabilities.raw.manualSupportLabel(text)
        } else {
            text.get(R.string.raw_output_unavailable_jpg)
        },
        nextAction = FeatureCatalogAction.UpdateManualRawEnabled(!draft.rawEnabled)
            .takeIf {
                isVisible && editingEnabled && manualCapabilities.raw == ManualControlSupport.APPLY
            },
        isToggleOn = draft.rawEnabled.takeIf {
            manualCapabilities.raw == ManualControlSupport.APPLY
        }
    )
    val isoControl = FeatureCatalogControlRenderModel(
        label = text.get(R.string.label_iso),
        value = draft.iso?.toString() ?: text.get(R.string.label_auto),
        availability = manualCapabilities.iso.toSettingsAvailability(),
        availabilityLabel = text.availabilityLabel(manualCapabilities.iso.toSettingsAvailability()),
        supportLabel = manualCapabilities.iso.manualSupportLabel(text),
        nextAction = FeatureCatalogAction.UpdateManualIso(
            nextNullableListValue(draft.iso, MANUAL_ISO_OPTIONS)
        ).takeIf { isVisible && editingEnabled && manualCapabilities.iso == ManualControlSupport.APPLY }
    )
    val shutterControl = FeatureCatalogControlRenderModel(
        label = text.get(R.string.label_shutter),
        value = draft.shutterSpeedMillis?.let { "${it}ms" } ?: text.get(R.string.label_auto),
        availability = manualCapabilities.shutter.toSettingsAvailability(),
        availabilityLabel = text.availabilityLabel(manualCapabilities.shutter.toSettingsAvailability()),
        supportLabel = manualCapabilities.shutter.manualSupportLabel(text),
        nextAction = FeatureCatalogAction.UpdateManualShutterSpeedMillis(
            nextNullableListValue(draft.shutterSpeedMillis, MANUAL_SHUTTER_OPTIONS)
        ).takeIf { isVisible && editingEnabled && manualCapabilities.shutter == ManualControlSupport.APPLY }
    )
    val exposureControl = FeatureCatalogControlRenderModel(
        label = text.get(R.string.label_ev),
        value = draft.exposureCompensationSteps?.let(::manualEvLabel) ?: text.get(R.string.label_auto),
        availability = manualCapabilities.exposureCompensation.toSettingsAvailability(),
        availabilityLabel = text.availabilityLabel(manualCapabilities.exposureCompensation.toSettingsAvailability()),
        supportLabel = manualCapabilities.exposureCompensation.manualSupportLabel(text),
        nextAction = FeatureCatalogAction.UpdateManualExposureCompensationSteps(
            nextNullableListValue(draft.exposureCompensationSteps, MANUAL_EXPOSURE_OPTIONS)
        ).takeIf {
            isVisible && editingEnabled &&
                manualCapabilities.exposureCompensation == ManualControlSupport.APPLY
        }
    )
    val focusControl = FeatureCatalogControlRenderModel(
        label = text.get(R.string.label_focus),
        value = draft.focusDistanceDiopters?.let { String.format(Locale.US, "%.1fD", it) }
            ?: text.get(R.string.label_auto),
        availability = manualCapabilities.focusDistance.toSettingsAvailability(),
        availabilityLabel = text.availabilityLabel(manualCapabilities.focusDistance.toSettingsAvailability()),
        supportLabel = manualCapabilities.focusDistance.manualSupportLabel(text),
        nextAction = FeatureCatalogAction.UpdateManualFocusDistanceDiopters(
            nextNullableListValue(draft.focusDistanceDiopters, MANUAL_FOCUS_OPTIONS)
        ).takeIf {
            isVisible && editingEnabled && manualCapabilities.focusDistance == ManualControlSupport.APPLY
        }
    )
    val apertureControl = FeatureCatalogControlRenderModel(
        label = text.get(R.string.label_aperture),
        value = draft.apertureFNumber?.let { "f/${manualOneDecimal(it)}" } ?: text.get(R.string.label_auto),
        availability = manualCapabilities.aperture.toSettingsAvailability(),
        availabilityLabel = text.availabilityLabel(manualCapabilities.aperture.toSettingsAvailability()),
        supportLabel = manualCapabilities.aperture.manualSupportLabel(text),
        nextAction = FeatureCatalogAction.UpdateManualApertureFNumber(
            nextNullableListValue(draft.apertureFNumber, MANUAL_APERTURE_OPTIONS)
        ).takeIf { isVisible && editingEnabled && manualCapabilities.aperture == ManualControlSupport.APPLY }
    )
    val whiteBalanceControl = FeatureCatalogControlRenderModel(
        label = text.get(R.string.label_wb),
        value = draft.whiteBalanceKelvin?.let { "${it}K" } ?: text.get(R.string.label_auto),
        availability = manualCapabilities.whiteBalance.toSettingsAvailability(),
        availabilityLabel = text.availabilityLabel(manualCapabilities.whiteBalance.toSettingsAvailability()),
        supportLabel = manualCapabilities.whiteBalance.manualSupportLabel(text),
        nextAction = FeatureCatalogAction.UpdateManualWhiteBalanceKelvin(
            nextNullableListValue(draft.whiteBalanceKelvin, MANUAL_WHITE_BALANCE_OPTIONS)
        ).takeIf {
            isVisible && editingEnabled && manualCapabilities.whiteBalance == ManualControlSupport.APPLY
        }
    )
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
            append(
                draft.localizedCompactSummary(
                    text = text,
                    rawApplied = manualCapabilities.raw == ManualControlSupport.APPLY
                )
            )
            append(" | ")
            append(runtimeSupportLabel)
            if (!editingEnabled) {
                append(" ")
                append(text.get(R.string.pro_controls_finish_capture_hint))
            }
        },
        rawControl = rawControl,
        isoControl = isoControl,
        shutterControl = shutterControl,
        exposureControl = exposureControl,
        focusControl = focusControl,
        apertureControl = apertureControl,
        whiteBalanceControl = whiteBalanceControl,
        primaryControls = buildPrimaryControls(
            draft = draft,
            text = text,
            rawControl = rawControl,
            isoControl = isoControl,
            shutterControl = shutterControl,
            exposureControl = exposureControl,
            focusControl = focusControl,
            apertureControl = apertureControl,
            whiteBalanceControl = whiteBalanceControl,
            rawSupport = manualCapabilities.raw,
            editingEnabled = isVisible && editingEnabled
        )
    )
}

private fun buildPrimaryControls(
    draft: ManualCaptureParams,
    text: AppTextResolver,
    rawControl: FeatureCatalogControlRenderModel,
    isoControl: FeatureCatalogControlRenderModel,
    shutterControl: FeatureCatalogControlRenderModel,
    exposureControl: FeatureCatalogControlRenderModel,
    focusControl: FeatureCatalogControlRenderModel,
    apertureControl: FeatureCatalogControlRenderModel,
    whiteBalanceControl: FeatureCatalogControlRenderModel,
    rawSupport: ManualControlSupport,
    editingEnabled: Boolean
): List<RuntimeProControlSpec> = listOf(
    RuntimeProControlSpec(
        id = RuntimeProControlId.FORMAT,
        railLabel = text.get(R.string.capture_format_jpg),
        value = rawControl.value,
        availability = rawControl.availability,
        supportLabel = rawControl.supportLabel,
        isSelectable = editingEnabled && rawSupport == ManualControlSupport.APPLY,
        options = buildList {
            add(
                RuntimeProScaleOption(
                    label = text.get(R.string.capture_format_jpg),
                    action = FeatureCatalogAction.UpdateManualRawEnabled(false)
                        .takeIf { editingEnabled && rawSupport == ManualControlSupport.APPLY },
                    isSelected = !draft.rawEnabled || rawSupport != ManualControlSupport.APPLY
                )
            )
            if (rawSupport == ManualControlSupport.APPLY) {
                add(
                    RuntimeProScaleOption(
                        label = text.get(R.string.capture_format_raw_jpg),
                        action = FeatureCatalogAction.UpdateManualRawEnabled(true)
                            .takeIf { editingEnabled },
                        isSelected = draft.rawEnabled
                    )
                )
            }
        }
    ),
    RuntimeProControlSpec(
        id = RuntimeProControlId.ISO,
        railLabel = "ISO",
        value = isoControl.value,
        availability = isoControl.availability,
        supportLabel = isoControl.supportLabel,
        isSelectable = editingEnabled && isoControl.availability == SettingsControlAvailability.SUPPORTED,
        options = MANUAL_ISO_OPTIONS.map { value ->
            RuntimeProScaleOption(
                label = value?.toString() ?: text.get(R.string.label_auto).uppercase(Locale.ROOT),
                action = FeatureCatalogAction.UpdateManualIso(value).takeIf {
                    editingEnabled && isoControl.availability == SettingsControlAvailability.SUPPORTED
                },
                isSelected = draft.iso == value
            )
        }
    ),
    RuntimeProControlSpec(
        id = RuntimeProControlId.SHUTTER,
        railLabel = "S",
        value = shutterControl.value,
        availability = shutterControl.availability,
        supportLabel = shutterControl.supportLabel,
        isSelectable = editingEnabled && shutterControl.availability == SettingsControlAvailability.SUPPORTED,
        options = MANUAL_SHUTTER_OPTIONS.map { value ->
            RuntimeProScaleOption(
                label = value?.let(::manualShutterScaleLabel)
                    ?: text.get(R.string.label_auto).uppercase(Locale.ROOT),
                action = FeatureCatalogAction.UpdateManualShutterSpeedMillis(value)
                    .takeIf {
                        editingEnabled &&
                            shutterControl.availability == SettingsControlAvailability.SUPPORTED
                    },
                isSelected = draft.shutterSpeedMillis == value
            )
        }
    ),
    RuntimeProControlSpec(
        id = RuntimeProControlId.EV,
        railLabel = "EV",
        value = exposureControl.value,
        availability = exposureControl.availability,
        supportLabel = exposureControl.supportLabel,
        isSelectable = editingEnabled && exposureControl.availability == SettingsControlAvailability.SUPPORTED,
        options = MANUAL_EXPOSURE_OPTIONS.map { value ->
            RuntimeProScaleOption(
                label = value?.let(::manualEvLabel)
                    ?: text.get(R.string.label_auto).uppercase(Locale.ROOT),
                action = FeatureCatalogAction.UpdateManualExposureCompensationSteps(value)
                    .takeIf {
                        editingEnabled &&
                            exposureControl.availability == SettingsControlAvailability.SUPPORTED
                    },
                isSelected = draft.exposureCompensationSteps == value
            )
        }
    ),
    RuntimeProControlSpec(
        id = RuntimeProControlId.FOCUS,
        railLabel = "AF",
        value = focusControl.value,
        availability = focusControl.availability,
        supportLabel = focusControl.supportLabel,
        isSelectable = editingEnabled && focusControl.availability == SettingsControlAvailability.SUPPORTED,
        options = MANUAL_FOCUS_OPTIONS.map { value ->
            RuntimeProScaleOption(
                label = value?.let { manualOneDecimal(it) }
                    ?: text.get(R.string.label_auto).uppercase(Locale.ROOT),
                action = FeatureCatalogAction.UpdateManualFocusDistanceDiopters(value)
                    .takeIf {
                        editingEnabled &&
                            focusControl.availability == SettingsControlAvailability.SUPPORTED
                    },
                isSelected = draft.focusDistanceDiopters == value
            )
        }
    ),
    RuntimeProControlSpec(
        id = RuntimeProControlId.APERTURE,
        railLabel = "F",
        value = apertureControl.value,
        availability = apertureControl.availability,
        supportLabel = apertureControl.supportLabel,
        isSelectable = editingEnabled && apertureControl.availability == SettingsControlAvailability.SUPPORTED,
        options = MANUAL_APERTURE_OPTIONS.map { value ->
            RuntimeProScaleOption(
                label = value?.let { "f/${manualOneDecimal(it)}" }
                    ?: text.get(R.string.label_auto).uppercase(Locale.ROOT),
                action = FeatureCatalogAction.UpdateManualApertureFNumber(value).takeIf {
                    editingEnabled && apertureControl.availability == SettingsControlAvailability.SUPPORTED
                },
                isSelected = draft.apertureFNumber == value
            )
        }
    ),
    RuntimeProControlSpec(
        id = RuntimeProControlId.WHITE_BALANCE,
        railLabel = "WB",
        value = whiteBalanceControl.value,
        availability = whiteBalanceControl.availability,
        supportLabel = whiteBalanceControl.supportLabel,
        isSelectable = editingEnabled && whiteBalanceControl.availability == SettingsControlAvailability.SUPPORTED,
        options = MANUAL_WHITE_BALANCE_OPTIONS.map { value ->
            RuntimeProScaleOption(
                label = value?.let { "${it}K" }
                    ?: text.get(R.string.label_auto).uppercase(Locale.ROOT),
                action = FeatureCatalogAction.UpdateManualWhiteBalanceKelvin(value).takeIf {
                    editingEnabled && whiteBalanceControl.availability == SettingsControlAvailability.SUPPORTED
                },
                isSelected = draft.whiteBalanceKelvin == value
            )
        }
    )
)

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

private fun ManualCaptureParams.localizedCompactSummary(
    text: AppTextResolver,
    rawApplied: Boolean
): String {
    return buildString {
        append(text.get(R.string.label_capture_format))
        append(" ")
        append(
            if (rawEnabled && rawApplied) text.get(R.string.capture_format_raw_jpg)
            else text.get(R.string.capture_format_jpg)
        )
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

private fun manualShutterScaleLabel(milliseconds: Long): String {
    if (milliseconds >= 1000L) return "${milliseconds / 1000}s"
    val denominator = (1000.0 / milliseconds.toDouble()).toInt().coerceAtLeast(1)
    return "1/$denominator"
}

private fun manualOneDecimal(value: Float): String = String.format(Locale.US, "%.1f", value)
