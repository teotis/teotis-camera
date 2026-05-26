# Rendering 2.0 Validation Fixes — Orchestration Index

## Goal

Verify and repair the remaining gaps after the external Rendering 2.0 landing. The current implementation is partly valid, but final acceptance is not appropriate until postprocess save reliability has an outer guard, recipe parsing is truly centralized, preview fidelity claims match what the app actually renders, and the planning/verification ledger is made honest.

## Execution Mode Recommendation

- Recommended mode: AGENT_VIEW
- Why: Four packages are mostly file-disjoint and can be assigned to separate Claude Code agents with one final audit.
- Alternatives rejected: SINGLE_AGENT — slower and less useful because the issues split cleanly by ownership; CLAUDE_BG_SCRIPT — possible, but manual Agent View is safer while repository status is busy; BATCH — not a mechanical repo-wide transform.
- Max parallel agents: 3
- Codex-retained work: final integration audit, product-truth judgment, and any real-device visual acceptance.

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:
- Read this index and all referenced package documents.
- Create or reuse an isolated git worktree for implementation.
- Make scope-bounded edits, add/update tests, and update docs as described in your assigned package.
- Run the listed verification commands.
- Commit locally within the worktree branch.
- Merge, push, or create PRs for worktree branches using only incremental, non-destructive operations.
- Write to ONLY your assigned status file under `status/`; never edit this INDEX or another package's status file.

## Stop Gates — Must Ask

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
| G1 | 01-postprocess-outer-guard, 02-recipe-single-truth, 04-ledger-and-gate-honesty | yes | none | medium-low; mostly disjoint app/core/docs files |
| G2 | 03-preview-fidelity-honesty | yes after 02 starts or finishes | 02 if it changes `PreviewEffectAdapter` recipe fields | medium; reads recipe/preview model semantics |
| G3 | 99-integration-audit | no | all implementation packages | final consistency audit |

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
| --- | --- | --- |
| `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` | 01-postprocess-outer-guard | 02, 03, 04 |
| `core/media/src/main/kotlin/com/opencamera/core/media/MediaPostProcessors.kt` | 01-postprocess-outer-guard | 02, 03, 04 |
| `core/media/src/test/kotlin/com/opencamera/core/media/CompositeMediaPostProcessorTest.kt` | 01-postprocess-outer-guard | 02, 03, 04 |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/RenderRecipe.kt` | 02-recipe-single-truth | 01, 03, 04 |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt` | 02-recipe-single-truth | 01, 03, 04 |
| `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsMetadataCodecs.kt` | 02-recipe-single-truth | 01, 03, 04 |
| `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt` | 02-recipe-single-truth | 01, 03, 04 |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewColorTransform.kt` | 03-preview-fidelity-honesty | 01, 02, 04 |
| `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt` | 03-preview-fidelity-honesty | 01, 02, 04 |
| `app/src/main/java/com/opencamera/app/PreviewColorTransformOverlay.kt` | 03-preview-fidelity-honesty | 01, 02, 04 |
| `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt` | 03-preview-fidelity-honesty | 01, 02, 04 |
| `docs/plans/2026-05-25-rendering-2-0-*.md` | 04-ledger-and-gate-honesty | 01, 02, 03 |
| `docs/plans/INDEX.md` | 04-ledger-and-gate-honesty | 01, 02, 03 |
| `scripts/verify_stage_7_observability.sh` | 04-ledger-and-gate-honesty | 01, 02, 03 |

## Agent Budget

- Recommended Claude Code agents: 4 implementation agents plus Codex final audit.
- Max parallel agents: 3.
- Codex usage: final audit only; do not use Codex for local implementation unless package agents block.
- When to pause: if `:app:assembleDebug` cannot pass after a clean module build, or if multiple agents need the same owned file.

## Dispatch Plan

| Package | Mode | Agent Name | Prompt File | Status File |
| --- | --- | --- | --- | --- |
| 01-postprocess-outer-guard | agent-view | agent-01-postprocess-outer-guard | `launchers/agent-view-prompts.md#package-01-postprocess-outer-guard` | `status/01-postprocess-outer-guard.md` |
| 02-recipe-single-truth | agent-view | agent-02-recipe-single-truth | `launchers/agent-view-prompts.md#package-02-recipe-single-truth` | `status/02-recipe-single-truth.md` |
| 03-preview-fidelity-honesty | agent-view | agent-03-preview-fidelity-honesty | `launchers/agent-view-prompts.md#package-03-preview-fidelity-honesty` | `status/03-preview-fidelity-honesty.md` |
| 04-ledger-and-gate-honesty | agent-view | agent-04-ledger-and-gate-honesty | `launchers/agent-view-prompts.md#package-04-ledger-and-gate-honesty` | `status/04-ledger-and-gate-honesty.md` |
| 99-integration-audit | codex | — | `validation/final-audit-prompt.md` | `status/99-integration-audit.md` |

## Status Ledger

| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| 01-postprocess-outer-guard | — | pending | — | — | — | — |
| 02-recipe-single-truth | — | pending | — | — | — | — |
| 03-preview-fidelity-honesty | — | pending | — | — | — | — |
| 04-ledger-and-gate-honesty | — | pending | — | — | — | — |
| 99-integration-audit | Codex | pending | — | — | — | — |

## Merge Strategy

- Merge order: 01 and 02 first, then 03, then 04, then final audit.
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
| [01-postprocess-outer-guard.md](packages/01-postprocess-outer-guard.md) | implementation agent | none | safe with 02/04 | Preserve `ShotCompleted` when composite-level postprocess throws. |
| [02-recipe-single-truth.md](packages/02-recipe-single-truth.md) | implementation agent | none | safe with 01/04 | Remove remaining recipe side-channel behavior and recipe-only no-op gap. |
| [03-preview-fidelity-honesty.md](packages/03-preview-fidelity-honesty.md) | implementation agent | after 02 semantics are stable | caution | Align preview transform claims with actual app rendering. |
| [04-ledger-and-gate-honesty.md](packages/04-ledger-and-gate-honesty.md) | implementation agent | none | safe | Fix plan status contradictions and isolate Stage 7 gate hang. |
| [99-integration-audit.md](packages/99-integration-audit.md) | Codex retained | after all packages | — | Final acceptance audit. |

## Recommended Execution Order

1. Launch packages 01, 02, and 04 in parallel.
2. Launch package 03 after package 02 reports whether recipe/preview model names changed.
3. Run 99 integration audit after all status files are filled.

## Launch Options

- Option A: Agent View manual dispatch — copy prompts from `launchers/agent-view-prompts.md`.
- Option B: `claude --bg` script — run `bash launchers/dispatch-claude-agents.sh`.
- Option C: Final integration audit — give `validation/final-audit-prompt.md` to Codex.

