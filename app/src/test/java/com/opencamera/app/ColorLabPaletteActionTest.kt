package com.opencamera.app

import com.opencamera.core.settings.ColorLabSpec
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.PhotoSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorLabPaletteActionTest {

    @Test
    fun `color lab palette touch updates persisted color lab spec and preserves strength`() {
        val persisted = PersistedSettings(
            photo = PhotoSettings(
                colorLabSpec = ColorLabSpec(
                    colorAxis = 0f,
                    toneAxis = 0f,
                    strength = 0.72f,
                    presetId = "warm-soft"
                )
            )
        )

        val action = colorLabPaletteUpdateAction(
            persisted = persisted,
            colorAxis = 0.45f,
            toneAxis = -0.35f
        )

        assertEquals(
            PersistedSettingsAction.UpdateColorLabSpec(
                ColorLabSpec(
                    colorAxis = 0.45f,
                    toneAxis = -0.35f,
                    strength = 0.72f,
                    presetId = "warm-soft"
                )
            ),
            action
        )
    }

    @Test
    fun `color lab palette touch clamps axes into supported range`() {
        val action = colorLabPaletteUpdateAction(
            persisted = PersistedSettings(),
            colorAxis = 3f,
            toneAxis = -3f
        )

        assertEquals(1f, action.spec.colorAxis)
        assertEquals(-1f, action.spec.toneAxis)
    }

    @Test
    fun `neutral color lab action resets the persisted spec`() {
        val action = neutralColorLabAction()

        assertEquals(ColorLabSpec(), action.spec)
    }
}
