# 99 - Finalize

## Package ID

`99-finalize`

## Goal

Finalize the Rendering 2.0 post-merge follow-up orchestration. This is not a passive audit: it must verify package evidence, merge completed package branches into an integration branch, run integration verification, merge the verified integration branch back to `main`, write the final report, and clean up only resources recorded by this orchestration after every prior step succeeds.

## Dependencies

- `01-app-unit-test-gate-cleanup`
- `02-ledger-status-reconciliation`

Unlock condition: both functional packages must be `completed` in `status/state.tsv`, with matching completed evidence in their Markdown status files.

## Allowed Paths

- `docs/plans/rendering-2-0-post-merge-followup-orchestration/status/99-finalize.md`
- `docs/plans/rendering-2-0-post-merge-followup-orchestration/FINAL_REPORT.md`
- Local git branches/worktrees recorded in `launchers/package-graph.tsv` and `status/state.tsv`

Finalize may merge package branch content into the integration branch and then into mainline after verification passes. It must not make unrelated manual runtime edits unless they are a tiny, scope-contained conflict resolution required by a merge and are recorded in the final report.

## Forbidden Without Explicit User Approval

- force-push
- hard reset
- delete remote branches
- delete unrecorded local branches/worktrees
- add secrets or credentials
- merge if any functional package is not completed
- continue after merge conflict or verification failure without recording `blocked`

## Required Steps

1. Read `INDEX.md`, `launchers/package-graph.tsv`, all package docs, all Markdown status files, and `status/state.tsv`.
2. Verify every functional package:
   - acceptance criteria addressed
   - changed files are within allowed paths
   - evidence pack complete
   - branch, worktree, base commit, commit hash recorded
   - verification commands passed or failure is explicitly justified
3. Stop if any package is `blocked`, `stale`, `invalid`, not completed, or has incomplete evidence.
4. Create or update integration branch `agent/rendering-2-0-post-merge-followup/integration`.
5. Merge functional package branches in this order:
   - `agent/rendering-2-0-post-merge-followup/01-app-unit-test-gate-cleanup`
   - `agent/rendering-2-0-post-merge-followup/02-ledger-status-reconciliation`
6. Stop and record conflicts without cleaning anything.
7. Run integration verification:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.RenderRecipeTest --tests com.opencamera.core.effect.PreviewEffectAdapterTest --tests com.opencamera.core.effect.PreviewColorTransformTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewColorTransformOverlayTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:compileDebugKotlin
```

8. Merge integration branch back to `main` only after verification passes.
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
