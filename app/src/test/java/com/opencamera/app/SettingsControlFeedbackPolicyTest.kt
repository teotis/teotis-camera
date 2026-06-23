package com.opencamera.app

import com.opencamera.core.settings.FeatureCatalogAction
import com.opencamera.core.settings.PersistedSettingsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsControlFeedbackPolicyTest {

    private val text = TestAppTextResolver()

    @Test
    fun `settings control success uses visible value update instead of toast`() {
        val control = SettingsControlRenderModel(
            label = "Shutter sound",
            value = "Off",
            nextAction = PersistedSettingsAction.UpdateShutterSoundEnabled(true)
        )

        assertNull(settingsControlApplyToastMessage(control, text))
    }

    @Test
    fun `settings control missing model still reports not loaded`() {
        assertEquals(
            "Settings not loaded yet",
            settingsControlApplyToastMessage(null, text)
        )
    }

    @Test
    fun `settings control without action still reports unsupported`() {
        val control = SettingsControlRenderModel(
            label = "Watermark",
            value = "Unavailable"
        )

        assertEquals(
            "Action not supported in current mode",
            settingsControlApplyToastMessage(control, text)
        )
    }

    @Test
    fun `feature catalog control success uses visible value update instead of toast`() {
        val control = FeatureCatalogControlRenderModel(
            label = "ISO",
            value = "Auto",
            nextAction = FeatureCatalogAction.UpdateManualIso(100)
        )

        assertNull(featureCatalogControlApplyToastMessage(control, text))
    }

    @Test
    fun `feature catalog control missing model still reports not loaded`() {
        assertEquals(
            "Settings not loaded yet",
            featureCatalogControlApplyToastMessage(null, text)
        )
    }

    @Test
    fun `feature catalog control without action still reports unsupported`() {
        val control = FeatureCatalogControlRenderModel(
            label = "ISO",
            value = "Auto"
        )

        assertEquals(
            "Action not supported in current mode",
            featureCatalogControlApplyToastMessage(control, text)
        )
    }
}
