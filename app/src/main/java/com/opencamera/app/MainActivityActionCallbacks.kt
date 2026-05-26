package com.opencamera.app

import com.opencamera.core.session.SessionIntent
import com.opencamera.core.settings.PersistedSettingsAction

@Suppress("EXPOSED_PARAMETER_TYPE")
internal interface MainActivityActionCallbacks {
    fun dispatch(intent: SessionIntent)
    fun applySettingsAction(action: PersistedSettingsAction)
    fun applySettingsControl(control: SettingsControlRenderModel?)
    fun reducePanel(command: CockpitPanelCommand)
    fun renderAfterPanelChange()
    fun renderLatestSettingsSurfaces()
    fun renderLatestFilterLab()
    fun maybeAutoPrepareFilter()
    fun saveCurrentFilterAsCustom(control: FilterLabSaveCustomRenderModel?)
    fun openSelectedFilterAdjustment(control: FilterLabAdjustRenderModel?)
    fun applyAdvancedFilterControl(control: FilterAdvancedControl)
    fun toggleFilterAdjustmentMode()
    fun handleFilterPaletteTouch(colorAxis: Float, toneAxis: Float)
    fun selectFilterLabFamily(family: FilterLabFamily)
    fun openPortraitLab()
    fun openWatermarkLabSelector()
    fun openWatermarkLabDetail(templateId: String)
    fun requestCameraPermissionIfNeeded()
    fun requestMicrophonePermission()
    fun showDisabledReason(reason: String)
    fun openLatestGalleryMedia()
    fun exportDevLog()
    fun cleanupDevLogByType(type: DevLogTab)
    fun cleanupAllDevLogs()
    fun refreshDevLogModel()
    fun selectDevLogTab(tab: DevLogTab)
    fun neutralColorLabAction(): PersistedSettingsAction
    fun toggleLowLightNightAssist()
    fun onBrightnessDragStart()
    fun onBrightnessDragEnd()
}
