package com.opencamera.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorIntentEngineTest {
    @Test
    fun `soft white preset resolves to a skin-safe brightening plan`() {
        val plan = ColorIntentEngine.resolve(
            ColorIntentRequest(
                styleProfileId = "photo-original",
                baseRenderSpec = FilterRenderSpec(),
                colorLabSpec = ColorLabSpec(presetId = ColorIntentPreset.SOFT_WHITE.id)
            )
        )

        assertEquals(ColorIntentPreset.SOFT_WHITE, plan.intent)
        assertTrue(plan.effectiveColorLabSpec.toneAxis > 0f)
        assertTrue(plan.recipe.toneLift > 0f)
        assertTrue(plan.recipe.skinProtection >= 0.72f)
        assertTrue(plan.diagnostics.contains("color-intent:soft-white"))
    }

    @Test
    fun `blue tone preset protects neutral colors while adding cool bias`() {
        val plan = ColorIntentEngine.resolve(
            ColorIntentRequest(
                styleProfileId = "photo-original",
                baseRenderSpec = FilterRenderSpec(),
                colorLabSpec = ColorLabSpec(presetId = ColorIntentPreset.BLUE_TONE.id)
            )
        )

        assertEquals(ColorIntentPreset.BLUE_TONE, plan.intent)
        assertTrue(plan.effectiveColorLabSpec.colorAxis < 0f)
        assertTrue(plan.recipe.warmthBias < 0f)
        assertTrue(plan.recipe.neutralProtection >= 0.72f)
        assertTrue(plan.finalRenderSpec.coolBoost > 0f)
    }

    @Test
    fun `custom palette keeps explicit axes instead of replacing them with a preset default`() {
        val plan = ColorIntentEngine.resolve(
            ColorIntentRequest(
                styleProfileId = "photo-original",
                baseRenderSpec = FilterRenderSpec(),
                colorLabSpec = ColorLabSpec(colorAxis = 0.42f, toneAxis = -0.18f)
            )
        )

        assertEquals(ColorIntentPreset.CUSTOM, plan.intent)
        assertEquals(0.42f, plan.effectiveColorLabSpec.colorAxis)
        assertEquals(-0.18f, plan.effectiveColorLabSpec.toneAxis)
        assertTrue(plan.finalRenderSpec.contrast > 1f)
    }

    @Test
    fun `style color rendering applies preset only color intent through the existing bridge`() {
        val result = renderStyleColorSpecWithRecipe(
            profileId = "photo-original",
            baseRenderSpec = FilterRenderSpec(),
            colorLabSpec = ColorLabSpec(presetId = ColorIntentPreset.CLEAR.id),
            styleStrength = 1f
        )

        requireNotNull(result)
        assertTrue(result.finalRenderSpec.shadowLift > 0f)
        assertTrue(result.recipe.toneLift > 0f)
        assertTrue(result.recipe.neutralProtection >= 0.72f)
    }
}
