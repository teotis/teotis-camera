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
import com.opencamera.core.session.PreviewMeteringFeedback
import com.opencamera.core.session.PreviewMeteringFeedbackStatus
import com.opencamera.core.session.SessionIntent
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.settings.ColorLabSpec
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PersistedSettingsAction
import kotlinx.coroutines.launch
import java.io.File

private enum class SettingsTab { COMMON, PHOTO, VIDEO }

class MainActivity : AppCompatActivity() {
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

        views = MainActivityViews.bind(this)
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
            activePanelRoute = if (activePanelRoute is CockpitPanelRoute.ColorLab) {
                CockpitPanelRoute.None
            } else {
                CockpitPanelRoute.ColorLab
            }
            if (activePanelRoute is CockpitPanelRoute.ColorLab) {
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
        buttonFilterEntry.setOnClickListener {
            activePanelRoute = if (activePanelRoute is CockpitPanelRoute.StyleLab) {
                CockpitPanelRoute.None
            } else {
                CockpitPanelRoute.StyleLab
            }
            if (activePanelRoute is CockpitPanelRoute.StyleLab) {
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
            val target = galleryOpenTargetFor(
                source = presentation.latestThumbnailSource,
                savedMediaType = presentation.latestSavedMediaType
            ) ?: run {
                Toast.makeText(this, R.string.gallery_open_failed, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val uri = when (target.kind) {
                GalleryOpenUriKind.CONTENT_URI -> Uri.parse(target.uri)
                GalleryOpenUriKind.ABSOLUTE_FILE -> {
                    val file = File(target.uri)
                    if (!file.exists()) {
                        Toast.makeText(this, R.string.gallery_open_failed, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                }
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, target.mimeType)
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
            if (activePanelRoute is CockpitPanelRoute.ColorLab) {
                lifecycleScope.launch {
                    container.sessionSettingsManager.apply(
                        neutralColorLabAction()
                    )
                }
            } else {
                toggleFilterAdjustmentMode()
            }
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
                    val tap = normalizedPreviewTapOrNull(
                        tapX = action.x,
                        tapY = action.y,
                        viewWidth = previewView.width,
                        viewHeight = previewView.height,
                        activeFrameRect = previewOverlayView.currentActiveFrameRectOrNull()
                    ) ?: return@GestureRouter
                    dispatch(
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
        titleText.text = getString(R.string.app_name)
        renderModeTrack(modeTrack)
        renderSettingsPage(settingsPage)
        renderPortraitLabPage(portraitLabPage)
        renderWatermarkLabSelectorPage(watermarkSelectorPage)
        renderWatermarkLabDetailPage(watermarkDetailPage)
        renderFilterLabPage(filterLabPage)
        previewOverlayView.render(previewOverlayRenderModel(state, container.previewEffectAdapter))
        previewOverlayView.updateFocusReticle(
            state.presentation.previewMeteringFeedback?.toFocusReticleRenderModel()
        )
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
            filterSelectionCard.isVisible = true
            filterCurrentSummary.isVisible = true
            filterSectionFiltersTitle.isVisible = true
        } else {
            filterSelectionList.removeAllViews()
            filterSelectionCard.isVisible = false
            filterCurrentSummary.isVisible = false
            filterSectionFiltersTitle.isVisible = false
            buttonFilterSaveCustom.isVisible = false
        }

        // Show/hide adjustment panel based on panel role
        filterSectionPaletteTitle.isVisible = model.showAdjustmentPanel
        if (model.showAdjustmentPanel) {
            renderAdjustmentPanel(
                model.adjustmentPanel,
                model.editingEnabled,
                model.showAdvancedControls,
                model.showModeToggle
            )
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
        button.isSingleLine = true
        button.maxLines = 1
        button.ellipsize = android.text.TextUtils.TruncateAt.END
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
        editingEnabled: Boolean,
        showAdvancedControls: Boolean = true,
        showModeToggle: Boolean = true
    ) {
        filterAdjustmentPanel.isVisible = model.isVisible
        buttonFilterModeToggle.isVisible = showModeToggle
        if (showAdvancedControls) {
            buttonFilterModeToggle.text = model.modeToggleLabel
            buttonFilterModeToggle.isEnabled = editingEnabled && model.selectedProfileId != null
        } else {
            buttonFilterModeToggle.text = getString(R.string.button_color_lab_reset)
            buttonFilterModeToggle.isEnabled = true
        }
        filterPaletteSummary.text = listOf(
            model.selectedProfileLabel,
            model.lightPalette.summary
        ).filter { value -> value.isNotBlank() }.joinToString(separator = "\n")
        filterPaletteHint.text = model.lightPalette.supportingText
        filterPaletteSurface.isVisible = model.mode == FilterAdjustmentMode.LIGHT
        filterPaletteHint.isVisible = model.mode == FilterAdjustmentMode.LIGHT
        filterAdvancedTitle.isVisible = showAdvancedControls
        filterAdvancedControls.isVisible = showAdvancedControls && model.mode == FilterAdjustmentMode.ADVANCED
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

    private fun renderQuickBubble(settingsPage: SessionSettingsPageRenderModel, text: AppTextResolver = AppTextResolver(this)) {
        val state = latestSessionState ?: return
        val sheet = quickPanelSheetRenderModel(state, text, sessionUiStrings())

        buttonQuickGrid.text = "${sheet.gridRow.title} ${sheet.gridRow.value}"
        buttonQuickGrid.isEnabled = sheet.gridRow.isEnabled

        buttonQuickFlash.text = "${sheet.qualityRow.title} ${sheet.qualityRow.value}"
        buttonQuickFlash.isEnabled = sheet.qualityRow.isEnabled

        buttonFrameRatio43.isEnabled = sheet.frameRatioEnabled
        buttonFrameRatio169.isEnabled = sheet.frameRatioEnabled
        buttonFrameRatio11.isEnabled = sheet.frameRatioEnabled
        sheet.frameRatioOptions.forEach { option ->
            val button = when (option.ratio) {
                FrameRatio.RATIO_4_3 -> buttonFrameRatio43
                FrameRatio.RATIO_16_9 -> buttonFrameRatio169
                FrameRatio.RATIO_1_1 -> buttonFrameRatio11
            }
            if (option.isSelected) {
                button.alpha = 1f
                button.setBackgroundResource(R.drawable.bg_quick_chip_selected)
            } else {
                button.alpha = 0.6f
                button.background = null
            }
        }

        buttonQuickLivePhoto.text = "${sheet.liveRow.title} ${sheet.liveRow.value}"
        buttonQuickLivePhoto.isEnabled = sheet.liveRow.isEnabled

        buttonQuickTimer.text = "${sheet.timerRow.title} ${sheet.timerRow.value}"
        buttonQuickTimer.isEnabled = sheet.timerRow.isEnabled
    }

    private var lastRenderedPanelRoute: CockpitPanelRoute = CockpitPanelRoute.None

    private fun renderPanelVisibility() {
        val route = activePanelRoute
        val routeChanged = route != lastRenderedPanelRoute
        lastRenderedPanelRoute = route
        settingsPanel.isVisible = route.isSettingsOpen
        filterPanel.isVisible = route is CockpitPanelRoute.StyleLab || route is CockpitPanelRoute.ColorLab
        panelDismissScrim.isVisible = route.isAnyPanelOpen
        if (routeChanged) {
            if (settingsPanel.isVisible) settingsPanel.scrollTo(0, 0)
            if (filterPanel.isVisible) filterPanel.scrollTo(0, 0)
        }

        val subpage = (route as? CockpitPanelRoute.Settings)?.subpage
        settingsRootContent.isVisible = route.isSettingsOpen && (subpage == null || subpage == SettingsSubpage.ROOT)
        settingsPortraitLabContent.isVisible = subpage == SettingsSubpage.PORTRAIT_LAB
        settingsWatermarkSelectorContent.isVisible = subpage == SettingsSubpage.WATERMARK_SELECTOR
        settingsWatermarkDetailContent.isVisible = subpage == SettingsSubpage.WATERMARK_DETAIL
        buttonSettingsBack.isVisible = route.isSettingsOpen && subpage != null && subpage != SettingsSubpage.ROOT

        buttonColorLabEntry.alpha = if (route is CockpitPanelRoute.ColorLab) 1f else 0.92f
        buttonSettingsEntry.alpha = if (route.isSettingsOpen) 1f else 0.92f
        buttonFilterEntry.alpha = if (route is CockpitPanelRoute.StyleLab) 1f else 0.92f
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
        val wasVisible = devConsolePanel.isVisible
        devConsolePanel.isVisible = isDevVisible
        buttonDevEntry.alpha = if (isDevVisible) 1f else 0.78f
        if (isDevVisible) {
            if (!wasVisible) {
                (devConsolePanel.getChildAt(0) as? androidx.core.widget.NestedScrollView)?.scrollTo(0, 0)
            }
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
                    button.setTextColor(ContextCompat.getColor(this, R.color.oc_accent))
                    button.setTypeface(null, android.graphics.Typeface.BOLD)
                    button.setBackgroundResource(R.drawable.bg_mode_track_active_chip)
                    button.alpha = 1f
                } else {
                    button.setTextColor(ContextCompat.getColor(this, R.color.oc_text_primary))
                    button.setTypeface(null, android.graphics.Typeface.NORMAL)
                    button.background = null
                    button.alpha = if (item.isAvailable) 0.78f else 0.42f
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
                val text = AppTextResolver(this)
                permissionStatus.text = text.permissionPermanentlyDenied()
                permissionStatus.visibility = View.VISIBLE
                permissionStatus.setOnClickListener {
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
            CockpitPanelRoute.StyleLab,
            CockpitPanelRoute.ColorLab,
            CockpitPanelRoute.DevConsole,
            CockpitPanelRoute.QuickBubble -> {
                if (activePanelRoute is CockpitPanelRoute.StyleLab || activePanelRoute is CockpitPanelRoute.ColorLab) {
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
                Toast.makeText(this@MainActivity, AppTextResolver(this@MainActivity).settingsBlockedByCapture(), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applySettingsControlAction(control: SettingsControlRenderModel?) {
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
            panelRole = if (activePanelRoute is CockpitPanelRoute.ColorLab) {
                StyleAndColorLabRole.COLOR_LAB
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
        if (activePanelRoute is CockpitPanelRoute.ColorLab) {
            val persisted = latestSessionState?.settings?.persisted ?: return
            filterPaletteSurface.updateReticle(colorAxis, toneAxis)
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
