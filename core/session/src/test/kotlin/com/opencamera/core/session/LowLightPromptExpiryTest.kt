package com.opencamera.core.session

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.PhotoSceneSignal
import com.opencamera.core.device.SceneLightState
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeIntent
import com.opencamera.core.mode.ModeRegistry
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.settings.SessionSettingsSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LowLightPromptExpiryTest {

    private fun createSession(
        trace: SessionTrace = InMemorySessionTrace(),
        scope: TestScope
    ): DefaultCameraSession {
        return DefaultCameraSession(
            registry = ModeRegistry(listOf(TestPhotoModePlugin())),
            trace = trace,
            baseDeviceCapabilities = DeviceCapabilities.DEFAULT,
            scope = TestScope(StandardTestDispatcher(scope.testScheduler)),
            settingsSnapshot = SessionSettingsSnapshot()
        )
    }

    @Test
    fun `photo low-light prompt is hidden after expiry`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        session.dispatch(SessionIntent.PreviewBindingStarted(reason = "test", isRecovery = false))
        session.dispatch(SessionIntent.PreviewFirstFrameAvailable(100))
        advanceUntilIdle()

        val lowLightSignal = PhotoSceneSignal(
            lightState = SceneLightState.LOW_LIGHT,
            brightnessScore = 0.1f,
            source = "test"
        )
        session.dispatch(SessionIntent.PhotoSceneSignalUpdated(lowLightSignal))
        // Use advanceTimeBy with a small value to process the dispatch
        // but not reach the 3000ms expiry delay
        advanceTimeBy(100)

        val promptBefore = session.state.value.presentation.photoLowLightPrompt
        assertNotNull(promptBefore)
        assertNotNull(promptBefore.visibleUntilElapsedMillis)

        // Now advance past the 3000ms to trigger expiry
        advanceTimeBy(3000)
        runCurrent()

        val promptAfter = session.state.value.presentation.photoLowLightPrompt
        assertNotNull(promptAfter)
        assertEquals(PhotoLowLightPromptStatus.HIDDEN, promptAfter.status)
        assertNull(promptAfter.visibleUntilElapsedMillis)
    }

    @Test
    fun `photo low-light prompt expiry is idempotent on already hidden prompt`() = runTest {
        val trace = InMemorySessionTrace()
        val session = createSession(trace, this)

        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        advanceUntilIdle()

        session.dispatch(SessionIntent.PhotoLowLightPromptExpired)
        advanceUntilIdle()

        val prompt = session.state.value.presentation.photoLowLightPrompt
        assertNull(prompt)
    }
}

private class TestPhotoModePlugin : com.opencamera.core.mode.CameraModePlugin {
    override val id: ModeId = ModeId.PHOTO

    override fun isSupported(deviceCapabilities: DeviceCapabilities): Boolean = true

    override fun create(context: ModeContext): ModeController = TestPhotoModeController()
}

private class TestPhotoModeController : ModeController {
    override val id: ModeId = ModeId.PHOTO
    override val snapshot: StateFlow<ModeSnapshot> = MutableStateFlow(
        ModeSnapshot(
            id = ModeId.PHOTO,
            uiSpec = com.opencamera.core.mode.ModeUiSpec(title = "Photo", shutterLabel = "Shutter"),
            state = com.opencamera.core.mode.ModeState(headline = "Photo mode", detail = "")
        )
    ).asStateFlow()

    override fun deviceGraph(): com.opencamera.core.device.DeviceGraphSpec = com.opencamera.core.device.DeviceGraphSpec.stillCapture()

    override suspend fun onEnter() {}

    override suspend fun onExit() {}

    override suspend fun handle(intent: com.opencamera.core.mode.ModeIntent): com.opencamera.core.mode.ModeSignal {
        return com.opencamera.core.mode.ModeSignal.None
    }
}
