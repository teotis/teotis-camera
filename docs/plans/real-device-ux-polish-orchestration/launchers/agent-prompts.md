# Agent Prompts

## Package: 00-mode-entry-visibility - Mode Entry Visibility

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/00-mode-entry-visibility.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/00-mode-entry-visibility.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Task: restore visible Humanistic and Portrait mode entrances through the existing catalog / bottom mode bar / availability filtering chain. Use the existing Mode Plugin -> Session state -> app render model flow; do not create a UI-only hidden mode list.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh advance --from 00-mode-entry-visibility
```

If your agent platform cannot run local shell commands, report: `completed but advance not run`, and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 03-quick-panel-outside-dismiss - Quick Panel Outside Dismiss

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/03-quick-panel-outside-dismiss.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/03-quick-panel-outside-dismiss.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Task: make taps outside the expanded Quick panel close Quick without triggering unintended capture/focus/mode actions, while preserving taps inside Quick controls.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh advance --from 03-quick-panel-outside-dismiss
```

If your agent platform cannot run local shell commands, report: `completed but advance not run`, and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 05-dev-log-storage-governance - Dev Log Storage Governance

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/05-dev-log-storage-governance.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/05-dev-log-storage-governance.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Task: add a 20MB total cap for exported Dev logs and one-tap cleanup by type, without deleting anything outside the app debug-log directory and without blocking camera startup.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh advance --from 05-dev-log-storage-governance
```

If your agent platform cannot run local shell commands, report: `completed but advance not run`, and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 01-style-copy-noise-cleanup - Style Copy Noise Cleanup

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/01-style-copy-noise-cleanup.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/01-style-copy-noise-cleanup.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Task: rename user-facing “镜头” to “风格” where it means Style, and remove meaningless selected-filter copy/actions such as “调整所选” and “打开可编辑的自定义副本”. Keep filter selection and custom profile behavior intact.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh advance --from 01-style-copy-noise-cleanup
```

If your agent platform cannot run local shell commands, report: `completed but advance not run`, and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 02-settings-third-level-navigation - Settings Third-Level Navigation

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/02-settings-third-level-navigation.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/02-settings-third-level-navigation.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Task: make Settings > Photo > Portrait and Settings > Photo > Watermark immediately route to their intended third-level interfaces, with tab/content/back behavior synchronized.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh advance --from 02-settings-third-level-navigation
```

If your agent platform cannot run local shell commands, report: `completed but advance not run`, and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 04-persistence-reset-unification - Persistence Reset Unification

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/04-persistence-reset-unification.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/04-persistence-reset-unification.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Task: unify automatic memory and Reset behavior for Settings, Style, Color Lab, and Quick through existing persisted settings/store patterns. Avoid scattered defaults and hidden app-local state owners.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh advance --from 04-persistence-reset-unification
```

If your agent platform cannot run local shell commands, report: `completed but advance not run`, and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 99-finalize - Finalize

Copy this prompt into an agent, or let `orchestrate.sh advance/finalize` launch it for Claude Code after all functional packages complete.

---

**Mode**: finalization executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/packages/99-finalize.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/99-finalize.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh`

Run the finalization package exactly as described in `packages/99-finalize.md`: verify evidence, merge functional package branches into integration branch, run integration verification, merge back to mainline only after verification passes, write `FINAL_REPORT.md`, update `status/99-finalize.md` and `state.tsv`, and clean up only recorded local resources after success.

If finalization fails, set status/state to `blocked`, record the failure stage and recovery suggestion, and preserve branches/worktrees.

---
