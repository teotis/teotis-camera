# Real Device Hotfix Rework — Orchestration Index

## Goal
Close the remaining real-device hotfix gaps found during Codex validation: make the shutter loading animation stop at the real bottom-layer data boundary, land the watermark/UI fix on main, and repair pinch zoom continuity plus bottom focal-length UI synchronization.

## Execution Mode Recommendation
- Recommended mode: AGENT_VIEW
- Why: Three implementation packages are mostly file-disjoint and can run in parallel, while final acceptance needs one cross-package audit.
- Alternatives rejected: SINGLE_AGENT — slower and unnecessary; CLAUDE_BG_SCRIPT — available as an option but not required; BATCH — this is not a mechanical repo-wide migration; AGENT_TEAM — implementation work does not need multi-hypothesis research.
- Max parallel agents: 3
- Codex-retained work: final integration audit, acceptance check against the original real-device issues, and any product-level judgment about animation/zoom feel.

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
| G1 | 01-shutter-data-boundary | yes, with 02 and 03 | none | caution — touches session/device/app capture path |
| G1 | 02-watermark-mainline | yes, with 01 and 03 | none | safe — mode plugin/settings/watermark tests only |
| G1 | 03-zoom-scaleend-sync | yes, with 01 and 02 | none | caution — app gesture/cockpit zoom files |
| G2 | 99-integration-audit | no | all G1 packages complete | reads all touched files and status evidence |

## File Ownership Map
| Path / Glob | Owner Package | Other Packages Must Not Edit |
|---|---|---|
| `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` | 01-shutter-data-boundary | 02, 03 |
| `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt` | 01-shutter-data-boundary | 02, 03 |
| `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt` | 01-shutter-data-boundary | 02, 03 |
| `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt` | 01-shutter-data-boundary | 02, 03 |
| `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt` | 01-shutter-data-boundary | 02, 03 |
| `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` | 01-shutter-data-boundary / 03-zoom-scaleend-sync | coordinate if both need edits |
| `app/src/main/java/com/opencamera/app/ShutterVisualDrawable.kt` | 01-shutter-data-boundary | 02, 03 |
| `feature/mode-*/src/main/kotlin/**ModePlugin.kt` | 02-watermark-mainline | 01, 03 |
| `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt` | 02-watermark-mainline | 01, 03 |
| `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt` | 02-watermark-mainline / 03-zoom-scaleend-sync | coordinate if both need edits |
| `app/src/main/java/com/opencamera/app/gesture/**` | 03-zoom-scaleend-sync | 01, 02 |
| `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt` | 03-zoom-scaleend-sync | 01, 02 |
| `app/src/test/**` and `core/session/src/test/**` | package owning the production path | other packages must not edit unrelated tests |

## Agent Budget
- Recommended Claude Code agents: 3 implementation agents + Codex final audit
- Max parallel agents: 3
- Codex usage: final audit only; Codex may fix only tiny, obvious integration misses.
- When to pause: any package requires product approval, crosses Stage 7 scope, or fails verification in a way that requires broad architecture changes.

## Dispatch Plan
| Package | Mode | Agent Name | Prompt File | Status File |
|---|---|---|---|---|
| 01-shutter-data-boundary | agent-view | agent-01-shutter-boundary | `launchers/agent-view-prompts.md#package-01-shutter-data-boundary` | `status/01-shutter-data-boundary.md` |
| 02-watermark-mainline | agent-view | agent-02-watermark-mainline | `launchers/agent-view-prompts.md#package-02-watermark-mainline` | `status/02-watermark-mainline.md` |
| 03-zoom-scaleend-sync | agent-view | agent-03-zoom-sync | `launchers/agent-view-prompts.md#package-03-zoom-scaleend-sync` | `status/03-zoom-scaleend-sync.md` |
| 99-integration-audit | codex | — | `validation/final-audit-prompt.md` | `status/99-integration-audit.md` |

## Status Ledger
| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
|---|---|---|---|---|---|---|
| 01-shutter-data-boundary | — | pending | — | — | — | — |
| 02-watermark-mainline | — | pending | — | — | — | — |
| 03-zoom-scaleend-sync | — | pending | — | — | — | — |
| 99-integration-audit | — | pending | — | — | — | — |

## Merge Strategy
- Merge order: 02 can merge independently; 01 and 03 may merge in either order but must resolve any `CockpitSurfaceRenderer.kt` conflicts explicitly.
- Rebase policy: rebase package branches on latest `main` before merge or PR.
- Conflict owner: if `CockpitSurfaceRenderer.kt` conflicts, 01 owns shutter rendering lines and 03 owns focal-length/zoom rendering lines.
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
| [01-shutter-data-boundary.md](packages/01-shutter-data-boundary.md) | implementation agent | none | caution | Make shutter animation stop at actual data receipt, not saved-file completion |
| [02-watermark-mainline.md](packages/02-watermark-mainline.md) | implementation agent | none | safe | Land/rework watermark UI/effect wiring on current main |
| [03-zoom-scaleend-sync.md](packages/03-zoom-scaleend-sync.md) | implementation agent | none | caution | Fix pinch zoom continuity and focal-length UI synchronization |
| [99-integration-audit.md](packages/99-integration-audit.md) | Codex retained | after all packages | — | Final acceptance audit |

## Recommended Execution Order
1. Launch packages 01, 02, and 03 in parallel.
2. Each agent writes only its own status file and commits its package.
3. Merge or PR completed packages.
4. Run the final integration audit after all implementation packages complete.

## Launch Options
- Option A: Agent View manual dispatch — copy prompts from `launchers/agent-view-prompts.md`.
- Option B: `claude --bg` script — run `bash docs/plans/real-device-hotfix-rework/launchers/dispatch-claude-agents.sh`.
- Option C: Final integration audit — give `validation/final-audit-prompt.md` to Codex.
