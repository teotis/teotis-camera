# Agent Prompts

## Package: 01-zoom-slider-render-latch - Zoom Slider Render Latch

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/packages/01-zoom-slider-render-latch.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/status/01-zoom-slider-render-latch.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/launchers/orchestrate.sh advance --from 01-zoom-slider-render-latch
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 02-brightness-dispatch-and-latch - Brightness Dispatch And Latch

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/packages/02-brightness-dispatch-and-latch.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/status/02-brightness-dispatch-and-latch.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/launchers/orchestrate.sh advance --from 02-brightness-dispatch-and-latch
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 03-pinch-zoom-basis-repair - Pinch Zoom Basis Repair

Copy this prompt into an agent, or let `orchestrate.sh advance` launch it after package 01 completes.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/packages/03-pinch-zoom-basis-repair.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/status/03-pinch-zoom-basis-repair.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. Wait for `01-zoom-slider-render-latch` to be completed unless the user explicitly overrides automation.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/launchers/orchestrate.sh advance --from 03-pinch-zoom-basis-repair
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 04-integration-verification-and-smoke - Integration Verification And Smoke

Copy this prompt into an agent, or let `orchestrate.sh advance` launch it after packages 01-03 complete.

---

**Mode**: package verifier
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/packages/04-integration-verification-and-smoke.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/status/04-integration-verification-and-smoke.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit `INDEX.md` or another package status file. Wait for packages 01-03 to be completed unless the user explicitly overrides automation.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/launchers/orchestrate.sh advance --from 04-integration-verification-and-smoke
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 99-finalize - Finalize

Copy this prompt into an agent only after all functional packages complete, or let `orchestrate.sh advance/finalize` launch it.

---

**Mode**: finalize executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/packages/99-finalize.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/status/99-finalize.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/launchers/orchestrate.sh`

Run the finalize package exactly as described. Preserve branches/worktrees on failure. Never force-push or hard reset.

---

