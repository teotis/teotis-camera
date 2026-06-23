package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.WatermarkStyleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private data class FakeEffectCapabilityQuery(
    val portraitDepth: Boolean = true,
    val documentGeometry: Boolean = true,
    val manualControls: Boolean = true
) : EffectCapabilityQuery {
    override fun supportsPortraitDepth(): Boolean = portraitDepth
    override fun supportsDocumentGeometry(): Boolean = documentGeometry
    override fun supportsManualControls(): Boolean = manualControls
}

class EffectCapabilityResolverTest {

    @Test
    fun `empty spec returns empty report`() {
        val resolver = EffectCapabilityResolver(FakeEffectCapabilityQuery())
        val report = resolver.resolve(EffectSpec.EMPTY)

        assertTrue(report.results.isEmpty())
        assertEquals(EffectSpec.EMPTY, report.effectiveSpec)
    }

    @Test
    fun `filter effect is always supported`() {
        val filter = FilterEffect(profileId = "vivid", renderSpec = null)
        val spec = EffectSpec(listOf(filter))
        val resolver = EffectCapabilityResolver(FakeEffectCapabilityQuery())

        val report = resolver.resolve(spec)

        assertEquals(1, report.results.size)
        assertEquals(EffectSupport.SUPPORTED, report.results[0].support)
        assertEquals(filter, report.results[0].entry)
    }

    @Test
    fun `watermark effect is always supported`() {
        val watermark = WatermarkEffect(
            templateId = "classic",
            tokens = mapOf("title" to "Test"),
            style = WatermarkStyleSettings()
        )
        val spec = EffectSpec(listOf(watermark))
        val resolver = EffectCapabilityResolver(FakeEffectCapabilityQuery())

        val report = resolver.resolve(spec)

        assertEquals(1, report.results.size)
        assertEquals(EffectSupport.SUPPORTED, report.results[0].support)
        assertEquals(watermark, report.results[0].entry)
    }

    @Test
    fun `portrait effect degraded when depth not supported`() {
        val portrait = PortraitEffect(
            profileId = "bokeh",
            renderPath = "depth",
            beautyPreset = "natural",
            beautyStrength = "balanced",
            bokehEffect = "circle"
        )
        val spec = EffectSpec(listOf(portrait))
        val resolver = EffectCapabilityResolver(
            FakeEffectCapabilityQuery(portraitDepth = false)
        )

        val report = resolver.resolve(spec)

        assertEquals(1, report.results.size)
        assertEquals(EffectSupport.DEGRADED, report.results[0].support)
        val degraded = report.results[0].entry as PortraitEffect
        assertEquals("focus", degraded.renderPath)
    }

    @Test
    fun `portrait effect supported when depth supported`() {
        val portrait = PortraitEffect(
            profileId = "bokeh",
            renderPath = "depth",
            beautyPreset = "natural",
            beautyStrength = "balanced",
            bokehEffect = "circle"
        )
        val spec = EffectSpec(listOf(portrait))
        val resolver = EffectCapabilityResolver(FakeEffectCapabilityQuery())

        val report = resolver.resolve(spec)

        assertEquals(1, report.results.size)
        assertEquals(EffectSupport.SUPPORTED, report.results[0].support)
        assertEquals(portrait, report.results[0].entry)
    }

    @Test
    fun `document effect degraded when scan not supported`() {
        val document = DocumentEffect(autoCrop = true, contrastProfile = "high")
        val spec = EffectSpec(listOf(document))
        val resolver = EffectCapabilityResolver(
            FakeEffectCapabilityQuery(documentGeometry = false)
        )

        val report = resolver.resolve(spec)

        assertEquals(1, report.results.size)
        assertEquals(EffectSupport.DEGRADED, report.results[0].support)
        val degraded = report.results[0].entry as DocumentEffect
        assertEquals(false, degraded.autoCrop)
        assertNull(degraded.contrastProfile)
    }

    @Test
    fun `document effect degraded resets colorMode to null and scanGuide to false`() {
        val document = DocumentEffect(
            autoCrop = true,
            contrastProfile = "high",
            colorMode = DocumentColorMode.BLACK_AND_WHITE,
            scanGuide = true
        )
        val spec = EffectSpec(listOf(document))
        val resolver = EffectCapabilityResolver(
            FakeEffectCapabilityQuery(documentGeometry = false)
        )

        val report = resolver.resolve(spec)

        assertEquals(EffectSupport.DEGRADED, report.results[0].support)
        val degraded = report.results[0].entry as DocumentEffect
        assertNull(degraded.colorMode)
        assertFalse(degraded.scanGuide)
        assertEquals(false, degraded.autoCrop)
        assertNull(degraded.contrastProfile)
    }

    @Test
    fun `document effect supported preserves colorMode and scanGuide`() {
        val document = DocumentEffect(
            autoCrop = true,
            contrastProfile = "high",
            colorMode = DocumentColorMode.COLOR_ENHANCED,
            scanGuide = true
        )
        val spec = EffectSpec(listOf(document))
        val resolver = EffectCapabilityResolver(FakeEffectCapabilityQuery())

        val report = resolver.resolve(spec)

        assertEquals(EffectSupport.SUPPORTED, report.results[0].support)
        val entry = report.results[0].entry as DocumentEffect
        assertEquals(DocumentColorMode.COLOR_ENHANCED, entry.colorMode)
        assertTrue(entry.scanGuide)
    }

    @Test
    fun `mixed spec resolves each entry independently`() {
        val filter = FilterEffect(profileId = "vivid", renderSpec = null)
        val portrait = PortraitEffect(
            profileId = "bokeh",
            renderPath = "depth",
            beautyPreset = "natural",
            beautyStrength = "balanced",
            bokehEffect = "circle"
        )
        val document = DocumentEffect(autoCrop = true, contrastProfile = "high")
        val spec = EffectSpec(listOf(filter, portrait, document))
        val resolver = EffectCapabilityResolver(
            FakeEffectCapabilityQuery(portraitDepth = false, documentGeometry = false)
        )

        val report = resolver.resolve(spec)

        assertEquals(3, report.results.size)
        // filter: supported
        assertEquals(EffectSupport.SUPPORTED, report.results[0].support)
        // portrait: degraded
        assertEquals(EffectSupport.DEGRADED, report.results[1].support)
        assertEquals("focus", (report.results[1].entry as PortraitEffect).renderPath)
        // document: degraded
        assertEquals(EffectSupport.DEGRADED, report.results[2].support)
        assertNull((report.results[2].entry as DocumentEffect).contrastProfile)
        // effective spec contains all 3 (none are UNSUPPORTED)
        assertEquals(3, report.effectiveSpec.entries.size)
    }
}
