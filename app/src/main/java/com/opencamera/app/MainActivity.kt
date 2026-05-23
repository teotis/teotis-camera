package com.opencamera.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.opencamera.app.neutralColorLabAction as neutralColorLabTopLevel
import com.opencamera.app.i18n.AppTextResolver
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.renderUriOrNull
import com.opencamera.core.mode.ModeId
import com.opencamera.core.session.SessionIntent
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PersistedSettingsAction
import kotlinx.coroutines.launch
import java.io.File

@Suppress("EXPOSED_PARAMETER_TYPE")
class MainActivity : AppCompatActivity(), MainActivityActionCallbacks {
    private val container: AppContainer
        get() = (application as OpenCameraApplication).container

    private lateinit var views: MainActivityViews
    private val shutterClickSound = MediaActionSound()
    private var lastRequestedThumbnailUri: String? = null
    private var lastPlayedShutterSoundShotId: String? = null
    private val panelRouter = CockpitPanelRouter()
    private val panelState: CockpitPanelUiState
        get() = panelRouter.state
    private val activePanelRoute: CockpitPanelRoute
        get() = panelState.route
    private var selectedDevLogTab = DevLogTab.KEY
    private var latestDevLogRenderModel: DevLogRenderModel? = null
    private lateinit var devLogExporter: DevLogExporter
    private var latestSettingsPageRenderModel: SessionSettingsPageRenderModel? = null
    private var latestPortraitLabRenderModel: PortraitLabPageRenderModel? = null
    private var latestWatermarkLabSelectorRenderModel: WatermarkLabSelectorRenderModel? = null
    private var latestWatermarkLabDetailRenderModel: WatermarkLabDetailRenderModel? = null
    private var latestFilterLabRenderModel: FilterLabPageRenderModel? = null
    private var latestSessionState: SessionState? = null
    private var lightPaletteBaseSpec: FilterRenderSpec? = null

    // Renderers (initialized in onCreate after views)
    private lateinit var cockpitRenderer: CockpitSurfaceRenderer
    private lateinit var settingsRenderer: SettingsPanelRenderer
    private lateinit var filterLabRenderer: FilterLabPanelRenderer
    private lateinit var devConsoleRenderer: DevConsoleRenderer
    private lateinit var mainRenderer: MainActivityRenderer
    private lateinit var actionBinder: MainActivityActionBinder

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val cameraGranted = result[Manifest.permission.CAMERA] ?: hasPermission(Manifest.permission.CAMERA)
        val micGranted = result[Manifest.permission.RECORD_AUDIO] ?: hasPermission(Manifest.permission.RECORD_AUDIO)
        syncPermissionState(cameraGranted, micGranted)

        if (cameraGranted) {
            views.topBar.permissionStatus.text = if (micGranted) {
                getString(R.string.permission_granted)
            } else {
                getString(R.string.permission_camera_only)
            }
        } else {
            views.topBar.permissionStatus.text = getString(R.string.permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        views = MainActivityViews.bind(this)
        initRenderers()
        devLogExporter = DevLogExporter(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.topPanel)) { v, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, statusBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomSheet)) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBars.bottom)
            insets
        }

        actionBinder = MainActivityActionBinder(
            views = views,
            snapshot = ::buildUiSnapshot,
            callbacks = this,
            hasPermission = ::hasPermission,
            captureConfigDisabledReason = ::captureConfigDisabledReason
        )
        actionBinder.bind()
        bindState()
        syncPermissionState()
        applyControlRotationForDisplay()
    }

    private fun initRenderers() {
        cockpitRenderer = CockpitSurfaceRenderer(
            context = this,
            topBar = views.topBar,
            quickPanel = views.quickPanel,
            bottomCockpit = views.bottomCockpit,
            modeTrack = views.modeTrack,
            preview = views.preview,
            callbacks = CockpitCallbacks(
                onZoomRatioSelected = { ratio -> dispatch(SessionIntent.ApplyZoomRatio(ratio)) }
            )
        )
        settingsRenderer = SettingsPanelRenderer(
            this, views.settingsPanel,
            onApplySettingsAction = ::applySettingsAction,
            onOpenWatermarkDetail = ::openWatermarkLabDetail
        )
        filterLabRenderer = FilterLabPanelRenderer(
            context = this,
            views = views.filterLab,
            onOpenAdjustment = { control -> openSelectedFilterAdjustment(control) },
            onSelectFilter = { action -> applySettingsAction(action) },
            isFilterAdjustmentVisible = { panelState.isFilterAdjustmentVisible }
        )
        devConsoleRenderer = DevConsoleRenderer(this, views.devConsole)
        mainRenderer = MainActivityRenderer(
            views, cockpitRenderer, settingsRenderer, filterLabRenderer, devConsoleRenderer
        )
    }

    private fun applyControlRotationForDisplay() {
        val rotation = display?.rotation ?: android.view.Surface.ROTATION_0
        val orientationModel = orientationRenderModel(rotation)
        val degrees = orientationModel.controlRotationDegrees
        listOf(
            // Right rail utility buttons
            views.topBar.filterEntry,
            views.quickPanel.launcher,
            views.devConsole.entry,
            // Bottom cockpit controls
            views.bottomCockpit.shutter,
            views.bottomCockpit.lensFacing,
            // Quick panel text-bearing buttons
            views.quickPanel.frame43,
            views.quickPanel.frame169,
            views.quickPanel.frame11,
            views.settingsPanel.gridMode
        ).forEach { it.rotation = degrees }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyControlRotationForDisplay()
    }

    override fun onStart() {
        super.onStart()
        container.cameraCoordinator.attachPreviewHost(this, views.preview.previewView)
        syncPermissionState()
        dispatch(SessionIntent.Boot)
        dispatch(SessionIntent.PreviewHostAttached)
        requestCameraPermissionIfNeeded()
    }

    override fun onStop() {
        super.onStop()
        dispatch(SessionIntent.PreviewHostDetached("Activity moved to background"))
    }

    private fun applyLocale(settings: com.opencamera.core.settings.PersistedSettings) {
        val language = settings.common.appLanguage
        val localeList = LocaleListCompat.forLanguageTags(language.storageKey)
        if (AppCompatDelegate.getApplicationLocales() != localeList) {
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }

    private fun bindState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.cameraSession.state.collect(::render)
            }
        }
    }

    private fun render(state: SessionState) {
        latestSessionState = state
        val text = AppTextResolver(this)
        applyLocale(state.settings.persisted)
        val controls = sessionControlsRenderModel(state, text.sessionUiStrings())
        val settingsPage = sessionSettingsPageRenderModel(state, text)
        val portraitLabPage = portraitLabPageRenderModel(state, text)
        val watermarkSelectorPage = watermarkLabSelectorRenderModel(state, text)
        val watermarkDetailPage = watermarkLabDetailRenderModel(
            state = state,
            templateId = panelState.selectedWatermarkDetailTemplateId
                ?: state.settings.persisted.photo.defaultWatermarkTemplateId,
            text = text
        )
        val filterLabPage = filterLabPageRenderModel(
            state = state,
            text = text,
            selectedFamily = selectedFilterLabFamily(state),
            panelRole = if (activePanelRoute is CockpitPanelRoute.ColorLab) {
                StyleAndColorLabRole.COLOR_LAB
            } else {
                StyleAndColorLabRole.STYLE
            },
            showAdjustmentPanel = panelState.isFilterAdjustmentVisible,
            adjustmentMode = panelState.filterAdjustmentMode
        )
        if (activePanelRoute is CockpitPanelRoute.ColorLab) {
            val colorLabModel = colorLabPanelRenderModel(state, text)
            views.filterLab.paletteSurface.updateReticle(colorLabModel.colorAxis, colorLabModel.toneAxis)
        }
        val modeTrack = modeTrackRenderModel(state, text)
        latestSettingsPageRenderModel = settingsPage
        latestPortraitLabRenderModel = portraitLabPage
        latestWatermarkLabSelectorRenderModel = watermarkSelectorPage
        latestWatermarkLabDetailRenderModel = watermarkDetailPage
        latestFilterLabRenderModel = filterLabPage
        // Top panel: lightweight primary status
        cockpitRenderer.renderTopTitle()
        cockpitRenderer.renderModeTrack(modeTrack)
        settingsRenderer.renderPage(settingsPage)
        settingsRenderer.renderTabs(panelState.selectedSettingsTab)
        settingsRenderer.renderPortraitLabPage(portraitLabPage)
        settingsRenderer.renderWatermarkSelectorPage(watermarkSelectorPage)
        settingsRenderer.renderWatermarkDetailPage(watermarkDetailPage)
        filterLabRenderer.renderPage(filterLabPage)
        mainRenderer.renderPanelVisibility(activePanelRoute)
        views.preview.overlayView.render(previewOverlayRenderModel(state, container.previewEffectAdapter))
        cockpitRenderer.renderPreviewMirror(state)
        maybePlayShutterSound(state)

        cockpitRenderer.renderShutter(state, controls)
        cockpitRenderer.renderCaptureOutput(sessionCaptureOutputText(state, sessionUiStrings()))
        cockpitRenderer.renderZoomCapsules(controls)
        val sheet = quickPanelSheetRenderModel(state, text, sessionUiStrings())
        cockpitRenderer.renderQuickBubble(settingsPage, sheet)
        mainRenderer.renderDevEntryVisibility(com.opencamera.app.BuildConfig.DEBUG)
        val devLogModel = devLogRenderModel(
            state = state,
            traceEvents = container.trace.snapshot(),
            isDebugBuild = com.opencamera.app.BuildConfig.DEBUG,
            selectedTab = selectedDevLogTab,
            text = text
        )
        latestDevLogRenderModel = devLogModel
        devConsoleRenderer.renderVisibility(activePanelRoute)
        devConsoleRenderer.render(devLogModel)

        val nextThumbnailRenderUri = state.presentation.pendingCaptureFeedback?.let { feedback ->
            feedback.outputPath.takeIf { File(it).isAbsolute }?.let { File(it).toURI().toString() }
        } ?: state.presentation.latestThumbnailSource?.renderUriOrNull()
        when (val command = nextThumbnailRenderCommand(lastRequestedThumbnailUri, nextThumbnailRenderUri)) {
            ThumbnailRenderCommand.NoOp -> Unit
            ThumbnailRenderCommand.Clear -> {
                lastRequestedThumbnailUri = null
                views.preview.thumbnail.setImageDrawable(null)
            }
            is ThumbnailRenderCommand.Load -> {
                lastRequestedThumbnailUri = command.uri
                views.preview.thumbnail.setImageURI(null)
                views.preview.thumbnail.setImageURI(Uri.parse(command.uri))
            }
        }

    }

    private fun renderSettingsPage(model: SessionSettingsPageRenderModel) {
        views.settingsPanel.headline.text = model.headline
        views.settingsPanel.supportingText.text = model.supportingText
        views.settingsPanel.heroSummary.text = model.heroSummary
        views.settingsPanel.heroSummary.isVisible = model.heroSummary.isNotEmpty()
        views.settingsPanel.commonSummary.text = model.commonSection.summary
        views.settingsPanel.commonSummary.isVisible = model.commonSection.summary.isNotEmpty()
        views.settingsPanel.photoSummary.text = model.photoSection.summary
        views.settingsPanel.photoSummary.isVisible = model.photoSection.summary.isNotEmpty()
        views.settingsPanel.videoSummary.text = model.videoSection.summary
        views.settingsPanel.videoSummary.isVisible = model.videoSection.summary.isNotEmpty()
        views.settingsPanel.catalogFooter.text = model.catalogFooter
        views.settingsPanel.catalogFooter.isVisible = model.catalogFooter.isNotEmpty()
        views.settingsPanel.editingHint.text = model.editingHint
        renderSettingsControl(views.settingsPanel.gridMode, model.commonSection.gridMode, model.editingEnabled)
        renderSettingsControl(views.settingsPanel.shutterSound, model.commonSection.shutterSound, model.editingEnabled)
        renderSettingsControl(views.settingsPanel.selfieMirror, model.commonSection.selfieMirror, model.editingEnabled)
        renderSettingsControl(views.settingsPanel.photoFilter, model.photoSection.defaultFilter, model.editingEnabled)
        views.settingsPanel.photoPortraitLab.text = model.photoSection.portraitLab.buttonLabel
        views.settingsPanel.photoPortraitLab.isEnabled = model.editingEnabled &&
            model.photoSection.portraitLab.availability != SettingsControlAvailability.UNSUPPORTED
        views.settingsPanel.photoWatermark.text = model.photoSection.watermarkTemplate.buttonLabel
        views.settingsPanel.photoWatermark.isEnabled = model.editingEnabled &&
            model.photoSection.watermarkTemplate.availability != SettingsControlAvailability.UNSUPPORTED
        renderSettingsControl(views.settingsPanel.photoLive, model.photoSection.livePhoto, model.editingEnabled)
        renderSettingsControl(views.settingsPanel.photoTimer, model.photoSection.countdown, model.editingEnabled)
        renderSettingsControl(views.settingsPanel.videoResolution, model.videoSection.resolution, model.editingEnabled)
        renderSettingsControl(views.settingsPanel.videoFrameRate, model.videoSection.frameRate, model.editingEnabled)
        renderSettingsControl(views.settingsPanel.videoDynamicFps, model.videoSection.dynamicFps, model.editingEnabled)
        renderSettingsControl(views.settingsPanel.videoAudio, model.videoSection.audioProfile, model.editingEnabled)
        renderSettingsControl(views.settingsPanel.videoFilter, model.videoSection.defaultFilter, model.editingEnabled)
        renderSettingsTabs()
        renderPanelVisibility()
    }

    private fun renderSettingsTabs() {
        val selectedTab = panelState.selectedSettingsTab
        views.settingsPanel.tabCommon.isEnabled = selectedTab != SettingsTab.COMMON
        views.settingsPanel.tabPhoto.isEnabled = selectedTab != SettingsTab.PHOTO
        views.settingsPanel.tabVideo.isEnabled = selectedTab != SettingsTab.VIDEO
        views.settingsPanel.tabCommon.alpha = if (selectedTab == SettingsTab.COMMON) 1f else 0.84f
        views.settingsPanel.tabPhoto.alpha = if (selectedTab == SettingsTab.PHOTO) 1f else 0.84f
        views.settingsPanel.tabVideo.alpha = if (selectedTab == SettingsTab.VIDEO) 1f else 0.84f
        views.settingsPanel.commonSection.isVisible = selectedTab == SettingsTab.COMMON
        views.settingsPanel.photoSection.isVisible = selectedTab == SettingsTab.PHOTO
        views.settingsPanel.videoSection.isVisible = selectedTab == SettingsTab.VIDEO
    }

    private fun renderPortraitLabPage(model: PortraitLabPageRenderModel) {
        views.settingsPanel.portraitHeadline.text = model.headline
        views.settingsPanel.portraitSupportingText.text = model.supportingText
        views.settingsPanel.portraitHeroSummary.text = model.heroSummary
        views.settingsPanel.portraitHeroSummary.isVisible = model.heroSummary.isNotEmpty()
        views.settingsPanel.portraitEditingHint.text = model.editingHint
        views.settingsPanel.portraitFooter.text = model.footer
        renderSettingsControl(views.settingsPanel.portraitProfile, model.profileControl, model.editingEnabled)
        renderSettingsControl(
            views.settingsPanel.portraitBeautyPreset,
            model.beautyPresetControl,
            model.editingEnabled
        )
        renderSettingsControl(
            views.settingsPanel.portraitBeautyStrength,
            model.beautyStrengthControl,
            model.editingEnabled
        )
        renderSettingsControl(
            views.settingsPanel.portraitBokehEffect,
            model.bokehEffectControl,
            model.editingEnabled
        )
        renderPanelVisibility()
    }

    private fun renderWatermarkLabSelectorPage(model: WatermarkLabSelectorRenderModel) {
        views.settingsPanel.watermarkSelectorHeadline.text = model.headline
        views.settingsPanel.watermarkSelectorSupportingText.text = model.supportingText
        views.settingsPanel.watermarkSelectorHeroSummary.text = model.heroSummary
        views.settingsPanel.watermarkSelectorHeroSummary.isVisible = model.heroSummary.isNotEmpty()
        views.settingsPanel.watermarkSelectorEditingHint.text = model.editingHint
        views.settingsPanel.watermarkSelectorFooter.text = model.footer
        views.settingsPanel.watermarkSelectorList.removeAllViews()
        model.items.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_settings_card)
                alpha = if (item.isSelected) 1f else 0.92f
                setPadding(14.dp, 14.dp, 14.dp, 14.dp)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (views.settingsPanel.watermarkSelectorList.childCount == 0) 0 else 8.dp
            }
            val title = TextView(this).apply {
                text = item.title
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.oc_text_primary))
            }
            card.addView(title)
            val supporting = TextView(this).apply {
                text = item.supportingText
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.oc_text_secondary))
                setPadding(0, 6.dp, 0, 0)
            }
            card.addView(supporting)
            item.useAction?.let { action ->
                val useButton = Button(
                    this,
                    null,
                    0,
                    R.style.Widget_OpenCamera_CompactButton
                ).apply {
                    text = getString(R.string.button_use_this_template)
                    isAllCaps = false
                    isEnabled = model.editingEnabled
                    setOnClickListener { applySettingsAction(action) }
                }
                val useParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 10.dp
                }
                card.addView(useButton, useParams)
            }
            item.editButtonLabel?.let { label ->
                val editButton = Button(
                    this,
                    null,
                    0,
                    R.style.Widget_OpenCamera_CompactButton
                ).apply {
                    text = label
                    isAllCaps = false
                    isEnabled = model.editingEnabled
                    setOnClickListener { openWatermarkLabDetail(item.templateId) }
                }
                val editParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 10.dp
                }
                card.addView(editButton, editParams)
            }
            views.settingsPanel.watermarkSelectorList.addView(card, params)
        }
        renderPanelVisibility()
    }

    private fun renderWatermarkLabDetailPage(model: WatermarkLabDetailRenderModel) {
        views.settingsPanel.watermarkDetailHeadline.text = model.headline
        views.settingsPanel.watermarkDetailSupportingText.text = model.supportingText
        views.settingsPanel.watermarkDetailHeroSummary.text = model.heroSummary
        views.settingsPanel.watermarkDetailHeroSummary.isVisible = model.heroSummary.isNotEmpty()
        views.settingsPanel.watermarkDetailEditingHint.text = model.editingHint
        views.settingsPanel.watermarkDetailFooter.text = model.footer
        renderSettingsControl(
            views.settingsPanel.watermarkPlacement,
            model.placementControl,
            model.editingEnabled
        )
        renderSettingsControl(
            views.settingsPanel.watermarkTextScale,
            model.textScaleControl,
            model.editingEnabled
        )
        renderSettingsControl(
            views.settingsPanel.watermarkTextOpacity,
            model.textOpacityControl,
            model.editingEnabled
        )
        model.frameBackgroundControl?.let { control ->
            views.settingsPanel.watermarkFrameBackground.isVisible = true
            renderSettingsControl(
                views.settingsPanel.watermarkFrameBackground,
                control,
                model.editingEnabled
            )
        } ?: run {
            views.settingsPanel.watermarkFrameBackground.isVisible = false
        }
        renderPanelVisibility()
    }

    private fun renderSettingsControl(
        button: Button,
        model: SettingsControlRenderModel,
        editingEnabled: Boolean
    ) {
        button.text = model.buttonLabel
        button.isEnabled = editingEnabled && model.isInteractive
    }

    private fun renderFilterLabPage(model: FilterLabPageRenderModel) {
        views.filterLab.headline.text = model.headline
        views.filterLab.supportingText.text = model.supportingText
        views.filterLab.heroSummary.text = model.heroSummary
        views.filterLab.heroSummary.isVisible = model.heroSummary.isNotEmpty()
        views.filterLab.currentSummary.text = model.currentFilterSummary
        views.filterLab.editingHint.text = model.editingHint
        views.filterLab.footer.text = model.footer

        // Show/hide family tabs based on panel role
        val tabsVisible = model.showFamilyTabs
        views.filterLab.photoTab.isVisible = tabsVisible
        views.filterLab.humanisticTab.isVisible = tabsVisible
        views.filterLab.portraitTab.isVisible = tabsVisible
        views.filterLab.videoTab.isVisible = tabsVisible
        if (tabsVisible) {
            renderFilterLabTab(views.filterLab.photoTab, model.photoTab)
            renderFilterLabTab(views.filterLab.humanisticTab, model.humanisticTab)
            renderFilterLabTab(views.filterLab.portraitTab, model.portraitTab)
            renderFilterLabTab(views.filterLab.videoTab, model.videoTab)
        }

        // Show/hide filter selection list based on panel role
        if (model.showFilterItems) {
            renderFilterSelectionList(model)
            renderSaveCustomControl(model.saveCustomControl, model.editingEnabled)
            views.filterLab.currentSummary.isVisible = true
        } else {
            views.filterLab.selectionList.removeAllViews()
            views.filterLab.currentSummary.isVisible = false
            views.filterLab.saveCustom.isVisible = false
        }

        // Show/hide adjustment panel based on panel role
        if (model.showAdjustmentPanel) {
            renderAdjustmentPanel(
                model.adjustmentPanel,
                model.editingEnabled,
                model.showAdvancedControls,
                model.showModeToggle
            )
        } else {
            views.filterLab.adjustmentPanel.isVisible = false
        }

        renderPanelVisibility()
    }

    private fun renderFilterLabTab(
        button: Button,
        model: FilterLabTabRenderModel
    ) {
        button.text = model.label
        button.isEnabled = !model.isSelected
        button.alpha = if (model.isSelected) 1f else 0.84f
    }

    private fun renderSaveCustomControl(
        model: FilterLabSaveCustomRenderModel,
        editingEnabled: Boolean
    ) {
        views.filterLab.saveCustom.text = model.buttonLabel
        views.filterLab.saveCustom.isEnabled = editingEnabled && model.isEnabled
    }

    private fun renderFilterSelectionList(model: FilterLabPageRenderModel) {
        views.filterLab.selectionList.removeAllViews()
        model.filterItems.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_settings_card)
                alpha = if (item.isSelected) 1f else 0.9f
                setPadding(14.dp, 14.dp, 14.dp, 14.dp)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (views.filterLab.selectionList.childCount == 0) 0 else 8.dp
            }

            val title = TextView(this).apply {
                text = item.title
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.oc_text_primary))
            }
            card.addView(title)

            val supporting = TextView(this).apply {
                text = item.supportingText
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.oc_text_secondary))
                setPadding(0, 6.dp, 0, 0)
            }
            card.addView(supporting)

            if (item.isSelected) {
                val adjustButton = Button(
                    this,
                    null,
                    0,
                    R.style.Widget_OpenCamera_CompactButton
                ).apply {
                    text = item.adjustButtonLabel
                    isAllCaps = false
                    isEnabled = model.editingEnabled && item.adjustButtonLabel != null
                    setOnClickListener {
                        openSelectedFilterAdjustment(model.adjustControl)
                    }
                }
                val adjustParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 10.dp
                }
                card.addView(adjustButton, adjustParams)
            } else {
                val selectButton = Button(
                    this,
                    null,
                    0,
                    R.style.Widget_OpenCamera_CompactButton
                ).apply {
                    text = getString(R.string.button_use_this_look)
                    isAllCaps = false
                    isEnabled = model.editingEnabled && item.nextAction != null
                    setOnClickListener {
                        panelRouter.reduce(CockpitPanelCommand.SelectFilterFamily(model.adjustControl.family))
                        item.nextAction?.let(::applySettingsAction)
                    }
                }
                val selectParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 10.dp
                }
                card.addView(selectButton, selectParams)
            }
            views.filterLab.selectionList.addView(card, params)
        }
    }

    private fun renderAdjustmentPanel(
        model: FilterAdjustmentPanelRenderModel,
        editingEnabled: Boolean,
        showAdvancedControls: Boolean = true,
        showModeToggle: Boolean = true
    ) {
        views.filterLab.adjustmentPanel.isVisible = model.isVisible
        views.filterLab.modeToggle.isVisible = showModeToggle
        if (showAdvancedControls) {
            views.filterLab.modeToggle.text = model.modeToggleLabel
            views.filterLab.modeToggle.isEnabled = editingEnabled && model.selectedProfileId != null
        } else {
            views.filterLab.modeToggle.text = getString(R.string.button_color_lab_reset)
            views.filterLab.modeToggle.isEnabled = true
        }
        views.filterLab.paletteSummary.text = "${model.selectedProfileLabel}\n${model.lightPalette.summary}"
        views.filterLab.paletteHint.text = model.lightPalette.supportingText
        views.filterLab.paletteSurface.isVisible = model.mode == FilterAdjustmentMode.LIGHT
        views.filterLab.paletteHint.isVisible = model.mode == FilterAdjustmentMode.LIGHT
        views.filterLab.advancedTitle.isVisible = showAdvancedControls
        views.filterLab.advancedControls.isVisible = showAdvancedControls && model.mode == FilterAdjustmentMode.ADVANCED
        views.filterLab.advancedExposure.text = model.advancedControls.buttonLabel(FilterAdvancedControl.EXPOSURE)
        views.filterLab.advancedSoftGlow.text = model.advancedControls.buttonLabel(FilterAdvancedControl.SOFT_GLOW)
        views.filterLab.advancedHalo.text = model.advancedControls.buttonLabel(FilterAdvancedControl.HALO)
        views.filterLab.advancedGrain.text = model.advancedControls.buttonLabel(FilterAdvancedControl.GRAIN)
        views.filterLab.advancedSharpness.text = model.advancedControls.buttonLabel(FilterAdvancedControl.SHARPNESS)
        views.filterLab.advancedVignette.text = model.advancedControls.buttonLabel(FilterAdvancedControl.VIGNETTE)
        views.filterLab.advancedHighlights.text = model.advancedControls.buttonLabel(FilterAdvancedControl.HIGHLIGHTS)
        views.filterLab.advancedShadows.text = model.advancedControls.buttonLabel(FilterAdvancedControl.SHADOWS)
        views.filterLab.advancedWarmBoost.text = model.advancedControls.buttonLabel(FilterAdvancedControl.WARM_BOOST)
        views.filterLab.advancedCoolBoost.text = model.advancedControls.buttonLabel(FilterAdvancedControl.COOL_BOOST)
        views.filterLab.advancedTemperatureShift.text =
            model.advancedControls.buttonLabel(FilterAdvancedControl.TEMPERATURE_SHIFT)
        views.filterLab.advancedTintShift.text =
            model.advancedControls.buttonLabel(FilterAdvancedControl.TINT_SHIFT)
        val advancedButtons = listOf(
            views.filterLab.advancedExposure,
            views.filterLab.advancedSoftGlow,
            views.filterLab.advancedHalo,
            views.filterLab.advancedGrain,
            views.filterLab.advancedSharpness,
            views.filterLab.advancedVignette,
            views.filterLab.advancedHighlights,
            views.filterLab.advancedShadows,
            views.filterLab.advancedWarmBoost,
            views.filterLab.advancedCoolBoost,
            views.filterLab.advancedTemperatureShift,
            views.filterLab.advancedTintShift
        )
        advancedButtons.forEach { button ->
            button.isEnabled = editingEnabled && model.selectedProfileId != null
        }
    }

    private fun renderPanelVisibility() {
        val route = activePanelRoute
        views.settingsPanel.panel.isVisible = route.isSettingsOpen
        views.filterLab.panel.isVisible = route is CockpitPanelRoute.StyleLab || route is CockpitPanelRoute.ColorLab
        views.panelDismissScrim.isVisible = route.isAnyPanelOpen

        val subpage = (route as? CockpitPanelRoute.Settings)?.subpage
        views.settingsPanel.rootContent.isVisible = route.isSettingsOpen && (subpage == null || subpage == SettingsSubpage.ROOT)
        views.settingsPanel.portraitLabContent.isVisible = subpage == SettingsSubpage.PORTRAIT_LAB
        views.settingsPanel.watermarkSelectorContent.isVisible = subpage == SettingsSubpage.WATERMARK_SELECTOR
        views.settingsPanel.watermarkDetailContent.isVisible = subpage == SettingsSubpage.WATERMARK_DETAIL
        views.settingsPanel.back.isVisible = route.isSettingsOpen && subpage != null && subpage != SettingsSubpage.ROOT

        views.topBar.colorLabEntry.alpha = if (route is CockpitPanelRoute.ColorLab) 1f else 0.92f
        views.topBar.settingsEntry.alpha = if (route.isSettingsOpen) 1f else 0.92f
        views.topBar.filterEntry.alpha = if (route is CockpitPanelRoute.StyleLab) 1f else 0.92f
        views.quickPanel.panel.isVisible = route is CockpitPanelRoute.QuickBubble
        views.quickPanel.launcher.alpha = if (route is CockpitPanelRoute.QuickBubble) 1f else 0.86f
    }

    private fun renderDevConsoleVisibility() {
        val isDevVisible = activePanelRoute is CockpitPanelRoute.DevConsole
        views.devConsole.panel.isVisible = isDevVisible
        views.devConsole.entry.alpha = if (isDevVisible) 1f else 0.78f
        if (isDevVisible) {
            renderDevConsole()
        }
    }


    override fun selectDevLogTab(tab: DevLogTab) {
        selectedDevLogTab = tab
        refreshDevLogModel()
    }

    override fun refreshDevLogModel() {
        val state = latestSessionState ?: return
        val model = devLogRenderModel(
            state = state,
            traceEvents = container.trace.snapshot(),
            isDebugBuild = com.opencamera.app.BuildConfig.DEBUG,
            selectedTab = selectedDevLogTab,
            text = AppTextResolver(this)
        )
        latestDevLogRenderModel = model
        renderDevConsole()
    }

    private fun renderDevConsole() {
        val model = latestDevLogRenderModel ?: return
        views.devConsole.title.text = model.title
        views.devConsole.summary.text = model.summaryText
        views.devConsole.summary.isVisible = model.summaryText.isNotBlank()
        views.devConsole.content.text = model.content
        views.devConsole.tabKey.isEnabled = model.selectedTab != DevLogTab.KEY
        views.devConsole.tabCore.isEnabled = model.selectedTab != DevLogTab.CORE
        views.devConsole.tabError.isEnabled = model.selectedTab != DevLogTab.ERROR
        views.devConsole.tabAll.isEnabled = model.selectedTab != DevLogTab.ALL
        val activeAlpha = 1f
        val inactiveAlpha = 0.84f
        views.devConsole.tabKey.alpha = if (model.selectedTab == DevLogTab.KEY) activeAlpha else inactiveAlpha
        views.devConsole.tabCore.alpha = if (model.selectedTab == DevLogTab.CORE) activeAlpha else inactiveAlpha
        views.devConsole.tabError.alpha = if (model.selectedTab == DevLogTab.ERROR) activeAlpha else inactiveAlpha
        views.devConsole.tabAll.alpha = if (model.selectedTab == DevLogTab.ALL) activeAlpha else inactiveAlpha
    }

    override fun requestMicrophonePermission() {
        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
    }

    override fun requestCameraPermissionIfNeeded() {
        when {
            hasPermission(Manifest.permission.CAMERA) -> {
                views.topBar.permissionStatus.text = if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    getString(R.string.permission_granted)
                } else {
                    getString(R.string.permission_camera_only)
                }
            }

            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                views.topBar.permissionStatus.text = getString(R.string.permission_pending)
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    )
                )
            }

            else -> {
                val text = AppTextResolver(this)
                views.topBar.permissionStatus.text = text.permissionPermanentlyDenied()
                views.topBar.permissionStatus.visibility = View.VISIBLE
                views.topBar.permissionStatus.setOnClickListener {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
            }
        }
    }

    private fun maybePlayShutterSound(state: SessionState) {
        val activeShot = state.activeShot
        val shouldPlay = state.settings.persisted.common.shutterSoundEnabled &&
            activeShot?.mediaType == com.opencamera.core.media.MediaType.PHOTO &&
            activeShot.shotId != lastPlayedShutterSoundShotId
        if (!shouldPlay) {
            return
        }
        shutterClickSound.play(MediaActionSound.SHUTTER_CLICK)
        lastPlayedShutterSoundShotId = activeShot?.shotId
    }

    private fun buildUiSnapshot(): MainActivityUiSnapshot {
        return MainActivityUiSnapshot(
            sessionState = latestSessionState,
            activePanelRoute = activePanelRoute,
            isFilterAdjustmentVisible = panelState.isFilterAdjustmentVisible,
            settingsPage = latestSettingsPageRenderModel,
            portraitLabPage = latestPortraitLabRenderModel,
            watermarkDetailPage = latestWatermarkLabDetailRenderModel,
            filterLabPage = latestFilterLabRenderModel,
            devLog = latestDevLogRenderModel
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onBackPressed() {
        val previousRoute = activePanelRoute
        panelRouter.reduce(CockpitPanelCommand.AndroidBack)
        if (activePanelRoute == previousRoute && activePanelRoute is CockpitPanelRoute.None) {
            super.onBackPressed()
            return
        }
        if (previousRoute is CockpitPanelRoute.StyleLab || previousRoute is CockpitPanelRoute.ColorLab) {
            lightPaletteBaseSpec = null
        }
        renderLatestSettingsSurfaces()
        renderPanelVisibility()
        renderDevConsoleVisibility()
    }

    override fun onDestroy() {
        shutterClickSound.release()
        super.onDestroy()
    }

    private fun sessionUiStrings(): SessionUiStrings {
        return SessionUiStrings(
            buttonSwitchToFront = getString(R.string.button_switch_to_front),
            buttonSwitchToBack = getString(R.string.button_switch_to_back),
            buttonSingleLens = getString(R.string.button_single_lens),
            buttonZoomPrefix = getString(R.string.button_zoom_prefix),
            buttonZoomUnavailable = getString(R.string.button_zoom_unavailable),
            buttonStillFast = getString(R.string.button_still_fast),
            buttonStillMax = getString(R.string.button_still_max),
            buttonStillQualityUnavailable = getString(R.string.button_still_quality_unavailable),
            buttonStill12Mp = getString(R.string.button_still_12mp),
            buttonStill8Mp = getString(R.string.button_still_8mp),
            buttonStill2Mp = getString(R.string.button_still_2mp),
            buttonStillResolutionUnavailable = getString(R.string.button_still_resolution_unavailable),
            outputErrorPrefix = getString(R.string.output_error_prefix),
            outputVideoPrefix = getString(R.string.output_video_prefix),
            outputLivePrefix = getString(R.string.output_live_prefix),
            outputSavedPrefix = getString(R.string.output_saved_prefix),
            outputPreviewPrefix = getString(R.string.output_preview_prefix),
            outputWaiting = getString(R.string.output_waiting)
        )
    }

    override fun applySettingsAction(action: PersistedSettingsAction) {
        lifecycleScope.launch {
            val result = container.sessionSettingsManager.apply(action)
            if (result is SessionSettingsApplyResult.BlockedByActiveShot) {
                Toast.makeText(this@MainActivity, AppTextResolver(this@MainActivity).settingsBlockedByCapture(), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun applySettingsControl(control: SettingsControlRenderModel?) {
        val text = AppTextResolver(this)
        if (control == null) {
            Toast.makeText(this, text.settingsNotLoaded(), Toast.LENGTH_SHORT).show()
            return
        }
        val action = control.nextAction
        if (action == null) {
            Toast.makeText(this, text.settingsActionUnsupported(), Toast.LENGTH_SHORT).show()
            return
        }
        applySettingsAction(action)
    }

    override fun openPortraitLab() {
        val settingsModel = latestSettingsPageRenderModel ?: return
        if (!settingsModel.editingEnabled ||
            settingsModel.photoSection.portraitLab.availability == SettingsControlAvailability.UNSUPPORTED
        ) {
            return
        }
        panelRouter.reduce(CockpitPanelCommand.OpenPortraitLab)
        renderLatestSettingsSurfaces()
    }

    override fun openWatermarkLabSelector() {
        val settingsModel = latestSettingsPageRenderModel ?: return
        if (!settingsModel.editingEnabled ||
            settingsModel.photoSection.watermarkTemplate.availability == SettingsControlAvailability.UNSUPPORTED
        ) {
            return
        }
        panelRouter.reduce(CockpitPanelCommand.OpenWatermarkSelector)
        renderLatestSettingsSurfaces()
    }

    override fun openWatermarkLabDetail(templateId: String) {
        val detailModel = latestWatermarkLabDetailRenderModel
        if (detailModel != null && !detailModel.editingEnabled) {
            return
        }
        panelRouter.reduce(CockpitPanelCommand.OpenWatermarkDetail(templateId))
        renderLatestSettingsSurfaces()
    }

    override fun selectFilterLabFamily(family: FilterLabFamily) {
        panelRouter.reduce(CockpitPanelCommand.SelectFilterFamily(family))
        lightPaletteBaseSpec = null
        maybeAutoPrepareFilter()
        renderLatestFilterLab()
    }

    private fun selectedFilterLabFamily(state: SessionState): FilterLabFamily {
        return panelState.selectedFilterLabFamilyOverride ?: defaultFilterLabFamily(state.activeMode)
    }

    override fun renderLatestFilterLab() {
        val state = latestSessionState ?: return
        val text = AppTextResolver(this)
        val model = filterLabPageRenderModel(
            state = state,
            text = text,
            selectedFamily = selectedFilterLabFamily(state),
            panelRole = if (activePanelRoute is CockpitPanelRoute.ColorLab) {
                StyleAndColorLabRole.COLOR_LAB
            } else {
                StyleAndColorLabRole.STYLE
            },
            showAdjustmentPanel = panelState.isFilterAdjustmentVisible,
            adjustmentMode = panelState.filterAdjustmentMode
        )
        latestFilterLabRenderModel = model
        if (panelState.isFilterAdjustmentVisible && lightPaletteBaseSpec == null) {
            lightPaletteBaseSpec = model.adjustmentPanel.renderSpec
        }
        renderFilterLabPage(model)
    }

    override fun renderLatestSettingsSurfaces() {
        val state = latestSessionState ?: return
        val text = AppTextResolver(this)
        val settingsModel = sessionSettingsPageRenderModel(state, text)
        val portraitLabModel = portraitLabPageRenderModel(state, text)
        val selectorModel = watermarkLabSelectorRenderModel(state, text)
        val detailModel = watermarkLabDetailRenderModel(
            state = state,
            templateId = panelState.selectedWatermarkDetailTemplateId
                ?: state.settings.persisted.photo.defaultWatermarkTemplateId,
            text = text
        )
        latestSettingsPageRenderModel = settingsModel
        latestPortraitLabRenderModel = portraitLabModel
        latestWatermarkLabSelectorRenderModel = selectorModel
        latestWatermarkLabDetailRenderModel = detailModel
        renderSettingsPage(settingsModel)
        renderPortraitLabPage(portraitLabModel)
        renderWatermarkLabSelectorPage(selectorModel)
        renderWatermarkLabDetailPage(detailModel)
    }

    override fun saveCurrentFilterAsCustom(control: FilterLabSaveCustomRenderModel?) {
        val sourceProfileId = control?.sourceProfileId ?: return
        if (!control.isEnabled) {
            return
        }
        lifecycleScope.launch {
            container.sessionSettingsManager.saveCurrentFilterAsCustom(
                family = control.family,
                sourceProfileId = sourceProfileId
            )
        }
    }

    override fun maybeAutoPrepareFilter() {
        val panel = latestFilterLabRenderModel?.adjustmentPanel ?: return
        if (!panel.needsAutoPrepare) return
        val profileId = panel.selectedProfileId ?: return
        val family = latestFilterLabRenderModel?.adjustControl?.family ?: return
        lifecycleScope.launch {
            container.sessionSettingsManager.prepareFilterForAdjustment(
                family = family,
                sourceProfileId = profileId
            )
            renderLatestFilterLab()
        }
    }

    override fun openSelectedFilterAdjustment(control: FilterLabAdjustRenderModel?) {
        val sourceProfileId = control?.sourceProfileId ?: return
        if (!control.isEnabled) {
            return
        }
        lifecycleScope.launch {
            val editableProfileId = container.sessionSettingsManager.prepareFilterForAdjustment(
                family = control.family,
                sourceProfileId = sourceProfileId
            )
            if (editableProfileId != null) {
                panelRouter.reduce(CockpitPanelCommand.SelectFilterFamily(control.family))
                lightPaletteBaseSpec = latestFilterLabRenderModel?.adjustmentPanel?.renderSpec
                renderLatestFilterLab()
            }
        }
    }

    override fun toggleFilterAdjustmentMode() {
        panelRouter.reduce(CockpitPanelCommand.ToggleFilterAdjustmentMode)
        if (panelState.filterAdjustmentMode == FilterAdjustmentMode.LIGHT) {
            lightPaletteBaseSpec = latestFilterLabRenderModel?.adjustmentPanel?.renderSpec
        }
        renderLatestFilterLab()
    }

    override fun applyAdvancedFilterControl(control: FilterAdvancedControl) {
        val panel = latestFilterLabRenderModel?.adjustmentPanel ?: return
        val profileId = panel.selectedProfileId ?: return
        lifecycleScope.launch {
            container.sessionSettingsManager.updateCustomFilterRenderSpec(
                filterProfileId = profileId,
                renderSpec = panel.renderSpec.nextAdvancedControl(control)
            )
        }
    }


    override fun neutralColorLabAction(): PersistedSettingsAction {
        return neutralColorLabTopLevel()
    }

    override fun handleFilterPaletteTouch(colorAxis: Float, toneAxis: Float) {
        if (activePanelRoute is CockpitPanelRoute.ColorLab) {
            val persisted = latestSessionState?.settings?.persisted ?: return
            views.filterLab.paletteSurface.updateReticle(colorAxis, toneAxis)
            lifecycleScope.launch {
                container.sessionSettingsManager.apply(
                    colorLabPaletteUpdateAction(
                        persisted = persisted,
                        colorAxis = colorAxis,
                        toneAxis = toneAxis
                    )
                )
            }
            return
        }
        val panel = latestFilterLabRenderModel?.adjustmentPanel ?: return
        val profileId = panel.selectedProfileId ?: return
        if (!panel.isVisible || panel.mode != FilterAdjustmentMode.LIGHT) {
            return
        }
        if (panel.needsAutoPrepare) return
        val baseSpec = lightPaletteBaseSpec ?: panel.renderSpec
        lifecycleScope.launch {
            container.sessionSettingsManager.updateCustomFilterRenderSpec(
                filterProfileId = profileId,
                renderSpec = baseSpec.applyLightPalette(colorAxis, toneAxis)
            )
        }
    }



    override fun renderAfterPanelChange() {
        mainRenderer.renderPanelVisibility(activePanelRoute)
        devConsoleRenderer.renderVisibility(activePanelRoute)
    }

    override fun reducePanel(command: CockpitPanelCommand) {
        panelRouter.reduce(command)
    }

    override fun dispatch(intent: SessionIntent) {
        lifecycleScope.launch {
            container.cameraSession.dispatch(intent)
        }
    }


    override fun openLatestGalleryMedia() {
        val state = latestSessionState ?: return
        val target = galleryOpenTargetFor(
            source = state.presentation.latestThumbnailSource,
            savedMediaType = state.presentation.latestSavedMediaType
        ) ?: run {
            Toast.makeText(this, R.string.gallery_open_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val galleryLauncher = GalleryLauncher(this)
        if (!galleryLauncher.open(target)) {
            Toast.makeText(this, R.string.gallery_open_failed, Toast.LENGTH_SHORT).show()
        }
    }


    override fun exportDevLog() {
        runCatching {
                val model = latestDevLogRenderModel ?: return
                val file = devLogExporter.export(model.exportContent)
                views.preview.captureOutput.text = "Debug log exported: ${file.absolutePath}"
            }
            .onFailure {
                Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
            }
    }

    override fun showDisabledReason(reason: String) {
        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
    }

    private fun captureConfigDisabledReason(state: SessionState): String? {
        if (!state.permissionState.cameraGranted) return getString(R.string.disabled_permission)
        if (state.previewStatus == com.opencamera.core.session.PreviewStatus.RECOVERING)
            return getString(R.string.disabled_preview_recovering)
        if (state.countdownRemainingSeconds != null) return getString(R.string.disabled_countdown)
        if (state.activeShot != null && state.recordingStatus == RecordingStatus.REQUESTING)
            return getString(R.string.disabled_preparing_recording)
        if (state.recordingStatus == RecordingStatus.RECORDING) return getString(R.string.disabled_recording)
        if (state.recordingStatus == RecordingStatus.STOPPING) return getString(R.string.disabled_stopping_recording)
        if (state.captureStatus == com.opencamera.core.session.CaptureStatus.SAVING)
            return getString(R.string.disabled_saving_photo)
        return null
    }

    private fun syncPermissionState(
        cameraGranted: Boolean = hasPermission(Manifest.permission.CAMERA),
        microphoneGranted: Boolean = hasPermission(Manifest.permission.RECORD_AUDIO)
    ) {
        lifecycleScope.launch {
            container.cameraSession.dispatch(
                SessionIntent.PermissionsUpdated(
                    cameraGranted = cameraGranted,
                    microphoneGranted = microphoneGranted
                )
            )
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun List<FilterAdvancedControlRenderModel>.buttonLabel(
        control: FilterAdvancedControl
    ): String {
        return first { item -> item.control == control }.buttonLabel
    }
}
