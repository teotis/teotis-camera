package com.opencamera.app

import kotlin.test.Test
import kotlin.test.assertEquals

class FilterPaletteViewTest {
    @Test
    fun `center point maps to neutral axes`() {
        val axes = paletteAxesFromPoint(
            x = 50f,
            y = 25f,
            width = 100,
            height = 50
        )

        assertEquals(0f, axes.colorAxis)
        assertEquals(0f, axes.toneAxis)
    }

    @Test
    fun `touch near center snaps to neutral origin`() {
        val axes = paletteAxesFromPoint(
            x = 99.5f,
            y = 59.5f,
            width = 180,
            height = 100
        )

        assertEquals(0f, axes.colorAxis)
        assertEquals(0f, axes.toneAxis)
    }

    @Test
    fun `touch near visible grid dot snaps to its palette axes`() {
        val axes = paletteAxesFromPoint(
            x = 113f,
            y = 42f,
            width = 180,
            height = 100
        )

        assertEquals(2f / 9f, axes.colorAxis, 0.0001f)
        assertEquals(0.2f, axes.toneAxis, 0.0001f)
    }

    @Test
    fun `top edge maps to positive tone axis`() {
        val axes = paletteAxesFromPoint(
            x = 50f,
            y = 0f,
            width = 100,
            height = 50
        )

        assertEquals(0f, axes.colorAxis)
        assertEquals(1f, axes.toneAxis)
    }

    @Test
    fun `bottom edge maps to negative tone axis`() {
        val axes = paletteAxesFromPoint(
            x = 50f,
            y = 50f,
            width = 100,
            height = 50
        )

        assertEquals(0f, axes.colorAxis)
        assertEquals(-1f, axes.toneAxis)
    }

    @Test
    fun `left and right edges map to cool and warm color axes`() {
        val left = paletteAxesFromPoint(0f, 25f, 100, 50)
        val right = paletteAxesFromPoint(100f, 25f, 100, 50)

        assertEquals(-1f, left.colorAxis)
        assertEquals(1f, right.colorAxis)
        assertEquals(0f, left.toneAxis)
        assertEquals(0f, right.toneAxis)
    }

    @Test
    fun `reticle point inverts axes consistently`() {
        val point = palettePointFromAxes(
            colorAxis = -0.5f,
            toneAxis = 0.25f
        )

        assertEquals(0.25f, point.xFraction)
        assertEquals(0.375f, point.yFraction)
    }

    @Test
    fun `invalid view size returns neutral axes`() {
        val axes = paletteAxesFromPoint(
            x = 10f,
            y = 10f,
            width = 0,
            height = 0
        )

        assertEquals(0f, axes.colorAxis)
        assertEquals(0f, axes.toneAxis)
    }
}
