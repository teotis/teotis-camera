# Agent Prompts

## Package: 01-photo-night-still - Photo And Night Still Mode Direct Tests

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/packages/01-photo-night-still.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/status/01-photo-night-still.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/launchers/orchestrate.sh`

You are implementing direct unit tests for `PhotoModePlugin` and `NightModePlugin`.

Before any command, read `AGENTS.md`, `/Users/dingren/.codex/RTK.md`, the INDEX, and your package doc. Use `rtk` for all shell commands. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. Do not change production code to make tests pass. If a production seam is needed, mark this package `blocked`.

Before calling `advance`, you must:

- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/launchers/orchestrate.sh advance --from 01-photo-night-still
```

If your agent platform cannot run local shell commands, report: `completed but advance not run`, and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 02-document-video-specialized - Document And Video Specialized Mode Direct Tests

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/packages/02-document-video-specialized.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/status/02-document-video-specialized.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/launchers/orchestrate.sh`

You are implementing direct unit tests for `DocumentModePlugin` and `VideoModePlugin`.

Before any command, read `AGENTS.md`, `/Users/dingren/.codex/RTK.md`, the INDEX, and your package doc. Use `rtk` for all shell commands. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. Do not change production code to make tests pass. If a production seam is needed, mark this package `blocked`.

Before calling `advance`, you must:

- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/launchers/orchestrate.sh advance --from 02-document-video-specialized
```

If your agent platform cannot run local shell commands, report: `completed but advance not run`, and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 03-portrait-pro-humanistic - Portrait Pro Humanistic Variant Direct Tests

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/packages/03-portrait-pro-humanistic.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/status/03-portrait-pro-humanistic.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/launchers/orchestrate.sh`

You are implementing direct unit tests for `PortraitModePlugin`, `ProModePlugin`, and `HumanisticModePlugin`.

Before any command, read `AGENTS.md`, `/Users/dingren/.codex/RTK.md`, the INDEX, and your package doc. Use `rtk` for all shell commands. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. Do not change production code to make tests pass. If a production seam is needed, mark this package `blocked`.

Before calling `advance`, you must:

- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/launchers/orchestrate.sh advance --from 03-portrait-pro-humanistic
```

If your agent platform cannot run local shell commands, report: `completed but advance not run`, and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 99-finalize - Finalize Feature Module Direct Tests

Copy this prompt into an agent only after all functional packages are completed, or let `orchestrate.sh advance/finalize` launch it for Claude Code.

---

**Mode**: finalize executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/packages/99-finalize.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/status/99-finalize.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/launchers/orchestrate.sh`

You are the finalizer. Read INDEX, graph, all package docs, all status files, and `state.tsv`. Verify every functional package, create/update the integration branch, merge package branches in the configured order, run integration verification, merge back to `main` only after verification passes, write `FINAL_REPORT.md` and `status/99-finalize.md`, and clean up only recorded local package branches/worktrees after full success.

Use `rtk` for all shell commands. Do not force-push, hard reset, delete remote branches, or delete unrecorded resources. On any failure, set `99-finalize` to `blocked`, record the failure stage, preserve all worktrees/branches, and stop.

Tail step after status is recorded:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/launchers/orchestrate.sh status
```

---
