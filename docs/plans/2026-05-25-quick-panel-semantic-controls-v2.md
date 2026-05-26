# Quick Panel Semantic Controls V2

## Goal

Upgrade Quick Panel from button-like text rows into a semantic camera control sheet: toggles for binary settings, segmented controls for small option sets, sliders for numeric controls, status badges for degraded/unsupported states, and consistent panel row grammar.

## Context

- User request: upgrade Quick Panel controls to UI/animation 2.0.
- Verified facts:
  - `activity_main.xml` currently contains `quickBubblePanel` with rows for grid, quality, resolution, brightness, frame ratio, Live, and timer.
  - `CockpitSurfaceRenderer.renderQuickBubble()` renders most rows by concatenating title and value into buttons.
  - A recent plan `2026-05-25-quick-panel-regression-repair.md` already covers restoring slider brightness and single-row frame ratio cycling.
  - `SessionCockpitRenderModel` owns `QuickPanelSheetRenderModel` and row enabled/disabled state.
- Relevant files:
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
  - `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- Non-goals:
  - Do not add new camera capabilities.
  - Do not change saved JPEG crop, video recording, or Color Lab behavior.
  - Do not expose engineering enum names or raw capability tags to users.
  - Do not make quick panel a general settings replacement.

## Implementation Scope

- Keep the quick panel as a compact secondary sheet.
- Convert rows by semantic type:
  - Grid: segmented/cycle row with current mode.
  - Quality/resolution/video spec: segmented or cycle row depending on option count.
  - Brightness: slider with compact value label.
  - Frame ratio: large segmented or cycle row; no tiny scattered chips.
  - Live: switch/toggle with degraded/unsupported badge.
  - Timer: segmented control for off/3s/10s or current supported set.
- Add a small row model field if needed:
  - `controlKind = TOGGLE | SEGMENTED | SLIDER | CYCLE | STATUS`
  - `availability = supported/degraded/unsupported`
  - `disabledReason`.
- Preserve existing dispatch paths through `SessionIntent` and settings actions.

## Steps

1. Ensure `2026-05-25-quick-panel-regression-repair.md` has landed first.
2. Extend render models only as needed.
   - Prefer adding a focused `QuickControlKind` rather than spreading view decisions through renderer code.
   - Keep labels resolved through `AppTextResolver`/strings.
3. Update XML or render dynamic rows.
   - If dynamic row creation is simpler, extract a small helper in `CockpitSurfaceRenderer` rather than bloating `renderQuickBubble()`.
   - Keep hit targets at least 44dp.
4. Bind actions by semantic control.
   - Toggle rows call existing settings action.
   - Segmented/cycle rows dispatch exact next value.
   - Slider dispatches only from user changes.
   - Disabled rows call existing `showDisabledReason` path if available.
5. Add state badges.
   - `unsupported`: visible low-opacity badge/reason, no active-looking switch.
   - `degraded`: enabled only if safe, with short degraded copy.
6. Add tests:
   - row kind selection for grid/live/timer/frame/brightness;
   - disabled/degraded rows do not look active;
   - brightness slider model preserves min/max/current;
   - no user-facing engineering enum leaks in quick panel labels.
7. Run focused verification.

## Acceptance Criteria

- Quick Panel no longer reads as a stack of generic buttons.
- Binary settings look like toggles; option sets look like segmented/cycle controls; numeric values use sliders.
- Disabled or unsupported controls keep their layout slot and explain the reason.
- Dragging brightness cannot cause renderer-driven dispatch loops.
- Quick Panel remains compact and does not obscure the shutter for common use.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Risks And Notes

- This package deliberately follows the regression repair package. If implemented first, it may hide existing quick-panel bugs instead of fixing them.
- Do not turn Quick Panel into a dense settings screen. Its job is fast capture setup.
- Final compactness and readability need Codex/user visual QA on a small phone.
