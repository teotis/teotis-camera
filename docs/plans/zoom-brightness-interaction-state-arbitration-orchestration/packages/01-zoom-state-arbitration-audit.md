# Package 01 — Zoom State Arbitration Audit

## Package ID

`01-zoom-state-arbitration-audit`

## Goal

Diagnose the real-device zoom issue and produce an implementation-ready design note for two symptoms that must be solved together: bottom zoom nodes lack numeric labels, and fast dragging visually rolls back before landing on the final value.

## Context

- User report: bottom zoom column/strip has no node numbers; fast drag first returns to the original position, then jumps to the final position.
- Current evidence to re-check:
  - `FocalLengthSliderView` draws dots but no labels beside/under nodes.
  - `FocalLengthSliderView.ACTION_UP` snaps every drag to the nearest preset, regardless of distance.
  - `CockpitSurfaceRenderer.renderFocalLengthSlider()` calls `setCurrentRatio(model.currentRatio)` on every render.
  - `FocalLengthSliderView.setCurrentRatio()` updates internal `currentRatio` even while dragging, but only invalidates when not dragging.
  - `MainActivity` dispatches `ApplyZoomRatio` for both continuous change and snapped selection.
  - `DefaultCameraSession.handleApplyZoomRatio()` updates session graph optimistically before device completion, and zoom has no request id / applied event.
- Reference package: `docs/plans/zoom-cockpit-v2-productization-orchestration/`.

## Non-Goals

- Do not implement runtime fixes in this package.
- Do not move zoom runtime ownership into UI.
- Do not redesign the whole Zoom Cockpit V2 visual system.
- Do not claim optical focal length or vendor lens switching without device evidence.

## Audit Scope

Read and cite:

- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- existing zoom package docs under `docs/plans/zoom-cockpit-v2-productization-orchestration/`

## Required Analysis

1. Build the event timeline for fast zoom drag:
   - pointer down;
   - local thumb update;
   - `onRatioChanged`;
   - session optimistic graph update;
   - render-model echo;
   - coordinator/device execution;
   - pointer up / snap;
   - stale render/device echoes.
2. Determine exactly which state source can overwrite which other source.
3. Decide whether the rollback is more likely caused by:
   - view release always snapping to nearest preset;
   - render echo writing old `model.currentRatio` into the active drag;
   - dispatch cadence/backpressure;
   - lack of zoom request acknowledgement;
   - interaction between multiple causes.
4. Specify node-number rendering requirements:
   - labels should be attached to preset dots;
   - labels must fit without overlapping thumb/floating label;
   - active preset label should not imply continuous value when between nodes.
5. Propose a minimal implementation sequence for zoom:
   - local drag latch;
   - render echo suppression while dragging;
   - continuous final release unless within snap threshold;
   - optional session-side pending zoom feedback/request id if needed;
   - node labels.

## Acceptance Criteria

- Status file contains a source-of-truth timeline for zoom drag.
- Status file identifies at least one concrete current-code rollback vector.
- Status file separates display-only node labels from state arbitration fixes.
- Status file recommends whether zoom needs a session `requestId`/ack path, or whether UI drag latch plus session optimistic state is enough.
- Status file lists exact files/tests an implementation package should touch.

## Suggested Verification Commands

These are read/audit commands unless the user authorizes implementation:

```bash
rtk rg -n "FocalLengthSlider|ApplyZoomRatio|ZoomRatio|renderFocalLength|onRatioChanged|onRatioSnapped" app core
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

## Allowed Paths

- `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/01-zoom-state-arbitration-audit.md`

## Forbidden Paths

- `app/src/main/**`
- `app/src/test/**`
- `core/session/**`
- `core/device/**`
- `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/INDEX.md`
- other packages' status files

## Dependencies

- Depends on: none

## Parallel Safety

- safe with `02-brightness-state-arbitration-audit`

## Expected Evidence Pack

- [ ] worktree path recorded
- [ ] branch name recorded
- [ ] git status captured
- [ ] code references with line numbers
- [ ] commands run and output summary
- [ ] root-cause hypothesis ranked
- [ ] recommended implementation split
- [ ] only allowed paths touched
