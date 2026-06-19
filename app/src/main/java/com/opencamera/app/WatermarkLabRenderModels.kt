package com.opencamera.app

import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.WatermarkFrameBackground
import com.opencamera.core.settings.WatermarkTemplate
import com.opencamera.core.settings.WatermarkTextOpacity
import com.opencamera.core.settings.WatermarkTextPlacement
import com.opencamera.core.settings.WatermarkTextScale
import com.opencamera.core.settings.watermarkStyleFor
import com.opencamera.core.session.SessionState
import com.opencamera.app.i18n.AppTextResolver

internal data class WatermarkLabTemplateItemRenderModel(
    val templateId: String,
    val title: String,
    val supportingText: String,
    val isSelected: Boolean,
    val useAction: PersistedSettingsAction?,
    val editButtonLabel: String? = null
)

internal data class WatermarkLabSelectorRenderModel(
    override val headline: String,
    override val supportingText: String,
    override val heroSummary: String,
    override val editingEnabled: Boolean,
    override val editingHint: String,
    val items: List<WatermarkLabTemplateItemRenderModel>,
    val footer: String
) : EditableSettingsPageRenderModel

internal data class WatermarkLabDetailRenderModel(
    override val headline: String,
    override val supportingText: String,
    override val heroSummary: String,
    val templateId: String,
    override val editingEnabled: Boolean,
    override val editingHint: String,
    val placementControl: SettingsControlRenderModel,
    val textScaleControl: SettingsControlRenderModel,
    val textOpacityControl: SettingsControlRenderModel,
    val frameBackgroundControl: SettingsControlRenderModel?,
    val footer: String
) : EditableSettingsPageRenderModel

internal fun watermarkLabSelectorRenderModel(
    state: SessionState,
    text: AppTextResolver
): WatermarkLabSelectorRenderModel {
    val settings = state.settings.persisted
    val catalog = state.settings.catalog
    val editingEnabled = settingsPageEditingEnabled(state)
    val supportsStillCapture = state.activeDeviceCapabilities.supportsStillCapture
    return WatermarkLabSelectorRenderModel(
        headline = text.get(R.string.label_watermark_lab),
        supportingText = text.get(R.string.watermark_selector_supporting),
        heroSummary = "",
        editingEnabled = editingEnabled,
        editingHint = if (editingEnabled) {
            text.get(R.string.watermark_selector_editing_enabled)
        } else {
            text.get(R.string.watermark_selector_editing_disabled)
        },
        items = catalog.watermarkTemplates.map { template ->
            val style = settings.photo.watermarkStyleFor(template.id)
            val isSelected = template.id == settings.photo.defaultWatermarkTemplateId
            val templateLabel = template.localizedLabel(text)
            WatermarkLabTemplateItemRenderModel(
                templateId = template.id,
                title = templateLabel,
                supportingText = buildString {
                    append(template.kindLabel(text))
                    append(" | ")
                    append(text.get(R.string.label_tokens))
                    append(" ")
                    append(template.tokenKeys.prettyWatermarkTokens(text))
                    append(" | ")
                    append(text.get(R.string.watermark_attr_placement_prefix))
                    append(text.watermarkPlacementLabel(style.textPlacement))
                    append(" | ")
                    append(text.get(R.string.watermark_attr_scale_prefix))
                    append(text.watermarkTextScaleLabel(style.textScale))
                    append(" | ")
                    append(text.get(R.string.watermark_attr_opacity_prefix))
                    append(text.watermarkTextOpacityLabel(style.textOpacity))
                    if (template.supportsFrameBorder) {
                        append(" | ")
                        append(text.get(R.string.watermark_attr_background_prefix))
                        append(text.watermarkFrameBackgroundLabel(style.frameBackground))
                    }
                    if (isSelected) {
                        append(text.get(R.string.watermark_selector_current_default))
                    }
                },
                isSelected = isSelected,
                useAction = if (supportsStillCapture && !isSelected) {
                    PersistedSettingsAction.UpdatePhotoWatermarkTemplate(template.id)
                } else {
                    null
                },
                editButtonLabel = if (supportsStillCapture) {
                    text.get(R.string.button_watermark_style_short)
                } else {
                    null
                }
            )
        },
        footer = if (supportsStillCapture) {
            text.get(R.string.watermark_selector_footer_supported)
        } else {
            text.get(R.string.watermark_selector_footer_unsupported)
        }
    )
}

internal fun watermarkLabDetailRenderModel(
    state: SessionState,
    templateId: String,
    text: AppTextResolver
): WatermarkLabDetailRenderModel {
    val settings = state.settings.persisted
    val catalog = state.settings.catalog
    val editingEnabled = settingsPageEditingEnabled(state)
    val supportsStillCapture = state.activeDeviceCapabilities.supportsStillCapture
    val template = catalog.watermarkTemplateOrNull(templateId)
        ?: catalog.watermarkTemplateOrNull(settings.photo.defaultWatermarkTemplateId)
        ?: catalog.watermarkTemplates.firstOrNull()
        ?: WatermarkTemplate(id = templateId, label = templateId)
    val style = settings.photo.watermarkStyleFor(template.id)
    val allowedPlacements = template.allowedPlacements
        .takeIf { it.isNotEmpty() }
        ?: WatermarkTextPlacement.entries.toSet()
    val allowedBackgrounds = template.allowedFrameBackgrounds
        .takeIf { it.isNotEmpty() }
        ?: WatermarkFrameBackground.entries.toSet()
    val controlAvailability = if (supportsStillCapture) {
        SettingsControlAvailability.SUPPORTED
        } else {
            SettingsControlAvailability.UNSUPPORTED
        }
    return WatermarkLabDetailRenderModel(
        headline = template.localizedLabel(text),
        supportingText = if (template.id == settings.photo.defaultWatermarkTemplateId) {
            text.get(R.string.watermark_detail_supporting_selected)
        } else {
            text.get(R.string.watermark_detail_supporting_not_selected)
        },
        heroSummary = "",
        templateId = template.id,
        editingEnabled = editingEnabled,
        editingHint = if (editingEnabled) {
            text.get(R.string.watermark_detail_editing_enabled)
        } else {
            text.get(R.string.watermark_detail_editing_disabled)
        },
        placementControl = SettingsControlRenderModel(
            label = text.get(R.string.label_text_placement),
            value = text.watermarkPlacementLabel(style.textPlacement),
            availability = controlAvailability,
            availabilityLabel = text.availabilityLabel(controlAvailability),
            supportLabel = if (supportsStillCapture) {
                text.placementsCount(allowedPlacements.size)
            } else {
                text.get(R.string.error_still_capture_unavailable)
            },
            nextAction = if (supportsStillCapture) {
                PersistedSettingsAction.UpdateWatermarkTextPlacement(
                    templateId = template.id,
                    placement = nextListValue(style.textPlacement, allowedPlacements.toList())
                )
            } else {
                null
            }
        ),
        textScaleControl = SettingsControlRenderModel(
            label = text.get(R.string.label_text_scale),
            value = text.watermarkTextScaleLabel(style.textScale),
            availability = controlAvailability,
            availabilityLabel = text.availabilityLabel(controlAvailability),
            supportLabel = if (supportsStillCapture) {
                text.stepsCount(WatermarkTextScale.entries.size)
            } else {
                text.get(R.string.error_still_capture_unavailable)
            },
            nextAction = if (supportsStillCapture) {
                PersistedSettingsAction.UpdateWatermarkTextScale(
                    templateId = template.id,
                    scale = nextListValue(style.textScale, WatermarkTextScale.entries.toList())
                )
            } else {
                null
            }
        ),
        textOpacityControl = SettingsControlRenderModel(
            label = text.get(R.string.label_text_opacity),
            value = text.watermarkTextOpacityLabel(style.textOpacity),
            availability = controlAvailability,
            availabilityLabel = text.availabilityLabel(controlAvailability),
            supportLabel = if (supportsStillCapture) {
                text.stepsCount(WatermarkTextOpacity.entries.size)
            } else {
                text.get(R.string.error_still_capture_unavailable)
            },
            nextAction = if (supportsStillCapture) {
                PersistedSettingsAction.UpdateWatermarkTextOpacity(
                    templateId = template.id,
                    opacity = nextListValue(style.textOpacity, WatermarkTextOpacity.entries.toList())
                )
            } else {
                null
            }
        ),
        frameBackgroundControl = if (template.supportsFrameBorder) {
            SettingsControlRenderModel(
                label = text.get(R.string.label_frame_background),
                value = text.watermarkFrameBackgroundLabel(style.frameBackground),
                availability = controlAvailability,
                availabilityLabel = text.availabilityLabel(controlAvailability),
                supportLabel = if (supportsStillCapture) {
                    text.moodsCount(allowedBackgrounds.size)
                } else {
                    text.get(R.string.error_still_capture_unavailable)
                },
                nextAction = if (supportsStillCapture) {
                    PersistedSettingsAction.UpdateWatermarkFrameBackground(
                        templateId = template.id,
                        background = nextListValue(
                            style.frameBackground,
                            allowedBackgrounds.toList()
                        )
                    )
                } else {
                    null
                }
            )
        } else {
            null
        },
        footer = buildString {
            append(text.get(R.string.watermark_detail_tokens_prefix))
            append(template.tokenKeys.prettyWatermarkTokens(text))
            append(". ")
            append(
                when {
                    template.id == "pure-text" -> text.get(R.string.watermark_detail_footer_pure_text)
                    template.supportsFrameBorder -> text.get(R.string.watermark_detail_footer_frame)
                    else -> text.get(R.string.watermark_detail_footer_overlay)
                }
            )
        }
    )
}

internal fun WatermarkTemplate.localizedLabel(text: AppTextResolver): String {
    return when (id) {
        "classic-overlay" -> text.get(R.string.watermark_template_classic_overlay)
        "travel-polaroid" -> text.get(R.string.watermark_template_travel_polaroid)
        "retro-frame" -> text.get(R.string.watermark_template_retro_frame)
        "pure-text" -> text.get(R.string.watermark_template_pure_text)
        "blur-four-border" -> text.get(R.string.watermark_template_blur_four_border)
        "professional-bottom-bar" -> text.get(R.string.watermark_template_professional_bottom_bar)
        "night-street" -> text.get(R.string.watermark_template_night_street)
        else -> label
    }
}

private fun WatermarkTemplate.kindLabel(text: AppTextResolver): String {
    return when (id) {
        "pure-text" -> text.get(R.string.watermark_template_pure_text)
        "blur-four-border" -> text.get(R.string.watermark_template_blur_four_border)
        else -> if (supportsFrameBorder) {
            text.get(R.string.watermark_template_expanded_frame)
        } else {
            text.get(R.string.watermark_template_classic_overlay)
        }
    }
}

internal fun Set<String>.prettyWatermarkTokens(text: AppTextResolver): String {
    return sorted().joinToString(separator = ", ") { token ->
        when (token) {
            "camera-params" -> text.get(R.string.watermark_token_camera_params)
            "datetime" -> text.get(R.string.watermark_token_datetime)
            "location" -> text.get(R.string.watermark_token_location)
            "model" -> text.get(R.string.watermark_token_model)
            else -> token.replace('-', ' ').replaceFirstChar(Char::titlecase)
        }
    }
}

internal fun com.opencamera.core.settings.FeatureCatalog.watermarkTemplateOrNull(
    templateId: String?
): WatermarkTemplate? {
    return watermarkTemplates.firstOrNull { template -> template.id == templateId }
}
