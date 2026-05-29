# 01 - Worktree Inventory And Safety Plan

## Goal

Produce an evidence-backed cleanup inventory for `.claude/worktrees`, `.worktrees`, `/private/tmp/open_camera-*`, and registered Git worktrees without deleting anything. The output must let the user approve an exact deletion list later.

## Package ID

`01-worktree-inventory-and-safety-plan`

## Context

- Current evidence: repository `81G`, `.claude/worktrees` `51G`, `.worktrees` `19G`.
- Earlier plan: `docs/plans/2026-05-25-git-worktree-hygiene-and-status-repair.md`.
- `public/teotis-camera` is protected and not a cleanup target.
- Package worktrees for this orchestration live under `/private/tmp/open_camera-orchestration/repo-hygiene-session-test-docs-archive/` and must not be counted as old stale work.

## Allowed Paths

- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/output/01-worktree-inventory-and-safety-plan/**`
- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/01-worktree-inventory-and-safety-plan.md`

## Forbidden Paths

- `.claude/worktrees/**` deletion or mutation
- `.worktrees/**` deletion or mutation
- `public/teotis-camera/**`
- production source files
- `docs/plans/INDEX.md`

## Implementation Scope

- Inventory registered worktrees and nested worktree-like directories.
- Classify entries as `active`, `candidate-stale`, `candidate-merged`, `unknown`, or `protected`.
- Produce an exact TSV candidate list for human review.
- Record before-size evidence.

## Steps

1. Read `AGENTS.md`, `/Users/dingren/.codex/RTK.md`, this package, and `INDEX.md`.
2. From the main checkout, run:
   ```bash
   rtk git worktree list
   rtk git status --short --branch
   rtk du -sh . .worktrees .claude/worktrees docs/plans public/teotis-camera
   rtk bash -lc 'find .claude/worktrees .worktrees -maxdepth 3 -type d -name .git -print'
   ```
3. For each worktree from `git worktree list`, record path, branch, commit, whether the branch is merged to `main`, and `git status --short` from that worktree when readable.
4. For nested `.git` pointer files under `.claude/worktrees` and `.worktrees`, record whether the gitdir points into current `open_camera`, another repository, or a missing path.
5. Write:
   - `output/01-worktree-inventory-and-safety-plan/worktree-inventory.md`
   - `output/01-worktree-inventory-and-safety-plan/cleanup-candidates.tsv`
   - `output/01-worktree-inventory-and-safety-plan/protected-paths.md`
6. Do not delete, move, prune, or `git worktree remove` anything.

## Acceptance Criteria

- Inventory covers `.claude/worktrees`, `.worktrees`, `/private/tmp/open_camera-*` registered worktrees, and the main checkout.
- `public/teotis-camera` is recorded as protected.
- Candidate cleanup list includes only exact absolute paths, classification, reason, branch, commit, dirty status, and confidence.
- Any unreadable or dirty worktree is classified `unknown`, not approved for cleanup.
- Status file includes before-size evidence and exact commands run.

## Verification Commands

```bash
rtk git worktree list
rtk git status --short --branch
rtk du -sh . .worktrees .claude/worktrees docs/plans public/teotis-camera
rtk wc -l docs/plans/repo-hygiene-session-test-docs-archive-orchestration/output/01-worktree-inventory-and-safety-plan/cleanup-candidates.tsv
```

## Expected Evidence Pack

- Worktree path and branch.
- `cleanup-candidates.tsv`.
- `protected-paths.md`.
- Command transcript summary.
- Explicit statement that no destructive cleanup was performed.

## Risks And Notes

- This package is intentionally read-only. The cleanup executor package must not infer approval from this inventory alone.
