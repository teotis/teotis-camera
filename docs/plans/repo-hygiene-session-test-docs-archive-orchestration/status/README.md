# Status Ledger

This directory is coordinator-owned. Package agents may update only their assigned status file and must mutate `state.tsv` through:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh mark-state <package-id> <state> ...
```

Do not use a status file copied into a package worktree as scheduler truth. The absolute files in this directory are the coordinator record.
