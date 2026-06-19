package com.opencamera.app.i18n

import android.content.Context
import androidx.annotation.StringRes
import com.opencamera.app.R
import com.opencamera.app.SettingsControlAvailability
import com.opencamera.app.FilterAdvancedControl
import com.opencamera.app.FilterLabFamily
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.AppLanguage
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.LiveSaveFormat
import com.opencamera.core.settings.LiveWatermarkMotionBehavior
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PortraitBeautyPreset
import com.opencamera.core.settings.PortraitBeautyStrength
import com.opencamera.core.settings.PortraitBokehEffect
import com.opencamera.core.settings.PortraitProfile
import com.opencamera.core.settings.WatermarkFrameBackground
import com.opencamera.core.settings.WatermarkTextOpacity
import com.opencamera.core.settings.WatermarkTextPlacement
import com.opencamera.core.settings.WatermarkTextScale

open class AppTextResolver(private val context: Context?) {
    private fun str(resId: Int, fallback: String): String =
        context?.getString(resId) ?: fallback

    internal open fun get(@StringRes resId: Int): String = context?.getString(resId).orEmpty()

    open fun modeDisplayName(modeId: ModeId): String = when (modeId) {
        ModeId.PHOTO -> str(R.string.button_photo_mode, "Photo")
        ModeId.CHECK_IN -> str(R.string.button_checkin_mode, "Check-in")
        ModeId.DOCUMENT -> str(R.string.button_document_mode, "Doc")
        ModeId.HUMANISTIC -> str(R.string.button_humanistic_mode, "Human")
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

    open fun countdownValueLabel(value: CountdownDuration): String = when (value) {
        CountdownDuration.OFF -> str(R.string.level_off, "Off")
        CountdownDuration.SECONDS_3 -> "3s"
        CountdownDuration.SECONDS_5 -> "5s"
        CountdownDuration.SECONDS_10 -> "10s"
    }

    open fun audioProfileLabel(value: AudioProfile): String = when (value) {
        AudioProfile.STANDARD -> str(R.string.audio_profile_standard, "Standard")
        AudioProfile.CONCERT -> str(R.string.audio_profile_concert, "Concert")
    }

    open fun dynamicFpsPolicyLabel(value: DynamicVideoFpsPolicy): String = when (value) {
        DynamicVideoFpsPolicy.LOCKED -> str(R.string.video_locked_fps, "Locked fps")
        DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS -> str(R.string.video_low_light_auto, "Low-light auto 24fps")
    }

    open fun liveWatermarkMotionBehaviorLabel(value: LiveWatermarkMotionBehavior): String = when (value) {
        LiveWatermarkMotionBehavior.STATIC_OVERLAY ->
            str(R.string.live_watermark_motion_static_overlay, "Static Overlay")
        LiveWatermarkMotionBehavior.FOLLOW_FRAME_LUMA ->
            str(R.string.live_watermark_motion_follow_luma, "Follow Frame Luma")
        LiveWatermarkMotionBehavior.FOLLOW_FRAME_LUMA_AND_MOTION ->
            str(R.string.live_watermark_motion_follow_luma_motion, "Follow Frame Luma + Motion")
    }

    open fun portraitProfileEnumLabel(value: PortraitProfile): String = when (value) {
        PortraitProfile.NATIVE -> str(R.string.portrait_profile_native, "Native Portrait")
        PortraitProfile.LUMINOUS -> str(R.string.portrait_profile_luminous, "Luminous Portrait")
    }

    open fun portraitBeautyPresetLabel(value: PortraitBeautyPreset): String = when (value) {
        PortraitBeautyPreset.AUTHENTIC -> str(R.string.portrait_beauty_preset_authentic, "Authentic")
        PortraitBeautyPreset.CLEAR -> str(R.string.portrait_beauty_preset_clear, "Clear")
        PortraitBeautyPreset.RADIANT -> str(R.string.portrait_beauty_preset_radiant, "Radiant")
    }

    open fun portraitBeautyStrengthLabel(value: PortraitBeautyStrength): String = when (value) {
        PortraitBeautyStrength.OFF -> str(R.string.portrait_beauty_strength_off, "Off")
        PortraitBeautyStrength.SOFT -> str(R.string.portrait_beauty_strength_soft, "Soft")
        PortraitBeautyStrength.BALANCED -> str(R.string.portrait_beauty_strength_balanced, "Balanced")
        PortraitBeautyStrength.ELEVATED -> str(R.string.portrait_beauty_strength_elevated, "Elevated")
    }

    open fun portraitBokehEffectLabel(value: PortraitBokehEffect): String = when (value) {
        PortraitBokehEffect.NATURAL -> str(R.string.portrait_bokeh_effect_natural, "Natural")
        PortraitBokehEffect.CREAMY -> str(R.string.portrait_bokeh_effect_creamy, "Creamy")
        PortraitBokehEffect.DREAMY -> str(R.string.portrait_bokeh_effect_dreamy, "Dreamy")
    }

    open fun liveSaveFormatValueLabel(value: LiveSaveFormat): String = when (value) {
        LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG -> str(R.string.live_save_format_motion_photo, "Motion Photo")
        LiveSaveFormat.MOTION_MP4_SIDECAR -> str(R.string.live_save_format_mp4_sidecar, "MP4 Sidecar")
        LiveSaveFormat.STILL_JPEG_ONLY -> str(R.string.live_save_format_jpeg_only, "JPEG Only")
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

    // Style card rail strings
    internal open fun styleRailTitle(family: FilterLabFamily): String = when (family) {
        FilterLabFamily.PHOTO -> str(R.string.style_rail_title_photo, "Photo Styles")
        FilterLabFamily.HUMANISTIC -> str(R.string.style_rail_title_humanistic, "Humanistic Styles")
        FilterLabFamily.PORTRAIT -> str(R.string.style_rail_title_portrait, "Portrait Styles")
        FilterLabFamily.VIDEO -> str(R.string.style_rail_title_video, "Video Styles")
    }

    internal open fun styleRailSupportingText(family: FilterLabFamily): String = when (family) {
        FilterLabFamily.PHOTO -> str(R.string.style_rail_supporting_photo, "Tap a card to apply this look instantly.")
        FilterLabFamily.HUMANISTIC -> str(R.string.style_rail_supporting_humanistic, "Street and life presets for a human perspective.")
        FilterLabFamily.PORTRAIT -> str(R.string.style_rail_supporting_portrait, "Portrait-specific tone presets.")
        FilterLabFamily.VIDEO -> str(R.string.style_rail_supporting_video, "Video look presets applied before recording.")
    }

    open fun filterLabHeroSummary(familyLabel: String, filterLabel: String, count: Int): String =
        String.format(str(R.string.filter_lab_hero_summary, "%1\$s default %2\$s • %3\$d looks staged"), familyLabel, filterLabel, count)
    open fun filterLabCurrentDefault(filterLabel: String): String =
        String.format(str(R.string.filter_lab_current_default, "Current default %s"), filterLabel)

    open fun filterLabNextLook(familyLabel: String): String =
        String.format(str(R.string.filter_lab_next_look, "Next %s look"), familyLabel)
    open fun filterLabLooksDeferred(count: Int): String =
        String.format(str(R.string.filter_lab_looks_deferred, "%d looks | import/export deferred"), count)
    open fun filterLabSaveCustomReady(familyLabel: String): String =
        String.format(str(R.string.filter_lab_save_custom_ready, "Ready • Becomes the %s default"), familyLabel)

    open fun devLogTitleKey(count: Int): String =
        String.format(str(R.string.dev_log_title_key, "Summary Log (%d)"), count)
    open fun devLogTitleCore(count: Int): String =
        String.format(str(R.string.dev_log_title_core, "Pipeline Log (%d)"), count)
    open fun devLogTitleError(count: Int): String =
        String.format(str(R.string.dev_log_title_error, "Error Log (%d)"), count)
    open fun devLogTitleAll(count: Int): String =
        String.format(str(R.string.dev_log_title_all, "All Events (%d)"), count)
    open fun devLogTitleLink(count: Int): String =
        String.format(str(R.string.dev_log_title_link, "Link Flow (%d)"), count)

    internal open fun filterCtrlLabel(control: FilterAdvancedControl): String = when (control) {
        FilterAdvancedControl.EXPOSURE -> str(R.string.filter_ctrl_exposure, "Exposure")
        FilterAdvancedControl.SOFT_GLOW -> str(R.string.filter_ctrl_soft_glow, "Soft Glow")
        FilterAdvancedControl.HALO -> str(R.string.filter_ctrl_halo, "Halo")
        FilterAdvancedControl.GRAIN -> str(R.string.filter_ctrl_grain, "Grain")
        FilterAdvancedControl.SHARPNESS -> str(R.string.filter_ctrl_sharpness, "Sharpness")
        FilterAdvancedControl.VIGNETTE -> str(R.string.filter_ctrl_vignette, "Vignette")
        FilterAdvancedControl.HIGHLIGHTS -> str(R.string.filter_ctrl_highlights, "Highlights")
        FilterAdvancedControl.SHADOWS -> str(R.string.filter_ctrl_shadows, "Shadows")
        FilterAdvancedControl.WARM_BOOST -> str(R.string.filter_ctrl_warm_boost, "Warm Boost")
        FilterAdvancedControl.COOL_BOOST -> str(R.string.filter_ctrl_cool_boost, "Cool Boost")
        FilterAdvancedControl.TEMPERATURE_SHIFT -> str(R.string.filter_ctrl_temp_shift, "Temp Shift")
        FilterAdvancedControl.TINT_SHIFT -> str(R.string.filter_ctrl_tint_shift, "Tint Shift")
    }

    open fun liveSaveFormatSupportLabel(count: Int): String = String.format(str(R.string.format_live_save_format_support, "%d formats: Motion Photo, MP4 Sidecar, JPEG Only"), count)

    open fun zoomRatioLabel(ratio: Float): String = String.format(str(R.string.format_zoom_ratio, "%sx"), ratio)
    open fun countdownSeconds(seconds: Int): String = String.format(str(R.string.format_countdown_seconds, "%ss"), seconds)
    open fun outputSizeLabel(width: Int, height: Int): String =
        String.format(str(R.string.format_output_size, "%1\$sx%2\$s"), width, height)
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

    open fun gridSupportLabel(count: Int): String = String.format(str(R.string.settings_page_grid_support, "Cycle %d layouts"), count)
    open fun watermarkTuningLabel(count: Int): String = String.format(str(R.string.settings_page_watermark_support, "Open selector + per-template tuning; %d templates"), count)
    open fun liveSupportLabel(durationMs: Int, watermarkBehavior: String): String = String.format(str(R.string.settings_page_live_support, "Saved default only; %1\$d ms bundle | dynamic watermark %2\$s"), durationMs, watermarkBehavior)
    open fun degradedResolutionLabel(saved: String, active: String): String = String.format(str(R.string.settings_page_degraded_resolution, "Saved as %1\$s, active graph uses %2\$s"), saved, active)
    open fun degradedFramerateLabel(saved: String, active: String): String = String.format(str(R.string.settings_page_degraded_framerate, "Saved as %1\$s, active graph uses %2\$s"), saved, active)
    open fun degradedDynamicFpsLabel(saved: String, active: String): String = String.format(str(R.string.settings_page_degraded_dynamic_fps, "Saved as %1\$s, active graph uses %2\$s"), saved, active)
    open fun degradedAudioLabel(saved: String, active: String): String = String.format(str(R.string.settings_page_degraded_audio, "Saved as %1\$s, active graph uses %2\$s"), saved, active)
    open fun videoFilterSeedCountLabel(count: Int): String = String.format(str(R.string.settings_page_video_filter_seed, "Saved filter seed; %d looks staged"), count)

    open fun watermarkSelectorTemplatesStaged(count: Int): String = String.format(str(R.string.watermark_selector_templates_staged, " templates staged"), count)

    // Watermark attribute prefixes for hero summary

    open fun watermarkPlacementLabel(value: WatermarkTextPlacement): String = when (value) {
        WatermarkTextPlacement.TOP_LEFT -> str(R.string.watermark_placement_top_left, "Top Left")
        WatermarkTextPlacement.TOP_RIGHT -> str(R.string.watermark_placement_top_right, "Top Right")
        WatermarkTextPlacement.BOTTOM_LEFT -> str(R.string.watermark_placement_bottom_left, "Bottom Left")
        WatermarkTextPlacement.BOTTOM_RIGHT -> str(R.string.watermark_placement_bottom_right, "Bottom Right")
        WatermarkTextPlacement.BOTTOM_CENTER -> str(R.string.watermark_placement_bottom_center, "Bottom Center")
    }

    open fun watermarkTextScaleLabel(value: WatermarkTextScale): String = when (value) {
        WatermarkTextScale.COMPACT -> str(R.string.watermark_scale_compact, "Compact")
        WatermarkTextScale.NORMAL -> str(R.string.watermark_scale_normal, "Normal")
        WatermarkTextScale.LARGE -> str(R.string.watermark_scale_large, "Large")
    }

    open fun watermarkTextOpacityLabel(value: WatermarkTextOpacity): String = when (value) {
        WatermarkTextOpacity.SUBTLE -> str(R.string.watermark_opacity_subtle, "Subtle")
        WatermarkTextOpacity.SOFT -> str(R.string.watermark_opacity_soft, "Soft")
        WatermarkTextOpacity.SOLID -> str(R.string.watermark_opacity_solid, "Solid")
    }

    open fun watermarkFrameBackgroundLabel(value: WatermarkFrameBackground): String = when (value) {
        WatermarkFrameBackground.DARK -> str(R.string.watermark_background_dark, "Dark")
        WatermarkFrameBackground.WHITE -> str(R.string.watermark_background_white, "White")
        WatermarkFrameBackground.SOURCE_BLUR -> str(R.string.watermark_background_source_blur, "Source Blur")
        WatermarkFrameBackground.SOURCE_LIGHT_BLUR -> str(R.string.watermark_background_light_blur, "Light Blur")
        WatermarkFrameBackground.SOURCE_VIVID_BLUR -> str(R.string.watermark_background_vivid_blur, "Vivid Blur")
    }

    open fun filterProfileLabel(profileId: String, fallback: String): String = when (profileId) {
        "photo-vivid" -> str(R.string.filter_profile_photo_vivid, "Vivid")
        "photo-original" -> str(R.string.filter_profile_photo_original, "Original")
        "photo-chasing-light" -> str(R.string.filter_profile_photo_chasing_light, "Chasing Light")
        "photo-rich" -> str(R.string.filter_profile_photo_rich, "Rich")
        "photo-texture" -> str(R.string.filter_profile_photo_texture, "Texture")
        "photo-bw" -> str(R.string.filter_profile_photo_bw, "B&W")
        "humanistic-original" -> str(R.string.filter_profile_humanistic_original, "Humanistic Original")
        "humanistic-vivid" -> str(R.string.filter_profile_humanistic_vivid, "Humanistic Vivid")
        "humanistic-street" -> str(R.string.filter_profile_humanistic_street, "Street")
        "humanistic-portrait" -> str(R.string.filter_profile_humanistic_portrait, "Portrait")
        "humanistic-life" -> str(R.string.filter_profile_humanistic_life, "Life")
        "portrait-blue" -> str(R.string.filter_profile_portrait_blue, "Portrait Blue")
        "portrait-retro" -> str(R.string.filter_profile_portrait_retro, "Portrait Retro")
        "portrait-ccd" -> str(R.string.filter_profile_portrait_ccd, "Portrait CCD")
        "portrait-vivid" -> str(R.string.filter_profile_portrait_vivid, "Portrait Vivid")
        "portrait-original" -> str(R.string.filter_profile_portrait_original, "Portrait Original")
        "portrait-chasing-light" -> str(R.string.filter_profile_portrait_chasing_light, "Portrait Chasing Light")
        "portrait-rich" -> str(R.string.filter_profile_portrait_rich, "Portrait Rich")
        else -> fallback
    }

    // Portrait lab

    // Pro controls

    // Disabled reasons

    // Frame ratio control

    // Low-light night assist prompt

    // Processing status

    // Recording status
    open fun previewStatusLabel(status: PreviewStatus): String = when (status) {
        PreviewStatus.IDLE -> str(R.string.status_preview_idle, "Idle")
        PreviewStatus.STARTING -> str(R.string.status_preview_starting, "Starting")
        PreviewStatus.ACTIVE -> str(R.string.status_preview_active, "Active")
        PreviewStatus.RECOVERING -> str(R.string.status_preview_recovering, "Recovering")
        PreviewStatus.SURFACE_LOST -> str(R.string.status_preview_surface_lost, "Surface lost")
        PreviewStatus.BLOCKED -> str(R.string.status_preview_blocked, "Blocked")
        PreviewStatus.ERROR -> str(R.string.status_preview_error, "Error")
    }
    open fun captureStatusLabel(status: CaptureStatus): String = when (status) {
        CaptureStatus.IDLE -> str(R.string.status_capture_idle, "Idle")
        CaptureStatus.REQUESTED -> str(R.string.status_capture_requested, "Requested")
        CaptureStatus.SAVING -> str(R.string.status_capture_saving, "Saving")
        CaptureStatus.DATA_RECEIVED -> str(R.string.status_capture_data_received, "Data received")
        CaptureStatus.COMPLETED -> str(R.string.status_capture_completed, "Completed")
        CaptureStatus.FAILED -> str(R.string.status_capture_failed, "Failed")
    }

    // Live photo asset labels

    // Settings blocked

    // Color lab summary
    open fun colorToneSummary(colorAxis: Float, toneAxis: Float): String {
        val colorLabel = when {
            colorAxis > 0.6f -> str(R.string.level_warm_plus, "Warm+")
            colorAxis > 0.08f -> str(R.string.level_warm, "Warm")
            colorAxis < -0.6f -> str(R.string.level_cool_plus, "Cool+")
            colorAxis < -0.08f -> str(R.string.level_cool, "Cool")
            else -> str(R.string.color_neutral, "Neutral")
        }
        val toneLabel = when {
            toneAxis > 0.08f -> str(R.string.tone_soft_lift, "Soft Lift")
            toneAxis < -0.08f -> str(R.string.tone_deep_contrast, "Deep Contrast")
            else -> str(R.string.tone_balanced, "Balanced")
        }
        return "$colorLabel / $toneLabel"
    }

    // Language switch
    open fun languageDisplayName(settings: PersistedSettings): String = when (settings.common.appLanguage) {
        AppLanguage.ZH -> "中文"
        AppLanguage.EN -> "English"
    }

    // Document batch organizer
    open fun documentBatchPageCount(count: Int): String = String.format(str(R.string.document_batch_page_count, "%d pages"), count)

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

    // Zoom slider
    open fun zoomSliderDescription(ratio: Float, disabledReason: String?): String =
        if (disabledReason != null) {
            String.format(str(R.string.zoom_slider_desc_reason, "Zoom: %s"), disabledReason)
        } else {
            String.format(str(R.string.zoom_slider_desc_ratio, "Zoom: %.1fx"), ratio)
        }

    // Check-in style panel
    open fun checkInEditingHint(enabled: Boolean): String =
        if (enabled) str(R.string.checkin_editing_hint, "Tap to select scene and style")
        else str(R.string.checkin_editing_disabled, "Capturing, cannot switch")

    // Debug summary labels
}
