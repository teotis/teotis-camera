# 01 Focus And Exposure Feedback V2

## Package ID

`01-focus-exposure-feedback-v2`

## Goal

Complete focus/exposure feedback V2 by adding a small visual-only reticle animation state, preserving geometry/lifetime correctness, and replacing the current vertical EV TODO with either a real session-owned preview brightness dispatch or an explicit no-op decision documented in evidence.

## Source Handoff / Evidence

- Original handoff: `docs/plans/2026-05-25-focus-exposure-feedback-v2.md`
- Validation gap: `PreviewOverlayView.drawFocusReticle()` still draws static circles/ticks and `MainActivityActionBinder` still has `TODO: exposure adjustment via vertical scroll`.

## File Ownership

- Allowed paths:
  - `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
  - `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt` focus/EV gesture branch only
  - `app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt` focus/EV branch only
  - `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt`
  - `app/src/test/java/com/opencamera/app/PreviewTapFocusGeometryTest.kt`
  - `app/src/test/java/com/opencamera/app/gesture/GesturePolicyTest.kt`
  - new focused app test files for focus reticle visual-state helpers
- Forbidden paths:
  - `CameraXCaptureAdapter.kt` and device adapter files
  - `core/session/**` unless a failing test proves a missing expiry hook and scope remains within the original package
  - Shutter, quick panel, and panel transition files owned by other packages

## Dependencies

Wait for `00-mode-order-regression` before final verification.

## Parallel Safety

Can run in parallel with `04-panel-transition-route-continuity`. Coordinate with `03-zoom-cockpit-v2` if both need `GesturePolicy.kt`.

## Implementation Scope

- Add a visual-only reticle animation state or pure helper for elapsed-time visual state.
- Animate requested/succeeded/degraded/failed/unsupported states with short scale/alpha/color changes.
- Keep feedback truth derived from `FocusReticleRenderModel`.
- Replace EV TODO with scoped behavior:
  - if using existing `SessionIntent.ApplyPreviewBrightness`, throttle and dispatch safely;
  - otherwise return `Ignore` and document why EV is intentionally deferred.

## Acceptance Criteria

- Reticle visual state changes over time for requested and terminal statuses.
- Static drawing remains clamped inside active frame/full preview bounds.
- Rapid new focus events do not let stale animation/expiry hide newer feedback.
- No UI-local camera runtime owner is introduced.
- No visible instructional text is added to preview.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewTapFocusGeometryTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence Pack

Write results to `status/01-focus-exposure-feedback-v2.md` using the status template.
