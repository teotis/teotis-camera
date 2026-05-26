# Rendering 2.0 Post-Merge Follow-Up - Orchestration Index

## Goal

Accept the already-landed positive Rendering 2.0 fixes where they are implementation-quality, then close the remaining verification and ledger gaps. The known follow-up work is deliberately narrow: unblock the app unit-test compile gate around preview geometry tests, reconcile the Rendering 2.0 status ledgers after the accepted fixes, and run a final Codex audit that reports whether anything new remains.

## Execution Mode Recommendation

- Recommended mode: BACKGROUND_AGENT_SCRIPT
- Why: Two follow-up packages are file-disjoint: one app test/geometry compile repair and one documentation/status reconciliation. They can run in parallel and report evidence independently.
- Alternatives rejected: SINGLE_AGENT - possible but slower; AGENT_VIEW - available manually but less convenient; BATCH - not a mechanical repo-wide edit; AGENT_TEAM - unnecessary for implementation.
- Max parallel agents: 2
- Codex-retained work: final cross-package audit, quality judgment on already-accepted positives, and any low-risk obvious status correction.

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:
- Read this index, all package documents, status templates, and referenced Rendering 2.0 docs.
- Create or reuse an isolated git worktree for implementation.
- Make scope-bounded edits, add or update tests, and update docs as described in your assigned package.
- Run the listed verification commands through `rtk`.
- Commit locally within the worktree branch.
- Merge, push, or create PRs for worktree branches when that is the normal final delivery step.
- Write to ONLY your assigned `status/<package-id>.md` file. Never edit `INDEX.md` or another package status file.

## Stop Gates - Must Ask

STOP and ask the user before:
- Crossing Stage boundaries or making architecture decisions beyond the package scope.
- Making product-level decisions where the requirement is genuinely ambiguous.
- Running network access, external API calls, or adding secrets/credentials.
- Performing destructive git operations: force-push, hard reset, deleting branches, deleting worktrees.
- Overwriting unrelated dirty changes outside your assigned allowed paths.
- Fixing verification failures when the fix expands beyond your package.

## Completion Policy

After completing your assigned package:
- Write your evidence pack to `status/<package-id>.md`; do not edit `INDEX.md`.
- Commit locally or produce the requested PR/merge artifact as the final step when available.
- Report changed behavior, test results, branch/worktree path, and unresolved risks.
- Do not delete the worktree unless explicitly instructed.

## Concurrency Plan

| Group | Packages | Can Run In Parallel | Must Wait For | Conflict Risk |
| --- | --- | --- | --- | --- |
| G1 | 01-app-unit-test-gate-cleanup | yes, with 02 | none | low - app test/geometry files only |
| G1 | 02-ledger-status-reconciliation | yes, with 01 | none | low - docs/status files only |
| G2 | 99-integration-audit | no | 01 and 02 complete or blocked | audit-only |

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
| --- | --- | --- |
| `app/src/test/java/com/opencamera/app/PreviewContentGeometryTest.kt` | 01-app-unit-test-gate-cleanup | 02 |
| `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt` | 01-app-unit-test-gate-cleanup | 02 |
| `app/src/main/java/com/opencamera/app/**Preview*Geometry*` | 01-app-unit-test-gate-cleanup | 02 |
| `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt` | 01-app-unit-test-gate-cleanup only if required for compile repair | 02 |
| `docs/plans/rendering-2-0-validation-fix-orchestration-v2/**` | 02-ledger-status-reconciliation | 01 |
| `docs/plans/2026-05-25-rendering-2-0-*.md` | 02-ledger-status-reconciliation | 01 |
| `docs/plans/INDEX.md` | 02-ledger-status-reconciliation | 01 |
| `docs/plans/rendering-2-0-post-merge-followup-orchestration/status/<package-id>.md` | assigned package only | all others |

## Agent Budget

- Recommended Claude Code agents: 2 implementation agents plus Codex audit.
- Max parallel agents: 2.
- Codex usage: final audit, focused verification summary, and product-quality judgment.
- When to pause: if app test cleanup requires runtime product behavior changes, or if ledger reconciliation lacks evidence for a claim.

## Dispatch Plan

| Package | Mode | Agent Name | Prompt File | Status File |
| --- | --- | --- | --- | --- |
| 01-app-unit-test-gate-cleanup | background | agent-render-v2-followup-01-app-gate | `launchers/agent-view-prompts.md#package-01-app-unit-test-gate-cleanup` | `status/01-app-unit-test-gate-cleanup.md` |
| 02-ledger-status-reconciliation | background | agent-render-v2-followup-02-ledger | `launchers/agent-view-prompts.md#package-02-ledger-status-reconciliation` | `status/02-ledger-status-reconciliation.md` |
| 99-integration-audit | Codex | - | `validation/final-audit-prompt.md` | `status/99-integration-audit.md` |

## Status Ledger

| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| 01-app-unit-test-gate-cleanup | - | pending | - | - | - | - |
| 02-ledger-status-reconciliation | - | pending | - | - | - | - |
| 99-integration-audit | Codex | pending | - | - | - | - |

## Merge Strategy

- Merge order: 01 and 02 can land in any order; 99 audits after both.
- Rebase policy: rebase on latest main/current integration branch before final PR or merge.
- Conflict owner: the package that owns the file resolves conflicts; status docs must not be edited by the app-test package.
- Final integration agent: Codex.
- Do not delete worktrees until the final audit passes or the user confirms cleanup.

## Evidence Pack Required From Each Agent

Each agent MUST write to its own `status/<package-id>.md` file after completion.
Do NOT edit `INDEX.md` directly.

Evidence pack must include:
- [ ] worktree path
- [ ] branch name
- [ ] git status
- [ ] git diff --stat
- [ ] changed files
- [ ] commands run and output summary
- [ ] test result summary
- [ ] commit hash or PR link
- [ ] unresolved risks
- [ ] self-certification that only allowed paths were touched

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
| --- | --- | --- | --- | --- |
| [01-app-unit-test-gate-cleanup.md](./packages/01-app-unit-test-gate-cleanup.md) | implementation agent | none | safe with 02 | Repair the unrelated app test compile blockers so the PreviewColorTransformOverlay app test can actually run. |
| [02-ledger-status-reconciliation.md](./packages/02-ledger-status-reconciliation.md) | documentation/status agent | none | safe with 01 | Update Rendering 2.0 ledgers to reflect accepted 01/02/03 positives and remaining validation limits without overclaiming product completion. |
| [99-integration-audit.md](./packages/99-integration-audit.md) | Codex retained | after 01 and 02 | - | Verify code, docs, ownership, and tests after follow-up packages land. |

## Recommended Execution Order

1. Launch packages 01 and 02 in parallel.
2. Package 01 should make no documentation claims beyond its own status evidence.
3. Package 02 should only claim implementation success where code and verification evidence exist.
4. Run Codex final audit from `validation/final-audit-prompt.md`.

## Launch Options

- Option A: Agent View manual dispatch - copy prompts from `launchers/agent-view-prompts.md`.
- Option B: background agent script - run `bash docs/plans/rendering-2-0-post-merge-followup-orchestration/launchers/dispatch-claude-agents.sh`.
- Option B with explicit mode override - set `CLAUDE_PERMISSION_MODE=acceptEdits|auto|default|dontAsk|bypassPermissions` only when you want to override Claude Code settings for this dispatch.
- Option B with auto mode - first opt in interactively with `claude --permission-mode auto`, then run `CLAUDE_PERMISSION_MODE=auto bash docs/plans/rendering-2-0-post-merge-followup-orchestration/launchers/dispatch-claude-agents.sh`.
- Option C: final integration audit - give `validation/final-audit-prompt.md` to Codex after packages complete.

## CLI Basis

This package follows the updated agent orchestration template for Claude Code 2.1 background sessions. The dispatch script uses `claude --bg --name` and prints `claude agents --cwd` for monitoring. It omits `--permission-mode` by default so the user's configured Claude Code settings apply. If `CLAUDE_PERMISSION_MODE=auto` is requested and Claude Code reports that auto mode requires interactive opt-in, the script prints `claude --permission-mode auto` as the required one-time setup.

The script intentionally does not use `--dangerously-skip-permissions`.
