# 07 - Worktree Cleanup Execution

## Goal

Execute only the worktree cleanup approved or explicitly deferred by the manual gate, then record before/after size and Git worktree evidence.

## Package ID

`07-worktree-cleanup-execution`

## Dependencies

- Depends on `manual-worktree-cleanup-approval`.

## Allowed Paths

- Exact paths listed in `output/manual-worktree-cleanup-approval/approved-cleanup.tsv`.
- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/output/07-worktree-cleanup-execution/**`
- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/07-worktree-cleanup-execution.md`

## Forbidden Paths

- `public/teotis-camera/**`
- Any path not listed in the approved cleanup TSV.
- Any path classified `unknown`, dirty, unreadable, or protected unless the approval TSV explicitly says it is disposable.
- `docs/plans/**` deletion.
- production source files.

## Implementation Scope

- If cleanup was deferred, record deferral and do not delete anything.
- If cleanup was approved, remove only approved paths using the action specified for each exact path.
- Prefer `git worktree remove <path>` for registered clean worktrees.
- Use directory deletion only for approved unregistered stale directories.
- Record before/after size.

## Steps

1. Check for either:
   - `output/manual-worktree-cleanup-approval/approved-cleanup.tsv`
   - `output/manual-worktree-cleanup-approval/cleanup-deferred.md`
2. If deferred, write a completion report that cleanup was intentionally skipped.
3. If approved, validate every approved path appears in package `01` inventory.
4. Capture before evidence:
   ```bash
   rtk du -sh . .worktrees .claude/worktrees
   rtk git worktree list
   ```
5. Execute only approved actions.
6. Capture after evidence and write cleanup transcript.

## Acceptance Criteria

- No unapproved path is deleted.
- Cleanup transcript includes every command and result.
- `git worktree list` remains readable.
- Before/after size is recorded.
- If cleanup is deferred, final report says no disk reduction was claimed.

## Verification Commands

```bash
rtk git worktree list
rtk git status --short --branch
rtk du -sh . .worktrees .claude/worktrees
rtk ls docs/plans/repo-hygiene-session-test-docs-archive-orchestration/output/07-worktree-cleanup-execution
```

## Expected Evidence Pack

- Approval or deferral artifact used.
- Cleanup transcript.
- Before/after size report.
- Git worktree list after cleanup.
- Commit hash if any repository files were changed.

## Risks And Notes

- Destructive cleanup must stop on first ambiguous path.
