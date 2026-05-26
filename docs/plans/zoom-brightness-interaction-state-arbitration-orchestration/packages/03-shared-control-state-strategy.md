# Package 03 — Shared Control State Strategy

## Package ID

`03-shared-control-state-strategy`

## Goal

Synthesize the zoom and brightness audits into one reusable interaction-state strategy that solves active-drag rollback without violating the architecture rule that UI renders state and dispatches intents only.

## Context

- User explicitly framed zoom and brightness as the same class of bug: local gesture state, session/device returned state, and throttle/confirmation state are fighting.
- Zoom should be handled first, then brightness should reuse the same strategy with domain-specific differences.
- This package must read status evidence from package 01 and 02 before writing its own recommendation.

## Non-Goals

- Do not implement code.
- Do not create a broad abstraction for every control in the app.
- Do not make UI the owner of committed zoom/brightness state.
- Do not remove existing session/device capability semantics.

## Required Design Output

Write a concise design in your status file with these sections:

1. **State Taxonomy**
   - `Committed session state`: what Session Kernel believes is current.
   - `Pending/requested state`: what has been requested but not confirmed.
   - `Device applied state`: what CameraX/adapter confirms, if the domain has ack.
   - `Active gesture state`: ephemeral pointer-local value shown only while the user is dragging.
   - `Rejected/degraded state`: failed, unsupported, degraded saved-only, or clamped.
2. **Render Arbitration Rule**
   - While a user is actively dragging, the control displays active gesture value and ignores older session/device echoes for the thumb/progress position.
   - On release, send a final requested value; then show pending/requested value until committed/applied or failed.
   - If a newer request exists, stale applied/failed results must not move the control.
   - If failure/degraded result belongs to the latest request, show explanation but avoid jarring rollback until the gesture has ended.
3. **Dispatch Cadence Rule**
   - Zoom: define whether continuous move events dispatch immediately, coalesced, or final-only plus preview feedback. Include rationale.
   - Brightness: define latest-wins dispatch and integer-step coalescing, preserving `requestId`.
4. **Domain Adaptation**
   - Zoom has float ratio, node labels, continuous vs discrete support, and currently lacks applied ack.
   - Brightness has integer steps, CameraX await, request id, and degraded/unsupported statuses.
5. **Implementation Split**
   - Recommended package order for a future implementation pass.
   - Exact file groups and tests.
   - Stop gates where user/Codex must decide product behavior.

## Suggested Design Direction

The expected direction, unless package evidence disproves it:

- Introduce a small app-layer ephemeral interaction latch for active controls, scoped to the view/renderer/binder layer, not a camera runtime owner.
- Prevent render methods from writing stale model values into active drag controls.
- Keep Session Kernel as the committed owner and make pending/requested state visible in render models.
- For zoom, either add a minimal request/applied feedback contract or explicitly decide that session optimistic state plus device failure diagnostics is sufficient for V2.
- For brightness, repair latest-wins dispatch first if duplicate dispatch is confirmed, then apply the same active-drag render suppression strategy.
- Add node labels to zoom as display-only rendering; do not mix it with state ownership.

## Acceptance Criteria

- Status file provides one shared strategy, not two unrelated fixes.
- Strategy explicitly says what UI-local state is allowed and what is forbidden.
- Strategy covers fast drag, slow drag, release, stale result, failure/degraded result, and external updates while not dragging.
- Strategy names focused tests that would catch rollback regressions.
- Strategy gives a future implementation sequence with zoom before brightness.

## Suggested Verification Commands

```bash
rtk rg -n "FocalLengthSlider|ApplyZoomRatio|ApplyPreviewBrightness|PreviewBrightnessFeedback|requestId|setCurrentRatio|brightnessSlider.progress" app core
```

## Allowed Paths

- `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/03-shared-control-state-strategy.md`

## Forbidden Paths

- `app/src/main/**`
- `app/src/test/**`
- `core/session/**`
- `core/device/**`
- `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/INDEX.md`
- other packages' status files

## Dependencies

- Depends on: `01-zoom-state-arbitration-audit`, `02-brightness-state-arbitration-audit`

## Parallel Safety

- unsafe; must wait for package 01 and 02 evidence

## Expected Evidence Pack

- [ ] package 01 status read and summarized
- [ ] package 02 status read and summarized
- [ ] shared state taxonomy
- [ ] render arbitration rule
- [ ] dispatch cadence rule
- [ ] implementation split
- [ ] unresolved product decisions
- [ ] only allowed paths touched
