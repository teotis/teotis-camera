# 05 - Docs Archive Pilot

## Goal

Run a small, reversible `docs/plans/` archive pilot using the policy from package `03`, moving only low-risk historical plan material and updating links.

## Package ID

`05-docs-archive-pilot`

## Dependencies

- Depends on `03-docs-plan-taxonomy-and-retention`.

## Allowed Paths

- `docs/plans/INDEX.md`
- `docs/plans/ARCHIVE_INDEX.md`
- `docs/plans/ARCHIVE_POLICY.md`
- `docs/plans/archive/**`
- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/output/05-docs-archive-pilot/**`
- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/05-docs-archive-pilot.md`

## Forbidden Paths

- Active orchestration directories with `status/state.tsv`.
- Packages currently marked `planned`, `blocked`, or active in `docs/plans/INDEX.md`.
- `codex/documentation.md` unless a moved link requires a one-line update and the package records it as unavoidable.
- production source files.

## Implementation Scope

- Select a bounded pilot batch from `archive-candidates.tsv`.
- Prefer single-file historical reports or clearly superseded indexes.
- Move with `git mv` to `docs/plans/archive/2026-05/`.
- Update `docs/plans/INDEX.md` or `ARCHIVE_INDEX.md` links.
- Prove no live references break.

## Steps

1. Read `ARCHIVE_POLICY.md`, `ARCHIVE_INDEX.md`, and package `03` candidate TSV.
2. Choose a small pilot batch, ideally 5-15 low-risk files or one low-risk folder.
3. Move only exact paths listed in package `03` candidates.
4. Update archive index with original path, new path, reason, and date.
5. Run link/reference checks.

## Acceptance Criteria

- Pilot is small and reversible.
- No active package, current stage rule, or `codex/documentation.md` current-status link is broken.
- Moved paths are discoverable through `ARCHIVE_INDEX.md`.
- No deletion occurs.

## Verification Commands

```bash
rtk rg -n "docs/plans/.+\\.md" docs/plans/ARCHIVE_INDEX.md docs/plans/INDEX.md
rtk rg -n "repo-hygiene-session-test-docs-archive" docs/plans/INDEX.md docs/plans/ARCHIVE_INDEX.md
rtk git status --short -- docs/plans
```

If files are moved, also run targeted checks for each old path:

```bash
rtk rg -n "<old-path-without-leading-docs/plans/>" docs/plans codex AGENTS.md CLAUDE.md
```

## Expected Evidence Pack

- List of moved paths.
- Before/after link checks.
- Archive index diff summary.
- Commit hash.

## Risks And Notes

- If candidate selection is ambiguous, mark this package blocked instead of moving files.
