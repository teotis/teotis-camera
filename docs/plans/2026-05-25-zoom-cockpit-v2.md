# Zoom Cockpit V2

## Goal

Upgrade zoom from a simple capsule row into a product-level camera zoom cockpit: exact preset chips, continuous pinch/drag feedback, capability-aware disabled/degraded states, and smooth visual updates that stay aligned with session/device zoom ownership.

## Context

- User request: upgrade Zoom Cockpit V2.
- Verified facts:
  - Stage 7 already has `zoomRatioCapability`, `ZoomRatioToggled`, and `ApplyZoomRatio` ownership in session/device contracts.
  - `CockpitSurfaceRenderer.renderZoomCapsules()` currently rebuilds `TextView` chips on each render.
  - `GesturePolicy` maps pinch zoom to throttled `SessionIntent.ApplyZoomRatio(targetRatio)`.
  - `SessionCockpitRenderModel.zoomCapsuleModels()` derives visible zoom capsules from capability and active graph.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
  - `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
  - `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
  - `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
  - `app/src/test/java/com/opencamera/app/gesture/GesturePolicyTest.kt`
- Non-goals:
  - Do not add vendor/private zoom features.
  - Do not bypass `SessionIntent.ApplyZoomRatio`.
  - Do not add a separate hidden zoom state in UI that can disagree with `SessionState`.
  - Do not implement cinematic zoom or lens-specific optical claims unless the capability model supports them.

## Implementation Scope

- Keep exact preset chips visible and tappable.
- Add continuous zoom feedback for pinch and optional long-press/drag on the zoom strip.
- Use a non-linear display mapping only in UI gesture math; the session still receives normalized ratio values.
- Show unsupported/degraded states honestly:
  - unsupported: strip hidden or disabled with reason;
  - discrete-only: drag snaps to supported ratios;
  - continuous: drag/pinch sends clamped ratio.
- Add status feedback for exact selection and clamping/degradation.

## Steps

1. Stabilize the zoom strip rendering.
   - Avoid rebuilding child views unnecessarily if it causes flicker.
   - Keep `ratio -> chip` identity stable.
2. Add a render model field for current continuous zoom label if needed.
   - Example: `currentZoomLabel`, `isContinuousZoomActive`, `nearestPresetRatio`.
   - Derive it from `state.activeDeviceGraph.preview.zoomRatio`.
3. Implement exact preset selection.
   - Tapping chip dispatches `SessionIntent.ApplyZoomRatio(capsule.ratio)`.
   - Do not dispatch `ZoomRatioToggled` from preset chips.
4. Add optional long-press/drag on zoom strip.
   - Start drag only inside zoom strip.
   - Convert drag position through a small pure helper such as `ZoomScaleMapper`.
   - Clamp/snap using `ZoomRatioCapability`.
   - Dispatch at a safe throttle to avoid flooding the session.
5. Refine pinch behavior.
   - Reset accumulation on scale end/cancel if needed.
   - Keep existing throttle or move it into a pure policy with tests.
6. Add tests:
   - exact chip tap maps to exact ratio;
   - continuous capability clamps to min/max;
   - discrete capability snaps to nearest supported ratio;
   - unsupported capability produces no runtime change and clear disabled/degraded render state.
7. Run focused verification.

## Acceptance Criteria

- Tapping `1x` selects exactly `1x`; tapping `2x` selects exactly `2x`.
- Pinch zoom updates the current zoom ratio without opening panels or switching modes.
- Drag zoom, if implemented, is interruptible and never sends unsupported ratios.
- UI reflects the active zoom from session state after adapter/device feedback.
- Unsupported or discrete-only zoom does not look like a fully continuous professional control.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- Zoom is device-dependent. Keep supported/degraded/unsupported semantics visible and testable.
- Continuous drag can generate many intents. Coalesce on the UI side or policy side, but do not skip final drag-end dispatch.
- Real-device acceptance should include fast tap, slow drag, pinch, mode switch after zoom, and recovery after preview restart.
