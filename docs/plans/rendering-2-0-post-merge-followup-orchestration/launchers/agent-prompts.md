# Agent Prompts

## Package: 01-app-unit-test-gate-cleanup - App Unit Test Gate Cleanup

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/packages/01-app-unit-test-gate-cleanup.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/status/01-app-unit-test-gate-cleanup.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/launchers/orchestrate.sh advance --from 01-app-unit-test-gate-cleanup
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 02-ledger-status-reconciliation - Ledger Status Reconciliation

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/packages/02-ledger-status-reconciliation.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/status/02-ledger-status-reconciliation.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/launchers/orchestrate.sh advance --from 02-ledger-status-reconciliation
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 99-finalize - Finalize

Copy this prompt into an agent only after the orchestrator launches it, or run `orchestrate.sh finalize` after all functional packages are completed.

---

**Mode**: finalize executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/packages/99-finalize.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/status/99-finalize.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/status/state.tsv`
**Final report**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/FINAL_REPORT.md`

Run the finalize package exactly as documented. Do not start if any functional package is not completed. Stop and record `blocked` on conflict, verification failure, incomplete evidence, or forbidden-path edits. Clean up only branches/worktrees recorded by this orchestration after all merge and verification steps succeed.

---
