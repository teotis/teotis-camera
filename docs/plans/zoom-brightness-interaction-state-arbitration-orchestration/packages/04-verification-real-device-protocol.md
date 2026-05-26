# Package 04 — Verification And Real-Device Protocol

## Package ID

`04-verification-real-device-protocol`

## Goal

Define the local verification, trace evidence, and real-device smoke protocol needed before claiming the zoom/brightness rebound fix works on device.

## Context

- User reported these as real-device interaction issues, so local unit tests alone are not enough.
- Stage 7 verification remains the project gate, but this problem also needs a small phone/device gesture checklist focused on fast drag behavior.
- This package must read the shared strategy from package 03.

## Non-Goals

- Do not implement code.
- Do not claim final pass without a real-device run.
- Do not add broad automation that requires new infrastructure.

## Required Output

Write a protocol in your status file covering:

1. **Focused Local Tests**
   - zoom slider math/view tests for node label layout, active drag echo suppression, snap threshold, final release value, and external update when not dragging;
   - render model tests for pending/current zoom and brightness;
   - coordinator tests for brightness latest-wins behavior;
   - session tests for stale brightness ack and any new zoom pending/ack contract if introduced.
2. **Trace/Log Evidence**
   - expected zoom trace sequence for fast drag;
   - expected brightness trace sequence with request id and stale filtering;
   - what trace pattern indicates rollback risk.
3. **Real-Device Smoke**
   - slow zoom drag from min to max;
   - fast zoom fling/drag and release;
   - preset node tap;
   - pinch after slider drag;
   - mode switch after zoom;
   - quick brightness slow drag;
   - quick brightness fast drag;
   - active capture/recording disabled behavior;
   - preview recovery after changing controls.
4. **Pass/Fail Criteria**
   - no visible thumb/progress rollback during active drag;
   - no jump to original value before final value after release;
   - node numbers visible and not overlapping;
   - stale device result does not overwrite newest request;
   - failed/degraded latest result is explained without silently snapping during active drag.

## Suggested Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

For worktrees or external agents, use:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
```

## Acceptance Criteria

- Status file provides local gates and real-device smoke steps.
- Status file distinguishes local pass from real-device pass.
- Protocol includes evidence collection expectations, not just manual impressions.
- Protocol includes rollback-specific fail examples.
- Protocol says Stage 7 gate is necessary after implementation but may be expensive; focused tests run first.

## Allowed Paths

- `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/04-verification-real-device-protocol.md`

## Forbidden Paths

- `app/src/main/**`
- `app/src/test/**`
- `core/session/**`
- `core/device/**`
- `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/INDEX.md`
- other packages' status files

## Dependencies

- Depends on: `03-shared-control-state-strategy`
- Dependency type: status
- Unlock condition: completed

## Parallel Safety

- unsafe; must wait for shared strategy

## Expected Evidence Pack

- [ ] package 03 status read and summarized
- [ ] local test matrix
- [ ] real-device smoke protocol
- [ ] trace/log expectations
- [ ] pass/fail criteria
- [ ] unresolved QA risks
- [ ] only allowed paths touched
