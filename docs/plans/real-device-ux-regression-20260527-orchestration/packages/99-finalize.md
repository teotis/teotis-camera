# 99-finalize

## Goal

Finalize the Real Device UX Regression 20260527 orchestration: verify package evidence, merge package branches into the integration branch, run integration verification, merge verified integration back to `main`, write final reports, and clean only recorded local resources after success.

## Required Inputs

- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-regression-20260527-orchestration/INDEX.md`
- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-regression-20260527-orchestration/launchers/package-graph.tsv`
- all package docs under `packages/`
- all status files under `status/`
- `status/state.tsv`

## Authorization

You are authorized to:

- Inspect package docs, status files, state, branches, commits, and diffs.
- Create/update integration branch `agent/real-device-ux-regression-20260527/integration`.
- Merge package branches in this order:
  `01-zoom-threshold-lens-switch`, `02-cockpit-layout-mode-entry-density`, `03-panel-control-polish`, `04-document-organizer-compact-close`, `05-integration-visual-smoke-protocol`.
- Run focused and stage verification.
- Merge the verified integration branch back to `main`.
- Write `FINAL_REPORT.md` and `status/99-finalize.md`.
- Delete only local package branches/worktrees created and recorded by this orchestration after every prior step succeeds.

## Forbidden

- Do not force-push.
- Do not hard reset.
- Do not delete remote branches.
- Do not delete unrecorded local branches/worktrees.
- Do not clean up anything after a merge or verification failure.
- Do not claim true device acceptance without actual device evidence.

## Required Steps

1. Read INDEX, graph, all package docs, all status files, and `state.tsv`.
2. Verify every functional package:
   - acceptance criteria addressed;
   - changed files are within allowed paths;
   - evidence pack complete;
   - branch, worktree, base commit, commit hash recorded;
   - verification commands passed or failure explicitly justified.
3. Stop and mark blocked if any functional package is not `completed`.
4. Create/update the integration branch.
5. Merge package branches in Merge Strategy order.
6. Stop and record conflicts without cleaning anything.
7. Run integration verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.DevLogRenderModelTest --tests com.opencamera.app.DevLogExporterTest --tests com.opencamera.app.CockpitPanelRouterTest --tests com.opencamera.app.DocumentBatchOrganizerRenderModelTest --tests com.opencamera.app.DocumentBatchRailRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

8. Merge integration branch back to `main` only after verification passes.
9. Update `codex/documentation.md` with a concise verified loop entry if implementation actually landed.
10. Write `FINAL_REPORT.md` and this status file.
11. Clean up only recorded local package worktrees/branches after all prior steps succeed.

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, and recovery suggestion.
- Preserve branches/worktrees on failure.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.
