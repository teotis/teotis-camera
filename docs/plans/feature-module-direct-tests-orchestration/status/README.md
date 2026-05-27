# Feature Module Direct Tests Status

`state.tsv` is the scheduler source of truth. Markdown files are human-readable evidence ledgers.

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

Package agents may write only their own status file and their own row in `state.tsv`. If Markdown status and `state.tsv` disagree after a package claims completion/blockage, `orchestrate.sh status` reports the row as invalid and downstream packages must not unlock.
