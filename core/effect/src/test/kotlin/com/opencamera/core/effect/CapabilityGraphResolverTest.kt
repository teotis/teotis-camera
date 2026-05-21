package com.opencamera.core.effect

import com.opencamera.core.device.CapabilityRequirement
import com.opencamera.core.device.CapabilityRequirementKind
import com.opencamera.core.device.CapabilitySupport
import com.opencamera.core.device.CapabilityUseSite
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.ManualControlCapabilityMatrix
import com.opencamera.core.device.ManualControlSupport
import com.opencamera.core.media.MediaProcessorAvailability
import com.opencamera.core.settings.WatermarkStyleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CapabilityGraphResolverTest {

    private fun requirement(
        id: String,
        kind: CapabilityRequirementKind,
        vararg useSites: CapabilityUseSite = arrayOf(CapabilityUseSite.CAPTURE),
        fallbackIds: List<String> = emptyList()
    ) = CapabilityRequirement(
        id = id,
        kind = kind,
        requiredFor = useSites.toSet(),
        fallbackIds = fallbackIds
    )

    private fun resolver(
        device: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        processors: MediaProcessorAvailability = MediaProcessorAvailability.ALL_AVAILABLE
    ) = CapabilityGraphResolver(device, processors)

    // --- Photo Live Photo ---

    @Test
    fun `live photo with temporal buffer available resolves all requirements supported`() {
        val r = resolver()
        val requirements = listOf(
            requirement("live-still", CapabilityRequirementKind.STILL_CAPTURE),
            requirement("live-temporal", CapabilityRequirementKind.TEMPORAL_RING_BUFFER),
            requirement("live-motion", CapabilityRequirementKind.MOTION_SIDE_CAR),
            requirement("live-save", CapabilityRequirementKind.SAVE_TRANSACTION),
            requirement("live-thumb", CapabilityRequirementKind.THUMBNAIL_RESULT)
        )
        val report = r.resolve("photo-live", requirements)

        assertTrue(report.allApplied)
        assertFalse(report.hasUnsupported)
        requirements.forEach { req ->
            assertEquals(CapabilitySupport.SUPPORTED, report.resolutionFor(req.id)?.support)
        }
    }

    @Test
    fun `live photo without temporal buffer resolves motion sidecar as unsupported`() {
        val r = resolver(
            processors = MediaProcessorAvailability.ALL_AVAILABLE.copy(
                temporalMediaAssemblerAvailable = false
            )
        )
        val requirements = listOf(
            requirement("live-still", CapabilityRequirementKind.STILL_CAPTURE),
            requirement("live-temporal", CapabilityRequirementKind.TEMPORAL_RING_BUFFER),
            requirement("live-motion", CapabilityRequirementKind.MOTION_SIDE_CAR)
        )
        val report = r.resolve("photo-live", requirements)

        assertEquals(CapabilitySupport.SUPPORTED, report.resolutionFor("live-still")?.support)
        assertEquals(CapabilitySupport.UNSUPPORTED, report.resolutionFor("live-temporal")?.support)
        assertEquals(CapabilitySupport.UNSUPPORTED, report.resolutionFor("live-motion")?.support)
        assertTrue(report.hasUnsupported)
        assertFalse(report.allApplied)
    }

    // --- Night Multi-Frame ---

    @Test
    fun `night multi-frame supported when device and processor available`() {
        val r = resolver()
        val req = requirement("night-merge", CapabilityRequirementKind.MULTI_FRAME_CAPTURE)
        val report = r.resolve("night", listOf(req))

        assertEquals(CapabilitySupport.SUPPORTED, report.resolutionFor("night-merge")?.support)
    }

    @Test
    fun `night multi-frame degraded when device does not support`() {
        val r = resolver(
            device = DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = false)
        )
        val req = requirement("night-merge", CapabilityRequirementKind.MULTI_FRAME_CAPTURE)
        val report = r.resolve("night", listOf(req))

        val resolution = report.resolutionFor("night-merge")
        assertNotNull(resolution)
        assertEquals(CapabilitySupport.DEGRADED, resolution.support)
        assertEquals("single-frame", resolution.selectedFallbackId)
    }

    @Test
    fun `night multi-frame degraded when merge processor unavailable`() {
        val r = resolver(
            processors = MediaProcessorAvailability.ALL_AVAILABLE.copy(
                multiFrameMergeAvailable = false
            )
        )
        val req = requirement("night-merge", CapabilityRequirementKind.MULTI_FRAME_CAPTURE)
        val report = r.resolve("night", listOf(req))

        val resolution = report.resolutionFor("night-merge")
        assertNotNull(resolution)
        assertEquals(CapabilitySupport.DEGRADED, resolution.support)
        assertEquals("single-frame", resolution.selectedFallbackId)
    }

    // --- Portrait Bokeh ---

    @Test
    fun `portrait bokeh supported when depth and render available`() {
        val r = resolver()
        val spec = EffectSpec(listOf(PortraitEffect("p1", "depth", "light", "medium", "soft")))
        val req = requirement("portrait-bokeh", CapabilityRequirementKind.PORTRAIT_SEGMENTATION)
        val report = r.resolve("portrait", listOf(req), spec)

        assertEquals(CapabilitySupport.SUPPORTED, report.resolutionFor("portrait-bokeh")?.support)
    }

    @Test
    fun `portrait bokeh degraded with focus fallback when depth not supported`() {
        val r = resolver(
            device = DeviceCapabilities.DEFAULT.copy(supportsPortraitDepthEffect = false)
        )
        val spec = EffectSpec(listOf(PortraitEffect("p1", "depth", "light", "medium", "soft")))
        val req = requirement("portrait-bokeh", CapabilityRequirementKind.PORTRAIT_SEGMENTATION)
        val report = r.resolve("portrait", listOf(req), spec)

        val resolution = report.resolutionFor("portrait-bokeh")
        assertNotNull(resolution)
        assertEquals(CapabilitySupport.DEGRADED, resolution.support)
        assertEquals("focus-fallback", resolution.selectedFallbackId)
    }

    @Test
    fun `portrait bokeh unsupported when render processor unavailable`() {
        val r = resolver(
            processors = MediaProcessorAvailability.ALL_AVAILABLE.copy(
                portraitRenderAvailable = false
            )
        )
        val spec = EffectSpec(listOf(PortraitEffect("p1", "depth", "light", "medium", "soft")))
        val req = requirement("portrait-bokeh", CapabilityRequirementKind.PORTRAIT_SEGMENTATION)
        val report = r.resolve("portrait", listOf(req), spec)

        assertEquals(CapabilitySupport.UNSUPPORTED, report.resolutionFor("portrait-bokeh")?.support)
    }

    // --- Filter Preview/Capture ---

    @Test
    fun `filter capture supported when render processor available`() {
        val r = resolver()
        val spec = EffectSpec(listOf(FilterEffect("vivid", null)))
        val req = requirement("filter-capture", CapabilityRequirementKind.FILTER_CAPTURE_RENDER)
        val report = r.resolve("photo-filter", listOf(req), spec)

        assertEquals(CapabilitySupport.SUPPORTED, report.resolutionFor("filter-capture")?.support)
    }

    @Test
    fun `filter capture unsupported when render processor missing`() {
        val r = resolver(
            processors = MediaProcessorAvailability.ALL_AVAILABLE.copy(
                filterRenderAvailable = false
            )
        )
        val spec = EffectSpec(listOf(FilterEffect("vivid", null)))
        val req = requirement("filter-capture", CapabilityRequirementKind.FILTER_CAPTURE_RENDER)
        val report = r.resolve("photo-filter", listOf(req), spec)

        assertEquals(CapabilitySupport.UNSUPPORTED, report.resolutionFor("filter-capture")?.support)
    }

    @Test
    fun `filter preview only when capture processor missing`() {
        val r = resolver(
            processors = MediaProcessorAvailability.ALL_AVAILABLE.copy(
                filterRenderAvailable = false
            )
        )
        val spec = EffectSpec(listOf(FilterEffect("vivid", null)))
        val req = requirement("filter-preview", CapabilityRequirementKind.FILTER_PREVIEW_RENDER)
        val report = r.resolve("photo-filter", listOf(req), spec)

        assertEquals(CapabilitySupport.PREVIEW_ONLY, report.resolutionFor("filter-preview")?.support)
    }

    @Test
    fun `filter requirements resolve ok when no filter in spec`() {
        val r = resolver(
            processors = MediaProcessorAvailability.ALL_AVAILABLE.copy(
                filterRenderAvailable = false
            )
        )
        val reqCapture = requirement("filter-capture", CapabilityRequirementKind.FILTER_CAPTURE_RENDER)
        val reqPreview = requirement("filter-preview", CapabilityRequirementKind.FILTER_PREVIEW_RENDER)
        val report = r.resolve("photo", listOf(reqCapture, reqPreview), EffectSpec.EMPTY)

        assertEquals(CapabilitySupport.SUPPORTED, report.resolutionFor("filter-capture")?.support)
        assertEquals(CapabilitySupport.SUPPORTED, report.resolutionFor("filter-preview")?.support)
    }

    // --- Pro Manual Controls ---

    @Test
    fun `manual control supported when APPLY controls exist`() {
        val r = resolver(
            device = DeviceCapabilities.DEFAULT.copy(
                manualControlCapabilities = ManualControlCapabilityMatrix.CAMERA2_INTEROP_DEFAULT
            )
        )
        val req = requirement("pro-manual", CapabilityRequirementKind.MANUAL_CONTROL)
        val report = r.resolve("pro", listOf(req))

        assertEquals(CapabilitySupport.SUPPORTED, report.resolutionFor("pro-manual")?.support)
    }

    @Test
    fun `manual control saved only when no APPLY controls`() {
        val r = resolver(
            device = DeviceCapabilities.DEFAULT.copy(
                manualControlCapabilities = ManualControlCapabilityMatrix.SAVED_ONLY_DEFAULT
            )
        )
        val req = requirement("pro-manual", CapabilityRequirementKind.MANUAL_CONTROL)
        val report = r.resolve("pro", listOf(req))

        assertEquals(CapabilitySupport.SAVED_ONLY, report.resolutionFor("pro-manual")?.support)
    }

    @Test
    fun `manual control unsupported when all controls unsupported`() {
        val matrix = ManualControlCapabilityMatrix(
            raw = ManualControlSupport.UNSUPPORTED,
            iso = ManualControlSupport.UNSUPPORTED,
            shutter = ManualControlSupport.UNSUPPORTED,
            exposureCompensation = ManualControlSupport.UNSUPPORTED,
            focusDistance = ManualControlSupport.UNSUPPORTED,
            aperture = ManualControlSupport.UNSUPPORTED,
            whiteBalance = ManualControlSupport.UNSUPPORTED
        )
        val r = resolver(
            device = DeviceCapabilities.DEFAULT.copy(manualControlCapabilities = matrix)
        )
        val req = requirement("pro-manual", CapabilityRequirementKind.MANUAL_CONTROL)
        val report = r.resolve("pro", listOf(req))

        assertEquals(CapabilitySupport.UNSUPPORTED, report.resolutionFor("pro-manual")?.support)
    }

    @Test
    fun `raw output saved only when raw is SAVED_ONLY`() {
        val r = resolver(
            device = DeviceCapabilities.DEFAULT.copy(
                manualControlCapabilities = ManualControlCapabilityMatrix.SAVED_ONLY_DEFAULT
            )
        )
        val req = requirement("pro-raw", CapabilityRequirementKind.RAW_OUTPUT)
        val report = r.resolve("pro", listOf(req))

        assertEquals(CapabilitySupport.SAVED_ONLY, report.resolutionFor("pro-raw")?.support)
    }

    @Test
    fun `raw output supported when raw is APPLY`() {
        val matrix = ManualControlCapabilityMatrix(
            raw = ManualControlSupport.APPLY,
            iso = ManualControlSupport.SAVED_ONLY,
            shutter = ManualControlSupport.SAVED_ONLY,
            exposureCompensation = ManualControlSupport.SAVED_ONLY,
            focusDistance = ManualControlSupport.SAVED_ONLY,
            aperture = ManualControlSupport.SAVED_ONLY,
            whiteBalance = ManualControlSupport.SAVED_ONLY
        )
        val r = resolver(
            device = DeviceCapabilities.DEFAULT.copy(manualControlCapabilities = matrix)
        )
        val req = requirement("pro-raw", CapabilityRequirementKind.RAW_OUTPUT)
        val report = r.resolve("pro", listOf(req))

        assertEquals(CapabilitySupport.SUPPORTED, report.resolutionFor("pro-raw")?.support)
    }

    // --- Video Watermark Sidecar ---

    @Test
    fun `watermark render supported when processor available`() {
        val r = resolver()
        val spec = EffectSpec(listOf(WatermarkEffect("t1", emptyMap(), WatermarkStyleSettings())))
        val req = requirement("video-watermark", CapabilityRequirementKind.WATERMARK_RENDER)
        val report = r.resolve("video", listOf(req), spec)

        assertEquals(CapabilitySupport.SUPPORTED, report.resolutionFor("video-watermark")?.support)
    }

    @Test
    fun `watermark render unsupported when processor unavailable`() {
        val r = resolver(
            processors = MediaProcessorAvailability.ALL_AVAILABLE.copy(
                watermarkRenderAvailable = false
            )
        )
        val spec = EffectSpec(listOf(WatermarkEffect("t1", emptyMap(), WatermarkStyleSettings())))
        val req = requirement("video-watermark", CapabilityRequirementKind.WATERMARK_RENDER)
        val report = r.resolve("video", listOf(req), spec)

        assertEquals(CapabilitySupport.UNSUPPORTED, report.resolutionFor("video-watermark")?.support)
    }

    // --- Document Geometry ---

    @Test
    fun `document geometry supported when enhancement available`() {
        val r = resolver()
        val spec = EffectSpec(listOf(DocumentEffect(true, "high")))
        val req = requirement("doc-geometry", CapabilityRequirementKind.DOCUMENT_GEOMETRY)
        val report = r.resolve("document", listOf(req), spec)

        assertEquals(CapabilitySupport.SUPPORTED, report.resolutionFor("doc-geometry")?.support)
    }

    @Test
    fun `document geometry degraded when enhancement not supported`() {
        val r = resolver(
            device = DeviceCapabilities.DEFAULT.copy(supportsDocumentScanEnhancement = false)
        )
        val spec = EffectSpec(listOf(DocumentEffect(true, "high")))
        val req = requirement(
            "doc-geometry", CapabilityRequirementKind.DOCUMENT_GEOMETRY,
            fallbackIds = listOf("basic-archive")
        )
        val report = r.resolve("document", listOf(req), spec)

        val resolution = report.resolutionFor("doc-geometry")
        assertNotNull(resolution)
        assertEquals(CapabilitySupport.DEGRADED, resolution.support)
        assertEquals("basic-archive", resolution.selectedFallbackId)
    }

    @Test
    fun `document geometry unsupported when processor unavailable`() {
        val r = resolver(
            processors = MediaProcessorAvailability.ALL_AVAILABLE.copy(
                documentProcessorAvailable = false
            )
        )
        val spec = EffectSpec(listOf(DocumentEffect(true, "high")))
        val req = requirement("doc-geometry", CapabilityRequirementKind.DOCUMENT_GEOMETRY)
        val report = r.resolve("document", listOf(req), spec)

        assertEquals(CapabilitySupport.UNSUPPORTED, report.resolutionFor("doc-geometry")?.support)
    }

    // --- Composite Reports ---

    @Test
    fun `mixed report reflects worst case in allApplied`() {
        val r = resolver(
            device = DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = false)
        )
        val requirements = listOf(
            requirement("still", CapabilityRequirementKind.STILL_CAPTURE),
            requirement("night-merge", CapabilityRequirementKind.MULTI_FRAME_CAPTURE)
        )
        val report = r.resolve("mixed", requirements)

        assertTrue(report.allApplied) // DEGRADED is still applied
        assertFalse(report.hasUnsupported)
    }

    @Test
    fun `mixed report with unsupported is not allApplied`() {
        val r = resolver(
            processors = MediaProcessorAvailability.ALL_AVAILABLE.copy(
                temporalMediaAssemblerAvailable = false
            )
        )
        val requirements = listOf(
            requirement("still", CapabilityRequirementKind.STILL_CAPTURE),
            requirement("temporal", CapabilityRequirementKind.TEMPORAL_RING_BUFFER)
        )
        val report = r.resolve("mixed", requirements)

        assertFalse(report.allApplied)
        assertTrue(report.hasUnsupported)
    }

    @Test
    fun `pipeline notes contain all resolved entries`() {
        val r = resolver()
        val requirements = listOf(
            requirement("still", CapabilityRequirementKind.STILL_CAPTURE),
            requirement("video", CapabilityRequirementKind.VIDEO_RECORDING),
            requirement("save", CapabilityRequirementKind.SAVE_TRANSACTION)
        )
        val report = r.resolve("test", requirements)

        assertEquals(3, report.pipelineNotes.size)
        assertTrue(report.pipelineNotes.all { it.startsWith("capability:") })
    }

    // --- Live Photo Composite ---

    @Test
    fun `live photo composite resolves all sub-requirements together`() {
        val r = resolver()
        val requirements = listOf(
            requirement("live-still", CapabilityRequirementKind.STILL_CAPTURE, CapabilityUseSite.LIVE_PHOTO),
            requirement("live-temporal", CapabilityRequirementKind.TEMPORAL_RING_BUFFER, CapabilityUseSite.LIVE_PHOTO),
            requirement("live-motion", CapabilityRequirementKind.MOTION_SIDE_CAR, CapabilityUseSite.LIVE_PHOTO),
            requirement("live-save", CapabilityRequirementKind.SAVE_TRANSACTION, CapabilityUseSite.LIVE_PHOTO),
            requirement("live-thumb", CapabilityRequirementKind.THUMBNAIL_RESULT, CapabilityUseSite.LIVE_PHOTO)
        )
        val report = r.resolve("photo-live", requirements)

        assertEquals(5, report.resolved.size)
        assertTrue(report.allApplied)
        assertEquals(5, report.pipelineNotes.size)
    }

    // --- Basic capability checks ---

    @Test
    fun `still capture unsupported when device lacks support`() {
        val r = resolver(
            device = DeviceCapabilities.DEFAULT.copy(supportsStillCapture = false)
        )
        val req = requirement("still", CapabilityRequirementKind.STILL_CAPTURE)
        val report = r.resolve("photo", listOf(req))

        assertEquals(CapabilitySupport.UNSUPPORTED, report.resolutionFor("still")?.support)
    }

    @Test
    fun `video recording unsupported when device lacks support`() {
        val r = resolver(
            device = DeviceCapabilities.DEFAULT.copy(supportsVideoRecording = false)
        )
        val req = requirement("video", CapabilityRequirementKind.VIDEO_RECORDING)
        val report = r.resolve("video", listOf(req))

        assertEquals(CapabilitySupport.UNSUPPORTED, report.resolutionFor("video")?.support)
    }

    @Test
    fun `preview frame stream degraded when snapshots unavailable`() {
        val r = resolver(
            device = DeviceCapabilities.DEFAULT.copy(supportsPreviewSnapshots = false)
        )
        val req = requirement(
            "preview-stream", CapabilityRequirementKind.PREVIEW_FRAME_STREAM,
            fallbackIds = listOf("direct-capture")
        )
        val report = r.resolve("preview", listOf(req))

        val resolution = report.resolutionFor("preview-stream")
        assertNotNull(resolution)
        assertEquals(CapabilitySupport.DEGRADED, resolution.support)
        assertEquals("direct-capture", resolution.selectedFallbackId)
    }
}
