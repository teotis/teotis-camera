import re

file_path = "app/src/main/java/com/opencamera/app/MainActivity.kt"

with open(file_path, 'r') as f:
    content = f.read()

# Fix renderLatestFilterLab function
old_render_latest_filter = """    private fun renderLatestFilterLab() {
        val state = latestSessionState ?: return
        val model = filterLabPageRenderModel(
            state = state,
            selectedFamily = selectedFilterLabFamily(state),
            showAdjustmentPanel = isFilterAdjustmentVisible,
            adjustmentMode = filterAdjustmentMode
        )
        latestFilterLabRenderModel = model
        renderFilterLabPage(model)
    }"""

new_render_latest_filter = """    private fun renderLatestFilterLab() {
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
    }"""

content = content.replace(old_render_latest_filter, new_render_latest_filter)

# Fix renderLatestSettingsSurfaces function
old_render_latest_settings = """    private fun renderLatestSettingsSurfaces() {
        val state = latestSessionState ?: return
        val settingsModel = sessionSettingsPageRenderModel(state)
        val portraitLabModel = portraitLabPageRenderModel(state)
        val selectorModel = watermarkLabSelectorRenderModel(state)
        val detailModel = watermarkLabDetailRenderModel(
            state = state,
            templateId = selectedWatermarkDetailTemplateId
                ?: state.settings.persisted.photo.defaultWatermarkTemplateId
        )"""

new_render_latest_settings = """    private fun renderLatestSettingsSurfaces() {
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
        )"""

content = content.replace(old_render_latest_settings, new_render_latest_settings)

with open(file_path, 'w') as f:
    f.write(content)

print("Fixed renderLatestFilterLab and renderLatestSettingsSurfaces")
