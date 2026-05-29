# Package 99 - Finalize

## Objective

Integrate all completed package branches, verify the combined behavior, update final reports, and clean only resources created by this orchestration after success.

## Dependencies

All functional packages:

- `01-camerax-camera2-signal-feasibility`
- `02-current-shutter-timing-tests`
- `03-capture-readiness-contract`
- `04-adapter-earliest-ready-signal`
- `05-shutter-sound-and-visible-rearm`
- `06-real-device-timing-protocol`

## Required Work

1. Read `INDEX.md`, `launchers/package-graph.tsv`, every package doc, every status file, and `status/state.tsv`.
2. Run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh verify-finalize
```

3. Verify every functional package:
   - state is `completed`,
   - package branch exists,
   - commit hash exists,
   - changed files are within allowed paths,
   - package worktree is clean or dirty state is recorded as blocker,
   - evidence and verification commands are complete.
4. Check the Capability Preflight section. Real-device audio/re-arm QA is external-assist and release-confidence only unless the user explicitly declares it blocking.
5. Create/update `agent/capture-readiness-sound/integration`.
6. Merge functional branches in the INDEX merge order.
7. Stop and record conflicts without cleanup.
8. Run integration verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

9. Merge the verified integration branch back to `main` only after verification passes and external gates are explicitly deferred or satisfied by the user.
10. Write `FINAL_REPORT.md` and `status/99-finalize.md`.
11. Delete only local branches/worktrees recorded by this orchestration after every previous finalize step succeeds.

## Failure Rules

Any failure sets `99-finalize` to `blocked`. Record failure stage, command, branch, conflict files if any, decisive log summary, and recovery suggestion in `status/99-finalize.md`, then update the ledger with `mark-state 99-finalize blocked ...`.

Do not force-push, hard reset, delete remote branches, delete unrecorded resources, or mix coordinator ledger/report commits with unrelated edits.
