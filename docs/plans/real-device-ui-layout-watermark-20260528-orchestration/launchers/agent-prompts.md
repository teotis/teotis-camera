# Agent Prompts

## Package: 01-preview-frame-containment - Preview Frame Containment

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/packages/01-preview-frame-containment.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/status/01-preview-frame-containment.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh scratch-path 01-preview-frame-containment`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch. Anything required for scheduling, completion, or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Before calling `advance`, you must:

- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- If this package was retried or previously blocked, inspect `state.tsv`, the package status file, and `status/events.jsonl` before editing. Carry forward the recorded `last_error`, `failed_command`, `conflict_files`, `log_summary`, and `recovery_hint` into your diagnosis.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh mark-state 01-preview-frame-containment completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh mark-state 01-preview-frame-containment blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh advance --from 01-preview-frame-containment
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 03-quick-watermark-cycle - Quick Watermark Cycle

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/packages/03-quick-watermark-cycle.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/status/03-quick-watermark-cycle.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh scratch-path 03-quick-watermark-cycle`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch. Anything required for scheduling, completion, or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Before calling `advance`, you must:

- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- If this package was retried or previously blocked, inspect `state.tsv`, the package status file, and `status/events.jsonl` before editing. Carry forward the recorded `last_error`, `failed_command`, `conflict_files`, `log_summary`, and `recovery_hint` into your diagnosis.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh mark-state 03-quick-watermark-cycle completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh mark-state 03-quick-watermark-cycle blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh advance --from 03-quick-watermark-cycle
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 02-bottom-cockpit-density - Bottom Cockpit Density

Copy this prompt into an agent after `01-preview-frame-containment` completes, or let `orchestrate.sh advance` launch it.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/packages/02-bottom-cockpit-density.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/status/02-bottom-cockpit-density.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh scratch-path 02-bottom-cockpit-density`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch. Anything required for scheduling, completion, or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Before calling `advance`, you must:

- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- If this package was retried or previously blocked, inspect `state.tsv`, the package status file, and `status/events.jsonl` before editing. Carry forward the recorded `last_error`, `failed_command`, `conflict_files`, `log_summary`, and `recovery_hint` into your diagnosis.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh mark-state 02-bottom-cockpit-density completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh mark-state 02-bottom-cockpit-density blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh advance --from 02-bottom-cockpit-density
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 04-real-device-acceptance - Real Device Acceptance

Copy this prompt only after all implementation packages complete. This package is marked manual in the graph because it requires device/screenshot evidence.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/packages/04-real-device-acceptance.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/status/04-real-device-acceptance.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh scratch-path 04-real-device-acceptance`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch. Anything required for scheduling, completion, or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Before calling `advance`, you must:

- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash if any, APK path, device evidence, verification commands/results, risks.
- If this package was retried or previously blocked, inspect `state.tsv`, the package status file, and `status/events.jsonl` before editing. Carry forward the recorded `last_error`, `failed_command`, `conflict_files`, `log_summary`, and `recovery_hint` into your diagnosis.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh mark-state 04-real-device-acceptance completed --commit <commit-sha-or-none> --verification "<command/device smoke: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh mark-state 04-real-device-acceptance blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh advance --from 04-real-device-acceptance
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 99-finalize - Finalize

Copy this prompt only after all functional packages are completed, or let `orchestrate.sh advance/finalize` launch it.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/packages/99-finalize.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/status/99-finalize.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh scratch-path 99-finalize`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc and perform the finalize operations authorized by the INDEX. Do not edit functional package statuses except to report an invalid mismatch through the ledger.

Before merging, run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh verify-finalize
```

For success:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh mark-state 99-finalize finalized --verification "<integration verification summary>" --integration "<integration branch and mainline merge>" --cleanup "<cleanup result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh mark-state 99-finalize blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh advance --from 99-finalize
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh status
```

---
