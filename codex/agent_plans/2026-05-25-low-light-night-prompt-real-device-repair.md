# Low-Light Night Prompt Real-Device Repair

## Goal

Make the automatic low-light night prompt actually appear in dark photo-mode previews, then disappear after its intended 3-second window.

## Context

- User request: no automatic night icon appears in dark scenes.
- Verified facts:
  - Low-light contracts already exist: `PhotoSceneSignal`, `SceneLightState`, `PhotoLowLightPrompt`, `PhotoLowLightPromptStatus`, and `SessionIntent.PhotoSceneSignalUpdated`.
  - `PreviewSceneBrightnessMonitor` exists and implements hysteresis thresholds, but its default `bitmapProvider` returns `null`.
  - `CameraSessionCoordinator` accepts `sceneBrightnessSource`, collects its signals, and forwards them to session, but `AppContainer` does not pass one.
  - `PreviewSceneBrightnessMonitor` needs access to the attached `PreviewView.bitmap`, which is only known when `CameraSessionCoordinator.attachPreviewHost()` runs.
  - `PhotoLowLightPromptExpired` exists, but there is no visible scheduler in app/session code dispatching it after `visibleUntilElapsedMillis`.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/AppContainer.kt`
  - `app/src/main/java/com/opencamera/app/camera/PreviewSceneBrightnessMonitor.kt`
  - `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/PreviewRecoverySessionProcessor.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
  - `app/src/test/java/com/opencamera/app/camera/CameraSessionCoordinatorTest.kt`
  - `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- Non-goals:
  - Do not auto-switch from `PHOTO` to `NIGHT`.
  - Do not hard-code the prompt visible for every dark-looking launch without a signal.
  - Do not tune thresholds beyond the existing `0.18 / 0.24` hysteresis without real-device samples.

## Implementation Scope

- Wire a real `SceneBrightnessSignalSource` into `CameraSessionCoordinator`.
- Ensure the source samples from the currently attached `PreviewView` at low rate.
- Schedule `PhotoLowLightPromptExpired` after the prompt's visible window.
- Keep unsupported/degraded semantics: unsupported should not show as an available user-facing icon.

## Steps

1. Redesign source wiring around the attached preview host.
   - Either let `PreviewSceneBrightnessMonitor` accept a mutable `PreviewView` host, or create a small source wrapper owned by `CameraSessionCoordinator` that samples `previewView?.bitmap`.
   - Avoid capturing a stale `PreviewView` after host detach.
2. Instantiate the source in `AppContainer`.
   - Pass it to `CameraSessionCoordinator(sceneBrightnessSource = previewSceneBrightnessSource)`, using the final local variable name chosen by the implementation.
   - Keep the sampling scope on `applicationScope` or coordinator scope, matching existing monitor patterns.
3. Start/stop sampling only with preview lifecycle.
   - Confirm `onPreviewStarted()` is called after successful bind.
   - Confirm `onPreviewStopped()` and `onPreviewHostDetached()` stop sampling and clear stale state.
4. Add prompt expiry scheduling.
   - When session shows a prompt with `visibleUntilElapsedMillis`, schedule `SessionIntent.PhotoLowLightPromptExpired`.
   - Dispatch the expiry only once per prompt window, and let session verify time/request freshness before hiding.
   - Prefer a request id or visible-until comparison so a stale expiry cannot hide a newer prompt.
5. Add tests.
   - Coordinator test: fake scene source emits `LOW_LIGHT`; coordinator forwards `PhotoSceneSignalUpdated`.
   - App/source test: source has a non-null bitmap provider after attach and no sampling after detach, where feasible.
   - Session test: prompt visible on low-light signal and hidden after expiry.
   - Render-model test: unsupported prompt stays hidden; available/degraded prompt is visible.

## Acceptance Criteria

- In photo mode with preview active, two consecutive dark samples produce `SceneLightState.LOW_LIGHT`.
- Session presentation exposes a visible low-light prompt for available/degraded support.
- The floating prompt appears for about 3 seconds and then hides.
- Tapping the prompt toggles `PhotoSettings.lowLightNightAssistEnabled`.
- If the device cannot sample preview bitmap, the state remains unknown and the UI does not pretend night assist is active.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Risks And Notes

- `PreviewView.bitmap` can be null during startup or on some devices. Treat that as unknown/degraded signal availability, not as normal light.
- Real-device threshold tuning should be a separate follow-up after collecting sample brightness scores from the target device.
