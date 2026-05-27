# Status Ledger

This directory is the coordinator truth for the real-device shutter feedback and watermark preview orchestration.

- `state.tsv` is the scheduler source of truth.
- Each package writes only its own `<package-id>.md`.
- Package agents must not edit `INDEX.md` or another package status file.
- If Markdown status and `state.tsv` disagree for a terminal state, `orchestrate.sh status` reports `invalid` and downstream packages must not launch.
