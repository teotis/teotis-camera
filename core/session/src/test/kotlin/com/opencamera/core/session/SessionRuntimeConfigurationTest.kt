package com.opencamera.core.session

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.mode.ModeRuntimeState
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.SessionSettingsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class SessionRuntimeConfigurationTest {

    @Test
    fun `default snapshot has consistent initial values`() {
        val config = SessionRuntimeConfiguration()
        assertEquals(DeviceCapabilities.DEFAULT, config.deviceCapabilities)
        assertEquals(LensFacing.BACK, config.lensFacing)
        assertEquals(StillCaptureQualityPreference.QUALITY, config.stillCaptureQuality)
        assertEquals(StillCaptureResolutionPreset.LARGE_12MP, config.stillCaptureResolutionPreset)
        assertEquals(null, config.stillCaptureOutputSize)
        assertEquals(PreviewRatio.FULL, config.previewRatio)
        assertEquals(SessionSettingsSnapshot(), config.settings)
    }

    @Test
    fun `copy changes only specified field`() {
        val original = SessionRuntimeConfiguration()

        val withLens = original.copy(lensFacing = LensFacing.FRONT)
        assertEquals(LensFacing.FRONT, withLens.lensFacing)
        assertEquals(original.deviceCapabilities, withLens.deviceCapabilities)
        assertEquals(original.stillCaptureQuality, withLens.stillCaptureQuality)
        assertEquals(original.stillCaptureResolutionPreset, withLens.stillCaptureResolutionPreset)
        assertEquals(original.stillCaptureOutputSize, withLens.stillCaptureOutputSize)
        assertEquals(original.previewRatio, withLens.previewRatio)
        assertEquals(original.settings, withLens.settings)
    }

    @Test
    fun `copy produces distinct instance`() {
        val original = SessionRuntimeConfiguration()
        val copied = original.copy(lensFacing = LensFacing.FRONT)
        assertNotSame(original, copied)
    }

    @Test
    fun `mode runtime state projects coherent snapshot from configuration`() {
        val config = SessionRuntimeConfiguration(
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(supportsFlashControl = false),
            lensFacing = LensFacing.FRONT,
            stillCaptureQuality = StillCaptureQualityPreference.LATENCY,
            stillCaptureResolutionPreset = StillCaptureResolutionPreset.MEDIUM_8MP
        )
        val runtimeState = ModeRuntimeState(
            deviceCapabilities = config.deviceCapabilities,
            lensFacing = config.lensFacing,
            stillCaptureResolutionPreset = config.stillCaptureResolutionPreset,
            stillCaptureQuality = config.stillCaptureQuality,
            stillCaptureOutputSize = config.stillCaptureOutputSize
        )

        assertEquals(config.deviceCapabilities, runtimeState.deviceCapabilities)
        assertEquals(config.lensFacing, runtimeState.lensFacing)
        assertEquals(config.stillCaptureResolutionPreset, runtimeState.stillCaptureResolutionPreset)
        assertEquals(config.stillCaptureQuality, runtimeState.stillCaptureQuality)
        assertEquals(config.stillCaptureOutputSize, runtimeState.stillCaptureOutputSize)
    }

    @Test
    fun `updating one field preserves all others in sequence`() {
        var config = SessionRuntimeConfiguration()

        config = config.copy(lensFacing = LensFacing.FRONT)
        config = config.copy(stillCaptureQuality = StillCaptureQualityPreference.LATENCY)
        config = config.copy(stillCaptureResolutionPreset = StillCaptureResolutionPreset.SMALL_2MP)
        config = config.copy(previewRatio = PreviewRatio.RATIO_4_3)

        assertEquals(LensFacing.FRONT, config.lensFacing)
        assertEquals(StillCaptureQualityPreference.LATENCY, config.stillCaptureQuality)
        assertEquals(StillCaptureResolutionPreset.SMALL_2MP, config.stillCaptureResolutionPreset)
        assertEquals(PreviewRatio.RATIO_4_3, config.previewRatio)
        assertEquals(DeviceCapabilities.DEFAULT, config.deviceCapabilities)
        assertEquals(null, config.stillCaptureOutputSize)
        assertEquals(SessionSettingsSnapshot(), config.settings)
    }

    @Test
    fun `settings update propagates through snapshot`() {
        val initial = SessionRuntimeConfiguration()
        val updatedSettings = SessionSettingsSnapshot(
            persisted = PersistedSettings(
                photo = PhotoSettings(defaultFilterProfileId = "photo-rich")
            )
        )
        val updated = initial.copy(settings = updatedSettings)

        assertEquals(updatedSettings, updated.settings)
        assertEquals("photo-rich", updated.settings.persisted.photo.defaultFilterProfileId)
        assertEquals(initial.deviceCapabilities, updated.deviceCapabilities)
    }

    @Test
    fun `mode runtime state projection updates after configuration change`() {
        var config = SessionRuntimeConfiguration()
        fun projectState() = ModeRuntimeState(
            deviceCapabilities = config.deviceCapabilities,
            lensFacing = config.lensFacing,
            stillCaptureResolutionPreset = config.stillCaptureResolutionPreset,
            stillCaptureQuality = config.stillCaptureQuality,
            stillCaptureOutputSize = config.stillCaptureOutputSize
        )

        val state1 = projectState()
        assertEquals(LensFacing.BACK, state1.lensFacing)
        assertEquals(StillCaptureQualityPreference.QUALITY, state1.stillCaptureQuality)

        config = config.copy(
            lensFacing = LensFacing.FRONT,
            stillCaptureQuality = StillCaptureQualityPreference.LATENCY
        )

        val state2 = projectState()
        assertEquals(LensFacing.FRONT, state2.lensFacing)
        assertEquals(StillCaptureQualityPreference.LATENCY, state2.stillCaptureQuality)
        assertEquals(config.deviceCapabilities, state2.deviceCapabilities)
    }

    @Test
    fun `multi-field copy is atomic`() {
        val config = SessionRuntimeConfiguration()
        val updated = config.copy(
            lensFacing = LensFacing.FRONT,
            stillCaptureQuality = StillCaptureQualityPreference.LATENCY,
            stillCaptureResolutionPreset = StillCaptureResolutionPreset.SMALL_2MP,
            previewRatio = PreviewRatio.RATIO_16_9
        )

        assertEquals(LensFacing.FRONT, updated.lensFacing)
        assertEquals(StillCaptureQualityPreference.LATENCY, updated.stillCaptureQuality)
        assertEquals(StillCaptureResolutionPreset.SMALL_2MP, updated.stillCaptureResolutionPreset)
        assertEquals(PreviewRatio.RATIO_16_9, updated.previewRatio)
    }

    @Test
    fun `current controller stays outside configuration`() {
        val config = SessionRuntimeConfiguration()
        assertNotNull(config)
        // SessionRuntimeConfiguration has no ModeController field - it is a pure
        // configuration snapshot, not a runtime resource holder.
        val fields = SessionRuntimeConfiguration::class.java.declaredFields
        val fieldNames = fields.map { it.name }.toSet()
        assertFalse(fieldNames.any { it.lowercase().contains("controller") })
    }
}

private fun assertFalse(condition: Boolean) {
    kotlin.test.assertFalse(condition)
}
