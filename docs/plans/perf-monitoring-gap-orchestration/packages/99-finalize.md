# Package 99 - Finalize

## Objective

Merge all functional packages into the integration branch, verify integration, merge to mainline, and produce the final report.

## Process

1. Read INDEX, graph, all package docs, all status files, and `state.tsv`.
2. Run `bash launchers/orchestrate.sh verify-finalize` before merging.
3. Verify every functional package:
   - acceptance criteria addressed
   - changed files are within allowed paths
   - evidence pack complete
   - branch, worktree, base commit, commit hash recorded
   - verification commands passed or failure is explicitly justified
   - package branch exists
   - package commit hash exists
   - package changed files are within allowed paths
   - package worktree is clean, or dirty state is recorded as a blocker
4. Check Landing Strategy. Decide task-level outcome before any mainline merge.
5. Create or update the integration branch `agent/perf-monitoring-gap/integration`.
6. Merge functional package branches in order: `01-capture-latency-timing -> 02-switch-latency-timing -> 03-pipeline-timing -> 04-runtime-metrics`.
7. Stop and record conflicts without cleaning anything.
8. Run integration verification:
   - `rtk ./scripts/verify_stage_7_observability.sh`
   - `rtk ./gradlew --no-daemon :app:assembleDebug`
9. Merge integration branch back to mainline only after verification passes.
10. Write `FINAL_REPORT.md` and `status/99-finalize.md`.
11. Delete only local package branches/worktrees recorded by this orchestration after every prior step succeeds.

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, log summary, and recovery suggestion in `status/99-finalize.md`.
- Also update the coordinator ledger with `mark-state 99-finalize blocked --error ... --failed-command ... --conflict-files ... --log-summary ... --recovery-hint ...`.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.

## Allowed Paths

- All paths in functional packages
- `docs/plans/perf-monitoring-gap-orchestration/` (coordinator files)
- Branch management (create/delete integration branch)

## Verification Commands

```bash
# Stage 7 verification (full)
rtk ./scripts/verify_stage_7_observability.sh

# Build verification
rtk ./gradlew --no-daemon :app:assembleDebug

# Focused session tests
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest

# Media pipeline tests
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test
```
