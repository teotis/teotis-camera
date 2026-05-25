package com.opencamera.core.effect

import com.opencamera.core.settings.PortraitBeautyPreset
import com.opencamera.core.settings.PortraitBeautyStrength
import com.opencamera.core.settings.PortraitBokehEffect
import com.opencamera.core.settings.PortraitProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortraitLayeredEffectResolverTest {

    @Test
    fun `depth path resolves successfully`() {
        val spec = PortraitLayeredEffectResolver.resolve(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.NATURAL
        )
        assertNotNull(spec)
        assertEquals("depth", spec.renderPath)
        assertEquals(PortraitProfile.NATIVE, spec.portraitProfile)
    }

    @Test
    fun `focus path resolves successfully`() {
        val spec = PortraitLayeredEffectResolver.resolve(
            renderPath = "focus",
            bokehStrength = null,
            subjectTracking = false,
            portraitProfile = PortraitProfile.LUMINOUS,
            beautyPreset = PortraitBeautyPreset.RADIANT,
            beautyStrength = PortraitBeautyStrength.SOFT,
            bokehEffect = PortraitBokehEffect.CREAMY
        )
        assertNotNull(spec)
        assertEquals("focus", spec.renderPath)
    }

    @Test
    fun `unsupported render path returns null`() {
        val spec = PortraitLayeredEffectResolver.resolve(
            renderPath = "unknown",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.AUTHENTIC,
            beautyStrength = PortraitBeautyStrength.OFF,
            bokehEffect = PortraitBokehEffect.NATURAL
        )
        assertNull(spec)
    }

    @Test
    fun `profile changes subject and background`() {
        val native = PortraitLayeredEffectResolver.resolve(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.CREAMY
        )!!
        val luminous = PortraitLayeredEffectResolver.resolve(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.LUMINOUS,
            beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.CREAMY
        )!!

        assertTrue(luminous.subjectBeauty.lift > native.subjectBeauty.lift)
        assertTrue(luminous.backgroundLightSpot.highlightBloom >= native.backgroundLightSpot.highlightBloom)
        assertTrue(luminous.backgroundBokeh.vignetteStrength > native.backgroundBokeh.vignetteStrength)
        assertTrue(luminous.strength > native.strength)
    }

    @Test
    fun `beauty preset affects only subject beauty`() {
        val authentic = PortraitLayeredEffectResolver.resolve(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.AUTHENTIC,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.NATURAL
        )!!
        val radiant = PortraitLayeredEffectResolver.resolve(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.RADIANT,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.NATURAL
        )!!

        assertTrue(radiant.subjectBeauty.smoothing > authentic.subjectBeauty.smoothing)
        assertTrue(radiant.subjectBeauty.lift > authentic.subjectBeauty.lift)
        // Background bokeh unchanged
        assertEquals(authentic.backgroundBokeh.blurScale, radiant.backgroundBokeh.blurScale)
        assertEquals(authentic.backgroundBokeh.edgeSoftness, radiant.backgroundBokeh.edgeSoftness)
    }

    @Test
    fun `bokeh effect affects only background fields`() {
        val natural = PortraitLayeredEffectResolver.resolve(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.NATURAL
        )!!
        val dreamy = PortraitLayeredEffectResolver.resolve(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.DREAMY
        )!!

        // Background differs
        assertTrue(dreamy.backgroundBokeh.edgeSoftness > natural.backgroundBokeh.edgeSoftness)
        // Subject beauty identical
        assertEquals(natural.subjectBeauty.smoothing, dreamy.subjectBeauty.smoothing)
        assertEquals(natural.subjectBeauty.lift, dreamy.subjectBeauty.lift)
        assertEquals(natural.subjectBeauty.saturationBoost, dreamy.subjectBeauty.saturationBoost)
    }

    @Test
    fun `beauty strength OFF zeroes subject beauty`() {
        val off = PortraitLayeredEffectResolver.resolve(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.RADIANT,
            beautyStrength = PortraitBeautyStrength.OFF,
            bokehEffect = PortraitBokehEffect.NATURAL
        )!!

        assertEquals(0f, off.subjectBeauty.smoothing)
        assertEquals(0f, off.subjectBeauty.saturationBoost)
    }
}
