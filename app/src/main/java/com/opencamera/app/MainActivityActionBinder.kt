package com.opencamera.app

import android.Manifest
import android.view.View
import android.widget.SeekBar
import com.opencamera.app.gesture.GestureAction
import com.opencamera.app.gesture.GestureEvent
import com.opencamera.app.gesture.GestureGuard
import com.opencamera.app.gesture.GestureGuardState
import com.opencamera.app.gesture.GesturePolicy
import com.opencamera.app.gesture.GestureRouter
import com.opencamera.app.gesture.GestureZone
import com.opencamera.app.i18n.AppTextResolver
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.SessionIntent
import com.opencamera.core.session.SessionState
import com.opencamera.core.settings.PersistedSettingsAction

internal class MainActivityActionBinder(
    private val views: MainActivityViews,
    private val snapshot: () -> MainActivityUiSnapshot,
    private val callbacks: MainActivityActionCallbacks,
    private val hasPermission: (String) -> Boolean,
    private val captureConfigDisabledReason: (SessionState) -> String?,
    private val modeTrackScrollGuard: ModeTrackScrollGuard = ModeTrackScrollGuard(scrollSlopPx = 12f)
) {
    private val gesturePolicy = GesturePolicy()
    private val gestureGuard = GestureGuard()
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
            callbacks.reducePanel(CockpitPanelCommand.DismissAll)
            callbacks.renderAfterPanelChange()
        }
        views.topBar.colorLabEntry.setOnClickListener {
            callbacks.reducePanel(CockpitPanelCommand.ToggleColorLab)
            callbacks.renderAfterPanelChange()
        }
        views.topBar.settingsEntry.setOnClickListener {
            callbacks.reducePanel(CockpitPanelCommand.ToggleSettingsRoot)
            callbacks.renderAfterPanelChange()
        }
        views.topBar.filterEntry.setOnClickListener {
            callbacks.reducePanel(CockpitPanelCommand.ToggleStyleLab)
            callbacks.renderAfterPanelChange()
        }
        views.quickPanel.launcher.setOnClickListener {
            callbacks.reducePanel(CockpitPanelCommand.ToggleQuickBubble)
            callbacks.renderAfterPanelChange()
        }
        views.floatingUtility.lowLightNightPrompt.setOnClickListener {
            callbacks.toggleLowLightNightAssist()
            callbacks.renderAfterPanelChange()
        }
        views.documentBatchRail.header.setOnClickListener {
            callbacks.reducePanel(CockpitPanelCommand.ToggleDocumentBatchOrganizer)
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
            val disabledReason = state?.let { shutterDisabledReason(it, AppTextResolver(views.bottomCockpit.shutter.context)) }
            if (disabledReason != null) {
                callbacks.showDisabledReason(disabledReason)
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
            callbacks.reducePanel(CockpitPanelCommand.CloseSettings)
            callbacks.renderAfterPanelChange()
        }
        views.settingsPanel.tabCommon.setOnClickListener {
            callbacks.reducePanel(CockpitPanelCommand.SelectSettingsTab(SettingsTab.COMMON))
            callbacks.renderAfterPanelChange()
        }
        views.settingsPanel.tabPhoto.setOnClickListener {
            callbacks.reducePanel(CockpitPanelCommand.SelectSettingsTab(SettingsTab.PHOTO))
            callbacks.renderAfterPanelChange()
        }
        views.settingsPanel.tabVideo.setOnClickListener {
            callbacks.reducePanel(CockpitPanelCommand.SelectSettingsTab(SettingsTab.VIDEO))
            callbacks.renderAfterPanelChange()
        }
        views.settingsPanel.back.setOnClickListener {
            callbacks.reducePanel(CockpitPanelCommand.SettingsBack)
            callbacks.renderLatestSettingsSurfaces()
            callbacks.renderAfterPanelChange()
        }

        // Quick panel
        views.quickPanel.grid.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.commonSection?.gridMode)
        }
        views.quickPanel.flash.setOnClickListener {
            callbacks.dispatch(SessionIntent.SecondaryActionPressed)
        }
        views.quickPanel.resolution.setOnClickListener {
            callbacks.dispatch(SessionIntent.StillCaptureResolutionToggled)
        }
        views.quickPanel.brightnessSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val snap = snapshot()
                val brightness = snap.quickPanelSheet?.brightnessRow ?: return
                val targetSteps = brightness.minSteps + progress
                callbacks.dispatch(SessionIntent.ApplyPreviewBrightness(targetSteps))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        views.quickPanel.frameRatio.setOnClickListener {
            val nextRatio = snapshot().quickPanelSheet?.frameRatioNext ?: return@setOnClickListener
            callbacks.dispatch(SessionIntent.FrameRatioSelected(nextRatio))
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
        views.settingsPanel.portraitDepthStrengthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                views.settingsPanel.portraitDepthStrengthValue.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: return
                callbacks.applySettingsAction(
                    PersistedSettingsAction.UpdatePortraitDepthStrength(progress)
                )
            }
        })

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
            callbacks.reducePanel(CockpitPanelCommand.CloseFilterLab)
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
                callbacks.applySettingsAction(callbacks.neutralColorLabAction())
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
            callbacks.reducePanel(CockpitPanelCommand.ToggleDevConsole)
            callbacks.renderAfterPanelChange()
        }
        views.devConsole.tabKey.setOnClickListener { callbacks.selectDevLogTab(DevLogTab.KEY) }
        views.devConsole.tabCore.setOnClickListener { callbacks.selectDevLogTab(DevLogTab.CORE) }
        views.devConsole.tabError.setOnClickListener { callbacks.selectDevLogTab(DevLogTab.ERROR) }
        views.devConsole.tabAll.setOnClickListener { callbacks.selectDevLogTab(DevLogTab.ALL) }
        views.devConsole.export.setOnClickListener {
            callbacks.exportDevLog()
        }
        views.devConsole.close.setOnClickListener {
            callbacks.reducePanel(CockpitPanelCommand.CloseDevConsole)
            callbacks.renderAfterPanelChange()
        }
    }

    private fun bindModeTrack() {
        val buttons = listOf(
            views.modeTrack.photo to ModeId.PHOTO,
            views.modeTrack.humanistic to ModeId.HUMANISTIC,
            views.modeTrack.night to ModeId.NIGHT,
            views.modeTrack.portrait to ModeId.PORTRAIT,
            views.modeTrack.pro to ModeId.PRO,
            views.modeTrack.video to ModeId.VIDEO,
            views.modeTrack.document to ModeId.DOCUMENT
        )
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
