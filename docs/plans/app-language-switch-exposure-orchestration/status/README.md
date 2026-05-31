# Runtime Status

Runtime status for the App Language Switch Exposure orchestration.

- `state.tsv` is the scheduler source of truth.
- Package Markdown files are human-readable evidence records.
- `events.jsonl` is append-only audit history created by `launchers/orchestrate.sh`.
- Package agents must update state through `mark-state`; do not edit `state.tsv` manually.
