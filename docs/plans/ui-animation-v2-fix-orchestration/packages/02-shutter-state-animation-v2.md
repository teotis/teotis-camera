# 02 Shutter State Animation V2

## Package ID

`02-shutter-state-animation-v2`

## Goal

Wire shutter state animation into the actual shutter control. The current landing added `ShutterVisualDrawable`, but `CockpitSurfaceRenderer.renderShutter()` still swaps static background resources, so the animation package is not functionally delivered.

## Source Handoff / Evidence

- Original handoff: `docs/plans/2026-05-25-shutter-state-animation-v2.md`
- Validation gap: `ShutterVisualDrawable.kt` exists, but `renderShutter()` still calls `setBackgroundResource(...)` with static selectors.

## File Ownership

- Allowed paths:
  - `app/src/main/java/com/opencamera/app/ShutterVisualDrawable.kt`
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` shutter method only
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` shutter visual-state helper only
  - `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt` shutter visual-state model only if needed
  - `app/src/main/res/layout/activity_main.xml` shutter block only
  - `app/src/main/res/drawable/bg_shutter*`
  - focused tests in `app/src/test/java/com/opencamera/app/*Shutter*Test.kt`, `CameraCockpitRenderModelTest.kt`, `SessionCockpitRenderModelTest.kt`
- Forbidden paths:
  - `DefaultCameraSession.kt` and core session behavior
  - Quick panel block in layout
  - FocalLengthSliderView and zoom gesture code

## Dependencies

Wait for `00-mode-order-regression`. Coordinate with `03-zoom-cockpit-v2` and `05-quick-panel-semantic-controls-v2` before editing shared cockpit files.

## Parallel Safety

Do not run in parallel with package 03 or 05 unless agents explicitly coordinate shared file edits.

## Implementation Scope

- Derive `ShutterVisualState` from `SessionState` and current shutter disabled reason.
- Attach `ShutterVisualDrawable` to the actual shutter button or a contained custom visual view.
- Preserve existing click handling and accessibility content description.
- Keep active recording clickable as the stop-recording action.
- Do not imply save success before saved-media/session state confirms it.

## Acceptance Criteria

- `renderShutter()` no longer relies only on static background selector swapping for visual state.
- `ShutterVisualDrawable` or equivalent custom visual is actually used by the shutter control.
- State mapping covers photo ready, countdown, saving, video requesting, video recording, video stopping, blocked, and failure/degraded if represented by session state.
- Active recording remains enabled/clickable.
- Focused render-model tests prove state mapping.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence Pack

Write results to `status/02-shutter-state-animation-v2.md` using the status template.
