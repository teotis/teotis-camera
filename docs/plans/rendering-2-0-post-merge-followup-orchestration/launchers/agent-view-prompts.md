# Agent View Prompts

## Package: 01-app-unit-test-gate-cleanup - App Unit Test Gate Cleanup

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/packages/01-app-unit-test-gate-cleanup.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/status/01-app-unit-test-gate-cleanup.md`

**File ownership**: you may edit the app preview geometry test files and the narrow app preview geometry helpers listed in the package doc. Do NOT touch docs/status ledgers, core/effect, camera capture code, or unrelated tests.
**Dependencies**: none.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or change runtime rendering behavior beyond a necessary existing helper contract -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file, not `INDEX.md`. Include worktree path, branch, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and a self-certification that you only touched allowed paths.

---

## Package: 02-ledger-status-reconciliation - Ledger Status Reconciliation

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/packages/02-ledger-status-reconciliation.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/status/02-ledger-status-reconciliation.md`

**File ownership**: you may edit the Rendering 2.0 docs and status ledgers listed in the package doc. Do NOT touch runtime source, tests, or another package status file.
**Dependencies**: none, but phrase package 01 state as pending/blocked unless there is evidence it has completed.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, claim product completion without evidence, or claim real-device validation without evidence -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file, not `INDEX.md`. Include worktree path, branch, git diff --stat, changed files, commands run, docs reviewed, unresolved risks, and a self-certification that you only touched allowed paths.

---
