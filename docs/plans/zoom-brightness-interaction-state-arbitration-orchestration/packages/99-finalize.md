# Package 99 — Finalize

## Package ID

`99-finalize`

## Goal

Verify all functional packages completed successfully, write `FINAL_REPORT.md`, and record the final orchestration outcome.

## Finalize Steps

1. Read `INDEX.md`, `launchers/package-graph.tsv`, all package docs, all status files, and `status/state.tsv`.
2. Verify every functional package:
   - acceptance criteria addressed in status file
   - changed files are within allowed paths
   - evidence pack complete (worktree, branch, base commit, commit hash, changed files, verification commands/results)
   - branch, worktree, base commit, commit hash recorded in state.tsv
   - verification commands passed or failure is explicitly justified
3. Decide whether finalization is allowed.
4. For this research-only plan, there are no code branches to merge — verify evidence completeness only.
5. Run integration verification: re-read all status files and confirm cross-package consistency.
6. Write `FINAL_REPORT.md` at the plan root with the full synthesis.
7. Write `status/99-finalize.md` with finalization evidence.
8. Delete only local package worktrees/branches recorded by this orchestration after every prior step succeeds.

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, and recovery suggestion.
- Preserve branches/worktrees on failure.
- Never force-push, hard reset, delete remote branches, or delete unrecorded local resources.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record verification summary and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.

## Acceptance Criteria

- Every functional package status file has been read and verified against its acceptance criteria.
- Cross-package consistency confirmed (01 and 02 evidence consumed by 03, 03 consumed by 04).
- `FINAL_REPORT.md` written with: per-package verdict, cross-package consistency, unresolved risks, recommended next action.
- `status/99-finalize.md` written with finalization evidence.
- PASS/PARTIAL/FAIL verdict recorded.

## Allowed Paths

- `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/status/99-finalize.md`
- `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/FINAL_REPORT.md`

## Forbidden Paths

- `app/src/main/**`
- `app/src/test/**`
- `core/session/**`
- `core/device/**`
- `docs/plans/zoom-brightness-interaction-state-arbitration-orchestration/INDEX.md`
- other packages' status files

## Dependencies

- Depends on: `01-zoom-state-arbitration-audit`, `02-brightness-state-arbitration-audit`, `03-shared-control-state-strategy`, `04-verification-real-device-protocol`

## Expected Evidence Pack

- [ ] all package statuses read and verified
- [ ] acceptance criteria matrix
- [ ] cross-package consistency report
- [ ] final PASS/PARTIAL/FAIL verdict
- [ ] FINAL_REPORT.md written
- [ ] recommended next action
- [ ] cleanup results (if applicable)
