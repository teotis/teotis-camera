# 03 Zoom Cockpit V2

## Package ID

`03-zoom-cockpit-v2`

## Goal

Finish Zoom Cockpit V2 by making the new focal length slider capability-aware, tested, and integrated with exact preset and continuous zoom semantics without creating UI-local camera runtime state.

## Source Handoff / Evidence

- Original handoff: `docs/plans/2026-05-25-zoom-cockpit-v2.md`
- Validation gap: `FocalLengthSliderView` exists and dispatches zoom callbacks, but there are no focused tests for snapping/clamping/discrete capability behavior and package verification is blocked by the mode order regression.

## File Ownership

- Allowed paths:
  - `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` zoom/focal slider methods only
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` zoom/focal slider model only
  - `app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt` pinch branch only
  - `app/src/main/res/layout/activity_main.xml` focal slider block only
  - focused zoom tests in app test sources
- Forbidden paths:
  - Shutter visual files
  - Quick panel block
  - Device adapter zoom implementation unless an existing test shows a contract mismatch

## Dependencies

Wait for `00-mode-order-regression`.

## Parallel Safety

Do not run in parallel with `02-shutter-state-animation-v2` or `05-quick-panel-semantic-controls-v2` if they edit `CockpitSurfaceRenderer.kt` or `activity_main.xml`.

## Implementation Scope

- Add pure helper tests for slider ratio mapping, clamping, and nearest-preset snapping.
- Ensure discrete-only capabilities snap to supported ratios.
- Ensure continuous capabilities send clamped ratios and final drag-end dispatch.
- Ensure unsupported zoom hides or disables zoom controls honestly.
- Keep exact preset taps dispatching `SessionIntent.ApplyZoomRatio(ratio)`.

## Acceptance Criteria

- `1x`, `2x`, and other preset taps select exact ratios.
- Drag and pinch never send ratios outside the capability min/max.
- Discrete support snaps to nearest supported ratio.
- Unsupported zoom does not look like a fully enabled continuous control.
- Focused tests cover slider mapping and render-model capability behavior.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence Pack

Write results to `status/03-zoom-cockpit-v2.md` using the status template.
