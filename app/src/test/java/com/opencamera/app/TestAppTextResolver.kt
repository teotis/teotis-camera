package com.opencamera.app

import com.opencamera.app.i18n.AppTextResolver
import com.opencamera.core.mode.ModeId
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.PersistedSettings

class TestAppTextResolver : AppTextResolver(null) {
    override fun modeDisplayName(modeId: ModeId): String = when (modeId) {
        ModeId.PHOTO -> "Photo"
        ModeId.DOCUMENT -> "Doc"
        ModeId.NIGHT -> "Scenery"
        ModeId.HUMANISTIC -> "Human"
        ModeId.PORTRAIT -> "Port"
        ModeId.PRO -> "Pro"
        ModeId.VIDEO -> "Video"
    }

    override fun modeTrackLabel(modeId: ModeId): String = modeDisplayName(modeId)

    override fun gridModeLabel(value: CompositionGridMode): String = when (value) {
        CompositionGridMode.OFF -> "Off"
        CompositionGridMode.RULE_OF_THIRDS -> "3x3"
        CompositionGridMode.GOLDEN_RATIO -> "Golden"
    }

    override fun countdownLabel(value: CountdownDuration): String = when (value) {
        CountdownDuration.OFF -> "Off"
        CountdownDuration.SECONDS_3 -> "3s"
        CountdownDuration.SECONDS_5 -> "5s"
        CountdownDuration.SECONDS_10 -> "10s"
    }

    override fun onOff(enabled: Boolean): String = if (enabled) "On" else "Off"
    override fun shutterShort(): String = "Shutter"
    override fun ratio(): String = "Ratio"
    override fun filter(): String = "Filter"
    override fun portrait(): String = "Portrait"
    override fun watermark(): String = "Watermark"
    override fun livePhoto(): String = "Live"
    override fun timer(): String = "Timer"
    override fun flash(): String = "Flash"
    override fun lensLab(): String = "Lens Lab"
    override fun filterLab(): String = "Filter Lab"
    override fun portraitLab(): String = "Portrait Lab"
    override fun watermarkLab(): String = "Watermark Lab"
    override fun devEntry(): String = "DEV"
    override fun devRestart(): String = "Restart Session"
    override fun devClose(): String = "Close"
    override fun devExportLog(): String = "Export Log"
    override fun devTabKey(): String = "Key"
    override fun devTabCore(): String = "Core"
    override fun devTabError(): String = "Error"
    override fun devTabAll(): String = "All"
    override fun quickLauncher(): String = "Quick"
    override fun moreControls(): String = "More"
    override fun stopSession(): String = "Stop Session"
    override fun restartSession(): String = "Restart Session"
    override fun closeSettings(): String = "Close Lens Lab"
    override fun closeFilter(): String = "Close Filter Lab"
    override fun permissionPending(): String = "Camera permission is required before preview can start."
    override fun permissionGranted(): String = "Camera permission granted. Preview is ready."
    override fun permissionCameraOnly(): String = "Camera granted. Video recording will be silent until microphone permission is granted."
    override fun permissionDenied(): String = "Camera permission denied. Tap shutter to request it again."
    override fun outputWaiting(): String = "No photo captured yet."
    override fun outputSavedPrefix(): String = "Last photo:"
    override fun outputVideoPrefix(): String = "Last video:"
    override fun outputLivePrefix(): String = "Last Live photo:"
    override fun outputErrorPrefix(): String = "Camera issue:"
    override fun outputPreviewPrefix(): String = "Preview thumbnail:"
    override fun settingsSectionCommon(): String = "General"
    override fun settingsSectionPhoto(): String = "Photo"
    override fun settingsSectionVideo(): String = "Video"
    override fun switchLens(): String = "Switch Lens"
    override fun zoomPrefix(): String = "Zoom"
    override fun zoomUnavailable(): String = "Zoom N/A"
    override fun stillQuality(): String = "Still Quality"
    override fun stillFast(): String = "Still Fast"
    override fun stillMax(): String = "Still Max"
    override fun stillQualityUnavailable(): String = "Still N/A"
    override fun stillResolution(): String = "Still Size"
    override fun still12Mp(): String = "Still 12MP"
    override fun still8Mp(): String = "Still 8MP"
    override fun still2Mp(): String = "Still 2MP"
    override fun stillResolutionUnavailable(): String = "Size N/A"
    override fun switchToFront(): String = "Switch to Front"
    override fun switchToBack(): String = "Switch to Back"
    override fun singleLens(): String = "Single Lens"
    override fun tone(): String = "Tone"
    override fun languageDisplayName(settings: PersistedSettings): String = "English"

    override fun sessionUiStrings(): SessionUiStrings {
        return SessionUiStrings(
            buttonSwitchToFront = "Switch to Front",
            buttonSwitchToBack = "Switch to Back",
            buttonSingleLens = "Single Lens",
            buttonZoomPrefix = "Zoom",
            buttonZoomUnavailable = "Zoom N/A",
            buttonStillFast = "Still Fast",
            buttonStillMax = "Still Max",
            buttonStillQualityUnavailable = "Still N/A",
            buttonStill12Mp = "Still 12MP",
            buttonStill8Mp = "Still 8MP",
            buttonStill2Mp = "Still 2MP",
            buttonStillResolutionUnavailable = "Size N/A",
            outputErrorPrefix = "Camera issue:",
            outputVideoPrefix = "Last video:",
            outputLivePrefix = "Last Live photo:",
            outputSavedPrefix = "Last photo:",
            outputPreviewPrefix = "Preview thumbnail:",
            outputWaiting = "No photo captured yet."
        )
    }
}
