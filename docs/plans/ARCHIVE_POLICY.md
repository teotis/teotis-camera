# docs/plans/ Archive Policy

Date: 2026-05-30

## Canonical Home

`docs/plans/` is the **canonical planning location** for this project. All handoff documents, orchestration packages, feature plans, and research packages live here. This policy does not change that — it defines how completed or historical plans are surfaced and optionally moved to an archive subdirectory.

## Retention Taxonomy

Every plan in `docs/plans/` has a lifecycle status. The statuses determine whether a plan is active, eligible for archive, or must stay in place.

| Status | Definition | Archive Eligible | Notes |
|---|---|---|---|
| `active` | Currently being executed by agents or awaiting user action | No | Must stay in canonical location |
| `planned` | Defined and scheduled but not yet started | No | Must stay in canonical location |
| `blocked` | Cannot proceed until a dependency or user action resolves | No | Must stay in canonical location |
| `validated` | Implementation complete; local tests pass; real-device QA pending | Conditional | May archive after all downstream references are resolved |
| `implemented` | Code landed; visual/device QA may still be pending | Conditional | May archive after all downstream references are resolved |
| `accepted locally` | Accepted on local machine; real-device or remote QA pending | Conditional | May archive after all downstream references are resolved |
| `finalized` | All QA gates passed; merged to mainline; no outstanding action | Yes | Safe to archive |
| `superseded` | Replaced by a newer plan or approach | Yes | Safe to archive with note pointing to replacement |
| `historical evidence` | Past audit, research, or review that informed decisions but has no active code impact | Yes | Safe to archive; retain as reference |

## Archive Rules

1. **docs/plans/ remains canonical.** Archive moves are within `docs/plans/archive/` — never to an external location.
2. **Moves are reversible.** Every archived file retains its original name and a back-link in `ARCHIVE_INDEX.md`.
3. **No active or blocked plans may be moved** without explicit user approval and documented reason.
4. **Conditional plans** (validated, implemented, accepted locally) may be archived only after:
   - All downstream package dependencies are resolved or explicitly deferred.
   - No active code or agent references point to the plan as a required input.
5. **Index preservation.** `docs/plans/INDEX.md` must always link to current active plans. Archived plans are listed in `docs/plans/ARCHIVE_INDEX.md`.
6. **Reference integrity.** Before archiving, verify that `codex/documentation.md`, `AGENTS.md`, and active orchestration packages do not depend on the plan as a live reference.

## Archive Directory Structure

```
docs/plans/
├── INDEX.md              # Active plans index
├── ARCHIVE_POLICY.md     # This file
├── ARCHIVE_INDEX.md      # Archived plans index
├── archive/
│   └── README.md         # Explains archive conventions
├── <active-plans>/       # Current plans remain here
└── ...
```

## Pilot Scope

Package `05-docs-archive-pilot` will execute a small, reversible archive pilot using candidates identified in `output/03-docs-plan-taxonomy-and-retention/archive-candidates.tsv`. The pilot will:

1. Move no more than 5 finalized/superseded plans.
2. Update `ARCHIVE_INDEX.md` with moved entries.
3. Verify no broken links in active indexes.
4. Report before/after state for user review.

## Enforcement

- This policy is advisory for agents; the orchestrator (`99-finalize`) enforces it during merge.
- Any package that moves plans outside `docs/plans/archive/` or breaks active index links fails verification.
