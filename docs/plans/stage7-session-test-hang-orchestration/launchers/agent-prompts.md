# Agent Prompts

## Package: 01-session-test-hang-diagnosis-and-repair - Session Test Hang Diagnosis And Repair

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/packages/01-session-test-hang-diagnosis-and-repair.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/status/01-session-test-hang-diagnosis-and-repair.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/launchers/orchestrate.sh advance --from 01-session-test-hang-diagnosis-and-repair
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 99-finalize - Finalize

Copy this prompt into an agent only after all functional packages are completed, or let `orchestrate.sh advance/finalize` launch it for Claude Code.

---

**Mode**: finalize executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/packages/99-finalize.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/status/99-finalize.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/launchers/orchestrate.sh`

Run the finalize package exactly as written. Do not run if any functional package is not completed. Preserve branches/worktrees on failure. On success, write `FINAL_REPORT.md`, mark `99-finalize` as `finalized`, and clean up only resources recorded by this orchestration.

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/launchers/orchestrate.sh status
```

---
