// 覆盖行为:
// - defaultFilterRenderSpecOrNull 查找已知 profile 返回非 null spec
// - defaultFilterRenderSpecOrNull 查找未知 profile 返回 null
// - DEFAULT_FILTER_PROFILES 非空、无重复 id、每个 profile 有非 null renderSpec
// - DEFAULT_FILTER_PROFILES 包含所有预期的分类
// - DEFAULT_WATERMARK_TEMPLATES 非空、无重复 id
// - DEFAULT_WATERMARK_TEMPLATES 各 kind 有正确的 allowedPlacements 约束
//
// 暂时不适合单测:
// - 无（纯数据常量验证，完全可测）

package com.opencamera.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsDefaultsTest {

    // --- defaultFilterRenderSpecOrNull ---

    @Test
    fun `defaultFilterRenderSpecOrNull returns spec for known profile id`() {
        val spec = defaultFilterRenderSpecOrNull("photo-vivid")
        assertNotNull(spec)
        assertEquals(1.08f, spec.contrast)
        assertEquals(1.14f, spec.saturation)
    }

    @Test
    fun `defaultFilterRenderSpecOrNull returns null for unknown profile id`() {
        assertNull(defaultFilterRenderSpecOrNull("nonexistent-profile"))
    }

    @Test
    fun `defaultFilterRenderSpecOrNull returns spec for portrait profile`() {
        val spec = defaultFilterRenderSpecOrNull("portrait-blue")
        assertNotNull(spec)
        assertEquals(2, spec.brightnessShift)
        assertEquals(1.06f, spec.contrast)
    }

    // --- DEFAULT_FILTER_PROFILES structure ---

    @Test
    fun `DEFAULT_FILTER_PROFILES is non-empty`() {
        assertTrue(DEFAULT_FILTER_PROFILES.isNotEmpty())
    }

    @Test
    fun `DEFAULT_FILTER_PROFILES has no duplicate ids`() {
        val ids = DEFAULT_FILTER_PROFILES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `all default filter profiles have non-null renderSpec`() {
        DEFAULT_FILTER_PROFILES.forEach { profile ->
            assertNotNull(profile.renderSpec, "Profile ${profile.id} should have a non-null renderSpec")
        }
    }

    @Test
    fun `all default filter profiles are marked as builtIn`() {
        DEFAULT_FILTER_PROFILES.forEach { profile ->
            assertTrue(profile.builtIn, "Profile ${profile.id} should be builtIn")
        }
    }

    @Test
    fun `DEFAULT_FILTER_PROFILES covers all non-custom categories`() {
        val categories = DEFAULT_FILTER_PROFILES.map { it.category }.toSet()
        assertTrue(FilterProfileCategory.PHOTO in categories)
        assertTrue(FilterProfileCategory.PORTRAIT in categories)
        assertTrue(FilterProfileCategory.HUMANISTIC in categories)
    }

    @Test
    fun `DEFAULT_FILTER_PROFILES has no custom category profiles`() {
        val customProfiles = DEFAULT_FILTER_PROFILES.filter {
            it.category == FilterProfileCategory.CUSTOM
        }
        assertTrue(customProfiles.isEmpty(), "Default profiles should not include CUSTOM category")
    }

    @Test
    fun `expected photo profile ids are present`() {
        val photoIds = DEFAULT_FILTER_PROFILES
            .filter { it.category == FilterProfileCategory.PHOTO }
            .map { it.id }
        assertTrue("photo-vivid" in photoIds)
        assertTrue("photo-original" in photoIds)
        assertTrue("photo-bw" in photoIds)
        assertTrue("photo-chasing-light" in photoIds)
        assertTrue("photo-rich" in photoIds)
        assertTrue("photo-texture" in photoIds)
    }

    // --- DEFAULT_WATERMARK_TEMPLATES structure ---

    @Test
    fun `DEFAULT_WATERMARK_TEMPLATES is non-empty`() {
        assertTrue(DEFAULT_WATERMARK_TEMPLATES.isNotEmpty())
    }

    @Test
    fun `DEFAULT_WATERMARK_TEMPLATES has no duplicate ids`() {
        val ids = DEFAULT_WATERMARK_TEMPLATES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `EXPANDED_FRAME templates restrict allowedPlacements to bottom row`() {
        val expandedFrame = DEFAULT_WATERMARK_TEMPLATES.filter {
            it.kind == WatermarkTemplateKind.EXPANDED_FRAME
        }
        assertTrue(expandedFrame.isNotEmpty())
        expandedFrame.forEach { template ->
            assertTrue(
                template.allowedPlacements.all {
                    it == WatermarkTextPlacement.BOTTOM_LEFT ||
                        it == WatermarkTextPlacement.BOTTOM_CENTER ||
                        it == WatermarkTextPlacement.BOTTOM_RIGHT
                },
                "EXPANDED_FRAME template ${template.id} should only allow bottom placements"
            )
        }
    }

    @Test
    fun `blur-four-border template has expected frame backgrounds`() {
        val template = DEFAULT_WATERMARK_TEMPLATES.first { it.id == "blur-four-border" }
        assertEquals(
            setOf(
                WatermarkFrameBackground.SOURCE_BLUR,
                WatermarkFrameBackground.SOURCE_LIGHT_BLUR,
                WatermarkFrameBackground.SOURCE_VIVID_BLUR
            ),
            template.allowedFrameBackgrounds
        )
    }

    @Test
    fun `pure-text template uses TEXT_OVERLAY kind`() {
        val template = DEFAULT_WATERMARK_TEMPLATES.first { it.id == "pure-text" }
        assertEquals(WatermarkTemplateKind.TEXT_OVERLAY, template.kind)
        assertEquals(false, template.supportsFrameBorder)
    }

    @Test
    fun `classic-overlay template has expected token keys`() {
        val template = DEFAULT_WATERMARK_TEMPLATES.first { it.id == "classic-overlay" }
        assertEquals(
            setOf("model", "datetime", "location", "camera-params"),
            template.tokenKeys
        )
    }
}
