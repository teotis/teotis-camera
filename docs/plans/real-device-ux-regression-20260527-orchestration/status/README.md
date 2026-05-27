# Status Ledger

`state.tsv` is the scheduler source of truth. Markdown files are the human evidence packs.

Package agents may edit only their own status file plus the matching `state.tsv` row. If Markdown status and `state.tsv` disagree, `orchestrate.sh status` reports invalid and no downstream package should launch.

Allowed states:

- `pending`
- `ready`
- `launched`
- `in_progress`
- `completed`
- `blocked`
- `stale`
- `invalid`
- `finalizing`
- `finalized`
