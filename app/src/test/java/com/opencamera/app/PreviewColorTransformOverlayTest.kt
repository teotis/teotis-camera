package com.opencamera.app

import com.opencamera.core.effect.PreviewColorTransform
import com.opencamera.core.settings.PreviewColorFidelity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PreviewColorTransformOverlayTest {

    @Test
    fun `non-neutral color transform becomes drawable overlay`() {
        val overlay = previewColorTransformOverlaySpec(
            PreviewColorTransform(
                tintColor = 0xFFFF8844.toInt(),
                tintAlpha = 0.24f,
                fidelity = PreviewColorFidelity.APPROXIMATE
            )
        )

        assertNotNull(overlay)
        assertEquals(0xFFFF8844.toInt(), overlay.tintColor)
        assertEquals(0.24f, overlay.tintAlpha)
        assertEquals(0f, overlay.vignetteStrength)
        assertEquals(0f, overlay.warmthShift)
    }

    @Test
    fun `none color transform does not create overlay`() {
        assertNull(previewColorTransformOverlaySpec(PreviewColorTransform.NONE))
    }

    @Test
    fun `zero alpha color transform does not create overlay`() {
        val overlay = previewColorTransformOverlaySpec(
            PreviewColorTransform(
                tintColor = 0xFFFF8844.toInt(),
                tintAlpha = 0f,
                fidelity = PreviewColorFidelity.APPROXIMATE
            )
        )

        assertNull(overlay)
    }
}
