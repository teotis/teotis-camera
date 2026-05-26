# Video Recording Elapsed-Time Presentation

> For text-only agents: this is a session-owned presentation task. Keep timing state in `Session Kernel`; UI should only render derived state and dispatch user intents. Use `rtk` for every command.

## Goal

Add an appropriate recording-time indication for video mode. While recording, the user should see a stable compact elapsed-time label such as `00:03`, `01:24`, or `1:02:09`.

## Current Gap

- `RecordingStatus` only exposes `IDLE`, `REQUESTING`, `RECORDING`, and `STOPPING`.
- `CockpitSurfaceRenderer.renderShutter()` changes the shutter content description and recording background, but there is no visible duration label.
- `CameraCockpitRenderModel` has `isRecording`, but no elapsed-time or status label beyond generic mode status text.
- `VideoModePlugin.recordingDetail()` says recording started, but it is not a live timer and should not own elapsed time.

## Required Behavior

- Timer starts when the device reports `ShotStarted` for a video recording, not merely when the user taps shutter.
- Timer updates roughly once per second while `recordingStatus == RECORDING`.
- Timer pauses no state machine; it is presentation only.
- Timer clears on `ShotCompleted`, `ShotFailed`, interrupted recording, permission loss, shutdown, and watchdog timeout.
- `REQUESTING` and `STOPPING` may show status text such as `Starting` or `Saving`, but must not show a fake running duration.
- The implementation must not create a second recording owner in UI, coordinator, or mode plugin.

## Suggested Design

### Session State

Add low-frequency presentation fields to `SessionPresentationState`:

```kotlin
val recordingStartedAtElapsedMillis: Long? = null
val recordingElapsedMillis: Long? = null
```

Use `SystemClock.elapsedRealtime()` or an injected clock at the session processor boundary. The state is session presentation state, not persisted settings.

### Session Processor

Extend `CaptureRecordingSessionProcessor`:

- Start a `recordingElapsedJob` in `handleShotStarted()` only when `shot.mediaType == MediaType.VIDEO`.
- Immediately set elapsed to `0L`.
- Update elapsed every 1000 ms while the current active shot is the same video shot and state remains `RECORDING`.
- Cancel the job in:
  - `handleShotCompleted()`,
  - `handleShotFailed()`,
  - `handleInterruptedShotFailure()`,
  - `cancelRecordingWatchdog()` or a new `cancelRecordingPresentationState(reason)` used by shutdown paths,
  - watchdog timeout.
- Clear both presentation fields whenever recording is no longer active.

The existing watchdog job uses a detached `CoroutineScope(Dispatchers.Default + SupervisorJob())`; for elapsed time prefer the injected processor `scope` so shutdown can cancel it with the session.

### Render Model

Add a small model field rather than formatting directly in views:

```kotlin
internal data class RecordingIndicatorRenderModel(
    val isVisible: Boolean,
    val label: String,
    val status: RecordingStatus
)
```

Attach it to `BottomCockpitRenderModel` or `CameraCockpitRenderModel`. The formatter should live in a pure Kotlin helper so tests can cover:

- `0 -> 00:00`
- `3_000 -> 00:03`
- `84_000 -> 01:24`
- `3_729_000 -> 1:02:09`

### UI Placement

Low-risk placement:

- Add a `TextView` above or near the shutter inside the existing `bottomSheet`.
- Show it only when `recordingStatus != IDLE`.
- For `RECORDING`, show elapsed duration.
- For `REQUESTING`, show localized `Starting`.
- For `STOPPING`, show localized `Saving`.

Avoid putting the timer into the shutter button text itself; the shutter is currently icon/background-driven and should remain stable.

## Files To Inspect Or Modify

- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`

## Tests

Add or update:

- `DefaultCameraSessionTest`
  - video `ShotStarted` sets `recordingStatus = RECORDING` and elapsed `0`.
  - elapsed advances while recording using a test dispatcher or injected clock.
  - `ShotCompleted` clears started/elapsed fields and sets `latestSavedMediaType = VIDEO`.
  - `ShotFailed` clears started/elapsed fields.
  - shutdown or permission loss clears timer fields.

- `CameraCockpitRenderModelTest`
  - `RECORDING + elapsed` formats a visible timer.
  - `REQUESTING` shows starting status.
  - `STOPPING` shows saving status.
  - `IDLE` hides the indicator.

Focused verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Manual Smoke

1. Enter video mode.
2. Tap shutter and confirm the indicator shows starting state before CameraX start if there is a delay.
3. Once recording starts, confirm the timer begins at `00:00` and advances once per second.
4. Stop recording and confirm the indicator switches to saving and then clears.
5. Confirm photo mode and still capture countdown are unchanged.

## Non-Goals

- Do not build a video duration parser from the saved MP4 in this pass.
- Do not add recording time limits, thermal policy, or storage-space warnings.
- Do not let `VideoModePlugin` own a timer separate from session state.
