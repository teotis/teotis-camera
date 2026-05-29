# 03 - Docs Plan Taxonomy And Retention

## Goal

Create a durable retention model for `docs/plans/` so active packages remain discoverable and historical packages can be archived without deleting decision history.

## Package ID

`03-docs-plan-taxonomy-and-retention`

## Allowed Paths

- `docs/plans/INDEX.md`
- `docs/plans/ARCHIVE_POLICY.md`
- `docs/plans/ARCHIVE_INDEX.md`
- `docs/plans/archive/README.md`
- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/output/03-docs-plan-taxonomy-and-retention/**`
- `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/03-docs-plan-taxonomy-and-retention.md`

## Forbidden Paths

- Moving or deleting existing plan files.
- `codex/documentation.md`
- `AGENTS.md`, `CLAUDE.md`
- production source files.

## Implementation Scope

- Define archive taxonomy: active, planned, blocked, validated, finalized, superseded, historical evidence.
- Add an archive policy that says `docs/plans/` remains canonical and archive moves are reversible.
- Generate a candidate inventory report for a later pilot.
- Update `docs/plans/INDEX.md` with this orchestration entry if not already present.

## Steps

1. Read `docs/plans/INDEX.md`, `codex/documentation.md`, and `docs/plans/2026-05-29-codex-document-asset-cleanup-p1.md`.
2. Create `docs/plans/ARCHIVE_POLICY.md`.
3. Create or update `docs/plans/archive/README.md`.
4. Create `docs/plans/ARCHIVE_INDEX.md` with sections for active/recent/planned/blocked/finalized/historical.
5. Generate `output/03-docs-plan-taxonomy-and-retention/archive-candidates.tsv`.
6. Do not move existing plans in this package.

## Acceptance Criteria

- Policy clearly states that `docs/plans/` remains the canonical planning location.
- Archive pilot candidates are exact paths with reason and risk level.
- No active or blocked package is marked for movement without explicit reason.
- `codex/documentation.md` and `docs/plans/INDEX.md` still point users to current status and current planning home.

## Verification Commands

```bash
rtk rg -n "canonical|archive|docs/plans" docs/plans/ARCHIVE_POLICY.md docs/plans/archive/README.md docs/plans/ARCHIVE_INDEX.md
rtk rg -n "repo-hygiene-session-test-docs-archive" docs/plans/INDEX.md
rtk wc -l docs/plans/repo-hygiene-session-test-docs-archive-orchestration/output/03-docs-plan-taxonomy-and-retention/archive-candidates.tsv
```

## Expected Evidence Pack

- Archive policy files.
- Candidate inventory.
- Verification summary.
- Commit hash.

## Risks And Notes

- This package is policy and inventory only; moving files happens in package `05`.
