package com.opencamera.app.i18n

import android.content.Context
import com.opencamera.app.R
import com.opencamera.app.SettingsControlAvailability
import com.opencamera.core.mode.ModeId
import com.opencamera.core.settings.AppLanguage
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.PersistedSettings

open class AppTextResolver(private val context: Context?) {
    private fun str(resId: Int, fallback: String): String =
        context?.getString(resId) ?: fallback

    open fun modeDisplayName(modeId: ModeId): String = when (modeId) {
        ModeId.PHOTO -> str(R.string.button_photo_mode, "Photo")
        ModeId.DOCUMENT -> str(R.string.button_document_mode, "Doc")
        ModeId.NIGHT -> str(R.string.button_night_mode, "Scenery")
        ModeId.HUMANISTIC -> str(R.string.button_humanistic_mode, "Human")
        ModeId.PORTRAIT -> str(R.string.button_portrait_mode, "Port")
        ModeId.PRO -> str(R.string.button_pro_mode, "Pro")
        ModeId.VIDEO -> str(R.string.button_video_mode, "Video")
    }

    open fun modeTrackLabel(modeId: ModeId): String = modeDisplayName(modeId)

    open fun gridModeLabel(value: CompositionGridMode): String = when (value) {
        CompositionGridMode.OFF -> str(R.string.grid_off, "Off")
        CompositionGridMode.RULE_OF_THIRDS -> str(R.string.grid_rule_of_thirds, "3x3")
        CompositionGridMode.GOLDEN_RATIO -> str(R.string.grid_golden_ratio, "Golden")
    }

    open fun countdownLabel(value: CountdownDuration): String = when (value) {
        CountdownDuration.OFF -> str(R.string.button_quick_timer, "Timer")
        CountdownDuration.SECONDS_3 -> "3s"
        CountdownDuration.SECONDS_5 -> "5s"
        CountdownDuration.SECONDS_10 -> "10s"
    }

    internal open fun availabilityLabel(value: SettingsControlAvailability): String = when (value) {
        SettingsControlAvailability.SUPPORTED -> str(R.string.availability_supported, "Supported")
        SettingsControlAvailability.DEGRADED -> str(R.string.availability_degraded, "Degraded")
        SettingsControlAvailability.UNSUPPORTED -> str(R.string.availability_unsupported, "Unsupported")
    }

    open fun onOff(enabled: Boolean): String = if (enabled) {
        str(R.string.label_on, "On")
    } else {
        str(R.string.label_off, "Off")
    }

    open fun shutterShort(): String = str(R.string.button_shutter_short, "Shutter")
    open fun ratio(): String = str(R.string.button_quick_ratio, "Ratio")
    open fun filter(): String = str(R.string.button_filter_entry, "Filter")
    open fun portrait(): String = str(R.string.button_portrait_mode, "Portrait")
    open fun watermark(): String = str(R.string.label_watermark_lab, "Watermark")
    open fun livePhoto(): String = str(R.string.button_quick_live, "Live")
    open fun timer(): String = str(R.string.button_quick_timer, "Timer")
    open fun flash(): String = str(R.string.button_quick_flash, "Flash")
    open fun settingsEntry(): String = str(R.string.button_settings_entry, "Settings")
    open fun filterLab(): String = str(R.string.button_filter_entry, "Filter Lab")
    open fun portraitLab(): String = str(R.string.button_portrait_mode, "Portrait Lab")
    open fun watermarkLab(): String = str(R.string.label_watermark_lab, "Watermark Lab")
    open fun devEntry(): String = str(R.string.button_dev_entry, "DEV")
    open fun devRestart(): String = str(R.string.button_dev_restart_session, "Restart Session")
    open fun devClose(): String = str(R.string.dev_close, "Close")
    open fun devExportLog(): String = str(R.string.dev_export_log, "Export Log")
    open fun devTabKey(): String = str(R.string.dev_tab_key, "Summary")
    open fun devTabCore(): String = str(R.string.dev_tab_core, "Pipeline")
    open fun devTabError(): String = str(R.string.dev_tab_error, "Error")
    open fun devTabAll(): String = str(R.string.dev_tab_all, "All")
    open fun quickLauncher(): String = str(R.string.button_quick_launcher, "Quick")
    open fun quickGrid(): String = str(R.string.button_quick_grid, "Grid")
    open fun quickQuality(): String = str(R.string.button_quick_flash, "Quality")
    open fun quickBrightness(): String = str(R.string.button_quick_brightness, "Brightness")
    open fun quickBrightnessNa(): String = str(R.string.button_quick_brightness_na, "N/A")
    open fun quickLive(): String = str(R.string.button_quick_live, "Live")
    open fun quickTimer(): String = str(R.string.button_quick_timer, "Timer")
    open fun moreControls(): String = str(R.string.button_more_controls, "More")
    open fun stopSession(): String = str(R.string.button_stop_session, "Stop Session")
    open fun restartSession(): String = str(R.string.button_restart_session, "Restart Session")
    open fun closeSettings(): String = str(R.string.button_settings_close, "Close Settings")
    open fun closeFilter(): String = str(R.string.button_filter_close, "Close Filter Lab")
    open fun permissionPending(): String = str(R.string.permission_pending, "Camera permission is required before preview can start.")
    open fun permissionGranted(): String = str(R.string.permission_granted, "Camera permission granted. Preview is ready.")
    open fun permissionCameraOnly(): String = str(R.string.permission_camera_only, "Camera granted. Video recording will be silent until microphone permission is granted.")
    open fun permissionDenied(): String = str(R.string.permission_denied, "Camera permission denied. Tap shutter to request it again.")
    open fun permissionPermanentlyDenied(): String = str(R.string.permission_permanently_denied, "Camera permission permanently denied. Please enable it in system settings.")
    open fun permissionOpenSettings(): String = str(R.string.permission_open_settings, "Open Settings")
    open fun outputWaiting(): String = str(R.string.output_waiting, "No photo captured yet.")
    open fun outputSavedPrefix(): String = str(R.string.output_saved_prefix, "Last photo:")
    open fun outputVideoPrefix(): String = str(R.string.output_video_prefix, "Last video:")
    open fun outputLivePrefix(): String = str(R.string.output_live_prefix, "Last Live photo:")
    open fun outputErrorPrefix(): String = str(R.string.output_error_prefix, "Camera issue:")
    open fun outputPreviewPrefix(): String = str(R.string.output_preview_prefix, "Preview thumbnail:")
    open fun settingsSectionCommon(): String = str(R.string.settings_section_common, "General")
    open fun settingsSectionPhoto(): String = str(R.string.settings_section_photo, "Photo")
    open fun settingsSectionVideo(): String = str(R.string.settings_section_video, "Video")
    open fun switchLens(): String = str(R.string.button_switch_lens, "Switch Lens")
    open fun zoomPrefix(): String = str(R.string.button_zoom_prefix, "Zoom")
    open fun zoomUnavailable(): String = str(R.string.button_zoom_unavailable, "Zoom N/A")
    open fun stillQuality(): String = str(R.string.button_still_quality, "Still Quality")
    open fun stillFast(): String = str(R.string.button_still_fast, "Still Fast")
    open fun stillMax(): String = str(R.string.button_still_max, "Still Max")
    open fun stillQualityUnavailable(): String = str(R.string.button_still_quality_unavailable, "Still N/A")
    open fun stillResolution(): String = str(R.string.button_still_resolution, "Still Size")
    open fun still12Mp(): String = str(R.string.button_still_12mp, "Still 12MP")
    open fun still8Mp(): String = str(R.string.button_still_8mp, "Still 8MP")
    open fun still2Mp(): String = str(R.string.button_still_2mp, "Still 2MP")
    open fun stillResolutionUnavailable(): String = str(R.string.button_still_resolution_unavailable, "Size N/A")
    open fun switchToFront(): String = str(R.string.button_switch_to_front, "Switch to Front")
    open fun switchToBack(): String = str(R.string.button_switch_to_back, "Switch to Back")
    open fun singleLens(): String = str(R.string.button_single_lens, "Single Lens")
    open fun tone(): String = str(R.string.label_tone, "Tone")
    open fun styleEntry(): String = str(R.string.button_palette_entry, "Lens")
    open fun colorLabEntry(): String = str(R.string.button_color_lab_entry, "Color Lab")
    open fun stylePanelTitle(): String = str(R.string.style_panel_title, "Style")
    open fun colorLabPanelTitle(): String = str(R.string.color_lab_panel_title, "Color Lab")
    open fun useThisStyle(): String = str(R.string.button_use_this_style, "Use Style")
    open fun paletteSectionTitle(): String = str(R.string.filter_section_palette, "Palette")

    // Editing hints
    open fun lensLabEditingEnabled(): String =
        str(R.string.lens_lab_editing_enabled, "Changes save instantly and refresh the active mode defaults.")
    open fun lensLabEditingDisabled(): String =
        str(R.string.lens_lab_editing_disabled, "Finish the current capture before changing saved defaults.")
    open fun portraitLabEditingEnabled(): String =
        str(R.string.portrait_lab_editing_enabled, "Portrait defaults save instantly and apply to the next portrait capture metadata and lightweight render pass.")
    open fun portraitLabEditingDisabled(): String =
        str(R.string.portrait_lab_editing_disabled, "Finish the current capture before changing portrait product defaults.")
    open fun watermarkSelectorEditingEnabled(): String =
        str(R.string.watermark_selector_editing_enabled, "Default template changes save instantly. Each template keeps its own placement, scale, opacity, and frame background preset.")
    open fun watermarkSelectorEditingDisabled(): String =
        str(R.string.watermark_selector_editing_disabled, "Finish the current capture before changing watermark defaults.")
    open fun watermarkDetailEditingEnabled(): String =
        str(R.string.watermark_detail_editing_enabled, "Template-specific styles save instantly and stay attached to this watermark preset.")
    open fun watermarkDetailEditingDisabled(): String =
        str(R.string.watermark_detail_editing_disabled, "Finish the current capture before changing watermark styles.")
    open fun filterLabEditingEnabled(): String =
        str(R.string.filter_lab_editing_enabled, "Selected family defaults save instantly and refresh the active mode when relevant.")
    open fun filterLabEditingDisabled(): String =
        str(R.string.filter_lab_editing_disabled, "Finish the current capture before changing filter defaults.")
    open fun statusCustomBadge(): String =
        str(R.string.status_custom_badge, " | Custom")

    // Filter Lab page strings
    open fun filterLabSupportingText(): String =
        str(R.string.filter_lab_supporting_text, "Independent filter panel for mode defaults. Import and export stay deferred in this closure; selected looks can be adjusted or saved as custom.")
    open fun filterLabHeroSummary(familyLabel: String, filterLabel: String, count: Int): String =
        String.format(str(R.string.filter_lab_hero_summary, "%s default %s • %d looks staged"), familyLabel, filterLabel, count)
    open fun filterLabCurrentDefault(filterLabel: String): String =
        String.format(str(R.string.filter_lab_current_default, "Current default %s"), filterLabel)
    open fun filterLabSelectedDefault(): String =
        str(R.string.filter_lab_selected_default, " | Selected default")
    open fun filterLabLightPaletteHint(): String =
        str(R.string.filter_lab_light_palette_hint, "Horizontal swipe for color, vertical swipe for tone.")

    open fun filterLabDragToCreateCustom(): String =
        str(R.string.filter_lab_drag_to_create_custom, "Drag palette to save as custom")

    open fun filterLabNextLook(familyLabel: String): String =
        String.format(str(R.string.filter_lab_next_look, "Next %s look"), familyLabel)
    open fun filterLabLooksDeferred(count: Int): String =
        String.format(str(R.string.filter_lab_looks_deferred, "%d looks | import/export deferred"), count)
    open fun filterLabFooter(): String =
        str(R.string.filter_lab_footer, "Independent Tone Lab prioritized. Panel adjustments and custom saves ongoing; import/export deferred.")
    open fun filterLabSaveCustomUnavailableProfile(): String =
        str(R.string.filter_lab_save_custom_unavailable_profile, "Unavailable • Missing source profile")
    open fun filterLabSaveCustomAlreadyCustom(): String =
        str(R.string.filter_lab_save_custom_already_custom, "Unavailable • Current default already custom")
    open fun filterLabSaveCustomReady(familyLabel: String): String =
        String.format(str(R.string.filter_lab_save_custom_ready, "Ready • Becomes the %s default"), familyLabel)

    // Filter light palette labels
    open fun filterLightPaletteColor(): String =
        str(R.string.filter_light_palette_color, "Color")
    open fun filterLightPaletteTone(): String =
        str(R.string.filter_light_palette_tone, "Tone")

    // Filter signed adjustment level labels
    open fun filterSignedOff(): String =
        str(R.string.filter_signed_off, "Off")
    open fun filterSignedWarm(): String =
        str(R.string.filter_signed_warm, "Warm")
    open fun filterSignedWarmPlus(): String =
        str(R.string.filter_signed_warm_plus, "Warm+")
    open fun filterSignedCool(): String =
        str(R.string.filter_signed_cool, "Cool")
    open fun filterSignedCoolPlus(): String =
        str(R.string.filter_signed_cool_plus, "Cool+")
    open fun filterSignedMagenta(): String =
        str(R.string.filter_signed_magenta, "Magenta")
    open fun filterSignedMagentaPlus(): String =
        str(R.string.filter_signed_magenta_plus, "Magenta+")
    open fun filterSignedGreen(): String =
        str(R.string.filter_signed_green, "Green")
    open fun filterSignedGreenPlus(): String =
        str(R.string.filter_signed_green_plus, "Green+")

    // Dev log title labels
    open fun devLogTitleKey(count: Int): String =
        String.format(str(R.string.dev_log_title_key, "Summary Log (%d)"), count)
    open fun devLogTitleCore(count: Int): String =
        String.format(str(R.string.dev_log_title_core, "Pipeline Log (%d)"), count)
    open fun devLogTitleError(count: Int): String =
        String.format(str(R.string.dev_log_title_error, "Error Log (%d)"), count)
    open fun devLogTitleAll(count: Int): String =
        String.format(str(R.string.dev_log_title_all, "All Events (%d)"), count)

    // Filter advanced control labels
    open fun filterCtrlExposure(): String = str(R.string.filter_ctrl_exposure, "Exposure")
    open fun filterCtrlSoftGlow(): String = str(R.string.filter_ctrl_soft_glow, "Soft Glow")
    open fun filterCtrlHalo(): String = str(R.string.filter_ctrl_halo, "Halo")
    open fun filterCtrlGrain(): String = str(R.string.filter_ctrl_grain, "Grain")
    open fun filterCtrlSharpness(): String = str(R.string.filter_ctrl_sharpness, "Sharpness")
    open fun filterCtrlVignette(): String = str(R.string.filter_ctrl_vignette, "Vignette")
    open fun filterCtrlHighlights(): String = str(R.string.filter_ctrl_highlights, "Highlights")
    open fun filterCtrlShadows(): String = str(R.string.filter_ctrl_shadows, "Shadows")
    open fun filterCtrlWarmBoost(): String = str(R.string.filter_ctrl_warm_boost, "Warm Boost")
    open fun filterCtrlCoolBoost(): String = str(R.string.filter_ctrl_cool_boost, "Cool Boost")
    open fun filterCtrlTempShift(): String = str(R.string.filter_ctrl_temp_shift, "Temp Shift")
    open fun filterCtrlTintShift(): String = str(R.string.filter_ctrl_tint_shift, "Tint Shift")

    // Filter family labels
    open fun filterFamilyPhoto(): String = str(R.string.filter_family_photo, "Photo")
    open fun filterFamilyHumanistic(): String = str(R.string.filter_family_humanistic, "Humanistic")
    open fun filterFamilyPortrait(): String = str(R.string.filter_family_portrait, "Portrait")
    open fun filterFamilyVideo(): String = str(R.string.filter_family_video, "Video")

    // Pro controls headings
    open fun proControlsScenery(): String = str(R.string.pro_controls_scenery, "Scenery Pro Controls")
    open fun proControlsPortrait(): String = str(R.string.pro_controls_portrait, "Portrait Pro Controls")
    open fun proControlsHumanistic(): String = str(R.string.pro_controls_humanistic, "Humanistic Pro Controls")
    open fun proControlsDefault(): String = str(R.string.pro_controls_default, "Pro Controls")

    // Settings section labels
    open fun compositionGridLabel(): String = str(R.string.label_composition_grid, "Composition grid")
    open fun shutterToneLabel(): String = str(R.string.label_shutter_tone, "Shutter tone")
    open fun selfieMirrorLabel(): String = str(R.string.label_selfie_mirror, "Selfie mirror")
    open fun defaultPhotoFilterLabel(): String = str(R.string.label_default_photo_filter, "Default photo filter")
    open fun portraitLabSettingLabel(): String = str(R.string.label_portrait_lab, "Portrait Lab")
    open fun watermarkLabSettingLabel(): String = str(R.string.label_watermark_lab, "Watermark Lab")
    open fun livePhotoDefaultLabel(): String = str(R.string.label_live_photo_default, "Live photo default")
    open fun countdownLabel(): String = str(R.string.label_countdown, "Countdown")
    open fun resolutionLabel(): String = str(R.string.label_resolution, "Resolution")
    open fun frameRateLabel(): String = str(R.string.label_frame_rate, "Frame rate")
    open fun dynamicFpsLabel(): String = str(R.string.label_dynamic_fps, "Dynamic fps")
    open fun audioSceneLabel(): String = str(R.string.label_audio_scene, "Audio scene")
    open fun videoFilterSeedLabel(): String = str(R.string.label_video_filter_seed, "Video filter seed")

    // Portrait Lab labels
    open fun portraitProfileLabel(): String = str(R.string.label_portrait_profile, "Portrait profile")
    open fun beautyPresetLabel(): String = str(R.string.label_beauty_preset, "Beauty preset")
    open fun beautyStrengthLabel(): String = str(R.string.label_beauty_strength, "Beauty strength")
    open fun bokehEffectLabel(): String = str(R.string.label_bokeh_effect, "Bokeh effect")

    // Watermark detail labels
    open fun textPlacementLabel(): String = str(R.string.label_text_placement, "Text placement")
    open fun textScaleLabel(): String = str(R.string.label_text_scale, "Text scale")
    open fun textOpacityLabel(): String = str(R.string.label_text_opacity, "Text opacity")
    open fun frameBackgroundLabel(): String = str(R.string.label_frame_background, "Frame background")
    open fun tokensLabel(): String = str(R.string.label_tokens, "Tokens")

    // Pro controls attribute labels
    open fun rawLabel(): String = str(R.string.label_raw, "RAW")
    open fun rawSavedOnlyValue(): String = str(R.string.raw_saved_only_value, "Record only")
    open fun isoLabel(): String = str(R.string.label_iso, "ISO")
    open fun shutterLabel(): String = str(R.string.label_shutter, "Shutter")
    open fun evLabel(): String = str(R.string.label_ev, "EV")
    open fun focusLabel(): String = str(R.string.label_focus, "Focus")
    open fun apertureLabel(): String = str(R.string.label_aperture, "Aperture")
    open fun wbLabel(): String = str(R.string.label_wb, "WB")
    open fun autoLabel(): String = str(R.string.label_auto, "Auto")

    // Button labels
    open fun adjustSelected(): String = str(R.string.button_adjust_selected, "Adjust Selected")
    open fun saveAsCustom(): String = str(R.string.button_save_as_custom, "Save as Custom")
    open fun useThisTemplate(): String = str(R.string.button_use_this_template, "Use This Template")
    open fun useThisLook(): String = str(R.string.button_use_this_look, "Use This Look")
    open fun openStylePage(): String = str(R.string.button_open_style_page, "Open Style Page")
    open fun switchToAdvanced(): String = str(R.string.button_switch_to_advanced, "Switch to Advanced")
    open fun switchToLight(): String = str(R.string.button_switch_to_light, "Switch to Light")
    open fun tapToCycleLabel(): String = str(R.string.button_tap_to_cycle, "Tap to cycle")

    // Level labels
    open fun levelWarm(): String = str(R.string.level_warm, "Warm")
    open fun levelWarmPlus(): String = str(R.string.level_warm_plus, "Warm+")
    open fun levelCool(): String = str(R.string.level_cool, "Cool")
    open fun levelCoolPlus(): String = str(R.string.level_cool_plus, "Cool+")
    open fun levelMagenta(): String = str(R.string.level_magenta, "Magenta")
    open fun levelMagentaPlus(): String = str(R.string.level_magenta_plus, "Magenta+")
    open fun levelGreen(): String = str(R.string.level_green, "Green")
    open fun levelGreenPlus(): String = str(R.string.level_green_plus, "Green+")

    // Tone/Color labels
    open fun toneSoftLift(): String = str(R.string.tone_soft_lift, "Soft Lift")
    open fun toneDeepContrast(): String = str(R.string.tone_deep_contrast, "Deep Contrast")
    open fun toneBalanced(): String = str(R.string.tone_balanced, "Balanced")
    open fun colorMagentaWarm(): String = str(R.string.color_magenta_warm, "Magenta/Warm")
    open fun colorGreenCool(): String = str(R.string.color_green_cool, "Green/Cool")
    open fun colorCoolMuted(): String = str(R.string.color_cool_muted, "Cool/Muted")
    open fun colorNeutral(): String = str(R.string.color_neutral, "Neutral")

    // Status labels
    open fun currentDefault(): String = str(R.string.status_current_default, "Current default")
    open fun selectedDefault(): String = str(R.string.status_selected_default, "Selected default")
    open fun rendererPending(): String = str(R.string.status_renderer_pending, "Renderer pending")
    open fun statusCustom(): String = str(R.string.status_custom, "Custom")
    open fun unavailableMissingProfile(): String = str(R.string.status_unavailable_missing_profile, "Unavailable • Missing source profile")
    open fun readyEditableCopy(): String = str(R.string.status_ready_editable_copy, "Ready • Opens an editable custom copy")
    open fun readyEditingCustom(): String = str(R.string.status_ready_editing_custom, "Ready • Editing current custom look")
    open fun unavailableAlreadyCustom(): String = str(R.string.status_unavailable_already_custom, "Unavailable • Current default already custom")
    open fun readyBecomesDefault(): String = str(R.string.status_ready_becomes_default, "Ready • Becomes the default")
    open fun noCompatibleLooks(): String = str(R.string.status_no_compatible_looks, "No compatible looks")

    // Error/Unavailable
    open fun stillCaptureUnavailable(): String = str(R.string.error_still_capture_unavailable, "Still capture unavailable on this device")
    open fun videoRecordingUnavailable(): String = str(R.string.error_video_recording_unavailable, "Video recording unavailable on this device")
    open fun microphoneUnavailable(): String = str(R.string.error_microphone_unavailable, "Microphone capture unavailable on this device")
    open fun noCompatibleFilters(): String = str(R.string.error_no_compatible_filters, "No compatible filters")
    open fun noWatermarkTemplates(): String = str(R.string.error_no_watermark_templates, "No watermark templates available")

    // Manual control support
    open fun camera2Interop(): String = str(R.string.manual_camera2_interop, "Camera2 interop")
    open fun savedOnly(): String = str(R.string.manual_saved_only, "Saved only")
    open fun temporarilyUnsupported(): String = str(R.string.manual_temporarily_unsupported, "Temporarily unsupported")
    open fun manualControlsUnavailable(): String = str(R.string.manual_controls_unavailable, "Manual controls currently unavailable")
    open fun manualAdapterApplies(): String = str(R.string.manual_adapter_applies, "Adapter applies")
    open fun manualStaySavedOnly(): String = str(R.string.manual_stay_saved_only, "stay saved-only")
    open fun manualTempUnsupportedSuffix(): String = str(R.string.manual_temporarily_unsupported_suffix, "temporarily unsupported")

    // Dynamic format methods
    open fun zoomRatioLabel(ratio: Float): String = String.format(str(R.string.format_zoom_ratio, "%sx"), ratio)
    open fun countdownSeconds(seconds: Int): String = String.format(str(R.string.format_countdown_seconds, "%ss"), seconds)
    open fun outputSizeLabel(width: Int, height: Int): String =
        String.format(str(R.string.format_output_size, "%sx%s"), width, height)
    open fun kelvinLabel(k: Int): String = String.format(str(R.string.format_kelvin, "%sK"), k)
    open fun shutterSpeedMs(ms: Int): String = String.format(str(R.string.format_shutter_ms, "%sms"), ms)
    open fun focusDistanceDiopters(d: Float): String =
        String.format(str(R.string.format_focus_diopters, "%.1fD"), d)
    open fun apertureFNumber(f: Float): String =
        String.format(str(R.string.format_aperture_f, "f/%s"), f)
    open fun supportCount(count: Int): String =
        String.format(str(R.string.format_support_count, "%s options"), count)
    open fun looksCount(count: Int): String =
        String.format(str(R.string.format_looks_count, "%s curated looks"), count)
    open fun templatesCount(count: Int): String =
        String.format(str(R.string.format_templates_count, "%s templates"), count)
    open fun presetsCount(count: Int): String =
        String.format(str(R.string.format_presets_count, "%s presets"), count)
    open fun placementsCount(count: Int): String =
        String.format(str(R.string.format_placements_count, "%s placements"), count)
    open fun stepsCount(count: Int): String =
        String.format(str(R.string.format_steps_count, "%s steps"), count)
    open fun levelsCount(count: Int): String =
        String.format(str(R.string.format_levels_count, "%s levels"), count)
    open fun moodsCount(count: Int): String =
        String.format(str(R.string.format_moods_count, "%s moods"), count)
    open fun productProfilesCount(count: Int): String =
        String.format(str(R.string.format_product_profiles_count, "%s product profiles"), count)
    open fun plansCount(count: Int): String =
        String.format(str(R.string.format_plans_count, "%s plans"), count)
    open fun renderingFeelsCount(count: Int): String =
        String.format(str(R.string.format_rendering_feels_count, "%s rendering feels"), count)
    open fun nextLookLabel(label: String): String =
        String.format(str(R.string.format_next_look, "Next %s look"), label)

    // Watermark tokens
    open fun watermarkTokenCameraParams(): String = str(R.string.watermark_token_camera_params, "Camera Params")
    open fun watermarkTokenDateTime(): String = str(R.string.watermark_token_datetime, "Date/Time")
    open fun watermarkTokenLocation(): String = str(R.string.watermark_token_location, "Location")
    open fun watermarkTokenModel(): String = str(R.string.watermark_token_model, "Model")

    // Lens labels
    open fun backLens(): String = str(R.string.lens_back, "Back")
    open fun frontLens(): String = str(R.string.lens_front, "Front")

    // General labels
    open fun placementLabel(): String = str(R.string.label_placement, "Placement")
    open fun scaleLabel(): String = str(R.string.label_scale, "Scale")
    open fun opacityLabel(): String = str(R.string.label_opacity, "Opacity")
    open fun backgroundLabel(): String = str(R.string.label_background, "Background")
    open fun colorLabel(): String = str(R.string.label_color, "Color")
    open fun toneLabel(): String = str(R.string.label_tone, "Tone")

    // Video spec labels
    open fun lowLightAuto24fps(): String = str(R.string.video_low_light_auto, "Low-light auto 24fps")
    open fun lockedFps(): String = str(R.string.video_locked_fps, "Locked fps")

    // Settings summary helpers
    open fun settingsSummaryGrid(): String = str(R.string.settings_summary_grid, "Grid")
    open fun settingsSummaryShutterSound(): String = str(R.string.settings_summary_shutter_sound, "Shutter sound")
    open fun settingsSummarySelfieMirror(): String = str(R.string.settings_summary_selfie_mirror, "Selfie mirror")
    open fun settingsSummaryFilter(): String = str(R.string.settings_summary_filter, "Filter")
    open fun settingsSummaryPortrait(): String = str(R.string.settings_summary_portrait, "Portrait")
    open fun settingsSummaryWatermark(): String = str(R.string.settings_summary_watermark, "Watermark")
    open fun settingsSummaryLive(): String = str(R.string.settings_summary_live, "Live")
    open fun settingsSummaryTimer(): String = str(R.string.settings_summary_timer, "Timer")
    open fun settingsSummaryMic(): String = str(R.string.settings_summary_mic, "Mic")
    open fun settingsSummaryFilters(): String = str(R.string.settings_summary_filters, "filters")
    open fun settingsSummaryWatermarkTemplates(): String = str(R.string.settings_summary_watermark_templates, "watermark templates")
    open fun settingsSummaryMsBundle(): String = str(R.string.settings_summary_ms_bundle, "ms bundle")
    open fun settingsSummaryProManualDraft(): String = str(R.string.settings_summary_pro_manual_draft, "Pro manual draft")

    // Settings joiners for sessionSettingsRenderModel
    open fun settingsJoinerGrid(): String = str(R.string.settings_joiner_grid, "Grid ")
    open fun settingsJoinerShutterSound(): String = str(R.string.settings_joiner_shutter_sound, " | Shutter sound ")
    open fun settingsJoinerSelfieMirror(): String = str(R.string.settings_joiner_selfie_mirror, " | Selfie mirror ")
    open fun settingsJoinerFilter(): String = str(R.string.settings_joiner_filter, "Filter ")
    open fun settingsJoinerPortrait(): String = str(R.string.settings_joiner_portrait, " | Portrait ")
    open fun settingsJoinerWatermark(): String = str(R.string.settings_joiner_watermark, " | Watermark ")
    open fun settingsJoinerLive(): String = str(R.string.settings_joiner_live, " | Live ")
    open fun settingsJoinerTimer(): String = str(R.string.settings_joiner_timer, " | Timer ")
    open fun settingsJoinerMic(): String = str(R.string.settings_joiner_mic, " | Mic ")

    // Settings page
    open fun settingsPageSupporting(): String = str(R.string.settings_page_supporting, "Quick defaults with explicit supported, degraded, and staged capability hints.")
    open fun gridSupportLabel(count: Int): String = String.format(str(R.string.settings_page_grid_support, "Cycle %d layouts"), count)
    open fun portraitTuningLabel(): String = str(R.string.settings_page_portrait_support, "Open profile, beauty, and bokeh tuning")
    open fun watermarkTuningLabel(count: Int): String = String.format(str(R.string.settings_page_watermark_support, "Open selector + per-template tuning; %d templates"), count)
    open fun liveSupportLabel(durationMs: Int, watermarkBehavior: String): String = String.format(str(R.string.settings_page_live_support, "Saved default only; %d ms bundle | dynamic watermark %s"), durationMs, watermarkBehavior)
    open fun degradedResolutionLabel(saved: String, active: String): String = String.format(str(R.string.settings_page_degraded_resolution, "Saved as %s, active graph uses %s"), saved, active)
    open fun degradedFramerateLabel(saved: String, active: String): String = String.format(str(R.string.settings_page_degraded_framerate, "Saved as %s, active graph uses %s"), saved, active)
    open fun degradedDynamicFpsLabel(saved: String, active: String): String = String.format(str(R.string.settings_page_degraded_dynamic_fps, "Saved as %s, active graph uses %s"), saved, active)
    open fun degradedAudioLabel(saved: String, active: String): String = String.format(str(R.string.settings_page_degraded_audio, "Saved as %s, active graph uses %s"), saved, active)
    open fun videoFilterSeedCountLabel(count: Int): String = String.format(str(R.string.settings_page_video_filter_seed, "Saved filter seed; %d looks staged"), count)
    open fun catalogFooterStillWatermark(): String = str(R.string.settings_page_catalog_footer_1, "Still watermark templates now flow into metadata and photo rendering.")
    open fun catalogFooterManualStaged(): String = str(R.string.settings_page_catalog_footer_2, "Manual drafts and Live/video defaults remain staged in the same settings spine.")
    open fun catalogFooterProManualPrefix(): String = str(R.string.settings_page_pro_manual_prefix, " | Pro manual draft ")

    // Watermark selector
    open fun watermarkSelectorSupporting(): String = str(R.string.watermark_selector_supporting, "Watermark selection sits one level below Settings. Pick the active template here, then enter the template-specific style page to edit.")
    open fun watermarkSelectorDefaultPrefix(): String = str(R.string.watermark_selector_default_prefix, "Default ")
    open fun watermarkSelectorTemplatesStaged(count: Int): String = String.format(str(R.string.watermark_selector_templates_staged, " templates staged"), count)
    open fun watermarkSelectorCurrentDefault(): String = str(R.string.watermark_selector_current_default, " | Current default")
    open fun watermarkEditAttrsFrame(): String = str(R.string.watermark_selector_edit_attrs_frame, "Placement, scale, opacity, background")
    open fun watermarkEditAttrsClassic(): String = str(R.string.watermark_selector_edit_attrs_classic, "Placement, scale, opacity")
    open fun watermarkSelectorFooterSupported(): String = str(R.string.watermark_selector_footer_supported, "Pure Text shows no frame; Classic Overlay keeps its border fixed; Travel Polaroid and Retro Frame expose frame variants; Blur Four Border uses blur-only backgrounds.")
    open fun watermarkSelectorFooterUnsupported(): String = str(R.string.watermark_selector_footer_unsupported, "Still capture is unavailable on this device, so Watermark Lab stays read-only.")

    // Watermark detail
    open fun watermarkDetailSupportingSelected(): String = str(R.string.watermark_detail_supporting_selected, "This is the active default watermark. Changes here will affect the next static photo rendered with this template.")
    open fun watermarkDetailSupportingNotSelected(): String = str(R.string.watermark_detail_supporting_not_selected, "This template is not yet the current default. Adjust here first, then switch from the selector page when ready.")
    open fun watermarkDetailFooterFrame(): String = str(R.string.watermark_detail_footer_frame, "Frame border rendering is live for static-photo export.")
    open fun watermarkDetailFooterOverlay(): String = str(R.string.watermark_detail_footer_overlay, "Classic overlay stays inside the source image without an expanded border.")
    open fun watermarkDetailTokensPrefix(): String = str(R.string.watermark_detail_tokens_prefix, "Tokens: ")

    // Watermark attribute prefixes for hero summary
    open fun watermarkAttrPlacementPrefix(): String = str(R.string.watermark_attr_placement_prefix, "Placement ")
    open fun watermarkAttrScalePrefix(): String = str(R.string.watermark_attr_scale_prefix, "Scale ")
    open fun watermarkAttrOpacityPrefix(): String = str(R.string.watermark_attr_opacity_prefix, "Opacity ")
    open fun watermarkAttrBackgroundPrefix(): String = str(R.string.watermark_attr_background_prefix, "Background ")
    open fun watermarkTemplateExpandedFrame(): String = str(R.string.watermark_template_expanded_frame, "Expanded frame")
    open fun watermarkTemplateClassicOverlay(): String = str(R.string.watermark_template_classic_overlay, "Classic overlay")
    open fun watermarkTemplatePureText(): String = str(R.string.watermark_template_pure_text, "纯文字")
    open fun watermarkTemplateBlurFourBorder(): String = str(R.string.watermark_template_blur_four_border, "模糊四边框")

    // Portrait lab
    open fun portraitLabSupporting(): String = str(R.string.portrait_lab_supporting, "Portrait product controls sit one level below Settings. Use this page to adjust the saved portrait profile, beauty behavior, and bokeh effect without changing the active portrait filter roster.")
    open fun portraitLabJoinerBeauty(): String = str(R.string.portrait_lab_joiner_beauty, "Beauty ")
    open fun portraitLabJoinerBokeh(): String = str(R.string.portrait_lab_joiner_bokeh, "Bokeh ")
    open fun portraitLabFooter(): String = str(R.string.portrait_lab_footer, "Tone Lab still owns portrait color style selection. Portrait Lab only governs the product profile, beauty plan/strength, and lightweight bokeh rendering metadata introduced in 6B-5.")

    // Pro controls
    open fun proControlsSupportingEditable(): String = str(R.string.pro_controls_supporting_editable, "Upper-layer manual draft is currently editable; each control indicates its status: Applied, Saved-only, or Temporarily unsupported.")
    open fun proControlsSupportingReadonly(): String = str(R.string.pro_controls_supporting_readonly, "Draft changes allowed, but this device currently holds all controls in saved-only or temporarily-unsupported state.")
    open fun proControlsFinishCaptureHint(): String = str(R.string.pro_controls_finish_capture_hint, "Finish the current capture before editing.")

    // Disabled reasons
    open fun disabledCountdown(): String = str(R.string.disabled_countdown, "Countdown in progress")
    open fun disabledSavingPhoto(): String = str(R.string.disabled_saving_photo, "Saving previous photo")
    open fun disabledPreparingRecording(): String = str(R.string.disabled_preparing_recording, "Preparing to record")
    open fun disabledRecording(): String = str(R.string.disabled_recording, "Unavailable during recording")
    open fun disabledStoppingRecording(): String = str(R.string.disabled_stopping_recording, "Stopping and saving")
    open fun disabledPreviewRecovering(): String = str(R.string.disabled_preview_recovering, "Camera recovering")
    open fun disabledPermission(): String = str(R.string.disabled_permission, "Camera permission required")

    // Frame ratio control
    open fun frameRatioTitle(): String = str(R.string.label_frame_ratio_title, "Frame")
    open fun disabledFrameRatioUnsupportedMode(): String = str(R.string.disabled_frame_ratio_unsupported_mode, "Frame ratio not supported in current mode")
    open fun disabledFrameRatioActiveShot(): String = str(R.string.disabled_frame_ratio_active_shot, "Wait for current capture to finish")
    open fun disabledFrameRatioCountdown(): String = str(R.string.disabled_frame_ratio_countdown, "Wait for countdown to finish")
    open fun disabledBrightnessUnsupported(): String = str(R.string.disabled_brightness_unsupported, "Brightness unavailable")
    open fun disabledBrightnessCountdown(): String = str(R.string.disabled_brightness_countdown, "Wait for countdown to finish")
    open fun disabledBrightnessActiveShot(): String = str(R.string.disabled_brightness_active_shot, "Wait for current capture to finish")

    // Low-light night assist prompt
    open fun buttonLowLightNightPrompt(): String = str(R.string.button_low_light_night_prompt, "Night")
    open fun buttonLowLightNightPromptEnabled(): String = str(R.string.button_low_light_night_prompt_enabled, "Night assist on")
    open fun buttonLowLightNightPromptDisabled(): String = str(R.string.button_low_light_night_prompt_disabled, "Night assist off")
    open fun buttonLowLightNightPromptDegraded(): String = str(R.string.button_low_light_night_prompt_degraded, "Night assist (single-frame)")
    open fun lowLightNightAssistContentDescription(): String = str(R.string.low_light_night_assist_content_description, "Low-light night assist prompt")

    // Recording status
    open fun statusRecordingStarting(): String = str(R.string.status_recording_starting, "Starting…")
    open fun statusRecordingActive(): String = str(R.string.status_recording_active, "Recording")
    open fun statusRecordingSaving(): String = str(R.string.status_recording_saving, "Saving…")

    // Settings blocked
    open fun settingsBlockedByCapture(): String = str(R.string.settings_blocked_by_capture, "Capture in progress, settings locked")
    open fun settingsNotLoaded(): String = str(R.string.settings_not_loaded, "Settings not loaded yet")
    open fun settingsActionUnsupported(): String = str(R.string.settings_action_unsupported, "Action not supported in current mode")

    // Color lab summary
    open fun colorToneSummary(colorAxis: Float, toneAxis: Float): String {
        val colorLabel = when {
            colorAxis > 0.6f -> levelWarmPlus()
            colorAxis > 0.08f -> levelWarm()
            colorAxis < -0.6f -> levelCoolPlus()
            colorAxis < -0.08f -> levelCool()
            else -> colorNeutral()
        }
        val toneLabel = when {
            toneAxis > 0.08f -> toneSoftLift()
            toneAxis < -0.08f -> toneDeepContrast()
            else -> toneBalanced()
        }
        return "$colorLabel / $toneLabel"
    }

    open fun languageDisplayName(settings: PersistedSettings): String = when (settings.common.appLanguage) {
        AppLanguage.ZH -> str(R.string.app_name, "OpenCamera")
        AppLanguage.EN -> "English"
    }

    internal open fun sessionUiStrings(): com.opencamera.app.SessionUiStrings {
        return com.opencamera.app.SessionUiStrings(
            buttonSwitchToFront = str(R.string.button_switch_to_front, "Switch to Front"),
            buttonSwitchToBack = str(R.string.button_switch_to_back, "Switch to Back"),
            buttonSingleLens = str(R.string.button_single_lens, "Single Lens"),
            buttonZoomPrefix = str(R.string.button_zoom_prefix, "Zoom"),
            buttonZoomUnavailable = str(R.string.button_zoom_unavailable, "Zoom N/A"),
            buttonStillFast = str(R.string.button_still_fast, "Still Fast"),
            buttonStillMax = str(R.string.button_still_max, "Still Max"),
            buttonStillQualityUnavailable = str(R.string.button_still_quality_unavailable, "Still N/A"),
            buttonStill12Mp = str(R.string.button_still_12mp, "Still 12MP"),
            buttonStill8Mp = str(R.string.button_still_8mp, "Still 8MP"),
            buttonStill2Mp = str(R.string.button_still_2mp, "Still 2MP"),
            buttonStillResolutionUnavailable = str(R.string.button_still_resolution_unavailable, "Size N/A"),
            outputErrorPrefix = str(R.string.output_error_prefix, "Camera issue:"),
            outputVideoPrefix = str(R.string.output_video_prefix, "Last video:"),
            outputLivePrefix = str(R.string.output_live_prefix, "Last Live photo:"),
            outputSavedPrefix = str(R.string.output_saved_prefix, "Last photo:"),
            outputPreviewPrefix = str(R.string.output_preview_prefix, "Preview thumbnail:"),
            outputWaiting = str(R.string.output_waiting, "No photo captured yet.")
        )
    }
}
