# Rendering 2.0 Validation Fixes V2 - Orchestration Index

## Goal

Repair the failed Rendering 2.0 landing with implementation packages that address the evidence-backed gaps found after the external execution claim: capture save reliability still lacks an outer postprocess fallback, Render Recipe V2 still has duplicate/local parsing behavior, Color Lab preview still overclaims fidelity when the matrix is not applied to the real preview path, and the documentation ledger still contradicts the actual implementation state.

## Execution Mode Recommendation

- Recommended mode: BACKGROUND_AGENT_SCRIPT
- Why: Four implementation packages are scope-bounded and mostly file-disjoint, and the user asked for the latest executable task package after external agent execution.
- Alternatives rejected: AGENT_VIEW - still available but more manual; SINGLE_AGENT - slower and less useful for disjoint repairs; BATCH - this is not a repo-wide mechanical transform; AGENT_TEAM - not appropriate for direct implementation.
- Max parallel agents: 4
- Codex-retained work: final cross-package integration audit, evidence review, and any low-risk obvious audit-only fix.

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:
- Read this index, package documents, status templates, and referenced project docs.
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
| G1 | 01-postprocess-outer-guard, 03-preview-fidelity-honesty | yes | none | low - disjoint app files |
| G1 | 02-recipe-single-truth | yes | none | medium - may touch `PhotoAlgorithmPostProcessor.kt`, coordinate if package 01 adds adjacent tests there |
| G1 | 04-ledger-and-gate-honesty | yes | none | low - docs/scripts only; must not claim implementation success before evidence exists |
| G2 | 99-integration-audit | no | 01, 02, 03, 04 complete or blocked | audit-only |

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
| --- | --- | --- |
| `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` | 01-postprocess-outer-guard | 02, 03, 04 |
| `core/media/**` | 01-postprocess-outer-guard | 02, 03, 04 |
| `app/src/test/**/camera/**` capture/postprocess tests | 01-postprocess-outer-guard | coordinate before editing |
| `core/effect/**/RenderRecipe*`, `*MetadataCodec*`, `EffectBridge*` | 02-recipe-single-truth | 01, 03, 04 |
| `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt` | 02-recipe-single-truth | 01 only if strictly needed for outer guard tests |
| `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt` | 03-preview-fidelity-honesty | 01, 02, 04 |
| `app/src/main/java/com/opencamera/app/**PreviewColorTransform*` | 03-preview-fidelity-honesty | 01, 02, 04 |
| `docs/plans/INDEX.md`, Rendering 2.0 docs, verification scripts | 04-ledger-and-gate-honesty | 01, 02, 03 |
| `docs/plans/rendering-2-0-validation-fix-orchestration-v2/status/<package-id>.md` | assigned package only | all others |

## Agent Budget

- Recommended Claude Code agents: 4 implementation agents plus Codex audit.
- Max parallel agents: 4.
- Codex usage: final audit, cross-package semantic review, and local verification summary.
- When to pause: any package reports a required architecture change, real-device-only blocker, destructive git need, or forbidden-path conflict.

## Dispatch Plan

| Package | Mode | Agent Name | Prompt File | Status File |
| --- | --- | --- | --- | --- |
| 01-postprocess-outer-guard | background | agent-render-v2-01-postprocess | `launchers/agent-view-prompts.md#package-01-postprocess-outer-guard` | `status/01-postprocess-outer-guard.md` |
| 02-recipe-single-truth | background | agent-render-v2-02-recipe | `launchers/agent-view-prompts.md#package-02-recipe-single-truth` | `status/02-recipe-single-truth.md` |
| 03-preview-fidelity-honesty | background | agent-render-v2-03-preview | `launchers/agent-view-prompts.md#package-03-preview-fidelity-honesty` | `status/03-preview-fidelity-honesty.md` |
| 04-ledger-and-gate-honesty | background or manual | agent-render-v2-04-ledger | `launchers/agent-view-prompts.md#package-04-ledger-and-gate-honesty` | `status/04-ledger-and-gate-honesty.md` |
| 99-integration-audit | Codex | - | `validation/final-audit-prompt.md` | `status/99-integration-audit.md` |

## Status Ledger

| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| 01-postprocess-outer-guard | external agent | accepted locally | mainline via `feat/rendering-2-0-upgrade` | `cc76237` | focused tests PASS | `guardedPostProcess(...)` in CameraXCaptureAdapter, PostprocessOuterGuardTest covers fallback |
| 02-recipe-single-truth | external agent | accepted locally | mainline via `feat/rendering-2-0-upgrade` | `ce250f3` | focused tests PASS | `RenderRecipe.from(ShotResult)` canonical fallback, recipe codec round-trip |
| 03-preview-fidelity-honesty | external agent | accepted locally (approximate only) | mainline via `feat/rendering-2-0-upgrade` | `3612c32` | focused tests PASS | `PreviewColorTransform`/`PreviewColorFidelity` model honest states; NOT real PreviewView parity |
| 04-ledger-and-gate-honesty | - | superseded by post-merge follow-up package 02 | - | - | - | ledger reconciliation handled by `rendering-2-0-post-merge-followup-orchestration` |
| 99-integration-audit | Codex | FAILED (pre-reconciliation) | mainline | - | static audit only | all packages failed pre-reconciliation; positive results accepted post-reconciliation |

## Merge Strategy

- Merge order: 01, 02, and 03 can land in any order after passing focused checks; 04 should land after it reflects the real final state; 99 audits after all.
- Rebase policy: rebase on the latest main/current integration branch before final PR or merge.
- Conflict owner: the package that owns the file resolves the conflict; adjacent test conflicts require coordination in status notes.
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
| [01-postprocess-outer-guard.md](./packages/01-postprocess-outer-guard.md) | implementation agent | none | safe with 03, coordinate with 02 only for tests | Ensure optional rendering/postprocess failures never lose saved media. |
| [02-recipe-single-truth.md](./packages/02-recipe-single-truth.md) | implementation agent | none | medium | Make Render Recipe V2 the single truth for metadata and postprocess work decisions. |
| [03-preview-fidelity-honesty.md](./packages/03-preview-fidelity-honesty.md) | implementation agent | none | safe with 01/02 | Make Color Lab preview behavior match the fidelity claim or report fallback honestly. |
| [04-ledger-and-gate-honesty.md](./packages/04-ledger-and-gate-honesty.md) | implementation agent | final doc status should follow implementation evidence | safe | Fix contradictory plan status and isolate the Stage 7 gate issue without deleting coverage. |
| [99-integration-audit.md](./packages/99-integration-audit.md) | Codex retained | after all packages | - | Verify acceptance, file ownership, tests, and final recommendation. |

## Recommended Execution Order

1. Launch packages 01, 02, 03, and 04 in parallel.
2. Package 04 must record uncertainty honestly if packages 01-03 are still pending.
3. Run Codex final audit from `validation/final-audit-prompt.md`.

## Launch Options

- Option A: Agent View manual dispatch - copy prompts from `launchers/agent-view-prompts.md`.
- Option B: background agent script - run `bash docs/plans/rendering-2-0-validation-fix-orchestration-v2/launchers/dispatch-claude-agents.sh`.
- Option B with explicit mode override - set `CLAUDE_PERMISSION_MODE=acceptEdits|auto|default|dontAsk|bypassPermissions` only when you want to override Claude Code settings for this dispatch.
- Option B with auto mode - first opt in interactively with `claude --permission-mode auto`, then run `CLAUDE_PERMISSION_MODE=auto bash docs/plans/rendering-2-0-validation-fix-orchestration-v2/launchers/dispatch-claude-agents.sh`.
- Option B with bypass permissions - configure an allowed user-level `permissions.defaultMode` or explicitly run `CLAUDE_PERMISSION_MODE=bypassPermissions bash docs/plans/rendering-2-0-validation-fix-orchestration-v2/launchers/dispatch-claude-agents.sh` only in an isolated environment you are comfortable granting.
- Option C: final integration audit - give `validation/final-audit-prompt.md` to Codex after packages complete.

## CLI Basis

This package was generated after checking local Claude Code `2.1.142` and the official Claude Code CLI and Agent View references. The dispatch script uses `claude --bg --name` for background sessions and `claude agents --cwd` for monitoring, with configurable `--model`, `--effort`, `--permission-mode`, and `--setting-sources`.

The script defaults to no `--permission-mode` flag. That means each `claude --bg` task unit inherits the effective Claude Code settings from the repo directory, including user-level or project-level `permissions.defaultMode`. If the user has configured `permissions.defaultMode` to `bypassPermissions` in an allowed settings scope, the generated background sessions can start in bypass mode without this repo hard-coding a dangerous flag.

If the user wants an explicit one-run override, set `CLAUDE_PERMISSION_MODE=bypassPermissions`, `auto`, `default`, `acceptEdits`, or another supported mode before running the script. Auto mode is not silently granted by this repo: if the user wants auto mode for `claude --bg`, they must opt in once interactively with `claude --permission-mode auto` or configure an allowed user-level default, then rerun the script with `CLAUDE_PERMISSION_MODE=auto`. If Agent View shows no tasks after running the script, check whether the first `claude --bg` failed before creating a background session because the requested permission mode was not authorized.

The script intentionally does not use `--dangerously-skip-permissions`.
