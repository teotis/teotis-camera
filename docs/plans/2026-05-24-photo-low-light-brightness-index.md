# 2026-05-24 Photo Low-Light And Brightness Feature Index

> For text-only agents: this is the master handoff for two photo-mode features. Pick one linked plan, keep edits scoped, and run every shell command through `rtk`.

## Source Request

Photo mode should support:

1. A low-light / night strategy.
   - Default is enabled.
   - User can disable or re-enable it.
   - When detection conditions are met, a night icon floats for 3 seconds.
   - The icon gives the user a chance to turn the strategy off or on.
2. The `快捷` quick panel should support frame brightness adjustment.

## Current-Code Evidence

- `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt` currently always submits normal photo capture. It applies filter, watermark, frame ratio, live photo, countdown, quality, and resolution, but it does not inspect any low-light scene signal.
- `feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt` already has the closest capture model: multi-frame profiles when `DeviceCapabilities.supportsNightMultiFrame` is true, and single-frame brightening fallback when multi-frame is unavailable.
- `core/device/src/main/kotlin/com/opencamera/core/device/VideoSpecSelection.kt` already defines `VideoSceneSignal(isLowLight, brightnessScore)`, but it is video-only and used only for runtime low-light FPS selection.
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt` owns runtime/presentation state and session effects. UI must dispatch intents or settings actions; it must not directly drive CameraX.
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt` already maps vertical preview scroll to `GestureAction.ShowExposureHint`, but the branch is still a TODO.
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt` exposes manual exposure compensation capability for capture requests, but there is no preview brightness / EV command yet.
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` and `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` render the quick bubble. The quick bubble currently has rows for grid, quality, frame ratio, live, and timer.

## Architecture Decision

Use a session-owned adaptive photo strategy, not an automatic mode switch.

Rejected alternatives:

- Auto-switch `PHOTO -> NIGHT` when low light is detected. This surprises the user, changes mode-track state, and risks turning scene detection into a hidden mode controller.
- Show only a hint and ask the user to manually switch to `Scenery`. This does not satisfy the requested photo-mode strategy.
- Let UI or `MainActivity` decide night capture and brightness behavior. This violates the existing boundary that UI renders state and dispatches intents only.

Recommended approach:

- Add a low-frequency scene signal contract that can be produced by app/device code and consumed by `Session Kernel`.
- Keep the persistent low-light/night assist toggle in `PhotoSettings`.
- Keep transient icon visibility, detection state, and 3-second prompt timing in `SessionPresentationState`.
- Let `PhotoModePlugin` choose a low-light `CaptureStrategy` only when session-provided runtime state says the strategy is active.
- Implement quick brightness as session/device preview exposure compensation, with a degraded/unsupported result path.

## Work Packages

1. [Photo Low-Light / Night Assist Strategy](./2026-05-24-photo-low-light-night-assist.md)
   - Adds settings, scene signal, presentation prompt, and photo capture strategy selection.
   - Should be implemented before brightness because it establishes the runtime scene-signal shape.

2. [Photo Quick Brightness Adjustment](./2026-05-24-photo-quick-brightness-adjustment.md)
   - Adds quick-panel brightness controls and preview EV command/result handling.
   - Can start after the low-light contract names are settled, but should avoid editing `PhotoModePlugin.kt` at the same time as package 1.

3. [Verification And Anti-Regression Gate](./2026-05-24-photo-low-light-brightness-verification.md)
   - Defines focused tests and global stage verification.
   - Should be run after either package, and fully after both are integrated.

## Parallelization

- Agent A can implement the low-light/night assist package.
- Agent B can implement the quick brightness package after Agent A lands the shared scene-signal names, or Agent B can start from UI/render-model work that does not touch session/device contracts.
- Agent C can implement the verification script after both contract packages are merged.

Avoid parallel edits to:

- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- `app/src/main/res/layout/activity_main.xml`

## Global Acceptance

- In photo mode, low-light/night assist defaults to on after a fresh settings load.
- When the scene becomes low-light, the session presentation exposes a floating night prompt for 3 seconds.
- The prompt can toggle the persisted setting off or on.
- If enabled and low-light is active, photo capture records low-light/night metadata and selects multi-frame strategy when supported.
- If multi-frame night capture is unavailable, the photo strategy degrades to single-frame brightening with explicit metadata and UI availability labels.
- If scene detection is unavailable, the feature does not pretend to be active.
- Quick panel shows brightness controls in photo mode.
- Brightness adjustment emits a session-owned preview brightness command and records supported, degraded, unsupported, and failed outcomes.
- UI does not call CameraX APIs directly.
- Stage 7 verification still passes:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Multimodal Boundary

This package does not require screenshot interpretation or raster asset generation. Text-only agents can implement the contracts, render models, layout row, and tests. Exact icon polish, screenshot review, and real-scene threshold tuning should be treated as a later multimodal or real-device QA pass.
