package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.InMemorySessionTrace
import com.opencamera.core.session.PermissionState
import com.opencamera.core.session.PreviewMetrics
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SessionLifecycle
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.SessionTraceEvent
import com.opencamera.core.session.TraceEventDomain
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
import org.junit.Assume.assumeTrue
import kotlin.test.Test

class DevLogPerformanceBenchmarkTest {

    private val keyEventNames = listOf(
        "session.created", "session.booted", "preview.first.frame",
        "mode.switched", "lens.switched", "capture.photo"
    )
    private val coreEventNames = listOf(
        "preview.binding.started", "preview.recovery.started", "preview.snapshot.updated",
        "capture.countdown.started", "capture.saving"
    )
    private val errorEventNames = listOf(
        "preview.error", "preview.surface.lost", "capture.failed"
    )

    private fun generateTraceEvents(count: Int): List<SessionTraceEvent> {
        val allEventNames = keyEventNames + coreEventNames + errorEventNames
        return (1..count).map { i ->
            val name = allEventNames[i % allEventNames.size]
            SessionTraceEvent(
                sequence = i,
                name = name,
                detail = "detail-$i",
                timestampMillis = i.toLong()
            )
        }
    }

    private fun defaultTestState(): SessionState = SessionState(
        lifecycle = SessionLifecycle.RUNNING,
        permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
        previewHostAvailable = true,
        previewStatus = PreviewStatus.ACTIVE,
        previewStatusDetail = null,
        activeMode = ModeId.PHOTO,
        availableModes = listOf(ModeId.PHOTO),
        captureStatus = CaptureStatus.IDLE,
        recordingStatus = RecordingStatus.IDLE,
        activeShot = null,
        modeSnapshot = ModeSnapshot(
            id = ModeId.PHOTO,
            uiSpec = ModeUiSpec(title = "Photo", shutterLabel = "Capture"),
            state = ModeState(headline = "Photo active", detail = "Ready")
        ),
        activeDeviceCapabilities = DeviceCapabilities.DEFAULT,
        activeDeviceGraph = DeviceGraphSpec.stillCapture(preferredLensFacing = LensFacing.BACK),
        previewMetrics = PreviewMetrics(),
        settings = SessionSettingsSnapshot(
            persisted = PersistedSettings(
                common = CommonSettings(
                    gridMode = CompositionGridMode.RULE_OF_THIRDS,
                    shutterSoundEnabled = false,
                    selfieMirrorEnabled = true
                ),
                photo = PhotoSettings(countdownDuration = CountdownDuration.OFF),
                video = VideoSettings(
                    defaultVideoSpec = VideoSpec(
                        resolution = VideoResolution.UHD_4K,
                        frameRate = VideoFrameRate.FPS_25,
                        dynamicFpsPolicy = DynamicVideoFpsPolicy.LOCKED,
                        audioProfile = AudioProfile.STANDARD
                    )
                )
            )
        ),
        presentation = SessionPresentationState(
            lastAction = "Ready",
            latestPipelineNotes = emptyList()
        )
    )

    @Test
    fun `benchmark render and export at various event counts`() {
        assumeTrue("Skip benchmark on CI", System.getenv("CI") != "true")
        val state = defaultTestState()

        listOf(1000, 5000, 20000).forEach { eventCount ->
            val events = generateTraceEvents(eventCount)

            val renderStart = System.nanoTime()
            devLogRenderModel(
                state = state,
                traceEvents = events,
                isDebugBuild = true,
                selectedTab = DevLogTab.ALL,
                text = TestAppTextResolver()
            )
            val renderMs = (System.nanoTime() - renderStart) / 1_000_000.0
            println("[bench] render@$eventCount=${"%.1f".format(renderMs)}ms")

            val exportStart = System.nanoTime()
            buildDevLogExportContent(
                state = state,
                traceEvents = events,
                linkEvents = emptyList(),
                resourceDiagnostics = null,
                deviceProbeSummary = null,
                pipelineNotes = emptyList(),
                clearCutoffs = DevLogClearCutoffs()
            )
            val exportMs = (System.nanoTime() - exportStart) / 1_000_000.0
            println("[bench] export@$eventCount=${"%.1f".format(exportMs)}ms")

            when (eventCount) {
                5000 -> {
                    if (renderMs >= 100.0) throw AssertionError("render@5000=${"%.1f".format(renderMs)}ms exceeded 100ms hard threshold")
                    if (exportMs >= 200.0) throw AssertionError("export@5000=${"%.1f".format(exportMs)}ms exceeded 200ms hard threshold")
                }
                20000 -> {
                    if (renderMs >= 400.0) throw AssertionError("render@20000=${"%.1f".format(renderMs)}ms exceeded 400ms hard threshold")
                    if (exportMs >= 1000.0) throw AssertionError("export@20000=${"%.1f".format(exportMs)}ms exceeded 1000ms hard threshold")
                }
            }
        }
    }

    @Test
    fun `benchmark record amortized per-event cost`() {
        assumeTrue("Skip benchmark on CI", System.getenv("CI") != "true")
        listOf(1000, 5000, 20000).forEach { eventCount ->
            val trace = InMemorySessionTrace(memoryRetention = eventCount + 100)
            val start = System.nanoTime()
            repeat(eventCount) { i ->
                trace.record("event.${i % 20}", "detail-$i")
            }
            val totalMs = (System.nanoTime() - start) / 1_000_000.0
            val perEventUs = (totalMs * 1000.0) / eventCount
            println("[bench] record@$eventCount=${"%.1f".format(totalMs)}ms (avg=${"%.2f".format(perEventUs)}us/event)")

            if (eventCount == 20000 && perEventUs >= 100.0) {
                throw AssertionError("record@$eventCount avg=${"%.1f".format(perEventUs)}us/event exceeded 100us hard threshold")
            }
        }
    }
}
