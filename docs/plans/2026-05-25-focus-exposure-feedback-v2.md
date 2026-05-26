# Focus And Exposure Feedback V2

## Goal

Turn the current tap-focus reticle into a polished, truthful focus/exposure feedback system: tap feedback appears under the finger, animates through requested/succeeded/degraded/failed/unsupported states, fades out safely, and optionally exposes vertical EV adjustment without bypassing session/device ownership.

## Context

- User request: upgrade focus / exposure feedback to UI/animation 2.0.
- Verified facts:
  - `MainActivityActionBinder` routes preview tap through `GestureRouter -> GesturePolicy -> SessionIntent.PreviewTapToFocus`.
  - `PreviewOverlayView` already draws a focus reticle from `FocusReticleRenderModel`.
  - `SessionPreviewRenderModel.focusReticleRenderModel()` maps session metering status to UI status.
  - `PreviewRecoverySessionProcessor` owns `PreviewMeteringFeedback` request/completion/expiry semantics.
  - A recent package already covers geometry/lifetime repair: `2026-05-25-preview-tap-feedback-geometry-and-lifetime.md`.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
  - `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/PreviewRecoverySessionProcessor.kt`
  - `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt`
  - `app/src/test/java/com/opencamera/app/PreviewTapFocusGeometryTest.kt`
  - `app/src/test/java/com/opencamera/app/SessionPreviewRenderModelTest.kt` if present or a new focused test file.
- Non-goals:
  - Do not implement CameraX metering in this package if it is still covered by `tap-focus-camerax-execution-repair`.
  - Do not persist focus/EV state as a user setting.
  - Do not add a complex manual-focus distance scale.
  - Do not add instructional text on the preview surface.

## Implementation Scope

- Add a small app-layer animation state for the focus reticle drawing only.
- Animate reticle scale/alpha/color/tick visibility for:
  - requested: quick expand/settle with amber ring;
  - succeeded: short white confirmation;
  - degraded AE-only: amber ring with ticks;
  - failed/unsupported: muted gray contraction/fade.
- Add optional EV drag affordance only through existing gesture/session paths:
  - vertical scroll can show transient EV hint;
  - if actual preview brightness is supported, dispatch `SessionIntent.ApplyPreviewBrightness`;
  - if unsupported/degraded, show the existing disabled/degraded reason.
- Keep all final status truth derived from session state, not local timers alone.

## Steps

1. Confirm the prerequisite geometry/lifetime plan has landed or implement its required coordinate and expiry fixes first.
2. Add a local `FocusReticleAnimationState` or equivalent inside `PreviewOverlayView`.
   - Store last model, model change time, and animation phase.
   - Use `postInvalidateOnAnimation()` while active.
   - Keep animation data separate from session state.
3. Extract pure timing helpers if practical:
   - e.g. `focusReticleVisualState(status, elapsedMillis)`.
   - Unit test requested/succeeded/degraded/failed fade timing without bitmap inspection.
4. Update `drawFocusReticle()` to read the visual state:
   - draw ring radius scale;
   - draw alpha fade;
   - draw degraded ticks;
   - keep clamping from geometry repair.
5. Wire vertical scroll carefully.
   - In `GesturePolicy`, `VerticalScroll` may remain `ShowExposureHint(deltaY)` or become a throttled dispatch to preview brightness only if existing brightness support is active.
   - Do not make vertical scroll mutate Color Lab or saved-photo brightness.
6. Add tests:
   - render model maps all metering statuses to distinct UI statuses;
   - helper returns visible states for requested/succeeded/degraded/failed;
   - rapid status replacement does not keep old feedback alive.
7. Run focused verification.

## Acceptance Criteria

- Tap feedback appears at the tapped point and never outside the active preview/capture frame.
- Requested, succeeded, degraded, failed, and unsupported states are visually distinguishable.
- Feedback disappears automatically and stale expiry cannot hide a newer tap.
- Vertical EV feedback, if implemented, does not affect Color Lab or saved-only postprocessing brightness.
- All preview metering and brightness actions still flow through `SessionIntent -> SessionEffect -> DeviceCommand`.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewTapFocusGeometryTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- This package should polish feedback only after the coordinate/lifetime bug is fixed. Beautiful wrong-position animation is worse than the current rough reticle.
- Avoid long animations. Focus feedback should feel immediate and get out of the way.
- Final acceptance of timing and readability is Codex/user visual QA.
