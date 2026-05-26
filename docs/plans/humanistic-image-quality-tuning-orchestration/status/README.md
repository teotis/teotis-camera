# Status Files

Each package executor writes to its own status file. Do NOT edit `INDEX.md`.

## How to use

1. Copy `package-status-template.md` structure into your assigned `<package-id>.md` file if it is not already present.
2. Fill in each section as you complete the package.
3. Do not edit other packages' status files.
4. Keep large reports inside your own status file under `## Evidence`.

## Machine-readable state

`state.tsv` is the scheduler source of truth. Markdown status is for humans. If markdown status and `state.tsv` disagree, `orchestrate.sh status` reports `invalid` and does not unlock downstream packages.

After completing your package, update BOTH your markdown status file AND the state.tsv row.
