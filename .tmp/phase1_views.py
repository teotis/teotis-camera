#!/usr/bin/env python3
"""Phase 1: Replace all view field references with MainActivityViews accessors."""

import re

# Complete mapping: old_field_name -> new_expression
MAPPING = {
    # Preview
    "previewView": "views.preview.previewView",
    "previewOverlayView": "views.preview.overlayView",
    "previewThumbnail": "views.preview.thumbnail",
    "captureOutput": "views.preview.captureOutput",
    # TopBar
    "titleText": "views.topBar.titleText",
    "permissionStatus": "views.topBar.permissionStatus",
    "buttonColorLabEntry": "views.topBar.colorLabEntry",
    "buttonSettingsEntry": "views.topBar.settingsEntry",
    "buttonFilterEntry": "views.topBar.filterEntry",
    # QuickPanel
    "buttonQuickGrid": "views.quickPanel.grid",
    "buttonQuickFlash": "views.quickPanel.flash",
    "buttonFrameRatio43": "views.quickPanel.frame43",
    "buttonFrameRatio169": "views.quickPanel.frame169",
    "buttonFrameRatio11": "views.quickPanel.frame11",
    "buttonQuickLivePhoto": "views.quickPanel.livePhoto",
    "buttonQuickTimer": "views.quickPanel.timer",
    "buttonQuickLauncher": "views.quickPanel.launcher",
    "quickBubblePanel": "views.quickPanel.panel",
    # Settings Panel
    "settingsPanel": "views.settingsPanel.panel",
    "filterPanel": "views.filterLab.panel",
    "buttonSettingsBack": "views.settingsPanel.back",
    "settingsRootContent": "views.settingsPanel.rootContent",
    "settingsPortraitLabContent": "views.settingsPanel.portraitLabContent",
    "settingsWatermarkSelectorContent": "views.settingsPanel.watermarkSelectorContent",
    "settingsWatermarkDetailContent": "views.settingsPanel.watermarkDetailContent",
    "settingsHeadline": "views.settingsPanel.headline",
    "settingsSupportingText": "views.settingsPanel.supportingText",
    "settingsHeroSummary": "views.settingsPanel.heroSummary",
    "settingsCommonSummary": "views.settingsPanel.commonSummary",
    "settingsPhotoSummary": "views.settingsPanel.photoSummary",
    "settingsVideoSummary": "views.settingsPanel.videoSummary",
    "settingsCatalogFooter": "views.settingsPanel.catalogFooter",
    "settingsEditingHint": "views.settingsPanel.editingHint",
    "portraitLabHeadline": "views.settingsPanel.portraitHeadline",
    "portraitLabSupportingText": "views.settingsPanel.portraitSupportingText",
    "portraitLabHeroSummary": "views.settingsPanel.portraitHeroSummary",
    "portraitLabEditingHint": "views.settingsPanel.portraitEditingHint",
    "buttonPortraitProfile": "views.settingsPanel.portraitProfile",
    "buttonPortraitBeautyPreset": "views.settingsPanel.portraitBeautyPreset",
    "buttonPortraitBeautyStrength": "views.settingsPanel.portraitBeautyStrength",
    "buttonPortraitBokehEffect": "views.settingsPanel.portraitBokehEffect",
    "portraitLabFooter": "views.settingsPanel.portraitFooter",
    "watermarkSelectorHeadline": "views.settingsPanel.watermarkSelectorHeadline",
    "watermarkSelectorSupportingText": "views.settingsPanel.watermarkSelectorSupportingText",
    "watermarkSelectorHeroSummary": "views.settingsPanel.watermarkSelectorHeroSummary",
    "watermarkSelectorList": "views.settingsPanel.watermarkSelectorList",
    "watermarkSelectorEditingHint": "views.settingsPanel.watermarkSelectorEditingHint",
    "watermarkSelectorFooter": "views.settingsPanel.watermarkSelectorFooter",
    "watermarkDetailHeadline": "views.settingsPanel.watermarkDetailHeadline",
    "watermarkDetailSupportingText": "views.settingsPanel.watermarkDetailSupportingText",
    "watermarkDetailHeroSummary": "views.settingsPanel.watermarkDetailHeroSummary",
    "watermarkDetailEditingHint": "views.settingsPanel.watermarkDetailEditingHint",
    "buttonWatermarkPlacement": "views.settingsPanel.watermarkPlacement",
    "buttonWatermarkTextScale": "views.settingsPanel.watermarkTextScale",
    "buttonWatermarkTextOpacity": "views.settingsPanel.watermarkTextOpacity",
    "buttonWatermarkFrameBackground": "views.settingsPanel.watermarkFrameBackground",
    "watermarkDetailFooter": "views.settingsPanel.watermarkDetailFooter",
    "buttonGridMode": "views.settingsPanel.gridMode",
    "buttonShutterSound": "views.settingsPanel.shutterSound",
    "buttonSelfieMirror": "views.settingsPanel.selfieMirror",
    "buttonPhotoFilter": "views.settingsPanel.photoFilter",
    "buttonPhotoPortraitLab": "views.settingsPanel.photoPortraitLab",
    "buttonPhotoWatermark": "views.settingsPanel.photoWatermark",
    "buttonPhotoLive": "views.settingsPanel.photoLive",
    "buttonPhotoTimer": "views.settingsPanel.photoTimer",
    "buttonVideoResolution": "views.settingsPanel.videoResolution",
    "buttonVideoFrameRate": "views.settingsPanel.videoFrameRate",
    "buttonVideoDynamicFps": "views.settingsPanel.videoDynamicFps",
    "buttonVideoAudio": "views.settingsPanel.videoAudio",
    "buttonVideoFilter": "views.settingsPanel.videoFilter",
    "buttonCloseSettings": "views.settingsPanel.close",
    "buttonSettingsTabCommon": "views.settingsPanel.tabCommon",
    "buttonSettingsTabPhoto": "views.settingsPanel.tabPhoto",
    "buttonSettingsTabVideo": "views.settingsPanel.tabVideo",
    "settingsCommonSection": "views.settingsPanel.commonSection",
    "settingsPhotoSection": "views.settingsPanel.photoSection",
    "settingsVideoSection": "views.settingsPanel.videoSection",
    # FilterLab
    "filterHeadline": "views.filterLab.headline",
    "filterSupportingText": "views.filterLab.supportingText",
    "filterHeroSummary": "views.filterLab.heroSummary",
    "filterCurrentSummary": "views.filterLab.currentSummary",
    "filterSelectionList": "views.filterLab.selectionList",
    "filterEditingHint": "views.filterLab.editingHint",
    "filterFooter": "views.filterLab.footer",
    "buttonFilterPhotoTab": "views.filterLab.photoTab",
    "buttonFilterHumanisticTab": "views.filterLab.humanisticTab",
    "buttonFilterPortraitTab": "views.filterLab.portraitTab",
    "buttonFilterVideoTab": "views.filterLab.videoTab",
    "buttonFilterSaveCustom": "views.filterLab.saveCustom",
    "filterAdjustmentPanel": "views.filterLab.adjustmentPanel",
    "buttonFilterModeToggle": "views.filterLab.modeToggle",
    "filterPaletteSummary": "views.filterLab.paletteSummary",
    "filterPaletteHint": "views.filterLab.paletteHint",
    "filterPaletteSurface": "views.filterLab.paletteSurface",
    "filterAdvancedTitle": "views.filterLab.advancedTitle",
    "filterAdvancedControls": "views.filterLab.advancedControls",
    "buttonAdvancedExposure": "views.filterLab.advancedExposure",
    "buttonAdvancedSoftGlow": "views.filterLab.advancedSoftGlow",
    "buttonAdvancedHalo": "views.filterLab.advancedHalo",
    "buttonAdvancedGrain": "views.filterLab.advancedGrain",
    "buttonAdvancedSharpness": "views.filterLab.advancedSharpness",
    "buttonAdvancedVignette": "views.filterLab.advancedVignette",
    "buttonAdvancedHighlights": "views.filterLab.advancedHighlights",
    "buttonAdvancedShadows": "views.filterLab.advancedShadows",
    "buttonAdvancedWarmBoost": "views.filterLab.advancedWarmBoost",
    "buttonAdvancedCoolBoost": "views.filterLab.advancedCoolBoost",
    "buttonAdvancedTemperatureShift": "views.filterLab.advancedTemperatureShift",
    "buttonAdvancedTintShift": "views.filterLab.advancedTintShift",
    "buttonCloseFilter": "views.filterLab.close",
    # DevConsole
    "buttonDevEntry": "views.devConsole.entry",
    "devConsolePanel": "views.devConsole.panel",
    "buttonDevTabKey": "views.devConsole.tabKey",
    "buttonDevTabCore": "views.devConsole.tabCore",
    "buttonDevTabError": "views.devConsole.tabError",
    "buttonDevTabAll": "views.devConsole.tabAll",
    "devConsoleTitle": "views.devConsole.title",
    "devConsoleSummary": "views.devConsole.summary",
    "devConsoleContent": "views.devConsole.content",
    "buttonDevExport": "views.devConsole.export",
    "buttonDevClose": "views.devConsole.close",
    # Bottom Cockpit
    "shutterButton": "views.bottomCockpit.shutter",
    "lensFacingButton": "views.bottomCockpit.lensFacing",
    "zoomCapsuleScroll": "views.bottomCockpit.zoomScroll",
    "zoomCapsuleRow": "views.bottomCockpit.zoomRow",
    # Mode Track
    "photoModeButton": "views.modeTrack.photo",
    "documentModeButton": "views.modeTrack.document",
    "nightModeButton": "views.modeTrack.night",
    "humanisticModeButton": "views.modeTrack.humanistic",
    "portraitModeButton": "views.modeTrack.portrait",
    "proModeButton": "views.modeTrack.pro",
    "videoModeButton": "views.modeTrack.video",
    "modeTrackScroll": "views.modeTrack.scroll",
    # PanelDismissScrim
    "panelDismissScrim": "views.panelDismissScrim",
}

# Fields that should also match when accessed as `this.fieldName` (Kotlin implicit)
# Sort by length descending so longer names match first
SORTED_KEYS = sorted(MAPPING.keys(), key=len, reverse=True)


def replace_word(line, old, new):
    """Replace old variable name with new expression, handling word boundaries."""
    # Use regex with negative lookbehind/lookahead for word boundaries
    pattern = r'(?<![a-zA-Z.])' + re.escape(old) + r'(?![a-zA-Z])'
    return re.sub(pattern, new, line)


def transform_file(path):
    with open(path, 'r') as f:
        content = f.read()

    lines = content.split('\n')
    new_lines = []

    # Find the range to remove: from 'private lateinit var previewView' to the blank line
    # after the view declarations (before 'private val shutterClickSound')
    # lines 0-indexed: view fields start around line 61, end around line 193
    in_view_fields = False
    removed_blank = False

    for i, line in enumerate(lines):
        # Mark start of view field section
        if line.strip() == 'private lateinit var previewView: PreviewView':
            in_view_fields = True
            continue

        if in_view_fields:
            # End of view field section is before:
            #   private val shutterClickSound = MediaActionSound()
            if 'private val shutterClickSound' in line:
                in_view_fields = False
                # Don't skip the blank line before shutterClickSound if there is one
                # Actually we need to add views field before shutterClickSound
                new_lines.append('    private lateinit var views: MainActivityViews')
                new_lines.append(line)
                continue
            # Skip all view declaration lines
            continue

        new_lines.append(line)

    content = '\n'.join(new_lines)

    # Step 2: Apply all field name replacements
    for old in SORTED_KEYS:
        new = MAPPING[old]
        content = replace_word(content, old, new)

    # Step 3: Remove findViewById calls in onCreate (they're in MainActivityViews.bind())
    # Match patterns like:  xxx = findViewById(R.id.yyy)
    content = re.sub(r'^\s+\w+ = findViewById\(R\.id\.\w+\)\s*$', '', content, flags=re.MULTILINE)

    # Step 4: Remove the empty lines from removed findViewById calls (collapse multiple blanks)
    content = re.sub(r'\n{3,}', '\n\n', content)

    # Step 5: Add views initialization after setContentView
    content = content.replace(
        'setContentView(R.layout.activity_main)\n',
        'setContentView(R.layout.activity_main)\n        views = MainActivityViews.bind(this)\n'
    )

    with open(path, 'w') as f:
        f.write(content)

    print(f'Transformed {path}')


if __name__ == '__main__':
    transform_file(
        '/Volumes/Extreme_SSD/project/codex_camera/app/src/main/java/com/opencamera/app/MainActivity.kt'
    )
