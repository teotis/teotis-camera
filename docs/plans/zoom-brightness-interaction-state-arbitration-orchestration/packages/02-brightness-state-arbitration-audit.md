# Package 02 — Brightness State Arbitration Audit

## Package ID

`02-brightness-state-arbitration-audit`

## Goal

Diagnose the quick brightness slider rebound problem as the brightness-domain version of the zoom rollback issue, and determine which parts of the existing brightness chain can be reused or repaired after the zoom strategy is settled.

## Context

- User report: quick brightness slider rebounds during fast dragging.
- Prior memory/evidence says the brightness path already exists: UI slider -> `SessionIntent.ApplyPreviewBrightness` -> `SessionEffect.ApplyPreviewBrightness` -> `DeviceCommand.ApplyPreviewBrightness` -> CameraX exposure compensation.
- Current evidence to re-check:
  - `MainActivityActionBinder` dispatches on `SeekBar.onProgressChanged(..., fromUser = true)`.
  - `CockpitSurfaceRenderer.renderQuickBubble()` writes `brightnessSlider.progress` on every render.
  - `SessionCockpitRenderModel.brightnessRenderModel()` displays `requestedSteps` while feedback is `REQUESTED`.
  - `DefaultCameraSession` creates a `requestId`, filters stale `PreviewBrightnessApplied` results, and updates applied steps only for matching result.
  - `CameraSessionCoordinator.latestPreviewBrightnessCommand(...)` currently appears to both launch a dispatch job and return the same command to an outer dispatch, which may duplicate device calls.
  - `CameraXCaptureAdapter.applyPreviewBrightness()` awaits `setExposureCompensationIndex(...)`, so rapid requests can serialize or lag behind user drag.

## Non-Goals

- Do not implement runtime fixes in this package.
- Do not change Color Lab/postprocess brightness; this is preview exposure compensation only.
- Do not remove the existing session-owned request id semantics unless evidence proves they are wrong.

## Audit Scope

Read and cite:

- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- quick panel docs: `docs/plans/2026-05-25-quick-panel-regression-repair.md` and `docs/plans/2026-05-25-quick-panel-semantic-controls-v2.md`

## Required Analysis

1. Build the event timeline for fast brightness drag:
   - pointer start;
   - local SeekBar progress;
   - `ApplyPreviewBrightness`;
   - session `REQUESTED`;
   - render-model requested echo;
   - coordinator latest-wins behavior;
   - device applied/failed event;
   - stale ack filtering.
2. Determine whether current rebound can come from:
   - renderer writing progress while user is dragging;
   - old `APPLIED` state replacing newer `REQUESTED`;
   - duplicate dispatch in coordinator;
   - serialized CameraX await calls;
   - missing UI drag latch;
   - interaction between these.
3. Decide whether brightness should share the zoom interaction policy exactly or use a brightness-specific adaptation:
   - zoom can be continuous float; brightness is bounded integer steps;
   - brightness already has request id;
   - brightness result can be `DEGRADED_SAVED_ONLY`, `FAILED`, or `UNSUPPORTED`.
4. Propose the repair order:
   - fix duplicate/latest-wins dispatch if confirmed;
   - add UI drag latch/render suppression;
   - preserve request id stale filtering;
   - expose clear degraded/failed result without snapping the thumb backwards while user is still dragging.

## Acceptance Criteria

- Status file contains a source-of-truth timeline for brightness drag.
- Status file verifies or falsifies the suspected duplicate dispatch in `CameraSessionCoordinator`.
- Status file explains why `REQUESTED` render-model priority is necessary but not sufficient for active dragging.
- Status file recommends how brightness reuses the zoom strategy without becoming a second runtime owner.
- Status file lists exact files/tests an implementation package should touch.

## Suggested Verification Commands

```bash
rtk rg -n "ApplyPreviewBrightness|PreviewBrightness|brightnessSlider|setOnSeekBarChangeListener|latestPreviewBrightnessCommand" app core
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

## Allowed Paths

- `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/02-brightness-state-arbitration-audit.md`

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

- safe with `01-zoom-state-arbitration-audit`

## Expected Evidence Pack

- [ ] worktree path recorded
- [ ] branch name recorded
- [ ] git status captured
- [ ] code references with line numbers
- [ ] commands run and output summary
- [ ] root-cause hypothesis ranked
- [ ] recommended implementation split
- [ ] only allowed paths touched
