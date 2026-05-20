package com.opencamera.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterProfileShareCodecTest {
    @Test
    fun `share codec round trips custom filter profile with render spec`() {
        val profile = FilterProfile(
            id = "custom-amber-street",
            label = "Amber Street",
            category = FilterProfileCategory.CUSTOM,
            builtIn = false,
            renderSpec = FilterRenderSpec(
                brightnessShift = 7,
                contrast = 1.11f,
                saturation = 0.93f,
                warmthShift = 5,
                tintShift = -4,
                monochromeMix = 0.08f,
                vignetteStrength = 0.17f,
                softGlowStrength = 0.1f,
                haloStrength = 0.2f,
                grainStrength = 0.08f,
                sharpnessBoost = 0.2f,
                highlightCompression = 0.18f,
                shadowLift = 0.16f,
                warmBoost = 0.12f
            )
        )

        val serialized = FilterProfileShareCodec.export(profile)
        val decoded = FilterProfileShareCodec.import(serialized)

        assertEquals(profile, decoded)
        assertTrue(serialized.contains("OPEN_CAMERA_FILTER_PROFILE_V1"))
        assertTrue(serialized.contains("id=custom-amber-street"))
    }

    @Test
    fun `metadata tags recover shared filter render spec`() {
        val renderSpec = FilterRenderSpec(
            brightnessShift = 5,
            contrast = 1.06f,
            saturation = 1.12f,
            warmthShift = -3,
            tintShift = 6,
            vignetteStrength = 0.12f
        )

        val recovered = FilterRenderSpec.fromMetadataTags(renderSpec.toMetadataTags())

        assertEquals(renderSpec, recovered)
    }

    @Test
    fun `feature catalog merges imported custom filter without dropping built ins`() {
        val imported = FilterProfile(
            id = "custom-indigo",
            label = "Indigo Story",
            category = FilterProfileCategory.CUSTOM,
            builtIn = false,
            renderSpec = FilterRenderSpec(contrast = 1.03f, warmthShift = -4)
        )

        val merged = FeatureCatalog().withImportedFilterProfile(imported)

        assertTrue(merged.filterProfiles.any { it.id == "photo-vivid" && it.builtIn })
        assertEquals(imported, merged.filterProfileOrNull("custom-indigo"))
        assertFalse(merged.filterProfileOrNull("custom-indigo")?.builtIn ?: true)
    }

    @Test
    fun `feature catalog can derive a new custom filter from built in profile`() {
        val custom = FeatureCatalog().createCustomFilterProfile("portrait-original")

        assertEquals("custom-portrait-original-1", custom?.id)
        assertEquals("Portrait Original Custom 1", custom?.label)
        assertEquals(FilterProfileCategory.CUSTOM, custom?.category)
        assertFalse(custom?.builtIn ?: true)
        assertEquals(
            FeatureCatalog().filterProfileOrNull("portrait-original")?.renderSpec,
            custom?.renderSpec
        )
    }

    @Test
    fun `imported filter profile serializer round trips multiple custom filters`() {
        val profiles = listOf(
            FilterProfile(
                id = "custom-indigo",
                label = "Indigo Story",
                category = FilterProfileCategory.CUSTOM,
                builtIn = false,
                renderSpec = FilterRenderSpec(contrast = 1.03f, warmthShift = -4)
            ),
            FilterProfile(
                id = "custom-amber",
                label = "Amber Story",
                category = FilterProfileCategory.CUSTOM,
                builtIn = false,
                renderSpec = FilterRenderSpec(brightnessShift = 5, vignetteStrength = 0.14f)
            )
        )

        val serialized = ImportedFilterProfilesSerializer.serialize(profiles)
        val decoded = ImportedFilterProfilesSerializer.deserialize(serialized)

        assertEquals(profiles, decoded)
    }
}
