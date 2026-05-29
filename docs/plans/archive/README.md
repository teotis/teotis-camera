# docs/plans/archive/

This directory contains **archived plans** that have reached a terminal lifecycle status (finalized, superseded, or historical evidence).

## Conventions

- Archived files retain their original names and content.
- Each archived entry has a corresponding record in `docs/plans/ARCHIVE_INDEX.md` with:
  - Original path
  - Archive date
  - Reason for archival
  - Back-link to any replacement plan (if superseded)
- Archived plans are **read-only reference material**, not active instructions.
- To restore an archived plan, move it back to `docs/plans/` and update both `ARCHIVE_INDEX.md` and `INDEX.md`.

## What Belongs Here

- Plans whose all QA gates passed and work is merged to mainline (`finalized`)
- Plans replaced by a newer approach (`superseded`)
- Past audits, research, or reviews that informed decisions but have no active code impact (`historical evidence`)

## What Does NOT Belong Here

- Plans with status `active`, `planned`, or `blocked` — these must stay in `docs/plans/`
- Plans still referenced by active orchestration packages or agent instructions
- Protected content governed by `scripts/PUBLIC_VERSION_RULES.md`
