package com.opencamera.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.MediaActionSound
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.opencamera.app.gesture.GestureAction
import com.opencamera.app.gesture.GestureEvent
import com.opencamera.app.gesture.GestureGuard
import com.opencamera.app.gesture.GestureGuardState
import com.opencamera.app.gesture.GesturePolicy
import com.opencamera.app.gesture.GestureRouter
import com.opencamera.app.gesture.GestureZone
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.opencamera.app.i18n.AppTextResolver
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.renderUriOrNull
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.catalogProfile
import com.opencamera.core.session.SessionIntent
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.FilterRenderSpec
import kotlinx.coroutines.launch
import java.io.File

private enum class SettingsTab { COMMON, PHOTO, VIDEO }

class MainActivity : AppCompatActivity() {
    private val container: AppContainer
        get() = (application as OpenCameraApplication).container

    private var selectedSettingsTab = SettingsTab.COMMON
    private lateinit var previewView: PreviewView
    private lateinit var previewOverlayView: PreviewOverlayView
    private lateinit var panelDismissScrim: View
    private lateinit var titleText: TextView
    private lateinit var permissionStatus: TextView
    private lateinit var buttonColorLabEntry: Button
    private lateinit var buttonSettingsEntry: Button
    private lateinit var buttonLensLabEntry: Button
    private lateinit var buttonFilterEntry: Button
    private lateinit var buttonQuickGrid: Button
    private lateinit var buttonQuickFlash: Button
    private lateinit var buttonFrameRatio43: Button
    private lateinit var buttonFrameRatio169: Button
    private lateinit var buttonFrameRatio11: Button
    private lateinit var buttonQuickLivePhoto: Button
    private lateinit var buttonQuickTimer: Button
    private lateinit var buttonQuickLauncher: Button
    private lateinit var quickBubblePanel: androidx.core.widget.NestedScrollView
    private lateinit var settingsPanel: androidx.core.widget.NestedScrollView
    private lateinit var filterPanel: androidx.core.widget.NestedScrollView
    private lateinit var buttonSettingsBack: Button
    private lateinit var settingsRootContent: LinearLayout
    private lateinit var settingsPortraitLabContent: LinearLayout
    private lateinit var settingsWatermarkSelectorContent: LinearLayout
    private lateinit var settingsWatermarkDetailContent: LinearLayout
    private lateinit var settingsHeadline: TextView
    private lateinit var settingsSupportingText: TextView
    private lateinit var settingsHeroSummary: TextView
    private lateinit var settingsCommonSummary: TextView
    private lateinit var settingsPhotoSummary: TextView
    private lateinit var settingsVideoSummary: TextView
    private lateinit var settingsCatalogFooter: TextView
    private lateinit var settingsEditingHint: TextView
    private lateinit var portraitLabHeadline: TextView
    private lateinit var portraitLabSupportingText: TextView
    private lateinit var portraitLabHeroSummary: TextView
    private lateinit var portraitLabEditingHint: TextView
    private lateinit var buttonPortraitProfile: Button
    private lateinit var buttonPortraitBeautyPreset: Button
    private lateinit var buttonPortraitBeautyStrength: Button
    private lateinit var buttonPortraitBokehEffect: Button
    private lateinit var portraitLabFooter: TextView
    private lateinit var watermarkSelectorHeadline: TextView
    private lateinit var watermarkSelectorSupportingText: TextView
    private lateinit var watermarkSelectorHeroSummary: TextView
    private lateinit var watermarkSelectorList: LinearLayout
    private lateinit var watermarkSelectorEditingHint: TextView
    private lateinit var watermarkSelectorFooter: TextView
    private lateinit var watermarkDetailHeadline: TextView
    private lateinit var watermarkDetailSupportingText: TextView
    private lateinit var watermarkDetailHeroSummary: TextView
    private lateinit var watermarkDetailEditingHint: TextView
    private lateinit var buttonWatermarkPlacement: Button
    private lateinit var buttonWatermarkTextScale: Button
    private lateinit var buttonWatermarkTextOpacity: Button
    private lateinit var buttonWatermarkFrameBackground: Button
    private lateinit var watermarkDetailFooter: TextView
    private lateinit var buttonGridMode: Button
    private lateinit var buttonShutterSound: Button
    private lateinit var buttonSelfieMirror: Button
    private lateinit var buttonPhotoFilter: Button
    private lateinit var buttonPhotoPortraitLab: Button
    private lateinit var buttonPhotoWatermark: Button
    private lateinit var buttonPhotoLive: Button
    private lateinit var buttonPhotoTimer: Button
    private lateinit var buttonVideoResolution: Button
    private lateinit var buttonVideoFrameRate: Button
    private lateinit var buttonVideoDynamicFps: Button
    private lateinit var buttonVideoAudio: Button
    private lateinit var buttonVideoFilter: Button
    private lateinit var buttonCloseSettings: Button
    private lateinit var buttonSettingsTabCommon: Button
    private lateinit var buttonSettingsTabPhoto: Button
    private lateinit var buttonSettingsTabVideo: Button
    private lateinit var settingsCommonSection: LinearLayout
    private lateinit var settingsPhotoSection: LinearLayout
    private lateinit var settingsVideoSection: LinearLayout
    private lateinit var filterHeadline: TextView
    private lateinit var filterSupportingText: TextView
    private lateinit var filterHeroSummary: TextView
    private lateinit var filterCurrentSummary: TextView
    private lateinit var filterSelectionList: LinearLayout
    private lateinit var filterEditingHint: TextView
    private lateinit var filterFooter: TextView
    private lateinit var buttonFilterPhotoTab: Button
    private lateinit var buttonFilterHumanisticTab: Button
    private lateinit var buttonFilterPortraitTab: Button
    private lateinit var buttonFilterVideoTab: Button
    private lateinit var buttonFilterSaveCustom: Button
    private lateinit var filterAdjustmentPanel: LinearLayout
    private lateinit var buttonFilterModeToggle: Button
    private lateinit var filterPaletteSummary: TextView
    private lateinit var filterPaletteHint: TextView
    private lateinit var filterPaletteSurface: FilterPaletteView
    private lateinit var filterAdvancedControls: LinearLayout
    private lateinit var buttonAdvancedExposure: Button
    private lateinit var buttonAdvancedSoftGlow: Button
    private lateinit var buttonAdvancedHalo: Button
    private lateinit var buttonAdvancedGrain: Button
    private lateinit var buttonAdvancedSharpness: Button
    private lateinit var buttonAdvancedVignette: Button
    private lateinit var buttonAdvancedHighlights: Button
    private lateinit var buttonAdvancedShadows: Button
    private lateinit var buttonAdvancedWarmBoost: Button
    private lateinit var buttonAdvancedCoolBoost: Button
    private lateinit var buttonAdvancedTemperatureShift: Button
    private lateinit var buttonAdvancedTintShift: Button
    private lateinit var buttonCloseFilter: Button
    private lateinit var captureOutput: TextView
    private lateinit var previewThumbnail: ImageView
    private lateinit var zoomCapsuleScroll: android.widget.HorizontalScrollView
    private lateinit var modeTrackScroll: android.widget.HorizontalScrollView
    private lateinit var zoomCapsuleRow: LinearLayout
    private lateinit var buttonDevEntry: Button
    private lateinit var devConsolePanel: com.google.android.material.card.MaterialCardView
    private lateinit var buttonDevTabKey: Button
    private lateinit var buttonDevTabCore: Button
    private lateinit var buttonDevTabError: Button
    private lateinit var buttonDevTabAll: Button
    private lateinit var devConsoleTitle: TextView
    private lateinit var devConsoleSummary: TextView
    private lateinit var devConsoleContent: TextView
    private lateinit var buttonDevExport: Button
    private lateinit var buttonDevClose: Button
    private lateinit var shutterButton: Button
    private lateinit var lensFacingButton: Button
    private lateinit var photoModeButton: Button
    private lateinit var documentModeButton: Button
    private lateinit var nightModeButton: Button
    private lateinit var humanisticModeButton: Button
    private lateinit var portraitModeButton: Button
    private lateinit var proModeButton: Button
    private lateinit var videoModeButton: Button
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
    private lateinit var gestureRouter: GestureRouter
    private val gesturePolicy = GesturePolicy()
    private val gestureGuard = GestureGuard()

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

        previewView = findViewById(R.id.cameraPreview)
        previewOverlayView = findViewById(R.id.previewOverlay)
        panelDismissScrim = findViewById(R.id.panelDismissScrim)
        titleText = findViewById(R.id.titleText)
        permissionStatus = findViewById(R.id.permissionStatus)
        buttonColorLabEntry = findViewById(R.id.buttonColorLabEntry)
        buttonSettingsEntry = findViewById(R.id.buttonSettingsEntry)
        buttonLensLabEntry = findViewById(R.id.buttonLensLabEntry)
        buttonFilterEntry = findViewById(R.id.buttonFilterEntry)
        buttonQuickGrid = findViewById(R.id.buttonQuickGrid)
        buttonQuickFlash = findViewById(R.id.buttonQuickFlash)
        buttonFrameRatio43 = findViewById(R.id.buttonFrameRatio43)
        buttonFrameRatio169 = findViewById(R.id.buttonFrameRatio169)
        buttonFrameRatio11 = findViewById(R.id.buttonFrameRatio11)
        buttonQuickLivePhoto = findViewById(R.id.buttonQuickLivePhoto)
        buttonQuickTimer = findViewById(R.id.buttonQuickTimer)
        buttonQuickLauncher = findViewById(R.id.buttonQuickLauncher)
        quickBubblePanel = findViewById(R.id.quickBubblePanel)
        settingsPanel = findViewById(R.id.settingsPanel)
        filterPanel = findViewById(R.id.filterPanel)
        buttonSettingsBack = findViewById(R.id.buttonSettingsBack)
        settingsRootContent = findViewById(R.id.settingsRootContent)
        settingsPortraitLabContent = findViewById(R.id.settingsPortraitLabContent)
        settingsWatermarkSelectorContent = findViewById(R.id.settingsWatermarkSelectorContent)
        settingsWatermarkDetailContent = findViewById(R.id.settingsWatermarkDetailContent)
        settingsHeadline = findViewById(R.id.settingsHeadline)
        settingsSupportingText = findViewById(R.id.settingsSupportingText)
        settingsHeroSummary = findViewById(R.id.settingsHeroSummary)
        settingsCommonSummary = findViewById(R.id.settingsCommonSummary)
        settingsPhotoSummary = findViewById(R.id.settingsPhotoSummary)
        settingsVideoSummary = findViewById(R.id.settingsVideoSummary)
        settingsCatalogFooter = findViewById(R.id.settingsCatalogFooter)
        settingsEditingHint = findViewById(R.id.settingsEditingHint)
        portraitLabHeadline = findViewById(R.id.portraitLabHeadline)
        portraitLabSupportingText = findViewById(R.id.portraitLabSupportingText)
        portraitLabHeroSummary = findViewById(R.id.portraitLabHeroSummary)
        portraitLabEditingHint = findViewById(R.id.portraitLabEditingHint)
        buttonPortraitProfile = findViewById(R.id.buttonPortraitProfile)
        buttonPortraitBeautyPreset = findViewById(R.id.buttonPortraitBeautyPreset)
        buttonPortraitBeautyStrength = findViewById(R.id.buttonPortraitBeautyStrength)
        buttonPortraitBokehEffect = findViewById(R.id.buttonPortraitBokehEffect)
        portraitLabFooter = findViewById(R.id.portraitLabFooter)
        watermarkSelectorHeadline = findViewById(R.id.watermarkSelectorHeadline)
        watermarkSelectorSupportingText = findViewById(R.id.watermarkSelectorSupportingText)
        watermarkSelectorHeroSummary = findViewById(R.id.watermarkSelectorHeroSummary)
        watermarkSelectorList = findViewById(R.id.watermarkSelectorList)
        watermarkSelectorEditingHint = findViewById(R.id.watermarkSelectorEditingHint)
        watermarkSelectorFooter = findViewById(R.id.watermarkSelectorFooter)
        watermarkDetailHeadline = findViewById(R.id.watermarkDetailHeadline)
        watermarkDetailSupportingText = findViewById(R.id.watermarkDetailSupportingText)
        watermarkDetailHeroSummary = findViewById(R.id.watermarkDetailHeroSummary)
        watermarkDetailEditingHint = findViewById(R.id.watermarkDetailEditingHint)
        buttonWatermarkPlacement = findViewById(R.id.buttonWatermarkPlacement)
        buttonWatermarkTextScale = findViewById(R.id.buttonWatermarkTextScale)
        buttonWatermarkTextOpacity = findViewById(R.id.buttonWatermarkTextOpacity)
        buttonWatermarkFrameBackground = findViewById(R.id.buttonWatermarkFrameBackground)
        watermarkDetailFooter = findViewById(R.id.watermarkDetailFooter)
        buttonGridMode = findViewById(R.id.buttonGridMode)
        buttonShutterSound = findViewById(R.id.buttonShutterSound)
        buttonSelfieMirror = findViewById(R.id.buttonSelfieMirror)
        buttonPhotoFilter = findViewById(R.id.buttonPhotoFilter)
        buttonPhotoPortraitLab = findViewById(R.id.buttonPhotoPortraitLab)
        buttonPhotoWatermark = findViewById(R.id.buttonPhotoWatermark)
        buttonPhotoLive = findViewById(R.id.buttonPhotoLive)
        buttonPhotoTimer = findViewById(R.id.buttonPhotoTimer)
        buttonVideoResolution = findViewById(R.id.buttonVideoResolution)
        buttonVideoFrameRate = findViewById(R.id.buttonVideoFrameRate)
        buttonVideoDynamicFps = findViewById(R.id.buttonVideoDynamicFps)
        buttonVideoAudio = findViewById(R.id.buttonVideoAudio)
        buttonVideoFilter = findViewById(R.id.buttonVideoFilter)
        buttonCloseSettings = findViewById(R.id.buttonCloseSettings)
        buttonSettingsTabCommon = findViewById(R.id.buttonSettingsTabCommon)
        buttonSettingsTabPhoto = findViewById(R.id.buttonSettingsTabPhoto)
        buttonSettingsTabVideo = findViewById(R.id.buttonSettingsTabVideo)
        settingsCommonSection = findViewById(R.id.settingsCommonSection)
        settingsPhotoSection = findViewById(R.id.settingsPhotoSection)
        settingsVideoSection = findViewById(R.id.settingsVideoSection)
        filterHeadline = findViewById(R.id.filterHeadline)
        filterSupportingText = findViewById(R.id.filterSupportingText)
        filterHeroSummary = findViewById(R.id.filterHeroSummary)
        filterCurrentSummary = findViewById(R.id.filterCurrentSummary)
        filterSelectionList = findViewById(R.id.filterSelectionList)
        filterEditingHint = findViewById(R.id.filterEditingHint)
        filterFooter = findViewById(R.id.filterFooter)
        buttonFilterPhotoTab = findViewById(R.id.buttonFilterPhotoTab)
        buttonFilterHumanisticTab = findViewById(R.id.buttonFilterHumanisticTab)
        buttonFilterPortraitTab = findViewById(R.id.buttonFilterPortraitTab)
        buttonFilterVideoTab = findViewById(R.id.buttonFilterVideoTab)
        buttonFilterSaveCustom = findViewById(R.id.buttonFilterSaveCustom)
        filterAdjustmentPanel = findViewById(R.id.filterAdjustmentPanel)
        buttonFilterModeToggle = findViewById(R.id.buttonFilterModeToggle)
        filterPaletteSummary = findViewById(R.id.filterPaletteSummary)
        filterPaletteHint = findViewById(R.id.filterPaletteHint)
        filterPaletteSurface = findViewById(R.id.filterPaletteSurface)
        filterAdvancedControls = findViewById(R.id.filterAdvancedControls)
        buttonAdvancedExposure = findViewById(R.id.buttonAdvancedExposure)
        buttonAdvancedSoftGlow = findViewById(R.id.buttonAdvancedSoftGlow)
        buttonAdvancedHalo = findViewById(R.id.buttonAdvancedHalo)
        buttonAdvancedGrain = findViewById(R.id.buttonAdvancedGrain)
        buttonAdvancedSharpness = findViewById(R.id.buttonAdvancedSharpness)
        buttonAdvancedVignette = findViewById(R.id.buttonAdvancedVignette)
        buttonAdvancedHighlights = findViewById(R.id.buttonAdvancedHighlights)
        buttonAdvancedShadows = findViewById(R.id.buttonAdvancedShadows)
        buttonAdvancedWarmBoost = findViewById(R.id.buttonAdvancedWarmBoost)
        buttonAdvancedCoolBoost = findViewById(R.id.buttonAdvancedCoolBoost)
        buttonAdvancedTemperatureShift = findViewById(R.id.buttonAdvancedTemperatureShift)
        buttonAdvancedTintShift = findViewById(R.id.buttonAdvancedTintShift)
        buttonCloseFilter = findViewById(R.id.buttonCloseFilter)
        captureOutput = findViewById(R.id.captureOutput)
        previewThumbnail = findViewById(R.id.previewThumbnail)
        shutterButton = findViewById(R.id.buttonShutter)
        lensFacingButton = findViewById(R.id.buttonLensFacing)
        photoModeButton = findViewById(R.id.buttonPhotoMode)
        documentModeButton = findViewById(R.id.buttonDocumentMode)
        nightModeButton = findViewById(R.id.buttonNightMode)
        humanisticModeButton = findViewById(R.id.buttonHumanisticMode)
        portraitModeButton = findViewById(R.id.buttonPortraitMode)
        proModeButton = findViewById(R.id.buttonProMode)
        videoModeButton = findViewById(R.id.buttonVideoMode)
        zoomCapsuleScroll = findViewById(R.id.zoomCapsuleScroll)
        modeTrackScroll = findViewById(R.id.modeTrackScroll)
        zoomCapsuleRow = findViewById(R.id.zoomCapsuleRow)
        buttonDevEntry = findViewById(R.id.buttonDevEntry)
        devConsolePanel = findViewById(R.id.devConsolePanel)
        buttonDevTabKey = findViewById(R.id.buttonDevTabKey)
        buttonDevTabCore = findViewById(R.id.buttonDevTabCore)
        buttonDevTabError = findViewById(R.id.buttonDevTabError)
        buttonDevTabAll = findViewById(R.id.buttonDevTabAll)
        devConsoleTitle = findViewById(R.id.devConsoleTitle)
        devConsoleSummary = findViewById(R.id.devConsoleSummary)
        devConsoleContent = findViewById(R.id.devConsoleContent)
        buttonDevExport = findViewById(R.id.buttonDevExport)
        buttonDevClose = findViewById(R.id.buttonDevClose)
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

        bindActions()
        bindGestureRouter()
        bindState()
        syncPermissionState()
        applyControlRotationForDisplay()
    }

    private fun applyControlRotationForDisplay() {
        val rotation = display?.rotation ?: android.view.Surface.ROTATION_0
        val orientationModel = orientationRenderModel(rotation)
        val degrees = orientationModel.controlRotationDegrees
        listOf(
            // Right rail utility buttons
            buttonFilterEntry,
            buttonQuickLauncher,
            buttonLensLabEntry,
            buttonDevEntry,
            // Bottom cockpit controls
            shutterButton,
            lensFacingButton,
            // Quick panel text-bearing buttons
            buttonFrameRatio43,
            buttonFrameRatio169,
            buttonFrameRatio11,
            buttonGridMode
        ).forEach { it.rotation = degrees }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyControlRotationForDisplay()
    }

    override fun onStart() {
        super.onStart()
        container.cameraCoordinator.attachPreviewHost(this, previewView)
        syncPermissionState()
        dispatch(SessionIntent.Boot)
        dispatch(SessionIntent.PreviewHostAttached)
        requestCameraPermissionIfNeeded()
    }

    override fun onStop() {
        super.onStop()
        dispatch(SessionIntent.PreviewHostDetached("Activity moved to background"))
    }

    private fun bindActions() {
        panelDismissScrim.setOnClickListener {
            activePanelRoute = CockpitPanelRoute.None
            selectedWatermarkDetailTemplateId = null
            selectedFilterLabFamilyOverride = null
            isFilterAdjustmentVisible = false
            lightPaletteBaseSpec = null
            selectedSettingsTab = SettingsTab.COMMON
            renderPanelVisibility()
            renderDevConsoleVisibility()
        }
        buttonColorLabEntry.setOnClickListener {
            activePanelRoute = if (activePanelRoute is CockpitPanelRoute.LensLab) {
                CockpitPanelRoute.None
            } else {
                CockpitPanelRoute.LensLab
            }
            if (activePanelRoute is CockpitPanelRoute.LensLab) {
                isFilterAdjustmentVisible = true
                maybeAutoPrepareFilter()
                renderLatestFilterLab()
            } else {
                selectedFilterLabFamilyOverride = null
                isFilterAdjustmentVisible = false
                lightPaletteBaseSpec = null
            }
            renderPanelVisibility()
        }
        buttonSettingsEntry.setOnClickListener {
            toggleSettingsPanel()
        }
        buttonLensLabEntry.setOnClickListener {
            activePanelRoute = if (activePanelRoute is CockpitPanelRoute.LensLab) {
                CockpitPanelRoute.None
            } else {
                CockpitPanelRoute.LensLab
            }
            if (activePanelRoute is CockpitPanelRoute.LensLab) {
                isFilterAdjustmentVisible = true
                maybeAutoPrepareFilter()
                renderLatestFilterLab()
            } else {
                selectedFilterLabFamilyOverride = null
                isFilterAdjustmentVisible = false
                lightPaletteBaseSpec = null
            }
            renderPanelVisibility()
        }
        buttonFilterEntry.setOnClickListener {
            activePanelRoute = if (activePanelRoute is CockpitPanelRoute.FilterLab) {
                CockpitPanelRoute.None
            } else {
                CockpitPanelRoute.FilterLab
            }
            if (activePanelRoute is CockpitPanelRoute.FilterLab) {
                isFilterAdjustmentVisible = true
                maybeAutoPrepareFilter()
                renderLatestFilterLab()
            } else {
                selectedFilterLabFamilyOverride = null
                isFilterAdjustmentVisible = false
                lightPaletteBaseSpec = null
            }
            renderPanelVisibility()
        }
        buttonCloseSettings.setOnClickListener {
            activePanelRoute = CockpitPanelRoute.None
            selectedWatermarkDetailTemplateId = null
            selectedSettingsTab = SettingsTab.COMMON
            renderPanelVisibility()
        }
        buttonSettingsTabCommon.setOnClickListener { selectedSettingsTab = SettingsTab.COMMON; renderSettingsTabs() }
        buttonSettingsTabPhoto.setOnClickListener { selectedSettingsTab = SettingsTab.PHOTO; renderSettingsTabs() }
        buttonSettingsTabVideo.setOnClickListener { selectedSettingsTab = SettingsTab.VIDEO; renderSettingsTabs() }
        buttonSettingsBack.setOnClickListener {
            val currentSettings = activePanelRoute as? CockpitPanelRoute.Settings ?: return@setOnClickListener
            activePanelRoute = when (currentSettings.subpage) {
                SettingsSubpage.WATERMARK_DETAIL -> {
                    selectedWatermarkDetailTemplateId = null
                    CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_SELECTOR)
                }
                SettingsSubpage.PORTRAIT_LAB,
                SettingsSubpage.WATERMARK_SELECTOR -> CockpitPanelRoute.Settings()
                else -> CockpitPanelRoute.Settings()
            }
            renderLatestSettingsSurfaces()
            renderPanelVisibility()
        }
        buttonCloseFilter.setOnClickListener {
            activePanelRoute = CockpitPanelRoute.None
            selectedFilterLabFamilyOverride = null
            isFilterAdjustmentVisible = false
            lightPaletteBaseSpec = null
            renderPanelVisibility()
        }
        buttonDevEntry.setOnClickListener {
            activePanelRoute = if (activePanelRoute is CockpitPanelRoute.DevConsole) {
                CockpitPanelRoute.None
            } else {
                CockpitPanelRoute.DevConsole
            }
            renderDevConsoleVisibility()
            renderPanelVisibility()
        }
        buttonQuickLauncher.setOnClickListener {
            activePanelRoute = if (activePanelRoute is CockpitPanelRoute.QuickBubble) {
                CockpitPanelRoute.None
            } else {
                CockpitPanelRoute.QuickBubble
            }
            latestSessionState?.let(::render)
        }
        buttonQuickGrid.setOnClickListener {
            applySettingsControlAction(latestSettingsPageRenderModel?.commonSection?.gridMode)
        }
        buttonQuickFlash.setOnClickListener {
            dispatch(SessionIntent.StillCaptureQualityToggled)
        }
        buttonFrameRatio43.setOnClickListener {
            dispatch(SessionIntent.FrameRatioSelected(FrameRatio.RATIO_4_3))
        }
        buttonFrameRatio169.setOnClickListener {
            dispatch(SessionIntent.FrameRatioSelected(FrameRatio.RATIO_16_9))
        }
        buttonFrameRatio11.setOnClickListener {
            dispatch(SessionIntent.FrameRatioSelected(FrameRatio.RATIO_1_1))
        }
        buttonQuickLivePhoto.setOnClickListener {
            applySettingsControlAction(latestSettingsPageRenderModel?.photoSection?.livePhoto)
        }
        buttonQuickTimer.setOnClickListener {
            applySettingsControlAction(latestSettingsPageRenderModel?.photoSection?.countdown)
        }
        buttonDevTabKey.setOnClickListener {
            selectedDevLogTab = DevLogTab.KEY
            refreshDevLogModel()
        }
        buttonDevTabCore.setOnClickListener {
            selectedDevLogTab = DevLogTab.CORE
            refreshDevLogModel()
        }
        buttonDevTabError.setOnClickListener {
            selectedDevLogTab = DevLogTab.ERROR
            refreshDevLogModel()
        }
        buttonDevTabAll.setOnClickListener {
            selectedDevLogTab = DevLogTab.ALL
            refreshDevLogModel()
        }
        buttonDevExport.setOnClickListener {
            refreshDevLogModel()
            val model = latestDevLogRenderModel ?: return@setOnClickListener
            if (model.exportContent.isBlank()) return@setOnClickListener
            val file = devLogExporter.export(model.exportContent)
            captureOutput.text = "Debug log exported: ${file.absolutePath}"
        }
        buttonDevClose.setOnClickListener {
            activePanelRoute = CockpitPanelRoute.None
            renderDevConsoleVisibility()
            renderPanelVisibility()
        }
        bindModeTrackTouch()
        shutterButton.setOnClickListener {
            if (!hasPermission(Manifest.permission.CAMERA)) {
                requestCameraPermissionIfNeeded()
                return@setOnClickListener
            }
            dispatch(SessionIntent.ShutterPressed)
        }
        lensFacingButton.setOnClickListener {
            dispatch(SessionIntent.LensFacingToggled)
        }
        previewThumbnail.setOnClickListener {
            val presentation = latestSessionState?.presentation ?: return@setOnClickListener
            val filePath = presentation.latestCapturePath
                ?: presentation.latestVideoPath
                ?: return@setOnClickListener
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(this, R.string.gallery_open_failed, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val mimeType = when (presentation.latestSavedMediaType) {
                com.opencamera.core.session.SavedMediaType.VIDEO -> "video/*"
                com.opencamera.core.session.SavedMediaType.PHOTO -> "image/*"
                null -> "image/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { startActivity(intent) }.onFailure {
                Toast.makeText(this, R.string.gallery_open_failed, Toast.LENGTH_SHORT).show()
            }
        }
        buttonGridMode.setOnClickListener {
            applySettingsControlAction(latestSettingsPageRenderModel?.commonSection?.gridMode)
        }
        buttonShutterSound.setOnClickListener {
            applySettingsControlAction(latestSettingsPageRenderModel?.commonSection?.shutterSound)
        }
        buttonSelfieMirror.setOnClickListener {
            applySettingsControlAction(latestSettingsPageRenderModel?.commonSection?.selfieMirror)
        }
        buttonPhotoFilter.setOnClickListener {
            applySettingsControlAction(latestSettingsPageRenderModel?.photoSection?.defaultFilter)
        }
        buttonPhotoPortraitLab.setOnClickListener {
            openPortraitLab()
        }
        buttonPhotoWatermark.setOnClickListener {
            openWatermarkLabSelector()
        }
        buttonPortraitProfile.setOnClickListener {
            applySettingsControlAction(latestPortraitLabRenderModel?.profileControl)
        }
        buttonPortraitBeautyPreset.setOnClickListener {
            applySettingsControlAction(latestPortraitLabRenderModel?.beautyPresetControl)
        }
        buttonPortraitBeautyStrength.setOnClickListener {
            applySettingsControlAction(latestPortraitLabRenderModel?.beautyStrengthControl)
        }
        buttonPortraitBokehEffect.setOnClickListener {
            applySettingsControlAction(latestPortraitLabRenderModel?.bokehEffectControl)
        }
        buttonPhotoLive.setOnClickListener {
            applySettingsControlAction(latestSettingsPageRenderModel?.photoSection?.livePhoto)
        }
        buttonPhotoTimer.setOnClickListener {
            applySettingsControlAction(latestSettingsPageRenderModel?.photoSection?.countdown)
        }
        buttonVideoResolution.setOnClickListener {
            applySettingsControlAction(latestSettingsPageRenderModel?.videoSection?.resolution)
        }
        buttonVideoFrameRate.setOnClickListener {
            applySettingsControlAction(latestSettingsPageRenderModel?.videoSection?.frameRate)
        }
        buttonVideoDynamicFps.setOnClickListener {
            applySettingsControlAction(latestSettingsPageRenderModel?.videoSection?.dynamicFps)
        }
        buttonVideoAudio.setOnClickListener {
            applySettingsControlAction(latestSettingsPageRenderModel?.videoSection?.audioProfile)
        }
        buttonVideoFilter.setOnClickListener {
            applySettingsControlAction(latestSettingsPageRenderModel?.videoSection?.defaultFilter)
        }
        buttonWatermarkPlacement.setOnClickListener {
            applySettingsControlAction(latestWatermarkLabDetailRenderModel?.placementControl)
        }
        buttonWatermarkTextScale.setOnClickListener {
            applySettingsControlAction(latestWatermarkLabDetailRenderModel?.textScaleControl)
        }
        buttonWatermarkTextOpacity.setOnClickListener {
            applySettingsControlAction(latestWatermarkLabDetailRenderModel?.textOpacityControl)
        }
        buttonWatermarkFrameBackground.setOnClickListener {
            applySettingsControlAction(latestWatermarkLabDetailRenderModel?.frameBackgroundControl)
        }
        buttonFilterPhotoTab.setOnClickListener {
            selectFilterLabFamily(FilterLabFamily.PHOTO)
        }
        buttonFilterHumanisticTab.setOnClickListener {
            selectFilterLabFamily(FilterLabFamily.HUMANISTIC)
        }
        buttonFilterPortraitTab.setOnClickListener {
            selectFilterLabFamily(FilterLabFamily.PORTRAIT)
        }
        buttonFilterVideoTab.setOnClickListener {
            selectFilterLabFamily(FilterLabFamily.VIDEO)
        }
        buttonFilterSaveCustom.setOnClickListener {
            saveCurrentFilterAsCustom(latestFilterLabRenderModel?.saveCustomControl)
        }
        buttonFilterModeToggle.setOnClickListener {
            toggleFilterAdjustmentMode()
        }
        buttonAdvancedExposure.setOnClickListener {
            applyAdvancedFilterControl(FilterAdvancedControl.EXPOSURE)
        }
        buttonAdvancedSoftGlow.setOnClickListener {
            applyAdvancedFilterControl(FilterAdvancedControl.SOFT_GLOW)
        }
        buttonAdvancedHalo.setOnClickListener {
            applyAdvancedFilterControl(FilterAdvancedControl.HALO)
        }
        buttonAdvancedGrain.setOnClickListener {
            applyAdvancedFilterControl(FilterAdvancedControl.GRAIN)
        }
        buttonAdvancedSharpness.setOnClickListener {
            applyAdvancedFilterControl(FilterAdvancedControl.SHARPNESS)
        }
        buttonAdvancedVignette.setOnClickListener {
            applyAdvancedFilterControl(FilterAdvancedControl.VIGNETTE)
        }
        buttonAdvancedHighlights.setOnClickListener {
            applyAdvancedFilterControl(FilterAdvancedControl.HIGHLIGHTS)
        }
        buttonAdvancedShadows.setOnClickListener {
            applyAdvancedFilterControl(FilterAdvancedControl.SHADOWS)
        }
        buttonAdvancedWarmBoost.setOnClickListener {
            applyAdvancedFilterControl(FilterAdvancedControl.WARM_BOOST)
        }
        buttonAdvancedCoolBoost.setOnClickListener {
            applyAdvancedFilterControl(FilterAdvancedControl.COOL_BOOST)
        }
        buttonAdvancedTemperatureShift.setOnClickListener {
            applyAdvancedFilterControl(FilterAdvancedControl.TEMPERATURE_SHIFT)
        }
        buttonAdvancedTintShift.setOnClickListener {
            applyAdvancedFilterControl(FilterAdvancedControl.TINT_SHIFT)
        }
        filterPaletteSurface.setOnPaletteTouchListener { colorAxis, toneAxis ->
            handleFilterPaletteTouch(colorAxis, toneAxis)
        }
    }

    private fun bindGestureRouter() {
        gestureRouter = GestureRouter(this) { event ->
            val guardState = GestureGuardState(
                activePanel = activePanelRoute,
                isFilterAdjustmentActive = isFilterAdjustmentVisible
            )
            if (!gestureGuard.isGestureAllowed(GestureZone.PREVIEW, guardState)) {
                return@GestureRouter
            }
            if (event is GestureEvent.HorizontalScroll && !gestureGuard.isHorizontalScrollAllowed(guardState)) {
                return@GestureRouter
            }
            val activeMode = latestSessionState?.activeMode ?: return@GestureRouter
            val currentZoom = latestSessionState?.activeDeviceGraph?.preview?.zoomRatio ?: 1.0f
            when (val action = gesturePolicy.map(event, activeMode, currentZoom)) {
                is GestureAction.DispatchSession -> dispatch(action.intent)
                is GestureAction.FocusAt -> {
                    // TODO: focus/metering tap-to-focus integration
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
            gestureRouter.onTouchEvent(v, event)
        }
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
            templateId = selectedWatermarkDetailTemplateId
                ?: state.settings.persisted.photo.defaultWatermarkTemplateId,
            text = text
        )
        val filterLabPage = filterLabPageRenderModel(
            state = state,
            text = text,
            selectedFamily = selectedFilterLabFamily(state),
            panelRole = if (activePanelRoute is CockpitPanelRoute.LensLab) {
                StyleAndColorLabRole.LENS_LAB
            } else {
                StyleAndColorLabRole.STYLE
            },
            showAdjustmentPanel = isFilterAdjustmentVisible,
            adjustmentMode = filterAdjustmentMode
        )
        val modeTrack = modeTrackRenderModel(state, text)
        latestSettingsPageRenderModel = settingsPage
        latestPortraitLabRenderModel = portraitLabPage
        latestWatermarkLabSelectorRenderModel = watermarkSelectorPage
        latestWatermarkLabDetailRenderModel = watermarkDetailPage
        latestFilterLabRenderModel = filterLabPage
        // Top panel: lightweight primary status
        titleText.text = getString(R.string.app_name)
        renderModeTrack(modeTrack)
        renderSettingsPage(settingsPage)
        renderPortraitLabPage(portraitLabPage)
        renderWatermarkLabSelectorPage(watermarkSelectorPage)
        renderWatermarkLabDetailPage(watermarkDetailPage)
        renderFilterLabPage(filterLabPage)
        previewOverlayView.render(previewOverlayRenderModel(state, container.previewEffectAdapter))
        previewView.scaleX = if (
            state.activeDeviceGraph.preferredLensFacing == LensFacing.FRONT &&
            state.settings.persisted.common.selfieMirrorEnabled
        ) {
            -1f
        } else {
            1f
        }
        maybePlayShutterSound(state)

        val shutterLabel = when (state.recordingStatus) {
            RecordingStatus.IDLE -> getString(R.string.button_photo_capture)
            RecordingStatus.REQUESTING -> getString(R.string.button_recording_starting)
            RecordingStatus.RECORDING -> getString(R.string.button_recording_stop)
            RecordingStatus.STOPPING -> getString(R.string.button_recording_saving)
        }
        shutterButton.contentDescription = shutterLabel
        shutterButton.text = ""
        if (state.recordingStatus != RecordingStatus.IDLE) {
            shutterButton.setBackgroundResource(R.drawable.bg_shutter_recording_selector)
        } else {
            shutterButton.setBackgroundResource(R.drawable.bg_shutter_selector)
        }
        lensFacingButton.text = controls.lensFacingButtonLabel
        shutterButton.isEnabled = state.modeSnapshot.state.isShutterEnabled
        lensFacingButton.isEnabled = controls.lensFacingEnabled
        captureOutput.text = sessionCaptureOutputText(state, sessionUiStrings())
        renderZoomCapsules(controls)
        renderQuickBubble(settingsPage)
        buttonDevEntry.isVisible = com.opencamera.app.BuildConfig.DEBUG
        val devLogModel = devLogRenderModel(
            state = state,
            traceEvents = container.trace.snapshot(),
            isDebugBuild = com.opencamera.app.BuildConfig.DEBUG,
            selectedTab = selectedDevLogTab,
            text = text
        )
        latestDevLogRenderModel = devLogModel
        renderDevConsole()

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

    private fun renderSettingsPage(model: SessionSettingsPageRenderModel) {
        settingsHeadline.text = model.headline
        settingsSupportingText.text = model.supportingText
        settingsHeroSummary.text = model.heroSummary
        settingsHeroSummary.isVisible = model.heroSummary.isNotEmpty()
        settingsCommonSummary.text = model.commonSection.summary
        settingsCommonSummary.isVisible = model.commonSection.summary.isNotEmpty()
        settingsPhotoSummary.text = model.photoSection.summary
        settingsPhotoSummary.isVisible = model.photoSection.summary.isNotEmpty()
        settingsVideoSummary.text = model.videoSection.summary
        settingsVideoSummary.isVisible = model.videoSection.summary.isNotEmpty()
        settingsCatalogFooter.text = model.catalogFooter
        settingsCatalogFooter.isVisible = model.catalogFooter.isNotEmpty()
        settingsEditingHint.text = model.editingHint
        renderSettingsControl(buttonGridMode, model.commonSection.gridMode, model.editingEnabled)
        renderSettingsControl(buttonShutterSound, model.commonSection.shutterSound, model.editingEnabled)
        renderSettingsControl(buttonSelfieMirror, model.commonSection.selfieMirror, model.editingEnabled)
        renderSettingsControl(buttonPhotoFilter, model.photoSection.defaultFilter, model.editingEnabled)
        buttonPhotoPortraitLab.text = model.photoSection.portraitLab.buttonLabel
        buttonPhotoPortraitLab.isEnabled = model.editingEnabled &&
            model.photoSection.portraitLab.availability != SettingsControlAvailability.UNSUPPORTED
        buttonPhotoWatermark.text = model.photoSection.watermarkTemplate.buttonLabel
        buttonPhotoWatermark.isEnabled = model.editingEnabled &&
            model.photoSection.watermarkTemplate.availability != SettingsControlAvailability.UNSUPPORTED
        renderSettingsControl(buttonPhotoLive, model.photoSection.livePhoto, model.editingEnabled)
        renderSettingsControl(buttonPhotoTimer, model.photoSection.countdown, model.editingEnabled)
        renderSettingsControl(buttonVideoResolution, model.videoSection.resolution, model.editingEnabled)
        renderSettingsControl(buttonVideoFrameRate, model.videoSection.frameRate, model.editingEnabled)
        renderSettingsControl(buttonVideoDynamicFps, model.videoSection.dynamicFps, model.editingEnabled)
        renderSettingsControl(buttonVideoAudio, model.videoSection.audioProfile, model.editingEnabled)
        renderSettingsControl(buttonVideoFilter, model.videoSection.defaultFilter, model.editingEnabled)
        renderSettingsTabs()
        renderPanelVisibility()
    }

    private fun renderSettingsTabs() {
        buttonSettingsTabCommon.isEnabled = selectedSettingsTab != SettingsTab.COMMON
        buttonSettingsTabPhoto.isEnabled = selectedSettingsTab != SettingsTab.PHOTO
        buttonSettingsTabVideo.isEnabled = selectedSettingsTab != SettingsTab.VIDEO
        buttonSettingsTabCommon.alpha = if (selectedSettingsTab == SettingsTab.COMMON) 1f else 0.84f
        buttonSettingsTabPhoto.alpha = if (selectedSettingsTab == SettingsTab.PHOTO) 1f else 0.84f
        buttonSettingsTabVideo.alpha = if (selectedSettingsTab == SettingsTab.VIDEO) 1f else 0.84f
        settingsCommonSection.isVisible = selectedSettingsTab == SettingsTab.COMMON
        settingsPhotoSection.isVisible = selectedSettingsTab == SettingsTab.PHOTO
        settingsVideoSection.isVisible = selectedSettingsTab == SettingsTab.VIDEO
    }

    private fun renderPortraitLabPage(model: PortraitLabPageRenderModel) {
        portraitLabHeadline.text = model.headline
        portraitLabSupportingText.text = model.supportingText
        portraitLabHeroSummary.text = model.heroSummary
        portraitLabHeroSummary.isVisible = model.heroSummary.isNotEmpty()
        portraitLabEditingHint.text = model.editingHint
        portraitLabFooter.text = model.footer
        renderSettingsControl(buttonPortraitProfile, model.profileControl, model.editingEnabled)
        renderSettingsControl(
            buttonPortraitBeautyPreset,
            model.beautyPresetControl,
            model.editingEnabled
        )
        renderSettingsControl(
            buttonPortraitBeautyStrength,
            model.beautyStrengthControl,
            model.editingEnabled
        )
        renderSettingsControl(
            buttonPortraitBokehEffect,
            model.bokehEffectControl,
            model.editingEnabled
        )
        renderPanelVisibility()
    }

    private fun renderWatermarkLabSelectorPage(model: WatermarkLabSelectorRenderModel) {
        watermarkSelectorHeadline.text = model.headline
        watermarkSelectorSupportingText.text = model.supportingText
        watermarkSelectorHeroSummary.text = model.heroSummary
        watermarkSelectorHeroSummary.isVisible = model.heroSummary.isNotEmpty()
        watermarkSelectorEditingHint.text = model.editingHint
        watermarkSelectorFooter.text = model.footer
        watermarkSelectorList.removeAllViews()
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
                topMargin = if (watermarkSelectorList.childCount == 0) 0 else 8.dp
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
            watermarkSelectorList.addView(card, params)
        }
        renderPanelVisibility()
    }

    private fun renderWatermarkLabDetailPage(model: WatermarkLabDetailRenderModel) {
        watermarkDetailHeadline.text = model.headline
        watermarkDetailSupportingText.text = model.supportingText
        watermarkDetailHeroSummary.text = model.heroSummary
        watermarkDetailHeroSummary.isVisible = model.heroSummary.isNotEmpty()
        watermarkDetailEditingHint.text = model.editingHint
        watermarkDetailFooter.text = model.footer
        renderSettingsControl(
            buttonWatermarkPlacement,
            model.placementControl,
            model.editingEnabled
        )
        renderSettingsControl(
            buttonWatermarkTextScale,
            model.textScaleControl,
            model.editingEnabled
        )
        renderSettingsControl(
            buttonWatermarkTextOpacity,
            model.textOpacityControl,
            model.editingEnabled
        )
        model.frameBackgroundControl?.let { control ->
            buttonWatermarkFrameBackground.isVisible = true
            renderSettingsControl(
                buttonWatermarkFrameBackground,
                control,
                model.editingEnabled
            )
        } ?: run {
            buttonWatermarkFrameBackground.isVisible = false
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
        filterHeadline.text = model.headline
        filterSupportingText.text = model.supportingText
        filterHeroSummary.text = model.heroSummary
        filterHeroSummary.isVisible = model.heroSummary.isNotEmpty()
        filterCurrentSummary.text = model.currentFilterSummary
        filterEditingHint.text = model.editingHint
        filterFooter.text = model.footer

        // Show/hide family tabs based on panel role
        val tabsVisible = model.showFamilyTabs
        buttonFilterPhotoTab.isVisible = tabsVisible
        buttonFilterHumanisticTab.isVisible = tabsVisible
        buttonFilterPortraitTab.isVisible = tabsVisible
        buttonFilterVideoTab.isVisible = tabsVisible
        if (tabsVisible) {
            renderFilterLabTab(buttonFilterPhotoTab, model.photoTab)
            renderFilterLabTab(buttonFilterHumanisticTab, model.humanisticTab)
            renderFilterLabTab(buttonFilterPortraitTab, model.portraitTab)
            renderFilterLabTab(buttonFilterVideoTab, model.videoTab)
        }

        // Show/hide filter selection list based on panel role
        if (model.showFilterItems) {
            renderFilterSelectionList(model)
            renderSaveCustomControl(model.saveCustomControl, model.editingEnabled)
            filterCurrentSummary.isVisible = true
        } else {
            filterSelectionList.removeAllViews()
            filterCurrentSummary.isVisible = false
            buttonFilterSaveCustom.isVisible = false
        }

        // Show/hide adjustment panel based on panel role
        if (model.showAdjustmentPanel) {
            renderAdjustmentPanel(model.adjustmentPanel, model.editingEnabled)
        } else {
            filterAdjustmentPanel.isVisible = false
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
        buttonFilterSaveCustom.text = model.buttonLabel
        buttonFilterSaveCustom.isEnabled = editingEnabled && model.isEnabled
    }

    private fun renderFilterSelectionList(model: FilterLabPageRenderModel) {
        filterSelectionList.removeAllViews()
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
                topMargin = if (filterSelectionList.childCount == 0) 0 else 8.dp
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
                        isFilterAdjustmentVisible = true
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
            filterSelectionList.addView(card, params)
        }
    }

    private fun renderAdjustmentPanel(
        model: FilterAdjustmentPanelRenderModel,
        editingEnabled: Boolean
    ) {
        filterAdjustmentPanel.isVisible = model.isVisible
        buttonFilterModeToggle.text = model.modeToggleLabel
        buttonFilterModeToggle.isEnabled = editingEnabled && model.selectedProfileId != null
        filterPaletteSummary.text = "${model.selectedProfileLabel}\n${model.lightPalette.summary}"
        filterPaletteHint.text = model.lightPalette.supportingText
        filterPaletteSurface.isVisible = model.mode == FilterAdjustmentMode.LIGHT
        filterPaletteHint.isVisible = model.mode == FilterAdjustmentMode.LIGHT
        filterAdvancedControls.isVisible = model.mode == FilterAdjustmentMode.ADVANCED
        buttonAdvancedExposure.text = model.advancedControls.buttonLabel(FilterAdvancedControl.EXPOSURE)
        buttonAdvancedSoftGlow.text = model.advancedControls.buttonLabel(FilterAdvancedControl.SOFT_GLOW)
        buttonAdvancedHalo.text = model.advancedControls.buttonLabel(FilterAdvancedControl.HALO)
        buttonAdvancedGrain.text = model.advancedControls.buttonLabel(FilterAdvancedControl.GRAIN)
        buttonAdvancedSharpness.text = model.advancedControls.buttonLabel(FilterAdvancedControl.SHARPNESS)
        buttonAdvancedVignette.text = model.advancedControls.buttonLabel(FilterAdvancedControl.VIGNETTE)
        buttonAdvancedHighlights.text = model.advancedControls.buttonLabel(FilterAdvancedControl.HIGHLIGHTS)
        buttonAdvancedShadows.text = model.advancedControls.buttonLabel(FilterAdvancedControl.SHADOWS)
        buttonAdvancedWarmBoost.text = model.advancedControls.buttonLabel(FilterAdvancedControl.WARM_BOOST)
        buttonAdvancedCoolBoost.text = model.advancedControls.buttonLabel(FilterAdvancedControl.COOL_BOOST)
        buttonAdvancedTemperatureShift.text =
            model.advancedControls.buttonLabel(FilterAdvancedControl.TEMPERATURE_SHIFT)
        buttonAdvancedTintShift.text =
            model.advancedControls.buttonLabel(FilterAdvancedControl.TINT_SHIFT)
        val advancedButtons = listOf(
            buttonAdvancedExposure,
            buttonAdvancedSoftGlow,
            buttonAdvancedHalo,
            buttonAdvancedGrain,
            buttonAdvancedSharpness,
            buttonAdvancedVignette,
            buttonAdvancedHighlights,
            buttonAdvancedShadows,
            buttonAdvancedWarmBoost,
            buttonAdvancedCoolBoost,
            buttonAdvancedTemperatureShift,
            buttonAdvancedTintShift
        )
        advancedButtons.forEach { button ->
            button.isEnabled = editingEnabled && model.selectedProfileId != null
        }
    }

    private fun toggleSettingsPanel() {
        activePanelRoute = if (activePanelRoute.isSettingsOpen) {
            CockpitPanelRoute.None
        } else {
            CockpitPanelRoute.Settings()
        }
        selectedWatermarkDetailTemplateId = null
        if (activePanelRoute.isSettingsOpen) {
            renderLatestSettingsSurfaces()
        }
        renderPanelVisibility()
    }

    private fun renderQuickBubble(settingsPage: SessionSettingsPageRenderModel) {
        val grid = settingsPage.commonSection.gridMode
        buttonQuickGrid.text = getString(R.string.button_quick_grid)
        buttonQuickGrid.contentDescription = "${getString(R.string.button_quick_grid)} ${grid.value}"
        buttonQuickGrid.isEnabled = grid.isInteractive

        buttonQuickFlash.text = getString(R.string.button_quick_flash)
        latestSessionState?.let { state ->
            val frameControl = frameRatioControlRenderModel(state)
            buttonFrameRatio43.isEnabled = frameControl.isEnabled
            buttonFrameRatio169.isEnabled = frameControl.isEnabled
            buttonFrameRatio11.isEnabled = frameControl.isEnabled
            frameControl.options.forEach { option ->
                when (option.ratio) {
                    FrameRatio.RATIO_4_3 -> buttonFrameRatio43.alpha = if (option.isSelected) 1f else 0.6f
                    FrameRatio.RATIO_16_9 -> buttonFrameRatio169.alpha = if (option.isSelected) 1f else 0.6f
                    FrameRatio.RATIO_1_1 -> buttonFrameRatio11.alpha = if (option.isSelected) 1f else 0.6f
                }
            }
        }

        val live = settingsPage.photoSection.livePhoto
        buttonQuickLivePhoto.text = getString(R.string.button_quick_live)
        buttonQuickLivePhoto.contentDescription = "${getString(R.string.button_quick_live)} ${live.value}"
        buttonQuickLivePhoto.isEnabled = live.isInteractive

        val timer = settingsPage.photoSection.countdown
        buttonQuickTimer.text = getString(R.string.button_quick_timer)
        buttonQuickTimer.contentDescription = "${getString(R.string.button_quick_timer)} ${timer.value}"
        buttonQuickTimer.isEnabled = timer.isInteractive
    }

    private fun renderPanelVisibility() {
        val route = activePanelRoute
        settingsPanel.isVisible = route.isSettingsOpen
        filterPanel.isVisible = route is CockpitPanelRoute.FilterLab || route is CockpitPanelRoute.LensLab
        panelDismissScrim.isVisible = route.isAnyPanelOpen

        val subpage = (route as? CockpitPanelRoute.Settings)?.subpage
        settingsRootContent.isVisible = route.isSettingsOpen && (subpage == null || subpage == SettingsSubpage.ROOT)
        settingsPortraitLabContent.isVisible = subpage == SettingsSubpage.PORTRAIT_LAB
        settingsWatermarkSelectorContent.isVisible = subpage == SettingsSubpage.WATERMARK_SELECTOR
        settingsWatermarkDetailContent.isVisible = subpage == SettingsSubpage.WATERMARK_DETAIL
        buttonSettingsBack.isVisible = route.isSettingsOpen && subpage != null && subpage != SettingsSubpage.ROOT

        buttonColorLabEntry.alpha = if (route is CockpitPanelRoute.LensLab) 1f else 0.92f
        buttonSettingsEntry.alpha = if (route.isSettingsOpen) 1f else 0.92f
        buttonFilterEntry.alpha = if (route is CockpitPanelRoute.FilterLab) 1f else 0.92f
        quickBubblePanel.isVisible = route is CockpitPanelRoute.QuickBubble
        buttonQuickLauncher.alpha = if (route is CockpitPanelRoute.QuickBubble) 1f else 0.86f
    }

    private fun renderZoomCapsules(controls: SessionControlsRenderModel) {
        zoomCapsuleScroll.isVisible = controls.isZoomCapsuleRowVisible
        if (!controls.isZoomCapsuleRowVisible) return
        zoomCapsuleRow.removeAllViews()
        controls.zoomCapsules.forEach { capsule ->
            val chip = TextView(this).apply {
                text = capsule.label
                textSize = resources.getDimension(R.dimen.text_size_zoom_chip) / resources.displayMetrics.density
                minWidth = resources.getDimension(R.dimen.zoom_chip_min_width).toInt()
                minHeight = resources.getDimension(R.dimen.zoom_chip_min_height).toInt()
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT
                setPadding(
                    resources.getDimension(R.dimen.zoom_chip_padding_h).toInt(),
                    resources.getDimension(R.dimen.zoom_chip_padding_v).toInt(),
                    resources.getDimension(R.dimen.zoom_chip_padding_h).toInt(),
                    resources.getDimension(R.dimen.zoom_chip_padding_v).toInt()
                )
                if (capsule.isActive) {
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.oc_text_primary))
                    setBackgroundResource(R.drawable.bg_zoom_chip_active)
                } else {
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.oc_text_secondary))
                    setBackgroundResource(R.drawable.bg_zoom_chip)
                }
                setOnClickListener {
                    dispatch(SessionIntent.ApplyZoomRatio(capsule.ratio))
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = if (zoomCapsuleRow.childCount == 0) 0 else 4.dp
            }
            zoomCapsuleRow.addView(chip, params)
        }
    }

    private fun renderDevConsoleVisibility() {
        val isDevVisible = activePanelRoute is CockpitPanelRoute.DevConsole
        devConsolePanel.isVisible = isDevVisible
        buttonDevEntry.alpha = if (isDevVisible) 1f else 0.78f
        if (isDevVisible) {
            renderDevConsole()
        }
    }

    private fun refreshDevLogModel() {
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
        devConsoleTitle.text = model.title
        devConsoleSummary.text = model.summaryText
        devConsoleSummary.isVisible = model.summaryText.isNotBlank()
        devConsoleContent.text = model.content
        buttonDevTabKey.isEnabled = model.selectedTab != DevLogTab.KEY
        buttonDevTabCore.isEnabled = model.selectedTab != DevLogTab.CORE
        buttonDevTabError.isEnabled = model.selectedTab != DevLogTab.ERROR
        buttonDevTabAll.isEnabled = model.selectedTab != DevLogTab.ALL
        val activeAlpha = 1f
        val inactiveAlpha = 0.84f
        buttonDevTabKey.alpha = if (model.selectedTab == DevLogTab.KEY) activeAlpha else inactiveAlpha
        buttonDevTabCore.alpha = if (model.selectedTab == DevLogTab.CORE) activeAlpha else inactiveAlpha
        buttonDevTabError.alpha = if (model.selectedTab == DevLogTab.ERROR) activeAlpha else inactiveAlpha
        buttonDevTabAll.alpha = if (model.selectedTab == DevLogTab.ALL) activeAlpha else inactiveAlpha
    }

    private val modeTrackScrollGuard = ModeTrackScrollGuard(scrollSlopPx = 12f)

    private fun bindModeTrackTouch() {
        val buttons = listOf(
            photoModeButton to ModeId.PHOTO,
            nightModeButton to ModeId.NIGHT,
            portraitModeButton to ModeId.PORTRAIT,
            proModeButton to ModeId.PRO,
            videoModeButton to ModeId.VIDEO,
            documentModeButton to ModeId.DOCUMENT
        )
        humanisticModeButton.visibility = View.GONE
        humanisticModeButton.setOnClickListener(null)
        modeTrackScrollGuard.attach(modeTrackScroll)
        buttons.forEach { (button, modeId) ->
            button.setOnClickListener {
                if (modeTrackScrollGuard.isScrolling) return@setOnClickListener
                val state = latestSessionState
                if (state != null) {
                    val reason = captureConfigDisabledReason(state)
                    if (reason != null) {
                        showDisabledReason(reason)
                        return@setOnClickListener
                    }
                }
                if (modeId == ModeId.VIDEO && !hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
                dispatch(SessionIntent.SwitchMode(modeId))
            }
        }
    }

    private var lastAutoScrolledActiveMode: ModeId? = null

    private fun renderModeTrack(model: ModeTrackRenderModel) {
        val buttons = listOf(
            photoModeButton,
            nightModeButton,
            portraitModeButton,
            proModeButton,
            videoModeButton,
            documentModeButton
        )
        humanisticModeButton.visibility = View.GONE
        model.items.forEachIndexed { index, item ->
            if (index < buttons.size) {
                val button = buttons[index]
                button.visibility = View.VISIBLE
                button.text = item.trackLabel
                button.isEnabled = item.isAvailable
                if (item.isActive) {
                    button.setTextColor(ContextCompat.getColor(this, R.color.oc_text_primary))
                    button.setTypeface(null, android.graphics.Typeface.BOLD)
                    button.setBackgroundResource(R.drawable.bg_mode_track_active)
                    button.alpha = 1f
                } else {
                    button.setTextColor(ContextCompat.getColor(this, R.color.oc_text_secondary))
                    button.setTypeface(null, android.graphics.Typeface.NORMAL)
                    button.background = null
                    button.alpha = if (item.isAvailable) 0.82f else 0.42f
                }
            }
        }
        buttons.drop(model.items.size).forEach { button ->
            button.visibility = View.GONE
        }
        // Auto-scroll only when active mode changes and user is not dragging
        val activeItem = model.items.firstOrNull { it.isActive }
        val activeModeId = activeItem?.modeId
        if (activeModeId != null && activeModeId != lastAutoScrolledActiveMode && !modeTrackScrollGuard.isScrolling) {
            lastAutoScrolledActiveMode = activeModeId
            modeTrackScroll.post {
                val activeButton = buttons.firstOrNull { b ->
                    val idx = buttons.indexOf(b)
                    idx < model.items.size && model.items[idx].isActive
                }
                activeButton?.let {
                    val viewWidth = modeTrackScroll.width
                    val chipCenter = it.left + it.width / 2
                    val scrollX = (chipCenter - viewWidth / 2).coerceAtLeast(0)
                    modeTrackScroll.smoothScrollTo(scrollX, 0)
                }
            }
        }
    }

    private fun requestCameraPermissionIfNeeded() {
        when {
            hasPermission(Manifest.permission.CAMERA) -> {
                permissionStatus.text = if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    getString(R.string.permission_granted)
                } else {
                    getString(R.string.permission_camera_only)
                }
            }

            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                permissionStatus.text = getString(R.string.permission_pending)
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    )
                )
            }

            else -> {
                permissionStatus.text = getString(R.string.permission_pending)
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    )
                )
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

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onBackPressed() {
        when (activePanelRoute) {
            CockpitPanelRoute.None -> {
                super.onBackPressed()
            }
            is CockpitPanelRoute.Settings -> {
                val settings = activePanelRoute as CockpitPanelRoute.Settings
                when (settings.subpage) {
                    SettingsSubpage.WATERMARK_DETAIL -> {
                        selectedWatermarkDetailTemplateId = null
                        activePanelRoute = CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_SELECTOR)
                    }
                    SettingsSubpage.PORTRAIT_LAB,
                    SettingsSubpage.WATERMARK_SELECTOR -> {
                        activePanelRoute = CockpitPanelRoute.Settings()
                    }
                    SettingsSubpage.ROOT -> {
                        activePanelRoute = CockpitPanelRoute.None
                        selectedSettingsTab = SettingsTab.COMMON
                    }
                }
                renderLatestSettingsSurfaces()
                renderPanelVisibility()
            }
            CockpitPanelRoute.FilterLab,
            CockpitPanelRoute.LensLab,
            CockpitPanelRoute.DevConsole,
            CockpitPanelRoute.QuickBubble -> {
                if (activePanelRoute is CockpitPanelRoute.FilterLab || activePanelRoute is CockpitPanelRoute.LensLab) {
                    selectedFilterLabFamilyOverride = null
                    isFilterAdjustmentVisible = false
                    lightPaletteBaseSpec = null
                }
                activePanelRoute = CockpitPanelRoute.None
                renderPanelVisibility()
                renderDevConsoleVisibility()
            }
        }
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

    private fun applySettingsAction(action: PersistedSettingsAction) {
        lifecycleScope.launch {
            val result = container.sessionSettingsManager.apply(action)
            if (result is SessionSettingsApplyResult.BlockedByActiveShot) {
                Toast.makeText(this@MainActivity, "拍摄进行中，无法更改设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applySettingsControlAction(control: SettingsControlRenderModel?) {
        if (control == null) {
            Toast.makeText(this, "设置尚未加载", Toast.LENGTH_SHORT).show()
            return
        }
        val action = control.nextAction
        if (action == null) {
            Toast.makeText(this, "当前模式不支持此操作", Toast.LENGTH_SHORT).show()
            return
        }
        applySettingsAction(action)
    }

    private fun openPortraitLab() {
        val settingsModel = latestSettingsPageRenderModel ?: return
        if (!settingsModel.editingEnabled ||
            settingsModel.photoSection.portraitLab.availability == SettingsControlAvailability.UNSUPPORTED
        ) {
            return
        }
        activePanelRoute = CockpitPanelRoute.Settings(SettingsSubpage.PORTRAIT_LAB)
        renderLatestSettingsSurfaces()
    }

    private fun openWatermarkLabSelector() {
        val settingsModel = latestSettingsPageRenderModel ?: return
        if (!settingsModel.editingEnabled ||
            settingsModel.photoSection.watermarkTemplate.availability == SettingsControlAvailability.UNSUPPORTED
        ) {
            return
        }
        activePanelRoute = CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_SELECTOR)
        selectedWatermarkDetailTemplateId = null
        renderLatestSettingsSurfaces()
    }

    private fun openWatermarkLabDetail(templateId: String) {
        val detailModel = latestWatermarkLabDetailRenderModel
        if (detailModel != null && !detailModel.editingEnabled) {
            return
        }
        selectedWatermarkDetailTemplateId = templateId
        activePanelRoute = CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_DETAIL)
        renderLatestSettingsSurfaces()
    }

    private fun selectFilterLabFamily(family: FilterLabFamily) {
        selectedFilterLabFamilyOverride = family
        isFilterAdjustmentVisible = true
        lightPaletteBaseSpec = null
        maybeAutoPrepareFilter()
        renderLatestFilterLab()
    }

    private fun selectedFilterLabFamily(state: SessionState): FilterLabFamily {
        return selectedFilterLabFamilyOverride ?: defaultFilterLabFamily(state.activeMode)
    }

    private fun renderLatestFilterLab() {
        val state = latestSessionState ?: return
        val text = AppTextResolver(this)
        val model = filterLabPageRenderModel(
            state = state,
            text = text,
            selectedFamily = selectedFilterLabFamily(state),
            panelRole = if (activePanelRoute is CockpitPanelRoute.LensLab) {
                StyleAndColorLabRole.LENS_LAB
            } else {
                StyleAndColorLabRole.STYLE
            },
            showAdjustmentPanel = isFilterAdjustmentVisible,
            adjustmentMode = filterAdjustmentMode
        )
        latestFilterLabRenderModel = model
        if (isFilterAdjustmentVisible && lightPaletteBaseSpec == null) {
            lightPaletteBaseSpec = model.adjustmentPanel.renderSpec
        }
        renderFilterLabPage(model)
    }

    private fun renderLatestSettingsSurfaces() {
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
        renderSettingsPage(settingsModel)
        renderPortraitLabPage(portraitLabModel)
        renderWatermarkLabSelectorPage(selectorModel)
        renderWatermarkLabDetailPage(detailModel)
    }

    private fun saveCurrentFilterAsCustom(control: FilterLabSaveCustomRenderModel?) {
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

    private fun maybeAutoPrepareFilter() {
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

    private fun openSelectedFilterAdjustment(control: FilterLabAdjustRenderModel?) {
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
                isFilterAdjustmentVisible = true
                filterAdjustmentMode = FilterAdjustmentMode.LIGHT
                lightPaletteBaseSpec = latestFilterLabRenderModel?.adjustmentPanel?.renderSpec
                renderLatestFilterLab()
            }
        }
    }

    private fun toggleFilterAdjustmentMode() {
        filterAdjustmentMode = if (filterAdjustmentMode == FilterAdjustmentMode.LIGHT) {
            FilterAdjustmentMode.ADVANCED
        } else {
            FilterAdjustmentMode.LIGHT
        }
        if (filterAdjustmentMode == FilterAdjustmentMode.LIGHT) {
            lightPaletteBaseSpec = latestFilterLabRenderModel?.adjustmentPanel?.renderSpec
        }
        renderLatestFilterLab()
    }

    private fun applyAdvancedFilterControl(control: FilterAdvancedControl) {
        val panel = latestFilterLabRenderModel?.adjustmentPanel ?: return
        val profileId = panel.selectedProfileId ?: return
        lifecycleScope.launch {
            container.sessionSettingsManager.updateCustomFilterRenderSpec(
                filterProfileId = profileId,
                renderSpec = panel.renderSpec.nextAdvancedControl(control)
            )
        }
    }

    private fun handleFilterPaletteTouch(colorAxis: Float, toneAxis: Float) {
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

    private fun dispatch(intent: SessionIntent) {
        lifecycleScope.launch {
            container.cameraSession.dispatch(intent)
        }
    }

    private fun showDisabledReason(reason: String) {
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
