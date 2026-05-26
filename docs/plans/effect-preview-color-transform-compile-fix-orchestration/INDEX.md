# Effect Preview Color Transform Compile Fix — Orchestration Index

## Goal

Repair the current `:core:effect:compileTestKotlin` blocker where `PreviewColorTransformTest.kt` references the removed `PreviewColorMatrixBuilder` API, then rerun the focused effect and Watermark 6B3 gates under an isolated build root.

## Execution Mode Recommendation

- Recommended mode: SINGLE_AGENT
- Why: one compile blocker, one ownership area, low concurrency value.
- Alternatives rejected: AGENT_VIEW / BACKGROUND_AGENT_SCRIPT — unnecessary for a single-file or small API repair.
- Max parallel agents: 1
- Codex-retained work: final audit only.

## Execution Authorization

The external agent is authorized to:
- Create or reuse an isolated git worktree.
- Edit only the allowed paths below.
- Run the verification commands.
- Commit locally within the worktree branch.
- Write only to `status/01-preview-color-transform-test-api.md`.

## Stop Gates — Must Ask

STOP and ask before:
- Changing runtime Color Lab/product behavior beyond what is required to compile and validate tests.
- Touching app UI or camera runtime files.
- Deleting worktrees, hard resetting, force pushing, or overwriting unrelated dirty changes.

## Completion Policy

After completing the package, write the evidence pack to the package status file, commit locally, and report branch/worktree/verification results.

## Concurrency Plan

| Group | Packages | Can Run In Parallel | Must Wait For | Conflict Risk |
|---|---|---|---|---|
| G1 | 01-preview-color-transform-test-api | no | none | low |
| G2 | 99-integration-audit | no | G1 | low |

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
|---|---|---|
| `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewColorTransform.kt` | 01 | 99 |
| `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewColorTransformTest.kt` | 01 | 99 |
| `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt` | 01 if needed | 99 |

## Dispatch Plan

| Package | Mode | Agent Name | Prompt File | Status File |
|---|---|---|---|---|
| 01-preview-color-transform-test-api | single-agent | effect-preview-transform-compile | `launchers/agent-view-prompts.md#pkg-01` | `status/01-preview-color-transform-test-api.md` |
| 99-integration-audit | codex | — | `validation/final-audit-prompt.md` | `status/99-integration-audit.md` |

## Status Ledger

| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
|---|---|---|---|---|---|---|
| 01-preview-color-transform-test-api | — | pending | — | — | — | — |
| 99-integration-audit | Codex | pending | — | — | — | — |

## Evidence Pack Required

Each implementation agent must report:
- worktree path and branch
- changed files
- git diff stat
- verification commands and pass/fail summary
- commit hash
- unresolved risks

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-preview-color-transform-test-api.md](packages/01-preview-color-transform-test-api.md) | implementation agent | none | single-agent | Repair missing `PreviewColorMatrixBuilder` compile blocker |
| [99-integration-audit.md](packages/99-integration-audit.md) | Codex retained | after 01 | — | Final gate and classification |

## Recommended Execution Order

1. Run package 01.
2. Run package 99 final audit.

## Launch Options

- Manual: copy `launchers/agent-view-prompts.md#pkg-01` into Claude Code.
- Optional background: run `bash docs/plans/effect-preview-color-transform-compile-fix-orchestration/launchers/dispatch-claude-agents.sh --print-only`, then launch the printed command if desired.
