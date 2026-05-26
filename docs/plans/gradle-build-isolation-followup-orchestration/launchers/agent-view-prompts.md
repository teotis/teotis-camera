# Agent View Prompts

Use these prompts in Claude Code Agent View if you choose manual dispatch instead of the background launcher. This package is updated for Claude Code `2.1.142`: use `dispatch-claude-agents.sh` for automatic `claude --bg --name` package sessions, and use `claude agents` to monitor or manually dispatch. The default permission mode is `default`; do not use `auto` unless the user has already opted in interactively with `claude --permission-mode auto` or configured user-level Claude Code settings.

Open Agent View from the repo root with:

```bash
claude agents --cwd /Volumes/Extreme_SSD/project/open_camera --permission-mode default --effort xhigh
```

Packages 01 and 02 may be launched in parallel. Run the Codex final audit only after both status files are completed.

## Package: 01-stage-script-isolation — Stage Script Isolation

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/gradle-build-isolation-followup-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/gradle-build-isolation-followup-orchestration/packages/01-stage-script-isolation.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/gradle-build-isolation-followup-orchestration/status/01-stage-script-isolation.md`

**File ownership**: you may edit `scripts/run_isolated_gradle.sh`, `scripts/verify_stage_6b0_settings_foundation.sh`, `scripts/verify_stage_6b1_mode_catalog.sh`, `scripts/verify_stage_6b2_filter_lab.sh`, and `scripts/verify_stage_6b3_watermark_v2.sh`. Do NOT touch docs, runtime app/core Kotlin files, this INDEX, or another package's status file.
**Dependencies**: none.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or make product/runtime camera changes -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file, not INDEX.md. Include worktree path, branch, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---

## Package: 02-ledger-and-rules-restoration — Ledger And Rules Restoration

Copy the block below into Claude Code Agent View.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/gradle-build-isolation-followup-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/gradle-build-isolation-followup-orchestration/packages/02-ledger-and-rules-restoration.md`
**Status file**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/gradle-build-isolation-followup-orchestration/status/02-ledger-and-rules-restoration.md`

**File ownership**: you may edit `AGENTS.md`, `codex/documentation.md`, `docs/plans/INDEX.md`, and `docs/plans/2026-05-26-agent-handoff-gradle-isolation-index.md`. Do NOT edit scripts, runtime app/core Kotlin files, this INDEX, or another package's status file.
**Dependencies**: none, but read package 01 so docs do not contradict the script plan.

**Stop gates**: force-push, hard reset, delete worktree, expand scope, touch forbidden paths, or claim all scripts are fixed before package 01 lands -> stop and ask.

**Evidence pack**: when done, write completion evidence to your status file, not INDEX.md. Include worktree path, branch, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths.

---
