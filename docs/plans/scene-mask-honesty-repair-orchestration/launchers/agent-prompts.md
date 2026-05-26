# Agent Prompts

## Package: 01-saved-photo-writeback-honesty - Saved Photo Writeback Honesty

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/packages/01-saved-photo-writeback-honesty.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/status/01-saved-photo-writeback-honesty.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/launchers/orchestrate.sh advance --from 01-saved-photo-writeback-honesty
```

If your agent platform cannot run local shell commands, report: `completed but advance not run`, and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 02-preview-analysis-budget-contract - Preview Analysis Budget Contract

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/packages/02-preview-analysis-budget-contract.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/status/02-preview-analysis-budget-contract.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/launchers/orchestrate.sh advance --from 02-preview-analysis-budget-contract
```

If your agent platform cannot run local shell commands, report: `completed but advance not run`, and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 03-scene-mask-verification-gate - Scene Mask Verification Gate

Copy this prompt into an agent, or let `orchestrate.sh advance` launch it for Claude Code.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/packages/03-scene-mask-verification-gate.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/status/03-scene-mask-verification-gate.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/launchers/orchestrate.sh advance --from 03-scene-mask-verification-gate
```

If your agent platform cannot run local shell commands, report: `completed but advance not run`, and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 99-finalize - Finalize

Copy this prompt into an agent, or let `orchestrate.sh advance/finalize` launch it for Claude Code.

---

**Mode**: finalize executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/packages/99-finalize.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/status/99-finalize.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/launchers/orchestrate.sh`

You are the finalize agent. Read all package docs, all status files, `package-graph.tsv`, and `state.tsv`. Verify every functional package evidence pack. If allowed, create/update the integration branch, merge recorded package branches, run integration verification, merge verified integration back to `main`, write `FINAL_REPORT.md` and `status/99-finalize.md`, update `state.tsv`, and clean up only recorded local branches/worktrees after every prior step succeeds. Preserve all resources on failure.

Do not force-push, hard reset, delete remote branches, delete unrecorded resources, or edit functional package status files.

---

