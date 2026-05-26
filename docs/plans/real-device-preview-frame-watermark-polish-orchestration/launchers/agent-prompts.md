# Agent Prompts

## Package: 01-preview-frame-contract - Preview Frame Contract

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/packages/01-preview-frame-contract.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/status/01-preview-frame-contract.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/status/state.tsv
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/launchers/orchestrate.sh advance --from 01-preview-frame-contract
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/launchers/orchestrate.sh advance
```
---

## Package: 02-natural-blur-border-rendering - Natural Blur Border Rendering

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/packages/02-natural-blur-border-rendering.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/status/02-natural-blur-border-rendering.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/status/state.tsv
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/launchers/orchestrate.sh advance --from 02-natural-blur-border-rendering
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/launchers/orchestrate.sh advance
```
---

## Package: 99-finalize - Integration, Merge, and Finalize

Copy this prompt into an agent, or let `orchestrate.sh advance` launch it when all functional packages are completed.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/packages/99-finalize.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/status/99-finalize.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/status/state.tsv
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before calling `advance`, you must:
- Set coordinator status to `finalized` or `blocked`.
- Fill evidence: integration branch, mainline merge commit, verification summary, cleanup results.
- Update the machine-readable state row consistently.

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/launchers/orchestrate.sh advance --from 99-finalize
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/launchers/orchestrate.sh advance
```
---
