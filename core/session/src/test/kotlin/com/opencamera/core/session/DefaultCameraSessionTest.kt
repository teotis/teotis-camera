package com.opencamera.core.session

import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.asCapabilityGraphQuery
import com.opencamera.core.device.DeviceRuntimeIssueKind
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.RecordingQualityPreset
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.media.FlashMode
import com.opencamera.core.media.LiveBundleStatus
import com.opencamera.core.media.LiveMotionSource
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.LiveTemporalWindow
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotExecutor
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.feature.document.DocumentModePlugin
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
import com.opencamera.feature.night.NightModePlugin
import com.opencamera.feature.photo.PhotoModePlugin
import com.opencamera.feature.portrait.PortraitModePlugin
import com.opencamera.feature.pro.ProModePlugin
import com.opencamera.feature.video.VideoModePlugin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCameraSessionTest {
    @Test
    fun `boot preserves attached preview host state when permission is granted`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

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
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        session.dispatch(SessionIntent.PreviewSurfaceLost("surface lost while recording"))
        advanceUntilIdle()

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
        advanceUntilIdle()

        session.dispatch(SessionIntent.PreviewError("camera provider restarted"))
        advanceUntilIdle()

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
        advanceUntilIdle()

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

        val shot = assertNotNull(session.state.value.activeShot)
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
        advanceUntilIdle()

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
        advanceUntilIdle()

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
        advanceUntilIdle()

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
        advanceUntilIdle()

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
        advanceUntilIdle()

        assertEquals(CaptureStatus.COMPLETED, session.state.value.captureStatus)
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
        advanceUntilIdle()
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
        advanceUntilIdle()
        assertEquals("Pictures/OpenCamera/photo-a.jpg", session.state.value.previewThumbnailPath)

        session.dispatch(
            SessionIntent.PreviewSnapshotUpdated(
                ThumbnailSource.PreviewSnapshot("/tmp/preview-b.jpg")
            )
        )
        advanceUntilIdle()

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
    fun `zoom toggle remains available while recording and blocks only unsupported devices`() = runTest {
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
        session.dispatch(SessionIntent.ZoomRatioToggled)
        advanceUntilIdle()

        assertEquals(2f, session.state.value.activeDeviceGraph.preview.zoomRatio)
        assertEquals("Zoom set to 2.0x", session.state.value.lastAction)
        assertTrue(trace.snapshot().any { it.name == "zoom.updated" })
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

        val shot = assertNotNull(session.state.value.activeShot)
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
        advanceUntilIdle()

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
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        advanceUntilIdle()

        assertEquals(0L, session.state.value.recordingElapsedMillis)

        advanceTimeBy(3_000)
        advanceUntilIdle()

        val elapsed = session.state.value.recordingElapsedMillis
        assertNotNull(elapsed)
        assertTrue("Expected elapsed >= 3000 but was $elapsed", elapsed >= 3_000L)
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
        advanceUntilIdle()

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

        assertEquals("Toggle Torch", session.state.value.modeSnapshot.uiSpec.secondaryActionLabel)
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
        assertEquals("PHOTO Auto", shot.postProcessSpec.watermarkText)
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
        assertEquals("source-vivid-blur", shot.saveRequest.metadata.customTags["watermarkFrameBackground"])
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

        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = "shot-live",
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

        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = "shot-still-only",
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

        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = "shot-degraded",
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
        assertEquals("PHOTO Off", shot.postProcessSpec.watermarkText)
    }

    @Test
    fun `pro mode cycles manual presets and annotates capture request`() = runTest {
        val session = createSession(
            InMemorySessionTrace(),
            this,
            settingsSnapshot = SessionSettingsSnapshot(
                catalog = FeatureCatalog(
                    manualCaptureDraft = ManualCaptureParams(
                        rawEnabled = true,
                        iso = 320,
                        shutterSpeedMillis = 33L,
                        exposureCompensationSteps = 1,
                        focusDistanceDiopters = 2.5f,
                        apertureFNumber = 1.8f,
                        whiteBalanceKelvin = 4800
                    )
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.PRO))
        session.dispatch(SessionIntent.SecondaryActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ModeId.PRO, session.state.value.activeMode)
        assertEquals("street", shot.saveRequest.metadata.customTags["preset"])
        assertEquals("manual", shot.saveRequest.metadata.customTags["controlMode"])
        assertEquals("metadata-draft", shot.saveRequest.metadata.customTags["manualDraftState"])
        assertEquals("on", shot.saveRequest.metadata.customTags["manualDraftRaw"])
        assertEquals("320", shot.saveRequest.metadata.customTags["manualDraftIso"])
        assertEquals("33", shot.saveRequest.metadata.customTags["manualDraftShutterSpeedMillis"])
        assertEquals("4800", shot.saveRequest.metadata.customTags["manualDraftWhiteBalanceKelvin"])
        assertEquals(true, shot.captureProfile.manualCaptureParams?.rawEnabled)
        assertEquals(320, shot.captureProfile.manualCaptureParams?.iso)
        assertEquals(33L, shot.captureProfile.manualCaptureParams?.shutterSpeedMillis)
        assertEquals(1, shot.captureProfile.manualCaptureParams?.exposureCompensationSteps)
        assertEquals(2.5f, shot.captureProfile.manualCaptureParams?.focusDistanceDiopters)
        assertEquals(1.8f, shot.captureProfile.manualCaptureParams?.apertureFNumber)
        assertEquals(4800, shot.captureProfile.manualCaptureParams?.whiteBalanceKelvin)
        assertEquals("auto", shot.saveRequest.metadata.customTags["flash"])
        assertEquals("Pictures/OpenCamera/Pro", shot.saveRequest.relativePath)
        assertEquals(FlashMode.AUTO, shot.captureProfile.flashMode)
        assertEquals("pro-manual-street", shot.postProcessSpec.algorithmProfile)
        assertEquals("1/250s", shot.postProcessSpec.exifOverrides["ExposureTime"])
        assertEquals("Photo capture requested", session.state.value.lastAction)
        assertEquals("Pro capture requested", session.state.value.modeSnapshot.state.headline)
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Street"))
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "Draft RAW On | ISO 320 | S 33ms | WB 4800K"
            )
        )
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Flash Auto"))
    }

    @Test
    fun `pro mode keeps preset flow while applying tertiary frame ratio`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.PRO))
        session.dispatch(SessionIntent.TertiaryActionPressed)
        session.dispatch(SessionIntent.SecondaryActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("street", shot.saveRequest.metadata.customTags["preset"])
        assertEquals("16:9", shot.saveRequest.metadata.customTags["frameRatio"])
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Frame 16:9"))
        assertEquals("Pro capture requested", session.state.value.modeSnapshot.state.headline)
    }

    @Test
    fun `pro mode falls back to flash off when flash control is unsupported`() = runTest {
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
        session.dispatch(SessionIntent.SwitchMode(ModeId.PRO))
        session.dispatch(SessionIntent.SecondaryActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ModeId.PRO, session.state.value.activeMode)
        assertEquals("street", shot.saveRequest.metadata.customTags["preset"])
        assertEquals("metadata-draft", shot.saveRequest.metadata.customTags["manualDraftState"])
        assertEquals("off", shot.saveRequest.metadata.customTags["flash"])
        assertEquals(FlashMode.OFF, shot.captureProfile.flashMode)
        assertEquals("pro-manual-street", shot.postProcessSpec.algorithmProfile)
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "Flash unavailable on this device"
            )
        )
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
    fun `portrait mode cycles styles and annotates portrait depth capture`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.PORTRAIT))
        session.dispatch(SessionIntent.SecondaryActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ModeId.PORTRAIT, session.state.value.activeMode)
        assertEquals("portrait-chasing-light", shot.saveRequest.metadata.customTags["style"])
        assertEquals("portrait-chasing-light", shot.saveRequest.metadata.customTags["filterProfile"])
        assertEquals("depth", shot.saveRequest.metadata.customTags["renderPath"])
        assertEquals("Pictures/OpenCamera/Portrait", shot.saveRequest.relativePath)
        assertEquals("OpenCamera_PORTRAIT", shot.saveRequest.fileNamePrefix)
        assertEquals("portrait-chasing-light", shot.postProcessSpec.algorithmProfile)
        assertEquals("Portrait", shot.postProcessSpec.exifOverrides["SceneCaptureType"])
        assertEquals("simulated-bokeh", shot.postProcessSpec.exifOverrides["DepthEffect"])
        assertEquals("Photo capture requested", session.state.value.lastAction)
        assertEquals("Portrait capture requested", session.state.value.modeSnapshot.state.headline)
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Portrait Chasing Light"))
    }

    @Test
    fun `portrait mode uses live photo shot kind when live default is enabled`() = runTest {
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
        session.dispatch(SessionIntent.SwitchMode(ModeId.PORTRAIT))
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
    fun `portrait mode uses shared photo countdown before creating shot plan`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
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
        session.dispatch(SessionIntent.SwitchMode(ModeId.PORTRAIT))
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()

        assertEquals(ModeId.PORTRAIT, session.state.value.activeMode)
        assertEquals(3, session.state.value.countdownRemainingSeconds)
        assertEquals(null, session.state.value.activeShot)
        assertEquals("Portrait countdown armed", session.state.value.modeSnapshot.state.headline)
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Timer 3s"))

        advanceTimeBy(3_000)
        runCurrent()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("portrait-original", shot.saveRequest.metadata.customTags["style"])
    }

    @Test
    fun `portrait mode carries profile beauty and bokeh metadata into shot plan`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    photo = PhotoSettings(
                        defaultPortraitFilterProfileId = "portrait-rich",
                        portraitProfile = PortraitProfile.LUMINOUS,
                        portraitBeautyPreset = PortraitBeautyPreset.RADIANT,
                        portraitBeautyStrength = PortraitBeautyStrength.ELEVATED,
                        portraitBokehEffect = PortraitBokehEffect.DREAMY
                    )
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.PORTRAIT))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("portrait-rich", shot.saveRequest.metadata.customTags["style"])
        assertEquals("luminous", shot.saveRequest.metadata.customTags["portraitProfile"])
        assertEquals("radiant", shot.saveRequest.metadata.customTags["portraitBeautyPreset"])
        assertEquals("elevated", shot.saveRequest.metadata.customTags["portraitBeautyStrength"])
        assertEquals("dreamy", shot.saveRequest.metadata.customTags["portraitBokehEffect"])
        assertEquals("Luminous Portrait", shot.postProcessSpec.exifOverrides["PortraitProfile"])
        assertEquals("Radiant", shot.postProcessSpec.exifOverrides["PortraitBeautyPreset"])
        assertEquals("Elevated", shot.postProcessSpec.exifOverrides["PortraitBeautyStrength"])
        assertEquals("Dreamy", shot.postProcessSpec.exifOverrides["PortraitBokehEffect"])
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Profile Luminous Portrait"))
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Bokeh Dreamy"))
    }

    @Test
    fun `portrait mode keeps render metadata while applying tertiary frame ratio`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.PORTRAIT))
        session.dispatch(SessionIntent.TertiaryActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("portrait-original", shot.saveRequest.metadata.customTags["style"])
        assertEquals("depth", shot.saveRequest.metadata.customTags["renderPath"])
        assertEquals("16:9", shot.saveRequest.metadata.customTags["frameRatio"])
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Frame 16:9"))
    }

    @Test
    fun `portrait mode pro variant carries manual draft into portrait capture`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            settingsSnapshot = SessionSettingsSnapshot(
                catalog = FeatureCatalog(
                    manualCaptureDraft = ManualCaptureParams(
                        rawEnabled = true,
                        iso = 200,
                        shutterSpeedMillis = 20L,
                        whiteBalanceKelvin = 4300
                    )
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.PORTRAIT))
        advanceUntilIdle()

        assertEquals("Enter Pro", session.state.value.modeSnapshot.uiSpec.proActionLabel)
        assertEquals(true, session.state.value.modeSnapshot.state.isProActionEnabled)

        session.dispatch(SessionIntent.ProActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("pro", shot.saveRequest.metadata.customTags["modeVariant"])
        assertEquals("manual", shot.saveRequest.metadata.customTags["controlMode"])
        assertEquals("metadata-draft", shot.saveRequest.metadata.customTags["manualDraftState"])
        assertEquals("on", shot.saveRequest.metadata.customTags["manualDraftRaw"])
        assertEquals(200, shot.captureProfile.manualCaptureParams?.iso)
        assertEquals(20L, shot.captureProfile.manualCaptureParams?.shutterSpeedMillis)
        assertEquals(4300, shot.captureProfile.manualCaptureParams?.whiteBalanceKelvin)
        assertEquals("portrait-original-pro", shot.postProcessSpec.algorithmProfile)
        assertEquals("Pro", shot.postProcessSpec.exifOverrides["PortraitVariant"])
        assertEquals("Exit Pro", session.state.value.modeSnapshot.uiSpec.proActionLabel)
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "Pro draft RAW On | ISO 200 | S 20ms | WB 4300K"
            )
        )
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
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Default style Humanistic Vivid"))
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "Subfeatures style, frame ratio, Live default, timer, and watermark"
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

        assertEquals("Enter Pro Assist", session.state.value.modeSnapshot.uiSpec.proActionLabel)

        session.dispatch(SessionIntent.ProActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("pro", shot.saveRequest.metadata.customTags["modeVariant"])
        assertEquals("assisted", shot.saveRequest.metadata.customTags["controlMode"])
        assertEquals("unsupported", shot.saveRequest.metadata.customTags["manualDraftState"])
        assertEquals(true, shot.captureProfile.manualCaptureParams?.rawEnabled)
        assertEquals("photo-original-pro-assist", shot.postProcessSpec.algorithmProfile)
        assertEquals("Pro Assist", shot.postProcessSpec.exifOverrides["HumanisticVariant"])
        assertEquals("Exit Pro Assist", session.state.value.modeSnapshot.uiSpec.proActionLabel)
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "saved-only draft because manual controls are unavailable"
            )
        )
    }

    @Test
    fun `night mode cycles profiles and emits multi frame shot plan`() = runTest {
        val session = createSession(
            InMemorySessionTrace(),
            this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = true)
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.NIGHT))
        session.dispatch(SessionIntent.SecondaryActionPressed)
        session.dispatch(SessionIntent.SecondaryActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ModeId.NIGHT, session.state.value.activeMode)
        assertEquals(ShotKind.MULTI_FRAME_CAPTURE, shot.shotKind)
        assertEquals("tripod", shot.saveRequest.metadata.customTags["profile"])
        assertEquals("multi-frame", shot.saveRequest.metadata.customTags["capturePath"])
        assertEquals("off", shot.saveRequest.metadata.customTags["flash"])
        assertEquals("Pictures/OpenCamera/Night", shot.saveRequest.relativePath)
        assertEquals(12, shot.captureProfile.frameCount)
        assertEquals(1400L, shot.captureProfile.longExposureMillis)
        assertTrue(shot.captureProfile.requiresTripod)
        assertEquals(FlashMode.OFF, shot.captureProfile.flashMode)
        assertEquals("night-multiframe-tripod", shot.postProcessSpec.algorithmProfile)
        assertEquals("Night", shot.postProcessSpec.exifOverrides["SceneCaptureType"])
        assertEquals("Photo capture requested", session.state.value.lastAction)
        assertEquals("Scenery capture requested", session.state.value.modeSnapshot.state.headline)
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Tripod"))
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Flash Off"))
    }

    @Test
    fun `night mode uses shared photo countdown before multi frame capture`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = true),
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
        session.dispatch(SessionIntent.SwitchMode(ModeId.NIGHT))
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()

        assertEquals(ModeId.NIGHT, session.state.value.activeMode)
        assertEquals(3, session.state.value.countdownRemainingSeconds)
        assertEquals(null, session.state.value.activeShot)
        assertEquals("Scenery countdown armed", session.state.value.modeSnapshot.state.headline)
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Timer 3s"))

        advanceTimeBy(3_000)
        runCurrent()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ShotKind.MULTI_FRAME_CAPTURE, shot.shotKind)
        assertEquals("handheld", shot.saveRequest.metadata.customTags["profile"])
    }

    @Test
    fun `night mode keeps multi frame plan while applying tertiary frame ratio`() = runTest {
        val session = createSession(
            InMemorySessionTrace(),
            this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = true)
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.NIGHT))
        session.dispatch(SessionIntent.TertiaryActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ShotKind.MULTI_FRAME_CAPTURE, shot.shotKind)
        assertEquals("16:9", shot.saveRequest.metadata.customTags["frameRatio"])
        assertEquals("handheld", shot.saveRequest.metadata.customTags["profile"])
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Frame 16:9"))
    }

    @Test
    fun `night mode pro variant carries manual draft into multi frame capture`() = runTest {
        val session = createSession(
            trace = InMemorySessionTrace(),
            testScope = this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = true),
            settingsSnapshot = SessionSettingsSnapshot(
                catalog = FeatureCatalog(
                    manualCaptureDraft = ManualCaptureParams(
                        rawEnabled = true,
                        iso = 250,
                        shutterSpeedMillis = 80L,
                        whiteBalanceKelvin = 3900
                    )
                )
            )
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.NIGHT))
        advanceUntilIdle()

        assertEquals("Enter Pro", session.state.value.modeSnapshot.uiSpec.proActionLabel)

        session.dispatch(SessionIntent.ProActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ShotKind.MULTI_FRAME_CAPTURE, shot.shotKind)
        assertEquals("pro", shot.saveRequest.metadata.customTags["modeVariant"])
        assertEquals("manual", shot.saveRequest.metadata.customTags["controlMode"])
        assertEquals("metadata-draft", shot.saveRequest.metadata.customTags["manualDraftState"])
        assertEquals(250, shot.captureProfile.manualCaptureParams?.iso)
        assertEquals(80L, shot.captureProfile.manualCaptureParams?.shutterSpeedMillis)
        assertEquals("night-multiframe-handheld-pro", shot.postProcessSpec.algorithmProfile)
        assertEquals("Pro", shot.postProcessSpec.exifOverrides["NightVariant"])
        assertEquals("Exit Pro", session.state.value.modeSnapshot.uiSpec.proActionLabel)
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "Pro draft RAW On | ISO 250 | S 80ms | WB 3900K"
            )
        )
    }

    @Test
    fun `pro mode degrades to assisted presets when manual controls are unavailable`() = runTest {
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
                supportsManualControls = false
            ),
            scope = TestScope(StandardTestDispatcher(testScheduler))
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.PRO))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ModeId.PRO, session.state.value.activeMode)
        assertEquals("assisted", shot.saveRequest.metadata.customTags["controlMode"])
        assertEquals("unsupported", shot.saveRequest.metadata.customTags["manualDraftState"])
        assertEquals("off", shot.saveRequest.metadata.customTags["manualDraftRaw"])
        assertEquals(emptyMap(), shot.postProcessSpec.exifOverrides)
        assertEquals("pro-assisted-balanced", shot.postProcessSpec.algorithmProfile)
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "manual controls are unavailable"
            )
        )
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "Draft RAW Off | ISO Auto | S Auto | WB Auto saved only."
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
    fun `portrait mode degrades to focus priority styles when depth effect is unavailable`() = runTest {
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
                supportsDocumentScanEnhancement = true,
                supportsPortraitDepthEffect = false
            ),
            scope = TestScope(StandardTestDispatcher(testScheduler))
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.PORTRAIT))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ModeId.PORTRAIT, session.state.value.activeMode)
        assertEquals("focus", shot.saveRequest.metadata.customTags["renderPath"])
        assertEquals("portrait-original", shot.postProcessSpec.algorithmProfile)
        assertEquals("focus-priority", shot.postProcessSpec.exifOverrides["DepthEffect"])
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "depth effect is unavailable"
            )
        )
    }

    @Test
    fun `night mode degrades to single frame brightening when multi frame is unavailable`() = runTest {
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
                supportsDocumentScanEnhancement = true,
                supportsPortraitDepthEffect = true,
                supportsNightMultiFrame = false
            ),
            scope = TestScope(StandardTestDispatcher(testScheduler))
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.NIGHT))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ModeId.NIGHT, session.state.value.activeMode)
        assertEquals(ShotKind.STILL_CAPTURE, shot.shotKind)
        assertEquals("single-frame-fallback", shot.saveRequest.metadata.customTags["capturePath"])
        assertEquals("auto", shot.saveRequest.metadata.customTags["flash"])
        assertEquals(1, shot.captureProfile.frameCount)
        assertEquals(null, shot.captureProfile.longExposureMillis)
        assertEquals(FlashMode.AUTO, shot.captureProfile.flashMode)
        assertEquals("night-fallback-balanced", shot.postProcessSpec.algorithmProfile)
        assertEquals("bright-single-frame", shot.postProcessSpec.exifOverrides["MergeStrategy"])
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "night multi-frame is unavailable"
            )
        )
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Flash Auto"))
    }

    @Test
    fun `night fallback flash degrades to off when flash control is unsupported`() = runTest {
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
                supportsDocumentScanEnhancement = true,
                supportsPortraitDepthEffect = true,
                supportsNightMultiFrame = false,
                supportsFlashControl = false
            ),
            scope = TestScope(StandardTestDispatcher(testScheduler))
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.NIGHT))
        session.dispatch(SessionIntent.SecondaryActionPressed)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(ModeId.NIGHT, session.state.value.activeMode)
        assertEquals("warm", shot.saveRequest.metadata.customTags["profile"])
        assertEquals("off", shot.saveRequest.metadata.customTags["flash"])
        assertEquals(FlashMode.OFF, shot.captureProfile.flashMode)
        assertEquals("night-fallback-warm", shot.postProcessSpec.algorithmProfile)
        assertTrue(
            session.state.value.modeSnapshot.state.detail.contains(
                "Flash unavailable on this device"
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

        session.dispatch(SessionIntent.LensFacingToggled)
        advanceUntilIdle()

        assertEquals(LensFacing.FRONT, session.state.value.activeDeviceGraph.preferredLensFacing)
        assertEquals("Switched to front lens", session.state.value.lastAction)

        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()

        assertEquals(ModeId.VIDEO, session.state.value.activeMode)
        assertEquals(LensFacing.FRONT, session.state.value.activeDeviceGraph.preferredLensFacing)
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
            StillCaptureQualityPreference.LATENCY,
            session.state.value.activeDeviceGraph.stillCapture.qualityPreference
        )

        session.dispatch(SessionIntent.StillCaptureQualityToggled)
        advanceUntilIdle()

        assertEquals(
            StillCaptureQualityPreference.QUALITY,
            session.state.value.activeDeviceGraph.stillCapture.qualityPreference
        )
        assertEquals("Still quality set to Max", session.state.value.lastAction)
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Still Max"))

        session.dispatch(SessionIntent.SwitchMode(ModeId.PRO))
        advanceUntilIdle()

        assertEquals(ModeId.PRO, session.state.value.activeMode)
        assertEquals(
            StillCaptureQualityPreference.QUALITY,
            session.state.value.activeDeviceGraph.stillCapture.qualityPreference
        )
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Still Max"))

        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals(StillCaptureQualityPreference.QUALITY, shot.captureProfile.stillCaptureQuality)
        assertEquals("quality", shot.saveRequest.metadata.customTags["stillQuality"])
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
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Size 8MP"))

        session.dispatch(SessionIntent.SwitchMode(ModeId.PORTRAIT))
        advanceUntilIdle()

        assertEquals(ModeId.PORTRAIT, session.state.value.activeMode)
        assertEquals(
            StillCaptureResolutionPreset.MEDIUM_8MP,
            session.state.value.activeDeviceGraph.stillCapture.resolutionPreset
        )
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Size 8MP"))

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
        assertTrue(session.state.value.modeSnapshot.state.detail.contains("Size 2MP"))
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
    fun `night multi frame permission loss clears stale diagnostics and records capture failure trace`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(
            trace,
            this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = true)
        )

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val seedShot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(seedShot))
        session.dispatch(
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = seedShot.shotId,
                    mediaType = MediaType.PHOTO,
                    outputPath = "Pictures/OpenCamera/seed.jpg",
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailSource = ThumbnailSource.SavedMedia("Pictures/OpenCamera/seed.jpg"),
                    metadata = SaveRequest.photoLibrary().metadata,
                    pipelineNotes = listOf("algorithm:seed-photo", "merge:placeholder")
                )
            )
        )
        advanceUntilIdle()

        session.dispatch(SessionIntent.SwitchMode(ModeId.NIGHT))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()

        val nightShot = assertNotNull(session.state.value.activeShot)
        assertEquals(ShotKind.MULTI_FRAME_CAPTURE, nightShot.shotKind)
        session.dispatch(SessionIntent.ShotStarted(nightShot))
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = false, microphoneGranted = true))
        advanceUntilIdle()

        assertEquals(ModeId.NIGHT, session.state.value.activeMode)
        assertEquals(PreviewStatus.BLOCKED, session.state.value.previewStatus)
        assertEquals(CaptureStatus.IDLE, session.state.value.captureStatus)
        assertEquals("Scenery capture failed", session.state.value.modeSnapshot.state.headline)
        assertEquals("Camera permission missing", session.state.value.modeSnapshot.state.detail)
        assertEquals(emptyList(), session.state.value.latestPipelineNotes)
        assertEquals(null, session.state.value.activeShot)
        assertTrue(
            trace.snapshot().any { event ->
                event.name == "capture.failed" &&
                    event.detail == "${nightShot.shotId}:Camera permission missing"
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
        advanceUntilIdle()

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
        advanceUntilIdle()

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
        advanceUntilIdle()

        session.dispatch(
            SessionIntent.PreviewMeteringCompleted(
                com.opencamera.core.device.PreviewMeteringResult(
                    requestId = "meter-1",
                    point = com.opencamera.core.device.PreviewMeteringPoint(0.5f, 0.4f),
                    status = com.opencamera.core.device.PreviewMeteringResultStatus.SUCCEEDED
                )
            )
        )
        advanceUntilIdle()

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
        advanceUntilIdle()
        session.dispatch(SessionIntent.PreviewTapToFocus(0.6f, 0.6f))
        advanceUntilIdle()

        session.dispatch(
            SessionIntent.PreviewMeteringCompleted(
                com.opencamera.core.device.PreviewMeteringResult(
                    requestId = "meter-1",
                    point = com.opencamera.core.device.PreviewMeteringPoint(0.3f, 0.3f),
                    status = com.opencamera.core.device.PreviewMeteringResultStatus.SUCCEEDED
                )
            )
        )
        advanceUntilIdle()

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
        advanceUntilIdle()

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
        advanceUntilIdle()

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

    private fun createSession(
        trace: InMemorySessionTrace,
        testScope: TestScope,
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        settingsSnapshot: SessionSettingsSnapshot = SessionSettingsSnapshot(),
        capabilityGraphResolver: com.opencamera.core.capability.CapabilityGraphResolver? = null,
        capabilityRequirements: () -> List<com.opencamera.core.capability.CapabilityRequirement> = { emptyList() }
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
            capabilityRequirements = capabilityRequirements
        )
    }

    private fun testModePlugins() = listOf(
        PhotoModePlugin(),
        DocumentModePlugin(),
        HumanisticModePlugin(),
        NightModePlugin(),
        PortraitModePlugin(),
        ProModePlugin(),
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
}
