#!/usr/bin/env python3
"""Phase 2: Replace render method calls with renderer delegations in MainActivity.kt."""

import re

PATH = '/Volumes/Extreme_SSD/project/codex_camera/app/src/main/java/com/opencamera/app/MainActivity.kt'

with open(PATH, 'r') as f:
    content = f.read()

# Replace render(state) method body with renderer delegation
# Find the render(state: SessionState) method and update its body

# Key replacements (order matters - longer names first):
replacements = [
    # render(state) sub-calls -> renderer delegation
    ('views.topBar.titleText.text = getString(R.string.app_name)', 'cockpitRenderer.renderTopTitle()'),
    ('renderModeTrack(modeTrack)', 'cockpitRenderer.renderModeTrack(modeTrack)'),
    ('renderSettingsPage(settingsPage)', 'settingsRenderer.renderPage(settingsPage)'),
    ('renderPortraitLabPage(portraitLabPage)', 'settingsRenderer.renderPortraitLabPage(portraitLabPage)'),
    ('renderWatermarkLabSelectorPage(watermarkSelectorPage)', 'settingsRenderer.renderWatermarkSelectorPage(watermarkSelectorPage)'),
    ('renderWatermarkLabDetailPage(watermarkDetailPage)', 'settingsRenderer.renderWatermarkDetailPage(watermarkDetailPage)'),
    ('renderFilterLabPage(filterLabPage)', 'filterLabRenderer.renderPage(filterLabPage)'),
    ('renderZoomCapsules(controls)', 'cockpitRenderer.renderZoomCapsules(controls)'),
    ('renderQuickBubble(settingsPage)', 'cockpitRenderer.renderQuickBubble(settingsPage, sheet)'),
    ('renderDevConsoleVisibility()', 'devConsoleRenderer.renderVisibility(activePanelRoute)'),
    ('renderDevConsole()', 'devConsoleRenderer.render(latestDevLogRenderModel)'),
    ('renderPanelVisibility()', 'mainRenderer.renderPanelVisibility(activePanelRoute)'),
    ('renderSettingsTabs()', 'settingsRenderer.renderTabs(panelState.selectedSettingsTab)'),

    # render(state) body - selfie mirror replacement
    ('views.preview.previewView.scaleX = if (', 'cockpitRenderer.renderPreviewMirror(state)\n        // scaleX handled by cockpitRenderer'),

    # renderLatestFilterLab calls
    ('renderLatestFilterLab()', '_renderLatestFilterLab()'),

    # renderLatestSettingsSurfaces calls
    ('renderLatestSettingsSurfaces()', '_renderLatestSettingsSurfaces()'),
]

for old, new in replacements:
    content = content.replace(old, new)

# Fix the scaleX block removal - actually need to handle it specially
# Remove the scaleX block that was replaced badly
content = re.sub(
    r'cockpitRenderer\.renderPreviewMirror\(state\)\n\s+// scaleX handled by cockpitRenderer\n\s+state\.activeDeviceGraph\.preferredLensFacing == LensFacing\.FRONT &&\n\s+state\.settings\.persisted\.common\.selfieMirrorEnabled\n\s+\) \{\n\s+-1f\n\s+\} else \{\n\s+1f\n\s+\}',
    'cockpitRenderer.renderPreviewMirror(state)',
    content
)

# Replace remaining renderDevConsole at dev console entry visibility
# (the one in the render(state) that's about dev entry visibility)
# Already handled above

# Fix shutter state application - check if it exists in render(state)
shutter_old = '''        val isRecording = state.recordingStatus != RecordingStatus.IDLE
        if (isRecording) {
            views.bottomCockpit.shutter.text = ""
            views.bottomCockpit.shutter.setBackgroundResource(R.drawable.bg_shutter_recording_selector)
        } else {
            views.bottomCockpit.shutter.text = ""
            views.bottomCockpit.shutter.setBackgroundResource(R.drawable.bg_shutter_selector)
        }
        views.bottomCockpit.shutter.isEnabled = state.modeSnapshot.state.isShutterEnabled
        views.bottomCockpit.lensFacing.text = controls.lensFacingButtonLabel
        views.bottomCockpit.lensFacing.isEnabled = controls.lensFacingEnabled'''
if shutter_old in content:
    content = content.replace(shutter_old, '        cockpitRenderer.renderShutter(state, controls)')

# Replace dev console entry visibility in render(state)
dev_entry_old = "        if (BuildConfig.DEBUG) views.devConsole.entry.isVisible = true"
if dev_entry_old in content:
    content = content.replace(dev_entry_old, '        mainRenderer.renderDevEntryVisibility(BuildConfig.DEBUG)')

# Replace captureOutput text assignment
capture_old = "        views.preview.captureOutput.text = sessionCaptureOutputText(state, text.sessionUiStrings())"
if capture_old in content:
    content = content.replace(capture_old, "        cockpitRenderer.renderCaptureOutput(sessionCaptureOutputText(state, text.sessionUiStrings()))")

# Fix the proxy method names back to original for definitions
# _renderLatestFilterLab -> renderLatestFilterLab (in method definitions only)
content = content.replace('private fun _renderLatestFilterLab', 'private fun renderLatestFilterLab')
content = content.replace('private fun _renderLatestSettingsSurfaces', 'private fun renderLatestSettingsSurfaces')
# Fix call sites too
content = content.replace('_renderLatestFilterLab()', 'renderLatestFilterLab()')
content = content.replace('_renderLatestSettingsSurfaces()', 'renderLatestSettingsSurfaces()')

with open(PATH, 'w') as f:
    f.write(content)

print(f'Phase 2 render refactor applied to {PATH}')
