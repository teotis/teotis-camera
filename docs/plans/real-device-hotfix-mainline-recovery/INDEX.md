# Real Device Hotfix Mainline Recovery — Orchestration Index

## Goal
Recover the failed hotfix integration by landing the completed watermark and zoom work on current `main`, then closing the remaining shutter visual acceptance gap. The final state must satisfy the original real-device issues in the active delivery line, not only in detached worktrees.

## Execution Mode Recommendation
- Recommended mode: BACKGROUND_AGENT_SCRIPT
- Why: three packages are independently launchable and the user asked for a task package after a failed orchestration audit; a background script reduces manual dispatch drift.
- Alternatives rejected: SINGLE_AGENT — slower and mixes unrelated ownership; AGENT_VIEW — still provided as a fallback but less direct; BATCH — this is not mechanical; AGENT_TEAM — implementation work, not research; CODEX_RETAINED_REVIEW — reserved for final audit only.
- Max parallel agents: 3
- Codex-retained work: final integration audit and product-level assessment of shutter animation and zoom feel.

## CLI Reference Check
- Official reference checked: Claude Code CLI reference at `https://code.claude.com/docs/en/cli-usage`.
- Official permission reference checked: Claude Code permission modes at `https://code.claude.com/docs/en/permission-modes`.
- Local version checked: `claude --version` -> `2.1.142 (Claude Code)`.
- Local Agents View checked: `claude agents --help` supports `--cwd`, `--model`, `--effort`, `--permission-mode`, and `--setting-sources`.
- Official CLI reference documents `claude agents`, `--bg`, `--name`, and `--permission-mode`.
- Auto mode note: `--permission-mode auto` cannot be silently granted by a repository or background script. The user must either opt in once interactively with `claude --permission-mode auto` or configure user-level settings. Therefore the generated background script defaults to `CLAUDE_PERMISSION_MODE=default`; use `CLAUDE_PERMISSION_MODE=auto` only after opt-in.

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:
- Read the plan, index, and all referenced package documents.
- Create or reuse an isolated git worktree for implementation.
- Make scope-bounded edits, add/update tests, and update docs as described in your assigned package.
- Run the listed verification commands.
- Commit locally within the worktree branch.
- Merge, push, or create PRs for worktree branches (incremental, non-destructive operations).
- Write to ONLY your assigned `status/<package-id>.md` file. Never edit `INDEX.md` or another package's status file.

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
- Write your evidence pack to `status/<package-id>.md`; do NOT edit `INDEX.md`.
- Merge, push, or create a PR as the final step; no need to ask.
- Report: what changed, test results, merge/PR status, and branch/worktree path.
- Do NOT delete the worktree unless explicitly instructed.

## Concurrency Plan
| Group | Packages | Can Run In Parallel | Must Wait For | Conflict Risk |
|---|---|---|---|---|
| G1 | 01-watermark-mainline-recovery | yes, with 02 and 03 | none | safe — mode/effect/settings files |
| G1 | 02-zoom-scaleend-mainline-recovery | yes, with 01 and 03 | none | safe — gesture tests and gesture policy |
| G1 | 03-shutter-visual-closure | yes, with 01 and 02 | none | caution — app cockpit/shutter render files |
| G2 | 99-integration-audit | no | all G1 packages complete and merged/PR'd | reads all package outputs |

## File Ownership Map
| Path / Glob | Owner Package | Other Packages Must Not Edit |
|---|---|---|
| `feature/mode-humanistic/**` | 01-watermark-mainline-recovery | 02, 03 |
| `feature/mode-portrait/**` | 01-watermark-mainline-recovery | 02, 03 |
| `feature/mode-night/**` | 01-watermark-mainline-recovery | 02, 03 |
| `feature/mode-pro/**` | 01-watermark-mainline-recovery | 02, 03 |
| `feature/mode-document/**` | 01-watermark-mainline-recovery | 02, 03 |
| `core/effect/src/test/**` | 01-watermark-mainline-recovery | 02, 03 |
| `core/settings/src/test/**` | 01-watermark-mainline-recovery | 02, 03 |
| `app/src/main/java/com/opencamera/app/gesture/**` | 02-zoom-scaleend-mainline-recovery | 01, 03 |
| `app/src/test/java/com/opencamera/app/gesture/**` | 02-zoom-scaleend-mainline-recovery | 01, 03 |
| `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` | 03-shutter-visual-closure | 01, 02 |
| `app/src/main/java/com/opencamera/app/ShutterVisualDrawable.kt` | 03-shutter-visual-closure | 01, 02 |
| `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` | 03-shutter-visual-closure | 01, 02 |
| `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt` | 03-shutter-visual-closure | 01, 02 |
| `core/session/src/test/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessorTest.kt` | 03-shutter-visual-closure | 01, 02 |

## Agent Budget
- Recommended Claude Code agents: 3 implementation agents + Codex final audit
- Max parallel agents: 3
- Codex usage: final audit only.
- When to pause: if an agent cannot merge/rebase onto current `main`, or if fixing tests requires touching files outside its Allowed Paths.

## Dispatch Plan
| Package | Mode | Agent Name | Prompt File | Status File |
|---|---|---|---|---|
| 01-watermark-mainline-recovery | background-agent | agent-01-watermark-recovery | `launchers/agent-view-prompts.md#package-01-watermark-mainline-recovery` | `status/01-watermark-mainline-recovery.md` |
| 02-zoom-scaleend-mainline-recovery | background-agent | agent-02-zoom-recovery | `launchers/agent-view-prompts.md#package-02-zoom-scaleend-mainline-recovery` | `status/02-zoom-scaleend-mainline-recovery.md` |
| 03-shutter-visual-closure | background-agent | agent-03-shutter-visual | `launchers/agent-view-prompts.md#package-03-shutter-visual-closure` | `status/03-shutter-visual-closure.md` |
| 99-integration-audit | codex | — | `validation/final-audit-prompt.md` | `status/99-integration-audit.md` |

## Status Ledger
| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
|---|---|---|---|---|---|---|
| 01-watermark-mainline-recovery | — | pending | — | — | — | — |
| 02-zoom-scaleend-mainline-recovery | — | pending | — | — | — | — |
| 03-shutter-visual-closure | — | pending | — | — | — | — |
| 99-integration-audit | — | pending | — | — | — | — |

## Merge Strategy
- Merge order: 01 and 02 may merge first in either order; 03 may merge independently if no conflicts; final audit runs after all three.
- Rebase policy: every package must rebase on latest `main` before merge/PR.
- Conflict owner: owner package resolves conflicts in its owned paths; if a conflict touches shared status files, stop and ask.
- Final integration agent: Codex.
- Do not delete worktrees until: final audit passes and the user confirms.

## Evidence Pack Required From Each Agent

Each agent MUST write to its own `status/<package-id>.md` file after completion.
Do NOT edit `INDEX.md` directly.

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
| [01-watermark-mainline-recovery.md](packages/01-watermark-mainline-recovery.md) | implementation agent | none | safe | Reapply completed watermark work to current main |
| [02-zoom-scaleend-mainline-recovery.md](packages/02-zoom-scaleend-mainline-recovery.md) | implementation agent | none | safe | Reapply completed ScaleEnd zoom fix to current main |
| [03-shutter-visual-closure.md](packages/03-shutter-visual-closure.md) | implementation agent | none | caution | Make shutter visual behavior match the data boundary contract |
| [99-integration-audit.md](packages/99-integration-audit.md) | Codex retained | after all packages | — | Final cross-package acceptance |

## Recommended Execution Order
1. Launch packages 01, 02, and 03 in parallel.
2. Each agent rebases on latest `main`, applies only its package, verifies, writes its status file, and merges/PRs.
3. Run the final Codex integration audit.

## Launch Options
- Option A: Agent View manual dispatch — copy prompts from `launchers/agent-view-prompts.md`.
- Option B: background agent script — run `bash docs/plans/real-device-hotfix-mainline-recovery/launchers/dispatch-claude-agents.sh` and monitor with `claude agents`.
  - Default permission mode is `default`.
  - To use auto mode, first run `claude --permission-mode auto` interactively and accept the opt-in prompt, then run `CLAUDE_PERMISSION_MODE=auto bash docs/plans/real-device-hotfix-mainline-recovery/launchers/dispatch-claude-agents.sh`.
  - If `--bg` fails before creating sessions because auto mode is not opted in, rerun the interactive opt-in command above or use the default mode.
- Option C: Final integration audit — give `validation/final-audit-prompt.md` to Codex.
