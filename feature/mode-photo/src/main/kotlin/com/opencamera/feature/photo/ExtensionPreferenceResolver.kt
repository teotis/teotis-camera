package com.opencamera.feature.photo

import com.opencamera.core.device.CameraExtensionMode
import com.opencamera.core.device.PhotoSceneSignal
import com.opencamera.core.device.SceneLightState

/**
 * Pure function that resolves the preferred CameraX extension mode
 * from the current photo scene state.
 *
 * Routing rules:
 * - BLUE_HOUR: prefer HDR for dynamic range
 * - LOW_LIGHT: prefer NIGHT for CameraX OEM night processing
 * - NORMAL: ordinary capture without extension
 * - UNKNOWN: ordinary capture without extension
 */
object ExtensionPreferenceResolver {

    fun resolvePreferredMode(sceneSignal: PhotoSceneSignal): CameraExtensionMode {
        return when (sceneSignal.lightState) {
            SceneLightState.BLUE_HOUR -> CameraExtensionMode.HDR
            SceneLightState.LOW_LIGHT -> CameraExtensionMode.NIGHT
            else -> CameraExtensionMode.NONE
        }
    }

    /**
     * Pipeline note summarizing the routing decision for diagnostics.
     */
    fun routingNote(sceneSignal: PhotoSceneSignal): String {
        val mode = resolvePreferredMode(sceneSignal)
        val sceneTag = sceneSignal.lightState.name.lowercase()
        val confidenceTag = sceneSignal.confidence?.let { "conf=$it" } ?: "conf=unknown"
        return "scene=$sceneTag,$confidenceTag,ext-preferred=${mode.tagValue}"
    }
}
