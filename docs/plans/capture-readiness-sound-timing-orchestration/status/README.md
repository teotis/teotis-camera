# Capture Readiness Sound Timing Status

This directory is the coordinator truth for package state. Package agents may update only their assigned status file and must mutate `state.tsv` through:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state <package-id> <state> ...
```

Use:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh status
```

for the current scheduler view.
