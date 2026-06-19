// 覆盖行为:
// - presetsFor 按 FilterProfileCategory 正确过滤 profiles
// - presetsFor 保持 catalog 中的顺序
// - presetsFor 为每个 preset 生成确定性 preview
// - presetsFor 标记当前 selected profile 为 isSelected=true
// - presetsFor 每个 preset 只有一个 isSelected=true
// - buildPresetLibrary 为每个 family 生成 presets
// - selectedPreset 返回当前选中的 preset
// - selectedPreset 在 selected id 不匹配时返回 null
// - previewForProfile 返回正确的 preview
// - previewForProfile 对未知 id 返回 null
// - availableFamilies 返回有 profiles 的 families
// - totalPresetCount 返回总 profile 数
// - StylePresetPreview.derivedMoodLabel 从 spec 确定性推导
// - applyAction 对应正确的 PersistedSettingsAction
//
// 暂时不适合单测:
// - 无（纯数据投影函数，完全可测）

package com.opencamera.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StylePresetCatalogTest {

    private val testCatalog = FeatureCatalog(
        filterProfiles = DEFAULT_FILTER_PROFILES
    )

    private val defaultSettings = PersistedSettings()

    // --- presetsFor: category filtering ---

    @Test
    fun `presetsFor PHOTO family returns only PHOTO category profiles`() {
        val presets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.PHOTO)
        assertTrue(presets.isNotEmpty())
        presets.forEach { preset ->
            assertEquals(StylePresetFamily.PHOTO, preset.family)
            assertEquals(FilterProfileCategory.PHOTO, preset.family.profileCategory)
        }
    }

    @Test
    fun `presetsFor PORTRAIT family returns only PORTRAIT category profiles`() {
        val presets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.PORTRAIT)
        assertTrue(presets.isNotEmpty())
        presets.forEach { preset ->
            assertEquals(StylePresetFamily.PORTRAIT, preset.family)
        }
    }

    @Test
    fun `presetsFor HUMANISTIC family returns only HUMANISTIC category profiles`() {
        val presets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.HUMANISTIC)
        assertTrue(presets.isNotEmpty())
        presets.forEach { preset ->
            assertEquals(StylePresetFamily.HUMANISTIC, preset.family)
        }
    }

    // --- ordering ---

    @Test
    fun `presetsFor preserves catalog ordering within family`() {
        val photoProfiles = DEFAULT_FILTER_PROFILES.filter { it.category == FilterProfileCategory.PHOTO }
        val presets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.PHOTO)
        assertEquals(photoProfiles.size, presets.size)
        presets.forEachIndexed { index, preset ->
            assertEquals(photoProfiles[index].id, preset.profileId)
            assertEquals(photoProfiles[index].label, preset.label)
        }
    }

    @Test
    fun `sortIndex matches insertion order`() {
        val presets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.PHOTO)
        presets.forEachIndexed { index, preset ->
            assertEquals(index, preset.sortIndex)
        }
    }

    // --- selected state ---

    @Test
    fun `presetsFor marks default photo profile as selected`() {
        val presets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.PHOTO)
        val selected = presets.filter { it.isSelected }
        assertEquals(1, selected.size, "Exactly one preset should be selected")
        assertEquals("photo-original", selected.first().profileId)
    }

    @Test
    fun `presetsFor marks custom selected photo profile`() {
        val customSettings = PersistedSettings(
            photo = PhotoSettings(defaultFilterProfileId = "photo-vivid")
        )
        val presets = StylePresetCatalog.presetsFor(testCatalog, customSettings, StylePresetFamily.PHOTO)
        val selected = presets.filter { it.isSelected }
        assertEquals(1, selected.size)
        assertEquals("photo-vivid", selected.first().profileId)
    }

    @Test
    fun `presetsFor marks default humanistic profile as selected`() {
        val presets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.HUMANISTIC)
        val selected = presets.filter { it.isSelected }
        assertEquals(1, selected.size)
        assertEquals("humanistic-original", selected.first().profileId)
    }

    @Test
    fun `presetsFor marks default portrait profile as selected`() {
        val presets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.PORTRAIT)
        val selected = presets.filter { it.isSelected }
        assertEquals(1, selected.size)
        assertEquals("portrait-original", selected.first().profileId)
    }

    @Test
    fun `presetsFor exactly one preset is selected per family`() {
        StylePresetFamily.entries.forEach { family ->
            val presets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, family)
            val selectedCount = presets.count { it.isSelected }
            assertEquals(1, selectedCount, "Family ${family.name} should have exactly one selected preset")
        }
    }

    // --- buildPresetLibrary ---

    @Test
    fun `buildPresetLibrary returns entry for every family`() {
        val library = StylePresetCatalog.buildPresetLibrary(testCatalog, defaultSettings)
        assertEquals(StylePresetFamily.entries.size, library.size)
        StylePresetFamily.entries.forEach { family ->
            assertTrue(library.containsKey(family))
        }
    }

    @Test
    fun `buildPresetLibrary each family has at least one preset`() {
        val library = StylePresetCatalog.buildPresetLibrary(testCatalog, defaultSettings)
        library.forEach { (family, presets) ->
            assertTrue(presets.isNotEmpty(), "Family ${family.name} should have presets")
        }
    }

    // --- selectedPreset ---

    @Test
    fun `selectedPreset returns the selected preset for PHOTO family`() {
        val selected = StylePresetCatalog.selectedPreset(testCatalog, defaultSettings, StylePresetFamily.PHOTO)
        assertNotNull(selected)
        assertEquals("photo-original", selected.profileId)
        assertTrue(selected.isSelected)
    }

    @Test
    fun `selectedPreset returns null when selected id not in catalog`() {
        val missingSettings = PersistedSettings(
            photo = PhotoSettings(defaultFilterProfileId = "nonexistent-id")
        )
        val selected = StylePresetCatalog.selectedPreset(testCatalog, missingSettings, StylePresetFamily.PHOTO)
        assertNull(selected)
    }

    // --- previewForProfile ---

    @Test
    fun `previewForProfile returns preview for known profile`() {
        val preview = StylePresetCatalog.previewForProfile(testCatalog, "photo-vivid")
        assertNotNull(preview)
        assertEquals(PreviewTier.NEUTRAL, preview.contrastTier)
    }

    @Test
    fun `previewForProfile returns null for unknown profile`() {
        assertNull(StylePresetCatalog.previewForProfile(testCatalog, "nonexistent"))
    }

    // --- preview descriptor stability ---

    @Test
    fun `previewForProfile is deterministic across calls`() {
        val first = StylePresetCatalog.previewForProfile(testCatalog, "photo-vivid")
        val second = StylePresetCatalog.previewForProfile(testCatalog, "photo-vivid")
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(first, second)
    }

    @Test
    fun `preview for B&W profile has high monochrome level`() {
        val preview = StylePresetCatalog.previewForProfile(testCatalog, "photo-bw")
        assertNotNull(preview)
        assertEquals(1.0f, preview.monochromeLevel)
        assertEquals("B&W", preview.derivedMoodLabel)
    }

    @Test
    fun `preview for original profile has neutral tiers`() {
        val preview = StylePresetCatalog.previewForProfile(testCatalog, "photo-original")
        assertNotNull(preview)
        assertEquals(PreviewTier.NEUTRAL, preview.contrastTier)
        assertEquals(PreviewTier.NEUTRAL, preview.brightnessTier)
        assertEquals(PreviewWarmth.NEUTRAL, preview.warmthDirection)
        assertEquals("Natural", preview.derivedMoodLabel)
    }

    @Test
    fun `preview for rich profile shows warm direction`() {
        val preview = StylePresetCatalog.previewForProfile(testCatalog, "photo-rich")
        assertNotNull(preview)
        assertEquals(PreviewWarmth.WARM, preview.warmthDirection)
    }

    @Test
    fun `preview for chasing-light profile shows cool direction`() {
        val preview = StylePresetCatalog.previewForProfile(testCatalog, "photo-chasing-light")
        assertNotNull(preview)
        assertEquals(PreviewWarmth.COOL, preview.warmthDirection)
    }

    // --- preview for default spec (no renderSpec) ---

    @Test
    fun `preview uses neutral defaults when renderSpec is null`() {
        val catalog = FeatureCatalog(
            filterProfiles = listOf(
                FilterProfile(
                    id = "test-null-spec",
                    label = "Null Spec",
                    category = FilterProfileCategory.PHOTO,
                    renderSpec = null
                )
            )
        )
        val presets = StylePresetCatalog.presetsFor(catalog, defaultSettings, StylePresetFamily.PHOTO)
        assertEquals(1, presets.size)
        val preview = presets.first().preview
        assertEquals(0f, preview.monochromeLevel)
        assertEquals(PreviewTier.NEUTRAL, preview.contrastTier)
        assertEquals("Natural", preview.derivedMoodLabel)
    }

    // --- applyAction ---

    @Test
    fun `applyAction for PHOTO family is UpdatePhotoFilter`() {
        val presets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.PHOTO)
        presets.forEach { preset ->
            assertTrue(preset.applyAction is PersistedSettingsAction.UpdatePhotoFilter)
            assertEquals(preset.profileId, (preset.applyAction as PersistedSettingsAction.UpdatePhotoFilter).filterProfileId)
        }
    }

    @Test
    fun `applyAction for HUMANISTIC family is UpdateHumanisticFilter`() {
        val presets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.HUMANISTIC)
        presets.forEach { preset ->
            assertTrue(preset.applyAction is PersistedSettingsAction.UpdateHumanisticFilter)
        }
    }

    @Test
    fun `applyAction for PORTRAIT family is UpdatePortraitFilter`() {
        val presets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.PORTRAIT)
        presets.forEach { preset ->
            assertTrue(preset.applyAction is PersistedSettingsAction.UpdatePortraitFilter)
        }
    }

    @Test
    fun `applyAction for VIDEO family is UpdateVideoFilter`() {
        val presets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.VIDEO)
        presets.forEach { preset ->
            assertTrue(preset.applyAction is PersistedSettingsAction.UpdateVideoFilter)
        }
    }

    @Test
    fun `applyAction produces correct state change through reducer`() {
        val presets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.PHOTO)
        val vividPreset = presets.first { it.profileId == "photo-vivid" }
        val reduced = defaultSettings.reduce(vividPreset.applyAction)
        assertEquals("photo-vivid", reduced.photo.defaultFilterProfileId)
    }

    // --- availableFamilies ---

    @Test
    fun `availableFamilies returns families with profiles`() {
        val families = StylePresetCatalog.availableFamilies(testCatalog)
        assertTrue(families.isNotEmpty())
        assertTrue(StylePresetFamily.PHOTO in families)
        assertTrue(StylePresetFamily.PORTRAIT in families)
        assertTrue(StylePresetFamily.HUMANISTIC in families)
    }

    @Test
    fun `availableFamilies includes VIDEO when PHOTO profiles exist`() {
        val families = StylePresetCatalog.availableFamilies(testCatalog)
        assertTrue(StylePresetFamily.VIDEO in families, "VIDEO family shares PHOTO profiles")
    }

    @Test
    fun `availableFamilies includes CHECK_IN when PHOTO profiles exist`() {
        val families = StylePresetCatalog.availableFamilies(testCatalog)
        assertTrue(StylePresetFamily.CHECK_IN in families, "CHECK_IN family shares PHOTO profiles")
    }

    // --- totalPresetCount ---

    @Test
    fun `totalPresetCount equals catalog filterProfiles size`() {
        assertEquals(
            DEFAULT_FILTER_PROFILES.size,
            StylePresetCatalog.totalPresetCount(testCatalog)
        )
    }

    @Test
    fun `totalPresetCount for empty catalog is zero`() {
        val emptyCatalog = FeatureCatalog(filterProfiles = emptyList())
        assertEquals(0, StylePresetCatalog.totalPresetCount(emptyCatalog))
    }

    // --- CHECK_IN shares PHOTO presets ---

    @Test
    fun `CHECK_IN family has same presets as PHOTO family`() {
        val photoPresets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.PHOTO)
        val checkInPresets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.CHECK_IN)
        assertEquals(photoPresets.size, checkInPresets.size)
        photoPresets.zip(checkInPresets).forEach { (photo, checkIn) ->
            assertEquals(photo.profileId, checkIn.profileId)
            assertEquals(photo.label, checkIn.label)
        }
    }

    // --- VIDEO shares PHOTO presets ---

    @Test
    fun `VIDEO family has same presets as PHOTO family`() {
        val photoPresets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.PHOTO)
        val videoPresets = StylePresetCatalog.presetsFor(testCatalog, defaultSettings, StylePresetFamily.VIDEO)
        assertEquals(photoPresets.size, videoPresets.size)
        photoPresets.zip(videoPresets).forEach { (photo, video) ->
            assertEquals(photo.profileId, video.profileId)
        }
    }

    // --- mood label derivation ---

    @Test
    fun `mood label vivid for high saturation`() {
        val preview = FilterRenderSpec(saturation = 1.2f).toStylePresetPreview()
        assertEquals("Vivid", preview.derivedMoodLabel)
    }

    @Test
    fun `mood label film for grain`() {
        val preview = FilterRenderSpec(grainStrength = 0.1f).toStylePresetPreview()
        assertEquals("Film", preview.derivedMoodLabel)
    }

    @Test
    fun `mood label combines vivid and warm`() {
        val preview = FilterRenderSpec(saturation = 1.2f, warmthShift = 5).toStylePresetPreview()
        assertEquals("Vivid Warm", preview.derivedMoodLabel)
    }

    @Test
    fun `mood label monochrome for high monochromeMix`() {
        val preview = FilterRenderSpec(monochromeMix = 0.8f).toStylePresetPreview()
        assertEquals("Monochrome", preview.derivedMoodLabel)
    }

    @Test
    fun `mood label punchy for high contrast`() {
        val preview = FilterRenderSpec(contrast = 1.2f).toStylePresetPreview()
        assertEquals("Punchy", preview.derivedMoodLabel)
    }

    @Test
    fun `mood label soft for low contrast`() {
        val preview = FilterRenderSpec(contrast = 0.9f).toStylePresetPreview()
        assertEquals("Soft", preview.derivedMoodLabel)
    }

    // --- preset does not duplicate persisted selection ---

    @Test
    fun `StylePreset is a projection, not a source of truth`() {
        val settings1 = PersistedSettings(photo = PhotoSettings(defaultFilterProfileId = "photo-vivid"))
        val settings2 = PersistedSettings(photo = PhotoSettings(defaultFilterProfileId = "photo-bw"))
        val presets1 = StylePresetCatalog.presetsFor(testCatalog, settings1, StylePresetFamily.PHOTO)
        val presets2 = StylePresetCatalog.presetsFor(testCatalog, settings2, StylePresetFamily.PHOTO)
        assertEquals(presets1.size, presets2.size)
        presets1.zip(presets2).forEach { (p1, p2) ->
            assertEquals(p1.profileId, p2.profileId)
            assertEquals(p1.label, p2.label)
        }
        assertTrue(presets1.first { it.profileId == "photo-vivid" }.isSelected)
        assertFalse(presets1.first { it.profileId == "photo-bw" }.isSelected)
        assertTrue(presets2.first { it.profileId == "photo-bw" }.isSelected)
        assertFalse(presets2.first { it.profileId == "photo-vivid" }.isSelected)
    }

    // --- CHECK_IN selected state uses PHOTO filter field ---

    @Test
    fun `CHECK_IN family selected state reflects photo filter setting`() {
        val settings = PersistedSettings(
            photo = PhotoSettings(defaultFilterProfileId = "photo-texture")
        )
        val presets = StylePresetCatalog.presetsFor(testCatalog, settings, StylePresetFamily.CHECK_IN)
        val selected = presets.firstOrNull { it.isSelected }
        assertNotNull(selected)
        assertEquals("photo-texture", selected.profileId)
    }

    // --- VIDEO selected state uses video filter field ---

    @Test
    fun `VIDEO family selected state reflects video filter setting`() {
        val settings = PersistedSettings(
            video = VideoSettings(defaultFilterProfileId = "photo-vivid")
        )
        val presets = StylePresetCatalog.presetsFor(testCatalog, settings, StylePresetFamily.VIDEO)
        val selected = presets.firstOrNull { it.isSelected }
        assertNotNull(selected)
        assertEquals("photo-vivid", selected.profileId)
    }
}
