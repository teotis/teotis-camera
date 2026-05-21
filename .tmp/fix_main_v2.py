import re

file_path = "app/src/main/java/com/opencamera/app/MainActivity.kt"

with open(file_path, 'r') as f:
    content = f.read()

# 1. Fix imports
old_import = "import androidx.activity.ComponentActivity\nimport androidx.activity.result.contract.ActivityResultContracts"
new_import = """import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.opencamera.app.i18n.AppTextResolver"""
content = content.replace(old_import, new_import)

# 2. Fix class declaration
content = content.replace("class MainActivity : ComponentActivity()", "class MainActivity : AppCompatActivity()")

# 3. Add applyLocale before bindState
old_bind = "    private fun bindState() {"
new_bind = """    private fun applyLocale(settings: com.opencamera.core.settings.PersistedSettings) {
        val language = settings.common.appLanguage
        val localeList = LocaleListCompat.forLanguageTags(language.storageKey)
        if (AppCompatDelegate.getApplicationLocales() != localeList) {
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }

    private fun bindState() {"""
content = content.replace(old_bind, new_bind)

# 4. Fix render() function
old_render = """    private fun render(state: SessionState) {
        latestSessionState = state
        val controls = sessionControlsRenderModel(state, sessionUiStrings())
        val settingsPage = sessionSettingsPageRenderModel(state)
        val portraitLabPage = portraitLabPageRenderModel(state)
        val watermarkSelectorPage = watermarkLabSelectorRenderModel(state)
        val watermarkDetailPage = watermarkLabDetailRenderModel(
            state = state,
            templateId = selectedWatermarkDetailTemplateId
                ?: state.settings.persisted.photo.defaultWatermarkTemplateId
        )
        val filterLabPage = filterLabPageRenderModel(
            state = state,
            selectedFamily = selectedFilterLabFamily(state),
            showAdjustmentPanel = isFilterAdjustmentVisible,
            adjustmentMode = filterAdjustmentMode
        )
        val modeTrack = modeTrackRenderModel(state)
        val primaryStatus = primaryStatusRenderModel(state)"""

new_render = """    private fun render(state: SessionState) {
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
        val primaryStatus = primaryStatusRenderModel(state, text)"""
content = content.replace(old_render, new_render)

# 5. Fix renderLatestFilterLab
old_rlf = """    private fun renderLatestFilterLab() {
        val state = latestSessionState ?: return
        val model = filterLabPageRenderModel(
            state = state,
            selectedFamily = selectedFilterLabFamily(state),
            showAdjustmentPanel = isFilterAdjustmentVisible,
            adjustmentMode = filterAdjustmentMode
        )"""

new_rlf = """    private fun renderLatestFilterLab() {
        val state = latestSessionState ?: return
        val text = AppTextResolver(this)
        val model = filterLabPageRenderModel(
            state = state,
            text = text,
            selectedFamily = selectedFilterLabFamily(state),
            showAdjustmentPanel = isFilterAdjustmentVisible,
            adjustmentMode = filterAdjustmentMode
        )"""
content = content.replace(old_rlf, new_rlf)

# 6. Fix renderLatestSettingsSurfaces
old_rls = """    private fun renderLatestSettingsSurfaces() {
        val state = latestSessionState ?: return
        val settingsModel = sessionSettingsPageRenderModel(state)
        val portraitLabModel = portraitLabPageRenderModel(state)
        val selectorModel = watermarkLabSelectorRenderModel(state)
        val detailModel = watermarkLabDetailRenderModel(
            state = state,
            templateId = selectedWatermarkDetailTemplateId
                ?: state.settings.persisted.photo.defaultWatermarkTemplateId
        )"""

new_rls = """    private fun renderLatestSettingsSurfaces() {
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
content = content.replace(old_rls, new_rls)

with open(file_path, 'w') as f:
    f.write(content)

print("Fixed all MainActivity.kt changes")
