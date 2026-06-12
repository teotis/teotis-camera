package com.opencamera.app.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraExtensionProbeTest {
    @Test
    fun `summary reports back night and hdr support while front is unsupported`() {
        val client = FakeCameraExtensionProbeClient(
            supported = setOf(
                CameraExtensionLensFacing.BACK to CameraExtensionMode.NIGHT,
                CameraExtensionLensFacing.BACK to CameraExtensionMode.HDR
            )
        )

        val report = CameraExtensionProbe.probe(client)

        assertEquals(
            "extensions: BACK night=supported hdr=supported bokeh=unsupported auto=unsupported face-retouch=unsupported | " +
                "FRONT night=unsupported hdr=unsupported bokeh=unsupported auto=unsupported face-retouch=unsupported",
            report.summaryLine()
        )
        assertTrue(report.toProbeText().contains("[camera-extensions]"))
    }

    @Test
    fun `selector creation failure is reported without throwing`() {
        val client = FakeCameraExtensionProbeClient(
            supported = setOf(CameraExtensionLensFacing.BACK to CameraExtensionMode.NIGHT),
            selectorFailures = setOf(CameraExtensionLensFacing.BACK to CameraExtensionMode.NIGHT)
        )

        val report = CameraExtensionProbe.probe(client)

        assertEquals(
            CameraExtensionSupport.SELECTOR_ERROR,
            report.entry(CameraExtensionLensFacing.BACK, CameraExtensionMode.NIGHT)?.support
        )
        assertTrue(report.summaryLine().contains("BACK night=selector-error"))
        assertTrue(report.toProbeText().contains("selector-error"))
    }

    @Test
    fun `manager failure is reported without throwing`() {
        val client = FakeCameraExtensionProbeClient(managerFailure = true)

        val report = CameraExtensionProbe.probe(client)

        assertTrue(report.summaryLine().contains("manager-unavailable"))
        assertEquals(
            CameraExtensionSupport.MANAGER_UNAVAILABLE,
            report.entry(CameraExtensionLensFacing.BACK, CameraExtensionMode.NIGHT)?.support
        )
        assertTrue(report.toProbeText().contains("manager-unavailable: ExtensionsManager unavailable"))
    }

    private class FakeCameraExtensionProbeClient(
        private val supported: Set<Pair<CameraExtensionLensFacing, CameraExtensionMode>> = emptySet(),
        private val selectorFailures: Set<Pair<CameraExtensionLensFacing, CameraExtensionMode>> = emptySet(),
        private val managerFailure: Boolean = false
    ) : CameraExtensionProbeClient {
        override fun isExtensionAvailable(
            lensFacing: CameraExtensionLensFacing,
            mode: CameraExtensionMode
        ): Boolean {
            if (managerFailure) {
                throw CameraExtensionManagerUnavailableException("ExtensionsManager unavailable")
            }
            return lensFacing to mode in supported
        }

        override fun verifyExtensionSelector(
            lensFacing: CameraExtensionLensFacing,
            mode: CameraExtensionMode
        ) {
            if (lensFacing to mode in selectorFailures) {
                throw IllegalStateException("selector rejected")
            }
        }
    }
}
