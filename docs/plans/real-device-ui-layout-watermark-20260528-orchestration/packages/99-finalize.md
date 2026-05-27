# Package 99 - Finalize

## Package ID

`99-finalize`

## Goal

Finalize this orchestration by validating package evidence, merging package branches into the integration branch, running integration verification, merging back to mainline only after verification passes, writing `FINAL_REPORT.md`, and cleaning up only recorded resources after success.

## Branch And Worktree

- Branch: `agent/real-device-ui-layout-watermark-20260528/99-finalize`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/real-device-ui-layout-watermark-20260528-99-finalize`

## Allowed Paths

- This plan directory:
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/status/99-finalize.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/FINAL_REPORT.md`
- Git branches/worktrees recorded by this orchestration.
- Integration branch: `agent/real-device-ui-layout-watermark-20260528/integration`.

## Required Work

1. Read `INDEX.md`, `launchers/package-graph.tsv`, every package doc, every status file, and `status/state.tsv`.
2. Run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh verify-finalize
```

3. Verify every functional package:
   - acceptance criteria addressed;
   - changed files are within allowed paths;
   - evidence pack complete;
   - branch, worktree, base commit, commit hash recorded;
   - verification commands passed or failure is explicitly justified;
   - package branch exists;
   - package commit hash exists when code changed;
   - package worktree is clean, or dirty state is recorded as a blocker.
4. Stop if any package is not `completed`.
5. Create or update the integration branch `agent/real-device-ui-layout-watermark-20260528/integration`.
6. Merge package branches in this order:
   - `01-preview-frame-containment`
   - `02-bottom-cockpit-density`
   - `03-quick-watermark-cycle`
   - `04-real-device-acceptance`
7. Stop and record conflicts without cleaning anything.
8. Run integration verification:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.PreviewContentGeometryTest --tests com.opencamera.app.SessionPreviewRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionSettingsManagerTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

9. Merge the verified integration branch back to `main` only after verification passes.
10. Write `FINAL_REPORT.md` and `status/99-finalize.md` for both success and failure.
11. Delete only local package branches/worktrees recorded by this orchestration after every prior step succeeds.

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, log summary, and recovery suggestion in `status/99-finalize.md`.
- Also update the coordinator ledger with `mark-state 99-finalize blocked --error ... --failed-command ... --conflict-files ... --log-summary ... --recovery-hint ...`; do not leave merge/test failures only in prose.
- Check `status/events.jsonl` when explaining repeated failures; do not keep retrying the same fingerprint after the retry breaker opens.
- Preserve branches/worktrees on failure.
- Never force-push, hard reset, delete remote branches, or delete unrecorded local resources.
- Never mix coordinator ledger/final-report commits with unrelated orchestration documents or concurrent mainline edits.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.
