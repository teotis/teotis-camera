package com.opencamera.app.reliability

import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.opencamera.app.camera.CameraSessionCoordinator
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.media.ShotExecutor
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeRegistry
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.DefaultCameraSession
import com.opencamera.core.session.InMemorySessionTrace
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SessionIntent
import com.opencamera.core.session.SessionState
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.feature.checkin.CheckInModePlugin
import com.opencamera.feature.document.DocumentModePlugin
import com.opencamera.feature.humanistic.HumanisticModePlugin
import com.opencamera.feature.photo.PhotoModePlugin
import com.opencamera.feature.video.VideoModePlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.robolectric.RuntimeEnvironment

/**
 * Runs a deterministic reliability scenario through the real
 * [DefaultCameraSession] + [CameraSessionCoordinator] path with a
 * [ProgrammableCameraDeviceAdapter] on a virtual clock.
 *
 * Every run is fully replayable from its [seed]: the step list and device
 * scripts are fixed inputs, and the virtual clock makes time deterministic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReliabilityScenarioRunner(
    private val seed: Long
) {
    class ScenarioResult(
        val seed: Long,
        val steps: List<ScenarioStep>,
        val violations: List<ReliabilityInvariants.Violation>,
        val finalState: SessionState,
        val emittedEvents: List<com.opencamera.core.device.DeviceEvent>,
        val traceEvents: List<com.opencamera.core.session.SessionTraceEvent>,
        val releasedCount: Int
    ) {
        val passed: Boolean get() = violations.isEmpty()

        fun summary(): String = buildString {
            append("seed=$seed steps=${steps.size} violations=${violations.size}")
            if (violations.isNotEmpty()) {
                append("\n")
                append(violations.joinToString("\n") { it.toString() })
                append("\ntrace-tail:\n")
                append(
                    traceEvents.takeLast(30).joinToString("\n") {
                        "${it.sequence} ${it.name} ${it.detail}"
                    }
                )
            }
        }
    }

    /**
     * @param steps scripted scenario steps
     * @param deviceScripts per-shot device scripts consumed by the adapter
     * @param deviceCapabilities capabilities exposed to the session
     * @param endWithShutdown when true the scenario dispatches Shutdown before settling
     */
    suspend fun run(
        testScope: TestScope,
        steps: List<ScenarioStep>,
        deviceScripts: List<ShotScript> = emptyList(),
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        endWithShutdown: Boolean = false
    ): ScenarioResult {
        val trace = InMemorySessionTrace()
        var shotIndex = 0
        val sessionScope = TestScope(StandardTestDispatcher(testScope.testScheduler))
        val adapterScope = TestScope(StandardTestDispatcher(testScope.testScheduler))
        val coordinatorScope = TestScope(StandardTestDispatcher(testScope.testScheduler))
        val session = DefaultCameraSession(
            registry = ModeRegistry(
                listOf(
                    PhotoModePlugin(),
                    DocumentModePlugin(),
                    HumanisticModePlugin(),
                    CheckInModePlugin(),
                    VideoModePlugin()
                )
            ),
            trace = trace,
            baseDeviceCapabilities = deviceCapabilities,
            scope = sessionScope,
            settingsSnapshot = SessionSettingsSnapshot(),
            shotExecutor = ShotExecutor(idGenerator = { "rel-${seed}-${++shotIndex}" }),
            recordingTimerDispatcher = null
        )
        val mediaStore = FakeMediaStoreModel()
        val adapter = ProgrammableCameraDeviceAdapter(
            scope = adapterScope,
            mediaStore = mediaStore,
            capabilities = deviceCapabilities
        )
        val coordinator = CameraSessionCoordinator(
            session = session,
            cameraAdapter = adapter,
            scope = coordinatorScope
        )
        deviceScripts.forEach { adapter.enqueueShotScript(it) }

        val stateHistory = mutableListOf<SessionState>()
        val stateJob = testScope.launch {
            session.state.collectLatest { stateHistory += it }
        }

        var hostAttached = false

        fun attachHost() {
            if (hostAttached) return
            coordinator.attachPreviewHost(
                lifecycleOwner = StartedLifecycleOwner(),
                previewView = PreviewView(RuntimeEnvironment.getApplication())
            )
            hostAttached = true
        }

        fun reattachHost() {
            // Detach with clearHost=true removes the coordinator's host reference;
            // re-attaching must always call back into the coordinator so a pending
            // preview bind can be flushed.
            coordinator.attachPreviewHost(
                lifecycleOwner = StartedLifecycleOwner(),
                previewView = PreviewView(RuntimeEnvironment.getApplication())
            )
            hostAttached = true
        }

        fun tick() = testScope.runCurrent()

        // Prime the coordinator collectors before dispatching anything: the
        // session effects SharedFlow has no replay, so an effect emitted before
        // the collector starts would be dropped and the preview would never bind.
        tick()

        // Bootstrap: permission + host + boot (mirrors MainActivity wiring order).
        session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
        session.dispatch(SessionIntent.PreviewHostAttached)
        session.dispatch(SessionIntent.Boot)
        attachHost()
        tick()

        steps.forEach { step ->
            when (step) {
                ScenarioStep.Boot -> {
                    session.dispatch(SessionIntent.Boot)
                    tick()
                }

                ScenarioStep.Shutdown -> {
                    session.dispatch(SessionIntent.Shutdown)
                    tick()
                }

                ScenarioStep.AttachHost -> {
                    session.dispatch(SessionIntent.PreviewHostAttached)
                    reattachHost()
                    tick()
                }

                is ScenarioStep.DetachHost -> {
                    session.dispatch(SessionIntent.PreviewHostDetached(step.reason))
                    tick()
                }

                is ScenarioStep.Permissions -> {
                    session.dispatch(
                        SessionIntent.PermissionsUpdated(
                            cameraGranted = step.camera,
                            microphoneGranted = step.microphone
                        )
                    )
                    tick()
                }

                ScenarioStep.ShutterPhoto -> {
                    session.dispatch(SessionIntent.ShutterPressed)
                    tick()
                }

                ScenarioStep.ShutterVideo -> {
                    if (session.state.value.activeMode != ModeId.VIDEO) {
                        session.dispatch(SessionIntent.SwitchMode(ModeId.VIDEO))
                        tick()
                    }
                    session.dispatch(SessionIntent.ShutterPressed)
                    tick()
                }

                ScenarioStep.StopActiveShot -> {
                    // Stopping with nothing recording must be a no-op, not a new
                    // recording submission (toggleRecording semantics).
                    val recording = session.state.value.recordingStatus
                    if (recording == RecordingStatus.RECORDING ||
                        recording == RecordingStatus.REQUESTING
                    ) {
                        session.dispatch(SessionIntent.ShutterPressed)
                    } else {
                        trace.record("scenario.stop.noop", "recording=$recording")
                    }
                    tick()
                }

                is ScenarioStep.CountdownShutter -> {
                    session.dispatch(SessionIntent.ShutterPressed)
                    tick()
                    testScope.advanceTimeBy(step.seconds * 1_000L)
                    tick()
                }

                is ScenarioStep.SwitchMode -> {
                    session.dispatch(SessionIntent.SwitchMode(step.modeId))
                    tick()
                }

                ScenarioStep.ToggleLens -> {
                    session.dispatch(SessionIntent.LensFacingToggled)
                    tick()
                }

                ScenarioStep.ToggleZoom -> {
                    session.dispatch(SessionIntent.ZoomRatioToggled)
                    tick()
                }

                is ScenarioStep.DeviceShotScript -> {
                    adapter.enqueueShotScript(step.script)
                    tick()
                }

                is ScenarioStep.DeviceRuntimeIssue -> {
                    adapter.emitRuntimeIssue(
                        kind = step.kind,
                        recoverable = step.recoverable
                    )
                    tick()
                }

                ScenarioStep.DeviceSurfaceLost -> {
                    adapter.emitSurfaceLost()
                    tick()
                }

                ScenarioStep.DevicePreviewError -> {
                    adapter.emitPreviewError()
                    tick()
                }

                is ScenarioStep.Wait -> {
                    testScope.advanceTimeBy(step.millis)
                    tick()
                }

                ScenarioStep.WaitWatchdogs -> {
                    testScope.advanceTimeBy(16_500L)
                    tick()
                }
            }
        }

        if (endWithShutdown) {
            session.dispatch(SessionIntent.Shutdown)
            tick()
        }

        // Settle: let every watchdog window pass and late events be processed.
        testScope.advanceTimeBy(16_500L)
        tick()

        val settledState = stateHistory.lastOrNull() ?: session.state.value
        val converged = settledState.recordingStatus == RecordingStatus.IDLE &&
            settledState.captureStatus != CaptureStatus.SAVING &&
            settledState.activeShot == null

        if (!converged) {
            // A recording that never reaches a terminal (e.g. a late Started after
            // the REQUESTING timeout) would make runTest's final advanceUntilIdle
            // spin forever on the recording elapsed loop. Record the invariant
            // violation, then force shutdown so the test terminates cleanly.
            System.err.println("CONVERGE-DEFENSE seed=$seed rec=${settledState.recordingStatus} cap=${settledState.captureStatus} shot=${settledState.activeShot?.shotId}")
            session.dispatch(SessionIntent.Shutdown)
            tick()
        }

        stateJob.cancel()

        val finalState = stateHistory.lastOrNull() ?: session.state.value
        val violations = buildList {
            addAll(
                ReliabilityInvariants(
                    states = stateHistory,
                    deviceEvents = adapter.emittedEvents,
                    mediaStore = mediaStore,
                    trace = trace,
                    finalState = finalState
                ).checkAll()
            )
            if (!converged) {
                add(
                    ReliabilityInvariants.Violation(
                        "INV-4a",
                        "scenario did not converge at settle " +
                            "(recording=${settledState.recordingStatus}," +
                            " capture=${settledState.captureStatus}," +
                            " activeShot=${settledState.activeShot?.shotId}); forced shutdown"
                    )
                )
            }
        }

        // Tear down the internal scopes (session intent loop, coordinator
        // collectors, adapter timers, elapsed loop) before runTest's final
        // advanceUntilIdle: leftover periodic tasks would otherwise keep the
        // scheduler busy forever and hang the test.
        sessionScope.cancel()
        adapterScope.cancel()
        coordinatorScope.cancel()
        testScope.runCurrent()

        return ScenarioResult(
            seed = seed,
            steps = steps,
            violations = violations,
            finalState = finalState,
            emittedEvents = adapter.emittedEvents,
            traceEvents = trace.snapshot(),
            releasedCount = adapter.releaseCount
        )
    }

    private class StartedLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle
            get() = registry
    }
}
