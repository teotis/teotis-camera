# Session Split 04: Capture And Recording Processor Extraction

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this plan. Use `rtk` for every command. Execute after session split packages 01 and 02; package 03 should ideally land first.

## Goal

Move capture countdown, shot planning, shot lifecycle, recording stop/watchdog, interrupted-shot failure, and live photo bundle handling into a focused internal processor.

## Files

Create:

- `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessorTest.kt` if useful for isolated tests.

Modify:

- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`

## Behavior To Move

Move these current `DefaultCameraSession.kt` functions into the new processor:

- `startRecordingWatchdog`
- `handleCountdownTick`
- `handleCountdownCompleted`
- `handleShotStarted`
- `handleShotCompleted`
- `latestLivePhotoBundleFor`
- `handleShotFailed`
- `handleInterruptedShotFailure`
- `countdownInProgress`
- `cancelPendingCountdown`
- `startCaptureCountdown`
- `submitCaptureStrategy`
- `enrichPlanWithStillOutputSize`

Move these fields into the processor:

- `pendingCountdownJob`
- `pendingCountdownStrategy`
- `recordingWatchdogJob`

Keep `ShotExecutor` injected into the processor instead of letting `DefaultCameraSession` call it directly.

## Processor Shape

Create:

```kotlin
package com.opencamera.core.session

import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.LiveBundleStatus
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotExecutor
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.hasPostProcessFailures
import com.opencamera.core.media.isTemporalMedia
import com.opencamera.core.media.outputPathOrNull
import com.opencamera.core.media.postProcessFailureSummary
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeSessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class CaptureRecordingSessionProcessor(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SessionState>,
    private val effects: MutableSharedFlow<SessionEffect>,
    private val trace: SessionTrace,
    private val shotExecutor: ShotExecutor,
    private val currentController: () -> ModeController,
    private val resolvedActiveDeviceGraph: () -> com.opencamera.core.device.DeviceGraphSpec,
    private val updateState: SessionStateUpdater,
    private val dispatch: suspend (SessionIntent) -> Unit
) {
    private var pendingCountdownJob: Job? = null
    private var pendingCountdownStrategy: CaptureStrategy? = null
    private var recordingWatchdogJob: Job? = null

    fun countdownInProgress(): Boolean = pendingCountdownStrategy != null

    suspend fun process(intent: SessionIntent) {
        when (intent) {
            is SessionIntent.CountdownTick -> handleCountdownTick(intent.remainingSeconds)
            SessionIntent.CountdownCompleted -> handleCountdownCompleted()
            is SessionIntent.ShotStarted -> handleShotStarted(intent.shot)
            is SessionIntent.ShotCompleted -> handleShotCompleted(intent.result)
            is SessionIntent.ShotFailed -> handleShotFailed(intent.shotId, intent.mediaType, intent.reason)
            else -> error("Unexpected capture/recording intent: $intent")
        }
    }

    fun cancelPendingCountdown(reason: String) {
        // Move the current DefaultCameraSession.cancelPendingCountdown body here.
    }

    fun startCaptureCountdown(strategy: CaptureStrategy, countdownSeconds: Int) {
        // Move the current DefaultCameraSession.startCaptureCountdown body here.
    }

    suspend fun submitCaptureStrategy(strategy: CaptureStrategy) {
        // Move the current DefaultCameraSession.submitCaptureStrategy body here.
    }
}
```

The processor needs a `dispatch` callback only for the existing countdown timer to dispatch `CountdownTick` and `CountdownCompleted`. Do not use it for recovery, mode switching, or device events.

## Interaction With Mode Processor

`handleModeIntent` currently receives `ModeSignal.SubmitCapture`, `ModeSignal.StopActiveCapture`, and `ModeSignal.ShowHint`.

After this package:

- `ModeSignal.SubmitCapture` should call `captureRecordingProcessor.startCaptureCountdown(strategy, countdownSeconds)` or `captureRecordingProcessor.submitCaptureStrategy(strategy)`.
- `ModeSignal.StopActiveCapture` should call a new capture processor method, for example:

```kotlin
suspend fun requestStopActiveCapture()
```

That method should preserve current behavior:

- require a stoppable shot through `shotExecutor.requireStoppableShot(state.value.activeShot)`;
- update `recordingStatus = RecordingStatus.STOPPING`;
- start the recording watchdog for `STOPPING`;
- emit `SessionEffect.StopActiveShot(stoppableShot.shotId)`;
- trace `recording.stop.requested`.

## Semantics To Preserve

- Countdown ticks only update state while a countdown is active.
- Countdown completion submits the pending strategy once.
- Session stop, host detach, preview stopped, preview error, and permission loss can cancel countdown through the processor's public `cancelPendingCountdown`.
- Photo `ShotStarted` sets `captureStatus = SAVING`, requests capture feedback snapshot, and traces `capture.saving`.
- Video `ShotStarted` sets `recordingStatus = RECORDING` and traces `recording.started`.
- `ShotCompleted`:
  - clears active shot;
  - sets photo/video status correctly;
  - preserves saved-media thumbnail priority;
  - updates latest capture/video paths;
  - stores temporal live bundle only when present;
  - writes postprocess failure summary to `lastError`;
  - records timing trace when timing exists.
- `ShotFailed` ignores orphaned/duplicate terminal events.
- Permission loss can call interrupted-shot failure trace and mode event without duplicating terminal state changes.
- Recording watchdog behavior remains:
  - `REQUESTING` timeout after 10 seconds;
  - `STOPPING` timeout after 15 seconds;
  - timeout resets recording state to idle and clears active shot.

## Tests To Keep Green

Run targeted capture/recording cases:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.photo shutter emits shot plan and completion updates state" --tests "com.opencamera.core.session.DefaultCameraSessionTest.photo countdown delays shot execution and blocks settings updates until completion"
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.video mode applies torch preference before recording and stops through shutter" --tests "com.opencamera.core.session.DefaultCameraSessionTest.shot failed for already-terminal shot id is idempotent"
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.live photo completion stores bundle and uses live saved action" --tests "com.opencamera.core.session.DefaultCameraSessionTest.saved media thumbnail survives after subsequent shot fails"
```

Then run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

## Acceptance Criteria

- Capture/recording behavior lives in `CaptureRecordingSessionProcessor.kt`.
- Countdown and recording watchdog jobs are not fields on `DefaultCameraSession`.
- `DefaultCameraSession` and other processors access countdown state only through explicit methods.
- No processor emits device commands directly; it emits `SessionEffect` through the shared effect sink.
- Existing capture, recording, thumbnail, live photo, and failure tests pass.
- Stage 7 verification passes:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Non-Goals

- Do not alter `ShotExecutor` planning semantics.
- Do not change media pipeline contracts.
- Do not change CameraX adapter execution.
- Do not move mode switching or lens/zoom controls in this package.
- Do not change capture feedback policy; it already lives in `SessionContracts.kt`.
