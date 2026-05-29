# Agent Prompts

## Package: 01-camerax-camera2-signal-feasibility - CameraX/Camera2 Signal Feasibility

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/packages/01-camerax-camera2-signal-feasibility.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/status/01-camerax-camera2-signal-feasibility.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/status/state.tsv`  
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh scratch-path 01-camerax-camera2-signal-feasibility`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh`

Read the INDEX and package doc. You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. Use `rtk` for shell commands. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes. Do not put secrets, tokens, `.env` files, hidden prompts, or authoritative completion evidence in scratch. Anything required for scheduling or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Do not attempt external-assist work. If the work requires a physical device or human-only judgment, mark the package `blocked` with a precise recovery hint.

Before calling `advance`, set coordinator status to `completed` or `blocked`, fill evidence, then update the machine-readable state row only through the orchestrator:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 01-camerax-camera2-signal-feasibility completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 01-camerax-camera2-signal-feasibility blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh advance --from 01-camerax-camera2-signal-feasibility
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 02-current-shutter-timing-tests - Current Shutter Timing Tests

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/packages/02-current-shutter-timing-tests.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/status/02-current-shutter-timing-tests.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/status/state.tsv`  
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh scratch-path 02-current-shutter-timing-tests`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh`

Read the INDEX and package doc. You may edit only allowed paths. Do not edit INDEX.md or another package status file. Use `rtk` for shell commands; in a worktree use `rtk ./scripts/run_isolated_gradle.sh`.

Before calling `advance`, set coordinator status to `completed` or `blocked`, fill evidence, then run one of:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 02-current-shutter-timing-tests completed --commit <commit-sha> --verification "<command: result>"
```

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 02-current-shutter-timing-tests blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh advance --from 02-current-shutter-timing-tests
```

---

## Package: 03-capture-readiness-contract - Capture Readiness Contract

Copy this prompt into an agent, or let `orchestrate.sh advance` launch it for Claude Code after dependencies complete.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/packages/03-capture-readiness-contract.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/status/03-capture-readiness-contract.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/status/state.tsv`  
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh scratch-path 03-capture-readiness-contract`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh`

Read dependency outputs and package doc before editing. You may edit only allowed paths. Use `rtk` for verification.

Completion:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 03-capture-readiness-contract completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh advance --from 03-capture-readiness-contract
```

Blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 03-capture-readiness-contract blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

---

## Package: 04-adapter-earliest-ready-signal - Adapter Earliest Ready Signal

Copy this prompt into an agent, or let `orchestrate.sh advance` launch it for Claude Code after dependencies complete.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/packages/04-adapter-earliest-ready-signal.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/status/04-adapter-earliest-ready-signal.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/status/state.tsv`  
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh scratch-path 04-adapter-earliest-ready-signal`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh`

Read Package 01 and 03 evidence before editing. Implement the earliest safe milestone, or explicitly wire the honest fallback. Use `rtk` for verification.

Completion:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 04-adapter-earliest-ready-signal completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh advance --from 04-adapter-earliest-ready-signal
```

Blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 04-adapter-earliest-ready-signal blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

---

## Package: 05-shutter-sound-and-visible-rearm - Shutter Sound And Visible Rearm

Copy this prompt into an agent, or let `orchestrate.sh advance` launch it for Claude Code after dependencies complete.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/packages/05-shutter-sound-and-visible-rearm.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/status/05-shutter-sound-and-visible-rearm.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/status/state.tsv`  
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh scratch-path 05-shutter-sound-and-visible-rearm`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh`

Read Package 03 and 04 evidence before editing. Move readiness sound away from `activeShot` and keep visual loading honest. Use `rtk` for verification.

Completion:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 05-shutter-sound-and-visible-rearm completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh advance --from 05-shutter-sound-and-visible-rearm
```

Blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 05-shutter-sound-and-visible-rearm blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

---

## Package: 06-real-device-timing-protocol - Real Device Timing Protocol

Copy this prompt into an agent, or let `orchestrate.sh advance` launch it for Claude Code after dependencies complete.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/packages/06-real-device-timing-protocol.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/status/06-real-device-timing-protocol.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/status/state.tsv`  
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh scratch-path 06-real-device-timing-protocol`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh`

Prepare the real-device protocol and APK/log evidence path. Do not claim device PASS. Use `rtk` for local build.

Completion:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 06-real-device-timing-protocol completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh advance --from 06-real-device-timing-protocol
```

Blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 06-real-device-timing-protocol blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

---

## Package: 99-finalize - Finalize

Copy this prompt into an agent only after all functional packages are completed, or let `orchestrate.sh advance/finalize` launch it.

---

**Mode**: finalizer  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/packages/99-finalize.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/status/99-finalize.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh`

Run `verify-finalize`, merge package branches in the INDEX order, run integration verification, merge back to main only after verification passes and external gates are satisfied or explicitly deferred, write `FINAL_REPORT.md`, update status, then clean only recorded resources after success.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh advance --from 99-finalize
```
