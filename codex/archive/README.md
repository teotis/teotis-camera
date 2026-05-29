# Codex Archive

This directory contains historical point-in-time documents that are preserved as evidence, not as default agent instructions.

Archived files are **not** part of the active context that agents should load automatically. They exist for:
- Historical traceability of past decisions and audits
- Evidence backing for current conclusions in `codex/documentation.md`
- Reference when investigating why a specific decision was made

## Directory Layout

- `readiness/` — Point-in-time readiness/audit/review reports. These capture the state at specific dates and may contain older `NO GO` or `CONDITIONAL GO` conclusions that have since been superseded.

## Usage Rule

Do not load files from `archive/` unless the current task explicitly asks for historical context, audit trail, or evidence from a specific past gate review. For current product status, read `codex/documentation.md` and `codex/Product-2.0-Standard.md`.
