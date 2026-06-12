package com.opencamera.app.camera

import androidx.camera.core.CameraSelector
import com.opencamera.core.device.CameraExtensionAvailability
import com.opencamera.core.device.CameraExtensionMode
import com.opencamera.core.device.CameraExtensionResolution
import com.opencamera.core.device.ExtensionCaptureStrategy
import com.opencamera.core.device.LensFacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CameraXCaptureAdapterExtensionResolutionTest {

    @Test
    fun `not requested returns NotRequested result`() {
        val result = resolveViaFake(
            strategy = ExtensionCaptureStrategy(CameraExtensionMode.NONE),
            lensFacing = LensFacing.BACK
        )
        assertIs<ExtensionSelectorResult.NotRequested>(result)
    }

    @Test
    fun `requested extension with available resolver returns Resolved`() {
        val fakeResolver = FakeExtensionSelectorResolver(
            availableModes = setOf(CameraExtensionMode.HDR)
        )
        val result = resolveViaFake(
            strategy = ExtensionCaptureStrategy(CameraExtensionMode.HDR),
            lensFacing = LensFacing.BACK,
            resolver = fakeResolver
        )
        assertIs<ExtensionSelectorResult.Resolved>(result)
        assertEquals(
            CameraExtensionAvailability.AVAILABLE,
            result.resolution.availability
        )
        assertTrue(result.resolution.isUsable)
    }

    @Test
    fun `requested extension with unsupported mode returns Fallback`() {
        val fakeResolver = FakeExtensionSelectorResolver(
            availableModes = emptySet()
        )
        val result = resolveViaFake(
            strategy = ExtensionCaptureStrategy(CameraExtensionMode.NIGHT),
            lensFacing = LensFacing.BACK,
            resolver = fakeResolver
        )
        assertIs<ExtensionSelectorResult.Fallback>(result)
        assertEquals(
            CameraExtensionAvailability.UNSUPPORTED,
            result.resolution.availability
        )
        assertFalse(result.resolution.isUsable)
    }

    @Test
    fun `requested extension with selector error returns Fallback`() {
        val fakeResolver = FakeExtensionSelectorResolver(
            availableModes = setOf(CameraExtensionMode.NIGHT),
            selectorErrors = setOf(CameraExtensionMode.NIGHT)
        )
        val result = resolveViaFake(
            strategy = ExtensionCaptureStrategy(CameraExtensionMode.NIGHT),
            lensFacing = LensFacing.BACK,
            resolver = fakeResolver
        )
        assertIs<ExtensionSelectorResult.Fallback>(result)
        assertEquals(
            CameraExtensionAvailability.SELECTOR_ERROR,
            result.resolution.availability
        )
    }

    @Test
    fun `requested extension with no resolver returns Fallback`() {
        val result = resolveViaFake(
            strategy = ExtensionCaptureStrategy(CameraExtensionMode.AUTO),
            lensFacing = LensFacing.FRONT,
            resolver = null
        )
        assertIs<ExtensionSelectorResult.Fallback>(result)
        assertEquals(
            CameraExtensionAvailability.MANAGER_UNAVAILABLE,
            result.resolution.availability
        )
    }

    @Test
    fun `requested extension with manager error returns Fallback`() {
        val fakeResolver = FakeExtensionSelectorResolver(
            managerFailure = true
        )
        val result = resolveViaFake(
            strategy = ExtensionCaptureStrategy(CameraExtensionMode.HDR),
            lensFacing = LensFacing.BACK,
            resolver = fakeResolver
        )
        assertIs<ExtensionSelectorResult.Fallback>(result)
        assertEquals(
            CameraExtensionAvailability.MANAGER_UNAVAILABLE,
            result.resolution.availability
        )
    }

    @Test
    fun `extension resolution pipeline note includes mode tag`() {
        val resolution = CameraExtensionResolution(
            requestedMode = CameraExtensionMode.AUTO,
            availability = CameraExtensionAvailability.AVAILABLE,
            reason = "OK"
        )
        assertEquals("extension:auto=available", resolution.pipelineNote)
    }

    @Test
    fun `extension resolution carries diagnostics on error`() {
        val resolution = CameraExtensionResolution(
            requestedMode = CameraExtensionMode.BOKEH,
            availability = CameraExtensionAvailability.QUERY_ERROR,
            reason = "query failed",
            diagnostics = mapOf("exception" to "RuntimeException")
        )
        assertEquals("RuntimeException", resolution.diagnostics["exception"])
    }

    @Test
    fun `all extension modes can be requested through strategy`() {
        for (mode in CameraExtensionMode.entries) {
            val strategy = ExtensionCaptureStrategy(desiredMode = mode)
            assertEquals(mode, strategy.desiredMode)
        }
    }

    // --- Helpers ---

    /**
     * Simulates the resolution logic from CameraXCaptureAdapter.resolveExtensionForBinding
     * without requiring Android framework types.
     */
    private fun resolveViaFake(
        strategy: ExtensionCaptureStrategy,
        lensFacing: LensFacing,
        resolver: ExtensionSelectorResolver? = null
    ): ExtensionSelectorResult {
        if (strategy.desiredMode == CameraExtensionMode.NONE) {
            return ExtensionSelectorResult.NotRequested
        }
        val r = resolver ?: return ExtensionSelectorResult.Fallback(
            CameraExtensionResolution(
                requestedMode = strategy.desiredMode,
                availability = CameraExtensionAvailability.MANAGER_UNAVAILABLE,
                reason = "Extension resolver not configured"
            )
        )
        return r.resolve(strategy.desiredMode, lensFacing)
    }

    /**
     * Fake resolver for unit tests. No Android framework dependencies.
     */
    private class FakeExtensionSelectorResolver(
        private val availableModes: Set<CameraExtensionMode> = emptySet(),
        private val selectorErrors: Set<CameraExtensionMode> = emptySet(),
        private val managerFailure: Boolean = false
    ) : ExtensionSelectorResolver {
        override fun resolve(
            desiredMode: CameraExtensionMode,
            lensFacing: LensFacing
        ): ExtensionSelectorResult {
            if (managerFailure) {
                return ExtensionSelectorResult.Fallback(
                    CameraExtensionResolution(
                        requestedMode = desiredMode,
                        availability = CameraExtensionAvailability.MANAGER_UNAVAILABLE,
                        reason = "ExtensionsManager unavailable"
                    )
                )
            }
            if (desiredMode !in availableModes) {
                return ExtensionSelectorResult.Fallback(
                    CameraExtensionResolution(
                        requestedMode = desiredMode,
                        availability = CameraExtensionAvailability.UNSUPPORTED,
                        reason = "Extension ${desiredMode.tagValue} not available"
                    )
                )
            }
            if (desiredMode in selectorErrors) {
                return ExtensionSelectorResult.Fallback(
                    CameraExtensionResolution(
                        requestedMode = desiredMode,
                        availability = CameraExtensionAvailability.SELECTOR_ERROR,
                        reason = "Selector creation failed"
                    )
                )
            }
            return ExtensionSelectorResult.Resolved(
                selector = CameraSelector.DEFAULT_BACK_CAMERA,
                resolution = CameraExtensionResolution(
                    requestedMode = desiredMode,
                    availability = CameraExtensionAvailability.AVAILABLE,
                    reason = "Extension ${desiredMode.tagValue} available"
                )
            )
        }
    }
}
