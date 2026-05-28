# Package 99 - Finalize Full Clear Mode V1

## Goal

Verify package evidence, merge functional package branches into `agent/full-clear-mode-v1/integration`, run integration verification, merge back to `main` only if allowed, and write the final report.

## Required Work

1. Read `INDEX.md`, `launchers/package-graph.tsv`, all package docs, all status files, and `status/state.tsv`.
2. Run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh verify-finalize
```

3. Verify every functional package:
   - acceptance criteria addressed,
   - changed files are within allowed paths,
   - evidence pack complete,
   - branch, worktree, base commit, commit hash recorded,
   - verification commands passed or failures are explicitly justified,
   - package branch and commit hash exist,
   - package worktree is clean or dirty state is recorded as blocker.
4. Check Capability Preflight. Real-device QA is external-assist and final product confidence only unless the user explicitly changes it to release-blocking.
5. Create or update `agent/full-clear-mode-v1/integration`.
6. Merge packages in the declared Merge Strategy order.
7. Stop and record conflicts without cleaning anything.
8. Run integration verification:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:mode:test :core:media:test :core:device:test
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :feature:mode-fullclear:test
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
```

9. Merge integration branch back to `main` only after verification passes and the user has not made real-device QA release-blocking.
10. Write `FINAL_REPORT.md` and `status/99-finalize.md`.
11. Delete only local package branches/worktrees recorded by this orchestration after every prior step succeeds.

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, log summary, and recovery suggestion.
- Also update the coordinator ledger with `mark-state 99-finalize blocked ...`.
- Preserve branches/worktrees on failure.
- Never force-push, hard reset, delete remote branches, or delete unrecorded local resources.

