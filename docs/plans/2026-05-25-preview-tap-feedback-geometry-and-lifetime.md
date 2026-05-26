# Preview Tap Feedback Geometry And Lifetime

## Goal

Make tap focus / AE feedback appear exactly at the tapped preview position, never outside the active preview/capture area, and disappear automatically after a short visible confirmation.

## Context

- User request: clicking to focus and adjusting brightness shows a tap animation offset from the tapped position; it can even appear outside the preview window and does not disappear.
- Verified facts:
  - `MainActivityActionBinder` dispatches `SessionIntent.PreviewTapToFocus` from `GestureAction.FocusAt`.
  - `normalizedPreviewTapOrNull()` currently filters by `activeFrameRect`, then normalizes relative to that rect.
  - `PreviewOverlayView.drawFocusReticle()` draws `normalizedX * width` and `normalizedY * height`, which assumes full-view normalized coordinates.
  - This mismatch means a tap inside a centered `activeFrameRect` is stored as frame-relative but drawn as full-overlay relative, producing visible offset.
  - `PreviewMeteringFeedback` is updated on request and completion, but there is no expiry intent or scheduled clear path.
  - `PreviewBrightnessFeedback` is also kept until another brightness action or reset path updates it; if it gets a visual indicator later, it needs the same lifetime rule.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/PreviewTapFocusGeometry.kt`
  - `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
  - `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/PreviewRecoverySessionProcessor.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
  - `app/src/test/java/com/opencamera/app/PreviewTapFocusGeometryTest.kt`
  - `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt`
  - `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- Non-goals:
  - Do not change CameraX metering execution unless a focused test proves it is required.
  - Do not add visible instructional text on the shooting surface.
  - Do not persist tap-focus state.

## Implementation Scope

- Use one coordinate system for tap dispatch, session state, and reticle drawing.
- Clamp reticle rendering so the whole ring stays inside the active frame or full preview content.
- Add a session-owned expiry path for `PreviewMeteringFeedback`.
- If brightness feedback has a visible touch indicator, make it use the same geometry/lifetime helper or explicitly keep it separate from the focus reticle.

## Steps

1. Fix coordinate mapping.
   - Preferred repair: make `normalizedPreviewTapOrNull()` return full-view normalized coordinates while still rejecting taps outside `activeFrameRect`.
   - Alternative repair: store coordinate-space metadata and draw frame-relative reticle back into `activeFrameRect`. Only choose this if CameraX metering needs frame-relative coordinates.
2. Update geometry tests.
   - A tap inside an inset `activeFrameRect` should normalize as `tapX / viewWidth`, not `(tapX - rect.left) / rect.width`, if using the preferred repair.
   - A tap outside `activeFrameRect` still returns `null`.
3. Clamp reticle drawing.
   - In `PreviewOverlayView.drawFocusReticle()`, compute the target bounds from `currentActiveFrameRectOrNull() ?: full view`.
   - Clamp `cx/cy` to `bounds.left + radius .. bounds.right - radius` and equivalent for `y`.
   - Add a pure helper test if possible so clamping is testable without bitmap inspection.
4. Add feedback expiry.
   - Add a focused session intent such as `PreviewMeteringFeedbackExpired(requestId: String)`.
   - Schedule it after request completion or after request if no device result arrives, using an existing session scope pattern.
   - Clear only when the request id still matches, so a stale timer cannot hide a newer tap.
5. Add brightness feedback lifetime if applicable.
   - If `PreviewBrightnessFeedback` is visually represented on the overlay, add `PreviewBrightnessFeedbackExpired` or fold it into a generic transient feedback expiry mechanism.
   - If brightness stays quick-panel only, do not invent an overlay animation; just document that issue 5 is covered by the AF/AE metering reticle path.

## Acceptance Criteria

- In 4:3, 16:9, and 1:1 preview frames, the reticle center appears under the tapped point.
- Taps outside the active frame are ignored and do not leave stale feedback.
- The reticle ring never extends outside the active frame or full preview bounds.
- Completed, failed, degraded, and unsupported feedback disappears automatically.
- Rapid consecutive taps keep the newest reticle and do not let an older expiry hide it.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewTapFocusGeometryTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- This is a high-confidence bug: current code mixes active-frame coordinates with full-view drawing coordinates.
- Final “under my finger” acceptance is visual and should be checked by Codex/user on the same real device that exposed the issue.
