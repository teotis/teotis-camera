# Status Directory

`state.tsv` is the scheduler source of truth. Package agents must update it only through:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh mark-state <package-id> <state>
```

Markdown files in this directory are human-readable evidence. They do not unlock dependencies unless the matching `state.tsv` row is consistent.
