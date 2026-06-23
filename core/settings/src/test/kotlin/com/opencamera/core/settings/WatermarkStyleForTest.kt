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

    @Test
    fun `watermarkStyleFor night-street returns nightStreetWatermarkStyle`() {
        val settings = PhotoSettings(
            nightStreetWatermarkStyle = WatermarkStyleSettings(
                textPlacement = WatermarkTextPlacement.BOTTOM_RIGHT,
                textScale = WatermarkTextScale.LARGE,
                textOpacity = WatermarkTextOpacity.SUBTLE,
                frameBackground = WatermarkFrameBackground.SOURCE_BLUR
            )
        )
        val style = settings.watermarkStyleFor("night-street")
        assertEquals(WatermarkTextPlacement.BOTTOM_RIGHT, style.textPlacement)
        assertEquals(WatermarkTextScale.LARGE, style.textScale)
        assertEquals(WatermarkTextOpacity.SUBTLE, style.textOpacity)
        assertEquals(WatermarkFrameBackground.SOURCE_BLUR, style.frameBackground)
    }

    @Test
    fun `watermarkStyleFor van-gogh-starry returns dedicated starry style`() {
        val settings = PhotoSettings(
            nightStreetWatermarkStyle = WatermarkStyleSettings(
                textPlacement = WatermarkTextPlacement.BOTTOM_RIGHT,
                textOpacity = WatermarkTextOpacity.SUBTLE
            ),
            vanGoghStarryWatermarkStyle = WatermarkStyleSettings(
                textPlacement = WatermarkTextPlacement.BOTTOM_CENTER,
                textOpacity = WatermarkTextOpacity.SOLID,
                frameBackground = WatermarkFrameBackground.DARK
            )
        )

        val style = settings.watermarkStyleFor("van-gogh-starry")

        assertEquals(WatermarkTextPlacement.BOTTOM_CENTER, style.textPlacement)
        assertEquals(WatermarkTextOpacity.SOLID, style.textOpacity)
        assertEquals(WatermarkFrameBackground.DARK, style.frameBackground)
    }

    @Test
    fun `watermarkStyleFor blue-hour returns dedicated blue hour style`() {
        val settings = PhotoSettings(
            nightStreetWatermarkStyle = WatermarkStyleSettings(
                textPlacement = WatermarkTextPlacement.BOTTOM_RIGHT,
                textOpacity = WatermarkTextOpacity.SUBTLE
            ),
            blueHourWatermarkStyle = WatermarkStyleSettings(
                textPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
                textOpacity = WatermarkTextOpacity.SOLID,
                frameBackground = WatermarkFrameBackground.DARK
            )
        )

        val style = settings.watermarkStyleFor("blue-hour")

        assertEquals(WatermarkTextPlacement.BOTTOM_LEFT, style.textPlacement)
        assertEquals(WatermarkTextOpacity.SOLID, style.textOpacity)
        assertEquals(WatermarkFrameBackground.DARK, style.frameBackground)
    }

    @Test
    fun `watermarkStyleFor professional-bottom-bar returns professionalBottomBarWatermarkStyle`() {
        val settings = PhotoSettings(
            professionalBottomBarWatermarkStyle = WatermarkStyleSettings(
                textPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
                frameBackground = WatermarkFrameBackground.WHITE
            )
        )
        val style = settings.watermarkStyleFor("professional-bottom-bar")
        assertEquals(WatermarkTextPlacement.BOTTOM_LEFT, style.textPlacement)
        assertEquals(WatermarkFrameBackground.WHITE, style.frameBackground)
    }
}
