# Status Directory

This directory is the coordinator truth for the Real Device UI Layout Watermark 20260528 orchestration.

- `state.tsv` is the scheduler source of truth.
- Package Markdown files are human-readable evidence.
- Agents must update `state.tsv` only through `launchers/orchestrate.sh mark-state`.
- `events.jsonl` is append-only runtime history.
- Scratch files are temporary and never unlock dependencies by themselves.

Use:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh status
```
