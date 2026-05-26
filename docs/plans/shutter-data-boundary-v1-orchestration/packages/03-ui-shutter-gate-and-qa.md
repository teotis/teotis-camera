# Package 03 - UI Shutter Gate And QA

## Package ID

`03-ui-shutter-gate-and-qa`

## Goal

Align app render models and shutter clickability with the session re-arm policy. The UI must render what the session permits: normal still capture should not stay disabled solely because media postprocess/save is still finishing, while blocked capture kinds and recording transitions remain truthful.

## Dependencies

- Wait for `02-session-rearm-policy` to complete.

## Branch And Worktree

- Branch: `agent/shutter-data-boundary-v1/03-ui-shutter-gate-and-qa`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/shutter-data-boundary-v1-03-ui-shutter-gate-and-qa`
- Base: latest `main` plus the merged/rebased result of `02-session-rearm-policy`, or let `99-finalize` merge in order.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` only if visual state plumbing requires it
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt` only if click gate needs alignment
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionStateRenderTest.kt` only if needed
- Coordinator status file: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/status/03-ui-shutter-gate-and-qa.md`
- Coordinator state file row for `03-ui-shutter-gate-and-qa`

## Forbidden Paths

- `CameraXCaptureAdapter.kt`
- core session implementation files, except if a tiny compile fix is required after rebasing package 02
- Other package status files
- `INDEX.md`
- visual redesign beyond shutter enabled/disabled semantics

## Implementation Guidance

1. Stop using generic `captureDisabledReason(...)` as the source for shutter clickability.
2. Ensure `shutterDisabledReason(...)` does not block merely because status is the non-blocking received/postprocess state after session policy releases the shot.
3. Preserve these truths:
   - active recording shutter remains clickable as stop recording,
   - recording `REQUESTING` and `STOPPING` remain blocked,
   - preview recovery/countdown remain blocked,
   - permission tap semantics remain unchanged,
   - special capture kinds remain blocked when session policy says so.
4. Keep visual state honest: a background save/postprocess may show a subtle saving/result indicator, but it must not disable the shutter for ordinary still capture.
5. Add focused render model tests. Do not rely on visual inspection only.

## Acceptance Criteria

- [ ] Render tests prove ordinary still after session re-arm is clickable.
- [ ] Render tests prove conservative active photo capture remains blocked before re-arm.
- [ ] Render tests prove active recording shutter remains clickable.
- [ ] Render tests prove recording request/stop states remain blocked.
- [ ] App focused tests pass.
- [ ] Evidence includes a real-device QA checklist for vivo X300 follow-up, but does not claim real-device PASS unless tested.

## Verification Commands

Run from the package worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest
```

## Evidence Required

- Worktree path and branch.
- Base commit and final commit hash.
- Changed files and `git diff --stat`.
- Test output summary.
- Explicit statement whether this is local-verification-only or includes real-device proof.
