# Package 99 - Finalize

## Package ID

`99-finalize`

## Goal

Finalize this orchestration: verify all functional package evidence, merge package branches into the integration branch in order, run integration verification, merge back to `main` if safe, write `FINAL_REPORT.md`, and clean up only recorded local branches/worktrees after success.

## Required Steps

1. Read `INDEX.md`, `launchers/package-graph.tsv`, all package docs, all status files, and `status/state.tsv`.
2. Verify every functional package:
   - acceptance criteria addressed;
   - changed files are within allowed paths;
   - evidence pack complete;
   - branch, worktree, base commit, commit hash recorded;
   - verification commands passed or failure is explicitly justified.
3. Stop if any package is not `completed` in both Markdown status and `state.tsv`.
4. Create or update integration branch `agent/zoom-brightness-rollback/integration`.
5. Merge branches in order:
   - `agent/zoom-brightness-rollback/01-zoom-slider-render-latch`
   - `agent/zoom-brightness-rollback/02-brightness-dispatch-and-latch`
   - `agent/zoom-brightness-rollback/03-pinch-zoom-basis-repair`
   - `agent/zoom-brightness-rollback/04-integration-verification-and-smoke`
6. Stop and record conflicts without cleaning anything.
7. Run integration verification:
   ```bash
   rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.gesture.GesturePolicyTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
   rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
   rtk ./scripts/verify_stage_7_observability.sh
   ```
8. Merge integration branch back to `main` only after verification passes.
9. Write `FINAL_REPORT.md` and `status/99-finalize.md` for success or failure.
10. Delete only local package branches/worktrees recorded by this orchestration after every prior step succeeds.

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, and recovery suggestion.
- Preserve branches/worktrees on failure.
- Never force-push, hard reset, delete remote branches, or delete unrecorded local resources.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.

