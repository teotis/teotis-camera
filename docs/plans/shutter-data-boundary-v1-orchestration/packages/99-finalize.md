# Package 99 - Finalize

## Package ID

`99-finalize`

## Goal

Perform the final integration for Shutter Data Boundary V1: verify package evidence, merge implementation branches into an integration branch, run integration verification, merge the verified result back to `main`, write `FINAL_REPORT.md`, and clean up only the local branches/worktrees recorded by this orchestration after success.

## Branch And Worktree

- Integration branch: `agent/shutter-data-boundary-v1/integration`
- Finalize branch/worktree if needed: `agent/shutter-data-boundary-v1/99-finalize`

## Required Steps

1. Read `INDEX.md`, `launchers/package-graph.tsv`, all package docs, all coordinator status files, and `status/state.tsv`.
2. Verify each functional package:
   - status/state are consistent and `completed`,
   - acceptance criteria are addressed,
   - changed files are within allowed paths,
   - branch, worktree, base commit, commit hash are recorded,
   - verification commands passed or a narrow justification is recorded.
3. Create/update integration branch from latest `main`.
4. Merge in order:
   - `agent/shutter-data-boundary-v1/01-adapter-data-boundary`
   - `agent/shutter-data-boundary-v1/02-session-rearm-policy`
   - `agent/shutter-data-boundary-v1/03-ui-shutter-gate-and-qa`
5. Stop and record conflict details if any merge conflicts occur.
6. Run integration verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.CameraXCaptureAdapterStillCaptureQualityTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

7. If integration verification passes, merge integration branch back to `main` with a non-force local merge.
8. Write `FINAL_REPORT.md` and `status/99-finalize.md`.
9. Mark `99-finalize` as `finalized` in `status/state.tsv`.
10. Delete only the local package worktrees/branches recorded by this orchestration, and only after every prior step succeeds.

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, and recovery suggestion.
- Preserve branches/worktrees on failure.
- Never force-push, hard reset, delete remote branches, or delete unrecorded local resources.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.
