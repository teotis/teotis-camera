package com.opencamera.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.MotionEvent
import android.view.View
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
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.renderUriOrNull
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.catalogProfile
import com.opencamera.core.session.SessionIntent
import com.opencamera.core.session.SessionState
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.FilterRenderSpec
import kotlinx.coroutines.launch

private enum class SettingsSubpage {
    ROOT,
    PORTRAIT_LAB,
    WATERMARK_SELECTOR,
    WATERMARK_DETAIL
}

class MainActivity : AppCompatActivity() {
    private val container: AppContainer
        get() = (application as OpenCameraApplication).container

    private lateinit var previewView: PreviewView
    private lateinit var previewOverlayView: PreviewOverlayView
    private lateinit var panelDismissScrim: View
    private lateinit var titleText: TextView
    private lateinit var permissionStatus: TextView
    private lateinit var buttonSettingsEntry: Button
    private lateinit var buttonFilterEntry: Button
    private lateinit var buttonQuickFlash: Button
    private lateinit var buttonQuickRatio: Button
    private lateinit var buttonQuickLauncher: Button
    private lateinit var quickBubblePanel: LinearLayout
    private var isQuickBubblePanelVisible = false
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
    private lateinit var zoomCapsuleRow: LinearLayout
    private lateinit var buttonDevEntry: Button
    private lateinit var devConsolePanel: com.google.android.material.card.MaterialCardView
    private lateinit var buttonDevTabKey: Button
    private lateinit var buttonDevTabCore: Button
    private lateinit var buttonDevTabError: Button
    private lateinit var buttonDevTabAll: Button
    private lateinit var devConsoleTitle: TextView
    private lateinit var devConsoleContent: TextView
    private lateinit var buttonDevExport: Button
    private lateinit var buttonDevClose: Button
    private lateinit var shutterButton: Button
    private lateinit var lensFacingButton: Button
    private lateinit var zoomRatioButton: Button
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
    private var isSettingsPanelVisible = false
    private var isFilterPanelVisible = false
    private var isDevConsoleVisible = false
    private var selectedDevLogTab = DevLogTab.KEY
    private var latestDevLogRenderModel: DevLogRenderModel? = null
    private lateinit var devLogExporter: DevLogExporter
    private var latestSettingsPageRenderModel: SessionSettingsPageRenderModel? = null
    private var latestPortraitLabRenderModel: PortraitLabPageRenderModel? = null
    private var latestWatermarkLabSelectorRenderModel: WatermarkLabSelectorRenderModel? = null
    private var latestWatermarkLabDetailRenderModel: WatermarkLabDetailRenderModel? = null
    private var latestFilterLabRenderModel: FilterLabPageRenderModel? = null
    private var latestSessionState: SessionState? = null
    private var currentSettingsSubpage = SettingsSubpage.ROOT
    private var selectedWatermarkDetailTemplateId: String? = null
    private var selectedFilterLabFamilyOverride: FilterLabFamily? = null
    private var isFilterAdjustmentVisible = false
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
        buttonSettingsEntry = findViewById(R.id.buttonSettingsEntry)
        buttonFilterEntry = findViewById(R.id.buttonFilterEntry)
        buttonQuickFlash = findViewById(R.id.buttonQuickFlash)
        buttonQuickRatio = findViewById(R.id.buttonQuickRatio)
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
        zoomRatioButton = findViewById(R.id.buttonZoomRatio)
        photoModeButton = findViewById(R.id.buttonPhotoMode)
        documentModeButton = findViewById(R.id.buttonDocumentMode)
        nightModeButton = findViewById(R.id.buttonNightMode)
        humanisticModeButton = findViewById(R.id.buttonHumanisticMode)
        portraitModeButton = findViewById(R.id.buttonPortraitMode)
        proModeButton = findViewById(R.id.buttonProMode)
        videoModeButton = findViewById(R.id.buttonVideoMode)
        zoomCapsuleScroll = findViewById(R.id.zoomCapsuleScroll)
        zoomCapsuleRow = findViewById(R.id.zoomCapsuleRow)
        buttonDevEntry = findViewById(R.id.buttonDevEntry)
        devConsolePanel = findViewById(R.id.devConsolePanel)
        buttonDevTabKey = findViewById(R.id.buttonDevTabKey)
        buttonDevTabCore = findViewById(R.id.buttonDevTabCore)
        buttonDevTabError = findViewById(R.id.buttonDevTabError)
        buttonDevTabAll = findViewById(R.id.buttonDevTabAll)
        devConsoleTitle = findViewById(R.id.devConsoleTitle)
        devConsoleContent = findViewById(R.id.devConsoleContent)
        buttonDevExport = findViewById(R.id.buttonDevExport)
        buttonDevClose = findViewById(R.id.buttonDevClose)
        devLogExporter = DevLogExporter(this)

        bindActions()
        bindGestureRouter()
        bindState()
        syncPermissionState()
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
            isSettingsPanelVisible = false
            isFilterPanelVisible = false
            isQuickBubblePanelVisible = false
            isDevConsoleVisible = false
            currentSettingsSubpage = SettingsSubpage.ROOT
            selectedWatermarkDetailTemplateId = null
            selectedFilterLabFamilyOverride = null
            isFilterAdjustmentVisible = false
            renderPanelVisibility()
            renderDevConsoleVisibility()
        }
        buttonSettingsEntry.setOnClickListener {
            isSettingsPanelVisible = !isSettingsPanelVisible
            if (isSettingsPanelVisible) {
                isFilterPanelVisible = false
                isQuickBubblePanelVisible = false
                currentSettingsSubpage = SettingsSubpage.ROOT
                selectedWatermarkDetailTemplateId = null
                renderLatestSettingsSurfaces()
            }
            renderPanelVisibility()
        }
        buttonFilterEntry.setOnClickListener {
            isFilterPanelVisible = !isFilterPanelVisible
            if (isFilterPanelVisible) {
                isSettingsPanelVisible = false
                isQuickBubblePanelVisible = false
                renderLatestFilterLab()
            } else {
                selectedFilterLabFamilyOverride = null
                isFilterAdjustmentVisible = false
            }
            renderPanelVisibility()
        }
        buttonCloseSettings.setOnClickListener {
            isSettingsPanelVisible = false
            currentSettingsSubpage = SettingsSubpage.ROOT
            selectedWatermarkDetailTemplateId = null
            renderPanelVisibility()
        }
        buttonSettingsBack.setOnClickListener {
            currentSettingsSubpage = when (currentSettingsSubpage) {
                SettingsSubpage.ROOT -> SettingsSubpage.ROOT
                SettingsSubpage.PORTRAIT_LAB -> SettingsSubpage.ROOT
                SettingsSubpage.WATERMARK_SELECTOR -> SettingsSubpage.ROOT
                SettingsSubpage.WATERMARK_DETAIL -> SettingsSubpage.WATERMARK_SELECTOR
            }
            if (currentSettingsSubpage != SettingsSubpage.WATERMARK_DETAIL) {
                selectedWatermarkDetailTemplateId = null
            }
            renderLatestSettingsSurfaces()
        }
        buttonCloseFilter.setOnClickListener {
            isFilterPanelVisible = false
            selectedFilterLabFamilyOverride = null
            isFilterAdjustmentVisible = false
            renderPanelVisibility()
        }
        buttonDevEntry.setOnClickListener {
            isDevConsoleVisible = !isDevConsoleVisible
            if (isDevConsoleVisible) {
                isQuickBubblePanelVisible = false
            }
            renderDevConsoleVisibility()
        }
        buttonQuickLauncher.setOnClickListener {
            isQuickBubblePanelVisible = !isQuickBubblePanelVisible
            if (isQuickBubblePanelVisible) {
                isSettingsPanelVisible = false
                isFilterPanelVisible = false
                isDevConsoleVisible = false
            }
            latestSessionState?.let(::render)
        }
        buttonDevTabKey.setOnClickListener {
            selectedDevLogTab = DevLogTab.KEY
            renderDevConsole()
        }
        buttonDevTabCore.setOnClickListener {
            selectedDevLogTab = DevLogTab.CORE
            renderDevConsole()
        }
        buttonDevTabError.setOnClickListener {
            selectedDevLogTab = DevLogTab.ERROR
            renderDevConsole()
        }
        buttonDevTabAll.setOnClickListener {
            selectedDevLogTab = DevLogTab.ALL
            renderDevConsole()
        }
        buttonDevExport.setOnClickListener {
            val model = latestDevLogRenderModel ?: return@setOnClickListener
            if (model.exportContent.isBlank()) return@setOnClickListener
            val file = devLogExporter.export(model.exportContent)
            captureOutput.text = "Debug log exported: ${file.absolutePath}"
        }
        buttonDevClose.setOnClickListener {
            isDevConsoleVisible = false
            renderDevConsoleVisibility()
        }
        photoModeButton.setOnClickListener {
            dispatch(SessionIntent.SwitchMode(ModeId.PHOTO))
        }
        documentModeButton.setOnClickListener {
            dispatch(SessionIntent.SwitchMode(ModeId.DOCUMENT))
        }
        nightModeButton.setOnClickListener {
            dispatch(SessionIntent.SwitchMode(ModeId.NIGHT))
        }
        humanisticModeButton.setOnClickListener {
            dispatch(SessionIntent.SwitchMode(ModeId.HUMANISTIC))
        }
        portraitModeButton.setOnClickListener {
            dispatch(SessionIntent.SwitchMode(ModeId.PORTRAIT))
        }
        proModeButton.setOnClickListener {
            dispatch(SessionIntent.SwitchMode(ModeId.PRO))
        }
        videoModeButton.setOnClickListener {
            if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
            dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        }
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
        zoomRatioButton.setOnClickListener {
            dispatch(SessionIntent.ZoomRatioToggled)
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
                isSettingsPanelOpen = isSettingsPanelVisible,
                isFilterPanelOpen = isFilterPanelVisible,
                isMoreControlsOpen = false,
                isFilterAdjustmentActive = isFilterAdjustmentVisible
            )
            if (!gestureGuard.isGestureAllowed(GestureZone.PREVIEW, guardState)) {
                return@GestureRouter
            }
            if (event is GestureEvent.HorizontalScroll && !gestureGuard.isHorizontalScrollAllowed(guardState)) {
                return@GestureRouter
            }
            val activeMode = latestSessionState?.activeMode ?: return@GestureRouter
            when (val action = gesturePolicy.map(event, activeMode)) {
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
        titleText.text = "${getString(R.string.app_name)} · ${text.modeDisplayName(state.activeMode)}"
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

        shutterButton.text = state.modeSnapshot.uiSpec.shutterLabel
        lensFacingButton.text = controls.lensFacingButtonLabel
        zoomRatioButton.text = controls.zoomButtonLabel
        shutterButton.isEnabled = state.modeSnapshot.state.isShutterEnabled
        lensFacingButton.isEnabled = controls.lensFacingEnabled
        zoomRatioButton.isEnabled = controls.zoomEnabled
        captureOutput.text = sessionCaptureOutputText(state, sessionUiStrings())
        renderZoomCapsules(controls)
        buttonDevEntry.isVisible = com.opencamera.app.BuildConfig.DEBUG
        val devLogModel = devLogRenderModel(
            state = state,
            traceEvents = container.trace.snapshot(),
            isDebugBuild = com.opencamera.app.BuildConfig.DEBUG,
            selectedTab = selectedDevLogTab
        )
        latestDevLogRenderModel = devLogModel
        renderDevConsole()

        val nextThumbnailRenderUri = state.presentation.latestThumbnailSource?.renderUriOrNull()
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
        settingsCommonSummary.text = model.commonSection.summary
        settingsPhotoSummary.text = model.photoSection.summary
        settingsVideoSummary.text = model.videoSection.summary
        settingsCatalogFooter.text = model.catalogFooter
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
        renderPanelVisibility()
    }

    private fun renderPortraitLabPage(model: PortraitLabPageRenderModel) {
        portraitLabHeadline.text = model.headline
        portraitLabSupportingText.text = model.supportingText
        portraitLabHeroSummary.text = model.heroSummary
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
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
            }
            card.addView(title)
            val supporting = TextView(this).apply {
                text = item.supportingText
                textSize = 12f
                setTextColor(0xFFD9E6F7.toInt())
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
        filterCurrentSummary.text = model.currentFilterSummary
        filterEditingHint.text = model.editingHint
        filterFooter.text = model.footer
        renderFilterLabTab(buttonFilterPhotoTab, model.photoTab)
        renderFilterLabTab(buttonFilterHumanisticTab, model.humanisticTab)
        renderFilterLabTab(buttonFilterPortraitTab, model.portraitTab)
        renderFilterLabTab(buttonFilterVideoTab, model.videoTab)
        renderFilterSelectionList(model)
        renderSaveCustomControl(model.saveCustomControl, model.editingEnabled)
        renderAdjustmentPanel(model.adjustmentPanel, model.editingEnabled)
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
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
            }
            card.addView(title)

            val supporting = TextView(this).apply {
                text = item.supportingText
                textSize = 12f
                setTextColor(0xFFD9E6F7.toInt())
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
                        isFilterAdjustmentVisible = false
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

    private fun renderPanelVisibility() {
        settingsPanel.isVisible = isSettingsPanelVisible
        filterPanel.isVisible = isFilterPanelVisible
        val anyPanelOpen = isSettingsPanelVisible || isFilterPanelVisible || isQuickBubblePanelVisible || isDevConsoleVisible
        panelDismissScrim.isVisible = anyPanelOpen
        val showRoot = isSettingsPanelVisible && currentSettingsSubpage == SettingsSubpage.ROOT
        val showPortraitLab =
            isSettingsPanelVisible && currentSettingsSubpage == SettingsSubpage.PORTRAIT_LAB
        val showWatermarkSelector =
            isSettingsPanelVisible && currentSettingsSubpage == SettingsSubpage.WATERMARK_SELECTOR
        val showWatermarkDetail =
            isSettingsPanelVisible && currentSettingsSubpage == SettingsSubpage.WATERMARK_DETAIL
        settingsRootContent.isVisible = showRoot
        settingsPortraitLabContent.isVisible = showPortraitLab
        settingsWatermarkSelectorContent.isVisible = showWatermarkSelector
        settingsWatermarkDetailContent.isVisible = showWatermarkDetail
        buttonSettingsBack.isVisible = !showRoot
        buttonSettingsEntry.alpha = if (isSettingsPanelVisible) 1f else 0.92f
        buttonFilterEntry.alpha = if (isFilterPanelVisible) 1f else 0.92f
        quickBubblePanel.isVisible = isQuickBubblePanelVisible
        buttonQuickLauncher.alpha = if (isQuickBubblePanelVisible) 1f else 0.86f
    }

    private fun renderZoomCapsules(controls: SessionControlsRenderModel) {
        zoomCapsuleScroll.isVisible = controls.isZoomCapsuleRowVisible
        if (!controls.isZoomCapsuleRowVisible) return
        zoomCapsuleRow.removeAllViews()
        controls.zoomCapsules.forEach { capsule ->
            val button = Button(
                this,
                null,
                0,
                R.style.Widget_OpenCamera_CompactButton
            ).apply {
                text = capsule.label
                isAllCaps = false
                textSize = 11f
                if (capsule.isActive) {
                    setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundResource(R.drawable.bg_mode_track_active)
                    alpha = 1f
                } else {
                    setTextColor(0xFFF5F7FA.toInt())
                    background = null
                    alpha = 0.78f
                }
                setOnClickListener {
                    dispatch(SessionIntent.ApplyZoomRatio(capsule.ratio))
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = if (zoomCapsuleRow.childCount == 0) 0 else 6.dp
            }
            zoomCapsuleRow.addView(button, params)
        }
    }

    private fun renderDevConsoleVisibility() {
        devConsolePanel.isVisible = isDevConsoleVisible && com.opencamera.app.BuildConfig.DEBUG
        buttonDevEntry.alpha = if (isDevConsoleVisible) 1f else 0.78f
        if (isDevConsoleVisible) {
            renderDevConsole()
        }
    }

    private fun renderDevConsole() {
        val model = latestDevLogRenderModel ?: return
        devConsoleTitle.text = model.title
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

    private fun renderModeTrack(model: ModeTrackRenderModel) {
        val buttons = listOf(
            photoModeButton,
            documentModeButton,
            nightModeButton,
            humanisticModeButton,
            portraitModeButton,
            proModeButton,
            videoModeButton
        )
        model.items.forEachIndexed { index, item ->
            if (index < buttons.size) {
                val button = buttons[index]
                button.text = item.trackLabel
                button.isEnabled = item.isAvailable
                if (item.isActive) {
                    button.setTextColor(0xFFFFFFFF.toInt())
                    button.setTypeface(null, android.graphics.Typeface.BOLD)
                    button.setBackgroundResource(R.drawable.bg_mode_track_active)
                    button.alpha = 1f
                } else {
                    button.setTextColor(0xFFF5F7FA.toInt())
                    button.setTypeface(null, android.graphics.Typeface.NORMAL)
                    button.background = null
                    button.alpha = if (item.isAvailable) 0.78f else 0.42f
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
            container.sessionSettingsManager.apply(action)
        }
    }

    private fun applySettingsControlAction(control: SettingsControlRenderModel?) {
        control?.nextAction?.let(::applySettingsAction)
    }

    private fun openPortraitLab() {
        val settingsModel = latestSettingsPageRenderModel ?: return
        if (!settingsModel.editingEnabled ||
            settingsModel.photoSection.portraitLab.availability == SettingsControlAvailability.UNSUPPORTED
        ) {
            return
        }
        currentSettingsSubpage = SettingsSubpage.PORTRAIT_LAB
        renderLatestSettingsSurfaces()
    }

    private fun openWatermarkLabSelector() {
        val settingsModel = latestSettingsPageRenderModel ?: return
        if (!settingsModel.editingEnabled ||
            settingsModel.photoSection.watermarkTemplate.availability == SettingsControlAvailability.UNSUPPORTED
        ) {
            return
        }
        currentSettingsSubpage = SettingsSubpage.WATERMARK_SELECTOR
        selectedWatermarkDetailTemplateId = null
        renderLatestSettingsSurfaces()
    }

    private fun openWatermarkLabDetail(templateId: String) {
        val detailModel = latestWatermarkLabDetailRenderModel
        if (detailModel != null && !detailModel.editingEnabled) {
            return
        }
        selectedWatermarkDetailTemplateId = templateId
        currentSettingsSubpage = SettingsSubpage.WATERMARK_DETAIL
        renderLatestSettingsSurfaces()
    }

    private fun selectFilterLabFamily(family: FilterLabFamily) {
        selectedFilterLabFamilyOverride = family
        isFilterAdjustmentVisible = false
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
            showAdjustmentPanel = isFilterAdjustmentVisible,
            adjustmentMode = filterAdjustmentMode
        )
        latestFilterLabRenderModel = model
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
