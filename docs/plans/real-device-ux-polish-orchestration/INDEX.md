# Real Device UX Polish — Orchestration Index

## Goal

Turn the latest real-device findings into a staged, executable optimization plan for OpenCamera: restore visible Humanistic/Portrait mode entrances, remove noisy Style/Filter copy, make Settings subpage navigation direct, dismiss Quick reliably on outside taps, unify persisted user adjustments with Reset controls, and add Stage 7-aligned Dev log storage governance. This package is implementation-oriented, but Codex created only orchestration materials in this loop and did not edit runtime code.

## Current Evidence Snapshot

- `ModeId` already includes `HUMANISTIC` and `PORTRAIT`, and `ModeRegistry.supportedModes(...)` filters plugin availability through `isSupported(...)`.
- App mode visibility converges in `SessionCockpitRenderModel.visibleModeEntryOrder(...)`, `modeDirectoryRenderModel(...)`, `modeTrackRenderModel(...)`, `CockpitSurfaceRenderer`, `MainActivityViews`, `activity_main.xml`, and focused `SessionCockpitRenderModelTest`.
- Settings already has `SettingsSubpage.PORTRAIT_LAB`, `WATERMARK_SELECTOR`, and `WATERMARK_DETAIL`, plus `CockpitPanelCommand.OpenPortraitLab/OpenWatermarkSelector/OpenWatermarkDetail`; the bug is likely route/tab/render refresh linkage, not missing entire concepts.
- Quick panel already uses `CockpitPanelRoute.QuickBubble` and `panelDismissScrim`; real-device behavior suggests the scrim/hit region or z-order does not cover the unrelated lower preview/cockpit area.
- Style/Color Lab already has persisted actions for selected filters, custom profiles, color lab spec, and style strength, but selected-filter copy and reset semantics need one coherent UX contract.
- Dev logs are currently rendered from in-memory trace events and exported by `DevLogExporter` into `debug-logs`; no current evidence of a 20MB cap or type-specific cleanup owner was found.
- Current main workspace is dirty and contains unresolved conflicts in preview/effect files. Package agents must use isolated worktrees and must not clean unrelated conflicts unless their package explicitly owns the touched files.
- Claude Code local version checked: `2.1.142 (Claude Code)`. Official Claude Code CLI docs confirm `claude agents`, `--bg`, `--name`, `--permission-mode`, `--model`, `--effort`, and note that `claude --help` does not list every flag. Source: https://code.claude.com/docs/en/cli-usage

## Execution Mode Recommendation

- Recommended mode: AGENT_VIEW
- Why: The six fixes are small but not fully file-disjoint; Agent View allows staged worktree execution with human/Codex control over merge order. Use at most two agents in parallel after the mode visibility package is resolved.
- Alternatives rejected:
  - SINGLE_AGENT — safest for conflicts, but wastes parallelism across Dev log and Quick dismiss.
  - BACKGROUND_AGENT_SCRIPT — provided as optional phased dispatch, but not recommended as the primary path because several packages touch shared app render/model files.
  - BATCH — not a mechanical repository-wide transform.
  - AGENT_TEAM — unnecessary for scoped implementation packages.
- Max parallel agents: 2
- Codex-retained work: final integration audit, product wording consistency, real-device/multimodal acceptance judgment, and any decision to cross stage boundaries.

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:
- Read this plan, all package documents, `AGENTS.md`, `codex/plan.md`, `codex/prompt.md`, `codex/documentation.md`, and referenced source/test files.
- Create or reuse an isolated git worktree for implementation.
- Make scope-bounded edits, add/update tests, and update docs as described in your assigned package.
- Run the listed verification commands through `rtk`.
- Commit locally within the worktree branch.
- Merge, push, or create PRs for worktree branches as incremental, non-destructive operations.
- Write to ONLY your assigned `status/<package-id>.md` file. Never edit `INDEX.md` or another package's status file.

## Stop Gates — Must Ask

STOP and ask the user before:
- Crossing Stage boundaries or declaring Stage 7 complete.
- Reopening frozen Stage 6/6B feature scope beyond the explicit real-device polish bug.
- Making broad architecture changes outside the existing Mode Plugin / Session Kernel / Device Adapter / Media Pipeline boundaries.
- Destructive git operations: force-push, hard reset, deleting branches/worktrees.
- Network access, external API calls, new dependencies, or adding secrets/credentials.
- Overwriting unrelated dirty changes or resolving unrelated conflicts from the main workspace.
- Editing forbidden paths or fixing verification failures when the fix expands beyond your package.
- Claiming a real-device UX issue is fixed without either local deterministic tests or an explicit “needs real-device smoke” residual risk.

## Completion Policy

After completing your assigned package:
- Write your evidence pack to `status/<package-id>.md` — do NOT edit `INDEX.md`.
- Merge, push, or create a PR as the final step if your normal worktree flow supports it; no need to ask for non-destructive integration steps.
- Report what changed, test results, merge/PR status, and branch/worktree path.
- Do NOT delete the worktree unless explicitly instructed.

## Concurrency Plan

| Group | Packages | Can Run In Parallel | Must Wait For | Conflict Risk |
|---|---|---|---|---|
| G0 | 00-mode-entry-visibility | no | none | high priority; may unblock app UI tests used by all later packages |
| G1 | 03-quick-panel-outside-dismiss, 05-dev-log-storage-governance | yes | 00 completed or at least understood | low/medium; mostly disjoint files |
| G2 | 01-style-copy-noise-cleanup, 02-settings-third-level-navigation | limited; prefer one at a time if both touch `SessionUiRenderModel.kt` | 00 completed | medium; both may touch app render model/text |
| G3 | 04-persistence-reset-unification | no | 01, 02, 03 settled | high; intentionally spans settings/style/color/quick reset semantics |
| G4 | 99-integration-audit | no | 00-05 complete | final audit |

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
|---|---|---|
| `core/mode/**`, `feature/mode-humanistic/**`, `feature/mode-portrait/**`, `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`, `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt` | 00-mode-entry-visibility | 01-05 unless coordinated |
| `app/src/main/java/com/opencamera/app/FilterLabPanelRenderer.kt`, filter/style copy sections in `SessionUiRenderModel.kt`, `AppTextResolver.kt`, string resources, filter/style tests | 01-style-copy-noise-cleanup | 00, 02-05 |
| `CockpitPanelRouter.kt`, `CockpitPanelRoute.kt`, `SettingsPanelRenderer.kt`, settings subpage sections in `MainActivityActionBinder.kt`, settings tests | 02-settings-third-level-navigation | 00, 01, 03-05 |
| `MainActivityRenderer.kt`, `MainActivityActionBinder.kt` quick dismiss code, `activity_main.xml` scrim/hit area, quick panel render tests | 03-quick-panel-outside-dismiss | 00-02, 04-05 |
| `core/settings/**`, `SharedPreferencesPersistedSettingsStore.kt`, reset actions/render models across Settings/Style/Color Lab/Quick, serializer/manager tests | 04-persistence-reset-unification | 00-03, 05 |
| `DevLogExporter.kt`, `DevLogRenderModel.kt`, `DevConsoleRenderer.kt`, Dev console views/layout, Dev log tests | 05-dev-log-storage-governance | 00-04 |
| `core/effect/**`, `PreviewOverlayView.kt`, `PreviewColorTransformOverlay.kt` | forbidden unless explicitly required by package 04 and conflicts are first resolved in the agent worktree | all packages must avoid casual edits |
| `docs/plans/real-device-ux-polish-orchestration/status/<package-id>.md` | matching package | all other packages |

## Agent Budget

- Recommended Claude Code agents: 4 implementation agents over staged phases + Codex audit.
- Max parallel agents: 2.
- Codex usage: final audit, product taste, real-device checklist, and conflict triage.
- When to pause: if package 00 cannot restore both Humanistic and Portrait without changing core mode/plugin contracts, or if package 04 reveals settings persistence requires a new hidden state owner.

## Dispatch Plan

| Package | Mode | Agent Name | Prompt File | Status File |
|---|---|---|---|---|
| 00-mode-entry-visibility | agent-view | agent-00-mode-entry | launchers/agent-view-prompts.md#package-00-mode-entry-visibility | status/00-mode-entry-visibility.md |
| 01-style-copy-noise-cleanup | agent-view | agent-01-style-copy | launchers/agent-view-prompts.md#package-01-style-copy-noise-cleanup | status/01-style-copy-noise-cleanup.md |
| 02-settings-third-level-navigation | agent-view | agent-02-settings-nav | launchers/agent-view-prompts.md#package-02-settings-third-level-navigation | status/02-settings-third-level-navigation.md |
| 03-quick-panel-outside-dismiss | agent-view | agent-03-quick-dismiss | launchers/agent-view-prompts.md#package-03-quick-panel-outside-dismiss | status/03-quick-panel-outside-dismiss.md |
| 04-persistence-reset-unification | agent-view | agent-04-persist-reset | launchers/agent-view-prompts.md#package-04-persistence-reset-unification | status/04-persistence-reset-unification.md |
| 05-dev-log-storage-governance | agent-view | agent-05-dev-log | launchers/agent-view-prompts.md#package-05-dev-log-storage-governance | status/05-dev-log-storage-governance.md |
| 99-integration-audit | codex | — | validation/final-audit-prompt.md | status/99-integration-audit.md |

## Status Ledger

| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
|---|---|---|---|---|---|---|
| 00-mode-entry-visibility | — | pending | — | — | — | — |
| 01-style-copy-noise-cleanup | — | pending | — | — | — | — |
| 02-settings-third-level-navigation | — | pending | — | — | — | — |
| 03-quick-panel-outside-dismiss | — | pending | — | — | — | — |
| 04-persistence-reset-unification | — | pending | — | — | — | — |
| 05-dev-log-storage-governance | — | pending | — | — | — | — |
| 99-integration-audit | — | pending | — | — | — | — |

## Merge Strategy

- Merge order: 00 first; 03 and 05 may merge next in either order; then 01 and 02; 04 last; 99 final audit.
- Rebase policy: rebase on latest main before PR or merge, especially before package 04.
- Conflict owner: the package that first touches a shared file owns resolving conflicts in its branch; Codex owns cross-package integration conflicts during final audit.
- Final integration agent: Codex.
- Do not delete worktrees until: final audit passes and the user confirms cleanup.

## Evidence Pack Required From Each Agent

Each agent MUST write to its own `status/<package-id>.md` file after completion.
Do NOT edit `INDEX.md` directly.

Evidence pack must include:
- [ ] worktree path
- [ ] branch name
- [ ] git status
- [ ] git diff --stat
- [ ] changed files, full list
- [ ] commands run with output summary
- [ ] test result summary
- [ ] commit hash / PR link
- [ ] per-acceptance-criterion status
- [ ] unresolved risks
- [ ] whether it touched only allowed paths

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [00-mode-entry-visibility.md](packages/00-mode-entry-visibility.md) | implementation agent | none | no | Restore Humanistic and Portrait visibility through catalog/bottom mode/availability chain |
| [01-style-copy-noise-cleanup.md](packages/01-style-copy-noise-cleanup.md) | implementation agent | 00 | limited | Rename Lens to Style and remove meaningless selected-filter copy/actions |
| [02-settings-third-level-navigation.md](packages/02-settings-third-level-navigation.md) | implementation agent | 00 | limited | Make Settings Photo Portrait/Watermark buttons directly route to third-level pages |
| [03-quick-panel-outside-dismiss.md](packages/03-quick-panel-outside-dismiss.md) | implementation agent | 00 | safe with 05 | Make Quick dismiss on unrelated lower/preview/cockpit taps |
| [04-persistence-reset-unification.md](packages/04-persistence-reset-unification.md) | implementation agent | 01, 02, 03 | no | Unify automatic memory and Reset across Settings, Style, Color Lab, and Quick |
| [05-dev-log-storage-governance.md](packages/05-dev-log-storage-governance.md) | implementation agent | none | safe with 03 | Add 20MB Dev log cap and type-specific one-tap cleanup |
| [99-integration-audit.md](packages/99-integration-audit.md) | Codex retained | after all packages | — | Final cross-package audit and real-device acceptance entry |

## Recommended Execution Order

1. Launch 00 first.
2. Launch 03 and 05 in parallel after 00 evidence is available.
3. Launch 01, then 02, or run them sequentially if both touch the same render/text files.
4. Launch 04 after 01/02/03 settle.
5. Run 99 final integration audit.

## Launch Options

- **Option A**: Agent View manual dispatch — copy prompts from `launchers/agent-view-prompts.md`.
- **Option B**: background agent script — run phased commands such as `bash launchers/dispatch-claude-agents.sh g0`, then `g1`, `g2`, `g3`, and `audit`.
- **Option C**: `/batch` — not recommended for this non-mechanical task.
- **Option D**: Final integration audit — give `validation/final-audit-prompt.md` to Codex after package evidence is written.

## Claude Background Permission Notes

- `claude --bg --name` is valid for creating Claude Code background sessions that appear in Agents View.
- Generated scripts default to inheriting the user's configured Claude Code permission mode by omitting `--permission-mode`.
- If the user has set `permissions.defaultMode` to `bypassPermissions` in `~/.claude/settings.json`, generated background sessions inherit it without this repository hard-coding it.
- Use `CLAUDE_PERMISSION_MODE=bypassPermissions`, `auto`, `default`, or another supported mode only when an explicit per-dispatch override is needed.
- If using `CLAUDE_PERMISSION_MODE=auto`, first run `claude --permission-mode auto` interactively once.
- Do not use `--dangerously-skip-permissions`.
