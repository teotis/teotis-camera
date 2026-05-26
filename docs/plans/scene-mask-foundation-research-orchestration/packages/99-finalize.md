# Package 99 - Finalize

## Package ID

`99-finalize`

## Goal

Finalize the Scene Mask foundation research orchestration. This is not a passive audit: it verifies every package evidence pack, integrates any recorded package branches if they exist, writes `FINAL_REPORT.md`, and records cleanup or failure state.

## Allowed Paths

- Read all repository files.
- Write:
  - `docs/plans/scene-mask-foundation-research-orchestration/status/99-finalize.md`
  - `docs/plans/scene-mask-foundation-research-orchestration/status/state.tsv`
  - `docs/plans/scene-mask-foundation-research-orchestration/FINAL_REPORT.md`
- Create/update integration branch `agent/scene-mask-foundation-research/integration`.
- Merge package branches recorded in `status/state.tsv` when they exist.

## Forbidden Paths

- Runtime code and tests, unless merging a completed package branch that already contains authorized changes.
- Package status files for functional packages.
- `INDEX.md`.
- Remote branches.
- Branches/worktrees not recorded by this orchestration.

## Dependencies

- `01-backend-capability-matrix`
- `02-current-implementation-audit`
- `03-product-architecture-design`
- `04-verification-real-device-protocol`

## Branch / Worktree Policy

- Finalize branch: `agent/scene-mask-foundation-research/integration`
- Worktree: `/private/tmp/open_camera-orchestration/scene-mask-foundation-research/99-finalize`
- Re-running finalize after success must report `already finalized`.
- Preserve all branches/worktrees on failure.
- Delete only recorded local package branches/worktrees after every prior finalize step succeeds.

## Finalize Steps

1. Read `INDEX.md`, `launchers/package-graph.tsv`, all package docs, all status files, and `status/state.tsv`.
2. Verify every functional package:
   - acceptance criteria addressed;
   - changed files are within allowed paths;
   - evidence pack complete;
   - branch, worktree, base commit, commit hash recorded or explicitly marked `research-status-only`;
   - verification commands passed or failure is explicitly justified.
3. Decide whether merging is allowed.
4. Create or update the integration branch.
5. Merge functional package branches in Merge Strategy order when branches exist and contain code/status changes intended for integration.
6. Stop and record conflicts without cleaning anything.
7. Run integration verification.
8. Merge integration branch back to `main` only after verification passes and there are branch changes to merge.
9. Write `FINAL_REPORT.md` and `status/99-finalize.md` for both success and failure.
10. Delete only local package branches/worktrees recorded by this orchestration after every prior step succeeds.

## Acceptance Criteria

- All functional packages are `completed` in `state.tsv`.
- Markdown status and `state.tsv` are consistent.
- Evidence is complete enough to decide `PASS`, `PARTIAL`, or `FAIL`.
- Final report explicitly addresses:
  - backend capability matrix;
  - current implementation readiness;
  - product architecture decision;
  - verification protocol;
  - whether to proceed to implementation repair.
- No cleanup happens on failure.

## Verification Commands

```bash
rtk git status --short
rtk rg -n "SceneMask|PreviewSceneMask|MlKit|MaskAware|scene-mask" app core docs/plans codex
```

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, and recovery suggestion.
- Preserve branches/worktrees on failure.
- Never force-push, hard reset, delete remote branches, or delete unrecorded local resources.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit if any, verification summary, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.

