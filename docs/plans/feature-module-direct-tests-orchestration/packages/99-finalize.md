# Package 99 - Finalize Feature Module Direct Tests

## Package ID

`99-finalize`

## Goal

Verify all functional packages, merge their branches into `agent/feature-module-direct-tests/integration`, run integration verification, merge the verified integration branch back to `main`, write the final report, and clean up only resources recorded by this orchestration after every prior step succeeds.

## Assigned Branch And Worktree

- Integration branch: `agent/feature-module-direct-tests/integration`
- Finalize branch/worktree if needed:
  - Branch: `agent/feature-module-direct-tests/99-finalize`
  - Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/feature-module-direct-tests-99-finalize`

## Inputs

- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/INDEX.md`
- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/launchers/package-graph.tsv`
- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/status/state.tsv`
- All package docs under `packages/`
- All status files under `status/`
- Functional package branches and worktrees recorded in `package-graph.tsv`

## Required Checks Before Merge

For every functional package:

1. Markdown status and `state.tsv` state are both `completed`.
2. Acceptance criteria are explicitly addressed.
3. Changed files are inside allowed paths.
4. Evidence pack includes worktree, branch, base commit, commit hash, changed files, verification commands/results, and risks.
5. Commit hash exists and belongs to the recorded branch.
6. Verification commands passed or the package recorded a justified blocker; justified blockers do not unlock finalize.

## Merge Procedure

1. Create or update `agent/feature-module-direct-tests/integration` from `main`.
2. Merge package branches in order:
   - `agent/feature-module-direct-tests/01-photo-night-still`
   - `agent/feature-module-direct-tests/02-document-video-specialized`
   - `agent/feature-module-direct-tests/03-portrait-pro-humanistic`
3. Stop on conflicts. Record conflict files and recovery suggestion. Do not clean up.
4. Run integration verification.
5. Merge integration branch back to `main` only after verification passes.
6. Write `FINAL_REPORT.md` and `status/99-finalize.md`.
7. Delete only recorded local package worktrees/branches after all finalize steps succeed.

## Integration Verification

Run from the main checkout after package branches are merged into the integration branch:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :feature:mode-photo:test :feature:mode-night:test :feature:mode-document:test :feature:mode-video:test :feature:mode-portrait:test :feature:mode-pro:test :feature:mode-humanistic:test
rtk ./scripts/verify_stage_7_observability.sh
```

If the full Stage 7 script is too slow or environment-blocked, record the focused feature tests as passed and mark full gate as not completed. Do not claim full validation.

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, and recovery suggestion.
- Preserve branches/worktrees on failure.
- Never force-push, hard reset, delete remote branches, or delete unrecorded local resources.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.
