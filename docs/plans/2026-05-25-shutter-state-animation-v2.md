# Shutter State Animation V2

## Goal

Replace the current static shutter background swap with a stateful shutter animation that communicates photo capture, countdown, saving, recording start, active recording, stopping, disabled/busy, and failure/degraded feedback without changing session capture behavior.

## Context

- User request: upgrade shutter button state animation to UI/animation 2.0.
- Verified facts:
  - `CockpitSurfaceRenderer.renderShutter()` currently sets `Button.text = ""`, content description, enabled state, and either `bg_shutter_selector` or `bg_shutter_recording_selector`.
  - `SessionCockpitRenderModel.kt` already contains shutter-specific disabled logic.
  - Recent plan `2026-05-25-shutter-state-visual-semantics-repair.md` covers the still-shutter tint/enable bug.
  - `SessionState` already exposes `recordingStatus`, `captureStatus`, `activeShot`, and `countdownRemainingSeconds`.
- Relevant files:
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/res/drawable/bg_shutter_selector.xml`
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/MainActivity.kt`
  - `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
  - `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- Non-goals:
  - Do not queue duplicate still captures.
  - Do not alter `DefaultCameraSession` recording stop semantics.
  - Do not add burst or hold-to-record behavior.
  - Do not hide save failure behind a success animation.

## Implementation Scope

- Introduce a small custom View or Drawable-backed control for the shutter visual layer.
- Keep click handling in `MainActivityActionBinder`.
- Derive visual state from a render model, not from direct Device/CameraX callbacks.
- Suggested visual states:
  - `PHOTO_READY`: white ring and inner fill.
  - `PHOTO_PRESSED`: quick inward pulse.
  - `COUNTDOWN`: circular progress/count text from session countdown.
  - `SAVING`: subtle rotating/progress ring or breathing disabled state.
  - `VIDEO_REQUESTING`: transition to red recording.
  - `VIDEO_RECORDING`: red stop control with elapsed status elsewhere.
  - `VIDEO_STOPPING`: disabled red/neutral saving state.
  - `BLOCKED`: visible neutral/accent busy state with existing disabled reason on tap.
  - `FAILURE_OR_DEGRADED`: short shake/fade only if session presentation exposes failure/degraded output.

## Steps

1. Confirm `2026-05-25-shutter-state-visual-semantics-repair.md` has landed or apply its helper/tint fixes first.
2. Add a render model enum such as `ShutterVisualState`.
   - Build it in app render-model code from `SessionState`.
   - Keep button enabled/clickable semantics separate from visual state.
3. Add `ShutterButtonView` or `ShutterVisualDrawable`.
   - Prefer a small custom `View` if XML replacement is acceptable.
   - If replacing `Button`, preserve accessibility content description and click binding.
   - Draw outer ring, inner fill/stop square, countdown text/progress, and saving ring with Canvas.
4. Update `activity_main.xml` and `MainActivityViews.kt`.
   - Replace or wrap `buttonShutter` without changing its id unless all references are updated in one pass.
   - Keep 72dp touch target.
5. Update `CockpitSurfaceRenderer.renderShutter()`.
   - Set visual state and content description.
   - Avoid repeated `setBackgroundResource()` churn once custom drawing owns visuals.
6. Add tests:
   - render model maps session states to expected `ShutterVisualState`;
   - active recording remains clickable;
   - saving/requested/countdown/recovering are blocked or disabled according to the existing shutter-specific helper.
7. Run focused verification.

## Acceptance Criteria

- Normal photo-ready shutter reads as a white camera shutter, not purple or text-button-like.
- Press feedback appears immediately on tap but does not imply final save success.
- While saving, the shutter remains visible and the UI communicates the blocked/busy state.
- Active video recording keeps the shutter clickable as stop-recording.
- Recording requesting/stopping are visually distinct from active recording.
- Failure/degraded feedback is only shown when session/media state actually reports failure/degradation.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Risks And Notes

- A custom shutter view can easily become a hidden capture state machine. Keep it visual-only.
- If replacing `Button` causes accessibility or click binding churn, use a visual child inside an existing clickable container.
- Final visual timing and color weight require Codex/user screenshot or device review.
