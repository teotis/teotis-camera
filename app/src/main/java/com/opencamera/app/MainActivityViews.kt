package com.opencamera.app

import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.widget.NestedScrollView
import com.google.android.material.card.MaterialCardView

internal data class PreviewViews(
    val previewView: PreviewView,
    val overlayView: PreviewOverlayView,
    val thumbnail: ImageView,
    val captureOutput: TextView
)

internal data class TopBarViews(
    val titleText: TextView,
    val permissionStatus: TextView,
    val colorLabEntry: Button,
    val settingsEntry: Button,
    val filterEntry: Button
)

internal data class QuickPanelViews(
    val panel: NestedScrollView,
    val grid: Button,
    val flash: Button,
    val brightnessMinus: Button,
    val brightnessValue: Button,
    val brightnessPlus: Button,
    val frame43: Button,
    val frame169: Button,
    val frame11: Button,
    val livePhoto: Button,
    val timer: Button,
    val launcher: Button
)

internal data class SettingsPanelViews(
    val panel: NestedScrollView,
    val close: Button,
    val back: Button,
    val rootContent: LinearLayout,
    val portraitLabContent: LinearLayout,
    val watermarkSelectorContent: LinearLayout,
    val watermarkDetailContent: LinearLayout,
    val headline: TextView,
    val supportingText: TextView,
    val heroSummary: TextView,
    val commonSummary: TextView,
    val photoSummary: TextView,
    val videoSummary: TextView,
    val catalogFooter: TextView,
    val editingHint: TextView,
    val tabCommon: Button,
    val tabPhoto: Button,
    val tabVideo: Button,
    val commonSection: LinearLayout,
    val photoSection: LinearLayout,
    val videoSection: LinearLayout,
    val gridMode: Button,
    val shutterSound: Button,
    val selfieMirror: Button,
    val photoFilter: Button,
    val photoPortraitLab: Button,
    val photoWatermark: Button,
    val photoLive: Button,
    val photoTimer: Button,
    val videoResolution: Button,
    val videoFrameRate: Button,
    val videoDynamicFps: Button,
    val videoAudio: Button,
    val videoFilter: Button,
    val portraitHeadline: TextView,
    val portraitSupportingText: TextView,
    val portraitHeroSummary: TextView,
    val portraitEditingHint: TextView,
    val portraitProfile: Button,
    val portraitBeautyPreset: Button,
    val portraitBeautyStrength: Button,
    val portraitBokehEffect: Button,
    val portraitFooter: TextView,
    val watermarkSelectorHeadline: TextView,
    val watermarkSelectorSupportingText: TextView,
    val watermarkSelectorHeroSummary: TextView,
    val watermarkSelectorList: LinearLayout,
    val watermarkSelectorEditingHint: TextView,
    val watermarkSelectorFooter: TextView,
    val watermarkDetailHeadline: TextView,
    val watermarkDetailSupportingText: TextView,
    val watermarkDetailHeroSummary: TextView,
    val watermarkDetailEditingHint: TextView,
    val watermarkPlacement: Button,
    val watermarkTextScale: Button,
    val watermarkTextOpacity: Button,
    val watermarkFrameBackground: Button,
    val watermarkDetailFooter: TextView
)

internal data class FilterLabViews(
    val panel: NestedScrollView,
    val close: Button,
    val headline: TextView,
    val supportingText: TextView,
    val heroSummary: TextView,
    val currentSummary: TextView,
    val sectionFiltersTitle: TextView,
    val selectionCard: LinearLayout,
    val selectionList: LinearLayout,
    val editingHint: TextView,
    val footer: TextView,
    val photoTab: Button,
    val humanisticTab: Button,
    val portraitTab: Button,
    val videoTab: Button,
    val saveCustom: Button,
    val sectionPaletteTitle: TextView,
    val adjustmentPanel: LinearLayout,
    val modeToggle: Button,
    val paletteSummary: TextView,
    val paletteHint: TextView,
    val paletteSurface: FilterPaletteView,
    val advancedTitle: TextView,
    val advancedControls: LinearLayout,
    val advancedExposure: Button,
    val advancedSoftGlow: Button,
    val advancedHalo: Button,
    val advancedGrain: Button,
    val advancedSharpness: Button,
    val advancedVignette: Button,
    val advancedHighlights: Button,
    val advancedShadows: Button,
    val advancedWarmBoost: Button,
    val advancedCoolBoost: Button,
    val advancedTemperatureShift: Button,
    val advancedTintShift: Button
)

internal data class DevConsoleViews(
    val entry: Button,
    val panel: MaterialCardView,
    val tabKey: Button,
    val tabCore: Button,
    val tabError: Button,
    val tabAll: Button,
    val title: TextView,
    val summary: TextView,
    val content: TextView,
    val export: Button,
    val close: Button
)

internal data class ModeTrackViews(
    val scroll: android.widget.HorizontalScrollView,
    val photo: Button,
    val night: Button,
    val portrait: Button,
    val pro: Button,
    val video: Button,
    val document: Button,
    val humanistic: Button
)

internal data class FloatingUtilityViews(
    val quickLauncher: Button,
    val lowLightNightPrompt: Button
)

internal data class BottomCockpitViews(
    val shutter: Button,
    val lensFacing: Button,
    val zoomScroll: android.widget.HorizontalScrollView,
    val zoomRow: LinearLayout,
    val recordingIndicator: TextView
)

internal data class MainActivityViews(
    val preview: PreviewViews,
    val topBar: TopBarViews,
    val quickPanel: QuickPanelViews,
    val floatingUtility: FloatingUtilityViews,
    val settingsPanel: SettingsPanelViews,
    val filterLab: FilterLabViews,
    val devConsole: DevConsoleViews,
    val modeTrack: ModeTrackViews,
    val bottomCockpit: BottomCockpitViews,
    val panelDismissScrim: View
) {
    companion object {
        fun bind(activity: AppCompatActivity): MainActivityViews {
            val preview = PreviewViews(
                previewView = activity.findViewById(R.id.cameraPreview),
                overlayView = activity.findViewById(R.id.previewOverlay),
                thumbnail = activity.findViewById(R.id.previewThumbnail),
                captureOutput = activity.findViewById(R.id.captureOutput)
            )
            val topBar = TopBarViews(
                titleText = activity.findViewById(R.id.titleText),
                permissionStatus = activity.findViewById(R.id.permissionStatus),
                colorLabEntry = activity.findViewById(R.id.buttonColorLabEntry),
                settingsEntry = activity.findViewById(R.id.buttonSettingsEntry),
                filterEntry = activity.findViewById(R.id.buttonFilterEntry)
            )
            val quickPanel = QuickPanelViews(
                panel = activity.findViewById(R.id.quickBubblePanel),
                grid = activity.findViewById(R.id.buttonQuickGrid),
                flash = activity.findViewById(R.id.buttonQuickFlash),
                brightnessMinus = activity.findViewById(R.id.buttonBrightnessMinus),
                brightnessValue = activity.findViewById(R.id.buttonBrightnessValue),
                brightnessPlus = activity.findViewById(R.id.buttonBrightnessPlus),
                frame43 = activity.findViewById(R.id.buttonFrameRatio43),
                frame169 = activity.findViewById(R.id.buttonFrameRatio169),
                frame11 = activity.findViewById(R.id.buttonFrameRatio11),
                livePhoto = activity.findViewById(R.id.buttonQuickLivePhoto),
                timer = activity.findViewById(R.id.buttonQuickTimer),
                launcher = activity.findViewById(R.id.buttonQuickLauncher)
            )
            val floatingUtility = FloatingUtilityViews(
                quickLauncher = activity.findViewById(R.id.buttonQuickLauncher),
                lowLightNightPrompt = activity.findViewById(R.id.buttonLowLightNightPrompt)
            )
            val settingsPanel = SettingsPanelViews(
                panel = activity.findViewById(R.id.settingsPanel),
                close = activity.findViewById(R.id.buttonCloseSettings),
                back = activity.findViewById(R.id.buttonSettingsBack),
                rootContent = activity.findViewById(R.id.settingsRootContent),
                portraitLabContent = activity.findViewById(R.id.settingsPortraitLabContent),
                watermarkSelectorContent = activity.findViewById(R.id.settingsWatermarkSelectorContent),
                watermarkDetailContent = activity.findViewById(R.id.settingsWatermarkDetailContent),
                headline = activity.findViewById(R.id.settingsHeadline),
                supportingText = activity.findViewById(R.id.settingsSupportingText),
                heroSummary = activity.findViewById(R.id.settingsHeroSummary),
                commonSummary = activity.findViewById(R.id.settingsCommonSummary),
                photoSummary = activity.findViewById(R.id.settingsPhotoSummary),
                videoSummary = activity.findViewById(R.id.settingsVideoSummary),
                catalogFooter = activity.findViewById(R.id.settingsCatalogFooter),
                editingHint = activity.findViewById(R.id.settingsEditingHint),
                tabCommon = activity.findViewById(R.id.buttonSettingsTabCommon),
                tabPhoto = activity.findViewById(R.id.buttonSettingsTabPhoto),
                tabVideo = activity.findViewById(R.id.buttonSettingsTabVideo),
                commonSection = activity.findViewById(R.id.settingsCommonSection),
                photoSection = activity.findViewById(R.id.settingsPhotoSection),
                videoSection = activity.findViewById(R.id.settingsVideoSection),
                gridMode = activity.findViewById(R.id.buttonGridMode),
                shutterSound = activity.findViewById(R.id.buttonShutterSound),
                selfieMirror = activity.findViewById(R.id.buttonSelfieMirror),
                photoFilter = activity.findViewById(R.id.buttonPhotoFilter),
                photoPortraitLab = activity.findViewById(R.id.buttonPhotoPortraitLab),
                photoWatermark = activity.findViewById(R.id.buttonPhotoWatermark),
                photoLive = activity.findViewById(R.id.buttonPhotoLive),
                photoTimer = activity.findViewById(R.id.buttonPhotoTimer),
                videoResolution = activity.findViewById(R.id.buttonVideoResolution),
                videoFrameRate = activity.findViewById(R.id.buttonVideoFrameRate),
                videoDynamicFps = activity.findViewById(R.id.buttonVideoDynamicFps),
                videoAudio = activity.findViewById(R.id.buttonVideoAudio),
                videoFilter = activity.findViewById(R.id.buttonVideoFilter),
                portraitHeadline = activity.findViewById(R.id.portraitLabHeadline),
                portraitSupportingText = activity.findViewById(R.id.portraitLabSupportingText),
                portraitHeroSummary = activity.findViewById(R.id.portraitLabHeroSummary),
                portraitEditingHint = activity.findViewById(R.id.portraitLabEditingHint),
                portraitProfile = activity.findViewById(R.id.buttonPortraitProfile),
                portraitBeautyPreset = activity.findViewById(R.id.buttonPortraitBeautyPreset),
                portraitBeautyStrength = activity.findViewById(R.id.buttonPortraitBeautyStrength),
                portraitBokehEffect = activity.findViewById(R.id.buttonPortraitBokehEffect),
                portraitFooter = activity.findViewById(R.id.portraitLabFooter),
                watermarkSelectorHeadline = activity.findViewById(R.id.watermarkSelectorHeadline),
                watermarkSelectorSupportingText = activity.findViewById(R.id.watermarkSelectorSupportingText),
                watermarkSelectorHeroSummary = activity.findViewById(R.id.watermarkSelectorHeroSummary),
                watermarkSelectorList = activity.findViewById(R.id.watermarkSelectorList),
                watermarkSelectorEditingHint = activity.findViewById(R.id.watermarkSelectorEditingHint),
                watermarkSelectorFooter = activity.findViewById(R.id.watermarkSelectorFooter),
                watermarkDetailHeadline = activity.findViewById(R.id.watermarkDetailHeadline),
                watermarkDetailSupportingText = activity.findViewById(R.id.watermarkDetailSupportingText),
                watermarkDetailHeroSummary = activity.findViewById(R.id.watermarkDetailHeroSummary),
                watermarkDetailEditingHint = activity.findViewById(R.id.watermarkDetailEditingHint),
                watermarkPlacement = activity.findViewById(R.id.buttonWatermarkPlacement),
                watermarkTextScale = activity.findViewById(R.id.buttonWatermarkTextScale),
                watermarkTextOpacity = activity.findViewById(R.id.buttonWatermarkTextOpacity),
                watermarkFrameBackground = activity.findViewById(R.id.buttonWatermarkFrameBackground),
                watermarkDetailFooter = activity.findViewById(R.id.watermarkDetailFooter)
            )
            val filterLab = FilterLabViews(
                panel = activity.findViewById(R.id.filterPanel),
                close = activity.findViewById(R.id.buttonCloseFilter),
                headline = activity.findViewById(R.id.filterHeadline),
                supportingText = activity.findViewById(R.id.filterSupportingText),
                heroSummary = activity.findViewById(R.id.filterHeroSummary),
                currentSummary = activity.findViewById(R.id.filterCurrentSummary),
                sectionFiltersTitle = activity.findViewById(R.id.filterSectionFiltersTitle),
                selectionCard = activity.findViewById(R.id.filterSelectionCard),
                selectionList = activity.findViewById(R.id.filterSelectionList),
                editingHint = activity.findViewById(R.id.filterEditingHint),
                footer = activity.findViewById(R.id.filterFooter),
                photoTab = activity.findViewById(R.id.buttonFilterPhotoTab),
                humanisticTab = activity.findViewById(R.id.buttonFilterHumanisticTab),
                portraitTab = activity.findViewById(R.id.buttonFilterPortraitTab),
                videoTab = activity.findViewById(R.id.buttonFilterVideoTab),
                saveCustom = activity.findViewById(R.id.buttonFilterSaveCustom),
                sectionPaletteTitle = activity.findViewById(R.id.filterSectionPaletteTitle),
                adjustmentPanel = activity.findViewById(R.id.filterAdjustmentPanel),
                modeToggle = activity.findViewById(R.id.buttonFilterModeToggle),
                paletteSummary = activity.findViewById(R.id.filterPaletteSummary),
                paletteHint = activity.findViewById(R.id.filterPaletteHint),
                paletteSurface = activity.findViewById(R.id.filterPaletteSurface),
                advancedTitle = activity.findViewById(R.id.filterAdvancedTitle),
                advancedControls = activity.findViewById(R.id.filterAdvancedControls),
                advancedExposure = activity.findViewById(R.id.buttonAdvancedExposure),
                advancedSoftGlow = activity.findViewById(R.id.buttonAdvancedSoftGlow),
                advancedHalo = activity.findViewById(R.id.buttonAdvancedHalo),
                advancedGrain = activity.findViewById(R.id.buttonAdvancedGrain),
                advancedSharpness = activity.findViewById(R.id.buttonAdvancedSharpness),
                advancedVignette = activity.findViewById(R.id.buttonAdvancedVignette),
                advancedHighlights = activity.findViewById(R.id.buttonAdvancedHighlights),
                advancedShadows = activity.findViewById(R.id.buttonAdvancedShadows),
                advancedWarmBoost = activity.findViewById(R.id.buttonAdvancedWarmBoost),
                advancedCoolBoost = activity.findViewById(R.id.buttonAdvancedCoolBoost),
                advancedTemperatureShift = activity.findViewById(R.id.buttonAdvancedTemperatureShift),
                advancedTintShift = activity.findViewById(R.id.buttonAdvancedTintShift)
            )
            val devConsole = DevConsoleViews(
                entry = activity.findViewById(R.id.buttonDevEntry),
                panel = activity.findViewById(R.id.devConsolePanel),
                tabKey = activity.findViewById(R.id.buttonDevTabKey),
                tabCore = activity.findViewById(R.id.buttonDevTabCore),
                tabError = activity.findViewById(R.id.buttonDevTabError),
                tabAll = activity.findViewById(R.id.buttonDevTabAll),
                title = activity.findViewById(R.id.devConsoleTitle),
                summary = activity.findViewById(R.id.devConsoleSummary),
                content = activity.findViewById(R.id.devConsoleContent),
                export = activity.findViewById(R.id.buttonDevExport),
                close = activity.findViewById(R.id.buttonDevClose)
            )
            val modeTrack = ModeTrackViews(
                scroll = activity.findViewById(R.id.modeTrackScroll),
                photo = activity.findViewById(R.id.buttonPhotoMode),
                night = activity.findViewById(R.id.buttonNightMode),
                portrait = activity.findViewById(R.id.buttonPortraitMode),
                pro = activity.findViewById(R.id.buttonProMode),
                video = activity.findViewById(R.id.buttonVideoMode),
                document = activity.findViewById(R.id.buttonDocumentMode),
                humanistic = activity.findViewById(R.id.buttonHumanisticMode)
            )
            val bottomCockpit = BottomCockpitViews(
                shutter = activity.findViewById(R.id.buttonShutter),
                lensFacing = activity.findViewById(R.id.buttonLensFacing),
                zoomScroll = activity.findViewById(R.id.zoomCapsuleScroll),
                zoomRow = activity.findViewById(R.id.zoomCapsuleRow),
                recordingIndicator = activity.findViewById(R.id.recordingIndicator)
            )
            return MainActivityViews(
                preview = preview,
                topBar = topBar,
                quickPanel = quickPanel,
                floatingUtility = floatingUtility,
                settingsPanel = settingsPanel,
                filterLab = filterLab,
                devConsole = devConsole,
                modeTrack = modeTrack,
                bottomCockpit = bottomCockpit,
                panelDismissScrim = activity.findViewById(R.id.panelDismissScrim)
            )
        }
    }
}
