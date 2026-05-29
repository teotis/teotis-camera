# Status Directory

This directory contains coordinator status files and the machine-readable state ledger.

- `state.tsv` — scheduler source of truth
- `events.jsonl` — append-only audit log
- `<package-id>.md` — human-readable package status
- `.orchestrate.lock` — concurrency lock
