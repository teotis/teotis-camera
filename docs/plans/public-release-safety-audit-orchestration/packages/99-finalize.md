# 99-finalize

## Goal

Integrate completed package outputs, verify that public safety rules and reports are coherent, and produce the final user-facing release safety report. This package must not push or rewrite public history.

## Dependencies

- `01-public-exposure-inventory`
- `02-public-rules-export-gate`
- `03-brand-reference-content-scrub`
- `04-public-history-remediation-plan`
- `05-export-diff-release-verification`

## Allowed Paths

- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/FINAL_REPORT.md`
- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/99-finalize.md`
- package output/status files for read-only inspection

Main-repo merge actions:

- create/update `agent/public-release-safety-audit/integration`
- merge completed package branches into the integration branch
- merge verified integration branch back to `main`

## Forbidden Paths And Actions

- Do not push to public remote.
- Do not force-push.
- Do not rewrite `public/teotis-camera` history.
- Do not delete public repo branches/worktrees.
- Do not clean up anything if merge or verification fails.

## Work

1. Read INDEX, graph, all package docs, all package status files, package output reports, and `state.tsv`.
2. Verify every functional package:
   - acceptance criteria addressed
   - changed files are within allowed paths
   - evidence pack complete
   - branch, worktree, base commit, commit hash recorded where applicable
   - verification commands passed or failure is explicitly justified
3. Merge package branches into `agent/public-release-safety-audit/integration` in graph order.
4. Run integration verification:
   - `rtk bash -n docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh`
   - `rtk bash docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh doctor`
   - `rtk bash scripts/verify_public_release_safety.sh` if the script exists
5. Write `FINAL_REPORT.md` with:
   - confirmed exposures
   - fixed items
   - remaining blockers
   - exact user-approval steps for public history cleanup
   - safe push/no-push recommendation
6. Mark finalize as `finalized` only if all integration checks pass and no unauthorized destructive public action was taken.

## Failure Rules

Any failure sets `99-finalize` to `blocked`. Record failure stage, command, branch, conflict files if any, and recovery suggestion. Preserve branches/worktrees on failure.

## Success Rules

Record integration branch, mainline merge commit, verification summary, and cleanup results. Re-running finalize after success must be idempotent and report `already finalized`.
