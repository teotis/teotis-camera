# 04 Panel Transition And Route Continuity

## Package ID

`04-panel-transition-route-continuity`

## Goal

Complete panel route continuity by adding a small interruptible transition helper around existing app-shell panel routes. Final visibility must remain correct even when animations are canceled or disabled.

## Source Handoff / Evidence

- Original handoff: `docs/plans/2026-05-25-panel-transition-route-continuity.md`
- Validation gap: `MainActivityRenderer.renderPanelVisibility()` still toggles `isVisible` directly and only sets entry alpha; no transition controller/helper exists.

## File Ownership

- Allowed paths:
  - `app/src/main/java/com/opencamera/app/MainActivityRenderer.kt`
  - `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt`
  - `app/src/main/java/com/opencamera/app/CockpitPanelRouter.kt`
  - `app/src/main/java/com/opencamera/app/gesture/GestureGuard.kt`
  - `app/src/main/res/layout/activity_main.xml` panel/scrim elevation/attributes only
  - `app/src/test/java/com/opencamera/app/CockpitPanelRouteTest.kt`
  - focused panel transition helper tests
- Forbidden paths:
  - Session kernel and device code
  - Quick panel content rows
  - Shutter/zoom visual implementation

## Dependencies

Wait for `00-mode-order-regression` before final verification.

## Parallel Safety

Can run in parallel with `01-focus-exposure-feedback-v2`.

## Implementation Scope

- Add a small transition helper for panel/scrim alpha and translate.
- Keep durations short and route changes interruptible.
- Ensure final visibility/alpha/translation is set deterministically.
- Preserve one-active-route behavior and outside-tap dismissal.
- Add tests for route reducer/visibility final state. If Android animation tests are heavy, isolate pure final-state helper tests.

## Acceptance Criteria

- Only one panel is open at a time.
- Quick, Settings, Style/Color Lab, Document organizer, and Dev routes keep consistent scrim/final state.
- Outside tap/back closes current route before app exit.
- Preview gestures remain blocked when a panel is active.
- Route changes remain correct under quick repeated panel entry taps.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CockpitPanelRouteTest --tests com.opencamera.app.gesture.GesturePolicyTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence Pack

Write results to `status/04-panel-transition-route-continuity.md` using the status template.
