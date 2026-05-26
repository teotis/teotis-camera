# Package 99 - Finalize

## Package ID

`99-finalize`

## Goal

Finalize the Scene Mask honesty repair implementation. Verify package evidence, merge package branches, run integration gates, merge back to `main` only after success, write `FINAL_REPORT.md`, and clean up only recorded local resources after every prior step succeeds.

## Allowed Paths

- Read all repository files.
- Write:
  - `docs/plans/scene-mask-honesty-repair-orchestration/status/99-finalize.md`
  - `docs/plans/scene-mask-honesty-repair-orchestration/status/state.tsv`
  - `docs/plans/scene-mask-honesty-repair-orchestration/FINAL_REPORT.md`
- Create/update integration branch `agent/scene-mask-honesty-repair/integration`.
- Merge package branches recorded in `status/state.tsv`.

## Forbidden Paths

- Editing runtime code directly during finalize except conflict markers created by merging package branches.
- Functional package status files.
- Remote branches.
- Branches/worktrees not recorded by this orchestration.

## Dependencies

- `01-saved-photo-writeback-honesty`
- `02-preview-analysis-budget-contract`
- `03-scene-mask-verification-gate`

## Branch / Worktree Policy

- Finalize branch: `agent/scene-mask-honesty-repair/integration`
- Worktree: `/private/tmp/open_camera-orchestration/scene-mask-honesty-repair/99-finalize`
- Re-running finalize after success must report `already finalized`.
- Preserve all branches/worktrees on failure.
- Delete only recorded local package branches/worktrees after every prior finalize step succeeds.

## Finalize Steps

1. Read `INDEX.md`, `launchers/package-graph.tsv`, all package docs, all status files, and `status/state.tsv`.
2. Verify every functional package:
   - acceptance criteria addressed;
   - changed files are within allowed paths;
   - evidence pack complete;
   - branch, worktree, base commit, commit hash recorded;
   - verification commands passed or failure is explicitly justified.
3. Create or update integration branch.
4. Merge functional package branches in merge strategy order.
5. Stop and record conflicts without cleaning anything.
6. Run integration verification:
   ```bash
   rtk ./scripts/verify_scene_mask_honesty.sh
   rtk ./gradlew --no-daemon :app:assembleDebug
   ```
7. Merge integration branch back to `main` only after verification passes.
8. Write `FINAL_REPORT.md` and `status/99-finalize.md` for both success and failure.
9. Delete only local package branches/worktrees recorded by this orchestration after every prior step succeeds.

## Acceptance Criteria

- All functional packages are `completed` in `state.tsv`.
- Markdown status and `state.tsv` are consistent.
- No package touched forbidden paths.
- Focused Scene Mask honesty gate passes.
- App debug assembly passes or failure is documented as unrelated and user-approved.
- Final report states that real-device visual QA is still required.

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, and recovery suggestion.
- Preserve branches/worktrees on failure.
- Never force-push, hard reset, delete remote branches, or delete unrecorded local resources.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.

