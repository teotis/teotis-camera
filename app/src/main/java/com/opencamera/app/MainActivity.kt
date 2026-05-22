package com.opencamera.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.opencamera.app.i18n.AppTextResolver
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.opencamera.core.media.renderUriOrNull
import com.opencamera.core.session.PreviewMeteringFeedback
import com.opencamera.core.session.PreviewMeteringFeedbackStatus
import com.opencamera.core.session.SessionIntent
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PersistedSettingsAction
import kotlinx.coroutines.launch
import java.io.File

enum class SettingsTab { COMMON, PHOTO, VIDEO }

@Suppress("EXPOSED_PARAMETER_TYPE")
class MainActivity : AppCompatActivity(), MainActivityActionCallbacks {
    private val container: AppContainer
        get() = (application as OpenCameraApplication).container

    private var orientationMonitor: CameraOrientationMonitor? = null
    private val contentRotator = OrientationContentRotator()
    private var latestOrientationRenderModel: CameraOrientationRenderModel =
        CameraOrientationRenderModel(
            CameraPhysicalOrientation.PORTRAIT,
            0f,
            com.opencamera.core.device.CameraOutputRotation.ROTATION_0
        )

    private var selectedSettingsTab = SettingsTab.COMMON
    private lateinit var views: MainActivityViews
    private lateinit var actionBinder: MainActivityActionBinder
    private lateinit var galleryLauncher: GalleryLauncher
    private lateinit var permissionUiController: PermissionUiController
    private lateinit var mainRenderer: MainActivityRenderer
    private lateinit var cockpitRenderer: CockpitSurfaceRenderer
    private lateinit var settingsRenderer: SettingsPanelRenderer
    private lateinit var filterLabRenderer: FilterLabPanelRenderer
    private lateinit var devConsoleRenderer: DevConsoleRenderer

    // Compatibility accessors for view groups
    private val previewView: PreviewView get() = views.preview.previewView
    private val previewOverlayView: PreviewOverlayView get() = views.preview.overlayView
    private val previewThumbnail: ImageView get() = views.preview.thumbnail
    private val captureOutput: TextView get() = views.preview.captureOutput
    private val titleText: TextView get() = views.topBar.titleText
    private val permissionStatus: TextView get() = views.topBar.permissionStatus
    private val buttonColorLabEntry: Button get() = views.topBar.colorLabEntry
    private val buttonSettingsEntry: Button get() = views.topBar.settingsEntry
    private val buttonFilterEntry: Button get() = views.topBar.filterEntry
    private val panelDismissScrim: View get() = views.panelDismissScrim
    private val quickBubblePanel get() = views.quickPanel.panel
    private val buttonQuickGrid: Button get() = views.quickPanel.grid
    private val buttonQuickFlash: Button get() = views.quickPanel.flash
    private val buttonFrameRatio43: Button get() = views.quickPanel.frame43
    private val buttonFrameRatio169: Button get() = views.quickPanel.frame169
    private val buttonFrameRatio11: Button get() = views.quickPanel.frame11
    private val buttonQuickLivePhoto: Button get() = views.quickPanel.livePhoto
    private val buttonQuickTimer: Button get() = views.quickPanel.timer
    private val buttonQuickLauncher: Button get() = views.quickPanel.launcher
    private val settingsPanel get() = views.settingsPanel.panel
    private val buttonCloseSettings: Button get() = views.settingsPanel.close
    private val buttonSettingsBack: Button get() = views.settingsPanel.back
    private val settingsRootContent: LinearLayout get() = views.settingsPanel.rootContent
    private val settingsPortraitLabContent: LinearLayout get() = views.settingsPanel.portraitLabContent
    private val settingsWatermarkSelectorContent: LinearLayout get() = views.settingsPanel.watermarkSelectorContent
    private val settingsWatermarkDetailContent: LinearLayout get() = views.settingsPanel.watermarkDetailContent
    private val settingsHeadline: TextView get() = views.settingsPanel.headline
    private val settingsSupportingText: TextView get() = views.settingsPanel.supportingText
    private val settingsHeroSummary: TextView get() = views.settingsPanel.heroSummary
    private val settingsCommonSummary: TextView get() = views.settingsPanel.commonSummary
    private val settingsPhotoSummary: TextView get() = views.settingsPanel.photoSummary
    private val settingsVideoSummary: TextView get() = views.settingsPanel.videoSummary
    private val settingsCatalogFooter: TextView get() = views.settingsPanel.catalogFooter
    private val settingsEditingHint: TextView get() = views.settingsPanel.editingHint
    private val buttonSettingsTabCommon: Button get() = views.settingsPanel.tabCommon
    private val buttonSettingsTabPhoto: Button get() = views.settingsPanel.tabPhoto
    private val buttonSettingsTabVideo: Button get() = views.settingsPanel.tabVideo
    private val settingsCommonSection: LinearLayout get() = views.settingsPanel.commonSection
    private val settingsPhotoSection: LinearLayout get() = views.settingsPanel.photoSection
    private val settingsVideoSection: LinearLayout get() = views.settingsPanel.videoSection
    private val buttonGridMode: Button get() = views.settingsPanel.gridMode
    private val buttonShutterSound: Button get() = views.settingsPanel.shutterSound
    private val buttonSelfieMirror: Button get() = views.settingsPanel.selfieMirror
    private val buttonPhotoFilter: Button get() = views.settingsPanel.photoFilter
    private val buttonPhotoPortraitLab: Button get() = views.settingsPanel.photoPortraitLab
    private val buttonPhotoWatermark: Button get() = views.settingsPanel.photoWatermark
    private val buttonPhotoLive: Button get() = views.settingsPanel.photoLive
    private val buttonPhotoTimer: Button get() = views.settingsPanel.photoTimer
    private val buttonVideoResolution: Button get() = views.settingsPanel.videoResolution
    private val buttonVideoFrameRate: Button get() = views.settingsPanel.videoFrameRate
    private val buttonVideoDynamicFps: Button get() = views.settingsPanel.videoDynamicFps
    private val buttonVideoAudio: Button get() = views.settingsPanel.videoAudio
    private val buttonVideoFilter: Button get() = views.settingsPanel.videoFilter
    private val portraitLabHeadline: TextView get() = views.settingsPanel.portraitHeadline
    private val portraitLabSupportingText: TextView get() = views.settingsPanel.portraitSupportingText
    private val portraitLabHeroSummary: TextView get() = views.settingsPanel.portraitHeroSummary
    private val portraitLabEditingHint: TextView get() = views.settingsPanel.portraitEditingHint
    private val buttonPortraitProfile: Button get() = views.settingsPanel.portraitProfile
    private val buttonPortraitBeautyPreset: Button get() = views.settingsPanel.portraitBeautyPreset
    private val buttonPortraitBeautyStrength: Button get() = views.settingsPanel.portraitBeautyStrength
    private val buttonPortraitBokehEffect: Button get() = views.settingsPanel.portraitBokehEffect
    private val portraitLabFooter: TextView get() = views.settingsPanel.portraitFooter
    private val watermarkSelectorHeadline: TextView get() = views.settingsPanel.watermarkSelectorHeadline
    private val watermarkSelectorSupportingText: TextView get() = views.settingsPanel.watermarkSelectorSupportingText
    private val watermarkSelectorHeroSummary: TextView get() = views.settingsPanel.watermarkSelectorHeroSummary
    private val watermarkSelectorList: LinearLayout get() = views.settingsPanel.watermarkSelectorList
    private val watermarkSelectorEditingHint: TextView get() = views.settingsPanel.watermarkSelectorEditingHint
    private val watermarkSelectorFooter: TextView get() = views.settingsPanel.watermarkSelectorFooter
    private val watermarkDetailHeadline: TextView get() = views.settingsPanel.watermarkDetailHeadline
    private val watermarkDetailSupportingText: TextView get() = views.settingsPanel.watermarkDetailSupportingText
    private val watermarkDetailHeroSummary: TextView get() = views.settingsPanel.watermarkDetailHeroSummary
    private val watermarkDetailEditingHint: TextView get() = views.settingsPanel.watermarkDetailEditingHint
    private val buttonWatermarkPlacement: Button get() = views.settingsPanel.watermarkPlacement
    private val buttonWatermarkTextScale: Button get() = views.settingsPanel.watermarkTextScale
    private val buttonWatermarkTextOpacity: Button get() = views.settingsPanel.watermarkTextOpacity
    private val buttonWatermarkFrameBackground: Button get() = views.settingsPanel.watermarkFrameBackground
    private val watermarkDetailFooter: TextView get() = views.settingsPanel.watermarkDetailFooter
    private val filterPanel get() = views.filterLab.panel
    private val buttonCloseFilter: Button get() = views.filterLab.close
    private val filterHeadline: TextView get() = views.filterLab.headline
    private val filterSupportingText: TextView get() = views.filterLab.supportingText
    private val filterHeroSummary: TextView get() = views.filterLab.heroSummary
    private val filterCurrentSummary: TextView get() = views.filterLab.currentSummary
    private val filterSectionFiltersTitle: TextView get() = views.filterLab.sectionFiltersTitle
    private val filterSelectionCard: LinearLayout get() = views.filterLab.selectionCard
    private val filterSelectionList: LinearLayout get() = views.filterLab.selectionList
    private val filterEditingHint: TextView get() = views.filterLab.editingHint
    private val filterFooter: TextView get() = views.filterLab.footer
    private val buttonFilterPhotoTab: Button get() = views.filterLab.photoTab
    private val buttonFilterHumanisticTab: Button get() = views.filterLab.humanisticTab
    private val buttonFilterPortraitTab: Button get() = views.filterLab.portraitTab
    private val buttonFilterVideoTab: Button get() = views.filterLab.videoTab
    private val buttonFilterSaveCustom: Button get() = views.filterLab.saveCustom
    private val filterSectionPaletteTitle: TextView get() = views.filterLab.sectionPaletteTitle
    private val filterAdjustmentPanel: LinearLayout get() = views.filterLab.adjustmentPanel
    private val buttonFilterModeToggle: Button get() = views.filterLab.modeToggle
    private val filterPaletteSummary: TextView get() = views.filterLab.paletteSummary
    private val filterPaletteHint: TextView get() = views.filterLab.paletteHint
    private val filterPaletteSurface: FilterPaletteView get() = views.filterLab.paletteSurface
    private val filterAdvancedTitle: TextView get() = views.filterLab.advancedTitle
    private val filterAdvancedControls: LinearLayout get() = views.filterLab.advancedControls
    private val buttonAdvancedExposure: Button get() = views.filterLab.advancedExposure
    private val buttonAdvancedSoftGlow: Button get() = views.filterLab.advancedSoftGlow
    private val buttonAdvancedHalo: Button get() = views.filterLab.advancedHalo
    private val buttonAdvancedGrain: Button get() = views.filterLab.advancedGrain
    private val buttonAdvancedSharpness: Button get() = views.filterLab.advancedSharpness
    private val buttonAdvancedVignette: Button get() = views.filterLab.advancedVignette
    private val buttonAdvancedHighlights: Button get() = views.filterLab.advancedHighlights
    private val buttonAdvancedShadows: Button get() = views.filterLab.advancedShadows
    private val buttonAdvancedWarmBoost: Button get() = views.filterLab.advancedWarmBoost
    private val buttonAdvancedCoolBoost: Button get() = views.filterLab.advancedCoolBoost
    private val buttonAdvancedTemperatureShift: Button get() = views.filterLab.advancedTemperatureShift
    private val buttonAdvancedTintShift: Button get() = views.filterLab.advancedTintShift
    private val buttonDevEntry: Button get() = views.devConsole.entry
    private val devConsolePanel get() = views.devConsole.panel
    private val buttonDevTabKey: Button get() = views.devConsole.tabKey
    private val buttonDevTabCore: Button get() = views.devConsole.tabCore
    private val buttonDevTabError: Button get() = views.devConsole.tabError
    private val buttonDevTabAll: Button get() = views.devConsole.tabAll
    private val devConsoleTitle: TextView get() = views.devConsole.title
    private val devConsoleSummary: TextView get() = views.devConsole.summary
    private val devConsoleContent: TextView get() = views.devConsole.content
    private val buttonDevExport: Button get() = views.devConsole.export
    private val buttonDevClose: Button get() = views.devConsole.close
    private val modeTrackScroll get() = views.modeTrack.scroll
    private val photoModeButton: Button get() = views.modeTrack.photo
    private val nightModeButton: Button get() = views.modeTrack.night
    private val portraitModeButton: Button get() = views.modeTrack.portrait
    private val proModeButton: Button get() = views.modeTrack.pro
    private val videoModeButton: Button get() = views.modeTrack.video
    private val documentModeButton: Button get() = views.modeTrack.document
    private val humanisticModeButton: Button get() = views.modeTrack.humanistic
    private val shutterButton: Button get() = views.bottomCockpit.shutter
    private val lensFacingButton: Button get() = views.bottomCockpit.lensFacing
    private val zoomCapsuleScroll get() = views.bottomCockpit.zoomScroll
    private val zoomCapsuleRow: LinearLayout get() = views.bottomCockpit.zoomRow
    private val shutterClickSound = MediaActionSound()
    private var lastRequestedThumbnailUri: String? = null
    private var lastPlayedShutterSoundShotId: String? = null
    private var activePanelRoute: CockpitPanelRoute = CockpitPanelRoute.None
    private var selectedDevLogTab = DevLogTab.KEY
    private var latestDevLogRenderModel: DevLogRenderModel? = null
    private lateinit var devLogExporter: DevLogExporter
    private var latestSettingsPageRenderModel: SessionSettingsPageRenderModel? = null
    private var latestPortraitLabRenderModel: PortraitLabPageRenderModel? = null
    private var latestWatermarkLabSelectorRenderModel: WatermarkLabSelectorRenderModel? = null
    private var latestWatermarkLabDetailRenderModel: WatermarkLabDetailRenderModel? = null
    private var latestFilterLabRenderModel: FilterLabPageRenderModel? = null
    private var latestSessionState: SessionState? = null
    private var selectedWatermarkDetailTemplateId: String? = null
    private var selectedFilterLabFamilyOverride: FilterLabFamily? = null
    private var isFilterAdjustmentVisible = true
    private var filterAdjustmentMode = FilterAdjustmentMode.LIGHT
    private var lightPaletteBaseSpec: FilterRenderSpec? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val cameraGranted = result[Manifest.permission.CAMERA] ?: hasPermission(Manifest.permission.CAMERA)
        val micGranted = result[Manifest.permission.RECORD_AUDIO] ?: hasPermission(Manifest.permission.RECORD_AUDIO)
        syncPermissionState(cameraGranted, micGranted)

        if (cameraGranted) {
            permissionStatus.text = if (micGranted) {
                getString(R.string.permission_granted)
            } else {
                getString(R.string.permission_camera_only)
            }
        } else {
            permissionStatus.text = getString(R.string.permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        views = MainActivityViews.bind(this)
        devLogExporter = DevLogExporter(this)

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
        settingsRenderer = SettingsPanelRenderer(this, views.settingsPanel)
        filterLabRenderer = FilterLabPanelRenderer(this, views.filterLab)
        devConsoleRenderer = DevConsoleRenderer(this, views.devConsole)
        mainRenderer = MainActivityRenderer(
            views = views,
            cockpit = cockpitRenderer,
            settings = settingsRenderer,
            filterLab = filterLabRenderer,
            devConsole = devConsoleRenderer
        )
        galleryLauncher = GalleryLauncher(this)
        permissionUiController = PermissionUiController(this, views.topBar.permissionStatus) { AppTextResolver(this) }
        actionBinder = MainActivityActionBinder(views, ::currentUiSnapshot, this, ::hasPermission, ::captureConfigDisabledReason)

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

        actionBinder.bind()
        bindState()
        syncPermissionState()
        initOrientationMonitor()
    }

    private fun initOrientationMonitor() {
        contentRotator.register(
            buttonFilterEntry,
            buttonQuickLauncher,
            buttonDevEntry,
            shutterButton,
            lensFacingButton,
            buttonFrameRatio43,
            buttonFrameRatio169,
            buttonFrameRatio11,
            buttonGridMode
        )
        orientationMonitor = CameraOrientationMonitor(this) { model ->
            latestOrientationRenderModel = model
            renderOrientation(model)
            dispatch(SessionIntent.OutputRotationChanged(model.outputRotation))
        }
    }

    private fun renderOrientation(model: CameraOrientationRenderModel) {
        contentRotator.applyRotation(model.contentRotationDegrees)
    }

    override fun onStart() {
        super.onStart()
        orientationMonitor?.enable()
        container.cameraCoordinator.attachPreviewHost(this, previewView)
        syncPermissionState()
        dispatch(SessionIntent.Boot)
        dispatch(SessionIntent.PreviewHostAttached)
        requestCameraPermissionIfNeeded()
    }

    override fun onStop() {
        super.onStop()
        orientationMonitor?.disable()
        dispatch(SessionIntent.PreviewHostDetached("Activity moved to background"))
    }

    private fun currentUiSnapshot(): MainActivityUiSnapshot = MainActivityUiSnapshot(
        sessionState = latestSessionState,
        activePanelRoute = activePanelRoute,
        isFilterAdjustmentVisible = isFilterAdjustmentVisible,
        settingsPage = latestSettingsPageRenderModel,
        portraitLabPage = latestPortraitLabRenderModel,
        watermarkDetailPage = latestWatermarkLabDetailRenderModel,
        filterLabPage = latestFilterLabRenderModel,
        devLog = latestDevLogRenderModel
    )

    // region MainActivityActionCallbacks

    override fun dispatch(intent: SessionIntent) {
        lifecycleScope.launch {
            container.cameraSession.dispatch(intent)
        }
    }

    override fun applySettingsAction(action: PersistedSettingsAction) {
        lifecycleScope.launch {
            val result = container.sessionSettingsManager.apply(action)
            if (result is SessionSettingsApplyResult.BlockedByActiveShot) {
                Toast.makeText(this@MainActivity, AppTextResolver(this@MainActivity).settingsBlockedByCapture(), Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Suppress("EXPOSED_PARAMETER_TYPE")
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

    override fun setPanelRoute(route: CockpitPanelRoute) {
        activePanelRoute = route
        if (route is CockpitPanelRoute.None) {
            selectedWatermarkDetailTemplateId = null
            selectedFilterLabFamilyOverride = null
            isFilterAdjustmentVisible = false
            lightPaletteBaseSpec = null
            selectedSettingsTab = SettingsTab.COMMON
        }
        if (route is CockpitPanelRoute.ColorLab || route is CockpitPanelRoute.StyleLab) {
            isFilterAdjustmentVisible = true
            maybeAutoPrepareFilter()
            renderLatestFilterLab()
        }
        if (route is CockpitPanelRoute.Settings) {
            selectedWatermarkDetailTemplateId = null
            renderLatestSettingsSurfaces()
        }
    }

    override fun renderAfterPanelChange() {
        mainRenderer.renderPanelVisibility(activePanelRoute)
        devConsoleRenderer.renderVisibility(activePanelRoute)
        devConsoleRenderer.render(latestDevLogRenderModel)
        if (activePanelRoute is CockpitPanelRoute.QuickBubble) {
            latestSessionState?.let(::render)
        }
    }

    override fun renderLatestSettingsSurfaces() {
        val state = latestSessionState ?: return
        val text = AppTextResolver(this)
        val settingsModel = sessionSettingsPageRenderModel(state, text)
        val portraitLabModel = portraitLabPageRenderModel(state, text)
        val selectorModel = watermarkLabSelectorRenderModel(state, text)
        val detailModel = watermarkLabDetailRenderModel(
            state = state,
            templateId = selectedWatermarkDetailTemplateId
                ?: state.settings.persisted.photo.defaultWatermarkTemplateId,
            text = text
        )
        latestSettingsPageRenderModel = settingsModel
        latestPortraitLabRenderModel = portraitLabModel
        latestWatermarkLabSelectorRenderModel = selectorModel
        latestWatermarkLabDetailRenderModel = detailModel
        settingsRenderer.renderPage(settingsModel)
        settingsRenderer.renderTabs(selectedSettingsTab)
        settingsRenderer.renderPortraitLabPage(portraitLabModel)
        settingsRenderer.renderWatermarkSelectorPage(selectorModel)
        settingsRenderer.renderWatermarkDetailPage(detailModel)
        mainRenderer.renderPanelVisibility(activePanelRoute)
    }

    override fun renderLatestFilterLab() {
        val state = latestSessionState ?: return
        val text = AppTextResolver(this)
        val model = filterLabPageRenderModel(
            state = state,
            text = text,
            selectedFamily = selectedFilterLabFamily(state),
            panelRole = if (activePanelRoute is CockpitPanelRoute.ColorLab) StyleAndColorLabRole.COLOR_LAB else StyleAndColorLabRole.STYLE,
            showAdjustmentPanel = isFilterAdjustmentVisible,
            adjustmentMode = filterAdjustmentMode
        )
        latestFilterLabRenderModel = model
        if (isFilterAdjustmentVisible && lightPaletteBaseSpec == null) {
            lightPaletteBaseSpec = model.adjustmentPanel.renderSpec
        }
        filterLabRenderer.renderPage(model)
        mainRenderer.renderPanelVisibility(activePanelRoute)
    }

    override fun maybeAutoPrepareFilter() {
        val panel = latestFilterLabRenderModel?.adjustmentPanel ?: return
        if (!panel.needsAutoPrepare) return
        val profileId = panel.selectedProfileId ?: return
        val family = latestFilterLabRenderModel?.adjustControl?.family ?: return
        lifecycleScope.launch {
            container.sessionSettingsManager.prepareFilterForAdjustment(family = family, sourceProfileId = profileId)
            renderLatestFilterLab()
        }
    }

    @Suppress("EXPOSED_PARAMETER_TYPE")
    override fun saveCurrentFilterAsCustom(control: FilterLabSaveCustomRenderModel?) {
        val sourceProfileId = control?.sourceProfileId ?: return
        if (!control.isEnabled) return
        lifecycleScope.launch {
            container.sessionSettingsManager.saveCurrentFilterAsCustom(family = control.family, sourceProfileId = sourceProfileId)
        }
    }

    @Suppress("EXPOSED_PARAMETER_TYPE")
    override fun openSelectedFilterAdjustment(control: FilterLabAdjustRenderModel?) {
        val sourceProfileId = control?.sourceProfileId ?: return
        if (!control.isEnabled) return
        lifecycleScope.launch {
            val editableProfileId = container.sessionSettingsManager.prepareFilterForAdjustment(family = control.family, sourceProfileId = sourceProfileId)
            if (editableProfileId != null) {
                isFilterAdjustmentVisible = true
                filterAdjustmentMode = FilterAdjustmentMode.LIGHT
                lightPaletteBaseSpec = latestFilterLabRenderModel?.adjustmentPanel?.renderSpec
                renderLatestFilterLab()
            }
        }
    }

    @Suppress("EXPOSED_PARAMETER_TYPE")
    override fun applyAdvancedFilterControl(control: FilterAdvancedControl) {
        val panel = latestFilterLabRenderModel?.adjustmentPanel ?: return
        val profileId = panel.selectedProfileId ?: return
        lifecycleScope.launch {
            container.sessionSettingsManager.updateCustomFilterRenderSpec(filterProfileId = profileId, renderSpec = panel.renderSpec.nextAdvancedControl(control))
        }
    }

    override fun toggleFilterAdjustmentMode() {
        filterAdjustmentMode = if (filterAdjustmentMode == FilterAdjustmentMode.LIGHT) FilterAdjustmentMode.ADVANCED else FilterAdjustmentMode.LIGHT
        if (filterAdjustmentMode == FilterAdjustmentMode.LIGHT) {
            lightPaletteBaseSpec = latestFilterLabRenderModel?.adjustmentPanel?.renderSpec
        }
        renderLatestFilterLab()
    }

    override fun handleFilterPaletteTouch(colorAxis: Float, toneAxis: Float) {
        if (activePanelRoute is CockpitPanelRoute.ColorLab) {
            val persisted = latestSessionState?.settings?.persisted ?: return
            filterPaletteSurface.updateReticle(colorAxis, toneAxis)
            lifecycleScope.launch {
                container.sessionSettingsManager.apply(colorLabPaletteUpdateAction(persisted = persisted, colorAxis = colorAxis, toneAxis = toneAxis))
            }
            return
        }
        val panel = latestFilterLabRenderModel?.adjustmentPanel ?: return
        val profileId = panel.selectedProfileId ?: return
        if (!panel.isVisible || panel.mode != FilterAdjustmentMode.LIGHT) return
        if (panel.needsAutoPrepare) return
        val baseSpec = lightPaletteBaseSpec ?: panel.renderSpec
        lifecycleScope.launch {
            container.sessionSettingsManager.updateCustomFilterRenderSpec(filterProfileId = profileId, renderSpec = baseSpec.applyLightPalette(colorAxis, toneAxis))
        }
    }

    @Suppress("EXPOSED_PARAMETER_TYPE")
    override fun selectFilterLabFamily(family: FilterLabFamily) {
        selectedFilterLabFamilyOverride = family
        isFilterAdjustmentVisible = true
        lightPaletteBaseSpec = null
        maybeAutoPrepareFilter()
        renderLatestFilterLab()
    }

    override fun openPortraitLab() {
        val settingsModel = latestSettingsPageRenderModel ?: return
        if (!settingsModel.editingEnabled || settingsModel.photoSection.portraitLab.availability == SettingsControlAvailability.UNSUPPORTED) return
        activePanelRoute = CockpitPanelRoute.Settings(SettingsSubpage.PORTRAIT_LAB)
        renderLatestSettingsSurfaces()
    }

    override fun openWatermarkLabSelector() {
        val settingsModel = latestSettingsPageRenderModel ?: return
        if (!settingsModel.editingEnabled || settingsModel.photoSection.watermarkTemplate.availability == SettingsControlAvailability.UNSUPPORTED) return
        activePanelRoute = CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_SELECTOR)
        selectedWatermarkDetailTemplateId = null
        renderLatestSettingsSurfaces()
    }

    override fun openWatermarkLabDetail(templateId: String) {
        val detailModel = latestWatermarkLabDetailRenderModel
        if (detailModel != null && !detailModel.editingEnabled) return
        selectedWatermarkDetailTemplateId = templateId
        activePanelRoute = CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_DETAIL)
        renderLatestSettingsSurfaces()
    }

    override fun requestCameraPermissionIfNeeded() {
        when {
            hasPermission(Manifest.permission.CAMERA) -> {
                permissionUiController.renderGrantedState(cameraGranted = true, microphoneGranted = hasPermission(Manifest.permission.RECORD_AUDIO))
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                permissionUiController.renderRationalePrompt()
                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            }
            else -> {
                permissionUiController.renderPermanentlyDenied()
            }
        }
    }

    override fun requestMicrophonePermission() {
        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
    }

    override fun showDisabledReason(reason: String) {
        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
    }

    override fun openLatestGalleryMedia() {
        val presentation = latestSessionState?.presentation ?: return
        val target = galleryOpenTargetFor(source = presentation.latestThumbnailSource, savedMediaType = presentation.latestSavedMediaType)
        if (target == null || !galleryLauncher.open(target)) {
            Toast.makeText(this, R.string.gallery_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun exportDevLog() {
        refreshDevLogModel()
        val model = latestDevLogRenderModel ?: return
        if (model.exportContent.isBlank()) return
        val file = devLogExporter.export(model.exportContent)
        captureOutput.text = "Debug log exported: ${file.absolutePath}"
    }

    override fun refreshDevLogModel() {
        val state = latestSessionState ?: return
        val model = devLogRenderModel(state = state, traceEvents = container.trace.snapshot(), isDebugBuild = com.opencamera.app.BuildConfig.DEBUG, selectedTab = selectedDevLogTab, text = AppTextResolver(this))
        latestDevLogRenderModel = model
        devConsoleRenderer.render(model)
    }

    // endregion

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
            templateId = selectedWatermarkDetailTemplateId
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
            showAdjustmentPanel = isFilterAdjustmentVisible,
            adjustmentMode = filterAdjustmentMode
        )
        if (activePanelRoute is CockpitPanelRoute.ColorLab) {
            val colorLabModel = colorLabPanelRenderModel(state, text)
            filterPaletteSurface.updateReticle(colorLabModel.colorAxis, colorLabModel.toneAxis)
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
        settingsRenderer.renderTabs(selectedSettingsTab)
        settingsRenderer.renderPortraitLabPage(portraitLabPage)
        settingsRenderer.renderWatermarkSelectorPage(watermarkSelectorPage)
        settingsRenderer.renderWatermarkDetailPage(watermarkDetailPage)
        filterLabRenderer.renderPage(filterLabPage)
        mainRenderer.renderPanelVisibility(activePanelRoute)
        previewOverlayView.render(previewOverlayRenderModel(state, container.previewEffectAdapter))
        previewOverlayView.updateFocusReticle(
            state.presentation.previewMeteringFeedback?.toFocusReticleRenderModel()
        )
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
        devConsoleRenderer.render(devLogModel)

        val nextThumbnailRenderUri = state.presentation.pendingCaptureFeedback?.let { feedback ->
            feedback.outputPath.takeIf { File(it).isAbsolute }?.let { File(it).toURI().toString() }
        } ?: state.presentation.latestThumbnailSource?.renderUriOrNull()
        when (val command = nextThumbnailRenderCommand(lastRequestedThumbnailUri, nextThumbnailRenderUri)) {
            ThumbnailRenderCommand.NoOp -> Unit
            ThumbnailRenderCommand.Clear -> {
                lastRequestedThumbnailUri = null
                previewThumbnail.setImageDrawable(null)
            }
            is ThumbnailRenderCommand.Load -> {
                lastRequestedThumbnailUri = command.uri
                previewThumbnail.setImageURI(null)
                previewThumbnail.setImageURI(Uri.parse(command.uri))
            }
        }

    }


    private fun selectedFilterLabFamily(state: SessionState): FilterLabFamily {
        return selectedFilterLabFamilyOverride ?: defaultFilterLabFamily(state.activeMode)
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

    internal fun captureConfigDisabledReason(state: SessionState): String? {
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

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun List<FilterAdvancedControlRenderModel>.buttonLabel(
        control: FilterAdvancedControl
    ): String {
        return first { item -> item.control == control }.buttonLabel
    }

    private fun PreviewMeteringFeedback.toFocusReticleRenderModel(): FocusReticleRenderModel {
        return FocusReticleRenderModel(
            normalizedX = normalizedX,
            normalizedY = normalizedY,
            status = when (status) {
                PreviewMeteringFeedbackStatus.REQUESTED -> FocusReticleStatus.REQUESTED
                PreviewMeteringFeedbackStatus.SUCCEEDED -> FocusReticleStatus.SUCCEEDED
                PreviewMeteringFeedbackStatus.DEGRADED_AUTO_EXPOSURE_ONLY -> FocusReticleStatus.DEGRADED
                PreviewMeteringFeedbackStatus.FAILED -> FocusReticleStatus.FAILED
                PreviewMeteringFeedbackStatus.UNSUPPORTED -> FocusReticleStatus.UNSUPPORTED
            }
        )
    }
}
