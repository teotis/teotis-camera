package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.CameraPerformanceClass
import com.opencamera.core.media.CameraThermalState
import com.opencamera.core.media.ResourceDiagnosticsSnapshot
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PermissionState
import com.opencamera.core.session.PreviewMetrics
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SessionLifecycle
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
import com.opencamera.core.session.SessionPresentationState
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Regression tests verifying that multi-frame fusion diagnostics
 * are surfaced through the dev log export for developer visibility.
 */
class FusionDiagnosticsDevLogTest {

    private val baseEvents = listOf(
        SessionTraceEvent(1, "session.created", "defaultMode=PHOTO", 1L),
        SessionTraceEvent(2, "session.booted", "mode=PHOTO", 2L)
    )

    @Test
    fun `dev log export surfaces fusion applied notes from resource diagnostics`() {
        val resourceDiag = ResourceDiagnosticsSnapshot(
            thermalState = CameraThermalState.NORMAL,
            performanceClass = CameraPerformanceClass.HIGH,
            memoryBudgetBytes = 512L * 1024 * 1024,
            activeAlgorithmJobs = 0,
            maxConcurrentAlgorithmJobs = 2,
            featureDegradations = emptyMap(),
            pipelineNotes = listOf(
                "merge:applied=true",
                "merge:strategy=pixel-average",
                "merge:inputs=3",
                "merge:reference-frame=2",
                "merge:motion-policy=lowest-score=0.12"
            )
        )
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = baseEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL,
            text = TestAppTextResolver(),
            resourceDiagnostics = resourceDiag
        )
        assertTrue(model.exportContent.contains("merge:applied=true"),
            "Dev log should surface merge:applied=true")
        assertTrue(model.exportContent.contains("merge:strategy=pixel-average"),
            "Dev log should surface merge strategy")
        assertTrue(model.exportContent.contains("merge:inputs=3"),
            "Dev log should surface input frame count")
        assertTrue(model.exportContent.contains("merge:reference-frame=2"),
            "Dev log should surface reference frame index")
    }

    @Test
    fun `dev log export surfaces fusion degraded notes from resource diagnostics`() {
        val resourceDiag = ResourceDiagnosticsSnapshot(
            thermalState = CameraThermalState.NORMAL,
            performanceClass = CameraPerformanceClass.HIGH,
            memoryBudgetBytes = 512L * 1024 * 1024,
            activeAlgorithmJobs = 0,
            maxConcurrentAlgorithmJobs = 2,
            featureDegradations = emptyMap(),
            pipelineNotes = listOf(
                "merge:applied=false",
                "merge:strategy=best-frame",
                "merge:skipped=insufficient-valid-frames",
                "merge:inputs=1",
                "degraded:multi-frame-fusion"
            )
        )
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = baseEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL,
            text = TestAppTextResolver(),
            resourceDiagnostics = resourceDiag
        )
        assertTrue(model.exportContent.contains("merge:applied=false"),
            "Dev log should surface merge:applied=false")
        assertTrue(model.exportContent.contains("degraded:multi-frame-fusion"),
            "Dev log should surface degradation label")
        assertTrue(model.exportContent.contains("merge:skipped=insufficient-valid-frames"),
            "Dev log should surface skip reason")
    }

    @Test
    fun `dev log omits fusion notes when resource diagnostics is null`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = baseEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL,
            text = TestAppTextResolver(),
            resourceDiagnostics = null
        )
        assertFalse(model.exportContent.contains("merge:"),
            "No merge notes should appear without resource diagnostics")
    }

    @Test
    fun `dev log export surfaces pixel average fusion details`() {
        val resourceDiag = ResourceDiagnosticsSnapshot(
            thermalState = CameraThermalState.NORMAL,
            performanceClass = CameraPerformanceClass.HIGH,
            memoryBudgetBytes = 512L * 1024 * 1024,
            activeAlgorithmJobs = 0,
            maxConcurrentAlgorithmJobs = 2,
            featureDegradations = emptyMap(),
            pipelineNotes = listOf(
                "merge:applied=true",
                "merge:strategy=pixel-average",
                "merge:pixel-average-frames=6",
                "merge:pixel-average-max-dim=1600",
                "merge:pixel-averaged=6-frames"
            )
        )
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = baseEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL,
            text = TestAppTextResolver(),
            resourceDiagnostics = resourceDiag
        )
        assertTrue(model.exportContent.contains("merge:pixel-average-frames=6"),
            "Dev log should show pixel average frame count")
        assertTrue(model.exportContent.contains("merge:pixel-average-max-dim=1600"),
            "Dev log should show pixel average max dimension")
    }

    private fun defaultTestSessionState(): SessionState = SessionState(
        lifecycle = SessionLifecycle.RUNNING,
        permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
        previewHostAvailable = true,
        previewStatus = PreviewStatus.ACTIVE,
        previewStatusDetail = null,
        activeMode = ModeId.PHOTO,
        availableModes = listOf(ModeId.PHOTO, ModeId.CHECK_IN),
        captureStatus = CaptureStatus.IDLE,
        recordingStatus = RecordingStatus.IDLE,
        activeShot = null,
        modeSnapshot = ModeSnapshot(
            id = ModeId.PHOTO,
            uiSpec = ModeUiSpec(title = "Photo", shutterLabel = "Capture"),
            state = ModeState(headline = "Photo mode active", detail = "Ready")
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
}
