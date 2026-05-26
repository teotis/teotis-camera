# Panel Transition And Route Continuity

## Goal

Make panel opening/closing feel continuous and predictable: one active route, one dismiss rule, short anchored transitions, stable scrim layering, and no preview gestures leaking through active panels.

## Context

- User request: upgrade panel transition and route continuity.
- Verified facts:
  - `CockpitPanelRoute` and `MainActivityRenderer.renderPanelVisibility()` already centralize much of panel visibility.
  - The current renderer mostly toggles `isVisible` and alpha.
  - `panelDismissScrim` exists and `MainActivityActionBinder` routes it to `CockpitPanelCommand.DismissAll`.
  - `GestureGuard` already blocks preview gestures when panels are active.
  - `codex/v2_ui/03_interaction_grammar.md` defines one active panel route and outside-tap close rules.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt`
  - `app/src/main/java/com/opencamera/app/CockpitPanelRouter.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityRenderer.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `app/src/main/java/com/opencamera/app/gesture/GestureGuard.kt`
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/test/java/com/opencamera/app/CockpitPanelRouteTest.kt` if present or new focused tests.
- Non-goals:
  - Do not move panel route into Session Kernel.
  - Do not add drag-to-dismiss unless it can be tested and kept isolated.
  - Do not animate panels so long that capture actions feel delayed.
  - Do not add new visual feature panels in this package.

## Implementation Scope

- Add a shared app-layer panel transition helper or renderer method.
- Animate:
  - scrim alpha in/out;
  - panel translate/alpha from the trigger side or bottom;
  - active entry alpha/accent state.
- Keep animation interruptible:
  - opening another route cancels old animation and starts the new one;
  - outside tap/back closes current route.
- Keep panels clickable while scrim dismisses only outside.

## Steps

1. Inspect current `CockpitPanelRoute` and router tests.
   - Ensure `isAnyPanelOpen` includes `QuickBubble`, `Settings`, `StyleLab`, `ColorLab`, and Dev route if applicable.
2. Add a small `PanelTransitionController` or renderer-local helper.
   - It should accept old route, new route, panel views, and scrim.
   - Use `ViewPropertyAnimator` or AndroidX animation APIs already available.
   - Keep durations short: roughly 120-180ms for panel/scrim.
3. Update `MainActivityRenderer.renderPanelVisibility()`.
   - Continue setting final `isVisible` correctly.
   - On route changes, run transition from/to final state.
   - Avoid resetting scroll position on every render; only route changes should reset.
4. Verify XML layering.
   - Scrim above preview/cockpit, below active panel.
   - Panel elevations higher than scrim.
   - Panel internal taps are not intercepted by scrim.
5. Update gesture tests/policy tests.
   - Active panel blocks preview focus/pinch.
   - Outside tap closes route.
   - Opening one panel closes previous panel.
6. Add lightweight renderer tests if feasible.
   - If direct view animation tests are too heavy, test route reducer and keep animation as a small wrapper with final-state assertions.
7. Run focused verification.

## Acceptance Criteria

- Only one panel is open at a time.
- Opening Quick, Settings, Style/Color Lab, or Dev produces consistent scrim and panel transition behavior.
- Outside tap and Android back close the current route before any app-exit behavior.
- Preview tap/pinch does not focus or zoom while a panel is open.
- Route changes remain correct when user taps entries quickly.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CockpitPanelRouteTest --tests com.opencamera.app.gesture.GesturePolicyTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- Animations should not be the source of truth. Always set final visibility/alpha/translation even when animations are disabled or canceled.
- If a global Reduce Motion setting exists later, this helper should become the single place to shorten/disable transitions.
- Codex/user should visually accept timing and layering on device or recording.
