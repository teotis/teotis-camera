package com.opencamera.core.mode

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.settings.FeatureCatalog
import com.opencamera.core.settings.ManualCaptureParams
import com.opencamera.core.settings.SessionSettingsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProVariantStateTest {

    @Test
    fun `initial state is disabled`() {
        val state = ProVariantState(context = proContext())

        assertFalse(state.isEnabled)
        assertEquals("standard", state.modeVariantTag())
        assertNull(state.currentManualDraftOrNull())
        assertEquals("photo-original", state.resolvedAlgorithmProfile("photo-original"))
    }

    @Test
    fun `toggle enables manual pro state`() {
        val state = ProVariantState(context = proContext())

        val result = state.toggle("Portrait")

        assertTrue(result.enabled)
        assertEquals("entered", result.eventSuffix)
        assertEquals(ModeSignal.ShowHint("Portrait Pro on"), result.signal)
        assertEquals("pro", state.modeVariantTag())
        assertEquals("manual", state.resolvedControlMode())
        assertEquals("metadata-draft", state.manualDraftState())
        assertEquals("photo-original-pro", state.resolvedAlgorithmProfile("photo-original"))
        assertEquals("Exit Pro", state.proActionLabel())
    }

    @Test
    fun `toggle disables after enable`() {
        val state = ProVariantState(context = proContext())

        state.toggle("Scenery")
        val result = state.toggle("Scenery")

        assertFalse(result.enabled)
        assertEquals("exited", result.eventSuffix)
        assertEquals(ModeSignal.ShowHint("Scenery Pro off"), result.signal)
        assertEquals("standard", state.modeVariantTag())
    }

    @Test
    fun `manual unavailable uses assisted metadata and pro assist labels`() {
        val state = ProVariantState(
            context = proContext(
                deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    supportsManualControls = false
                )
            )
        )

        state.toggle("Humanistic")

        assertEquals("assisted", state.resolvedControlMode())
        assertEquals("unsupported", state.manualDraftState())
        assertEquals("photo-original-pro-assist", state.resolvedAlgorithmProfile("photo-original"))
        assertEquals("Exit Pro Assist", state.proActionLabel())
        assertEquals("Pro Assist", state.variantExifLabel())
        assertTrue(
            state.summaryText("humanistic").contains(
                "saved-only draft because manual controls are unavailable"
            )
        )
    }

    @Test
    fun `metadata tags are emitted only when enabled`() {
        val state = ProVariantState(
            context = proContext(
                manualDraft = ManualCaptureParams(
                    rawEnabled = true,
                    iso = 320,
                    shutterSpeedMillis = 33L,
                    whiteBalanceKelvin = 4800
                )
            )
        )

        assertTrue(state.metadataTags().isEmpty())

        state.toggle("Portrait")
        val tags = state.metadataTags()

        assertEquals("manual", tags["controlMode"])
        assertEquals("metadata-draft", tags["manualDraftState"])
        assertEquals("on", tags["manualDraftRaw"])
        assertEquals("320", tags["manualDraftIso"])
        assertEquals("33", tags["manualDraftShutterSpeedMillis"])
        assertEquals("4800", tags["manualDraftWhiteBalanceKelvin"])
    }

    private fun proContext(
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        manualDraft: ManualCaptureParams = ManualCaptureParams()
    ): ModeContext {
        return ModeContext(
            runtimeState = {
                ModeRuntimeState(
                    deviceCapabilities = deviceCapabilities,
                    lensFacing = LensFacing.BACK,
                    stillCaptureQuality = StillCaptureQualityPreference.LATENCY,
                    stillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP
                )
            },
            settingsSnapshotProvider = {
                SessionSettingsSnapshot(
                    catalog = FeatureCatalog(
                        manualCaptureDraft = manualDraft
                    )
                )
            }
        )
    }
}
