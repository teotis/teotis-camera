# Status Ledger

`state.tsv` is the scheduler source of truth. Package agents must update it only through:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh mark-state <package-id> <state> ...
```

Human-readable package status files live next to this README. Scratch files are temporary and never satisfy acceptance criteria by themselves.
