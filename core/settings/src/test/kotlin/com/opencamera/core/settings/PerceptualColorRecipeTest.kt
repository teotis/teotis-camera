package com.opencamera.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerceptualColorRecipeTest {

    @Test
    fun `neutral spec produces neutral recipe`() {
        val recipe = ColorLabSpec().toRecipe()
        assertTrue(recipe.isNeutral)
    }

    @Test
    fun `zero strength produces neutral recipe`() {
        val recipe = ColorLabSpec(colorAxis = 1f, toneAxis = -1f, strength = 0f).toRecipe()
        assertTrue(recipe.isNeutral)
    }

    @Test
    fun `warm airy corner produces non-trivial recipe`() {
        val recipe = ColorLabSpec(colorAxis = 1f, toneAxis = 1f).toRecipe()
        assertFalse(recipe.isNeutral)
        assertTrue(recipe.toneLift > 0f)
        assertTrue(recipe.warmthBias > 0f)
        assertTrue(recipe.chromaBoost > 0f)
    }

    @Test
    fun `warm deep corner produces strong depth and warmth`() {
        val recipe = ColorLabSpec(colorAxis = 1f, toneAxis = -1f).toRecipe()
        assertTrue(recipe.toneDepth > 0f)
        assertTrue(recipe.warmthBias > 0f)
        assertTrue(recipe.chromaBoost > 0f)
    }

    @Test
    fun `cool airy corner produces cool bias with tone lift`() {
        val recipe = ColorLabSpec(colorAxis = -1f, toneAxis = 1f).toRecipe()
        assertTrue(recipe.toneLift > 0f)
        assertTrue(recipe.warmthBias < 0f)
        assertTrue(recipe.chromaBoost > 0f)
    }

    @Test
    fun `cool deep corner produces depth with cool bias`() {
        val recipe = ColorLabSpec(colorAxis = -1f, toneAxis = -1f).toRecipe()
        assertTrue(recipe.toneDepth > 0f)
        assertTrue(recipe.warmthBias < 0f)
    }

    @Test
    fun `texture science produces reduced chroma compared to natural`() {
        val natural = ColorLabSpec(colorAxis = 1f, toneAxis = 0f).toRecipe(StyleColorScience.NATURAL)
        val texture = ColorLabSpec(colorAxis = 1f, toneAxis = 0f).toRecipe(StyleColorScience.TEXTURE)
        assertTrue(texture.chromaBoost < natural.chromaBoost)
        assertTrue(texture.neutralProtection > natural.neutralProtection)
    }

    @Test
    fun `vivid science produces higher chroma than natural`() {
        val natural = ColorLabSpec(colorAxis = 1f, toneAxis = 0f).toRecipe(StyleColorScience.NATURAL)
        val vivid = ColorLabSpec(colorAxis = 1f, toneAxis = 0f).toRecipe(StyleColorScience.VIVID)
        assertTrue(vivid.chromaBoost > natural.chromaBoost)
    }

    @Test
    fun `monochrome science ignores color axis`() {
        val recipe = ColorLabSpec(colorAxis = 1f, toneAxis = 0f).toRecipe(StyleColorScience.MONOCHROME)
        assertEquals(0f, recipe.chromaBoost)
        assertEquals(0f, recipe.warmthBias)
        assertEquals(0f, recipe.tintBias)
    }

    @Test
    fun `monochrome science keeps tone axis`() {
        val recipe = ColorLabSpec(colorAxis = 0f, toneAxis = 1f).toRecipe(StyleColorScience.MONOCHROME)
        assertTrue(recipe.toneLift > 0f)
    }

    @Test
    fun `all four corners produce non-trivial recipes across natural science`() {
        val corners = listOf(
            ColorLabSpec(colorAxis = 1f, toneAxis = 1f),
            ColorLabSpec(colorAxis = 1f, toneAxis = -1f),
            ColorLabSpec(colorAxis = -1f, toneAxis = 1f),
            ColorLabSpec(colorAxis = -1f, toneAxis = -1f)
        )
        corners.forEach { spec ->
            val recipe = spec.toRecipe(StyleColorScience.NATURAL)
            assertFalse(recipe.isNeutral, "Corner (${
                spec.colorAxis}, ${spec.toneAxis}) should not be neutral")
            assertTrue(recipe.neutralProtection > 0f)
            assertTrue(recipe.skinProtection > 0f)
        }
    }

    @Test
    fun `skin and neutral protection are set for non-monochrome`() {
        val recipe = ColorLabSpec(colorAxis = 0.5f, toneAxis = -0.5f).toRecipe(StyleColorScience.NATURAL)
        assertTrue(recipe.neutralProtection >= 0.5f)
        assertTrue(recipe.skinProtection >= 0.5f)
    }

    @Test
    fun `non-neutral recipe metadata round trip preserves all fields`() {
        val original = ColorLabSpec(colorAxis = 1f, toneAxis = -1f).toRecipe(StyleColorScience.NATURAL)
        val tags = original.toMetadataTags()
        val parsed = parsePerceptualColorRecipe(tags)

        assertEquals(original.toneLift, parsed.toneLift)
        assertEquals(original.toneDepth, parsed.toneDepth)
        assertEquals(original.chromaBoost, parsed.chromaBoost)
        assertEquals(original.warmthBias, parsed.warmthBias)
        assertEquals(original.tintBias, parsed.tintBias)
        assertEquals(original.shadowTint, parsed.shadowTint)
        assertEquals(original.highlightTint, parsed.highlightTint)
        assertEquals(original.neutralProtection, parsed.neutralProtection)
        assertEquals(original.skinProtection, parsed.skinProtection)
    }

    @Test
    fun `neutral recipe produces empty metadata tags`() {
        val tags = PerceptualColorRecipe.NEUTRAL.toMetadataTags()
        assertTrue(tags.isEmpty())
    }

    @Test
    fun `missing recipeToneLift in tags returns neutral recipe`() {
        val parsed = parsePerceptualColorRecipe(mapOf("recipeChromaBoost" to "0.5"))
        assertTrue(parsed.isNeutral)
    }
}
