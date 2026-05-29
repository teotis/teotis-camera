# Status Directory

This directory contains coordinator status files for the risk-based test tranche orchestration.

- `state.tsv` - Machine-readable state ledger (scheduler source of truth)
- `events.jsonl` - Append-only audit log
- `package-status-template.md` - Template for per-package status
- `01-settings-codecs-tests.md` through `05-testability-audit.md` - Per-package human-readable status
- `99-finalize.md` - Finalize status
- `launch-*.log` - Raw launch output from claude --bg
