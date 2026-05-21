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
    open fun watermark(): String = str(R.string.button_palette_entry, "Watermark")
    open fun livePhoto(): String = str(R.string.button_quick_live, "Live")
    open fun timer(): String = str(R.string.button_quick_timer, "Timer")
    open fun flash(): String = str(R.string.button_quick_flash, "Flash")
    open fun lensLab(): String = str(R.string.button_settings_entry, "Lens Lab")
    open fun filterLab(): String = str(R.string.button_filter_entry, "Filter Lab")
    open fun portraitLab(): String = str(R.string.button_portrait_mode, "Portrait Lab")
    open fun watermarkLab(): String = str(R.string.button_palette_entry, "Watermark Lab")
    open fun devEntry(): String = str(R.string.button_dev_entry, "DEV")
    open fun devRestart(): String = str(R.string.button_dev_restart_session, "Restart Session")
    open fun devClose(): String = str(R.string.dev_close, "Close")
    open fun devExportLog(): String = str(R.string.dev_export_log, "Export Log")
    open fun devTabKey(): String = str(R.string.dev_tab_key, "Key")
    open fun devTabCore(): String = str(R.string.dev_tab_core, "Core")
    open fun devTabError(): String = str(R.string.dev_tab_error, "Error")
    open fun devTabAll(): String = str(R.string.dev_tab_all, "All")
    open fun quickLauncher(): String = str(R.string.button_quick_launcher, "Quick")
    open fun moreControls(): String = str(R.string.button_more_controls, "More")
    open fun stopSession(): String = str(R.string.button_stop_session, "Stop Session")
    open fun restartSession(): String = str(R.string.button_restart_session, "Restart Session")
    open fun closeSettings(): String = str(R.string.button_settings_close, "Close Lens Lab")
    open fun closeFilter(): String = str(R.string.button_filter_close, "Close Filter Lab")
    open fun permissionPending(): String = str(R.string.permission_pending, "Camera permission is required before preview can start.")
    open fun permissionGranted(): String = str(R.string.permission_granted, "Camera permission granted. Preview is ready.")
    open fun permissionCameraOnly(): String = str(R.string.permission_camera_only, "Camera granted. Video recording will be silent until microphone permission is granted.")
    open fun permissionDenied(): String = str(R.string.permission_denied, "Camera permission denied. Tap shutter to request it again.")
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
    open fun tone(): String = str(R.string.button_palette_entry, "Tone")

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
        String.format(str(R.string.dev_log_title_key, "Key Log (%d)"), count)
    open fun devLogTitleCore(count: Int): String =
        String.format(str(R.string.dev_log_title_core, "Core Log (%d)"), count)
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
