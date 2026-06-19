package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.media.CameraPerformanceClass
import com.opencamera.core.media.CameraThermalState
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.ResourceDiagnosticsSnapshot
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PermissionState
import com.opencamera.core.session.PreviewMetrics
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SavedMediaType
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionDiagnosticsRenderModelTest {


        @Test
        fun `session diagnostics text renders debug dump perf snapshot and recovery trace`() {
            val state = defaultSessionState(
                previewStatus = PreviewStatus.ACTIVE,
                previewMetrics = PreviewMetrics(
                    bindCount = 4,
                    recoveryCount = 2,
                    lastFirstFrameLatencyMillis = 88,
                    bestFirstFrameLatencyMillis = 80,
                    worstFirstFrameLatencyMillis = 120,
                    lastStartReason = "recover after provider restart"
                ),
                lastError = "provider restarted"
            )
            val traceEvents = listOf(
                SessionTraceEvent(1, "preview.surface.lost", "provider restarted", 1L),
                SessionTraceEvent(2, "preview.recovery.started", "recover after provider restart", 2L),
                SessionTraceEvent(3, "preview.first.frame", "88ms", 3L),
                SessionTraceEvent(4, "intent.received", "PreviewFirstFrameAvailable", 4L)
            )

            val diagnostics = sessionDiagnosticsText(state, traceEvents)

            assertTrue(diagnostics.contains("DebugDump: RUNNING | PHOTO | preview=ACTIVE"))
            assertTrue(
                diagnostics.contains(
                    "PerfSnapshot: last=88 ms, best=80 ms, worst=120 ms, binds=4, recoveries=2, budget=within budget (Recovery, warn=180 ms, fail=320 ms)"
                )
            )
            assertTrue(
                diagnostics.contains(
                    "RecoveryTrace: idle, last=recover after provider restart, recoveredFrame=88 ms, failure=provider restarted"
                )
            )
            assertTrue(diagnostics.contains("Error: provider restarted"))
            assertTrue(diagnostics.contains("2. preview.recovery.started -> recover after provider restart"))
        }



        @Test
        fun `session diagnostics text includes resource diagnostics when present`() {
            val state = defaultSessionState(previewStatus = PreviewStatus.ACTIVE)
            val resourceDiag = ResourceDiagnosticsSnapshot(
                thermalState = CameraThermalState.WARM,
                performanceClass = CameraPerformanceClass.MID,
                memoryBudgetBytes = 256L * 1024 * 1024,
                activeAlgorithmJobs = 1,
                maxConcurrentAlgorithmJobs = 2,
                featureDegradations = mapOf("live" to "degraded:max-frames"),
                pipelineNotes = listOf("resource:class=mid", "resource:thermal=warm")
            )
            val diagnostics = sessionDiagnosticsText(
                state = state,
                traceEvents = emptyList(),
                resourceDiagnostics = resourceDiag
            )
            assertTrue(diagnostics.contains("Resource: thermal=warm | class=mid | memory=256MB | jobs=1/2 | degradations=live=degraded:max-frames"))
        }



        @Test
        fun `capture output preview thumbnail hides raw file path`() {
            val state = defaultSessionState().copy(
                presentation = defaultSessionState().presentation.copy(
                    previewThumbnailPath = "/data/user/0/com.opencamera.app/cache/preview-thumbnails/test.jpg"
                )
            )

            assertEquals("Preview thumbnail:", sessionCaptureOutputText(state, strings, TestAppTextResolver()))
        }

        private fun defaultSessionState(
            activeDeviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
            activeDeviceGraph: DeviceGraphSpec = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true
            ),
            previewStatus: PreviewStatus = PreviewStatus.ACTIVE,
            previewMetrics: PreviewMetrics = PreviewMetrics(),
            activeShot: ShotRequest? = null,
            latestSavedMediaType: SavedMediaType? = null,
            latestCapturePath: String? = null,
            latestVideoPath: String? = null,
            latestLivePhotoBundle: LivePhotoBundle? = null,
            latestPipelineNotes: List<String> = emptyList(),
            lastError: String? = null,
            activeMode: ModeId = ModeId.PHOTO,
            availableModes: List<ModeId> = listOf(
                ModeId.PHOTO,
                ModeId.CHECK_IN,
                ModeId.HUMANISTIC,
                ModeId.VIDEO
            ),
            modeSnapshot: ModeSnapshot = ModeSnapshot(
                id = ModeId.PHOTO,
                uiSpec = ModeUiSpec(
                    title = "PHOTO",
                    shutterLabel = "Capture PHOTO"
                ),
                state = ModeState(
                    headline = "PHOTO mode active",
                    detail = "Ready"
                )
            ),
            persistedPhotoSettings: PhotoSettings = PhotoSettings(
                defaultFilterProfileId = "portrait-retro",
                defaultHumanisticFilterProfileId = "humanistic-street",
                defaultPortraitFilterProfileId = "portrait-original",
                defaultWatermarkTemplateId = "travel-polaroid",
                livePhotoEnabledByDefault = true,
                countdownDuration = CountdownDuration.SECONDS_3
            )
        ): SessionState {
            return SessionState(
                lifecycle = SessionLifecycle.RUNNING,
                permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
                previewHostAvailable = true,
                previewStatus = previewStatus,
                previewStatusDetail = null,
                activeMode = activeMode,
                availableModes = availableModes,
                captureStatus = CaptureStatus.IDLE,
                recordingStatus = RecordingStatus.IDLE,
                activeShot = activeShot,
                modeSnapshot = modeSnapshot,
                activeDeviceCapabilities = activeDeviceCapabilities,
                activeDeviceGraph = activeDeviceGraph,
                previewMetrics = previewMetrics,
                settings = SessionSettingsSnapshot(
                    persisted = PersistedSettings(
                        common = CommonSettings(
                            gridMode = CompositionGridMode.RULE_OF_THIRDS,
                            shutterSoundEnabled = false,
                            selfieMirrorEnabled = true
                        ),
                        photo = persistedPhotoSettings,
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
                    latestCapturePath = latestCapturePath,
                    latestVideoPath = latestVideoPath,
                    latestLivePhotoBundle = latestLivePhotoBundle,
                    latestSavedMediaType = latestSavedMediaType,
                    latestPipelineNotes = latestPipelineNotes,
                    lastError = lastError
                )
            )
        }

    companion object {
            private val strings = SessionUiStrings(
                buttonSwitchToFront = "Switch to Front",
                buttonSwitchToBack = "Switch to Back",
                buttonSingleLens = "Single Lens",
                buttonZoomPrefix = "Zoom",
                buttonZoomUnavailable = "Zoom N/A",
                buttonStillFast = "Still Fast",
                buttonStillMax = "Still Max",
                buttonStillQualityUnavailable = "Still N/A",
                buttonStill12Mp = "Still 12MP",
                buttonStill8Mp = "Still 8MP",
                buttonStill2Mp = "Still 2MP",
                buttonStillResolutionUnavailable = "Size N/A",
                outputErrorPrefix = "Camera issue:",
                outputVideoPrefix = "Last video:",
                outputLivePrefix = "Last Live photo:",
                outputSavedPrefix = "Last photo:",
                outputPreviewPrefix = "Preview thumbnail:",
                outputWaiting = "No photo captured yet."
            )
        }

}
