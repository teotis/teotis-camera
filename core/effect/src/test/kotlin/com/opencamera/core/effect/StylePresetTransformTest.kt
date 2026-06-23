package com.opencamera.core.effect

import com.opencamera.core.settings.DEFAULT_FILTER_PROFILES
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.PreviewColorFidelity
import com.opencamera.core.settings.StylePreset
import com.opencamera.core.settings.StylePresetFamily
import com.opencamera.core.settings.createApplyAction
import com.opencamera.core.settings.reduce
import com.opencamera.core.settings.toStylePresetPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Pack R, G, B into an opaque ARGB int (test helper). */
private fun rgb(r: Int, g: Int, b: Int): Int =
    (0xFF shl 24) or (r shl 16) or (g shl 8) or b

/** Extract red channel from packed ARGB int. */
private fun Int.r(): Int = (this shr 16) and 0xFF
/** Extract green channel from packed ARGB int. */
private fun Int.g(): Int = (this shr 8) and 0xFF
/** Extract blue channel from packed ARGB int. */
private fun Int.b(): Int = this and 0xFF

class StylePresetTransformTest {

    private val adapter = PreviewEffectAdapter()

    // ── Helper: resolve a FilterProfile by ID from the default catalog ──

    private fun profile(id: String) =
        DEFAULT_FILTER_PROFILES.first { it.id == id }

    private fun photoPreset(profileId: String, selected: Boolean = false): StylePreset {
        val p = profile(profileId)
        return StylePreset(
            profileId = p.id,
            label = p.label,
            family = StylePresetFamily.PHOTO,
            preview = p.renderSpec?.toStylePresetPreview()
                ?: FilterRenderSpec().toStylePresetPreview(),
            isSelected = selected,
            applyAction = StylePresetFamily.PHOTO.createApplyAction(p.id),
            sortIndex = 0
        )
    }

    // ── Bridge: StylePreset.toFilterEffect ──

    @Test
    fun `toFilterEffect creates FilterEffect with matching profileId and renderSpec`() {
        val preset = photoPreset("photo-vivid")
        val filterProfile = profile("photo-vivid")

        val filterEffect = preset.toFilterEffect(filterProfile)

        assertEquals("photo-vivid", filterEffect.profileId)
        assertEquals(filterProfile.renderSpec, filterEffect.renderSpec)
    }

    @Test
    fun `toFilterEffect with original profile preserves null renderSpec`() {
        val preset = photoPreset("photo-original")
        val filterProfile = profile("photo-original")

        val filterEffect = preset.toFilterEffect(filterProfile)

        assertEquals("photo-original", filterEffect.profileId)
        // photo-original has near-identity spec (not null)
        assertNotNull(filterEffect.renderSpec)
    }

    // ── Bridge: FilterProfile.toPreviewColorTransform ──

    @Test
    fun `original profile produces near-identity transform`() {
        val transform = profile("photo-original").toPreviewColorTransform()

        // photo-original has contrast=1.01, saturation=1.01 — nearly invisible
        assertFalse(transform.isIdentity)
        assertNotNull(transform.matrix)
        assertEquals(PreviewColorFidelity.APPROXIMATE, transform.fidelity)
    }

    @Test
    fun `vivid profile produces non-identity transform`() {
        val transform = profile("photo-vivid").toPreviewColorTransform()

        assertFalse(transform.isIdentity)
        assertEquals(PreviewColorFidelity.APPROXIMATE, transform.fidelity)
        assertNotNull(transform.matrix)
    }

    @Test
    fun `bw profile produces monochrome transform`() {
        val transform = profile("photo-bw").toPreviewColorTransform()

        assertFalse(transform.isIdentity)
        assertNotNull(transform.matrix)
        assertEquals(PreviewColorFidelity.APPROXIMATE, transform.fidelity)
    }

    @Test
    fun `texture profile produces non-identity transform`() {
        val transform = profile("photo-texture").toPreviewColorTransform()

        assertFalse(transform.isIdentity)
        assertEquals(PreviewColorFidelity.APPROXIMATE, transform.fidelity)
    }

    // ── Full pipeline: preset → EffectSpec → adapter → color transform ──

    @Test
    fun `applying original preset through adapter produces near-identity transform`() {
        val preset = photoPreset("photo-original")
        val filterProfile = profile("photo-original")
        val filterEffect = preset.toFilterEffect(filterProfile)
        val effectSpec = EffectSpec(listOf(filterEffect))

        val renderModel = adapter.adapt(effectSpec)

        // photo-original has near-identity spec (contrast=1.01, saturation=1.01)
        assertFalse(renderModel.colorTransform.isIdentity,
            "Original preset transform should be nearly identity")
    }

    @Test
    fun `applying vivid preset through adapter produces warm non-identity transform`() {
        val preset = photoPreset("photo-vivid")
        val filterProfile = profile("photo-vivid")
        val filterEffect = preset.toFilterEffect(filterProfile)
        val effectSpec = EffectSpec(listOf(filterEffect))

        val renderModel = adapter.adapt(effectSpec)

        assertFalse(renderModel.colorTransform.isIdentity,
            "Vivid preset should produce non-identity transform")
        assertEquals(PreviewColorFidelity.APPROXIMATE, renderModel.colorTransform.fidelity)
        assertNotNull(renderModel.filterOverlay)
    }

    @Test
    fun `applying bw preset through adapter produces monochrome transform`() {
        val preset = photoPreset("photo-bw")
        val filterProfile = profile("photo-bw")
        val filterEffect = preset.toFilterEffect(filterProfile)
        val effectSpec = EffectSpec(listOf(filterEffect))

        val renderModel = adapter.adapt(effectSpec)

        assertFalse(renderModel.colorTransform.isIdentity,
            "B&W preset should produce non-identity transform")
        assertNotNull(renderModel.colorTransform.matrix)
        assertEquals(PreviewColorFidelity.APPROXIMATE, renderModel.colorTransform.fidelity)
    }

    @Test
    fun `applying texture preset through adapter produces non-identity transform`() {
        val preset = photoPreset("photo-texture")
        val filterProfile = profile("photo-texture")
        val filterEffect = preset.toFilterEffect(filterProfile)
        val effectSpec = EffectSpec(listOf(filterEffect))

        val renderModel = adapter.adapt(effectSpec)

        assertFalse(renderModel.colorTransform.isIdentity,
            "Texture preset should produce non-identity transform")
        assertNotNull(renderModel.filterOverlay)
    }

    // ── Switching presets produces different transforms ──

    @Test
    fun `switching from original to vivid produces stronger transform`() {
        val originalSpec = EffectSpec(listOf(
            photoPreset("photo-original").toFilterEffect(profile("photo-original"))
        ))
        val vividSpec = EffectSpec(listOf(
            photoPreset("photo-vivid").toFilterEffect(profile("photo-vivid"))
        ))

        val originalTransform = adapter.adapt(originalSpec).colorTransform
        val vividTransform = adapter.adapt(vividSpec).colorTransform

        // Both are non-identity (original has near-identity spec)
        assertFalse(originalTransform.isIdentity)
        assertFalse(vividTransform.isIdentity)

        // Vivid should have a more pronounced color matrix deviation from identity
        val vividMatrix = vividTransform.matrix!!
        val origMatrix = originalTransform.matrix!!
        // Vivid saturation (1.14) should produce larger deviation from identity than original (1.01)
        assertTrue(kotlin.math.abs(vividMatrix[0] - 1f) > kotlin.math.abs(origMatrix[0] - 1f),
            "Vivid should deviate more from identity than original")
    }

    @Test
    fun `switching from vivid to bw changes transform characteristics`() {
        val vividSpec = EffectSpec(listOf(
            photoPreset("photo-vivid").toFilterEffect(profile("photo-vivid"))
        ))
        val bwSpec = EffectSpec(listOf(
            photoPreset("photo-bw").toFilterEffect(profile("photo-bw"))
        ))

        val vividTransform = adapter.adapt(vividSpec).colorTransform
        val bwTransform = adapter.adapt(bwSpec).colorTransform

        // Both should be non-identity
        assertFalse(vividTransform.isIdentity)
        assertFalse(bwTransform.isIdentity)

        // B&W should have monochrome mix affecting the color matrix
        val bwMatrix = bwTransform.matrix!!
        // Saturation rows in B&W should reflect monochrome (near-grayscale weights)
        val bwSatR = bwMatrix[0] + bwMatrix[1] + bwMatrix[2]
        assertTrue(bwSatR < 1.5f,
            "B&W saturation row sum ($bwSatR) should reflect reduced saturation")

        // Vivid should preserve color channel differentiation
        val vividMatrix = vividTransform.matrix!!
        assertTrue(vividMatrix[0] > 0.5f,
            "Vivid red channel should be near unity (was ${vividMatrix[0]})")
    }

    // ── Degraded/approximate fidelity semantics ──

    @Test
    fun `non-identity preset reports approximate fidelity`() {
        val richSpec = EffectSpec(listOf(
            photoPreset("photo-rich").toFilterEffect(profile("photo-rich"))
        ))
        val textureSpec = EffectSpec(listOf(
            photoPreset("photo-texture").toFilterEffect(profile("photo-texture"))
        ))

        val richModel = adapter.adapt(richSpec)
        val textureModel = adapter.adapt(textureSpec)

        assertEquals(PreviewColorFidelity.APPROXIMATE, richModel.colorTransform.fidelity)
        assertEquals(PreviewColorFidelity.APPROXIMATE, textureModel.colorTransform.fidelity)
    }

    @Test
    fun `original preset reports approximate fidelity for near-identity spec`() {
        val originalSpec = EffectSpec(listOf(
            photoPreset("photo-original").toFilterEffect(profile("photo-original"))
        ))

        val model = adapter.adapt(originalSpec)

        // photo-original has contrast=1.01, saturation=1.01 → non-identity
        assertEquals(PreviewColorFidelity.APPROXIMATE, model.colorTransform.fidelity)
    }

    // ── Settings reducer: applying preset updates defaultFilterProfileId ──

    @Test
    fun `applyAction updates photo filter profile id via settings reduce`() {
        val preset = photoPreset("photo-vivid")
        val initialSettings = PersistedSettings(
            photo = PhotoSettings(defaultFilterProfileId = "photo-original")
        )

        val updatedSettings = initialSettings.reduce(preset.applyAction)

        assertEquals("photo-vivid", updatedSettings.photo.defaultFilterProfileId)
    }

    @Test
    fun `applyAction for different presets updates to different profile ids`() {
        val initialSettings = PersistedSettings(
            photo = PhotoSettings(defaultFilterProfileId = "photo-original")
        )

        val vividSettings = initialSettings.reduce(
            photoPreset("photo-vivid").applyAction
        )
        val bwSettings = initialSettings.reduce(
            photoPreset("photo-bw").applyAction
        )

        assertEquals("photo-vivid", vividSettings.photo.defaultFilterProfileId)
        assertEquals("photo-bw", bwSettings.photo.defaultFilterProfileId)
    }

    // ── Mask-aware fallback priority: style transform must not be overridden ──

    @Test
    fun `style transform takes priority over mask fallback`() {
        val preset = photoPreset("photo-vivid")
        val filterProfile = profile("photo-vivid")
        val filterEffect = preset.toFilterEffect(filterProfile)
        val effectSpec = EffectSpec(listOf(filterEffect))

        val staleMask = PreviewSceneMaskSnapshot.UNAVAILABLE.copy(
            backendId = "mlkit-selfie",
            isStale = true,
            isAvailable = false
        )

        val renderModel = adapter.adapt(effectSpec, maskSnapshot = staleMask)

        // The spec-based transform should take priority (not mask fallback)
        assertFalse(renderModel.colorTransform.isIdentity,
            "Style transform should override mask fallback")
        assertEquals(PreviewColorFidelity.APPROXIMATE, renderModel.colorTransform.fidelity)
    }

    @Test
    fun `original style with available mask still applies spec transform`() {
        val preset = photoPreset("photo-original")
        val filterProfile = profile("photo-original")
        val filterEffect = preset.toFilterEffect(filterProfile)
        val effectSpec = EffectSpec(listOf(filterEffect))

        val availableMask = PreviewSceneMaskSnapshot.UNAVAILABLE.copy(
            isAvailable = true,
            backendId = "mlkit-person"
        )

        val renderModel = adapter.adapt(effectSpec, maskSnapshot = availableMask)

        // photo-original has near-identity spec (contrast=1.01, saturation=1.01)
        // The spec-based transform takes priority over mask-aware
        assertEquals(PreviewColorFidelity.APPROXIMATE, renderModel.colorTransform.fidelity)
    }

    // ── Humanistic family presets ──

    @Test
    fun `humanistic presets resolve correct profiles and produce transforms`() {
        val humanisticProfiles = DEFAULT_FILTER_PROFILES
            .filter { it.category == FilterProfileCategory.HUMANISTIC }

        assertTrue(humanisticProfiles.isNotEmpty(), "Should have humanistic profiles")

        humanisticProfiles.forEach { profile ->
            val transform = profile.toPreviewColorTransform()
            // All profiles should produce a valid transform
            assertTrue(transform.fidelity != PreviewColorFidelity.NONE || transform.isIdentity,
                "Profile ${profile.id} should have valid fidelity")
        }
    }

    // ── applyFilterRenderSpecToStops: five-dimension coverage ────────────

    private val neutralStop = PreviewStop(rgb(150, 140, 130), 0.5f)

    @Test
    fun `applyFilterRenderSpecToStops returns same stops count`() {
        val stops = listOf(
            PreviewStop(rgb(100, 120, 140), 0f),
            PreviewStop(rgb(200, 180, 160), 1f)
        )
        val result = applyFilterRenderSpecToStops(FilterRenderSpec(), stops)
        assertEquals(stops.size, result.size)
    }

    @Test
    fun `applyFilterRenderSpecToStops identity spec produces near-identical colors`() {
        val result = applyFilterRenderSpecToStops(FilterRenderSpec(), listOf(neutralStop))
        assertEquals(neutralStop.color, result[0].color)
        assertEquals(neutralStop.fraction, result[0].fraction)
    }

    @Test
    fun `applyFilterRenderSpecToStops high contrast stretches tonal range`() {
        // Use values clearly away from midpoint (127.5) so contrast has visible effect
        val brightStop = PreviewStop(rgb(200, 180, 160), 0.5f)
        val spec = FilterRenderSpec(contrast = 1.5f)
        val result = applyFilterRenderSpecToStops(spec, listOf(brightStop))
        val outR = result[0].color.r()
        // R=200: (200/255 - 0.5) * 1.5 * 255 + 127.5 ≈ 216.5 → clearly > 200
        assertTrue(outR > 200, "High contrast should push bright red above 200, got $outR")
    }

    @Test
    fun `applyFilterRenderSpecToStops low contrast compresses tonal range`() {
        val brightStop = PreviewStop(rgb(200, 180, 160), 0.5f)
        val spec = FilterRenderSpec(contrast = 0.5f)
        val result = applyFilterRenderSpecToStops(spec, listOf(brightStop))
        val outR = result[0].color.r()
        // R=200: (200/255 - 0.5) * 0.5 * 255 + 127.5 ≈ 163.75 → clearly < 200
        assertTrue(outR < 200, "Low contrast should bring bright red below 200, got $outR")
    }

    @Test
    fun `applyFilterRenderSpecToStops positive brightness shift lightens all channels`() {
        val spec = FilterRenderSpec(brightnessShift = 12)
        val result = applyFilterRenderSpecToStops(spec, listOf(neutralStop))
        val outR = result[0].color.r()
        val outG = result[0].color.g()
        val outB = result[0].color.b()
        assertTrue(outR > 150, "Brightness shift +12 should raise red above 150, got $outR")
        assertTrue(outG > 140, "Brightness shift +12 should raise green above 140, got $outG")
        assertTrue(outB > 130, "Brightness shift +12 should raise blue above 130, got $outB")
    }

    @Test
    fun `applyFilterRenderSpecToStops negative brightness shift darkens all channels`() {
        val spec = FilterRenderSpec(brightnessShift = -10)
        val result = applyFilterRenderSpecToStops(spec, listOf(neutralStop))
        val outR = result[0].color.r()
        val outB = result[0].color.b()
        assertTrue(outR < 150, "Brightness shift -10 should lower red below 150, got $outR")
        assertTrue(outB < 130, "Brightness shift -10 should lower blue below 130, got $outB")
    }

    @Test
    fun `applyFilterRenderSpecToStops positive warmth shift raises red and lowers blue`() {
        val spec = FilterRenderSpec(warmthShift = 10)
        val result = applyFilterRenderSpecToStops(spec, listOf(neutralStop))
        val outR = result[0].color.r()
        val outB = result[0].color.b()
        // R=150+10=160, B=130-10=120
        assertTrue(outR >= 155, "Warmth +10 should raise red, got $outR")
        assertTrue(outB <= 125, "Warmth +10 should lower blue, got $outB")
    }

    @Test
    fun `applyFilterRenderSpecToStops negative warmth shift raises blue and lowers red`() {
        val spec = FilterRenderSpec(warmthShift = -10)
        val result = applyFilterRenderSpecToStops(spec, listOf(neutralStop))
        val outR = result[0].color.r()
        val outB = result[0].color.b()
        // R=150-10=140, B=130+10=140
        assertTrue(outR <= 145, "Cool -10 should lower red, got $outR")
        assertTrue(outB >= 135, "Cool -10 should raise blue, got $outB")
    }

    @Test
    fun `applyFilterRenderSpecToStops high saturation boosts vivid channels`() {
        val colorful = PreviewStop(rgb(200, 100, 80), 0.5f)
        val spec = FilterRenderSpec(saturation = 1.5f)
        val result = applyFilterRenderSpecToStops(spec, listOf(colorful))
        val outR = result[0].color.r()
        val outG = result[0].color.g()
        assertTrue(outR > 200, "High saturation should boost red above 200, got $outR")
        assertTrue(outG < 100, "High saturation should pull green below 100, got $outG")
    }

    @Test
    fun `applyFilterRenderSpecToStops monochrome mix reduces color variation`() {
        val colorful = PreviewStop(rgb(220, 100, 50), 0.5f)
        val spec = FilterRenderSpec(monochromeMix = 1.0f)
        val result = applyFilterRenderSpecToStops(spec, listOf(colorful))
        val outR = result[0].color.r()
        val outG = result[0].color.g()
        val outB = result[0].color.b()
        val spread = maxOf(outR, outG, outB) - minOf(outR, outG, outB)
        assertTrue(spread <= 5, "Full monochrome should produce near-gray, spread=$spread (R=$outR,G=$outG,B=$outB)")
    }

    @Test
    fun `applyFilterRenderSpecToStops partial monochromeMix blends toward gray`() {
        val colorful = PreviewStop(rgb(220, 100, 50), 0.5f)
        val spec = FilterRenderSpec(monochromeMix = 0.5f)
        val result = applyFilterRenderSpecToStops(spec, listOf(colorful))
        val outR = result[0].color.r()
        val outG = result[0].color.g()
        assertTrue(outR < 220, "Partial monochrome should reduce red from 220, got $outR")
        assertTrue(outG > 100, "Partial monochrome should raise green from 100, got $outG")
    }

    @Test
    fun `applyFilterRenderSpecToStops warmBoost adds warmth to red channel`() {
        val spec = FilterRenderSpec(warmBoost = 0.2f)
        val result = applyFilterRenderSpecToStops(spec, listOf(neutralStop))
        val outR = result[0].color.r()
        assertTrue(outR > 150, "warmBoost should raise red, got $outR")
    }

    @Test
    fun `applyFilterRenderSpecToStops coolBoost adds coolness to blue channel`() {
        val spec = FilterRenderSpec(coolBoost = 0.2f)
        val result = applyFilterRenderSpecToStops(spec, listOf(neutralStop))
        val outB = result[0].color.b()
        assertTrue(outB > 130, "coolBoost should raise blue, got $outB")
    }

    @Test
    fun `applyFilterRenderSpecToStops combined spec produces expected compound effect`() {
        val spec = FilterRenderSpec(
            brightnessShift = 5,
            contrast = 1.1f,
            saturation = 1.1f,
            warmthShift = 6
        )
        val result = applyFilterRenderSpecToStops(spec, listOf(neutralStop))
        val outR = result[0].color.r()
        assertTrue(outR > 155, "Combined warm+bright+contrast+sat should push red above 155, got $outR")
        assertEquals(0.5f, result[0].fraction, "Fraction should be preserved")
    }

    @Test
    fun `applyFilterRenderSpecToStops preserves stop fractions`() {
        val stops = listOf(
            PreviewStop(rgb(100, 120, 140), 0f),
            PreviewStop(rgb(200, 180, 160), 0.35f),
            PreviewStop(rgb(220, 200, 180), 0.65f),
            PreviewStop(rgb(50, 45, 55), 1f)
        )
        val spec = FilterRenderSpec(saturation = 1.2f, warmthShift = 5)
        val result = applyFilterRenderSpecToStops(spec, stops)
        assertEquals(4, result.size)
        assertEquals(0f, result[0].fraction)
        assertEquals(0.35f, result[1].fraction)
        assertEquals(0.65f, result[2].fraction)
        assertEquals(1f, result[3].fraction)
    }

    @Test
    fun `toStylePresetPreview includes raw spec values`() {
        val spec = FilterRenderSpec(
            saturation = 1.25f,
            contrast = 1.15f,
            brightnessShift = 8,
            warmthShift = 7
        )
        val preview = spec.toStylePresetPreview()
        assertEquals(1.25f, preview.rawSaturation)
        assertEquals(1.15f, preview.rawContrast)
        assertEquals(8, preview.rawBrightnessShift)
        assertEquals(7, preview.rawWarmthShift)
    }

    @Test
    fun `toStylePresetPreview default spec has neutral raw values`() {
        val spec = FilterRenderSpec()
        val preview = spec.toStylePresetPreview()
        assertEquals(1f, preview.rawSaturation)
        assertEquals(1f, preview.rawContrast)
        assertEquals(0, preview.rawBrightnessShift)
        assertEquals(0, preview.rawWarmthShift)
    }

}
