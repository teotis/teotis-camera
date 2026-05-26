# Gradle Build Isolation Follow-Up — Orchestration Index

## Goal

Finish the build-isolation hardening after the external-agent repair. The current Gradle override and `run_isolated_gradle.sh` wrapper are valid, but old verification scripts can still clean or compile against the shared `~/.codex-build/OpenCamera` root, and the planning ledger needs a durable record of the accepted fix plus remaining follow-up.

## Execution Mode Recommendation

- Recommended mode: BACKGROUND_AGENT_SCRIPT
- Why: The remaining work splits cleanly into two independently launchable packages, and the current Claude Code `2.1.142` setup supports `claude --bg` background sessions plus `claude agents` for monitoring. The launcher omits `--permission-mode` by default so each task inherits the user's Claude Code settings; bypass permissions must be enabled by the user, not silently granted by this repo.
- Alternatives rejected: SINGLE_AGENT — possible, but slower than two small focused agents; AGENT_VIEW — still available as the manual fallback, but the user asked for the latest task package and background script is now the preferred launcher; BATCH — not a repo-wide mechanical transform; AGENT_TEAM — unnecessary for implementation.
- Max parallel agents: 2
- Codex-retained work: final integration audit, verification of actual build roots, and decision on whether the external fix is fully accepted.

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:
- Read this index and all referenced package documents.
- Create or reuse an isolated git worktree for implementation.
- Make scope-bounded edits, add/update tests, and update docs as described in your assigned package.
- Run the listed verification commands through `rtk`.
- Commit locally within the worktree branch.
- Merge, push, or create PRs for worktree branches using only incremental, non-destructive operations.
- Write to ONLY your assigned status file under `status/`; never edit this INDEX or another package's status file.

## Stop Gates — Must Ask

STOP and ask the user before:
- Crossing Stage boundaries or changing camera product behavior.
- Making architectural decisions beyond build/verification isolation.
- Destructive git operations: force-push, hard reset, deleting branches/worktrees.
- Network access, external API calls, or adding secrets/credentials.
- Overwriting unrelated dirty changes outside your assigned Allowed Paths.
- Fixing verification failures when the fix expands scope beyond your package.

## Completion Policy

After completing your assigned package:
- Write your evidence pack to `status/<package-id>.md`; do NOT edit this INDEX.
- Merge, push, or create a PR as the final step if your environment is configured for that.
- Report what changed, test results, merge/PR status, and branch/worktree path.
- Do NOT delete the worktree unless explicitly instructed.

## Concurrency Plan

| Group | Packages | Can Run In Parallel | Must Wait For | Conflict Risk |
| --- | --- | --- | --- | --- |
| G1 | 01-stage-script-isolation, 02-ledger-and-rules-restoration | yes | none | low; scripts and docs are separated |
| G2 | 99-integration-audit | no | G1 packages | final consistency audit |

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
| --- | --- | --- |
| `scripts/run_isolated_gradle.sh` | 01-stage-script-isolation | 02 |
| `scripts/verify_stage_6b0_settings_foundation.sh` | 01-stage-script-isolation | 02 |
| `scripts/verify_stage_6b1_mode_catalog.sh` | 01-stage-script-isolation | 02 |
| `scripts/verify_stage_6b2_filter_lab.sh` | 01-stage-script-isolation | 02 |
| `scripts/verify_stage_6b3_watermark_v2.sh` | 01-stage-script-isolation | 02 |
| `docs/plans/2026-05-26-agent-handoff-gradle-isolation-index.md` | 02-ledger-and-rules-restoration | 01 |
| `docs/plans/INDEX.md` | 02-ledger-and-rules-restoration | 01 |
| `codex/documentation.md` | 02-ledger-and-rules-restoration | 01 |
| `AGENTS.md` | 02-ledger-and-rules-restoration | 01 |
| `docs/plans/gradle-build-isolation-followup-orchestration/**` | Codex / final audit only after generation | package agents may write only their own `status/*.md` |

## Agent Budget

- Recommended Claude Code agents: 2 implementation agents plus Codex final audit.
- Max parallel agents: 2.
- Codex usage: final audit only unless implementation agents block.
- When to pause: if both packages need to edit `AGENTS.md` or any package discovers a script outside allowed paths that must be changed for correctness.

## Dispatch Plan

| Package | Mode | Agent Name | Prompt File | Status File |
| --- | --- | --- | --- | --- |
| 01-stage-script-isolation | background-agent-script | agent-01-stage-script-isolation | `launchers/agent-view-prompts.md#package-01-stage-script-isolation` | `status/01-stage-script-isolation.md` |
| 02-ledger-and-rules-restoration | background-agent-script | agent-02-ledger-and-rules-restoration | `launchers/agent-view-prompts.md#package-02-ledger-and-rules-restoration` | `status/02-ledger-and-rules-restoration.md` |
| 99-integration-audit | codex | — | `validation/final-audit-prompt.md` | `status/99-integration-audit.md` |

## Status Ledger

| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| 01-stage-script-isolation | — | pending | — | — | — | — |
| 02-ledger-and-rules-restoration | — | pending | — | — | — | — |
| 99-integration-audit | Codex | pending | — | — | — | — |

## Merge Strategy

- Merge order: 01 and 02 may land in either order if file ownership is respected, then 99 audit.
- Rebase policy: rebase on latest main before PR/merge if worktree tooling supports it.
- Conflict owner: the package that owns the file in the File Ownership Map resolves conflicts.
- Final integration agent: Codex.
- Do not delete worktrees until final audit passes or the user confirms.

## Evidence Pack Required From Each Agent

Each agent MUST write to its own `status/<package-id>.md` file after completion.
Do NOT edit this INDEX directly.

Evidence pack must include:
- [ ] worktree path
- [ ] branch name
- [ ] git status
- [ ] git diff --stat
- [ ] changed files
- [ ] commands run
- [ ] test result summary
- [ ] commit hash / PR link
- [ ] unresolved risks
- [ ] whether it touched only allowed paths

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
| --- | --- | --- | --- | --- |
| [01-stage-script-isolation.md](./packages/01-stage-script-isolation.md) | implementation agent | none | safe | Make old stage verification scripts use isolated build roots consistently. |
| [02-ledger-and-rules-restoration.md](./packages/02-ledger-and-rules-restoration.md) | implementation agent | none | safe | Restore the missing handoff package and sync project instructions/status. |
| [99-integration-audit.md](./packages/99-integration-audit.md) | Codex retained | after all packages | — | Verify package evidence, build roots, script behavior, and acceptance. |

## Recommended Execution Order

1. Launch packages 01 and 02 in parallel.
2. Merge or collect both evidence packs.
3. Run Codex final integration audit.

## Launch Options

- Option A: background agent script — run `bash docs/plans/gradle-build-isolation-followup-orchestration/launchers/dispatch-claude-agents.sh` to launch both package agents with `claude --bg --name`, then monitor in `claude agents`.
- Option B: Agent View manual fallback — open `claude agents --cwd /Volumes/Extreme_SSD/project/open_camera --effort xhigh`, then copy prompts from `launchers/agent-view-prompts.md`.
- Option C: Explicit permission override, only when the user has authorized it — set `CLAUDE_PERMISSION_MODE=bypassPermissions`, `auto`, `default`, or another supported mode before running the launcher. For `auto`, first run `claude --permission-mode auto` once interactively if your setup requires opt-in. If `auto` fails with an opt-in message, rerun without an override or complete the interactive opt-in.
- Option D: Final integration audit — give `validation/final-audit-prompt.md` to Codex.
