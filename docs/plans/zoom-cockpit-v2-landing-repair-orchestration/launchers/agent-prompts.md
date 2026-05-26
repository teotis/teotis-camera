# Agent Prompts

## Package: 01-session-recording-zoom-policy - Session Recording Zoom Policy

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/packages/01-session-recording-zoom-policy.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/status/01-session-recording-zoom-policy.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before calling `advance`, you must set coordinator status to `completed` or `blocked`, fill evidence, and update the machine-readable state row consistently.

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/launchers/orchestrate.sh advance --from 01-session-recording-zoom-policy
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/launchers/orchestrate.sh advance
```
---

## Package: 02-slider-render-contract-reconciliation - Slider Render Contract Reconciliation

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/packages/02-slider-render-contract-reconciliation.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/status/02-slider-render-contract-reconciliation.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before calling `advance`, you must set coordinator status to `completed` or `blocked`, fill evidence, and update the machine-readable state row consistently.

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/launchers/orchestrate.sh advance --from 02-slider-render-contract-reconciliation
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/launchers/orchestrate.sh advance
```
---

## Package: 03-orchestration-ledger-repair - Orchestration Ledger Repair

Copy this prompt into an agent after packages 01 and 02 complete, or let `orchestrate.sh advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/packages/03-orchestration-ledger-repair.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/status/03-orchestration-ledger-repair.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before calling `advance`, you must set coordinator status to `completed` or `blocked`, fill evidence, and update the machine-readable state row consistently.

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/launchers/orchestrate.sh advance --from 03-orchestration-ledger-repair
```
---

## Package: 99-finalize - Finalize

Copy this prompt into an agent after all functional packages complete, or let `orchestrate.sh advance/finalize` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/packages/99-finalize.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/status/99-finalize.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file.

Before calling `advance`, you must set coordinator status to `finalized` or `blocked`, fill evidence, and update the machine-readable state row consistently.

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/launchers/orchestrate.sh advance --from 99-finalize
```
---

