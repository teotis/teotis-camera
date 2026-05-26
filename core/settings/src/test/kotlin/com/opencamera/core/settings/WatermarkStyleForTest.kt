package com.opencamera.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class WatermarkStyleForTest {

    @Test
    fun `watermarkStyleFor classic-overlay returns classicOverlayWatermarkStyle`() {
        val settings = PhotoSettings(
            classicOverlayWatermarkStyle = WatermarkStyleSettings(
                textPlacement = WatermarkTextPlacement.TOP_RIGHT,
                textScale = WatermarkTextScale.LARGE
            )
        )
        val style = settings.watermarkStyleFor("classic-overlay")
        assertEquals(WatermarkTextPlacement.TOP_RIGHT, style.textPlacement)
        assertEquals(WatermarkTextScale.LARGE, style.textScale)
    }

    @Test
    fun `watermarkStyleFor unknown template falls back to classicOverlayWatermarkStyle`() {
        val settings = PhotoSettings(
            classicOverlayWatermarkStyle = WatermarkStyleSettings(
                textPlacement = WatermarkTextPlacement.BOTTOM_CENTER,
                textOpacity = WatermarkTextOpacity.SUBTLE
            )
        )
        val style = settings.watermarkStyleFor("nonexistent-template")
        assertEquals(WatermarkTextPlacement.BOTTOM_CENTER, style.textPlacement)
        assertEquals(WatermarkTextOpacity.SUBTLE, style.textOpacity)
    }

    @Test
    fun `watermarkStyleFor travel-polaroid returns travelPolaroidWatermarkStyle`() {
        val settings = PhotoSettings(
            travelPolaroidWatermarkStyle = WatermarkStyleSettings(
                textPlacement = WatermarkTextPlacement.TOP_LEFT,
                frameBackground = WatermarkFrameBackground.WHITE
            )
        )
        val style = settings.watermarkStyleFor("travel-polaroid")
        assertEquals(WatermarkTextPlacement.TOP_LEFT, style.textPlacement)
        assertEquals(WatermarkFrameBackground.WHITE, style.frameBackground)
    }

    @Test
    fun `watermarkStyleFor pure-text returns pureTextWatermarkStyle`() {
        val settings = PhotoSettings(
            pureTextWatermarkStyle = WatermarkStyleSettings(
                textScale = WatermarkTextScale.COMPACT,
                textOpacity = WatermarkTextOpacity.SOFT
            )
        )
        val style = settings.watermarkStyleFor("pure-text")
        assertEquals(WatermarkTextScale.COMPACT, style.textScale)
        assertEquals(WatermarkTextOpacity.SOFT, style.textOpacity)
    }

    @Test
    fun `watermarkStyleFor blur-four-border returns blurFourBorderWatermarkStyle`() {
        val settings = PhotoSettings(
            blurFourBorderWatermarkStyle = WatermarkStyleSettings(
                textPlacement = WatermarkTextPlacement.BOTTOM_CENTER,
                frameBackground = WatermarkFrameBackground.SOURCE_LIGHT_BLUR
            )
        )
        val style = settings.watermarkStyleFor("blur-four-border")
        assertEquals(WatermarkTextPlacement.BOTTOM_CENTER, style.textPlacement)
        assertEquals(WatermarkFrameBackground.SOURCE_LIGHT_BLUR, style.frameBackground)
    }
}
