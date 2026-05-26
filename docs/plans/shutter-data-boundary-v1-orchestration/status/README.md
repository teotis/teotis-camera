# Status Files

This directory is the coordinator source for the Shutter Data Boundary V1 orchestration.

- Package agents may write only their assigned `<package-id>.md` file and their own row in `state.tsv`.
- Package agents must not edit `INDEX.md` or another package status file.
- `state.tsv` is the scheduler source of truth.
- If Markdown status and `state.tsv` disagree, `orchestrate.sh status` should treat the package as invalid until reconciled.
