package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
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
            selectedTab = DevLogTab.KEY
        )
        assertTrue(model.isAvailable)
    }

    @Test
    fun `release build makes dev log unavailable`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = false,
            selectedTab = DevLogTab.KEY
        )
        assertFalse(model.isAvailable)
        assertEquals("", model.content)
        assertEquals("", model.exportContent)
    }

    @Test
    fun `key tab shows only key events`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.KEY
        )
        assertTrue(model.content.contains("session.created"))
        assertTrue(model.content.contains("session.booted"))
        assertTrue(model.content.contains("preview.first.frame"))
        assertTrue(model.content.contains("mode.switched"))
        assertFalse(model.content.contains("preview.binding.started"))
        assertFalse(model.content.contains("intent.received"))
    }

    @Test
    fun `error tab shows errors and blocked events`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ERROR
        )
        assertTrue(model.content.contains("preview.error"))
        assertTrue(model.content.contains("zoom.switch.blocked"))
        assertTrue(model.content.contains("lens.switch.skipped"))
        assertFalse(model.content.contains("session.created"))
    }

    @Test
    fun `core tab shows core events`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.CORE
        )
        assertTrue(model.content.contains("preview.binding.started"))
        assertTrue(model.content.contains("preview.recovery.started"))
        assertTrue(model.content.contains("intent.received"))
        assertFalse(model.content.contains("session.created"))
    }

    @Test
    fun `all tab shows full event list`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL
        )
        assertTrue(model.content.contains("session.created"))
        assertTrue(model.content.contains("preview.error"))
        assertTrue(model.content.contains("intent.received"))
        assertTrue(model.content.contains("mode.switched"))
    }

    @Test
    fun `export content includes all categories and core summary`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL
        )
        assertTrue(model.exportContent.contains("=== KEY EVENTS ==="))
        assertTrue(model.exportContent.contains("=== CORE EVENTS ==="))
        assertTrue(model.exportContent.contains("=== ERROR EVENTS ==="))
        assertTrue(model.exportContent.contains("=== ALL EVENTS ==="))
        assertTrue(model.exportContent.contains("=== CORE SUMMARY ==="))
        assertTrue(model.exportContent.contains("DebugDump:"))
        assertTrue(model.exportContent.contains("PerfSnapshot:"))
        assertTrue(model.exportContent.contains("RecoveryTrace:"))
    }

    @Test
    fun `title reflects selected tab and event count`() {
        val keyModel = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.KEY
        )
        assertTrue(keyModel.title.startsWith("Key Log"))
        val allModel = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents,
            isDebugBuild = true,
            selectedTab = DevLogTab.ALL
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
            selectedTab = DevLogTab.KEY
        )
        assertTrue(model.content.contains("capture.timing"))
        assertTrue(model.content.contains("recording.timing"))
        assertTrue(model.content.contains("device=245ms"))
        assertTrue(model.content.contains("total=263ms"))
    }

    @Test
    fun `core tab shows preview snapshot ignored event`() {
        val model = devLogRenderModel(
            state = defaultTestSessionState(),
            traceEvents = sampleTraceEvents + listOf(
                SessionTraceEvent(11, "preview.snapshot.ignored", "/tmp/preview-b.jpg", 11L)
            ),
            isDebugBuild = true,
            selectedTab = DevLogTab.CORE
        )
        assertTrue(model.content.contains("preview.snapshot.ignored"))
    }

    private fun defaultTestSessionState(): SessionState {
        return SessionState(
            lifecycle = SessionLifecycle.RUNNING,
            permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
            previewHostAvailable = true,
            previewStatus = PreviewStatus.ACTIVE,
            previewStatusDetail = null,
            activeMode = ModeId.PHOTO,
            availableModes = listOf(ModeId.PHOTO, ModeId.NIGHT, ModeId.HUMANISTIC, ModeId.VIDEO),
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
                enablePreviewSnapshots = true,
                qualityPreference = StillCaptureQualityPreference.LATENCY,
                resolutionPreset = StillCaptureResolutionPreset.LARGE_12MP
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
