/**
 * SessionUiRenderContracts + SettingsTab 计算属性测试
 *
 * 覆盖行为:
 * - SettingsControlRenderModel.isInteractive 四种条件组合
 * - SettingsControlRenderModel.buttonLabel 格式（含/不含 supportLabel）
 * - FeatureCatalogControlRenderModel.isInteractive 两种条件
 * - FeatureCatalogControlRenderModel.buttonLabel 格式
 * - SettingsControlAvailability 枚举值
 * - SettingsTab 枚举值
 *
 * 不适合单测的行为:
 * - 无（所有 computed properties 均为纯函数逻辑）
 */
package com.opencamera.app

import com.opencamera.core.settings.FeatureCatalogAction
import com.opencamera.core.settings.PersistedSettingsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionUiRenderContractsTest {

    // region SettingsControlRenderModel.isInteractive

    @Test
    fun settingsControl_isInteractive_true_when_enabled_supported_and_has_action() {
        val model = SettingsControlRenderModel(
            label = "Label",
            value = "Val",
            availability = SettingsControlAvailability.SUPPORTED,
            nextAction = PersistedSettingsAction.UpdateShutterSoundEnabled(true),
            enabled = true
        )
        assertTrue(model.isInteractive)
    }

    @Test
    fun settingsControl_isInteractive_false_when_disabled() {
        val model = SettingsControlRenderModel(
            label = "Label",
            value = "Val",
            availability = SettingsControlAvailability.SUPPORTED,
            nextAction = PersistedSettingsAction.UpdateShutterSoundEnabled(true),
            enabled = false
        )
        assertFalse(model.isInteractive)
    }

    @Test
    fun settingsControl_isInteractive_false_when_unsupported() {
        val model = SettingsControlRenderModel(
            label = "Label",
            value = "Val",
            availability = SettingsControlAvailability.UNSUPPORTED,
            nextAction = PersistedSettingsAction.UpdateShutterSoundEnabled(true),
            enabled = true
        )
        assertFalse(model.isInteractive)
    }

    @Test
    fun settingsControl_isInteractive_false_when_no_action() {
        val model = SettingsControlRenderModel(
            label = "Label",
            value = "Val",
            availability = SettingsControlAvailability.SUPPORTED,
            nextAction = null,
            enabled = true
        )
        assertFalse(model.isInteractive)
    }

    @Test
    fun settingsControl_isInteractive_false_when_degraded_and_no_action() {
        val model = SettingsControlRenderModel(
            label = "Label",
            value = "Val",
            availability = SettingsControlAvailability.DEGRADED,
            nextAction = null,
            enabled = true
        )
        assertFalse(model.isInteractive)
    }

    // endregion

    // region SettingsControlRenderModel.buttonLabel

    @Test
    fun settingsControl_buttonLabel_contains_label_value_availability() {
        val model = SettingsControlRenderModel(
            label = "Shutter Sound",
            value = "On",
            availability = SettingsControlAvailability.SUPPORTED
        )
        val label = model.buttonLabel
        assertTrue(label.contains("Shutter Sound"))
        assertTrue(label.contains("On"))
        // SUPPORTED items omit availability text from buttonLabel (shown separately as statusText)
        assertFalse(label.contains("Supported"))
    }

    @Test
    fun settingsControl_buttonLabel_uses_availabilityLabel_when_non_empty() {
        val model = SettingsControlRenderModel(
            label = "ISO",
            value = "Auto",
            availability = SettingsControlAvailability.UNSUPPORTED,
            availabilityLabel = "需要手动模式"
        )
        assertTrue(model.buttonLabel.contains("需要手动模式"))
        assertFalse(model.buttonLabel.contains("Unsupported"))
    }

    @Test
    fun settingsControl_buttonLabel_falls_back_to_enum_name_when_availabilityLabel_empty() {
        val model = SettingsControlRenderModel(
            label = "ISO",
            value = "Auto",
            availability = SettingsControlAvailability.DEGRADED,
            availabilityLabel = ""
        )
        assertTrue(model.buttonLabel.contains("Degraded"))
    }

    @Test
    fun settingsControl_buttonLabel_includes_supportLabel_with_bullet() {
        val model = SettingsControlRenderModel(
            label = "Grid",
            value = "3x3",
            supportLabel = "Pro"
        )
        assertTrue(model.buttonLabel.contains(" • Pro"))
    }

    @Test
    fun settingsControl_buttonLabel_omits_bullet_when_no_supportLabel() {
        val model = SettingsControlRenderModel(
            label = "Grid",
            value = "3x3",
            supportLabel = null
        )
        assertFalse(model.buttonLabel.contains("•"))
    }

    // endregion

    // region FeatureCatalogControlRenderModel.isInteractive

    @Test
    fun featureCatalog_isInteractive_true_when_has_action() {
        val model = FeatureCatalogControlRenderModel(
            label = "RAW",
            value = "Off",
            nextAction = FeatureCatalogAction.UpdateManualRawEnabled(true)
        )
        assertTrue(model.isInteractive)
    }

    @Test
    fun featureCatalog_isInteractive_false_when_no_action() {
        val model = FeatureCatalogControlRenderModel(
            label = "RAW",
            value = "Off",
            nextAction = null
        )
        assertFalse(model.isInteractive)
    }

    // endregion

    // region FeatureCatalogControlRenderModel.buttonLabel

    @Test
    fun featureCatalog_buttonLabel_contains_label_value_availability() {
        val model = FeatureCatalogControlRenderModel(
            label = "ISO",
            value = "400",
            availability = SettingsControlAvailability.SUPPORTED
        )
        val label = model.buttonLabel
        assertTrue(label.contains("ISO"))
        assertTrue(label.contains("400"))
        assertTrue(label.contains("Supported"))
    }

    @Test
    fun featureCatalog_buttonLabel_includes_supportLabel_with_bullet() {
        val model = FeatureCatalogControlRenderModel(
            label = "Focus",
            value = "Auto",
            supportLabel = "Manual"
        )
        assertTrue(model.buttonLabel.contains(" • Manual"))
    }

    @Test
    fun featureCatalog_buttonLabel_uses_availabilityLabel_when_non_empty() {
        val model = FeatureCatalogControlRenderModel(
            label = "Shutter",
            value = "1/60",
            availability = SettingsControlAvailability.UNSUPPORTED,
            availabilityLabel = "不支持"
        )
        assertTrue(model.buttonLabel.contains("不支持"))
    }

    // endregion

    // region SettingsControlAvailability enum

    @Test
    fun settingsControlAvailability_has_expected_values() {
        val values = SettingsControlAvailability.entries
        assertEquals(3, values.size)
        assertEquals(SettingsControlAvailability.SUPPORTED, values[0])
        assertEquals(SettingsControlAvailability.DEGRADED, values[1])
        assertEquals(SettingsControlAvailability.UNSUPPORTED, values[2])
    }

    // endregion

    // region SettingsTab enum

    @Test
    fun settingsTab_has_expected_values() {
        val values = SettingsTab.entries
        assertEquals(3, values.size)
        assertEquals(SettingsTab.COMMON, values[0])
        assertEquals(SettingsTab.PHOTO, values[1])
        assertEquals(SettingsTab.VIDEO, values[2])
    }

    @Test
    fun settingsTab_valueOf_works() {
        assertEquals(SettingsTab.COMMON, SettingsTab.valueOf("COMMON"))
        assertEquals(SettingsTab.PHOTO, SettingsTab.valueOf("PHOTO"))
        assertEquals(SettingsTab.VIDEO, SettingsTab.valueOf("VIDEO"))
    }

    // endregion

    // region default values

    @Test
    fun settingsControl_defaults_to_supported_enabled_no_action() {
        val model = SettingsControlRenderModel(label = "L", value = "V")
        assertEquals(SettingsControlAvailability.SUPPORTED, model.availability)
        assertEquals("", model.availabilityLabel)
        assertNull(model.supportLabel)
        assertNull(model.nextAction)
        assertTrue(model.enabled)
        assertNull(model.disabledReason)
        assertFalse(model.isInteractive) // no nextAction
    }

    @Test
    fun featureCatalog_defaults_to_supported_no_action() {
        val model = FeatureCatalogControlRenderModel(label = "L", value = "V")
        assertEquals(SettingsControlAvailability.SUPPORTED, model.availability)
        assertEquals("", model.availabilityLabel)
        assertNull(model.supportLabel)
        assertNull(model.nextAction)
        assertFalse(model.isInteractive) // no nextAction
    }

    // endregion
}
