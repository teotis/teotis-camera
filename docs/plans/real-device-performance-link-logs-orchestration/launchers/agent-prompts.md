# Agent Prompts

## Package: 01-performance-timing-contract - Performance Timing Contract

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/packages/01-performance-timing-contract.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/status/01-performance-timing-contract.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh scratch-path 01-performance-timing-contract`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, raw frame/media data, or authoritative completion evidence in scratch.

Run shell commands through `rtk`. In a package worktree, run Gradle with `rtk ./scripts/run_isolated_gradle.sh ...`.

Do not attempt external-assist work inside this package. If the work requires a physical device, user-owned account, secret, external approval, or human-only judgment, mark the package `blocked` with a precise recovery hint.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh mark-state 01-performance-timing-contract completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh mark-state 01-performance-timing-contract blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh advance --from 01-performance-timing-contract
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 02-core-flow-instrumentation - Core Flow Instrumentation

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/packages/02-core-flow-instrumentation.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/status/02-core-flow-instrumentation.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh scratch-path 02-core-flow-instrumentation`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. Use coordinator status/state paths above, even from a worktree.

Use scratch only for temporary shared notes. Do not put credentials, private keys, raw media/frame data, hidden prompts, or authoritative evidence in scratch.

Run shell commands through `rtk`. In a package worktree, run Gradle with `rtk ./scripts/run_isolated_gradle.sh ...`.

Before calling `advance`, inspect `state.tsv`, your status file, and `status/events.jsonl` if this package was retried or previously blocked. Preserve prior recovery context in your diagnosis.

Update state through:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh mark-state 02-core-flow-instrumentation completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh mark-state 02-core-flow-instrumentation blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh advance --from 02-core-flow-instrumentation
```

If shell commands are unavailable, report "completed but advance not run" and ask the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 03-dev-link-log-export - Dev Link Log Export

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/packages/03-dev-link-log-export.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/status/03-dev-link-log-export.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh scratch-path 03-dev-link-log-export`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. Use coordinator status/state paths above, even from a worktree.

Do not attempt real-device testing. Do not export or persist raw media/frame data, secrets, credentials, hidden prompts, or private user material.

Run shell commands through `rtk`. In a package worktree, run Gradle with `rtk ./scripts/run_isolated_gradle.sh ...`.

Before calling `advance`, fill the coordinator status evidence including an exported link-log sample and test results.

Update state through:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh mark-state 03-dev-link-log-export completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh mark-state 03-dev-link-log-export blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh advance --from 03-dev-link-log-export
```

If shell commands are unavailable, report "completed but advance not run" and ask the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 04-real-device-log-analysis-protocol - Real-Device Log Analysis Protocol

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/packages/04-real-device-log-analysis-protocol.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/status/04-real-device-log-analysis-protocol.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh scratch-path 04-real-device-log-analysis-protocol`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. Use coordinator status/state paths above, even from a worktree.

This package prepares the protocol only. Do not claim real-device QA passed. Do not add private device media, credentials, hidden prompts, or raw logs containing secrets.

Run shell commands through `rtk`. In a package worktree, run Gradle with `rtk ./scripts/run_isolated_gradle.sh ...`.

Before calling `advance`, fill the coordinator status evidence including protocol path, APK/install command, verification results, and remaining external-assist evidence.

Update state through:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh mark-state 04-real-device-log-analysis-protocol completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh mark-state 04-real-device-log-analysis-protocol blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh advance --from 04-real-device-log-analysis-protocol
```

If shell commands are unavailable, report "completed but advance not run" and ask the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 99-finalize - Finalize

Copy this prompt into an agent, or let `orchestrate.sh advance/finalize` launch it for Claude Code.

---

**Mode**: finalizer
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/packages/99-finalize.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/status/99-finalize.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh scratch-path 99-finalize`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh`

Read the package doc and perform the finalize steps exactly. Do not run `99-finalize` if any functional package is not completed. Do not force-push, hard reset, delete remote branches, delete unrecorded worktrees/branches, or claim real-device performance QA passed without exported device evidence.

Start with:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh verify-finalize
```

On success, merge package branches in the documented order, run integration verification, write `FINAL_REPORT.md`, update `status/99-finalize.md`, mark finalized through the orchestrator, and clean up only recorded resources after all previous steps succeed.

On failure, preserve branches/worktrees and mark blocked:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh mark-state 99-finalize blocked \
  --error "<specific failure>" \
  --failed-command "<failed command>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<decisive log lines>" \
  --recovery-hint "<next action>"
```

Tail step, only after final status is recorded:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh advance --from 99-finalize
```
