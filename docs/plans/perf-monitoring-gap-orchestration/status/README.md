# Status Files

This directory contains the coordinator status files for each package.

- `state.tsv` — Machine-readable state ledger (source of truth)
- `events.jsonl` — Append-only audit log
- `01-capture-latency-timing.md` — Human-readable status for package 01
- `02-switch-latency-timing.md` — Human-readable status for package 02
- `03-pipeline-timing.md` — Human-readable status for package 03
- `04-runtime-metrics.md` — Human-readable status for package 04
- `99-finalize.md` — Human-readable status for finalize

## State Transitions

```
pending → ready → launched → in_progress → completed
                                    ↘ blocked → (retry) → launched
                                    ↘ stale
                                    ↘ invalid
```

## Commands

```bash
# View current status
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh status

# Mark a package state
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh mark-state <package-id> <state> [--commit <sha>] [--verification "<text>"]
```
