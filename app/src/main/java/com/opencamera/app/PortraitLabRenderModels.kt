package com.opencamera.app

import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.PortraitBeautyPreset
import com.opencamera.core.settings.PortraitBeautyStrength
import com.opencamera.core.settings.PortraitBokehEffect
import com.opencamera.core.settings.PortraitProfile
import com.opencamera.core.session.SessionState
import com.opencamera.app.i18n.AppTextResolver

internal data class PortraitLabPageRenderModel(
    override val headline: String,
    override val supportingText: String,
    override val heroSummary: String,
    override val editingEnabled: Boolean,
    override val editingHint: String,
    val profileControl: SettingsControlRenderModel,
    val beautyPresetControl: SettingsControlRenderModel,
    val beautyStrengthControl: SettingsControlRenderModel,
    val bokehEffectControl: SettingsControlRenderModel,
    val depthStrength: Int,
    val depthStrengthLabel: String,
    val updateDepthStrengthAction: PersistedSettingsAction.UpdatePortraitDepthStrength?,
    val footer: String
) : EditableSettingsPageRenderModel

internal fun portraitLabPageRenderModel(
    state: SessionState,
    text: AppTextResolver
): PortraitLabPageRenderModel {
    val settings = state.settings.persisted
    val editingEnabled = settingsPageEditingEnabled(state)
    val supportsStillCapture = state.activeDeviceCapabilities.supportsStillCapture
    val availability = if (supportsStillCapture) {
        SettingsControlAvailability.SUPPORTED
    } else {
        SettingsControlAvailability.UNSUPPORTED
    }
    return PortraitLabPageRenderModel(
        headline = text.get(R.string.button_portrait_mode),
        supportingText = text.get(R.string.portrait_lab_supporting),
        heroSummary = "",
        editingEnabled = editingEnabled,
        editingHint = if (editingEnabled) {
            text.get(R.string.portrait_lab_editing_enabled)
        } else {
            text.get(R.string.portrait_lab_editing_disabled)
        },
        profileControl = SettingsControlRenderModel(
            label = text.get(R.string.label_portrait_profile),
            value = text.portraitProfileEnumLabel(settings.photo.portraitProfile),
            availability = availability,
            availabilityLabel = text.availabilityLabel(availability),
            supportLabel = if (supportsStillCapture) {
                text.productProfilesCount(PortraitProfile.entries.size)
            } else {
                text.get(R.string.error_still_capture_unavailable)
            },
            nextAction = if (supportsStillCapture) {
                PersistedSettingsAction.UpdatePortraitProfile(
                    nextListValue(settings.photo.portraitProfile, PortraitProfile.entries.toList())
                )
            } else {
                null
            }
        ),
        beautyPresetControl = SettingsControlRenderModel(
            label = text.get(R.string.label_beauty_preset),
            value = text.portraitBeautyPresetLabel(settings.photo.portraitBeautyPreset),
            availability = availability,
            availabilityLabel = text.availabilityLabel(availability),
            supportLabel = if (supportsStillCapture) {
                text.plansCount(PortraitBeautyPreset.entries.size)
            } else {
                text.get(R.string.error_still_capture_unavailable)
            },
            nextAction = if (supportsStillCapture) {
                PersistedSettingsAction.UpdatePortraitBeautyPreset(
                    nextListValue(
                        settings.photo.portraitBeautyPreset,
                        PortraitBeautyPreset.entries.toList()
                    )
                )
            } else {
                null
            }
        ),
        beautyStrengthControl = SettingsControlRenderModel(
            label = text.get(R.string.label_beauty_strength),
            value = text.portraitBeautyStrengthLabel(settings.photo.portraitBeautyStrength),
            availability = availability,
            availabilityLabel = text.availabilityLabel(availability),
            supportLabel = if (supportsStillCapture) {
                text.levelsCount(PortraitBeautyStrength.entries.size)
            } else {
                text.get(R.string.error_still_capture_unavailable)
            },
            nextAction = if (supportsStillCapture) {
                PersistedSettingsAction.UpdatePortraitBeautyStrength(
                    nextListValue(
                        settings.photo.portraitBeautyStrength,
                        PortraitBeautyStrength.entries.toList()
                    )
                )
            } else {
                null
            }
        ),
        bokehEffectControl = SettingsControlRenderModel(
            label = text.get(R.string.label_bokeh_effect),
            value = text.portraitBokehEffectLabel(settings.photo.portraitBokehEffect),
            availability = availability,
            availabilityLabel = text.availabilityLabel(availability),
            supportLabel = if (supportsStillCapture) {
                text.renderingFeelsCount(PortraitBokehEffect.entries.size)
            } else {
                text.get(R.string.error_still_capture_unavailable)
            },
            nextAction = if (supportsStillCapture) {
                PersistedSettingsAction.UpdatePortraitBokehEffect(
                    nextListValue(
                        settings.photo.portraitBokehEffect,
                        PortraitBokehEffect.entries.toList()
                    )
                )
            } else {
                null
            }
        ),
        depthStrength = settings.photo.portraitDepthStrength,
        depthStrengthLabel = "${settings.photo.portraitDepthStrength}%",
        updateDepthStrengthAction = if (supportsStillCapture && editingEnabled) {
            PersistedSettingsAction.UpdatePortraitDepthStrength(
                settings.photo.portraitDepthStrength
            )
        } else {
            null
        },
        footer = text.get(R.string.portrait_lab_footer)
    )
}
