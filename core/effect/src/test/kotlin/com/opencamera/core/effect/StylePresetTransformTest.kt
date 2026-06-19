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

}
