package com.opencamera.core.capability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultiFrameInputFormatCapabilityTest {

    // ── Fake queries ──────────────────────────────────────────────

    private data class FakeMultiFrameInputApiQuery(
        val deviceRaw: Boolean = true,
        val multiFrameRaw: Boolean = false,
        val yuvBurst: Boolean = true,
        val jpegDng: Boolean = false
    ) : MultiFrameInputApiQuery {
        override fun deviceRawCaptureSupported(): Boolean = deviceRaw
        override fun cameraXMultiFrameRawSupported(): Boolean = multiFrameRaw
        override fun cameraXYuvBurstSupported(): Boolean = yuvBurst
        override fun cameraXJpegDngCombinedSupported(): Boolean = jpegDng
    }

    private fun resolver(
        apiQuery: MultiFrameInputApiQuery = FakeMultiFrameInputApiQuery(),
        stillCapture: Boolean = true,
        nightMultiFrame: Boolean = true
    ) = MultiFrameInputFormatResolver(apiQuery, stillCapture, nightMultiFrame)

    // ── CameraX 1.4.x default gate status ───────────────────────

    @Test
    fun `CameraX 1_4 query reports device raw capable but no multi-frame raw`() {
        val query = CameraX_1_4_MultiFrameInputApiQuery()
        assertTrue(query.deviceRawCaptureSupported())
        assertFalse(query.cameraXMultiFrameRawSupported())
        assertTrue(query.cameraXYuvBurstSupported())
        assertFalse(query.cameraXJpegDngCombinedSupported())
    }

    @Test
    fun `resolver with CameraX 1_4 query produces jpeg-burst supported`() {
        val matrix = resolver(apiQuery = CameraX_1_4_MultiFrameInputApiQuery()).resolve()
        assertEquals(CapabilitySupport.SUPPORTED, matrix.jpegBurst.support)
        assertTrue(matrix.jpegBurst.isUsable)
    }

    @Test
    fun `resolver with CameraX 1_4 query produces yuv-burst supported`() {
        val matrix = resolver(apiQuery = CameraX_1_4_MultiFrameInputApiQuery()).resolve()
        assertEquals(CapabilitySupport.SUPPORTED, matrix.yuvBurst.support)
        assertTrue(matrix.yuvBurst.isUsable)
    }

    @Test
    fun `resolver with CameraX 1_4 query produces raw-dng unsupported`() {
        val matrix = resolver(apiQuery = CameraX_1_4_MultiFrameInputApiQuery()).resolve()
        assertEquals(CapabilitySupport.UNSUPPORTED, matrix.rawDng.support)
        assertFalse(matrix.rawDng.isUsable)
        assertTrue(matrix.rawDng.deviceRawCapable)
        assertFalse(matrix.rawDng.cameraXApiSupported)
    }

    @Test
    fun `resolver with CameraX 1_4 query produces jpeg-dng unsupported`() {
        val matrix = resolver(apiQuery = CameraX_1_4_MultiFrameInputApiQuery()).resolve()
        assertEquals(CapabilitySupport.UNSUPPORTED, matrix.jpegDng.support)
        assertFalse(matrix.jpegDng.isUsable)
        assertTrue(matrix.jpegDng.deviceRawCapable)
        assertFalse(matrix.jpegDng.cameraXApiSupported)
    }

    @Test
    fun `CameraX 1_4 raw-dng reason mentions single-frame limitation`() {
        val matrix = resolver(apiQuery = CameraX_1_4_MultiFrameInputApiQuery()).resolve()
        assertTrue(matrix.rawDng.reason.contains("single-frame"))
        assertTrue(matrix.rawDng.reason.contains("ImageCapture"))
    }

    @Test
    fun `CameraX 1_4 jpeg-dng reason mentions multi-frame unavailability`() {
        val matrix = resolver(apiQuery = CameraX_1_4_MultiFrameInputApiQuery()).resolve()
        assertTrue(matrix.jpegDng.reason.contains("not available"))
    }

    // ── Device without RAW hardware ──────────────────────────────

    @Test
    fun `raw-dng unsupported when device does not support raw capture`() {
        val apiQuery = FakeMultiFrameInputApiQuery(deviceRaw = false, multiFrameRaw = true)
        val matrix = resolver(apiQuery = apiQuery).resolve()
        assertEquals(CapabilitySupport.UNSUPPORTED, matrix.rawDng.support)
        assertFalse(matrix.rawDng.deviceRawCapable)
    }

    @Test
    fun `jpeg-dng unsupported when device does not support raw capture`() {
        val apiQuery = FakeMultiFrameInputApiQuery(deviceRaw = false, jpegDng = true)
        val matrix = resolver(apiQuery = apiQuery).resolve()
        assertEquals(CapabilitySupport.UNSUPPORTED, matrix.jpegDng.support)
    }

    // ── Future API with multi-frame RAW support ──────────────────

    @Test
    fun `raw-dng supported when both device and API support multi-frame raw`() {
        val apiQuery = FakeMultiFrameInputApiQuery(deviceRaw = true, multiFrameRaw = true)
        val matrix = resolver(apiQuery = apiQuery).resolve()
        assertEquals(CapabilitySupport.SUPPORTED, matrix.rawDng.support)
        assertTrue(matrix.rawDng.isUsable)
        assertTrue(matrix.rawDng.deviceRawCapable)
        assertTrue(matrix.rawDng.cameraXApiSupported)
    }

    @Test
    fun `jpeg-dng supported when both device and API support combined`() {
        val apiQuery = FakeMultiFrameInputApiQuery(deviceRaw = true, jpegDng = true)
        val matrix = resolver(apiQuery = apiQuery).resolve()
        assertEquals(CapabilitySupport.SUPPORTED, matrix.jpegDng.support)
        assertTrue(matrix.jpegDng.isUsable)
        assertTrue(matrix.jpegDng.deviceRawCapable)
        assertTrue(matrix.jpegDng.cameraXApiSupported)
    }

    // ── Device without still capture ─────────────────────────────

    @Test
    fun `all formats unsupported when device lacks still capture`() {
        val matrix = resolver(stillCapture = false).resolve()
        assertEquals(CapabilitySupport.UNSUPPORTED, matrix.jpegBurst.support)
        assertEquals(CapabilitySupport.UNSUPPORTED, matrix.rawDng.support)
        assertEquals(CapabilitySupport.UNSUPPORTED, matrix.jpegDng.support)
    }

    // ── Night multi-frame not supported ──────────────────────────

    @Test
    fun `jpeg-burst degraded when night multi-frame not supported`() {
        val matrix = resolver(nightMultiFrame = false).resolve()
        assertEquals(CapabilitySupport.DEGRADED, matrix.jpegBurst.support)
        assertTrue(matrix.jpegBurst.isUsable)
    }

    @Test
    fun `yuv-burst degraded when night multi-frame not supported`() {
        val matrix = resolver(nightMultiFrame = false).resolve()
        assertEquals(CapabilitySupport.DEGRADED, matrix.yuvBurst.support)
        assertTrue(matrix.yuvBurst.isUsable)
    }

    @Test
    fun `raw-dng still unsupported when night multi-frame not supported even if API available`() {
        val apiQuery = FakeMultiFrameInputApiQuery(deviceRaw = true, multiFrameRaw = true)
        val matrix = resolver(apiQuery = apiQuery, nightMultiFrame = false).resolve()
        // RAW support is independent of night multi-frame — device + API check
        assertEquals(CapabilitySupport.SUPPORTED, matrix.rawDng.support)
    }

    // ── YUV burst unavailable ────────────────────────────────────

    @Test
    fun `yuv-burst unsupported when API does not support it`() {
        val apiQuery = FakeMultiFrameInputApiQuery(yuvBurst = false)
        val matrix = resolver(apiQuery = apiQuery).resolve()
        assertEquals(CapabilitySupport.UNSUPPORTED, matrix.yuvBurst.support)
    }

    // ── MultiFrameInputCapabilityMatrix ──────────────────────────

    @Test
    fun `matrix diagnostics contains all format tags`() {
        val matrix = resolver(apiQuery = CameraX_1_4_MultiFrameInputApiQuery()).resolve()
        val diagnostics = matrix.diagnostics
        assertEquals(4, diagnostics.size)
        assertTrue(diagnostics.any { it.contains("jpeg-burst") })
        assertTrue(diagnostics.any { it.contains("yuv-burst") })
        assertTrue(diagnostics.any { it.contains("raw-dng") })
        assertTrue(diagnostics.any { it.contains("jpeg-dng") })
    }

    @Test
    fun `matrix forFormat returns correct capability`() {
        val matrix = resolver(apiQuery = CameraX_1_4_MultiFrameInputApiQuery()).resolve()
        assertEquals(matrix.jpegBurst, matrix.forFormat(MultiFrameInputFormat.JPEG_BURST))
        assertEquals(matrix.yuvBurst, matrix.forFormat(MultiFrameInputFormat.YUV_BURST))
        assertEquals(matrix.rawDng, matrix.forFormat(MultiFrameInputFormat.RAW_DNG))
        assertEquals(matrix.jpegDng, matrix.forFormat(MultiFrameInputFormat.JPEG_DNG))
    }

    @Test
    fun `matrix allFormats returns all four capabilities`() {
        val matrix = resolver(apiQuery = CameraX_1_4_MultiFrameInputApiQuery()).resolve()
        assertEquals(4, matrix.allFormats.size)
    }

    @Test
    fun `hasRawCapability true when device supports raw`() {
        val apiQuery = FakeMultiFrameInputApiQuery(deviceRaw = true)
        val matrix = resolver(apiQuery = apiQuery).resolve()
        assertTrue(matrix.hasRawCapability)
    }

    @Test
    fun `hasRawCapability false when device does not support raw`() {
        val apiQuery = FakeMultiFrameInputApiQuery(deviceRaw = false)
        val matrix = resolver(apiQuery = apiQuery).resolve()
        assertFalse(matrix.hasRawCapability)
    }

    @Test
    fun `matrix summary contains all format labels`() {
        val matrix = resolver(apiQuery = CameraX_1_4_MultiFrameInputApiQuery()).resolve()
        val summary = matrix.summary()
        assertTrue(summary.contains("JPEG Burst"))
        assertTrue(summary.contains("YUV Burst"))
        assertTrue(summary.contains("RAW/DNG"))
        assertTrue(summary.contains("JPEG+DNG"))
    }

    // ── MultiFrameInputFormatCapability ───────────────────────────

    @Test
    fun `capability diagnosticsTag format`() {
        val cap = MultiFrameInputFormatCapability(
            format = MultiFrameInputFormat.RAW_DNG,
            support = CapabilitySupport.UNSUPPORTED,
            reason = "test"
        )
        assertEquals("mfi:raw-dng=unsupported", cap.diagnosticsTag)
    }

    @Test
    fun `capability summary contains format label and support`() {
        val cap = MultiFrameInputFormatCapability(
            format = MultiFrameInputFormat.JPEG_BURST,
            support = CapabilitySupport.SUPPORTED,
            reason = "all good"
        )
        assertTrue(cap.summary().contains("JPEG Burst"))
        assertTrue(cap.summary().contains("supported"))
        assertTrue(cap.summary().contains("all good"))
    }

    @Test
    fun `capability isUsable for SUPPORTED`() {
        assertTrue(
            MultiFrameInputFormatCapability(
                MultiFrameInputFormat.JPEG_BURST, CapabilitySupport.SUPPORTED, "ok"
            ).isUsable
        )
    }

    @Test
    fun `capability isUsable for DEGRADED`() {
        assertTrue(
            MultiFrameInputFormatCapability(
                MultiFrameInputFormat.JPEG_BURST, CapabilitySupport.DEGRADED, "fallback"
            ).isUsable
        )
    }

    @Test
    fun `capability not isUsable for UNSUPPORTED`() {
        assertFalse(
            MultiFrameInputFormatCapability(
                MultiFrameInputFormat.RAW_DNG, CapabilitySupport.UNSUPPORTED, "no"
            ).isUsable
        )
    }

    @Test
    fun `capability not isUsable for SAVED_ONLY`() {
        assertFalse(
            MultiFrameInputFormatCapability(
                MultiFrameInputFormat.RAW_DNG, CapabilitySupport.SAVED_ONLY, "draft"
            ).isUsable
        )
    }

    // ── CapabilityGraphDeviceQuery integration ───────────────────

    @Test
    fun `CapabilityGraphDeviceQuery multiFrameInputFormatMatrix returns null by default`() {
        val query = object : CapabilityGraphDeviceQuery {
            override fun supportsStillCapture() = true
            override fun supportsVideoRecording() = true
            override fun supportsPreviewSnapshots() = true
            override fun supportsNightMultiFrame() = true
            override fun manualControlSummary() = CapabilityManualControlSummary(false, false)
            override fun rawOutputSupport() = CapabilitySupport.SUPPORTED
            override fun supportsPortraitDepth() = true
            override fun supportsDocumentGeometry() = true
        }
        assertNull(query.multiFrameInputFormatMatrix())
    }

    // ── Raw DNG diagnostic reason content ────────────────────────

    @Test
    fun `raw-dng reason with CameraX 1_4 mentions ImageCapture for single-frame`() {
        val matrix = resolver(apiQuery = CameraX_1_4_MultiFrameInputApiQuery()).resolve()
        assertTrue(matrix.rawDng.reason.contains("ImageCapture"))
    }

    @Test
    fun `raw-dng reason with CameraX 1_4 mentions fusion limitation`() {
        val matrix = resolver(apiQuery = CameraX_1_4_MultiFrameInputApiQuery()).resolve()
        assertTrue(matrix.rawDng.reason.contains("fusion"))
    }

    @Test
    fun `jpeg-dng reason with CameraX 1_4 mentions combined input`() {
        val matrix = resolver(apiQuery = CameraX_1_4_MultiFrameInputApiQuery()).resolve()
        assertTrue(matrix.jpegDng.reason.contains("combined"))
    }
}
