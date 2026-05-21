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
        CompositionGridMode.OFF -> str(R.string.button_still_quality, "Quality")
        CompositionGridMode.RULE_OF_THIRDS -> "3x3"
        CompositionGridMode.GOLDEN_RATIO -> str(R.string.button_still_max, "Golden")
    }

    open fun countdownLabel(value: CountdownDuration): String = when (value) {
        CountdownDuration.OFF -> str(R.string.button_quick_timer, "Timer")
        CountdownDuration.SECONDS_3 -> "3s"
        CountdownDuration.SECONDS_5 -> "5s"
        CountdownDuration.SECONDS_10 -> "10s"
    }

    internal open fun availabilityLabel(value: SettingsControlAvailability): String = when (value) {
        SettingsControlAvailability.SUPPORTED -> str(R.string.button_still_max, "Max")
        SettingsControlAvailability.DEGRADED -> str(R.string.button_still_fast, "Fast")
        SettingsControlAvailability.UNSUPPORTED -> str(R.string.button_still_quality_unavailable, "N/A")
    }

    open fun onOff(enabled: Boolean): String = if (enabled) {
        str(R.string.button_quick_flash, "On")
    } else {
        str(R.string.button_quick_ratio, "Off")
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
