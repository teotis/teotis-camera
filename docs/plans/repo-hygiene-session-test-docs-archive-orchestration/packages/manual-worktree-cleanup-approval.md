# Manual Gate - Worktree Cleanup Approval

## Goal

Let the user or Codex approve an exact cleanup list after reviewing package `01` evidence, or explicitly defer cleanup while allowing other governance work to proceed.

## Package ID

`manual-worktree-cleanup-approval`

## Classification

`external-assist`: destructive cleanup requires human approval.

## Allowed Paths

- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/output/manual-worktree-cleanup-approval/**`
- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/manual-worktree-cleanup-approval.md`

## Forbidden Paths

- Any deletion or mutation of `.claude/worktrees/**`, `.worktrees/**`, `/private/tmp/**`, or `public/teotis-camera/**`.

## Steps

1. Read `output/01-worktree-inventory-and-safety-plan/worktree-inventory.md`.
2. Review `output/01-worktree-inventory-and-safety-plan/cleanup-candidates.tsv`.
3. Choose one path:
   - Approve exact candidates by writing `output/manual-worktree-cleanup-approval/approved-cleanup.tsv`.
   - Defer cleanup by writing `output/manual-worktree-cleanup-approval/cleanup-deferred.md`.
4. Mark this package completed through the orchestrator.

## Approval TSV Required Columns

```tsv
absolute_path	classification	reason	approved_action
```

Allowed `approved_action` values:

- `git-worktree-remove`
- `delete-directory`
- `skip`

## Acceptance Criteria

- Approval file contains only paths that appeared in package `01` inventory.
- No path under `public/teotis-camera` is approved.
- No dirty or unreadable worktree is approved for deletion unless the approval note explicitly says the dirty contents are disposable.
- If cleanup is deferred, the deferral file says why and whether finalization may proceed without actual size reduction.

## Verification Commands

```bash
rtk ls docs/plans/repo-hygiene-session-test-docs-archive-orchestration/output/manual-worktree-cleanup-approval
rtk bash docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh mark-state manual-worktree-cleanup-approval completed --verification "manual cleanup approval or deferral recorded"
```

## Risks And Notes

- Do not use this manual gate to approve broad globs. The executor accepts exact paths only.
