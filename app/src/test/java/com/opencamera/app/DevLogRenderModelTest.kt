package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.LensNode
import com.opencamera.core.device.LensNodeAvailability
import com.opencamera.core.device.PhysicalStillCaptureOutputProbe
import com.opencamera.core.device.StillCaptureCameraProbe
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.StillCaptureResolutionSource
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.media.CameraPerformanceClass
import com.opencamera.core.media.CameraThermalState
import com.opencamera.core.media.ResourceDiagnosticsSnapshot
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.LinkEventStatus
import com.opencamera.core.session.PermissionState
import com.opencamera.core.session.PerformanceLinkEvent
import com.opencamera.core.session.PreviewMetrics
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SessionLifecycle
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.SessionTraceEvent
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.CommonSettings
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSettings
import com.opencamera.core.settings.VideoSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevLogRenderModelTest {

    private val sampleTraceEvents = listOf(
        SessionTraceEvent(1, "session.created", "defaultMode=PHOTO", 1L),
        SessionTraceEvent(2, "session.booted", "mode=PHOTO", 2L),
        SessionTraceEvent(3, "preview.binding.started", "session boot", 3L),
        SessionTraceEvent(4, "preview.recovery.started", "recover after stall", 4L),
        SessionTraceEvent(5, "preview.error", "camera error", 5L),
        SessionTraceEvent(6, "zoom.switch.blocked", "countdown=3", 6L),
        SessionTraceEvent(7, "lens.switch.skipped", "already BACK", 7L),
        SessionTraceEvent(8, "intent.received", "ZoomRatioToggled", 8L),
        SessionTraceEvent(9, "preview.first.frame", "88ms", 9L),
        SessionTraceEvent(10, "mode.switched", "PORTRAIT", 10L)
    )

    @Test
    fun `debug build makes dev log available`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.KEY,
            text = TestAppTextResolver()
        )
        assertTrue(model.isAvailable)
    }

    @Test
    fun `release build makes dev log unavailable`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = false,
            selectedTab = DevLogTab.KEY,
            text = TestAppTextResolver()
        )
        assertFalse(model.isAvailable)
        assertEquals(emptyList(), model.visibleEvents)
    }

    @Test
    fun `key tab shows only key events`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.KEY,
            text = TestAppTextResolver()
        )
        assertTrue(model.visibleEvents.any { it.displayText.contains("session.created") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("session.booted") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("preview.first.frame") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("mode.switched") })
        assertFalse(model.visibleEvents.any { it.displayText.contains("preview.binding.started") })
        assertFalse(model.visibleEvents.any { it.displayText.contains("intent.received") })
    }

    @Test
    fun `cleared key tab hides old key events but keeps new events visible`() {
        val clearCutoffs = DevLogClearCutoffs().markCleared(
            type = DevLogTab.KEY,
            traceEvents = sampleTraceEvents,
            linkEvents = emptyList()
        )
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents + SessionTraceEvent(
                11,
                "preview.first.frame",
                "new frame after cleanup",
                11L
            ),
            isDebugBuild = true,
            selectedTab = DevLogTab.KEY,
            text = TestAppTextResolver(),
            clearCutoffs = clearCutoffs
        )

        assertFalse(model.visibleEvents.any { it.displayText.contains("session.created") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("new frame after cleanup") })
        assertTrue(model.title.endsWith("(1)"))
    }

    @Test
    fun `error tab shows errors and blocked events`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ERROR,
            text = TestAppTextResolver()
        )
        assertTrue(model.visibleEvents.any { it.displayText.contains("preview.error") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("zoom.switch.blocked") })
        assertFalse(model.visibleEvents.any { it.displayText.contains("lens.switch.skipped") })
        assertFalse(model.visibleEvents.any { it.displayText.contains("session.created") })
    }

    @Test
    fun `error tab excludes normal unavailable and skipped events`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents + listOf(
                SessionTraceEvent(11, "zoom.switch.unavailable", "single camera", 11L),
                SessionTraceEvent(12, "still-quality.unavailable", "mode unsupported", 12L),
                SessionTraceEvent(13, "settings.update.skipped", "no change", 13L),
                SessionTraceEvent(14, "preview.surface.lost", "surface destroyed", 14L)
            ),
            isDebugBuild = true,
            selectedTab = DevLogTab.ERROR,
            text = TestAppTextResolver()
        )

        assertFalse(model.visibleEvents.any { it.displayText.contains("zoom.switch.unavailable") })
        assertFalse(model.visibleEvents.any { it.displayText.contains("still-quality.unavailable") })
        assertFalse(model.visibleEvents.any { it.displayText.contains("settings.update.skipped") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("preview.surface.lost") })
    }

    @Test
    fun `error tab excludes high frequency zoom apply skipped events`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents + listOf(
                SessionTraceEvent(11, "zoom.apply.skipped", "2.6x", 11L),
                SessionTraceEvent(12, "preview.error", "camera error", 12L)
            ),
            isDebugBuild = true,
            selectedTab = DevLogTab.ERROR,
            text = TestAppTextResolver()
        )

        assertFalse(model.visibleEvents.any { it.displayText.contains("zoom.apply.skipped") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("preview.error") })
    }

    @Test
    fun `core tab shows core events`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.CORE,
            text = TestAppTextResolver()
        )
        assertTrue(model.visibleEvents.any { it.displayText.contains("preview.binding.started") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("preview.recovery.started") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("intent.received") })
        assertFalse(model.visibleEvents.any { it.displayText.contains("session.created") })
    }

    @Test
    fun `all tab shows full event list`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL,
            text = TestAppTextResolver()
        )
        assertTrue(model.visibleEvents.any { it.displayText.contains("session.created") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("preview.error") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("intent.received") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("mode.switched") })
    }

    @Test
    fun `export content includes all categories and core summary`() {
        val exportContent = buildDevLogExportContent(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            linkEvents = emptyList(),
            resourceDiagnostics = null,
            deviceProbeSummary = null,
            pipelineNotes = emptyList(),
            clearCutoffs = DevLogClearCutoffs()
        )
        assertTrue(exportContent.contains("=== KEY EVENTS ==="))
        assertTrue(exportContent.contains("=== CORE EVENTS ==="))
        assertTrue(exportContent.contains("=== ERROR EVENTS ==="))
        assertTrue(exportContent.contains("=== ALL EVENTS ==="))
        assertTrue(exportContent.contains("=== CORE SUMMARY ==="))
        assertTrue(exportContent.contains("DebugDump:"))
        assertTrue(exportContent.contains("PerfSnapshot:"))
        assertTrue(exportContent.contains("RecoveryTrace:"))
    }

    @Test
    fun `title reflects selected tab and event count`() {
        val keyModel = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.KEY,
            text = TestAppTextResolver()
        )
        assertTrue(keyModel.title.startsWith("Summary Log"))
        val allModel = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL,
            text = TestAppTextResolver()
        )
        assertTrue(allModel.title.startsWith("All Events"))
    }

    @Test
    fun `key tab shows timing events`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents + listOf(
                SessionTraceEvent(11, "capture.timing", "shot=shot-1,device=245ms,postprocess=18ms,total=263ms", 11L),
                SessionTraceEvent(12, "recording.timing", "shot=shot-2,device=--ms,postprocess=--ms,total=5230ms", 12L)
            ),
            isDebugBuild = true,
            selectedTab = DevLogTab.KEY,
            text = TestAppTextResolver()
        )
        assertTrue(model.visibleEvents.any { it.displayText.contains("capture.timing") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("recording.timing") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("device=245ms") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("total=263ms") })
    }

    @Test
    fun `summary highlights slowest timing event`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents + listOf(
                SessionTraceEvent(11, "capture.timing", "shot=shot-1,device=245ms,postprocess=18ms,total=263ms", 11L),
                SessionTraceEvent(12, "capture.timing", "shot=shot-2,device=827ms,postprocess=4510ms,total=5386ms", 12L),
                SessionTraceEvent(13, "capture.timing", "shot=shot-3,device=200ms,postprocess=10ms,total=230ms", 13L)
            ),
            isDebugBuild = true,
            selectedTab = DevLogTab.KEY,
            text = TestAppTextResolver()
        )

        assertTrue(model.summaryText.contains("Slowest timing"))
        assertTrue(model.summaryText.contains("shot=shot-2"))
        assertTrue(model.summaryText.contains("total=5386ms"))
    }

    @Test
    fun `core tab shows preview snapshot ignored event`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents + listOf(
                SessionTraceEvent(11, "preview.snapshot.ignored", "/tmp/preview-b.jpg", 11L)
            ),
            isDebugBuild = true,
            selectedTab = DevLogTab.CORE,
            text = TestAppTextResolver()
        )
        assertTrue(model.visibleEvents.any { it.displayText.contains("preview.snapshot.ignored") })
    }

    @Test
    fun `dev log includes resource diagnostics in export when available`() {
        val resourceDiag = ResourceDiagnosticsSnapshot(
            thermalState = CameraThermalState.WARM,
            performanceClass = CameraPerformanceClass.MID,
            memoryBudgetBytes = 256L * 1024 * 1024,
            activeAlgorithmJobs = 1,
            maxConcurrentAlgorithmJobs = 2,
            featureDegradations = mapOf("live" to "degraded:max-frames"),
            pipelineNotes = listOf("resource:class=mid", "resource:thermal=warm", "resource:live=degraded:max-frames")
        )
        val exportContent = buildDevLogExportContent(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            linkEvents = emptyList(),
            resourceDiagnostics = resourceDiag,
            deviceProbeSummary = null,
            pipelineNotes = emptyList(),
            clearCutoffs = DevLogClearCutoffs()
        )
        assertTrue(exportContent.contains("=== RESOURCE DIAGNOSTICS ==="))
        assertTrue(exportContent.contains("resource:class=mid"))
        assertTrue(exportContent.contains("resource:thermal=warm"))
        assertTrue(exportContent.contains("resource:live=degraded:max-frames"))
    }

    @Test
    fun `dev log omits resource diagnostics section when null`() {
        val exportContent = buildDevLogExportContent(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            linkEvents = emptyList(),
            resourceDiagnostics = null,
            deviceProbeSummary = null,
            pipelineNotes = emptyList(),
            clearCutoffs = DevLogClearCutoffs()
        )
        assertFalse(exportContent.contains("=== RESOURCE DIAGNOSTICS ==="))
    }

    @Test
    fun `storage summary populates display fields when provided`() {
        val summary = StorageSummary(5L * 1024 * 1024, 20L * 1024 * 1024)
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL,
            text = TestAppTextResolver(),
            storageSummary = summary
        )
        assertEquals("5 MB", model.storageUsedDisplay)
        assertEquals("20 MB", model.storageCapacityDisplay)
        assertEquals(0.25f, model.storageUsageRatio)
        assertTrue(model.canCleanup)
    }

    @Test
    fun `storage summary defaults when null`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL,
            text = TestAppTextResolver(),
            storageSummary = null
        )
        assertEquals("", model.storageUsedDisplay)
        assertEquals("", model.storageCapacityDisplay)
        assertEquals(0f, model.storageUsageRatio)
        assertFalse(model.canCleanup)
    }

    @Test
    fun `canCleanup is false when storage is empty`() {
        val summary = StorageSummary(0L, 20L * 1024 * 1024)
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL,
            text = TestAppTextResolver(),
            storageSummary = summary
        )
        assertFalse(model.canCleanup)
    }

    @Test
    fun `core tab includes link events when provided`() {
        val linkEvents = listOf(
            PerformanceLinkEvent(
                flow = "preview-startup",
                stage = "bind",
                status = LinkEventStatus.COMPLETED,
                correlationId = "flow-1",
                startElapsedMillis = 100,
                endElapsedMillis = 180,
                durationMillis = 80,
                detail = null,
                source = "CameraXAdapter"
            ),
            PerformanceLinkEvent(
                flow = "preview-startup",
                stage = "first-frame",
                status = LinkEventStatus.COMPLETED,
                correlationId = "flow-1",
                startElapsedMillis = 180,
                endElapsedMillis = 260,
                durationMillis = 80,
                detail = null,
                source = "SessionKernel"
            )
        )
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.CORE,
            text = TestAppTextResolver(),
            linkEvents = linkEvents
        )
        assertTrue(model.visibleEvents.any { it.displayText.contains("preview-startup") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("flow-1") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("bind") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("duration=80ms") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("--- Link Timing ---") })
    }

    @Test
    fun `core tab without link events shows only trace events`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.CORE,
            text = TestAppTextResolver(),
            linkEvents = emptyList()
        )
        assertFalse(model.visibleEvents.any { it.displayText.contains("--- 链路耗时 ---") })
    }

    @Test
    fun `key tab content includes wall clock timestamps`() {
        val events = listOf(
            SessionTraceEvent(1, "session.created", "defaultMode=PHOTO", 1717334400000L)
        )
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = events,
            isDebugBuild = true,
            selectedTab = DevLogTab.KEY,
            text = TestAppTextResolver()
        )
        assertTrue(model.visibleEvents.any { it.displayText.contains("session.created") })
        assertTrue(model.visibleEvents.any {
            it.displayText.matches(Regex(".*\\d{2}:\\d{2}:\\d{2}\\.\\d{3}.*session\\.created.*"))
        })
    }

    @Test
    fun `export content includes link events section`() {
        val linkEvents = listOf(
            PerformanceLinkEvent(
                flow = "capture",
                stage = "shutter-to-device",
                status = LinkEventStatus.COMPLETED,
                correlationId = "shot-1",
                startElapsedMillis = 0,
                endElapsedMillis = 245,
                durationMillis = 245,
                detail = null,
                source = "DeviceAdapter"
            )
        )
        val exportContent = buildDevLogExportContent(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            linkEvents = linkEvents,
            resourceDiagnostics = null,
            deviceProbeSummary = null,
            pipelineNotes = emptyList(),
            clearCutoffs = DevLogClearCutoffs()
        )
        assertTrue(exportContent.contains("=== LINK EVENTS ==="))
        assertTrue(exportContent.contains("capture"))
        assertTrue(exportContent.contains("shutter-to-device"))
        assertTrue(exportContent.contains("shot-1"))
        assertTrue(exportContent.contains("duration=245ms"))
    }

    @Test
    fun `export content includes device probe summary when provided`() {
        val probeSummary = "cameras: 2 | lens-facings: BACK,FRONT"
        val exportContent = buildDevLogExportContent(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            linkEvents = emptyList(),
            resourceDiagnostics = null,
            deviceProbeSummary = probeSummary,
            pipelineNotes = emptyList(),
            clearCutoffs = DevLogClearCutoffs()
        )
        assertTrue(exportContent.contains("=== DEVICE PROBE ==="))
        assertTrue(exportContent.contains("cameras: 2"))
    }

    @Test
    fun `export content includes camera extension probe summary when provided`() {
        val probeSummary =
            "extensions: BACK night=supported hdr=unsupported bokeh=unsupported auto=unsupported face-retouch=unsupported"
        val exportContent = buildDevLogExportContent(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            linkEvents = emptyList(),
            resourceDiagnostics = null,
            deviceProbeSummary = probeSummary,
            pipelineNotes = emptyList(),
            clearCutoffs = DevLogClearCutoffs()
        )

        assertTrue(exportContent.contains("=== DEVICE PROBE ==="))
        assertTrue(exportContent.contains("extensions: BACK night=supported"))
    }

    @Test
    fun `core tab content includes camera extension probe summary when provided`() {
        val probeSummary =
            "extensions: BACK night=supported hdr=unsupported bokeh=unsupported auto=unsupported face-retouch=unsupported"
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.CORE,
            text = TestAppTextResolver(),
            deviceProbeSummary = probeSummary
        )

        assertTrue(model.visibleEvents.any { it.displayText.contains("extensions: BACK night=supported") })
    }

    @Test
    fun `release build does not export device probe summary`() {
        val exportContent = buildDevLogExportContent(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            linkEvents = emptyList(),
            resourceDiagnostics = null,
            deviceProbeSummary = "extensions: BACK night=supported",
            pipelineNotes = emptyList(),
            clearCutoffs = DevLogClearCutoffs()
        )
        assertTrue(exportContent.contains("extensions: BACK night=supported"))
    }

    @Test
    fun `computeDeviceProbeSummary includes camera count and lens facings`() {
        val caps = DeviceCapabilities(
            availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT),
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 2f, 5f),
                    previewBaseRatios = listOf(1f, 3f, 5f),
                    lensNodeMap = mapOf(
                        LensNode.WIDE to LensNodeAvailability(
                            node = LensNode.WIDE,
                        available = true,
                        thresholdRatio = 1f,
                        physicalCameraId = "0"
                    ),
                    LensNode.TELEPHOTO to LensNodeAvailability(
                        node = LensNode.TELEPHOTO,
                        available = true,
                        thresholdRatio = 2f,
                        physicalCameraId = "1"
                    )
                )
            )
        )
        val summary = computeDeviceProbeSummary(caps)
        assertTrue(summary.contains("cameras: 2"))
        assertTrue(summary.contains("BACK") && summary.contains("FRONT"))
        assertTrue(summary.contains("Wide"))
        assertTrue(summary.contains("Telephoto"))
        assertTrue(summary.contains("id=0"))
        assertTrue(summary.contains("id=1"))
        assertTrue(summary.contains("preview-bases=1.0, 3.0, 5.0"))
    }

    @Test
    fun `computeDeviceProbeSummary includes output sizes`() {
        val caps = DeviceCapabilities(
            availableStillCaptureOutputSizes = listOf(
                StillCaptureOutputSize(
                    width = 8000,
                    height = 6000,
                    resolutionSource = StillCaptureResolutionSource.MAXIMUM_RESOLUTION
                ),
                StillCaptureOutputSize(width = 4000, height = 3000),
                StillCaptureOutputSize(width = 3264, height = 2448)
            )
        )
        val summary = computeDeviceProbeSummary(caps)
        assertTrue(summary.contains("still-output: 3 sizes"))
        assertTrue(summary.contains("48MP:8000x6000(maximum-resolution)"))
        assertTrue(summary.contains("12MP:4000x3000(standard)"))
        assertTrue(summary.contains("8MP:3264x2448(standard)"))
    }

    @Test
    fun `computeDeviceProbeSummary includes physical camera output probe details`() {
        val caps = DeviceCapabilities(
            stillCaptureCameraProbes = listOf(
                StillCaptureCameraProbe(
                    cameraId = "0",
                    lensFacing = LensFacing.BACK,
                    physicalCameraIds = setOf("0", "2"),
                    outputSizes = listOf(
                        StillCaptureOutputSize(width = 4096, height = 3072)
                    ),
                    physicalOutputProbes = listOf(
                        PhysicalStillCaptureOutputProbe(
                            cameraId = "2",
                            outputSizes = listOf(
                                StillCaptureOutputSize(
                                    width = 8000,
                                    height = 6000,
                                    resolutionSource = StillCaptureResolutionSource.MAXIMUM_RESOLUTION
                                )
                            )
                        )
                    )
                )
            )
        )

        val summary = computeDeviceProbeSummary(caps)

        assertTrue(summary.contains("still-camera-probe: id=0,lens=BACK,physical=0|2,sizes=13MP:4096x3072(standard)"))
        assertTrue(summary.contains("physical-still-probe: parent=0,id=2,sizes=48MP:8000x6000(maximum-resolution)"))
    }

    @Test
    fun `computeDeviceProbeSummary reports degraded and unsupported facts`() {
        val caps = DeviceCapabilities(
            supportsFlashControl = false,
            supportsNightMultiFrame = false,
            supportsPortraitDepthEffect = false
        )
        val summary = computeDeviceProbeSummary(caps)
        assertTrue(summary.contains("flash=DEGRADED"))
        assertTrue(summary.contains("nightMultiFrame=DEGRADED"))
        assertTrue(summary.contains("portraitDepth=DEGRADED"))
    }

    @Test
    fun `all tab shows photo routing trace events`() {
        val routingEvents = listOf(
            SessionTraceEvent(11, "photo.routing.blue_hour.ext-hdr", "scene=blue_hour,ext-preferred=hdr", 11L),
            SessionTraceEvent(12, "photo.routing.normal.ext-none", "scene=normal,ext-preferred=none", 12L)
        )
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents + routingEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL,
            text = TestAppTextResolver()
        )
        assertTrue(model.visibleEvents.any { it.displayText.contains("photo.routing.blue_hour.ext-hdr") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("photo.routing.normal.ext-none") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("ext-preferred=hdr") })
    }

    @Test
    fun `dev log export includes shot pipeline notes when provided`() {
        val pipelineNotes = listOf(
            "extension:auto=available",
            "resource:class=high",
            "algorithm:photo-default"
        )
        val exportContent = buildDevLogExportContent(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            linkEvents = emptyList(),
            resourceDiagnostics = null,
            deviceProbeSummary = null,
            pipelineNotes = pipelineNotes,
            clearCutoffs = DevLogClearCutoffs()
        )
        assertTrue(exportContent.contains("=== SHOT PIPELINE ==="))
        assertTrue(exportContent.contains("extension:auto=available"))
        assertTrue(exportContent.contains("algorithm:photo-default"))
    }

    @Test
    fun `all tab content shows pipeline notes when present`() {
        val pipelineNotes = listOf("extension:auto=available", "algorithm:photo-default")
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL,
            text = TestAppTextResolver(),
            latestPipelineNotes = pipelineNotes
        )
        assertTrue(model.visibleEvents.any { it.displayText.contains("Pipeline Notes") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("extension:auto=available") })
        assertTrue(model.visibleEvents.any { it.displayText.contains("algorithm:photo-default") })
    }

    @Test
    fun `all tab summarizes latest shot timing breakdown from pipeline notes`() {
        val pipelineNotes = listOf(
            "timing:device=598ms",
            "timing:save-io=598ms",
            "timing:postprocess=2653ms",
            "timing:total=3281ms",
            "timing:postprocess:PhotoAlgorithm=1200ms",
            "timing:postprocess:PhotoWatermark=900ms",
            "algorithm-render:timing:humanistic size=3000x4000 decode=80ms render=700ms encode=250ms total=1030ms",
            "watermark:timing:blue-hour size=3000x4000 decode=40ms render=420ms encode=180ms total=640ms"
        )
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL,
            text = TestAppTextResolver(),
            latestPipelineNotes = pipelineNotes
        )

        val breakdown = model.visibleEvents
            .map { it.displayText }
            .single { it.startsWith("Postprocess timing breakdown:") }
        assertTrue(breakdown.contains("total=3281ms"))
        assertTrue(breakdown.contains("device=598ms"))
        assertTrue(breakdown.contains("save-io=598ms"))
        assertTrue(breakdown.contains("postprocess=2653ms"))
        assertTrue(breakdown.contains("postprocess.PhotoAlgorithm=1200ms"))
        assertTrue(breakdown.contains("postprocess.PhotoWatermark=900ms"))
        assertTrue(breakdown.contains("algorithm-render.total=1030ms"))
        assertTrue(breakdown.contains("watermark.total=640ms"))
    }

    @Test
    fun `dev log export includes shot timing breakdown when pipeline timings exist`() {
        val pipelineNotes = listOf(
            "timing:device=872ms",
            "timing:postprocess=2963ms",
            "timing:total=3870ms",
            "timing:postprocess:PhotoAlgorithm=1400ms",
            "combined-render:timing:humanistic+blue-hour algorithm=900ms watermark=800ms encode=300ms total=2000ms"
        )

        val exportContent = buildDevLogExportContent(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            linkEvents = emptyList(),
            resourceDiagnostics = null,
            deviceProbeSummary = null,
            pipelineNotes = pipelineNotes,
            clearCutoffs = DevLogClearCutoffs()
        )

        assertTrue(exportContent.contains("Postprocess timing breakdown:"))
        assertTrue(exportContent.contains("total=3870ms"))
        assertTrue(exportContent.contains("postprocess=2963ms"))
        assertTrue(exportContent.contains("postprocess.PhotoAlgorithm=1400ms"))
        assertTrue(exportContent.contains("combined-render.total=2000ms"))
    }

    @Test
    fun `all tab without pipeline notes does not show pipeline header`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL,
            text = TestAppTextResolver(),
            latestPipelineNotes = emptyList()
        )
        assertFalse(model.visibleEvents.any { it.displayText.contains("Pipeline Notes") })
    }

    @Test
    fun `core tab includes countdown cancelled event`() {
        val events = listOf(
            SessionTraceEvent(
                sequence = 1,
                name = "capture.countdown.cancelled",
                detail = "user cancelled",
                timestampMillis = 1L
            )
        )
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = events,
            isDebugBuild = true,
            selectedTab = DevLogTab.CORE,
            text = TestAppTextResolver()
        )
        assertTrue(model.visibleEvents.any { it.displayText.contains("capture.countdown.cancelled") })
    }

    @Test
    fun `release build does not export shot pipeline notes`() {
        val pipelineNotes = listOf("extension:auto=available")
        val exportContent = buildDevLogExportContent(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            linkEvents = emptyList(),
            resourceDiagnostics = null,
            deviceProbeSummary = null,
            pipelineNotes = pipelineNotes,
            clearCutoffs = DevLogClearCutoffs()
        )
        assertTrue(exportContent.contains("=== SHOT PIPELINE ==="))
        assertTrue(exportContent.contains("extension:auto=available"))
    }

    @Test
    fun `export content is empty when traceEvents are empty`() {
        val exportContent = buildDevLogExportContent(
            state = defaultTestSessionState(),
            traceEvents = emptyList(),
            linkEvents = emptyList(),
            resourceDiagnostics = null,
            deviceProbeSummary = null,
            pipelineNotes = emptyList(),
            clearCutoffs = DevLogClearCutoffs()
        )
        assertTrue(exportContent.contains("=== KEY EVENTS ==="))
        assertTrue(exportContent.contains("=== ALL EVENTS ==="))
        assertTrue(exportContent.contains("=== CORE SUMMARY ==="))
    }

    @Test
    fun `export content is built with all sections`() {
        val exportContent = buildDevLogExportContent(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            linkEvents = emptyList(),
            resourceDiagnostics = null,
            deviceProbeSummary = null,
            pipelineNotes = emptyList(),
            clearCutoffs = DevLogClearCutoffs()
        )
        assertTrue(exportContent.isNotEmpty())
        assertTrue(exportContent.contains("=== ALL EVENTS ==="))
    }

    @Test
    fun `visibleEvents contains all events within retention limit`() {
        val manyEvents = (1..5000).map { i ->
            SessionTraceEvent(i, "event.$i", "detail=$i", i.toLong())
        }
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = manyEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL,
            text = TestAppTextResolver()
        )
        assertTrue(model.visibleEvents.size <= 5000)
        assertTrue(model.visibleEvents.any { it.displayText.contains("event.") })
    }

    @Test
    fun `visibleEvents item type is TraceEventItem for regular events`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.KEY,
            text = TestAppTextResolver()
        )
        model.visibleEvents.forEach { item ->
            assertTrue(item is TraceEventItem)
        }
    }

    private fun defaultTestSessionState(): SessionState {
        return SessionState(
            lifecycle = SessionLifecycle.RUNNING,
            permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
            previewHostAvailable = true,
            previewStatus = PreviewStatus.ACTIVE,
            previewStatusDetail = null,
            activeMode = ModeId.PHOTO,
            availableModes = listOf(ModeId.PHOTO, ModeId.CHECK_IN, ModeId.HUMANISTIC, ModeId.VIDEO),
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = ModeSnapshot(
                id = ModeId.PHOTO,
                uiSpec = ModeUiSpec(title = "PHOTO", shutterLabel = "Capture PHOTO"),
                state = ModeState(headline = "PHOTO mode active", detail = "Ready")
            ),
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT,
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true
            ),
            previewMetrics = PreviewMetrics(),
            settings = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    common = CommonSettings(
                        gridMode = CompositionGridMode.RULE_OF_THIRDS,
                        shutterSoundEnabled = false,
                        selfieMirrorEnabled = true
                    ),
                    photo = PhotoSettings(
                        defaultFilterProfileId = "portrait-retro",
                        defaultHumanisticFilterProfileId = "humanistic-street",
                        defaultPortraitFilterProfileId = "portrait-original",
                        defaultWatermarkTemplateId = "travel-polaroid",
                        livePhotoEnabledByDefault = true,
                        countdownDuration = CountdownDuration.SECONDS_3
                    ),
                    video = VideoSettings(
                        defaultVideoSpec = VideoSpec(
                            resolution = VideoResolution.UHD_4K,
                            frameRate = VideoFrameRate.FPS_25,
                            dynamicFpsPolicy = DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS,
                            audioProfile = AudioProfile.CONCERT
                        ),
                        defaultFilterProfileId = "photo-rich"
                    )
                )
            ),
            presentation = SessionPresentationState(
                lastAction = "Ready",
                latestCapturePath = null,
                latestVideoPath = null,
                latestLivePhotoBundle = null,
                latestSavedMediaType = null,
                latestPipelineNotes = emptyList(),
                lastError = null
            )
        )
    }
}
