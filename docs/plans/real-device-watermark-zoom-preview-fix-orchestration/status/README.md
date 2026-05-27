# Status Ledger

This directory is coordinator-owned runtime state for `real-device-watermark-zoom-preview-fix-orchestration`.

- `state.tsv` is the scheduler source of truth.
- Package Markdown files are human-readable evidence.
- Agents must update `state.tsv` only with `launchers/orchestrate.sh mark-state`.
- Agents may write only their assigned status file.
- Scratch files are temporary notes and never satisfy completion by themselves.

Valid package ids:

- `01-watermark-template-preview-expectation`
- `02-zoom-threshold-live-preview-switch`
- `03-integration-real-device-smoke-protocol`
- `99-finalize`
