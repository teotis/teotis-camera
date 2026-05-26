# 05 Quick Panel Semantic Controls V2

## Package ID

`05-quick-panel-semantic-controls-v2`

## Goal

Complete Quick Panel semantic controls V2. The current landing has a brightness slider and single frame-ratio row, but most rows remain generic text buttons and do not expose a semantic control kind, toggle/segmented states, or availability badges.

## Source Handoff / Evidence

- Original handoff: `codex/agent_plans/2026-05-25-quick-panel-semantic-controls-v2.md`
- Validation gap: `activity_main.xml` still uses generic `Button` rows for grid, resolution, Live, and timer; `QuickPanelRowRenderModel` lacks `QuickControlKind`/availability fields.

## File Ownership

- Allowed paths:
  - `app/src/main/res/layout/activity_main.xml` quick panel block only
  - `app/src/main/java/com/opencamera/app/MainActivityViews.kt` quick panel binding only
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` `renderQuickBubble()` and quick helper methods only
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt` quick panel binding only
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` quick panel model only
  - focused tests in `SessionCockpitRenderModelTest.kt` and `SessionUiRenderModelTest.kt`
- Forbidden paths:
  - Shutter/zoom/focus implementation blocks
  - Session kernel and device adapter files
  - Color Lab/filter panel behavior outside quick panel entry links

## Dependencies

Wait for `00-mode-order-regression`. Prefer after `02-shutter-state-animation-v2` and `03-zoom-cockpit-v2` to avoid shared layout/renderer conflicts.

## Parallel Safety

Do not run in parallel with packages 02 or 03 unless file ownership is manually coordinated.

## Implementation Scope

- Add a small `QuickControlKind` or equivalent row semantic model.
- Convert binary settings such as Live to toggle/switch semantics.
- Convert small option sets such as timer/grid/frame ratio to segmented or cycle semantics.
- Keep brightness as slider and prevent renderer-driven dispatch loops.
- Add supported/degraded/unsupported or disabled reason display in model and UI.
- Preserve existing `SessionIntent` and settings-action dispatch paths.

## Acceptance Criteria

- Quick Panel no longer reads as a stack of generic buttons.
- Binary settings look and behave like toggles.
- Option sets look and behave like segmented/cycle controls.
- Numeric brightness uses a slider and cannot render-loop dispatch.
- Disabled or unsupported rows keep layout slot and explain reason.
- No engineering enum labels leak into visible quick panel copy.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Expected Evidence Pack

Write results to `status/05-quick-panel-semantic-controls-v2.md` using the status template.
