# Agent Prompts

## Package: 01-capture-latency-timing - Shutter-to-Capture Latency (G1)

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/packages/01-capture-latency-timing.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/status/01-capture-latency-timing.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh scratch-path 01-capture-latency-timing`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch. Anything required for scheduling, completion, or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Do not attempt external-assist work inside a Claude package. If you discover a package requires a physical device, user-owned account, secret, external approval, or human-only judgment that was not declared, mark the package `blocked` with a precise recovery hint instead of improvising or claiming completion.

Do not invent an unapproved fallback. If the primary package path cannot land, classify why, identify whether an INDEX-declared fallback applies, and record whether the situation should retry, investigate, ask the user, switch to a named fallback, or abort the orchestration. If your package might be an independent merge candidate, state why it is independent and provide standalone verification; otherwise say it should be discarded if the main plan fails.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- For blockers, classify the failure as one of: `capability-gap`, `invalid-requirement`, `external-dependency`, `verification-failure`, `merge-conflict`, `design-invalid`, `cost-out-of-scope`, or `unknown-needs-investigation`.
- If this package was retried or previously blocked, inspect `state.tsv`, the package status file, and `status/events.jsonl` before editing. Carry forward the recorded `last_error`, `failed_command`, `conflict_files`, `log_summary`, and `recovery_hint` into your diagnosis.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh mark-state 01-capture-latency-timing completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh mark-state 01-capture-latency-timing blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh advance --from 01-capture-latency-timing
```

---

## Package: 02-switch-latency-timing - Mode/Lens Switch Latency (G2, G3)

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/packages/02-switch-latency-timing.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/status/02-switch-latency-timing.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh scratch-path 02-switch-latency-timing`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch. Anything required for scheduling, completion, or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Do not attempt external-assist work inside a Claude package. If you discover a package requires a physical device, user-owned account, secret, external approval, or human-only judgment that was not declared, mark the package `blocked` with a precise recovery hint instead of improvising or claiming completion.

Do not invent an unapproved fallback. If the primary package path cannot land, classify why, identify whether an INDEX-declared fallback applies, and record whether the situation should retry, investigate, ask the user, switch to a named fallback, or abort the orchestration. If your package might be an independent merge candidate, state why it is independent and provide standalone verification; otherwise say it should be discarded if the main plan fails.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- For blockers, classify the failure as one of: `capability-gap`, `invalid-requirement`, `external-dependency`, `verification-failure`, `merge-conflict`, `design-invalid`, `cost-out-of-scope`, or `unknown-needs-investigation`.
- If this package was retried or previously blocked, inspect `state.tsv`, the package status file, and `status/events.jsonl` before editing. Carry forward the recorded `last_error`, `failed_command`, `conflict_files`, `log_summary`, and `recovery_hint` into your diagnosis.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh mark-state 02-switch-latency-timing completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh mark-state 02-switch-latency-timing blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh advance --from 02-switch-latency-timing
```

---

## Package: 03-pipeline-timing - Media Pipeline Timing (G4, G5, G8)

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/packages/03-pipeline-timing.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/status/03-pipeline-timing.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh scratch-path 03-pipeline-timing`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch. Anything required for scheduling, completion, or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Do not attempt external-assist work inside a Claude package. If you discover a package requires a physical device, user-owned account, secret, external approval, or human-only judgment that was not declared, mark the package `blocked` with a precise recovery hint instead of improvising or claiming completion.

Do not invent an unapproved fallback. If the primary package path cannot land, classify why, identify whether an INDEX-declared fallback applies, and record whether the situation should retry, investigate, ask the user, switch to a named fallback, or abort the orchestration. If your package might be an independent merge candidate, state why it is independent and provide standalone verification; otherwise say it should be discarded if the main plan fails.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- For blockers, classify the failure as one of: `capability-gap`, `invalid-requirement`, `external-dependency`, `verification-failure`, `merge-conflict`, `design-invalid`, `cost-out-of-scope`, or `unknown-needs-investigation`.
- If this package was retried or previously blocked, inspect `state.tsv`, the package status file, and `status/events.jsonl` before editing. Carry forward the recorded `last_error`, `failed_command`, `conflict_files`, `log_summary`, and `recovery_hint` into your diagnosis.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh mark-state 03-pipeline-timing completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh mark-state 03-pipeline-timing blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh advance --from 03-pipeline-timing
```

---

## Package: 04-runtime-metrics - Runtime Quality Metrics (G6, G7, G9, G10)

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/packages/04-runtime-metrics.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/status/04-runtime-metrics.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh scratch-path 04-runtime-metrics`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch. Anything required for scheduling, completion, or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Do not attempt external-assist work inside a Claude package. If you discover a package requires a physical device, user-owned account, secret, external approval, or human-only judgment that was not declared, mark the package `blocked` with a precise recovery hint instead of improvising or claiming completion.

Do not invent an unapproved fallback. If the primary package path cannot land, classify why, identify whether an INDEX-declared fallback applies, and record whether the situation should retry, investigate, ask the user, switch to a named fallback, or abort the orchestration. If your package might be an independent merge candidate, state why it is independent and provide standalone verification; otherwise say it should be discarded if the main plan fails.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- For blockers, classify the failure as one of: `capability-gap`, `invalid-requirement`, `external-dependency`, `verification-failure`, `merge-conflict`, `design-invalid`, `cost-out-of-scope`, or `unknown-needs-investigation`.
- If this package was retried or previously blocked, inspect `state.tsv`, the package status file, and `status/events.jsonl` before editing. Carry forward the recorded `last_error`, `failed_command`, `conflict_files`, `log_summary`, and `recovery_hint` into your diagnosis.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh mark-state 04-runtime-metrics completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh mark-state 04-runtime-metrics blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh advance --from 04-runtime-metrics
```

---

## Package: 99-finalize - Integration & Finalize

This package is auto-launched when all functional packages complete. Do not manually start it.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/packages/99-finalize.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/status/99-finalize.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh scratch-path 99-finalize`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh

Read INDEX, graph, all package docs, all status files, and `state.tsv`. Run `bash launchers/orchestrate.sh verify-finalize` before merging. Verify every functional package, check Landing Strategy, create/update integration branch, merge in order, run integration verification, merge to mainline, write FINAL_REPORT.md.

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/perf-monitoring-gap-orchestration/launchers/orchestrate.sh advance --from 99-finalize
```

---
