# Package 01 - Adapter Data Boundary

## Package ID

`01-adapter-data-boundary`

## Goal

Make the CameraX adapter treat `DataReceived` as the device/frame boundary for ordinary still capture, so expensive postprocess and result assembly no longer keep the session effect collector blocked. The adapter must still emit `ShotCompleted` with the final processed result, timing notes, and failure/degraded diagnostics.

## Branch And Worktree

- Branch: `agent/shutter-data-boundary-v1/01-adapter-data-boundary`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/shutter-data-boundary-v1-01-adapter-data-boundary`
- Base: latest `main` unless `99-finalize` or the user says otherwise.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/test/java/com/opencamera/app/camera/**`
- `core/media/src/test/**` only if needed for existing postprocess test helpers
- Coordinator status file: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/status/01-adapter-data-boundary.md`
- Coordinator state file row for `01-adapter-data-boundary`

## Forbidden Paths

- `core/session/**`
- UI render files under `app/src/main/java/com/opencamera/app/`
- Other package status files
- `INDEX.md`
- unrelated formatting or cleanup

## Implementation Guidance

1. Keep the existing `DeviceEvent.DataReceived` emission immediately after `PhotoCaptureOutcome.Success`.
2. Ensure postprocess and final `ShotCompleted` delivery no longer block `DeviceCommand.ExecuteShot` dispatch for ordinary still capture after data has been received.
3. Prefer using the existing `adapterScope` for final result processing if a background job is needed.
4. Preserve `ShotTiming` fields:
   - requested
   - device capture started
   - device capture completed
   - postprocess completed
5. Preserve fail-soft behavior from `guardedPostProcess(...)`.
6. Do not introduce a new session owner in the adapter. The adapter reports events; session decides re-arm policy.

## Acceptance Criteria

- [ ] `DataReceived` still emits before postprocess work starts.
- [ ] `ExecuteShot` handling is not held by postprocess after data received for normal still capture.
- [ ] `ShotCompleted` still emits exactly once with processed result/timing notes.
- [ ] Postprocess failure still produces a degraded `ShotCompleted` result rather than crashing or dropping the shot.
- [ ] Video and multi-frame behavior are not loosened accidentally.
- [ ] Focused adapter tests cover slow postprocess or otherwise prove dispatch no longer waits for final postprocess after data received.

## Verification Commands

Run from the package worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest --tests com.opencamera.app.camera.CameraXCaptureAdapterStillCaptureQualityTest
```

Add any new focused adapter test command to the evidence pack.

## Evidence Required

- Worktree path and branch.
- Base commit and final commit hash.
- Changed files and `git diff --stat`.
- Explanation of how `DataReceived` and `ShotCompleted` ordering is preserved.
- Verification command output summary.
- Any residual concurrency risk.
