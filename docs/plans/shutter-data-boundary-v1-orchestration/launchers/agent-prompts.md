# Agent Prompts

## Package: 01-adapter-data-boundary - Adapter Data Boundary

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/packages/01-adapter-data-boundary.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/status/01-adapter-data-boundary.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. Create or reuse the assigned worktree and branch from the package doc. Do not rely on status files inside the worktree; write the coordinator status path above.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/launchers/orchestrate.sh advance --from 01-adapter-data-boundary
```

If your agent platform cannot run local shell commands, report: `completed but advance not run`, and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 02-session-rearm-policy - Session Re-arm Policy

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/packages/02-session-rearm-policy.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/status/02-session-rearm-policy.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. Create or reuse the assigned worktree and branch from the package doc. Do not rely on status files inside the worktree; write the coordinator status path above.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/launchers/orchestrate.sh advance --from 02-session-rearm-policy
```

If your agent platform cannot run local shell commands, report: `completed but advance not run`, and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 03-ui-shutter-gate-and-qa - UI Shutter Gate And QA

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/packages/03-ui-shutter-gate-and-qa.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/status/03-ui-shutter-gate-and-qa.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. Wait for `02-session-rearm-policy` to complete unless the user explicitly overrides automation. Create or reuse the assigned worktree and branch from the package doc. Do not rely on status files inside the worktree; write the coordinator status path above.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/launchers/orchestrate.sh advance --from 03-ui-shutter-gate-and-qa
```

If your agent platform cannot run local shell commands, report: `completed but advance not run`, and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 99-finalize - Finalize

Copy this prompt into an agent only after all functional packages are completed, or let `orchestrate.sh advance/finalize` launch it for Claude Code.

---

**Mode**: finalize executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/packages/99-finalize.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/status/99-finalize.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/launchers/orchestrate.sh`

Run the finalize package exactly as described. Do not force-push, hard reset, delete remote branches, or delete unrecorded resources. On success, write `FINAL_REPORT.md`, update `status/99-finalize.md`, and mark `99-finalize` finalized in state. On failure, mark it blocked and preserve all branches/worktrees.

---
