package com.opencamera.core.settings

import kotlin.math.abs
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
    fun `missing recipe toneLift in tags returns neutral recipe`() {
        val parsed = parsePerceptualColorRecipe(mapOf("recipe.chromaBoost" to "0.5"))
        assertTrue(parsed.isNeutral)
    }

    @Test
    fun `codec round trip preserves recipe values`() {
        val original = PerceptualColorRecipe(
            toneLift = 0.4f,
            toneDepth = 0.25f,
            chromaBoost = 0.18f,
            warmthBias = 0.32f,
            tintBias = -0.08f,
            shadowTint = 0.12f,
            highlightTint = 0.05f,
            neutralProtection = 0.75f,
            skinProtection = 0.70f
        )
        val tags = original.toMetadataTags()
        val parsed = parsePerceptualColorRecipe(tags)
        assertEquals(original, parsed)
        assertFalse(parsed.isNeutral)
    }

    @Test
    fun `legacy recipe tag keys are still parseable`() {
        val tags = mapOf(
            "recipeToneLift" to "0.3",
            "recipeToneDepth" to "0.2",
            "recipeChromaBoost" to "0.1",
            "recipeWarmthBias" to "0.15",
            "recipeTintBias" to "0.0",
            "recipeNeutralProtection" to "0.8",
            "recipeSkinProtection" to "0.7"
        )
        val parsed = parsePerceptualColorRecipe(tags)
        assertEquals(0.3f, parsed.toneLift)
        assertEquals(0.2f, parsed.toneDepth)
        assertEquals(0.1f, parsed.chromaBoost)
        assertEquals(0.15f, parsed.warmthBias)
        assertEquals(0.0f, parsed.tintBias)
        assertFalse(parsed.isNeutral)
    }

    // --- Dead zone tests ---

    @Test
    fun `small offset within dead zone produces neutral recipe`() {
        val recipe = ColorLabSpec(colorAxis = 0.05f, toneAxis = -0.05f).toRecipe()
        assertTrue(recipe.isNeutral, "Small offsets within dead zone should be neutral")
    }

    @Test
    fun `offset beyond dead zone produces non-neutral recipe`() {
        val recipe = ColorLabSpec(colorAxis = 0.15f, toneAxis = 0f).toRecipe()
        assertFalse(recipe.isNeutral, "Offsets beyond dead zone should be non-neutral")
    }

    // --- Corner strength tests ---

    @Test
    fun `warm deep corner produces minimum non-neutral recipe values`() {
        val recipe = ColorLabSpec(colorAxis = 1f, toneAxis = -1f).toRecipe()
        assertFalse(recipe.isNeutral)
        assertTrue(recipe.warmthBias > 0.15f, "warm deep corner should have warmth bias > 0.15 (was ${recipe.warmthBias})")
        assertTrue(recipe.toneDepth > 0.15f, "warm deep corner should have tone depth > 0.15 (was ${recipe.toneDepth})")
        assertTrue(recipe.chromaBoost > 0.10f, "warm deep corner should have chroma boost > 0.10 (was ${recipe.chromaBoost})")
    }

    @Test
    fun `cool airy corner produces minimum non-neutral recipe values`() {
        val recipe = ColorLabSpec(colorAxis = -1f, toneAxis = 1f).toRecipe()
        assertFalse(recipe.isNeutral)
        assertTrue(recipe.warmthBias < -0.15f, "cool airy corner should have negative warmth bias (was ${recipe.warmthBias})")
        assertTrue(recipe.toneLift > 0.15f, "cool airy corner should have tone lift > 0.15 (was ${recipe.toneLift})")
        assertTrue(recipe.chromaBoost > 0.10f, "cool airy corner should have chroma boost > 0.10 (was ${recipe.chromaBoost})")
    }

    @Test
    fun `warm airy corner produces non-neutral recipe`() {
        val recipe = ColorLabSpec(colorAxis = 1f, toneAxis = 1f).toRecipe()
        assertFalse(recipe.isNeutral)
        assertTrue(recipe.warmthBias > 0.10f)
        assertTrue(recipe.toneLift > 0.10f)
    }

    @Test
    fun `cool deep corner produces non-neutral recipe`() {
        val recipe = ColorLabSpec(colorAxis = -1f, toneAxis = -1f).toRecipe()
        assertFalse(recipe.isNeutral)
        assertTrue(recipe.warmthBias < -0.10f)
        assertTrue(recipe.toneDepth > 0.10f)
    }

    // --- Protection behavior ---

    @Test
    fun `gray protection remains high for warm extreme`() {
        val recipe = ColorLabSpec(colorAxis = 1f, toneAxis = 0.5f).toRecipe()
        assertTrue(recipe.neutralProtection >= 0.65f,
            "Neutral protection should be >= 0.65 at warm extreme (was ${recipe.neutralProtection})")
        assertTrue(recipe.skinProtection >= 0.60f,
            "Skin protection should be >= 0.60 at warm extreme (was ${recipe.skinProtection})")
    }

    @Test
    fun `skin protection remains high for cool extreme`() {
        val recipe = ColorLabSpec(colorAxis = -1f, toneAxis = -0.5f).toRecipe()
        assertTrue(recipe.skinProtection >= 0.60f,
            "Skin protection should be >= 0.60 at cool extreme (was ${recipe.skinProtection})")
    }

    // --- MONOCHROME mutes color axis ---

    @Test
    fun `monochrome keeps color axis muted`() {
        val recipe = ColorLabSpec(colorAxis = 1f, toneAxis = -1f)
            .toRecipe(colorScience = StyleColorScience.MONOCHROME)
        assertEquals(0f, recipe.warmthBias)
        assertEquals(0f, recipe.chromaBoost)
        assertFalse(recipe.isNeutral, "Monochrome should still have non-neutral tone")
        assertTrue(recipe.toneDepth > 0f, "Monochrome tone axis should still be effective")
    }
}
