# Status Directory

Human-readable status files for each package are written here by package agents.

Do not edit these files manually — use the orchestrator shell commands.

- `state.tsv`: machine-readable state ledger (source of truth for scheduling)
- `events.jsonl`: append-only audit log
- `<package-id>.md`: per-package human-readable status, written by package agents
