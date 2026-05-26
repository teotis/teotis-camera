# Package 99 - Finalize

## Package ID

`99-finalize`

## Goal

Finalize the Stage 7 session test hang orchestration. This is an active finalize package, not a passive audit. It must verify package evidence, merge the functional package branch into the integration branch, run integration verification, merge the verified integration branch back to mainline, write `FINAL_REPORT.md`, and clean up only resources recorded by this orchestration after every prior step succeeds.

## Allowed Paths

- `docs/plans/stage7-session-test-hang-orchestration/status/99-finalize.md`
- `docs/plans/stage7-session-test-hang-orchestration/status/state.tsv` only the `99-finalize` row
- `docs/plans/stage7-session-test-hang-orchestration/FINAL_REPORT.md`

## Forbidden Paths

- Functional package implementation files unless resolving a merge conflict after explicit user approval
- `INDEX.md`
- other package status files except read-only inspection
- any branch/worktree not recorded in `launchers/package-graph.tsv` or `status/state.tsv`

## Required Finalize Steps

1. Read `INDEX.md`, `launchers/package-graph.tsv`, all package docs, all status files, and `status/state.tsv`.
2. Verify every functional package:
   - acceptance criteria addressed
   - changed files are within allowed paths
   - evidence pack complete
   - branch, worktree, base commit, and commit hash recorded
   - verification commands passed or failure is explicitly justified
3. Stop if any functional package is not `completed`.
4. Create or update integration branch `agent/stage7-session-test-hang/integration`.
5. Merge package branches in this order:
   - `agent/stage7-session-test-hang/01-session-test-hang-diagnosis-and-repair`
6. Stop and record conflict files without cleaning anything if a merge conflict occurs.
7. Run integration verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest
```

8. Merge the verified integration branch back to `main` using a local non-force merge.
9. Write `FINAL_REPORT.md` and `status/99-finalize.md` for both success and failure.
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
