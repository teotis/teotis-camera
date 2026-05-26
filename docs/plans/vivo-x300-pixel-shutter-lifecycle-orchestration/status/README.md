# Status Files

Each package executor writes to its own status file. Do NOT edit `INDEX.md`.

## How to use

1. Copy `package-status-template.md` to `<package-id>.md`, or complete the pre-created file for your package.
2. Fill in each section as you complete the package.
3. Do not edit other packages' status files.

## Machine-readable state

`state.tsv` is the scheduler source of truth. If Markdown status and `state.tsv` disagree, `orchestrate.sh status` reports `invalid` and does not unlock downstream packages.

After completing your package, update both your Markdown status file AND the corresponding row in `state.tsv`.

## Orchestration

Use `orchestrate.sh` to manage package lifecycle:
- `bash launchers/orchestrate.sh status` — view all package states
- `bash launchers/orchestrate.sh advance --from <package-id>` — trigger next wave
- `bash launchers/orchestrate.sh retry <package-id>` — reset blocked/stale/invalid packages
