package com.opencamera.app

import android.Manifest
import android.view.HapticFeedbackConstants
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
import com.opencamera.core.settings.ResetTarget

internal fun <T : Any> orderedAdvancedFilterControlBindings(
    viewsByControl: Map<FilterAdvancedControl, T>
): List<Pair<T, FilterAdvancedControl>> {
    return FilterAdvancedControl.entries.map { control ->
        requireNotNull(viewsByControl[control]) {
            "Missing view binding for advanced filter control $control"
        } to control
    }
}

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
            val activeMode = snapshot().sessionState?.activeMode
            val role = if (activeMode != null) styleSurfaceRole(activeMode) else StyleSurfaceRole.PANEL
            // 统一风格入口路由：拍照 / 人文 / 视频 / 打卡四模式下点击风格入口只激活
            // `CockpitPanelRoute.StyleLab`，由 `stylePresetCardRail` 作为唯一可见表面承载。
            // `StyleStrip` 与 `CheckInStylePanel` 不再从风格入口激活（ISSUE-001），
            // 其命令与路由保留以隔离而非删除，避免破坏既有路由器测试与非风格入口引用。
            if (role == StyleSurfaceRole.HIDDEN) return@setOnClickListener
            callbacks.reducePanel(CockpitPanelCommand.ToggleStyleLab)
            callbacks.renderAfterPanelChange()
        }
        views.quickPanel.launcher.setOnClickListener {
            callbacks.reducePanel(CockpitPanelCommand.ToggleQuickBubble)
            callbacks.renderAfterPanelChange()
        }
        views.quickPanel.content.setOnClickListener {
            callbacks.reducePanel(CockpitPanelCommand.DismissAll)
            callbacks.renderAfterPanelChange()
        }
        views.floatingUtility.lowLightNightPrompt.setOnClickListener {
            callbacks.toggleLowLightNightAssist()
            callbacks.renderAfterPanelChange()
        }
        views.documentBatchRail.overviewButton.setOnClickListener {
            callbacks.startDocumentBatchExport()
        }
        views.documentBatchOrganizer.close.setOnClickListener {
            callbacks.reducePanel(CockpitPanelCommand.CloseDocumentBatchOrganizer)
            callbacks.renderAfterPanelChange()
        }
        views.documentBatchOrganizer.continueShooting.setOnClickListener {
            callbacks.reducePanel(CockpitPanelCommand.CloseBatchOverview)
            callbacks.renderAfterPanelChange()
        }
        views.documentBatchOrganizer.exportButton.setOnClickListener {
            callbacks.startDocumentBatchExport()
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
            callbacks.reducePanel(CockpitPanelCommand.DocumentBatchCaptureTriggered)
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
            callbacks.renderAfterPanelChange()
        }

        // Quick panel
        views.quickPanel.grid.setOnClickListener {
            val settings = snapshot().sessionState?.settings?.persisted ?: return@setOnClickListener
            callbacks.applySettingsAction(PersistedSettingsAction.UpdateGridMode(
                com.opencamera.core.settings.CompositionGridMode.entries.let { modes ->
                    val idx = modes.indexOf(settings.common.gridMode)
                    modes[(idx + 1) % modes.size]
                }
            ))
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
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                callbacks.onBrightnessDragStart()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                callbacks.onBrightnessDragEnd()
            }
        })
        views.quickPanel.frameRatio.setOnClickListener {
            val nextRatio = snapshot().quickPanelSheet?.frameRatioNext ?: return@setOnClickListener
            callbacks.dispatch(SessionIntent.FrameRatioSelected(nextRatio))
        }
        views.quickPanel.watermark.setOnClickListener {
            val action = snapshot().quickPanelSheet?.watermarkAction ?: return@setOnClickListener
            callbacks.applySettingsAction(action)
        }
        views.quickPanel.livePhoto.setOnHapticClickListener {
            val snap = snapshot()
            val action = quickLivePhotoToggleAction(
                state = snap.sessionState,
                sheet = snap.quickPanelSheet
            ) ?: return@setOnHapticClickListener
            callbacks.applySettingsAction(action)
        }
        views.quickPanel.timer.setOnClickListener {
            val state = snapshot().sessionState ?: return@setOnClickListener
            val settings = state.settings.persisted
            val catalog = state.settings.catalog
            val nextAction = settings.photo.countdownDuration.let { current ->
                catalog.countdownOptions
                    .sortedBy(com.opencamera.core.settings.CountdownDuration::ordinal)
                    .let { options ->
                        val idx = options.indexOf(current)
                        if (idx >= 0 && options.size > 1) options[(idx + 1) % options.size] else null
                    }
            }?.let(PersistedSettingsAction::UpdateCountdownDuration)
            if (nextAction != null) {
                callbacks.applySettingsAction(nextAction)
            }
        }
        views.quickPanel.resetDefaults.setOnClickListener {
            val action = snapshot().quickPanelSheet?.resetQuickAction ?: return@setOnClickListener
            callbacks.applySettingsAction(action)
        }

        // Settings sub-page buttons
        views.settingsPanel.shutterSound.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.commonSection?.shutterSound)
        }
        views.settingsPanel.selfieMirror.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.commonSection?.selfieMirror)
        }
        views.settingsPanel.appLanguage.setOnClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.commonSection?.languageControl)
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
        views.settingsPanel.photoLive.setOnHapticClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.photoSection?.photoLive)
        }
        views.settingsPanel.photoLiveSaveFormat.setOnHapticClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.photoSection?.liveSaveFormat)
        }
        views.settingsPanel.photoTimer.setOnHapticClickListener {
            callbacks.applySettingsControl(snapshot().settingsPage?.photoSection?.photoTimer)
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

        views.settingsPanel.resetDefaults.setOnClickListener {
            val action = snapshot().settingsPage?.resetSettingsAction ?: return@setOnClickListener
            callbacks.applySettingsAction(action)
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
        views.filterLab.resetDefaults.setOnClickListener {
            val action = snapshot().filterLabPage?.resetStyleAction ?: return@setOnClickListener
            callbacks.applySettingsAction(action)
        }
        views.filterLab.modeToggle.setOnClickListener {
            val current = snapshot().activePanelRoute
            if (current is CockpitPanelRoute.ColorLab) {
                callbacks.applySettingsAction(callbacks.neutralColorLabAction())
            } else {
                callbacks.toggleFilterAdjustmentMode()
            }
        }
        orderedAdvancedFilterControlBindings(
            mapOf(
                FilterAdvancedControl.EXPOSURE to views.filterLab.advancedExposure,
                FilterAdvancedControl.SOFT_GLOW to views.filterLab.advancedSoftGlow,
                FilterAdvancedControl.HALO to views.filterLab.advancedHalo,
                FilterAdvancedControl.GRAIN to views.filterLab.advancedGrain,
                FilterAdvancedControl.SHARPNESS to views.filterLab.advancedSharpness,
                FilterAdvancedControl.VIGNETTE to views.filterLab.advancedVignette,
                FilterAdvancedControl.HIGHLIGHTS to views.filterLab.advancedHighlights,
                FilterAdvancedControl.SHADOWS to views.filterLab.advancedShadows,
                FilterAdvancedControl.WARM_BOOST to views.filterLab.advancedWarmBoost,
                FilterAdvancedControl.COOL_BOOST to views.filterLab.advancedCoolBoost,
                FilterAdvancedControl.TEMPERATURE_SHIFT to views.filterLab.advancedTemperatureShift,
                FilterAdvancedControl.TINT_SHIFT to views.filterLab.advancedTintShift
            )
        ).forEach { (button, control) ->
            button.setOnClickListener {
                callbacks.applyAdvancedFilterControl(control)
            }
        }
        views.filterLab.paletteSurface.setOnPaletteTouchListener { colorAxis, toneAxis ->
            callbacks.handleFilterPaletteTouch(colorAxis, toneAxis)
        }
    }

    private fun bindDevConsoleActions() {
        views.devConsole.entry.setOnHapticClickListener {
            callbacks.reducePanel(CockpitPanelCommand.ToggleDevConsole)
            callbacks.renderAfterPanelChange()
        }
        views.devConsole.tabKey.setOnHapticClickListener { callbacks.selectDevLogTab(DevLogTab.KEY) }
        views.devConsole.tabCore.setOnHapticClickListener { callbacks.selectDevLogTab(DevLogTab.CORE) }
        views.devConsole.tabError.setOnHapticClickListener { callbacks.selectDevLogTab(DevLogTab.ERROR) }
        views.devConsole.tabAll.setOnHapticClickListener { callbacks.selectDevLogTab(DevLogTab.ALL) }
        views.devConsole.export.setOnHapticClickListener {
            callbacks.exportDevLog()
        }
        views.devConsole.vendorProbe.setOnHapticClickListener {
            callbacks.triggerVendorProbe()
        }
        views.devConsole.close.setOnHapticClickListener {
            val selectedTab = snapshot().devLog?.selectedTab ?: DevLogTab.ALL
            if (selectedTab == DevLogTab.ALL) {
                callbacks.cleanupAllDevLogs()
            } else {
                callbacks.cleanupDevLogByType(selectedTab)
            }
        }
    }

    private fun bindModeTrack() {
        val buttons = listOf(
            views.modeTrack.photo to ModeId.PHOTO,
            views.modeTrack.checkIn to ModeId.CHECK_IN,
            views.modeTrack.humanistic to ModeId.HUMANISTIC,
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
                if (snapshot().activePanelRoute is CockpitPanelRoute.CheckInStylePanel) {
                    callbacks.reducePanel(CockpitPanelCommand.DismissAll)
                    callbacks.renderAfterPanelChange()
                }
                callbacks.dispatch(SessionIntent.SwitchMode(modeId))
            }
        }
        views.modeTrack.modeAction.setOnClickListener {
            callbacks.dispatch(SessionIntent.ProActionPressed)
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
            if (event !is GestureEvent.PinchZoom && event !is GestureEvent.PinchBegin) {
                gesturePolicy.syncZoomRatio(currentZoom)
            }
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
                    // EV via vertical scroll intentionally deferred:
                    // ApplyPreviewBrightness requires absolute step values and session-owned
                    // brightness tracking. A delta-only gesture without current-step feedback
                    // would produce incorrect exposure compensation. The quick panel slider
                    // already dispatches ApplyPreviewBrightness with correct absolute steps.
                }
                is GestureAction.AssistModeSwitch -> {
                    // TODO: mode track assist switch via horizontal scroll
                }
                is GestureAction.Ignore -> Unit
            }
        }
        previewView.setOnTouchListener { v, event ->
            val snap = snapshot()
            val guardState = GestureGuardState(
                activePanel = snap.activePanelRoute,
                isFilterAdjustmentActive = snap.isFilterAdjustmentVisible
            )
            if (!gestureGuard.isGestureAllowed(GestureZone.PREVIEW, guardState)) {
                // Let the touch fall through to panelDismissScrim so that an
                // outside tap dismisses the active panel instead of being lost.
                false
            } else {
                gestureRouter!!.onTouchEvent(v, event)
            }
        }
    }

    val isModeTrackScrolling: Boolean get() = modeTrackScrollGuard.isScrolling
}

private fun View.setOnHapticClickListener(onClick: (View) -> Unit) {
    setOnClickListener { view ->
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onClick(view)
    }
}
