# Status Files

Orchestration status tracking. Each functional package and the finalizer write their completion evidence here.

- `state.tsv` — machine-readable scheduler ledger (source of truth for `orchestrate.sh`)
- `events.jsonl` — append-only audit log
- `<package-id>.md` — human-readable package completion report
- `99-finalize.md` — human-readable finalize report

## state.tsv Columns

| Column | Description |
|---|---|
| package_id | Package identifier |
| state | pending / ready / manual_required / launched / in_progress / completed / blocked / stale / invalid / finalizing / finalized |
| launched_at | ISO 8601 timestamp when agent was launched |
| completed_at | ISO 8601 timestamp when completed |
| agent | Claude Code session ID or "manual" |
| branch | Package branch name |
| worktree | Package worktree absolute path |
| base_commit | Base commit SHA when package started |
| commit_hash | Final package commit SHA |
| verification | Verification commands and results |
| integration | Integration status |
| cleanup | Cleanup status |
| last_error | Concise failure statement |
| failed_command | Exact command or operation that failed |
| conflict_files | Comma-separated conflicted files |
| log_summary | Short summary of decisive log lines |
| recovery_hint | Next action a retry/repair agent should try |
