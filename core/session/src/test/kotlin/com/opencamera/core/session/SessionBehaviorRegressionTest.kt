package com.opencamera.core.session

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.media.MediaType
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeRegistry
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.SessionSettingsSnapshot
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Cross-boundary behavior regression tests for post-refactor session lifecycle.
 *
 * Covers: idle reconfiguration, countdown blocking, active photo blocking,
 * active/pending video semantics, continuous vs discrete zoom exceptions,
 * settings/capability projection, and render model contract consistency.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionBehaviorRegressionTest {

    @Test
    fun `idle state allows mode switch`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        runCurrent()
        assertEquals(ModeId.PHOTO, session.state.value.activeMode)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        runCurrent()
        assertEquals(ModeId.VIDEO, session.state.value.activeMode)
        assertEquals("Switched to Video", session.state.value.lastAction)
    }

    @Test
    fun `idle state allows settings update`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        runCurrent()
        session.dispatch(SessionIntent.SettingsUpdated(
            SessionSettingsSnapshot(persisted = PersistedSettings(
                photo = PhotoSettings(defaultFilterProfileId = "portrait-retro")
            ))
        ))
        runCurrent()
        assertEquals("Session settings updated", session.state.value.lastAction)
        assertEquals("portrait-retro", session.state.value.settings.persisted.photo.defaultFilterProfileId)
    }

    @Test
    fun `idle state allows still quality toggle`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        runCurrent()
        session.dispatch(SessionIntent.StillCaptureQualityToggled)
        runCurrent()
        assertEquals("Still quality set to Fast", session.state.value.lastAction)
    }

    @Test
    fun `idle state allows still resolution toggle`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        runCurrent()
        session.dispatch(SessionIntent.StillCaptureResolutionToggled)
        runCurrent()
        assertEquals("Still resolution set to 8MP", session.state.value.lastAction)
    }

    @Test
    fun `idle state allows preview ratio toggle`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        runCurrent()
        session.dispatch(SessionIntent.PreviewRatioToggled)
        runCurrent()
        assertEquals("Preview ratio set to 4:3", session.state.value.lastAction)
    }

    @Test
    fun `countdown blocks mode switch`() = runTest {
        val session = createSession(InMemorySessionTrace(), this,
            settingsSnapshot = SessionSettingsSnapshot(persisted = PersistedSettings(
                photo = PhotoSettings(countdownDuration = CountdownDuration.SECONDS_3)
            ))
        )
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        assertEquals(3, session.state.value.countdownRemainingSeconds)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        runCurrent()
        assertEquals(ModeId.PHOTO, session.state.value.activeMode)
        assertEquals("Wait for countdown to finish before switching modes", session.state.value.lastAction)
    }

    @Test
    fun `countdown blocks settings update`() = runTest {
        val session = createSession(InMemorySessionTrace(), this,
            settingsSnapshot = SessionSettingsSnapshot(persisted = PersistedSettings(
                photo = PhotoSettings(countdownDuration = CountdownDuration.SECONDS_3)
            ))
        )
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        session.dispatch(SessionIntent.SettingsUpdated(
            SessionSettingsSnapshot(persisted = PersistedSettings(
                photo = PhotoSettings(defaultFilterProfileId = "blocked")
            ))
        ))
        runCurrent()
        assertEquals("Wait for countdown to finish before updating settings", session.state.value.lastAction)
    }

    @Test
    fun `countdown blocks still quality toggle`() = runTest {
        val session = createSession(InMemorySessionTrace(), this,
            settingsSnapshot = SessionSettingsSnapshot(persisted = PersistedSettings(
                photo = PhotoSettings(countdownDuration = CountdownDuration.SECONDS_3)
            ))
        )
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        session.dispatch(SessionIntent.StillCaptureQualityToggled)
        runCurrent()
        assertEquals("Wait for countdown to finish before changing still quality", session.state.value.lastAction)
    }

    @Test
    fun `active photo blocks mode switch`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        runCurrent()
        assertEquals(ModeId.PHOTO, session.state.value.activeMode)
        assertEquals("Wait for current capture to finish before switching modes", session.state.value.lastAction)
    }

    @Test
    fun `active photo blocks settings update`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.SettingsUpdated(
            SessionSettingsSnapshot(persisted = PersistedSettings(
                photo = PhotoSettings(defaultFilterProfileId = "blocked")
            ))
        ))
        runCurrent()
        assertEquals("Wait for current capture to finish before updating settings", session.state.value.lastAction)
    }

    @Test
    fun `active photo blocks lens switch`() = runTest {
        val session = createSession(InMemorySessionTrace(), this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT)
            )
        )
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.LensFacingToggled)
        runCurrent()
        assertEquals(LensFacing.BACK, session.state.value.activeDeviceGraph.preferredLensFacing)
        assertEquals("Wait for current capture to finish before switching lenses", session.state.value.lastAction)
    }

    @Test
    fun `active video recording blocks mode switch with video text`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()
        val shot = assertNotNull(session.state.value.activeShot)
        session.dispatch(SessionIntent.ShotStarted(shot))
        advanceUntilIdle()
        assertEquals(RecordingStatus.RECORDING, session.state.value.recordingStatus)
        session.dispatch(SessionIntent.SwitchMode(ModeId.PHOTO))
        runCurrent()
        assertEquals(ModeId.VIDEO, session.state.value.activeMode)
        assertEquals("Stop recording before switching modes", session.state.value.lastAction)
    }

    @Test
    fun `pending video blocks settings update`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        session.dispatch(SessionIntent.ShutterPressed)
        runCurrent()
        assertNotNull(session.state.value.activeShot)
        assertEquals(RecordingStatus.REQUESTING, session.state.value.recordingStatus)
        session.dispatch(SessionIntent.SettingsUpdated(
            SessionSettingsSnapshot(persisted = PersistedSettings(
                photo = PhotoSettings(defaultFilterProfileId = "blocked")
            ))
        ))
        runCurrent()
        assertEquals("Wait for current capture to finish before updating settings", session.state.value.lastAction)
    }

    @Test
    fun `active video recording blocks lens switch`() = runTest {
        val session = createSession(InMemorySessionTrace(), this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT)
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
        session.dispatch(SessionIntent.LensFacingToggled)
        runCurrent()
        assertEquals("Stop recording before switching lenses", session.state.value.lastAction)
    }

    @Test
    fun `continuous zoom allowed during recording`() = runTest {
        val session = createSession(InMemorySessionTrace(), this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 10f), defaultRatio = 1f
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
        session.dispatch(SessionIntent.ApplyZoomRatio(5f))
        advanceUntilIdle()
        assertEquals(5f, session.state.value.activeDeviceGraph.preview.zoomRatio)
        assertEquals("Zoom set to 5.0x", session.state.value.lastAction)
    }

    @Test
    fun `discrete zoom blocked during recording`() = runTest {
        val session = createSession(InMemorySessionTrace(), this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(1f, 2f, 5f), defaultRatio = 1f
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
        session.dispatch(SessionIntent.ApplyZoomRatio(5f))
        advanceUntilIdle()
        assertEquals(1f, session.state.value.activeDeviceGraph.preview.zoomRatio)
        assertEquals("Zoom preset stepping is blocked during recording", session.state.value.lastAction)
    }

    @Test
    fun `discrete zoom toggle blocked during recording`() = runTest {
        val session = createSession(InMemorySessionTrace(), this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(1f, 2f), defaultRatio = 1f
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
        session.dispatch(SessionIntent.ZoomRatioToggled)
        advanceUntilIdle()
        assertEquals(1f, session.state.value.activeDeviceGraph.preview.zoomRatio)
        assertEquals("Zoom preset stepping is blocked during recording", session.state.value.lastAction)
    }

    @Test
    fun `runtime configuration settings propagate to session state`() = runTest {
        val settings = SessionSettingsSnapshot(persisted = PersistedSettings(
            photo = PhotoSettings(defaultFilterProfileId = "portrait-retro", livePhotoEnabledByDefault = true)
        ))
        val session = createSession(InMemorySessionTrace(), this, settingsSnapshot = settings)
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()
        assertEquals(settings, session.state.value.settings)
        assertEquals("portrait-retro", session.state.value.settings.persisted.photo.defaultFilterProfileId)
        assertTrue(session.state.value.settings.persisted.photo.livePhotoEnabledByDefault)
    }

    @Test
    fun `settings update mid-session propagates to subsequent captures`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()
        session.dispatch(SessionIntent.SettingsUpdated(
            SessionSettingsSnapshot(persisted = PersistedSettings(
                photo = PhotoSettings(defaultFilterProfileId = "photo-rich")
            ))
        ))
        advanceUntilIdle()
        assertEquals("photo-rich", session.state.value.settings.persisted.photo.defaultFilterProfileId)
        session.dispatch(SessionIntent.ShutterPressed)
        advanceUntilIdle()
        val shot = assertNotNull(session.state.value.activeShot)
        assertEquals("photo-rich", shot.saveRequest.metadata.customTags["filterProfile"])
    }

    @Test
    fun `device capability update propagates to session state and mode graph`() = runTest {
        val session = createSession(InMemorySessionTrace(), this)
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
        advanceUntilIdle()
        assertTrue(session.state.value.activeDeviceGraph.recording.audioEnabledWhenPermitted)
        session.dispatch(SessionIntent.DeviceCapabilitiesUpdated(
            DeviceCapabilities.DEFAULT.copy(supportsAudioRecording = false)
        ))
        advanceUntilIdle()
        assertFalse(session.state.value.activeDeviceCapabilities.supportsAudioRecording)
        assertFalse(session.state.value.activeDeviceGraph.recording.audioEnabledWhenPermitted)
    }

    @Test
    fun `session state projection to render model consistent after reconfiguration`() = runTest {
        val session = createSession(InMemorySessionTrace(), this,
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT),
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(1f, 2f, 5f), defaultRatio = 1f
                )
            )
        )
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()
        assertEquals(ModeId.PHOTO, session.state.value.activeMode)
        assertEquals(LensFacing.BACK, session.state.value.activeDeviceGraph.preferredLensFacing)
        assertEquals(1f, session.state.value.activeDeviceGraph.preview.zoomRatio)
        session.dispatch(SessionIntent.LensFacingToggled)
        session.dispatch(SessionIntent.ZoomRatioToggled)
        advanceUntilIdle()
        assertEquals(LensFacing.FRONT, session.state.value.activeDeviceGraph.preferredLensFacing)
        assertEquals(2f, session.state.value.activeDeviceGraph.preview.zoomRatio)
        assertEquals(ModeId.PHOTO, session.state.value.activeMode)
    }

    private fun createSession(
        trace: InMemorySessionTrace,
        testScope: kotlinx.coroutines.test.TestScope,
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        settingsSnapshot: SessionSettingsSnapshot = SessionSettingsSnapshot()
    ): DefaultCameraSession {
        var shotIndex = 0
        return DefaultCameraSession(
            registry = ModeRegistry(testModePlugins()),
            trace = trace,
            baseDeviceCapabilities = deviceCapabilities,
            scope = kotlinx.coroutines.test.TestScope(kotlinx.coroutines.test.StandardTestDispatcher(testScope.testScheduler)),
            settingsSnapshot = settingsSnapshot,
            shotExecutor = com.opencamera.core.media.ShotExecutor(idGenerator = { "regression-${++shotIndex}" })
        )
    }

    private fun testModePlugins() = listOf(
        com.opencamera.feature.photo.PhotoModePlugin(),
        com.opencamera.feature.document.DocumentModePlugin(),
        com.opencamera.feature.humanistic.HumanisticModePlugin(),
        com.opencamera.feature.checkin.CheckInModePlugin(),
        com.opencamera.feature.video.VideoModePlugin()
    )
}
