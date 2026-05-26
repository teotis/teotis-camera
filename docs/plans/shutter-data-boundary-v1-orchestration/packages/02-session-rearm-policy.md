# Package 02 - Session Re-arm Policy

## Package ID

`02-session-rearm-policy`

## Goal

Move shutter re-arm authority into the session kernel. On `DataReceived`, ordinary single-frame still capture should become eligible for the next shutter press; conservative capture kinds must remain blocked until `ShotCompleted`.

## Branch And Worktree

- Branch: `agent/shutter-data-boundary-v1/02-session-rearm-policy`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/shutter-data-boundary-v1-02-session-rearm-policy`
- Base: latest `main` unless `99-finalize` or the user says otherwise.

## Allowed Paths

- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessorTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt` only if needed
- `core/media/src/main/kotlin/com/opencamera/core/media/**` only for small policy helpers if session-only placement is worse
- Coordinator status file: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/status/02-session-rearm-policy.md`
- Coordinator state file row for `02-session-rearm-policy`

## Forbidden Paths

- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- UI render files under `app/src/main/java/com/opencamera/app/`
- Other package status files
- `INDEX.md`
- broad session refactors unrelated to re-arm policy

## Implementation Guidance

1. Add a small, testable session-owned re-arm policy. Keep it near `CaptureRecordingSessionProcessor` or in a focused helper.
2. `DataReceived` for an eligible ordinary still should:
   - move status to a non-blocking received/postprocess state, or use existing `DATA_RECEIVED` with clear semantics,
   - release `activeShot` only when the policy says the next shot is safe,
   - keep enough presentation state for the later `ShotCompleted` update.
3. Conservative cases must keep `activeShot` until `ShotCompleted`:
   - `MULTI_FRAME_CAPTURE`
   - high-pixel capture until real-device evidence exists
   - document/portrait/rendering paths where saved result is semantically required
   - video lifecycle
4. Do not let UI, coordinator, or adapter decide the re-arm rule.
5. Preserve `StopActiveShot` semantics for video recording.

## Acceptance Criteria

- [ ] Focused tests prove ordinary still `DataReceived` releases the shutter/session gate.
- [ ] Focused tests prove multi-frame remains blocked until `ShotCompleted`.
- [ ] Tests prove `ShotCompleted` after early re-arm still updates presentation/latest media without requiring the same `activeShot`.
- [ ] Recording stop semantics are unchanged.
- [ ] The policy is explicit and discoverable, not hidden in UI conditions.

## Verification Commands

Run from the package worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest --tests com.opencamera.core.session.DefaultCameraSessionTest
```

## Evidence Required

- Worktree path and branch.
- Base commit and final commit hash.
- Changed files and `git diff --stat`.
- Re-arm policy table implemented in code/tests.
- Verification output summary.
- Any conservative cases intentionally left blocked.
