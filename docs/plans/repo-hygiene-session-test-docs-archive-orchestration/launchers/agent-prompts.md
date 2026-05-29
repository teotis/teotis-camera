# Agent Prompts

Use these prompts manually, or let `launchers/orchestrate.sh start/advance` launch Claude Code background agents. Every package must write coordinator status to the absolute status path below, not to a copied file inside its implementation worktree.

## Package: 01-worktree-inventory-and-safety-plan - Worktree Inventory And Safety Plan

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/packages/01-worktree-inventory-and-safety-plan.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/01-worktree-inventory-and-safety-plan.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/state.tsv`  
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh scratch-path 01-worktree-inventory-and-safety-plan`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh`

Read `AGENTS.md`, `/Users/dingren/.codex/RTK.md`, the INDEX, and your package doc. Use `rtk` for all shell commands. You may edit only the allowed paths in the package doc. This package is read-only with respect to existing worktrees: do not delete, move, prune, or repair any worktree.

Before calling `advance`, set coordinator status to `completed` or `blocked`, fill evidence, then update the machine ledger only through:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh mark-state 01-worktree-inventory-and-safety-plan completed --commit <commit-sha> --verification "<commands: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh mark-state 01-worktree-inventory-and-safety-plan blocked --error "<specific blocker>" --failed-command "<failed command>" --log-summary "<short log summary>" --recovery-hint "<next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh advance --from 01-worktree-inventory-and-safety-plan
```

---

## Package: 02-session-test-fixture-recovery-split - Session Test Fixture And Recovery Split

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/packages/02-session-test-fixture-recovery-split.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/02-session-test-fixture-recovery-split.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/state.tsv`  
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh scratch-path 02-session-test-fixture-recovery-split`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh`

Read `AGENTS.md`, `/Users/dingren/.codex/RTK.md`, the INDEX, and your package doc. Use `rtk` for all shell commands and `rtk ./scripts/run_isolated_gradle.sh` for Gradle in a package worktree. Edit only allowed paths. Do not change production session code to make a test split pass.

Before calling `advance`, fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, baseline failures if any, and risks.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh mark-state 02-session-test-fixture-recovery-split completed --commit <commit-sha> --verification "<commands: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh advance --from 02-session-test-fixture-recovery-split
```

If blocked, use `mark-state 02-session-test-fixture-recovery-split blocked ...` with exact failed command and recovery hint.

---

## Package: 03-docs-plan-taxonomy-and-retention - Docs Plan Taxonomy And Retention

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/packages/03-docs-plan-taxonomy-and-retention.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/03-docs-plan-taxonomy-and-retention.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/state.tsv`  
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh scratch-path 03-docs-plan-taxonomy-and-retention`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh`

Read the INDEX and package doc. Create policy/inventory only; do not move or delete plans in this package. Preserve `docs/plans/` as canonical planning home.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh mark-state 03-docs-plan-taxonomy-and-retention completed --commit <commit-sha> --verification "<commands: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh advance --from 03-docs-plan-taxonomy-and-retention
```

If blocked, use `mark-state 03-docs-plan-taxonomy-and-retention blocked ...`.

---

## Package: manual-worktree-cleanup-approval - Manual Worktree Cleanup Approval

---

**Mode**: manual gate  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/packages/manual-worktree-cleanup-approval.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/manual-worktree-cleanup-approval.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh`

Review `output/01-worktree-inventory-and-safety-plan/cleanup-candidates.tsv`. Create either `output/manual-worktree-cleanup-approval/approved-cleanup.tsv` with exact approved paths/actions, or `output/manual-worktree-cleanup-approval/cleanup-deferred.md`. Do not delete anything in this gate.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh mark-state manual-worktree-cleanup-approval completed --verification "manual cleanup approval or deferral recorded"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh advance --from manual-worktree-cleanup-approval
```

---

## Package: 04-session-test-capture-mode-split - Session Test Capture And Mode Split

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/packages/04-session-test-capture-mode-split.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/04-session-test-capture-mode-split.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/state.tsv`  
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh scratch-path 04-session-test-capture-mode-split`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh`

Start only after package `02` is completed. Use isolated Gradle. Edit only allowed session test files and keep production code untouched.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh mark-state 04-session-test-capture-mode-split completed --commit <commit-sha> --verification "<commands: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh advance --from 04-session-test-capture-mode-split
```

If blocked, use `mark-state 04-session-test-capture-mode-split blocked ...`.

---

## Package: 05-docs-archive-pilot - Docs Archive Pilot

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/packages/05-docs-archive-pilot.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/05-docs-archive-pilot.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/state.tsv`  
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh scratch-path 05-docs-archive-pilot`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh`

Start only after package `03` is completed. Move only a small low-risk pilot batch listed by package `03`, using `git mv`. Do not move active orchestration directories.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh mark-state 05-docs-archive-pilot completed --commit <commit-sha> --verification "<commands: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh advance --from 05-docs-archive-pilot
```

If blocked, use `mark-state 05-docs-archive-pilot blocked ...`.

---

## Package: 06-session-test-device-presentation-split - Session Test Device, Presentation, Document, Zoom, And Readiness Split

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/packages/06-session-test-device-presentation-split.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/06-session-test-device-presentation-split.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/state.tsv`  
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh scratch-path 06-session-test-device-presentation-split`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh`

Start only after package `04` is completed. Use isolated Gradle. Do not edit production code. Preserve or document any pre-existing test failures.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh mark-state 06-session-test-device-presentation-split completed --commit <commit-sha> --verification "<commands: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh advance --from 06-session-test-device-presentation-split
```

If blocked, use `mark-state 06-session-test-device-presentation-split blocked ...`.

---

## Package: 07-worktree-cleanup-execution - Worktree Cleanup Execution

---

**Mode**: package executor  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/packages/07-worktree-cleanup-execution.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/07-worktree-cleanup-execution.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/state.tsv`  
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh scratch-path 07-worktree-cleanup-execution`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh`

Start only after the manual approval gate is completed. If cleanup is deferred, record the deferral and do not delete anything. If approved, delete only exact approved paths/actions from the approval TSV.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh mark-state 07-worktree-cleanup-execution completed --commit <commit-sha> --verification "<commands: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh advance --from 07-worktree-cleanup-execution
```

If blocked, use `mark-state 07-worktree-cleanup-execution blocked ...`.

---

## Package: 99-finalize - Finalize

---

**Mode**: finalizer  
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/INDEX.md`  
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/packages/99-finalize.md`  
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/99-finalize.md`  
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/status/state.tsv`  
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh`

Read INDEX, graph, all package docs, all status files, and `state.tsv`. Verify every functional package, create/update the integration branch, merge package branches in configured order, run integration verification, merge back to `main` only after verification passes and cleanup approval/defer state is explicit, write `FINAL_REPORT.md` and `status/99-finalize.md`, then clean up only recorded local package branches/worktrees after full success.

Use `rtk` for all shell commands. Do not force-push, hard reset, delete remote branches, or delete unrecorded resources. On any failure, set `99-finalize` to `blocked`, record failure stage, preserve all worktrees/branches, and stop.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh verify-finalize
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh status
```

---
