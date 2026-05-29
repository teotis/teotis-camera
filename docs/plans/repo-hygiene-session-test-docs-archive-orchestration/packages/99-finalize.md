# 99 - Finalize Repo Hygiene, Session Test Split, And Plans Archive

## Goal

Verify all functional packages, merge completed branches in order, run integration checks, update the final report, and clean up only worktrees/branches created by this orchestration after success.

## Package ID

`99-finalize`

## Allowed Paths

- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/FINAL_REPORT.md`
- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/99-finalize.md`
- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/events.jsonl`
- Integration branch and package branches recorded in `launchers/package-graph.tsv`.

## Forbidden Paths

- Force-push, hard reset, remote branch deletion.
- Deleting any branch/worktree not created and recorded by this orchestration.
- Deleting `public/teotis-camera` or unapproved stale worktrees.

## Steps

1. Read `INDEX.md`, `launchers/package-graph.tsv`, every package doc, every status file, and `status/state.tsv`.
2. Run:
   ```bash
   rtk bash docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh verify-finalize
   ```
3. Verify package evidence:
   - acceptance criteria addressed
   - changed files are within allowed paths
   - branch, worktree, base commit, commit hash recorded when code/docs changed
   - verification commands passed or baseline failures are explicitly justified
   - package worktree is clean, or dirty state is recorded as blocker
4. Check the manual cleanup gate:
   - if cleanup was approved, package `07` must have executed and recorded before/after evidence;
   - if cleanup was deferred, final report must not claim disk reduction.
5. Create/update integration branch `agent/repo-hygiene-session-test-docs-archive/integration`.
6. Merge functional package branches in merge order.
7. Run integration verification.
8. Merge integration branch back to `main` only after verification passes.
9. Write `FINAL_REPORT.md` and `status/99-finalize.md`.
10. Delete only recorded local package branches/worktrees after every prior step succeeds.

## Integration Verification Commands

```bash
rtk bash docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh verify-finalize
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
rtk rg -n "repo-hygiene-session-test-docs-archive" docs/plans/INDEX.md codex/documentation.md
rtk git worktree list
rtk git status --short --branch
```

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, log summary, and recovery suggestion in `status/99-finalize.md`.
- Also update the coordinator ledger with `mark-state 99-finalize blocked --error ... --failed-command ... --conflict-files ... --log-summary ... --recovery-hint ...`.
- Preserve branches/worktrees on failure.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, cleanup result, and residual risks.
- Re-running finalize after success must be idempotent and report `already finalized`.
