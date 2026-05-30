// 覆盖行为:
// - ManualCaptureParams.compactSummary() 格式化输出
// - ManualCaptureParams.toMetadataTags() 默认/自定义前缀
// - FilterRenderSpec.toMetadataTags() + parseFilterRenderSpec() round-trip
// - parseFilterRenderSpec 缺少 version 标签时返回 null
// - parseFilterRenderSpec 部分字段缺失时使用默认值
// - 自定义 prefix 的 round-trip
//
// 暂时不适合单测:
// - 无（纯数据转换函数，完全可测）

package com.opencamera.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsMetadataCodecsTest {

    // --- ManualCaptureParams.compactSummary ---

    @Test
    fun `compactSummary with all null fields shows Auto everywhere`() {
        val params = ManualCaptureParams(rawEnabled = false)
        val summary = params.compactSummary()

        assertTrue(summary.contains("RAW Off"))
        assertTrue(summary.contains("ISO Auto"))
        assertTrue(summary.contains("S Auto"))
        assertTrue(summary.contains("WB Auto"))
    }

    @Test
    fun `compactSummary with all fields set shows values`() {
        val params = ManualCaptureParams(
            rawEnabled = true,
            iso = 1600,
            shutterSpeedMillis = 100L,
            whiteBalanceKelvin = 4500
        )
        val summary = params.compactSummary()

        assertTrue(summary.contains("RAW On"))
        assertTrue(summary.contains("ISO 1600"))
        assertTrue(summary.contains("S 100ms"))
        assertTrue(summary.contains("WB 4500K"))
    }

    // --- ManualCaptureParams.toMetadataTags ---

    @Test
    fun `toMetadataTags default prefix produces manualDraft keys`() {
        val params = ManualCaptureParams(rawEnabled = true, iso = 400)
        val tags = params.toMetadataTags()

        assertEquals("on", tags["manualDraftRaw"])
        assertEquals("400", tags["manualDraftIso"])
        assertEquals("auto", tags["manualDraftShutterSpeedMillis"])
        assertEquals(7, tags.size)
    }

    @Test
    fun `toMetadataTags custom prefix produces prefixed keys`() {
        val params = ManualCaptureParams(rawEnabled = false)
        val tags = params.toMetadataTags(prefix = "draft2")

        assertEquals("off", tags["draft2Raw"])
        assertEquals("auto", tags["draft2Iso"])
    }

    // --- FilterRenderSpec metadata round-trip ---

    @Test
    fun `FilterRenderSpec toMetadataTags round trip with defaults`() {
        val spec = FilterRenderSpec()
        val tags = spec.toMetadataTags()
        val parsed = parseFilterRenderSpec(tags)

        assertNotNull(parsed)
        assertEquals(spec, parsed)
    }

    @Test
    fun `FilterRenderSpec toMetadataTags round trip with non-default values`() {
        val spec = FilterRenderSpec(
            brightnessShift = 12,
            contrast = 1.25f,
            saturation = 0.8f,
            warmthShift = -5,
            tintShift = 3,
            monochromeMix = 0.5f,
            vignetteStrength = 0.2f,
            softGlowStrength = 0.15f,
            haloStrength = 0.1f,
            grainStrength = 0.08f,
            sharpnessBoost = 0.3f,
            highlightCompression = 0.22f,
            shadowLift = 0.18f,
            warmBoost = 0.14f,
            coolBoost = 0.06f
        )

        val tags = spec.toMetadataTags()
        val parsed = parseFilterRenderSpec(tags)

        assertNotNull(parsed)
        assertEquals(spec, parsed)
    }

    @Test
    fun `parseFilterRenderSpec returns null when version tag missing`() {
        val tags = mapOf(
            "filterSpec.brightnessShift" to "5",
            "filterSpec.contrast" to "1.1f"
        )
        assertNull(parseFilterRenderSpec(tags))
    }

    @Test
    fun `parseFilterRenderSpec uses defaults for missing field tags`() {
        val tags = mapOf(
            "filterSpec.version" to "1",
            "filterSpec.brightnessShift" to "10"
        )
        val parsed = parseFilterRenderSpec(tags)

        assertNotNull(parsed)
        assertEquals(10, parsed.brightnessShift)
        assertEquals(1f, parsed.contrast)
        assertEquals(1f, parsed.saturation)
        assertEquals(0, parsed.warmthShift)
    }

    @Test
    fun `parseFilterRenderSpec with custom prefix`() {
        val spec = FilterRenderSpec(brightnessShift = 7, contrast = 1.1f)
        val tags = spec.toMetadataTags(prefix = "customSpec")

        assertEquals("1", tags["customSpec.version"])
        assertEquals("7", tags["customSpec.brightnessShift"])

        val parsed = parseFilterRenderSpec(tags, prefix = "customSpec")
        assertNotNull(parsed)
        assertEquals(spec, parsed)
    }

    @Test
    fun `FilterRenderSpec metadata tags include version key`() {
        val tags = FilterRenderSpec().toMetadataTags()
        assertEquals("1", tags["filterSpec.version"])
    }
}
