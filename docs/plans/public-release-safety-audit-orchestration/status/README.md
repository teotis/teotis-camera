# Public Release Safety Audit Status

`state.tsv` is the scheduler source of truth. Package agents must update it only through:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh mark-state <package-id> <state>
```

Markdown status files are for human evidence. If Markdown and `state.tsv` disagree, run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh doctor
```
