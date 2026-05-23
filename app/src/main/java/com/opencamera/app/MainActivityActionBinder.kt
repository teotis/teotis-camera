package com.opencamera.app

import android.Manifest
import android.view.View
import com.opencamera.app.gesture.GestureAction
import com.opencamera.app.gesture.GestureEvent
import com.opencamera.app.gesture.GestureGuard
import com.opencamera.app.gesture.GestureGuardState
import com.opencamera.app.gesture.GesturePolicy
import com.opencamera.app.gesture.GestureRouter
import com.opencamera.app.gesture.GestureZone
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.SessionIntent
import com.opencamera.core.session.SessionState

internal class MainActivityActionBinder(
    private val views: MainActivityViews,
    private val snapshot: () -> MainActivityUiSnapshot,
    private val callbacks: MainActivityActionCallbacks,
    private val hasPermission: (String) -> Boolean,
    private val captureConfigDisabledReason: (SessionState) -> String?
) {
    private val gesturePolicy = GesturePolicy()
    private val gestureGuard = GestureGuard()
    private val modeTrackScrollGuard = ModeTrackScrollGuard(scrollSlopPx = 12f)
    private var gestureRouter: GestureRouter? = null

    fun bind() {
        bindPanelActions()
        bindCaptureActions()
        bindSettingsActions()
        bindFilterActions()
        bindDevConsoleActions()
        bindModeTrack()
        bindPreviewGestures()
    }

    private fun bindPanelActions() {
        views.panelDismissScrim.setOnClickListener {
            callbacks.setPanelRoute(CockpitPanelRoute.None)
            callbacks.renderAfterPanelChange()
        }
        views.topBar.colorLabEntry.setOnClickListener {
            val current = snapshot().activePanelRoute
            val next = if (current is CockpitPanelRoute.ColorLab) {
                CockpitPanelRoute.None
            } else {
                CockpitPanelRoute.ColorLab
            }
            callbacks.setPanelRoute(next)
            callbacks.renderAfterPanelChange()
        }
        views.topBar.settingsEntry.setOnClickListener {
            val current = snapshot().activePanelRoute
            val next = if (current.isSettingsOpen) {
                CockpitPanelRoute.None
            } else {
                CockpitPanelRoute.Settings()
            }
            callbacks.setPanelRoute(next)
            callbacks.renderAfterPanelChange()
        }
        views.topBar.filterEntry.setOnClickListener {
            val current = snapshot().activePanelRoute
            val next = if (current is CockpitPanelRoute.StyleLab) {
                CockpitPanelRoute.None
            } else {
                CockpitPanelRoute.StyleLab
            }
            callbacks.setPanelRoute(next)
            callbacks.renderAfterPanelChange()
        }
        views.quickPanel.launcher.setOnClickListener {
            val current = snapshot().activePanelRoute
            val next = if (current is CockpitPanelRoute.QuickBubble) {
                CockpitPanelRoute.None
            } else {
                CockpitPanelRoute.QuickBubble
            }
            callbacks.setPanelRoute(next)
            callbacks.renderAfterPanelChange()
        }
    }

    private fun bindCaptureActions() {
        views.bottomCockpit.shutter.setOnClickListener {
            val state = snapshot().sessionState
            if (state?.permissionState?.cameraGranted != true) {
                callbacks.requestCameraPermissionIfNeeded()
                return@setOnClickListener
            }
            callbacks.dispatch(SessionIntent.ShutterPressed)
        }
        views.bottomCockpit.lensFacing.setOnClickListener {
            callbacks.dispatch(SessionIntent.LensFacingToggled)
        }
        views.preview.thumbnail.setOnClickListener {
            callbacks.openLatestGalleryMedia()
        }
    }

    private fun bindSettingsActions() {
        views.settingsPanel.close.setOnClickListener {
            callbacks.setPanelRoute(CockpitPanelRoute.None)
            callbacks.renderAfterPanelChange()
        }
        views.settingsPanel.tabCommon.setOnClickListener {
            callbacks.renderAfterPanelChange()
        }
        views.settingsPanel.tabPhoto.setOnClickListener {
            callbacks.renderAfterPanelChange()
        }
        views.settingsPanel.tabVideo.setOnClickListener {
            callbacks.renderAfterPanelChange()
        }
        views.settingsPanel.back.setOnClickListener {
            val current = snapshot().activePanelRoute as? CockpitPanelRoute.Settings
                ?: return@setOnClickListener
            callbacks.setPanelRoute(when (current.subpage) {
                SettingsSubpage.WATERMARK_DETAIL -> CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_SELECTOR)
                SettingsSubpage.PORTRAIT_LAB,
                SettingsSubpage.WATERMARK_SELECTOR -> CockpitPanelRoute.Settings()
                else -> CockpitPanelRoute.Settings()
            })
            callbacks.renderLatestSettingsSurfaces()
            callbacks.renderAfterPanelChange()
        }

        // Quick panel
        views.quickPanel.grid.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.commonSection?.gridMode)
        }
        views.quickPanel.flash.setOnClickListener {
            callbacks.dispatch(SessionIntent.StillCaptureQualityToggled)
        }
        views.quickPanel.frame43.setOnClickListener {
            callbacks.dispatch(SessionIntent.FrameRatioSelected(FrameRatio.RATIO_4_3))
        }
        views.quickPanel.frame169.setOnClickListener {
            callbacks.dispatch(SessionIntent.FrameRatioSelected(FrameRatio.RATIO_16_9))
        }
        views.quickPanel.frame11.setOnClickListener {
            callbacks.dispatch(SessionIntent.FrameRatioSelected(FrameRatio.RATIO_1_1))
        }
        views.quickPanel.livePhoto.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.photoSection?.livePhoto)
        }
        views.quickPanel.timer.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.photoSection?.countdown)
        }

        // Settings sub-page buttons
        views.settingsPanel.gridMode.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.commonSection?.gridMode)
        }
        views.settingsPanel.shutterSound.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.commonSection?.shutterSound)
        }
        views.settingsPanel.selfieMirror.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.commonSection?.selfieMirror)
        }
        views.settingsPanel.photoFilter.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.photoSection?.defaultFilter)
        }
        views.settingsPanel.photoPortraitLab.setOnClickListener {
            callbacks.openPortraitLab()
        }
        views.settingsPanel.photoWatermark.setOnClickListener {
            callbacks.openWatermarkLabSelector()
        }
        views.settingsPanel.photoLive.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.photoSection?.livePhoto)
        }
        views.settingsPanel.photoTimer.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.photoSection?.countdown)
        }
        views.settingsPanel.videoResolution.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.videoSection?.resolution)
        }
        views.settingsPanel.videoFrameRate.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.videoSection?.frameRate)
        }
        views.settingsPanel.videoDynamicFps.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.videoSection?.dynamicFps)
        }
        views.settingsPanel.videoAudio.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.videoSection?.audioProfile)
        }
        views.settingsPanel.videoFilter.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.videoSection?.defaultFilter)
        }

        // Portrait lab
        views.settingsPanel.portraitProfile.setOnClickListener {
            callbacks.applySettingsControl(snapshot().portraitLabPage?.profileControl)
        }
        views.settingsPanel.portraitBeautyPreset.setOnClickListener {
            callbacks.applySettingsControl(snapshot().portraitLabPage?.beautyPresetControl)
        }
        views.settingsPanel.portraitBeautyStrength.setOnClickListener {
            callbacks.applySettingsControl(snapshot().portraitLabPage?.beautyStrengthControl)
        }
        views.settingsPanel.portraitBokehEffect.setOnClickListener {
            callbacks.applySettingsControl(snapshot().portraitLabPage?.bokehEffectControl)
        }

        // Watermark detail
        views.settingsPanel.watermarkPlacement.setOnClickListener {
            callbacks.applySettingsControl(snapshot().watermarkDetailPage?.placementControl)
        }
        views.settingsPanel.watermarkTextScale.setOnClickListener {
            callbacks.applySettingsControl(snapshot().watermarkDetailPage?.textScaleControl)
        }
        views.settingsPanel.watermarkTextOpacity.setOnClickListener {
            callbacks.applySettingsControl(snapshot().watermarkDetailPage?.textOpacityControl)
        }
        views.settingsPanel.watermarkFrameBackground.setOnClickListener {
            callbacks.applySettingsControl(snapshot().watermarkDetailPage?.frameBackgroundControl)
        }
    }

    private fun bindFilterActions() {
        views.filterLab.close.setOnClickListener {
            callbacks.setPanelRoute(CockpitPanelRoute.None)
            callbacks.renderAfterPanelChange()
        }
        views.filterLab.photoTab.setOnClickListener {
            callbacks.selectFilterLabFamily(FilterLabFamily.PHOTO)
        }
        views.filterLab.humanisticTab.setOnClickListener {
            callbacks.selectFilterLabFamily(FilterLabFamily.HUMANISTIC)
        }
        views.filterLab.portraitTab.setOnClickListener {
            callbacks.selectFilterLabFamily(FilterLabFamily.PORTRAIT)
        }
        views.filterLab.videoTab.setOnClickListener {
            callbacks.selectFilterLabFamily(FilterLabFamily.VIDEO)
        }
        views.filterLab.saveCustom.setOnClickListener {
            callbacks.saveCurrentFilterAsCustom(snapshot().filterLabPage?.saveCustomControl)
        }
        views.filterLab.modeToggle.setOnClickListener {
            val current = snapshot().activePanelRoute
            if (current is CockpitPanelRoute.ColorLab) {
                callbacks.applySettingsAction(neutralColorLabAction())
            } else {
                callbacks.toggleFilterAdjustmentMode()
            }
        }
        views.filterLab.advancedExposure.setOnClickListener {
            callbacks.applyAdvancedFilterControl(FilterAdvancedControl.EXPOSURE)
        }
        views.filterLab.advancedSoftGlow.setOnClickListener {
            callbacks.applyAdvancedFilterControl(FilterAdvancedControl.SOFT_GLOW)
        }
        views.filterLab.advancedHalo.setOnClickListener {
            callbacks.applyAdvancedFilterControl(FilterAdvancedControl.HALO)
        }
        views.filterLab.advancedGrain.setOnClickListener {
            callbacks.applyAdvancedFilterControl(FilterAdvancedControl.GRAIN)
        }
        views.filterLab.advancedSharpness.setOnClickListener {
            callbacks.applyAdvancedFilterControl(FilterAdvancedControl.SHARPNESS)
        }
        views.filterLab.advancedVignette.setOnClickListener {
            callbacks.applyAdvancedFilterControl(FilterAdvancedControl.VIGNETTE)
        }
        views.filterLab.advancedHighlights.setOnClickListener {
            callbacks.applyAdvancedFilterControl(FilterAdvancedControl.HIGHLIGHTS)
        }
        views.filterLab.advancedShadows.setOnClickListener {
            callbacks.applyAdvancedFilterControl(FilterAdvancedControl.SHADOWS)
        }
        views.filterLab.advancedWarmBoost.setOnClickListener {
            callbacks.applyAdvancedFilterControl(FilterAdvancedControl.WARM_BOOST)
        }
        views.filterLab.advancedCoolBoost.setOnClickListener {
            callbacks.applyAdvancedFilterControl(FilterAdvancedControl.COOL_BOOST)
        }
        views.filterLab.advancedTemperatureShift.setOnClickListener {
            callbacks.applyAdvancedFilterControl(FilterAdvancedControl.TEMPERATURE_SHIFT)
        }
        views.filterLab.advancedTintShift.setOnClickListener {
            callbacks.applyAdvancedFilterControl(FilterAdvancedControl.TINT_SHIFT)
        }
        views.filterLab.paletteSurface.setOnPaletteTouchListener { colorAxis, toneAxis ->
            callbacks.handleFilterPaletteTouch(colorAxis, toneAxis)
        }
    }

    private fun bindDevConsoleActions() {
        views.devConsole.entry.setOnClickListener {
            val current = snapshot().activePanelRoute
            val next = if (current is CockpitPanelRoute.DevConsole) {
                CockpitPanelRoute.None
            } else {
                CockpitPanelRoute.DevConsole
            }
            callbacks.setPanelRoute(next)
            callbacks.renderAfterPanelChange()
        }
        views.devConsole.tabKey.setOnClickListener {
            callbacks.refreshDevLogModel()
        }
        views.devConsole.tabCore.setOnClickListener {
            callbacks.refreshDevLogModel()
        }
        views.devConsole.tabError.setOnClickListener {
            callbacks.refreshDevLogModel()
        }
        views.devConsole.tabAll.setOnClickListener {
            callbacks.refreshDevLogModel()
        }
        views.devConsole.export.setOnClickListener {
            callbacks.exportDevLog()
        }
        views.devConsole.close.setOnClickListener {
            callbacks.setPanelRoute(CockpitPanelRoute.None)
            callbacks.renderAfterPanelChange()
        }
    }

    private fun bindModeTrack() {
        val buttons = listOf(
            views.modeTrack.photo to ModeId.PHOTO,
            views.modeTrack.night to ModeId.NIGHT,
            views.modeTrack.portrait to ModeId.PORTRAIT,
            views.modeTrack.pro to ModeId.PRO,
            views.modeTrack.video to ModeId.VIDEO,
            views.modeTrack.document to ModeId.DOCUMENT
        )
        views.modeTrack.humanistic.visibility = View.GONE
        views.modeTrack.humanistic.setOnClickListener(null)
        modeTrackScrollGuard.attach(views.modeTrack.scroll)
        buttons.forEach { (button, modeId) ->
            button.setOnClickListener {
                if (modeTrackScrollGuard.isScrolling) return@setOnClickListener
                val state = snapshot().sessionState
                if (state != null) {
                    val reason = captureConfigDisabledReason(state)
                    if (reason != null) {
                        callbacks.showDisabledReason(reason)
                        return@setOnClickListener
                    }
                }
                if (modeId == ModeId.VIDEO && !hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    callbacks.requestMicrophonePermission()
                }
                callbacks.dispatch(SessionIntent.SwitchMode(modeId))
            }
        }
    }

    private fun bindPreviewGestures() {
        val previewView = views.preview.previewView
        val overlayView = views.preview.overlayView
        gestureRouter = GestureRouter(previewView.context) { event ->
            val snap = snapshot()
            val guardState = GestureGuardState(
                activePanel = snap.activePanelRoute,
                isFilterAdjustmentActive = snap.isFilterAdjustmentVisible
            )
            if (!gestureGuard.isGestureAllowed(GestureZone.PREVIEW, guardState)) {
                return@GestureRouter
            }
            if (event is GestureEvent.HorizontalScroll && !gestureGuard.isHorizontalScrollAllowed(guardState)) {
                return@GestureRouter
            }
            val activeMode = snap.sessionState?.activeMode ?: return@GestureRouter
            val currentZoom = snap.sessionState?.activeDeviceGraph?.preview?.zoomRatio ?: 1.0f
            when (val action = gesturePolicy.map(event, activeMode, currentZoom)) {
                is GestureAction.DispatchSession -> callbacks.dispatch(action.intent)
                is GestureAction.FocusAt -> {
                    val tap = normalizedPreviewTapOrNull(
                        tapX = action.x,
                        tapY = action.y,
                        viewWidth = previewView.width,
                        viewHeight = previewView.height,
                        activeFrameRect = overlayView.currentActiveFrameRectOrNull()
                    ) ?: return@GestureRouter
                    callbacks.dispatch(
                        SessionIntent.PreviewTapToFocus(
                            normalizedX = tap.x,
                            normalizedY = tap.y
                        )
                    )
                }
                is GestureAction.ShowExposureHint -> {
                    // TODO: exposure adjustment via vertical scroll
                }
                is GestureAction.AssistModeSwitch -> {
                    // TODO: mode track assist switch via horizontal scroll
                }
                is GestureAction.Ignore -> Unit
            }
        }
        previewView.setOnTouchListener { v, event ->
            gestureRouter!!.onTouchEvent(v, event)
        }
    }

    val isModeTrackScrolling: Boolean get() = modeTrackScrollGuard.isScrolling
}
