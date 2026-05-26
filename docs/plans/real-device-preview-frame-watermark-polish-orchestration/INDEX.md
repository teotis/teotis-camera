# Real Device Preview Frame And Blur Watermark Polish - Orchestration Index

## Goal

Resolve two real-device visual regressions as independent, verifiable closed loops: frame-ratio selection must keep the live preview as the current lens full-field view while only the UI frame and saved still crop change, and the `blur-four-border` watermark must generate a natural content-derived border instead of a pale fixed-looking frame. This package is intentionally scoped below a stage transition: it refines Stage 6B/7 product behavior without reopening frozen feature stages or adding a new camera runtime owner.

## Execution Mode Recommendation

- Recommended mode: AGENT_VIEW
- Why: The preview/frame package and watermark-rendering package touch disjoint owners and can run in parallel if they do not edit existing orchestration ledgers. Each requires product judgment plus focused tests, so manual Agent View dispatch is safer than one-shot background launch.
- Alternatives rejected:
  - SINGLE_AGENT - possible, but slower and likely to blur preview architecture decisions with bitmap tuning.
  - BACKGROUND_AGENT_SCRIPT - provided as an optional launcher, but do not use it while other preview or Watermark 2.0 agents are actively editing overlapping files.
  - BATCH - not a mechanical migration.
  - AGENT_TEAM - direct implementation packages are clearer than exploratory debate agents.
- Max parallel agents: 2
- Codex-retained work: final integration audit, multimodal/real-device acceptance judgment, and any decision to merge this with existing preview or Watermark 2.0 remediation lines.

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:
- Read the plan, index, and all referenced package documents.
- Create or reuse an isolated git worktree for implementation.
- Make scope-bounded edits, add/update tests, and update docs as described in your assigned package.
- Run the listed verification commands.
- Commit locally within the worktree branch.
- Merge, push, or create PRs for worktree branches (incremental, non-destructive operations).
- Write to ONLY your assigned `status/<package-id>.md` file - never edit `INDEX.md` or another package's status file.

## Stop Gates - Must Ask

STOP and ask the user before:
- Crossing stage boundaries or declaring a new stage.
- Rebinding or resizing CameraX preview solely because the user selected `4:3`, `16:9`, or `1:1`.
- Creating a second hidden session/device owner in UI, coordinator, adapter, or postprocessor code.
- Claiming saved output matches the visible frame without deterministic crop tests or real-device evidence.
- Tuning `blur-four-border` by adding a fixed white/cream/dark overlay that hides the content-derived background.
- Adding network dependencies, external ML/image libraries, secrets, or API calls.
- Destructive git operations: force-push, hard reset, deleting branches/worktrees.
- Overwriting unrelated dirty changes outside your assigned Allowed Paths.
- Fixing verification failures when the fix expands beyond your package.

## Completion Policy

After completing your assigned package:
- Write your evidence pack to `status/<package-id>.md` - do NOT edit `INDEX.md`.
- Merge, push, or create a PR as the final step - no need to ask.
- Report: what changed, test results, merge/PR status, and branch/worktree path.
- Do NOT delete the worktree unless explicitly instructed.

## Concurrency Plan

| Group | Packages | Can Run In Parallel | Must Wait For | Conflict Risk |
|---|---|---|---|---|
| G1 | 01-preview-frame-contract, 02-natural-blur-border-rendering | yes | none | safe if existing preview/Watermark remediation packages are not active |
| G2 | 99-integration-audit | no | G1 complete | final audit only |

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
|---|---|---|
| `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt` | 01-preview-frame-contract | 02 |
| `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt` preview geometry sections | 01-preview-frame-contract | 02 |
| `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` preview/use-case and zoom/frame sections | 01-preview-frame-contract | 02 |
| `app/src/main/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessor.kt` | 01-preview-frame-contract | 02 |
| `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt` | 01-preview-frame-contract | 02 |
| `app/src/test/java/com/opencamera/app/PreviewContentGeometryTest.kt` | 01-preview-frame-contract | 02 |
| `app/src/test/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessorTest.kt` | 01-preview-frame-contract | 02 |
| `core/device/**` narrowly scoped preview/capture capability contract files | 01-preview-frame-contract | 02 |
| `core/media/**` narrowly scoped composition crop contract files | 01-preview-frame-contract | 02 |
| `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt` blur-frame background/rendering helpers | 02-natural-blur-border-rendering | 01 |
| `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkPostProcessorTest.kt` | 02-natural-blur-border-rendering | 01 |
| `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkTemplateResolverTest.kt` only if resolver notes need strengthening | 02-natural-blur-border-rendering | 01 |
| `core/settings/**` | neither package by default | 01 and 02 must ask unless a failing test proves settings are the cause |
| `docs/plans/**` | package agents may edit only their own status file | all package agents must not edit indexes or other packages |

## Agent Budget

- Recommended Claude Code agents: 2 implementation agents + Codex final audit
- Max parallel agents: 2
- Codex usage: final audit, real-device/multimodal review, and merge decision only
- When to pause: either package reports an unverifiable product claim, or `01` discovers the preview crop is caused by a deeper CameraX/device request path that requires a product/architecture decision beyond frame-ratio semantics.

## Dispatch Plan

| Package | Mode | Agent Name | Prompt File | Status File |
|---|---|---|---|---|
| 01-preview-frame-contract | agent-view | agent-01-preview-frame-contract | launchers/agent-view-prompts.md#package-01-preview-frame-contract | status/01-preview-frame-contract.md |
| 02-natural-blur-border-rendering | agent-view | agent-02-natural-blur-border | launchers/agent-view-prompts.md#package-02-natural-blur-border-rendering | status/02-natural-blur-border-rendering.md |
| 99-integration-audit | codex | - | validation/final-audit-prompt.md | status/99-integration-audit.md |

## Status Ledger

| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
|---|---|---|---|---|---|---|
| 01-preview-frame-contract | - | pending | - | - | - | - |
| 02-natural-blur-border-rendering | - | pending | - | - | - | - |
| 99-integration-audit | - | pending | - | - | - | - |

## Merge Strategy

- Merge order: either `01` or `02` may merge first if file ownership is respected; run `99` after both land.
- Rebase policy: rebase on latest main before merge/PR.
- Conflict owner: `01-preview-frame-contract` owns preview/content/crop conflicts; `02-natural-blur-border-rendering` owns watermark rendering conflicts.
- Final integration agent: Codex.
- Do not delete worktrees until: final audit passes and the user confirms cleanup.

## Evidence Pack Required From Each Agent

Each agent MUST write to its own `status/<package-id>.md` file after completion.
Do NOT edit `INDEX.md` directly - that causes concurrent-write conflicts.

Evidence pack must include:
- [ ] worktree path
- [ ] branch name
- [ ] git status
- [ ] git diff --stat
- [ ] changed files (full list)
- [ ] commands run (verification commands + output summary)
- [ ] test result summary (pass/fail counts)
- [ ] commit hash / PR link
- [ ] unresolved risks (if any)
- [ ] whether it touched only allowed paths (self-certify)

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-preview-frame-contract.md](packages/01-preview-frame-contract.md) | implementation agent | none | safe with 02 | Keep preview full-lens while frame ratio affects only UI crop frame and saved still crop |
| [02-natural-blur-border-rendering.md](packages/02-natural-blur-border-rendering.md) | implementation agent | none | safe with 01 | Replace fixed-looking blur-frame background with content-aware edge extension and tested visual constraints |
| [99-integration-audit.md](packages/99-integration-audit.md) | Codex retained | after 01 and 02 | - | Final cross-package acceptance audit |

## Recommended Execution Order

1. Check no active external agent is editing `PreviewOverlayView.kt`, `CameraXCaptureAdapter.kt`, `PhotoFrameRatioPostProcessor.kt`, or `PhotoWatermarkPostProcessor.kt`.
2. Launch `01` and `02` in parallel if the tree is quiet.
3. Run `99` after both packages complete and write their status files.

## Claude Background Permission Notes

- Official Anthropic CLI documentation lists `--model`, `--permission-mode`, and related CLI flags, and Claude Code settings document `permissions.defaultMode`; local `claude --version` was checked as `2.1.142 (Claude Code)` when this kit was generated.
- Generated scripts default to inheriting the user's configured Claude Code permission mode by omitting `--permission-mode`.
- If the user has set `permissions.defaultMode` to `bypassPermissions` in `~/.claude/settings.json`, background sessions inherit that mode without the repository hard-coding it.
- Use `CLAUDE_PERMISSION_MODE=bypassPermissions`, `auto`, `default`, or another supported mode only when an explicit per-dispatch override is needed.
- If the user wants `CLAUDE_PERMISSION_MODE=auto`, run `claude --permission-mode auto` once interactively first. If that fails, rerun with `CLAUDE_PERMISSION_MODE=default`.
- Do not use `--dangerously-skip-permissions`.

## Launch Options

- **Option A**: Agent View manual dispatch - copy prompts from `launchers/agent-view-prompts.md`.
- **Option B**: background agent script - run `rtk bash docs/plans/real-device-preview-frame-watermark-polish-orchestration/launchers/dispatch-claude-agents.sh`; the script launches both G1 packages and prints a `claude agents --cwd ...` command.
- **Option C**: final integration audit - give `validation/final-audit-prompt.md` to Codex after both packages finish.
