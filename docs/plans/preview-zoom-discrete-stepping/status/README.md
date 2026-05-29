# Status Files

This directory contains runtime status for each package and the machine-readable state ledger.

- `state.tsv` — machine-readable state ledger (source of truth for scheduler)
- `events.jsonl` — append-only audit log of all state transitions
- `<package-id>.md` — human-readable status for each package
- `.orchestrate.lock` — runtime lock directory (created/removed by orchestrate.sh)
