# Watermark 2.0 Validation Fixes - Orchestration Index

## Goal

Repair the two blockers found during Watermark 2.0 external landing validation so the official 6B3 watermark gate can become meaningful again: the `core:effect` preview API/test drift must compile and pass, and the `blur-four-border` template must reject invalid solid frame backgrounds at the persisted-action boundary, not only in UI/resolver fallback.

## Execution Mode Recommendation

- Recommended mode: AGENT_VIEW
- Why: There are two implementation packages with mostly disjoint file ownership, followed by one Codex-retained final audit.
- Alternatives rejected: SINGLE_AGENT - workable but slower and less useful because the two blockers split cleanly by module; CLAUDE_BG_SCRIPT - provided as an optional launcher, but manual Agent View is safer while the repo has multiple active planning streams; BATCH - this is not a mechanical repo-wide transform.
- Max parallel agents: 2
- Codex-retained work: final integration audit, product-truth judgment, and final ledger/doc update after agents report evidence.

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:
- Read this index and all referenced package documents.
- Create or reuse an isolated git worktree for implementation.
- Make scope-bounded edits, add/update tests, and update docs as described in your assigned package.
- Run the listed verification commands.
- Commit locally within the worktree branch.
- Merge, push, or create PRs for worktree branches using only incremental, non-destructive operations.
- Write to ONLY your assigned status file under `status/`; never edit this INDEX or another package's status file.

## Stop Gates - Must Ask

STOP and ask the user before:
- Crossing Stage boundaries or making architectural decisions beyond scope.
- Product-level decisions where requirements are genuinely ambiguous.
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
| G1 | 01-effect-preview-api-drift, 02-blur-border-settings-guard | yes | none | low; disjoint `core:effect` and `core:settings` ownership |
| G2 | 99-integration-audit | no | all implementation packages | final consistency audit |

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
| --- | --- | --- |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewColorTransform.kt` | 01-effect-preview-api-drift | 02 |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectModel.kt` | 01-effect-preview-api-drift | 02 |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt` | 01-effect-preview-api-drift | 02 |
| `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewColorTransformTest.kt` | 01-effect-preview-api-drift | 02 |
| `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt` | 01-effect-preview-api-drift | 02 |
| `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsActions.kt` | 02-blur-border-settings-guard | 01 |
| `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDefaults.kt` | 02-blur-border-settings-guard | 01 |
| `core/settings/src/test/kotlin/com/opencamera/core/settings/PersistedSettingsSerializerTest.kt` | 02-blur-border-settings-guard | 01 |
| `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkTemplateResolverTest.kt` | 02-blur-border-settings-guard | 01 |
| `docs/plans/2026-05-25-watermark-2-*.md` | 99-integration-audit | 01, 02 |
| `docs/plans/INDEX.md` | 99-integration-audit | 01, 02 |
| `codex/documentation.md` | 99-integration-audit | 01, 02 |

## Agent Budget

- Recommended Claude Code agents: 2 implementation agents plus Codex final audit.
- Max parallel agents: 2.
- Codex usage: final audit only; do not use Codex for package implementation unless agents block.
- When to pause: if either package needs to edit the other package's owned files, or if the official 6B3 script fails outside the two known blockers.

## Dispatch Plan

| Package | Mode | Agent Name | Prompt File | Status File |
| --- | --- | --- | --- | --- |
| 01-effect-preview-api-drift | agent-view | agent-01-effect-preview-api-drift | `launchers/agent-view-prompts.md#package-01-effect-preview-api-drift` | `status/01-effect-preview-api-drift.md` |
| 02-blur-border-settings-guard | agent-view | agent-02-blur-border-settings-guard | `launchers/agent-view-prompts.md#package-02-blur-border-settings-guard` | `status/02-blur-border-settings-guard.md` |
| 99-integration-audit | codex | - | `validation/final-audit-prompt.md` | `status/99-integration-audit.md` |

## Status Ledger

| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| 01-effect-preview-api-drift | - | pending | - | - | - | - |
| 02-blur-border-settings-guard | - | pending | - | - | - | - |
| 99-integration-audit | Codex | pending | - | - | - | - |

## Merge Strategy

- Merge order: either implementation package may merge first; run final audit after both land.
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
| [01-effect-preview-api-drift.md](packages/01-effect-preview-api-drift.md) | implementation agent | none | safe with 02 | Repair `core:effect` preview test/API drift that blocks the official Watermark V2 gate. |
| [02-blur-border-settings-guard.md](packages/02-blur-border-settings-guard.md) | implementation agent | none | safe with 01 | Guard direct persisted actions so `blur-four-border` cannot store solid backgrounds. |
| [99-integration-audit.md](packages/99-integration-audit.md) | Codex retained | after 01 and 02 | - | Final acceptance audit and ledger update. |

## Recommended Execution Order

1. Launch packages 01 and 02 in parallel.
2. Wait for both status files to include evidence packs.
3. Run 99 integration audit from `validation/final-audit-prompt.md`.

## Launch Options

- Option A: Agent View manual dispatch - copy prompts from `launchers/agent-view-prompts.md`.
- Option B: `claude --bg` script - run `bash launchers/dispatch-claude-agents.sh`.
- Option C: `/batch` - use `launchers/batch-instruction.md` only if the user wants a single Claude Code batch session.
- Option D: Final integration audit - give `validation/final-audit-prompt.md` to Codex after implementation packages finish.

