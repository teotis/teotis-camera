package com.opencamera.feature.photo

import com.opencamera.core.device.CameraExtensionMode
import com.opencamera.core.device.PhotoSceneSignal
import com.opencamera.core.device.SceneLightState
import kotlin.test.Test
import kotlin.test.assertEquals

class ExtensionPreferenceResolverTest {

    @Test
    fun `blue hour prefers HDR extension`() {
        val signal = PhotoSceneSignal(
            lightState = SceneLightState.BLUE_HOUR,
            brightnessScore = 0.25f,
            source = "preview-bitmap-metrics",
            averageLuma = 0.25f,
            blueCyanRatio = 0.45f,
            highlightRatio = 0.15f,
            confidence = 0.75f
        )
        assertEquals(CameraExtensionMode.HDR, ExtensionPreferenceResolver.resolvePreferredMode(signal))
    }

    @Test
    fun `low light prefers NIGHT extension`() {
        val signal = PhotoSceneSignal(
            lightState = SceneLightState.LOW_LIGHT,
            brightnessScore = 0.1f,
            source = "preview-bitmap-metrics"
        )
        assertEquals(CameraExtensionMode.NIGHT, ExtensionPreferenceResolver.resolvePreferredMode(signal))
    }

    @Test
    fun `normal uses no extension`() {
        val signal = PhotoSceneSignal(
            lightState = SceneLightState.NORMAL,
            brightnessScore = 0.6f,
            source = "preview-bitmap-metrics"
        )
        assertEquals(CameraExtensionMode.NONE, ExtensionPreferenceResolver.resolvePreferredMode(signal))
    }

    @Test
    fun `unknown uses no extension`() {
        val signal = PhotoSceneSignal(lightState = SceneLightState.UNKNOWN)
        assertEquals(CameraExtensionMode.NONE, ExtensionPreferenceResolver.resolvePreferredMode(signal))
    }

    @Test
    fun `routing note contains scene state and extension preference`() {
        val signal = PhotoSceneSignal(
            lightState = SceneLightState.BLUE_HOUR,
            confidence = 0.8f
        )
        val note = ExtensionPreferenceResolver.routingNote(signal)
        assertEquals("scene=blue_hour,conf=0.8,ext-preferred=hdr", note)
    }

    @Test
    fun `routing note with null confidence shows unknown`() {
        val signal = PhotoSceneSignal(lightState = SceneLightState.LOW_LIGHT)
        val note = ExtensionPreferenceResolver.routingNote(signal)
        assertEquals("scene=low_light,conf=unknown,ext-preferred=night", note)
    }

    @Test
    fun `routing note for normal scene`() {
        val signal = PhotoSceneSignal(
            lightState = SceneLightState.NORMAL,
            confidence = 0.9f
        )
        val note = ExtensionPreferenceResolver.routingNote(signal)
        assertEquals("scene=normal,conf=0.9,ext-preferred=none", note)
    }
}
