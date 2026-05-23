# Photo Low-Light And Brightness Verification Plan

> For text-only agents: this is the integration gate for the photo low-light/night assist and quick brightness packages. Use `rtk` for every command.

## Goal

Prevent partial or misleading implementations. The feature is acceptable only when settings, session state, device commands, photo capture metadata, quick panel rendering, and verification all agree.

## Focused Test Matrix

### Settings

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
```

Required coverage:

- `PhotoSettings.lowLightNightAssistEnabled` defaults to true.
- Reducer toggles low-light/night assist without changing unrelated photo settings.
- Serializer round-trips low-light/night assist.
- Unknown/missing keys fall back to enabled.

### Session

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionIntentOwnershipTest
```

Required coverage:

- Low-light scene signal in active photo preview shows prompt.
- Prompt expiry hides the icon state after 3 seconds.
- Low-light signal outside photo mode does not show the photo prompt.
- Disabled low-light assist still shows a disabled prompt when low light is detected.
- Multi-frame support reports available; no multi-frame reports degraded; no still capture reports unsupported.
- Brightness increase/decrease/reset emits session effects only when allowed.
- Brightness changes are blocked during countdown and active still capture.
- Applied brightness result updates session presentation.
- Unsupported/failed brightness result does not falsely update applied value.
- Stale low-light or brightness results do not overwrite newer state.

### App Coordinator And Adapter

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest
```

Required coverage:

- Coordinator forwards scene signals into session only as intents.
- Coordinator forwards brightness effects into `DeviceCommand.ApplyPreviewBrightness`.
- Coordinator forwards brightness results back into session.
- Adapter maps CameraX exposure range into explicit supported/degraded/unsupported semantics.
- Adapter brightness failures do not emit preview bind/runtime recovery failures unless the camera itself has a real runtime issue.

### Photo Mode

Run the focused photo strategy tests. If the implementation adds `PhotoModePluginTest`, use the feature module test command; if the assertions live in existing session tests, use the session command below.

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

Required coverage:

- Normal light photo capture preserves current behavior.
- Enabled low-light with multi-frame support submits multi-frame capture strategy.
- Enabled low-light without multi-frame support submits degraded single-frame brightening strategy.
- Disabled low-light assist preserves normal photo capture.
- Capture metadata includes low-light assist strategy and brightness score when active.

### UI Render Model

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest
```

Required coverage:

- Quick panel exposes brightness row in photo mode.
- Brightness row is disabled during countdown and active shot.
- Brightness values format as `-1`, `0`, `+1`.
- Floating low-light prompt render model maps all visible states:
  - enabled supported
  - disabled supported
  - enabled degraded
  - disabled degraded
  - hidden
- Quick labels fit within the existing quick panel constraints.

## Build Gate

Run:

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
```

If Gradle reports transient Kotlin/build-directory errors under `~/.codex-build/OpenCamera`, rerun the smallest failed command serially before calling it a product regression.

## Stage Gate

Run:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

This stage script must still pass because the feature touches session, app coordinator, adapter, and UI render state.

## Manual Smoke Checklist

Use this only after the unit/build gates pass.

1. Fresh install or cleared settings: confirm low-light/night assist is on by default.
2. Enter photo mode with preview active.
3. Simulate or encounter low light: confirm the prompt appears for about 3 seconds.
4. Tap prompt: confirm setting toggles off and prompt reflects off state.
5. Toggle back on.
6. Capture in low light: confirm saved metadata/pipeline notes indicate multi-frame or degraded single-frame strategy.
7. Open quick panel and press brightness plus/minus: confirm preview EV changes if supported, or explicit disabled/unsupported state if not.
8. Start countdown or photo capture: confirm brightness controls are disabled or blocked.
9. Switch to video mode: confirm photo low-light prompt and photo brightness quick controls do not masquerade as video features.

## Anti-Stub Checks

Before claiming completion, search for these patterns:

```bash
rtk rg -n "TODO: exposure|ShowExposureHint|lowLightNightAssist|PhotoSceneSignal|ApplyPreviewBrightness|PreviewBrightness" app core feature
```

Acceptable results:

- Tests and planned comments may mention old gaps.
- Production `TODO: exposure adjustment via vertical scroll` should be removed only if vertical scroll is implemented; otherwise the TODO can remain but quick brightness must be available through quick panel buttons.
- `ApplyPreviewBrightness` must not be a fixed unsupported stub.
- Low-light assist must not be metadata-only when multi-frame support is available.

## Real-Device Boundaries

The local gate can prove contracts and routing. It cannot prove:

- Final threshold quality for every real scene.
- Vendor night-mode parity.
- Exact icon polish on every screen size.
- Whether CameraX exposure compensation is visibly strong on every device.

Record those as real-device QA follow-ups instead of weakening the local tests.
