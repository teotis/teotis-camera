package com.opencamera.app.camera

import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.FocusStackFrameRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FocusStackManualFocusPlannerTest {
    @Test
    fun `near focus uses nearest supported distance for preferred lens`() {
        val profiles = listOf(
            CameraLensProfile(
                lensFacing = LensFacing.BACK,
                hasFlashUnit = true,
                minimumFocusDistanceDiopters = 9.5f
            ),
            CameraLensProfile(
                lensFacing = LensFacing.FRONT,
                hasFlashUnit = false,
                minimumFocusDistanceDiopters = 3.0f
            )
        )

        val distance = focusStackManualFocusDistance(
            role = FocusStackFrameRole.NEAR,
            cameraProfiles = profiles,
            preferredLensFacing = LensFacing.BACK
        )

        assertEquals(9.5f, distance)
    }

    @Test
    fun `near focus falls back when no profile exposes minimum focus distance`() {
        val distance = focusStackManualFocusDistance(
            role = FocusStackFrameRole.NEAR,
            cameraProfiles = listOf(
                CameraLensProfile(lensFacing = LensFacing.BACK, hasFlashUnit = true)
            ),
            preferredLensFacing = LensFacing.BACK
        )

        assertEquals(DEFAULT_FOCUS_STACK_NEAR_FOCUS_DISTANCE_DIOPTERS, distance)
    }

    @Test
    fun `far focus requests infinity focus`() {
        val distance = focusStackManualFocusDistance(
            role = FocusStackFrameRole.FAR,
            cameraProfiles = emptyList(),
            preferredLensFacing = null
        )

        assertEquals(FOCUS_STACK_FAR_FOCUS_DISTANCE_DIOPTERS, distance)
    }

    @Test
    fun `none role does not request manual focus`() {
        assertNull(
            focusStackManualFocusDistance(
                role = FocusStackFrameRole.NONE,
                cameraProfiles = emptyList(),
                preferredLensFacing = null
            )
        )
    }
}
