package com.opencamera.core.session

import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.asCapabilityGraphQuery
import com.opencamera.core.device.DeviceRuntimeIssueKind
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.LensNode
import com.opencamera.core.device.LensNodeAvailability
import com.opencamera.core.device.PhotoLowLightStrategySupport
import com.opencamera.core.device.PhotoSceneSignal
import com.opencamera.core.device.PreviewStreamAspect
import com.opencamera.core.device.RecordingQualityPreset
import com.opencamera.core.device.SceneLightState
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.media.FlashMode
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.LiveBundleStatus
import com.opencamera.core.media.LiveMotionSource
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.LiveTemporalWindow
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.LivePhotoCaptureSpec
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotExecutor
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.feature.document.DocumentModePlugin
import com.opencamera.feature.checkin.CheckInModePlugin
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeRegistry
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.DEFAULT_FILTER_PROFILES
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.FeatureCatalog
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.ManualCaptureParams
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.PortraitBeautyPreset
import com.opencamera.core.settings.PortraitBeautyStrength
import com.opencamera.core.settings.PortraitBokehEffect
import com.opencamera.core.settings.PortraitProfile
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSettings
import com.opencamera.core.settings.VideoSpec
import com.opencamera.core.settings.WatermarkFrameBackground
import com.opencamera.core.settings.WatermarkStyleSettings
import com.opencamera.core.settings.WatermarkTextOpacity
import com.opencamera.core.settings.WatermarkTextPlacement
import com.opencamera.core.settings.WatermarkTextScale
import com.opencamera.feature.humanistic.HumanisticModePlugin
import com.opencamera.feature.photo.PhotoModePlugin
import com.opencamera.feature.video.VideoModePlugin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCameraSessionTest {
    @Test
    fun `boot preserves attached preview host state when permission is granted`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        runCurrent()

        assertTrue(session.state.value.previewHostAvailable)
        assertEquals(SessionLifecycle.RUNNING, session.state.value.lifecycle)
        assertTrue(trace.snapshot().any { it.name == "preview.host.attached" })
        assertTrue(trace.snapshot().any { it.name == "session.booted" })
    }

    @Test
    fun `preview surface loss during recording becomes error without recovery bind`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()

        val shot = assertNotNull(session.state.value.activeShot, session.state.value.toString())
        session.dispatch(SessionIntent.ShotStarted(shot))
        session.dispatch(SessionIntent.PreviewSurfaceLost("surface lost while recording"))
        runCurrent()

        assertEquals(PreviewStatus.ERROR, session.state.value.previewStatus)
        assertEquals(
            "Preview surface lost during recording: surface lost while recording",
            session.state.value.lastError
        )
        assertTrue(trace.snapshot().any { it.name == "preview.error" })
    }

    @Test
    fun `preview error requests recovery bind when preview host is still attached`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(
            SessionIntent.PreviewBindingStarted(
                reason = "initial attach",
                isRecovery = false
            )
        )
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(90))
        runCurrent()

        session.dispatch(SessionIntent.PreviewError("camera provider restarted"))
        runCurrent()

        assertEquals(PreviewStatus.ERROR, session.state.value.previewStatus)
        assertEquals("Preview error, attempting recovery", session.state.value.lastAction)
        assertTrue(
            trace.snapshot().any { event ->
                event.name == "preview.recovery.requested" &&
                    event.detail == "recover after preview error: camera provider restarted"
            }
        )

        session.dispatch(
            SessionIntent.PreviewBindingStarted(
                reason = "recover after preview error: camera provider restarted",
                isRecovery = true
            )
        )
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(108))
        runCurrent()

        assertEquals(PreviewStatus.ACTIVE, session.state.value.previewStatus)
        assertEquals(2, session.state.value.previewMetrics.bindCount)
        assertEquals(1, session.state.value.previewMetrics.recoveryCount)
        assertEquals(108, session.state.value.previewMetrics.lastFirstFrameLatencyMillis)
    }

    @Test
    fun `preview error during active recording does not request recovery bind`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot, session.state.value.toString())
        session.dispatch(SessionIntent.ShotStarted(shot))
        session.dispatch(SessionIntent.PreviewError("encoder pipeline unstable"))
        advanceUntilIdle()

        assertEquals(PreviewStatus.ERROR, session.state.value.previewStatus)
        assertEquals("Preview error", session.state.value.lastAction)
        assertTrue(trace.snapshot().none { it.name == "preview.recovery.requested" })
    }

    @Test
    fun `recoverable runtime issue requests recovery bind when preview host is attached`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(
            SessionIntent.PreviewRuntimeIssue(
                DeviceRuntimeIssue(
                    kind = DeviceRuntimeIssueKind.PROVIDER_FAILURE,
                    reason = "camera provider restarted",
                    isRecoverable = true
                )
            )
        )
        runCurrent()

        assertEquals(PreviewStatus.ERROR, session.state.value.previewStatus)
        assertEquals(
            "Preview runtime issue, attempting recovery",
            session.state.value.lastAction
        )
        assertEquals(
            "Provider failure: camera provider restarted",
            session.state.value.lastError
        )
        assertTrue(
            trace.snapshot().any { event ->
                event.name == "preview.runtime.issue" &&
                    event.detail.contains("kind=PROVIDER_FAILURE")
            }
        )
        assertTrue(
            trace.snapshot().any { event ->
                event.name == "preview.recovery.requested" &&
                    event.detail == "recover after provider failure: camera provider restarted"
            }
        )
    }

    @Test
    fun `preview stall runtime issue requests recovery bind with stall specific reason`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(
            SessionIntent.PreviewRuntimeIssue(
                DeviceRuntimeIssue(
                    kind = DeviceRuntimeIssueKind.PREVIEW_STALL,
                    reason = "first frame timed out after 300 ms (Cold start): session boot",
                    isRecoverable = true
                )
            )
        )
        runCurrent()

        assertEquals(PreviewStatus.ERROR, session.state.value.previewStatus)
        assertEquals(
            "Preview runtime issue, attempting recovery",
            session.state.value.lastAction
        )
        assertEquals(
            "Preview stalled: first frame timed out after 300 ms (Cold start): session boot",
            session.state.value.lastError
        )
        assertTrue(
            trace.snapshot().any { event ->
                event.name == "preview.recovery.requested" &&
                    event.detail ==
                    "recover after preview stall: first frame timed out after 300 ms (Cold start): session boot"
            }
        )
    }

    @Test
    fun `fatal runtime issue keeps manual intervention semantics`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(
            SessionIntent.PreviewRuntimeIssue(
                DeviceRuntimeIssue(
                    kind = DeviceRuntimeIssueKind.CAMERA_FATAL,
                    reason = "camera service died",
                    isRecoverable = false
                )
            )
        )
        runCurrent()

        assertEquals(PreviewStatus.ERROR, session.state.value.previewStatus)
        assertEquals(
            "Preview runtime issue, manual intervention required",
            session.state.value.lastAction
        )
        assertEquals(
            "Camera fatal error: camera service died",
            session.state.value.lastError
        )
        assertTrue(trace.snapshot().none { it.name == "preview.recovery.requested" })
    }

    @Test
    fun `runtime issue during active recovery becomes recovery failed without requeueing recovery`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(
            SessionIntent.PreviewBindingStarted(
                reason = "recover after provider failure",
                isRecovery = true
            )
        )
        session.dispatch(
            SessionIntent.PreviewRuntimeIssue(
                DeviceRuntimeIssue(
                    kind = DeviceRuntimeIssueKind.BIND_FAILURE,
                    reason = "rebind failed",
                    isRecoverable = true
                )
            )
        )
        runCurrent()

        assertEquals(PreviewStatus.ERROR, session.state.value.previewStatus)
        assertEquals("Preview recovery failed", session.state.value.lastAction)
        assertEquals("Bind failure: rebind failed", session.state.value.lastError)
        assertTrue(trace.snapshot().any { it.name == "preview.recovery.failed" })
        assertEquals(
            0,
            trace.snapshot().count { it.name == "preview.recovery.requested" }
        )
    }

    @Test
    fun `preview host detach clears host availability and records trace`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewHostDetached("Activity moved to background"))
        advanceUntilIdle()

        assertFalse(session.state.value.previewHostAvailable)
        assertEquals("Activity moved to background", session.state.value.previewStatusDetail)
        assertTrue(trace.snapshot().any { it.name == "preview.host.detached" })
    }

    @Test
    fun `preview host reattach after detach requests recovery bind`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val effects = mutableListOf<SessionEffect>()
        val effectCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effect ->
                effects += effect
            }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        effects.clear()
        session.dispatch(SessionIntent.PreviewHostDetached("Activity moved to background"))
        advanceUntilIdle()

        effects.clear()
        session.dispatch(SessionIntent.PreviewHostAttached)
        advanceUntilIdle()

        val bindEffect = effects.filterIsInstance<SessionEffect.BindPreview>().lastOrNull()
        assertNotNull(bindEffect)
        assertTrue(bindEffect.isRecovery)
        assertEquals(
            "recover after preview host detached: Activity moved to background",
            bindEffect.reason
        )
        assertTrue(trace.snapshot().any { it.name == "preview.host.recovery.requested" })

        effectCollector.cancel()
    }

    @Test
    fun `camera permission grant resumes pending host recovery after foreground attach`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val effects = mutableListOf<SessionEffect>()
        val effectCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effect ->
                effects += effect
            }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        session.dispatch(SessionIntent.PreviewHostDetached("Activity moved to background"))
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = false, microphoneGranted = true))
        advanceUntilIdle()

        effects.clear()
        session.dispatch(SessionIntent.PreviewHostAttached)
        advanceUntilIdle()
        assertTrue(effects.none { it is SessionEffect.BindPreview })

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        advanceUntilIdle()

        val bindEffect = effects.filterIsInstance<SessionEffect.BindPreview>().lastOrNull()
        assertNotNull(bindEffect)
        assertTrue(bindEffect.isRecovery)
        assertEquals(
            "recover after preview host detached: Activity moved to background",
            bindEffect.reason
        )
        assertTrue(trace.snapshot().any { it.name == "preview.host.recovery.requested" })

        effectCollector.cancel()
    }

    @Test
    fun `first launch permission dialog recovery emits bind preview after host reattach`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val effects = mutableListOf<SessionEffect>()
        val effectCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effect ->
                effects += effect
            }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = false, microphoneGranted = false))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertTrue(effects.none { it is SessionEffect.BindPreview })

        session.dispatch(SessionIntent.PreviewHostDetached("permission dialog"))
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        advanceUntilIdle()

        assertTrue(effects.none { it is SessionEffect.BindPreview })

        effects.clear()
        session.dispatch(SessionIntent.PreviewHostAttached)
        advanceUntilIdle()

        val bindEffect = effects.filterIsInstance<SessionEffect.BindPreview>().lastOrNull()
        assertNotNull(bindEffect)
        assertTrue(bindEffect.isRecovery)

        effectCollector.cancel()
    }

    @Test
    fun `boot enters running session with photo mode active`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertEquals(SessionLifecycle.RUNNING, session.state.value.lifecycle)
        assertEquals(PreviewStatus.IDLE, session.state.value.previewStatus)
        assertEquals(ModeId.PHOTO, session.state.value.activeMode)
        assertEquals(
            CaptureTemplate.STILL_CAPTURE,
            session.state.value.activeDeviceGraph.template
        )
        assertEquals("Session booted", session.state.value.lastAction)
        assertTrue(trace.snapshot().any { it.name == "session.booted" })
    }

    @Test
    fun `photo shutter emits shot plan and completion updates state`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()

        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = shot.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/test.jpg",
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailSource = ThumbnailSource.SavedMedia("Pictures/OpenCamera/test.jpg"),
                    metadata = SaveRequest.photoLibrary().metadata,
                    pipelineNotes = listOf("algorithm:photo-default")
                )
            )
        )
        runCurrent()

        assertEquals(CaptureStatus.IDLE, session.state.value.captureStatus)
        assertEquals(RecordingStatus.IDLE, session.state.value.recordingStatus)
        assertEquals("Photo saved", session.state.value.lastAction)
        assertEquals("Pictures/OpenCamera/test.jpg", session.state.value.latestCapturePath)
        assertEquals(SavedMediaType.PHOTO, session.state.value.latestSavedMediaType)
        assertEquals("Pictures/OpenCamera/test.jpg", session.state.value.previewThumbnailPath)
        assertEquals(listOf("algorithm:photo-default"), session.state.value.latestPipelineNotes)
        assertTrue(trace.snapshot().any { it.name == "capture.saved" })
    }

    @Test
    fun `preview snapshot after saved media does not overwrite thumbnail`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(
            SessionIntent.PreviewSnapshotUpdated(
                ThumbnailSource.PreviewSnapshot("/tmp/preview-a.jpg")
            )
        )
        runCurrent()
        assertEquals("/tmp/preview-a.jpg", session.state.value.previewThumbnailPath)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = shot.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/photo-a.jpg",
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailSource = ThumbnailSource.SavedMedia(
                        "Pictures/OpenCamera/photo-a.jpg",
                        "file:///tmp/photo-a.jpg"
                    ),
                    metadata = SaveRequest.photoLibrary().metadata
                )
            )
        )
        runCurrent()
        assertEquals("Pictures/OpenCamera/photo-a.jpg", session.state.value.previewThumbnailPath)

        session.dispatch(
            SessionIntent.PreviewSnapshotUpdated(
                ThumbnailSource.PreviewSnapshot("/tmp/preview-b.jpg")
            )
        )
        runCurrent()

        assertEquals("Pictures/OpenCamera/photo-a.jpg", session.state.value.previewThumbnailPath)
        assertTrue(session.state.value.latestThumbnailSource is ThumbnailSource.SavedMedia)
    }

    @Test
    fun `photo countdown delays shot execution and blocks settings updates until completion`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(
                        countdownDuration = CountdownDuration.SECONDS_3
                    )
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()

        assertEquals(CaptureStatus.REQUESTED, session.state.value.captureStatus)
        assertEquals(3, session.state.value.countdownRemainingSeconds)
        assertEquals(null, session.state.value.activeShot)
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Timer 3s"))

        session.dispatch(
            SessionIntent.SettingsUpdated(
                SessionSettingsSnapshot(
                    persisted = PersistedSettings(
                        photo = PhotoSettings(defaultFilterProfileId = "photo-rich")
                    )
                )
            )
        )
        runCurrent()
        assertEquals(
            "Wait for countdown to finish before updating settings",
            session.state.value.lastAction
        )

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, session.state.value.countdownRemainingSeconds)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, session.state.value.countdownRemainingSeconds)

        advanceTimeBy(1_000)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(null, session.state.value.countdownRemainingSeconds)
        assertEquals("Photo capture requested", session.state.value.lastAction)
        assertTrue(trace.snapshot().any { it.name == "capture.countdown.started" })
        assertTrue(trace.snapshot().any { it.name == "capture.photo" })
        assertEquals(MediaType.PHOTO, shot.mediaType)
    }

    @Test
    fun `front camera photo capture carries selfie mirror and shutter sound tags`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT)
            ),
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    common = com.opencamera.core.settings.CommonSettings(
                        shutterSoundEnabled = false,
                        selfieMirrorEnabled = true
                    )
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.LensFacingToggled)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(LensFacing.FRONT, session.state.value.activeDeviceGraph.preferredLensFacing)
        assertEquals("front", shot.saveRequest.metadata.customTags["captureLensFacing"])
        assertEquals("on", shot.saveRequest.metadata.customTags["selfieMirrorEnabled"])
        assertEquals("true", shot.saveRequest.metadata.customTags["selfieMirrorApply"])
        assertEquals("off", shot.saveRequest.metadata.customTags["shutterSoundEnabled"])
    }

    @Test
    fun `photo countdown blocks mode switching until countdown finishes`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(
                        countdownDuration = CountdownDuration.SECONDS_5
                    )
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()

        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        runCurrent()

        assertEquals(ModeId.PHOTO, session.state.value.activeMode)
        assertEquals(5, session.state.value.countdownRemainingSeconds)
        assertEquals(
            "Wait for countdown to finish before switching modes",
            session.state.value.lastAction
        )
        assertTrue(trace.snapshot().any { it.name == "mode.switch.blocked" })
    }

    @Test
    fun `zoom toggle cycles configured preset ratios and updates active graph`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(1f, 2f, 5f),
                    defaultRatio = 1f
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ZoomRatioToggled)
        advanceUntilIdle()

        assertEquals(2f, session.state.value.activeDeviceGraph.preview.zoomRatio)
        assertEquals("Zoom set to 2.0x", session.state.value.lastAction)
        assertTrue(trace.snapshot().any { it.name == "zoom.updated" && it.detail == "2.0x" })

        session.dispatch(SessionIntent.ZoomRatioToggled)
        advanceUntilIdle()

        assertEquals(5f, session.state.value.activeDeviceGraph.preview.zoomRatio)
    }

    @Test
    fun `zoom toggle blocks discrete preset during recording`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(1f, 2f),
                    defaultRatio = 1f
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        advanceUntilIdle()
        assertEquals(RecordingStatus.RECORDING, session.state.value.recordingStatus)

        session.dispatch(SessionIntent.ZoomRatioToggled)
        advanceUntilIdle()

        assertEquals(1f, session.state.value.activeDeviceGraph.preview.zoomRatio)
        assertEquals("Zoom preset stepping is blocked during recording", session.state.value.lastAction)
        assertTrue(trace.snapshot().any { it.name == "zoom.switch.blocked.recording" })
    }

    @Test
    fun `zoom toggle reports unavailable when device exposes no alternate ratios`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.UNSUPPORTED,
                    supportedRatios = listOf(1f),
                    defaultRatio = 1f
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ZoomRatioToggled)
        advanceUntilIdle()

        assertEquals(1f, session.state.value.activeDeviceGraph.preview.zoomRatio)
        assertEquals("Zoom switching is unavailable on this device", session.state.value.lastAction)
        assertTrue(trace.snapshot().any { it.name == "zoom.switch.unavailable" })
    }

    @Test
    fun `apply zoom ratio sets target ratio directly`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(1f, 2f, 5f),
                    defaultRatio = 1f
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ApplyZoomRatio(5f))
        advanceUntilIdle()

        assertEquals(5f, session.state.value.activeDeviceGraph.preview.zoomRatio)
        assertEquals("Zoom set to 5.0x", session.state.value.lastAction)
        assertTrue(trace.snapshot().any { it.name == "zoom.updated" && it.detail == "5.0x" })
    }

    @Test
    fun `apply zoom ratio blocks discrete preset during recording`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(1f, 2f, 5f),
                    defaultRatio = 1f
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        advanceUntilIdle()
        assertEquals(RecordingStatus.RECORDING, session.state.value.recordingStatus)

        session.dispatch(SessionIntent.ApplyZoomRatio(5f))
        advanceUntilIdle()

        assertEquals(1f, session.state.value.activeDeviceGraph.preview.zoomRatio)
        assertEquals("Zoom preset stepping is blocked during recording", session.state.value.lastAction)
        assertTrue(trace.snapshot().any { it.name == "zoom.apply.blocked.recording" })
        assertFalse(trace.snapshot().any { it.name == "zoom.updated" })
    }

    @Test
    fun `apply zoom ratio allows continuous zoom during recording`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 10f),
                    defaultRatio = 1f
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        advanceUntilIdle()
        assertEquals(RecordingStatus.RECORDING, session.state.value.recordingStatus)

        session.dispatch(SessionIntent.ApplyZoomRatio(3.5f))
        advanceUntilIdle()

        assertEquals(3.5f, session.state.value.activeDeviceGraph.preview.zoomRatio)
        assertEquals("Zoom set to 3.5x", session.state.value.lastAction)
        assertTrue(trace.snapshot().any { it.name == "zoom.updated" && it.detail == "3.5x" })
    }

    @Test
    fun `apply zoom ratio with lens node map tracks previewZoomRatio to captureZoom via frame-scale switch`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(0.6f, 1f, 2f, 5f, 10f),
                    defaultRatio = 1f,
                    lensNodeMap = threeNodeMap
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        // captureZoom = 3.0 → bases [1.0, 2.0, 5.0]
        // switchAt 2.0→5.0 = 2.0/0.775 ≈ 2.58; 3.0 >= 2.58 → base 5.0
        // Continuous: min(0.949*3.0, 5.0) = 2.847
        session.dispatch(SessionIntent.ApplyZoomRatio(3.0f))
        advanceUntilIdle()

        assertEquals(3.0f, session.state.value.activeDeviceGraph.preview.zoomRatio)
        val previewZoom3 = session.state.value.activeDeviceGraph.preview.previewZoomRatio
        assertTrue(
            previewZoom3 >= 2.8f && previewZoom3 <= 2.9f,
            "At 3.0x: expected ≈2.847, got $previewZoom3"
        )
    }

    @Test
    fun `previewZoomRatio tracks captureZoom with frame-scale based switching`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(0.6f, 1f, 2f, 5f, 10f),
                    defaultRatio = 1f,
                    lensNodeMap = threeNodeMap
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        // captureZoom = 2.5 → bases [1.0, 2.0, 5.0]
        // switchAt 2.0→5.0 = 2.0/0.775 ≈ 2.58; 2.5 < 2.58 → base 2.0
        // Continuous: min(0.949*2.5, 5.0) = 2.372
        session.dispatch(SessionIntent.ApplyZoomRatio(2.5f))
        advanceUntilIdle()
        val at25 = session.state.value.activeDeviceGraph.preview.previewZoomRatio
        assertTrue(at25 >= 2.3f && at25 <= 2.45f, "At 2.5x: expected ≈2.372, got $at25")

        // captureZoom = 3.5 → switchAt 2.58 passed → base 5.0; continuous: min(0.949*3.5, 5.0) = 3.322
        session.dispatch(SessionIntent.ApplyZoomRatio(3.5f))
        advanceUntilIdle()
        val at35 = session.state.value.activeDeviceGraph.preview.previewZoomRatio
        assertTrue(at35 >= 3.25f && at35 <= 3.4f, "At 3.5x: expected ≈3.322, got $at35")
    }

    @Test
    fun `previewZoomRatio tracks captureZoom with WYSIWYG frame when crossing lens thresholds`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(0.6f, 1f, 2f, 5f, 10f),
                    defaultRatio = 1f,
                    lensNodeMap = threeNodeMap
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        // captureZoom = 4.5 → bases [1.0, 2.0, 5.0]; interval [2.0, 5.0]
        // Continuous: min(0.949*4.5, 5.0) = 4.271
        session.dispatch(SessionIntent.ApplyZoomRatio(4.5f))
        advanceUntilIdle()
        val at45 = session.state.value.activeDeviceGraph.preview.previewZoomRatio
        assertTrue(at45 >= 4.2f && at45 <= 4.35f, "At 4.5x: expected ≈4.271, got $at45")

        // captureZoom = 6.0 → above all baselines → cap at 5.0
        session.dispatch(SessionIntent.ApplyZoomRatio(6.0f))
        advanceUntilIdle()
        assertEquals(5.0f, session.state.value.activeDeviceGraph.preview.previewZoomRatio)
    }

    @Test
    fun `previewZoomRatio is always less than or equal to captureZoomRatio`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(0.6f, 1f, 2f, 5f, 10f),
                    defaultRatio = 1f,
                    lensNodeMap = threeNodeMap
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        val testRatios = listOf(0.6f, 1.0f, 1.5f, 2.0f, 3.0f, 5.0f, 7.0f, 10.0f)
        for (captureZoom in testRatios) {
            session.dispatch(SessionIntent.ApplyZoomRatio(captureZoom))
            advanceUntilIdle()
            val previewZoom = session.state.value.activeDeviceGraph.preview.previewZoomRatio
            assertTrue(
                previewZoom <= session.state.value.activeDeviceGraph.preview.zoomRatio,
                "previewZoomRatio ($previewZoom) should be <= captureZoomRatio (${session.state.value.activeDeviceGraph.preview.zoomRatio}) at captureZoom=$captureZoom"
            )
        }
    }

    @Test
    fun `preview host detach cancels active photo countdown`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(
                        countdownDuration = CountdownDuration.SECONDS_3
                    )
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()

        assertEquals(3, session.state.value.countdownRemainingSeconds)

        session.dispatch(SessionIntent.PreviewHostDetached("Activity moved to background"))
        advanceUntilIdle()

        assertEquals(null, session.state.value.countdownRemainingSeconds)
        assertEquals(CaptureStatus.IDLE, session.state.value.captureStatus)
        assertFalse(session.state.value.previewHostAvailable)
        assertEquals("Preview host detached", session.state.value.lastAction)
        assertTrue(
            trace.snapshot().any { event ->
                event.name == "capture.countdown.cancelled" &&
                    event.detail == "Countdown cancelled because preview host detached"
            }
        )
    }

    @Test
    fun `camera permission loss cancels active photo countdown`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(
                        countdownDuration = CountdownDuration.SECONDS_3
                    )
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()

        assertEquals(3, session.state.value.countdownRemainingSeconds)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = false, microphoneGranted = true))
        advanceUntilIdle()

        assertEquals(null, session.state.value.countdownRemainingSeconds)
        assertEquals(CaptureStatus.IDLE, session.state.value.captureStatus)
        assertEquals(PreviewStatus.BLOCKED, session.state.value.previewStatus)
        assertEquals("Camera permission required for preview", session.state.value.lastAction)
        assertTrue(
            trace.snapshot().any { event ->
                event.name == "capture.countdown.cancelled" &&
                    event.detail == "Countdown cancelled because camera permission is missing"
            }
        )
    }

    @Test
    fun `video mode applies torch preference before recording and stops through shutter`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.SecondaryActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot, session.state.value.toString())
        assertEquals("on", shot.saveRequest.metadata.customTags["torch"])
        assertEquals(true, shot.captureProfile.torchEnabled)
        session.dispatch(SessionIntent.ShotStarted(shot))
        advanceUntilIdle()

        assertEquals(ModeId.VIDEO, session.state.value.activeMode)
        assertEquals(RecordingStatus.RECORDING, session.state.value.recordingStatus)
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Torch On"))

        session.dispatch(SessionIntent.SecondaryActionPressed)
        advanceUntilIdle()
        assertEquals(
            "Stop recording before changing torch",
            session.state.value.lastAction
        )
        session.dispatch(SessionIntent.ShutterPressed)
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = shot.shotId,
                    mediaType = MediaType.VIDEO,
                    outputPath = "Movies/OpenCamera/test.mp4",
                    saveRequest = SaveRequest.videoLibrary(),
                    thumbnailSource = ThumbnailSource.SavedMedia("Movies/OpenCamera/test.mp4"),
                    metadata = SaveRequest.videoLibrary().metadata,
                    pipelineNotes = listOf("algorithm:video-default")
                )
            )
        )
        runCurrent()

        assertEquals(RecordingStatus.IDLE, session.state.value.recordingStatus)
        assertTrue(trace.snapshot().any { it.name == "recording.started" })
        assertTrue(trace.snapshot().any { it.name == "recording.saved" })
        assertEquals("Movies/OpenCamera/test.mp4", session.state.value.latestVideoPath)
        assertEquals(SavedMediaType.VIDEO, session.state.value.latestSavedMediaType)
        assertEquals("Video saved", session.state.value.modeSnapshot.state.headline)
    }

    @Test
    fun `video shot started sets recording status and elapsed to zero`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        advanceUntilIdle()

        assertEquals(RecordingStatus.RECORDING, session.state.value.recordingStatus)
        assertEquals(0L, session.state.value.recordingStartedAtElapsedMillis)
        assertEquals(0L, session.state.value.recordingElapsedMillis)
    }

    @Test
    fun `video recording elapsed advances over time`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace,
            this,
            recordingTimerDispatcher = StandardTestDispatcher(testScheduler),
            elapsedRealtimeMillis = { testScheduler.currentTime }
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()

        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()

        assertEquals(0L, session.state.value.recordingElapsedMillis)

        advanceTimeBy(3_100)
        runCurrent()

        val elapsed = session.state.value.recordingElapsedMillis!!
        assertTrue(elapsed >= 3_000L, "Expected elapsed >= 3000 but was $elapsed")

        session.dispatch(SessionIntent.ShotFailed(shotId = shot.shotId, mediaType = MediaType.VIDEO, reason = "test complete"))
        runCurrent()
    }

    @Test
    fun `video shot completed clears recording elapsed fields`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        advanceUntilIdle()

        assertNotNull(session.state.value.recordingStartedAtElapsedMillis)
        assertNotNull(session.state.value.recordingElapsedMillis)

        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = shot.shotId,
                    mediaType = MediaType.VIDEO,
                    outputPath = "Movies/OpenCamera/test.mp4",
                    saveRequest = SaveRequest.videoLibrary(),
                    thumbnailSource = ThumbnailSource.SavedMedia("Movies/OpenCamera/test.mp4"),
                    metadata = SaveRequest.videoLibrary().metadata,
                    pipelineNotes = emptyList()
                )
            )
        )
        runCurrent()

        assertEquals(RecordingStatus.IDLE, session.state.value.recordingStatus)
        assertNull(session.state.value.recordingStartedAtElapsedMillis)
        assertNull(session.state.value.recordingElapsedMillis)
        assertEquals(SavedMediaType.VIDEO, session.state.value.latestSavedMediaType)
    }

    @Test
    fun `video shot failed clears recording elapsed fields`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        advanceUntilIdle()

        assertNotNull(session.state.value.recordingStartedAtElapsedMillis)
        assertNotNull(session.state.value.recordingElapsedMillis)

        session.dispatch(
            SessionIntent.ShotFailed(
                shotId = shot.shotId,
                mediaType = MediaType.VIDEO,
                reason = "Camera disconnected"
            )
        )
        advanceUntilIdle()

        assertEquals(RecordingStatus.IDLE, session.state.value.recordingStatus)
        assertNull(session.state.value.recordingStartedAtElapsedMillis)
        assertNull(session.state.value.recordingElapsedMillis)
    }

    @Test
    fun `video mode cycles quality through tertiary action before recording`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()

        assertEquals("Cycle Quality", session.state.value.modeSnapshot.uiSpec.tertiaryActionLabel)
        assertEquals(true, session.state.value.modeSnapshot.state.isTertiaryActionEnabled)
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Quality 4K"))

        session.dispatch(SessionIntent.TertiaryActionPressed)
        advanceUntilIdle()

        assertEquals(
            RecordingQualityPreset.FHD,
            session.state.value.activeDeviceGraph.recording.qualityPreset
        )
        assertEquals(
            VideoResolution.FHD_1080P,
            session.state.value.activeDeviceGraph.recording.videoSpec.resolution
        )

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("fhd", shot.saveRequest.metadata.customTags["videoQuality"])
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "Waiting for Session Kernel to start the 1080p 25fps recording task"
            )
        )
        assertTrue(session.state.value.modeSnapshot.state.headline.contains("Recording requested"))
    }

    @Test
    fun `shot failed for already-terminal shot id is idempotent`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        advanceUntilIdle()
        assertEquals(RecordingStatus.RECORDING, session.state.value.recordingStatus)

        // First failure clears the active shot
        session.dispatch(
            SessionIntent.ShotFailed(
                shotId = shot.shotId,
                mediaType = MediaType.VIDEO,
                reason = "Recording interrupted by lifecycle stop"
            )
        )
        advanceUntilIdle()
        assertEquals(RecordingStatus.IDLE, session.state.value.recordingStatus)
        assertNull(session.state.value.activeShot)

        // Second failure for same shotId should be idempotent (no-op)
        session.dispatch(
            SessionIntent.ShotFailed(
                shotId = shot.shotId,
                mediaType = MediaType.VIDEO,
                reason = "Video recording error: code 4"
            )
        )
        advanceUntilIdle()

        // Should only have one recording.failed trace event
        val failedTraces = trace.snapshot().filter { it.name == "recording.failed" }
        assertEquals(1, failedTraces.size)
        assertTrue(failedTraces[0].detail.contains("Recording interrupted by lifecycle stop"))
        assertTrue(trace.snapshot().any { it.name == "shot.failed.orphaned" })
    }

    @Test
    fun `session exposes settings snapshot and video mode consumes default video spec`() = runTest {
        val settingsSnapshot = SessionSettingsSnapshot(
            persisted = PersistedSettings(
                video = VideoSettings(
                    defaultVideoSpec = VideoSpec(
                        resolution = VideoResolution.UHD_4K,
                        frameRate = VideoFrameRate.FPS_25,
                        dynamicFpsPolicy = DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS,
                        audioProfile = AudioProfile.CONCERT
                    )
                )
            )
        )
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            settingsSnapshot = settingsSnapshot
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()

        assertEquals(settingsSnapshot, session.state.value.settings)
        assertEquals(
            RecordingQualityPreset.UHD,
            session.state.value.activeDeviceGraph.recording.qualityPreset
        )
        assertEquals(
            VideoResolution.UHD_4K,
            session.state.value.activeDeviceGraph.recording.videoSpec.resolution
        )
        assertEquals(
            VideoFrameRate.FPS_25,
            session.state.value.activeDeviceGraph.recording.videoSpec.frameRate
        )
        assertEquals(
            DynamicVideoFpsPolicy.LOCKED,
            session.state.value.activeDeviceGraph.recording.videoSpec.dynamicFpsPolicy
        )
        assertEquals(
            AudioProfile.STANDARD,
            session.state.value.activeDeviceGraph.recording.videoSpec.audioProfile
        )
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Default 4K 25fps"))
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Active 4K 25fps fallback"))
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Mic Concert"))

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("4k", shot.saveRequest.metadata.customTags["defaultVideoResolution"])
        assertEquals("25", shot.saveRequest.metadata.customTags["defaultVideoFrameRate"])
        assertEquals(
            "low-light-auto-24fps",
            shot.saveRequest.metadata.customTags["dynamicFpsPolicy"]
        )
        assertEquals("concert", shot.saveRequest.metadata.customTags["audioProfile"])
        assertEquals("4k", shot.saveRequest.metadata.customTags["resolvedVideoResolution"])
        assertEquals("25", shot.saveRequest.metadata.customTags["resolvedVideoFrameRate"])
        assertEquals("locked", shot.saveRequest.metadata.customTags["resolvedDynamicFpsPolicy"])
        assertEquals("standard", shot.saveRequest.metadata.customTags["resolvedAudioProfile"])
    }

    @Test
    fun `settings update refreshes active photo mode filter without switching modes`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        session.dispatch(
            SessionIntent.SettingsUpdated(
                SessionSettingsSnapshot(
                    persisted = PersistedSettings(
                        photo = PhotoSettings(
                            defaultFilterProfileId = "photo-rich",
                            defaultWatermarkTemplateId = "travel-polaroid"
                        )
                    )
                )
            )
        )
        advanceUntilIdle()

        assertEquals("Session settings updated", session.state.value.lastAction)
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Filter Rich"))
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Watermark Travel Polaroid"))
        assertEquals("photo-rich", session.state.value.settings.persisted.photo.defaultFilterProfileId)
        assertEquals(
            "travel-polaroid",
            session.state.value.settings.persisted.photo.defaultWatermarkTemplateId
        )

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ShotKind.STILL_CAPTURE, shot.shotKind)
        assertEquals("photo-rich", shot.saveRequest.metadata.customTags["filterProfile"])
        assertEquals("travel-polaroid", shot.saveRequest.metadata.customTags["watermarkTemplate"])
        assertEquals("bottom-left", shot.saveRequest.metadata.customTags["watermarkPosition"])
        assertEquals("1.0", shot.saveRequest.metadata.customTags["watermarkTextScale"])
        assertEquals("1.0", shot.saveRequest.metadata.customTags["watermarkTextOpacity"])
        assertEquals("white", shot.saveRequest.metadata.customTags["watermarkFrameBackground"])
        assertEquals("photo-rich", shot.postProcessSpec.algorithmProfile)
    }

    @Test
    fun `photo mode carries imported custom filter render spec through session shot metadata`() = runTest {
        val customFilter = FilterProfile(
            id = "custom-amber-street",
            label = "Amber Street",
            category = FilterProfileCategory.CUSTOM,
            builtIn = false,
            renderSpec = FilterRenderSpec(
                brightnessShift = 7,
                contrast = 1.11f,
                saturation = 0.93f,
                warmthShift = 5,
                vignetteStrength = 0.17f
            )
        )
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(
                        defaultFilterProfileId = customFilter.id
                    )
                ),
                catalog = FeatureCatalog(
                    filterProfiles = DEFAULT_FILTER_PROFILES + customFilter
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Filter Amber Street"))

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("custom-amber-street", shot.saveRequest.metadata.customTags["filterProfile"])
        assertEquals("1", shot.saveRequest.metadata.customTags["filterSpec.version"])
        assertEquals("7", shot.saveRequest.metadata.customTags["filterSpec.brightnessShift"])
        assertEquals("custom-amber-street", shot.postProcessSpec.algorithmProfile)
    }

    @Test
    fun `settings update refreshes active video defaults and recording graph`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()

        session.dispatch(
            SessionIntent.SettingsUpdated(
                SessionSettingsSnapshot(
                    persisted = PersistedSettings(
                        video = VideoSettings(
                            defaultVideoSpec = VideoSpec(
                                resolution = VideoResolution.HD_720P,
                                frameRate = VideoFrameRate.FPS_60,
                                dynamicFpsPolicy = DynamicVideoFpsPolicy.LOCKED,
                                audioProfile = AudioProfile.STANDARD
                            ),
                            defaultFilterProfileId = "portrait-retro"
                        )
                    )
                )
            )
        )
        advanceUntilIdle()

        assertEquals("Session settings updated", session.state.value.lastAction)
        assertEquals(
            RecordingQualityPreset.HD,
            session.state.value.activeDeviceGraph.recording.qualityPreset
        )
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Default 720p 60fps"))
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Filter Portrait Retro"))

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("720p", shot.saveRequest.metadata.customTags["defaultVideoResolution"])
        assertEquals("60", shot.saveRequest.metadata.customTags["defaultVideoFrameRate"])
        assertEquals("portrait-retro", shot.saveRequest.metadata.customTags["filterProfile"])
        assertEquals("portrait-retro", shot.postProcessSpec.algorithmProfile)
    }

    @Test
    fun `video mode disables torch control when unsupported`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            deviceCapabilities = DeviceCapabilities(
                supportsStillCapture = true,
                supportsVideoRecording = true,
                supportsPreviewSnapshots = true,
                supportsAudioRecording = true,
                supportsManualControls = true,
                supportsDocumentScanEnhancement = true,
                supportsPortraitDepthEffect = true,
                supportsNightMultiFrame = true,
                supportsFlashControl = false
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()

        assertEquals("Torch Unsupported", session.state.value.modeSnapshot.uiSpec.secondaryActionLabel)
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "Torch unavailable on this device"
            )
        )

        session.dispatch(SessionIntent.SecondaryActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("off", shot.saveRequest.metadata.customTags["torch"])
        assertEquals(false, shot.captureProfile.torchEnabled)
    }

    @Test
    fun `device capability update refreshes active video mode ui and graph`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()

        assertEquals("Cycle Torch", session.state.value.modeSnapshot.uiSpec.secondaryActionLabel)
        assertTrue(session.state.value.activeDeviceGraph.recording.audioEnabledWhenPermitted)

        session.dispatch(
            SessionIntent.DeviceCapabilitiesUpdated(
                DeviceCapabilities.DEFAULT.copy(
                    supportsAudioRecording = false,
                    supportsFlashControl = false
                )
            )
        )
        advanceUntilIdle()

        assertFalse(session.state.value.activeDeviceCapabilities.supportsFlashControl)
        assertEquals("Torch Unsupported", session.state.value.modeSnapshot.uiSpec.secondaryActionLabel)
        assertFalse(session.state.value.activeDeviceGraph.recording.audioEnabledWhenPermitted)

        session.dispatch(SessionIntent.SecondaryActionPressed)
        advanceUntilIdle()
        assertEquals("Torch is unavailable on this device", session.state.value.lastAction)

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("off", shot.saveRequest.metadata.customTags["torch"])
        assertFalse(shot.captureProfile.torchEnabled)
    }

    @Test
    fun `photo mode cycles flash and annotates capture request`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SecondaryActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ModeId.PHOTO, session.state.value.activeMode)
        assertEquals(FlashMode.AUTO, shot.captureProfile.flashMode)
        assertEquals("auto", shot.saveRequest.metadata.customTags["flash"])
        assertEquals("photo-original", shot.saveRequest.metadata.customTags["filterProfile"])
        assertEquals("PHOTO", shot.postProcessSpec.watermarkText)
        assertEquals("photo-original", shot.postProcessSpec.algorithmProfile)
        assertEquals("Photo capture requested", session.state.value.lastAction)
        assertEquals("Still capture requested", session.state.value.modeSnapshot.state.headline)
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Filter Original"))
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Flash Auto"))
    }

    @Test
    fun `photo mode consumes persisted default filter when building capture request`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(
                        defaultFilterProfileId = "photo-rich",
                        defaultWatermarkTemplateId = "retro-frame",
                        livePhotoEnabledByDefault = true
                    )
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Filter Rich"))
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Watermark Retro Frame"))
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Live On"))

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ShotKind.LIVE_PHOTO, shot.shotKind)
        assertEquals("photo-rich", shot.saveRequest.metadata.customTags["filterProfile"])
        assertEquals("retro-frame", shot.saveRequest.metadata.customTags["watermarkTemplate"])
        assertEquals("on", shot.saveRequest.metadata.customTags["livePhotoDefault"])
        assertEquals(
            "dynamic",
            shot.saveRequest.metadata.customTags["liveWatermarkMode"]
        )
        assertEquals(
            "follow-frame-luma-and-motion",
            shot.saveRequest.metadata.customTags["liveWatermarkBehavior"]
        )
        assertEquals(
            "follow-frame-luma",
            shot.saveRequest.metadata.customTags["liveWatermarkBrightnessCoupling"]
        )
        assertEquals(
            "follow-frame-motion",
            shot.saveRequest.metadata.customTags["liveWatermarkOpacityCoupling"]
        )
        assertEquals("bottom-center", shot.saveRequest.metadata.customTags["watermarkPosition"])
        assertEquals("1.0", shot.saveRequest.metadata.customTags["watermarkTextScale"])
        assertEquals("0.8", shot.saveRequest.metadata.customTags["watermarkTextOpacity"])
        assertEquals("dark", shot.saveRequest.metadata.customTags["watermarkFrameBackground"])
        assertEquals("photo-rich", shot.postProcessSpec.algorithmProfile)
    }

    @Test
    fun `live photo completion stores bundle and uses live saved action`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(
                        livePhotoEnabledByDefault = true
                    )
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val activeShot = assertNotNull(session.state.value.activeShot)
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = activeShot.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/live.jpg",
                    saveRequest = SaveRequest.photoLibrary(
                        metadata = MediaMetadata(
                            customTags = mapOf("livePhotoDefault" to "on")
                        )
                    ),
                    thumbnailSource = ThumbnailSource.SavedMedia("Pictures/OpenCamera/live.jpg"),
                    metadata = MediaMetadata(
                        customTags = mapOf("livePhotoDefault" to "on")
                    ),
                    livePhotoBundle = LivePhotoBundle(
                        stillPath = "Pictures/OpenCamera/live.jpg",
                        motionPath = "Pictures/OpenCamera/live.live.mp4",
                        sidecarPath = "Pictures/OpenCamera/live.live.json",
                        motionDurationMillis = 1500,
                        motionMimeType = "video/mp4",
                        sidecarMimeType = "application/vnd.opencamera.live+json"
                    )
                )
            )
        )
        advanceUntilIdle()

        assertEquals("Live photo saved", session.state.value.lastAction)
        assertEquals("Pictures/OpenCamera/live.live.mp4", session.state.value.latestLivePhotoBundle?.motionPath)
        assertEquals("Pictures/OpenCamera/live.jpg", session.state.value.latestCapturePath)
    }

    @Test
    fun `live photo completion with still-only fallback does not store bundle`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(livePhotoEnabledByDefault = true)
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val activeShot = assertNotNull(session.state.value.activeShot)
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = activeShot.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/still.jpg",
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailSource = ThumbnailSource.SavedMedia("Pictures/OpenCamera/still.jpg"),
                    metadata = MediaMetadata(),
                    livePhotoBundle = LivePhotoBundle(
                        stillPath = "Pictures/OpenCamera/still.jpg",
                        motionPath = "Pictures/OpenCamera/still.live.mp4",
                        sidecarPath = "Pictures/OpenCamera/still.live.json",
                        motionDurationMillis = 1_500,
                        motionMimeType = "video/mp4",
                        sidecarMimeType = "application/vnd.opencamera.live+json",
                        bundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK
                    )
                )
            )
        )
        advanceUntilIdle()

        assertEquals("Live photo saved (still only)", session.state.value.lastAction)
        assertNull(session.state.value.latestLivePhotoBundle)
    }

    @Test
    fun `live photo completion with degraded motion still stores bundle`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(livePhotoEnabledByDefault = true)
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val activeShot = assertNotNull(session.state.value.activeShot)
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = activeShot.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/degraded.jpg",
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailSource = ThumbnailSource.SavedMedia("Pictures/OpenCamera/degraded.jpg"),
                    metadata = MediaMetadata(),
                    livePhotoBundle = LivePhotoBundle(
                        stillPath = "Pictures/OpenCamera/degraded.jpg",
                        motionPath = "Pictures/OpenCamera/degraded.live.mp4",
                        sidecarPath = "Pictures/OpenCamera/degraded.live.json",
                        motionDurationMillis = 1_500,
                        motionMimeType = "video/mp4",
                        sidecarMimeType = "application/vnd.opencamera.live+json",
                        bundleStatus = LiveBundleStatus.DEGRADED_MOTION,
                        temporalWindow = LiveTemporalWindow(
                            requestedDurationMillis = 1_500,
                            preShutterMillis = 0,
                            postShutterMillis = 800,
                            frameCount = 24,
                            source = LiveMotionSource.POST_SHUTTER_FRAMES
                        )
                    )
                )
            )
        )
        advanceUntilIdle()

        assertEquals("Live photo saved", session.state.value.lastAction)
        assertNotNull(session.state.value.latestLivePhotoBundle)
        assertEquals(LiveBundleStatus.DEGRADED_MOTION, session.state.value.latestLivePhotoBundle?.bundleStatus)
    }

    @Test
    fun `photo mode emits watermark style metadata for selected template`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(
                        defaultWatermarkTemplateId = "travel-polaroid",
                        travelPolaroidWatermarkStyle = WatermarkStyleSettings(
                            textPlacement = WatermarkTextPlacement.TOP_RIGHT,
                            textScale = WatermarkTextScale.COMPACT,
                            textOpacity = WatermarkTextOpacity.SUBTLE,
                            frameBackground = WatermarkFrameBackground.SOURCE_LIGHT_BLUR
                        )
                    )
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("travel-polaroid", shot.saveRequest.metadata.customTags["watermarkTemplate"])
        assertEquals("top-right", shot.saveRequest.metadata.customTags["watermarkPosition"])
        assertEquals("0.85", shot.saveRequest.metadata.customTags["watermarkTextScale"])
        assertEquals("0.55", shot.saveRequest.metadata.customTags["watermarkTextOpacity"])
        assertEquals("source-light-blur", shot.saveRequest.metadata.customTags["watermarkFrameBackground"])
    }

    @Test
    fun `photo mode cycles frame ratio through tertiary action`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertEquals("Cycle Frame", session.state.value.modeSnapshot.uiSpec.tertiaryActionLabel)
        assertEquals(true, session.state.value.modeSnapshot.state.isTertiaryActionEnabled)

        session.dispatch(SessionIntent.TertiaryActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("16:9", shot.saveRequest.metadata.customTags["frameRatio"])
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Frame 16:9"))
        assertEquals("Still capture requested", session.state.value.modeSnapshot.state.headline)
    }

    @Test
    fun `photo mode disables flash control when device does not support it`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            deviceCapabilities = DeviceCapabilities(
                supportsStillCapture = true,
                supportsVideoRecording = true,
                supportsPreviewSnapshots = true,
                supportsAudioRecording = true,
                supportsManualControls = true,
                supportsDocumentScanEnhancement = true,
                supportsPortraitDepthEffect = true,
                supportsNightMultiFrame = true,
                supportsFlashControl = false
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertEquals(ModeId.PHOTO, session.state.value.activeMode)
        assertEquals("Flash Unsupported", session.state.value.modeSnapshot.uiSpec.secondaryActionLabel)
        assertEquals(false, session.state.value.modeSnapshot.state.isSecondaryActionEnabled)
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "Flash control unavailable"
            )
        )

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(FlashMode.OFF, shot.captureProfile.flashMode)
        assertEquals("off", shot.saveRequest.metadata.customTags["flash"])
        assertEquals("PHOTO", shot.postProcessSpec.watermarkText)
    }

    @Test
    fun `document mode cycles scan profiles and routes saves to document library`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.DOCUMENT))
        session.dispatch(SessionIntent.SecondaryActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ModeId.DOCUMENT, session.state.value.activeMode)
        assertEquals("whiteboard", shot.saveRequest.metadata.customTags["profile"])
        assertEquals("enhanced", shot.saveRequest.metadata.customTags["scanMode"])
        assertEquals("Pictures/OpenCamera/Documents", shot.saveRequest.relativePath)
        assertEquals("OpenCamera_DOC", shot.saveRequest.fileNamePrefix)
        assertEquals("document-whiteboard-scan", shot.postProcessSpec.algorithmProfile)
        assertEquals("Document", shot.postProcessSpec.exifOverrides["SceneCaptureType"])
        assertEquals("Photo capture requested", session.state.value.lastAction)
        assertEquals("Document capture requested", session.state.value.modeSnapshot.state.headline)
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Whiteboard"))
    }

    @Test
    fun `humanistic mode cycles styles and emits still capture metadata`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.HUMANISTIC))
        session.dispatch(SessionIntent.SecondaryActionPressed)
        session.dispatch(SessionIntent.TertiaryActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ModeId.HUMANISTIC, session.state.value.activeMode)
        assertEquals("humanistic-vivid", shot.saveRequest.metadata.customTags["style"])
        assertEquals("humanistic", shot.saveRequest.metadata.customTags["mode"])
        assertEquals("humanistic", shot.saveRequest.metadata.customTags["modeDisplay"])
        assertEquals("photo-vivid", shot.saveRequest.metadata.customTags["algorithmProfile"])
        assertEquals("16:9", shot.saveRequest.metadata.customTags["frameRatio"])
        assertEquals("photo-vivid", shot.postProcessSpec.algorithmProfile)
        assertEquals("Humanistic", shot.postProcessSpec.exifOverrides["SceneCaptureType"])
        assertEquals("Humanistic capture requested", session.state.value.modeSnapshot.state.headline)
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("默认风格"))
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "支持风格、画幅、动态照片、定时及水印共享设置"
            )
        )
    }

    @Test
    fun `humanistic mode uses live photo shot kind when live default is enabled`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(
                        livePhotoEnabledByDefault = true
                    )
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.HUMANISTIC))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ShotKind.LIVE_PHOTO, shot.shotKind)
        assertEquals("on", shot.saveRequest.metadata.customTags["livePhotoDefault"])
        assertEquals(
            "follow-frame-luma-and-motion",
            shot.saveRequest.metadata.customTags["liveWatermarkBehavior"]
        )
    }

    @Test
    fun `humanistic mode pro variant degrades to saved only draft when manual controls are unavailable`() = runTest {
        val session = DefaultCameraSession(
            registry = ModeRegistry(testModePlugins()),
            trace = InMemorySessionTrace(),
            baseDeviceCapabilities = DeviceCapabilities(
                supportsStillCapture = true,
                supportsVideoRecording = true,
                supportsPreviewSnapshots = true,
                supportsAudioRecording = true,
                supportsManualControls = false
            ),
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            settingsSnapshot = SessionSettingsSnapshot(
                catalog = FeatureCatalog(
                    manualCaptureDraft = ManualCaptureParams(
                        rawEnabled = true,
                        iso = 640,
                        shutterSpeedMillis = 40L,
                        whiteBalanceKelvin = 5100
                    )
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.HUMANISTIC))
        advanceUntilIdle()

        assertEquals("Professional assist", session.state.value.modeSnapshot.uiSpec.proActionLabel)

        session.dispatch(SessionIntent.ProActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("pro", shot.saveRequest.metadata.customTags["modeVariant"])
        assertEquals("assisted", shot.saveRequest.metadata.customTags["controlMode"])
        assertEquals("unsupported", shot.saveRequest.metadata.customTags["manualDraftState"])
        assertEquals(true, shot.captureProfile.manualCaptureParams?.rawEnabled)
        assertEquals("photo-original-pro-assist", shot.postProcessSpec.algorithmProfile)
        assertEquals("Professional assist", shot.postProcessSpec.exifOverrides["HumanisticVariant"])
        assertEquals("Professional assist on", session.state.value.modeSnapshot.uiSpec.proActionLabel)
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "saved-only draft because manual controls are unavailable"
            )
        )
    }

    @Test
    fun `document mode degrades to basic archive profiles when scan enhancement is unavailable`() = runTest {
        val session = DefaultCameraSession(
            registry = ModeRegistry(
                testModePlugins()
            ),
            trace = InMemorySessionTrace(),
            baseDeviceCapabilities = DeviceCapabilities(
                supportsStillCapture = true,
                supportsVideoRecording = true,
                supportsPreviewSnapshots = true,
                supportsAudioRecording = true,
                supportsManualControls = true,
                supportsDocumentScanEnhancement = false
            ),
            scope = TestScope(StandardTestDispatcher(testScheduler))
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.DOCUMENT))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ModeId.DOCUMENT, session.state.value.activeMode)
        assertEquals("basic", shot.saveRequest.metadata.customTags["scanMode"])
        assertEquals("document-basic-archive", shot.postProcessSpec.algorithmProfile)
        assertEquals("basic-archive", shot.postProcessSpec.exifOverrides["ProcessingRendered"])
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "document enhancement is unavailable"
            )
        )
    }

    @Test
    fun `preview binding and first frame update metrics and diagnostics`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(
            SessionIntent.PreviewBindingStarted(
                reason = "initial attach",
                isRecovery = false
            )
        )
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(84))
        session.dispatch(
            SessionIntent.PreviewSnapshotUpdated(
                ThumbnailSource.PreviewSnapshot("/tmp/preview.jpg")
            )
        )
        advanceUntilIdle()

        assertEquals(PreviewStatus.ACTIVE, session.state.value.previewStatus)
        assertEquals(84, session.state.value.previewMetrics.lastFirstFrameLatencyMillis)
        assertEquals("/tmp/preview.jpg", session.state.value.previewThumbnailPath)
        assertEquals("Preview active (84 ms first frame)", session.state.value.lastAction)
    }

    @Test
    fun `mode switch is blocked while recording`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        session.dispatch(SessionIntent.SwitchMode(ModeId.PHOTO))
        advanceUntilIdle()

        assertEquals(ModeId.VIDEO, session.state.value.activeMode)
        assertEquals("Stop recording before switching modes", session.state.value.lastAction)
    }

    @Test
    fun `mode switch is blocked while photo capture is in flight`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()

        assertEquals(ModeId.PHOTO, session.state.value.activeMode)
        assertEquals(
            "Wait for current capture to finish before switching modes",
            session.state.value.lastAction
        )
    }

    @Test
    fun `second ordinary still tap is accepted after DATA_RECEIVED rearm`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)

        // First capture: ShutterPressed → ShotStarted → DataReceived
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot1 = assertNotNull(session.state.value.activeShot)
        assertEquals(CaptureStatus.REQUESTED, session.state.value.captureStatus)

        session.dispatch(SessionIntent.ShotStarted(shot1))
        runCurrent()
        assertEquals(CaptureStatus.SAVING, session.state.value.captureStatus)

        session.dispatch(SessionIntent.DataReceived(shot1.shotId, MediaType.PHOTO))
        runCurrent()
        assertEquals(CaptureStatus.DATA_RECEIVED, session.state.value.captureStatus)
        assertNull(session.state.value.activeShot)

        // Second capture: ShutterPressed must be accepted (activeShot is null at DATA_RECEIVED)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot2 = assertNotNull(session.state.value.activeShot)
        assertEquals(CaptureStatus.REQUESTED, session.state.value.captureStatus)
        assertTrue(shot2.shotId != shot1.shotId)

        // Verify second shot goes through full lifecycle
        session.dispatch(SessionIntent.ShotStarted(shot2))
        runCurrent()
        assertEquals(CaptureStatus.SAVING, session.state.value.captureStatus)
        assertEquals(shot2.shotId, session.state.value.activeShot?.shotId)

        session.dispatch(SessionIntent.ShotCompleted(
            ShotResult(
                shotId = shot2.shotId,
                mediaType = MediaType.PHOTO,
                outputPath = "Pictures/OpenCamera/second.jpg",
                saveRequest = SaveRequest.photoLibrary(),
                thumbnailSource = ThumbnailSource.SavedMedia("Pictures/OpenCamera/second.jpg"),
                metadata = SaveRequest.photoLibrary().metadata
            )
        ))
        runCurrent()
        assertEquals(CaptureStatus.IDLE, session.state.value.captureStatus)
        assertNull(session.state.value.activeShot)

        assertTrue(trace.snapshot().any { it.name == "capture.data.received" })
    }

    @Test
    fun `shutter tap is blocked while ordinary photo activeShot is non-null`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot1 = assertNotNull(session.state.value.activeShot)

        // Second ShutterPressed while activeShot is still set — must be blocked
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()

        assertEquals(shot1.shotId, session.state.value.activeShot?.shotId)
        assertEquals("Wait for current capture to finish before sending another command", session.state.value.lastAction)
        assertTrue(trace.snapshot().any { it.name == "mode.intent.blocked" })
    }

    @Test
    fun `live photo rearms on DataReceived while postprocess continues`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(livePhotoEnabledByDefault = true)
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ShotKind.LIVE_PHOTO, shot.shotKind)

        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()
        assertEquals(CaptureStatus.SAVING, session.state.value.captureStatus)

        session.dispatch(SessionIntent.DataReceived(shot.shotId, MediaType.PHOTO))
        runCurrent()
        assertEquals(CaptureStatus.DATA_RECEIVED, session.state.value.captureStatus)
        assertNull(session.state.value.activeShot)
        assertEquals(shot.shotId, session.state.value.presentation.pendingPostprocess?.shotId)

        // ShotCompleted clears pending postprocess and publishes final media.
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = shot.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/live.jpg",
                    saveRequest = SaveRequest.photoLibrary(
                        metadata = MediaMetadata(customTags = mapOf("livePhotoDefault" to "on"))
                    ),
                    thumbnailSource = ThumbnailSource.SavedMedia("Pictures/OpenCamera/live.jpg"),
                    metadata = MediaMetadata(customTags = mapOf("livePhotoDefault" to "on")),
                    livePhotoBundle = LivePhotoBundle(
                        stillPath = "Pictures/OpenCamera/live.jpg",
                        motionPath = "Pictures/OpenCamera/live.live.mp4",
                        sidecarPath = "Pictures/OpenCamera/live.live.json",
                        motionDurationMillis = 1500,
                        motionMimeType = "video/mp4",
                        sidecarMimeType = "application/vnd.opencamera.live+json"
                    )
                )
            )
        )
        advanceUntilIdle()
        assertNull(session.state.value.activeShot)
        assertNull(session.state.value.presentation.pendingPostprocess)
    }

    @Test
    fun `session state exposes independent active device graph for active mode`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertEquals(
            CaptureTemplate.STILL_CAPTURE,
            session.state.value.activeDeviceGraph.template
        )
        assertTrue(session.state.value.activeDeviceGraph.preview.snapshotsEnabled)

        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()

        assertEquals(
            CaptureTemplate.VIDEO_RECORDING,
            session.state.value.activeDeviceGraph.template
        )
        assertTrue(session.state.value.activeDeviceGraph.recording.audioEnabledWhenPermitted)
    }

    @Test
    fun `lens toggle updates active graph and persists across mode switches`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT)
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertEquals(LensFacing.BACK, session.state.value.activeDeviceGraph.preferredLensFacing)
        assertEquals(LensFacing.BACK, session.state.value.activeDeviceGraph.activeLensFacing)

        session.dispatch(SessionIntent.LensFacingToggled)
        advanceUntilIdle()

        assertEquals(LensFacing.FRONT, session.state.value.activeDeviceGraph.preferredLensFacing)
        assertEquals(LensFacing.FRONT, session.state.value.activeDeviceGraph.activeLensFacing)
        assertEquals("Switched to front lens", session.state.value.lastAction)

        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()

        assertEquals(ModeId.VIDEO, session.state.value.activeMode)
        assertEquals(LensFacing.FRONT, session.state.value.activeDeviceGraph.preferredLensFacing)
        assertEquals(LensFacing.FRONT, session.state.value.activeDeviceGraph.activeLensFacing)
    }

    @Test
    fun `lens toggle is ignored when no alternate lens exists`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK)
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.LensFacingToggled)
        advanceUntilIdle()

        assertEquals(LensFacing.BACK, session.state.value.activeDeviceGraph.preferredLensFacing)
        assertEquals("No alternate lens available on this device", session.state.value.lastAction)
    }

    @Test
    fun `still quality toggle updates active graph and persists across mode switches`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertEquals(
            StillCaptureQualityPreference.QUALITY,
            session.state.value.activeDeviceGraph.stillCapture.qualityPreference
        )

        session.dispatch(SessionIntent.StillCaptureQualityToggled)
        advanceUntilIdle()

        assertEquals(
            StillCaptureQualityPreference.LATENCY,
            session.state.value.activeDeviceGraph.stillCapture.qualityPreference
        )
        assertEquals("Still quality set to Fast", session.state.value.lastAction)
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Still Fast"))

        session.dispatch(SessionIntent.SwitchMode(ModeId.HUMANISTIC))
        advanceUntilIdle()

        assertEquals(ModeId.HUMANISTIC, session.state.value.activeMode)
        assertEquals(
            StillCaptureQualityPreference.LATENCY,
            session.state.value.activeDeviceGraph.stillCapture.qualityPreference
        )
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(StillCaptureQualityPreference.LATENCY, shot.captureProfile.stillCaptureQuality)
        assertEquals("latency", shot.saveRequest.metadata.customTags["stillQuality"])
    }

    @Test
    fun `still quality toggle is ignored outside still capture modes`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()

        session.dispatch(SessionIntent.StillCaptureQualityToggled)
        advanceUntilIdle()

        assertEquals(
            CaptureTemplate.VIDEO_RECORDING,
            session.state.value.activeDeviceGraph.template
        )
        assertEquals(
            "Still quality is only available in photo modes",
            session.state.value.lastAction
        )
    }

    @Test
    fun `still resolution toggle updates active graph and persists across mode switches`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertEquals(
            StillCaptureResolutionPreset.LARGE_12MP,
            session.state.value.activeDeviceGraph.stillCapture.resolutionPreset
        )

        session.dispatch(SessionIntent.StillCaptureResolutionToggled)
        advanceUntilIdle()

        assertEquals(
            StillCaptureResolutionPreset.MEDIUM_8MP,
            session.state.value.activeDeviceGraph.stillCapture.resolutionPreset
        )
        assertEquals("Still resolution set to 8MP", session.state.value.lastAction)
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("8MP"))

        session.dispatch(SessionIntent.SwitchMode(ModeId.HUMANISTIC))
        advanceUntilIdle()

        assertEquals(ModeId.HUMANISTIC, session.state.value.activeMode)
        assertEquals(
            StillCaptureResolutionPreset.MEDIUM_8MP,
            session.state.value.activeDeviceGraph.stillCapture.resolutionPreset
        )
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("8MP"))

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(
            StillCaptureResolutionPreset.MEDIUM_8MP,
            shot.captureProfile.stillCaptureResolutionPreset
        )
        assertEquals("8mp", shot.saveRequest.metadata.customTags["stillResolution"])
    }

    @Test
    fun `still resolution toggle is ignored outside still capture modes`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()

        session.dispatch(SessionIntent.StillCaptureResolutionToggled)
        advanceUntilIdle()

        assertEquals(
            CaptureTemplate.VIDEO_RECORDING,
            session.state.value.activeDeviceGraph.template
        )
        assertEquals(
            "Still resolution is only available in photo modes",
            session.state.value.lastAction
        )
    }

    @Test
    fun `still resolution toggle cycles only through presets supported by current capabilities`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableStillCaptureResolutionPresets = setOf(
                    StillCaptureResolutionPreset.MEDIUM_8MP,
                    StillCaptureResolutionPreset.SMALL_2MP
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertEquals(
            StillCaptureResolutionPreset.MEDIUM_8MP,
            session.state.value.activeDeviceGraph.stillCapture.resolutionPreset
        )

        session.dispatch(SessionIntent.StillCaptureResolutionToggled)
        advanceUntilIdle()

        assertEquals(
            StillCaptureResolutionPreset.SMALL_2MP,
            session.state.value.activeDeviceGraph.stillCapture.resolutionPreset
        )

        session.dispatch(SessionIntent.StillCaptureResolutionToggled)
        advanceUntilIdle()

        assertEquals(
            StillCaptureResolutionPreset.MEDIUM_8MP,
            session.state.value.activeDeviceGraph.stillCapture.resolutionPreset
        )
    }

    @Test
    fun `still resolution toggle cycles native output sizes and annotates shot metadata`() = runTest {
        val outputSizes = listOf(
            StillCaptureOutputSize(width = 4000, height = 3000),
            StillCaptureOutputSize(width = 3264, height = 2448),
            StillCaptureOutputSize(width = 1600, height = 1200)
        )
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableStillCaptureOutputSizes = outputSizes,
                availableStillCaptureResolutionPresets = setOf(
                    StillCaptureResolutionPreset.LARGE_12MP,
                    StillCaptureResolutionPreset.MEDIUM_8MP,
                    StillCaptureResolutionPreset.SMALL_2MP
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertEquals(
            outputSizes.first(),
            session.state.value.activeDeviceGraph.stillCapture.outputSize
        )

        session.dispatch(SessionIntent.StillCaptureResolutionToggled)
        advanceUntilIdle()

        assertEquals(
            outputSizes[1],
            session.state.value.activeDeviceGraph.stillCapture.outputSize
        )
        assertEquals(
            StillCaptureResolutionPreset.MEDIUM_8MP,
            session.state.value.activeDeviceGraph.stillCapture.resolutionPreset
        )
        assertEquals("Still resolution set to 3264x2448", session.state.value.lastAction)

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(
            "3264x2448",
            shot.saveRequest.metadata.customTags["stillOutputSize"]
        )
        assertEquals("8mp", shot.saveRequest.metadata.customTags["stillResolution"])
    }

    @Test
    fun `preview ratio toggle updates active device graph preview stream aspect`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertEquals(PreviewRatio.FULL, session.state.value.previewRatio)
        assertEquals(
            PreviewStreamAspect.FULL,
            session.state.value.activeDeviceGraph.preview.streamAspect
        )

        session.dispatch(SessionIntent.PreviewRatioToggled)
        advanceUntilIdle()

        assertEquals(PreviewRatio.RATIO_4_3, session.state.value.previewRatio)
        assertEquals(
            PreviewStreamAspect.RATIO_4_3,
            session.state.value.activeDeviceGraph.preview.streamAspect
        )
        assertEquals("Preview ratio set to 4:3", session.state.value.lastAction)
    }

    @Test
    fun `preview ratio toggle rebinds active preview with updated stream aspect`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)
        val effects = mutableListOf<SessionEffect>()
        val effectCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effects += it }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(12))
        advanceUntilIdle()

        effects.clear()
        session.dispatch(SessionIntent.PreviewRatioToggled)
        advanceUntilIdle()

        val bindEffect = effects.filterIsInstance<SessionEffect.BindPreview>().lastOrNull()
        assertNotNull(bindEffect)
        assertFalse(bindEffect.isRecovery)
        assertEquals("preview ratio changed to 4:3", bindEffect.reason)
        assertEquals(
            PreviewStreamAspect.RATIO_4_3,
            bindEffect.deviceGraph.preview.streamAspect
        )

        effectCollector.cancel()
    }

    @Test
    fun `device capability update clamps still resolution to supported preset`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        session.dispatch(SessionIntent.DeviceCapabilitiesUpdated(DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureResolutionPresets = setOf(StillCaptureResolutionPreset.SMALL_2MP)
        )))
        advanceUntilIdle()

        assertEquals(
            StillCaptureResolutionPreset.SMALL_2MP,
            session.state.value.activeDeviceGraph.stillCapture.resolutionPreset
        )
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("2MP"))
        assertEquals(
            "Still resolution adjusted to 2MP for current lens",
            session.state.value.lastAction
        )
    }

    @Test
    fun `unsupported mode switch is ignored and session keeps processing intents`() = runTest {
        val trace = InMemorySessionTrace()
        val session = DefaultCameraSession(
            registry = ModeRegistry(
                testModePlugins()
            ),
            trace = trace,
            baseDeviceCapabilities = DeviceCapabilities(
                supportsStillCapture = false,
                supportsVideoRecording = true
            ),
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            defaultMode = ModeId.VIDEO
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.PHOTO))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        advanceUntilIdle()

        assertEquals(ModeId.VIDEO, session.state.value.activeMode)
        assertEquals(RecordingStatus.RECORDING, session.state.value.recordingStatus)
        assertEquals("Recording in progress", session.state.value.modeSnapshot.state.headline)
        assertTrue(trace.snapshot().any { it.name == "mode.switch.unsupported" })
    }

    @Test
    fun `surface loss transitions into recovery and tracks counts`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(
            SessionIntent.PreviewBindingStarted(
                reason = "initial attach",
                isRecovery = false
            )
        )
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(90))
        session.dispatch(SessionIntent.PreviewSurfaceLost("Preview stream returned to IDLE"))
        session.dispatch(
            SessionIntent.PreviewBindingStarted(
                reason = "surface lost",
                isRecovery = true
            )
        )
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(120))
        advanceUntilIdle()

        assertEquals(PreviewStatus.ACTIVE, session.state.value.previewStatus)
        assertEquals(2, session.state.value.previewMetrics.bindCount)
        assertEquals(1, session.state.value.previewMetrics.recoveryCount)
        assertEquals(90, session.state.value.previewMetrics.bestFirstFrameLatencyMillis)
        assertEquals(120, session.state.value.previewMetrics.worstFirstFrameLatencyMillis)
    }

    @Test
    fun `camera permission loss blocks preview and clears active recording state`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = false, microphoneGranted = true))
        advanceUntilIdle()

        assertEquals(PreviewStatus.BLOCKED, session.state.value.previewStatus)
        assertEquals(RecordingStatus.IDLE, session.state.value.recordingStatus)
        assertNull(session.state.value.recordingStartedAtElapsedMillis)
        assertNull(session.state.value.recordingElapsedMillis)
        assertEquals("Camera permission missing", session.state.value.lastError)
        assertEquals("Recording failed", session.state.value.modeSnapshot.state.headline)
        assertEquals("Camera permission missing", session.state.value.modeSnapshot.state.detail)
        assertEquals(null, session.state.value.activeShot)
        assertTrue(
            trace.snapshot().any { event ->
                event.name == "recording.failed" &&
                    event.detail == "${shot.shotId}:Camera permission missing"
            }
        )
    }

    @Test
    fun `camera permission loss fails active photo shot and clears in flight capture`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = false, microphoneGranted = true))
        advanceUntilIdle()

        assertEquals(PreviewStatus.BLOCKED, session.state.value.previewStatus)
        assertEquals(CaptureStatus.IDLE, session.state.value.captureStatus)
        assertEquals("Camera permission missing", session.state.value.lastError)
        assertEquals("Photo capture failed", session.state.value.modeSnapshot.state.headline)
        assertEquals("Camera permission missing", session.state.value.modeSnapshot.state.detail)
        assertEquals(null, session.state.value.activeShot)
        assertTrue(
            trace.snapshot().any { event ->
                event.name == "capture.failed" &&
                    event.detail == "${shot.shotId}:Camera permission missing"
            }
        )
    }

    @Test
    fun `unsupported default mode falls back to first supported mode`() = runTest {
        val session = DefaultCameraSession(
            registry = ModeRegistry(
                testModePlugins()
            ),
            trace = InMemorySessionTrace(),
            baseDeviceCapabilities = DeviceCapabilities(
                supportsStillCapture = false,
                supportsVideoRecording = true
            ),
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            defaultMode = ModeId.PHOTO
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertEquals(ModeId.VIDEO, session.state.value.activeMode)
        assertEquals(listOf(ModeId.VIDEO), session.state.value.availableModes)
    }

    @Test
    fun `trace keeps only the latest configured events`() {
        val trace = InMemorySessionTrace(maxEvents = 3)

        repeat(5) { index ->
            trace.record("event$index", "detail$index")
        }

        assertEquals(listOf(3, 4, 5), trace.snapshot().map { it.sequence })
        assertEquals(listOf("event2", "event3", "event4"), trace.snapshot().map { it.name })
    }

    @Test
    fun `capture feedback snapshot is accepted when shot is active`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))

        session.dispatch(
            SessionIntent.CaptureFeedbackSnapshotUpdated(
                shotId = shot.shotId,
                outputPath = "/tmp/feedback-a.jpg"
            )
        )
        advanceUntilIdle()

        assertNotNull(session.state.value.presentation.pendingCaptureFeedback)
        assertEquals(shot.shotId, session.state.value.presentation.pendingCaptureFeedback?.shotId)
        assertEquals("/tmp/feedback-a.jpg", session.state.value.presentation.pendingCaptureFeedback?.outputPath)
        assertTrue(trace.snapshot().any { it.name == "capture.feedback.snapshot.updated" })
    }

    @Test
    fun `capture feedback snapshot is ignored for unknown shotId`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))

        session.dispatch(
            SessionIntent.CaptureFeedbackSnapshotUpdated(
                shotId = "wrong-shot-id",
                outputPath = "/tmp/feedback-b.jpg"
            )
        )
        advanceUntilIdle()

        assertNull(session.state.value.presentation.pendingCaptureFeedback)
        assertTrue(trace.snapshot().any { it.name == "capture.feedback.snapshot.skipped" })
    }

    @Test
    fun `capture feedback snapshot is ignored when no active shot`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        session.dispatch(
            SessionIntent.CaptureFeedbackSnapshotUpdated(
                shotId = "ghost-shot",
                outputPath = "/tmp/feedback-c.jpg"
            )
        )
        advanceUntilIdle()

        assertNull(session.state.value.presentation.pendingCaptureFeedback)
        assertTrue(trace.snapshot().any { it.name == "capture.feedback.snapshot.skipped" })
    }

    @Test
    fun `shot completed clears capture feedback preview`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))

        session.dispatch(
            SessionIntent.CaptureFeedbackSnapshotUpdated(
                shotId = shot.shotId,
                outputPath = "/tmp/feedback-d.jpg"
            )
        )
        advanceUntilIdle()
        assertNotNull(session.state.value.presentation.pendingCaptureFeedback)

        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = shot.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/photo-d.jpg",
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailSource = ThumbnailSource.SavedMedia(
                        "Pictures/OpenCamera/photo-d.jpg",
                        "file:///tmp/photo-d.jpg"
                    ),
                    metadata = SaveRequest.photoLibrary().metadata
                )
            )
        )
        advanceUntilIdle()

        assertNull(session.state.value.presentation.pendingCaptureFeedback)
        assertTrue(session.state.value.latestThumbnailSource is ThumbnailSource.SavedMedia)
        assertEquals("Pictures/OpenCamera/photo-d.jpg", session.state.value.previewThumbnailPath)
    }

    @Test
    fun `shot failed clears capture feedback preview`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))

        session.dispatch(
            SessionIntent.CaptureFeedbackSnapshotUpdated(
                shotId = shot.shotId,
                outputPath = "/tmp/feedback-e.jpg"
            )
        )
        advanceUntilIdle()
        assertNotNull(session.state.value.presentation.pendingCaptureFeedback)

        session.dispatch(
            SessionIntent.ShotFailed(
                shotId = shot.shotId,
                mediaType = MediaType.PHOTO,
                reason = "camera error"
            )
        )
        advanceUntilIdle()

        assertNull(session.state.value.presentation.pendingCaptureFeedback)
        assertEquals(CaptureStatus.FAILED, session.state.value.captureStatus)
    }

    @Test
    fun `shot started clears stale capture feedback`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shotA = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shotA))

        session.dispatch(
            SessionIntent.CaptureFeedbackSnapshotUpdated(
                shotId = shotA.shotId,
                outputPath = "/tmp/feedback-f.jpg"
            )
        )
        advanceUntilIdle()
        assertNotNull(session.state.value.presentation.pendingCaptureFeedback)

        // Complete first shot so a new shot can start
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = shotA.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/photo-f.jpg",
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailSource = ThumbnailSource.SavedMedia(
                        "Pictures/OpenCamera/photo-f.jpg",
                        "file:///tmp/photo-f.jpg"
                    ),
                    metadata = SaveRequest.photoLibrary().metadata
                )
            )
        )
        advanceUntilIdle()
        assertNull(session.state.value.presentation.pendingCaptureFeedback)

        // Start a second shot; stale feedback from previous shot should already be gone
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shotB = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shotB))
        advanceUntilIdle()

        assertNull(session.state.value.presentation.pendingCaptureFeedback)
    }

    @Test
    fun `saved media thumbnail survives after subsequent shot fails`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shotA = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shotA))
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = shotA.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/photo-good.jpg",
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailSource = ThumbnailSource.SavedMedia(
                        "Pictures/OpenCamera/photo-good.jpg",
                        "file:///tmp/photo-good.jpg"
                    ),
                    metadata = SaveRequest.photoLibrary().metadata
                )
            )
        )
        advanceUntilIdle()
        assertEquals("Pictures/OpenCamera/photo-good.jpg", session.state.value.previewThumbnailPath)
        assertTrue(session.state.value.latestThumbnailSource is ThumbnailSource.SavedMedia)

        // Fail a subsequent shot
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shotB = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shotB))
        session.dispatch(
            SessionIntent.ShotFailed(shotId = shotB.shotId, mediaType = MediaType.PHOTO, reason = "camera error")
        )
        advanceUntilIdle()

        // Thumbnail from previous successful shot should survive
        assertEquals("Pictures/OpenCamera/photo-good.jpg", session.state.value.previewThumbnailPath)
        assertTrue(session.state.value.latestThumbnailSource is ThumbnailSource.SavedMedia)
        assertEquals(CaptureStatus.FAILED, session.state.value.captureStatus)
        assertNull(session.state.value.presentation.pendingCaptureFeedback)
        assertEquals("camera error", session.state.value.lastError)
    }

    @Test
    fun `preview snapshot before first saved media populates thumbnail`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        // Before any saved media, preview snapshot fills thumbnail
        session.dispatch(
            SessionIntent.PreviewSnapshotUpdated(
                ThumbnailSource.PreviewSnapshot("/tmp/preview-initial.jpg")
            )
        )
        advanceUntilIdle()

        assertEquals("/tmp/preview-initial.jpg", session.state.value.previewThumbnailPath)
        assertTrue(session.state.value.latestThumbnailSource is ThumbnailSource.PreviewSnapshot)
    }

    @Test
    fun `watermark expanded frame suppresses raw capture feedback`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        val watermarkedShot = shot.copy(
            saveRequest = shot.saveRequest.copy(
                metadata = shot.saveRequest.metadata.copy(
                    watermarkText = "OpenCamera",
                    customTags = shot.saveRequest.metadata.customTags + ("watermarkTemplate" to "travel-polaroid")
                )
            )
        )
        session.dispatch(SessionIntent.ShotStarted(watermarkedShot))
        session.dispatch(
            SessionIntent.CaptureFeedbackSnapshotUpdated(
                shotId = watermarkedShot.shotId,
                outputPath = "/tmp/raw-feedback.jpg"
            )
        )
        advanceUntilIdle()

        assertNull(session.state.value.presentation.pendingCaptureFeedback)
        assertTrue(trace.snapshot().any { it.name == "capture.feedback.snapshot.suppressed" })
    }

    @Test
    fun `watermark with classic-overlay template accepts capture feedback`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        val overlayShot = shot.copy(
            saveRequest = shot.saveRequest.copy(
                metadata = shot.saveRequest.metadata.copy(
                    watermarkText = "OpenCamera",
                    customTags = shot.saveRequest.metadata.customTags + ("watermarkTemplate" to "classic-overlay")
                )
            )
        )
        session.dispatch(SessionIntent.ShotStarted(overlayShot))
        session.dispatch(
            SessionIntent.CaptureFeedbackSnapshotUpdated(
                shotId = overlayShot.shotId,
                outputPath = "/tmp/overlay-feedback.jpg"
            )
        )
        advanceUntilIdle()

        assertNotNull(session.state.value.presentation.pendingCaptureFeedback)
        assertEquals(overlayShot.shotId, session.state.value.presentation.pendingCaptureFeedback?.shotId)
        assertEquals("/tmp/overlay-feedback.jpg", session.state.value.presentation.pendingCaptureFeedback?.outputPath)
        assertTrue(trace.snapshot().any { it.name == "capture.feedback.snapshot.updated" })
    }

    @Test
    fun `watermark with non-overlay template suppresses raw capture feedback`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        val watermarkedShot = shot.copy(
            saveRequest = shot.saveRequest.copy(
                metadata = shot.saveRequest.metadata.copy(
                    watermarkText = "OpenCamera",
                    customTags = shot.saveRequest.metadata.customTags
                        .filterKeys { it != "watermarkTemplate" } + ("watermarkTemplate" to "frame-expand")
                )
            )
        )
        session.dispatch(SessionIntent.ShotStarted(watermarkedShot))
        session.dispatch(
            SessionIntent.CaptureFeedbackSnapshotUpdated(
                shotId = watermarkedShot.shotId,
                outputPath = "/tmp/raw-feedback-non-overlay.jpg"
            )
        )
        advanceUntilIdle()

        assertNull(session.state.value.presentation.pendingCaptureFeedback)
        assertTrue(trace.snapshot().any { it.name == "capture.feedback.snapshot.suppressed" })
    }

    @Test
    fun `color lab filtered photo suppresses raw capture feedback`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        val filteredShot = shot.copy(
            saveRequest = shot.saveRequest.copy(
                metadata = shot.saveRequest.metadata.copy(
                    customTags = shot.saveRequest.metadata.customTags + mapOf(
                        "filterProfile" to "photo-original",
                        "filterSpec.version" to "1",
                        "filterSpec.warmthShift" to "12",
                        "filterSpec.contrast" to "1.17"
                    )
                )
            )
        )
        session.dispatch(SessionIntent.ShotStarted(filteredShot))
        session.dispatch(
            SessionIntent.CaptureFeedbackSnapshotUpdated(
                shotId = filteredShot.shotId,
                outputPath = "/tmp/raw-feedback-filtered.jpg"
            )
        )
        advanceUntilIdle()

        assertNull(session.state.value.presentation.pendingCaptureFeedback)
        assertTrue(trace.snapshot().any {
            it.name == "capture.feedback.snapshot.suppressed" &&
                it.detail.contains("final-output-postprocess")
        })
    }

    @Test
    fun `color lab filtered photo shot completed updates thumbnail to saved media`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        val filteredShot = shot.copy(
            saveRequest = shot.saveRequest.copy(
                metadata = shot.saveRequest.metadata.copy(
                    customTags = shot.saveRequest.metadata.customTags + mapOf(
                        "filterProfile" to "photo-original",
                        "filterSpec.version" to "1",
                        "filterSpec.warmthShift" to "12",
                        "filterSpec.contrast" to "1.17"
                    )
                )
            )
        )
        session.dispatch(SessionIntent.ShotStarted(filteredShot))
        advanceUntilIdle()

        assertEquals(CaptureStatus.SAVING, session.state.value.captureStatus)
        assertNotNull(session.state.value.activeShot)

        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = filteredShot.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/color-lab-photo.jpg",
                    saveRequest = filteredShot.saveRequest,
                    thumbnailSource = ThumbnailSource.SavedMedia(
                        outputPath = "Pictures/OpenCamera/color-lab-photo.jpg"
                    ),
                    metadata = filteredShot.saveRequest.metadata,
                    pipelineNotes = listOf(
                        "algorithm-render:applied:photo-original",
                        "filterSpec.warmthShift=12",
                        "filterSpec.contrast=1.17"
                    )
                )
            )
        )
        advanceUntilIdle()

        assertEquals(CaptureStatus.IDLE, session.state.value.captureStatus)
        assertNull(session.state.value.activeShot)
        assertEquals("Pictures/OpenCamera/color-lab-photo.jpg", session.state.value.latestCapturePath)
        assertEquals(SavedMediaType.PHOTO, session.state.value.latestSavedMediaType)
        assertTrue(session.state.value.latestThumbnailSource is ThumbnailSource.SavedMedia)
        assertEquals("Pictures/OpenCamera/color-lab-photo.jpg", session.state.value.previewThumbnailPath)
        assertTrue(session.state.value.latestPipelineNotes.contains("algorithm-render:applied:photo-original"))
        assertTrue(trace.snapshot().any { it.name == "capture.saved" })
    }

    @Test
    fun `color lab shot postprocess failure still completes with degraded save`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        val filteredShot = shot.copy(
            saveRequest = shot.saveRequest.copy(
                metadata = shot.saveRequest.metadata.copy(
                    customTags = shot.saveRequest.metadata.customTags + mapOf(
                        "filterProfile" to "custom-lab-vivid",
                        "filterSpec.version" to "1",
                        "filterSpec.brightnessShift" to "5",
                        "filterSpec.saturation" to "1.2"
                    )
                )
            )
        )
        session.dispatch(SessionIntent.ShotStarted(filteredShot))
        advanceUntilIdle()

        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = filteredShot.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/color-lab-degraded.jpg",
                    saveRequest = filteredShot.saveRequest,
                    thumbnailSource = ThumbnailSource.SavedMedia(
                        outputPath = "Pictures/OpenCamera/color-lab-degraded.jpg"
                    ),
                    metadata = filteredShot.saveRequest.metadata,
                    pipelineNotes = listOf("algorithm-render:failed:render-exception")
                )
            )
        )
        advanceUntilIdle()

        assertEquals(CaptureStatus.IDLE, session.state.value.captureStatus)
        assertNull(session.state.value.activeShot)
        assertEquals("Pictures/OpenCamera/color-lab-degraded.jpg", session.state.value.latestCapturePath)
        assertTrue(session.state.value.latestThumbnailSource is ThumbnailSource.SavedMedia)
        assertTrue(session.state.value.latestPipelineNotes.contains("algorithm-render:failed:render-exception"))
        assertTrue(trace.snapshot().any { it.name == "capture.saved" })
    }

    @Test
    fun `non default frame ratio suppresses raw capture feedback`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        val framedShot = shot.copy(
            saveRequest = shot.saveRequest.copy(
                metadata = shot.saveRequest.metadata.copy(
                    customTags = shot.saveRequest.metadata.customTags
                        .filterKeys { it != "frameRatio" } + ("frameRatio" to "16:9")
                )
            )
        )
        session.dispatch(SessionIntent.ShotStarted(framedShot))
        session.dispatch(
            SessionIntent.CaptureFeedbackSnapshotUpdated(
                shotId = framedShot.shotId,
                outputPath = "/tmp/raw-feedback-frame.jpg"
            )
        )
        advanceUntilIdle()

        assertNull(session.state.value.presentation.pendingCaptureFeedback)
        assertTrue(trace.snapshot().any {
            it.name == "capture.feedback.snapshot.suppressed" &&
                it.detail.contains("final-output-postprocess")
        })
    }

    // --- Tap-to-focus metering ---

    @Test
    fun `active preview tap emits ApplyPreviewMetering effect`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val effects = mutableListOf<SessionEffect>()
        val effectCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effect -> effects += effect }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(100))
        advanceUntilIdle()

        effects.clear()
        session.dispatch(SessionIntent.PreviewTapToFocus(0.5f, 0.4f))
        runCurrent()

        val meteringEffect = effects.filterIsInstance<SessionEffect.ApplyPreviewMetering>().singleOrNull()
        assertNotNull(meteringEffect)
        assertEquals("meter-1", meteringEffect.request.requestId)
        assertEquals(0.5f, meteringEffect.request.point.normalizedX)
        assertEquals(0.4f, meteringEffect.request.point.normalizedY)
        assertEquals(PreviewMeteringFeedbackStatus.REQUESTED, session.state.value.presentation.previewMeteringFeedback?.status)
        assertTrue(trace.snapshot().any { it.name == "preview.metering.requested" })

        effectCollector.cancel()
    }

    @Test
    fun `tap focus request point is clamped to 0`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val effects = mutableListOf<SessionEffect>()
        val effectCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effect -> effects += effect }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(100))
        advanceUntilIdle()

        effects.clear()
        session.dispatch(SessionIntent.PreviewTapToFocus(-0.1f, 1.5f))
        runCurrent()

        val meteringEffect = effects.filterIsInstance<SessionEffect.ApplyPreviewMetering>().singleOrNull()
        assertNotNull(meteringEffect)
        assertEquals(0f, meteringEffect.request.point.normalizedX)
        assertEquals(1f, meteringEffect.request.point.normalizedY)

        effectCollector.cancel()
    }

    @Test
    fun `preview inactive tap is ignored and emits no metering effect`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val effects = mutableListOf<SessionEffect>()
        val effectCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effect -> effects += effect }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        effects.clear()
        session.dispatch(SessionIntent.PreviewSurfaceLost("test surface lost"))
        advanceUntilIdle()
        effects.clear()

        session.dispatch(SessionIntent.PreviewTapToFocus(0.5f, 0.5f))
        advanceUntilIdle()

        val meteringEffects = effects.filterIsInstance<SessionEffect.ApplyPreviewMetering>()
        assertTrue(meteringEffects.isEmpty())
        assertTrue(trace.snapshot().any { it.name == "preview.metering.ignored" })

        effectCollector.cancel()
    }

    @Test
    fun `missing permission tap is ignored and emits no metering effect`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val effects = mutableListOf<SessionEffect>()
        val effectCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effect -> effects += effect }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = false, microphoneGranted = false))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()
        effects.clear()

        session.dispatch(SessionIntent.PreviewTapToFocus(0.5f, 0.5f))
        advanceUntilIdle()

        val meteringEffects = effects.filterIsInstance<SessionEffect.ApplyPreviewMetering>()
        assertTrue(meteringEffects.isEmpty())
        assertTrue(trace.snapshot().any { it.name == "preview.metering.ignored" })

        effectCollector.cancel()
    }

    @Test
    fun `metering completion updates feedback to SUCCEEDED`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val effects = mutableListOf<SessionEffect>()
        val effectCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effect -> effects += effect }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(100))
        advanceUntilIdle()

        session.dispatch(SessionIntent.PreviewTapToFocus(0.5f, 0.4f))
        runCurrent()

        session.dispatch(
            SessionIntent.PreviewMeteringCompleted(
                com.opencamera.core.device.PreviewMeteringResult(
                    requestId = "meter-1",
                    point = com.opencamera.core.device.PreviewMeteringPoint(0.5f, 0.4f),
                    status = com.opencamera.core.device.PreviewMeteringResultStatus.SUCCEEDED
                )
            )
        )
        runCurrent()

        assertEquals(PreviewMeteringFeedbackStatus.SUCCEEDED, session.state.value.presentation.previewMeteringFeedback?.status)
        assertTrue(trace.snapshot().any { it.name == "preview.metering.succeeded" })

        effectCollector.cancel()
    }

    @Test
    fun `stale metering completion does not overwrite newer request`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val effects = mutableListOf<SessionEffect>()
        val effectCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effect -> effects += effect }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(100))
        advanceUntilIdle()

        session.dispatch(SessionIntent.PreviewTapToFocus(0.3f, 0.3f))
        runCurrent()
        session.dispatch(SessionIntent.PreviewTapToFocus(0.6f, 0.6f))
        runCurrent()

        session.dispatch(
            SessionIntent.PreviewMeteringCompleted(
                com.opencamera.core.device.PreviewMeteringResult(
                    requestId = "meter-1",
                    point = com.opencamera.core.device.PreviewMeteringPoint(0.3f, 0.3f),
                    status = com.opencamera.core.device.PreviewMeteringResultStatus.SUCCEEDED
                )
            )
        )
        runCurrent()

        assertEquals(PreviewMeteringFeedbackStatus.REQUESTED, session.state.value.presentation.previewMeteringFeedback?.status)
        assertEquals("meter-2", session.state.value.presentation.previewMeteringFeedback?.requestId)
        assertTrue(trace.snapshot().any { it.name == "preview.metering.stale" })

        effectCollector.cancel()
    }

    @Test
    fun `unsupported metering result updates feedback to UNSUPPORTED`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val effects = mutableListOf<SessionEffect>()
        val effectCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effect -> effects += effect }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(100))
        advanceUntilIdle()

        session.dispatch(SessionIntent.PreviewTapToFocus(0.5f, 0.5f))
        runCurrent()

        session.dispatch(
            SessionIntent.PreviewMeteringCompleted(
                com.opencamera.core.device.PreviewMeteringResult(
                    requestId = "meter-1",
                    point = com.opencamera.core.device.PreviewMeteringPoint(0.5f, 0.5f),
                    status = com.opencamera.core.device.PreviewMeteringResultStatus.UNSUPPORTED,
                    reason = "AF not available"
                )
            )
        )
        runCurrent()

        assertEquals(PreviewMeteringFeedbackStatus.UNSUPPORTED, session.state.value.presentation.previewMeteringFeedback?.status)
        assertEquals("AF not available", session.state.value.presentation.previewMeteringFeedback?.reason)
        assertTrue(trace.snapshot().any { it.name == "preview.metering.unsupported" })

        effectCollector.cancel()
    }

    // --- Output Rotation ---

    @Test
    fun `dispatching new rotation updates state and emits one update effect`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val effects = mutableListOf<SessionEffect>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effects += it }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        session.dispatch(SessionIntent.OutputRotationChanged(com.opencamera.core.device.CameraOutputRotation.ROTATION_90))
        advanceUntilIdle()

        assertEquals(com.opencamera.core.device.CameraOutputRotation.ROTATION_90, session.state.value.outputRotation)
        val rotationEffects = effects.filterIsInstance<SessionEffect.UpdateOutputRotation>()
        assertEquals(1, rotationEffects.size)
        assertEquals(com.opencamera.core.device.CameraOutputRotation.ROTATION_90, rotationEffects[0].rotation)
        assertTrue(trace.snapshot().any { it.name == "orientation.output.changed" })
        job.cancel()
    }

    @Test
    fun `dispatching same rotation twice emits no duplicate effect`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val effects = mutableListOf<SessionEffect>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effects += it }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        session.dispatch(SessionIntent.OutputRotationChanged(com.opencamera.core.device.CameraOutputRotation.ROTATION_90))
        advanceUntilIdle()
        session.dispatch(SessionIntent.OutputRotationChanged(com.opencamera.core.device.CameraOutputRotation.ROTATION_90))
        advanceUntilIdle()

        val rotationEffects = effects.filterIsInstance<SessionEffect.UpdateOutputRotation>()
        assertEquals(1, rotationEffects.size)
        assertTrue(trace.snapshot().any { it.name == "orientation.output.skipped" })
        job.cancel()
    }

    @Test
    fun `rotation change during active preview does not emit BindPreview`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val effects = mutableListOf<SessionEffect>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effects += it }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(100))
        advanceUntilIdle()

        effects.clear()

        session.dispatch(SessionIntent.OutputRotationChanged(com.opencamera.core.device.CameraOutputRotation.ROTATION_270))
        advanceUntilIdle()

        assertFalse(effects.any { it is SessionEffect.BindPreview })
        assertTrue(effects.any { it is SessionEffect.UpdateOutputRotation })
        job.cancel()
    }

    @Test
    fun `rotation change while recording does not stop active shot`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val effects = mutableListOf<SessionEffect>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effects += it }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(100))
        advanceUntilIdle()

        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val videoShot = session.state.value.activeShot
        assertNotNull(videoShot)

        effects.clear()

        session.dispatch(SessionIntent.OutputRotationChanged(com.opencamera.core.device.CameraOutputRotation.ROTATION_90))
        advanceUntilIdle()

        assertNotNull(session.state.value.activeShot)
        assertFalse(effects.any { it is SessionEffect.StopActiveShot })
        job.cancel()
    }

    // --- G2/G3 Switch Latency Timing ---

    @Test
    fun `mode switch records link event with correct flow and stage`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(64))
        advanceUntilIdle()

        assertEquals(ModeId.PHOTO, session.state.value.activeMode)

        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(180))
        advanceUntilIdle()

        val events = session.linkEventSnapshot()
        val modeSwitchEvents = events.filter { it.flow == "mode-switch" && it.stage == "total" }
        assertEquals(1, modeSwitchEvents.size)
        val modeEvent = modeSwitchEvents.first()
        assertEquals(LinkEventStatus.COMPLETED, modeEvent.status)
        assertNotNull(modeEvent.durationMillis)
        assertTrue(modeEvent.durationMillis!! >= 0)
        assertEquals("DefaultCameraSession", modeEvent.source)
    }

    @Test
    fun `mode switch records sub-step trace events`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(64))
        advanceUntilIdle()

        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(120))
        advanceUntilIdle()

        val traceEvents = trace.snapshot()
        assertTrue(traceEvents.any { it.name == "mode.switch.unbind" })
        assertTrue(traceEvents.any { it.name == "mode.switch.bind" })
        assertTrue(traceEvents.any { it.name == "mode.switch.first.frame" })
        // existing events preserved
        assertTrue(traceEvents.any { it.name == "mode.switched" })
        assertTrue(traceEvents.any { it.name == "mode.switch.started" })
    }

    @Test
    fun `lens switch records link event with correct flow and stage`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT)
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(55))
        advanceUntilIdle()

        session.dispatch(SessionIntent.LensFacingToggled)
        advanceUntilIdle()
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(210))
        advanceUntilIdle()

        val events = session.linkEventSnapshot()
        val lensSwitchEvents = events.filter { it.flow == "lens-switch" && it.stage == "total" }
        assertEquals(1, lensSwitchEvents.size)
        val lensEvent = lensSwitchEvents.first()
        assertEquals(LinkEventStatus.COMPLETED, lensEvent.status)
        assertNotNull(lensEvent.durationMillis)
        assertTrue(lensEvent.durationMillis!! >= 0)
        assertEquals("DefaultCameraSession", lensEvent.source)
    }

    @Test
    fun `lens switch records sub-step trace events`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT)
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(55))
        advanceUntilIdle()

        session.dispatch(SessionIntent.LensFacingToggled)
        advanceUntilIdle()
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(150))
        advanceUntilIdle()

        val traceEvents = trace.snapshot()
        assertTrue(traceEvents.any { it.name == "lens.switch.unbind" })
        assertTrue(traceEvents.any { it.name == "lens.switch.bind" })
        assertTrue(traceEvents.any { it.name == "lens.switch.first.frame" })
        // existing events preserved
        assertTrue(traceEvents.any { it.name == "lens.switched" })
        assertTrue(traceEvents.any { it.name == "lens.switch.started" })
    }

    @Test
    fun `mode switch span completed as failed on preview error`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(64))
        advanceUntilIdle()

        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()
        session.dispatch(SessionIntent.PreviewError("camera disconnected"))
        advanceUntilIdle()

        val events = session.linkEventSnapshot()
        val failedEvents = events.filter { it.flow == "mode-switch" && it.status == LinkEventStatus.FAILED }
        assertEquals(1, failedEvents.size)
    }

    @Test
    fun `mode switch blocked by active shot does not start link span`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(64))
        advanceUntilIdle()

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        // Try switching while a shot is active
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()

        val events = session.linkEventSnapshot()
        val modeSwitchEvents = events.filter { it.flow == "mode-switch" }
        assertTrue(modeSwitchEvents.isEmpty())
        assertTrue(trace.snapshot().any { it.name == "mode.switch.blocked" })
    }

    @Test
    fun `lens switch blocked by countdown does not start link span`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(
                        countdownDuration = CountdownDuration.SECONDS_3
                    )
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(64))
        advanceUntilIdle()

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()

        session.dispatch(SessionIntent.LensFacingToggled)
        advanceUntilIdle()

        val events = session.linkEventSnapshot()
        val lensSwitchEvents = events.filter { it.flow == "lens-switch" }
        assertTrue(lensSwitchEvents.isEmpty())
        assertTrue(trace.snapshot().any { it.name == "lens.switch.blocked" })
    }

    private fun createSession(
        trace: InMemorySessionTrace,
        testScope: TestScope,
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        settingsSnapshot: SessionSettingsSnapshot = SessionSettingsSnapshot(),
        capabilityGraphResolver: com.opencamera.core.capability.CapabilityGraphResolver? = null,
        capabilityRequirements: () -> List<com.opencamera.core.capability.CapabilityRequirement> = { emptyList() },
        recordingTimerDispatcher: CoroutineDispatcher = Dispatchers.Default,
        elapsedRealtimeMillis: () -> Long = { System.nanoTime() / 1_000_000L }
    ): DefaultCameraSession {
        var shotIndex = 0
        return DefaultCameraSession(
            registry = ModeRegistry(
                testModePlugins()
            ),
            trace = trace,
            baseDeviceCapabilities = deviceCapabilities,
            scope = TestScope(StandardTestDispatcher(testScope.testScheduler)),
            settingsSnapshot = settingsSnapshot,
            shotExecutor = ShotExecutor(idGenerator = { "shot-${++shotIndex}" }),
            capabilityGraphResolver = capabilityGraphResolver,
            capabilityRequirements = capabilityRequirements,
            recordingTimerDispatcher = recordingTimerDispatcher,
            elapsedRealtimeMillis = elapsedRealtimeMillis
        )
    }

    private fun testModePlugins() = listOf(
        PhotoModePlugin(),
        DocumentModePlugin(),
        HumanisticModePlugin(),
        CheckInModePlugin(),
        VideoModePlugin()
    )

    // --- Capability Graph Resolver integration ---

    @Test
    fun `activeCapabilityReport remains null when resolver is null`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertNull(session.state.value.activeCapabilityReport)
    }

    @Test
    fun `activeCapabilityReport remains null when requirements are empty`() = runTest {
        val trace = InMemorySessionTrace()
        val resolver = com.opencamera.core.capability.CapabilityGraphResolver(
            deviceQuery = DeviceCapabilities.DEFAULT.asCapabilityGraphQuery(),
            mediaProcessors = com.opencamera.core.media.MediaProcessorAvailability.ALL_AVAILABLE
        )
        val session = createSession(
            trace = trace,
            testScope = this,
            capabilityGraphResolver = resolver,
            capabilityRequirements = { emptyList() }
        )
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertNull(session.state.value.activeCapabilityReport)
    }

    @Test
    fun `activeCapabilityReport is populated when resolver and requirements provided`() = runTest {
        val trace = InMemorySessionTrace()
        val resolver = com.opencamera.core.capability.CapabilityGraphResolver(
            deviceQuery = DeviceCapabilities.DEFAULT.asCapabilityGraphQuery(),
            mediaProcessors = com.opencamera.core.media.MediaProcessorAvailability.ALL_AVAILABLE
        )
        val session = createSession(
            trace = trace,
            testScope = this,
            capabilityGraphResolver = resolver,
            capabilityRequirements = {
                listOf(
                    com.opencamera.core.capability.CapabilityRequirement(
                        id = "still",
                        kind = com.opencamera.core.capability.CapabilityRequirementKind.STILL_CAPTURE,
                        requiredFor = setOf(com.opencamera.core.capability.CapabilityUseSite.CAPTURE)
                    )
                )
            }
        )
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        val report = session.state.value.activeCapabilityReport
        assertNotNull(report)
        assertEquals(1, report.resolved.size)
        assertEquals("still", report.resolved.first().requirement.id)
    }

    @Test
    fun `capabilityPipelineNotes returns empty when no report`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertTrue(session.state.value.capabilityPipelineNotes.isEmpty())
    }

    // --- Low-light night assist ---

    @Test
    fun `low light signal in photo mode with active preview shows prompt`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewBindingStarted(reason = "test", isRecovery = false))
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(50))
        advanceUntilIdle()

        session.dispatch(
            SessionIntent.PhotoSceneSignalUpdated(
                PhotoSceneSignal(
                    lightState = SceneLightState.LOW_LIGHT,
                    brightnessScore = 0.15f,
                    source = "preview-bitmap-luma"
                )
            )
        )
        runCurrent()

        val prompt = session.state.value.presentation.photoLowLightPrompt
        assertNotNull(prompt)
        assertEquals(PhotoLowLightPromptStatus.AVAILABLE_ENABLED, prompt.status)
        assertNotNull(prompt.visibleUntilElapsedMillis)
        assertEquals(0.15f, prompt.brightnessScore)
        assertTrue(trace.snapshot().any { it.name == "photo.low-light.detected" })
        assertTrue(trace.snapshot().any { it.name == "photo.low-light.prompt.visible" })
    }

    @Test
    fun `low light signal outside photo mode does not show prompt`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.PreviewBindingStarted(reason = "test", isRecovery = false))
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(50))
        advanceUntilIdle()

        session.dispatch(
            SessionIntent.PhotoSceneSignalUpdated(
                PhotoSceneSignal(
                    lightState = SceneLightState.LOW_LIGHT,
                    brightnessScore = 0.15f,
                    source = "preview-bitmap-luma"
                )
            )
        )
        advanceUntilIdle()

        assertNull(session.state.value.presentation.photoLowLightPrompt)
        assertTrue(trace.snapshot().any { it.name == "photo.low-light.ignored" })
    }

    @Test
    fun `disabled setting shows disabled prompt state on low light detection`() = runTest {
        val trace = InMemorySessionTrace()
        val settings = SessionSettingsSnapshot(
            persisted = PersistedSettings(
                photo = PhotoSettings(lowLightNightAssistEnabled = false)
            )
        )
        val session = createSession(trace, this, settingsSnapshot = settings)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewBindingStarted(reason = "test", isRecovery = false))
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(50))
        advanceUntilIdle()

        session.dispatch(
            SessionIntent.PhotoSceneSignalUpdated(
                PhotoSceneSignal(
                    lightState = SceneLightState.LOW_LIGHT,
                    brightnessScore = 0.12f,
                    source = "preview-bitmap-luma"
                )
            )
        )
        runCurrent()

        val prompt = session.state.value.presentation.photoLowLightPrompt
        assertNotNull(prompt)
        assertEquals(PhotoLowLightPromptStatus.AVAILABLE_DISABLED, prompt.status)
    }

    @Test
    fun `multi frame support maps to available state`() = runTest {
        val trace = InMemorySessionTrace()
        val caps = DeviceCapabilities.DEFAULT.copy(
            supportsStillCapture = true,
            supportsNightMultiFrame = true
        )
        val session = createSession(trace, this, deviceCapabilities = caps)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewBindingStarted(reason = "test", isRecovery = false))
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(50))
        advanceUntilIdle()

        session.dispatch(
            SessionIntent.PhotoSceneSignalUpdated(
                PhotoSceneSignal(
                    lightState = SceneLightState.LOW_LIGHT,
                    brightnessScore = 0.10f,
                    source = "test"
                )
            )
        )
        runCurrent()

        val prompt = session.state.value.presentation.photoLowLightPrompt
        assertNotNull(prompt)
        assertEquals(PhotoLowLightPromptStatus.AVAILABLE_ENABLED, prompt.status)
    }

    @Test
    fun `no multi frame maps to degraded state`() = runTest {
        val trace = InMemorySessionTrace()
        val caps = DeviceCapabilities.DEFAULT.copy(
            supportsStillCapture = true,
            supportsNightMultiFrame = false
        )
        val session = createSession(trace, this, deviceCapabilities = caps)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewBindingStarted(reason = "test", isRecovery = false))
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(50))
        advanceUntilIdle()

        session.dispatch(
            SessionIntent.PhotoSceneSignalUpdated(
                PhotoSceneSignal(
                    lightState = SceneLightState.LOW_LIGHT,
                    brightnessScore = 0.10f,
                    source = "test"
                )
            )
        )
        runCurrent()

        val prompt = session.state.value.presentation.photoLowLightPrompt
        assertNotNull(prompt)
        assertEquals(PhotoLowLightPromptStatus.DEGRADED_ENABLED, prompt.status)
    }

    @Test
    fun `unsupported still capture does not present as available`() = runTest {
        val trace = InMemorySessionTrace()
        val caps = DeviceCapabilities.DEFAULT.copy(
            supportsStillCapture = false,
            supportsNightMultiFrame = false
        )
        val session = createSession(trace, this, deviceCapabilities = caps)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewBindingStarted(reason = "test", isRecovery = false))
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(50))
        advanceUntilIdle()

        session.dispatch(
            SessionIntent.PhotoSceneSignalUpdated(
                PhotoSceneSignal(
                    lightState = SceneLightState.LOW_LIGHT,
                    brightnessScore = 0.10f,
                    source = "test"
                )
            )
        )
        runCurrent()

        val prompt = session.state.value.presentation.photoLowLightPrompt
        assertNotNull(prompt)
        assertEquals(PhotoLowLightPromptStatus.UNSUPPORTED, prompt.status)
    }

    @Test
    fun `photo scene signal is stored in presentation state`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        val signal = PhotoSceneSignal(
            lightState = SceneLightState.NORMAL,
            brightnessScore = 0.45f,
            source = "test"
        )
        session.dispatch(SessionIntent.PhotoSceneSignalUpdated(signal))
        advanceUntilIdle()

        assertEquals(signal, session.state.value.presentation.photoSceneSignal)
    }

    // ── Document batch ────────────────────────────────────────────

    @Test
    fun `switching to document mode creates active batch with fresh batchId`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertEquals(
            DocumentBatchStatus.INACTIVE,
            session.state.value.presentation.documentBatch.status
        )
        assertTrue(session.state.value.presentation.documentBatch.batchId.isEmpty())

        session.dispatch(SessionIntent.SwitchMode(ModeId.DOCUMENT))
        advanceUntilIdle()

        assertEquals(
            DocumentBatchStatus.ACTIVE,
            session.state.value.presentation.documentBatch.status
        )
        assertTrue(session.state.value.presentation.documentBatch.batchId.isNotEmpty())
        assertTrue(session.state.value.presentation.documentBatch.items.isEmpty())
    }

    @Test
    fun `re-entering document mode after clear reuses existing active batch`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.DOCUMENT))
        advanceUntilIdle()

        val firstBatchId = session.state.value.presentation.documentBatch.batchId

        session.dispatch(SessionIntent.DocumentBatchClear)
        advanceUntilIdle()

        session.dispatch(SessionIntent.SwitchMode(ModeId.PHOTO))
        advanceUntilIdle()
        session.dispatch(SessionIntent.SwitchMode(ModeId.DOCUMENT))
        advanceUntilIdle()

        assertEquals(firstBatchId, session.state.value.presentation.documentBatch.batchId)
    }

    @Test
    fun `clearing document batch removes all items but keeps batch active`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.DOCUMENT))
        advanceUntilIdle()

        suspend fun dispatchShot(path: String) {
            session.dispatch(SessionIntent.ShutterPressed)
            advanceUntilIdle()
            val shot = assertNotNull(session.state.value.activeShot)
            session.dispatch(SessionIntent.ShotStarted(shot))
            session.dispatch(
                SessionIntent.ShotCompleted(
                    ShotResult(
                        shotId = shot.shotId,
                        mediaType = MediaType.PHOTO,
                        outputPath = path,
                        saveRequest = SaveRequest.photoLibrary(),
                        thumbnailSource = ThumbnailSource.SavedMedia(path),
                        metadata = SaveRequest.photoLibrary().metadata
                    )
                )
            )
            advanceUntilIdle()
        }

        dispatchShot("Pictures/OpenCamera/doc1.jpg")

        assertTrue(session.state.value.presentation.documentBatch.items.isNotEmpty())

        session.dispatch(SessionIntent.DocumentBatchClear)
        advanceUntilIdle()

        assertEquals(
            DocumentBatchStatus.ACTIVE,
            session.state.value.presentation.documentBatch.status
        )
        assertTrue(session.state.value.presentation.documentBatch.items.isEmpty())
        assertNull(session.state.value.presentation.documentBatch.latestItemId)
    }

    @Test
    fun `removing item from batch renumbers remaining items`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.DOCUMENT))
        advanceUntilIdle()

        suspend fun dispatchShot(path: String) {
            session.dispatch(SessionIntent.ShutterPressed)
            advanceUntilIdle()
            val shot = assertNotNull(session.state.value.activeShot)
            session.dispatch(SessionIntent.ShotStarted(shot))
            session.dispatch(
                SessionIntent.ShotCompleted(
                    ShotResult(
                        shotId = shot.shotId,
                        mediaType = MediaType.PHOTO,
                        outputPath = path,
                        saveRequest = SaveRequest.photoLibrary(),
                        thumbnailSource = ThumbnailSource.SavedMedia(path),
                        metadata = SaveRequest.photoLibrary().metadata
                    )
                )
            )
            advanceUntilIdle()
        }

        dispatchShot("Pictures/OpenCamera/doc1.jpg")
        dispatchShot("Pictures/OpenCamera/doc2.jpg")

        val items = session.state.value.presentation.documentBatch.items
        assertEquals(2, items.size)
        assertEquals(0, items[0].orderIndex)
        assertEquals(1, items[1].orderIndex)

        session.dispatch(SessionIntent.DocumentBatchRemoveItem(items[0].itemId))
        advanceUntilIdle()

        val remaining = session.state.value.presentation.documentBatch.items
        assertEquals(1, remaining.size)
        assertEquals(0, remaining[0].orderIndex)
        assertEquals(items[1].itemId, remaining[0].itemId)
    }

    @Test
    fun `removing unknown item does not corrupt batch`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.DOCUMENT))
        advanceUntilIdle()

        session.dispatch(SessionIntent.DocumentBatchRemoveItem("non-existent-id"))
        advanceUntilIdle()

        assertTrue(session.state.value.presentation.documentBatch.items.isEmpty())
        assertEquals(
            "Cannot remove item: non-existent-id not in batch",
            session.state.value.lastAction
        )
    }

    @Test
    fun `moving item up changes order deterministically`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.DOCUMENT))
        advanceUntilIdle()

        suspend fun dispatchShot(path: String) {
            session.dispatch(SessionIntent.ShutterPressed)
            advanceUntilIdle()
            val shot = assertNotNull(session.state.value.activeShot)
            session.dispatch(SessionIntent.ShotStarted(shot))
            session.dispatch(
                SessionIntent.ShotCompleted(
                    ShotResult(
                        shotId = shot.shotId,
                        mediaType = MediaType.PHOTO,
                        outputPath = path,
                        saveRequest = SaveRequest.photoLibrary(),
                        thumbnailSource = ThumbnailSource.SavedMedia(path),
                        metadata = SaveRequest.photoLibrary().metadata
                    )
                )
            )
            advanceUntilIdle()
        }

        dispatchShot("Pictures/OpenCamera/doc1.jpg")
        dispatchShot("Pictures/OpenCamera/doc2.jpg")
        dispatchShot("Pictures/OpenCamera/doc3.jpg")

        val items = session.state.value.presentation.documentBatch.items
        assertEquals(3, items.size)

        session.dispatch(SessionIntent.DocumentBatchMoveItem(items[2].itemId, DocumentBatchMoveDirection.UP))
        advanceUntilIdle()

        val moved = session.state.value.presentation.documentBatch.items
        assertEquals(3, moved.size)
        assertEquals(items[0].itemId, moved[0].itemId)
        assertEquals(items[2].itemId, moved[1].itemId)
        assertEquals(items[1].itemId, moved[2].itemId)
        assertEquals(0, moved[0].orderIndex)
        assertEquals(1, moved[1].orderIndex)
        assertEquals(2, moved[2].orderIndex)
    }

    @Test
    fun `moving item down changes order deterministically`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.DOCUMENT))
        advanceUntilIdle()

        suspend fun dispatchShot(path: String) {
            session.dispatch(SessionIntent.ShutterPressed)
            advanceUntilIdle()
            val shot = assertNotNull(session.state.value.activeShot)
            session.dispatch(SessionIntent.ShotStarted(shot))
            session.dispatch(
                SessionIntent.ShotCompleted(
                    ShotResult(
                        shotId = shot.shotId,
                        mediaType = MediaType.PHOTO,
                        outputPath = path,
                        saveRequest = SaveRequest.photoLibrary(),
                        thumbnailSource = ThumbnailSource.SavedMedia(path),
                        metadata = SaveRequest.photoLibrary().metadata
                    )
                )
            )
            advanceUntilIdle()
        }

        dispatchShot("Pictures/OpenCamera/doc1.jpg")
        dispatchShot("Pictures/OpenCamera/doc2.jpg")
        dispatchShot("Pictures/OpenCamera/doc3.jpg")

        val items = session.state.value.presentation.documentBatch.items
        assertEquals(3, items.size)

        session.dispatch(SessionIntent.DocumentBatchMoveItem(items[0].itemId, DocumentBatchMoveDirection.DOWN))
        advanceUntilIdle()

        val moved = session.state.value.presentation.documentBatch.items
        assertEquals(3, moved.size)
        assertEquals(items[1].itemId, moved[0].itemId)
        assertEquals(items[0].itemId, moved[1].itemId)
        assertEquals(items[2].itemId, moved[2].itemId)
        assertEquals(0, moved[0].orderIndex)
        assertEquals(1, moved[1].orderIndex)
        assertEquals(2, moved[2].orderIndex)
    }

    @Test
    fun `moving unknown item does not alter batch state`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.DOCUMENT))
        advanceUntilIdle()

        val batchBefore = session.state.value.presentation.documentBatch
        session.dispatch(SessionIntent.DocumentBatchMoveItem("non-existent-id", DocumentBatchMoveDirection.DOWN))
        advanceUntilIdle()

        val batchAfter = session.state.value.presentation.documentBatch
        assertEquals(batchBefore.items, batchAfter.items)
        assertEquals("Cannot move: non-existent-id not in batch", session.state.value.lastAction)
    }

    @Test
    fun `shot completed in non-document mode does not append to batch`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertEquals(
            DocumentBatchStatus.INACTIVE,
            session.state.value.presentation.documentBatch.status
        )

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = shot.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/photo.jpg",
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailSource = ThumbnailSource.SavedMedia("Pictures/OpenCamera/photo.jpg"),
                    metadata = SaveRequest.photoLibrary().metadata
                )
            )
        )
        advanceUntilIdle()

        assertTrue(session.state.value.presentation.documentBatch.items.isEmpty())
    }

    @Test
    fun `shot completed in document mode auto-appends to batch`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.DOCUMENT))
        advanceUntilIdle()

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = shot.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/doc_page.jpg",
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailSource = ThumbnailSource.SavedMedia("Pictures/OpenCamera/doc_page.jpg"),
                    metadata = SaveRequest.photoLibrary().metadata
                )
            )
        )
        advanceUntilIdle()

        val batch = session.state.value.presentation.documentBatch
        assertEquals(1, batch.items.size)
        val item = batch.items[0]
        assertEquals(shot.shotId, item.itemId)
        assertEquals(shot.shotId, item.shotId)
        assertEquals(0, item.orderIndex)
        assertEquals("Pictures/OpenCamera/doc_page.jpg", item.outputPath)
        assertEquals(DocumentBatchCropStatus.NOT_REQUESTED, item.cropStatus)
        assertEquals(item.itemId, batch.latestItemId)
    }

    @Test
    fun `latestThumbnailSource unchanged after document batch clear`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.DOCUMENT))
        advanceUntilIdle()

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = shot.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/doc_page.jpg",
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailSource = ThumbnailSource.SavedMedia("Pictures/OpenCamera/doc_page.jpg"),
                    metadata = SaveRequest.photoLibrary().metadata
                )
            )
        )
        advanceUntilIdle()

        val thumbBeforeClear = session.state.value.presentation.latestThumbnailSource
        assertTrue(thumbBeforeClear is ThumbnailSource.SavedMedia)

        session.dispatch(SessionIntent.DocumentBatchClear)
        advanceUntilIdle()

        val thumbAfterClear = session.state.value.presentation.latestThumbnailSource
        assertTrue(thumbAfterClear is ThumbnailSource.SavedMedia)
        assertEquals(
            thumbBeforeClear.outputPath,
            thumbAfterClear.outputPath
        )
    }

    @Test
    fun `removing last item sets latestItemId to null`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.DOCUMENT))
        advanceUntilIdle()

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = shot.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/doc_page.jpg",
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailSource = ThumbnailSource.SavedMedia("Pictures/OpenCamera/doc_page.jpg"),
                    metadata = SaveRequest.photoLibrary().metadata
                )
            )
        )
        advanceUntilIdle()

        assertNotNull(session.state.value.presentation.documentBatch.latestItemId)

        session.dispatch(SessionIntent.DocumentBatchRemoveItem(shot.shotId))
        advanceUntilIdle()

        assertNull(session.state.value.presentation.documentBatch.latestItemId)
    }

    @Test
    fun `moving item at boundary is clamped without error`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.DOCUMENT))
        advanceUntilIdle()

        suspend fun dispatchShot(path: String) {
            session.dispatch(SessionIntent.ShutterPressed)
            advanceUntilIdle()
            val shot = assertNotNull(session.state.value.activeShot)
            session.dispatch(SessionIntent.ShotStarted(shot))
            session.dispatch(
                SessionIntent.ShotCompleted(
                    ShotResult(
                        shotId = shot.shotId,
                        mediaType = MediaType.PHOTO,
                        outputPath = path,
                        saveRequest = SaveRequest.photoLibrary(),
                        thumbnailSource = ThumbnailSource.SavedMedia(path),
                        metadata = SaveRequest.photoLibrary().metadata
                    )
                )
            )
            advanceUntilIdle()
        }

        dispatchShot("Pictures/OpenCamera/doc1.jpg")
        dispatchShot("Pictures/OpenCamera/doc2.jpg")

        val itemsBefore = session.state.value.presentation.documentBatch.items

        session.dispatch(SessionIntent.DocumentBatchMoveItem(itemsBefore[0].itemId, DocumentBatchMoveDirection.UP))
        advanceUntilIdle()

        val itemsAfter = session.state.value.presentation.documentBatch.items
        assertEquals(itemsBefore[0].itemId, itemsAfter[0].itemId)
        assertEquals(itemsBefore[1].itemId, itemsAfter[1].itemId)
        assertEquals("Item already at target position", session.state.value.lastAction)
    }

    @Test
    fun `degraded shot completed from postprocess failure updates thumbnail and clears active shot`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)

        session.dispatch(SessionIntent.ShotStarted(shot))
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = shot.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/degraded-photo.jpg",
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailSource = ThumbnailSource.SavedMedia(
                        outputPath = "Pictures/OpenCamera/degraded-photo.jpg",
                        renderUri = "file:///tmp/degraded-photo.jpg"
                    ),
                    metadata = SaveRequest.photoLibrary().metadata,
                    pipelineNotes = listOf(
                        "algorithm-render:applied:photo-vivid",
                        "postprocess:failed:PhotoWatermark"
                    )
                )
            )
        )
        advanceUntilIdle()

        assertNull(session.state.value.activeShot)
        val thumbnail = session.state.value.latestThumbnailSource
        assertTrue(thumbnail is ThumbnailSource.SavedMedia)
        assertEquals("Pictures/OpenCamera/degraded-photo.jpg", thumbnail.outputPath)
        assertEquals("Photo saved (degraded)", session.state.value.lastAction)
    }

    // --- Lens node threshold and hysteresis ---

    private val twoNodeMap = mapOf(
        LensNode.WIDE to LensNodeAvailability(
            node = LensNode.WIDE, available = true, thresholdRatio = 0f, physicalCameraId = "0"
        ),
        LensNode.TELEPHOTO to LensNodeAvailability(
            node = LensNode.TELEPHOTO, available = true, thresholdRatio = 2.0f, physicalCameraId = "1"
        )
    )

    private val threeNodeMap = twoNodeMap + (
        LensNode.PERISCOPE to LensNodeAvailability(
            node = LensNode.PERISCOPE, available = true, thresholdRatio = 5.0f, physicalCameraId = "2"
        )
    )

    @Test
    fun `evaluateLensNode returns WIDE when lens node map is empty`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        val result = session.evaluateLensNode(3.0f, null, emptyMap())
        assertEquals(LensNode.WIDE, result)
    }

    @Test
    fun `evaluateLensNode returns WIDE for ratio below 2x threshold`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        val result = session.evaluateLensNode(1.5f, null, twoNodeMap)
        assertEquals(LensNode.WIDE, result)
    }

    @Test
    fun `evaluateLensNode returns TELEPHOTO when ratio above threshold plus hysteresis`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        // 2.1 > 2.0 + 0.1 → TELEPHOTO
        val result = session.evaluateLensNode(2.1f, null, twoNodeMap)
        assertEquals(LensNode.TELEPHOTO, result)
    }

    @Test
    fun `evaluateLensNode blocks switch when ratio within hysteresis band going up`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        // 2.05 is above 2.0 threshold but below 2.1 (threshold + hysteresis) → stays WIDE
        val result = session.evaluateLensNode(2.05f, LensNode.WIDE, twoNodeMap)
        assertEquals(LensNode.WIDE, result)
    }

    @Test
    fun `evaluateLensNode switches to TELEPHOTO when crossing threshold with hysteresis`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        // 2.15 > 2.0 + 0.105 (5% of 2.15) → TELEPHOTO
        val result = session.evaluateLensNode(2.15f, LensNode.WIDE, twoNodeMap)
        assertEquals(LensNode.TELEPHOTO, result)
    }

    @Test
    fun `evaluateLensNode switches back to WIDE when dropping below threshold minus hysteresis`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        // 1.8 < 2.0 - 0.105 (5% of 2.1 threshold) → WIDE
        val result = session.evaluateLensNode(1.8f, LensNode.TELEPHOTO, twoNodeMap)
        assertEquals(LensNode.WIDE, result)
    }

    @Test
    fun `evaluateLensNode blocks switch back when ratio within hysteresis band going down`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        // 1.95: actualDelta = max(0.05, 1.95 * 0.05) = 0.0975
        // 1.95 <= 2.0 - 0.0975 = 1.9025? NO → stays TELEPHOTO
        val result = session.evaluateLensNode(1.95f, LensNode.TELEPHOTO, twoNodeMap)
        assertEquals(LensNode.TELEPHOTO, result)
    }

    @Test
    fun `evaluateLensNode returns PERISCOPE for ratio above 5x threshold`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        val result = session.evaluateLensNode(5.1f, null, threeNodeMap)
        assertEquals(LensNode.PERISCOPE, result)
    }

    @Test
    fun `evaluateLensNode drops to TELEPHOTO when leaving PERISCOPE range`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        // 4.7 <= 5.0 - 0.25 (5% of 5.0) → switches to TELEPHOTO
        val result = session.evaluateLensNode(4.7f, LensNode.PERISCOPE, threeNodeMap)
        assertEquals(LensNode.TELEPHOTO, result)
    }

    @Test
    fun `evaluateLensNode hysteresis prevents jitter at threshold boundary`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        // Simulate jitter: ratio oscillates in the hysteresis band (below 2.1)
        var current: LensNode? = null
        current = session.evaluateLensNode(1.95f, current, twoNodeMap)
        assertEquals(LensNode.WIDE, current)
        current = session.evaluateLensNode(2.05f, current, twoNodeMap)
        assertEquals(LensNode.WIDE, current)
        current = session.evaluateLensNode(1.98f, current, twoNodeMap)
        assertEquals(LensNode.WIDE, current)
        // Only switch when clearly past the hysteresis band
        current = session.evaluateLensNode(2.15f, current, twoNodeMap)
        assertEquals(LensNode.TELEPHOTO, current)
    }

    @Test
    fun `evaluateLensNode with unavailable node falls through to WIDE`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        val mapWithUnavailable = mapOf(
            LensNode.WIDE to LensNodeAvailability(
                node = LensNode.WIDE, available = true, thresholdRatio = 0f, physicalCameraId = "0"
            ),
            LensNode.TELEPHOTO to LensNodeAvailability(
                node = LensNode.TELEPHOTO, available = false, thresholdRatio = 2.0f
            )
        )
        // Ratio is above 2x but TELEPHOTO is unavailable → WIDE
        val result = session.evaluateLensNode(3.0f, null, mapWithUnavailable)
        assertEquals(LensNode.WIDE, result)
    }

    // ── computePreviewZoomRatio tests ──────────────────────────────────

    @Test
    fun `computePreviewZoomRatio returns captureZoom when lens node map is empty`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        val result = session.computePreviewZoomRatio(3.0f, emptyMap())
        assertEquals(3.0f, result)
    }

    @Test
    fun `computePreviewZoomRatio returns 1f when captureZoom below 1 and empty map`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        val result = session.computePreviewZoomRatio(0.5f, emptyMap())
        assertEquals(1.0f, result)
    }

    @Test
    fun `computePreviewZoomRatio clamps zero wide threshold and applies frame-scale switch`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        // twoNodeMap: WIDE(0f)→base=1.0, TELE(2.0f)→base=2.0 → bases [1.0, 2.0]
        // switchAt = 1.0 / 0.775 ≈ 1.29
        // At 1.0f: at baseline, returns 1.0
        assertEquals(1.0f, session.computePreviewZoomRatio(1.0f, twoNodeMap))
        // At 1.2f (< switchAt): still in interval, continuous formula gives 1.163
        val at12 = session.computePreviewZoomRatio(1.2f, twoNodeMap)
        assertTrue(at12 >= 1.0f && at12 <= 2.0f, "Expected between 1.0 and 2.0, got $at12")
        // At 1.5f (>= switchAt): switches to base 2.0, continuous formula gives min(0.949*1.5, 2.0)=1.424
        val at15 = session.computePreviewZoomRatio(1.5f, twoNodeMap)
        assertTrue(at15 >= 1.4f && at15 <= 1.45f, "Expected ≈1.424, got $at15")
    }

    @Test
    fun `computePreviewZoomRatio switches to next preview base when frame scale drops below threshold`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        // twoNodeMap gives bases [1.0, 2.0]; switchAt = 1.0/0.775 ≈ 1.29
        // Below switchAt: continuous formula applied within interval
        val at12 = session.computePreviewZoomRatio(1.2f, twoNodeMap)
        assertTrue(at12 >= 1.0f && at12 <= 1.5f, "At 1.2x (below switchAt), got $at12")
        // Above switchAt: switches to base 2.0, continuous formula = min(0.949*1.5, 2.0) = 1.424
        val at15 = session.computePreviewZoomRatio(1.5f, twoNodeMap)
        assertTrue(at15 >= 1.4f && at15 <= 1.5f, "At 1.5x (above switchAt), got $at15")
        // At base value: returns the base directly
        assertEquals(2.0f, session.computePreviewZoomRatio(2.0f, twoNodeMap))
    }

    @Test
    fun `computePreviewZoomRatio keeps ultrawide preview through 1x capture range then frame-scale switch`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        val capability = ZoomRatioCapability(
            support = ZoomControlSupport.CONTINUOUS,
            supportedRatios = listOf(0.7f, 1.0f, 2.0f, 5.0f),
            previewBaseRatios = listOf(0.7f, 1.0f, 3.0f, 5.0f)
        )

        // At 0.7x: baseline, returns 0.7
        assertEquals(0.7f, session.computePreviewZoomRatio(0.7f, capability))
        // At 0.9x: below first base (0.7) + in 0.7→1.0 interval; continuous formula
        val at09 = session.computePreviewZoomRatio(0.9f, capability)
        assertTrue(at09 >= 0.7f && at09 <= 1.0f, "At 0.9x: expected [0.7, 1.0], got $at09")
        // At 1.0x: at baseline 1.0, returns 1.0 (firstBase >= 1.0)
        assertEquals(1.0f, session.computePreviewZoomRatio(1.0f, capability))
        // At 1.5x: in 1.0→3.0 interval; switchAt = 1.0/0.775 ≈ 1.29, 1.5 >= 1.29 → base 3.0
        // Continuous formula: min(0.949*1.5, 3.0) = 1.424
        val at15 = session.computePreviewZoomRatio(1.5f, capability)
        assertTrue(at15 >= 1.4f && at15 <= 1.45f, "At 1.5x: expected ≈1.424, got $at15")
        // At 2.0f: switchAt 1.29 already passed → base 3.0; continuous formula: min(0.949*2.0, 3.0) = 1.898
        val at20 = session.computePreviewZoomRatio(2.0f, capability)
        assertTrue(at20 >= 1.85f && at20 <= 1.95f, "At 2.0x: expected ≈1.898, got $at20")
    }

    @Test
    fun `computePreviewZoomRatio switches to periscope preview base based on frame scale`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        // threeNodeMap gives bases [1.0, 2.0, 5.0]
        // 1.0→2.0 switchAt = 1.0/0.775 ≈ 1.29
        // 2.0→5.0 switchAt = 2.0/0.775 ≈ 2.58
        // At 2.5: switchAt 1.29 passed → base 2.0; switchAt 2.58 not reached
        // Continuous: min(0.949*2.5, 5.0) = 2.372
        val at25 = session.computePreviewZoomRatio(2.5f, threeNodeMap)
        assertTrue(at25 >= 2.3f && at25 <= 2.45f, "At 2.5x: expected ≈2.372, got $at25")
        // At 3.0: 2.0→5.0 switchAt passed → base 5.0; continuous: min(0.949*3.0, 5.0) = 2.847
        val at30 = session.computePreviewZoomRatio(3.0f, threeNodeMap)
        assertTrue(at30 >= 2.8f && at30 <= 2.9f, "At 3.0x: expected ≈2.847, got $at30")
        // At 5.0: baseline, returns 5.0
        assertEquals(5.0f, session.computePreviewZoomRatio(5.0f, threeNodeMap))
    }

    @Test
    fun `computePreviewZoomRatio returns max threshold for captureZoom above all thresholds`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        // captureZoom = 10.0 → max threshold is 5.0 (PERISCOPE)
        val result = session.computePreviewZoomRatio(10.0f, threeNodeMap)
        assertEquals(5.0f, result)
    }

    @Test
    fun `computePreviewZoomRatio previewZoomRatio always less than or equal to captureZoom`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        val testRatios = listOf(0.6f, 1.0f, 1.5f, 2.0f, 3.0f, 5.0f, 8.0f, 10.0f)
        for (captureZoom in testRatios) {
            val previewZoom = session.computePreviewZoomRatio(captureZoom, threeNodeMap)
            assertTrue(
                previewZoom <= captureZoom,
                "previewZoomRatio ($previewZoom) should be <= captureZoom ($captureZoom)"
            )
        }
    }

    @Test
    fun `computePreviewZoomRatio never returns invalid zero when captureZoom below thresholds`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        val result = session.computePreviewZoomRatio(0.3f, threeNodeMap)
        assertEquals(0.3f, result)
    }

    @Test
    fun `computePreviewZoomRatio with unavailable node ignores it`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        val mapWithUnavailable = mapOf(
            LensNode.WIDE to LensNodeAvailability(
                node = LensNode.WIDE, available = true, thresholdRatio = 0f, physicalCameraId = "0"
            ),
            LensNode.TELEPHOTO to LensNodeAvailability(
                node = LensNode.TELEPHOTO, available = false, thresholdRatio = 2.0f
            )
        )
        val result = session.computePreviewZoomRatio(3.0f, mapWithUnavailable)
        assertEquals(1.0f, result)
    }

    @Test
    fun `logical camera preview bases drive previewZoomRatio without lens rebind`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(0.7f, 1f, 2f, 5f, 10f),
                    defaultRatio = 1f,
                    previewBaseRatios = listOf(0.7f, 1f, 3f, 5f),
                    lensNodeMap = mapOf(
                        LensNode.WIDE to LensNodeAvailability(
                            node = LensNode.WIDE,
                            available = true,
                            thresholdRatio = 0.7f,
                            physicalCameraId = "0"
                        )
                    )
                )
            )
        )
        val effects = mutableListOf<SessionEffect>()
        val effectCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effect -> effects += effect }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()
        effects.clear()

        // At 1.5f: in interval [1.0, 3.0]; continuous: min(0.949*1.5, 3.0) = 1.424
        session.dispatch(SessionIntent.ApplyZoomRatio(1.5f))
        advanceUntilIdle()
        val at15 = session.state.value.activeDeviceGraph.preview.previewZoomRatio
        assertTrue(at15 >= 1.4f && at15 <= 1.45f, "At 1.5x: expected ≈1.424, got $at15")

        // At 3.3f: in interval [3.0, 5.0]; continuous: min(0.949*3.3, 5.0) = 3.132
        session.dispatch(SessionIntent.ApplyZoomRatio(3.3f))
        advanceUntilIdle()
        val at33 = session.state.value.activeDeviceGraph.preview.previewZoomRatio
        assertTrue(at33 >= 3.1f && at33 <= 3.2f, "At 3.3x: expected ≈3.132, got $at33")
        assertTrue(effects.filterIsInstance<SessionEffect.ApplyZoomRatio>().any {
            it.zoomRatio == 3.3f && it.previewZoomRatio == at33
        })
        assertTrue(effects.none { it is SessionEffect.SwitchLensNode })

        effectCollector.cancel()
    }

    // ── Continuous previewZoomRatio contract tests ────────────────────────

    @Test
    fun `previewZoomRatio stays within frame-scale bounds for 1x to 10x sweep`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(0.6f, 1f, 2f, 5f, 10f),
                    defaultRatio = 1f,
                    lensNodeMap = threeNodeMap
                )
            )
        )
        val testRatios = listOf(0.6f, 0.7f, 1.0f, 1.3f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f, 7.0f, 10.0f)
        var previousPreviewZoom = 0f
        for (captureZoom in testRatios) {
            session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
            session.dispatch(SessionIntent.Boot)
            advanceUntilIdle()
            session.dispatch(SessionIntent.ApplyZoomRatio(captureZoom))
            advanceUntilIdle()
            val previewZoom = session.state.value.activeDeviceGraph.preview.previewZoomRatio
            val capture = session.state.value.activeDeviceGraph.preview.zoomRatio
            assertTrue(
                previewZoom <= capture,
                "previewZoomRatio ($previewZoom) should be <= captureZoomRatio ($capture) at captureZoom=$captureZoom"
            )
            assertTrue(
                previewZoom >= 0.01f,
                "previewZoomRatio ($previewZoom) should be >= MIN_NON_ZERO at captureZoom=$captureZoom"
            )
            if (previousPreviewZoom > 0f && previousPreviewZoom > 1.0f && previousPreviewZoom < 5.0f) {
                // Between first and last baselines: no consecutive equal values
                assertTrue(
                    previewZoom != previousPreviewZoom,
                    "previewZoomRatio must be continuous (changed at every step), but was $previousPreviewZoom then $previewZoom at captureZoom=$captureZoom"
                )
            }
            previousPreviewZoom = previewZoom
        }
    }

    @Test
    fun `previewZoomRatio continuous between 1x and 2x`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 2f, 5f, 10f),
                    defaultRatio = 1f,
                    lensNodeMap = twoNodeMap
                )
            )
        )
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        val values = mutableListOf<Float>()
        var zoom = 1.0f
        while (zoom <= 2.0f) {
            session.dispatch(SessionIntent.ApplyZoomRatio(zoom))
            advanceUntilIdle()
            values.add(session.state.value.activeDeviceGraph.preview.previewZoomRatio)
            zoom = (zoom * 10f + 0.1f * 10f).roundToInt() / 10f
        }

        // No constant interval: no 3 consecutive equal values
        for (i in 2 until values.size) {
            assertFalse(
                values[i] == values[i - 1] && values[i - 1] == values[i - 2],
                "previewZoomRatio has constant interval at index $i: ${values[i - 2]}, ${values[i - 1]}, ${values[i]}"
            )
        }
        // Always <= captureZoom
        zoom = 1.0f
        for (v in values) {
            assertTrue(v <= zoom, "previewZoomRatio ($v) should be <= captureZoom ($zoom)")
            zoom = (zoom * 10f + 0.1f * 10f).roundToInt() / 10f
        }
    }

    @Test
    fun `previewZoomRatio continuous between 2x and 3x`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 2f, 5f, 10f),
                    defaultRatio = 1f,
                    lensNodeMap = threeNodeMap
                )
            )
        )
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        val values = mutableListOf<Float>()
        var zoom = 2.0f
        while (zoom <= 3.0f) {
            session.dispatch(SessionIntent.ApplyZoomRatio(zoom))
            advanceUntilIdle()
            values.add(session.state.value.activeDeviceGraph.preview.previewZoomRatio)
            zoom = (zoom * 10f + 0.1f * 10f).roundToInt() / 10f
        }

        // No constant interval
        for (i in 2 until values.size) {
            assertFalse(
                values[i] == values[i - 1] && values[i - 1] == values[i - 2],
                "previewZoomRatio has constant interval at index $i: ${values[i - 2]}, ${values[i - 1]}, ${values[i]}"
            )
        }
        // At exact baseline values, returns the baseline
        session.dispatch(SessionIntent.ApplyZoomRatio(2.0f))
        advanceUntilIdle()
        assertEquals(2.0f, session.state.value.activeDeviceGraph.preview.previewZoomRatio)
    }

    @Test
    fun `previewZoomRatio does not jump when physical lens switches`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace = trace,
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(0.6f, 1f, 2f, 5f, 10f),
                    defaultRatio = 1f,
                    lensNodeMap = threeNodeMap
                )
            )
        )
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        // Walk through 1.5x to 2.5x (lens switch at evaluateLensNode threshold ~2.1)
        val zoomSteps = listOf(1.5f, 1.6f, 1.7f, 1.8f, 1.9f, 2.0f, 2.1f, 2.2f, 2.3f, 2.4f, 2.5f)
        var prevPreview = 0f
        for (zoom in zoomSteps) {
            session.dispatch(SessionIntent.ApplyZoomRatio(zoom))
            advanceUntilIdle()
            val preview = session.state.value.activeDeviceGraph.preview.previewZoomRatio
            assertTrue(
                preview <= zoom,
                "previewZoomRatio ($preview) should be <= captureZoom ($zoom)"
            )
            // Check for small jumps (< 0.2) between adjacent steps
            if (prevPreview > 0f) {
                val jump = kotlin.math.abs(preview - prevPreview)
                assertTrue(
                    jump < 0.2f,
                    "previewZoomRatio jump too large: $prevPreview → $preview at captureZoom=$zoom (delta=$jump)"
                )
            }
            prevPreview = preview
        }
    }

    @Test
    fun `previewZoomRatio at lens threshold is within continuous bounds`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace = trace, testScope = this)
        // threeNodeMap: evaluateLensNode threshold at ~2.1 (with hysteresis from WIDE)
        // At 2.0x: base 2.0, previewZoom = 2.0 (exact baseline)
        // At 2.1x: base 2.0 (hysteresis keeps WIDE), previewZoom in [2.0, 5.0] interval
        val at20 = session.computePreviewZoomRatio(2.0f, threeNodeMap)
        val at21 = session.computePreviewZoomRatio(2.1f, threeNodeMap)
        assertEquals(2.0f, at20, "At 2.0x baseline: should return 2.0")
        assertTrue(
            at21 >= 1.9f && at21 <= 2.15f,
            "At 2.1x (near threshold): expected within ±0.15 of 2.0, got $at21"
        )
        val jump = kotlin.math.abs(at21 - at20)
        assertTrue(
            jump <= 0.15f,
            "Jump at lens threshold: $at20 → $at21 (delta=$jump) should be ≤ 0.15"
        )
    }

    // ── CaptureReadiness contract tests ───────────────────────────────

    @Test
    fun `CaptureCommitted sets readiness for ordinary still capture`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(CaptureStatus.REQUESTED, session.state.value.captureStatus)

        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()
        assertEquals(CaptureStatus.SAVING, session.state.value.captureStatus)
        assertNull(session.state.value.presentation.captureReadiness)

        session.dispatch(
            SessionIntent.CaptureCommitted(
                shotId = shot.shotId,
                mediaType = MediaType.PHOTO,
                source = "camera2:onCaptureCompleted",
                elapsedTimestampMs = 450L
            )
        )
        runCurrent()

        val readiness = assertNotNull(session.state.value.presentation.captureReadiness)
        assertEquals(shot.shotId, readiness.shotId)
        assertEquals(MediaType.PHOTO, readiness.mediaType)
        assertEquals("camera2:onCaptureCompleted", readiness.source)
        assertEquals(450L, readiness.elapsedTimestampMs)
        assertTrue(trace.snapshot().any { it.name == "capture.committed" })
    }

    @Test
    fun `CaptureCommitted re-arms ordinary still capture for next shutter press`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot1 = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot1))
        runCurrent()

        session.dispatch(
            SessionIntent.CaptureCommitted(
                shotId = shot1.shotId,
                mediaType = MediaType.PHOTO,
                source = "camera2:onCaptureCompleted",
                elapsedTimestampMs = 450L
            )
        )
        runCurrent()

        assertNull(
            session.state.value.activeShot,
            "ordinary still capture should be truly re-armed at CaptureCommitted"
        )

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()

        val shot2 = assertNotNull(session.state.value.activeShot)
        assertTrue(shot2.shotId != shot1.shotId)
        assertEquals(CaptureStatus.REQUESTED, session.state.value.captureStatus)
    }

    @Test
    fun `DataReceived sets capture readiness fallback when Camera2 committed callback is absent`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()
        assertNull(session.state.value.presentation.captureReadiness)

        session.dispatch(SessionIntent.DataReceived(shot.shotId, MediaType.PHOTO))
        runCurrent()

        val readiness = assertNotNull(session.state.value.presentation.captureReadiness)
        assertEquals(shot.shotId, readiness.shotId)
        assertEquals("DeviceEvent.DataReceived", readiness.source)
        assertNull(session.state.value.activeShot)
    }

    @Test
    fun `CaptureCommitted does not set readiness for multi-frame capture`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)

        // Simulate a night/multi-frame shot
        val multiFrameShot = ShotRequest(
            shotId = "shot-mf-1",
            shotKind = ShotKind.MULTI_FRAME_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = com.opencamera.core.media.PostProcessSpec(
                algorithmProfile = "night-multiframe-tripod"
            ),
            captureProfile = com.opencamera.core.media.CaptureProfile(frameCount = 12)
        )

        session.dispatch(SessionIntent.ShotStarted(multiFrameShot))
        runCurrent()

        session.dispatch(
            SessionIntent.CaptureCommitted(
                shotId = multiFrameShot.shotId,
                mediaType = MediaType.PHOTO,
                source = "camera2:onCaptureCompleted",
                elapsedTimestampMs = 800L
            )
        )
        runCurrent()

        assertNull(
            session.state.value.presentation.captureReadiness,
            "Multi-frame capture must NOT set readiness (conservative)"
        )
    }

    @Test
    fun `CaptureCommitted does not set readiness for live photo`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)

        val livePhotoShot = ShotRequest(
            shotId = "shot-live-1",
            shotKind = ShotKind.LIVE_PHOTO,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = com.opencamera.core.media.PostProcessSpec(),
            captureProfile = com.opencamera.core.media.CaptureProfile(),
            livePhotoSpec = com.opencamera.core.media.LivePhotoCaptureSpec()
        )

        session.dispatch(SessionIntent.ShotStarted(livePhotoShot))
        runCurrent()

        session.dispatch(
            SessionIntent.CaptureCommitted(
                shotId = livePhotoShot.shotId,
                mediaType = MediaType.PHOTO,
                source = "camera2:onCaptureCompleted",
                elapsedTimestampMs = 600L
            )
        )
        runCurrent()

        assertNull(
            session.state.value.presentation.captureReadiness,
            "Live photo must NOT set readiness (conservative)"
        )
    }

    @Test
    fun `capture readiness is cleared on next ShotStarted`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)

        // First capture: set readiness
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot1 = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot1))
        runCurrent()
        session.dispatch(
            SessionIntent.CaptureCommitted(
                shotId = shot1.shotId,
                mediaType = MediaType.PHOTO,
                source = "camera2:onCaptureCompleted",
                elapsedTimestampMs = 450L
            )
        )
        runCurrent()
        assertNotNull(session.state.value.presentation.captureReadiness)

        // Complete first shot via DataReceived + ShotCompleted
        session.dispatch(SessionIntent.DataReceived(shot1.shotId, MediaType.PHOTO))
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = shot1.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/first.jpg",
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailSource = ThumbnailSource.SavedMedia("Pictures/OpenCamera/first.jpg"),
                    metadata = SaveRequest.photoLibrary().metadata
                )
            )
        )
        runCurrent()
        assertNull(session.state.value.presentation.captureReadiness)

        // Second capture: readiness should start null, then be set again
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot2 = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot2))
        runCurrent()
        assertNull(
            session.state.value.presentation.captureReadiness,
            "Readiness must be cleared when new shot starts"
        )

        session.dispatch(
            SessionIntent.CaptureCommitted(
                shotId = shot2.shotId,
                mediaType = MediaType.PHOTO,
                source = "camera2:onCaptureCompleted",
                elapsedTimestampMs = 500L
            )
        )
        runCurrent()
        val readiness2 = assertNotNull(session.state.value.presentation.captureReadiness)
        assertEquals(shot2.shotId, readiness2.shotId)
    }

    @Test
    fun `CaptureCommitted does not set readiness when activeShot mismatched`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()

        // Dispatch CaptureCommitted with wrong shotId
        session.dispatch(
            SessionIntent.CaptureCommitted(
                shotId = "wrong-shot-id",
                mediaType = MediaType.PHOTO,
                source = "camera2:onCaptureCompleted",
                elapsedTimestampMs = 300L
            )
        )
        runCurrent()

        assertNull(
            session.state.value.presentation.captureReadiness,
            "Readiness must NOT be set for mismatched shotId"
        )
    }

    @Test
    fun `CaptureCommitted is idempotent and handled once per shot`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        runCurrent()

        session.dispatch(
            SessionIntent.CaptureCommitted(
                shotId = shot.shotId,
                mediaType = MediaType.PHOTO,
                source = "camera2:onCaptureCompleted",
                elapsedTimestampMs = 450L
            )
        )
        runCurrent()
        val readiness1 = assertNotNull(session.state.value.presentation.captureReadiness)
        assertEquals(450L, readiness1.elapsedTimestampMs)

        // Second CaptureCommitted for same shot: readiness remains the first handled value.
        session.dispatch(
            SessionIntent.CaptureCommitted(
                shotId = shot.shotId,
                mediaType = MediaType.PHOTO,
                source = "camera2:onCaptureCompleted",
                elapsedTimestampMs = 500L
            )
        )
        runCurrent()
        assertEquals(
            450L,
            session.state.value.presentation.captureReadiness?.elapsedTimestampMs,
            "Readiness should be handled once for the committed shot"
        )
    }

    @Test
    fun `CaptureCommitted does not break recording stop semantics`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val videoShot = assertNotNull(session.state.value.activeShot)
        assertEquals(MediaType.VIDEO, videoShot.mediaType)

        session.dispatch(SessionIntent.ShotStarted(videoShot))
        runCurrent()
        assertEquals(RecordingStatus.RECORDING, session.state.value.recordingStatus)

        // CaptureCommitted for a video shot: readiness must NOT be set
        session.dispatch(
            SessionIntent.CaptureCommitted(
                shotId = videoShot.shotId,
                mediaType = MediaType.VIDEO,
                source = "camera2:onCaptureCompleted",
                elapsedTimestampMs = 200L
            )
        )
        runCurrent()
        assertNull(session.state.value.presentation.captureReadiness)

        // Recording still active after CaptureCommitted
        assertEquals(RecordingStatus.RECORDING, session.state.value.recordingStatus)
        assertEquals(videoShot.shotId, session.state.value.activeShot?.shotId)

        // Stop recording (via ShotCompleted) works normally
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = videoShot.shotId,
                    mediaType = MediaType.VIDEO,
                    outputPath = "Movies/OpenCamera/test.mp4",
                    saveRequest = videoShot.saveRequest,
                    thumbnailSource = ThumbnailSource.SavedMedia("Movies/OpenCamera/test.mp4"),
                    metadata = SaveRequest.photoLibrary().metadata
                )
            )
        )
        runCurrent()

        assertEquals(RecordingStatus.IDLE, session.state.value.recordingStatus)
        assertNull(session.state.value.activeShot)
    }

    // ── CheckIn mode session-level metadata assertions ──────────────────────

    @Test
    fun `checkin portrait scenario captures with correct metadata and save path`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.CHECK_IN))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ModeId.CHECK_IN, session.state.value.activeMode)
        assertEquals("check-in", shot.saveRequest.metadata.customTags["mode"])
        assertEquals("portrait", shot.saveRequest.metadata.customTags["checkInScenario"])
        assertEquals("portrait", shot.saveRequest.metadata.customTags["compatMode"])
        assertEquals("Check-in", shot.saveRequest.metadata.customTags["watermarkModeName"])
        assertEquals("Pictures/OpenCamera/Check-in", shot.saveRequest.relativePath)
        assertEquals("OpenCamera_CHECKIN", shot.saveRequest.fileNamePrefix)
        assertEquals("Check-in", shot.postProcessSpec.exifOverrides["SceneCaptureType"])
        assertTrue(shot.postProcessSpec.algorithmProfile!!.startsWith("checkin-"))
        assertEquals("Check-in capture requested", session.state.value.modeSnapshot.state.headline)
    }

    @Test
    fun `checkin clarity scenario captures MultiFrame via settings snapshot`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(defaultCheckInScenario = "clarity")
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.CHECK_IN))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("check-in", shot.saveRequest.metadata.customTags["mode"])
        assertEquals("clarity", shot.saveRequest.metadata.customTags["checkInScenario"])
        assertEquals("clarity-assist", shot.saveRequest.metadata.customTags["compatMode"])
        assertEquals("checkin-clarity-best-frame-v1", shot.postProcessSpec.algorithmProfile)
        assertEquals("Check-in Clarity", shot.postProcessSpec.exifOverrides["SceneCaptureType"])
        assertEquals("Clarity Assist", shot.postProcessSpec.exifOverrides["CompatSceneCaptureType"])
        assertEquals(3, shot.captureProfile.frameCount)
    }

    @Test
    fun `checkin clarity degradation falls back to SingleFrame when multi-frame unsupported`() = runTest {
        val session = DefaultCameraSession(
            registry = ModeRegistry(testModePlugins()),
            trace = InMemorySessionTrace(),
            baseDeviceCapabilities = DeviceCapabilities(
                supportsStillCapture = true,
                supportsVideoRecording = true,
                supportsPreviewSnapshots = true,
                supportsAudioRecording = true,
                supportsNightMultiFrame = false
            ),
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(defaultCheckInScenario = "clarity")
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.CHECK_IN))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("clarity", shot.saveRequest.metadata.customTags["checkInScenario"])
        assertEquals("clarity-assist", shot.saveRequest.metadata.customTags["compatMode"])
        assertEquals("single-frame", shot.saveRequest.metadata.customTags["captureStrategyFallback"])
        assertEquals("multi-frame-unsupported", shot.saveRequest.metadata.customTags["degradationReason"])
        assertEquals("single-frame-best-frame", shot.saveRequest.metadata.customTags["degradation-policy"])
        assertEquals("checkin-clarity-best-frame-v1", shot.postProcessSpec.algorithmProfile)
        assertEquals(1, shot.captureProfile.frameCount)
    }

    @Test
    fun `checkin clarity snapshot headline contains scenario via settings snapshot`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(defaultCheckInScenario = "clarity")
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.CHECK_IN))
        advanceUntilIdle()

        val headline = session.state.value.modeSnapshot.state.headline
        assertTrue(headline.contains("Check-in"), "Headline should contain Check-in, got: $headline")
        assertTrue(headline.contains("全清"), "Headline should mention 全清, got: $headline")
    }

    @Test
    fun `checkin capture aid tags are present in session metadata`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.CHECK_IN))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("back", shot.saveRequest.metadata.customTags["captureLensFacing"])
        assertEquals("false", shot.saveRequest.metadata.customTags["selfieMirrorApply"])
        assertTrue(shot.saveRequest.metadata.customTags.containsKey("stillQuality"))
        assertTrue(shot.saveRequest.metadata.customTags.containsKey("style"))
        assertTrue(shot.saveRequest.metadata.customTags.containsKey("bokehStrength"))
        assertTrue(shot.saveRequest.metadata.customTags.containsKey("stillResolution"))
    }

    @Test
    fun `checkin watermark metadata flows through session shot`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(
                        defaultWatermarkTemplateId = "classic-overlay"
                    )
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.CHECK_IN))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("classic-overlay", shot.saveRequest.metadata.customTags["watermarkTemplate"])
        assertTrue(shot.postProcessSpec.watermarkText!!.contains("Check-in"))
    }

    @Test
    fun `checkin style cycling rotates through styles without affecting scenario`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.CHECK_IN))
        session.dispatch(SessionIntent.SecondaryActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        val initialStyle = shot.saveRequest.metadata.customTags["style"]
        assertNotNull(initialStyle)
        assertTrue(initialStyle.startsWith("portrait-"), "Style should start with portrait-, got: $initialStyle")

        session.dispatch(SessionIntent.SecondaryActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot2 = assertNotNull(session.state.value.activeShot)
        val nextStyle = shot2.saveRequest.metadata.customTags["style"]
        assertNotNull(nextStyle)
        assertTrue(nextStyle.startsWith("portrait-"), "Next style should start with portrait-, got: $nextStyle")
    }

    // --- ModeIntent signal path coverage ---

    @Test
    fun `ModeSignal None via ProActionPressed in video mode does not alter capture or recording state`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()

        assertEquals(ModeId.VIDEO, session.state.value.activeMode)
        assertNull(session.state.value.activeShot)
        assertEquals(RecordingStatus.IDLE, session.state.value.recordingStatus)

        session.dispatch(SessionIntent.ProActionPressed)
        advanceUntilIdle()

        assertEquals(ModeId.VIDEO, session.state.value.activeMode)
        assertNull(session.state.value.activeShot)
        assertEquals(RecordingStatus.IDLE, session.state.value.recordingStatus)
        assertEquals(CaptureStatus.IDLE, session.state.value.captureStatus)
        assertEquals(
            "session.booted",
            trace.snapshot().first { it.name == "session.booted" || it.name == "mode.signal" }.name
        )
    }

    @Test
    fun `ModeSignal SubmitCapture via ShutterPressed in photo mode starts capture`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        assertEquals(ModeId.PHOTO, session.state.value.activeMode)
        assertNull(session.state.value.activeShot)

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(MediaType.PHOTO, shot.mediaType)
        assertTrue(
            session.state.value.captureStatus == CaptureStatus.REQUESTED ||
                session.state.value.captureStatus == CaptureStatus.IDLE
        )
        assertNull(session.state.value.presentation.countdownRemainingSeconds)
    }

    @Test
    fun `ModeSignal SubmitCapture with countdown sets countdown remaining`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace, this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(countdownDuration = CountdownDuration.SECONDS_3)
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()

        assertEquals(3, session.state.value.presentation.countdownRemainingSeconds)
        assertEquals(CaptureStatus.REQUESTED, session.state.value.captureStatus)

        // Verify countdown decrements
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(2, session.state.value.presentation.countdownRemainingSeconds)
    }

    @Test
    fun `ModeSignal StopActiveCapture via ShutterPressed during recording sets stopping`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)
        val effects = mutableListOf<SessionEffect>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            session.effects.collect { effects += it }
        }

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(MediaType.VIDEO, shot.mediaType)
        session.dispatch(SessionIntent.ShotStarted(shot))
        advanceUntilIdle()
        assertEquals(RecordingStatus.RECORDING, session.state.value.recordingStatus)

        effects.clear()
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        assertEquals(RecordingStatus.STOPPING, session.state.value.recordingStatus)
        assertTrue(effects.any { it is SessionEffect.StopActiveShot })
        job.cancel()
    }

    @Test
    fun `ModeSignal ShowHint via FrameRatioSelected in video mode sets hint message`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()

        session.dispatch(SessionIntent.FrameRatioSelected(FrameRatio.RATIO_16_9))
        advanceUntilIdle()

        assertEquals("视频模式暂不支持画幅切换", session.state.value.lastAction)
        assertEquals(ModeId.VIDEO, session.state.value.activeMode)
        assertNull(session.state.value.activeShot)
    }
}
